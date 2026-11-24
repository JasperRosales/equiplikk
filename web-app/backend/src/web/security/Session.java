package web.security;

import java.time.Instant;

/** Server-side login session. The browser only ever holds the opaque token. */
public final class Session {

    private final String token;
    private final long userId;
    private final String role;
    private final String username;
    private final String fullName;
    private final Instant expiresAt;
    private Instant lastAccess = Instant.now();

    Session(String token, long userId, String role, String username,
            String fullName, Instant expiresAt) {
        this.token = token;
        this.userId = userId;
        this.role = role;
        this.username = username;
        this.fullName = fullName;
        this.expiresAt = expiresAt;
    }

    public String token() {
        return token;
    }

    public long userId() {
        return userId;
    }

    public String role() {
        return role;
    }

    public String username() {
        return username;
    }

    public String fullName() {
        return fullName;
    }

    public boolean expired() {
        return Instant.now().isAfter(expiresAt);
    }

    public void touch() {
        lastAccess = Instant.now();
    }

    public Instant lastAccess() {
        return lastAccess;
    }
}
