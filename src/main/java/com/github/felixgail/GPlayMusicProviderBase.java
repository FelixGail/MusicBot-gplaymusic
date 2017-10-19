package com.github.felixgail;

import com.github.bjoernpetersen.jmusicbot.InitStateWriter;
import com.github.bjoernpetersen.jmusicbot.InitializationException;
import com.github.bjoernpetersen.jmusicbot.Loggable;
import com.github.bjoernpetersen.jmusicbot.PlaybackFactoryManager;
import com.github.bjoernpetersen.jmusicbot.Song;
import com.github.bjoernpetersen.jmusicbot.provider.Provider;
import com.github.felixgail.gplaymusic.api.GPlayMusic;
import com.github.felixgail.gplaymusic.model.shema.Track;

import javax.annotation.Nonnull;
import java.util.logging.Logger;

public abstract class GPlayMusicProviderBase implements Provider, Loggable{
  private Song.Builder songBuilder;
  private Logger logger;

  @Override
  @Nonnull
  public Logger getLogger() {
    if(logger == null) {
      logger = createLogger();
    }
    return logger;
  }

  public abstract GPlayMusic getAPI();

  @Override
  public final void initialize(@Nonnull InitStateWriter initStateWriter,
                         @Nonnull PlaybackFactoryManager manager) throws InitializationException {
    this.songBuilder = initializeChild(initStateWriter, manager);
  }

  public abstract Song.Builder initializeChild(@Nonnull InitStateWriter initStateWriter,
                                               @Nonnull PlaybackFactoryManager manager)
      throws InitializationException;

  public Song getSongFromTrack(Track track) {
    songBuilder.id(track.getID())
        .title(track.getTitle())
        .description(track.getArtist())
        .duration(Math.toIntExact(track.getDurationMillis() / 1000));
    if (track.getAlbumArtRef().isPresent()) {
      songBuilder.albumArtUrl(track.getAlbumArtRef().get().get(0).getUrl());
    } else {
      songBuilder.albumArtUrl(null);
    }
    return songBuilder.build();
  }

}
