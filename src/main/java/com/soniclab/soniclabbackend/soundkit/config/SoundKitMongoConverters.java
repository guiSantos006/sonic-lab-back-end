package com.soniclab.soniclabbackend.soundkit.config;

import com.soniclab.soniclabbackend.config.MongoConverter;
import com.soniclab.soniclabbackend.soundkit.entity.SoundKitId;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.stereotype.Component;

import java.util.UUID;

public class SoundKitMongoConverters {

    @Component
    @WritingConverter
    public static class SoundKitIdToUuidConverter implements MongoConverter<SoundKitId, UUID> {
        @Override
        public UUID convert(SoundKitId source) {
            return source.uuid();
        }
    }

    @Component
    @ReadingConverter
    public static class UuidToSoundKitIdConverter implements MongoConverter<UUID, SoundKitId> {
        @Override
        public SoundKitId convert(UUID source) {
            return new SoundKitId(source);
        }
    }
}
