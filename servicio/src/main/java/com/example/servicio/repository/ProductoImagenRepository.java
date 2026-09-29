package com.example.servicio.repository;

import com.example.servicio.entity.ProductoImagen;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para la entidad ProductoImagen.
 * Mapea la tabla 'products_productimage' compartida con Django.
 */
@Repository
public interface ProductoImagenRepository extends JpaRepository<ProductoImagen, Long> {

    /**
     * Trae las imagenes de un conjunto de productos, priorizando la principal.
     * Cada producto aparece como mucho una vez en la respuesta si se agrupa
     * por productoId (la principal va primero).
     */
    @Query("SELECT pi FROM ProductoImagen pi WHERE pi.productoId IN :ids "
         + "ORDER BY pi.esPrincipal DESC, pi.id ASC")
    List<ProductoImagen> findPrincipalesEn(@Param("ids") Collection<Long> ids);

    long countByProductoId(Long productoId);

    @Query("SELECT pi.productoId, COUNT(pi) FROM ProductoImagen pi "
         + "WHERE pi.productoId IN :ids GROUP BY pi.productoId")
    List<Object[]> countPorProducto(@Param("ids") Collection<Long> ids);

    @Query("SELECT DISTINCT pi.productoId FROM ProductoImagen pi "
         + "WHERE pi.productoId IN :ids AND pi.esPrincipal = true")
    java.util.Set<Long> findProductoIdsConPrincipal(@Param("ids") Collection<Long> ids);

    /**
     * Borra fisicamente las imagenes del producto (cascade de Django).
     *
     * Necesario antes del hard delete del producto: Django creo el FK
     * products_productimage.product_id con ON DELETE NO ACTION porque su
     * on_delete=models.CASCADE se aplica en el collector, no en la base de
     * datos. Sin este DELETE, PostgreSQL rechaza el borrado del producto con
     * DataIntegrityViolationException.
     */
    @Modifying
    @Query("DELETE FROM ProductoImagen pi WHERE pi.productoId = :productoId")
    int deleteByProductoId(@Param("productoId") Long productoId);
}