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
     * Maneja el conflicto de version (bloqueo optimista).
     *
     * En esta rama MongoDB solo se cubre VersionConflictException, que es el
     * chequeo explicito de `version` en ProductoServiceImpl: el cliente envio
     * una version que ya no es la vigente porque otro usuario (u otro backend)
     * escribio mientras el formulario estaba abierto. No se mapean
     * OptimisticLockingFailureException ni jakarta.persistence.OptimisticLockException
     * porque son de Hibernate y esta rama no usa JPA.
     *
     * @return HTTP 409 CONFLICT con la version que el cliente deberia recargar.
     */
    @ExceptionHandler(VersionConflictException.class)
    public ResponseEntity<Map<String, Object>> handleVersionConflict(VersionConflictException ex) {
        log.warn("Excepcion 409 Conflicto de version (optimistic locking): {}", ex.getMessage());
        Map<String, Object> detalles = new HashMap<>();
        detalles.put("versionEsperada", ex.getVersionEsperada());
        detalles.put("versionActual", ex.getVersionActual());
        return crearRespuestaError(HttpStatus.CONFLICT,
                "El producto fue modificado por otro usuario. Recargue para ver los cambios mas recientes.",
                detalles);
    }

    /**
     * Fallo de la dependencia Django durante una operacion que no puede
     * completarse sin ella.
     *
     * 503 y no 400 a proposito: el producto es valido, lo que falla es el
     * servicio de destino. Asi el frontend puede reintentar en lugar de
     * pedirle al usuario que corrija datos.
     */
    @ExceptionHandler(InterServiceException.class)
    public ResponseEntity<Map<String, Object>> handleInterService(InterServiceException ex) {
        log.error("El backend Django no esta disponible: {}", ex.getMessage());
        return crearRespuestaError(HttpStatus.SERVICE_UNAVAILABLE,
                "El servicio de Django no esta disponible. La operacion se cancelo sin cambios; "
                + "reintenta en unos instantes.",
                Map.of());
    }

    /**
     * No se pudo CONSULTAR a Django, en vez de fallar al escribir en el.
     *
     * Es distinto de {@link InterServiceException}: aqui Django no hizo nada,
     * solo que no se pudo leer su respuesta (típicamente 401 por
     * X-Internal-Token ausente, o la dependencia caída). La purga sigue
     * bloqueada por el fail-safe de InterServiceClient, pero la respuesta
     * dice lo que pasó de verdad en vez de afirmar que el producto tiene
     * órdenes. 503 y no 400: el producto es válido, no hay nada que el
     * usuario pueda corregir.
     */
    @ExceptionHandler(InterServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleInterServiceUnavailable(InterServiceUnavailableException ex) {
        log.error("No se pudo consultar a Django: {}", ex.getMessage());
        return crearRespuestaError(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo verificar el historial del producto porque el servicio de Django "
                + "no respondio. La operacion se cancelo sin cambios; reintenta en unos instantes.",
                Map.of());
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
