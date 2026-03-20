# SDD Products API — Referencia de la API REST

## Endpoints

| Método | URL | Descripción | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/products` | Crear producto | `CreateProductRequest` | `201 ProductResponse` |
| `GET` | `/api/v1/products/{id}` | Consultar por ID | — | `200 ProductResponse` |
| `GET` | `/api/v1/products` | Listar con filtros | Query params | `200 PaginatedResponse<ProductResponse>` |
| `PUT` | `/api/v1/products/{id}` | Actualizar producto | `UpdateProductRequest` | `200 ProductResponse` |
| `DELETE` | `/api/v1/products/{id}` | Eliminar (soft-delete) | — | `204 No Content` |
| `PATCH` | `/api/v1/products/{id}/stock` | Ajustar stock | `StockAdjustmentRequest` | `200 ProductResponse` |

---

## POST /api/v1/products — Crear Producto

**Request Body:**
```json
{
  "sku": "PROD-001",
  "name": "Laptop Pro 15",
  "description": "Laptop de alto rendimiento",
  "price": 1299.9900,
  "stock": 50,
  "category": "ELECTRONICS"
}
```

**Response 201:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "sku": "PROD-001",
  "name": "Laptop Pro 15",
  "description": "Laptop de alto rendimiento",
  "price": 1299.9900,
  "stock": 50,
  "category": "ELECTRONICS",
  "createdAt": "2024-01-15T10:30:00+00:00",
  "updatedAt": "2024-01-15T10:30:00+00:00"
}
```

**Response 400 — Validación fallida:**
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "fields": ["sku", "price"]
}
```

**Response 409 — SKU duplicado:**
```json
{
  "error": "SKU_ALREADY_EXISTS",
  "message": "Product with SKU already exists: PROD-001",
  "fields": []
}
```

---

## GET /api/v1/products/{id} — Consultar por ID

**Response 200:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "sku": "PROD-001",
  "name": "Laptop Pro 15",
  "description": "Laptop de alto rendimiento",
  "price": 1299.9900,
  "stock": 50,
  "category": "ELECTRONICS",
  "createdAt": "2024-01-15T10:30:00+00:00",
  "updatedAt": "2024-01-15T10:30:00+00:00"
}
```

**Response 404:**
```json
{
  "error": "PRODUCT_NOT_FOUND",
  "message": "Product not found with id: 550e8400-e29b-41d4-a716-446655440000",
  "fields": []
}
```

---

## GET /api/v1/products — Listar Productos

**Query Parameters:**

| Parámetro | Tipo | Default | Descripción |
|---|---|---|---|
| `page` | `int` | `0` | Número de página (0-indexed) |
| `size` | `int` | `10` | Tamaño de página |
| `category` | `string` | — | Filtro exacto: `ELECTRONICS`, `PERIPHERALS`, `SOFTWARE`, `ACCESSORIES` |
| `minPrice` | `decimal` | — | Precio mínimo (inclusive) |
| `maxPrice` | `decimal` | — | Precio máximo (inclusive) |
| `name` | `string` | — | Substring del nombre (case-insensitive) |
| `sku` | `string` | — | SKU exacto (case-sensitive) |

**Response 200:**
```json
{
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "sku": "PROD-001",
      "name": "Laptop Pro 15",
      "price": 1299.9900,
      "stock": 50,
      "category": "ELECTRONICS",
      "createdAt": "2024-01-15T10:30:00+00:00",
      "updatedAt": "2024-01-15T10:30:00+00:00"
    }
  ],
  "page": 0,
  "pageSize": 10,
  "total": 1
}
```

---

## PUT /api/v1/products/{id} — Actualizar Producto

Solo los campos presentes y no nulos en el body se actualizan. El campo `sku` no es aceptado.

**Request Body (todos los campos son opcionales):**
```json
{
  "name": "Laptop Pro 15 Updated",
  "description": "Nueva descripción",
  "price": 1199.9900,
  "stock": 45,
  "category": "ELECTRONICS"
}
```

**Response 422 — Intento de cambiar SKU:**
```json
{
  "error": "IMMUTABLE_SKU",
  "message": "SKU cannot be modified after creation",
  "fields": []
}
```

---

## DELETE /api/v1/products/{id} — Eliminar Producto

Realiza un soft-delete: asigna `deletedAt` al timestamp actual. El registro físico se preserva.

**Response 204:** Sin body.

---

## PATCH /api/v1/products/{id}/stock — Ajustar Stock

**Request Body:**
```json
{
  "delta": -5
}
```

Delta positivo = incremento. Delta negativo = decremento.

**Response 200:** `ProductResponse` con stock actualizado.

**Response 422 — Stock insuficiente:**
```json
{
  "error": "INSUFFICIENT_STOCK",
  "message": "Insufficient stock. Current: 3, requested delta: -5",
  "fields": []
}
```

---

## Tabla de Códigos de Error

| Código | HTTP Status | Descripción |
|---|---|---|
| `PRODUCT_NOT_FOUND` | 404 Not Found | No existe producto activo con el ID proporcionado |
| `SKU_ALREADY_EXISTS` | 409 Conflict | Ya existe un producto activo con ese SKU |
| `IMMUTABLE_SKU` | 422 Unprocessable Entity | Intento de modificar el SKU de un producto existente |
| `INSUFFICIENT_STOCK` | 422 Unprocessable Entity | El ajuste de stock resultaría en un valor negativo |
| `INVALID_STOCK` | 400 Bad Request | Valor de stock inválido |
| `VALIDATION_ERROR` | 400 Bad Request | Uno o más campos del request no pasan la validación Bean Validation |
| `MALFORMED_JSON` | 400 Bad Request | El body de la request no es JSON válido o no es parseable |
| `INTERNAL_ERROR` | 500 Internal Server Error | Error inesperado del servidor |

---

## Valores de Category

| Valor | Descripción |
|---|---|
| `ELECTRONICS` | Dispositivos electrónicos |
| `PERIPHERALS` | Periféricos de computadora |
| `SOFTWARE` | Productos de software |
| `ACCESSORIES` | Accesorios varios |
