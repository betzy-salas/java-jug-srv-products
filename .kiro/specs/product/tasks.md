# Implementation Plan: Gestión de Productos

## Overview

Implementación completa de la SDD Products API en Java 17 + Spring Boot 3.2 siguiendo arquitectura en capas estricta (Controller → Service → Repository → Entity). Las tareas están ordenadas para que cada una pueda ejecutarse sin dependencias no resueltas.

## Tasks

- [x] 1. Setup del proyecto — pom.xml y configuración
  - [x] 1.1 Crear `pom.xml` con todas las dependencias del stack
    - Incluir: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `postgresql`, `flyway-core`, `flyway-database-postgresql`, `mapstruct`, `mapstruct-processor`, `springdoc-openapi-starter-webmvc-ui`, `spring-boot-starter-test`, `jqwik`, `jqwik-spring`
    - Configurar `maven-compiler-plugin` con `annotationProcessorPaths` para `mapstruct-processor`
    - Configurar plugin JaCoCo con cobertura mínima de 80% en líneas y ramas
    - Java 17, Spring Boot 3.2.x
    - _Requirements: NFR Test Coverage_

  - [x] 1.2 Crear `src/main/resources/application.yml`
    - Datasource PostgreSQL 15 (url, username, password como placeholders)
    - JPA: `hibernate.ddl-auto: validate`, `show-sql: false`, dialecto PostgreSQL
    - Flyway: `enabled: true`, `locations: classpath:db/migration`
    - Jackson: `serialization.write-dates-as-timestamps: false` para ISO 8601
    - springdoc: `swagger-ui.path: /swagger-ui.html`, `api-docs.path: /v3/api-docs`
    - _Requirements: REQ-007.1, REQ-007.2_

  - [x] 1.3 Crear `src/main/resources/application-test.yml`
    - Datasource H2 en modo PostgreSQL (`MODE=PostgreSQL`) o Testcontainers
    - Flyway habilitado para tests
    - JPA: `ddl-auto: create-drop` para tests de repositorio
    - _Requirements: NFR Test Coverage_

- [x] 2. Migración Flyway — esquema de base de datos
  - [x] 2.1 Crear `src/main/resources/db/migration/V1__create_products_table.sql`
    - Tabla `products` con columnas: `id UUID NOT NULL`, `sku VARCHAR(50) NOT NULL`, `name VARCHAR(100) NOT NULL`, `description TEXT`, `price NUMERIC(19,4) NOT NULL CHECK (price > 0)`, `stock INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0)`, `category VARCHAR(20) NOT NULL`, `created_at TIMESTAMPTZ NOT NULL`, `updated_at TIMESTAMPTZ NOT NULL`, `deleted_at TIMESTAMPTZ`
    - Constraints: `pk_products PRIMARY KEY (id)`, `uq_products_sku UNIQUE (sku)`
    - Índices: `idx_products_sku`, `idx_products_category`, `idx_products_deleted_at`, `idx_products_created_at DESC`
    - Sin `SERIAL` ni `GENERATED` — el `id` lo genera el Service
    - _Requirements: REQ-001.1, REQ-001.2, REQ-005.2_

- [x] 3. Domain layer — entidad, enum y excepciones
  - [x] 3.1 Crear `Category.java` enum en `domain/`
    - Valores: `ELECTRONICS`, `PERIPHERALS`, `SOFTWARE`, `ACCESSORIES`
    - _Requirements: REQ-001.12_

  - [x] 3.2 Crear `Product.java` entidad JPA en `domain/entity/`
    - Anotaciones: `@Entity`, `@Table(name = "products")`, `@SQLRestriction("deleted_at IS NULL")`
    - Campos: `id` (`UUID`, `@Column(updatable = false)`), `sku`, `name`, `description`, `price` (`BigDecimal`, `precision=19, scale=4`), `stock` (`int`), `category` (`@Enumerated(EnumType.STRING)`), `createdAt`, `updatedAt`, `deletedAt` (nullable) — todos `OffsetDateTime`
    - `@PrePersist` asigna `createdAt = updatedAt = OffsetDateTime.now()`
    - `@PreUpdate` asigna `updatedAt = OffsetDateTime.now()`
    - Sin `@GeneratedValue` — el `id` se asigna desde el Service
    - _Requirements: REQ-001.2, REQ-001.3, REQ-001.4, REQ-005.1, REQ-005.2_

  - [x] 3.3 Crear las 5 excepciones de dominio en `domain/exception/`
    - `ProductNotFoundException extends RuntimeException` — mensaje con el UUID no encontrado
    - `SkuAlreadyExistsException extends RuntimeException` — mensaje con el SKU duplicado
    - `ImmutableSkuException extends RuntimeException`
    - `InsufficientStockException extends RuntimeException` — mensaje con stock actual y delta
    - `InvalidStockException extends RuntimeException`
    - _Requirements: REQ-001.6, REQ-002.2, REQ-002.3, REQ-004.3, REQ-006.3_

- [x] 4. Repository layer
  - [x] 4.1 Crear `ProductRepository.java` en `repository/`
    - Extiende `JpaRepository<Product, UUID>`
    - Derived methods: `Optional<Product> findBySku(String sku)`, `boolean existsBySku(String sku)`, `Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable)`, `Page<Product> findByCategory(Category category, Pageable pageable)`, `Page<Product> findByPriceBetween(BigDecimal min, BigDecimal max, Pageable pageable)`, `Page<Product> findByCategoryAndPriceBetween(...)` y combinaciones necesarias para los filtros AND
    - Prohibido `@Query` JPQL/SQL — solo derived methods
    - `@SQLRestriction` en la entidad garantiza exclusión de soft-deleted en todos los métodos
    - _Requirements: REQ-003.1, REQ-003.3, REQ-003.4, REQ-003.5, REQ-003.6, REQ-003.7, REQ-003.8_

- [x] 5. DTOs — request y response
  - [x] 5.1 Crear DTOs de request en `dto/request/`
    - `CreateProductRequest`: `@NotBlank @Pattern(regexp="^[A-Z0-9-]+$") @Size(max=50) String sku`, `@NotBlank @Size(max=100) String name`, `String description`, `@NotNull @Positive BigDecimal price`, `@Min(0) int stock`, `@NotNull Category category`
    - `UpdateProductRequest`: `@Size(max=100) String name`, `String description`, `@Positive BigDecimal price`, `@Min(0) Integer stock`, `Category category` — todos opcionales (null = no actualizar). Sin campo `sku`
    - `StockAdjustmentRequest`: `@NotNull Integer delta` — sin restricción de rango (validación en Service)
    - _Requirements: REQ-001.7–REQ-001.12, REQ-004.3, REQ-004.5, REQ-006.2_

  - [x] 5.2 Crear DTOs de response en `dto/response/`
    - `ProductResponse`: `UUID id`, `String sku`, `String name`, `String description`, `BigDecimal price`, `int stock`, `Category category`, `String createdAt`, `String updatedAt`
    - `PaginatedResponse<T>`: `List<T> data`, `int page`, `int pageSize`, `long total`
    - `ErrorResponse`: `String error`, `String message`, `List<String> fields` (nunca null — lista vacía si no aplica)
    - _Requirements: REQ-001.1, REQ-003.2, REQ-007.1, REQ-007.2_

- [x] 6. Mapper — MapStruct
  - [x] 6.1 Crear `ProductMapper.java` en `mapper/`
    - `@Mapper(componentModel = "spring")`
    - `ProductResponse toResponse(Product product)` — mapea `OffsetDateTime` a `String` ISO 8601
    - `Product toEntity(CreateProductRequest request)` — sin mapear `id`, `createdAt`, `updatedAt`, `deletedAt` (los gestiona el Service y `@PrePersist`)
    - _Requirements: REQ-001.1, REQ-007.1, REQ-007.4_

- [x] 7. Service layer — interfaz e implementación
  - [ ] 7.1 Crear `ProductService.java` interfaz en `service/`
    - Javadoc en cada método
    - Métodos: `ProductResponse create(CreateProductRequest request)`, `ProductResponse findById(UUID id)`, `PaginatedResponse<ProductResponse> findAll(int page, int size, Category category, BigDecimal minPrice, BigDecimal maxPrice, String name, String sku)`, `ProductResponse update(UUID id, UpdateProductRequest request)`, `void delete(UUID id)`, `ProductResponse adjustStock(UUID id, StockAdjustmentRequest request)`
    - _Requirements: REQ-001–REQ-006_

  - [ ] 7.2 Crear `ProductServiceImpl.java` en `service/impl/`
    - `@Service`, `@Transactional` en todos los métodos de escritura
    - Inyección via constructor (no `@Autowired` en campo)
    - `create`: genera `UUID.randomUUID()`, verifica `existsBySku` → lanza `SkuAlreadyExistsException` si duplicado, mapea con `toEntity`, asigna `id`, llama `save`, retorna `toResponse`
    - `findById`: `findById` → `orElseThrow(ProductNotFoundException)`
    - `findAll`: construye `PageRequest.of(page, size, Sort.by("createdAt").descending())`, aplica filtros combinados con derived methods según parámetros presentes, retorna `PaginatedResponse`
    - `update`: busca producto, verifica que el body JSON no contenga `sku` (lanza `ImmutableSkuException`), actualiza solo campos no nulos, llama `save`, retorna `toResponse`
    - `delete`: busca producto, asigna `deletedAt = OffsetDateTime.now()`, llama `save`
    - `adjustStock`: busca producto, calcula `newStock = currentStock + delta`, lanza `InsufficientStockException` si `newStock < 0`, asigna nuevo stock, llama `save`, retorna `toResponse`
    - _Requirements: REQ-001.1–REQ-001.6, REQ-002.1–REQ-002.3, REQ-003.1–REQ-003.8, REQ-004.1–REQ-004.4, REQ-005.1–REQ-005.3, REQ-006.1–REQ-006.5_

- [x] 8. Controller layer
  - [x] 8.1 Crear `ProductController.java` en `controller/`
    - `@RestController`, `@RequestMapping("/api/v1/products")`
    - Inyección de `ProductService` via constructor
    - Javadoc con `@Operation`, `@ApiResponse` en cada endpoint
    - `POST /` → `@Valid @RequestBody CreateProductRequest` → `201 Created` con `ProductResponse`
    - `GET /{id}` → `200 OK` con `ProductResponse`
    - `GET /` → `@RequestParam` opcionales (`page=0`, `size=10`, `category`, `minPrice`, `maxPrice`, `name`, `sku`) → `200 OK` con `PaginatedResponse<ProductResponse>`
    - `PUT /{id}` → `@Valid @RequestBody UpdateProductRequest` → `200 OK` con `ProductResponse`
    - `DELETE /{id}` → `204 No Content` sin body
    - `PATCH /{id}/stock` → `@Valid @RequestBody StockAdjustmentRequest` → `200 OK` con `ProductResponse`
    - _Requirements: REQ-001.1, REQ-002.1, REQ-003.1–REQ-003.2, REQ-004.1, REQ-005.1, REQ-006.1_

- [x] 9. Exception handling — GlobalExceptionHandler
  - [x] 9.1 Crear `GlobalExceptionHandler.java` en `exception/`
    - `@RestControllerAdvice`
    - Handler para `ProductNotFoundException` → `404` con código `PRODUCT_NOT_FOUND`
    - Handler para `SkuAlreadyExistsException` → `409` con código `SKU_ALREADY_EXISTS`
    - Handler para `ImmutableSkuException` → `422` con código `IMMUTABLE_SKU`
    - Handler para `InsufficientStockException` → `422` con código `INSUFFICIENT_STOCK`
    - Handler para `InvalidStockException` → `400` con código `INVALID_STOCK`
    - Handler para `MethodArgumentNotValidException` → `400` con código `VALIDATION_ERROR`, campo `fields` con los nombres de los campos inválidos
    - Handler para `HttpMessageNotReadableException` → `400` con código `MALFORMED_JSON`
    - Handler fallback `Exception` → `500` con código `INTERNAL_ERROR`
    - `fields` siempre es lista (nunca `null`) — lista vacía cuando no aplica
    - _Requirements: REQ-001.7, REQ-002.2, REQ-004.3, REQ-004.5, REQ-005.3, REQ-006.3, REQ-007.3_

- [x] 10. OpenAPI config
  - [x] 10.1 Crear `OpenApiConfig.java` en `config/`
    - `@Configuration`
    - Bean `OpenAPI` con título `SDD Products API`, versión `1.0.0`, descripción del servicio
    - _Requirements: NFR API Documentation_

- [x] 11. Checkpoint — compilación y arranque
  - Verificar que el proyecto compila sin errores (`mvn compile`)
  - Verificar que los tests de contexto de Spring arrancan correctamente
  - Asegurarse de que MapStruct genera los mappers en `target/generated-sources`
  - Preguntar al usuario si hay dudas antes de continuar con los tests

- [x] 12. Unit tests — ProductRepositoryTest
  - [x] 12.1 Crear `ProductRepositoryTest.java` con `@DataJpaTest`
    - Verificar que `findById` excluye productos con `deletedAt != null` (valida `@SQLRestriction`)
    - Verificar que `findAll(Pageable)` excluye soft-deleted
    - Verificar que `existsBySku` retorna `false` para SKU de producto soft-deleted
    - Verificar que el registro físico permanece en BD tras soft-delete (acceso via `EntityManager` con filtro desactivado)
    - Verificar `findByNameContainingIgnoreCase` con distintas combinaciones de mayúsculas/minúsculas
    - Patrón de nombres: `should_[expectedBehavior]_when_[condition]`
    - _Requirements: REQ-002.3, REQ-003.1, REQ-005.2_

  - [ ]* 12.2 Escribir property test para `@SQLRestriction` — Property 6
    - **Property 6: Listado excluye soft-deleted**
    - **Validates: Requirements REQ-003.1**

- [x] 13. Unit tests — ProductServiceImplTest
  - [x] 13.1 Crear `ProductServiceImplTest.java` con `@ExtendWith(MockitoExtension.class)`
    - `create`: verifica que se llama `UUID.randomUUID()` (id no nulo y de tipo UUID), que `existsBySku` se consulta antes de `save`, que lanza `SkuAlreadyExistsException` cuando `existsBySku` retorna `true`
    - `findById`: verifica `200` con producto activo, lanza `ProductNotFoundException` cuando `findById` retorna `Optional.empty()`
    - `update`: verifica actualización de campos no nulos, que `updatedAt` se renueva, que lanza `ImmutableSkuException` (simular detección de campo `sku` en body), que lanza `ProductNotFoundException` para id inexistente
    - `delete`: verifica que `deletedAt` se asigna y `save` se llama, que lanza `ProductNotFoundException` para id inexistente
    - `adjustStock`: verifica `newStock = currentStock + delta` para delta positivo y negativo válido, que lanza `InsufficientStockException` cuando `currentStock + delta < 0`, que lanza `ProductNotFoundException` para id inexistente
    - Patrón AAA, nombres `should_[expectedBehavior]_when_[condition]`
    - _Requirements: REQ-001.1–REQ-001.6, REQ-002.2–REQ-002.3, REQ-004.1–REQ-004.4, REQ-005.1–REQ-005.3, REQ-006.1–REQ-006.4_

  - [ ]* 13.2 Escribir property test para creación — Property 1
    - **Property 1: Creación exitosa — estado inicial correcto**
    - **Validates: Requirements REQ-001.1, REQ-001.2, REQ-001.3, REQ-001.4**

  - [ ]* 13.3 Escribir property test para ajuste de stock válido — Property 14
    - **Property 14: Ajuste de stock válido — newStock = currentStock + delta**
    - **Validates: Requirements REQ-006.1, REQ-006.2**

  - [ ]* 13.4 Escribir property test para stock no negativo — Property 15
    - **Property 15: Stock no negativo — delta inválido rechazado sin efecto**
    - **Validates: Requirements REQ-006.3**

- [x] 14. Unit tests — ProductControllerTest
  - [x] 14.1 Crear `ProductControllerTest.java` con `@WebMvcTest(ProductController.class)` + `MockMvc`
    - `POST /api/v1/products`: verifica `201` con body válido, `400` con campos inválidos (name en blanco, price negativo, sku con caracteres inválidos), `409` cuando service lanza `SkuAlreadyExistsException`
    - `GET /api/v1/products/{id}`: verifica `200` con `ProductResponse` correcto, `404` cuando service lanza `ProductNotFoundException`
    - `GET /api/v1/products`: verifica `200` con `PaginatedResponse`, parámetros de paginación por defecto
    - `PUT /api/v1/products/{id}`: verifica `200` con campos actualizados, `404`, `422` para `ImmutableSkuException`
    - `DELETE /api/v1/products/{id}`: verifica `204` sin body, `404`
    - `PATCH /api/v1/products/{id}/stock`: verifica `200` con stock actualizado, `422` para `InsufficientStockException`, `400` para JSON malformado
    - Verificar estructura de `ErrorResponse` (campos `error`, `message`, `fields`) en cada caso de error
    - _Requirements: REQ-001.1, REQ-001.7, REQ-002.1–REQ-002.2, REQ-003.1, REQ-004.1, REQ-004.3, REQ-005.1, REQ-006.1, REQ-006.3, REQ-007.3_

  - [ ]* 14.2 Escribir property test para validación de request — Property 2
    - **Property 2: Validación de request — campos inválidos rechazados**
    - **Validates: Requirements REQ-001.7, REQ-001.8, REQ-001.9, REQ-001.10, REQ-001.11, REQ-001.12**

  - [ ]* 14.3 Escribir property test para SKU duplicado — Property 3
    - **Property 3: SKU duplicado entre activos → 409**
    - **Validates: Requirements REQ-001.6**

  - [ ]* 14.4 Escribir property test para round-trip crear y consultar — Property 4
    - **Property 4: Consulta por ID — round-trip crear y consultar**
    - **Validates: Requirements REQ-002.1**

  - [ ]* 14.5 Escribir property test para producto no visible → 404 — Property 5
    - **Property 5: Producto no visible → 404**
    - **Validates: Requirements REQ-002.2, REQ-002.3, REQ-004.4, REQ-005.3, REQ-006.4**

  - [ ]* 14.6 Escribir property test para filtro por category — Property 7
    - **Property 7: Filtro por category**
    - **Validates: Requirements REQ-003.3**

  - [ ]* 14.7 Escribir property test para filtro por rango de precio — Property 8
    - **Property 8: Filtro por rango de precio**
    - **Validates: Requirements REQ-003.4, REQ-003.5**

  - [ ]* 14.8 Escribir property test para filtro por name — Property 9
    - **Property 9: Filtro por name (contains, case-insensitive)**
    - **Validates: Requirements REQ-003.6**

  - [ ]* 14.9 Escribir property test para filtro por SKU — Property 10
    - **Property 10: Filtro por SKU (exact match)**
    - **Validates: Requirements REQ-003.7**

  - [ ]* 14.10 Escribir property test para actualización exitosa — Property 11
    - **Property 11: Actualización exitosa — campos actualizados y updatedAt renovado**
    - **Validates: Requirements REQ-004.1, REQ-004.2**

  - [ ]* 14.11 Escribir property test para SKU inmutable — Property 12
    - **Property 12: SKU inmutable — PUT con campo sku → 422**
    - **Validates: Requirements REQ-004.3**

  - [ ]* 14.12 Escribir property test para soft-delete e invisibilidad — Property 13
    - **Property 13: Soft-delete — deletedAt asignado e invisibilidad posterior**
    - **Validates: Requirements REQ-005.1, REQ-005.2**

  - [ ]* 14.13 Escribir property test para JSON round-trip — Property 16
    - **Property 16: JSON round-trip — serializar y deserializar ProductResponse**
    - **Validates: Requirements REQ-007.1, REQ-007.2, REQ-007.4**

- [x] 15. Property-based tests — ProductPropertyTest
  - [x] 15.1 Crear `ProductPropertyTest.java` en `test/.../property/` con jqwik
    - Clase anotada con `@ExtendWith(SpringExtension.class)` y `@SpringBootTest` o `@WebMvcTest` según el scope de cada propiedad
    - Implementar generadores `@Provide`: `validCreateRequests()`, `invalidCreateRequests()`, `validSkus()`, `validUpdateRequests()`, `validProductResponses()`, `positiveBigDecimals()`, `mixedProductSets()`
    - Cada `@Property(tries = 100)` etiquetado con comentario `// Feature: product, Property N: descripción`
    - _Requirements: NFR Test Coverage_

  - [x] 15.2 Implementar Properties 1–5 (creación, validación, SKU duplicado, round-trip, 404)
    - Property 1: `should_haveCorrectInitialState_when_productIsCreated` — verifica `id != null`, `sku/name/price/category` iguales al request, `deletedAt == null`, `createdAt == updatedAt`
    - Property 2: `should_return400_when_requestHasInvalidFields` — genera requests con al menos un campo inválido, verifica HTTP 400 y `fields` no vacío
    - Property 3: `should_return409_when_skuAlreadyExists` — crea producto, intenta crear otro con mismo SKU, verifica 409
    - Property 4: `should_returnEquivalentProduct_when_queriedAfterCreation` — crea y consulta, verifica equivalencia de campos
    - Property 5: `should_return404_when_productNotVisible` — UUID aleatorio o id de soft-deleted, verifica 404 en GET/PUT/PATCH/DELETE
    - _Requirements: REQ-001.1–REQ-001.6, REQ-002.1–REQ-002.3_

  - [x] 15.3 Implementar Properties 6–10 (listado y filtros)
    - Property 6: `should_excludeSoftDeleted_when_listing` — mezcla activos y soft-deleted, verifica que ningún soft-deleted aparece en ninguna página
    - Property 7: `should_returnOnlyMatchingCategory_when_categoryFilterApplied` — verifica que todos los resultados tienen la category del filtro
    - Property 8: `should_returnProductsInPriceRange_when_priceFilterApplied` — verifica `price >= min && price <= max` para todos los resultados
    - Property 9: `should_returnProductsContainingName_when_nameFilterApplied` — verifica `name.toLowerCase().contains(fragment.toLowerCase())`
    - Property 10: `should_returnExactSkuMatch_when_skuFilterApplied` — verifica que todos los resultados tienen exactamente el SKU del filtro
    - _Requirements: REQ-003.1, REQ-003.3–REQ-003.7_

  - [x] 15.4 Implementar Properties 11–16 (actualización, soft-delete, stock, JSON)
    - Property 11: `should_reflectChangesAndUpdateTimestamp_when_updateSucceeds` — verifica campos actualizados y `updatedAt >= updatedAt_previo`
    - Property 12: `should_return422_when_putIncludesSku` — body con campo `sku`, verifica 422 y producto sin cambios
    - Property 13: `should_beInvisibleAfterDelete_when_softDeleteApplied` — verifica `deletedAt != null` en BD y 404 en operaciones posteriores
    - Property 14: `should_applyDeltaCorrectly_when_stockAdjustmentIsValid` — verifica `newStock == currentStock + delta` para delta válido
    - Property 15: `should_return422AndLeaveStockUnchanged_when_deltaWouldResultInNegativeStock` — verifica 422 y stock sin cambios
    - Property 16: `should_preserveAllFields_when_jsonRoundTrip` — serializa `ProductResponse` a JSON y deserializa, verifica equivalencia campo a campo incluyendo precisión de `BigDecimal`
    - _Requirements: REQ-004.1–REQ-004.3, REQ-005.1–REQ-005.2, REQ-006.1–REQ-006.3, REQ-007.1, REQ-007.2, REQ-007.4_

- [x] 16. Checkpoint final — cobertura y calidad
  - Ejecutar `mvn test` y verificar que todos los tests pasan
  - Verificar que JaCoCo reporta >= 80% de cobertura en líneas y ramas
  - Verificar que los 16 `@Property(tries = 100)` se ejecutan sin fallos
  - Preguntar al usuario si hay dudas antes de dar por completada la implementación

- [x] 19. Inicializar repositorio Git local
  - [x] 19.1 Ejecutar `git init` en la raíz del proyecto
  - [x] 19.2 Crear `.gitignore` con entradas para `target/`, `.env`, `*.class`, `*.jar`, `*.log`, `application-local.yml`
  - [x] 19.3 Hacer el primer commit de estructura: `git add .gitignore && git commit -m "chore: initialize repository"`
  - _Requirements: Flujo de entrega_

- [x] 20. Crear rama de feature
  - [x] 20.1 Ejecutar `git checkout -b feat/product-management`
  - [x] 20.2 Verificar que la rama activa es `feat/product-management` con `git branch --show-current`
  - _Requirements: Flujo de entrega_

- [x] 21. Commit del código generado con Conventional Commits
  - [x] 21.1 Ejecutar `git add .` para agregar todos los archivos generados
  - [x] 21.2 Ejecutar el commit con el mensaje:
    ```
    feat(products): implement product management CRUD API

    - REQ-001 a REQ-007 implementados según requirements.md
    - Arquitectura en capas (Controller → Service → Repository → Entity)
    - 16 propiedades de corrección validadas con jqwik
    - Cobertura >= 80% verificada con JaCoCo
    - Documentación OpenAPI en /swagger-ui.html
    - Artefactos SDD: requirements.md, design.md, tasks.md
    - Documentación Confluence en docs/confluence/
    - Historias Jira en docs/jira-stories.md

    Refs: #product-management
    ```
  - _Requirements: REQ-001 a REQ-007, Conventional Commits_

- [x] 22. Push al repositorio remoto
  - [x] 22.1 Verificar que la variable de entorno `GITHUB_REPO_URL` está configurada
    - Si no está configurada, detener y notificar al usuario: "Configura GITHUB_REPO_URL con la URL HTTPS de tu repositorio en GitHub antes de continuar"
  - [x] 22.2 Ejecutar `git remote add origin $GITHUB_REPO_URL`
  - [x] 22.3 Ejecutar `git push -u origin feat/product-management`
    - Si el push falla por autenticación, verificar que `GITHUB_TOKEN` está configurado y usar: `git remote set-url origin https://$GITHUB_TOKEN@<host>/<org>/<repo>.git`
  - _Requirements: Flujo de entrega_

- [-] 23. Crear Pull Request en GitHub
  - [x] 23.1 Verificar que `GITHUB_TOKEN` está configurado como variable de entorno
  - [-] 23.2 Crear el PR via GitHub CLI (`gh pr create`) con:
    - `--base develop`
    - `--title "feat(products): implement product management CRUD API"`
    - `--body` con la descripción completa incluyendo:
      - Trazabilidad: `requirements.md`, `design.md`, `tasks.md`
      - Checklist: SOLID ✅, Clean Code ✅, tests ✅, cobertura >= 80% ✅, sin secrets hardcodeados ✅
      - Instrucciones de prueba: `mvn test`, `mvn verify`, `mvn spring-boot:run`
      - Referencias a REQ-001 a REQ-007
    - Si `gh` no está instalado, imprimir la URL del PR para crearlo manualmente: `https://github.com/<org>/<repo>/compare/develop...feat/product-management`
  - _Requirements: Estándares de PR_

## Notes

- Las tareas marcadas con `*` son opcionales y pueden omitirse para un MVP más rápido
- Cada tarea referencia los requisitos específicos que implementa para trazabilidad completa
- El orden garantiza que cada tarea tiene sus dependencias resueltas: domain → repository → dto → mapper → service → controller → exception handler → tests
- Los property tests en la tarea 15 son la implementación centralizada de las 16 propiedades del design.md; las sub-tareas `*` en tareas 12–14 son las versiones distribuidas cerca de cada componente
- `@SQLRestriction("deleted_at IS NULL")` en la entidad es el mecanismo central del soft-delete — no requiere código adicional en Repository ni Service para el filtrado

- [x] 17. Documentación Jira — historias de usuario y subtareas
  - [x] 17.1 Generar `docs/jira-stories.md` con las épicas, historias y subtareas en formato Jira-ready
    - **Épica**: `[EPIC] SDD Products API — Gestión de Catálogo de Productos`
    - Una historia por cada REQ-001 a REQ-007, con formato:
      ```
      Título: [STORY] REQ-00X: <nombre>
      Como: <rol>
      Quiero: <acción>
      Para: <beneficio>
      Criterios de aceptación: (copiados de requirements.md)
      Story Points: estimación
      Labels: backend, spring-boot, sdd
      ```
    - Subtareas técnicas por historia derivadas de las tareas de implementación correspondientes
    - Incluir sección de NFRs como tareas de tipo "Technical Task"
    - _Requirements: REQ-001 a REQ-007, NFRs_

  - [x] 17.2 Generar `docs/jira-import.csv` en formato CSV compatible con importación masiva de Jira
    - Columnas: `Issue Type`, `Summary`, `Description`, `Acceptance Criteria`, `Story Points`, `Labels`, `Epic Link`, `Parent`
    - Una fila por épica, historia y subtarea
    - Permite importar todo el backlog de una vez desde Jira → Projects → Import
    - _Requirements: REQ-001 a REQ-007_

- [x] 18. Documentación técnica Confluence
  - [x] 18.1 Generar `docs/confluence/01-overview.md` — Visión general del sistema
    - Descripción del producto, usuarios, alcance y decisiones de diseño
    - Diagrama de arquitectura en capas (texto Mermaid embebido)
    - Reglas de negocio críticas con ejemplos de comportamiento esperado
    - Basado en: `project.md` y sección Overview de `design.md`

  - [x] 18.2 Generar `docs/confluence/02-api-reference.md` — Referencia de la API REST
    - Tabla de endpoints con método, URL, descripción, request body y response body
    - Ejemplos de request/response JSON para cada endpoint (casos exitosos y de error)
    - Tabla completa de códigos de error con código, HTTP status y descripción
    - Basado en: sección API Contract de `design.md`

  - [x] 18.3 Generar `docs/confluence/03-data-model.md` — Modelo de datos
    - Diagrama entidad-relación de la tabla `products` (texto)
    - Descripción de cada campo con tipo, restricciones y significado de negocio
    - Explicación del mecanismo de soft-delete (`deletedAt`)
    - Basado en: secciones Data Models y Database Schema de `design.md`

  - [x] 18.4 Generar `docs/confluence/04-dev-guide.md` — Guía de desarrollo
    - Requisitos previos (Java 17, Maven, PostgreSQL 15)
    - Pasos para levantar el proyecto localmente
    - Cómo ejecutar los tests (`mvn test`) y ver el reporte JaCoCo
    - Convenciones de código, estructura de carpetas y reglas del stack
    - Basado en: `tech-stack.md` y `structure.md`

  - [x] 18.5 Generar `docs/confluence/05-testing-strategy.md` — Estrategia de testing
    - Descripción del enfoque dual: unit tests + property-based tests
    - Tabla de las 16 propiedades de corrección con descripción y requisito que valida
    - Cómo interpretar los reportes de JaCoCo y jqwik
    - Basado en: sección Testing Strategy de `design.md`
