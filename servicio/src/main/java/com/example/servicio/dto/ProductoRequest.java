package com.example.servicio.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class ProductoRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
    @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "El nombre contiene caracteres invalido")
    private String nombre;

    @Size(max = 500, message = "La descripcion no puede superar 500 caracteres")
    private String descripcion;

    @NotNull(message = "El precio base es obligatorio")
    @DecimalMin(value = "50.0", message = "El precio minimo es 50.0 COP")
    private BigDecimal precioBase;

    @NotBlank(message = "La referencia es obligatoria")
    @Pattern(regexp = "^[A-Z0-9\\-]{3,20}$", message = "Formato de referencia invalido")
    private String referencia;

    private Integer stock = 0;

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
