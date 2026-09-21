package com.example.servicio.dto;

import org.springframework.data.domain.Page;
import java.util.List;

public class ProductoPageResponse {
    private List<ProductoResponse> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean last;

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
