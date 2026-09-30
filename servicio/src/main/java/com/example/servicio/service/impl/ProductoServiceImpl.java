package com.example.servicio.service.impl;

import com.example.servicio.dto.ProductoRequest;
import com.example.servicio.dto.ProductoResponse;
import com.example.servicio.entity.Producto;
import com.example.servicio.entity.Producto.EstadoProducto;
import com.example.servicio.entity.ProductoGrupos;
import com.example.servicio.exception.BusinessRuleException;
import com.example.servicio.exception.ResourceNotFoundException;
import com.example.servicio.exception.VersionConflictException;
import com.example.servicio.repository.ProductoImagenRepository;
import com.example.servicio.repository.ProductoRepository;
import com.example.servicio.repository.VarianteRepository;
import com.example.servicio.service.ProductoService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.servicio.entity.ProductoImagen;
import com.example.servicio.client.InterServiceClient;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementacion del servicio de productos (CRUD completo vía Spring Boot).
 *
 * <h2>Responsabilidades</h2>
 * <ul>
 *   <li>Implementar el contrato {@link ProductoService}.</li>
 *   <li>Aplicar las reglas de negocio (referencia única, precio COP, estados).</li>
 *   <li>Coordinar con Django vía {@link InterServiceClient} cuando la regla
 *       necesita datos que viven en el otro microservicio (ej: órdenes).</li>
 *   <li>Registrar auditoría de cada cambio de estado.</li>
 * </ul>
 *
 * <h2>Separación con el controlador</h2>
 * El controlador traduce HTTP; esta clase conoce el dominio. Si una regla
 * estuviera en ProductoController no se podría reutilizar desde otro canal
 * (por ejemplo, un consumidor de RabbitMQ) sin duplicarla.
 *
 * <h2>Transacciones</h2>
 * La clase esta anotada con {@code @Transactional} a nivel de clase: cada
 * metodo publico corre dentro de una transaccion. Si una operacion falla a
 * mitad (por ejemplo, en la purga se borran imagenes pero falla el producto),
 * Spring hace rollback y la BD queda consistente.
 */
@Service
@Transactional
public class ProductoServiceImpl implements ProductoService {

    private static final Logger log = LoggerFactory.getLogger(ProductoServiceImpl.class);

    private final ProductoRepository productoRepository;
    private final ProductoImagenRepository productoImagenRepository;
    private final VarianteRepository varianteRepository;
    private final InterServiceClient interServiceClient;
    private final Validator validator;

    public ProductoServiceImpl(ProductoRepository productoRepository,
                               ProductoImagenRepository productoImagenRepository,
                               VarianteRepository varianteRepository,
                               InterServiceClient interServiceClient,
                               Validator validator) {
        this.productoRepository = productoRepository;
        this.productoImagenRepository = productoImagenRepository;
        this.varianteRepository = varianteRepository;
        this.interServiceClient = interServiceClient;
        this.validator = validator;
    }

    /**
     * Aplica el grupo {@code ProductoGrupos.AlCrear} sobre la entidad.
     *
     * <p>Las validaciones del grupo Default las dispara Hibernate solo en cada
     * INSERT/UPDATE. Las de AlCrear no: hay que pedirlas explicitamente, y este
     * es el unico punto donde tiene sentido, porque la regla es "al dar de alta
     * un producto, la referencia debe tener este formato" y no "toda fila de
     * esta tabla debe cumplirlo".</p>
     *
     * <p>Se convierte la primera violacion en {@link BusinessRuleException}
     * para que el controlador la traduzca a 400 con el mensaje del anotado,
     * en vez de dejar que reviente como ConstraintViolationException -> 500.</p>
     *
     * @throws BusinessRuleException si el producto no cumple las reglas de alta
     */
    private void validarAlCrear(Producto producto) {
        Set<ConstraintViolation<Producto>> violations = validator.validate(producto, ProductoGrupos.AlCrear.class);
        if (!violations.isEmpty()) {
            throw new BusinessRuleException(violations.iterator().next().getMessage());
        }
    }

    private Map<Long, String> indexarImagenesPrincipales(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return productoImagenRepository.findPrincipalesEn(ids).stream()
                .collect(Collectors.toMap(
                        ProductoImagen::getProductoId,
                        ProductoImagen::getImage,
                        (a, b) -> a));
    }

    private Map<Long, String> indexarImagenesPrincipalesDePagina(Page<Producto> pagina) {
        List<Long> ids = pagina.getContent().stream()
                .map(Producto::getId)
                .toList();
        return indexarImagenesPrincipales(ids);
    }

    private ProductoResponse toResponse(Producto producto, Map<Long, String> mainImages) {
        return ProductoResponse.fromEntity(producto, mainImages.get(producto.getId()));
    }

    private String toJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : value.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escapeJson(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Boolean || v instanceof Number) {
                sb.append(v);
            } else {
                sb.append('"').append(escapeJson(String.valueOf(v))).append('"');
            }
        }
        return sb.append('}').toString();
    }

    private static String escapeJson(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private Map<String, Object> snapshot(Producto p) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", p.getNombre());
        m.put("description", p.getDescripcion());
        m.put("base_price", p.getPrecioBase());
        m.put("referencia", p.getReferencia());
        m.put("stock", p.getStock());
        m.put("is_active", p.getIsActive());
        m.put("is_approved", p.getAprobado());
        return m;
    }

    private void registrarAuditoria(Long productId, String action,
                                    Map<String, Object> before, Map<String, Object> after,
                                    String motivo) {
        productoRepository.insertAudit(
                productId,
                action,
                "spring",
                toJson(before),
                toJson(after),
                motivo == null ? "" : motivo);
    }

    private void validarPrecioCop(BigDecimal precio) {
        if (precio == null) {
            return;
        }
        if (!esPrecioCopValido(precio)) {
            throw new BusinessRuleException("El precio en COP debe ser >= 50 y multiplo de 50.");
        }
    }

    private static boolean esPrecioCopValido(BigDecimal precio) {
        return precio.compareTo(BigDecimal.valueOf(50)) >= 0
                && precio.remainder(BigDecimal.valueOf(50)).compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Enriquece una pagina de respuestas con:
     * - flags de auditoria (wasDisapproved / wasPublished / wasDeleted)
     * - contadores de imagenes/variantes, total_stock y ready_to_publish
     */
    private void enriquecer(List<ProductoResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return;
        }
        List<Long> ids = responses.stream().map(ProductoResponse::getId).toList();

        Map<Long, Set<String>> flags = new HashMap<>();
        for (Object[] row : productoRepository.findAuditFlags(ids)) {
            Long pid = ((Number) row[0]).longValue();
            String action = String.valueOf(row[1]);
            flags.computeIfAbsent(pid, k -> new HashSet<>()).add(action);
        }

        Map<Long, Long> imgCounts = new HashMap<>();
        for (Object[] row : productoImagenRepository.countPorProducto(ids)) {
            imgCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        Map<Long, Long> varCounts = new HashMap<>();
        for (Object[] row : varianteRepository.countPorProducto(ids)) {
            varCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        Set<Long> withMain = productoImagenRepository.findProductoIdsConPrincipal(ids);
        Set<Long> withStock = varianteRepository.findProductoIdsConStock(ids);
        Map<Long, Integer> totalStock = new HashMap<>();
        for (Object[] row : varianteRepository.sumStockPorProducto(ids)) {
            Long pid = ((Number) row[0]).longValue();
            totalStock.put(pid, ((Number) row[1]).intValue());
        }

        for (ProductoResponse r : responses) {
            Set<String> actions = flags.getOrDefault(r.getId(), Set.of());
            r.setWasDisapproved(actions.contains("disapproved"));
            r.setWasPublished(actions.contains("published"));
            r.setWasDeleted(actions.contains("deleted"));
            r.setImagesCount(imgCounts.getOrDefault(r.getId(), 0L));
            r.setVariantsCount(varCounts.getOrDefault(r.getId(), 0L));
            r.setTotalStock(totalStock.getOrDefault(r.getId(),
                    r.getStock() != null ? r.getStock() : 0));
            r.setReadyToPublish(withMain.contains(r.getId()) && withStock.contains(r.getId()));
        }
    }

    private Page<ProductoResponse> toEnrichedPage(Page<Producto> pagina, Map<Long, String> mainImages) {
        Page<ProductoResponse> page = pagina.map(p -> toResponse(p, mainImages));
        enriquecer(page.getContent());
        return page;
    }

    @Override
    public ProductoResponse crearProducto(ProductoRequest request) {
        log.info("JPA: Creando nuevo producto con referencia: {}", request.getReferencia());

        validarPrecioCop(request.getPrecioBase());

        if (productoRepository.existsByNombre(request.getNombre())) {
            throw new BusinessRuleException("Ya existe un producto con el nombre: " + request.getNombre());
        }

        if (request.getReferencia() != null && productoRepository.existsByReferencia(request.getReferencia())) {
            throw new BusinessRuleException("Ya existe un producto con la referencia: " + request.getReferencia());
        }

        Producto producto = new Producto();
        producto.setNombre(request.getNombre());
        producto.setDescripcion(request.getDescripcion());
        producto.setPrecioBase(request.getPrecioBase());
        producto.setReferencia(request.getReferencia());
        producto.setStock(request.getStock() != null ? request.getStock() : 0);
        // Paridad Django: productos nuevos entran PENDIENTES (ambos flags false)
        producto.setIsActive(false);
        producto.setAprobado(false);

        validarAlCrear(producto);

        Producto guardado = productoRepository.save(producto);
        productoRepository.flush();
        registrarAuditoria(guardado.getId(), "created",
                Map.of(), snapshot(guardado), "");
        log.info("Producto creado exitosamente con JPA - ID: {}", guardado.getId());

        ProductoResponse res = ProductoResponse.fromEntity(guardado);
        enriquecer(List.of(res));
        return res;
    }

    @Override
    public ProductoResponse obtenerPorId(Long id) {
        log.info("JPA: Buscando producto por ID: {}", id);
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));
        Map<Long, String> mainImages = indexarImagenesPrincipales(List.of(id));
        ProductoResponse res = toResponse(producto, mainImages);
        enriquecer(List.of(res));
        return res;
    }

    @Override
    public ProductoResponse actualizarProducto(Long id, ProductoRequest request) {
        log.info("JPA: Actualizando producto ID: {}", id);
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        validarPrecioCop(request.getPrecioBase());

        // Bloqueo optimista a nivel de cliente: si el formulario traía la
        // version con la que se cargó y la fila ya no la tiene, otro usuario
        // (u otro backend) escribió en medio. Sin este chequeo, @Version solo
        // detectaría la carrera ENTRE dos peticiones de Spring simultáneas, no
        // el caso normal: un admin que abre el formulario, tarda 2 minutos y
        // guarda encima de lo que otro acaba de guardar.
        if (request.getVersion() != null
                && !request.getVersion().equals(producto.getVersion())) {
            throw new VersionConflictException(request.getVersion(), producto.getVersion());
        }

        if (!producto.getNombre().equalsIgnoreCase(request.getNombre()) &&
                productoRepository.existsByNombre(request.getNombre())) {
            throw new BusinessRuleException("Ya existe otro producto con el nombre: " + request.getNombre());
        }

        Map<String, Object> before = snapshot(producto);

        producto.setNombre(request.getNombre());
        producto.setDescripcion(request.getDescripcion());
        producto.setPrecioBase(request.getPrecioBase());
        if (request.getReferencia() != null) {
            producto.setReferencia(request.getReferencia());
        }
        if (request.getStock() != null) {
            producto.setStock(request.getStock());
        }

        Producto actualizado = productoRepository.save(producto);
        productoRepository.flush();
        registrarAuditoria(actualizado.getId(), "updated",
                before, snapshot(actualizado), "");
        log.info("Producto ID: {} actualizado correctamente con JPA", id);

        ProductoResponse res = ProductoResponse.fromEntity(actualizado);
        enriquecer(List.of(res));
        return res;
    }

    @Override
    public void cambiarEstado(Long id, EstadoProducto nuevoEstado) {
        log.info("JPA: Cambiando estado del producto ID: {} a {}", id, nuevoEstado);
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        producto.setEstado(nuevoEstado);
        productoRepository.save(producto);
        log.info("Estado del producto ID: {} actualizado a {}", id, nuevoEstado);
    }

    /**
     * SOFT DELETE — deja el producto marcado como BORRADO, sin borrarlo.
     *
     * <p><b>Por qué no es un DELETE físico:</b> las líneas de orden historical
     * referencian al producto. Borrar la fila rompería la integridad referencial
     * y se perdería el historial de ventas. Un borrado lógico conserva el
     * registro y además permite deshacer.</p>
     *
     * <p><b>Qué hace, en orden:</b></p>
     * <ol>
     *   <li>{@code cambiarEstado(BORRADO)} → {@code save()} ⇒ UPDATE.</li>
     *   <li>{@code flush()} → fuerza el INSERT/UPDATE a ejecutarse ahora en vez
     *       de esperar al commit. Se usa porque el registro de auditoría que
     *       sigue necesita leer el estado real.</li>
     *   <li>Registra auditoría {@code "deleted"} con el estado anterior
     *       (is_active=true) y el nuevo (is_active=false, is_approved=false).
     *       Ese historial después es lo que habilita la purga.</li>
     * </ol>
     *
     * <p>SQL resultante: {@code UPDATE products_product SET estado='BORRADO' ...}
     * Devuelve 204 porque no hay nada que responder.</p>
     *
     * @throws ResourceNotFoundException si el producto no existe (→ 404)
     */
    @Override
    public void eliminarLogico(Long id) {
        log.info("JPA: Realizando borrado logico del producto ID: {}", id);
        cambiarEstado(id, EstadoProducto.BORRADO);
        productoRepository.flush();
        registrarAuditoria(id, "deleted",
                Map.of("is_active", true),
                Map.of("is_active", false, "is_approved", false),
                "soft-delete");
        log.info("JPA: Auditoria 'deleted' registrada para producto ID: {}", id);
    }

    /**
     * Purga fisica (hard delete) de un producto.
     *
     * Guardias (paridad Django destroy):
     *  1) No aprobado Y con historial disapproved/published/deleted
     *     (protege productos PENDIENTES).
     *  2) Sin ordenes asociadas — consulta a Django via InterServiceClient.
     *  3) Backstop en la propia BD: si aun asi hay lineas de orden, se rechaza
     *     en vez de desvincularlas (el historial de ventas no se toca).
     *
     * Luego se replica el collector de Django: todos los FK hacia
     * products_product estan en ON DELETE NO ACTION, porque el on_delete de
     * Django se aplica en Python y no en PostgreSQL. Si no se limpian las
     * tablas hijas, el DELETE del producto falla con
     * DataIntegrityViolationException.
     *
     * La guardia 3 tiene un matiz importante: si Django no responde, el dato es
     * DESCONOCIDO, no "tiene ordenes". InterServiceClient lanza entonces
     * InterServiceUnavailableException y la purga se corta igual (el fail-safe
     * no se toca), pero el cliente recibe un 503 con el motivo real en vez de un
     * 400 que afirmaria falsamente que el producto tiene historial de ventas.
     *
     * @throws com.example.servicio.exception.ResourceNotFoundException      si no existe (→ 404)
     * @throws com.example.servicio.exception.BusinessRuleException          si no es elegible o tiene ordenes (→ 400)
     * @throws com.example.servicio.exception.InterServiceUnavailableException si Django no pudo responder (→ 503)
     */
    @Override
    public void purgarProducto(Long id) {
        log.warn("JPA: Purga fisica (destructiva) del producto ID {}", id);
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        if (Boolean.TRUE.equals(producto.getAprobado())) {
            throw new BusinessRuleException(
                    "Solo se pueden borrar productos desaprobados o con soft delete.");
        }

        List<String> historial = productoRepository.findAuditActions(id);
        boolean elegible = historial.contains("disapproved")
                || historial.contains("published")
                || historial.contains("deleted");
        if (!elegible) {
            throw new BusinessRuleException(
                    "Solo se pueden borrar productos desaprobados o con soft delete.");
        }

        if (interServiceClient.tieneOrdenesAsociadas(id)) {
            throw new BusinessRuleException(
                    "No se puede purgar: existen ordenes con este producto. "
                    + "Los datos historicos se conservan (snapshot en OrderItem).");
        }

        // Backstop: Django pudo responder que no hay ordenes y aun quedar filas
        // (carrera, o una orden creada por otra via). No se nullan en silencio.
        long lineasOrden = productoRepository.countOrderItems(id);
        if (lineasOrden > 0) {
            throw new BusinessRuleException(
                    "No se puede purgar: el producto aparece en " + lineasOrden
                    + " linea(s) de orden. Los datos historicos se conservan.");
        }

        limpiarDependencias(id);
        productoRepository.flush();
        productoRepository.delete(producto);
        log.warn("Producto ID: {} purgado fisicamente de la base de datos", id);
    }

    /**
     * Elimina las filas hijas que bloquean el borrado del producto.
     *
     * El orden importa: carts_cartitem tiene FK tanto a products_product
     * como a products_variant, y ambas estan en NO ACTION, asi que los items
     * de carrito se van antes que las variantes.
     */
    private void limpiarDependencias(Long id) {
        int itemsCarrito = productoRepository.deleteCarritoItems(id);
        int categorias = productoRepository.deleteCategorias(id);
        int motivos = productoRepository.deleteMotivosDesaprobacion(id);
        int resenas = productoRepository.deleteReviews(id);
        int variantes = varianteRepository.deleteByProductoId(id);
        int imagenes = productoImagenRepository.deleteByProductoId(id);
        // La auditoría se conserva con product_id NULL (paridad SET_NULL).
        productoRepository.nullifyAuditRefs(id);

        if (imagenes > 0 || variantes > 0 || resenas > 0 || itemsCarrito > 0
                || categorias > 0 || motivos > 0) {
            log.warn("JPA: Cascada de purga del producto ID {} -> imagenes={} variantes={} "
                            + "resenas={} carrito={} categorias={} motivos={}",
                    id, imagenes, variantes, resenas, itemsCarrito, categorias, motivos);
        }
    }

    @Override
    public Page<ProductoResponse> listarPaginado(Pageable pageable) {
        Page<Producto> pagina = productoRepository.findByIsActiveTrueOrAprobadoTrue(pageable);
        return toEnrichedPage(pagina, indexarImagenesPrincipalesDePagina(pagina));
    }

    @Override
    public Page<ProductoResponse> buscarPorNombreYEstado(String nombre, EstadoProducto estado, Pageable pageable) {
        Boolean isActive = (estado == EstadoProducto.ACTIVO);
        Page<Producto> pagina = productoRepository.findByNombreContainingIgnoreCaseAndIsActive(
                nombre, isActive, pageable);
        return toEnrichedPage(pagina, indexarImagenesPrincipalesDePagina(pagina));
    }

    @Override
    public Page<ProductoResponse> buscarPor3CamposOr(String query, Pageable pageable) {
        Page<Producto> pagina = productoRepository.buscarPor3CamposOr(query, pageable);
        return toEnrichedPage(pagina, indexarImagenesPrincipalesDePagina(pagina));
    }

    @Override
    public Page<ProductoResponse> listarConFiltros(
            String search,
            Boolean isActive,
            Boolean isApproved,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable) {
        log.info("JPA: Listado con filtros search='{}' isActive={} isApproved={} min={} max={}",
                search, isActive, isApproved, minPrice, maxPrice);
        Page<Producto> pagina = productoRepository.buscarConFiltros(
                search, isActive, isApproved, minPrice, maxPrice, pageable);
        return toEnrichedPage(pagina, indexarImagenesPrincipalesDePagina(pagina));
    }
}
