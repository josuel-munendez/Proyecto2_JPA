package com.example.servicio.client;

import com.example.servicio.exception.InterServiceUnavailableException;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Cliente inter-servicio: consultas que el microservicio Spring Boot le hace
 * al backend Django. Es comunicación MÁQUINA-A-MÁQUINA, no usuario-a-servicio,
 * así que no viaja JWT de usuario sino un token compartido en el header
 * X-Internal-Token: no hay expiración ni refresh que gestionar, solo rotación
 * desde el .env.
 *
 * Endpoints que expone Django:
 * <pre>
 *   GET /api/orders/check-product/{id}/   -&gt; {"has_orders": bool, "order_count": int}
 *   GET /api/internal/products/health/    -&gt; {"status": "ok", ...}
 *   GET /api/internal/products/stats/     -&gt; {"total": n, "activos": n, ...}
 *   GET /api/internal/products/recent/    -&gt; {"products": [...]}
 *   GET /api/internal/products/exists/    -&gt; {"exists": bool}
 * </pre>
 *
 * La URL base, los timeouts y el header X-Internal-Token los inyecta
 * RestClientConfig.
 *
 * PRINCIPIO FAIL-SAFE: cuando una consulta protege una operación destructiva
 * y Django no responde, la operación se BLOQUEA igual. Es preferible no purgar
 * un producto a purgar mal y perder la trazabilidad de una orden. La diferencia
 * es que ya no se devuelve un {@code true} silencioso: se lanza
 * {@link InterServiceUnavailableException}, de modo que el mensaje que ve el
 * operador dice "no se pudo verificar" en vez de afirmar "tiene órdenes", que es
 * un dato que el microservicio no llegó a conocer. Los métodos de solo-lectura
 * (health, stats, recent, exists) sí devuelven un valor neutro, porque no hay
 * nada que proteger.
 */
@Component
public class InterServiceClient {

    private static final Logger log = LoggerFactory.getLogger(InterServiceClient.class);

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;

    public InterServiceClient(RestClient djangoRestClient) {
        this.restClient = djangoRestClient;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Guardia de purga física
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Consulta a Django si el producto tiene líneas de orden asociadas.
     *
     * <p>Único método que <b>no</b> degrada a un valor por defecto ante un
     * error: lanza {@link InterServiceUnavailableException}. El fail-safe se
     * mantiene intacto porque el servicio trata esa excepción como bloqueo, pero
     * el operador recibe un 503 honesto en vez de un 400 que afirma un hecho
     * falso.</p>
     *
     * @param productoId Id del producto a verificar.
     * @return {@code true} si el producto tiene al menos una línea de orden
     *         asociada. Falso positivo explícito: sólo cuando Django respondió
     *         y respondió que no.
     * @throws InterServiceUnavailableException si la consulta no pudo
     *         completarse (Django caído, 401 por token interno ausente,
     *         timeout). El dato es desconocido, no "tiene órdenes".
     */
    public boolean tieneOrdenesAsociadas(Long productoId) {
        Map<String, Object> body;
        try {
            body = restClient.get()
                    .uri("/api/orders/check-product/{id}/", productoId)
                    .retrieve()
                    .body(MAP_TYPE);
        } catch (Exception e) {
            log.warn("INTER-SERVICE: no se pudo verificar ordenes del producto {} ({}). "
                    + "Se bloquea la purga por fail-safe, pero el dato es desconocido.",
                    productoId, e.getMessage());
            throw new InterServiceUnavailableException(
                    "No se pudo verificar si el producto " + productoId
                    + " tiene ordenes asociadas: el servicio de ordenes no respondio ("
                    + e.getMessage() + "). La purga se bloquea por seguridad, pero no se "
                    + "puede afirmar que el producto tenga ordenes. Verifique que el "
                    + "backend Django este levantado y que INTERNAL_API_TOKEN coincida.",
                    e);
        }
        boolean has = body != null && Boolean.TRUE.equals(body.get("has_orders"));
        log.info("INTER-SERVICE: producto {} tieneOrdenes={}", productoId, has);
        return has;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Diagnóstico y sincronización
    // ═══════════════════════════════════════════════════════════════════════

    /** ¿Django responde a un health check? No tiene efecto fail-safe. */
    public boolean djangoDisponible() {
        try {
            Map<String, Object> body = restClient.get()
                    .uri("/api/internal/products/health/")
                    .retrieve()
                    .body(MAP_TYPE);
            return body != null && "ok".equals(body.get("status"));
        } catch (Exception e) {
            log.warn("INTER-SERVICE: Django no responde al health check: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Agregados por estado de los productos en Django.
     * @return el mapa de stats, o vacío si Django no responde.
     */
    public Map<String, Object> obtenerStatsProductos() {
        try {
            Map<String, Object> body = restClient.get()
                    .uri("/api/internal/products/stats/")
                    .retrieve()
                    .body(MAP_TYPE);
            return body != null ? body : Map.of();
        } catch (Exception e) {
            log.warn("INTER-SERVICE: error obteniendo stats de productos: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * Productos con updated_at >= since (sincronización incremental).
     *
     * @param since instante ISO 8601, ej: {@code 2026-09-20T00:00:00Z}
     * @param limit  tope de resultados (Django lo recorta a 500)
     * @return lista de productos, vacía si Django no responde.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> obtenerProductosRecientes(String since, int limit) {
        String uri = UriComponentsBuilder
                .fromPath("/api/internal/products/recent/")
                .queryParam("since", since)
                .queryParam("limit", limit)
                .build()
                .toUriString();
        try {
            Map<String, Object> body = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(MAP_TYPE);
            if (body == null || !(body.get("products") instanceof List<?> lista)) {
                return List.of();
            }
            return (List<Map<String, Object>>) lista;
        } catch (Exception e) {
            log.warn("INTER-SERVICE: error obteniendo productos recientes: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * ¿Existe ya esta referencia en Django? Evita crear el mismo producto
     * dos veces desde backends distintos.
     *
     * @return true/false; false si Django no responde (no bloquea, la unicidad
     *         ya la garantiza la restricción UNIQUE de la base compartida).
     */
    public boolean referenciaExisteEnDjango(String referencia) {
        String uri = UriComponentsBuilder
                .fromPath("/api/internal/products/exists/")
                .queryParam("ref", referencia)
                .build()
                .toUriString();
        try {
            Map<String, Object> body = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(MAP_TYPE);
            return body != null && Boolean.TRUE.equals(body.get("exists"));
        } catch (Exception e) {
            log.warn("INTER-SERVICE: error verificando referencia {}: {}", referencia, e.getMessage());
            return false;
        }
    }
}
