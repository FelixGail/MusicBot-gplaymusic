package com.github.felixgail.musicbot.gplaymusic

import com.github.felixgail.gplaymusic.model.enums.StreamQuality
import net.bjoernpetersen.musicbot.api.config.ConfigSerializer
import net.bjoernpetersen.musicbot.api.config.SerializationException

internal class StreamQualitySerializer : ConfigSerializer<StreamQuality> {
    @Throws(SerializationException::class)
    override fun deserialize(string: String): StreamQuality {
        try {
            return StreamQuality.valueOf(string)
        } catch (e: IllegalArgumentException) {
            throw SerializationException()
        }
    }

    override fun serialize(obj: StreamQuality): String {
        return obj.name
    }
}
