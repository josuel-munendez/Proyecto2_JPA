package com.example.servicio;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test de humo: verifica que el contexto de Spring arranca.
 *
 * Usa el perfil "test" a proposito. Sin el, este test construiria un contexto
 * distinto al de las demas clases (los perfiles forman parte de la clave de
 * cache del contexto) y Testcontainers intentaria levantar un SEGUNDO
 * contenedor de MongoDB para el mismo proceso, que es lo que hacia fallar la
 * suite completa con "Container startup failed". Ademas, sin perfil, este test
 * se conectaria al MongoDB Atlas de produccion desde la suite de pruebas.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ServicioApplicationTests {

	@Test
	void contextLoads() {
	}

}
