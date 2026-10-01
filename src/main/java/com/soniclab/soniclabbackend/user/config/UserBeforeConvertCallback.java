package com.soniclab.soniclabbackend.user.config;

import com.soniclab.soniclabbackend.user.entity.User;
import com.soniclab.soniclabbackend.user.entity.UserId;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

@Component
public class UserBeforeConvertCallback implements BeforeConvertCallback<User> {

    @Override
    public User onBeforeConvert(User user, String collection) {
        if (user.getId() == null) {
            user.setId(new UserId());
        }
        return user;
    }

}
