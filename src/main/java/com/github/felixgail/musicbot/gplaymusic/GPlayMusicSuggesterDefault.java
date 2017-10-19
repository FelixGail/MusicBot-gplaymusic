package com.github.felixgail.musicbot.gplaymusic;

import com.github.bjoernpetersen.jmusicbot.InitStateWriter;
import com.github.bjoernpetersen.jmusicbot.InitializationException;
import com.github.bjoernpetersen.jmusicbot.Song;
import com.github.bjoernpetersen.jmusicbot.config.Config;
import com.github.bjoernpetersen.jmusicbot.config.ui.TextBox;
import com.github.bjoernpetersen.jmusicbot.platform.Platform;
import com.github.bjoernpetersen.jmusicbot.platform.Support;
import com.github.bjoernpetersen.jmusicbot.provider.DependencyMap;
import com.github.bjoernpetersen.jmusicbot.provider.Provider;
import com.github.felixgail.gplaymusic.model.shema.Station;
import com.github.felixgail.gplaymusic.model.shema.Track;
import com.github.felixgail.gplaymusic.model.shema.snippets.StationSeed;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class GPlayMusicSuggesterDefault extends GPlayMusicSuggesterBase {
  private final static int recentlyPlayedMaxSize = 200;

  private Station radioStation;
  private Song lastSuggested;
  private List<Song> recentlyPlayedSongs;
  private Config.StringEntry fallbackSong;

  @Override
  public Set<Class<? extends Provider>> getChildDependencies() {
    return Collections.emptySet();
  }

  @Override
  public void initializeChild(@Nonnull InitStateWriter initStateWriter, @Nonnull DependencyMap<Provider> dependencyMap)
      throws InitializationException, InterruptedException {
    recentlyPlayedSongs = new LinkedList<>();
    try {
      createStation(fallbackSong.getValue());
    } catch (IOException e) {
      throw new InitializationException("Unable to create Station on song " + fallbackSong.getValue(), e);
    }
  }

  @Nonnull
  @Override
  public Song suggestNext() {
    List<Song> suggestionList = getNextSuggestions(1);
    lastSuggested = suggestionList.size() > 0 ? suggestionList.get(0) :
        getProvider()
            .getSongFromInfo(fallbackSong.getValue(), "Fallback Song", "Unknown", 0, null);
    return lastSuggested;
  }

  @Nonnull
  @Override
  public List<Song> getNextSuggestions(int i) {
    List<Song> songsToReturn = new LinkedList<>();
    while (songsToReturn.size() < i) {
      try {
        radioStation.getTracks(songsToTracks(recentlyPlayedSongs), true, true)
            .forEach(track -> handleRespondedTrack(songsToReturn, i, track));

      } catch (IOException e) {
        logSevere(e, "IOException while fetching for station songs");
      }
    }
    return songsToReturn;
  }

  private void handleRespondedTrack(Collection<Song> songs, int max, Track track) {
    if (songs.size() <= max) {
      Song song = getProvider().getSongFromTrack(track);
      handleRecentlyPlayed(song);
      songs.add(song);
    }
  }

  @Nonnull
  @Override
  public String getId() {
    return "gplaymusicdefaultuggester";
  }

  @Nonnull
  @Override
  public List<? extends Config.Entry> initializeConfigEntries(@Nonnull Config config) {
    fallbackSong = config.new StringEntry(
        getClass(),
        "Fallback",
        "ID of a song to build the radio upon",
        false,
        "Tj6fhurtstzgdpvfm4xv6i5cei4",
        new TextBox(),
        value -> {
          if (!value.startsWith("T")) {
            fallbackSong.set("Tj6fhurtstzgdpvfm4xv6i5cei4");
          }
          return null;
        }
    );
    return Collections.singletonList(fallbackSong);
  }

  @Override
  public void destructConfigEntries() {
    fallbackSong.destruct();
    fallbackSong = null;
  }

  @Nonnull
  @Override
  public List<? extends Config.Entry> getMissingConfigEntries() {
    if (fallbackSong.getValue() == null || fallbackSong.checkError() != null) {
      return Collections.singletonList(fallbackSong);
    }
    return Collections.emptyList();
  }

  @Nonnull
  @Override
  public String getReadableName() {
    return "GPlayMusic DefaultSuggester";
  }

  @Nonnull
  @Override
  public Support getSupport(@Nonnull Platform platform) {
    return Support.YES;
  }

  @Override
  public void close() throws IOException {
    if (radioStation != null) {
      radioStation.delete();
    }
  }

  @Override
  public void notifyPlayed(@Nonnull Song song) {
    handleRecentlyPlayed(song);
    try {
      createStation(song.getId());
    } catch (IOException e) {
      logWarning(e, "Error while creating station on key %s. Using old station.", song.getId());
    }
  }

  @Override
  public void removeSuggestion(@Nonnull Song song) {
    //For now take unliked song from suggestions.
    handleRecentlyPlayed(song);
  }

  private void createStation(@Nonnull String songID) throws IOException {
    if (lastSuggested == null || !songID.equals(lastSuggested.getId())) {
      Station station = Station
          .create(new StationSeed(Track.getTrack(songID)), "Station on " + songID, false);
      fallbackSong.set(songID);
      if (radioStation != null) {
        radioStation.delete();
      }
      radioStation = station;
    }
  }

  private void handleRecentlyPlayed(Song song) {
    if (recentlyPlayedSongs.size() >= recentlyPlayedMaxSize) {
      recentlyPlayedSongs.remove(0);
    }
    if (recentlyPlayedSongs.stream().noneMatch(s -> s.getId().equals(song.getId()))) {
      recentlyPlayedSongs.add(song);
    }
  }
}
