package com.soniclab.soniclabbackend.soundkit.storage;

/**
 * Classificação de visibilidade para arquivos gerenciados pelo storage.
 * - PUBLIC: Arquivos públicos que possuem URL permanente (ex.: capas de SoundKits, áudios de demonstração).
 * - PRIVATE: Arquivos restritos acessíveis exclusivamente via URLs pré-assinadas temporárias (ex.: pacotes de áudio ZIP).
 */
public enum FileVisibility {
    PUBLIC,
    PRIVATE
}
