package com.soniclab.soniclabbackend.catalog.domain;

import java.util.List;

public record AudioPreview(
        String streamUrl,              // URL pública do arquivo (ex: mp3 compactado para streaming rápido)
        int durationInSeconds,// Duração do áudio (ex: 45s) -> React já renderiza "00:45" sem esperar o áudio carregar
        List<Double> waveformPeaks// Picos da onda sonora (Waveform) para desenhar as ondas no React (ex: Wavesurfer.js)
) {
    public AudioPreview {
        if (streamUrl == null || streamUrl.isBlank()) {
            throw new IllegalArgumentException("The URL of the preview cannot be null or empty");
        }
        if (durationInSeconds < 0) {
            throw new IllegalArgumentException("The duration cannot be negative.");
        }
    }
}