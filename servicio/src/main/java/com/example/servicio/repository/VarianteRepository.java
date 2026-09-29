package com.example.servicio.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.example.servicio.entity.Variante;

/**
 * Repositorio Spring Data MongoDB para los documentos de variante.
 */
@Repository
public interface VarianteRepository extends MongoRepository<Variante, String> {

    List<Variante> findByProductoId(String productoId);

    /** Variantes de un producto en orden estable, para el detalle. */
    List<Variante> findByProductoIdOrderByIdAsc(String productoId);

    List<Variante> findByProductoIdIn(Collection<String> productoIds);

    long countByProductoId(String productoId);

    long countByProductoIdIn(Collection<String> productoIds);

    /** Variantes con existencias: las que aportan al total_stock. */
    List<Variante> findByProductoIdInAndStockGreaterThan(Collection<String> productoIds, Integer stock);

    /**
     * Borra las variantes de un producto. Devuelve cuantas se fueron.
     * Equivalente en MongoDB al DELETE en cascada de la rama JPA.
     */
    long deleteByProductoId(String productoId);
}
