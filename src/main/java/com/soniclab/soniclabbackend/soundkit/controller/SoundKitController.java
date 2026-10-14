package com.soniclab.soniclabbackend.soundkit.controller;

import com.soniclab.soniclabbackend.soundkit.dto.request.CreateSoundKitRequest;
import com.soniclab.soniclabbackend.soundkit.dto.request.SoundKitFilterParams;
import com.soniclab.soniclabbackend.soundkit.dto.response.PageResponse;
import com.soniclab.soniclabbackend.soundkit.dto.response.SoundKitResponse;
import com.soniclab.soniclabbackend.soundkit.service.SoundKitService;
import com.soniclab.soniclabbackend.user.config.JWTUserData;
import com.soniclab.soniclabbackend.user.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/soundkits")
@Tag(
        name = "SoundKits",
        description = "Gerenciamento completo do catálogo de SoundKits: consulta pública com filtros e paginação, listagem de tags, cadastro de kits pelo produtor, visualização dos próprios kits e exclusão lógica."
)
public class SoundKitController {

    private final SoundKitService soundKitService;

    public SoundKitController(SoundKitService soundKitService) {
        this.soundKitService = soundKitService;
    }

    // ==========================================
    // Endpoints Públicos (Não requerem Token JWT)
    // ==========================================

    @GetMapping
    @Operation(
            summary = "Listar catálogo de SoundKits com filtros e paginação",
            description = "Retorna uma lista paginada de SoundKits ativos disponíveis no marketplace. " +
                    "Permite busca textual por título (q), filtro por categoria (type), lista de tags e faixa de preço (minPrice e maxPrice). " +
                    "Suporta parâmetros de paginação: page (iniciando em 0), size (padrão 20) e sort (ex: 'createdAt,desc' ou 'price,asc')."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Página de SoundKits recuperada com sucesso"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Parâmetros de busca inválidos (ex: minPrice maior que maxPrice ou número de tags excedido)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<PageResponse<SoundKitResponse>> list(
            @ParameterObject SoundKitFilterParams filterParams,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(soundKitService.search(filterParams, pageable));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Obter detalhes de um SoundKit por ID",
            description = "Retorna os detalhes completos de um SoundKit ativo a partir do seu identificador único (UUID). " +
                    "Não inclui o link de download direto do arquivo pago (este é liberado apenas após a confirmação de compra)."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "SoundKit encontrado com sucesso"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "SoundKit não encontrado ou inativo no sistema",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<SoundKitResponse> getById(
            @Parameter(
                    description = "Identificador único (UUID) do SoundKit desejado",
                    example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
                    required = true
            )
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(soundKitService.getActiveById(id));
    }

    @GetMapping("/tags")
    @Operation(
            summary = "Listar todas as tags distintas em uso",
            description = "Retorna uma lista com todas as tags únicas associadas a SoundKits ativos na plataforma. " +
                    "Ideal para o front-end em React popular componentes de autocomplete, filtros em dropdown e chips clicáveis de pesquisa."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Lista de tags recuperada com sucesso",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(type = "string", example = "trap"))
                    )
            )
    })
    public ResponseEntity<List<String>> listTags() {
        return ResponseEntity.ok(soundKitService.listTags());
    }

    // ==========================================
    // Endpoints Autenticados (Requerem Token JWT)
    // ==========================================

    @PostMapping
    @Operation(
            summary = "Cadastrar um novo SoundKit",
            description = "Cria um novo SoundKit vinculado automaticamente ao usuário produtor autenticado (via JWT). " +
                    "Antes de chamar este endpoint, o front-end deve realizar o upload dos arquivos (capa, pacote ZIP e demo de áudio) " +
                    "nos endpoints de upload dedicados e fornecer as respectivas URLs e objetos neste payload. " +
                    "Retorna o status HTTP 201 Created com o cabeçalho 'Location' contendo a URL do recurso criado.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "SoundKit criado com sucesso"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Erro de validação nos dados fornecidos (ex: título ausente, preço negativo, audioDemo inválido)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Requisição não autorizada (token JWT ausente, inválido ou expirado)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<SoundKitResponse> create(
            @Valid @RequestBody CreateSoundKitRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JWTUserData user
    ) {
        SoundKitResponse response = soundKitService.create(request, user);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/my-kits")
    @Operation(
            summary = "Listar SoundKits criados pelo produtor logado",
            description = "Retorna uma lista paginada contendo apenas os SoundKits ativos pertencentes ao produtor atualmente autenticado. " +
                    "Útil para o painel de controle/dashboard do produtor na aplicação React.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Página de SoundKits do criador recuperada com sucesso"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Requisição não autorizada (token JWT ausente ou inválido)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<PageResponse<SoundKitResponse>> listMyKits(
            @Parameter(hidden = true) @AuthenticationPrincipal JWTUserData user,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(soundKitService.listByCreator(user, pageable));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Desativar (excluir logicamente) um SoundKit",
            description = "Desativa um SoundKit específico pelo seu ID. Esta operação executa um soft-delete (marca active = false), " +
                    "removendo o kit do catálogo público. " +
                    "Apenas o próprio produtor que criou o kit ou um usuário com perfil ADMIN têm autorização para executar esta ação.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "SoundKit desativado com sucesso (sem conteúdo no corpo da resposta)"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Requisição não autorizada (token JWT ausente ou expirado)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Acesso proibido (usuário logado não é o dono do kit nem administrador)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "SoundKit não encontrado ou já se encontra inativo",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<Void> delete(
            @Parameter(
                    description = "Identificador único (UUID) do SoundKit a ser desativado",
                    example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
                    required = true
            )
            @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal JWTUserData user
    ) {
        soundKitService.deactivate(id, user);
        return ResponseEntity.noContent().build();
    }
}
