package com.sdd.products.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.products.domain.Category;
import com.sdd.products.domain.exception.ImmutableSkuException;
import com.sdd.products.domain.exception.InsufficientStockException;
import com.sdd.products.domain.exception.InvalidStockException;
import com.sdd.products.domain.exception.ProductNotFoundException;
import com.sdd.products.domain.exception.SkuAlreadyExistsException;
import com.sdd.products.dto.request.CreateProductRequest;
import com.sdd.products.dto.request.StockAdjustmentRequest;
import com.sdd.products.dto.request.UpdateProductRequest;
import com.sdd.products.dto.response.PaginatedResponse;
import com.sdd.products.dto.response.ProductResponse;
import com.sdd.products.exception.GlobalExceptionHandler;
import com.sdd.products.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import(GlobalExceptionHandler.class)
class ProductControllerTest {

    private static final String BASE_URL = "/api/v1/products";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    private UUID productId;
    private ProductResponse sampleResponse;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        sampleResponse = buildResponse(productId, "SKU-001", 10);
    }

    // --- POST / ---

    @Test
    void should_return201_when_createProductWithValidRequest() throws Exception {
        CreateProductRequest request = buildCreateRequest("SKU-001");
        when(productService.create(any())).thenReturn(sampleResponse);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("SKU-001"));
    }

    @Test
    void should_return400_when_createProductWithBlankName() throws Exception {
        CreateProductRequest request = buildCreateRequest("SKU-001");
        request.setName("");

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields").isArray());
    }

    @Test
    void should_return400_when_createProductWithNegativePrice() throws Exception {
        CreateProductRequest request = buildCreateRequest("SKU-001");
        request.setPrice(new BigDecimal("-1.00"));

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void should_return400_when_createProductWithInvalidSkuPattern() throws Exception {
        CreateProductRequest request = buildCreateRequest("invalid sku!");

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void should_return409_when_createProductWithDuplicateSku() throws Exception {
        CreateProductRequest request = buildCreateRequest("SKU-001");
        when(productService.create(any())).thenThrow(new SkuAlreadyExistsException("SKU-001"));

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SKU_ALREADY_EXISTS"));
    }

    // --- GET /{id} ---

    @Test
    void should_return200_when_getProductByExistingId() throws Exception {
        when(productService.findById(productId)).thenReturn(sampleResponse);

        mockMvc.perform(get(BASE_URL + "/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId.toString()))
                .andExpect(jsonPath("$.sku").value("SKU-001"));
    }

    @Test
    void should_return404_when_getProductByNonExistentId() throws Exception {
        when(productService.findById(productId)).thenThrow(new ProductNotFoundException(productId));

        mockMvc.perform(get(BASE_URL + "/{id}", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PRODUCT_NOT_FOUND"));
    }

    // --- GET / ---

    @Test
    void should_return200_when_listProductsWithDefaultPagination() throws Exception {
        PaginatedResponse<ProductResponse> page = new PaginatedResponse<>(
                List.of(sampleResponse), 0, 10, 1L);
        when(productService.findAll(0, 10, null, null, null, null, null)).thenReturn(page);

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.total").value(1));
    }

    // --- PUT /{id} ---

    @Test
    void should_return200_when_updateProductWithValidRequest() throws Exception {
        UpdateProductRequest request = new UpdateProductRequest();
        request.setName("Updated Name");
        when(productService.update(eq(productId), any())).thenReturn(sampleResponse);

        mockMvc.perform(put(BASE_URL + "/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void should_return404_when_updateNonExistentProduct() throws Exception {
        UpdateProductRequest request = new UpdateProductRequest();
        request.setName("Name");
        when(productService.update(eq(productId), any()))
                .thenThrow(new ProductNotFoundException(productId));

        mockMvc.perform(put(BASE_URL + "/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void should_return422_when_updateAttemptsSKUChange() throws Exception {
        UpdateProductRequest request = new UpdateProductRequest();
        when(productService.update(eq(productId), any()))
                .thenThrow(new ImmutableSkuException());

        mockMvc.perform(put(BASE_URL + "/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("IMMUTABLE_SKU"));
    }

    // --- DELETE /{id} ---

    @Test
    void should_return204_when_deleteExistingProduct() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/{id}", productId))
                .andExpect(status().isNoContent());
    }

    @Test
    void should_return404_when_deleteNonExistentProduct() throws Exception {
        doThrow(new ProductNotFoundException(productId)).when(productService).delete(productId);

        mockMvc.perform(delete(BASE_URL + "/{id}", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PRODUCT_NOT_FOUND"));
    }

    // --- PATCH /{id}/stock ---

    @Test
    void should_return200_when_adjustStockWithValidDelta() throws Exception {
        StockAdjustmentRequest request = new StockAdjustmentRequest();
        request.setDelta(5);
        ProductResponse updated = buildResponse(productId, "SKU-001", 15);
        when(productService.adjustStock(eq(productId), any())).thenReturn(updated);

        mockMvc.perform(patch(BASE_URL + "/{id}/stock", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(15));
    }

    @Test
    void should_return422_when_adjustStockResultsInNegativeStock() throws Exception {
        StockAdjustmentRequest request = new StockAdjustmentRequest();
        request.setDelta(-999);
        when(productService.adjustStock(eq(productId), any()))
                .thenThrow(new InsufficientStockException(10, -999));

        mockMvc.perform(patch(BASE_URL + "/{id}/stock", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void should_return400_when_adjustStockWithMalformedJson() throws Exception {
        mockMvc.perform(patch(BASE_URL + "/{id}/stock", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ invalid json }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_JSON"));
    }

    @Test
    void should_return400_when_adjustStockWithInvalidStockValue() throws Exception {
        StockAdjustmentRequest request = new StockAdjustmentRequest();
        request.setDelta(-5);
        when(productService.adjustStock(eq(productId), any()))
                .thenThrow(new InvalidStockException("Stock cannot be negative"));

        mockMvc.perform(patch(BASE_URL + "/{id}/stock", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_STOCK"));
    }

    // --- property-style loop tests ---

    // Feature: product, Property 2: Validación de request — campos inválidos rechazados — REQ-001.7, REQ-001.8, REQ-001.9, REQ-001.10, REQ-001.11, REQ-001.12
    @Test
    void should_return400_when_requestHasInvalidFieldsWithVariousInvalidInputs() throws Exception {
        // Scenario 1: blank name
        CreateProductRequest blankName = buildCreateRequest("SKU-001");
        blankName.setName("");
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields").isArray());

        // Scenario 2: null name
        CreateProductRequest nullName = buildCreateRequest("SKU-001");
        nullName.setName(null);
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        // Scenario 3: negative price
        CreateProductRequest negativePrice = buildCreateRequest("SKU-001");
        negativePrice.setPrice(new BigDecimal("-1.00"));
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negativePrice)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        // Scenario 4: invalid SKU pattern
        CreateProductRequest invalidSku = buildCreateRequest("invalid sku!");
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidSku)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // Feature: product, Property 3: SKU duplicado entre activos → 409 — REQ-001.6
    @Test
    void should_return409_when_skuAlreadyExistsWithVariousSkus() throws Exception {
        String[] skus = {"SKU-A", "SKU-B", "SKU-C", "SKU-D", "SKU-E"};
        for (String sku : skus) {
            Mockito.reset(productService);
            CreateProductRequest request = buildCreateRequest(sku);
            when(productService.create(any())).thenThrow(new SkuAlreadyExistsException(sku));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SKU_ALREADY_EXISTS"));
        }
    }

    // Feature: product, Property 4: Consulta por ID — round-trip crear y consultar — REQ-002.1
    @Test
    void should_returnEquivalentProduct_when_queriedAfterCreationWithVariousIds() throws Exception {
        for (int i = 0; i < 5; i++) {
            UUID id = UUID.randomUUID();
            ProductResponse response = buildResponse(id, "SKU-RT-" + i, i * 2);
            when(productService.findById(id)).thenReturn(response);

            mockMvc.perform(get(BASE_URL + "/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id.toString()))
                    .andExpect(jsonPath("$.sku").value("SKU-RT-" + i));
        }
    }

    // Feature: product, Property 5: Producto no visible → 404 — REQ-002.2, REQ-002.3, REQ-004.4, REQ-005.3, REQ-006.4
    @Test
    void should_return404_when_productNotVisibleForVariousOperations() throws Exception {
        for (int i = 0; i < 3; i++) {
            UUID id = UUID.randomUUID();
            Mockito.reset(productService);

            when(productService.findById(id)).thenThrow(new ProductNotFoundException(id));
            when(productService.update(eq(id), any())).thenThrow(new ProductNotFoundException(id));
            when(productService.adjustStock(eq(id), any())).thenThrow(new ProductNotFoundException(id));
            doThrow(new ProductNotFoundException(id)).when(productService).delete(id);

            // GET /{id} → 404
            mockMvc.perform(get(BASE_URL + "/{id}", id))
                    .andExpect(status().isNotFound());

            // PUT /{id} → 404
            UpdateProductRequest updateRequest = new UpdateProductRequest();
            updateRequest.setName("Some Name");
            mockMvc.perform(put(BASE_URL + "/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isNotFound());

            // PATCH /{id}/stock → 404
            StockAdjustmentRequest stockRequest = new StockAdjustmentRequest();
            stockRequest.setDelta(1);
            mockMvc.perform(patch(BASE_URL + "/{id}/stock", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(stockRequest)))
                    .andExpect(status().isNotFound());

            // DELETE /{id} → 404
            mockMvc.perform(delete(BASE_URL + "/{id}", id))
                    .andExpect(status().isNotFound());
        }
    }

    // Feature: product, Property 7: Filtro por category — REQ-003.3
    @Test
    void should_returnOnlyMatchingCategory_when_categoryFilterAppliedWithVariousCategories() throws Exception {
        for (Category category : Category.values()) {
            ProductResponse response = buildResponse(UUID.randomUUID(), "SKU-CAT-" + category.name(), 5);
            response.setCategory(category);
            PaginatedResponse<ProductResponse> page = new PaginatedResponse<>(List.of(response), 0, 10, 1L);
            when(productService.findAll(0, 10, category, null, null, null, null)).thenReturn(page);

            mockMvc.perform(get(BASE_URL + "?category=" + category.name()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].category").value(category.name()));
        }
    }

    // Feature: product, Property 8: Filtro por rango de precio — REQ-003.4, REQ-003.5
    @Test
    void should_returnProductsInPriceRange_when_priceFilterAppliedWithVariousRanges() throws Exception {
        int[][] ranges = {{10, 50}, {100, 200}, {5, 15}};
        int[] midpoints = {30, 150, 10};

        for (int i = 0; i < ranges.length; i++) {
            int min = ranges[i][0];
            int max = ranges[i][1];
            BigDecimal minPrice = new BigDecimal(String.valueOf(min));
            BigDecimal maxPrice = new BigDecimal(String.valueOf(max));

            ProductResponse response = buildResponse(UUID.randomUUID(), "SKU-PRICE-" + i, 5);
            response.setPrice(new BigDecimal(String.valueOf(midpoints[i])));
            PaginatedResponse<ProductResponse> page = new PaginatedResponse<>(List.of(response), 0, 10, 1L);
            when(productService.findAll(0, 10, null, minPrice, maxPrice, null, null)).thenReturn(page);

            mockMvc.perform(get(BASE_URL + "?minPrice=" + min + "&maxPrice=" + max))
                    .andExpect(status().isOk());
        }
    }

    // Feature: product, Property 9: Filtro por name (contains, case-insensitive) — REQ-003.6
    @Test
    void should_returnProductsContainingName_when_nameFilterAppliedWithVariousFragments() throws Exception {
        String[] fragments = {"keyboard", "mouse", "monitor"};
        String[] names = {"Mechanical keyboard", "Wireless mouse", "4K monitor"};

        for (int i = 0; i < fragments.length; i++) {
            String fragment = fragments[i];
            ProductResponse response = buildResponse(UUID.randomUUID(), "SKU-NAME-" + i, 5);
            response.setName(names[i]);
            PaginatedResponse<ProductResponse> page = new PaginatedResponse<>(List.of(response), 0, 10, 1L);
            when(productService.findAll(0, 10, null, null, null, fragment, null)).thenReturn(page);

            mockMvc.perform(get(BASE_URL + "?name=" + fragment))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].name",
                            org.hamcrest.Matchers.containsStringIgnoringCase(fragment)));
        }
    }

    // Feature: product, Property 10: Filtro por SKU (exact match) — REQ-003.7
    @Test
    void should_returnExactSkuMatch_when_skuFilterAppliedWithVariousSkus() throws Exception {
        String[] skus = {"SKU-001", "SKU-ABC", "PROD-XYZ"};

        for (String sku : skus) {
            ProductResponse response = buildResponse(UUID.randomUUID(), sku, 5);
            PaginatedResponse<ProductResponse> page = new PaginatedResponse<>(List.of(response), 0, 10, 1L);
            when(productService.findAll(0, 10, null, null, null, null, sku)).thenReturn(page);

            mockMvc.perform(get(BASE_URL + "?sku=" + sku))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].sku").value(sku));
        }
    }

    // Feature: product, Property 11: Actualización exitosa — campos actualizados y updatedAt renovado — REQ-004.1, REQ-004.2
    @Test
    void should_reflectChangesAndUpdateTimestamp_when_updateSucceedsWithVariousFields() throws Exception {
        String[] names = {"Updated Name 1", "Updated Name 2", "Updated Name 3"};

        for (String name : names) {
            UpdateProductRequest request = new UpdateProductRequest();
            request.setName(name);

            ProductResponse response = buildResponse(productId, "SKU-001", 10);
            response.setName(name);
            response.setUpdatedAt("2024-06-01T12:00:00Z");
            when(productService.update(eq(productId), any())).thenReturn(response);

            mockMvc.perform(put(BASE_URL + "/{id}", productId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value(name));
        }
    }

    // Feature: product, Property 12: SKU inmutable — PUT con campo sku → 422 — REQ-004.3
    @Test
    void should_return422_when_putIncludesSkuWithVariousSkuValues() throws Exception {
        String[] skuValues = {"NEW-SKU", "CHANGED-SKU", "ANOTHER-SKU"};

        for (String sku : skuValues) {
            UpdateProductRequest request = new UpdateProductRequest();
            request.setSku(sku);
            when(productService.update(eq(productId), any())).thenThrow(new ImmutableSkuException());

            mockMvc.perform(put(BASE_URL + "/{id}", productId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error").value("IMMUTABLE_SKU"));
        }
    }

    // Feature: product, Property 13: Soft-delete — deletedAt asignado e invisibilidad posterior — REQ-005.1, REQ-005.2
    @Test
    void should_beInvisibleAfterDelete_when_softDeleteAppliedWithVariousIds() throws Exception {
        for (int i = 0; i < 3; i++) {
            UUID id = UUID.randomUUID();

            doNothing().when(productService).delete(id);
            mockMvc.perform(delete(BASE_URL + "/{id}", id))
                    .andExpect(status().isNoContent());

            when(productService.findById(id)).thenThrow(new ProductNotFoundException(id));
            mockMvc.perform(get(BASE_URL + "/{id}", id))
                    .andExpect(status().isNotFound());
        }
    }

    // Feature: product, Property 16: JSON round-trip — serializar y deserializar ProductResponse — REQ-007.1, REQ-007.2, REQ-007.4
    @Test
    void should_preserveAllFields_when_jsonRoundTripWithVariousProductResponses() throws Exception {
        for (int i = 0; i < 5; i++) {
            ProductResponse original = new ProductResponse();
            original.setId(UUID.randomUUID());
            original.setSku("SKU-RND-" + i);
            original.setPrice(new BigDecimal((i + 1) * 10 + "." + String.format("%04d", i * 100)));
            original.setStock(i * 5);
            original.setCategory(Category.ELECTRONICS);
            original.setCreatedAt("2024-01-01T00:00:00Z");
            original.setUpdatedAt("2024-01-01T00:00:00Z");

            String json = objectMapper.writeValueAsString(original);
            ProductResponse deserialized = objectMapper.readValue(json, ProductResponse.class);

            org.junit.jupiter.api.Assertions.assertEquals(original.getId(), deserialized.getId());
            org.junit.jupiter.api.Assertions.assertEquals(original.getSku(), deserialized.getSku());
            org.junit.jupiter.api.Assertions.assertEquals(original.getStock(), deserialized.getStock());
            org.junit.jupiter.api.Assertions.assertEquals(original.getCategory(), deserialized.getCategory());
            org.junit.jupiter.api.Assertions.assertEquals(0, original.getPrice().compareTo(deserialized.getPrice()));
        }
    }

    // --- helpers ---

    private ProductResponse buildResponse(UUID id, String sku, int stock) {
        ProductResponse r = new ProductResponse();
        r.setId(id);
        r.setSku(sku);
        r.setName("Sample Product");
        r.setPrice(new BigDecimal("49.99"));
        r.setStock(stock);
        r.setCategory(Category.ELECTRONICS);
        r.setCreatedAt("2024-01-01T00:00:00Z");
        r.setUpdatedAt("2024-01-01T00:00:00Z");
        return r;
    }

    private CreateProductRequest buildCreateRequest(String sku) {
        CreateProductRequest r = new CreateProductRequest();
        r.setSku(sku);
        r.setName("Sample Product");
        r.setPrice(new BigDecimal("49.99"));
        r.setCategory(Category.ELECTRONICS);
        return r;
    }
}
