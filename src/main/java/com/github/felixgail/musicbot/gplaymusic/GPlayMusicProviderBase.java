package com.github.felixgail.musicbot.gplaymusic;

import com.github.felixgail.gplaymusic.api.GPlayMusic;
import com.github.felixgail.gplaymusic.model.Track;
import javax.annotation.Nonnull;
import net.bjoernpetersen.musicbot.api.player.Song;
import net.bjoernpetersen.musicbot.api.plugin.IdBase;
import net.bjoernpetersen.musicbot.spi.plugin.Provider;

@IdBase(displayName = "Google Play Music")
public abstract class GPlayMusicProviderBase implements Provider {

    public abstract GPlayMusic getAPI();

    public Song getSongFromTrack(Track track) {
        String albumArtUrl = null;
        if (track.getAlbumArtRef().isPresent()) {
            albumArtUrl = track.getAlbumArtRef().get().get(0).getUrl();
        }
        return getSongFromInfo(track.getID(), track.getTitle(), track.getArtist(),
            Math.toIntExact(track.getDurationMillis() / 1000), albumArtUrl);
    }

    public Song getSongFromInfo(@Nonnull String id, @Nonnull String title,
        @Nonnull String description, int duration, String albumArtUrl) {
        return new Song(id, this, title, description, duration, albumArtUrl);
    }
}
