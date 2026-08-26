package com.soniclab.soniclabbackend.catalog.application.usecase;

import com.soniclab.soniclabbackend.catalog.application.dto.input.CreatePresetBankInput;
import com.soniclab.soniclabbackend.catalog.application.dto.output.ProductDetailOutput;
import com.soniclab.soniclabbackend.catalog.application.dto.output.ProductOutputMapper;
import com.soniclab.soniclabbackend.catalog.domain.AudioPreview;
import com.soniclab.soniclabbackend.catalog.domain.PresetBank;
import com.soniclab.soniclabbackend.catalog.domain.Price;
import com.soniclab.soniclabbackend.catalog.domain.ProductId;
import com.soniclab.soniclabbackend.catalog.domain.repository.ProductRepository;
import org.springframework.stereotype.Service;

@Service
public class CreatePresetBankUseCase {

    private final ProductRepository productRepository;

    public CreatePresetBankUseCase(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public ProductDetailOutput execute(CreatePresetBankInput input) {
        var price = new Price(input.priceAmount(), input.currency());

        AudioPreview audioPreview = null;
        if (input.audioPreview() != null) {
            audioPreview = new AudioPreview(
                    input.audioPreview().streamUrl(),
                    input.audioPreview().durationInSeconds(),
                    input.audioPreview().waveformPeaks()
            );
        }

        var presetBank = new PresetBank(
                new ProductId(),
                input.title(),
                input.description(),
                price,
                input.creatorId(),
                input.creatorName(),
                input.tags(),
                input.coverImageUrl(),
                audioPreview,
                input.securedFileStorageKey(),
                input.targetPlugins()
        );

        var savedProduct = productRepository.save(presetBank);
        return ProductOutputMapper.toDetailOutput(savedProduct);
    }
}
