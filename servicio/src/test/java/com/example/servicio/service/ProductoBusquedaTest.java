package com.example.servicio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.servicio.TestcontainersConfiguration;
import com.example.servicio.entity.Producto;
import com.example.servicio.entity.Producto.EstadoProducto;
import com.example.servicio.exception.ResourceNotFoundException;
import com.example.servicio.entity.ProductoImagen;
import com.example.servicio.entity.Variante;
import com.example.servicio.repository.ProductoAuditoriaRepository;
import com.example.servicio.repository.ProductoImagenRepository;
import com.example.servicio.repository.ProductoRepository;
import com.example.servicio.repository.VarianteRepository;
import com.example.servicio.client.InterServiceClient;

/**
 * Semantica de la busqueda de productos sobre MongoDB real.
 *
 * El foco es que lo que escribe el usuario se trate como TEXTO LITERAL y no
 * como un patron de regex. Si el termino se pasa crudo a {@code $regex}, una
 * busqueda de ".*" devuelve el catalogo entero y un patron sin cerrar deja la
 * consulta colgada. Las dos rutas de busqueda deben comportarse igual.
 *
 * Reutiliza el contenedor MongoDB compartido de {@link TestcontainersConfiguration}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ProductoBusquedaTest {

    private static final PageRequest PAGINA = PageRequest.of(0, 10);

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

    private void guardar(String nombre, String referencia) {
        productoRepository.save(new Producto(null, nombre, "descripcion", new java.math.BigDecimal("100"),
                referencia, true, EstadoProducto.ACTIVO, 1));
    }

    @Test
    @DisplayName("buscarPor3CamposOr trata '.*' como texto, no devuelve el catalogo")
    void elComodinNoTraeTodoEnLaBusquedaPorTresCampos() {
        guardar("Camiseta Regex", "REG-001");
        guardar("Camiseta Sencilla", "SEN-002");
        guardar("Pantalon Largo", "PAN-003");

        // Con el termino en crudo, '.*' coincide con cualquier texto y
        // devolveria los tres productos.
        var res = productoService.buscarPor3CamposOr(".*", PAGINA);

        assertThat(res.getTotalElements())
                .as("'.*' es texto literal: ningun producto contiene esos caracteres")
                .isZero();
    }

    @Test
    @DisplayName("buscarPor3CamposOr encuentra por subcadena real")
    void encuentraPorSubcadena() {
        guardar("Camiseta Regex", "REG-001");
        guardar("Camiseta Sencilla", "SEN-002");

        var res = productoService.buscarPor3CamposOr("Sencilla", PAGINA);

        assertThat(res.getTotalElements()).isEqualTo(1);
        assertThat(res.getContent().get(0).getNombre()).isEqualTo("Camiseta Sencilla");
    }

    @Test
    @DisplayName("buscarPor3CamposOr encuentra por referencia")
    void encuentraPorReferencia() {
        guardar("Camiseta Regex", "REG-001");
        guardar("Camiseta Sencilla", "SEN-002");

        var res = productoService.buscarPor3CamposOr("REG-001", PAGINA);

        assertThat(res.getTotalElements()).isEqualTo(1);
        assertThat(res.getContent().get(0).getReferencia()).isEqualTo("REG-001");
    }

    @Test
    @DisplayName("Un patron invalido no rompe la consulta")
    void unPatronInvalidoNoRompeLaConsulta() {
        guardar("Camiseta Regex", "REG-001");

        var res = productoService.buscarPor3CamposOr("Cafete(", PAGINA);

        assertThat(res.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("listarConFiltros tambien trata '.*' como texto literal")
    void elComodinNoTraeTodoEnLosFiltros() {
        guardar("Camiseta Regex", "REG-001");
        guardar("Camiseta Sencilla", "SEN-002");
        guardar("Pantalon Largo", "PAN-003");

        var res = productoService.listarConFiltros(".*", null, null, null, null, PAGINA);

        assertThat(res.getTotalElements())
                .as("listarConFiltros escapa el termino y no debe Traer el catalogo entero")
                .isZero();
    }

    @Test
    @DisplayName("El detalle trae imagenes y variantes desde MongoDB")
    void elDetalleTraeLosSubrecursos() {
        Producto p = productoRepository.save(new Producto(null, "Camiseta Detalle",
                "descripcion", new java.math.BigDecimal("100"), "DET-001", true,
                EstadoProducto.ACTIVO, 1));
        String id = p.getId();

        ProductoImagen imagen = new ProductoImagen();
        imagen.setProductoId(id);
        imagen.setImage("https://res.cloudinary.com/demo/image/upload/camiseta.png");
        imagen.setEsPrincipal(true);
        imagenRepository.save(imagen);

        Variante conPrecio = new Variante();
        conPrecio.setProductoId(id);
        conPrecio.setSize("G");
        conPrecio.setColor("Rojo");
        conPrecio.setColorHex("#FF0000");
        conPrecio.setStock(3);
        conPrecio.setPriceVariant(new java.math.BigDecimal("120.00"));
        varianteRepository.save(conPrecio);

        Variante sinPrecio = new Variante();
        sinPrecio.setProductoId(id);
        sinPrecio.setSize("M");
        sinPrecio.setColor("Azul");
        sinPrecio.setColorHex("#0000FF");
        sinPrecio.setStock(7);
        varianteRepository.save(sinPrecio);

        var detalle = productoService.obtenerPorId(id);

        // El frontend delega en Spring estos subrecursos, asi que tienen que
        // venir completos: si vuelven vacios, el admin pierde la galeria y las
        // variantes sin ningun error visible.
        assertThat(detalle.getImagenes()).hasSize(1);
        assertThat(detalle.getImagenes().get(0).getEsPrincipal()).isTrue();
        assertThat(detalle.getVariantes()).hasSize(2);

        // precioEfectivo replica la regla de Django: override si existe, y si no
        // el precio base del producto.
        var varianteConPrecio = detalle.getVariantes().stream()
                .filter(v -> "G".equals(v.getSize()))
                .findFirst()
                .orElseThrow();
        var varianteSinPrecio = detalle.getVariantes().stream()
                .filter(v -> "M".equals(v.getSize()))
                .findFirst()
                .orElseThrow();
        assertThat(varianteConPrecio.getPrecioEfectivo()).isEqualByComparingTo("120.00");
        assertThat(varianteSinPrecio.getPrecioEfectivo()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("El listado NO arrastra las listas completas, solo los conteos")
    void elListadoNoArrastraLasListas() {
        Producto p = productoRepository.save(new Producto(null, "Camiseta Ligera",
                "descripcion", new java.math.BigDecimal("100"), "LIG-001", true,
                EstadoProducto.ACTIVO, 1));
        ProductoImagen imagen = new ProductoImagen();
        imagen.setProductoId(p.getId());
        imagen.setImage("https://res.cloudinary.com/demo/image/upload/x.png");
        imagen.setEsPrincipal(true);
        imagenRepository.save(imagen);

        Variante variante = new Variante();
        variante.setProductoId(p.getId());
        variante.setSize("M");
        variante.setColor("Azul");
        variante.setStock(7);
        varianteRepository.save(variante);

        var pagina = productoService.listarPaginado(PAGINA);

        assertThat(pagina.getContent()).isNotEmpty();
        assertThat(pagina.getContent().get(0).getImagesCount()).isEqualTo(1L);
        assertThat(pagina.getContent().get(0).getVariantsCount()).isEqualTo(1L);
        // Traer 2 listas por producto en un listado de 20 serian 40 consultas
        // y una respuesta enorme para datos que la vista no usa.
        assertThat(pagina.getContent().get(0).getImagenes()).isEmpty();
        assertThat(pagina.getContent().get(0).getVariantes()).isEmpty();
    }

    @Test
    @DisplayName("el detalle de un producto borrado da 404, no 200 con el producto muerto")
    void elDetalleDeUnProductoBorradoNoSeVe() {
        guardar("Producto a eliminar", "BOR-001");
        var guardado = productoRepository.findByEstadoNot(EstadoProducto.BORRADO, PAGINA)
                .getContent().get(0);
        productoService.eliminarLogico(guardado.getId());

        // El listado ya ocultaba los BORRADO, pero el detalle iba con findById
        // a secas y devolvia 200 con el producto entero. Asi, abrir el detalle
        // de algo ya eliminado lo resucitaba en pantalla y el admin podia
        // seguir editandolo.
        assertThat(productoRepository.findById(guardado.getId()))
                .as("el documento sigue en Mongo: es borrado logico, no fisico")
                .isPresent();

        assertThatThrownBy(() -> productoService.obtenerPorId(guardado.getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(guardado.getId());
    }

    @Test
    @DisplayName("un producto activo sigue viendose en el detalle")
    void elDetalleDeUnProductoActivoSeVe() {
        guardar("Producto visible", "VIS-001");
        var guardado = productoRepository.findByEstadoNot(EstadoProducto.BORRADO, PAGINA)
                .getContent().get(0);

        var respuesta = productoService.obtenerPorId(guardado.getId());

        assertThat(respuesta.getId()).isEqualTo(guardado.getId());
        assertThat(respuesta.getNombre()).isEqualTo("Producto visible");
    }

    @Test
    @DisplayName("un producto inactivo o pendiente tambien se ve: solo se ocultan los borrados")
    void elDetalleNoOcultaLosQueSoloEstanInactivos() {
        // El filtro es por BORRADO, no por isActive: un producto desactivado
        // sigue siendo real y el panel de administracion tiene que poder abrirlo.
        productoRepository.save(new Producto(null, "Inactivo", "descripcion",
                new java.math.BigDecimal("100"), "INA-001", false, EstadoProducto.INACTIVO, 1));

        var guardado = productoRepository.findByEstadoNot(EstadoProducto.BORRADO, PAGINA)
                .getContent().get(0);

        assertThat(productoService.obtenerPorId(guardado.getId()).getId())
                .isEqualTo(guardado.getId());
    }
}
