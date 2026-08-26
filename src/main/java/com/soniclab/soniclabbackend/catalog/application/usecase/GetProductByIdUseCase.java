package com.soniclab.soniclabbackend.catalog.application.usecase;

import com.soniclab.soniclabbackend.catalog.application.dto.output.ProductDetailOutput;
import com.soniclab.soniclabbackend.catalog.application.dto.output.ProductOutputMapper;
import com.soniclab.soniclabbackend.catalog.application.exception.ProductNotFoundException;
import com.soniclab.soniclabbackend.catalog.domain.ProductId;
import com.soniclab.soniclabbackend.catalog.domain.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GetProductByIdUseCase {

    private final ProductRepository productRepository;

    public GetProductByIdUseCase(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public ProductDetailOutput execute(UUID id) {
        var productId = new ProductId(id);
        var product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        return ProductOutputMapper.toDetailOutput(product);
    }
}
