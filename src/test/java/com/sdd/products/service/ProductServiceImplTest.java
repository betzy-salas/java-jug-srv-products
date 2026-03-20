package com.sdd.products.service;

import com.sdd.products.domain.Category;
import com.sdd.products.domain.entity.Product;
import com.sdd.products.domain.exception.InsufficientStockException;
import com.sdd.products.domain.exception.ProductNotFoundException;
import com.sdd.products.domain.exception.SkuAlreadyExistsException;
import com.sdd.products.dto.request.CreateProductRequest;
import com.sdd.products.dto.request.StockAdjustmentRequest;
import com.sdd.products.dto.request.UpdateProductRequest;
import com.sdd.products.dto.response.PaginatedResponse;
import com.sdd.products.dto.response.ProductResponse;
import com.sdd.products.mapper.ProductMapper;
import com.sdd.products.repository.ProductRepository;
import com.sdd.products.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository repository;

    @Mock
    private ProductMapper mapper;

    @InjectMocks
    private ProductServiceImpl service;

    private Product sampleProduct;
    private ProductResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleProduct = buildProduct(UUID.randomUUID(), "SKU-001", 10);
        sampleResponse = buildResponse(sampleProduct);
    }

    // --- create ---

    @Test
    void should_createProduct_when_skuIsUnique() {
        CreateProductRequest request = buildCreateRequest("SKU-001");
        when(repository.existsBySku("SKU-001")).thenReturn(false);
        when(mapper.toEntity(request)).thenReturn(sampleProduct);
        when(repository.save(any())).thenReturn(sampleProduct);
        when(mapper.toResponse(sampleProduct)).thenReturn(sampleResponse);

        ProductResponse result = service.create(request);

        assertThat(result).isNotNull();
        verify(repository).existsBySku("SKU-001");
        verify(repository).save(sampleProduct);
    }

    @Test
    void should_assignUuid_when_productIsCreated() {
        CreateProductRequest request = buildCreateRequest("SKU-NEW");
        Product productWithoutId = buildProduct(null, "SKU-NEW", 0);
        when(repository.existsBySku("SKU-NEW")).thenReturn(false);
        when(mapper.toEntity(request)).thenReturn(productWithoutId);
        when(repository.save(any())).thenReturn(productWithoutId);
        when(mapper.toResponse(any())).thenReturn(sampleResponse);

        service.create(request);

        assertThat(productWithoutId.getId()).isNotNull();
    }

    @Test
    void should_throwSkuAlreadyExistsException_when_skuIsDuplicated() {
        CreateProductRequest request = buildCreateRequest("SKU-001");
        when(repository.existsBySku("SKU-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(SkuAlreadyExistsException.class);

        verify(repository, never()).save(any());
    }

    // --- findById ---

    @Test
    void should_returnProduct_when_activeProductExists() {
        UUID id = sampleProduct.getId();
        when(repository.findById(id)).thenReturn(Optional.of(sampleProduct));
        when(mapper.toResponse(sampleProduct)).thenReturn(sampleResponse);

        ProductResponse result = service.findById(id);

        assertThat(result).isEqualTo(sampleResponse);
    }

    @Test
    void should_throwProductNotFoundException_when_productDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(ProductNotFoundException.class);
    }

    // --- update ---

    @Test
    void should_updateNonNullFields_when_updateRequestIsPartial() {
        UUID id = sampleProduct.getId();
        UpdateProductRequest request = new UpdateProductRequest();
        request.setName("Updated Name");
        request.setPrice(new BigDecimal("199.99"));

        when(repository.findById(id)).thenReturn(Optional.of(sampleProduct));
        when(repository.save(any())).thenReturn(sampleProduct);
        when(mapper.toResponse(sampleProduct)).thenReturn(sampleResponse);

        service.update(id, request);

        assertThat(sampleProduct.getName()).isEqualTo("Updated Name");
        assertThat(sampleProduct.getPrice()).isEqualByComparingTo("199.99");
    }

    @Test
    void should_throwProductNotFoundException_when_updatingNonExistentProduct() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, new UpdateProductRequest()))
                .isInstanceOf(ProductNotFoundException.class);
    }

    // --- delete ---

    @Test
    void should_setDeletedAt_when_productIsDeleted() {
        UUID id = sampleProduct.getId();
        when(repository.findById(id)).thenReturn(Optional.of(sampleProduct));
        when(repository.save(any())).thenReturn(sampleProduct);

        service.delete(id);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    void should_throwProductNotFoundException_when_deletingNonExistentProduct() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ProductNotFoundException.class);

        verify(repository, never()).save(any());
    }

    // --- adjustStock ---

    @Test
    void should_increaseStock_when_deltaIsPositive() {
        UUID id = sampleProduct.getId();
        StockAdjustmentRequest request = new StockAdjustmentRequest();
        request.setDelta(5);

        when(repository.findById(id)).thenReturn(Optional.of(sampleProduct));
        when(repository.save(any())).thenReturn(sampleProduct);
        when(mapper.toResponse(sampleProduct)).thenReturn(sampleResponse);

        service.adjustStock(id, request);

        assertThat(sampleProduct.getStock()).isEqualTo(15);
    }

    @Test
    void should_decreaseStock_when_deltaIsNegativeAndStockSufficient() {
        UUID id = sampleProduct.getId();
        StockAdjustmentRequest request = new StockAdjustmentRequest();
        request.setDelta(-3);

        when(repository.findById(id)).thenReturn(Optional.of(sampleProduct));
        when(repository.save(any())).thenReturn(sampleProduct);
        when(mapper.toResponse(sampleProduct)).thenReturn(sampleResponse);

        service.adjustStock(id, request);

        assertThat(sampleProduct.getStock()).isEqualTo(7);
    }

    @Test
    void should_throwInsufficientStockException_when_deltaWouldResultInNegativeStock() {
        UUID id = sampleProduct.getId();
        StockAdjustmentRequest request = new StockAdjustmentRequest();
        request.setDelta(-999);

        when(repository.findById(id)).thenReturn(Optional.of(sampleProduct));

        assertThatThrownBy(() -> service.adjustStock(id, request))
                .isInstanceOf(InsufficientStockException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void should_throwProductNotFoundException_when_adjustingStockOfNonExistentProduct() {
        UUID id = UUID.randomUUID();
        StockAdjustmentRequest request = new StockAdjustmentRequest();
        request.setDelta(1);

        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.adjustStock(id, request))
                .isInstanceOf(ProductNotFoundException.class);
    }

    // --- findAll ---

    @Test
    void should_callFindAll_when_noFiltersProvided() {
        PageImpl<Product> page = new PageImpl<>(List.of(sampleProduct));
        when(repository.findAll(any(Pageable.class))).thenReturn(page);
        when(mapper.toResponse(any(Product.class))).thenReturn(sampleResponse);

        PaginatedResponse<ProductResponse> result = service.findAll(0, 10, null, null, null, null, null);

        assertThat(result.getData()).hasSize(1);
        verify(repository).findAll(any(Pageable.class));
    }

    @Test
    void should_callFindByCategory_when_onlyCategoryProvided() {
        PageImpl<Product> page = new PageImpl<>(List.of(sampleProduct));
        when(repository.findByCategory(eq(Category.ELECTRONICS), any(Pageable.class))).thenReturn(page);
        when(mapper.toResponse(any(Product.class))).thenReturn(sampleResponse);

        PaginatedResponse<ProductResponse> result = service.findAll(0, 10, Category.ELECTRONICS, null, null, null, null);

        assertThat(result.getData()).hasSize(1);
        verify(repository).findByCategory(eq(Category.ELECTRONICS), any(Pageable.class));
    }

    @Test
    void should_callFindByNameContaining_when_onlyNameProvided() {
        PageImpl<Product> page = new PageImpl<>(List.of(sampleProduct));
        when(repository.findByNameContainingIgnoreCase(eq("sample"), any(Pageable.class))).thenReturn(page);
        when(mapper.toResponse(any(Product.class))).thenReturn(sampleResponse);

        PaginatedResponse<ProductResponse> result = service.findAll(0, 10, null, null, null, "sample", null);

        assertThat(result.getData()).hasSize(1);
        verify(repository).findByNameContainingIgnoreCase(eq("sample"), any(Pageable.class));
    }

    @Test
    void should_callFindByPriceBetween_when_onlyPriceRangeProvided() {
        BigDecimal min = new BigDecimal("10.00");
        BigDecimal max = new BigDecimal("100.00");
        PageImpl<Product> page = new PageImpl<>(List.of(sampleProduct));
        when(repository.findByPriceBetween(eq(min), eq(max), any(Pageable.class))).thenReturn(page);
        when(mapper.toResponse(any(Product.class))).thenReturn(sampleResponse);

        PaginatedResponse<ProductResponse> result = service.findAll(0, 10, null, min, max, null, null);

        assertThat(result.getData()).hasSize(1);
        verify(repository).findByPriceBetween(eq(min), eq(max), any(Pageable.class));
    }

    @Test
    void should_callFindByCategoryAndName_when_categoryAndNameProvided() {
        PageImpl<Product> page = new PageImpl<>(List.of(sampleProduct));
        when(repository.findByCategoryAndNameContainingIgnoreCase(eq(Category.ELECTRONICS), eq("sample"), any(Pageable.class))).thenReturn(page);
        when(mapper.toResponse(any(Product.class))).thenReturn(sampleResponse);

        PaginatedResponse<ProductResponse> result = service.findAll(0, 10, Category.ELECTRONICS, null, null, "sample", null);

        assertThat(result.getData()).hasSize(1);
        verify(repository).findByCategoryAndNameContainingIgnoreCase(eq(Category.ELECTRONICS), eq("sample"), any(Pageable.class));
    }

    @Test
    void should_callFindByCategoryAndPrice_when_categoryAndPriceProvided() {
        BigDecimal min = new BigDecimal("10.00");
        BigDecimal max = new BigDecimal("100.00");
        PageImpl<Product> page = new PageImpl<>(List.of(sampleProduct));
        when(repository.findByCategoryAndPriceBetween(eq(Category.ELECTRONICS), eq(min), eq(max), any(Pageable.class))).thenReturn(page);
        when(mapper.toResponse(any(Product.class))).thenReturn(sampleResponse);

        PaginatedResponse<ProductResponse> result = service.findAll(0, 10, Category.ELECTRONICS, min, max, null, null);

        assertThat(result.getData()).hasSize(1);
        verify(repository).findByCategoryAndPriceBetween(eq(Category.ELECTRONICS), eq(min), eq(max), any(Pageable.class));
    }

    @Test
    void should_callFindByNameAndPrice_when_nameAndPriceProvided() {
        BigDecimal min = new BigDecimal("10.00");
        BigDecimal max = new BigDecimal("100.00");
        PageImpl<Product> page = new PageImpl<>(List.of(sampleProduct));
        when(repository.findByNameContainingIgnoreCaseAndPriceBetween(eq("sample"), eq(min), eq(max), any(Pageable.class))).thenReturn(page);
        when(mapper.toResponse(any(Product.class))).thenReturn(sampleResponse);

        PaginatedResponse<ProductResponse> result = service.findAll(0, 10, null, min, max, "sample", null);

        assertThat(result.getData()).hasSize(1);
        verify(repository).findByNameContainingIgnoreCaseAndPriceBetween(eq("sample"), eq(min), eq(max), any(Pageable.class));
    }

    @Test
    void should_callFindByCategoryAndNameAndPrice_when_allFiltersProvided() {
        BigDecimal min = new BigDecimal("10.00");
        BigDecimal max = new BigDecimal("100.00");
        PageImpl<Product> page = new PageImpl<>(List.of(sampleProduct));
        when(repository.findByCategoryAndNameContainingIgnoreCaseAndPriceBetween(
                eq(Category.ELECTRONICS), eq("sample"), eq(min), eq(max), any(Pageable.class))).thenReturn(page);
        when(mapper.toResponse(any(Product.class))).thenReturn(sampleResponse);

        PaginatedResponse<ProductResponse> result = service.findAll(0, 10, Category.ELECTRONICS, min, max, "sample", null);

        assertThat(result.getData()).hasSize(1);
        verify(repository).findByCategoryAndNameContainingIgnoreCaseAndPriceBetween(
                eq(Category.ELECTRONICS), eq("sample"), eq(min), eq(max), any(Pageable.class));
    }

    @Test
    void should_returnSingleResult_when_skuFilterProvided() {
        when(repository.findBySku("SKU-001")).thenReturn(Optional.of(sampleProduct));
        when(mapper.toResponse(any(Product.class))).thenReturn(sampleResponse);

        PaginatedResponse<ProductResponse> result = service.findAll(0, 10, null, null, null, null, "SKU-001");

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getTotal()).isEqualTo(1L);
    }

    @Test
    void should_returnEmptyResult_when_skuFilterMatchesNothing() {
        when(repository.findBySku("SKU-NONE")).thenReturn(Optional.empty());

        PaginatedResponse<ProductResponse> result = service.findAll(0, 10, null, null, null, null, "SKU-NONE");

        assertThat(result.getData()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(0L);
    }

    // Feature: product, Property 1: Creación exitosa — estado inicial correcto — REQ-001.1, REQ-001.2, REQ-001.3, REQ-001.4
    @Test
    void should_haveCorrectInitialState_when_productIsCreatedWithVariousInputs() {
        for (int i = 0; i < 20; i++) {
            String sku = "SKU-" + i;
            CreateProductRequest request = new CreateProductRequest();
            request.setSku(sku);
            request.setName("Product " + i);
            request.setPrice(new BigDecimal("10.00"));
            request.setStock(i % 10);
            request.setCategory(Category.ELECTRONICS);

            Product product = new Product();
            product.setId(null);
            product.setSku(sku);
            product.setName("Product " + i);
            product.setPrice(new BigDecimal("10.00"));
            product.setStock(i % 10);
            product.setCategory(Category.ELECTRONICS);
            product.setCreatedAt(OffsetDateTime.now());
            product.setUpdatedAt(OffsetDateTime.now());
            product.setDeletedAt(null);

            when(repository.existsBySku(sku)).thenReturn(false);
            when(mapper.toEntity(request)).thenReturn(product);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toResponse(any(Product.class))).thenAnswer(inv -> buildResponse(inv.getArgument(0)));

            service.create(request);

            assertThat(product.getId()).isNotNull();
            assertThat(product.getDeletedAt()).isNull();
            assertThat(product.getSku()).isEqualTo(sku);

            Mockito.reset(repository, mapper);
        }
    }

    // Feature: product, Property 14: Ajuste de stock válido — newStock = currentStock + delta — REQ-006.1, REQ-006.2
    @Test
    void should_applyDeltaCorrectly_when_stockAdjustmentIsValidWithVariousValues() {
        for (int i = 0; i < 20; i++) {
            int currentStock = i * 5;
            int delta = i + 1;

            UUID id = UUID.randomUUID();
            Product product = buildProduct(id, "SKU-" + i, currentStock);

            StockAdjustmentRequest request = new StockAdjustmentRequest();
            request.setDelta(delta);

            when(repository.findById(id)).thenReturn(Optional.of(product));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toResponse(any(Product.class))).thenAnswer(inv -> buildResponse(inv.getArgument(0)));

            service.adjustStock(id, request);

            assertThat(product.getStock()).isEqualTo(currentStock + delta);

            Mockito.reset(repository, mapper);
        }
    }

    // Feature: product, Property 15: Stock no negativo — delta inválido rechazado sin efecto — REQ-006.3
    @Test
    void should_leaveStockUnchanged_when_deltaWouldResultInNegativeStockWithVariousValues() {
        for (int i = 0; i < 20; i++) {
            int currentStock = i * 3;
            int negativeDelta = -(currentStock + i + 1);

            UUID id = UUID.randomUUID();
            Product product = buildProduct(id, "SKU-" + i, currentStock);

            StockAdjustmentRequest request = new StockAdjustmentRequest();
            request.setDelta(negativeDelta);

            when(repository.findById(id)).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> service.adjustStock(id, request))
                    .isInstanceOf(InsufficientStockException.class);

            assertThat(product.getStock()).isEqualTo(currentStock);
            verify(repository, never()).save(any());

            Mockito.reset(repository, mapper);
        }
    }

    // --- helpers ---

    private Product buildProduct(UUID id, String sku, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setSku(sku);
        p.setName("Sample Product");
        p.setPrice(new BigDecimal("49.99"));
        p.setStock(stock);
        p.setCategory(Category.ELECTRONICS);
        p.setCreatedAt(OffsetDateTime.now());
        p.setUpdatedAt(OffsetDateTime.now());
        return p;
    }

    private ProductResponse buildResponse(Product p) {
        ProductResponse r = new ProductResponse();
        r.setId(p.getId());
        r.setSku(p.getSku());
        r.setName(p.getName());
        r.setDescription(p.getDescription());
        r.setPrice(p.getPrice());
        r.setStock(p.getStock());
        r.setCategory(p.getCategory());
        r.setCreatedAt(p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
        r.setUpdatedAt(p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : null);
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
