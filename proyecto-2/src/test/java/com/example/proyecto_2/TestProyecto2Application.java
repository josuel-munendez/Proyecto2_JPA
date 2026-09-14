package com.example.proyecto_2;

import org.springframework.boot.SpringApplication;

public class TestProyecto2Application {

	public static void main(String[] args) {
		SpringApplication.from(Proyecto2Application::main).with(TestcontainersConfiguration.class).run(args);
	}

}
