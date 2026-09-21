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
 * Repositorio JPA para la entidad Producto con metodos personalizados.
 * Incluye busquedas por 2 campos (AND) y por 3 campos (OR), mas paginacion.
 */
@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    Optional<Producto> findByReferencia(String referencia);

    boolean existsByNombre(String nombre);

    boolean existsByReferencia(String referencia);

    /**
     * BUSQUEDA POR 2 CAMPOS CON OPERADOR AND.
     * Busca productos cuyo nombre contenga el termino (case-insensitive) Y tengan un estado especifico.
     */
    Page<Producto> findByNombreContainingIgnoreCaseAndEstado(
            String nombre, EstadoProducto estado, Pageable pageable);

    /**
     * BUSQUEDA POR 3 CAMPOS CON OPERADOR OR (JPQL personalizado).
     * Busca productos cuyo nombre, descripcion O referencia contengan el termino de busqueda.
     * Excluye productos con estado BORRADO.
     */
    @Query("SELECT p FROM Producto p WHERE " +
           "(LOWER(p.nombre) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.descripcion) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.referencia) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
           "p.estado <> :excluido")
    Page<Producto> buscarPor3CamposOr(@Param("query") String query,
                                      @Param("excluido") EstadoProducto excluido,
                                      Pageable pageable);

    /**
     * Paginacion por estado omitiendo productos borrados.
     */
    Page<Producto> findByEstadoNot(EstadoProducto estado, Pageable pageable);
}
