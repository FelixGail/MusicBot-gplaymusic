package com.github.felixgail.musicbot.gplaymusic;

import com.github.felixgail.gplaymusic.api.GPlayMusic;
import com.github.felixgail.gplaymusic.model.Station;
import com.github.felixgail.gplaymusic.model.snippets.StationSeed;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import javax.annotation.Nonnull;
import net.bjoernpetersen.musicbot.api.config.Config;
import net.bjoernpetersen.musicbot.api.config.TextBox;
import net.bjoernpetersen.musicbot.api.player.Song;
import net.bjoernpetersen.musicbot.api.player.SongEntry;
import net.bjoernpetersen.musicbot.spi.plugin.InitializationException;
import net.bjoernpetersen.musicbot.spi.plugin.NoSuchSongException;
import net.bjoernpetersen.musicbot.spi.plugin.Suggester;
import net.bjoernpetersen.musicbot.spi.plugin.management.InitStateWriter;

public class GPlayMusicSuggesterDefault extends GPlayMusicSuggesterBase {

  private final static int recentlyPlayedMaxSize = 200;

  private Station radioStation;
  private Song lastSuggested;
  private List<Song> recentlyPlayedSongs;
  private List<Song> suggestions;
  private Config.StringEntry fallbackSongEntry;
  private Song fallbackSong;

  @Override
  public void initialize(@Nonnull InitStateWriter initStateWriter) throws InitializationException {
    recentlyPlayedSongs = new LinkedList<>();
    suggestions = new LinkedList<>();

    try {
      fallbackSong = getProvider().lookup(fallbackSongEntry.get());
    } catch (NoSuchSongException e) {
      throw new InitializationException("Could not find fallback song", e);
    }

    try {
      createStation(fallbackSong);
    } catch (IOException e) {
      throw new InitializationException("Unable to create Station on song " + fallbackSong, e);
    }
  }

  @Nonnull
  @Override
  public Song suggestNext() {
    List<Song> suggestionList = getNextSuggestions(1);
    lastSuggested = suggestionList.size() > 0 ? suggestionList.get(0) : fallbackSong;
    suggestions.remove(lastSuggested);
    return lastSuggested;
  }

  @Nonnull
  @Override
  public List<Song> getNextSuggestions(int i) {
    while (suggestions.size() < i) {
      try {
        radioStation.getTracks(songsToTracks(recentlyPlayedSongs), true, true)
            .forEach(track -> suggestions.add(getProvider().getSongFromTrack(track)));

      } catch (IOException e) {
        logger.error("IOException while fetching for station songs", e);
      }
    }
    return suggestions.subList(0, i);
  }

  @Override
  public void createStateEntries(@Nonnull Config config) {
  }

  @Nonnull
  @Override
  public List<Config.Entry<?>> createConfigEntries(@Nonnull Config config) {
    fallbackSongEntry = config.new StringEntry(
        "Fallback",
        "ID of a song to build the radio upon",
        value -> {
          if (!value.startsWith("T")) {
            return "Song IDs must start with 'T'";
          }
          return null;
        },
        TextBox.INSTANCE,
        "Tj6fhurtstzgdpvfm4xv6i5cei4"
    );

    return Collections.singletonList(fallbackSongEntry);
  }

  @Nonnull
  @Override
  public List<Config.Entry<?>> createSecretEntries(@Nonnull Config config) {
    return Collections.emptyList();
  }

  @Nonnull
  @Override
  public String getName() {
    return "GPlayMusic DefaultSuggester";
  }

  @Nonnull
  @Override
  public String getDescription() {
    return "Suggest songs from a GPlayMusic station based on the last played song.";
  }

  @Nonnull
  @Override
  public String getSubject() {
    if (fallbackSong == null) {
      return getName();
    } else {
      return "Based on " + fallbackSong.getTitle();
    }
  }

  @Override
  public void close() throws IOException {
    if (radioStation != null) {
      radioStation.delete();
    }
  }

  @Override
  public void notifyPlayed(@Nonnull SongEntry entry) {
    Song song = entry.getSong();
    handleRecentlyPlayed(song);
    try {
      createStation(song);
    } catch (IOException e) {
      System.out
          .printf("Error while creating station on key %s. Using old station.\n%s", song.getId(),
              e);
    }
  }

  @Override
  public void removeSuggestion(@Nonnull Song song) {
    //For now take unliked song from suggestions.
    handleRecentlyPlayed(song);
  }

  @Override
  public void dislike(@Nonnull Song song) {
    Suggester.DefaultImpls.dislike(this, song);
  }

  private void createStation(@Nonnull Song song) throws IOException {
    if (lastSuggested == null || !song.getId().equals(lastSuggested.getId())) {
      GPlayMusic api = getProvider().getAPI();
      Station station = api.getStationApi()
          .create(new StationSeed(
                  api.getTrackApi().getTrack(song.getId())),
              "Station on " + song.getTitle(),
              false);
      fallbackSongEntry.set(song.getId());
      fallbackSong = song;
      if (radioStation != null) {
        radioStation.delete();
      }
      suggestions.clear();
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
    suggestions.remove(song);
  }
}
