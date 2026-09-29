package com.example.servicio.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import com.example.servicio.entity.ProductoAuditoria;

/**
 * Repositorio Spring Data MongoDB de la auditoria de producto.
 *
 * En la rama JPA la auditoria vivia en la tabla 'products_productaudit' de
 * Django y el servicio la consultaba e insertaba con SQL nativo. Aqui es una
 * coleccion propia con la misma semantica.
 */
@Repository
public interface ProductoAuditoriaRepository extends MongoRepository<ProductoAuditoria, String> {

    List<ProductoAuditoria> findByProductoId(String productoId);

    /**
     * Acciones de historial de un producto, limitadas a las que lo hacen
     * elegible para purga (published / disapproved / deleted).
     */
    @Query("{ 'productoId': ?0, 'action': { $in: [ 'published', 'disapproved', 'deleted' ] } }")
    List<ProductoAuditoria> findHistorial(String productoId);

    @Query("{ 'productoId': { $in: ?0 }, 'action': { $in: [ 'published', 'disapproved', 'deleted' ] } }")
    List<ProductoAuditoria> findHistorialDe(Collection<String> productoIds);

    /**
     * Desvincula la auditoria antes de la purga fisica (paridad SET_NULL de
     * Django): el documento de auditoria sobrevive con productoId a null, para
     * que el historial no se pierda aunque el producto desaparezca.
     *
     * @Query define el filtro, @Update el cambio a aplicar.
     */
    @Query("{ 'productoId': ?0 }")
    @Update("{ '$set': { 'productoId': null } }")
    long desvincularProducto(String productoId);

    long deleteByProductoId(String productoId);
}
