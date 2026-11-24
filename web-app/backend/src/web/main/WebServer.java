package web.main;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import web.handlers.AuthHandler;
import web.handlers.EquipmentHandler;
import web.handlers.MiscHandler;
import web.handlers.NotificationHandler;
import web.handlers.RequestHandler;
import web.handlers.UserHandler;
import web.webapi.ApiContext;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;

/**
 * EquipLink web access. This server is only a transport layer: every business
 * operation is delegated to the existing desktop controllers/services.
 */
public final class WebServer {

    private final Path webHome;
    private final Path frontend;
    private final AppServices services;
    private final int port;
    private final int maxBodyBytes;

    private WebServer(Path webHome, Properties config) throws IOException {
        this.webHome = webHome;
        this.frontend = webHome.resolve("frontend").normalize();
        this.port = Integer.parseInt(config.getProperty("port", "8080").trim());
        int sessionMinutes = Integer.parseInt(config.getProperty("sessionMinutes", "480").trim());
        this.maxBodyBytes = Integer.parseInt(config.getProperty("maxBodyBytes", "2097152").trim());
        this.services = new AppServices(webHome, sessionMinutes);
    }

    public static void main(String[] args) throws Exception {
        String homeValue = System.getProperty("equiplink.web.home");
        Path webHome = homeValue == null || homeValue.isBlank()
                ? Paths.get(".").toAbsolutePath().normalize()
                : Paths.get(homeValue).toAbsolutePath().normalize();
        Properties config = loadConfig(webHome.resolve("config").resolve("web-app.properties"));
        WebServer application = new WebServer(webHome, config);
        application.start(config.getProperty("bind", "0.0.0.0").trim());
    }

    private static Properties loadConfig(Path file) throws IOException {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        return properties;
    }

    private void start(String bind) throws IOException {
        String host = bind.isBlank() ? "0.0.0.0" : bind;
        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/api/", new ApiHandler());
        server.createContext("/", new StaticHandler());
        server.setExecutor(Executors.newFixedThreadPool(24));
        writePid();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            deletePid();
            server.stop(0);
        }));
        server.start();
        System.out.println("EquipLink Web is running.");
        System.out.println("  Local:   http://localhost:" + port);
        for (String address : lanAddresses()) {
            System.out.println("  Network: http://" + address + ":" + port);
        }
        System.out.println("Press Ctrl+C in this window (or run stop-web.bat) to stop.");
    }

    private void writePid() {
        try {
            Path pidFile = webHome.resolve("config").resolve("server.pid");
            Files.writeString(pidFile, String.valueOf(ProcessHandle.current().pid()));
        } catch (IOException ignored) {
            // PID file is a convenience for the stop script, not a requirement.
        }
    }

    private void deletePid() {
        try {
            Files.deleteIfExists(webHome.resolve("config").resolve("server.pid"));
        } catch (IOException ignored) {
            // Nothing to do during shutdown.
        }
    }

    private static List<String> lanAddresses() {
        List<String> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address.isSiteLocalAddress() && !address.isLoopbackAddress()) {
                        String text = address.getHostAddress();
                        if (!result.contains(text)) {
                            result.add(text);
                        }
                    }
                }
            }
        } catch (IOException ignored) {
            // Local URL is still printed.
        }
        return result;
    }

    private final class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            ApiContext context = null;
            try {
                if (!sameOrigin(exchange)) {
                    exchange.sendResponseHeaders(403, -1);
                    return;
                }
                context = new ApiContext(exchange, services.sessions, maxBodyBytes);
                route(context);
            } catch (IllegalArgumentException exception) {
                if (context != null) {
                    context.error(400, exception.getMessage());
                } else {
                    sendText(exchange, 400, exception.getMessage());
                }
            } catch (SecurityException exception) {
                if (context != null) {
                    context.error(403, exception.getMessage());
                } else {
                    sendText(exchange, 403, exception.getMessage());
                }
            } catch (Exception exception) {
                exception.printStackTrace();
                if (context != null) {
                    context.error(500, "The request could not be completed. Please try again.");
                } else {
                    sendText(exchange, 500, "The request could not be completed.");
                }
            } finally {
                if (context != null) {
                    if (!context.isResponded()) {
                        context.error(404, "Endpoint not found.");
                    }
                    context.close();
                } else {
                    exchange.close();
                }
            }
        }
    }

    private void route(ApiContext context) throws Exception {
        String feature = context.segment(0);
        switch (feature == null ? "" : feature.toLowerCase(Locale.ROOT)) {
            case "health" -> context.ok(Map.of("ok", true));
            case "auth" -> AuthHandler.handle(context, services);
            case "equipment" -> EquipmentHandler.handle(context, services);
            case "requests" -> RequestHandler.handle(context, services);
            case "dashboard" -> MiscHandler.handleDashboard(context, services);
            case "notifications" -> NotificationHandler.handle(context, services);
            case "users" -> UserHandler.handleUsers(context, services);
            case "password-requests" -> UserHandler.handlePasswordRequests(context, services);
            case "venues" -> MiscHandler.handleVenues(context, services);
            case "profile" -> MiscHandler.handleProfile(context, services);
            case "meta" -> MiscHandler.handleMeta(context, services);
            case "export" -> RequestHandler.handle(context, services);
            default -> context.error(404, "Endpoint not found.");
        }
    }

    private static boolean sameOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null || origin.isBlank()) {
            return true;
        }
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (host == null || host.isBlank()) {
            return true;
        }
        String expected = "http://" + host;
        String secure = "https://" + host;
        return origin.equalsIgnoreCase(expected) || origin.equalsIgnoreCase(secure);
    }

    private final class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!exchange.getRequestMethod().equalsIgnoreCase("GET")
                        && !exchange.getRequestMethod().equalsIgnoreCase("HEAD")) {
                    sendText(exchange, 405, "Method not allowed.");
                    return;
                }
                String raw = exchange.getRequestURI().getRawPath();
                String path = URLDecoder.decode(raw == null ? "/" : raw, StandardCharsets.UTF_8);
                if (path.isBlank() || path.equals("/")) {
                    path = "/index.html";
                }
                String relative = path.startsWith("/") ? path.substring(1) : path;
                Path resolved = frontend.resolve(relative.replace('/', java.io.File.separatorChar))
                        .normalize();
                if (!resolved.startsWith(frontend)) {
                    sendText(exchange, 403, "Forbidden.");
                    return;
                }
                if (Files.isDirectory(resolved)) {
                    resolved = resolved.resolve("index.html");
                }
                if (!Files.isRegularFile(resolved)) {
                    if (!path.contains(".")) {
                        resolved = frontend.resolve("index.html");
                    } else {
                        sendText(exchange, 404, "File not found.");
                        return;
                    }
                }
                byte[] bytes = Files.readAllBytes(resolved);
                exchange.getResponseHeaders().set("Content-Type", mime(resolved));
                // Front-end fixes must be visible immediately on LAN devices.
                exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                if (exchange.getRequestMethod().equalsIgnoreCase("HEAD")) {
                    exchange.sendResponseHeaders(200, -1);
                } else {
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream output = exchange.getResponseBody()) {
                        output.write(bytes);
                    }
                }
            } catch (Exception exception) {
                exception.printStackTrace();
                sendText(exchange, 500, "The page could not be loaded.");
            } finally {
                exchange.close();
            }
        }
    }

    private static String mime(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".woff2")) return "font/woff2";
        if (name.endsWith(".woff")) return "font/woff";
        if (name.endsWith(".ttf")) return "font/ttf";
        if (name.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private static void sendText(HttpExchange exchange, int status, String message)
            throws IOException {
        byte[] bytes = String.valueOf(message).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
