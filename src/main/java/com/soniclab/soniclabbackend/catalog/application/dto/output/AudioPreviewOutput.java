package com.soniclab.soniclabbackend.catalog.application.dto.output;

import java.util.List;

public record AudioPreviewOutput(
        String streamUrl,
        int durationInSeconds,
        List<Double> waveformPeaks
) {}
