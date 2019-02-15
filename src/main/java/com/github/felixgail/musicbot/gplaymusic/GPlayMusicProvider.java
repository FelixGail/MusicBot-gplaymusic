package com.github.felixgail.musicbot.gplaymusic;

import com.github.felixgail.gplaymusic.api.GPlayMusic;
import com.github.felixgail.gplaymusic.exceptions.NetworkException;
import com.github.felixgail.gplaymusic.model.Track;
import com.github.felixgail.gplaymusic.model.enums.StreamQuality;
import com.github.felixgail.gplaymusic.util.TokenProvider;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import net.bjoernpetersen.musicbot.api.config.ChoiceBox;
import net.bjoernpetersen.musicbot.api.config.Config;
import net.bjoernpetersen.musicbot.api.config.IntSerializer;
import net.bjoernpetersen.musicbot.api.config.NonnullConfigChecker;
import net.bjoernpetersen.musicbot.api.config.NumberBox;
import net.bjoernpetersen.musicbot.api.config.PasswordBox;
import net.bjoernpetersen.musicbot.api.config.TextBox;
import net.bjoernpetersen.musicbot.api.loader.FileResource;
import net.bjoernpetersen.musicbot.api.loader.SongLoadingException;
import net.bjoernpetersen.musicbot.api.player.Song;
import net.bjoernpetersen.musicbot.spi.loader.Resource;
import net.bjoernpetersen.musicbot.spi.plugin.InitializationException;
import net.bjoernpetersen.musicbot.spi.plugin.NoSuchSongException;
import net.bjoernpetersen.musicbot.spi.plugin.Playback;
import net.bjoernpetersen.musicbot.spi.plugin.management.InitStateWriter;
import net.bjoernpetersen.musicbot.spi.plugin.predefined.Mp3PlaybackFactory;
import net.bjoernpetersen.musicbot.spi.plugin.predefined.UnsupportedAudioFileException;
import net.bjoernpetersen.musicbot.spi.util.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import svarzee.gps.gpsoauth.AuthToken;
import svarzee.gps.gpsoauth.Gpsoauth;

public class GPlayMusicProvider extends GPlayMusicProviderBase {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final static long tokenCooldownMillis = 60000;

  private Config.StringEntry username;
  private Config.StringEntry password;
  private Config.StringEntry androidID;
  private Config.StringEntry token;
  private Config.SerializedEntry<StreamQuality> streamQuality;
  private Config.SerializedEntry<Integer> cacheTime;
  @Inject
  private FileStorage fileStorage;
  private File fileDir;
  @Inject
  private Mp3PlaybackFactory playbackFactory;
  private GPlayMusic api;

  private LoadingCache<String, Song> cachedSongs;

  @Nonnull
  @Override
  public String getName() {
    return "GPlayMusic";
  }

  @Nonnull
  @Override
  public String getDescription() {
    return "Provides songs from Google Play Music";
  }

  @Override
  public void createStateEntries(@Nonnull Config config) {
  }

  @Nonnull
  @Override
  public List<Config.Entry<?>> createConfigEntries(@Nonnull Config config) {
    username = config.new StringEntry(
        "Username",
        "Username or Email of your Google account with AllAccess subscription",
        NonnullConfigChecker.INSTANCE,
        TextBox.INSTANCE
    );

    streamQuality = config.new SerializedEntry<>(
        "Quality",
        "Sets the quality in which the songs are streamed",
        new StreamQualitySerializer(),
        value -> null,
        new ChoiceBox<>(
            Enum::name,
            () -> Arrays.asList(StreamQuality.values()),
            false),
        StreamQuality.HIGH
    );

    cacheTime = config.new SerializedEntry<>(
        "Cache Time",
        "Duration in Minutes until cached songs will be deleted.",
        IntSerializer.INSTANCE,
        (value) -> null,
        new NumberBox(1, 3600),
        60
    );

    return ImmutableList.of(username, streamQuality, cacheTime);
  }

  @Nonnull
  @Override
  public List<Config.Entry<?>> createSecretEntries(@Nonnull Config config) {
    password = config.new StringEntry(
        "Password",
        "Password/App password of your Google account",
        NonnullConfigChecker.INSTANCE,
        PasswordBox.INSTANCE
    );

    androidID = config.new StringEntry(
        "Android ID",
        "IMEI or GoogleID of your smartphone with GooglePlayMusic installed",
        NonnullConfigChecker.INSTANCE,
        TextBox.INSTANCE
    );

    token = config.new StringEntry(
        "Token",
        "Authtoken",
        NonnullConfigChecker.INSTANCE,
        TextBox.INSTANCE
    );

    return ImmutableList.of(password, androidID, token);
  }

  @Override
  public GPlayMusic getAPI() {
    return api;
  }

  @Override
  public void initialize(@Nonnull InitStateWriter initStateWriter) throws InitializationException {
    initStateWriter.state("Obtaining storage dir");
    fileDir = new File(fileStorage.forPlugin(this, true), "songs/");

    initStateWriter.state("Creating cache");
    cachedSongs = CacheBuilder.newBuilder()
        .expireAfterAccess(cacheTime.get(), TimeUnit.MINUTES)
        .initialCapacity(256)
        .maximumSize(1024)
        .build(new CacheLoader<String, Song>() {
          @Override
          public Song load(@Nonnull String key) throws Exception {
            logger.debug("Adding song with id '%s' to cache.", key);
            return getSongFromTrack(getAPI().getTrackApi().getTrack(key));
          }
        });

    File songDir = fileDir;
    if (!songDir.exists()) {
      if (!songDir.mkdir()) {
        throw new InitializationException("Unable to create song directory");
      }
    }

    initStateWriter.state("Logging into GPlayMusic");
    try {
      loginToService(initStateWriter);
    } catch (IOException | Gpsoauth.TokenRequestFailed e) {
      initStateWriter.warning("Logging into GPlayMusic failed!");
      throw new InitializationException(e);
    }
  }

  private void loginToService(@Nonnull InitStateWriter initStateWriter)
      throws IOException, Gpsoauth.TokenRequestFailed {
    AuthToken authToken = null;
    boolean existingToken = false;
    if (token.get() != null && token.checkError() == null) {
      authToken = TokenProvider.provideToken(token.get());
      existingToken = true;
      initStateWriter.state("Trying to login with existing token.");
    } else {
      initStateWriter.state("Fetching new token.");
      authToken = TokenProvider.provideToken(username.get(), password.get(), androidID.get());
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
  public void close() {
  }

  @Nonnull
  @Override
  public List<Song> search(@Nonnull String query, int offset) {
    try {
      return api.getTrackApi().search(query, 30).stream()
          .map(this::getSongFromTrack)
          .peek(song -> cachedSongs.put(song.getId(), song))
          .collect(Collectors.toList());
    } catch (IOException e) {
      if (e instanceof NetworkException
          && ((NetworkException) e).getCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
        if (generateNewToken()) {
          return search(query, offset);
        }
      } else {
        logger.warn("Exception while searching with query '" + query + "'", e);
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
      throw new NoSuchSongException(songId, GPlayMusicProviderBase.class, e);
    }
  }

  @Nonnull
  @Override
  public List<Song> lookupBatch(@Nonnull List<String> list) {
    return GPlayMusicProvider.DefaultImpls.lookupBatch(this, list);
  }

  @Nonnull
  @Override
  public Resource loadSong(@Nonnull Song song) throws SongLoadingException {
    String songDir = fileDir.getPath();
    try {
      Track track = getAPI().getTrackApi().getTrack(song.getId());
      Path path = Paths.get(songDir, song.getId() + ".mp3");
      Path tmpPath = Paths.get(songDir, song.getId() + ".mp3.tmp");
      if (!Files.exists(path)) {
        track.download(streamQuality.get(), tmpPath);
        Files.move(tmpPath, path);
      }
      return new FileResource(path.toFile());
    } catch (IOException e) {
      throw new SongLoadingException(e);
    }
  }

  @Nonnull
  @Override
  public Playback supplyPlayback(@Nonnull Song song, @Nonnull Resource resource)
      throws IOException {
    FileResource fileResource = (FileResource) resource;
    try {
      return playbackFactory.createPlayback(fileResource.getFile());
    } catch (UnsupportedAudioFileException e) {
      throw new IOException(e);
    }
  }

  @Nonnull
  @Override
  public String getSubject() {
    return "GPlayMusic";
  }

  private Boolean generateNewToken() {
    long diffLastRequest = System.currentTimeMillis() - TokenProvider.getLastTokenFetched();
    if (diffLastRequest > tokenCooldownMillis) {
      logger.info("Authorization expired. Requesting new token.");
      try {
        api.changeToken(
            TokenProvider.provideToken(username.get(), password.get(), androidID.get()));
        return true;
      } catch (Gpsoauth.TokenRequestFailed | IOException e) {
        logger.error("Exception while trying to generate new token. Unable to authenticate client.",
            e);
      }
    } else {
      logger.info("Token request on cooldown. Please wait %d seconds.", diffLastRequest * 1000);
    }
    return false;
  }
}
