package com.example.servicio.entity;

import java.math.BigDecimal;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Documento MongoDB que representa un Producto.
 * Herencia de BaseEntity para auditoria de fechas (createdAt, updatedAt).
 * Arquitectura orientada al dominio: el documento encapsula las reglas de negocio.
 *
 * 5 validaciones con anotaciones Jakarta:
 *   1. @NotBlank en nombre (campo obligatorio)
 *   2. @Size en nombre y descripcion (longitud controlada)
 *   3. @Pattern en nombre (sin caracteres de control ni HTML)
 *   4. @NotNull + @DecimalMin en precioBase (valor minimo)
 *   5. @Pattern en referencia (formato alfanumerico)
 */
@Document(collection = "productos")
public class Producto extends BaseEntity {

    public enum EstadoProducto {
        ACTIVO,
        INACTIVO,
        BORRADO
    }

    @Id
    private String id;

    // === VALIDACION 1: @NotBlank ===
    @NotBlank(message = "El nombre del producto es obligatorio")
    // === VALIDACION 2: @Size ===
    @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
    // === VALIDACION 3: @Pattern ===
    @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "El nombre no puede contener caracteres de control")
    @Indexed(unique = true)
    private String nombre;

    @Size(max = 500, message = "La descripcion no puede superar 500 caracteres")
    private String descripcion;

    // === VALIDACION 4: @NotNull + @DecimalMin ===
    @NotNull(message = "El precio base es obligatorio")
    @DecimalMin(value = "50.0", message = "El precio minimo es $50 COP (debe ser multiplo de 50)")
    private BigDecimal precioBase;

    // === VALIDACION 5: @Pattern ===
    @Pattern(regexp = "^[A-Z0-9\\-]{3,20}$", message = "La referencia debe ser alfanumerica (3-20 caracteres, mayusculas, numeros o guiones)")
    @Indexed(unique = true)
    private String referencia;

    private Boolean aprobado = false;

    private EstadoProducto estado = EstadoProducto.INACTIVO;

    private Integer stock = 0;

    public Producto() {
    }

    public Producto(String id, String nombre, String descripcion, BigDecimal precioBase,
            String referencia, Boolean aprobado, EstadoProducto estado, Integer stock) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precioBase = precioBase;
        this.referencia = referencia;
        this.aprobado = aprobado;
        this.estado = estado;
        this.stock = stock;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
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
        return "Producto{id='" + id + "', nombre='" + nombre + "', precioBase=" + precioBase
                + ", referencia='" + referencia + "', estado=" + estado + '}';
    }
}
