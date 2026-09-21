package com.example.servicio.exception;

/**
 * ============================================================================
 * EXCEPCIÓN PERSONALIZADA: BusinessRuleException
 * ============================================================================
 * Excepción lanzada cuando una operación solicitada por el usuario viola una
 * regla de negocio del dominio (ej: nombre duplicado, referencia repetida,
 * intento de activación de un producto sin stock ni precio válido).
 *
 * Mapeo HTTP:
 * Esta excepción es capturada por GlobalExceptionHandler y traducida automáticamente
 * a una respuesta HTTP con código de estado 400 BAD REQUEST.
 * ============================================================================
 */
public class BusinessRuleException extends RuntimeException {

    /**
     * Construye la excepción con un mensaje detallado de la regla de negocio infringida.
     *
     * @param message Explicación de la inconsistencia de negocio detectada.
     */
    public BusinessRuleException(String message) {
        super(message);
    }
}
