package src.util;

import java.util.List;

/**
 * Small built-in notification tones synthesized with the standard Java Sound API.
 *
 * <p>No audio files are shipped, so the feature keeps working on any machine with
 * a normal Java runtime. Every call is best-effort: a missing sound card, a busy
 * audio line, or a headless environment never interrupts the application.</p>
 */
public final class NotificationSound {

    /** Names shown in Settings; the first entry is the default. */
    public static final List<String> NAMES = List.of(
            "Chime", "Bell", "Soft", "Alert");

    private static final String DEFAULT = "Chime";
    private static volatile long lastPlayedAt;

    private NotificationSound() {
    }

    /** Normalizes a stored sound name to one of {@link #NAMES}. */
    public static String normalize(String name) {
        if (name == null || name.isBlank()) {
            return DEFAULT;
        }
        for (String candidate : NAMES) {
            if (candidate.equalsIgnoreCase(name.trim())) {
                return candidate;
            }
        }
        return DEFAULT;
    }

    /**
     * Plays a short tone without blocking the caller. Calls closer together than
     * {@code minimumGapMillis} are ignored so a burst of notifications produces one
     * sound instead of several overlapping tones.
     */
    public static void play(String name, long minimumGapMillis) {
        long now = System.currentTimeMillis();
        synchronized (NotificationSound.class) {
            if (now - lastPlayedAt < Math.max(0, minimumGapMillis)) {
                return;
            }
            lastPlayedAt = now;
        }
        String selected = normalize(name);
        Thread worker = new Thread(() -> synthesize(selected), "equiplink-notification-sound");
        worker.setDaemon(true);
        worker.start();
    }

    /** Plays a short preview regardless of the normal throttle. */
    public static void preview(String name) {
        String selected = normalize(name);
        Thread worker = new Thread(() -> synthesize(selected), "equiplink-sound-preview");
        worker.setDaemon(true);
        worker.start();
    }

    private static void synthesize(String name) {
        double[] frequencies;
        int noteMillis;
        switch (name) {
            case "Bell" -> {
                frequencies = new double[] { 1318.5, 1046.5 };
                noteMillis = 260;
            }
            case "Soft" -> {
                frequencies = new double[] { 523.3, 659.3 };
                noteMillis = 220;
            }
            case "Alert" -> {
                frequencies = new double[] { 880.0, 880.0, 1174.7 };
                noteMillis = 150;
            }
            default -> {
                frequencies = new double[] { 783.99, 1046.5 };
                noteMillis = 210;
            }
        }
        try {
            float sampleRate = 22050f;
            int samplesPerNote = Math.max(1, (int) (sampleRate * noteMillis / 1000.0));
            int total = samplesPerNote * frequencies.length;
            byte[] audio = new byte[total * 2];
            int index = 0;
            for (double frequency : frequencies) {
                for (int i = 0; i < samplesPerNote; i++) {
                    double progress = i / (double) samplesPerNote;
                    // Soft attack and exponential decay avoid clicks at both ends.
                    double envelope = Math.min(1.0, progress / 0.04)
                            * Math.pow(1.0 - progress, 1.8);
                    double value = Math.sin(2.0 * Math.PI * frequency * i / sampleRate)
                            * envelope * 0.22;
                    short sample = (short) Math.round(value * Short.MAX_VALUE);
                    audio[index++] = (byte) (sample & 0xff);
                    audio[index++] = (byte) ((sample >> 8) & 0xff);
                }
            }
            javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(
                    sampleRate, 16, 1, true, false);
            javax.sound.sampled.Clip clip = javax.sound.sampled.AudioSystem.getClip();
            if (clip == null) {
                return;
            }
            try (clip) {
                clip.open(format, audio, 0, audio.length);
                clip.start();
                // Keep the clip alive until playback finishes.
                Thread.sleep((long) frequencies.length * noteMillis + 80L);
            }
        } catch (Exception ignored) {
            // Sound is optional; the visual notification is always shown.
        } catch (LinkageError ignored) {
            // Some minimal runtimes omit javax.sound entirely.
        }
    }
}
