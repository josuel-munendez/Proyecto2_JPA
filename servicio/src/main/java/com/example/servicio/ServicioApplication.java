package com.example.servicio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ============================================================================
 * CLASE PRINCIPAL DE ARRANQUE: ServicioApplication
 * ============================================================================
 * Microservicio desarrollado bajo el framework Spring Boot 4 y Java 21 LTS.
 * 
 * Funcionalidades clave habilitadas:
 * - Autoconfiguración de Spring Boot (@SpringBootApplication)
 * - Escaneo de componentes (@ComponentScan)
 * - Configuración de beans de persistencia, seguridad y MVC
 * 
 * Este microservicio opera como backend independiente desacoplado del frontend
 * del proyecto formativo, exponiendo tanto APIs RESTful como vistas Thymeleaf.
 * ============================================================================
 */
@SpringBootApplication
public class ServicioApplication {

    /**
     * Punto de entrada principal (Entry Point) de la máquina virtual de Java (JVM).
     *
     * @param args Argumentos de línea de comandos pasados durante la ejecución.
     */
    public static void main(String[] args) {
        SpringApplication.run(ServicioApplication.class, args);
    }
}
