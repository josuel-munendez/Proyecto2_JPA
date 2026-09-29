package com.example.servicio;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * MongoDB de pruebas vía Testcontainers, compartido por toda la JVM.
 *
 * CONTENEDOR ÚNICO (y no uno por clase de test): Spring cachea el contexto de
 * aplicación, pero cada test que combina {@code @MockitoBean} genera una clave
 * de contexto distinta y termina pidiendo su propio contenedor. Con dos
 * instancias de MongoDB la máquina se queda sin memoria y la suite falla con
 * "Container startup failed for image mongo:latest". Este equipo tiene
 * 3,3 GiB de RAM, así que un único contenedor es la diferencia entre una suite
 * verde y una roja.
 *
 * Por eso el contenedor es un {@code static} y el bean declara
 * {@code destroyMethod = ""}: Spring destruiría el contenedor al cerrar el
 * primer contexto y el segundo se quedaría sin base de datos. Con destroyMethod
 * vacío, el contenedor sobrevive a los tests y lo limpia el ryuk de
 * Testcontainers al terminar la JVM.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final DockerImageName IMAGEN = DockerImageName.parse("mongo:latest");

    private static MongoDBContainer mongoCompartido;

    @Bean(destroyMethod = "")
    @ServiceConnection
    public MongoDBContainer mongoDbContainer() {
        if (mongoCompartido == null) {
            mongoCompartido = new MongoDBContainer(IMAGEN);
            mongoCompartido.start();
        }
        return mongoCompartido;
    }
}
