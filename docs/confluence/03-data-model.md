# SDD Products API — Modelo de Datos

## Diagrama Entidad-Relación

```
+--------------------------------------------------+
|                    products                       |
+--------------------------------------------------+
| id            UUID          NOT NULL  PK          |
| sku           VARCHAR(50)   NOT NULL  UNIQUE       |
| name          VARCHAR(100)  NOT NULL               |
| description   TEXT          NULL                   |
| price         NUMERIC(19,4) NOT NULL  CHECK > 0   |
| stock         INTEGER       NOT NULL  DEFAULT 0    |
|                                       CHECK >= 0  |
| category      VARCHAR(20)   NOT NULL               |
| created_at    TIMESTAMPTZ   NOT NULL               |
| updated_at    TIMESTAMPTZ   NOT NULL               |
| deleted_at    TIMESTAMPTZ   NULL                   |
+--------------------------------------------------+

Índices:
  pk_products          PRIMARY KEY (id)
  uq_products_sku      UNIQUE (sku)
  idx_products_sku     (sku)
  idx_products_category (category)
  idx_products_deleted_at (deleted_at)
  idx_products_created_at (created_at DESC)
```

---

## Descripción de Campos

| Campo | Tipo Java | Tipo SQL | Restricciones | Descripción |
|---|---|---|---|---|
| `id` | `UUID` | `UUID` | NOT NULL, PK | Identificador único. Generado con `UUID.randomUUID()` en la capa de servicio. Nunca delegado a la BD. |
| `sku` | `String` | `VARCHAR(50)` | NOT NULL, UNIQUE | Identificador de negocio. Solo mayúsculas, dígitos y guiones (`^[A-Z0-9-]+$`). Inmutable tras la creación. |
| `name` | `String` | `VARCHAR(100)` | NOT NULL | Nombre del producto. Máximo 100 caracteres. |
| `description` | `String` | `TEXT` | NULL | Descripción opcional del producto. |
| `price` | `BigDecimal` | `NUMERIC(19,4)` | NOT NULL, > 0 | Precio monetario. Precisión de 4 decimales. Nunca `double` ni `float`. |
| `stock` | `int` | `INTEGER` | NOT NULL, >= 0, DEFAULT 0 | Cantidad en inventario. Nunca negativo. |
| `category` | `Category` (enum) | `VARCHAR(20)` | NOT NULL | Clasificación del producto. Valores: `ELECTRONICS`, `PERIPHERALS`, `SOFTWARE`, `ACCESSORIES`. |
| `createdAt` | `OffsetDateTime` | `TIMESTAMPTZ` | NOT NULL | Timestamp de creación. Asignado en `@PrePersist`. Inmutable (`updatable = false`). |
| `updatedAt` | `OffsetDateTime` | `TIMESTAMPTZ` | NOT NULL | Timestamp de última modificación. Actualizado en `@PreUpdate`. |
| `deletedAt` | `OffsetDateTime` | `TIMESTAMPTZ` | NULL | Timestamp de eliminación lógica. `null` = activo, valor = eliminado. |

---

## Mecanismo de Soft-Delete

El soft-delete es el mecanismo central de eliminación en este sistema. Los productos **nunca se borran físicamente** de la base de datos.

### Cómo funciona

1. Al llamar `DELETE /api/v1/products/{id}`, el servicio asigna `deletedAt = OffsetDateTime.now()` y llama `save()`
2. El registro permanece en la tabla `products` con `deleted_at` asignado
3. La anotación `@SQLRestriction("deleted_at IS NULL")` en la entidad hace que Hibernate aplique automáticamente el filtro `WHERE deleted_at IS NULL` en **todas** las queries estándar

### Configuración en la entidad

```java
@Entity
@Table(name = "products")
@SQLRestriction("deleted_at IS NULL")  // Hibernate 6+ — filtro automático global
public class Product {
    // ...
    @Column(nullable = true)
    private OffsetDateTime deletedAt;
}
```

### Efecto en las operaciones

| Operación | Comportamiento con soft-deleted |
|---|---|
| `findById(id)` | Retorna `Optional.empty()` → lanza `ProductNotFoundException` |
| `findAll(pageable)` | Excluye automáticamente los soft-deleted |
| `existsBySku(sku)` | Retorna `false` para SKU de producto eliminado |
| `findBySku(sku)` | Retorna `Optional.empty()` |
| Todos los derived methods | Aplican el filtro automáticamente |

### Acceso a registros eliminados (auditoría)

Para acceder a registros eliminados (solo para auditoría), se debe usar `EntityManager` con una native query que bypasee el filtro:

```java
// Solo para auditoría — no usar en lógica de negocio normal
entityManager.createNativeQuery("SELECT * FROM products WHERE deleted_at IS NOT NULL")
```

---

## Enum Category

```java
public enum Category {
    ELECTRONICS,   // Dispositivos electrónicos
    PERIPHERALS,   // Periféricos de computadora
    SOFTWARE,      // Productos de software
    ACCESSORIES    // Accesorios varios
}
```

Almacenado como `VARCHAR(20)` con `@Enumerated(EnumType.STRING)` para legibilidad en la BD.

---

## Migración Flyway

El esquema se gestiona con Flyway. La migración inicial está en:

```
src/main/resources/db/migration/V1__create_products_table.sql
```

Flyway aplica las migraciones automáticamente al arrancar la aplicación. Para agregar cambios al esquema, crear un nuevo archivo `V2__descripcion.sql`.
