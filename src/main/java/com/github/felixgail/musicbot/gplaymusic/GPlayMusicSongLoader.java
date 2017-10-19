package com.github.felixgail.musicbot.gplaymusic;

import com.github.bjoernpetersen.jmusicbot.Song;
import com.github.bjoernpetersen.jmusicbot.SongLoader;
import com.github.felixgail.musicbot.gplaymusic.gplaymusic.model.enums.StreamQuality;
import com.github.felixgail.musicbot.gplaymusic.gplaymusic.model.shema.Track;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GPlayMusicSongLoader implements SongLoader {
  private String songDir;
  private StreamQuality quality;

  GPlayMusicSongLoader(StreamQuality quality, String songDir) {
    this.quality = quality;
    this.songDir = songDir;
  }

  @Override
  public boolean load(Song song) {
    try {
      Track track = Track.getTrack(song.getId());
      Path path = Paths.get(songDir, song.getId() + ".mp3");
      Path tmpPath = Paths.get(songDir, song.getId() + ".mp3.tmp");
      if (!Files.exists(path)) {
        track.download(quality, tmpPath);
        Files.move(tmpPath, path);
      }
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}
