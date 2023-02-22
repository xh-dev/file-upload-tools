package dev.xethh.tools.utils;

import io.vavr.control.Try;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicLong;

public class FileUtils {
    public static void transfer(
            FileChannel osChannel,
            ReadableByteChannel channelRead,
            int length,
            AtomicLong cur) {
        Try.of(() -> {
            osChannel.transferFrom(channelRead, cur.get(), length);
            cur.addAndGet(length);
            return 1;
        }).get();
    }

    public static void moveFile(File fromFile, File toFile) throws IOException {
        Files.copy(fromFile.toPath(), toFile.toPath());
        if (fromFile.exists())
            Files.delete(fromFile.toPath());
    }
    public static void deleteFile(File toBeDeleted) throws IOException {
        if (toBeDeleted.exists())
            Files.delete(toBeDeleted.toPath());
    }
}
