package com.example.servicio.service.impl;

import com.example.servicio.dto.ProductoRequest;
import com.example.servicio.dto.ProductoResponse;
import com.example.servicio.entity.Producto;
import com.example.servicio.entity.Producto.EstadoProducto;
import com.example.servicio.exception.BusinessRuleException;
import com.example.servicio.exception.ResourceNotFoundException;
import com.example.servicio.repository.ProductoRepository;
import com.example.servicio.service.ProductoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ProductoServiceImpl implements ProductoService {

    private static final Logger log = LoggerFactory.getLogger(ProductoServiceImpl.class);
    private final ProductoRepository productoRepository;

    public ProductoServiceImpl(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @Override
    public ProductoResponse crearProducto(ProductoRequest request) {
        log.info("MongoDB: Creando nuevo producto con referencia: {}", request.getReferencia());

        if (productoRepository.existsByNombre(request.getNombre())) {
            log.warn("Intento fallido de creacion: El nombre '{}' ya existe en MongoDB", request.getNombre());
            throw new BusinessRuleException("Ya existe un producto con el nombre: " + request.getNombre());
        }

        if (productoRepository.existsByReferencia(request.getReferencia())) {
            log.warn("Intento fallido de creacion: La referencia '{}' ya existe en MongoDB", request.getReferencia());
            throw new BusinessRuleException("Ya existe un producto con la referencia: " + request.getReferencia());
        }

        Producto producto = new Producto();
        producto.setNombre(request.getNombre());
        producto.setDescripcion(request.getDescripcion());
        producto.setPrecioBase(request.getPrecioBase());
        producto.setReferencia(request.getReferencia());
        producto.setStock(request.getStock() != null ? request.getStock() : 0);
        producto.setEstado(EstadoProducto.ACTIVO);
        producto.setAprobado(true);
        producto.onCreate();

        Producto guardado = productoRepository.save(producto);
        log.info("Producto creado exitosamente en MongoDB con ID: {}", guardado.getId());
        return ProductoResponse.fromEntity(guardado);
    }

    @Override
    public ProductoResponse obtenerPorId(String id) {
        log.info("MongoDB: Buscando producto por ID: {}", id);
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Producto no encontrado en MongoDB con ID: {}", id);
                    return new ResourceNotFoundException("Producto no encontrado con ID: " + id);
                });
        return ProductoResponse.fromEntity(producto);
    }

    @Override
    public ProductoResponse actualizarProducto(String id, ProductoRequest request) {
        log.info("MongoDB: Actualizando producto ID: {}", id);
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        if (!producto.getNombre().equalsIgnoreCase(request.getNombre()) &&
                productoRepository.existsByNombre(request.getNombre())) {
            log.warn("Conflicto al actualizar: El nombre '{}' ya pertenece a otro producto", request.getNombre());
            throw new BusinessRuleException("Ya existe otro producto con el nombre: " + request.getNombre());
        }

        producto.setNombre(request.getNombre());
        producto.setDescripcion(request.getDescripcion());
        producto.setPrecioBase(request.getPrecioBase());
        if (request.getStock() != null) {
            producto.setStock(request.getStock());
        }
        producto.onUpdate();

        Producto actualizado = productoRepository.save(producto);
        log.info("Producto ID: {} actualizado correctamente en MongoDB", id);
        return ProductoResponse.fromEntity(actualizado);
    }

    @Override
    public void cambiarEstado(String id, EstadoProducto nuevoEstado) {
        log.info("MongoDB: Cambiando estado del producto ID: {} a {}", id, nuevoEstado);
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        producto.setEstado(nuevoEstado);
        producto.onUpdate();
        productoRepository.save(producto);
        log.info("Estado del producto ID: {} actualizado a {}", id, nuevoEstado);
    }

    @Override
    public void eliminarLogico(String id) {
        log.info("MongoDB: Realizando borrado logico del producto ID: {}", id);
        cambiarEstado(id, EstadoProducto.BORRADO);
    }

    @Override
    public Page<ProductoResponse> listarPaginado(Pageable pageable) {
        log.info("MongoDB: Listando productos paginados - Pagina: {}, Tamanio: {}",
                pageable.getPageNumber(), pageable.getPageSize());
        return productoRepository.findByEstadoNot(EstadoProducto.BORRADO, pageable)
                .map(ProductoResponse::fromEntity);
    }

    @Override
    public Page<ProductoResponse> buscarPorNombreYEstado(String nombre, EstadoProducto estado, Pageable pageable) {
        log.info("MongoDB: Busqueda AND (2 campos): nombre='{}', estado={}", nombre, estado);
        return productoRepository.findByNombreRegexAndEstado(".*" + nombre + ".*", estado, pageable)
                .map(ProductoResponse::fromEntity);
    }

    @Override
    public Page<ProductoResponse> buscarPor3CamposOr(String query, Pageable pageable) {
        log.info("MongoDB: Busqueda OR (3 campos) con termino regex: '{}'", query);
        return productoRepository.buscarPor3CamposOr(query, pageable)
                .map(ProductoResponse::fromEntity);
    }
}
