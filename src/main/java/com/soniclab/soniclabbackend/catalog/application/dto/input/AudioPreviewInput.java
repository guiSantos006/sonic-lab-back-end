package com.soniclab.soniclabbackend.catalog.application.dto.input;

import java.util.List;

public record AudioPreviewInput(
        String streamUrl,
        int durationInSeconds,
        List<Double> waveformPeaks
) {}
