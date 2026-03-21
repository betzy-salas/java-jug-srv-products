# Stack Tecnológico

## Core

| Tecnología | Versión | Uso |
|---|---|---|
| Java | 17 | Lenguaje principal |
| Spring Boot | 3.2.x | Framework base |
| Spring Data JPA | (incluido en Boot 3.2) | Abstracción de acceso a datos |
| Hibernate | 6.4.x (incluido en Boot 3.2) | ORM — proveedor JPA |
| PostgreSQL | 15 | Base de datos relacional |
| Maven | 3.9.x | Build y gestión de dependencias |
| MapStruct | 1.5.x | Mapeo DTO ↔ Entity en tiempo de compilación |
| Flyway | 10.x (incluido en Boot 3.2) | Migraciones de base de datos |

## Testing

| Tecnología | Versión | Uso |
|---|---|---|
| JUnit 5 | (incluido en Boot 3.2) | Framework de tests |
| Mockito | (incluido en Boot 3.2) | Mocking en unit tests |
| jqwik | 1.8.x | Property-Based Testing |
| JaCoCo | 0.8.x | Cobertura de código |

- Cobertura mínima requerida: **80%**
- Cobertura verificada en cada PR via plugin JaCoCo en Maven

## Documentación API

- **springdoc-openapi** 2.x (compatible con Spring Boot 3)
- Swagger UI disponible en `/swagger-ui.html`
- Spec OpenAPI 3 disponible en `/v3/api-docs`

## Dependencias Maven relevantes

```xml
<!-- Web -->
<dependency>spring-boot-starter-web</dependency>

<!-- Persistencia -->
<dependency>spring-boot-starter-data-jpa</dependency>
<dependency>postgresql</dependency>
<dependency>flyway-core</dependency>
<dependency>flyway-database-postgresql</dependency>  <!-- requerido en Flyway 10 para PostgreSQL -->

<!-- Validación — NO viene incluido por defecto en Spring Boot 3 -->
<dependency>spring-boot-starter-validation</dependency>

<!-- Mapeo -->
<dependency>mapstruct</dependency>
<dependency>mapstruct-processor</dependency>  <!-- annotation processor en maven-compiler-plugin -->

<!-- Documentación -->
<dependency>springdoc-openapi-starter-webmvc-ui</dependency>

<!-- Testing -->
<dependency>spring-boot-starter-test</dependency>  <!-- JUnit 5 + Mockito -->
<dependency>jqwik</dependency>
<dependency>jqwik-spring</dependency>
```

## Reglas de uso del stack

### ORM / Acceso a datos
- **Prohibido usar `@Query` con JPQL o SQL nativo** — ver `structure.md` sección "Repository — soft-delete"
- Todo acceso a datos via derived query methods de Spring Data JPA
- Filtro de soft-delete via `@SQLRestriction("deleted_at IS NULL")` en la entidad (feature de Hibernate 6)
- `@Transactional` obligatorio en todos los métodos de `ProductServiceImpl` que escriben en BD

### Tipos de datos
- IDs: `UUID` — generados en Service con `UUID.randomUUID()`, nunca con `@GeneratedValue(IDENTITY)`
- Precios: `BigDecimal` — nunca `double` ni `float` para valores monetarios
- Fechas: `OffsetDateTime` — serializado a ISO 8601 en respuestas JSON

### Validación
- Bean Validation en DTOs de request con `@Valid` en el Controller
- Anotaciones correctas por tipo:
  - Strings requeridos: `@NotBlank`
  - Objetos requeridos: `@NotNull`
  - Precio (debe ser positivo): `@NotNull @Positive` — NO usar `@Min(0)` para precios
  - Stock (mínimo 0): `@Min(0)`
  - Delta de stock (puede ser negativo): sin restricción de rango, validación en Service

### MapStruct
- Procesador de anotaciones configurado en `maven-compiler-plugin`
- `@Mapper(componentModel = "spring")` para integración con Spring DI
- Nunca mapear manualmente entre DTO y entidad fuera del mapper

## Comandos Maven en Windows (PowerShell)

El entorno de ejecución es **Windows con PowerShell**. `tail` no existe — usar equivalentes PowerShell:

```powershell
# Compilar (sin tail)
mvn compile

# Compilar y capturar últimas líneas
mvn compile 2>&1 | Select-Object -Last 20

# Ejecutar tests
mvn test

# Ejecutar tests y capturar últimas líneas
mvn test 2>&1 | Select-Object -Last 30

# Verificar con JaCoCo
mvn verify 2>&1 | Select-Object -Last 30
```

**Regla**: NUNCA usar `tail`, `grep`, `cat`, `find` en comandos shell — usar cmdlets PowerShell equivalentes (`Select-Object -Last N`, `Select-String`, `Get-Content`, `Get-ChildItem`).
