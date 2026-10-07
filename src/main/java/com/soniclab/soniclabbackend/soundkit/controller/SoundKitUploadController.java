package com.soniclab.soniclabbackend.soundkit.controller;

import com.soniclab.soniclabbackend.soundkit.dto.response.SoundKitUploadResponse;
import com.soniclab.soniclabbackend.soundkit.entity.AudioDemo;
import com.soniclab.soniclabbackend.soundkit.service.SoundKitFileProcessor;
import com.soniclab.soniclabbackend.soundkit.storage.FileStorageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/soundkits/uploads")
public class SoundKitUploadController {

    private final SoundKitFileProcessor fileProcessor;
    private final FileStorageService storageService;

    public SoundKitUploadController(SoundKitFileProcessor fileProcessor, FileStorageService storageService) {
        this.fileProcessor = fileProcessor;
        this.storageService = storageService;
    }

    /**
     * Endpoint para upload e processamento do pacote de áudio (ZIP ou arquivos soltos).
     */
    @PostMapping(value = "/package", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SoundKitUploadResponse> uploadPackage(@RequestParam("files") List<MultipartFile> files) {
        SoundKitUploadResponse response = fileProcessor.processAndStorePackage(files);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint para upload da imagem de capa (coverUrl).
     */
    @PostMapping(value = "/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadCover(@RequestParam("file") MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "cover.jpg";
        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf(".")) : ".jpg";
        String path = "soundkits/covers/" + UUID.randomUUID() + ext;

        String url = storageService.upload(path, file.getInputStream(), file.getContentType());
        return ResponseEntity.ok(Map.of("coverUrl", url));
    }

    /**
     * Endpoint para upload do áudio de demonstração, retornando a URL, duração e a forma de onda (AudioDemo).
     */
    @PostMapping(value = "/demo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AudioDemo> uploadDemo(@RequestParam("file") MultipartFile file) {
        AudioDemo audioDemo = fileProcessor.processAudioDemo(file);
        return ResponseEntity.ok(audioDemo);
    }
}
