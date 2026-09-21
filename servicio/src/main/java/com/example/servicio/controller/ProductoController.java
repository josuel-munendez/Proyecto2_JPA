package com.example.servicio.controller;

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

/**
 * Controlador REST para MongoDB - CRUD y paginacion de Productos.
 * Retorna codigos de estado HTTP claros (200, 201, 204, 400, 404, 429, 500).
 */
@RestController
@RequestMapping("/api/v1/productos")
public class ProductoController {

    private static final Logger log = LoggerFactory.getLogger(ProductoController.class);
    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @PostMapping
    public ResponseEntity<ProductoResponse> crearProducto(@Valid @RequestBody ProductoRequest request) {
        log.info("MongoDB REST POST: Crear producto - referencia={}", request.getReferencia());
        ProductoResponse productoCreado = productoService.crearProducto(request);
        log.info("MongoDB REST POST: Producto creado exitosamente - id={}", productoCreado.getId());
        return new ResponseEntity<>(productoCreado, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductoResponse> obtenerPorId(@PathVariable String id) {
        log.info("MongoDB REST GET: Obtener producto ID {}", id);
        ProductoResponse producto = productoService.obtenerPorId(id);
        return ResponseEntity.ok(producto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductoResponse> actualizarProducto(
            @PathVariable String id,
            @Valid @RequestBody ProductoRequest request) {
        log.info("MongoDB REST PUT: Actualizar producto ID {}", id);
        ProductoResponse actualizado = productoService.actualizarProducto(id, request);
        log.info("MongoDB REST PUT: Producto ID {} actualizado correctamente", id);
        return ResponseEntity.ok(actualizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarProducto(@PathVariable String id) {
        log.info("MongoDB REST DELETE: Eliminar producto ID {}", id);
        productoService.eliminarLogico(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<ProductoPageResponse> listarProductos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) EstadoProducto estado,
            @RequestParam(required = false) String search) {

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ProductoResponse> resultadoPage;

        if (nombre != null && !nombre.isBlank() && estado != null) {
            log.info("MongoDB REST GET: Busqueda AND - nombre='{}', estado={}", nombre, estado);
            resultadoPage = productoService.buscarPorNombreYEstado(nombre, estado, pageable);
        } else if (search != null && !search.isBlank()) {
            log.info("MongoDB REST GET: Busqueda OR (3 campos) - search='{}'", search);
            resultadoPage = productoService.buscarPor3CamposOr(search, pageable);
        } else {
            resultadoPage = productoService.listarPaginado(pageable);
        }

        return ResponseEntity.ok(ProductoPageResponse.fromPage(resultadoPage));
    }
}
