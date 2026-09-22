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
        log.info("JPA: Creando nuevo producto con referencia: {}", request.getReferencia());

        if (productoRepository.existsByNombre(request.getNombre())) {
            log.warn("Intento fallido de creacion: El nombre '{}' ya existe", request.getNombre());
            throw new BusinessRuleException("Ya existe un producto con el nombre: " + request.getNombre());
        }

        if (request.getReferencia() != null && productoRepository.existsByReferencia(request.getReferencia())) {
            log.warn("Intento fallido de creacion: La referencia '{}' ya existe", request.getReferencia());
            throw new BusinessRuleException("Ya existe un producto con la referencia: " + request.getReferencia());
        }

        Producto producto = new Producto();
        producto.setNombre(request.getNombre());
        producto.setDescripcion(request.getDescripcion());
        producto.setPrecioBase(request.getPrecioBase());
        producto.setReferencia(request.getReferencia());
        producto.setStock(request.getStock() != null ? request.getStock() : 0);
        producto.setEstado(EstadoProducto.ACTIVO);

        Producto guardado = productoRepository.save(producto);
        log.info("Producto creado exitosamente con JPA - ID: {}", guardado.getId());
        return ProductoResponse.fromEntity(guardado);
    }

    @Override
    public ProductoResponse obtenerPorId(Long id) {
        log.info("JPA: Buscando producto por ID: {}", id);
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Producto no encontrado con ID: {}", id);
                    return new ResourceNotFoundException("Producto no encontrado con ID: " + id);
                });
        return ProductoResponse.fromEntity(producto);
    }

    @Override
    public ProductoResponse actualizarProducto(Long id, ProductoRequest request) {
        log.info("JPA: Actualizando producto ID: {}", id);
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
        if (request.getReferencia() != null) {
            producto.setReferencia(request.getReferencia());
        }
        if (request.getStock() != null) {
            producto.setStock(request.getStock());
        }

        Producto actualizado = productoRepository.save(producto);
        log.info("Producto ID: {} actualizado correctamente con JPA", id);
        return ProductoResponse.fromEntity(actualizado);
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

    @Override
    public void eliminarLogico(Long id) {
        log.info("JPA: Realizando borrado logico del producto ID: {}", id);
        cambiarEstado(id, EstadoProducto.BORRADO);
    }

    @Override
    public Page<ProductoResponse> listarPaginado(Pageable pageable) {
        log.info("JPA: Listando productos paginados - Pagina: {}, Tamanio: {}",
                pageable.getPageNumber(), pageable.getPageSize());
        return productoRepository.findByIsActiveTrueOrAprobadoTrue(pageable)
                .map(ProductoResponse::fromEntity);
    }

    @Override
    public Page<ProductoResponse> buscarPorNombreYEstado(String nombre, EstadoProducto estado, Pageable pageable) {
        log.info("JPA: Busqueda AND (2 campos): nombre='{}', estado={}", nombre, estado);
        Boolean isActive = (estado == EstadoProducto.ACTIVO);
        return productoRepository.findByNombreContainingIgnoreCaseAndIsActive(nombre, isActive, pageable)
                .map(ProductoResponse::fromEntity);
    }

    @Override
    public Page<ProductoResponse> buscarPor3CamposOr(String query, Pageable pageable) {
        log.info("JPA: Busqueda OR (3 campos) con termino: '{}'", query);
        return productoRepository.buscarPor3CamposOr(query, pageable)
                .map(ProductoResponse::fromEntity);
    }
}
