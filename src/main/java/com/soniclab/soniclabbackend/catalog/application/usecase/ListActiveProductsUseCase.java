package com.soniclab.soniclabbackend.catalog.application.usecase;

import com.soniclab.soniclabbackend.catalog.application.dto.output.ProductCardOutput;
import com.soniclab.soniclabbackend.catalog.application.dto.output.ProductOutputMapper;
import com.soniclab.soniclabbackend.catalog.domain.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ListActiveProductsUseCase {

    private final ProductRepository productRepository;

    public ListActiveProductsUseCase(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<ProductCardOutput> execute() {
        return productRepository.findAllActive()
                .stream()
                .map(ProductOutputMapper::toCardOutput)
                .toList();
    }
}
