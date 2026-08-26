package com.soniclab.soniclabbackend.catalog.application.exception;

import com.soniclab.soniclabbackend.catalog.domain.ProductId;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(ProductId id) {
        super("Product not found with id: " + id.uuid());
    }

    public ProductNotFoundException(String message) {
        super(message);
    }
}
