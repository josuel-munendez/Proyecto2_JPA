package com.example.servicio.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.example.servicio.client.dto.CategoriaDTO;
import com.example.servicio.exception.InterServiceException;

/**
 * Contrato HTTP entre Spring y los endpoints internos de categorías de Django,
 * y comportamiento cuando Django no responde.
 *
 * La asimetría es deliberada y es lo que hay que fijar aquí: leer tolera caídas,
 * escribir no. Leer solo afecta a lo que se ve en pantalla, así que devolver
 * vacío es aceptable; escribir, no. Si un usuario desmarca una categoría y el
 * guardado responde éxito sin haberlo hecho, se pierde el cambio sin enterarse.
 *
 * Se usa un servidor simulado en vez de mocks de la API fluida de RestClient
 * porque lo que importa aquí es el HTTP de verdad: método, URL, header de
 * token y body. Con mocks encadenados, un cambio en la URL o en el nombre del
 * token pasaba los tests sin que nadie lo notara.
 */
class InterServiceClientCategoriasTest {

    private static final String REF = "507f1f77bcf86cd799439011";
    private static final String TOKEN = "token-de-pruebas";

    /** El cliente expande el {ref} antes de enviar, así que aquí va ya expandido. */
    private static final String URL_LEER =
            "http://django.test/api/internal/products/categories/" + REF + "/";
    private static final String URL_ESCRIBIR =
            "http://django.test/api/internal/products/categories/" + REF + "/set/";

    private final RestClient.Builder builder = RestClient.builder()
            .baseUrl("http://django.test")
            .defaultHeader("X-Internal-Token", TOKEN);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final InterServiceClient cliente = new InterServiceClient(builder.build());

    @Test
    @DisplayName("leer categorías consulta la URL interna y devuelve la lista")
    void leerDevuelveLoQueDiceDjango() {
        server.expect(requestTo(URL_LEER))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withSuccess(
                        """
                        {"product_ref": "%s", "categories": [
                            {"id": 1, "name": "Cafes"},
                            {"id": 2, "name": "Ropa"}
                        ]}
                        """.formatted(REF),
                        MediaType.APPLICATION_JSON));

        List<CategoriaDTO> categorias = cliente.obtenerCategorias(REF);

        assertThat(categorias).extracting(CategoriaDTO::getId).containsExactly(1L, 2L);
        assertThat(categorias).extracting(CategoriaDTO::getName).containsExactly("Cafes", "Ropa");
        server.verify();
    }

    @Test
    @DisplayName("si Django cae, leer devuelve vacío y no revienta el detalle")
    void leerDegradaAUnaListaVacia() {
        server.expect(requestTo(URL_LEER))
                .andRespond(withServerError());

        assertThat(cliente.obtenerCategorias(REF))
                .as("mejor un detalle sin categorías que ninguna pantalla")
                .isNotNull()
                .isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("si Django devuelve un body sin categories, tampoco se devuelve null")
    void leerCuerpoVacioDevuelveLista() {
        server.expect(requestTo(URL_LEER))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(cliente.obtenerCategorias(REF)).isNotNull().isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("escribir manda PUT con el conjunto completo y el token")
    void escribirMandaElConjuntoCompleto() {
        server.expect(requestTo(URL_ESCRIBIR))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andExpect(content().json("{\"categoria_ids\": [1, 4]}"))
                .andRespond(withSuccess("{\"categories\": []}", MediaType.APPLICATION_JSON));

        cliente.reemplazarCategorias(REF, List.of(1L, 4L));

        server.verify();
    }

    @Test
    @DisplayName("escribir null se manda como lista vacía, no como body nulo")
    void escribirNullSeNormaliza() {
        // Map.of no admite null: sin normalizar esto sería un NPE al construir
        // el body, en lugar de un "este producto no tiene categorías".
        server.expect(requestTo(URL_ESCRIBIR))
                .andExpect(content().json("{\"categoria_ids\": []}"))
                .andRespond(withSuccess("{\"categories\": []}", MediaType.APPLICATION_JSON));

        cliente.reemplazarCategorias(REF, null);

        server.verify();
    }

    @Test
    @DisplayName("si Django cae al escribir, el error sube hasta el usuario")
    void escribirFallaEnVezDeFingir() {
        server.expect(requestTo(URL_ESCRIBIR))
                .andRespond(withServerError());

        assertThatThrownBy(() -> cliente.reemplazarCategorias(REF, List.of(1L)))
                .as("un guardado a medias que dice 'ok' es peor que un error")
                .isInstanceOf(InterServiceException.class)
                .hasMessageContaining(REF);
        server.verify();
    }
    // El public_id va como query param: lleva barras y en la ruta se
    // codificarían como %2F, que no todos los proxies respetan.
    private static final String URL_ARCHIVO =
            "http://django.test/api/internal/products/archivos/?path=products%2F2026%2F09%2Fabc123";

    @Test
    @DisplayName("borrar el archivo avisa a Django con DELETE y el public_id en la query")
    void borraElArchivoEnDjango() {
        server.expect(requestTo(URL_ARCHIVO))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withSuccess());

        cliente.eliminarArchivoEnDjango("products/2026/09/abc123");

        server.verify();
    }

    @Test
    @DisplayName("no llama a Django si el public_id viene vacío")
    void noIntentaBorrarSiElPublicIdVieneVacio() {
        cliente.eliminarArchivoEnDjango(null);
        cliente.eliminarArchivoEnDjango("   ");

        // Sin expect() no hay peticiones: si se enviara alguna, verify() fallaría.
        server.verify();
    }

    @Test
    @DisplayName("un fallo de Django no propaga: el documento ya se borró de Mongo")
    void unFalloDeDjangoNoRompeLaEliminacion() {
        // El documento de la imagen se borra de MongoDB antes de avisar a
        // Django. Si esta llamada fallara hacia arriba, el usuario veria un
        // error por un archivo que ya no le afecta a su producto, cuando en
        // realidad lo que quiero es que la eliminacion termine.
        server.expect(requestTo(URL_ARCHIVO)).andRespond(withServerError());

        assertThatNoException()
                .isThrownBy(() -> cliente.eliminarArchivoEnDjango("products/2026/09/abc123"));
    }

    @Test
    @DisplayName("un fallo de Django deja el archivo huérfano y se avisa por log")
    void unFalloDeDjangoQuedaRegistrado() {
        server.expect(requestTo(URL_ARCHIVO)).andRespond(withServerError());

        cliente.eliminarArchivoEnDjango("products/2026/09/abc123");

        // El warning tiene que decir que queda basura en Cloudinary, para que
        // se pueda limpiar en una pasada posterior.
        server.verify();
    }

}
