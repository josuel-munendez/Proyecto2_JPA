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
 * REQUISITO 2: Controlador global de excepciones.
 * Maneja errores y retorna codigos de estado HTTP adecuados con respuestas estructuradas JSON.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Excepcion 404 Recurso No Encontrado: {}", ex.getMessage());
        return crearRespuestaError(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessRule(BusinessRuleException ex) {
        log.warn("Excepcion 400 Regla de Negocio: {}", ex.getMessage());
        return crearRespuestaError(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        log.warn("Excepcion 400 Validacion de DTO/Entidad fallida");
        Map<String, String> errores = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errores.put(error.getField(), error.getDefaultMessage());
        }
        return crearRespuestaError(HttpStatus.BAD_REQUEST, "Error de validacion en los datos de entrada", errores);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGlobalException(Exception ex) {
        log.error("Excepcion 500 Error Interno del Servidor: ", ex);
        return crearRespuestaError(HttpStatus.INTERNAL_SERVER_ERROR, "Ha ocurrido un error interno en el servidor", null);
    }

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
