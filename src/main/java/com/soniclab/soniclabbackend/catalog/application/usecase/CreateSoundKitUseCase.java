package com.soniclab.soniclabbackend.catalog.application.usecase;

import com.soniclab.soniclabbackend.catalog.application.dto.input.CreateSoundKitInput;
import com.soniclab.soniclabbackend.catalog.application.dto.output.ProductDetailOutput;
import com.soniclab.soniclabbackend.catalog.application.dto.output.ProductOutputMapper;
import com.soniclab.soniclabbackend.catalog.domain.AudioPreview;
import com.soniclab.soniclabbackend.catalog.domain.Price;
import com.soniclab.soniclabbackend.catalog.domain.ProductId;
import com.soniclab.soniclabbackend.catalog.domain.SoundKit;
import com.soniclab.soniclabbackend.catalog.domain.repository.ProductRepository;
import org.springframework.stereotype.Service;

@Service
public class CreateSoundKitUseCase {

    private final ProductRepository productRepository;

    public CreateSoundKitUseCase(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public ProductDetailOutput execute(CreateSoundKitInput input) {
        var price = new Price(input.priceAmount(), input.currency());

        AudioPreview audioPreview = null;
        if (input.audioPreview() != null) {
            audioPreview = new AudioPreview(
                    input.audioPreview().streamUrl(),
                    input.audioPreview().durationInSeconds(),
                    input.audioPreview().waveformPeaks()
            );
        }

        var soundKit = new SoundKit(
                new ProductId(),
                input.title(),
                input.description(),
                price,
                input.creatorId(),
                input.creatorName(),
                input.tags(),
                input.coverImageUrl(),
                audioPreview,
                input.securedFileStorageKey()
        );

        var savedProduct = productRepository.save(soundKit);
        return ProductOutputMapper.toDetailOutput(savedProduct);
    }
}
