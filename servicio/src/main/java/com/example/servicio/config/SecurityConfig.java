package com.example.servicio.config;

import com.example.servicio.security.RateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * ============================================================================
 * CONFIGURACIÓN DE SEGURIDAD: SecurityConfig (Spring Security)
 * ============================================================================
 * Configura la cadena de filtros de seguridad (SecurityFilterChain) para la aplicación.
 *
 * Características Implementadas:
 * 1. CSRF Deshabilitado: Apropiado para APIs RESTful Stateless que utilizan tokens / autenticación basada en cabeceras.
 * 2. Sesiones STATELESS: No crea sesiones HTTP en memoria en el servidor, optimizando rendimiento y escalabilidad.
 * 3. Enrutamiento de Autorización: Permite el acceso público a la API y a las vistas web.
 * 4. Integración de Filtros Personalizados: Registra `RateLimitFilter` antes de `UsernamePasswordAuthenticationFilter`.
 * ============================================================================
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(RateLimitFilter rateLimitFilter) {
        this.rateLimitFilter = rateLimitFilter;
    }

    /**
     * Define la cadena de filtros de seguridad HTTP.
     *
     * @param http Objeto HttpSecurity para personalizar la seguridad web.
     * @return SecurityFilterChain ensamblada.
     * @throws Exception En caso de errores en la configuración de Spring Security.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/**").permitAll()
                .requestMatchers("/productos/**").permitAll()
                .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
                .anyRequest().permitAll()
            )
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
