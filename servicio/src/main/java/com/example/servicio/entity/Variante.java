package com.example.servicio.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad que mapea la tabla 'products_variant' de Django.
 * Comparte la misma base de datos que el backend Django.
 */
@Entity
@Table(name = "products_variant")
public class Variante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productoId;

    @Column(name = "size", length = 20)
    private String size;

    @Column(name = "color", length = 20)
    private String color;

    @Column(name = "color_hex", length = 7)
    private String colorHex;

    @Column(name = "color_nombre", length = 50)
    private String colorNombre;

    @Column(name = "stock")
    private Integer stock = 0;

    @Column(name = "price_variant", precision = 10, scale = 2)
    private BigDecimal priceVariant;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProductoId() { return productoId; }
    public void setProductoId(Long productoId) { this.productoId = productoId; }

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
}
