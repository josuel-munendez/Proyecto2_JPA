# 📦 Proyecto 2 & 3: Microservicio CRUD de Productos (JPA/PostgreSQL & MongoDB)

Microservicio desarrollado con **Spring Boot 4 / Java 21** aplicando **Arquitectura Orientada al Dominio (ODD / DDD)**, programación por capas, contratos mediante interfaces, herencia con auditoría, validaciones robustas con Jakarta Validation, manejo centralizado de excepciones con códigos de estado HTTP semánticos, sistema de logs estructurados, seguridad con Spring Security, control de tasa de peticiones (**Rate Limiting**), prevención de exploits/inyecciones, vistas web interactivas con **Thymeleaf + Bootstrap 5**, y soporte para bases de datos relacionales (PostgreSQL/JPA) y NoSQL (MongoDB).

---

## 🌳 Estructura de Ramas y Conexión

El proyecto cuenta con dos implementaciones independientes según el motor de persistencia y la arquitectura de datos:

| Rama en `Proyecto2_JPA` | Motor de Base de Datos | Tecnología ORM / ODM | Puerto | Conexión con `projecto_formativo` |
| :--- | :--- | :--- | :--- | :--- |
| `main` | **PostgreSQL** | Spring Data JPA / Hibernate | `8082` | Rama `java/microservicio` |
| `java/mongoDB` | **MongoDB** | Spring Data MongoDB | `8083` | Rama `java/mongoDB` |

---

## 🚀 Requisitos y Características Implementadas

### 1. 🏗️ Arquitectura Orientada al Dominio (ODD) y Capas
- **Entidades / Documentos de Dominio**: Encapsulan la lógica y reglas de negocio del producto (`esPrecioCopValido()`, `puedePublicarse()`).
- **Herencia y Auditoría**: Clase base abstracta `BaseEntity` que gestiona automáticamente las marcas temporales `createdAt` y `updatedAt`.
- **Capa de Repositorio**: Uso de interfaces `JpaRepository` y `MongoRepository` con consultas derivadas y personalizadas.
- **Capa de Servicios**: Desacoplamiento total mediante la interfaz `ProductoService` y su implementación `ProductoServiceImpl`.
- **Capa de Controladores**: 
  - `ProductoController`: API RESTful con respuestas JSON estandarizadas.
  - `ProductoViewController`: Controlador web MVC para renderizado de vistas Thymeleaf.
- **DTOs**: `ProductoRequest`, `ProductoResponse` y `ProductoPageResponse` para transferencia de datos segura.

### 2. 🔍 Búsquedas Avanzadas (AND / OR)
- **Búsqueda por 2 campos con operador AND**:
  - *JPA/PostgreSQL*: `findByNombreContainingIgnoreCaseAndEstado`
  - *MongoDB*: `findByNombreRegexAndEstado` (expresión regular case-insensitive + estado)
- **Búsqueda por 3 campos con operador OR**:
  - *JPA/PostgreSQL*: Consulta JPQL sobre `nombre`, `descripcion` y `referencia`, filtrando registros no borrados.
  - *MongoDB*: Consulta `@Query` con operadores `$or` y `$regex` sobre `nombre`, `descripcion` y `referencia`.

### 3. 🛡️ Validaciones con Anotaciones en la Entidad (5 Validaciones)
1. `@NotBlank(message = "El nombre del producto es obligatorio")`: Control de campos no vacíos.
2. `@Size(min = 3, max = 100)`: Restricción de longitud del nombre (y `@Size(max = 500)` en descripción).
3. `@Pattern(regexp = "^[^\\p{Cntrl}]*$")`: Sanitización contra caracteres de control e inyecciones.
4. `@NotNull` + `@DecimalMin(value = "50.0")`: Validación de precio base mínimo en COP (múltiplo de 50).
5. `@Pattern(regexp = "^[A-Z0-9\\-]{3,20}$")`: Formato alfanumérico estricto para la referencia única del producto.

### 4. 🚨 Manejo Centralizado de Excepciones y Códigos HTTP
Implementado con `GlobalExceptionHandler` (`@RestControllerAdvice`), retornando payloads JSON consistentes con códigos de estado HTTP precisos:
- `200 OK`: Operaciones de lectura y actualización exitosas.
- `201 CREATED`: Creación exitosa de producto.
- `204 NO CONTENT`: Eliminación lógica completada.
- `400 BAD REQUEST`: Errores de validación de datos (`MethodArgumentNotValidException`) o reglas de negocio violadas (`BusinessRuleException`).
- `404 NOT FOUND`: Recurso no encontrado (`ResourceNotFoundException`).
- `429 TOO MANY REQUESTS`: Límite de peticiones por minuto superado (Rate Limit).
- `500 INTERNAL SERVER ERROR`: Errores no controlados del servidor.

### 5. 📜 Sistema de Logs Estructurados
Uso de **SLF4J / Logback** registrando eventos en consola y en archivo rotativo (`logs/`):
- `INFO`: Creación, actualización, búsquedas y cambios de estado.
- `WARN`: Intentos de nombres/referencias duplicadas, fallos de validación y bloqueos por Rate Limit.
- `ERROR`: Excepciones no controladas y recursos no localizados en base de datos.

### 6. 📄 Paginación con JPA / MongoDB y Vistas Web
- Paginación dinámica y configurable (`page`, `size`, `sortBy`, `sortDir`).
- Interfaz web interactiva en `/productos` con **Bootstrap 5**, navegación entre páginas (Anterior / Siguiente / Números) y barra de búsqueda en tiempo real.

---

## 🌟 Milla Extra (Seguridad, Anti-Exploits y Rate Limiting)

- **Control de Tasa de Peticiones (Rate Limiting)**: `RateLimitFilter` y `RateLimitService` monitorean peticiones por IP y método HTTP (GET: 120/min, POST/PUT: 30/min, DELETE: 20/min), bloqueando abusos y ataques de fuerza bruta/DDoS.
- **Configuración CORS**: `CorsConfig` habilita el consumo seguro desde el frontend del proyecto formativo (`localhost:3000`, `localhost:5173`, `localhost:8080`).
- **Protección contra Inyección y Exploits**: Parámetros tipados en JPQL/Mongo Queries, DTOs validados con anotaciones Jakarta y límites de tamaño de cabeceras/parámetros en el servidor Tomcat.

---

## 🛠️ Instalación y Ejecución

### Prerrequisitos
- Java 21 LTS instalado
- PostgreSQL (puerto 5432) o MongoDB (puerto 27017) según la rama elegida

### Ejecución en PostgreSQL (Rama `main`)
```bash
git checkout main
cd servicio
./mvnw clean compile
./mvnw spring-boot:run
```

### Ejecución en MongoDB (Rama `java/mongoDB`)
```bash
git checkout java/mongoDB
cd servicio
./mvnw clean compile
./mvnw spring-boot:run
```

---

## 📡 Endpoints de la API REST (`/api/v1/productos`)

| Método | Endpoint | Descripción | Parámetros Query |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/productos` | Listar productos paginados | `page`, `size`, `sortBy`, `sortDir`, `nombre`, `estado`, `search` |
| `GET` | `/api/v1/productos/{id}` | Obtener producto por ID | - |
| `POST` | `/api/v1/productos` | Crear nuevo producto | - (Body JSON) |
| `PUT` | `/api/v1/productos/{id}` | Actualizar producto | - (Body JSON) |
| `DELETE` | `/api/v1/productos/{id}` | Eliminación lógica de producto | - |

### Vistas Web (Thymeleaf)
- **Listado y Búsqueda**: `http://localhost:8082/productos` (o `8083` en MongoDB)
- **Crear Producto**: `http://localhost:8082/productos/nuevo`
- **Editar Producto**: `http://localhost:8082/productos/editar/{id}`
