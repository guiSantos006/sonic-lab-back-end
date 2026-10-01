package com.soniclab.soniclabbackend.user.config;

import com.soniclab.soniclabbackend.config.MongoConverter;
import com.soniclab.soniclabbackend.user.entity.UserId;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.stereotype.Component;

import java.util.UUID;

public class UserMongoConverters {

    @Component
    @WritingConverter
    public static class UserIdToUuidConverter implements MongoConverter<UserId, UUID> {
        @Override
        public UUID convert(UserId source) {
            return source.uuid();
        }
    }

    @Component
    @ReadingConverter
    public static class UuidToUserIdConverter implements MongoConverter<UUID, UserId> {
        @Override
        public UserId convert(UUID source) {
            return new UserId(source);
        }
    }
}
