package com.example.servicio.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Imagen de un producto tal y como vive en la coleccion MongoDB
 * {@code producto_imagenes}.
 *
 * Reemplaza a la tabla de imágenes de Django en la rama java/mongoDB. Django
 * guarda además un {@code order} para ordenar la galería; aquí el orden es el
 * del propio documento, y {@code esPrincipal} cumple la función de la imagen
 * principal.
 */
public class ProductoImagenDTO {

    private String id;
    private String productoId;
    private String image;
    private Boolean esPrincipal;
    private String cloudinaryUrl;
    private LocalDateTime createdAt;

    public ProductoImagenDTO() {
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
}
