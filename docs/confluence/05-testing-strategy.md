# SDD Products API — Estrategia de Testing

## Enfoque Dual: Unit Tests + Property-Based Tests

La estrategia de testing combina dos enfoques complementarios para garantizar la corrección del sistema:

| Tipo | Framework | Alcance | Archivo |
|---|---|---|---|
| Unit tests — Repository | JUnit 5 + `@DataJpaTest` | Queries, soft-delete, `@SQLRestriction` | `ProductRepositoryTest` |
| Unit tests — Service | JUnit 5 + Mockito | Lógica de negocio, excepciones | `ProductServiceImplTest` |
| Unit tests — Controller | JUnit 5 + MockMvc + `@WebMvcTest` | HTTP in/out, validación, error responses | `ProductControllerTest` |
| Property-Based Tests | jqwik `@Property` | Invariantes del dominio con inputs aleatorios | `ProductPropertyTest` |

---

## Unit Tests

### ProductRepositoryTest (`@DataJpaTest`)

Verifica el comportamiento del repositorio con una BD H2 en memoria (modo PostgreSQL).

Casos clave:
- `findById` excluye productos con `deletedAt != null` — valida `@SQLRestriction`
- `findAll(Pageable)` excluye soft-deleted automáticamente
- `existsBySku` retorna `false` para SKU de producto eliminado
- El registro físico permanece en BD tras soft-delete (verificado via native query)
- `findByNameContainingIgnoreCase` funciona con distintas combinaciones de mayúsculas/minúsculas

### ProductServiceImplTest (`@ExtendWith(MockitoExtension.class)`)

Verifica la lógica de negocio con mocks del repository y mapper.

Casos clave por operación:

| Operación | Casos cubiertos |
|---|---|
| `create` | SKU único → éxito, SKU duplicado → `SkuAlreadyExistsException`, UUID asignado |
| `findById` | Producto activo → éxito, inexistente → `ProductNotFoundException` |
| `update` | Campos no nulos actualizados, inexistente → `ProductNotFoundException` |
| `delete` | `deletedAt` asignado y `save` llamado, inexistente → `ProductNotFoundException` |
| `adjustStock` | Delta positivo, delta negativo válido, delta negativo inválido → `InsufficientStockException` |

### ProductControllerTest (`@WebMvcTest`)

Verifica la capa HTTP con MockMvc y el service mockeado.

Casos clave por endpoint:

| Endpoint | Casos cubiertos |
|---|---|
| `POST /` | 201 válido, 400 name vacío, 400 price negativo, 400 SKU inválido, 409 duplicado |
| `GET /{id}` | 200 activo, 404 inexistente |
| `GET /` | 200 con paginación por defecto |
| `PUT /{id}` | 200 actualizado, 404 inexistente, 422 intento de cambiar SKU |
| `DELETE /{id}` | 204 éxito, 404 inexistente |
| `PATCH /{id}/stock` | 200 delta válido, 422 stock insuficiente, 400 JSON malformado |

---

## Property-Based Tests (jqwik)

Los property tests verifican que los **invariantes del dominio** se cumplen para cualquier input generado aleatoriamente, no solo para los casos que el desarrollador imaginó.

Cada `@Property` se ejecuta con **100 intentos** (`tries = 100`) con inputs generados por jqwik.

### Las 16 Propiedades de Corrección

| # | Nombre | Descripción | Requisito |
|---|---|---|---|
| 1 | Estado inicial correcto | `id != null`, `deletedAt == null`, campos iguales al request | REQ-001.1–4 |
| 2 | Validación de request | Campos inválidos rechazados antes de persistir | REQ-001.7–12 |
| 3 | SKU duplicado → excepción | `existsBySku=true` lanza `SkuAlreadyExistsException`, `save` nunca llamado | REQ-001.6 |
| 4 | Round-trip crear y consultar | Producto creado es recuperable con los mismos campos | REQ-002.1 |
| 5 | Producto no visible → 404 | UUID aleatorio o soft-deleted lanza `ProductNotFoundException` | REQ-002.2–3 |
| 6 | Listado excluye soft-deleted | Ningún producto con `deletedAt != null` aparece en ninguna página | REQ-003.1 |
| 7 | Filtro por category | Todos los resultados tienen exactamente la category del filtro | REQ-003.3 |
| 8 | Filtro por rango de precio | `price >= min && price <= max` para todos los resultados | REQ-003.4–5 |
| 9 | Filtro por name (case-insensitive) | `name.toLowerCase().contains(fragment.toLowerCase())` | REQ-003.6 |
| 10 | Filtro por SKU (exact match) | Todos los resultados tienen exactamente el SKU del filtro | REQ-003.7 |
| 11 | Actualización — solo campos no nulos | Campos nulos en request no modifican el producto | REQ-004.1–2 |
| 12 | SKU inmutable | El SKU del producto no cambia tras `update` | REQ-004.3 |
| 13 | Soft-delete — `deletedAt` asignado | `deletedAt != null` tras `delete`, era `null` antes | REQ-005.1–2 |
| 14 | Ajuste de stock válido | `newStock == currentStock + delta` para delta >= 0 | REQ-006.1–2 |
| 15 | Stock no negativo | Delta que resulta en stock < 0 lanza excepción, stock sin cambios | REQ-006.3 |
| 16 | JSON round-trip | Todos los campos de `ProductResponse` preservados exactamente | REQ-007.1–2–4 |

### Ejemplo de Property Test

```java
// Feature: product, Property 15: Stock no negativo — delta inválido rechazado sin efecto
@Property(tries = 100)
void should_return422AndLeaveStockUnchanged_when_deltaWouldResultInNegativeStock(
        @ForAll @IntRange(min = 0, max = 100) int currentStock,
        @ForAll @IntRange(min = 1, max = 500) int excessDelta) {

    int negativeDelta = -(currentStock + excessDelta); // siempre resulta en stock negativo
    int stockBefore = currentStock;

    // ... setup mocks ...

    assertThatThrownBy(() -> service.adjustStock(id, request))
            .isInstanceOf(InsufficientStockException.class);

    assertThat(product.getStock()).isEqualTo(stockBefore); // stock sin cambios
    verify(repository, never()).save(any());               // save nunca llamado
}
```

---

## Cobertura de Código (JaCoCo)

### Configuración

JaCoCo está configurado en `pom.xml` con los siguientes límites mínimos:

| Métrica | Mínimo requerido |
|---|---|
| Cobertura de líneas | 80% |
| Cobertura de ramas | 80% |

El build falla automáticamente si no se alcanza el mínimo (`mvn verify`).

### Cómo ver el reporte

```bash
mvn verify
open target/site/jacoco/index.html
```

El reporte muestra cobertura por paquete, clase y método. Las líneas en rojo no están cubiertas.

### Cómo interpretar los resultados de jqwik

Cuando un property test falla, jqwik muestra:
- El **contraejemplo mínimo** que falsifica la propiedad (shrinking automático)
- El número de intentos exitosos antes del fallo
- La excepción o assertion que falló

```
PropertyFailureException: Property [should_applyDeltaCorrectly...] falsified with sample {
  currentStock=0, positiveDelta=1
}
```

Esto indica exactamente qué input rompe la invariante, facilitando el diagnóstico.

---

## Ejecutar Tests

```bash
# Todos los tests
mvn test

# Con reporte de cobertura
mvn verify

# Solo property tests
mvn test -Dtest=ProductPropertyTest

# Ver reporte JaCoCo
open target/site/jacoco/index.html
```
