package com.sdd.products.controller;

import com.sdd.products.domain.Category;
import com.sdd.products.dto.request.CreateProductRequest;
import com.sdd.products.dto.request.StockAdjustmentRequest;
import com.sdd.products.dto.request.UpdateProductRequest;
import com.sdd.products.dto.response.PaginatedResponse;
import com.sdd.products.dto.response.ProductResponse;
import com.sdd.products.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * REST controller for product catalog management.
 * Base path: /api/v1/products
 */
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Product catalog management endpoints")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Creates a new product.
     *
     * @param request the product creation request
     * @return the created product
     */
    @Operation(summary = "Create a product", description = "Creates a new product in the catalog")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Product created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "409", description = "SKU already exists")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        return productService.create(request);
    }

    /**
     * Retrieves an active product by its ID.
     *
     * @param id the product UUID
     * @return the product
     */
    @Operation(summary = "Get product by ID", description = "Returns an active product by its UUID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Product found"),
        @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable UUID id) {
        return productService.findById(id);
    }

    /**
     * Lists active products with optional filters and pagination.
     *
     * @param page     zero-indexed page number (default 0)
     * @param size     page size (default 10)
     * @param category optional category filter
     * @param minPrice optional minimum price filter (inclusive)
     * @param maxPrice optional maximum price filter (inclusive)
     * @param name     optional name substring filter (case-insensitive)
     * @param sku      optional exact SKU filter
     * @return paginated list of products
     */
    @Operation(summary = "List products", description = "Returns a paginated list of active products with optional filters")
    @ApiResponse(responseCode = "200", description = "Products retrieved successfully")
    @GetMapping
    public PaginatedResponse<ProductResponse> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String sku) {
        return productService.findAll(page, size, category, minPrice, maxPrice, name, sku);
    }

    /**
     * Updates mutable fields of an existing product.
     *
     * @param id      the product UUID
     * @param request the update request (all fields optional)
     * @return the updated product
     */
    @Operation(summary = "Update product", description = "Updates mutable fields of an existing product. SKU cannot be changed.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Product updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "404", description = "Product not found"),
        @ApiResponse(responseCode = "422", description = "Attempt to change immutable SKU")
    })
    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable UUID id,
                                  @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(id, request);
    }

    /**
     * Soft-deletes a product by setting its deletedAt timestamp.
     *
     * @param id the product UUID
     */
    @Operation(summary = "Delete product", description = "Soft-deletes a product. The record is preserved for audit purposes.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Product deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        productService.delete(id);
    }

    /**
     * Adjusts the stock of a product by applying a delta value.
     *
     * @param id      the product UUID
     * @param request the stock adjustment request with delta
     * @return the updated product
     */
    @Operation(summary = "Adjust stock", description = "Adjusts product stock by a delta. Positive = increase, negative = decrease.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Stock adjusted successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "404", description = "Product not found"),
        @ApiResponse(responseCode = "422", description = "Insufficient stock for the requested adjustment")
    })
    @PatchMapping("/{id}/stock")
    public ProductResponse adjustStock(@PathVariable UUID id,
                                       @Valid @RequestBody StockAdjustmentRequest request) {
        return productService.adjustStock(id, request);
    }
}
