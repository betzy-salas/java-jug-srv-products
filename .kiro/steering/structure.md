# Arquitectura y Estructura del Proyecto

## Arquitectura en capas (estricta)

```
Controller → Service (interface) → Repository → Domain Entity
```

Reglas:
- El Controller solo conoce la interfaz de Service, nunca la implementación ni el Repository
- El Service solo conoce el Repository y las entidades de dominio
- El Repository solo conoce la entidad JPA
- Las excepciones de dominio se lanzan desde Service y se capturan en GlobalExceptionHandler
- Los DTOs nunca cruzan la frontera de la capa Service hacia abajo

## Estructura de carpetas

```
src/
  main/
    java/com/sdd/products/
      controller/
        ProductController.java       # @RestController, @RequestMapping("/api/v1/products")
      service/
        ProductService.java          # Interface — define el contrato del caso de uso
        impl/
          ProductServiceImpl.java    # @Service, @Transactional
      repository/
        ProductRepository.java       # Interface JpaRepository<Product, UUID>
                                     # Solo derived methods — NO @Query JPQL/SQL nativo
                                     # Filtro soft-delete via @SQLRestriction en la entidad
      domain/
        entity/
          Product.java               # @Entity, campos: id(UUID), sku, name, description,
                                     # price(BigDecimal), stock(int), createdAt, updatedAt,
                                     # deletedAt(nullable) — null=activo, timestamp=eliminado
        exception/                   # Excepciones de dominio (extienden RuntimeException)
          ProductNotFoundException.java      # → HTTP 404
          SkuAlreadyExistsException.java     # → HTTP 409
          ImmutableSkuException.java         # → HTTP 422
          InsufficientStockException.java    # → HTTP 422
          InvalidStockException.java         # → HTTP 400
      dto/
        request/
          CreateProductRequest.java  # @NotBlank sku, name; @NotNull @Positive price;
                                     # @Min(0) stock (opcional, default 0)
          UpdateProductRequest.java  # name, description, price, stock — todos opcionales
                                     # sku ausente (no se acepta)
          StockAdjustmentRequest.java # delta: int (positivo=incremento, negativo=decremento)
        response/
          ProductResponse.java       # id, sku, name, description, price, stock,
                                     # createdAt(String ISO8601), updatedAt(String ISO8601)
          PaginatedResponse.java     # data(List<T>), page, pageSize, total
          ErrorResponse.java         # error(String código), message(String), fields(List<String>)
      mapper/
        ProductMapper.java           # @Mapper(componentModel="spring") MapStruct interface
                                     # toResponse(Product), toEntity(CreateProductRequest)
      config/
        OpenApiConfig.java           # Configuración springdoc-openapi (título, versión, descripción)
      exception/
        GlobalExceptionHandler.java  # @RestControllerAdvice — traduce excepciones a ErrorResponse
    resources/
      db/migration/
        V1__create_products_table.sql  # Tabla products con todos los campos incluyendo deleted_at
      application.yml                  # Config principal (datasource, jpa, flyway)
      application-test.yml             # Config para tests (H2 o Testcontainers PostgreSQL)
  test/
    java/com/sdd/products/
      controller/
        ProductControllerTest.java   # @WebMvcTest + MockMvc — prueba HTTP in/out
      service/
        ProductServiceImplTest.java  # @ExtendWith(MockitoExtension) — lógica de negocio
      repository/
        ProductRepositoryTest.java   # @DataJpaTest — queries y soft-delete
      property/
        ProductPropertyTest.java     # jqwik @Property — 16 propiedades de corrección
```

## Notas de implementación importantes

### Entidad Product

- `id`: `UUID`, generado en `ProductServiceImpl` con `UUID.randomUUID()`, NO con `@GeneratedValue`
- `price`: `BigDecimal` (nunca `double` o `float` para valores monetarios)
- `stock`: `int` o `Integer`
- `deletedAt`: `OffsetDateTime` nullable — `@SQLRestriction("deleted_at IS NULL")` en la entidad aplica el filtro automáticamente en todas las operaciones estándar de JPA/Hibernate. `null` = activo, timestamp = eliminado.
- `createdAt` / `updatedAt`: `OffsetDateTime`, gestionados con `@PrePersist` / `@PreUpdate`

### Repository — soft-delete

**Regla estricta: NO se permite el uso de `@Query` con JPQL o SQL nativo.** Todo acceso a datos debe realizarse mediante los mecanismos nativos de Spring Data JPA / Hibernate:

- **Derived query methods**: métodos cuyo nombre Spring Data traduce automáticamente a queries
- **`@SQLRestriction`** (Hibernate 6+): filtro global a nivel de entidad que excluye automáticamente los soft-deleted en todas las operaciones estándar

Configuración en la entidad:

```java
@Entity
@SQLRestriction("deleted_at IS NULL")  // Hibernate aplica este filtro en todas las queries estándar
public class Product { ... }
```

Con `@SQLRestriction` activo, los derived methods funcionan correctamente sin código adicional:

```java
public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findById(UUID id);              // excluye soft-deleted automáticamente
    Page<Product> findAll(Pageable pageable);          // excluye soft-deleted automáticamente
    boolean existsBySku(String sku);                  // excluye soft-deleted automáticamente
    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);
    Optional<Product> findBySku(String sku);
}
```

Para operaciones de auditoría que requieran acceder a registros eliminados, usar un `EntityManager` con `@Filter` de Hibernate desactivado — esto es la excepción, no la regla, y debe estar documentado explícitamente.

### GlobalExceptionHandler — mapeo de excepciones

```
ProductNotFoundException      → 404  NOT_FOUND
SkuAlreadyExistsException     → 409  CONFLICT
ImmutableSkuException         → 422  UNPROCESSABLE_ENTITY
InsufficientStockException    → 422  UNPROCESSABLE_ENTITY
InvalidStockException         → 400  BAD_REQUEST
MethodArgumentNotValidException → 400 (Bean Validation errors)
HttpMessageNotReadableException → 400 (JSON malformado)
```

### Paginación

Usar `Pageable` de Spring Data con parámetros `page` (0-indexed) y `size`. El Controller recibe `@RequestParam` y construye `PageRequest.of(page, size)`.

## Convenciones de código

### SOLID

- **S**: Una responsabilidad por clase. Controller = HTTP. Service = lógica de negocio. Repository = datos.
- **O**: Comportamiento nuevo via interfaces, no modificando clases existentes.
- **L**: `ProductServiceImpl` es intercambiable por cualquier otra implementación de `ProductService`.
- **I**: Interfaces de Service granulares, no monolíticas.
- **D**: Inyección via constructor únicamente. Nunca `@Autowired` en campo.

### Clean Code

- Métodos < 20 líneas
- Nombres en inglés, descriptivos y sin abreviaciones
- Sin números mágicos — usar constantes con nombre
- Un nivel de abstracción por método
- Early return en lugar de else anidado
- Clases < 200 líneas como guía general
- Sin comentarios obvios; el código se documenta a sí mismo
- Javadoc en interfaces de Service y métodos públicos del Controller

### Tests

- Patrón AAA: Arrange / Act / Assert
- Nombres: `should_[expectedBehavior]_when_[condition]`
- Un assert conceptual por test
- Property tests etiquetados: `// Feature: product, Property N: descripción`
- Mínimo 100 tries por propiedad jqwik (`@Property(tries = 100)`)

### DTOs y Entidades

- DTOs nunca expuestos como entidades JPA ni viceversa
- Mapeo exclusivo via MapStruct (`ProductMapper`)
- DTOs de request con Bean Validation completa (`@Valid` en Controller)
- `UpdateProductRequest` no incluye campo `sku` — si llega en el body, el Service lo ignora y lanza `ImmutableSkuException`

### Manejo de errores

- Excepciones de dominio extienden `RuntimeException` (unchecked)
- `GlobalExceptionHandler` es el único lugar donde se construye `ErrorResponse`
- Nunca retornar `null`; usar `Optional<T>` o lanzar excepción de dominio
- Validaciones de negocio en Service, validaciones de formato en DTOs con Bean Validation

### Commits (Conventional Commits)

- `feat:` nueva funcionalidad
- `fix:` corrección de bug
- `refactor:` sin cambio de comportamiento
- `test:` añadir o modificar tests
- `docs:` documentación

## Estándares de PR

- Mínimo 2 approvals
- CI verde obligatorio
- Cobertura >= 80% verificada con JaCoCo
- Artefactos SDD completos y referenciados en la descripción del PR

## Flujo de entrega a GitHub

### Convención de branches

```
feat/<issue-id>-<descripcion-kebab>    # nueva funcionalidad
fix/<issue-id>-<descripcion-kebab>     # corrección de bug
refactor/<issue-id>-<descripcion-kebab>
```

Ejemplos:
- `feat/42-product-management`
- `fix/87-stock-negative-validation`

Rama base de integración: `develop` → merge a `main` solo via release.

### Pasos del flujo

1. **Inicializar repositorio local**
   ```bash
   git init
   echo "target/\n.env\n*.class\n*.jar" > .gitignore
   git add .gitignore
   ```

2. **Crear rama de feature**
   ```bash
   git checkout -b feat/<issue-id>-<descripcion-kebab>
   ```

3. **Commit con Conventional Commits**
   ```bash
   git add .
   git commit -m "feat(products): implement product management CRUD API

   - REQ-001 a REQ-007 implementados según requirements.md
   - Arquitectura en capas según design.md
   - 16 propiedades de corrección validadas con jqwik
   - Cobertura >= 80% verificada con JaCoCo

   Refs: #<issue-id>"
   ```

4. **Conectar con repositorio remoto y hacer push**
   ```bash
   git remote add origin $GITHUB_REPO_URL
   git push -u origin feat/<issue-id>-<descripcion-kebab>
   ```
   > `GITHUB_REPO_URL` debe estar configurada como variable de entorno antes de ejecutar esta tarea.
   > Formato: `https://github.com/<org>/<repo>.git`

5. **Crear Pull Request**
   - Base: `develop`
   - Título: `feat(products): implement product management CRUD API`
   - Descripción debe incluir:
     - Trazabilidad a `requirements.md`, `design.md`, `tasks.md`
     - Checklist: SOLID, Clean Code, tests, cobertura >= 80%, sin secrets hardcodeados
     - Instrucciones de prueba local

### Variables de entorno requeridas para el flujo Git

| Variable | Descripción | Ejemplo |
|---|---|---|
| `GITHUB_REPO_URL` | URL HTTPS del repositorio remoto | `https://github.com/mi-org/sdd-products.git` |
| `GITHUB_TOKEN` | Token de acceso personal (PAT) con scope `repo` | `ghp_xxxxxxxxxxxx` |

Estas variables **no se almacenan en ningún archivo del proyecto**. Se configuran en el entorno de ejecución (shell, CI/CD secrets, etc.).
