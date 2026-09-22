package com.example.servicio.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Entidad que mapea la tabla 'products_product' de Django.
 * Comparte la misma base de datos que el backend Django.
 *
 * Columnas compartidas con Django:
 *   id, name, description, base_price, referencia, stock,
 *   is_active, is_approved, created_at, updated_at
 *
 * Soft delete (BORRADO): se representa como is_active=false + is_approved=false.
 */
@Entity
@Table(name = "products_product")
public class Producto extends BaseEntity {

    public enum EstadoProducto {
        ACTIVO,
        INACTIVO,
        BORRADO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre del producto es obligatorio")
    @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
    @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "El nombre no puede contener caracteres de control")
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String nombre;

    @Size(max = 500, message = "La descripcion no puede superar 500 caracteres")
    @Column(name = "description", length = 500)
    private String descripcion;

    @NotNull(message = "El precio base es obligatorio")
    @DecimalMin(value = "50.0", message = "El precio minimo es $50 COP (debe ser multiplo de 50)")
    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioBase;

    @Pattern(regexp = "^[A-Z0-9\\-]{3,20}$", message = "La referencia debe ser alfanumerica (3-20 caracteres, mayusculas, numeros o guiones)")
    @Column(name = "referencia", length = 20)
    private String referencia;

    @Column(name = "stock", nullable = false)
    private Integer stock = 0;

    @Column(name = "is_approved", nullable = false)
    private Boolean aprobado = false;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // === Constructores ===

    public Producto() {
    }

    public Producto(Long id, String nombre, String descripcion, BigDecimal precioBase,
            String referencia, Boolean aprobado, Boolean isActive, Integer stock) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precioBase = precioBase;
        this.referencia = referencia;
        this.aprobado = aprobado;
        this.isActive = isActive;
        this.stock = stock;
    }

    // === Getters y Setters ===

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

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public Boolean getAprobado() { return aprobado; }
    public void setAprobado(Boolean aprobado) { this.aprobado = aprobado; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    // === Estado derivado (compatibilidad con la API existente) ===

    /**
     * Deriva el estado del producto a partir de is_active e is_approved.
     * is_active=true  → ACTIVO
     * is_active=false, is_approved=true  → INACTIVO
     * is_active=false, is_approved=false → BORRADO (soft delete)
     */
    public EstadoProducto getEstado() {
        if (Boolean.TRUE.equals(isActive)) {
            return EstadoProducto.ACTIVO;
        }
        if (Boolean.TRUE.equals(aprobado)) {
            return EstadoProducto.INACTIVO;
        }
        return EstadoProducto.BORRADO;
    }

    /**
     * Establece el estado del producto mapeando a is_active e is_approved.
     */
    public void setEstado(EstadoProducto estado) {
        switch (estado) {
            case ACTIVO:
                this.isActive = true;
                this.aprobado = true;
                break;
            case INACTIVO:
                this.isActive = false;
                this.aprobado = true;
                break;
            case BORRADO:
                this.isActive = false;
                this.aprobado = false;
                break;
        }
    }

    // === Reglas de negocio ===

    public boolean esPrecioCopValido() {
        if (precioBase == null) return false;
        return precioBase.compareTo(BigDecimal.valueOf(50)) >= 0
                && precioBase.remainder(BigDecimal.valueOf(50)).compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean puedePublicarse() {
        return nombre != null && !nombre.isBlank()
                && precioBase != null && esPrecioCopValido()
                && referencia != null && !referencia.isBlank()
                && stock != null && stock > 0;
    }

    @Override
    public String toString() {
        return "Producto{id=" + id + ", nombre='" + nombre + "', precioBase=" + precioBase
                + ", referencia='" + referencia + "', isActive=" + isActive
                + ", aprobado=" + aprobado + '}';
    }
}
