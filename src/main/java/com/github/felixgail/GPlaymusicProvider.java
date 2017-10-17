package com.github.felixgail;

import com.github.bjoernpetersen.jmusicbot.InitStateWriter;
import com.github.bjoernpetersen.jmusicbot.InitializationException;
import com.github.bjoernpetersen.jmusicbot.Loggable;
import com.github.bjoernpetersen.jmusicbot.PlaybackFactoryManager;
import com.github.bjoernpetersen.jmusicbot.Song;
import com.github.bjoernpetersen.jmusicbot.config.Config;
import com.github.bjoernpetersen.jmusicbot.config.ui.ChoiceBox;
import com.github.bjoernpetersen.jmusicbot.config.ui.DefaultStringConverter;
import com.github.bjoernpetersen.jmusicbot.config.ui.FileChooserButton;
import com.github.bjoernpetersen.jmusicbot.config.ui.PasswordBox;
import com.github.bjoernpetersen.jmusicbot.config.ui.StringChoice;
import com.github.bjoernpetersen.jmusicbot.config.ui.TextBox;
import com.github.bjoernpetersen.jmusicbot.platform.Platform;
import com.github.bjoernpetersen.jmusicbot.platform.Support;
import com.github.bjoernpetersen.jmusicbot.playback.PlaybackFactory;
import com.github.bjoernpetersen.jmusicbot.provider.NoSuchSongException;
import com.github.bjoernpetersen.jmusicbot.provider.Provider;
import com.github.bjoernpetersen.mp3Playback.Mp3PlaybackFactory;
import com.github.felixgail.gplaymusic.api.GPlayMusic;
import com.github.felixgail.gplaymusic.api.TokenProvider;
import com.github.felixgail.gplaymusic.model.enums.StreamQuality;
import com.github.felixgail.gplaymusic.model.shema.Track;
import svarzee.gps.gpsoauth.AuthToken;
import svarzee.gps.gpsoauth.Gpsoauth;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GPlaymusicProvider implements Loggable, Provider {

  private Config.StringEntry username;
  private Config.StringEntry password;
  private Config.StringEntry androidID;
  private Config.StringEntry token;
  private Config.StringEntry fileDir;
  private Config.StringEntry streamQuality;
  private List<Config.StringEntry> configEntries;
  private Mp3PlaybackFactory playbackFactory;
  private GPlayMusic api;

  private Song.Builder songBuilder;
  private Map<String, Song> cachedSongs;

  @Nonnull
  @Override
  public Class<? extends Provider> getBaseClass() {
    return GPlaymusicProvider.class;
  }

  @Nonnull
  @Override
  public Support getSupport(@Nonnull Platform platform) {
    switch (platform) {
      case ANDROID:
      case LINUX:
      case WINDOWS:
        return Support.YES;
      case UNKNOWN:
      default:
        return Support.MAYBE;
    }
  }

  @Nonnull
  @Override
  public List<? extends Config.Entry> initializeConfigEntries(@Nonnull Config config) {
    username = config.new StringEntry(
        getClass(),
        "Username",
        "Username or Email of your google account with AllAccess subscription",
        false, // not a secret
        null,
        new TextBox(),
        value -> {
          // check for valid value
          if (!value.isEmpty()) {
            return null;
          } else {
            return "Value is required.";
          }
        }
    );

    password = config.new StringEntry(
        getClass(),
        "Password",
        "Password/App password of your google account",
        true,
        null,
        new PasswordBox(),
        value -> {
          // check for valid value
          if (!value.isEmpty()) {
            return null;
          } else {
            return "Value is required.";
          }
        }
    );

    androidID = config.new StringEntry(
        getClass(),
        "Android ID",
        "IMEI or GoogleID of your smartphone with GooglePlayMusic installed",
        true,
        null,
        new TextBox(),
        value -> {
          // check for valid value
          if (!value.isEmpty()) {
            return null;
          } else {
            return "Value is required.";
          }
        }
    );

    fileDir = config.new StringEntry(
        getClass(),
        "Song Directory",
        "Directory in which songs will be temprorarily saved.",
        false,
        "songs/",
        new FileChooserButton(true),
        value -> {
          File file = new File(value);
          if (file.getParentFile().exists() && (!file.exists() || (file.isDirectory() && file.list().length == 0))) {
            return null;
          } else {
            return "Value has to be an empty directory or not existing while having a parent directory.";
          }
        }
    );

    streamQuality = config.new StringEntry(
        getClass(),
        "Quality",
        "Sets the quality in which the songs are streamed",
        false,
        "HIGH",
        new ChoiceBox<>(() -> Stream.of("LOW", "MEDIUM", "HIGH")
            .map(s -> new StringChoice(s, s))
            .collect(Collectors.toList()),
            DefaultStringConverter.INSTANCE, false),
        value -> {
          try {
            StreamQuality.valueOf(value);
          } catch (IllegalArgumentException e) {
            return "Value has to be LOW, MEDIUM or HIGH";
          }
          return null;
        }
    );

    token = config.new StringEntry(
        getClass(),
        "Token",
        "Authtoken",
        true,
        null,
        new TextBox(),
        value -> {
          // check for valid value
          if (!value.isEmpty()) {
            return null;
          } else {
            return "Value is required.";
          }
        }
    );

    configEntries = Arrays.asList(username, password, androidID, fileDir, streamQuality);
    return configEntries;
  }

  @Nonnull
  @Override
  public List<? extends Config.Entry> getMissingConfigEntries() {
    return configEntries.stream().filter(entry -> entry.checkError() != null).collect(Collectors.toList());
  }

  @Override
  public void destructConfigEntries() {
    configEntries.forEach(stringEntry -> {
      stringEntry.destruct();
      stringEntry = null;
    });
    token.destruct();
    token = null;
  }

  @Override
  public Set<Class<? extends PlaybackFactory>> getPlaybackDependencies() {
    return Collections.singleton(Mp3PlaybackFactory.class);
  }

  @Override
  public void initialize(@Nonnull InitStateWriter initStateWriter,
                         @Nonnull PlaybackFactoryManager manager) throws InitializationException {
    initStateWriter.state("Initializing...");
    playbackFactory = manager.getFactory(Mp3PlaybackFactory.class);
    cachedSongs = new HashMap<>();

    File songDir = new File(fileDir.getValue());
    if (!songDir.exists()) {
      if (!songDir.mkdir()) {
        throw new InitializationException("Unable to create song directory");
      }
    }

    songBuilder = new Song.Builder()
        .songLoader(new GPlayMusicSongLoader(StreamQuality.valueOf(streamQuality.getValue()), fileDir.getValue()))
        .playbackSupplier(new GPlayMusicPlaybackSupplier(fileDir.getValue(), playbackFactory))
        .provider(this);

    try {
      loginToService(initStateWriter);
    } catch (IOException | Gpsoauth.TokenRequestFailed e) {
      initStateWriter.warning("Logging into GPlayMusic failed!");
      throw new InitializationException(e);
    }
  }

  private void loginToService(@Nonnull InitStateWriter initStateWriter) throws IOException, Gpsoauth.TokenRequestFailed {
    AuthToken authToken = null;
    boolean existingToken = false;
    if (token.checkError() == null) {
      authToken = TokenProvider.provideToken(token.getValue());
      existingToken = true;
      initStateWriter.state("Trying to login with existing token.");
    } else {
      initStateWriter.state("Fetching new token.");
      authToken = TokenProvider.provideToken(username.getValue(), password.getValue(), androidID.getValue());
      token.set(authToken.getToken());
    }
    try {
      api = new GPlayMusic.Builder().setAuthToken(authToken).build();
    } catch (com.github.felixgail.gplaymusic.api.exceptions.InitializationException e) {
      if (existingToken) {
        token.set(null);
        loginToService(initStateWriter);
      } else {
        throw e;
      }
    }
  }

  @Override
  public void close() throws IOException {
    playbackFactory = null;
    api = null;
    songBuilder = null;
    cachedSongs = null;

    Files.walk(Paths.get(fileDir.getValue()))
        .map(Path::toFile)
        .sorted((o1, o2) -> -o1.compareTo(o2))
        .forEach(File::delete);
  }

  @Nonnull
  @Override
  public List<Song> search(@Nonnull String query) {
    try {
      return api.searchTracks(query, 30).stream()
          .map(this::getSongFromTrack)
          .peek(song -> cachedSongs.put(song.getId(), song))
          .collect(Collectors.toList());
    } catch (IOException e) {
      logWarning(e, "Exception while searching with query '%s'", query);
      return Collections.emptyList();
    }
  }

  private Song getSongFromTrack(Track track) {
    songBuilder.id(track.getID())
        .title(track.getTitle())
        .description(track.getArtist())
        .duration(Integer.valueOf(track.getDurationMillis()) / 1000);
    if (track.getAlbumArtRef().isPresent()) {
      songBuilder.albumArtUrl(track.getAlbumArtRef().get().get(0).getUrl());
    } else {
      songBuilder.albumArtUrl(null);
    }
    return songBuilder.build();
  }

  @Nonnull
  @Override
  public Song lookup(@Nonnull String songId) throws NoSuchSongException {
    if (cachedSongs.containsKey(songId)) {
      return cachedSongs.get(songId);
    } else {
      try {
        Song song = getSongFromTrack(Track.getTrack(songId));
        cachedSongs.put(song.getId(), song);
        return song;
      } catch (IOException e) {
        throw new NoSuchSongException(e);
      }
    }
  }

  @Nonnull
  @Override
  public String getId() {
    return "gplaymusic";
  }

  @Nonnull
  @Override
  public String getReadableName() {
    return "GPlayMusic Songs";
  }
}
