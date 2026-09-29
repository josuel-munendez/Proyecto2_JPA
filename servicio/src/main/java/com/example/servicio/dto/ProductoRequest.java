package com.example.servicio.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

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
    @DecimalMax(value = "99999999.99", message = "El precio no puede superar $99.999.999,99 COP")
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

    /**
     * Versión del producto que el cliente cree tener (bloqueo optimista).
     *
     * Opcional a propósito: si no viene (POST, o clientes legacy), la
     * actualización se aplica sin comprobar nada. Si viene y no coincide con
     * la de la fila, significa que otro usuario (o Django) la modificó
     * mientras este formulario estaba abierto y se responde 409 en vez de
     * pisar su trabajo.
     */
    private Long version;

    /**
     * Ids de las categorías del producto, del catálogo de Django.
     *
     * Es el conjunto COMPLETO de las marcadas en el formulario, no un delta:
     * las que no vienen aquí quedan desasignadas. Así el cliente no tiene que
     * calcular la diferencia, y un reintento con el mismo conjunto no duplica
     * nada.
     *
     * Opcional y con valor por defecto null (no lista vacía) a propósito, para
     * poder distinguir los dos casos:
     *   null -> "no me digas nada de categorías": el guardado del producto sigue
     *           adelante aunque Django esté caído, y no se toca la asignación.
     *   []   -> "quítamelas todas".
     * Si fuera una lista vacía por defecto, cualquier cliente que no envíe el
     * campo (incluidos los que no saben nada de categorías) borraría todas las
     * categorías del producto al guardar.
     *
     * Los ids son enteros porque las categorías siguen viviendo en Django.
     */
    private List<Long> categoriaIds = null;

    // ========================================================================
    // GETTERS Y SETTERS
    // ========================================================================

    public List<Long> getCategoriaIds() { return categoriaIds; }
    public void setCategoriaIds(List<Long> categoriaIds) { this.categoriaIds = categoriaIds; }

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

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
