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
import com.example.servicio.dto.ProductoImagenDTO;
import com.example.servicio.dto.VarianteDTO;
import com.example.servicio.entity.ProductoAuditoria;
import java.util.List;

/**
 * Controlador REST para MongoDB - CRUD y paginacion de Productos.
 * Paridad de filtros con Django ProductViewSet (search, is_active, is_approved, precios).
 */
@RestController
@RequestMapping("/api/v1/productos")
public class ProductoController {

    private static final Logger log = LoggerFactory.getLogger(ProductoController.class);

    /** Mapeo de campos de ordenamiento del frontend/Django a propiedades del documento. */
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
        log.info("MongoDB REST POST: Crear producto - referencia={}", request.getReferencia());
        ProductoResponse productoCreado = productoService.crearProducto(request);
        return new ResponseEntity<>(productoCreado, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductoResponse> obtenerPorId(@PathVariable String id) {
        log.info("MongoDB REST GET: Obtener producto ID {}", id);
        return ResponseEntity.ok(productoService.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductoResponse> actualizarProducto(
            @PathVariable String id,
            @Valid @RequestBody ProductoRequest request) {
        log.info("MongoDB REST PUT: Actualizar producto ID {}", id);
        return ResponseEntity.ok(productoService.actualizarProducto(id, request));
    }

    /** PATCH con el mismo comportamiento que PUT (paridad Django partial_update). */
    @PatchMapping("/{id}")
    public ResponseEntity<ProductoResponse> actualizarProductoParcial(
            @PathVariable String id,
            @Valid @RequestBody ProductoRequest request) {
        log.info("MongoDB REST PATCH: Actualizar producto ID {}", id);
        return ResponseEntity.ok(productoService.actualizarProducto(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarProducto(@PathVariable String id) {
        log.info("MongoDB REST DELETE: Soft delete producto ID {}", id);
        productoService.eliminarLogico(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/purgar")
    public ResponseEntity<Void> purgarProducto(@PathVariable String id) {
        log.info("MongoDB REST DELETE: Purga fisica del producto ID {}", id);
        productoService.purgarProducto(id);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Panel de administración
    //
    // El panel antes hablaba con Django para todo esto. Con el producto en
    // MongoDB, Django no puede resolver un ObjectId, así que estas rutas
    // viven en Spring. Mantener el mismo nombre conceptual de operación evita
    // rehacer la lógica de negocio de la UI.
    // ═══════════════════════════════════════════════════════════════════════

    /** GET /{id}/auditorias — historial del producto, del más nuevo al más viejo. */
    @GetMapping("/{id}/auditorias")
    public ResponseEntity<List<ProductoAuditoria>> listarAuditorias(@PathVariable String id) {
        return ResponseEntity.ok(productoService.listarAuditorias(id));
    }

    /** POST /{id}/desaprobar — body: {"motivo": "..."} */
    @PostMapping("/{id}/desaprobar")
    public ResponseEntity<ProductoResponse> desaprobar(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body) {
        String motivo = body == null ? null : body.get("motivo");
        return ResponseEntity.ok(productoService.desaprobar(id, motivo));
    }

    /** PATCH /{id}/activo — alterna el estado activo y devuelve el resultado. */
    @PatchMapping("/{id}/activo")
    public ResponseEntity<ProductoResponse> cambiarActivo(@PathVariable String id) {
        return ResponseEntity.ok(productoService.cambiarActivo(id));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Imágenes
    // ─────────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/imagenes")
    public ResponseEntity<ProductoImagenDTO> agregarImagen(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        String image = (String) body.get("image");
        // ausente o null = no principal. Solo se marca principal si el cliente
        // lo pide explícitamente, para no desplazar la portada sin querer.
        boolean esPrincipal = Boolean.TRUE.equals(body.get("esPrincipal"));
        ProductoImagenDTO dto = productoService.agregarImagen(id, image, esPrincipal);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /** Marca una imagen existente como portada del producto. */
    @PatchMapping("/{id}/imagenes/{imagenId}")
    public ResponseEntity<ProductoImagenDTO> marcarImagenPrincipal(
            @PathVariable String id, @PathVariable String imagenId) {
        return ResponseEntity.ok(productoService.marcarImagenPrincipal(id, imagenId));
    }

    @DeleteMapping("/{id}/imagenes/{imagenId}")
    public ResponseEntity<Void> eliminarImagen(
            @PathVariable String id, @PathVariable String imagenId) {
        productoService.eliminarImagen(id, imagenId);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Variantes
    // ─────────────────────────────────────────────────────────────────────

    /** POST crea; PUT actualiza. El id va en la ruta, no en el body. */
    @PostMapping("/{id}/variantes")
    public ResponseEntity<VarianteDTO> crearVariante(
            @PathVariable String id, @RequestBody VarianteDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productoService.guardarVariante(id, request, null));
    }

    @PutMapping("/{id}/variantes/{varianteId}")
    public ResponseEntity<VarianteDTO> actualizarVariante(
            @PathVariable String id, @PathVariable String varianteId,
            @RequestBody VarianteDTO request) {
        return ResponseEntity.ok(productoService.guardarVariante(id, request, varianteId));
    }

    @DeleteMapping("/{id}/variantes/{varianteId}")
    public ResponseEntity<Void> eliminarVariante(
            @PathVariable String id, @PathVariable String varianteId) {
        productoService.eliminarVariante(id, varianteId);
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

            log.info("MongoDB REST GET: Filtros search='{}' active={} approved={}",
                    searchFinal, activeFinal, approvedFinal);
            resultadoPage = productoService.listarConFiltros(
                    searchFinal, activeFinal, approvedFinal, min_price, max_price, pageable);
        } else if (nombre != null && !nombre.isBlank() && estado != null) {
            resultadoPage = productoService.buscarPorNombreYEstado(nombre, estado, pageable);
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
