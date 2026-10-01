package com.soniclab.soniclabbackend.soundkit.config;

import com.soniclab.soniclabbackend.soundkit.entity.SoundKit;
import com.soniclab.soniclabbackend.soundkit.entity.SoundKitId;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

@Component
public class SoundKitBeforeConvertCallback implements BeforeConvertCallback<SoundKit> {

    @Override
    public SoundKit onBeforeConvert(SoundKit soundKit, String collection) {
        if (soundKit.getId() == null) {
            soundKit.setId(new SoundKitId());
        }
        return soundKit;
    }
}
