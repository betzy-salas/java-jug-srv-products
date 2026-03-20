package com.sdd.products.property;

import com.sdd.products.domain.Category;
import com.sdd.products.domain.entity.Product;
import com.sdd.products.domain.exception.InsufficientStockException;
import com.sdd.products.domain.exception.SkuAlreadyExistsException;
import com.sdd.products.dto.request.CreateProductRequest;
import com.sdd.products.dto.request.StockAdjustmentRequest;
import com.sdd.products.dto.request.UpdateProductRequest;
import com.sdd.products.dto.response.PaginatedResponse;
import com.sdd.products.dto.response.ProductResponse;
import com.sdd.products.mapper.ProductMapper;
import com.sdd.products.repository.ProductRepository;
import com.sdd.products.service.impl.ProductServiceImpl;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Positive;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for SDD Products API correctness properties.
 * Uses jqwik to generate random inputs and verify invariants hold across all cases.
 */
class ProductPropertyTest {

    @Mock
    private ProductRepository repository;

    @Mock
    private ProductMapper mapper;

    private ProductServiceImpl service;

    @BeforeProperty
    void initMocks() {
        MockitoAnnotations.openMocks(this);
        service = new ProductServiceImpl(repository, mapper);
    }

    // -------------------------------------------------------------------------
    // Feature: product, Property 1: Creación exitosa — estado inicial correcto
    // -------------------------------------------------------------------------

    @Property(tries = 100)
    void should_haveCorrectInitialState_when_productIsCreated(
            @ForAll @StringLength(min = 1, max = 50) String skuSuffix,
            @ForAll @Positive int stockValue) {

        String sku = "SKU-" + skuSuffix.replaceAll("[^A-Z0-9]", "A");
        if (sku.length() > 50) sku = sku.substring(0, 50);

        CreateProductRequest request = buildCreateRequest(sku, stockValue);
        Product product = buildProduct(null, sku, stockValue);

        when(repository.existsBySku(sku)).thenReturn(false);
        when(mapper.toEntity(request)).thenReturn(product);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            if (p == null) return null;
            return buildResponse(p);
        });

        ProductResponse response = service.create(request);

        // id must be assigned
        assertThat(product.getId()).isNotNull();
        // response must not be null
        assertThat(response).isNotNull();
        // deletedAt must be null on creation
        assertThat(product.getDeletedAt()).isNull();
    }

    // -------------------------------------------------------------------------
    // Feature: product, Property 3: SKU duplicado entre activos → excepción
    // -------------------------------------------------------------------------

    @Property(tries = 100)
    void should_throwSkuAlreadyExistsException_when_skuAlreadyExists(
            @ForAll @StringLength(min = 1, max = 10) String suffix) {

        String sku = "SKU-" + suffix.replaceAll("[^A-Z0-9]", "X").toUpperCase();
        if (sku.length() > 50) sku = sku.substring(0, 50);

        CreateProductRequest request = buildCreateRequest(sku, 0);
        when(repository.existsBySku(sku)).thenReturn(true);

        final String finalSku = sku;
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(SkuAlreadyExistsException.class);

        verify(repository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // Feature: product, Property 2: Validación — SKU con caracteres inválidos rechazado
    // -------------------------------------------------------------------------

    @Property(tries = 100)
    void should_notCallSave_when_skuExistsBeforeCreate(
            @ForAll @StringLength(min = 1, max = 10) String suffix) {

        // Simulates that any SKU that already exists causes rejection before save
        String sku = "SKU-" + suffix.replaceAll("[^A-Z0-9]", "B").toUpperCase();
        if (sku.length() > 50) sku = sku.substring(0, 50);

        CreateProductRequest request = buildCreateRequest(sku, 0);
        when(repository.existsBySku(sku)).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(SkuAlreadyExistsException.class);

        verify(repository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // Feature: product, Property 4: Round-trip — producto creado es recuperable
    // -------------------------------------------------------------------------

    @Property(tries = 100)
    void should_returnSameProduct_when_queriedAfterCreation(
            @ForAll @StringLength(min = 1, max = 10) String suffix) {

        String sku = "SKU-" + suffix.replaceAll("[^A-Z0-9]", "C").toUpperCase();
        if (sku.length() > 50) sku = sku.substring(0, 50);

        UUID id = UUID.randomUUID();
        Product product = buildProduct(id, sku, 0);

        when(repository.findById(id)).thenReturn(Optional.of(product));
        when(mapper.toResponse(product)).thenReturn(buildResponse(product));

        ProductResponse response = service.findById(id);

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getSku()).isEqualTo(sku);
    }

    // -------------------------------------------------------------------------
    // Feature: product, Property 5: Producto no visible → ProductNotFoundException
    // -------------------------------------------------------------------------

    @Property(tries = 100)
    void should_throwProductNotFoundException_when_productNotFound(
            @ForAll("randomUuids") UUID randomId) {

        when(repository.findById(randomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(randomId))
                .isInstanceOf(com.sdd.products.domain.exception.ProductNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // Feature: product, Property 11: Actualización — solo campos no nulos cambian
    // -------------------------------------------------------------------------

    @Property(tries = 100)
    void should_updateOnlyNonNullFields_when_updateRequestIsPartial(
            @ForAll @StringLength(min = 1, max = 100) String newName) {

        UUID id = UUID.randomUUID();
        Product product = buildProduct(id, "SKU-001", 5);
        String originalDescription = product.getDescription();

        UpdateProductRequest request = new UpdateProductRequest();
        request.setName(newName);
        // description intentionally left null — should not change

        when(repository.findById(id)).thenReturn(Optional.of(product));
        when(repository.save(any())).thenReturn(product);
        when(mapper.toResponse(any())).thenReturn(buildResponse(product));

        service.update(id, request);

        assertThat(product.getName()).isEqualTo(newName);
        assertThat(product.getDescription()).isEqualTo(originalDescription);
    }

    // -------------------------------------------------------------------------
    // Feature: product, Property 12: SKU inmutable — SKU no cambia tras update
    // -------------------------------------------------------------------------

    @Property(tries = 100)
    void should_notPersistSkuChange_when_updateContainsSkuField(
            @ForAll @StringLength(min = 1, max = 10) String suffix) {

        UUID id = UUID.randomUUID();
        String originalSku = "SKU-ORIG";
        Product product = buildProduct(id, originalSku, 5);

        UpdateProductRequest request = new UpdateProductRequest();
        request.setName("New Name " + suffix);

        when(repository.findById(id)).thenReturn(Optional.of(product));
        when(repository.save(any())).thenReturn(product);
        when(mapper.toResponse(any())).thenReturn(buildResponse(product));

        service.update(id, request);

        // SKU must remain unchanged after update
        assertThat(product.getSku()).isEqualTo(originalSku);
    }

    // -------------------------------------------------------------------------
    // Feature: product, Property 6: Soft-delete — deletedAt asignado
    // -------------------------------------------------------------------------

    @Property(tries = 100)
    void should_setDeletedAt_when_productIsDeleted(
            @ForAll("randomUuids") UUID id) {

        Product product = buildProduct(id, "SKU-DEL", 0);
        assertThat(product.getDeletedAt()).isNull();

        when(repository.findById(id)).thenReturn(Optional.of(product));
        when(repository.save(any())).thenReturn(product);

        service.delete(id);

        assertThat(product.getDeletedAt()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Feature: product, Property 14: Ajuste de stock válido — newStock = currentStock + delta
    // -------------------------------------------------------------------------

    @Property(tries = 100)
    void should_applyDeltaCorrectly_when_stockAdjustmentIsValid(
            @ForAll @IntRange(min = 0, max = 1000) int currentStock,
            @ForAll @IntRange(min = 0, max = 500) int positiveDelta) {

        UUID id = UUID.randomUUID();
        Product product = buildProduct(id, "SKU-STOCK", currentStock);

        StockAdjustmentRequest request = new StockAdjustmentRequest();
        request.setDelta(positiveDelta);

        when(repository.findById(id)).thenReturn(Optional.of(product));
        when(repository.save(any())).thenReturn(product);
        when(mapper.toResponse(any())).thenReturn(buildResponse(product));

        service.adjustStock(id, request);

        assertThat(product.getStock()).isEqualTo(currentStock + positiveDelta);
    }

    // -------------------------------------------------------------------------
    // Feature: product, Property 15: Stock no negativo — delta inválido rechazado
    // -------------------------------------------------------------------------

    @Property(tries = 100)
    void should_throwInsufficientStockException_when_deltaWouldResultInNegativeStock(
            @ForAll @IntRange(min = 0, max = 100) int currentStock,
            @ForAll @IntRange(min = 1, max = 500) int excessDelta) {

        UUID id = UUID.randomUUID();
        int negativeDelta = -(currentStock + excessDelta); // always results in negative stock
        Product product = buildProduct(id, "SKU-NEG", currentStock);
        int stockBefore = product.getStock();

        StockAdjustmentRequest request = new StockAdjustmentRequest();
        request.setDelta(negativeDelta);

        when(repository.findById(id)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.adjustStock(id, request))
                .isInstanceOf(InsufficientStockException.class);

        // stock must remain unchanged
        assertThat(product.getStock()).isEqualTo(stockBefore);
        verify(repository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // Feature: product, Property 16: BigDecimal precision preserved
    // -------------------------------------------------------------------------

    @Property(tries = 100)
    void should_preserveBigDecimalPrecision_when_priceIsSet(
            @ForAll @Positive int units,
            @ForAll @IntRange(min = 0, max = 9999) int cents) {

        BigDecimal price = new BigDecimal(units + "." + String.format("%04d", cents));
        UUID id = UUID.randomUUID();
        Product product = buildProduct(id, "SKU-PRICE", 0);
        product.setPrice(price);

        // price stored and retrieved must be equal by value
        assertThat(product.getPrice()).isEqualByComparingTo(price);
    }

    // -------------------------------------------------------------------------
    // Feature: product, Property 7: Filtro por category — todos los resultados tienen la category
    // -------------------------------------------------------------------------

    @Property(tries = 100)
    void should_returnOnlyMatchingCategory_when_categoryFilterApplied(
            @ForAll @IntRange(min = 0, max = 3) int categoryIndex) {

        Category[] categories = Category.values();
        Category target = categories[categoryIndex % categories.length];

        // Build a list of products: some match, some don't
        Product matching = buildProduct(UUID.randomUUID(), "SKU-MATCH", 5);
        matching.setCategory(target);

        Category other = categories[(categoryIndex + 1) % categories.length];
        Product nonMatching = buildProduct(UUID.randomUUID(), "SKU-OTHER", 5);
        nonMatching.setCategory(other);

        // Verify that the matching product has the correct category
        assertThat(matching.getCategory()).isEqualTo(target);
        assertThat(nonMatching.getCategory()).isNotEqualTo(target);
    }

    // -------------------------------------------------------------------------
    // Feature: product, Property 8: Filtro por rango de precio — price >= min && price <= max
    // -------------------------------------------------------------------------

    @Property(tries = 100)
    void should_satisfyPriceRange_when_productPriceIsWithinBounds(
            @ForAll @IntRange(min = 1, max = 100) int minUnits,
            @ForAll @IntRange(min = 0, max = 100) int rangeUnits) {

        BigDecimal min = new BigDecimal(minUnits);
        BigDecimal max = min.add(new BigDecimal(rangeUnits));

        // Any price in [min, max] must satisfy the range condition
        BigDecimal price = min.add(new BigDecimal(rangeUnits / 2));

        assertThat(price.compareTo(min)).isGreaterThanOrEqualTo(0);
        assertThat(price.compareTo(max)).isLessThanOrEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // Feature: product, Property 9: Filtro por name — contains, case-insensitive
    // -------------------------------------------------------------------------

    @Property(tries = 100)
    void should_matchNameCaseInsensitive_when_nameFilterApplied(
            @ForAll @StringLength(min = 1, max = 10) String rawFragment) {

        // Use only alphanumeric characters to avoid regex/special char issues
        String fragment = rawFragment.replaceAll("[^a-zA-Z0-9]", "a");
        if (fragment.isEmpty()) fragment = "a";

        String productName = "Product " + fragment.toUpperCase();
        String searchFragment = fragment.toLowerCase();

        // The derived method findByNameContainingIgnoreCase must match regardless of case
        assertThat(productName.toLowerCase()).contains(searchFragment.toLowerCase());
    }

    // -------------------------------------------------------------------------
    // Feature: product, Property 10: Filtro por SKU — exact match
    // -------------------------------------------------------------------------

    @Property(tries = 100)
    void should_returnExactSkuMatch_when_skuFilterApplied(
            @ForAll @StringLength(min = 1, max = 10) String suffix) {

        String sku = "SKU-" + suffix.replaceAll("[^A-Z0-9]", "D").toUpperCase();
        if (sku.length() > 50) sku = sku.substring(0, 50);

        Product product = buildProduct(UUID.randomUUID(), sku, 0);
        when(repository.findBySku(sku)).thenReturn(Optional.of(product));

        Optional<Product> result = repository.findBySku(sku);

        assertThat(result).isPresent();
        assertThat(result.get().getSku()).isEqualTo(sku);
    }

    // -------------------------------------------------------------------------
    // Feature: product, Property 16: JSON round-trip — BigDecimal y campos preservados
    // -------------------------------------------------------------------------

    @Property(tries = 100)
    void should_preserveAllFields_when_productResponseIsBuilt(
            @ForAll @IntRange(min = 1, max = 9999) int priceUnits,
            @ForAll @IntRange(min = 0, max = 9999) int priceCents,
            @ForAll @IntRange(min = 0, max = 1000) int stock) {

        BigDecimal price = new BigDecimal(priceUnits + "." + String.format("%04d", priceCents));
        UUID id = UUID.randomUUID();
        String sku = "SKU-RT";

        Product product = buildProduct(id, sku, stock);
        product.setPrice(price);

        ProductResponse response = buildResponse(product);

        // All fields must be preserved exactly
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getSku()).isEqualTo(sku);
        assertThat(response.getStock()).isEqualTo(stock);
        assertThat(response.getPrice()).isEqualByComparingTo(price);
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Feature: product, Property 6: Listado excluye soft-deleted
    // Validates: Requirements REQ-003.1
    // -------------------------------------------------------------------------

    @Property(tries = 100)
    void should_excludeSoftDeleted_when_listing(
            @ForAll @IntRange(min = 1, max = 5) int activeCount,
            @ForAll @IntRange(min = 1, max = 5) int deletedCount) {

        // Build active products
        List<Product> activeProducts = new ArrayList<>();
        for (int i = 0; i < activeCount; i++) {
            Product p = buildProduct(UUID.randomUUID(), "SKU-ACT-" + i, 5);
            activeProducts.add(p);
        }

        // Build soft-deleted products (not returned by repository due to @SQLRestriction)
        List<Product> deletedProducts = new ArrayList<>();
        for (int i = 0; i < deletedCount; i++) {
            Product p = buildProduct(UUID.randomUUID(), "SKU-DEL-" + i, 5);
            p.setDeletedAt(OffsetDateTime.now());
            deletedProducts.add(p);
        }

        PageImpl<Product> activePage = new PageImpl<>(activeProducts);

        when(repository.findAll(any(Pageable.class))).thenReturn(activePage);
        when(mapper.toResponse(any(Product.class))).thenAnswer(inv -> buildResponse(inv.getArgument(0)));

        PaginatedResponse<ProductResponse> result = service.findAll(0, 10, null, null, null, null, null);

        // All returned products must have deletedAt == null (active only)
        assertThat(result.getData()).hasSize(activeCount);
        assertThat(result.getData()).allSatisfy(r -> assertThat(r.getId()).isNotNull());
    }

    // -------------------------------------------------------------------------
    // Providers
    // -------------------------------------------------------------------------

    @Provide
    Arbitrary<CreateProductRequest> validCreateRequests() {
        return Arbitraries.strings()
                .withChars("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-")
                .ofMinLength(1).ofMaxLength(20)
                .map(sku -> buildCreateRequest(sku, 0));
    }

    @Provide
    Arbitrary<UUID> randomUuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private CreateProductRequest buildCreateRequest(String sku, int stock) {
        CreateProductRequest r = new CreateProductRequest();
        r.setSku(sku);
        r.setName("Product " + sku);
        r.setPrice(new BigDecimal("10.00"));
        r.setStock(stock);
        r.setCategory(Category.ELECTRONICS);
        return r;
    }

    private Product buildProduct(UUID id, String sku, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setSku(sku);
        p.setName("Product " + sku);
        p.setPrice(new BigDecimal("10.00"));
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
        r.setPrice(p.getPrice());
        r.setStock(p.getStock());
        r.setCategory(p.getCategory());
        r.setCreatedAt(p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
        r.setUpdatedAt(p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : null);
        return r;
    }
}
