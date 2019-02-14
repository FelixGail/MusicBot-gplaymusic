package com.github.felixgail.musicbot.gplaymusic;

import com.github.felixgail.gplaymusic.model.Track;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import net.bjoernpetersen.musicbot.api.player.Song;
import net.bjoernpetersen.musicbot.api.plugin.IdBase;
import net.bjoernpetersen.musicbot.spi.plugin.Suggester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@IdBase(displayName = "GPlayMusic last played station")
public abstract class GPlayMusicSuggesterBase implements Suggester {

  @Inject
  private GPlayMusicProviderBase provider;

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  protected GPlayMusicProviderBase getProvider() {
    return provider;
  }

  public List<Track> songsToTracks(Collection<Song> songs) {
    return songs.stream()
        .map(song -> {
          try {
            return getProvider().getAPI().getTrackApi().getTrack(song.getId());
          } catch (IOException e) {
            logger.warn("Error while fetching track.", e);
            return null;
          }
        })
        .collect(Collectors.toList());
  }
}
