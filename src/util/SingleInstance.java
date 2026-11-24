package src.util;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Keeps one desktop process per Windows user so a hidden background copy cannot
 * be started twice and play duplicate notification sounds.
 */
public final class SingleInstance {

    private static FileChannel channel;
    private static FileLock lock;

    private SingleInstance() {
    }

    public static boolean acquire() {
        try {
            Path directory = Path.of(System.getProperty("user.home"), ".equiplink");
            Files.createDirectories(directory);
            Path path = directory.resolve("app.lock");
            channel = FileChannel.open(path,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            lock = channel.tryLock();
            return lock != null;
        } catch (IOException exception) {
            // Failing open is safer than preventing the app from starting because an
            // anti-virus product temporarily holds the lock file.
            return true;
        } catch (Exception exception) {
            return true;
        }
    }

    public static void release() {
        try {
            if (lock != null) {
                lock.release();
            }
            if (channel != null) {
                channel.close();
            }
        } catch (IOException ignored) {
        } finally {
            lock = null;
            channel = null;
        }
    }
}
