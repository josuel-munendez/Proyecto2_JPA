package com.example.servicio.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Aviso de arranque sobre la configuración inter-servicio.
 *
 * {@code app.internal.token} se resuelve desde la variable de entorno
 * {@code INTERNAL_API_TOKEN}, cuyo valor vive en el .env del backend Django, que
 * la JVM no lee. Si arranca sin exportarla, el header {@code X-Internal-Token}
 * viaja vacío, Django responde 401 y el fail-safe de
 * {@code InterServiceClient.tieneOrdenesAsociadas} bloquea TODA purga física con
 * un 400 que dice "existen ordenes con este producto" aunque no haya ninguna.
 *
 * Ese fallo es silencioso por diseño (el fail-safe no puede distinguir "Django
 * no responde" de "el token no viaja"), asi que sin este aviso el unico sintoma
 * es un 400 de negocio inexplicable. Arranque con
 * {@code ../start.sh}, que exporta la variable leyendo el .env de Django.
 *
 * No detiene la aplicacion: los endpoints de solo lectura degradan a un valor
 * neutro y el resto del CRUD de productos no depende de Django.
 */
@Component
public class InterServiceConfigVerifier implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InterServiceConfigVerifier.class);

    private final String internalToken;
    private final String baseUrl;

    public InterServiceConfigVerifier(
            @Value("${app.internal.token:}") String internalToken,
            @Value("${app.django.base-url:http://127.0.0.1:8000}") String baseUrl) {
        this.internalToken = internalToken;
        this.baseUrl = baseUrl;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("INTER-SERVICE: Django={}", baseUrl);
        if (internalToken.isBlank()) {
            log.warn("INTER-SERVICE: app.internal.token VACIO. INTERNAL_API_TOKEN no esta "
                    + "definida, el header X-Internal-Token viajara vacio y Django respondera 401. "
                    + "Las purgas fisicas quedaran bloqueadas por el fail-safe con un 503 "
                    + "'No se pudo verificar si el producto tiene ordenes' (NO es un problema de "
                    + "ordenes: es que la consulta a Django no se pudo hacer). "
                    + "Arranque con start.sh o exporte INTERNAL_API_TOKEN antes de lanzar.");
        } else {
            log.info("INTER-SERVICE: X-Internal-Token configurado ({} caracteres)",
                    internalToken.length());
        }
    }
}
