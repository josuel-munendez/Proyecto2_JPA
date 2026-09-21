package com.example.servicio.repository;

import com.example.servicio.entity.Producto;
import com.example.servicio.entity.Producto.EstadoProducto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio Spring Data MongoDB para el documento Producto.
 * Incluye metodos personalizados, paginacion y consultas con regex para MongoDB.
 */
@Repository
public interface ProductoRepository extends MongoRepository<Producto, String> {

    Optional<Producto> findByReferencia(String referencia);

    boolean existsByNombre(String nombre);

    boolean existsByReferencia(String referencia);

    /**
     * BUSQUEDA POR 2 CAMPOS CON OPERADOR AND (MongoDB).
     * Busca productos cuyo nombre coincida (regex case-insensitive) Y tengan un estado especifico.
     */
    Page<Producto> findByNombreRegexAndEstado(
            String regexNombre, EstadoProducto estado, Pageable pageable);

    /**
     * BUSQUEDA POR 3 CAMPOS CON OPERADOR OR (MongoDB Query).
     * Busca productos cuyo nombre, descripcion O referencia coincidan con el termino de busqueda.
     * Excluye productos con estado BORRADO.
     */
    @Query("{ '$and': [ " +
           "  { '$or': [ " +
           "    { 'nombre': { '$regex': ?0, '$options': 'i' } }, " +
           "    { 'descripcion': { '$regex': ?0, '$options': 'i' } }, " +
           "    { 'referencia': { '$regex': ?0, '$options': 'i' } } " +
           "  ] }, " +
           "  { 'estado': { '$ne': 'BORRADO' } } " +
           "] }")
    Page<Producto> buscarPor3CamposOr(String regexQuery, Pageable pageable);

    /**
     * Paginacion por estado omitiendo productos borrados.
     */
    Page<Producto> findByEstadoNot(EstadoProducto estado, Pageable pageable);
}
