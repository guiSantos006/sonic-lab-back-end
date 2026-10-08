package com.soniclab.soniclabbackend.soundkit.dto.response;

public record SoundKitUploadResponse(
        String fileUrl,
        long sizeInBytes,
        int totalAudioFiles
) {
}
