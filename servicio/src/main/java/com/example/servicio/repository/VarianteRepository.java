package com.example.servicio.repository;

import com.example.servicio.entity.Variante;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para la entidad Variante.
 * Mapea la tabla 'products_variant' compartida con Django.
 */
@Repository
public interface VarianteRepository extends JpaRepository<Variante, Long> {

    long countByProductoId(Long productoId);

    @Query("SELECT v.productoId, COUNT(v) FROM Variante v "
         + "WHERE v.productoId IN :ids GROUP BY v.productoId")
    List<Object[]> countPorProducto(@Param("ids") Collection<Long> ids);

    @Query("SELECT DISTINCT v.productoId FROM Variante v "
         + "WHERE v.productoId IN :ids AND v.stock > 0")
    Set<Long> findProductoIdsConStock(@Param("ids") Collection<Long> ids);

    @Query("SELECT v.productoId, COALESCE(SUM(v.stock), 0) FROM Variante v "
         + "WHERE v.productoId IN :ids GROUP BY v.productoId")
    List<Object[]> sumStockPorProducto(@Param("ids") Collection<Long> ids);

    /**
     * Borra fisicamente las variantes del producto (cascade de Django).
     *
     * Debe ejecutarse DESPUES de limpiar carts_cartitem, porque ese FK
     * (carts_cartitem.variant_id) tambien esta en ON DELETE NO ACTION.
     */
    @Modifying
    @Query("DELETE FROM Variante v WHERE v.productoId = :productoId")
    int deleteByProductoId(@Param("productoId") Long productoId);
}
