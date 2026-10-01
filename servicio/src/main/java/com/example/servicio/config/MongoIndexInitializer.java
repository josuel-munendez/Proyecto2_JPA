package com.example.servicio.config;

import com.example.servicio.entity.Producto;
import com.example.servicio.entity.ProductoAuditoria;
import com.example.servicio.entity.ProductoImagen;
import com.example.servicio.entity.Variante;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver.IndexDefinitionHolder;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.stereotype.Component;

/**
 * Crea los indices declarados con @Indexed y @CompoundIndex en las entidades.
 *
 * Spring Boot 3 lo hacia solo cuando se ponia
 * spring.data.mongodb.auto-index-creation=true. Boot 4 elimino esa propiedad:
 * no aparece en el metadata de spring-boot-mongodb ni como
 * spring.mongodb.auto-index-creation ni como la clave antigua, asi que
 * escribirla no da error, simplemente no hace nada. Sin esto, las entidades
 * declaraban @Indexed(unique = true) sobre Producto.nombre y
 * Producto.referencia y la base se quedaba unicamente con el indice _id_,
 * dejando la unicidad solo garantizada en la capa de aplicacion.
 *
 * Se resuelve el indice a partir de las anotaciones, no se codifica a mano, de
 * modo que las entidades siguen siendo la unica fuente de verdad.
 *
 * Los errores se registran y NO interrumpen el arranque. Un indice unique no
 * se puede crear si ya hay documentos que lo violan, y eso no debe impedir que
 * el servicio levante: es preferible arrancar avisando por log que quedarse
 * caido.
 */
@Component
public class MongoIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexInitializer.class);

    /** Entidades con indices declarados. Se listan para no tocar colecciones ajenas. */
    private static final Class<?>[] ENTIDADES = {
            Producto.class,
            ProductoAuditoria.class,
            Variante.class,
            ProductoImagen.class,
    };

    private final MongoTemplate mongoTemplate;
    private final MongoMappingContext mappingContext;

    public MongoIndexInitializer(MongoTemplate mongoTemplate, MongoMappingContext mappingContext) {
        this.mongoTemplate = mongoTemplate;
        this.mappingContext = mappingContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        MongoPersistentEntityIndexResolver resolver = new MongoPersistentEntityIndexResolver(mappingContext);

        for (Class<?> tipo : ENTIDADES) {
            IndexOperations ops = mongoTemplate.indexOps(tipo);
            for (IndexDefinitionHolder holder : resolver.resolveIndexForEntity(entidadDe(tipo))) {
                crear(ops, holder);
            }
        }
    }

    private void crear(IndexOperations ops, IndexDefinitionHolder holder) {
        try {
            ops.ensureIndex(holder.getIndexDefinition());
            log.info("MongoDB: indice ensured en {} -> {}",
                    ops.getIndexInfo().isEmpty() ? "?" : holder.getCollection(), holder);
        } catch (Exception e) {
            // Típico: E11000 duplicate key, cuando ya hay documentos que violan
            // un @Indexed(unique = true). Se avisa, pero el servicio arranca.
            log.warn("MongoDB: no se pudo crear el indice {} en {}: {}",
                    holder.getIndexKeys(), holder.getCollection(), e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private MongoPersistentEntity<?> entidadDe(Class<?> tipo) {
        for (MongoPersistentEntity<?> entidad : mappingContext.getPersistentEntities()) {
            if (tipo.equals(entidad.getType())) {
                return entidad;
            }
        }
        // Si la entidad no estuviera mapeada no habria nada que indexar.
        throw new IllegalStateException("Entidad no registrada en el mapping context: " + tipo.getSimpleName());
    }
}
