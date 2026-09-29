package com.example.servicio.client;

import java.util.List;
import java.util.Map;

import com.example.servicio.exception.InterServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Cliente inter-servicio: consultas y operaciones que el microservicio Spring
 * Boot le hace al backend Django. Es comunicación MÁQUINA-A-MÁQUINA, no
 * usuario-a-servicio, así que no viaja JWT de usuario sino un token compartido
 * en el header X-Internal-Token: no hay expiración ni refresh que gestionar,
 * solo rotación desde el .env.
 *
 * Endpoints que expone Django:
 * <pre>
 *   DELETE /api/internal/products/dependencies/{ref}/ -&gt; cascada del lado Django
 *   GET    /api/orders/check-product/{ref}/          -&gt; {"has_orders": bool, "order_count": int}
 *   GET    /api/internal/products/health/             -&gt; {"status": "ok", ...}
 *   GET    /api/internal/products/stats/              -&gt; {"total": n, "activos": n, ...}
 *   GET    /api/internal/products/recent/             -&gt; {"products": [...]}
 *   GET    /api/internal/products/exists/             -&gt; {"exists": bool}
 * </pre>
 *
 * IDENTIFICADORES: en esta rama (java/mongoDB) el producto vive en la colección
 * `productos` de MongoDB, así que su id es un ObjectId (24 hex) y no el entero
 * de la tabla products_product. Por eso todos los métodos que identifican un
 * producto reciben {@code String productoRef} y no {@code Long}.
 *
 * PRINCIPIO FAIL-SAFE: cuando una operación depende de lo que conteste Django y
 * Django no responde, el método devuelve el valor que la BLOQUEA. Es preferible
 * no purgar un producto a purgar mal y perder la trazabilidad de una orden. Por
 * eso {@link #tieneOrdenesAsociadas(String)} y
 * {@link #limpiarDependenciasEnDjango(String)} devuelven el valor
 * conservador ante cualquier error. Los métodos de solo-lectura (health,
 * stats, recent, exists) en cambio devuelven un valor neutro, porque no hay
 * nada que proteger.
 *
 * La URL base, los timeouts y el header X-Internal-Token los inyecta
 * RestClientConfig.
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
    // Purga física
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * @param productoRef ObjectId de Mongo del producto.
     * @return true si el producto tiene al menos una línea de orden asociada.
     *         true TAMBIÉN si la consulta falla (fail-safe de integridad).
     */
    public boolean tieneOrdenesAsociadas(String productoRef) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri("/api/orders/check-product/{ref}/", productoRef)
                    .retrieve()
                    .body(MAP_TYPE);
            boolean has = body != null && Boolean.TRUE.equals(body.get("has_orders"));
            log.info("INTER-SERVICE: producto {} tieneOrdenes={}", productoRef, has);
            return has;
        } catch (Exception e) {
            log.warn("INTER-SERVICE: no se pudo verificar órdenes del producto {} ({}). "
                    + "Se asume que tiene órdenes y se bloquea la purga.",
                    productoRef, e.getMessage());
            return true;
        }
    }

    /**
     * Pide a Django que limpie SU parte de la cascada: los ítems de carrito, los
     * vínculos con categorías y las reseñas, que viven en el PostgreSQL de
     * Django y son inalcanzables desde MongoDB.
     *
     * Fail-safe: si Django falla, el servicio NO continúa con la purga. No se
     * puede devolver "ok" porque eso significaría "purgar igual y dejar basura
     * en Django"; el llamador debe abortar.
     *
     * @throws InterServiceException si Django no confirma el borrado.
     */
    public void limpiarDependenciasEnDjango(String productoRef) {
        try {
            Map<String, Object> body = restClient.delete()
                    .uri("/api/internal/products/dependencies/{ref}/", productoRef)
                    .retrieve()
                    .body(MAP_TYPE);
            long total = body == null ? 0L : ((Number) body.getOrDefault("total", 0)).longValue();
            log.info("INTER-SERVICE: Django eliminó {} dependencia(s) del producto {}: {}",
                    total, productoRef, body);
        } catch (Exception e) {
            log.error("INTER-SERVICE: Django no pudo limpiar las dependencias del producto {}. "
                    + "Se aborta la purga para no dejar referencias huérfanas en Django.",
                    productoRef, e);
            throw new InterServiceException(
                    "No se pudo limpiar las dependencias del producto en Django: " + e.getMessage(), e);
        }
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
     * @return true/false; false si Django no responde (no bloquea, en esta
     *         rama la unicidad la garantiza el índice único de la referencia
     *         en la colección `productos`).
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
