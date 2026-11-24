package web.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads and writes the SAME room.md the desktop application uses, so venue changes
 * made in either interface appear in the other. ICT-only write permission is checked
 * by the API handler, not here.
 */
public final class VenueService {

    private final Path repositoryRoom;
    private final Path dataRoom;

    public VenueService(Path webHome) {
        this.repositoryRoom = webHome.resolve("..").normalize().resolve("room.md");
        String localAppData = System.getenv("LOCALAPPDATA");
        this.dataRoom = (localAppData == null || localAppData.isBlank())
                ? Path.of(System.getProperty("user.home"), ".equiplink", "room.md")
                : Path.of(localAppData, "EquipLink", "room.md");
    }

    public List<String> findAll() {
        Set<String> rooms = new LinkedHashSet<>(parse(readLines(roomFile())));
        rooms.addAll(parse(readLines(repositoryRoom)));
        return new ArrayList<>(rooms);
    }

    public void saveAll(List<String> rooms) throws IOException {
        Path target = roomFile();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "room-options-", ".md");
        try {
            List<String> lines = new ArrayList<>();
            lines.add("#Rooms");
            for (String room : rooms) {
                String value = room == null ? "" : room.trim();
                if (!value.isBlank()) {
                    lines.add("- " + value);
                }
            }
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path roomFile() {
        if (Files.isRegularFile(repositoryRoom)) {
            return repositoryRoom;
        }
        return dataRoom;
    }

    private static List<String> readLines(Path path) {
        try {
            return Files.isRegularFile(path)
                    ? Files.readAllLines(path, StandardCharsets.UTF_8)
                    : List.of();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    static List<String> parse(List<String> lines) {
        Set<String> rooms = new LinkedHashSet<>();
        for (String line : lines) {
            String value = line == null ? "" : line.trim();
            if (!value.startsWith("- ") && !value.startsWith("* ")) {
                continue;
            }
            value = value.substring(2).trim();
            if (!value.isEmpty()) {
                rooms.add(value);
            }
        }
        return new ArrayList<>(rooms);
    }

    /** Bundled fallback used only when no room.md file exists yet. */
    public List<String> bundled() {
        try (InputStream resource = VenueService.class.getResourceAsStream("/room.md")) {
            if (resource != null) {
                String text = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
                return parse(text.lines().toList());
            }
        } catch (IOException ignored) {
            // Fall through to the built-in room.
        }
        return List.of("Room 101");
    }
}
