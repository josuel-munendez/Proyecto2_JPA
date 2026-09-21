package com.example.servicio.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Entidad principal que representa un Producto en el microservicio JPA.
 * Herencia de BaseEntity para auditoria de fechas (created_at, updated_at).
 * Arquitectura orientada al dominio: la entidad encapsula las reglas de negocio.
 *
 * Codigo de estados:
 *   ACTIVO    - Producto visible y disponible para venta
 *   INACTIVO  - Producto oculto pero conserva datos
 *   BORRADO   - Eliminacion logica del producto
 *
 * 5 validaciones con anotaciones Jakarta:
 *   1. @NotBlank en nombre (campo obligatorio)
 *   2. @Size en nombre y descripcion (longitud controlada)
 *   3. @Pattern en nombre (sin caracteres de control ni HTML)
 *   4. @NotNull + @DecimalMin en precioBase (valor minimo)
 *   5. @Pattern en referencia (formato alfanumerico)
 */
@Entity
@Table(name = "productos")
public class Producto extends BaseEntity {

    /**
     * Enum que define los estados posibles de un producto.
     * Permite gestionar el ciclo de vida: creacion -> activacion -> desactivacion -> eliminacion.
     */
    public enum EstadoProducto {
        ACTIVO,
        INACTIVO,
        BORRADO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // === VALIDACION 1: @NotBlank - El nombre es obligatorio ===
    @NotBlank(message = "El nombre del producto es obligatorio")
    // === VALIDACION 2: @Size - Control de longitud del nombre ===
    @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
    // === VALIDACION 3: @Pattern - Sin caracteres de control ni SQL injection ===
    @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "El nombre no puede contener caracteres de control")
    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    @Size(max = 500, message = "La descripcion no puede superar 500 caracteres")
    @Column(length = 500)
    private String descripcion;

    // === VALIDACION 4: @NotNull + @DecimalMin - Precio minimo en pesos colombianos ===
    @NotNull(message = "El precio base es obligatorio")
    @DecimalMin(value = "50.0", message = "El precio minimo es $50 COP (debe ser multiplo de 50)")
    @Column(name = "precio_base", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioBase;

    // === VALIDACION 5: @Pattern en referencia - Formato alfanumerico unico ===
    @Pattern(regexp = "^[A-Z0-9\\-]{3,20}$", message = "La referencia debe ser alfanumerica (3-20 caracteres, mayusculas, numeros o guiones)")
    @Column(nullable = false, unique = true, length = 20)
    private String referencia;

    @Column(nullable = false)
    private Boolean aprobado = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoProducto estado = EstadoProducto.INACTIVO;

    @Column(name = "stock", nullable = false)
    private Integer stock = 0;

    // === Constructores ===

    public Producto() {
    }

    public Producto(Long id, String nombre, String descripcion, BigDecimal precioBase,
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

    // === Getters y Setters ===

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public BigDecimal getPrecioBase() {
        return precioBase;
    }

    public void setPrecioBase(BigDecimal precioBase) {
        this.precioBase = precioBase;
    }

    public String getReferencia() {
        return referencia;
    }

    public void setReferencia(String referencia) {
        this.referencia = referencia;
    }

    public Boolean getAprobado() {
        return aprobado;
    }

    public void setAprobado(Boolean aprobado) {
        this.aprobado = aprobado;
    }

    public EstadoProducto getEstado() {
        return estado;
    }

    public void setEstado(EstadoProducto estado) {
        this.estado = estado;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    /**
     * Verifica si el precio base es valido para COP (Colombianos).
     * Regla de negocio: el precio debe ser multiplo de 50 y mayor o igual a 50.
     */
    public boolean esPrecioCopValido() {
        if (precioBase == null) return false;
        return precioBase.compareTo(BigDecimal.valueOf(50)) >= 0
                && precioBase.remainder(BigDecimal.valueOf(50)).compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Indica si el producto puede ser publicado.
     * Requiere: nombre, precio valido, referencia, al menos 1 de stock.
     */
    public boolean puedePublicarse() {
        return nombre != null && !nombre.isBlank()
                && precioBase != null && esPrecioCopValido()
                && referencia != null && !referencia.isBlank()
                && stock != null && stock > 0;
    }

    @Override
    public String toString() {
        return "Producto{id=" + id + ", nombre='" + nombre + "', precioBase=" + precioBase
                + ", referencia='" + referencia + "', estado=" + estado + '}';
    }
}
