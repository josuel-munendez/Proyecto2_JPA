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
 * ============================================================================
 * FILTRO HTTP DE SEGURIDAD: RateLimitFilter
 * ============================================================================
 * Intercepta todas las peticiones HTTP entrantes dirigidas a las rutas de la API (/api/**)
 * antes de que alcancen los controladores.
 *
 * Flujo de Ejecución:
 * 1. Extrae la dirección IP real del cliente (inspeccionando cabeceras de proxy como X-Forwarded-For).
 * 2. Consulta al `RateLimitService` si la IP no ha rebasado la cuota por minuto.
 * 3. Si la cuota fue superada:
 *    - Corta la cadena de filtros de inmediato.
 *    - Escribe directamente una respuesta JSON con código HTTP 429 TOO MANY REQUESTS.
 * 4. Si la cuota es válida, delega la ejecución al siguiente filtro de la cadena (`filterChain.doFilter`).
 * ============================================================================
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
        
        // Aplicar el control de tasa exclusivamente a las rutas de la API REST
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

    /**
     * Extrae la IP de origen del cliente soportando entornos tras proxies inversos o balanceadores de carga.
     *
     * @param request Petición HTTP recibida.
     * @return Dirección IP del cliente en formato String.
     */
    private String obtenerIpCliente(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
