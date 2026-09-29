package com.example.servicio.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.example.servicio.entity.ProductoImagen;

/**
 * Repositorio Spring Data MongoDB para los documentos de imagen.
 *
 * Sustituye a los derived deletes de la rama JPA: en MongoDB no hay FK que
 * obeyecer, la relacion es el campo productoId y se borra por criterio.
 */
@Repository
public interface ProductoImagenRepository extends MongoRepository<ProductoImagen, String> {

    List<ProductoImagen> findByProductoId(String productoId);

    /**
     * Galeria de un producto en orden de insercion, para el detalle.
     *
     * El orden importa porque en Django la galería se ordenaba por un campo
     * `order` explicito; en MongoDB createdAt hace ese papel, así que la
     * galería sale en el mismo orden que antes sin campo extra.
     */
    List<ProductoImagen> findByProductoIdOrderByCreatedAtAsc(String productoId);

    /** Imagenes marcadas como principales de un producto. */
    List<ProductoImagen> findByProductoIdAndEsPrincipalTrue(String productoId);

    List<ProductoImagen> findByProductoIdIn(Collection<String> productoIds);

    List<ProductoImagen> findByProductoIdInAndEsPrincipalTrue(Collection<String> productoIds);

    Optional<ProductoImagen> findFirstByProductoIdAndEsPrincipalTrue(String productoId);

    long countByProductoId(String productoId);

    long countByProductoIdIn(Collection<String> productoIds);

    /**
     * Borra las imagenes de un producto. Devuelve cuantas se fueron.
     * Es el equivalente en MongoDB del DELETE en cascada de la rama JPA.
     */
    long deleteByProductoId(String productoId);
}
