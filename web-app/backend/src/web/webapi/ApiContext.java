package web.webapi;

import com.sun.net.httpserver.HttpExchange;
import src.model.User;
import web.security.Session;
import web.security.SessionManager;
import web.service.WebUserRepository;
import web.util.Json;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Per-request context: routing, body, query, session and response helpers. */
public final class ApiContext {

    private final HttpExchange exchange;
    private final SessionManager sessions;
    private final int maxBodyBytes;
    private final String path;
    private final List<String> segments;
    private final Map<String, String> query = new LinkedHashMap<>();
    private final Map<String, Object> body;

    private Session session;
    private User user;
    private boolean responded;

    public ApiContext(HttpExchange exchange, SessionManager sessions, int maxBodyBytes)
            throws IOException {
        this.exchange = exchange;
        this.sessions = sessions;
        this.maxBodyBytes = maxBodyBytes;

        String rawPath = exchange.getRequestURI().getRawPath();
        this.path = URLDecoder.decode(rawPath == null ? "/" : rawPath, StandardCharsets.UTF_8);
        this.segments = new ArrayList<>();
        for (String part : this.path.split("/")) {
            if (!part.isBlank()) {
                segments.add(part);
            }
        }
        // Handlers address routes relative to /api: segment(0) is the feature.
        if (!segments.isEmpty() && "api".equalsIgnoreCase(segments.get(0))) {
            segments.remove(0);
        }

        String rawQuery = exchange.getRequestURI().getRawQuery();
        if (rawQuery != null && !rawQuery.isBlank()) {
            for (String pair : rawQuery.split("&")) {
                int split = pair.indexOf('=');
                String key = split < 0 ? pair : pair.substring(0, split);
                String value = split < 0 ? "" : pair.substring(split + 1);
                query.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                        URLDecoder.decode(value, StandardCharsets.UTF_8));
            }
        }

        this.session = sessions.find(
                SessionManager.tokenFromCookies(exchange.getRequestHeaders().getFirst("Cookie")));
        byte[] rawBody = exchange.getRequestBody().readAllBytes();
        if (rawBody.length > maxBodyBytes) {
            throw new IllegalArgumentException("Request body is too large.");
        }
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        String text = new String(rawBody, StandardCharsets.UTF_8);
        if (!text.isBlank() && (contentType == null || contentType.toLowerCase(Locale.ROOT)
                .contains("json"))) {
            body = Json.object(Json.parse(text));
        } else {
            body = new LinkedHashMap<>();
        }
    }

    public HttpExchange exchange() {
        return exchange;
    }

    public String method() {
        return exchange.getRequestMethod().toUpperCase(Locale.ROOT);
    }

    public String path() {
        return path;
    }

    public List<String> segments() {
        return segments;
    }

    public String segment(int index) {
        return index < segments.size() ? segments.get(index) : "";
    }

    public long longSegment(int index) {
        try {
            return Long.parseLong(segment(index));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("A numeric id is required in the URL.");
        }
    }

    public String query(String key) {
        return query.getOrDefault(key, "");
    }

    public Map<String, Object> body() {
        return body;
    }

    public String text(String key) {
        return Json.text(body, key);
    }

    public long number(String key) {
        return Json.number(body, key);
    }

    public int integer(String key) {
        return Json.integer(body, key);
    }

    public boolean flag(String key) {
        return Json.flag(body, key);
    }

    public List<Object> list(String key) {
        return Json.array(body.get(key));
    }

    public Session session() {
        return session;
    }

    public User user() {
        return user;
    }

    /** Validates the session, reloads the account from PostgreSQL and checks roles. */
    public User require(String... allowedRoles) throws Exception {
        if (session == null) {
            error(401, "Please sign in to continue.");
            return null;
        }
        User fresh = WebUserRepository.findById(session.userId());
        if (fresh == null || fresh.isDeleted() || !fresh.isActive()) {
            sessions.invalidate(session.token());
            error(401, "This account is no longer active. Please sign in again.");
            return null;
        }
        user = fresh;
        if (allowedRoles.length > 0) {
            boolean allowed = false;
            for (String role : allowedRoles) {
                if (role.equalsIgnoreCase(fresh.getRole())) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                error(403, "Your account is not allowed to perform this action.");
                return null;
            }
        }
        return fresh;
    }

    public boolean isResponded() {
        return responded;
    }

    public void setCookie(String cookie) {
        exchange.getResponseHeaders().add("Set-Cookie", cookie);
    }

    public void json(int status, Object value) throws IOException {
        byte[] bytes = Json.write(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        responded = true;
    }

    public void ok(Object value) throws IOException {
        json(200, value);
    }

    public void created(Object value) throws IOException {
        json(201, value);
    }

    public void noContent() throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(204, -1);
        responded = true;
    }

    public void error(int status, String message) throws IOException {
        if (responded) {
            return;
        }
        json(status, Map.of("error", message == null ? "Request failed." : message));
    }

    public void bytes(int status, byte[] content, String contentType, String fileName)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        if (fileName != null && !fileName.isBlank()) {
            String safe = fileName.replace("\"", "").replace("\r", "").replace("\n", "");
            exchange.getResponseHeaders().set("Content-Disposition",
                    "attachment; filename=\"" + safe + "\"");
        }
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, content.length);
        exchange.getResponseBody().write(content);
        responded = true;
    }

    public void close() {
        exchange.close();
    }

    /** Convenience for handlers that need a consistent role set. */
    public static String[] STAFF() {
        return new String[] { "ICT_STAFF", "FACILITY_STAFF" };
    }

    public static String[] STAFF_REVIEWERS() {
        return new String[] { "ICT_STAFF", "FACILITY_STAFF", "ADMIN", "DIRECTOR" };
    }

    public static String[] ALL_ROLES() {
        return new String[] { "BORROWER", "ICT_STAFF", "FACILITY_STAFF", "ADMIN", "DIRECTOR" };
    }

    public static boolean oneOf(String value, String... candidates) {
        if (value == null) {
            return false;
        }
        return Arrays.stream(candidates).anyMatch(value::equalsIgnoreCase);
    }
}
