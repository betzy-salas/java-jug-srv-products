package com.sdd.products.service;

import com.sdd.products.domain.Category;
import com.sdd.products.dto.request.CreateProductRequest;
import com.sdd.products.dto.request.StockAdjustmentRequest;
import com.sdd.products.dto.request.UpdateProductRequest;
import com.sdd.products.dto.response.PaginatedResponse;
import com.sdd.products.dto.response.ProductResponse;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service interface defining the use case contract for product management.
 */
public interface ProductService {

    /**
     * Creates a new product with the given data.
     *
     * @param request the product creation request with all required fields
     * @return the created product as a response DTO
     * @throws com.sdd.products.domain.exception.SkuAlreadyExistsException if the SKU already exists among active products
     */
    ProductResponse create(CreateProductRequest request);

    /**
     * Finds an active product by its unique identifier.
     *
     * @param id the UUID of the product to find
     * @return the product as a response DTO
     * @throws com.sdd.products.domain.exception.ProductNotFoundException if no active product exists with the given id
     */
    ProductResponse findById(UUID id);

    /**
     * Returns a paginated list of active products, optionally filtered by category, price range, name, or SKU.
     *
     * @param page     zero-indexed page number
     * @param size     number of items per page
     * @param category optional filter by exact category
     * @param minPrice optional minimum price (inclusive)
     * @param maxPrice optional maximum price (inclusive)
     * @param name     optional substring filter on product name (case-insensitive)
     * @param sku      optional exact SKU filter (case-sensitive); when present, other filters are ignored
     * @return a paginated response containing matching products
     */
    PaginatedResponse<ProductResponse> findAll(int page, int size, Category category,
                                               BigDecimal minPrice, BigDecimal maxPrice,
                                               String name, String sku);

    /**
     * Updates the mutable fields of an existing active product.
     * Only non-null fields in the request are applied; null fields are left unchanged.
     *
     * @param id      the UUID of the product to update
     * @param request the update request with optional fields
     * @return the updated product as a response DTO
     * @throws com.sdd.products.domain.exception.ProductNotFoundException if no active product exists with the given id
     */
    ProductResponse update(UUID id, UpdateProductRequest request);

    /**
     * Soft-deletes an active product by setting its {@code deletedAt} timestamp.
     * The product record is preserved in the database for audit purposes.
     *
     * @param id the UUID of the product to delete
     * @throws com.sdd.products.domain.exception.ProductNotFoundException if no active product exists with the given id
     */
    void delete(UUID id);

    /**
     * Adjusts the stock of an active product by applying the given delta.
     * A positive delta increases stock; a negative delta decreases it.
     *
     * @param id      the UUID of the product whose stock will be adjusted
     * @param request the stock adjustment request containing the delta value
     * @return the updated product as a response DTO
     * @throws com.sdd.products.domain.exception.ProductNotFoundException   if no active product exists with the given id
     * @throws com.sdd.products.domain.exception.InsufficientStockException if the resulting stock would be negative
     */
    ProductResponse adjustStock(UUID id, StockAdjustmentRequest request);
}
