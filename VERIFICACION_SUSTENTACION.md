# Verificación y sustentación: Proyectos 2 y 3

Fecha: 2026-09-30. Microservicio: `servicio/`. Ramas: `main` (JPA/PostgreSQL) y `java/mongoDB` (MongoDB).

## Arquitectura

En el frontend React, `ProductList.jsx` llama `deleteMicroProduct()` en `productService.js`; `microservice.js` usa Axios con base `/api/v1`; el request llega a `ProductoController`. El módulo es híbrido: Spring administra los campos base del producto y Django conserva imágenes, variantes y categorías. No describirlo como si todos los subrecursos fueran JPA.

JPA comparte `products_product` con Django en Neon. Django es dueño de las migraciones y tablas auxiliares. La rama Mongo guarda documentos en la colección `productos`.

## Reto, código y demostración

| Reto | Código | Cómo demostrar |
|---|---|---|
| CRUD REST | `ProductoController`, `ProductoService`, `ProductoServiceImpl`, `ProductoRepository` | Importar `postman/Proyecto2-3-Productos.postman_collection.json`; crear, obtener, editar y borrar. |
| AND (2 campos) | `buscarPorNombreYEstado` + método derivado `findByNombreContainingIgnoreCaseAndIsActive` | `GET /api/v1/productos/buscar/and?nombre=Buzo&estado=ACTIVO&page=0&size=10`. Cumple ambos criterios. |
| OR (3 campos) | `buscarPor3CamposOr` en controlador y repositorio | `GET /api/v1/productos/buscar/or?query=Buzo&page=0&size=10`. Busca nombre O descripción O referencia usando parámetros enlazados. |
| Excepciones/status | `GlobalExceptionHandler` (`@RestControllerAdvice`) | Invocar validación inválida para `400`, ID inexistente para `404` y superar límite para `429`. |
| Cinco validaciones | `Producto.java` y `ProductoRequest.java` | Nombre requerido/longitud/patrón; descripción ≤500; precio ≥50; referencia con patrón al crear. El DTO valida la entrada HTTP y la entidad protege el dominio. |
| Logs | Controlador, servicio, `GlobalExceptionHandler`, `RateLimitService` | Mostrar INFO de operaciones, WARN de reglas/límites y ERROR de excepciones no controladas. |
| Paginación | `listarProductos`, `Pageable`, `ProductoPageResponse`, `templates/productos/index.html` | `GET /api/v1/productos?page=0&page_size=10`; verificar `content`, `pageNumber`, `pageSize`, `totalElements` y la vista `/productos`. |
| Rate limit | `RateLimitFilter`, `RateLimitService`, `RateLimitProperties` | Defaults: GET 120/min, POST/PUT 30/min, DELETE 20/min por IP/proceso. En Postman ejecutar solo el request dedicado con 121 iteraciones en menos de un minuto. |
| CORS | `CorsConfig.java` | Abrir frontend en `localhost:5173`, verificar preflight y origen permitido. CORS no es autenticación. |

### DELETE explicado

`DELETE /api/v1/productos/{id}` no lleva body: el ID va en la URL. Spring llama `eliminarLogico(id)`, conserva la fila y responde `204 No Content`. Un GET posterior puede devolver `200` con `wasDeleted=true`; el borrado físico separado es `/api/v1/productos/{id}/purgar` y tiene reglas de negocio adicionales.

Smoke JPA con PostgreSQL local efímero: `POST 201`, `DELETE 204`, `GET 200` con `wasDeleted=true`; la tabla de auditoría registró `deleted / soft-delete`. Neon no se usó para ese smoke. En una base vacía, la tabla `products_productaudit` debe existir desde las migraciones Django; Hibernate no crea por sí solo todo el esquema compartido.

## Verificaciones ejecutadas

- JPA/PostgreSQL: `./mvnw test`, 1/1 test de carga de contexto contra PostgreSQL Testcontainers.
- MongoDB: `./mvnw test`, 1/1 test de carga de contexto contra Mongo Testcontainers; smoke local CRUD completo: POST 201, GET 200, PUT 200, DELETE 204 y GET posterior `estado=BORRADO`.
- Django: 141/141 tests con SQLite temporal y Mongo desactivado.
- Frontend: ESLint de `ProductForm.jsx` y build de Vite pasaron; el build advierte un chunk JS >500 kB.
- WebP: el selector acepta `image/webp`; el frontend valida extensión, tamaño y resolución; `ProductImage.full_clean()` pasó una prueba con WebP real 400x400.
- Seed Neon: 30 productos, 30 imágenes y 262 variantes, 0 fallos. Las nueve URLs fuente se probaron como JPEG 800x800. Posterior a la carga: 30/30 productos con imagen y variantes, 7 categorías, 49 asociaciones.
- `migrate --check` no detectó migraciones pendientes antes del reset de productos.

## Caveats que debes explicar con honestidad

- `SecurityConfig` usa `permitAll()`: Spring Security está presente, pero la API no exige autenticación. CORS y rate limit no la reemplazan.
- Rate limit es en memoria por proceso, no distribuido. `X-Forwarded-For` solo es confiable detrás de un proxy que lo sobrescriba.
- JPA deriva `BORRADO` de `is_active=false` e `is_approved=false`; Django también usa esos flags para productos pendientes. Para demostrar un borrado, enseñar `wasDeleted` y la auditoría, no solo el texto `estado`.
- Las suites Spring tienen solo test de contexto; no cubren cada endpoint. Se hizo smoke HTTP para DELETE JPA. El smoke CRUD Mongo aún debe confirmarse.
- Hay credenciales Atlas antiguas en el historial remoto de `java/mongoDB`. La configuración actual usa `spring.mongodb.uri` con `SPRING_MONGODB_URI` y fallback local no sensible; hay que rotar la credencial expuesta antes del despliegue.
- La comparación de `java/microservicio` con su remoto muestra 67 archivos distintos, no 53. Hay cambios locales sin confirmar en ambos repositorios; no deben agregarse a commits automáticamente.

## Repetir el seed de productos

Solo tras seleccionar conscientemente Neon: `DB_TYPE=neon USE_MONGODB=False venv/bin/python manage.py seed_all --skip-users --clean-products`. Esto limpia productos y dependencias en cascada, imágenes Cloudinary, variantes, reseñas, carritos y categorías de producto. No limpia usuarios ni órdenes; sus snapshots se conservan y la FK nullable del producto puede quedar en null.

Configura `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` y `SPRING_MONGODB_URI` fuera de Git. No guardes valores reales en Postman ni en archivos versionados.
