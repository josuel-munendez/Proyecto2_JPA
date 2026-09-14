package com.example.servicio;

import org.springframework.boot.SpringApplication;

public class TestServicioApplication {

	public static void main(String[] args) {
		SpringApplication.from(ServicioApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
