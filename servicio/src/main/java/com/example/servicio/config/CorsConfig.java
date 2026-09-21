package com.example.servicio.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * ============================================================================
 * CONFIGURACIÓN DE CORS: CorsConfig (Cross-Origin Resource Sharing)
 * ============================================================================
 * Habilita y parametriza el intercambio de recursos de origen cruzado para permitir
 * que aplicaciones Frontend SPA (React, Vue, Vite, etc.) consuman los endpoints REST.
 *
 * Parámetros Configurados:
 * - Allowed Origins: Dominios autorizados (configurables vía app.cors.allowed-origins).
 * - Allowed Methods: GET, POST, PUT, DELETE, OPTIONS, PATCH.
 * - Allowed Headers: Authorization, Content-Type, Accept, etc.
 * - MaxAge: Cacheo de la negociación pre-flight (3600 segundos).
 * ============================================================================
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:8080}")
    private String allowedOrigins;

    /**
     * Define el filtro CorsFilter global aplicado a todas las rutas (/**).
     *
     * @return CorsFilter configurado.
     */
    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowCredentials(true);
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        config.setAllowedOrigins(origins);
        config.setAllowedHeaders(List.of("Origin", "Content-Type", "Accept", "Authorization", "X-Requested-With"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setMaxAge(3600L);

        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
