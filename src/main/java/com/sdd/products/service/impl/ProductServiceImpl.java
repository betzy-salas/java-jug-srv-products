package com.sdd.products.service.impl;

import com.sdd.products.domain.Category;
import com.sdd.products.domain.entity.Product;
import com.sdd.products.domain.exception.ImmutableSkuException;
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
import com.sdd.products.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository repository;
    private final ProductMapper mapper;

    public ProductServiceImpl(ProductRepository repository, ProductMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        if (repository.existsBySku(request.getSku())) {
            throw new SkuAlreadyExistsException(request.getSku());
        }
        Product product = mapper.toEntity(request);
        product.setId(UUID.randomUUID());
        repository.save(product);
        return mapper.toResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse findById(UUID id) {
        return mapper.toResponse(
                repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id))
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<ProductResponse> findAll(int page, int size, Category category,
                                                      BigDecimal minPrice, BigDecimal maxPrice,
                                                      String name, String sku) {
        if (sku != null) {
            return repository.findBySku(sku)
                    .map(p -> {
                        List<ProductResponse> data = List.of(mapper.toResponse(p));
                        return new PaginatedResponse<>(data, 0, size, 1L);
                    })
                    .orElse(new PaginatedResponse<>(List.of(), 0, size, 0L));
        }

        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Product> result = selectPage(category, minPrice, maxPrice, name, pageable);

        return new PaginatedResponse<>(
                result.getContent().stream().map(mapper::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements()
        );
    }

    @Override
    @Transactional
    public ProductResponse update(UUID id, UpdateProductRequest request) {
        Product product = repository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        if (request.getSku() != null) {
            throw new ImmutableSkuException();
        }

        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getStock() != null) {
            product.setStock(request.getStock());
        }
        if (request.getCategory() != null) {
            product.setCategory(request.getCategory());
        }

        repository.save(product);
        return mapper.toResponse(product);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Product product = repository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        product.setDeletedAt(OffsetDateTime.now());
        repository.save(product);
    }

    @Override
    @Transactional
    public ProductResponse adjustStock(UUID id, StockAdjustmentRequest request) {
        Product product = repository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        int newStock = product.getStock() + request.getDelta();
        if (newStock < 0) {
            throw new InsufficientStockException(product.getStock(), request.getDelta());
        }

        product.setStock(newStock);
        repository.save(product);
        return mapper.toResponse(product);
    }

    private Page<Product> selectPage(Category category, BigDecimal minPrice, BigDecimal maxPrice,
                                     String name, PageRequest pageable) {
        boolean hasCategory = category != null;
        boolean hasPrice = minPrice != null && maxPrice != null;
        boolean hasName = name != null;

        if (hasCategory && hasPrice && hasName) {
            return repository.findByCategoryAndNameContainingIgnoreCaseAndPriceBetween(
                    category, name, minPrice, maxPrice, pageable);
        }
        if (hasCategory && hasPrice) {
            return repository.findByCategoryAndPriceBetween(category, minPrice, maxPrice, pageable);
        }
        if (hasCategory && hasName) {
            return repository.findByCategoryAndNameContainingIgnoreCase(category, name, pageable);
        }
        if (hasPrice && hasName) {
            return repository.findByNameContainingIgnoreCaseAndPriceBetween(name, minPrice, maxPrice, pageable);
        }
        if (hasCategory) {
            return repository.findByCategory(category, pageable);
        }
        if (hasPrice) {
            return repository.findByPriceBetween(minPrice, maxPrice, pageable);
        }
        if (hasName) {
            return repository.findByNameContainingIgnoreCase(name, pageable);
        }
        return repository.findAll(pageable);
    }
}
