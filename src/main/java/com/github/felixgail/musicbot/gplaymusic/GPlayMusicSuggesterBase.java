package com.github.felixgail.musicbot.gplaymusic;

import com.github.bjoernpetersen.jmusicbot.InitStateWriter;
import com.github.bjoernpetersen.jmusicbot.InitializationException;
import com.github.bjoernpetersen.jmusicbot.Loggable;
import com.github.bjoernpetersen.jmusicbot.Song;
import com.github.bjoernpetersen.jmusicbot.provider.DependencyMap;
import com.github.bjoernpetersen.jmusicbot.provider.Provider;
import com.github.bjoernpetersen.jmusicbot.provider.Suggester;
import com.github.felixgail.gplaymusic.model.shema.Track;
import com.google.common.collect.Sets;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public abstract class GPlayMusicSuggesterBase implements Suggester, Loggable {

  private GPlayMusicProviderBase provider;

  private Logger logger;

  @Override
  @Nonnull
  public Logger getLogger() {
    if (logger == null) {
      logger = createLogger();
    }
    return logger;
  }

  @Override
  @Nonnull
  public final Set<Class<? extends Provider>> getDependencies() {
    return Sets.union(getChildDependencies(), Collections.singleton(GPlayMusicProviderBase.class));
  }

  public abstract Set<Class<? extends Provider>> getChildDependencies();

  @Override
  public final void initialize(@Nonnull InitStateWriter initStateWriter, @Nonnull DependencyMap<Provider> dependencyMap)
      throws InitializationException, InterruptedException {
    provider = dependencyMap.get(GPlayMusicProviderBase.class);
    initializeChild(initStateWriter, dependencyMap);
  }

  public abstract void initializeChild(@Nonnull InitStateWriter initStateWriter,
                                       @Nonnull DependencyMap<Provider> dependencyMap)
      throws InitializationException, InterruptedException;

  public GPlayMusicProviderBase getProvider() {
    return provider;
  }

  public List<Track> songsToTracks(Collection<Song> songs) {
    return songs.stream()
        .map(song ->
            //As the Track#getID is the only needed attribute, leave other parameters empty.
            new Track(song.getId(), "", "", "", 0, 0,
                0, 0, "", ""))
        .collect(Collectors.toList());
  }
}
