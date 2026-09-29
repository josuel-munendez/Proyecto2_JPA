package com.example.servicio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.servicio.client.InterServiceClient;
import com.example.servicio.entity.Producto;
import com.example.servicio.entity.Producto.EstadoProducto;
import com.example.servicio.entity.ProductoAuditoria;
import com.example.servicio.entity.ProductoImagen;
import com.example.servicio.entity.Variante;
import com.example.servicio.exception.BusinessRuleException;
import com.example.servicio.exception.VersionConflictException;
import com.example.servicio.repository.ProductoAuditoriaRepository;
import com.example.servicio.repository.ProductoImagenRepository;
import com.example.servicio.repository.ProductoRepository;
import com.example.servicio.repository.VarianteRepository;
import com.example.servicio.TestcontainersConfiguration;

/**
 * Pruebas de la purga en cascada sobre un MongoDB real (Testcontainers).
 *
 * Por que Testcontainers y no una base en memoria: la purga depende de como
 * MongoDB resuelve los borrados por criterio y los indices unicos. Un mock o
 * un H2 en memoria darian verde con una implementacion que en MongoDB real
 * deja hijos huerfanos, que es exactamente el bug que esta prueba existe para
 * cazar.
 *
 * InterServiceClient va simulado: el lado de Django (PostgreSQL) se prueba
 * aparte, aqui lo que importa es la cascada dentro de MongoDB y que se llame
 * al cliente en el orden correcto.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ProductoPurgaCascadaTest {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private ProductoImagenRepository imagenRepository;

    @Autowired
    private VarianteRepository varianteRepository;

    @Autowired
    private ProductoAuditoriaRepository auditoriaRepository;

    @Autowired
    private ProductoService productoService;

    @MockitoBean
    private InterServiceClient interServiceClient;

    @BeforeEach
    void limpiar() {
        varianteRepository.deleteAll();
        imagenRepository.deleteAll();
        auditoriaRepository.deleteAll();
        productoRepository.deleteAll();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Utilidades de fixtures
    // ═══════════════════════════════════════════════════════════════════════

    /** Producto pendiente de aprobacion: por defecto no es purgable. */
    private Producto nuevoProducto(String nombre, String referencia) {
        Producto p = new Producto();
        p.setNombre(nombre);
        p.setDescripcion("descripcion de prueba");
        p.setPrecioBase(new BigDecimal("100"));
        p.setReferencia(referencia);
        p.setStock(5);
        p.setAprobado(false);
        p.setEstado(EstadoProducto.INACTIVO);
        p.setVersion(0L);
        p.onCreate();
        return productoRepository.save(p);
    }

    /** Producto con soft delete e historial: el caso que sí es purgable. */
    private Producto nuevoProductoPurgable(String nombre, String referencia) {
        Producto p = nuevoProducto(nombre, referencia);
        productoService.eliminarLogico(p.getId());
        return p;
    }

    private void registrarHistorial(String productoId, String accion) {
        ProductoAuditoria a = new ProductoAuditoria(productoId, accion, "test", Map.of(), Map.of(), "");
        a.onCreate();
        auditoriaRepository.save(a);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Cascada
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("La purga borra imagenes, variantes y el producto, y conserva la auditoria")
    void purgaBorraHijosYConservaAuditoria() {
        Producto p = nuevoProductoPurgable("Camiseta con estampado", "CAM-001");
        String id = p.getId();

        imagenRepository.save(new ProductoImagen(id, "products/cam001", true));
        imagenRepository.save(new ProductoImagen(id, "products/cam001b", false));
        varianteRepository.save(new Variante(id, "M", "Negro", 4));
        varianteRepository.save(new Variante(id, "L", "Negro", 2));
        registrarHistorial(id, ProductoAuditoria.ACTION_DELETED);

        productoService.purgarProducto(id);

        assertThat(productoRepository.findById(id)).isEmpty();
        assertThat(imagenRepository.findByProductoId(id)).isEmpty();
        assertThat(varianteRepository.findByProductoId(id)).isEmpty();

        // La auditoria sobrevive con productoId a null (paridad SET_NULL).
        assertThat(auditoriaRepository.findAll())
                .isNotEmpty()
                .allSatisfy(a -> assertThat(a.getProductoId()).isNull());
    }

    @Test
    @DisplayName("La purga pide a Django que limpie su parte antes de tocar MongoDB")
    void purgaDelegaLimpiezaEnDjango() {
        Producto p = nuevoProductoPurgable("Mochila urbana", "MOCH-002");
        String id = p.getId();
        registrarHistorial(id, ProductoAuditoria.ACTION_DISAPPROVED);

        when(interServiceClient.tieneOrdenesAsociadas(anyString())).thenReturn(false);

        productoService.purgarProducto(id);

        // Django debe recibir la limpieza de su lado (carrito, categorias, resenas).
        verify(interServiceClient).limpiarDependenciasEnDjango(id);
        assertThat(productoRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("Un producto pendiente de aprobacion NO se purga")
    void noPurgaProductoPendiente() {
        Producto p = nuevoProducto("Zapatillas urbanas", "ZAP-003");
        String id = p.getId();
        when(interServiceClient.tieneOrdenesAsociadas(anyString())).thenReturn(false);

        assertThatThrownBy(() -> productoService.purgarProducto(id))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("desaprobados");

        assertThat(productoRepository.findById(id)).isPresent();
        // No debe haber intentado limpiar nada en Django.
        verify(interServiceClient, never()).limpiarDependenciasEnDjango(anyString());
    }

    @Test
    @DisplayName("Un producto con ordenes asociadas NO se purga")
    void noPurgaProductoConOrdenes() {
        Producto p = nuevoProductoPurgable("Gorra urbana", "GOR-004");
        String id = p.getId();
        registrarHistorial(id, ProductoAuditoria.ACTION_PUBLISHED);

        when(interServiceClient.tieneOrdenesAsociadas(anyString())).thenReturn(true);

        assertThatThrownBy(() -> productoService.purgarProducto(id))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ordenes");

        assertThat(productoRepository.findById(id)).isPresent();
    }

    @Test
    @DisplayName("Un producto aprobado NO se purga aunque tenga historial")
    void noPurgaProductoAprobado() {
        Producto p = nuevoProducto("Sudadera premium", "SUD-005");
        String id = p.getId();
        registrarHistorial(id, ProductoAuditoria.ACTION_PUBLISHED);
        Producto enBd = productoRepository.findById(id).orElseThrow();
        enBd.setAprobado(true);
        productoRepository.save(enBd);

        when(interServiceClient.tieneOrdenesAsociadas(anyString())).thenReturn(false);

        assertThatThrownBy(() -> productoService.purgarProducto(id))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(productoRepository.findById(id)).isPresent();
    }

    @Test
    @DisplayName("Purgar un producto inexistente da 404, no 500")
    void purgaDeIdInexistente() {
        when(interServiceClient.tieneOrdenesAsociadas(anyString())).thenReturn(false);

        assertThatThrownBy(() -> productoService.purgarProducto("507f1f77bcf86cd799439011"))
                .isInstanceOf(com.example.servicio.exception.ResourceNotFoundException.class);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Bloqueo optimista
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Actualizar con una version desactualizada da conflicto 409")
    void conflictoDeVersion() {
        Producto p = nuevoProducto("Polo Algorithms", "POL-006");
        String id = p.getId();
        Producto enBd = productoRepository.findById(id).orElseThrow();
        enBd.setVersion(5L);
        productoRepository.save(enBd);

        com.example.servicio.dto.ProductoRequest request = new com.example.servicio.dto.ProductoRequest();
        request.setNombre("Polo Algorithms v2");
        request.setDescripcion("descripcion");
        request.setPrecioBase(new BigDecimal("200"));
        request.setReferencia("POL-006");
        request.setStock(3);
        request.setVersion(1L);

        assertThatThrownBy(() -> productoService.actualizarProducto(id, request))
                .isInstanceOf(VersionConflictException.class);
    }

    @Test
    @DisplayName("Actualizar sin version no comprueba nada (clientes legacy)")
    void actualizacionSinVersion() {
        Producto p = nuevoProducto("Vestido Panelera", "VES-007");
        String id = p.getId();

        com.example.servicio.dto.ProductoRequest request = new com.example.servicio.dto.ProductoRequest();
        request.setNombre("Vestido Panelera v2");
        request.setDescripcion("descripcion");
        request.setPrecioBase(new BigDecimal("300"));
        request.setReferencia("VES-007");
        request.setStock(3);
        // request.setVersion(...) ausente a proposito

        var res = productoService.actualizarProducto(id, request);

        assertThat(res.getNombre()).isEqualTo("Vestido Panelera v2");
        assertThat(res.getVersion()).isEqualTo(1L);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Enriquecimiento
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("La respuesta trae imagenes, variantes, stock total y readyToPublish")
    void enriquecimientoDeRespuesta() {
        Producto p = nuevoProducto("Hoodie Neon", "HOO-008");
        String id = p.getId();
        registrarHistorial(id, ProductoAuditoria.ACTION_PUBLISHED);

        // Sin imagen principal ni variantes con stock: no puede publicarse.
        var sinNada = productoService.obtenerPorId(id);
        assertThat(sinNada.getImagesCount()).isZero();
        assertThat(sinNada.getVariantsCount()).isZero();
        assertThat(sinNada.getReadyToPublish()).isFalse();
        assertThat(sinNada.getWasPublished()).isTrue();
        assertThat(sinNada.getWasDisapproved()).isFalse();

        imagenRepository.save(new ProductoImagen(id, "products/hoodie", true));
        varianteRepository.save(new Variante(id, "M", "Azul", 3));
        varianteRepository.save(new Variante(id, "L", "Azul", 5));

        var completo = productoService.obtenerPorId(id);
        assertThat(completo.getImagesCount()).isEqualTo(1L);
        assertThat(completo.getVariantsCount()).isEqualTo(2L);
        assertThat(completo.getTotalStock()).isEqualTo(8);
        assertThat(completo.getReadyToPublish()).isTrue();
        assertThat(completo.getMainImage()).isEqualTo("products/hoodie");
    }

    @Test
    @DisplayName("Los filtros combinables respetan search, flags y rango de precio")
    void filtrosCombinables() {
        nuevoProducto("Camiseta Alpha", "CAM-100");
        Producto b = nuevoProducto("Camiseta Beta", "CAM-101");
        Producto enBd = productoRepository.findById(b.getId()).orElseThrow();
        enBd.setAprobado(true);
        enBd.setIsActive(true);
        enBd.setEstado(EstadoProducto.ACTIVO);
        productoRepository.save(enBd);
        nuevoProducto("Pantalón Gamma", "PAN-102");

        // Solo los visibles (activo o aprobado).
        var visibles = productoService.listarPaginado(
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(visibles.getTotalElements()).isEqualTo(1);

        // Sin filtros: los tres.
        var todos = productoService.listarConFiltros(null, null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(todos.getTotalElements()).isEqualTo(3);

        // Por termino de busqueda.
        var porNombre = productoService.listarConFiltros("Gamma", null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(porNombre.getTotalElements()).isEqualTo(1);
        assertThat(porNombre.getContent().get(0).getNombre()).isEqualTo("Pantalón Gamma");

        // Por referencia.
        var porRef = productoService.listarConFiltros("CAM-100", null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(porRef.getTotalElements()).isEqualTo(1);

        // Por precio: ninguno cuesta mas de 150.
        var caros = productoService.listarConFiltros(null, null, null, null, new BigDecimal("150"),
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(caros.getTotalElements()).isEqualTo(3);

        var muyCaros = productoService.listarConFiltros(null, null, null, new BigDecimal("150"), null,
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(muyCaros.getTotalElements()).isZero();

        // Por bandera de aprobacion.
        var aprobados = productoService.listarConFiltros(null, null, true, null, null,
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(aprobados.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("El precio debe ser multiplo de 50 y mayor o igual a 50")
    void validacionDePrecioCop() {
        var request = new com.example.servicio.dto.ProductoRequest();
        request.setNombre("Producto Caro");
        request.setDescripcion("d");
        request.setPrecioBase(new BigDecimal("75")); // no es multiplo de 50
        request.setReferencia("CAR-001");

        assertThatThrownBy(() -> productoService.crearProducto(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("multiplo de 50");
    }

    @Test
    @DisplayName("El nombre duplicado se rechaza")
    void nombreDuplicado() {
        nuevoProducto("Camiseta Unica", "UNQ-001");

        var request = new com.example.servicio.dto.ProductoRequest();
        request.setNombre("Camiseta Unica");
        request.setDescripcion("d");
        request.setPrecioBase(new BigDecimal("100"));
        request.setReferencia("UNQ-002");

        assertThatThrownBy(() -> productoService.crearProducto(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("nombre");
    }
}
