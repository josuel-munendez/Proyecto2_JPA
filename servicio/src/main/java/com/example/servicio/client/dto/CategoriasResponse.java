package com.example.servicio.client.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Envoltorio de la respuesta de /api/internal/products/categories/.
 *
 * Django devuelve `categories` y, en el PUT de reemplazo, también `ignorados`
 * con los ids que no existían. Se ignoran a propósito en lugar de abortar: si
 * Spring reenvía una categoría que alguien desactivó entre la lectura y el
 * guardado, lo razonable es guardar el resto.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CategoriasResponse {

    private String productRef;
    private List<CategoriaDTO> categories = new ArrayList<>();

    public CategoriasResponse() {
    }

    public String getProductRef() { return productRef; }
    public void setProductRef(String productRef) { this.productRef = productRef; }

    public List<CategoriaDTO> getCategories() { return categories; }
    public void setCategories(List<CategoriaDTO> categories) { this.categories = categories; }
}
