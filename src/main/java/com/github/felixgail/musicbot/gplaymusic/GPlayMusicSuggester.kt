package com.github.felixgail.musicbot.gplaymusic

import com.github.felixgail.gplaymusic.model.Track
import net.bjoernpetersen.musicbot.api.player.Song
import net.bjoernpetersen.musicbot.api.plugin.IdBase
import net.bjoernpetersen.musicbot.spi.plugin.Suggester
import org.slf4j.LoggerFactory
import java.io.IOException
import javax.inject.Inject

@IdBase(displayName = "GPlayMusic last played station")
abstract class GPlayMusicSuggester : Suggester {

    @Inject
    protected lateinit var provider: GPlayMusicProvider

    protected val logger = LoggerFactory.getLogger(javaClass)!!

    fun songsToTracks(songs: Collection<Song>): List<Track> {
        return songs.mapNotNull { (id) ->
            try {
                provider.api.trackApi.getTrack(id)
            } catch (e: IOException) {
                logger.warn("Error while fetching track.", e)
                null
            }
        }
    }
}
