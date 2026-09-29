package com.example.servicio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.servicio.entity.Producto;
import com.example.servicio.repository.ProductoRepository;
import com.example.servicio.service.impl.ProductoPurgeScheduler;

/**
 * Logica del lote de purga programada, probada sin contexto de Spring.
 *
 * Lo que importa aqui es el comportamiento de "todo o nada": un producto con
 * ordenes asociadas, o un fallo puntual de Django, no debe impedir que se purguen
 * los demas candidatos. Si un fallo parase el lote, la purga de la madrugada se
 * quedaria a medias y los productos ya borrados se acumularian sin limpiar.
 *
 * Al ser un test unitario no necesita MongoDB ni el contenedor compartido.
 */
@ExtendWith(MockitoExtension.class)
class ProductoPurgeSchedulerTest {

    @Mock
    private ProductoService productoService;

    @Mock
    private ProductoRepository productoRepository;

    @InjectMocks
    private ProductoPurgeScheduler scheduler;

    private static Producto producto(String id) {
        return new Producto(id, "Producto " + id, "desc", BigDecimal.TEN,
                "REF-" + id, true, Producto.EstadoProducto.ACTIVO, 1);
    }

    @Test
    @DisplayName("Purga cada candidato devuelto por el repositorio")
    void purgaTodosLosCandidatos() {
        when(productoRepository.findPurgeCandidates(any()))
                .thenReturn(List.of(producto("a"), producto("b"), producto("c")));

        scheduler.purgarProductosAntiguos();

        verify(productoService).purgarProducto("a");
        verify(productoService).purgarProducto("b");
        verify(productoService).purgarProducto("c");
    }

    @Test
    @DisplayName("Un candidato que falla no impide purgar los siguientes")
    void unFalloNoCortaElLote() {
        when(productoRepository.findPurgeCandidates(any()))
                .thenReturn(List.of(producto("a"), producto("b"), producto("c")));
        doThrow(new RuntimeException("producto con ordenes asociadas"))
                .when(productoService).purgarProducto("b");

        scheduler.purgarProductosAntiguos();

        verify(productoService).purgarProducto("a");
        verify(productoService).purgarProducto("b");
        verify(productoService).purgarProducto("c");
    }

    @Test
    @DisplayName("Sin candidatos no invoca ninguna purga")
    void sinCandidatosNoPurga() {
        when(productoRepository.findPurgeCandidates(any())).thenReturn(List.of());

        scheduler.purgarProductosAntiguos();

        verify(productoService, never()).purgarProducto(any());
    }

    @Test
    @DisplayName("La ventana de gracia son 30 dias desde ahora")
    void consultaConTreintaDiasDeGracia() {
        when(productoRepository.findPurgeCandidates(any())).thenReturn(List.of());

        LocalDateTime antes = LocalDateTime.now().minusDays(30).minusMinutes(1);
        scheduler.purgarProductosAntiguos();
        LocalDateTime despues = LocalDateTime.now().minusDays(30).plusMinutes(1);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(productoRepository).findPurgeCandidates(captor.capture());

        assertThat(captor.getValue())
                .isBetween(antes, despues)
                .isNotNull();
        verify(productoService, never()).purgarProducto(eq(null));
    }
}
