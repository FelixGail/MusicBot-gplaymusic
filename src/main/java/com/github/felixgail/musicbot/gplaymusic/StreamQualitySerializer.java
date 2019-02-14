package com.github.felixgail.musicbot.gplaymusic;

import com.github.felixgail.gplaymusic.model.enums.StreamQuality;
import net.bjoernpetersen.musicbot.api.config.ConfigSerializer;
import net.bjoernpetersen.musicbot.api.config.SerializationException;
import org.jetbrains.annotations.NotNull;

final class StreamQualitySerializer implements ConfigSerializer<StreamQuality> {

    @Override
    public StreamQuality deserialize(@NotNull String s) throws SerializationException {
        try {
            return StreamQuality.valueOf(s);
        } catch (IllegalArgumentException e) {
            throw new SerializationException();
        }
    }

    @NotNull
    @Override
    public String serialize(StreamQuality streamQuality) {
        return streamQuality.name();
    }
}
