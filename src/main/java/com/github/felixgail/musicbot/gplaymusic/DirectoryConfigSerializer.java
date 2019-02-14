package com.github.felixgail.musicbot.gplaymusic;

import java.io.File;
import net.bjoernpetersen.musicbot.api.config.ConfigSerializer;
import org.jetbrains.annotations.NotNull;

final class DirectoryConfigSerializer implements ConfigSerializer<File> {

    @Override
    public File deserialize(@NotNull String s) {
        return new File(s);
    }

    @NotNull
    @Override
    public String serialize(File file) {
        return file.getPath();
    }
}
