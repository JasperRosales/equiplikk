package web.security;

import src.model.User;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory sessions with HttpOnly cookies; no credentials are stored in the browser. */
public final class SessionManager {

    public static final String COOKIE_NAME = "equiplink_session";

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final int timeoutMinutes;

    public SessionManager(int timeoutMinutes) {
        this.timeoutMinutes = Math.max(15, timeoutMinutes);
    }

    public Session create(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Session session = new Session(token, user.getUserId(), user.getRole(),
                user.getUsername(), user.getFullName(),
                Instant.now().plus(timeoutMinutes, ChronoUnit.MINUTES));
        sessions.put(token, session);
        return session;
    }

    public Session find(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Session session = sessions.get(token);
        if (session == null) {
            return null;
        }
        if (session.expired()) {
            sessions.remove(token);
            return null;
        }
        session.touch();
        return session;
    }

    public void invalidate(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    /** Cookie value for the session created by a successful login. */
    public String cookie(Session session) {
        return COOKIE_NAME + "=" + session.token()
                + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + (timeoutMinutes * 60L);
    }

    /** Expired cookie used on logout. */
    public String clearingCookie() {
        return COOKIE_NAME + "=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0";
    }

    /** Extracts the session token from a Cookie header. */
    public static String tokenFromCookies(String header) {
        if (header == null) {
            return null;
        }
        for (String part : header.split(";")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && COOKIE_NAME.equals(pair[0])) {
                return pair[1];
            }
        }
        return null;
    }
}
