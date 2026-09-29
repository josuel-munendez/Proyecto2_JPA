package com.example.servicio.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Documento MongoDB que representa una variante de producto (coleccion
 * 'variantes').
 *
 * Contraparte de la tabla 'products_variant' de Django en la rama
 * PostgreSQL. Aqui el producto vive en MongoDB, asi que productoId es su
 * ObjectId y no un entero.
 */
@Document(collection = "variantes")
@CompoundIndex(name = "idx_variante_producto", def = "{'productoId': 1}")
public class Variante {

    @Id
    private String id;

    /** ObjectId del documento Producto en la coleccion 'productos'. */
    private String productoId;

    private String size;
    private String color;
    private String colorHex;
    private String colorNombre;
    private Integer stock = 0;
    private BigDecimal priceVariant;
    private LocalDateTime createdAt;

    public Variante() {
    }

    public Variante(String productoId, String size, String color, Integer stock) {
        this.productoId = productoId;
        this.size = size;
        this.color = color;
        this.stock = stock;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProductoId() { return productoId; }
    public void setProductoId(String productoId) { this.productoId = productoId; }

    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }

    public String getColorNombre() { return colorNombre; }
    public void setColorNombre(String colorNombre) { this.colorNombre = colorNombre; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public BigDecimal getPriceVariant() { return priceVariant; }
    public void setPriceVariant(BigDecimal priceVariant) { this.priceVariant = priceVariant; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Variante{id=" + id + ", productoId=" + productoId
                + ", size=" + size + ", color=" + color + ", stock=" + stock + '}';
    }
}
