package com.soniclab.soniclabbackend.soundkit.dto.request;

import com.soniclab.soniclabbackend.soundkit.entity.AudioDemo;
import com.soniclab.soniclabbackend.soundkit.entity.KitType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Schema(description = "Payload para cadastro de um novo SoundKit na plataforma")
public record CreateSoundKitRequest(
        @Schema(
                description = "Título público do SoundKit",
                example = "808 Mafia Trap Essentials",
                minLength = 3,
                maxLength = 100,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "O título é obrigatório")
        @Size(min = 3, max = 100, message = "O título deve ter entre 3 e 100 caracteres")
        String title,

        @Schema(
                description = "Descrição detalhada do conteúdo do kit, estilo sonoro e diferenciais",
                example = "Mais de 150 sons exclusivos para produção de Trap e Boom Bap, incluindo 808s afinados, kicks socados, claps e hi-hats limpos.",
                maxLength = 1000
        )
        @Size(max = 1000, message = "A descrição pode ter no máximo 1000 caracteres")
        String description,

        @Schema(
                description = "Preço do kit em BRL. Use 0.0 para kits gratuitos.",
                example = "49.90",
                minimum = "0.0",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "O preço é obrigatório")
        @DecimalMin(value = "0.0", inclusive = true, message = "O preço não pode ser negativo")
        BigDecimal price,

        @Schema(
                description = "Categoria/Tipo do kit",
                example = "DRUM_KIT",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "O tipo do kit é obrigatório")
        KitType type,

        @Schema(
                description = "URL pública da capa do SoundKit (gerada previamente no endpoint de upload de capa)",
                example = "http://localhost:8080/uploads/soundkits/covers/capa-trap.jpg"
        )
        String coverUrl,

        @Schema(
                description = "URL ou caminho do arquivo ZIP completo do pacote (gerado previamente no endpoint de upload de pacote)",
                example = "http://localhost:8080/uploads/soundkits/packages/trap_kit_2026.zip",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "O arquivo do kit é obrigatório")
        String fileUrl,

        @Schema(
                description = "Estrutura do áudio de demonstração com waveform para pré-escuta do usuário",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "O áudio demo é obrigatório")
        @Valid
        AudioDemo audioDemo,

        @Schema(
                description = "Lista de tags para categorização e busca rápida (máximo 15 tags)",
                example = "[\"trap\", \"808\", \"drums\", \"hiphop\"]"
        )
        @Size(max = 15, message = "O kit pode ter no máximo 15 tags")
        List<@NotBlank(message = "A tag não pode ser vazia")
        @Size(max = 30, message = "Cada tag pode ter no máximo 30 caracteres") String> tags,

        @Schema(
                description = "Especificações técnicas do pacote em pares chave/valor (ex: formato, quantidade de arquivos, taxa de amostragem, DAW recomendada)",
                example = "{\"formato\": \"WAV 24-bit 44.1kHz\", \"totalArquivos\": \"120\", \"compatibilidade\": \"Qualquer DAW\"}"
        )
        Map<String, String> specifications
) {
}
