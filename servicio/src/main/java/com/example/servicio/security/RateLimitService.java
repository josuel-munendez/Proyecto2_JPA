package com.example.servicio.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ============================================================================
 * SERVICIO DE RATE LIMITING: RateLimitService
 * ============================================================================
 * Gestiona el control de tráfico y tasa de peticiones concurrentes por cliente.
 * 
 * Mecanismo de Funcionamiento:
 * - Emplea una clave compuesta: [IP_CLIENTE] : [METODO_HTTP] : [VENTANA_DE_MINUTO]
 * - Utiliza estructuras de datos concurrentes (`ConcurrentHashMap` y `AtomicInteger`)
 *   para garantizar seguridad en entornos multi-hilo sin bloqueos pesados de sincronización.
 * - Registra alertas WARN en logs cuando un cliente excede el umbral configurado.
 * ============================================================================
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private final RateLimitProperties properties;

    /** Contadores concurrentes de peticiones en memoria */
    private final Map<String, RequestCounter> counters = new ConcurrentHashMap<>();

    public RateLimitService(RateLimitProperties properties) {
        this.properties = properties;
    }

    /**
     * Evalúa si una petición entrante debe ser permitida o bloqueada.
     *
     * @param clientIp Dirección IP de origen de la solicitud.
     * @param httpMethod Método HTTP invocado (GET, POST, PUT, DELETE).
     * @return true si la solicitud está dentro del límite; false si ha superado la cuota por minuto.
     */
    public boolean permitirPeticion(String clientIp, String httpMethod) {
        int limite = obtenerLimitePorMetodo(httpMethod);
        long minutoActual = System.currentTimeMillis() / 60000;
        String key = clientIp + ":" + httpMethod.toUpperCase() + ":" + minutoActual;

        RequestCounter counter = counters.computeIfAbsent(key, k -> new RequestCounter());
        int peticionesCount = counter.incrementAndGet();

        if (peticionesCount > limite) {
            log.warn("Rate Limit superado para IP: {} - Metodo: {} - Peticiones: {}/min (Limite: {})",
                    clientIp, httpMethod, peticionesCount, limite);
            return false;
        }
        return true;
    }

    /**
     * Obtiene el límite máximo de peticiones por minuto según el método HTTP.
     *
     * @param method Método HTTP evaluado.
     * @return Cantidad entera de peticiones permitidas por minuto.
     */
    private int obtenerLimitePorMetodo(String method) {
        return switch (method.toUpperCase()) {
            case "GET" -> properties.getGetPerMinute();
            case "POST" -> properties.getPostPerMinute();
            case "PUT" -> properties.getPutPerMinute();
            case "DELETE" -> properties.getDeletePerMinute();
            default -> 30;
        };
    }

    /**
     * Clase interna para conteo atómico y seguro entre múltiples hilos de ejecución.
     */
    private static class RequestCounter {
        private final AtomicInteger count = new AtomicInteger(0);

        public int incrementAndGet() {
            return count.incrementAndGet();
        }
    }
}
