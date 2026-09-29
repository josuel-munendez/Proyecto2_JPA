package com.example.servicio.repository;

import com.example.servicio.entity.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
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

    /**
     * Candidatos seguros a purga automática: soft-delete (ambos flags false)
     * con historial de publicación, desaprobación o soft delete en auditoría Django.
     * Protege los productos PENDIENTES (ambos false sin auditoría).
     */
    @Query(value = """
            SELECT p.* FROM products_product p
            WHERE p.is_active = false
              AND p.is_approved = false
              AND p.updated_at < :cutoff
              AND EXISTS (
                  SELECT 1 FROM products_productaudit a
                  WHERE a.product_id = p.id
                    AND a.action IN ('published', 'disapproved', 'deleted')
              )
            """, nativeQuery = true)
    List<Producto> findPurgeCandidates(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Filtros combinables del listado (paridad con Django ProductViewSet):
     * search (nombre/descripcion/referencia), is_active, is_approved, min/max precio.
     * Parámetros null = sin filtro.
     */
    @Query("""
            SELECT p FROM Producto p
            WHERE (:search IS NULL OR :search = ''
                   OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(p.descripcion) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(p.referencia) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:isActive IS NULL OR p.isActive = :isActive)
              AND (:isApproved IS NULL OR p.aprobado = :isApproved)
              AND (:minPrice IS NULL OR p.precioBase >= :minPrice)
              AND (:maxPrice IS NULL OR p.precioBase <= :maxPrice)
            """)
    Page<Producto> buscarConFiltros(
            @Param("search") String search,
            @Param("isActive") Boolean isActive,
            @Param("isApproved") Boolean isApproved,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable);

    /**
     * Historial de auditoría del producto (disapproved / published / deleted).
     * Paridad con la regla de hard delete de Django.
     */
    @Query(value = """
            SELECT action FROM products_productaudit
            WHERE product_id = :id
              AND action IN ('disapproved', 'published', 'deleted')
            """, nativeQuery = true)
    List<String> findAuditActions(@Param("id") Long id);

    /**
     * Batch de flags de auditoría para una página de productos.
     * Retorna filas [productId, action].
     */
    @Query(value = """
            SELECT product_id, action FROM products_productaudit
            WHERE product_id IN (:ids)
              AND action IN ('disapproved', 'published', 'deleted')
            """, nativeQuery = true)
    List<Object[]> findAuditFlags(@Param("ids") Collection<Long> ids);

    /**
     * Registra una entrada de auditoría compatible con Django ProductAudit.
     */
    @Modifying
    @Query(value = """
            INSERT INTO products_productaudit
                (product_id, action, actor, before_data, after_data, motivo, created_at)
            VALUES
                (:productId, :action, :actor,
                 CAST(:beforeData AS jsonb),
                 CAST(:afterData AS jsonb),
                 :motivo,
                 NOW())
            """, nativeQuery = true)
    void insertAudit(
            @Param("productId") Long productId,
            @Param("action") String action,
            @Param("actor") String actor,
            @Param("beforeData") String beforeData,
            @Param("afterData") String afterData,
            @Param("motivo") String motivo);

    /**
     * Desvincula la auditoría antes del hard delete (paridad SET_NULL de Django).
     * Necesario si la BD aún tiene ON DELETE RESTRICT en el FK.
     */
    @Modifying
    @Query(value = """
            UPDATE products_productaudit SET product_id = NULL
            WHERE product_id = :productId
            """, nativeQuery = true)
    void nullifyAuditRefs(@Param("productId") Long productId);

    // ========================================================================
    // Cascada de limpieza previa al hard delete.
    //
    // Django creo TODOS estos FK con ON DELETE NO ACTION: su on_delete se
    // aplica en el collector de Django, no en la base de datos. Por eso el
    // microservicio tiene que replicar el collector a mano, o PostgreSQL
    // rechaza el DELETE del producto con DataIntegrityViolationException.
    //
    // Estas tablas no tienen entidad mapeada (las gestiona Django), asi que
    // se resuelven con SQL nativo en lugar de derived deletes.
    // ========================================================================

    /** Reseñas del producto (Django: on_delete=models.CASCADE). */
    @Modifying
    @Query(value = "DELETE FROM products_review WHERE product_id = :productId",
           nativeQuery = true)
    int deleteReviews(@Param("productId") Long productId);

    /** Motivos de desaprobacion (hijo del producto, se va con el). */
    @Modifying
    @Query(value = "DELETE FROM products_motivodesaprobacion WHERE product_id = :productId",
           nativeQuery = true)
    int deleteMotivosDesaprobacion(@Param("productId") Long productId);

    /** Vínculos producto-categoría del catálogo (tabla intermedia M2M). */
    @Modifying
    @Query(value = "DELETE FROM catalog_productcategory WHERE product_id = :productId",
           nativeQuery = true)
    int deleteCategorias(@Param("productId") Long productId);

    /**
     * Ítems de carrito (Django: on_delete=models.CASCADE).
     *
     * Debe ejecutarse ANTES de borrar products_variant, porque
     * carts_cartitem.variant_id también está en ON DELETE NO ACTION.
     */
    @Modifying
    @Query(value = "DELETE FROM carts_cartitem WHERE product_id = :productId",
           nativeQuery = true)
    int deleteCarritoItems(@Param("productId") Long productId);

    /**
     * Red de seguridad para las órdenes: comprueba en la propia base si el
     * producto aparece en alguna linea de orden.
     *
     * No replica el SET_NULL de Django a propósito — el servicio aplica una
     * regla de negocio más estricta (no purgar productos con historial de
     * ventas). Si el chequeo a Django y esta consulta discrepan, no se nullan
     * las lineas en silencio: se rechaza la purga.
     */
    @Query(value = """
            SELECT COUNT(*) FROM orders_orderitem WHERE product_id = :productId
            """, nativeQuery = true)
    long countOrderItems(@Param("productId") Long productId);
}
