package com.example.servicio.service;

import com.example.servicio.dto.ProductoRequest;
import com.example.servicio.dto.ProductoResponse;
import com.example.servicio.entity.Producto.EstadoProducto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductoService {
    ProductoResponse crearProducto(ProductoRequest request);
    ProductoResponse obtenerPorId(Long id);
    ProductoResponse actualizarProducto(Long id, ProductoRequest request);
    void cambiarEstado(Long id, EstadoProducto nuevoEstado);
    void eliminarLogico(Long id);

    Page<ProductoResponse> listarPaginado(Pageable pageable);
    Page<ProductoResponse> buscarPorNombreYEstado(String nombre, EstadoProducto estado, Pageable pageable);
    Page<ProductoResponse> buscarPor3CamposOr(String query, Pageable pageable);
}
