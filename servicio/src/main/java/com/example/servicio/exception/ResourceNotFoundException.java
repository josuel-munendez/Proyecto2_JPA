package com.example.servicio.exception;

/**
 * ============================================================================
 * EXCEPCIÓN PERSONALIZADA: ResourceNotFoundException
 * ============================================================================
 * Excepción de tiempo de ejecución (Unchecked Exception) lanzada cuando se intenta
 * acceder, actualizar o eliminar un registro/entidad que no existe en la base de datos.
 *
 * Mapeo HTTP:
 * Esta excepción es capturada por GlobalExceptionHandler y traducida automáticamente
 * a una respuesta HTTP con código de estado 404 NOT FOUND.
 * ============================================================================
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Construye la excepción con un mensaje descriptivo del recurso no encontrado.
     *
     * @param message Detalle del error (ej: "Producto no encontrado con ID: 15").
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
