package com.example.servicio.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ============================================================================
 * PROPIEDADES DE CONFIGURACIÓN: RateLimitProperties
 * ============================================================================
 * Mapea las propiedades del archivo application.properties con el prefijo "app.rate-limit"
 * para parametrizar de forma dinámica y desacoplada las cuotas de peticiones por minuto.
 * ============================================================================
 */
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    /** Límite de peticiones de lectura (GET) permitidas por minuto por IP */
    private int getPerMinute = 120;

    /** Límite de peticiones de creación (POST) permitidas por minuto por IP */
    private int postPerMinute = 30;

    /** Límite de peticiones de actualización (PUT) permitidas por minuto por IP */
    private int putPerMinute = 30;

    /** Límite de peticiones de eliminación (DELETE) permitidas por minuto por IP */
    private int deletePerMinute = 20;

    // ========================================================================
    // GETTERS Y SETTERS
    // ========================================================================

    public int getGetPerMinute() { return getPerMinute; }
    public void setGetPerMinute(int getPerMinute) { this.getPerMinute = getPerMinute; }

    public int getPostPerMinute() { return postPerMinute; }
    public void setPostPerMinute(int postPerMinute) { this.postPerMinute = postPerMinute; }

    public int getPutPerMinute() { return putPerMinute; }
    public void setPutPerMinute(int putPerMinute) { this.putPerMinute = putPerMinute; }

    public int getDeletePerMinute() { return deletePerMinute; }
    public void setDeletePerMinute(int deletePerMinute) { this.deletePerMinute = deletePerMinute; }
}
