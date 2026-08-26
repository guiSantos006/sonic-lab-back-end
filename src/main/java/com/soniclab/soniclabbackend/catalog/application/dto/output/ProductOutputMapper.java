package com.soniclab.soniclabbackend.catalog.application.dto.output;

import com.soniclab.soniclabbackend.catalog.domain.AudioPreview;
import com.soniclab.soniclabbackend.catalog.domain.PresetBank;
import com.soniclab.soniclabbackend.catalog.domain.Product;

import java.util.Collections;

public final class ProductOutputMapper {

    private ProductOutputMapper() {}

    public static AudioPreviewOutput toAudioPreviewOutput(AudioPreview audioPreview) {
        if (audioPreview == null) {
            return null;
        }
        return new AudioPreviewOutput(
                audioPreview.streamUrl(),
                audioPreview.durationInSeconds(),
                audioPreview.waveformPeaks()
        );
    }

    public static ProductCardOutput toCardOutput(Product product) {
        if (product == null) {
            return null;
        }
        return new ProductCardOutput(
                product.getId().uuid().toString(),
                product.getTitle(),
                product.getPrice().amount(),
                product.getPrice().currency(),
                product.getCreatorId(),
                product.getCreatorName(),
                product.getCategory().name(),
                product.getCoverImageUrl(),
                toAudioPreviewOutput(product.getAudioPreview()),
                product.getTags()
        );
    }

    public static ProductDetailOutput toDetailOutput(Product product) {
        if (product == null) {
            return null;
        }

        var targetPlugins = product instanceof PresetBank presetBank
                ? presetBank.getTargetPlugins()
                : Collections.<String>emptySet();

        return new ProductDetailOutput(
                product.getId().uuid().toString(),
                product.getTitle(),
                product.getDescription(),
                product.getPrice().amount(),
                product.getPrice().currency(),
                product.getCreatorId(),
                product.getCreatorName(),
                product.getCategory().name(),
                product.getTags(),
                product.getCoverImageUrl(),
                toAudioPreviewOutput(product.getAudioPreview()),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                targetPlugins
        );
    }
}
