package com.soniclab.soniclabbackend.soundkit.entity;

import java.util.List;

public record AudioDemo(
        String url,
        Integer durationInSeconds,
        List<Float> waveformData
) {}
