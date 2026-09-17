package com.soniclab.soniclabbackend.user.config;

import com.soniclab.soniclabbackend.user.entity.UserId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.util.List;
import java.util.UUID;

@Configuration
public class MongoConvertersConfig {

    @Bean
    public MongoCustomConversions customConversions() {
        return new MongoCustomConversions(List.of(
                new UserIdToUuidConverter(),
                new UuidToUserIdConverter()
        ));
    }

    @WritingConverter
    public static class UserIdToUuidConverter implements Converter<UserId, UUID> {
        @Override
        public UUID convert(UserId source) {
            return source.uuid();
        }
    }

    @ReadingConverter
    public static class UuidToUserIdConverter implements Converter<UUID, UserId> {
        @Override
        public UserId convert(UUID source) {
            return new UserId(source);
        }
    }
}
