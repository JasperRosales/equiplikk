package src.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/**
 * Stores only the opaque random session token on this machine. No username,
 * password, or personal account data is written to local storage.
 */
public final class SessionStore {

    private static final String FILE_NAME = "session.token";

    private SessionStore() {
    }

    public static Path tokenPath() {
        return Path.of(System.getProperty("user.home"), ".equiplink", FILE_NAME);
    }

    /** Returns the saved token, or null when there is no usable saved session. */
    public static String read() {
        try {
            Path path = tokenPath();
            if (!Files.isRegularFile(path)) {
                return null;
            }
            String token = Files.readString(path, StandardCharsets.UTF_8).trim();
            return token.matches("[A-Za-z0-9_-]{32,128}") ? token : null;
        } catch (IOException exception) {
            return null;
        }
    }

    public static void write(String token) throws IOException {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("A session token is required.");
        }
        Path path = tokenPath();
        Files.createDirectories(path.getParent());
        Files.writeString(path, token, StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(path, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Windows uses its own ACL model; the file remains under the user profile.
        }
    }

    public static void clear() {
        try {
            Files.deleteIfExists(tokenPath());
        } catch (IOException ignored) {
            // A failed delete does not grant access: the token is also revoked in PostgreSQL.
        }
    }
}
