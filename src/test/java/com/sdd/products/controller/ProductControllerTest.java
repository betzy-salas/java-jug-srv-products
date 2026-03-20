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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
