package com.example.servicio.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.servicio.entity.Producto;

/**
 * Operaciones de ProductoRepository que no se pueden expresar por derivacion
 * porque los criterios se arman en runtime (filtros opcionales) o porque
 * necesitan consultar otra coleccion (auditoria).
 */
public interface ProductoRepositoryCustom {

    /**
     * Filtros combinables del listado, con paridad de la rama JPA.
     * Un parametro null o vacio significa "sin filtro":
     *   search     -> nombre, descripcion o referencia contienen el termino
     *   isActive   -> bandera de visibilidad
     *   isApproved -> bandera de aprobacion
     *   minPrice   -> precioBase >=
     *   maxPrice   -> precioBase <=
     */
    Page<Producto> buscarConFiltros(
            String search,
            Boolean isActive,
            Boolean isApproved,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable);

    /**
     * Candidatos seguros a purga automatica: soft delete (isActive=false y
     * aprobado=false) anterior a `cutoff` Y con historial de publicacion,
     * desaprobacion o soft delete en la auditoria.
     *
     * La segunda condicion protege los productos PENDIENTES: un producto
     * creado y nunca publicado ni descartado se queda en el catalogo, igual
     * que en la rama JPA.
     */
    List<Producto> findPurgeCandidates(LocalDateTime cutoff);

    /**
     * Busqueda OR sobre nombre, descripcion y referencia, case-insensitive,
     * restringida a los productos visibles (isActive o aprobado).
     *
     * El termino se trata como TEXTO LITERAL: se escapan los metacaracteres de
     * regex antes de mandarlo a {@code $regex}. Vive aqui, y no como
     * {@code @Query}, precisamente para compartir el escapado con
     * {@link #buscarConFiltros}: dejarla como query derivada hacia que el
     * usuario escribiera regex sin querer (" .* " devolvia todo el catalogo) y
     * que un patron sin cerrar rompiera la consulta con un error de MongoDB.
     */
    Page<Producto> buscarPor3CamposOr(String termino, Pageable pageable);
}
