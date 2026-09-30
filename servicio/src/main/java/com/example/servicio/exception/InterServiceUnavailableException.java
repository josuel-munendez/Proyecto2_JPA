package com.example.servicio.exception;

/**
 * ============================================================================
 * EXCEPCIÓN PERSONALIZADA: InterServiceUnavailableException
 * ============================================================================
 *
 * <p>Se lanza cuando una consulta a otro microservicio (Django) <b>no se pudo
 * ejecutar</b>, y por lo tanto el dato solicitado es <b>desconocido</b>.</p>
 *
 * <h2>Por qué existe separate de BusinessRuleException</h2>
 *
 * Antes, {@code InterServiceClient.tieneOrdenesAsociadas} devolvía {@code true}
 * ante cualquier error (el fail-safe) y el servicio lo traducía a:
 *
 * <pre>
 *   "No se puede purgar: existen ordenes con este producto."
 * </pre>
 *
 * Ese mensaje <b>afirmaba un hecho que el código no sabía</b>. Si Django
 * estaba caído, devolvía 401, o el token interno faltaba, el resultado era el
 * MISMO texto: un operador veía "el producto tiene órdenes" cuando en realidad
 * nadie había preguntado. El fail-safe era correcto —no purgar ante
 * incertidumbre— pero el diagnóstico era falso, y eso hace imposible saber si
 * hay que esperar a que Django levante o buscar el producto con historial real.
 *
 * <p>Con esta excepción los dos casos se reportan distinto:</p>
 * <ul>
 *   <li>Dato obtenido y es {@code true} → el producto <b>sí</b> tiene órdenes
 *       → {@link BusinessRuleException} → 400.</li>
 *   <li>Dato <b>no</b> obtenido → {@code InterServiceUnavailableException} → 503,
 *       que dice "no se pudo verificar", no "tiene órdenes".</li>
 * </ul>
 *
 * <p>La decisión de seguridad NO cambia: ante duda, la purga se bloquea. Lo que
 * cambia es que el operador recibe la causa real.</p>
 *
 * <h2>Mapeo HTTP</h2>
 * Traducida por {@code GlobalExceptionHandler} a <b>503 SERVICE UNAVAILABLE</b>:
 * el recurso ajeno está caído, no el pedido del usuario. Un 400 inducía a pensar
 * que el producto estaba mal; un 503 indica que hay que reintentar más tarde.
 */
public class InterServiceUnavailableException extends RuntimeException {

    /**
     * Construye la excepción con el detalle técnico del fallo de comunicación.
     *
     * @param message Descripción de qué consulta no pudo completarse y por qué.
     */
    public InterServiceUnavailableException(String message) {
        super(message);
    }

    /**
     * Construye la excepción conservando la causa original, para que el log
     * conserve la traza del 401 / timeout / connection refused original.
     *
     * @param message Descripción del fallo de comunicación.
     * @param causa   Excepción original de la que falló la consulta.
     */
    public InterServiceUnavailableException(String message, Throwable causa) {
        super(message, causa);
    }
}
