package com.sdd.products.repository;

import com.sdd.products.domain.Category;
import com.sdd.products.domain.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Product> findByCategory(Category category, Pageable pageable);

    Page<Product> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    Page<Product> findByCategoryAndPriceBetween(Category category, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    Page<Product> findByCategoryAndNameContainingIgnoreCase(Category category, String name, Pageable pageable);

    Page<Product> findByNameContainingIgnoreCaseAndPriceBetween(String name, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    Page<Product> findByCategoryAndNameContainingIgnoreCaseAndPriceBetween(Category category, String name, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);
}
