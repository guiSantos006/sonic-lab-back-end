package com.soniclab.soniclabbackend.catalog.domain.repository;

import com.soniclab.soniclabbackend.catalog.domain.Product;
import com.soniclab.soniclabbackend.catalog.domain.ProductId;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(ProductId id);
    List<Product> findAllActive();
}
