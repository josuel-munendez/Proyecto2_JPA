package com.example.servicio.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ActiveProfiles;

import com.example.servicio.TestcontainersConfiguration;
import com.example.servicio.service.impl.ProductoPurgeScheduler;

/**
 * El scheduler de purga solo dispara si la aplicacion tiene
 * {@code @EnableScheduling}. Sin ese annotation, {@code @Scheduled} se procesa
 * como un metodo comun que nunca se invoca, y el servicio arranca "sano" con la
 * purga automatica de las 3:00 AM apagada y sin un solo aviso en los logs.
 *
 * Por eso se prueba la infraestructura de planificacion y no solo que la clase
 * exista: es exactamente la regresion que se cuela al portar codigo entre
 * ramas, porque compila igual y ningun test funcional la detecta.
 *
 * Esta clase habilita el scheduler con {@code properties}, anulando el
 * {@code app.purge.scheduler.enabled=false} del perfil de pruebas. El cron de
 * las 3:00 AM no llega a dispararse durante el test, asi que no toca datos.
 */
@SpringBootTest(properties = "app.purge.scheduler.enabled=true")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SchedulerHabilitadoTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("El contexto tiene el postprocesador que ejecuta los @Scheduled")
    void elPostprocesadorDeTareasEstaRegistrado() {
        assertThat(context.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class))
                .as("Sin ScheduledAnnotationBeanPostProcessor los @Scheduled nunca se ejecutan. "
                        + "Revisar que ServicioApplication conserve @EnableScheduling.")
                .isNotEmpty();
    }

    @Test
    @DisplayName("Con la propiedad activa, el scheduler de purga queda registrado como bean")
    void elSchedulerEstaRegistrado() {
        assertThat(context.getBean(ProductoPurgeScheduler.class))
                .as("ProductoPurgeScheduler deberia ser un bean (@Component) cuando "
                        + "app.purge.scheduler.enabled=true")
                .isNotNull();
    }
}
