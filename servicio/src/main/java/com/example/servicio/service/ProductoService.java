package com.example.servicio.service;

import com.example.servicio.dto.ProductoRequest;
import com.example.servicio.dto.ProductoResponse;
import com.example.servicio.entity.Producto.EstadoProducto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface ProductoService {
    ProductoResponse crearProducto(ProductoRequest request);
    ProductoResponse obtenerPorId(Long id);
    ProductoResponse actualizarProducto(Long id, ProductoRequest request);
    void cambiarEstado(Long id, EstadoProducto nuevoEstado);
    void eliminarLogico(Long id);
    void purgarProducto(Long id);

    Page<ProductoResponse> listarPaginado(Pageable pageable);
    Page<ProductoResponse> buscarPorNombreYEstado(String nombre, EstadoProducto estado, Pageable pageable);
    Page<ProductoResponse> buscarPor3CamposOr(String query, Pageable pageable);

    /**
     * Listado con filtros combinables (paridad Django list/search):
     * search, is_active, is_approved, min_price, max_price.
     * Parámetros null = sin filtro.
     */
    Page<ProductoResponse> listarConFiltros(
            String search,
            Boolean isActive,
            Boolean isApproved,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable);
}
