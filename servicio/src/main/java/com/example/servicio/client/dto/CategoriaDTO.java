package com.example.servicio.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Categoría tal y como la devuelve Django (catalog_category).
 *
 * El id sigue siendo un entero porque las categorías NO se movieron a
 * MongoDB: son pocas, las comparten muchos productos y el resto de Django las
 * usa. Lo único que cambia respecto a la rama PostgreSQL es que la relación con
 * el producto se guarda por product_ref en vez de por FK.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CategoriaDTO {

    private Long id;
    private String name;

    public CategoriaDTO() {
    }

    public CategoriaDTO(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
