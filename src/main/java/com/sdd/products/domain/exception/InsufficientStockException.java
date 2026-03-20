package com.sdd.products.domain.exception;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(int currentStock, int delta) {
        super("Insufficient stock. Current: " + currentStock + ", requested delta: " + delta);
    }
}
