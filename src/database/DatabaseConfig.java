package src.database;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * PostgreSQL connection settings used by {@link Database}.
 *
 * <p>Every saved configuration is written to a properties file in the user's
 * home directory so it survives restarts and can be inspected or copied:</p>
 *
 * <pre>%USERPROFILE%\.equiplink\database.properties</pre>
 *
 * <p>Older configurations stored in Java preferences are still read as a
 * fallback, but new saves always go to the file.</p>
 */
public final class DatabaseConfig {

    private static final Preferences PREFS =
            Preferences.userNodeForPackage(DatabaseConfig.class);

    private static final String REMEMBER = "remember";
    private static final String HOST = "host";
    private static final String PORT = "port";
    private static final String DATABASE = "database";
    private static final String USER = "user";
    private static final String PASSWORD = "password";
    private static final String SSL_MODE = "sslMode";

    /** Settings used until a user saves a configuration. */
    public static final DatabaseConfig DEFAULT = new DatabaseConfig(
            "192.168.194.3", "5432", "equiplink", "postgres", "srhmrc22", "prefer");

    private static volatile DatabaseConfig current = load();

    private final String host;
    private final String port;
    private final String database;
    private final String user;
    private final String password;
    private final String sslMode;

    public DatabaseConfig(
            String host, String port, String database, String user, String password) {
        this(host, port, database, user, password, "prefer");
    }

    public DatabaseConfig(
            String host, String port, String database, String user, String password,
            String sslMode) {

        this.host = host.trim();
        this.port = port.trim();
        this.database = database.trim();
        this.user = user.trim();
        this.password = password;
        this.sslMode = sslMode == null || sslMode.isBlank()
                ? "prefer" : sslMode.trim();
    }

    /** Settings used by every new database connection. */
    public static DatabaseConfig current() {
        return current;
    }

    /** Where saved settings live for the current Windows user. */
    public static Path configPath() {
        return Path.of(System.getProperty("user.home"), ".equiplink",
                "database.properties");
    }

    /** True when a configuration has been saved. */
    public static boolean hasSaved() {
        return Files.isRegularFile(configPath()) || PREFS.getBoolean(REMEMBER, false);
    }

    /** Re-reads saved settings from disk. */
    public static void reload() {
        current = load();
    }

    /** Applies settings without writing them to disk. */
    public static void apply(DatabaseConfig config) {
        current = config;
    }

    /**
     * Applies the settings immediately and saves them to
     * {@code .equiplink/database.properties} for every future launch.
     */
    public static void save(DatabaseConfig config) throws IOException {

        Path path = configPath();
        Files.createDirectories(path.getParent());

        Properties properties = new Properties();
        properties.setProperty(HOST, config.host);
        properties.setProperty(PORT, config.port);
        properties.setProperty(DATABASE, config.database);
        properties.setProperty(USER, config.user);
        properties.setProperty(PASSWORD, config.password);
        properties.setProperty(SSL_MODE, config.sslMode);

        try (OutputStream out = Files.newOutputStream(path)) {
            properties.store(out, "EquipLink database configuration");
        }

        current = config;

        // Keep any older preferences-based configuration in sync as well.
        try {
            PREFS.put(HOST, config.host);
            PREFS.put(PORT, config.port);
            PREFS.put(DATABASE, config.database);
            PREFS.put(USER, config.user);
            PREFS.put(PASSWORD, config.password);
            PREFS.put(SSL_MODE, config.sslMode);
            PREFS.putBoolean(REMEMBER, true);
            PREFS.flush();
        } catch (BackingStoreException | SecurityException ignored) {
            // The properties file is the authoritative copy.
        }
    }

    public String jdbcUrl() {
        // sslmode=require is needed by hosted PostgreSQL providers such as Supabase.
        return "jdbc:postgresql://" + host + ":" + port + "/" + database
                + "?sslmode=" + sslMode;
    }

    public String host() {
        return host;
    }

    public String port() {
        return port;
    }

    public String database() {
        return database;
    }

    public String user() {
        return user;
    }

    public String password() {
        return password;
    }

    public String sslMode() {
        return sslMode;
    }

    private static DatabaseConfig load() {

        Path path = configPath();
        if (Files.isRegularFile(path)) {
            Properties properties = new Properties();
            try (InputStream in = Files.newInputStream(path)) {
                properties.load(in);
                return new DatabaseConfig(
                        properties.getProperty(HOST, DEFAULT.host),
                        properties.getProperty(PORT, DEFAULT.port),
                        properties.getProperty(DATABASE, DEFAULT.database),
                        properties.getProperty(USER, DEFAULT.user),
                        properties.getProperty(PASSWORD, DEFAULT.password),
                        properties.getProperty(SSL_MODE, DEFAULT.sslMode));
            } catch (IOException ignored) {
                // Fall through to the built-in defaults.
            }
        }

        // Compatibility with configurations saved by older builds.
        if (PREFS.getBoolean(REMEMBER, false)) {
            return new DatabaseConfig(
                    PREFS.get(HOST, DEFAULT.host),
                    PREFS.get(PORT, DEFAULT.port),
                    PREFS.get(DATABASE, DEFAULT.database),
                    PREFS.get(USER, DEFAULT.user),
                    PREFS.get(PASSWORD, DEFAULT.password),
                    PREFS.get(SSL_MODE, DEFAULT.sslMode));
        }

        return DEFAULT;
    }
}
