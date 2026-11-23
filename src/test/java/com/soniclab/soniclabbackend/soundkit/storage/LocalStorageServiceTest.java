package com.soniclab.soniclabbackend.soundkit.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.FileSystemUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class LocalStorageServiceTest {

    private Path tempUploadDir;
    private LocalStorageService localStorageService;

    @BeforeEach
    void setUp() throws IOException {
        tempUploadDir = Files.createTempDirectory("soniclab-test-uploads");
        localStorageService = new LocalStorageService(
                tempUploadDir.toString(),
                "http://localhost:8080/uploads/"
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        FileSystemUtils.deleteRecursively(tempUploadDir);
    }

    @Test
    @DisplayName("Deve fazer upload de arquivo localmente e retornar a key correta")
    void testUploadSuccess() {
        byte[] content = "test audio data".getBytes();
        InputStream in = new ByteArrayInputStream(content);

        String path = "soundkits/demos/demo.wav";
        String key = localStorageService.upload(path, in, content.length, "audio/wav", FileVisibility.PUBLIC);

        assertEquals("soundkits/demos/demo.wav", key);
        assertTrue(localStorageService.exists(key));
        assertTrue(Files.exists(tempUploadDir.resolve("soundkits/demos/demo.wav")));
    }

    @Test
    @DisplayName("Deve retornar URL pública correta para arquivo")
    void testGetPublicUrl() {
        String url = localStorageService.getPublicUrl("soundkits/covers/cover.jpg");
        assertEquals("http://localhost:8080/uploads/soundkits/covers/cover.jpg", url);
    }

    @Test
    @DisplayName("Deve retornar mesma URL pública para generateDownloadUrl em ambiente local")
    void testGenerateDownloadUrl() {
        String urlWithDefaultTtl = localStorageService.generateDownloadUrl("soundkits/packages/pack.zip");
        String urlWithCustomTtl = localStorageService.generateDownloadUrl("soundkits/packages/pack.zip", Duration.ofMinutes(30));

        assertEquals("http://localhost:8080/uploads/soundkits/packages/pack.zip", urlWithDefaultTtl);
        assertEquals(urlWithDefaultTtl, urlWithCustomTtl);
    }

    @Test
    @DisplayName("Deve excluir arquivo local existente")
    void testDeleteExistingFile() {
        byte[] content = "data to delete".getBytes();
        String key = localStorageService.upload("soundkits/packages/to-delete.zip", new ByteArrayInputStream(content), content.length, "application/zip");

        assertTrue(localStorageService.exists(key));

        localStorageService.delete(key);

        assertFalse(localStorageService.exists(key));
    }

    @Test
    @DisplayName("Deve tolerar exclusão de arquivo inexistente sem lançar erro")
    void testDeleteNonExistentFile() {
        assertDoesNotThrow(() -> localStorageService.delete("soundkits/packages/missing.zip"));
    }

    @Test
    @DisplayName("Deve bloquear tentativa de path traversal com SecurityException")
    void testPathTraversalPrevention() {
        InputStream in = new ByteArrayInputStream("malicious".getBytes());

        assertThrows(IllegalArgumentException.class, () ->
                localStorageService.upload("../outside.txt", in, 9, "text/plain")
        );
    }

    @Test
    @DisplayName("Deve classificar isPublic corretamente")
    void testIsPublic() {
        assertTrue(localStorageService.isPublic("soundkits/covers/cover.png"));
        assertTrue(localStorageService.isPublic("soundkits/demos/demo.mp3"));
        assertFalse(localStorageService.isPublic("soundkits/packages/pack.zip"));
    }
}
