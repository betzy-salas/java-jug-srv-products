# Requisitos: Gestión de Productos

## Introducción

API REST para gestión de catálogo de productos de e-commerce.
Operaciones: crear, consultar, listar, actualizar, eliminar productos y ajustar stock.
URL base: `/api/v1/products`.

## Glosario

- **System**: la SDD Products API — servicio REST de gestión de catálogo
- **Operator**: usuario de negocio que gestiona el catálogo vía la API
- **External_System**: sistema externo que consume la API para leer o modificar productos
- **Product**: entidad del catálogo con campos `id`, `sku`, `name`, `description`, `price`, `stock`, `category`, `createdAt`, `updatedAt`, `deletedAt`
- **SKU**: identificador de negocio del producto — solo MAYÚSCULAS, números y guiones (ej. `PROD-001`)
- **Category**: clasificación del producto — valores permitidos: `ELECTRONICS`, `PERIPHERALS`, `SOFTWARE`, `ACCESSORIES`
- **Soft_Delete**: mecanismo de eliminación lógica que asigna un timestamp a `deletedAt` sin borrar el registro físicamente. `deletedAt = null` indica producto activo; `deletedAt != null` indica producto eliminado
- **UUID**: identificador único universal generado por el sistema en la capa de servicio
- **BigDecimal**: tipo numérico de precisión arbitraria usado para representar precios monetarios
- **PaginatedResponse**: respuesta que incluye `data`, `page`, `pageSize` y `total`
- **ErrorResponse**: respuesta de error con campos `error` (código), `message` y `fields` (lista de campos inválidos)
- **Delta**: valor entero (positivo o negativo) que representa el ajuste a aplicar sobre el stock actual

---

## Requisitos

### REQ-001: Crear Producto

**Historia de usuario:** Como Operator, quiero crear un producto con todos sus atributos, para que esté disponible en el catálogo.

#### Criterios de aceptación

1. CUANDO el Operator envía `POST /api/v1/products` con un cuerpo de solicitud válido, EL System DEBERÁ persistir el producto y retornar HTTP 201 con el `ProductResponse` creado incluyendo el UUID generado por el sistema como `id`
2. EL System DEBERÁ generar el `id` como UUID mediante `UUID.randomUUID()` — nunca delegado a la base de datos
3. EL System DEBERÁ asignar `createdAt` y `updatedAt` al `OffsetDateTime` actual en el momento de la creación
4. EL System DEBERÁ asignar `deletedAt` a `null` en la creación
5. EL System DEBERÁ asignar `stock` a `0` cuando el campo esté ausente en la solicitud
6. CUANDO la solicitud contenga un `sku` que ya exista entre los productos activos, EL System DEBERÁ retornar HTTP 409
7. SI el cuerpo de la solicitud falla la validación Bean Validation, ENTONCES EL System DEBERÁ retornar HTTP 400 con un `ErrorResponse` identificando los campos inválidos exactos
8. EL System DEBERÁ requerir que `name` sea una cadena no vacía de máximo 100 caracteres
9. EL System DEBERÁ requerir que `sku` coincida con el patrón `^[A-Z0-9-]+$` (solo letras mayúsculas, dígitos y guiones)
10. EL System DEBERÁ requerir que `price` sea un `BigDecimal` mayor a 0
11. EL System DEBERÁ requerir que `stock` sea un `int` con valor mínimo 0 cuando se proporcione
12. EL System DEBERÁ requerir que `category` sea uno de: `ELECTRONICS`, `PERIPHERALS`, `SOFTWARE`, `ACCESSORIES`

---

### REQ-002: Consultar Producto por ID

**Historia de usuario:** Como Operator o External_System, quiero consultar un producto por su ID, para ver sus datos actuales.

#### Criterios de aceptación

1. CUANDO el cliente envía `GET /api/v1/products/{id}` y existe un producto con ese UUID con `deletedAt = null`, EL System DEBERÁ retornar HTTP 200 con el `ProductResponse`
2. SI no existe ningún producto con el `id` proporcionado, ENTONCES EL System DEBERÁ retornar HTTP 404
3. SI el producto existe pero tiene `deletedAt != null`, ENTONCES EL System DEBERÁ retornar HTTP 404 — un producto soft-deleted se trata como inexistente en todas las operaciones estándar

---

### REQ-003: Listar Productos

**Historia de usuario:** Como Operator o External_System, quiero listar los productos activos con paginación y filtros, para explorar o buscar en el catálogo.

#### Criterios de aceptación

1. CUANDO el cliente envía `GET /api/v1/products`, EL System DEBERÁ retornar HTTP 200 con un `PaginatedResponse` que contenga únicamente productos donde `deletedAt = null`
2. EL System DEBERÁ usar por defecto `page=0`, `size=10`, ordenado por `createdAt` descendente cuando no se proporcionen parámetros
3. DONDE se proporcione el parámetro de filtro `category`, EL System DEBERÁ retornar únicamente los productos que coincidan con ese valor de `Category`
4. DONDE se proporcione el parámetro de filtro `minPrice`, EL System DEBERÁ retornar únicamente los productos con `price >= minPrice`
5. DONDE se proporcione el parámetro de filtro `maxPrice`, EL System DEBERÁ retornar únicamente los productos con `price <= maxPrice`
6. DONDE se proporcione el parámetro de filtro `name`, EL System DEBERÁ retornar únicamente los productos cuyo `name` contenga esa cadena (sin distinción de mayúsculas/minúsculas)
7. DONDE se proporcione el parámetro de filtro `sku`, EL System DEBERÁ retornar únicamente el producto cuyo `sku` coincida exactamente (con distinción de mayúsculas/minúsculas)
8. MIENTRAS múltiples parámetros de filtro estén activos simultáneamente, EL System DEBERÁ aplicar todos los filtros como condiciones AND

---

### REQ-004: Actualizar Producto

**Historia de usuario:** Como Operator, quiero actualizar los atributos de un producto, para que el catálogo refleje la información correcta.

#### Criterios de aceptación

1. CUANDO el Operator envía `PUT /api/v1/products/{id}` con un cuerpo de solicitud válido, EL System DEBERÁ actualizar únicamente los campos no nulos presentes en la solicitud y retornar HTTP 200 con el `ProductResponse` actualizado
2. EL System DEBERÁ actualizar `updatedAt` al `OffsetDateTime` actual en cada actualización exitosa
3. SI el cuerpo de la solicitud incluye el campo `sku`, ENTONCES EL System DEBERÁ retornar HTTP 422 — el SKU es inmutable tras la creación
4. SI no existe ningún producto con el `id` proporcionado o el producto tiene `deletedAt != null`, ENTONCES EL System DEBERÁ retornar HTTP 404
5. SI el cuerpo de la solicitud falla la validación Bean Validation, ENTONCES EL System DEBERÁ retornar HTTP 400 con un `ErrorResponse` identificando los campos inválidos exactos

---

### REQ-005: Eliminar Producto (Soft Delete)

**Historia de usuario:** Como Operator, quiero eliminar un producto del catálogo, para que deje de ser visible mientras sus datos se conservan para auditoría.

#### Criterios de aceptación

1. CUANDO el Operator envía `DELETE /api/v1/products/{id}` y el producto existe con `deletedAt = null`, EL System DEBERÁ asignar `deletedAt` al `OffsetDateTime` actual y retornar HTTP 204 sin cuerpo de respuesta
2. EL System NO DEBERÁ eliminar físicamente el registro de la base de datos — la fila debe permanecer con `deletedAt` asignado
3. SI no existe ningún producto con el `id` proporcionado o el producto ya tiene `deletedAt != null`, ENTONCES EL System DEBERÁ retornar HTTP 404

---

### REQ-006: Ajuste de Stock

**Historia de usuario:** Como Operator, quiero ajustar el stock de un producto mediante un valor delta, para que el inventario refleje los cambios reales sin reemplazar el producto completo.

#### Criterios de aceptación

1. CUANDO el Operator envía `PATCH /api/v1/products/{id}/stock` con un `StockAdjustmentRequest` válido, EL System DEBERÁ aplicar `nuevoStock = stockActual + delta` y retornar HTTP 200 con el `ProductResponse` actualizado
2. EL System DEBERÁ aceptar un `delta` de cualquier valor entero (positivo para incremento, negativo para decremento)
3. SI `stockActual + delta` resultaría en un valor menor a 0, ENTONCES EL System DEBERÁ retornar HTTP 422 y dejar el stock sin cambios
4. SI no existe ningún producto con el `id` proporcionado o el producto tiene `deletedAt != null`, ENTONCES EL System DEBERÁ retornar HTTP 404
5. EL System DEBERÁ actualizar `updatedAt` al `OffsetDateTime` actual en cada ajuste de stock exitoso

---

### REQ-007: Serialización JSON

**Historia de usuario:** Como External_System, quiero que todas las respuestas de la API sean JSON válido y que el JSON inválido sea rechazado, para que la integración sea confiable y predecible.

#### Criterios de aceptación

1. EL System DEBERÁ serializar todos los campos de `ProductResponse` a JSON válido, con `createdAt` y `updatedAt` formateados como cadenas ISO 8601 (`OffsetDateTime`)
2. EL System DEBERÁ serializar `price` como número JSON preservando la precisión de `BigDecimal` (sin pérdida de punto flotante)
3. SI el cuerpo de la solicitud contiene JSON malformado (no parseable), ENTONCES EL System DEBERÁ retornar HTTP 400 con un `ErrorResponse`
4. PARA TODOS los objetos `ProductResponse` válidos, serializar a JSON y luego deserializar DEBERÁ producir un objeto equivalente (propiedad round-trip)

---

## Requisitos No Funcionales

### Rendimiento
- EL System DEBERÁ responder a todos los endpoints con latencia P95 inferior a 200ms bajo carga normal

### Cobertura de Tests
- EL System DEBERÁ mantener una cobertura mínima de líneas y ramas del 80%, verificada por JaCoCo en cada build
- EL System DEBERÁ incluir property-based tests usando jqwik con un mínimo de 100 intentos por `@Property`

### Documentación de API
- EL System DEBERÁ exponer todos los endpoints vía especificación OpenAPI 3 en `/v3/api-docs`
- EL System DEBERÁ exponer Swagger UI en `/swagger-ui.html`
- Cada endpoint DEBERÁ incluir resumen, descripción de parámetros y todos los códigos de respuesta HTTP posibles documentados
