package com.example.servicio.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Documento MongoDB que representa una imagen de producto (coleccion
 * 'producto_imagenes').
 *
 * En la rama PostgreSQL esto mapeaba la tabla 'products_productimage' de
 * Django. En la rama java/mongoDB el producto vive en MongoDB, asi que sus
 * hijos tambien: no hay tabla que compartir y productoId es el ObjectId del
 * documento Producto, no un entero de PostgreSQL.
 *
 * Las imagenes se gestionan desde Django (subida a Cloudinary); el
 * microservicio solo las lee para enrichcer las respuestas y las borra en la
 * purga en cascada.
 */
@Document(collection = "producto_imagenes")
@CompoundIndex(name = "idx_imagen_producto", def = "{'productoId': 1, 'esPrincipal': -1}")
public class ProductoImagen {

    @Id
    private String id;

    /** ObjectId del documento Producto en la coleccion 'productos'. */
    private String productoId;

    /** public_id de Cloudinary. */
    private String image;

    private Boolean esPrincipal = false;

    private String cloudinaryUrl;

    private LocalDateTime createdAt;

    public ProductoImagen() {
    }

    public ProductoImagen(String productoId, String image, Boolean esPrincipal) {
        this.productoId = productoId;
        this.image = image;
        this.esPrincipal = esPrincipal;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProductoId() { return productoId; }
    public void setProductoId(String productoId) { this.productoId = productoId; }

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
