package com.example.servicio.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP saliente hacia el backend Django (comunicación
 * servicio-a-servicio, no usuario-a-servicio).
 *
 * Dos responsabilidades que se configuran UNA sola vez aquí para que
 * InterServiceClient no tenga que repetirlas en cada llamada:
 *
 *  1. TIMEOUTS CORTOS (3s conexión / 5s lectura). Si Django está caído, Spring
 *     no debe quedarse esperando los 30s por defecto del JDK: una purga que
 *     espera 30s por un health check es una purga que parece colgada.
 *
 *  2. HEADER X-Internal-Token. Token compartido definido en el .env de ambos
 *     proyectos. No es JWT: no hay un usuario detrás de la llamada, es el
 *     microservicio máquina-a-máquina, por eso no hay refresh ni expiración.
 *
 * Si app.internal.token queda vacío (variable no definida), el header viaja
 * vacío y Django responde 401: falla cerrado, nunca abierto.
 */
@Configuration
public class RestClientConfig {

    /**
     * Se usa {@link RestClient#builder()} y no el {@code RestClient.Builder}
     * auto-configurado de Spring Boot: ese bean solo existe si el proyecto
     * depende de spring-boot-restclient, y este servicio no lo necesita. La
     * factoría estática no depende de ninguna configuración del contexto.
     */
    @Bean
    public RestClient djangoRestClient(
            @Value("${app.django.base-url:http://127.0.0.1:8000}") String baseUrl,
            @Value("${app.internal.token:}") String internalToken,
            @Value("${app.django.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${app.django.read-timeout-ms:5000}") long readTimeoutMs) {

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("X-Internal-Token", internalToken)
                .build();
    }
}
