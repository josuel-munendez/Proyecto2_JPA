package com.example.servicio.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad que mapea la tabla 'products_productimage' de Django.
 * Comparte la misma base de datos que el backend Django.
 *
 * Columnas compartidas con Django:
 *   id, product_id, image (public_id de Cloudinary), is_main,
 *   cloudinary_url, created_at
 *
 * Solo lectura: las imagenes se gestionan desde Django.
 */
@Entity
@Table(name = "products_productimage")
public class ProductoImagen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productoId;

    @Column(name = "image")
    private String image;

    @Column(name = "is_main")
    private Boolean esPrincipal = false;

    @Column(name = "cloudinary_url")
    private String cloudinaryUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProductoId() { return productoId; }
    public void setProductoId(Long productoId) { this.productoId = productoId; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public Boolean getEsPrincipal() { return esPrincipal; }
    public void setEsPrincipal(Boolean esPrincipal) { this.esPrincipal = esPrincipal; }

    public String getCloudinaryUrl() { return cloudinaryUrl; }
    public void setCloudinaryUrl(String cloudinaryUrl) { this.cloudinaryUrl = cloudinaryUrl; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "ProductoImagen{id=" + id + ", productoId=" + productoId
                + ", esPrincipal=" + esPrincipal + '}';
    }
}