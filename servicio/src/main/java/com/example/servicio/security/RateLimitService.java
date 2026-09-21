package com.example.servicio.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servicio de control de tasa de peticiones (Rate Limiting) por IP y metodo HTTP.
 * Milla Extra: Bloqueo de peticiones excesivas por minuto para evitar exploits/DDoS.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private final RateLimitProperties properties;

    // Almacena peticiones por clave (IP + Metodo) en la ventana actual de 1 minuto
    private final Map<String, RequestCounter> counters = new ConcurrentHashMap<>();

    public RateLimitService(RateLimitProperties properties) {
        this.properties = properties;
    }

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

    private int obtenerLimitePorMetodo(String method) {
        return switch (method.toUpperCase()) {
            case "GET" -> properties.getGetPerMinute();
            case "POST" -> properties.getPostPerMinute();
            case "PUT" -> properties.getPutPerMinute();
            case "DELETE" -> properties.getDeletePerMinute();
            default -> 30;
        };
    }

    private static class RequestCounter {
        private final AtomicInteger count = new AtomicInteger(0);

        public int incrementAndGet() {
            return count.incrementAndGet();
        }
    }
}
