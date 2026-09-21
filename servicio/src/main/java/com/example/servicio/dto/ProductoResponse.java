package com.example.servicio.dto;

import com.example.servicio.entity.Producto;
import com.example.servicio.entity.Producto.EstadoProducto;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductoResponse {
    private Long id;
    private String nombre;
    private String descripcion;
    private BigDecimal precioBase;
    private String referencia;
    private Boolean aprobado;
    private EstadoProducto estado;
    private Integer stock;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProductoResponse fromEntity(Producto producto) {
        ProductoResponse res = new ProductoResponse();
        res.setId(producto.getId());
        res.setNombre(producto.getNombre());
        res.setDescripcion(producto.getDescripcion());
        res.setPrecioBase(producto.getPrecioBase());
        res.setReferencia(producto.getReferencia());
        res.setAprobado(producto.getAprobado());
        res.setEstado(producto.getEstado());
        res.setStock(producto.getStock());
        res.setCreatedAt(producto.getCreatedAt());
        res.setUpdatedAt(producto.getUpdatedAt());
        return res;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
