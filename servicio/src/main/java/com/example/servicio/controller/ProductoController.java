package com.example.servicio.controller;

import com.example.servicio.client.InterServiceClient;
import com.example.servicio.dto.ProductoPageResponse;
import com.example.servicio.dto.ProductoRequest;
import com.example.servicio.dto.ProductoResponse;
import com.example.servicio.entity.Producto.EstadoProducto;
import com.example.servicio.service.ProductoService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Controlador REST del microservicio JPA/PostgreSQL: CRUD y paginación de Productos.
 *
 * <h2>Cómo llega una petición acá</h2>
 * <pre>
 *   ProductList.jsx  (botón "Eliminar")
 *     → productService.js  deleteMicroProduct(id)
 *     → microservice.js   axios DELETE /api/v1/productos/{id}
 *     → vite.config.js    proxy '/api/v1' ⇒ http://localhost:8082
 *     → ESTE CLASE        ProductoController.eliminarProducto(id)
 *     → ProductoServiceImpl.eliminarLogico(id)
 *     → ProductoRepository / PostgreSQL (Neon)
 * </pre>
 * El proxy de Vite es sólo desarrollo; en producción el prefijo '/api/v1'
 * apunta directo a este servicio.
 *
 * <h2>Endpoints</h2>
 * <pre>
 *   POST   /                 crear          → 201 + ProductoResponse
 *   GET    /                 listar pagina  → 200 + ProductoPageResponse
 *   GET    /buscar/and       reto 1 (Y)    → 200 + ProductoPageResponse
 *   GET    /buscar/or        reto 1 (O)    → 200 + ProductoPageResponse
 *   GET    /{id}             detalle        → 200 + ProductoResponse
 *   PUT    /{id}             actualizar     → 200 + ProductoResponse
 *   PATCH  /{id}             actualizar     → 200 (paridad con partial_update de Django)
 *   DELETE /{id}             SOFT delete    → 204 (estado = BORRADO)
 *   DELETE /{id}/purgar      HARD delete    → 204 (DELETE físico en la BD)
 * </pre>
 *
 * <h2>Por qué hay dos endpoints de borrado</h2>
 * Son operaciones distintas, no dos formas de la misma:
 * <ul>
 *   <li><b>DELETE /{id}</b>: borra lógicamente. El verbo es DELETE pero la BD
 *       recibe un UPDATE; la fila sobrevive para no romper la integridad
 *       referencial con las órdenes históricas.</li>
 *   <li><b>DELETE /{id}/purgar</b>: borra de verdad, y sólo si el producto
 *       está en un estado final y no tiene órdenes asociadas.</li>
 * </ul>
 *
 * <h2>Errores</h2>
 * No se capturan excepciones acá: las traduce {@code GlobalExceptionHandler}
 * (@RestControllerAdvice) a códigos HTTP semánticos —
 * 400 validación o regla de negocio, 404 no encontrado,
 * 409 conflicto de versión, 429 rate limit, 500 sin manejar.
 */
@RestController
@RequestMapping("/api/v1/productos")
public class ProductoController {

    private static final Logger log = LoggerFactory.getLogger(ProductoController.class);

    /** Mapeo de campos de ordenamiento del frontend/Django → propiedades JPA. */
    private static final Map<String, String> SORT_MAP = Map.ofEntries(
            Map.entry("name", "nombre"),
            Map.entry("base_price", "precioBase"),
            Map.entry("created_at", "createdAt"),
            Map.entry("updated_at", "updatedAt"),
            Map.entry("is_active", "isActive"),
            Map.entry("is_approved", "aprobado"),
            Map.entry("stock", "stock"),
            Map.entry("id", "id"));

    private final ProductoService productoService;
    private final InterServiceClient interServiceClient;

    public ProductoController(ProductoService productoService,
                              InterServiceClient interServiceClient) {
        this.productoService = productoService;
        this.interServiceClient = interServiceClient;
    }

    @PostMapping
    public ResponseEntity<ProductoResponse> crearProducto(@Valid @RequestBody ProductoRequest request) {
        log.info("JPA REST POST: Crear producto - referencia={}", request.getReferencia());
        ProductoResponse productoCreado = productoService.crearProducto(request);
        return new ResponseEntity<>(productoCreado, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductoResponse> obtenerPorId(@PathVariable Long id) {
        log.info("JPA REST GET: Obtener producto ID {}", id);
        return ResponseEntity.ok(productoService.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductoResponse> actualizarProducto(
            @PathVariable Long id,
            @Valid @RequestBody ProductoRequest request) {
        log.info("JPA REST PUT: Actualizar producto ID {}", id);
        return ResponseEntity.ok(productoService.actualizarProducto(id, request));
    }

    /** PATCH con el mismo comportamiento que PUT (paridad Django partial_update). */
    @PatchMapping("/{id}")
    public ResponseEntity<ProductoResponse> actualizarProductoParcial(
            @PathVariable Long id,
            @Valid @RequestBody ProductoRequest request) {
        log.info("JPA REST PATCH: Actualizar producto ID {}", id);
        return ResponseEntity.ok(productoService.actualizarProducto(id, request));
    }

    /**
     * SOFT DELETE — marca el producto como BORRADO.
     *
     * <p>Usa el verbo DELETE (que es lo que corresponde en REST) pero internamente
     * ejecuta un UPDATE: la fila no se elimina. Es deliberado, porque las líneas
     * de orden apuntan a este producto y borrarlo rompería la integridad
     * referencial del historial de ventas.</p>
     *
     * <p>No lleva body: el id viaja en la ruta, así que un payload sería
     * redundante. RFC 9110 §9.3.5 clarify que el body de un DELETE no tiene
     * semántica definida.</p>
     *
     * <p>Responde 204 No Content; por eso el cliente no tiene cuerpo que adaptar.</p>
     *
     * @throws com.example.servicio.exception.ResourceNotFoundException si no existe (→ 404)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarProducto(@PathVariable Long id) {
        log.info("JPA REST DELETE: Soft delete producto ID {}", id);
        productoService.eliminarLogico(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * HARD DELETE — elimina físicamente el producto de PostgreSQL.
     *
     * <p>Es la única ruta que termina en un DELETE real de JPA
     * ({code deleteById}). Las reglas de elegibilidad se aplican en
     * ProductoServiceImpl.purgarProducto, no acá: el controlador sólo traduce
     * HTTP y no conoce reglas de negocio.</p>
     *
     * <p>Si el producto no es elegible → BusinessRuleException → 400.</p>
     *
     * @throws com.example.servicio.exception.ResourceNotFoundException si no existe (→ 404)
     * @throws com.example.servicio.exception.BusinessRuleException     si tiene órdenes (→ 400)
     */
    @DeleteMapping("/{id}/purgar")
    public ResponseEntity<Void> purgarProducto(@PathVariable Long id) {
        log.warn("JPA REST DELETE: Purga fisica del producto ID {}", id);
        productoService.purgarProducto(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Diagnostico de la comunicacion servicio-a-servicio con Django.
     * Responde siempre 200 con el estado real, para poder distinguir
     * "Django caido" de "endpoint roto" al depurar.
     */
    @GetMapping("/internal/django-status")
    public ResponseEntity<Map<String, Object>> djangoStatus() {
        boolean disponible = interServiceClient.djangoDisponible();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("djangoDisponible", disponible);
        body.put("stats", disponible ? interServiceClient.obtenerStatsProductos() : Map.of());
        return ResponseEntity.ok(body);
    }

    /**
     * RETO 1 (mitad AND) — busqueda por 2 campos con operador Y, paginada.
     *
     * <p>Contrasta directamente con {@code /buscar/or}, que usa el operador O
     * sobre 3 campos. Los dos estan exposed para poder mostrar en la
     * sustentacion la diferencia entre conjuncion y disyuncion sobre la misma
     * tabla:</p>
     * <pre>
     *   AND  → nombre CONTIENE 'camiseta' Y estado = ACTIVO
     *   OR   → nombre LIKE '%x%' O descripcion LIKE '%x%' O referencia LIKE '%x%'
     * </pre>
     *
     * <p>La consulta no se escribe a mano: Spring Data la deriva del nombre
     * {@code findByNombreContainingIgnoreCaseAndIsActive}, donde el
     * {@code And} del metodo ES el operador Y.</p>
     *
     * <p>Por eso esta ruta es separada y no un parametro mas del listado
     * general: {@link #listarProductos} ya resuelve nombre+estado por su cuenta
     * (el bloque de compatibilidad legacy), asi que unparametro reutilizado
     * llegaria aqui sin ejecutar nunca esta consulta.</p>
     *
     * @param nombre texto a buscar dentro del nombre (case-insensitive)
     * @param estado estado exacto que debe tener el producto
     * @return pagina de productos que cumple AMBAS condiciones a la vez
     */
    @GetMapping("/buscar/and")
    public ResponseEntity<ProductoPageResponse> buscarPorNombreYEstado(
            @RequestParam String nombre,
            @RequestParam EstadoProducto estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        int pageSize = Math.min(Math.max(size, 1), 100);
        int paginaActual = Math.max(page, 0);
        Pageable pageable = PageRequest.of(paginaActual, pageSize,
                Sort.by(SORT_MAP.getOrDefault("id", "id")).descending());

        log.info("JPA REST GET: Busqueda AND - nombre CONTIENS '{}' Y estado={} (pagina {})",
                nombre, estado, paginaActual);
        return ResponseEntity.ok(ProductoPageResponse.fromPage(
                productoService.buscarPorNombreYEstado(nombre, estado, pageable)));
    }

    /**
     * RETO 1 (mitad OR) — busqueda por 3 campos con operador O, paginada.
     *
     * <p>Devuelve el producto si coincide en <b>al menos uno</b> de los tres
     * campos, ademas de excluir los borrados logicamente.</p>
     *
     * <p>El parametro viaja como binding de JPQL ({@code :query}) y se
     * concatena dentro de un {@code CONCAT('%', :query, '%')}, nunca se
     * interpola en la cadena JPQL. Por eso un termino con
     * {@code ' OR '1'='1} se trata como texto literal a buscar y no como SQL:
     * es la misma razon por la que la busqueda resiste inyeccion SQL.</p>
     *
     * @param query termino buscado simultaneamente en nombre, descripcion y referencia
     * @return pagina de productos que coincide en cualquiera de los 3 campos
     */
    @GetMapping("/buscar/or")
    public ResponseEntity<ProductoPageResponse> buscarPor3CamposOr(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        int pageSize = Math.min(Math.max(size, 1), 100);
        int paginaActual = Math.max(page, 0);
        Pageable pageable = PageRequest.of(paginaActual, pageSize, Sort.by("id").descending());

        log.info("JPA REST GET: Busqueda OR - (nombre OR descripcion OR referencia) CONTIENE '{}' (pagina {})",
                query, paginaActual);
        return ResponseEntity.ok(ProductoPageResponse.fromPage(
                productoService.buscarPor3CamposOr(query, pageable)));
    }

    @GetMapping
    public ResponseEntity<ProductoPageResponse> listarProductos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer page_size,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) EstadoProducto estado,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean is_active,
            @RequestParam(required = false) Boolean is_approved,
            @RequestParam(required = false) BigDecimal min_price,
            @RequestParam(required = false) BigDecimal max_price,
            @RequestParam(required = false) String ordering) {

        int pageSize = page_size != null ? page_size : size;
        if (pageSize < 1) pageSize = 10;
        if (pageSize > 100) pageSize = 100;
        if (page < 0) page = 0;

        String sortProperty = resolveSortProperty(sortBy, ordering);
        boolean descending = resolveDescending(sortDir, ordering, sortBy);

        Sort sort = descending ? Sort.by(sortProperty).descending() : Sort.by(sortProperty).ascending();
        Pageable pageable = PageRequest.of(page, pageSize, sort);

        boolean hasFiltros = (search != null && !search.isBlank())
                || is_active != null
                || is_approved != null
                || min_price != null
                || max_price != null
                || nombre != null;

        Page<ProductoResponse> resultadoPage;

        if (hasFiltros) {
            String searchFinal = (search != null && !search.isBlank())
                    ? search
                    : nombre;
            Boolean activeFinal = is_active;
            Boolean approvedFinal = is_approved;

            // Compatibilidad legacy: nombre + estado ACTIVO/INACTIVO/BORRADO
            if (estado != null && activeFinal == null && approvedFinal == null) {
                switch (estado) {
                    case ACTIVO -> { activeFinal = true; approvedFinal = true; }
                    case INACTIVO -> { activeFinal = false; approvedFinal = true; }
                    case BORRADO -> { activeFinal = false; approvedFinal = false; }
                }
            }

            log.info("JPA REST GET: Filtros search='{}' active={} approved={}",
                    searchFinal, activeFinal, approvedFinal);
            resultadoPage = productoService.listarConFiltros(
                    searchFinal, activeFinal, approvedFinal, min_price, max_price, pageable);
        } else {
            resultadoPage = productoService.listarPaginado(pageable);
        }

        return ResponseEntity.ok(ProductoPageResponse.fromPage(resultadoPage));
    }

    private static String resolveSortProperty(String sortBy, String ordering) {
        String candidate = sortBy;
        if ((candidate == null || candidate.isBlank()) && ordering != null && !ordering.isBlank()) {
            candidate = ordering.startsWith("-") ? ordering.substring(1) : ordering;
        }
        if (candidate == null || candidate.isBlank()) {
            candidate = "id";
        }
        return SORT_MAP.getOrDefault(candidate, "id");
    }

    private static boolean resolveDescending(String sortDir, String ordering, String sortBy) {
        if (ordering != null && !ordering.isBlank()) {
            String key = ordering.startsWith("-") ? ordering.substring(1) : ordering;
            if (SORT_MAP.containsKey(key) || key.equals("id")) {
                return ordering.startsWith("-");
            }
        }
        if (sortBy != null && sortBy.startsWith("-")) {
            return true;
        }
        return "desc".equalsIgnoreCase(sortDir);
    }
}
