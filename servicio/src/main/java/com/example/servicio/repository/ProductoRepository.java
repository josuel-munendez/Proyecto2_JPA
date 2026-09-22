package com.example.servicio.repository;

import com.example.servicio.entity.Producto;
import com.example.servicio.entity.Producto.EstadoProducto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio JPA para la entidad Producto.
 * Mapea la tabla 'products_product' compartida con Django.
 */
@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    Optional<Producto> findByReferencia(String referencia);

    boolean existsByNombre(String nombre);

    boolean existsByReferencia(String referencia);

    /**
     * Busca productos por nombre (case-insensitive) y que estén activos.
     */
    Page<Producto> findByNombreContainingIgnoreCaseAndIsActive(
            String nombre, Boolean isActive, Pageable pageable);

    /**
     * Búsqueda OR en nombre, descripcion y referencia.
     * Excluye productos borrados (is_active=false AND is_approved=false).
     */
    @Query("SELECT p FROM Producto p WHERE " +
           "(LOWER(p.nombre) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.descripcion) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.referencia) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
           "NOT (p.isActive = false AND p.aprobado = false)")
    Page<Producto> buscarPor3CamposOr(@Param("query") String query, Pageable pageable);

    /**
     * Lista todos los productos que NO están borrados.
     * BORRADO = is_active=false AND is_approved=false.
     */
    Page<Producto> findByIsActiveTrueOrAprobadoTrue(Pageable pageable);

    /**
     * Lista solo productos activos (para catálogo público).
     */
    Page<Producto> findByIsActiveTrue(Pageable pageable);
}
