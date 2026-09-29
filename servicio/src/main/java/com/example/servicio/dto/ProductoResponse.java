package com.example.servicio.dto;

import com.example.servicio.entity.Producto;
import com.example.servicio.entity.Producto.EstadoProducto;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductoResponse {
    private Long id;
    private Long version;
    private String nombre;
    private String descripcion;
    private BigDecimal precioBase;
    private String referencia;
    private Boolean aprobado;
    private EstadoProducto estado;
    private Boolean isActive;
    private Integer stock;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String mainImage;
    private Long imagesCount;
    private Long variantsCount;
    private Integer totalStock;
    private Boolean readyToPublish;
    private Boolean wasDisapproved;
    private Boolean wasPublished;
    private Boolean wasDeleted;

    public static ProductoResponse fromEntity(Producto producto) {
        return fromEntity(producto, null);
    }

    public static ProductoResponse fromEntity(Producto producto, String mainImage) {
        ProductoResponse res = new ProductoResponse();
        res.setId(producto.getId());
        res.setVersion(producto.getVersion());
        res.setNombre(producto.getNombre());
        res.setDescripcion(producto.getDescripcion());
        res.setPrecioBase(producto.getPrecioBase());
        res.setReferencia(producto.getReferencia());
        res.setAprobado(producto.getAprobado());
        res.setEstado(producto.getEstado());
        res.setIsActive(producto.getIsActive());
        res.setStock(producto.getStock());
        res.setCreatedAt(producto.getCreatedAt());
        res.setUpdatedAt(producto.getUpdatedAt());
        res.setMainImage(mainImage);
        res.setWasDisapproved(false);
        res.setWasPublished(false);
        res.setWasDeleted(false);
        res.setReadyToPublish(false);
        res.setImagesCount(0L);
        res.setVariantsCount(0L);
        res.setTotalStock(producto.getStock() != null ? producto.getStock() : 0);
        return res;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public BigDecimal getPrecioBase() { return precioBase; }
    public void setPrecioBase(BigDecimal precioBase) { this.precioBase = precioBase; }
    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }
    public Boolean getAprobado() { return aprobado; }
    public void setAprobado(Boolean aprobado) { this.aprobado = aprobado; }
    public EstadoProducto getEstado() { return estado; }
    public void setEstado(EstadoProducto estado) { this.estado = estado; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getMainImage() { return mainImage; }
    public void setMainImage(String mainImage) { this.mainImage = mainImage; }

    public Long getImagesCount() { return imagesCount; }
    public void setImagesCount(Long imagesCount) { this.imagesCount = imagesCount; }

    public Long getVariantsCount() { return variantsCount; }
    public void setVariantsCount(Long variantsCount) { this.variantsCount = variantsCount; }

    public Integer getTotalStock() { return totalStock; }
    public void setTotalStock(Integer totalStock) { this.totalStock = totalStock; }

    public Boolean getReadyToPublish() { return readyToPublish; }
    public void setReadyToPublish(Boolean readyToPublish) { this.readyToPublish = readyToPublish; }

    public Boolean getWasDisapproved() { return wasDisapproved; }
    public void setWasDisapproved(Boolean wasDisapproved) { this.wasDisapproved = wasDisapproved; }

    public Boolean getWasPublished() { return wasPublished; }
    public void setWasPublished(Boolean wasPublished) { this.wasPublished = wasPublished; }

    public Boolean getWasDeleted() { return wasDeleted; }
    public void setWasDeleted(Boolean wasDeleted) { this.wasDeleted = wasDeleted; }
}
