package com.github.felixgail.musicbot.gplaymusic;

import com.github.bjoernpetersen.jmusicbot.InitStateWriter;
import com.github.bjoernpetersen.jmusicbot.InitializationException;
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
import com.github.felixgail.gplaymusic.exceptions.NetworkException;
import com.github.felixgail.gplaymusic.model.Track;
import com.github.felixgail.gplaymusic.model.enums.StreamQuality;
import com.github.felixgail.gplaymusic.util.TokenProvider;
import com.github.zafarkhaja.semver.Version;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import svarzee.gps.gpsoauth.AuthToken;
import svarzee.gps.gpsoauth.Gpsoauth;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GPlayMusicProvider extends GPlayMusicProviderBase {

  private final static long tokenCooldownMillis = 60000;

  private Config.StringEntry username;
  private Config.StringEntry password;
  private Config.StringEntry androidID;
  private Config.StringEntry token;
  private Config.StringEntry fileDir;
  private Config.StringEntry streamQuality;
  private Config.StringEntry cacheTime;
  private List<Config.StringEntry> configEntries;
  private Mp3PlaybackFactory playbackFactory;
  private GPlayMusic api;
  private Song.Builder songBuilder;

  private LoadingCache<String, Song> cachedSongs;

  @Nonnull
  @Override
  public Class<? extends Provider> getBaseClass() {
    return GPlayMusicProviderBase.class;
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
          if (file.getParentFile() != null &&
              (file.getParentFile().exists() && (!file.exists() || (file.isDirectory() && file.listFiles().length == 0)))) {
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

    cacheTime = config.new StringEntry(
        getClass(),
        "Cache Time",
        "Duration in Minutes until cached songs will be deleted.",
        false,
        "60",
        new TextBox(),
        value -> {
          try {
            Integer.parseInt(value);
          } catch (NumberFormatException e) {
            return String.format("Value is either higher than %d or not a number", Integer.MAX_VALUE);
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


    configEntries = Arrays.asList(username, password, androidID, fileDir, streamQuality, cacheTime);
    return configEntries;
  }

  @Nonnull
  @Override
  public List<? extends Config.Entry> getMissingConfigEntries() {
    return configEntries.stream().filter(Config.StringEntry::isNullOrError).collect(Collectors.toList());
  }

  @Override
  public void destructConfigEntries() {
    username.destruct();
    username = null;
    password.destruct();
    password = null;
    androidID.destruct();
    androidID = null;
    fileDir.destruct();
    fileDir = null;
    streamQuality.destruct();
    streamQuality = null;
    cacheTime.destruct();
    cacheTime = null;
    token.destruct();
    token = null;
    configEntries = null;
  }

  @Override
  public Set<Class<? extends PlaybackFactory>> getPlaybackDependencies() {
    return Collections.singleton(Mp3PlaybackFactory.class);
  }

  @Override
  public GPlayMusic getAPI() {
    return api;
  }

  @Override
  public Song.Builder initializeChild(@Nonnull InitStateWriter initStateWriter,
                                      @Nonnull PlaybackFactoryManager manager) throws InitializationException {
    initStateWriter.state("Initializing...");
    playbackFactory = manager.getFactory(Mp3PlaybackFactory.class);
    RemovalListener<String, Song> removalListener = removalNotification -> {
      Song song = removalNotification.getValue();
      try {
        logFine("Removing song with id '%s' from cache.", song.getId());
        Files.deleteIfExists(Paths.get(fileDir.getValue(), song.getId() + ".mp3"));
      } catch (IOException e) {
        logWarning(e, "IOException while removing song '%s (%s)'", song.getTitle(), song.getId());
      }
    };
    cachedSongs = CacheBuilder.newBuilder()
        .expireAfterAccess(Integer.parseInt(cacheTime.getValue()), TimeUnit.MINUTES)
        .initialCapacity(256)
        .maximumSize(1024)
        .build(new CacheLoader<String, Song>() {
          @Override
          public Song load(@Nonnull String key) throws Exception {
            logFine("Adding song with id '%s' to cache.", key);
            return getSongFromTrack(getAPI().getTrackApi().getTrack(key));
          }
        });

    File songDir = new File(fileDir.getValue());
    if (!songDir.exists()) {
      if (!songDir.mkdir()) {
        throw new InitializationException("Unable to create song directory");
      }
    }

    songBuilder = new Song.Builder()
        .songLoader(new GPlayMusicSongLoader(StreamQuality.valueOf(streamQuality.getValue()), fileDir.getValue(), this))
        .playbackSupplier(new GPlayMusicPlaybackSupplier(fileDir.getValue(), playbackFactory))
        .provider(this);

    try {
      loginToService(initStateWriter);
    } catch (IOException | Gpsoauth.TokenRequestFailed e) {
      initStateWriter.warning("Logging into GPlayMusic failed!");
      throw new InitializationException(e);
    }
    return songBuilder;
  }

  private void loginToService(@Nonnull InitStateWriter initStateWriter) throws IOException, Gpsoauth.TokenRequestFailed {
    AuthToken authToken = null;
    boolean existingToken = false;
    if (token.getValue() != null && token.checkError() == null) {
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
    } catch (com.github.felixgail.gplaymusic.exceptions.InitializationException e) {
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
    deleteDir(new File(fileDir.getValue()));
  }

  private void deleteDir(File file) {
    File[] contents = file.listFiles();
    if (contents != null) {
      for (File f : contents) {
        deleteDir(f);
      }
    }
    file.delete();
  }

  @Nonnull
  @Override
  public List<Song> search(@Nonnull String query) {
    try {
      return api.getTrackApi().search(query, 30).stream()
          .map(this::getSongFromTrack)
          .peek(song -> cachedSongs.put(song.getId(), song))
          .collect(Collectors.toList());
    } catch (IOException e) {
      if (e instanceof NetworkException && ((NetworkException) e).getCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
        if (generateNewToken()) {
          return search(query);
        }
      } else {
        logWarning(e, "Exception while searching with query '%s'", query);

      }
    }
    return Collections.emptyList();
  }


  @Nonnull
  @Override
  public Song lookup(@Nonnull String songId) throws NoSuchSongException {
    try {
      return cachedSongs.get(songId);
    } catch (ExecutionException e) {
      throw new NoSuchSongException(e);
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

  @Nonnull
  @Override
  public Version getMinSupportedVersion() {
    return Version.forIntegers(0, 11, 0);
  }

  private Boolean generateNewToken() {
    long diffLastRequest = System.currentTimeMillis() - TokenProvider.getLastTokenFetched();
    if (diffLastRequest > tokenCooldownMillis) {
      logInfo("Authorization expired. Requesting new token.");
      try {
        api.changeToken(TokenProvider.provideToken(username.getValue(), password.getValue(), androidID.getValue()));
        return true;
      } catch (Gpsoauth.TokenRequestFailed | IOException e) {
        logSevere(e, "Exception while trying to generate new token. Unable to authenticate client.");
      }
    } else {
      logInfo("Token request on cooldown. Please wait %d seconds.", diffLastRequest * 1000);
    }
    return false;
  }
}
