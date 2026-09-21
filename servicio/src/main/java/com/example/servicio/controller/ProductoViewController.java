package com.example.servicio.controller;

import com.example.servicio.dto.ProductoRequest;
import com.example.servicio.dto.ProductoResponse;
import com.example.servicio.entity.Producto.EstadoProducto;
import com.example.servicio.service.ProductoService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controlador Thymeleaf para JPA/PostgreSQL - Vistas del CRUD de productos.
 * Maneja paginacion, busqueda y operaciones CRUD con validacion.
 */
@Controller
@RequestMapping("/productos")
public class ProductoViewController {

    private static final Logger log = LoggerFactory.getLogger(ProductoViewController.class);
    private final ProductoService productoService;

    public ProductoViewController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping
    public String listarProductos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(required = false) String search,
            Model model) {

        log.info("JPA VIEW: Listando productos - pagina={}, tamanio={}, search={}", page, size, search);

        Pageable pageable = PageRequest.of(page, size);
        Page<ProductoResponse> productosPage;

        if (search != null && !search.isBlank()) {
            log.info("JPA VIEW: Busqueda OR (3 campos) con termino '{}'", search);
            productosPage = productoService.buscarPor3CamposOr(search, pageable);
        } else {
            productosPage = productoService.listarPaginado(pageable);
        }

        model.addAttribute("productosPage", productosPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", productosPage.getTotalPages());
        model.addAttribute("search", search);

        return "productos/index";
    }

    @GetMapping("/nuevo")
    public String mostrarFormularioCrear(Model model) {
        log.info("JPA VIEW: Mostrando formulario de creacion de producto");
        model.addAttribute("productoRequest", new ProductoRequest());
        model.addAttribute("titulo", "Crear Nuevo Producto");
        return "productos/form";
    }

    @PostMapping("/guardar")
    public String guardarProducto(
            @Valid @ModelAttribute("productoRequest") ProductoRequest request,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            log.warn("JPA VIEW: Errores de validacion al guardar producto: {}", bindingResult.getAllErrors());
            model.addAttribute("titulo", "Crear Nuevo Producto");
            return "productos/form";
        }

        log.info("JPA VIEW: Guardando producto - referencia={}", request.getReferencia());
        productoService.crearProducto(request);
        redirectAttributes.addFlashAttribute("mensajeExito", "Producto guardado correctamente");
        return "redirect:/productos";
    }

    @GetMapping("/editar/{id}")
    public String mostrarFormularioEditar(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("JPA VIEW: Mostrando formulario de edicion para producto ID {}", id);

        try {
            ProductoResponse response = productoService.obtenerPorId(id);
            ProductoRequest request = new ProductoRequest();
            request.setNombre(response.getNombre());
            request.setDescripcion(response.getDescripcion());
            request.setPrecioBase(response.getPrecioBase());
            request.setReferencia(response.getReferencia());
            request.setStock(response.getStock());

            model.addAttribute("productoRequest", request);
            model.addAttribute("productoId", id);
            model.addAttribute("titulo", "Editar Producto #" + id);
            return "productos/form";
        } catch (Exception e) {
            log.error("JPA VIEW: Error al cargar producto ID {} para edicion: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("mensajeError", "Producto no encontrado");
            return "redirect:/productos";
        }
    }

    @PostMapping("/actualizar/{id}")
    public String actualizarProducto(
            @PathVariable Long id,
            @Valid @ModelAttribute("productoRequest") ProductoRequest request,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            log.warn("JPA VIEW: Errores de validacion al actualizar producto ID {}: {}", id, bindingResult.getAllErrors());
            model.addAttribute("productoId", id);
            model.addAttribute("titulo", "Editar Producto #" + id);
            return "productos/form";
        }

        log.info("JPA VIEW: Actualizando producto ID {}", id);
        productoService.actualizarProducto(id, request);
        redirectAttributes.addFlashAttribute("mensajeExito", "Producto actualizado correctamente");
        return "redirect:/productos";
    }

    @GetMapping("/eliminar/{id}")
    public String eliminarProducto(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("JPA VIEW: Eliminando producto ID {}", id);
        productoService.eliminarLogico(id);
        redirectAttributes.addFlashAttribute("mensajeExito", "Producto eliminado correctamente");
        return "redirect:/productos";
    }
}
