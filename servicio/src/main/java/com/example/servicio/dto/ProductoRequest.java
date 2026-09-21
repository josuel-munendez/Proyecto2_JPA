package com.example.servicio.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * ============================================================================
 * DTO: ProductoRequest (Data Transfer Object para Creación/Edición)
 * ============================================================================
 * Objeto de transferencia utilizado para recibir y encapsular los datos de entrada
 * desde el cliente (peticiones POST / PUT en la API REST o formularios web MVC).
 *
 * Propósito en la Arquitectura por Capas:
 * 1. Desacoplamiento: Aísla el modelo de dominio interno de las entradas directas del usuario.
 * 2. Validación temprana: Ejecuta validaciones declarativas Jakarta Validation antes de
 *    invocar la capa de servicio.
 * 3. Seguridad: Previene ataques de sobre-asignación (Mass Assignment / Over-posting).
 * ============================================================================
 */
public class ProductoRequest {

    /**
     * Nombre comercial o descriptivo del producto.
     * Validación: Obligatorio, entre 3 y 100 caracteres, sin caracteres de control maliciosos.
     */
    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
    @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "El nombre contiene caracteres invalidos")
    private String nombre;

    /**
     * Descripción detallada del producto.
     * Validación: Opcional, pero con longitud máxima de 500 caracteres.
     */
    @Size(max = 500, message = "La descripcion no puede superar 500 caracteres")
    private String descripcion;

    /**
     * Precio base en pesos colombianos (COP).
     * Validación: Obligatorio, con un valor mínimo de 50.0 COP.
     */
    @NotNull(message = "El precio base es obligatorio")
    @DecimalMin(value = "50.0", message = "El precio minimo es 50.0 COP")
    private BigDecimal precioBase;

    /**
     * Código de referencia único asignado al producto (ej: PROD-001, RED-CAM-01).
     * Validación: Obligatorio, patrón alfanumérico en mayúsculas con longitud de 3 a 20 caracteres.
     */
    @NotBlank(message = "La referencia es obligatoria")
    @Pattern(regexp = "^[A-Z0-9\\-]{3,20}$", message = "Formato de referencia invalido (3-20 caracteres alfanumericos)")
    private String referencia;

    /**
     * Cantidad de unidades disponibles en inventario.
     * Por defecto se inicializa en 0 si no es provisto.
     */
    private Integer stock = 0;

    // ========================================================================
    // GETTERS Y SETTERS
    // ========================================================================

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
}
