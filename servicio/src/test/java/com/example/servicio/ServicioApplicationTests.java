package com.example.servicio;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=update")
class ServicioApplicationTests {

	@Test
	void contextLoads() {
	}

}
