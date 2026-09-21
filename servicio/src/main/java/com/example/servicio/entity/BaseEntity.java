package com.example.servicio.entity;

import java.time.LocalDateTime;

/**
 * Clase base abstracta para documentos MongoDB que requieren auditoria de fechas.
 * Proporciona campos de creacion y actualizacion automaticos.
 * MongoDB no tiene @PrePersist/@PreUpdate, por lo que se llaman manualmente desde el Service.
 */
public abstract class BaseEntity {

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Establece ambas fechas de auditoria con la fecha y hora actual.
     * Se llama manualmente al crear un documento nuevo.
     */
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Actualiza la fecha de modificacion con la fecha y hora actual.
     * Se llama manualmente al actualizar un documento existente.
     */
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
