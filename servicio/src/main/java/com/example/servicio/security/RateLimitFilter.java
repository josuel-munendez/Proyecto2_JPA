package com.example.servicio.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro HTTP para interceptar peticiones y aplicar Rate Limiting.
 * Retorna estado 429 (Too Many Requests) si se sobrepasa el limite.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    public RateLimitFilter(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        // Aplicar rate limiting solo a endpoints de la API REST
        if (path.startsWith("/api/")) {
            String clientIp = obtenerIpCliente(request);
            String method = request.getMethod();

            if (!rateLimitService.permitirPeticion(clientIp, method)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("""
                    {
                        "status": 429,
                        "error": "Too Many Requests",
                        "message": "Ha superado el limite de peticiones permitidas por minuto. Intente de nuevo mas tarde."
                    }
                    """);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String obtenerIpCliente(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
