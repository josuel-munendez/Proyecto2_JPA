package com.example.servicio.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.example.servicio.client.InterServiceClient;
import com.example.servicio.dto.ProductoImagenDTO;
import com.example.servicio.dto.ProductoRequest;
import com.example.servicio.dto.ProductoResponse;
import com.example.servicio.dto.VarianteDTO;
import com.example.servicio.entity.Producto;
import com.example.servicio.entity.Producto.EstadoProducto;
import com.example.servicio.entity.ProductoAuditoria;
import com.example.servicio.entity.ProductoImagen;
import com.example.servicio.entity.Variante;
import com.example.servicio.exception.BusinessRuleException;
import com.example.servicio.exception.ResourceNotFoundException;
import com.example.servicio.exception.VersionConflictException;
import com.example.servicio.repository.ProductoAuditoriaRepository;
import com.example.servicio.repository.ProductoImagenRepository;
import com.example.servicio.repository.ProductoRepository;
import com.example.servicio.repository.VarianteRepository;
import com.example.servicio.service.ProductoService;

/**
 * Implementacion del servicio de productos (CRUD completo) sobre MongoDB.
 *
 * Reparte las responsabilidades de la purga fisica entre dos bases de datos:
 *   - MongoDB (aqui): el producto, sus imagenes, sus variantes y la auditoria.
 *   - PostgreSQL de Django (via InterServiceClient): los items de carrito, los
 *     vinculos con categorias y las resenas, que no son alcanzables desde
 *     MongoDB. Django expone un endpoint que hace esa parte.
 *
 * ATOMICIDAD: MongoDB ofrece transacciones solo en réplicas, y la base de
 * desarrollo (standalone) no las soporta. Por eso la clase NO lleva
 * {@code @Transactional} (a diferencia de la rama JPA) y en su lugar ordena
 * las operaciones de forma que un fallo a mitad no deje datos inconsistentes:
 * primero se borran los hijos, que son recuperables, y solo al final el
 * producto. Si algo falla, el producto sigue existiendo y sus hijos se pueden
 * volver a generar; al reves, un producto huerfano con hijos colgando seria
 * irrecuperable.
 */
@Service
public class ProductoServiceImpl implements ProductoService {

    private static final Logger log = LoggerFactory.getLogger(ProductoServiceImpl.class);

    private final ProductoRepository productoRepository;
    private final ProductoImagenRepository productoImagenRepository;
    private final VarianteRepository varianteRepository;
    private final ProductoAuditoriaRepository auditoriaRepository;
    private final InterServiceClient interServiceClient;

    public ProductoServiceImpl(ProductoRepository productoRepository,
                               ProductoImagenRepository productoImagenRepository,
                               VarianteRepository varianteRepository,
                               ProductoAuditoriaRepository auditoriaRepository,
                               InterServiceClient interServiceClient) {
        this.productoRepository = productoRepository;
        this.productoImagenRepository = productoImagenRepository;
        this.varianteRepository = varianteRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.interServiceClient = interServiceClient;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Enriquecimiento de respuestas
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Imagen principal de cada producto de la collection. Una consulta por
     * lote: pedir una imagen por producto seria un N+1 sobre MongoDB.
     */
    private Map<String, String> indexarImagenesPrincipales(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return productoImagenRepository.findByProductoIdInAndEsPrincipalTrue(ids).stream()
                .collect(Collectors.toMap(
                        ProductoImagen::getProductoId,
                        ProductoImagen::getImage,
                        (a, b) -> a));
    }

    private Map<String, String> indexarImagenesPrincipalesDePagina(Page<Producto> pagina) {
        return indexarImagenesPrincipales(pagina.getContent().stream()
                .map(Producto::getId)
                .toList());
    }

    private ProductoResponse toResponse(Producto producto, Map<String, String> mainImages) {
        return ProductoResponse.fromEntity(producto, mainImages.get(producto.getId()));
    }

    private Page<ProductoResponse> toEnrichedPage(Page<Producto> pagina, Map<String, String> mainImages) {
        Page<ProductoResponse> page = pagina.map(p -> toResponse(p, mainImages));
        enriquecer(page.getContent());
        return page;
    }

    /**
     * Enriquece una lista de respuestas con:
     * - flags de auditoria (wasDisapproved / wasPublished / wasDeleted)
     * - conteos de imagenes y variantes, stock total y readyToPublish
     *
     * Todo se resuelve con una consulta por coleccion para la pagina
     * completa, no por producto.
     */
    private void enriquecer(List<ProductoResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return;
        }
        List<String> ids = responses.stream().map(ProductoResponse::getId).toList();

        Map<String, Set<String>> flags = new HashMap<>();
        for (ProductoAuditoria a : auditoriaRepository.findHistorialDe(ids)) {
            if (a.getProductoId() != null) {
                flags.computeIfAbsent(a.getProductoId(), k -> new HashSet<>()).add(a.getAction());
            }
        }

        // Un solo recorrido de imagenes y variantes para toda la pagina: de
        // cada documento sale el conteo de su producto, y el total de stock
        // se acumula en la misma vuelta.
        Map<String, Long> imgCounts = new HashMap<>();
        Map<String, Long> varCounts = new HashMap<>();
        Map<String, Integer> totalStock = new HashMap<>();
        Set<String> withMain = new HashSet<>();
        Set<String> withStock = new HashSet<>();

        for (ProductoImagen img : productoImagenRepository.findByProductoIdIn(ids)) {
            imgCounts.merge(img.getProductoId(), 1L, Long::sum);
            if (Boolean.TRUE.equals(img.getEsPrincipal())) {
                withMain.add(img.getProductoId());
            }
        }
        for (Variante v : varianteRepository.findByProductoIdIn(ids)) {
            varCounts.merge(v.getProductoId(), 1L, Long::sum);
            if (v.getStock() != null && v.getStock() > 0) {
                withStock.add(v.getProductoId());
                totalStock.merge(v.getProductoId(), v.getStock(), Integer::sum);
            }
        }

        for (ProductoResponse r : responses) {
            Set<String> actions = flags.getOrDefault(r.getId(), Set.of());
            r.setWasDisapproved(actions.contains(ProductoAuditoria.ACTION_DISAPPROVED));
            r.setWasPublished(actions.contains(ProductoAuditoria.ACTION_PUBLISHED));
            r.setWasDeleted(actions.contains(ProductoAuditoria.ACTION_DELETED));
            r.setImagesCount(imgCounts.getOrDefault(r.getId(), 0L));
            r.setVariantsCount(varCounts.getOrDefault(r.getId(), 0L));
            r.setTotalStock(totalStock.getOrDefault(r.getId(),
                    r.getStock() != null ? r.getStock() : 0));
            r.setReadyToPublish(withMain.contains(r.getId()) && withStock.contains(r.getId()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Auditoria
    // ═══════════════════════════════════════════════════════════════════════

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

    private void registrarAuditoria(String productoId, String action,
                                    Map<String, Object> before, Map<String, Object> after,
                                    String motivo) {
        ProductoAuditoria a = new ProductoAuditoria(
                productoId, action, "spring", before, after, motivo == null ? "" : motivo);
        a.onCreate();
        auditoriaRepository.save(a);
    }

    private static boolean esPrecioCopValido(BigDecimal precio) {
        return precio.compareTo(BigDecimal.valueOf(50)) >= 0
                && precio.remainder(BigDecimal.valueOf(50)).compareTo(BigDecimal.ZERO) == 0;
    }

    private void validarPrecioCop(BigDecimal precio) {
        if (precio == null) {
            return;
        }
        if (!esPrecioCopValido(precio)) {
            throw new BusinessRuleException("El precio en COP debe ser >= 50 y multiplo de 50.");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CRUD
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public ProductoResponse crearProducto(ProductoRequest request) {
        log.info("MongoDB: Creando nuevo producto con referencia: {}", request.getReferencia());

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
        // Paridad con Django: los productos nuevos entran PENDIENTES (ambos
        // flags en false) y no se publican solos.
        producto.setAprobado(false);
        producto.setEstado(EstadoProducto.INACTIVO);
        producto.sincronizarEstado();
        producto.setVersion(0L);
        producto.onCreate();

        Producto guardado = productoRepository.save(producto);
        registrarAuditoria(guardado.getId(), ProductoAuditoria.ACTION_CREATED,
                Map.of(), snapshot(guardado), "");

        log.info("Producto creado exitosamente en MongoDB con ID: {}", guardado.getId());
        ProductoResponse res = ProductoResponse.fromEntity(guardado);
        enriquecer(List.of(res));
        sincronizarCategorias(guardado.getId(), request.getCategoriaIds());
        return res;
    }

    /**
     * Replica en Django el conjunto de categorías del producto.
     *
     * Solo actúa si el cliente envió el campo. Con null se deja la asignación
     * como esté: el motivo es que las categorías viven en el PostgreSQL de
     * Django y, si Django está caído, bloquear el guardado del producto entero
     * (que sí está en MongoDB y sí se pudo escribir) sería castigar al usuario
     * por una dependencia que no es suya. Con lista vacía se quitan todas,
     * porque entonces el cliente sí está diciendo "quítamelas".
     *
     * Si Django falla con lista no nula sí se propaga el error: el usuario
     * desmarcó algo explícitamente y se le debe decir que no se guardó, en
     * lugar de confirmar un guardado a medias.
     */
    private void sincronizarCategorias(String productoId, List<Long> categoriaIds) {
        if (categoriaIds == null) {
            return;
        }
        interServiceClient.reemplazarCategorias(productoId, categoriaIds);
    }

    @Override
    public ProductoResponse obtenerPorId(String id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));
        ProductoResponse res = toResponse(producto, indexarImagenesPrincipales(List.of(id)));
        enriquecer(List.of(res));
        adjuntarSubrecursos(res, producto);
        res.setCategorias(interServiceClient.obtenerCategorias(id));
        return res;
    }

    /**
     * Añade al detalle las listas completas de imágenes y variantes.
     *
     * Solo en el detalle de un producto: son dos consultas extra y en un listado
     * de 20 productos serían 40 por nada, cuando el listado solo necesita los
     * conteos que ya calcula {@link #enriquecer}.
     *
     * El precio de cada variante se resuelve con la misma regla que usaba
     * Django (Variant.effective_price): el override si existe, y si no el
     * precio base del producto.
     */
    private void adjuntarSubrecursos(ProductoResponse res, Producto producto) {
        res.setImagenes(productoImagenRepository.findByProductoIdOrderByCreatedAtAsc(producto.getId())
                .stream()
                .map(this::toImagenDTO)
                .toList());

        res.setVariantes(varianteRepository.findByProductoIdOrderByIdAsc(producto.getId())
                .stream()
                .map(v -> toVarianteDTO(v, producto.getPrecioBase()))
                .toList());
    }

    private ProductoImagenDTO toImagenDTO(ProductoImagen imagen) {
        ProductoImagenDTO dto = new ProductoImagenDTO();
        dto.setId(imagen.getId());
        dto.setProductoId(imagen.getProductoId());
        dto.setImage(imagen.getImage());
        dto.setEsPrincipal(imagen.getEsPrincipal());
        dto.setCloudinaryUrl(imagen.getCloudinaryUrl());
        dto.setCreatedAt(imagen.getCreatedAt());
        return dto;
    }

    private VarianteDTO toVarianteDTO(Variante variante, BigDecimal precioBase) {
        VarianteDTO dto = new VarianteDTO();
        dto.setId(variante.getId());
        dto.setProductoId(variante.getProductoId());
        dto.setSize(variante.getSize());
        dto.setColor(variante.getColor());
        dto.setColorHex(variante.getColorHex());
        dto.setColorNombre(variante.getColorNombre());
        dto.setStock(variante.getStock());
        dto.setPriceVariant(variante.getPriceVariant());
        dto.setPrecioEfectivo(variante.getPriceVariant() != null ? variante.getPriceVariant() : precioBase);
        dto.setCreatedAt(variante.getCreatedAt());
        return dto;
    }

    @Override
    public ProductoResponse actualizarProducto(String id, ProductoRequest request) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        validarPrecioCop(request.getPrecioBase());

        // Bloqueo optimista a nivel de cliente. MongoDB no tiene @Version, asi
        // que la comparacion es explicita: si el formulario traia la version
        // con la que se cargo y el documento ya tiene otra, otro usuario (u
        // otro backend) escribio en medio. Sin esto, un admin que deja el
        // formulario abierto 2 minutos pisa lo que otro acaba de guardar.
        if (request.getVersion() != null
                && !request.getVersion().equals(producto.getVersion())) {
            throw new VersionConflictException(request.getVersion(), producto.getVersion());
        }

        if (!producto.getNombre().equalsIgnoreCase(request.getNombre())
                && productoRepository.existsByNombre(request.getNombre())) {
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
        producto.setVersion(producto.getVersion() == null ? 1L : producto.getVersion() + 1);
        producto.onUpdate();

          Producto actualizado = productoRepository.save(producto);
          registrarAuditoria(actualizado.getId(), ProductoAuditoria.ACTION_UPDATED,
                  before, snapshot(actualizado), "");

          log.info("Producto ID: {} actualizado correctamente en MongoDB (version {})",
                  id, actualizado.getVersion());
          ProductoResponse res = ProductoResponse.fromEntity(actualizado,
                  indexarImagenesPrincipales(List.of(id)).get(id));
          enriquecer(List.of(res));
          // Las categorías se replican DESPUÉS de validar la versión: si el
          // guardado va a fallar por conflicto, no tiene sentido haber aplicado
          // a medias el cambio de categorías en Django.
          sincronizarCategorias(id, request.getCategoriaIds());
          res.setCategorias(interServiceClient.obtenerCategorias(id));
          return res;
      }

    @Override
    public void cambiarEstado(String id, EstadoProducto nuevoEstado) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        producto.setEstado(nuevoEstado);
        producto.sincronizarEstado();
        producto.onUpdate();
        productoRepository.save(producto);
        log.info("Estado del producto ID: {} actualizado a {}", id, nuevoEstado);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Auditoría
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Historial de auditoría del producto, del más reciente al más antiguo.
     *
     * Reemplaza a GET /api/products/{id}/audits/ de Django, que en la rama
     * MongoDB no puede resolver el producto por su ObjectId. La fuente de
     * verdad es la colección producto_auditoria.
     */
    @Override
    public List<ProductoAuditoria> listarAuditorias(String id) {
        if (!productoRepository.existsById(id)) {
            throw new ResourceNotFoundException("Producto no encontrado con ID: " + id);
        }
        return auditoriaRepository.findByProductoIdOrderByCreatedAtDesc(id);
    }

    @Override
    public ProductoResponse desaprobar(String id, String motivo) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        Map<String, Object> before = snapshot(producto);

        // Desaprobar = desactivar y volver a dejar pendiente de aprobación.
        // Se hace sobre los tres campos y no solo sobre `estado` porque en
        // MongoDB isActive y aprobado son banderas independientes y el catálogo
        // filtra por ellas, no por estado.
        producto.setAprobado(false);
        producto.setIsActive(false);
        producto.setEstado(EstadoProducto.INACTIVO);
        producto.setVersion(producto.getVersion() == null ? 1L : producto.getVersion() + 1);
        producto.onUpdate();
        Producto actualizado = productoRepository.save(producto);

        registrarAuditoria(id, ProductoAuditoria.ACTION_DISAPPROVED,
                before, snapshot(actualizado), motivo);
        log.info("Producto ID: {} desaprobado. Motivo: {}", id, motivo);

        ProductoResponse res = ProductoResponse.fromEntity(actualizado,
                indexarImagenesPrincipales(List.of(id)).get(id));
        enriquecer(List.of(res));
        return res;
    }

    @Override
    public ProductoResponse cambiarActivo(String id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        boolean nuevoValor = !Boolean.TRUE.equals(producto.getIsActive());
        producto.setIsActive(nuevoValor);
        producto.setEstado(nuevoValor ? EstadoProducto.ACTIVO : EstadoProducto.INACTIVO);
        producto.sincronizarEstado();
        producto.setVersion(producto.getVersion() == null ? 1L : producto.getVersion() + 1);
        producto.onUpdate();
        Producto actualizado = productoRepository.save(producto);

        registrarAuditoria(id, ProductoAuditoria.ACTION_UPDATED,
                Map.of("is_active", !nuevoValor),
                Map.of("is_active", nuevoValor),
                nuevoValor ? "activado" : "desactivado");

        ProductoResponse res = ProductoResponse.fromEntity(actualizado,
                indexarImagenesPrincipales(List.of(id)).get(id));
        enriquecer(List.of(res));
        return res;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Imágenes
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public ProductoImagenDTO agregarImagen(String id, String image, boolean esPrincipal) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));
        if (image == null || image.isBlank()) {
            throw new BusinessRuleException("La URL de la imagen es obligatoria");
        }

        // Una sola imagen principal por producto: si la nueva lo es, se
        // democan las demás. Sin esto, dos imágenes con esPrincipal=true
        // harían que el catálogo mostrara una distinta cada vez.
        if (esPrincipal) {
            productoImagenRepository.findByProductoIdAndEsPrincipalTrue(id)
                    .forEach(a -> { a.setEsPrincipal(false); productoImagenRepository.save(a); });
        }

        ProductoImagen nueva = new ProductoImagen();
        nueva.setProductoId(id);
        nueva.setImage(image.trim());
        nueva.setEsPrincipal(esPrincipal || productoImagenRepository.countByProductoId(id) == 0);
        // ProductoImagen no extiende BaseEntity y MongoDB no dispara @PrePersist,
        // así que la fecha se rellena a mano como hace el resto del servicio.
        // Sin esto, createdAt queda null y findByProductoIdOrderByCreatedAtAsc
        // devolvería la galería en un orden arbitrario.
        nueva.setCreatedAt(LocalDateTime.now());
        ProductoImagen guardada = productoImagenRepository.save(nueva);
        log.info("Imagen {} añadida al producto {}", guardada.getId(), id);
        return toImagenDTO(guardada);
    }

    @Override
    public void eliminarImagen(String id, String imagenId) {
        ProductoImagen imagen = productoImagenRepository.findById(imagenId)
                .filter(i -> i.getProductoId() != null && i.getProductoId().equals(id))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "La imagen " + imagenId + " no pertenece al producto " + id));
        productoImagenRepository.delete(imagen);
        log.info("Imagen {} eliminada del producto {}", imagenId, id);
    }

    @Override
    public ProductoImagenDTO marcarImagenPrincipal(String id, String imagenId) {
        ProductoImagen imagen = productoImagenRepository.findById(imagenId)
                .filter(i -> i.getProductoId() != null && i.getProductoId().equals(id))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "La imagen " + imagenId + " no pertenece al producto " + id));

        // Se democan las demás antes de promover esta. Al revés (promover y
        // luego limpiar) dejaría un instante con dos principales, y el
        // catálogo puede leer entremedias y mostrar la imagen equivocada.
        productoImagenRepository.findByProductoIdAndEsPrincipalTrue(id).stream()
                .filter(otra -> !otra.getId().equals(imagenId))
                .forEach(otra -> {
                    otra.setEsPrincipal(false);
                    productoImagenRepository.save(otra);
                });

        imagen.setEsPrincipal(true);
        ProductoImagen guardada = productoImagenRepository.save(imagen);
        log.info("Imagen {} promovida a principal del producto {}", imagenId, id);
        return toImagenDTO(guardada);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Variantes
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public VarianteDTO guardarVariante(String id, VarianteDTO request, String varianteId) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        Variante variante = (varianteId == null || varianteId.isBlank())
                ? new Variante()
                : varianteRepository.findById(varianteId)
                        .filter(v -> v.getProductoId() != null && v.getProductoId().equals(id))
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "La variante " + varianteId + " no pertenece al producto " + id));

        variante.setProductoId(id);
        variante.setSize(request.getSize());
        variante.setColor(request.getColor());
        variante.setColorHex(request.getColorHex());
        variante.setColorNombre(request.getColorNombre());
        variante.setStock(request.getStock() == null ? 0 : request.getStock());
        variante.setPriceVariant(request.getPriceVariant());
        if (variante.getCreatedAt() == null) {
            variante.setCreatedAt(LocalDateTime.now());
        }
        Variante guardada = varianteRepository.save(variante);
        log.info("Variante {} guardada en el producto {}", guardada.getId(), id);
        return toVarianteDTO(guardada, producto.getPrecioBase());
    }

    @Override
    public void eliminarVariante(String id, String varianteId) {
        Variante variante = varianteRepository.findById(varianteId)
                .filter(v -> v.getProductoId() != null && v.getProductoId().equals(id))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "La variante " + varianteId + " no pertenece al producto " + id));
        varianteRepository.delete(variante);
        log.info("Variante {} eliminada del producto {}", varianteId, id);
    }

    @Override
    public void eliminarLogico(String id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        producto.setEstado(EstadoProducto.BORRADO);
        producto.sincronizarEstado();
        producto.setAprobado(false);
        producto.onUpdate();
        productoRepository.save(producto);

        registrarAuditoria(id, ProductoAuditoria.ACTION_DELETED,
                Map.of("is_active", true),
                Map.of("is_active", false, "is_approved", false),
                "soft-delete");
        log.info("Auditoria 'deleted' registrada para producto ID: {}", id);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Purga fisica
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Purga fisica (hard delete) de un producto.
     *
     * Guardias (paridad con Django destroy):
     *  1) No aprobado Y con historial published/disapproved/deleted.
     *     Protege los productos PENDIENTES.
     *  2) Sin ordenes asociadas, consultado a Django. El fail-safe del
     *     cliente devuelve true si Django no responde, asi que un Django caido
     *     bloquea la purga en vez de permitirla.
     *
     * El borrado real se reparte entre las dos bases:
     *   MongoDB    -> variantes, imagenes, desvinculacion de auditoria, producto
     *   Django/PG  -> carrito, categorias y resenas (por HTTP)
     */
    @Override
    public void purgarProducto(String id) {
        log.warn("MongoDB: Purga fisica (destructiva) del producto ID {}", id);
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        if (Boolean.TRUE.equals(producto.getAprobado())) {
            throw new BusinessRuleException(
                    "Solo se pueden borrar productos desaprobados o con soft delete.");
        }

        boolean tieneHistorial = auditoriaRepository.findHistorial(id).stream()
                .map(ProductoAuditoria::getAction)
                .anyMatch(ProductoAuditoria.ACCIONES_HISTORIAL::contains);
        if (!tieneHistorial) {
            throw new BusinessRuleException(
                    "Solo se pueden borrar productos desaprobados o con soft delete.");
        }

        if (interServiceClient.tieneOrdenesAsociadas(id)) {
            throw new BusinessRuleException(
                    "No se puede purgar: existen ordenes con este producto. "
                    + "Los datos historicos se conservan (snapshot en OrderItem).");
        }

        // Orden de la cascada. Primero Django: si su limpieza falla, se aborta
        // y el producto sigue intacto. Despues MongoDB, de los hijos al padre.
        interServiceClient.limpiarDependenciasEnDjango(id);
        limpiarDependencias(id);

        productoRepository.delete(producto);
        log.warn("Producto ID: {} purgado fisicamente de MongoDB", id);
    }

    /**
     * Elimina los hijos del producto en MongoDB.
     *
     * No hay claves foraneas que cleaned por cascada como en PostgreSQL, asi
     * que cada borrado es explicito. Primero las variantes y las imagenes, que
     * son los unicos hijos aqui; la auditoria se desvincula (productoId = null)
     * en vez de borrarse, para conservar el historial igual que hace Django con
     * SET_NULL.
     */
    private void limpiarDependencias(String id) {
        long variantes = varianteRepository.deleteByProductoId(id);
        long imagenes = productoImagenRepository.deleteByProductoId(id);
        long auditoria = auditoriaRepository.desvincularProducto(id);

        if (imagenes > 0 || variantes > 0 || auditoria > 0) {
            log.warn("MongoDB: Cascada de purga del producto ID {} -> imagenes={} variantes={} "
                    + "auditoria desvinculada={}", id, imagenes, variantes, auditoria);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Listados
    // ═══════════════════════════════════════════════════════════════════════

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
        log.info("MongoDB: Listado con filtros search='{}' isActive={} isApproved={} min={} max={}",
                search, isActive, isApproved, minPrice, maxPrice);
        Page<Producto> pagina = productoRepository.buscarConFiltros(
                search, isActive, isApproved, minPrice, maxPrice, pageable);
        return toEnrichedPage(pagina, indexarImagenesPrincipalesDePagina(pagina));
    }
}
