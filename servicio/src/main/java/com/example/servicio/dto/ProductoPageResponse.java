package com.example.servicio.dto;

import org.springframework.data.domain.Page;
import java.util.List;

/**
 * ============================================================================
 * DTO: ProductoPageResponse (Data Transfer Object para Respuestas Paginadas)
 * ============================================================================
 * Estructura de salida estandarizada para envolver colecciones paginadas de productos.
 *
 * Propósito:
 * - Evita exponer metadatos internos complejos de Spring Data (`org.springframework.data.domain.PageImpl`).
 * - Facilita el consumo desde el Frontend (React / Fetch API) entregando una estructura limpia:
 *   {
 *     "content": [...],
 *     "pageNumber": 0,
 *     "pageSize": 10,
 *     "totalElements": 45,
 *     "totalPages": 5,
 *     "last": false
 *   }
 * ============================================================================
 */
public class ProductoPageResponse {

    /** Lista de elementos de la página actual */
    private List<ProductoResponse> content;

    /** Índice de la página actual (base 0) */
    private int pageNumber;

    /** Cantidad máxima de elementos por página */
    private int pageSize;

    /** Cantidad total de registros existentes que coinciden con los filtros */
    private long totalElements;

    /** Cantidad total de páginas calculadas */
    private int totalPages;

    /** Bandera que indica si es la última página disponible */
    private boolean last;

    /**
     * Método fábrica estático (Static Factory Method) para transformar un objeto Page de Spring Data
     * en una instancia estandarizada de ProductoPageResponse.
     *
     * @param page Objeto Page generado por el repositorio/servicio.
     * @return Instancia DTO lista para serialización JSON.
     */
    public static ProductoPageResponse fromPage(Page<ProductoResponse> page) {
        ProductoPageResponse response = new ProductoPageResponse();
        response.setContent(page.getContent());
        response.setPageNumber(page.getNumber());
        response.setPageSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setLast(page.isLast());
        return response;
    }

    // ========================================================================
    // GETTERS Y SETTERS
    // ========================================================================

    public List<ProductoResponse> getContent() { return content; }
    public void setContent(List<ProductoResponse> content) { this.content = content; }

    public int getPageNumber() { return pageNumber; }
    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }

    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public boolean isLast() { return last; }
    public void setLast(boolean last) { this.last = last; }
}
