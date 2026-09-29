package com.example.servicio.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.example.servicio.entity.Producto;
import com.example.servicio.entity.ProductoAuditoria;

/**
 * Implementacion con MongoTemplate de ProductoRepositoryCustom.
 *
 * En la rama JPA estos filtros eran JPQL con parameters opcionales
 * (':search IS NULL OR ...'). MongoDB no tiene JPQL, asi que aqui los
 * criterios se componen de forma condicional: solo se anaden los que aplican.
 */
public class ProductoRepositoryImpl implements ProductoRepositoryCustom {

    /**
     * Escapa los metacaracteres de regex antes de usarlo como patron.
     *
     * El termino de busqueda viene del usuario. Sin este escape, un producto
     * llamado "Cafete" haria que buscar "Cafe." fallara y, al reves, un
     * patron malicioso podria inducir backtracking en el servidor.
     */
    private static String regexSeguro(String texto) {
        return Pattern.quote(texto);
    }

    private final MongoTemplate mongoTemplate;
    private final ProductoAuditoriaRepository auditoriaRepository;

    public ProductoRepositoryImpl(MongoTemplate mongoTemplate,
                                  ProductoAuditoriaRepository auditoriaRepository) {
        this.mongoTemplate = mongoTemplate;
        this.auditoriaRepository = auditoriaRepository;
    }

    @Override
    public Page<Producto> buscarConFiltros(
            String search,
            Boolean isActive,
            Boolean isApproved,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable) {

        List<Criteria> criterios = new ArrayList<>();

        if (search != null && !search.isBlank()) {
            String patron = regexSeguro(search);
            criterios.add(new Criteria().orOperator(
                    Criteria.where("nombre").regex(patron, "i"),
                    Criteria.where("descripcion").regex(patron, "i"),
                    Criteria.where("referencia").regex(patron, "i")));
        }
        if (isActive != null) {
            criterios.add(Criteria.where("isActive").is(isActive));
        }
        if (isApproved != null) {
            criterios.add(Criteria.where("aprobado").is(isApproved));
        }
        if (minPrice != null) {
            criterios.add(Criteria.where("precioBase").gte(minPrice));
        }
        if (maxPrice != null) {
            criterios.add(Criteria.where("precioBase").lte(maxPrice));
        }

        Criteria where = criterios.isEmpty()
                ? new Criteria()
                : new Criteria().andOperator(criterios.toArray(new Criteria[0]));

        Query query = Query.query(where).with(pageable);
        List<Producto> productos = mongoTemplate.find(query, Producto.class);

        // El total de la pagina se pide con la misma query sin paginar.
        long total = mongoTemplate.count(Query.query(where), Producto.class);

        return new PageImpl<>(productos, pageable, total);
    }

    @Override
    public Page<Producto> buscarPor3CamposOr(String termino, Pageable pageable) {
        // Mismo escapado que buscarConFiltros: el usuario escribe texto, no regex.
        String patron = regexSeguro(termino == null ? "" : termino);

        Criteria where = new Criteria().andOperator(
                new Criteria().orOperator(
                        Criteria.where("nombre").regex(patron, "i"),
                        Criteria.where("descripcion").regex(patron, "i"),
                        Criteria.where("referencia").regex(patron, "i")),
                new Criteria().orOperator(
                        Criteria.where("isActive").is(true),
                        Criteria.where("aprobado").is(true)));

        Query query = Query.query(where).with(pageable);
        List<Producto> productos = mongoTemplate.find(query, Producto.class);
        long total = mongoTemplate.count(Query.query(where), Producto.class);

        return new PageImpl<>(productos, pageable, total);
    }

    @Override
    public List<Producto> findPurgeCandidates(LocalDateTime cutoff) {
        Criteria where = Criteria.where("isActive").is(false)
                .and("aprobado").is(false)
                .and("updatedAt").lt(cutoff);

        List<Producto> candidatos = mongoTemplate.find(
                Query.query(where), Producto.class);

        if (candidatos.isEmpty()) {
            return List.of();
        }

        // Segunda pasada: solo los que tienen historial relevante. Se consulta
        // la coleccion de auditoria una vez con todos los ids, en vez de una
        // consulta por producto.
        List<String> ids = candidatos.stream().map(Producto::getId).toList();
        List<String> conHistorial = auditoriaRepository.findHistorialDe(ids).stream()
                .map(ProductoAuditoria::getProductoId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        return candidatos.stream()
                .filter(p -> conHistorial.contains(p.getId()))
                .toList();
    }
}
