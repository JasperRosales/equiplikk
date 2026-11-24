package src.ui.components;

import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import src.ui.AppTheme;

/**
 * Rounded badge showing the account picture, or the user's initials when no
 * picture has been uploaded. Every profile surface passes the same saved bytes
 * from the authenticated {@code User}, so the avatar never drifts out of sync.
 */
public class Avatar extends JLabel {

    private final Color fill;
    private final int arc;
    private BufferedImage image;

    public Avatar(String text, Color fill, int arc) {
        super(text, SwingConstants.CENTER);
        this.fill = fill;
        this.arc = arc;
        setFont(AppTheme.semibold(11));
        setForeground(AppTheme.onPrimary());
        setOpaque(false);
        setPreferredSize(new Dimension(34, 34));
    }

    /** One call updates the initials fallback and the picture together. */
    public void setIdentity(String fullName, byte[] picture) {
        setText(initials(fullName));
        setImage(picture);
    }

    /** Accepts the normalized bytes saved on the user account; null restores initials. */
    public void setImage(byte[] picture) {
        BufferedImage decoded = null;
        if (picture != null && picture.length > 0) {
            try {
                decoded = ImageIO.read(new ByteArrayInputStream(picture));
            } catch (IOException exception) {
                decoded = null;
            }
        }
        image = decoded;
        revalidate();
        repaint();
    }

    /** Shared initials helper so header, sidebar and My Profile stay identical. */
    public static String initials(String name) {
        if (name == null || name.isBlank()) {
            return "EL";
        }
        String[] parts = name.trim().split("\\s+");
        return parts.length == 1
                ? parts[0].substring(0, 1).toUpperCase()
                : (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1))
                        .toUpperCase();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int radius = Math.min(arc, Math.min(width, height));
        Shape badge = new RoundRectangle2D.Float(0, 0, width, height, radius, radius);

        if (image == null) {
            g.setColor(fill);
            g.fill(badge);
            g.dispose();
            super.paintComponent(graphics);
            return;
        }

        // Center-crop the picture into the same badge dimensions used for initials.
        g.setClip(badge);
        int side = Math.min(width, height);
        double scale = Math.max(side / (double) image.getWidth(),
                side / (double) image.getHeight());
        int drawnWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int drawnHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
        g.drawImage(image, (width - drawnWidth) / 2, (height - drawnHeight) / 2,
                drawnWidth, drawnHeight, this);
        g.dispose();
    }
}
