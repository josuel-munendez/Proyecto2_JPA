package com.example.servicio.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.example.servicio.entity.Producto;
import com.example.servicio.entity.Producto.EstadoProducto;

/**
 * Repositorio Spring Data MongoDB para el documento Producto (coleccion
 * 'productos').
 *
 * Metodos declarados por derivacion: el nombre sigue el patron
 * findBy&lt;Campo&gt;&lt;Condicion&gt;, donde los campos son los del documento
 * (nombre, descripcion, precioBase, referencia, aprobado, isActive, estado).
 *
 * Los filtros combinables y los candidatos a purga estan en
 * ProductoRepositoryCustom, porque dependen de criteria que se arman en
 * runtime y no se pueden expresar por derivacion.
 */
@Repository
public interface ProductoRepository extends MongoRepository<Producto, String>, ProductoRepositoryCustom {

    Optional<Producto> findByReferencia(String referencia);

    boolean existsByNombre(String nombre);

    boolean existsByReferencia(String referencia);

    /**
     * Catalogo: visibles (activo o aprobado). Equivalente a
     * findByIsActiveTrueOrAprobadoTrue de la rama JPA.
     */
    Page<Producto> findByIsActiveTrueOrAprobadoTrue(Pageable pageable);

    Page<Producto> findByIsActiveTrue(Pageable pageable);

    Page<Producto> findByEstadoNot(EstadoProducto estado, Pageable pageable);

    /**
     * Detalle de un producto que no esté eliminado.
     *
     * El listado ya usa findByEstadoNot para esconder los borrados, pero
     * obtenerPorId iba con findById a secas y devolvía 200 con el producto
     * BORRADO: el listado lo ocultaba y, al abrir el detalle o editarlo, el
     * admin se encontraba el producto "vivo" otra vez y podía modificarlo.
     */
    Optional<Producto> findByIdAndEstadoNot(String id, EstadoProducto estado);

    Page<Producto> findByNombreContainingIgnoreCaseAndIsActive(
            String nombre, Boolean isActive, Pageable pageable);

    Page<Producto> findByNombreRegexAndEstado(
            String regexNombre, EstadoProducto estado, Pageable pageable);

    /**
     * Busqueda OR sobre nombre, descripcion y referencia.
     *
     * La implementacion vive en {@link ProductoRepositoryImpl} y no como
     * {@code @Query}derivada, para poder escapar los metacaracteres de regex del
     * termino que escribe el usuario. Ver {@link ProductoRepositoryCustom}.
     */

    List<Producto> findByIdIn(Collection<String> ids);

    /**
     * Candidatos brutos a purga automatica: soft delete (ambos flags false) y
     * antiguos. El filtro de "tiene historial publicacion/desaprobacion/delete"
     * se aplica despues en ProductoRepositoryImpl, porque necesita mirar la
     * coleccion de auditoria.
     */
    List<Producto> findByIsActiveFalseAndAprobadoFalse(Pageable pageable);
}
