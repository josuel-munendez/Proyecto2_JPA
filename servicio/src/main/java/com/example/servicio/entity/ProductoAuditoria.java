package com.example.servicio.entity;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Documento MongoDB de auditoria de producto (coleccion 'producto_auditoria').
 *
 * En la rama PostgreSQL el microservicio insertaba filas en la tabla
 * 'products_productaudit' de Django con SQL nativo. En esta rama MongoDB no
 * hay tabla que compartir, asi que la auditoria es un documento propio con la
 * misma semantica: 'action', snapshot before/after y el producto al que
 * pertenece.
 *
 * Acciones (mismos valores que Django ProductAudit.ACTION_CHOICES):
 *   created, updated, published, disapproved, deleted
 *
 * Las tres ultimas ('published', 'disapproved', 'deleted') son las que hacen
 * elegible a un producto para la purga fisica.
 *
 * En la purga, productoId se pone a null en vez de borrar el documento
 * (paridad SET_NULL de Django): el historial sobrevive al producto.
 */
@Document(collection = "producto_auditoria")
@CompoundIndex(name = "idx_audit_producto", def = "{'productoId': 1, 'action': 1}")
public class ProductoAuditoria extends BaseEntity {

    public static final String ACTION_CREATED = "created";
    public static final String ACTION_UPDATED = "updated";
    public static final String ACTION_PUBLISHED = "published";
    public static final String ACTION_DISAPPROVED = "disapproved";
    public static final String ACTION_DELETED = "deleted";

    /**
     * Acciones que hacen elegible a un producto para la purga fisica.
     * Un producto PENDIENTE (nunca publicado ni descartado) queda protegido.
     */
    public static final List<String> ACCIONES_HISTORIAL = List.of(
            ACTION_PUBLISHED, ACTION_DISAPPROVED, ACTION_DELETED);

    @Id
    private String id;

    /** ObjectId del Producto. null tras una purga (SET_NULL). */
    @Indexed
    private String productoId;

    private String action;

    private String actor;

    private Map<String, Object> beforeData = new LinkedHashMap<>();

    private Map<String, Object> afterData = new LinkedHashMap<>();

    /** Motivo de la desaprobacion / nota de la accion. */
    private String motivo;

    public ProductoAuditoria() {
    }

    public ProductoAuditoria(String productoId, String action, String actor,
                             Map<String, Object> beforeData, Map<String, Object> afterData,
                             String motivo) {
        this.productoId = productoId;
        this.action = action;
        this.actor = actor;
        setBeforeData(beforeData);
        setAfterData(afterData);
        this.motivo = motivo;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProductoId() { return productoId; }
    public void setProductoId(String productoId) { this.productoId = productoId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public Map<String, Object> getBeforeData() { return beforeData; }
    public final void setBeforeData(Map<String, Object> beforeData) {
        this.beforeData = beforeData == null ? new LinkedHashMap<>() : beforeData;
    }

    public Map<String, Object> getAfterData() { return afterData; }
    public final void setAfterData(Map<String, Object> afterData) {
        this.afterData = afterData == null ? new LinkedHashMap<>() : afterData;
    }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    @Override
    public String toString() {
        return "ProductoAuditoria{id=" + id + ", productoId=" + productoId
                + ", action=" + action + '}';
    }
}
