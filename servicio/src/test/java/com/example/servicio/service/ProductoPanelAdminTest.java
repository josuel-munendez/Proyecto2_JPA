package com.example.servicio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.servicio.TestcontainersConfiguration;
import com.example.servicio.client.InterServiceClient;
import com.example.servicio.client.dto.CategoriaDTO;
import com.example.servicio.dto.ProductoImagenDTO;
import com.example.servicio.dto.ProductoRequest;
import com.example.servicio.dto.VarianteDTO;
import com.example.servicio.entity.Producto;
import com.example.servicio.entity.Producto.EstadoProducto;
import com.example.servicio.entity.ProductoAuditoria;
import com.example.servicio.entity.ProductoImagen;
import com.example.servicio.entity.Variante;
import com.example.servicio.exception.InterServiceException;
import com.example.servicio.exception.ResourceNotFoundException;
import com.example.servicio.repository.ProductoAuditoriaRepository;
import com.example.servicio.repository.ProductoImagenRepository;
import com.example.servicio.repository.ProductoRepository;
import com.example.servicio.repository.VarianteRepository;

/**
 * Operaciones del panel de administración sobre MongoDB.
 *
 * Estas operaciones existían en Django y desaparecieron al mover los productos a
 * Mongo: Django no puede resolver un ObjectId, así que ahora las resuelve
 * Spring. Aquí se fija el comportamiento con el que el panel depende:
 * la auditoría se escribe, desaprobar deja el producto inactivo y pendiente,
 * y las categorías viajan a Django solo cuando el cliente las manda.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ProductoPanelAdminTest {

    @Autowired private ProductoRepository productoRepository;
    @Autowired private ProductoImagenRepository imagenRepository;
    @Autowired private VarianteRepository varianteRepository;
    @Autowired private ProductoAuditoriaRepository auditoriaRepository;
    @Autowired private ProductoService productoService;

    @MockitoBean private InterServiceClient interServiceClient;

    @BeforeEach
    void limpiar() {
        varianteRepository.deleteAll();
        imagenRepository.deleteAll();
        auditoriaRepository.deleteAll();
        productoRepository.deleteAll();
    }

    private int secuencia;

    /**
     * Cada producto necesita nombre y referencia distintos: la unicidad del
     * nombre es una regla de negocio real, y saltársela con un @Disabled
     * escondería justo los tests que la necesitan.
     */
    private ProductoRequest requestValido() {
        secuencia++;
        ProductoRequest r = new ProductoRequest();
        r.setNombre("Camiseta Panel " + secuencia);
        r.setPrecioBase(new BigDecimal("50000"));
        r.setReferencia("PAN-" + secuencia);
        r.setStock(5);
        return r;
    }

    // ── Auditoría ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("crear deja historial 'created' y listarlo lo devuelve")
    void laAuditoriaSeRegistraAlCrear() {
        var creado = productoService.crearProducto(requestValido());

        List<ProductoAuditoria> historial = productoService.listarAuditorias(creado.getId());

        assertThat(historial).hasSize(1);
        assertThat(historial.get(0).getAction()).isEqualTo(ProductoAuditoria.ACTION_CREATED);
        assertThat(historial.get(0).getProductoId()).isEqualTo(creado.getId());
    }

    @Test
    @DisplayName("el historial se lee del más nuevo al más viejo")
    void elHistorialVaDelMasNuevoAlMasViejo() {
        var creado = productoService.crearProducto(requestValido());
        ProductoRequest cambios = requestValido();
        cambios.setNombre("Camiseta Panel Renombrada");
        productoService.actualizarProducto(creado.getId(), cambios);

        List<ProductoAuditoria> historial = productoService.listarAuditorias(creado.getId());

        assertThat(historial).hasSize(2);
        assertThat(historial.get(0).getAction()).isEqualTo(ProductoAuditoria.ACTION_UPDATED);
        assertThat(historial.get(1).getAction()).isEqualTo(ProductoAuditoria.ACTION_CREATED);
    }

    @Test
    @DisplayName("auditar un producto inexistente da 404, no una lista vacía")
    void auditarInexistenteFalla() {
        assertThatThrownBy(() -> productoService.listarAuditorias("507f1f77bcf86cd799439099"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Desaprobar ────────────────────────────────────────────────────────

    @Test
    @DisplayName("desaprobar desactiva, deja pendiente y guarda el motivo")
    void desaprobarDesactivaYGuardaElMotivo() {
        var creado = productoService.crearProducto(requestValido());
        Producto p = productoRepository.findById(creado.getId()).orElseThrow();
        p.setAprobado(true);
        p.setIsActive(true);
        p.setEstado(EstadoProducto.ACTIVO);
        productoRepository.save(p);

        var res = productoService.desaprobar(creado.getId(), "Fotos de producto borrosas");

        assertThat(res.getIsActive()).isFalse();
        assertThat(res.getAprobado()).isFalse();

        var auditoria = productoService.listarAuditorias(creado.getId()).get(0);
        assertThat(auditoria.getAction()).isEqualTo(ProductoAuditoria.ACTION_DISAPPROVED);
        assertThat(auditoria.getMotivo()).isEqualTo("Fotos de producto borrosas");
    }

    @Test
    @DisplayName("desaprobar sin motivo funciona igual y lo guarda como vacío")
    void desaprobarSinMotivo() {
        var creado = productoService.crearProducto(requestValido());

        var res = productoService.desaprobar(creado.getId(), null);

        assertThat(res.getIsActive()).isFalse();
        assertThat(productoService.listarAuditorias(creado.getId()).get(0).getMotivo()).isEmpty();
    }

    // ── Activar / desactivar ──────────────────────────────────────────────

    @Test
    @DisplayName("alternar activo devuelve el estado en vez de dejarlo adivinar")
    void alternarActivoDevuelveElEstado() {
        var creado = productoService.crearProducto(requestValido());

        // Los productos nuevos entran inactivos, asi que el primer toggle activa.
        var activado = productoService.cambiarActivo(creado.getId());
        assertThat(activado.getIsActive()).isTrue();

        var desactivado = productoService.cambiarActivo(creado.getId());
        assertThat(desactivado.getIsActive()).isFalse();
    }

    // ── Imágenes ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("la primera imagen sin marca queda siendo la principal")
    void laPrimeraImagenQuedaPrincipal() {
        var creado = productoService.crearProducto(requestValido());

        ProductoImagenDTO imagen = productoService.agregarImagen(
                creado.getId(), "https://cdn.example.com/a.png", false);

        assertThat(imagen.getEsPrincipal()).isTrue();
    }

    @Test
    @DisplayName("una nueva principal desplaza a la anterior")
    void laNuevaPrincipalDesplazaALaAnterior() {
        var creado = productoService.crearProducto(requestValido());
        productoService.agregarImagen(creado.getId(), "https://cdn.example.com/a.png", false);

        ProductoImagenDTO segunda = productoService.agregarImagen(
                creado.getId(), "https://cdn.example.com/b.png", true);

        assertThat(segunda.getEsPrincipal()).isTrue();
        assertThat(imagenRepository.findByProductoIdOrderByCreatedAtAsc(creado.getId()))
                .filteredOn(i -> Boolean.TRUE.equals(i.getEsPrincipal()))
                .as("solo puede haber una imagen principal por producto")
                .hasSize(1);
    }

    @Test
    @DisplayName("promover una imagen existente deja una sola principal")
    void promoverDejaUnaSolaPrincipal() {
        var creado = productoService.crearProducto(requestValido());
        productoService.agregarImagen(creado.getId(), "https://cdn.example.com/a.png", false);
        ProductoImagenDTO segunda = productoService.agregarImagen(
                creado.getId(), "https://cdn.example.com/b.png", true);

        ProductoImagenDTO promovida = productoService.marcarImagenPrincipal(
                creado.getId(), segunda.getId());

        assertThat(promovida.getEsPrincipal()).isTrue();
        assertThat(imagenRepository.findByProductoIdOrderByCreatedAtAsc(creado.getId()))
                .filteredOn(i -> Boolean.TRUE.equals(i.getEsPrincipal()))
                .hasSize(1);
    }

    @Test
    @DisplayName("no se puede promover una imagen de otro producto")
    void noSePuedePromoverImagenAjena() {
        var primero = productoService.crearProducto(requestValido());
        var segundo = productoService.crearProducto(requestValido());
        var ajena = productoService.agregarImagen(
                segundo.getId(), "https://cdn.example.com/ajena.png", false);

        assertThatThrownBy(() -> productoService.marcarImagenPrincipal(primero.getId(), ajena.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("quitar una imagen exige que pertenezca al producto")
    void noSePuedeQuitarUnaImagenDeOtroProducto() {
        var primero = productoService.crearProducto(requestValido());
        var segundo = productoService.crearProducto(requestValido());
        var ajena = productoService.agregarImagen(
                segundo.getId(), "https://cdn.example.com/ajena.png", false);

        assertThatThrownBy(() -> productoService.eliminarImagen(primero.getId(), ajena.getId()))
                .as("si no, un producto podría borrar imágenes de otro")
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("no se acepta una imagen sin URL")
    void imagenSinUrlFalla() {
        var creado = productoService.crearProducto(requestValido());

        assertThatThrownBy(() -> productoService.agregarImagen(creado.getId(), "  ", false))
                .isInstanceOf(com.example.servicio.exception.BusinessRuleException.class);
    }

    // ── Variantes ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("guardar variante sin id la crea y con id la actualiza")
    void guardarVarianteCreaYActualiza() {
        var creado = productoService.crearProducto(requestValido());
        VarianteDTO datos = new VarianteDTO();
        datos.setSize("M");
        datos.setColor("Azul");
        datos.setStock(3);

        VarianteDTO creada = productoService.guardarVariante(creado.getId(), datos, null);
        assertThat(creada.getId()).isNotNull();
        assertThat(creada.getSize()).isEqualTo("M");

        datos.setColor("Verde");
        VarianteDTO actualizada = productoService.guardarVariante(creado.getId(), datos, creada.getId());
        assertThat(actualizada.getColor()).isEqualTo("Verde");
        assertThat(varianteRepository.count()).as("actualizar no debe crear una segunda fila").isEqualTo(1);
    }

    @Test
    @DisplayName("no se puede editar una variante de otro producto")
    void noSePuedeEditarVarianteAjena() {
        var primero = productoService.crearProducto(requestValido());
        var segundo = productoService.crearProducto(requestValido());
        VarianteDTO datos = new VarianteDTO();
        datos.setSize("L");
        VarianteDTO ajena = productoService.guardarVariante(segundo.getId(), datos, null);

        assertThatThrownBy(() -> productoService.guardarVariante(primero.getId(), datos, ajena.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("borrar una variante solo la quita a ella")
    void borrarVarianteNoAfectaALasDemas() {
        var creado = productoService.crearProducto(requestValido());
        VarianteDTO datos = new VarianteDTO();
        datos.setSize("S");
        var una = productoService.guardarVariante(creado.getId(), datos, null);
        productoService.guardarVariante(creado.getId(), datos, null);

        productoService.eliminarVariante(creado.getId(), una.getId());

        assertThat(varianteRepository.findByProductoIdOrderByIdAsc(creado.getId())).hasSize(1);
    }

    // ── Categorías ────────────────────────────────────────────────────────

    @Test
    @DisplayName("el detalle trae las categorías que devuelve Django")
    void elDetalleTraeLasCategorias() {
        var creado = productoService.crearProducto(requestValido());
        when(interServiceClient.obtenerCategorias(anyString())).thenReturn(
                List.of(new CategoriaDTO(1L, "Cafes"), new CategoriaDTO(4L, "Ropa")));

        var detalle = productoService.obtenerPorId(creado.getId());

        assertThat(detalle.getCategorias())
                .extracting(CategoriaDTO::getName)
                .containsExactly("Cafes", "Ropa");
    }

    @Test
    @DisplayName("el detalle no inventa categorías si Django no devolvió ninguna")
    void sinCategoriasDevuelveListaVaciaYNoNull() {
        var creado = productoService.crearProducto(requestValido());
        when(interServiceClient.obtenerCategorias(anyString())).thenReturn(List.of());

        var detalle = productoService.obtenerPorId(creado.getId());

        assertThat(detalle.getCategorias())
                .as("un null aquí rompería el .map() del frontend")
                .isNotNull().isEmpty();
    }

    @Test
    @DisplayName("crear manda las categorías a Django")
    void crearMandaLasCategorias() {
        ProductoRequest r = requestValido();
        r.setCategoriaIds(List.of(1L, 2L));

        var creado = productoService.crearProducto(r);

        verify(interServiceClient).reemplazarCategorias(creado.getId(), List.of(1L, 2L));
    }

    @Test
    @DisplayName("crear sin tocar categorías no borra las que ya había")
    void sinCampoCategoriasNoSeTocaNada() {
        // El caso que rompía la edición: si el cliente no manda categoriaIds,
        // procurar las vacías convertiría "no lo sé" en "quítamelas todas".
        ProductoRequest r = requestValido();
        assertThat(r.getCategoriaIds()).isNull();

        var creado = productoService.crearProducto(r);

        verify(interServiceClient, never()).reemplazarCategorias(anyString(), any());
    }

    @Test
    @DisplayName("una lista vacía sí quita todas las categorías")
    void listaVaciaQuitaTodas() {
        ProductoRequest r = requestValido();
        r.setCategoriaIds(List.of());

        var creado = productoService.crearProducto(r);

        verify(interServiceClient).reemplazarCategorias(creado.getId(), List.of());
    }

    @Test
    @DisplayName("actualizar replica las categorías después de validar la versión")
    void actualizarReplicaLasCategorias() {
        var creado = productoService.crearProducto(requestValido());
        ProductoRequest cambios = requestValido();
        cambios.setNombre("Camiseta Renombrada");
        cambios.setVersion(productoRepository.findById(creado.getId()).orElseThrow().getVersion());
        cambios.setCategoriaIds(List.of(7L));

        productoService.actualizarProducto(creado.getId(), cambios);

        verify(interServiceClient).reemplazarCategorias(creado.getId(), List.of(7L));
    }

    @Test
    @DisplayName("si Django rechaza las categorías, el guardado falla en vez de fingir")
    void falloEnDjangoNoSeFinge() {
        var creado = productoService.crearProducto(requestValido());
        ProductoRequest cambios = requestValido();
        cambios.setNombre("Camiseta Renombrada");
        cambios.setVersion(productoRepository.findById(creado.getId()).orElseThrow().getVersion());
        cambios.setCategoriaIds(List.of(7L));
        org.mockito.Mockito.doThrow(new InterServiceException("Django caido", new IllegalStateException()))
                .when(interServiceClient).reemplazarCategorias(anyString(), any());

        assertThatThrownBy(() -> productoService.actualizarProducto(creado.getId(), cambios))
                .as("si no, el usuario vería 'guardado' con las categorías sin guardar")
                .isInstanceOf(InterServiceException.class);
    }

    @Test
    @DisplayName("el listado no llama a Django por producto")
    void elListadoNoLlamaADjangoPorProducto() {
        var creado = productoService.crearProducto(requestValido());
        productoService.cambiarActivo(creado.getId());

        var pagina = productoService.listarPaginado(
                org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(1);
        verify(interServiceClient, never()).obtenerCategorias(anyString());
    }
}
