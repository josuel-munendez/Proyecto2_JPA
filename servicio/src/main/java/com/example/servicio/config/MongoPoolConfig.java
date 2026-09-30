package com.example.servicio.config;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Pool de conexiones de MongoDB Atlas.
 *
 * Solo se ajusta el pool; la cadena de conexion la aplica Spring Boot por su
 * cuenta a partir de las connection details. Injectarla aqui a proposito
 * seria un error: en los tests la URI no llega como propiedad
 * spring.data.mongodb.uri sino como MongoDBContainer de Testcontainers, asi
 * que leerla con @Value rompia el contexto entero. De delegar en Boot ademas
 * se evita pisar lo que venga bien puesto en la URI (tls, retryWrites,
 * replicaSet) y se respeta el contenedor de los tests sin tocar nada.
 *
 * El tuning vive en el codigo y no en la URI porque la URI viene de
 * SPRING_DATA_MONGODB_URI, que es un secreto y por tanto no se versiona: si
 * el tuning se dejara ahi seria invisible para quien lea el repositorio.
 *
 * Todos los valores son sobreescribibles por propiedad, para poder ajustarlos
 * por entorno sin recompilar.
 */
@Configuration
public class MongoPoolConfig {

    /**
     * LOWEST_PRECEDENCE para que este customizer se aplique DESPUES del de
     * Spring Boot y el pool gane. En el orden inverso, Boot sobrescribiria
     * estos valores con los suyos y el ajuste no serviria de nada.
     */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public MongoClientSettingsBuilderCustomizer mongoPoolCustomizer(
            @Value("${app.mongo.max-pool-size:20}") int maxPoolSize,
            @Value("${app.mongo.min-pool-size:2}") int minPoolSize,
            @Value("${app.mongo.max-connection-idle-time:60s}") Duration maxIdleTime,
            @Value("${app.mongo.max-connection-life-time:30m}") Duration maxLifeTime,
            @Value("${app.mongo.server-selection-timeout:5s}") Duration serverSelectionTimeout,
            @Value("${app.mongo.connect-timeout:10s}") Duration connectTimeout,
            @Value("${app.mongo.socket-timeout:45s}") Duration socketTimeout,
            @Value("${app.mongo.wait-queue-timeout:5s}") Duration waitQueueTimeout) {

        return builder -> builder
                .applyToConnectionPoolSettings(b -> {
                    // Atlas cuenta las conexiones contra un techo del plan. Un
                    // pool sobredimensionado aqui no acelera nada: solo compite
                    // por el limite con los demas clientes del proyecto. 20
                    // conexiones cubren de sobra la concurrencia de este
                    // servicio.
                    b.maxSize(maxPoolSize);
                    // El minimo mantiene vivas unas cuantas conexiones. La razon
                    // importante aqui es economica: cada una cuesta un handshake
                    // TLS contra sa-east-1, que cruza el oceano y se nota en la
                    // primera consulta tras un arranque en frio.
                    b.minSize(minPoolSize);
                    // Atlas escala a cero cuando no hay trafico. Una conexion
                    // inactiva mucho tiempo queda muerta por el lado del cluster
                    // y la siguiente peticion falla con "server selection
                    // timeout"; se le pide la devolucion antes de que muera.
                    b.maxConnectionIdleTime(millis(maxIdleTime), TimeUnit.MILLISECONDS);
                    // Y antes de que llegue a morir, se recicla de forma ordenada
                    // para que la rotacion no coincida con un pico de trafico.
                    b.maxConnectionLifeTime(millis(maxLifeTime), TimeUnit.MILLISECONDS);
                    // Ante un pico de concurrencia, fallar rapido y sin esperar es
                    // mejor que encolar peticiones que van a expirar igualmente:
                    // el cliente recibe un error claro en vez de un timeout
                    // opaco. maxConnecting acota por separado los huecos de
                    // conexion, que si no disparan el coste del handshake.
                    b.maxConnecting(maxPoolSize);
                    b.maxWaitTime(millis(waitQueueTimeout), TimeUnit.MILLISECONDS);
                })
                .applyToClusterSettings(b ->
                        b.serverSelectionTimeout(
                                millis(serverSelectionTimeout), TimeUnit.MILLISECONDS))
                .applyToSocketSettings(b -> {
                    b.connectTimeout(millis(connectTimeout), TimeUnit.MILLISECONDS);
                    // Ojo: esto es readTimeout. Contra sa-east-1 un aggregate
                    // grande puede tardar, pero dejarlo en 0 (sin limite)
                    // convierte un cuelgue del cluster en un cuelgue de este
                    // hilo, que es peor.
                    b.readTimeout(millis(socketTimeout), TimeUnit.MILLISECONDS);
                });
    }

    private static long millis(Duration d) {
        return d.toMillis();
    }
}
