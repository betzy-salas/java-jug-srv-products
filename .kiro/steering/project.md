# Producto: SDD Products API

## Descripción

SDD Products API es un servicio REST de gestión de catálogo de productos para e-commerce. Expone endpoints CRUD sobre productos con validación estricta de reglas de negocio, soporte de soft-delete para auditoría y control de inventario.

Este servicio es el sistema de registro (system of record) del catálogo. Otros sistemas consumen su API para leer y modificar productos.

## Usuarios del sistema

- **Operator**: usuario de negocio que gestiona el catálogo (crear, actualizar, eliminar productos, ajustar stock)
- **External System**: sistema externo que consume la API REST para leer datos del catálogo o integrarse con él

## Reglas de negocio críticas

Estas reglas son invariantes del dominio y deben ser respetadas en toda la implementación:

1. **SKU inmutable**: el SKU de un producto no puede modificarse una vez creado. Cualquier intento debe retornar HTTP 422.
2. **Stock no negativo**: el stock nunca puede ser menor a cero. Operaciones que resulten en stock negativo deben ser rechazadas con HTTP 422.
3. **Soft-delete obligatorio**: los productos nunca se eliminan físicamente. Se marca `deletedAt` con timestamp. Los datos se conservan para auditoría.
4. **Invisibilidad post-delete**: un producto soft-deleted es tratado como inexistente en todas las operaciones estándar (GET, PUT, PATCH, DELETE retornan 404; listados lo excluyen).
5. **Campos obligatorios en creación**: nombre, SKU y precio son requeridos. Stock es opcional (default 0).
6. **SKU único**: no pueden existir dos productos activos con el mismo SKU. Duplicado retorna HTTP 409.

## Alcance del servicio

**Incluye:**
- CRUD de productos (crear, consultar por ID, listar con paginación y filtros, actualizar, eliminar)
- Ajuste de stock como operación dedicada (`PATCH /api/v1/products/{id}/stock`)
- Validación de reglas de negocio antes de persistir
- Documentación OpenAPI accesible en `/swagger-ui.html`

**No incluye:**
- Autenticación ni autorización (fuera de scope)
- Gestión de categorías como entidad separada (CRUD de categorías), imágenes o variantes de producto
- Historial de cambios de precio o stock
- Notificaciones o eventos de dominio

## Decisiones de diseño

- **Layered Architecture**: Controller → Service (interface) → Repository → Entity. Sin saltarse capas.
- **Soft-delete via `deletedAt`**: campo nullable en la entidad. `null` = activo, timestamp = eliminado.
- **IDs como UUID**: generados en la capa de servicio, no delegados a la base de datos.
- **DTOs separados de entidades**: nunca exponer la entidad JPA directamente en la API.
- **GlobalExceptionHandler**: todas las excepciones de dominio se traducen a `ErrorResponse` JSON en un único lugar.
- **Validación antes de persistencia**: el Validator actúa antes de cualquier operación de escritura para garantizar atomicidad.
