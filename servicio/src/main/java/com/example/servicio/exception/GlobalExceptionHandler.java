package com.example.servicio.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * ============================================================================
 * CONTROLADOR GLOBAL DE EXCEPCIONES: GlobalExceptionHandler
 * ============================================================================
 * Intercepta de forma transversal todas las excepciones lanzadas por los
 * controladores REST utilizando la anotación @RestControllerAdvice (Aspect-Oriented).
 *
 * Beneficios para la Arquitectura y Seguridad:
 * 1. Respuestas Estandarizadas: Transforma errores en payloads JSON consistentes.
 * 2. Prevención de Fugas de Información: Oculta trazas internas de la base de datos
 *    o del framework (StackTraces) frente a usuarios externos.
 * 3. Códigos de Estado Semánticos: Mapea cada tipo de fallo al código HTTP correspondiente
 *    (400, 404, 500).
 * ============================================================================
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Maneja excepciones de tipo ResourceNotFoundException (Recurso No Encontrado).
     *
     * @param ex Instancia de la excepción capturada.
     * @return Respuesta HTTP 404 (NOT FOUND) con el mensaje de error.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Excepcion 404 Recurso No Encontrado: {}", ex.getMessage());
        return crearRespuestaError(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    /**
     * Maneja excepciones de reglas de negocio infringidas (BusinessRuleException).
     *
     * @param ex Instancia de la excepción de negocio capturada.
     * @return Respuesta HTTP 400 (BAD REQUEST) con el motivo del rechazo.
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessRule(BusinessRuleException ex) {
        log.warn("Excepcion 400 Regla de Negocio: {}", ex.getMessage());
        return crearRespuestaError(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    /**
     * Maneja fallos en las validaciones de anotaciones Jakarta (@Valid / @NotBlank / @Size, etc.).
     *
     * @param ex Excepción lanzada por Spring MVC cuando la validación del RequestBody falla.
     * @return Respuesta HTTP 400 (BAD REQUEST) con el mapa detallado de campos y mensajes de error.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        log.warn("Excepcion 400 Validacion de DTO/Entidad fallida");
        Map<String, String> errores = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errores.put(error.getField(), error.getDefaultMessage());
        }
        return crearRespuestaError(HttpStatus.BAD_REQUEST, "Error de validacion en los datos de entrada", errores);
    }

    /**
     * Manejador global de respaldo para cualquier excepción no controlada explícitamente.
     *
     * @param ex Excepción genérica capturada.
     * @return Respuesta HTTP 500 (INTERNAL SERVER ERROR) con un mensaje amigable y seguro.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGlobalException(Exception ex) {
        log.error("Excepcion 500 Error Interno del Servidor: ", ex);
        return crearRespuestaError(HttpStatus.INTERNAL_SERVER_ERROR, "Ha ocurrido un error interno en el servidor", null);
    }

    /**
     * Construye la estructura JSON estándar de respuesta de error.
     *
     * @param status Código de estado HTTP asignado.
     * @param mensaje Mensaje descriptivo principal del error.
     * @param detalles Objeto opcional con detalles específicos (ej: mapa de campos inválidos).
     * @return ResponseEntity con la estructura JSON y el status HTTP.
     */
    private ResponseEntity<Map<String, Object>> crearRespuestaError(HttpStatus status, String mensaje, Object detalles) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", mensaje);
        if (detalles != null) {
            body.put("details", detalles);
        }
        return new ResponseEntity<>(body, status);
    }
}
