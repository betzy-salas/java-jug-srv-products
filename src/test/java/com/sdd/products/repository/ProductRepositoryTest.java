package com.sdd.products.repository;

import com.sdd.products.domain.Category;
import com.sdd.products.domain.entity.Product;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
class ProductRepositoryTest {

    @Autowired
    private ProductRepository repository;

    @Autowired
    private EntityManager entityManager;

    private Product activeProduct;
    private Product softDeletedProduct;

    @BeforeEach
    void setUp() {
        activeProduct = buildProduct("SKU-ACTIVE", "Active Product", null);
        softDeletedProduct = buildProduct("SKU-DELETED", "Deleted Product", OffsetDateTime.now());

        repository.save(activeProduct);
        repository.save(softDeletedProduct);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void should_returnProduct_when_findByIdWithActiveProduct() {
        Optional<Product> result = repository.findById(activeProduct.getId());
        assertThat(result).isPresent();
        assertThat(result.get().getSku()).isEqualTo("SKU-ACTIVE");
    }

    @Test
    void should_returnEmpty_when_findByIdWithSoftDeletedProduct() {
        Optional<Product> result = repository.findById(softDeletedProduct.getId());
        assertThat(result).isEmpty();
    }

    @Test
    void should_excludeSoftDeleted_when_findAll() {
        Page<Product> page = repository.findAll(PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getSku()).isEqualTo("SKU-ACTIVE");
    }

    @Test
    void should_returnFalse_when_existsBySkuForSoftDeletedProduct() {
        boolean exists = repository.existsBySku("SKU-DELETED");
        assertThat(exists).isFalse();
    }

    @Test
    void should_returnTrue_when_existsBySkuForActiveProduct() {
        boolean exists = repository.existsBySku("SKU-ACTIVE");
        assertThat(exists).isTrue();
    }

    @Test
    void should_preserveRecordInDatabase_when_softDeleted() {
        // Access the soft-deleted record bypassing @SQLRestriction via native query through EntityManager
        Long count = (Long) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM products WHERE sku = 'SKU-DELETED'")
                .getSingleResult();
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void should_findByNameCaseInsensitive_when_nameContainsFragment() {
        Page<Product> upper = repository.findByNameContainingIgnoreCase("ACTIVE", PageRequest.of(0, 10));
        Page<Product> lower = repository.findByNameContainingIgnoreCase("active", PageRequest.of(0, 10));
        Page<Product> mixed = repository.findByNameContainingIgnoreCase("AcTiVe", PageRequest.of(0, 10));

        assertThat(upper.getTotalElements()).isEqualTo(1);
        assertThat(lower.getTotalElements()).isEqualTo(1);
        assertThat(mixed.getTotalElements()).isEqualTo(1);
    }

    @Test
    void should_returnEmpty_when_findBySkuForSoftDeletedProduct() {
        Optional<Product> result = repository.findBySku("SKU-DELETED");
        assertThat(result).isEmpty();
    }

    // Feature: product, Property 6: Listado excluye soft-deleted — REQ-003.1
    @Test
    void should_excludeSoftDeleted_when_listingWithVariousActiveCounts() {
        Random random = new Random();
        for (int iteration = 0; iteration < 10; iteration++) {
            repository.deleteAll();
            entityManager.flush();
            entityManager.clear();

            int n = 1 + random.nextInt(5); // active products: 1..5
            int m = 1 + random.nextInt(5); // soft-deleted products: 1..5

            for (int i = 0; i < n; i++) {
                repository.save(buildProduct("SKU-ACTIVE-" + iteration + "-" + i, "Active " + i, null));
            }
            for (int i = 0; i < m; i++) {
                repository.save(buildProduct("SKU-DELETED-" + iteration + "-" + i, "Deleted " + i, OffsetDateTime.now()));
            }
            entityManager.flush();
            entityManager.clear();

            Page<Product> page = repository.findAll(PageRequest.of(0, 100));

            assertThat(page.getContent()).hasSize(n);
            assertThat(page.getContent()).allMatch(p -> p.getDeletedAt() == null);
        }
    }

    // --- helpers ---

    private Product buildProduct(String sku, String name, OffsetDateTime deletedAt) {
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setSku(sku);
        p.setName(name);
        p.setPrice(new BigDecimal("99.99"));
        p.setStock(10);
        p.setCategory(Category.ELECTRONICS);
        p.setDeletedAt(deletedAt);
        return p;
    }
}
