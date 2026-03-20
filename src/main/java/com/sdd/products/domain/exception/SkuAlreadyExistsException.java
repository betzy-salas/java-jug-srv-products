package com.sdd.products.domain.exception;

public class SkuAlreadyExistsException extends RuntimeException {

    public SkuAlreadyExistsException(String sku) {
        super("Product with SKU already exists: " + sku);
    }
}
