package com.sdd.products.exception;

import com.sdd.products.domain.exception.ImmutableSkuException;
import com.sdd.products.domain.exception.InsufficientStockException;
import com.sdd.products.domain.exception.InvalidStockException;
import com.sdd.products.domain.exception.ProductNotFoundException;
import com.sdd.products.domain.exception.SkuAlreadyExistsException;
import com.sdd.products.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Global exception handler that translates domain exceptions into structured ErrorResponse JSON.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleProductNotFound(ProductNotFoundException ex) {
        return new ErrorResponse("PRODUCT_NOT_FOUND", ex.getMessage(), List.of());
    }

    @ExceptionHandler(SkuAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleSkuAlreadyExists(SkuAlreadyExistsException ex) {
        return new ErrorResponse("SKU_ALREADY_EXISTS", ex.getMessage(), List.of());
    }

    @ExceptionHandler(ImmutableSkuException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleImmutableSku(ImmutableSkuException ex) {
        return new ErrorResponse("IMMUTABLE_SKU", ex.getMessage(), List.of());
    }

    @ExceptionHandler(InsufficientStockException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleInsufficientStock(InsufficientStockException ex) {
        return new ErrorResponse("INSUFFICIENT_STOCK", ex.getMessage(), List.of());
    }

    @ExceptionHandler(InvalidStockException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidStock(InvalidStockException ex) {
        return new ErrorResponse("INVALID_STOCK", ex.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        List<String> fields = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(FieldError::getField)
                .distinct()
                .toList();
        return new ErrorResponse("VALIDATION_ERROR", "Request validation failed", fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMalformedJson(HttpMessageNotReadableException ex) {
        return new ErrorResponse("MALFORMED_JSON", "Request body is malformed or unreadable", List.of());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneric(Exception ex) {
        return new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred", List.of());
    }
}
