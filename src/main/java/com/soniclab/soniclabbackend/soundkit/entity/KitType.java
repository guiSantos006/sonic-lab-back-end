package com.soniclab.soniclabbackend.soundkit.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = "Tipo de SoundKit disponível no sistema: " +
                "DRUM_KIT (baterias, 808s e percussões), " +
                "PRESET_BANK (bancos de timbres para sintetizadores como Serum/Vital), " +
                "SAMPLE_PACK (loops melódicos e samples de áudio), " +
                "MIDI_KIT (progressões de acordes e arquivos MIDI)"
)
public enum KitType {

    DRUM_KIT,
    PRESET_BANK,
    SAMPLE_PACK,
    MIDI_KIT

}
