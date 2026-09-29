package com.example.servicio.service.impl;

import com.example.servicio.entity.Producto;
import com.example.servicio.service.ProductoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Tarea programada: purga física de productos en soft-delete/desaprobados
 * con más de {@value #DIAS_GRACIA} días de gracia.
 *
 * Reutiliza {@link ProductoService#purgarProducto(Long)} (misma lógica y
 * guardias que el botón manual): solo estados finales y sin órdenes.
 * Solo candidatos con auditoría publicada/desaprobada — nunca toca
 * productos pendientes de aprobación.
 */
@Component
public class ProductoPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProductoPurgeScheduler.class);
    private static final int DIAS_GRACIA = 30;

    private final ProductoService productoService;
    private final com.example.servicio.repository.ProductoRepository productoRepository;

    public ProductoPurgeScheduler(
            ProductoService productoService,
            com.example.servicio.repository.ProductoRepository productoRepository) {
        this.productoService = productoService;
        this.productoRepository = productoRepository;
    }

    /** Se ejecuta automáticamente a las 3:00 AM todos los días. */
    @Scheduled(cron = "0 0 3 * * *")
    public void purgarProductosAntiguos() {
        log.info("SCHEDULER: Buscando candidatos a purga (gracia={} días)...", DIAS_GRACIA);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(DIAS_GRACIA);
        List<Producto> candidatos = productoRepository.findPurgeCandidates(cutoff);
        int purgados = 0;

        for (Producto p : candidatos) {
            try {
                productoService.purgarProducto(p.getId());
                purgados++;
                log.warn("SCHEDULER: Producto ID {} ({}) purgado tras {} días en borrado/desaprobado",
                        p.getId(), p.getNombre(), DIAS_GRACIA);
            } catch (Exception e) {
                log.warn("SCHEDULER: No se pudo purgar producto ID {}: {}", p.getId(), e.getMessage());
            }
        }
        log.info("SCHEDULER: Purga automática completada. Eliminados: {}", purgados);
    }
}
