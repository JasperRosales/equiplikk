package src.util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Normalizes a user-selected image into the single PNG avatar format stored on
 * the account. Keeping this in one place means the desktop client and any other
 * client using the same database save identical, bounded image bytes.
 */
public final class ProfilePicture {

    /** Upper bound for the file a user may pick. */
    public static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
    /** Upper bound after normalization, so a single account cannot bloat the database. */
    public static final int MAX_STORED_BYTES = 2 * 1024 * 1024;
    private static final int MAX_SIDE = 256;

    private ProfilePicture() {
    }

    /** Reads and normalizes the chosen file; caller shows any IOException to the user. */
    public static byte[] fromFile(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("Choose a valid image file.");
        }
        if (file.length() > MAX_UPLOAD_BYTES) {
            throw new IOException("Choose an image smaller than 5 MB.");
        }
        return normalize(Files.readAllBytes(file.toPath()));
    }

    /** Decodes, downscales and re-encodes an image as PNG. */
    public static byte[] normalize(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IOException("The image is empty.");
        }
        if (bytes.length > MAX_UPLOAD_BYTES) {
            throw new IOException("Choose an image smaller than 5 MB.");
        }

        BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
        if (source == null || source.getWidth() < 1 || source.getHeight() < 1) {
            throw new IOException("That file is not a supported image.");
        }

        int width = source.getWidth();
        int height = source.getHeight();
        if (Math.max(width, height) > MAX_SIDE) {
            double scale = MAX_SIDE / (double) Math.max(width, height);
            width = Math.max(1, (int) Math.round(width * scale));
            height = Math.max(1, (int) Math.round(height * scale));
        }

        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();

        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        if (!ImageIO.write(output, "png", encoded)) {
            throw new IOException("The image could not be saved.");
        }
        byte[] result = encoded.toByteArray();
        if (result.length > MAX_STORED_BYTES) {
            throw new IOException("The image is too detailed; choose a smaller one.");
        }
        return result;
    }
}
