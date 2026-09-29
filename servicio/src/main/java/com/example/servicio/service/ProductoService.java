package com.example.servicio.service;

import com.example.servicio.dto.ProductoRequest;
import com.example.servicio.dto.ProductoResponse;
import com.example.servicio.entity.Producto.EstadoProducto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import com.example.servicio.entity.ProductoAuditoria;
import com.example.servicio.dto.ProductoImagenDTO;
import com.example.servicio.dto.VarianteDTO;

public interface ProductoService {
    ProductoResponse crearProducto(ProductoRequest request);
    ProductoResponse obtenerPorId(String id);
    ProductoResponse actualizarProducto(String id, ProductoRequest request);
    void cambiarEstado(String id, EstadoProducto nuevoEstado);
    void eliminarLogico(String id);
    void purgarProducto(String id);

    // ─────────────────────────────────────────────────────────────────────
    // Panel de administración
    //
    // Todos estos reemplazan endpoints que en la rama PostgreDB colgaban de
    // Django. Con productos en MongoDB, Django no puede resolver el id del
    // producto, así que cada operación que el panel necesita vive en Spring,
    // que es quien tiene el documento.
    // ─────────────────────────────────────────────────────────────────────

    /** Historial de auditoría del producto, del más reciente al más antiguo. */
    List<ProductoAuditoria> listarAuditorias(String id);

    /**
     * Desaprueba el producto: lo desactiva y lo deja pendiente de aprobación.
     *
     * @param motivo motivo de la desaprobación, queda en la auditoría.
     */
    ProductoResponse desaprobar(String id, String motivo);

    /**
     * Alterna isActive y devuelve el producto ya actualizado, para que el
     * frontend no tenga que adivinar el estado resultante.
     */
    ProductoResponse cambiarActivo(String id);

    ProductoImagenDTO agregarImagen(String id, String image, boolean esPrincipal);
    void eliminarImagen(String id, String imagenId);

    /** Promueve una imagen ya existente a principal, desmocando las demás. */
    ProductoImagenDTO marcarImagenPrincipal(String id, String imagenId);

    /** @param varianteId null para crear, o el id para actualizar. */
    VarianteDTO guardarVariante(String id, VarianteDTO request, String varianteId);
    void eliminarVariante(String id, String varianteId);

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
