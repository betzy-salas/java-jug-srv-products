# SDD Products API — Guía de Desarrollo

## Requisitos Previos

| Herramienta | Versión | Instalación |
|---|---|---|
| Java | 17 | [adoptium.net](https://adoptium.net) |
| Maven | 3.9.x | [maven.apache.org](https://maven.apache.org) |
| PostgreSQL | 15 | [postgresql.org](https://www.postgresql.org) |
| Git | cualquiera | [git-scm.com](https://git-scm.com) |

---

## Levantar el Proyecto Localmente

### 1. Clonar el repositorio

```bash
git clone <repo-url>
cd products-api
```

### 2. Crear la base de datos PostgreSQL

```sql
CREATE DATABASE products_db;
CREATE USER products_user WITH PASSWORD 'products_pass';
GRANT ALL PRIVILEGES ON DATABASE products_db TO products_user;
```

### 3. Configurar variables de entorno (opcional)

Por defecto `application.yml` usa los placeholders. Puedes sobreescribirlos:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/products_db
export SPRING_DATASOURCE_USERNAME=products_user
export SPRING_DATASOURCE_PASSWORD=products_pass
```

### 4. Compilar el proyecto

```bash
mvn compile
```

### 5. Arrancar la aplicación

```bash
mvn spring-boot:run
```

La aplicación arranca en `http://localhost:8080`.
Flyway aplica automáticamente las migraciones al arrancar.

### 6. Verificar que funciona

```bash
curl http://localhost:8080/api/v1/products
# → { "data": [], "page": 0, "pageSize": 10, "total": 0 }

# Swagger UI
open http://localhost:8080/swagger-ui.html
```

---

## Ejecutar los Tests

### Todos los tests

```bash
mvn test
```

### Tests con reporte de cobertura JaCoCo

```bash
mvn verify
```

El reporte HTML se genera en `target/site/jacoco/index.html`.

### Solo tests de un módulo específico

```bash
# Solo tests de repositorio
mvn test -Dtest=ProductRepositoryTest

# Solo property tests
mvn test -Dtest=ProductPropertyTest

# Solo tests de controller
mvn test -Dtest=ProductControllerTest
```

---

## Estructura de Carpetas

```
src/
  main/
    java/com/sdd/products/
      controller/          # @RestController — capa HTTP
      service/             # Interfaz ProductService
        impl/              # ProductServiceImpl — lógica de negocio
      repository/          # ProductRepository — solo derived methods
      domain/
        entity/            # Product.java — entidad JPA
        exception/         # Excepciones de dominio (5 clases)
      dto/
        request/           # CreateProductRequest, UpdateProductRequest, StockAdjustmentRequest
        response/          # ProductResponse, PaginatedResponse, ErrorResponse
      mapper/              # ProductMapper — MapStruct
      config/              # OpenApiConfig
      exception/           # GlobalExceptionHandler
    resources/
      db/migration/        # Migraciones Flyway (V1__, V2__, ...)
      application.yml      # Config principal
      application-test.yml # Config para tests (H2)
  test/
    java/com/sdd/products/
      controller/          # ProductControllerTest (@WebMvcTest)
      service/             # ProductServiceImplTest (@ExtendWith Mockito)
      repository/          # ProductRepositoryTest (@DataJpaTest)
      property/            # ProductPropertyTest (jqwik @Property)
```

---

## Convenciones de Código

### Arquitectura

- **Controller** solo conoce la interfaz `ProductService` — nunca `ProductServiceImpl` ni `ProductRepository`
- **Service** solo conoce `ProductRepository` y entidades de dominio — nunca DTOs de response
- **Repository** solo conoce la entidad JPA — sin lógica de negocio
- Inyección **siempre via constructor** — nunca `@Autowired` en campo

### Reglas del Stack

| Regla | Correcto | Incorrecto |
|---|---|---|
| IDs | `UUID.randomUUID()` en Service | `@GeneratedValue` en entidad |
| Precios | `BigDecimal` | `double`, `float` |
| Fechas | `OffsetDateTime` | `LocalDateTime`, `Date` |
| Queries | Derived methods | `@Query` con JPQL/SQL |
| Soft-delete | `@SQLRestriction` en entidad | Campo `active: boolean` |
| Mapeo | MapStruct | Mapeo manual en Service/Controller |

### Nombres de Tests

```java
// Patrón: should_[comportamientoEsperado]_when_[condición]
void should_createProduct_when_skuIsUnique() { ... }
void should_throwSkuAlreadyExistsException_when_skuIsDuplicated() { ... }
void should_return404_when_productNotFound() { ... }
```

### Commits (Conventional Commits)

```
feat: add stock adjustment endpoint
fix: correct soft-delete filter in findAll
refactor: extract selectPage logic to private method
test: add property tests for stock invariants
docs: update API reference with new error codes
```

---

## Agregar una Nueva Migración de BD

1. Crear archivo en `src/main/resources/db/migration/`
2. Nombrar con el siguiente número de versión: `V2__descripcion_del_cambio.sql`
3. Flyway lo aplica automáticamente al arrancar

```sql
-- V2__add_brand_to_products.sql
ALTER TABLE products ADD COLUMN brand VARCHAR(50);
```

---

## Agregar un Nuevo Endpoint

1. Agregar método a la interfaz `ProductService` con Javadoc
2. Implementar en `ProductServiceImpl` con `@Transactional` si escribe en BD
3. Agregar handler en `ProductController` con `@Operation` y `@ApiResponse`
4. Si hay nueva excepción de dominio, agregar handler en `GlobalExceptionHandler`
5. Escribir tests unitarios y property tests correspondientes
6. Verificar cobertura con `mvn verify`

---

## Flujo de Entrega a GitHub

### Variables de entorno requeridas

Antes de ejecutar las tareas de entrega, configura estas variables en tu shell:

```bash
export GITHUB_REPO_URL=https://github.com/<org>/<repo>.git
export GITHUB_TOKEN=ghp_xxxxxxxxxxxx   # PAT con scope 'repo'
```

> Estas variables **no se almacenan en ningún archivo del proyecto**.

### Pasos del flujo (tareas 19–23 en tasks.md)

#### Tarea 19 — Inicializar Git

```bash
git init
# .gitignore se crea automáticamente con las entradas correctas
git add .gitignore
git commit -m "chore: initialize repository"
```

#### Tarea 20 — Crear rama de feature

```bash
git checkout -b feat/product-management
git branch --show-current   # debe mostrar: feat/product-management
```

#### Tarea 21 — Commit del código

```bash
git add .
git commit -m "feat(products): implement product management CRUD API

- REQ-001 a REQ-007 implementados según requirements.md
- Arquitectura en capas (Controller → Service → Repository → Entity)
- 16 propiedades de corrección validadas con jqwik
- Cobertura >= 80% verificada con JaCoCo
- Documentación OpenAPI en /swagger-ui.html

Refs: #product-management"
```

#### Tarea 22 — Push al remoto

```bash
git remote add origin $GITHUB_REPO_URL
git push -u origin feat/product-management
```

Si falla por autenticación:

```bash
git remote set-url origin https://$GITHUB_TOKEN@github.com/<org>/<repo>.git
git push -u origin feat/product-management
```

#### Tarea 23 — Crear Pull Request

Con GitHub CLI instalado:

```bash
gh pr create \
  --base develop \
  --title "feat(products): implement product management CRUD API" \
  --body "## Descripción
Implementación completa de SDD Products API.

## Trazabilidad
- requirements.md: REQ-001 a REQ-007
- design.md: arquitectura en capas, 16 propiedades de corrección
- tasks.md: tareas 1–23 completadas

## Checklist
- [x] SOLID compliance
- [x] Clean Code (métodos < 20 líneas)
- [x] Tests unitarios y property-based
- [x] Cobertura >= 80% (JaCoCo)
- [x] Sin secrets hardcodeados
- [x] Artefactos SDD completos

## Prueba local
\`\`\`bash
mvn test        # ejecutar tests
mvn verify      # tests + cobertura JaCoCo
mvn spring-boot:run  # arrancar la app
\`\`\`"
```

Sin GitHub CLI, crear el PR manualmente en:
`https://github.com/<org>/<repo>/compare/develop...feat/product-management`
