# Documentación Jira — SDD Products API

## Épica

**[EPIC] SDD Products API — Gestión de Catálogo de Productos**
- Labels: `backend`, `spring-boot`, `sdd`
- Descripción: Implementación completa de la API REST de gestión de catálogo de productos para e-commerce. Incluye CRUD de productos, ajuste de stock, soft-delete, validación de reglas de negocio y documentación OpenAPI.

---

## Historias de Usuario

---

### [STORY] REQ-001: Crear Producto

**Como:** Operator
**Quiero:** crear un producto con todos sus atributos mediante `POST /api/v1/products`
**Para:** que el producto esté disponible en el catálogo con un UUID generado por el sistema

**Criterios de aceptación:**
- `POST /api/v1/products` con body válido retorna HTTP 201 con `ProductResponse` incluyendo UUID generado
- El `id` se genera con `UUID.randomUUID()` en la capa de servicio, nunca delegado a la BD
- `createdAt` y `updatedAt` se asignan al momento de la creación
- `deletedAt` es `null` al crear
- `stock` es `0` por defecto si no se proporciona
- SKU duplicado entre activos retorna HTTP 409
- Validación fallida retorna HTTP 400 con `ErrorResponse` indicando campos inválidos
- `name`: no vacío, máximo 100 caracteres
- `sku`: patrón `^[A-Z0-9-]+$`, máximo 50 caracteres
- `price`: `BigDecimal` mayor a 0
- `stock`: entero mínimo 0
- `category`: uno de `ELECTRONICS`, `PERIPHERALS`, `SOFTWARE`, `ACCESSORIES`

**Story Points:** 5
**Labels:** `backend`, `spring-boot`, `sdd`, `domain`

#### Subtareas técnicas

- `[TASK]` Crear entidad `Product` con `@SQLRestriction("deleted_at IS NULL")` y `@PrePersist`/`@PreUpdate`
- `[TASK]` Crear `CreateProductRequest` con Bean Validation completa
- `[TASK]` Implementar `ProductServiceImpl.create()` con verificación de SKU duplicado
- `[TASK]` Crear migración Flyway `V1__create_products_table.sql`
- `[TASK]` Test unitario: `should_createProduct_when_skuIsUnique`
- `[TASK]` Test unitario: `should_throwSkuAlreadyExistsException_when_skuIsDuplicated`
- `[TASK]` Property test: Property 1 — estado inicial correcto tras creación

---

### [STORY] REQ-002: Consultar Producto por ID

**Como:** Operator o External_System
**Quiero:** consultar un producto por su UUID mediante `GET /api/v1/products/{id}`
**Para:** ver los datos actuales del producto

**Criterios de aceptación:**
- Producto activo encontrado retorna HTTP 200 con `ProductResponse`
- UUID inexistente retorna HTTP 404
- Producto con `deletedAt != null` retorna HTTP 404 (tratado como inexistente)

**Story Points:** 2
**Labels:** `backend`, `spring-boot`, `sdd`

#### Subtareas técnicas

- `[TASK]` Implementar `ProductServiceImpl.findById()` con `orElseThrow(ProductNotFoundException)`
- `[TASK]` Endpoint `GET /{id}` en `ProductController`
- `[TASK]` Test unitario: `should_returnProduct_when_activeProductExists`
- `[TASK]` Test unitario: `should_throwProductNotFoundException_when_productDoesNotExist`
- `[TASK]` Property test: Property 4 — round-trip crear y consultar
- `[TASK]` Property test: Property 5 — producto no visible retorna 404

---

### [STORY] REQ-003: Listar Productos con Paginación y Filtros

**Como:** Operator o External_System
**Quiero:** listar productos activos con paginación y filtros opcionales mediante `GET /api/v1/products`
**Para:** explorar y buscar en el catálogo de forma eficiente

**Criterios de aceptación:**
- Retorna HTTP 200 con `PaginatedResponse` con solo productos activos (`deletedAt = null`)
- Defaults: `page=0`, `size=10`, orden por `createdAt` descendente
- Filtro `category`: solo productos con esa categoría
- Filtro `minPrice`: solo productos con `price >= minPrice`
- Filtro `maxPrice`: solo productos con `price <= maxPrice`
- Filtro `name`: contiene la cadena (case-insensitive)
- Filtro `sku`: coincidencia exacta (case-sensitive)
- Múltiples filtros se aplican como condiciones AND

**Story Points:** 5
**Labels:** `backend`, `spring-boot`, `sdd`, `pagination`

#### Subtareas técnicas

- `[TASK]` Crear derived methods en `ProductRepository` para todas las combinaciones de filtros
- `[TASK]` Implementar `ProductServiceImpl.findAll()` con lógica `selectPage`
- `[TASK]` Endpoint `GET /` con `@RequestParam` opcionales en `ProductController`
- `[TASK]` Test: `should_excludeSoftDeleted_when_findAll`
- `[TASK]` Property test: Property 6 — listado excluye soft-deleted
- `[TASK]` Property test: Property 7 — filtro por category
- `[TASK]` Property test: Property 8 — filtro por rango de precio
- `[TASK]` Property test: Property 9 — filtro por name (case-insensitive)
- `[TASK]` Property test: Property 10 — filtro por SKU (exact match)

---

### [STORY] REQ-004: Actualizar Producto

**Como:** Operator
**Quiero:** actualizar los atributos de un producto mediante `PUT /api/v1/products/{id}`
**Para:** que el catálogo refleje la información correcta

**Criterios de aceptación:**
- Actualiza solo campos no nulos del request, retorna HTTP 200 con `ProductResponse` actualizado
- `updatedAt` se renueva en cada actualización exitosa
- Body con campo `sku` retorna HTTP 422 (SKU inmutable)
- Producto inexistente o soft-deleted retorna HTTP 404
- Validación fallida retorna HTTP 400

**Story Points:** 3
**Labels:** `backend`, `spring-boot`, `sdd`, `domain`

#### Subtareas técnicas

- `[TASK]` Crear `UpdateProductRequest` sin campo `sku`
- `[TASK]` Implementar `ProductServiceImpl.update()` con patch parcial
- `[TASK]` Endpoint `PUT /{id}` en `ProductController`
- `[TASK]` Test: `should_updateNonNullFields_when_updateRequestIsPartial`
- `[TASK]` Test: `should_return422_when_updateAttemptsSKUChange`
- `[TASK]` Property test: Property 11 — campos actualizados y `updatedAt` renovado
- `[TASK]` Property test: Property 12 — SKU inmutable

---

### [STORY] REQ-005: Eliminar Producto (Soft Delete)

**Como:** Operator
**Quiero:** eliminar un producto del catálogo mediante `DELETE /api/v1/products/{id}`
**Para:** que deje de ser visible mientras sus datos se conservan para auditoría

**Criterios de aceptación:**
- Asigna `deletedAt` al timestamp actual, retorna HTTP 204 sin body
- El registro físico permanece en la BD con `deletedAt` asignado
- Producto inexistente o ya eliminado retorna HTTP 404

**Story Points:** 2
**Labels:** `backend`, `spring-boot`, `sdd`, `soft-delete`

#### Subtareas técnicas

- `[TASK]` Implementar `ProductServiceImpl.delete()` con asignación de `deletedAt`
- `[TASK]` Endpoint `DELETE /{id}` en `ProductController`
- `[TASK]` Test repositorio: `should_preserveRecordInDatabase_when_softDeleted`
- `[TASK]` Test: `should_setDeletedAt_when_productIsDeleted`
- `[TASK]` Property test: Property 13 — soft-delete e invisibilidad posterior

---

### [STORY] REQ-006: Ajuste de Stock

**Como:** Operator
**Quiero:** ajustar el stock de un producto mediante `PATCH /api/v1/products/{id}/stock`
**Para:** que el inventario refleje los cambios reales sin reemplazar el producto completo

**Criterios de aceptación:**
- Aplica `nuevoStock = stockActual + delta`, retorna HTTP 200 con `ProductResponse`
- Delta puede ser positivo (incremento) o negativo (decremento)
- `stockActual + delta < 0` retorna HTTP 422 y deja el stock sin cambios
- Producto inexistente o soft-deleted retorna HTTP 404
- `updatedAt` se renueva en cada ajuste exitoso

**Story Points:** 3
**Labels:** `backend`, `spring-boot`, `sdd`, `inventory`

#### Subtareas técnicas

- `[TASK]` Crear `StockAdjustmentRequest` con campo `delta: @NotNull Integer`
- `[TASK]` Implementar `ProductServiceImpl.adjustStock()` con validación de stock negativo
- `[TASK]` Endpoint `PATCH /{id}/stock` en `ProductController`
- `[TASK]` Test: `should_increaseStock_when_deltaIsPositive`
- `[TASK]` Test: `should_throwInsufficientStockException_when_deltaWouldResultInNegativeStock`
- `[TASK]` Property test: Property 14 — ajuste correcto con delta válido
- `[TASK]` Property test: Property 15 — stock no negativo, delta inválido rechazado

---

### [STORY] REQ-007: Serialización JSON y Documentación OpenAPI

**Como:** External_System
**Quiero:** que todas las respuestas sean JSON válido y la API esté documentada en OpenAPI
**Para:** que la integración sea confiable y predecible

**Criterios de aceptación:**
- `createdAt` y `updatedAt` serializados como ISO 8601
- `price` serializado como número JSON preservando precisión `BigDecimal`
- JSON malformado retorna HTTP 400 con `ErrorResponse`
- Round-trip JSON serializar/deserializar produce objeto equivalente
- OpenAPI 3 disponible en `/v3/api-docs`
- Swagger UI disponible en `/swagger-ui.html`

**Story Points:** 2
**Labels:** `backend`, `spring-boot`, `sdd`, `openapi`

#### Subtareas técnicas

- `[TASK]` Configurar Jackson: `write-dates-as-timestamps: false`
- `[TASK]` Crear `OpenApiConfig.java` con título, versión y descripción
- `[TASK]` Crear `GlobalExceptionHandler` con handler para `HttpMessageNotReadableException`
- `[TASK]` Property test: Property 16 — JSON round-trip preserva todos los campos

---

## Tareas Técnicas (NFRs)

### [TECHNICAL TASK] NFR-001: Cobertura de Tests >= 80%

- Configurar JaCoCo con límite mínimo de 80% en líneas y ramas
- Verificar cobertura en cada build con `mvn verify`
- Labels: `backend`, `testing`, `quality`
- Story Points: 2

### [TECHNICAL TASK] NFR-002: Property-Based Testing con jqwik

- Implementar 16 propiedades de corrección con `@Property(tries = 100)`
- Cubrir: creación, validación, SKU duplicado, round-trip, soft-delete, filtros, stock, JSON
- Labels: `backend`, `testing`, `pbt`
- Story Points: 5

### [TECHNICAL TASK] NFR-003: Documentación OpenAPI completa

- Todos los endpoints con `@Operation`, `@ApiResponse` y descripción de parámetros
- Swagger UI accesible en `/swagger-ui.html`
- Labels: `backend`, `documentation`, `openapi`
- Story Points: 1
