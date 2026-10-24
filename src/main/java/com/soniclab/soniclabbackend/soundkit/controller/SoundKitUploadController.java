package com.soniclab.soniclabbackend.soundkit.controller;

import com.soniclab.soniclabbackend.soundkit.dto.response.SoundKitUploadResponse;
import com.soniclab.soniclabbackend.soundkit.entity.AudioDemo;
import com.soniclab.soniclabbackend.soundkit.service.SoundKitFileProcessor;
import com.soniclab.soniclabbackend.soundkit.storage.FileStorageService;
import com.soniclab.soniclabbackend.user.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/soundkits/uploads")
@Tag(
        name = "SoundKits - Uploads",
        description = "Endpoints para upload de arquivos associados a um SoundKit (pacote de áudio ZIP/soltos, imagem de capa e áudio demo com forma de onda). Utilizados no front-end em React antes do envio do cadastro final do SoundKit."
)
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
    @Operation(
            summary = "Upload do pacote de áudio (ZIP ou lista de arquivos)",
            description = "Recebe um arquivo ZIP ou múltiplos arquivos de áudio soltos. " +
                    "O back-end valida os formatos, compacta caso necessário e armazena o pacote. " +
                    "Retorna a URL do arquivo ZIP final (fileUrl), o tamanho em bytes e o total de arquivos de áudio válidos processados.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Pacote enviado e processado com sucesso"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Arquivos inválidos, formato não suportado ou lista vazia",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Não autenticado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<SoundKitUploadResponse> uploadPackage(
            @Parameter(
                    description = "Arquivo ZIP do pacote ou lista de arquivos de áudio (WAV, MP3, FLAC, AIFF)",
                    required = true
            )
            @RequestParam("files") List<MultipartFile> files
    ) {
        SoundKitUploadResponse response = fileProcessor.processAndStorePackage(files);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint para upload da imagem de capa (coverUrl).
     */
    @PostMapping(value = "/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload da imagem de capa do SoundKit",
            description = "Recebe uma imagem (PNG, JPG, WEBP), armazena no servidor de arquivos e retorna a URL pública acessível para exibição e inclusão no campo 'coverUrl'.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Capa enviada com sucesso, retorna objeto com a propriedade coverUrl"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Arquivo inválido ou erro no processamento do arquivo",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Não autenticado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<Map<String, String>> uploadCover(
            @Parameter(
                    description = "Arquivo de imagem (PNG, JPEG, WEBP)",
                    required = true
            )
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "cover.jpg";
        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf(".")) : ".jpg";
        String path = "soundkits/covers/" + UUID.randomUUID() + ext;

        try (InputStream is = file.getInputStream()) {
            String key = storageService.upload(path, is, file.getSize(), file.getContentType());
            return ResponseEntity.ok(Map.of("coverUrl", storageService.getPublicUrl(key)));
        }
    }

    /**
     * Endpoint para upload do áudio de demonstração, retornando a URL, duração e a forma de onda (AudioDemo).
     */
    @PostMapping(value = "/demo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload do áudio demo de pré-escuta",
            description = "Recebe o arquivo de áudio de demonstração (MP3, WAV), calcula a duração em segundos e gera os dados de onda sonora (waveformData). " +
                    "Retorna o objeto AudioDemo pronto para ser embutido na requisição de criação do SoundKit.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Áudio demo processado com sucesso"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Arquivo inválido ou não é um formato de áudio suportado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Não autenticado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<AudioDemo> uploadDemo(
            @Parameter(
                    description = "Arquivo de áudio demo (MP3 ou WAV)",
                    required = true
            )
            @RequestParam("file") MultipartFile file
    ) {
        AudioDemo audioDemo = fileProcessor.processAudioDemo(file);
        return ResponseEntity.ok(audioDemo);
    }
}
