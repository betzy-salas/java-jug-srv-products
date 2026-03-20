package com.sdd.products.domain.exception;

public class ImmutableSkuException extends RuntimeException {

    public ImmutableSkuException() {
        super("SKU cannot be modified after creation");
    }
}
