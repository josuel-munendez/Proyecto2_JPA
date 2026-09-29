package com.example.servicio.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.servicio.entity.Producto;
import com.example.servicio.repository.ProductoRepository;
import com.example.servicio.service.ProductoService;

/**
 * Tarea programada: purga fisica de productos con soft-delete o
 * desaprobados hace mas de {@value #DIAS_GRACIA} dias.
 *
 * Reutiliza {@link ProductoService#purgarProducto(String)}, o sea exactamente
 * la misma logica y las mismas guardias que el boton manual de la UI: solo
 * estados finales y sin ordenes asociadas. Por eso un fallo puntual (Django
 * caido, producto con ordenes) no aborta el lote: se registra y se sigue con
 * el siguiente.
 *
 * Solo toca candidatos con auditoria published/disapproved/deleted, asi que
 * un producto pendiente de aprobacion nunca se purga por tiempo.
 *
 * Se desactiva con {@code app.purge.scheduler.enabled=false}, util en pruebas
 * y para arrancar el servicio en una demo sin que la tarea dispare sola.
 */
@Component
@ConditionalOnProperty(name = "app.purge.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ProductoPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProductoPurgeScheduler.class);

    private static final int DIAS_GRACIA = 30;

    private final ProductoService productoService;
    private final ProductoRepository productoRepository;

    public ProductoPurgeScheduler(ProductoService productoService,
                                 ProductoRepository productoRepository) {
        this.productoService = productoService;
        this.productoRepository = productoRepository;
    }

    /** Se ejecuta automaticamente a las 3:00 AM todos los dias. */
    @Scheduled(cron = "0 0 3 * * *")
    public void purgarProductosAntiguos() {
        log.info("SCHEDULER: Buscando candidatos a purga (gracia={} dias)...", DIAS_GRACIA);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(DIAS_GRACIA);
        List<Producto> candidatos = productoRepository.findPurgeCandidates(cutoff);
        int purgados = 0;

        for (Producto p : candidatos) {
            try {
                productoService.purgarProducto(p.getId());
                purgados++;
                log.warn("SCHEDULER: Producto ID {} ({}) purgado tras {} dias en borrado/desaprobado",
                        p.getId(), p.getNombre(), DIAS_GRACIA);
            } catch (Exception e) {
                // Un fallo no debe cortar el lote: los demas candidatos son
                // independientes y se pueden purgar sin problema.
                log.warn("SCHEDULER: No se pudo purgar producto ID {}: {}", p.getId(), e.getMessage());
            }
        }
        log.info("SCHEDULER: Purga automatica completada. Eliminados: {}", purgados);
    }
}
