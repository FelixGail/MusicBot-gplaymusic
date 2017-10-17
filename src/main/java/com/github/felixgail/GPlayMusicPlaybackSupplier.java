package com.github.felixgail;

import com.github.bjoernpetersen.jmusicbot.PlaybackSupplier;
import com.github.bjoernpetersen.jmusicbot.Song;
import com.github.bjoernpetersen.jmusicbot.playback.Playback;
import com.github.bjoernpetersen.mp3Playback.Mp3PlaybackFactory;

import javax.annotation.Nonnull;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;

public class GPlayMusicPlaybackSupplier implements PlaybackSupplier {
  private String songDir;
  private Mp3PlaybackFactory playbackFactory;

  public GPlayMusicPlaybackSupplier(String songDir, Mp3PlaybackFactory factory) {
    this.songDir = songDir;
    this.playbackFactory = factory;
  }

  @Nonnull
  @Override
  public Playback supply(Song song) throws IOException {
    try {
      return playbackFactory.createPlayback(new File(songDir, song.getId() + ".mp3"));
    } catch (UnsupportedAudioFileException e) {
      throw new IOException(e);
    }
  }
}
