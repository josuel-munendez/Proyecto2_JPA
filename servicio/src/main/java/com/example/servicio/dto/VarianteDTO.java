package com.example.servicio.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Variante de un producto tal y como vive en la coleccion MongoDB
 * {@code variantes}.
 *
 * Reemplaza a la tabla de variantes de Django en la rama java/mongoDB. Django
 * calculaba un precio efectivo con {@code price_variant ?? product.base_price};
 * aquí {@code precioEfectivo} replica esa regla para que el carrito y el
 * checkout vean el mismo número que antes.
 */
public class VarianteDTO {

    private String id;
    private String productoId;
    private String size;
    private String color;
    private String colorHex;
    private String colorNombre;
    private Integer stock;
    private BigDecimal priceVariant;

    /** priceVariant si tiene, y si no el precio base del producto. */
    private BigDecimal precioEfectivo;

    private LocalDateTime createdAt;

    public VarianteDTO() {
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

    public BigDecimal getPrecioEfectivo() { return precioEfectivo; }
    public void setPrecioEfectivo(BigDecimal precioEfectivo) { this.precioEfectivo = precioEfectivo; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
