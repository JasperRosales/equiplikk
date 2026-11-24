package src.ui;

import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.prefs.Preferences;

public final class AppTheme {

    // ---- EquipLink palette (single source of truth for every screen) ----
    // Official brand colors: Blood Red #660000 and Pure White #FFFFFF.
    /** Brand primary: Blood Red. */
    public static final Color PRIMARY = new Color(0x66, 0x00, 0x00);
    /** Soft red derived from the brand color, used for accents and hovers. */
    public static final Color PRIMARY_LIGHT = new Color(0x8C, 0x1A, 0x1A);
    /** Deep Blood Red, used for pressed states and depth. */
    public static final Color PRIMARY_DARK = new Color(0x4D, 0x00, 0x00);
    /** Dark readable ink on Pure White surfaces. */
    public static final Color INK = new Color(0x2B, 0x1A, 0x1A);
    public static final Color INK_MUTED = new Color(0x6E, 0x5A, 0x5A);
    /** Pure White is the main surface for pages, panels, cards and dialogs. */
    public static final Color LIGHT_BG = new Color(0xFF, 0xFF, 0xFF);
    /** Subtle warm gray-red shade for alternating rows and sunken details. */
    public static final Color LIGHT_BG_DEEP = new Color(0xF8, 0xF4, 0xF4);
    public static final Color LIGHT_BORDER = new Color(0xE4, 0xD8, 0xD8);

    // Legacy aliases kept for compatibility.
    public static final Color BG = LIGHT_BG;
    public static final Color NAVY = INK;
    public static final Color BLUE = PRIMARY;

    // Exact colors from the original EquipLink dark-mode reference.
    private static final Color DARK_BACKGROUND = new Color(0x1A, 0x08, 0x08);
    private static final Color DARK_HEADER = DARK_BACKGROUND;
    private static final Color DARK_SURFACE = new Color(0x24, 0x0D, 0x0D);
    private static final Color DARK_SURFACE_DEEP = DARK_SURFACE;
    private static final Color DARK_SURFACE_RAISED = DARK_SURFACE;
    private static final Color DARK_INPUT = new Color(0x30, 0x12, 0x12);
    private static final Color DARK_BORDER = new Color(0x53, 0x20, 0x20);
    private static final Color DARK_PRIMARY_TEXT = Color.WHITE;
    private static final Color DARK_SECONDARY_TEXT = new Color(0xD9, 0xCA, 0xCA);
    private static final Color DARK_BUTTON = new Color(0x64, 0x05, 0x05);
    private static final Color DARK_BUTTON_HOVER = new Color(0x6C, 0x10, 0x10);
    private static final Color DARK_PRIMARY_SOFT = new Color(0x4A, 0x1E, 0x1E);
    private static final Color DARK_BUTTON_PRESSED = PRIMARY_DARK;
    private static final Color DARK_PRIMARY_INK = Color.WHITE;
    private static final Color DARK_SIDEBAR = PRIMARY;
    private static final Color DARK_SIDEBAR_MUTED = DARK_SECONDARY_TEXT;
    private static final Color DARK_SIDEBAR_DIVIDER = PRIMARY_LIGHT;
    private static final Color DARK_SIDEBAR_SHADOW_DARK = PRIMARY_DARK;
    private static final Color DARK_ACTIVE_NAV_BG = Color.WHITE;
    private static final Color DARK_ACTIVE_NAV_INK = PRIMARY;
    private static final Color DARK_SHADOW_LIGHT = new Color(0x3A, 0x16, 0x16);
    private static final Color DARK_SHADOW_DARK = new Color(0x10, 0x04, 0x04);
    private static final Color DARK_DANGER = new Color(0xC6, 0x28, 0x28);
    private static final Color DARK_SUCCESS = new Color(0x1B, 0x8F, 0x5A);
    private static final Color DARK_WARNING = new Color(0xC4, 0x76, 0x00);
    private static final Color DARK_TINT = new Color(0x9C, 0x59, 0x59);
    private static final Color DARK_BRAND_START = PRIMARY;
    private static final Color DARK_BRAND_END = PRIMARY_DARK;

    private static final Preferences PREFS = Preferences.userNodeForPackage(AppTheme.class);
    private static final String DARK_MODE_KEY = "darkMode";
    private static Font regularFont;
    private static Font semiboldFont;
    private static boolean darkMode = PREFS.getBoolean(DARK_MODE_KEY, false);

    private AppTheme() {
    }

    public static Color background() {
        return darkMode ? DARK_BACKGROUND : LIGHT_BG;
    }

    /** Web --header: a lifted wine tone that separates the top bar from the page. */
    public static Color headerBackground() {
        return darkMode ? DARK_HEADER : LIGHT_BG;
    }

    /** Slightly deeper page tone, used for gradients and sunken areas. */
    public static Color backgroundDeep() {
        return darkMode ? DARK_SURFACE_DEEP : LIGHT_BG_DEEP;
    }

    /** Cards and panels keep the dark-mode surface color; depth comes from shadows. */
    public static Color surface() {
        return darkMode ? DARK_SURFACE : LIGHT_BG;
    }

    /** Input / inset fill. */
    public static Color elevatedSurface() {
        return darkMode ? DARK_INPUT : new Color(0xFF, 0xF9, 0xF2);
    }

    /** Form controls: white in light mode so the existing look is unchanged. */
    public static Color inputBackground() {
        return darkMode ? DARK_INPUT : LIGHT_BG;
    }

    /** Raised feedback surface (web --surface-raised). */
    public static Color raisedSurface() {
        return darkMode ? DARK_SURFACE_RAISED : LIGHT_BG;
    }

    /** Authentication form area (web --surface-deep in dark mode). */
    public static Color authBackground() {
        return darkMode ? DARK_SURFACE_DEEP : LIGHT_BG;
    }

    /** Table headers match web th; light mode keeps the original white header. */
    public static Color tableHeaderBackground() {
        return darkMode ? DARK_SURFACE_DEEP : LIGHT_BG;
    }

    /** Alternating rows match web tbody tr:nth-child(even). */
    public static Color alternateRowBackground() {
        return darkMode ? DARK_SURFACE_DEEP : LIGHT_BG;
    }

    public static Color foreground() {
        return darkMode ? DARK_PRIMARY_TEXT : INK;
    }

    public static Color muted() {
        return darkMode ? DARK_SECONDARY_TEXT : INK_MUTED;
    }

    public static Color border() {
        return darkMode ? DARK_BORDER : LIGHT_BORDER;
    }

    /** Top-left highlight of a neumorphic surface. */
    public static Color shadowLight() {
        return darkMode ? DARK_SHADOW_LIGHT : Color.WHITE;
    }

    /** Bottom-right shade of a neumorphic surface. */
    public static Color shadowDark() {
        return darkMode ? DARK_SHADOW_DARK : LIGHT_BORDER;
    }

    public static Color primary() {
        return PRIMARY;
    }

    /** Primary button fill: dark maroon in dark mode, unchanged Blood Red in light mode. */
    public static Color buttonBackground() {
        return darkMode ? DARK_BUTTON : PRIMARY;
    }

    /** Primary button hover fill, used only by the dark maroon theme. */
    public static Color buttonHoverBackground() {
        return darkMode ? DARK_BUTTON_HOVER : PRIMARY;
    }

    /** Web --primary-pressed. */
    public static Color buttonPressedBackground() {
        return darkMode ? DARK_BUTTON_PRESSED : PRIMARY_DARK;
    }

    public static Color primaryDark() {
        return PRIMARY_DARK;
    }

    /** Web --primary-ink: readable red used for text, icons and links on surfaces. */
    public static Color primaryInk() {
        return darkMode ? DARK_PRIMARY_INK : PRIMARY;
    }

    public static Color sidebar() {
        return darkMode ? DARK_SIDEBAR : PRIMARY;
    }

    public static Color sidebarText() {
        return Color.WHITE;
    }

    public static Color sidebarMuted() {
        return darkMode ? DARK_SIDEBAR_MUTED : new Color(0xF0, 0xD7, 0xD7);
    }

    public static Color sidebarDivider() {
        return darkMode ? DARK_SIDEBAR_DIVIDER : PRIMARY_LIGHT;
    }

    public static Color sidebarShadowLight() {
        return darkMode ? DARK_SIDEBAR_DIVIDER : PRIMARY_LIGHT;
    }

    public static Color sidebarShadowDark() {
        return darkMode ? DARK_SIDEBAR_SHADOW_DARK : PRIMARY_DARK;
    }

    public static Color success() {
        return darkMode ? DARK_SUCCESS : new Color(0x1B, 0x8F, 0x5A);
    }

    public static Color warning() {
        return darkMode ? DARK_WARNING : new Color(0xC4, 0x76, 0x00);
    }

    public static Color danger() {
        return darkMode ? DARK_DANGER : new Color(0xC6, 0x28, 0x28);
    }

    /** Secondary accent used in charts and stat cards (web --tint in dark mode). */
    public static Color accent() {
        return primaryTint();
    }

    /** Web --primary-soft: selected rows, chips and active filters. */
    public static Color primarySoft() {
        return darkMode ? DARK_PRIMARY_SOFT : new Color(0xFA, 0xEF, 0xEF);
    }

    /** Web --tint: brand mixed 35% toward white in light mode, exact rose in dark mode. */
    public static Color primaryTint() {
        return darkMode ? DARK_TINT : mix(PRIMARY, Color.WHITE, 0.35f);
    }

    /** White or light red text on Blood Red surfaces. */
    public static Color onPrimary() {
        return Color.WHITE;
    }

    /** Muted text on branded surfaces; dark mode uses the web sidebar-muted value. */
    public static Color onPrimaryMuted() {
        return darkMode ? DARK_SIDEBAR_MUTED : alpha(Color.WHITE, 205);
    }

    /** Translucent white tile behind the logo mark. */
    public static Color brandBadgeBackground() {
        return alpha(Color.WHITE, 235);
    }

    /** Gradient endpoints for branded panels and the splash screen. */
    public static Color brandGradientStart() {
        return darkMode ? DARK_BRAND_START : PRIMARY;
    }

    public static Color brandGradientEnd() {
        return darkMode ? DARK_BRAND_END : PRIMARY_DARK;
    }

    /** Active navigation pill: web --sidebar-active-bg. */
    public static Color activeNavPill() {
        return darkMode ? DARK_ACTIVE_NAV_BG : alpha(Color.WHITE, 235);
    }

    /** Text and accent bar on the active navigation pill. */
    public static Color activeNavInk() {
        return darkMode ? DARK_ACTIVE_NAV_INK : PRIMARY;
    }

    /** Subtle neutral shade for dividers and secondary surfaces. */
    public static Color subtle() {
        return darkMode ? DARK_SURFACE_DEEP : LIGHT_BG_DEEP;
    }

    public static Color disabledForeground() {
        return new Color(0x8A, 0x77, 0x77);
    }

    public static Color focusColor() {
        return alpha(PRIMARY, 90);
    }

    public static Color focusedBorderColor() {
        return PRIMARY;
    }

    public static Color scrollThumb() {
        return alpha(PRIMARY_LIGHT, 130);
    }

    public static Color scrollHoverThumb() {
        return alpha(PRIMARY, 180);
    }

    public static Color scrollPressedThumb() {
        return PRIMARY;
    }

    public static Color tooltipBackground() {
        return darkMode ? new Color(0x4A, 0x14, 0x22) : INK;
    }

    public static Color alpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.max(0, Math.min(255, alpha)));
    }

    public static Color mix(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    public static boolean isDarkMode() {
        return darkMode;
    }

    public static void setDarkMode(boolean enabled) {
        darkMode = enabled;
        PREFS.putBoolean(DARK_MODE_KEY, enabled);
        install();
        com.formdev.flatlaf.FlatLaf.updateUI();
    }

    /** Installs the FlatLaf look and feel with EquipLink's colors and fonts. */
    public static void install() {
        javax.swing.UIManager.put("defaultFont", regular(13));
        javax.swing.UIManager.put("@accentColor", hex(primary()));
        javax.swing.UIManager.put("Component.arc", 14);
        javax.swing.UIManager.put("Button.arc", 14);
        javax.swing.UIManager.put("TextComponent.arc", 14);
        javax.swing.UIManager.put("CheckBox.arc", 6);
        javax.swing.UIManager.put("Component.focusWidth", 1);
        javax.swing.UIManager.put("Component.innerFocusWidth", 0);
        javax.swing.UIManager.put("Component.focusColor", focusColor());
        javax.swing.UIManager.put("Component.focusedBorderColor", focusedBorderColor());
        java.awt.Insets fieldMargin = new java.awt.Insets(8, 10, 8, 10);
        javax.swing.UIManager.put("TextField.margin", fieldMargin);
        javax.swing.UIManager.put("PasswordField.margin", fieldMargin);
        javax.swing.UIManager.put("FormattedTextField.margin", fieldMargin);
        javax.swing.UIManager.put("TextArea.margin", fieldMargin);
        javax.swing.UIManager.put("ComboBox.padding", new java.awt.Insets(6, 8, 6, 8));
        javax.swing.UIManager.put("Spinner.padding", new java.awt.Insets(6, 8, 6, 8));
        javax.swing.UIManager.put("Spinner.editorBorderPainted", false);
        javax.swing.UIManager.put("ScrollBar.width", 10);
        javax.swing.UIManager.put("ScrollBar.thumbArc", 999);
        javax.swing.UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
        javax.swing.UIManager.put("ScrollBar.showButtons", false);
        javax.swing.UIManager.put("ScrollBar.track", new Color(0, 0, 0, 0));
        javax.swing.UIManager.put("ScrollBar.thumb", scrollThumb());
        javax.swing.UIManager.put("ScrollBar.hoverThumb", scrollHoverThumb());
        javax.swing.UIManager.put("ScrollBar.pressedThumb", scrollPressedThumb());
        javax.swing.UIManager.put("OptionPane.buttonAreaBorder",
                javax.swing.BorderFactory.createEmptyBorder(12, 0, 0, 0));
        javax.swing.UIManager.put("Table.rowHeight", 34);
        javax.swing.UIManager.put("Table.showHorizontalLines", true);
        javax.swing.UIManager.put("Table.intercellSpacing", new java.awt.Dimension(0, 1));
        javax.swing.UIManager.put("TableHeader.height", 36);
        javax.swing.UIManager.put("TitlePane.unifiedBackground", true);
        installAlignmentFix();
        java.util.Map<String, String> extras = new java.util.HashMap<>();
        // Inputs read as soft wells; cards and dialogs use the lifted surface.
        String card = hex(surface());
        String well = hex(elevatedSurface());
        String text = hex(foreground());
        for (String component : new String[] {
                "ComboBox", "TextField", "PasswordField", "FormattedTextField",
                "TextArea", "Spinner", "EditorPane", "TextPane" }) {
            extras.put(component + ".background", well);
            extras.put(component + ".foreground", text);
        }
        for (String component : new String[] {
                "List", "PopupMenu", "MenuItem", "Button", "ToggleButton", "Table" }) {
            extras.put(component + ".background", card);
            extras.put(component + ".foreground", text);
        }
        extras.put("ComboBox.buttonBackground", well);
        extras.put("ComboBox.buttonEditableBackground", well);
        extras.put("ComboBox.popupBackground", card);
        extras.put("ComboBox.selectionBackground", hex(primary()));
        extras.put("ComboBox.selectionForeground", hex(onPrimary()));
        extras.put("List.selectionBackground", hex(primary()));
        extras.put("List.selectionForeground", hex(onPrimary()));
        extras.put("MenuItem.selectionBackground", hex(primary()));
        extras.put("MenuItem.selectionForeground", hex(onPrimary()));
        extras.put("ToggleButton.selectedBackground", hex(primarySoft()));
        extras.put("ToggleButton.selectedForeground", hex(primaryInk()));
        extras.put("Spinner.buttonBackground", well);
        extras.put("Component.borderColor", hex(border()));
        extras.put("Component.disabledBorderColor", hex(border()));
        extras.put("Component.disabledForeground", hex(disabledForeground()));
        extras.put("Button.disabledBackground", hex(subtle()));
        extras.put("Button.disabledForeground", hex(disabledForeground()));
        extras.put("Panel.background", card);
        extras.put("OptionPane.background", card);
        extras.put("RootPane.background", card);
        extras.put("TitlePane.background", card);
        extras.put("TableHeader.background", hex(tableHeaderBackground()));
        extras.put("Table.gridColor", hex(border()));
        extras.put("Table.alternateRowBackground", hex(alternateRowBackground()));
        extras.put("Table.selectionBackground", hex(primary()));
        extras.put("Table.selectionForeground", hex(onPrimary()));
        extras.put("Separator.foreground", hex(border()));
        extras.put("ToolTip.background", hex(tooltipBackground()));
        extras.put("ToolTip.foreground", hex(onPrimary()));
        extras.put("Label.foreground", text);
        extras.put("CheckBox.foreground", text);
        extras.put("RadioButton.foreground", text);
        extras.put("TitledBorder.titleColor", text);
        extras.put("TableHeader.foreground", hex(muted()));
        extras.put("TextComponent.placeholderForeground", hex(muted()));
        com.formdev.flatlaf.FlatLaf.setGlobalExtraDefaults(extras);
        if (darkMode) {
            com.formdev.flatlaf.FlatDarkLaf.setup();
        } else {
            com.formdev.flatlaf.FlatLightLaf.setup();
        }
    }

    public static Font regular(float size) {
        loadFonts();
        return regularFont.deriveFont(size);
    }

    public static Font semibold(float size) {
        loadFonts();
        return semiboldFont.deriveFont(size);
    }

    private static boolean alignmentFixInstalled;

    /**
     * EquipLink lays out pages with vertical BoxLayouts and expects every row to be
     * left-aligned. Swing defaults most components to centre alignment, and mixing the
     * two makes BoxLayout offset rows. This aligns children of vertical BoxLayouts left.
     */
    private static void installAlignmentFix() {
        if (alignmentFixInstalled) {
            return;
        }
        alignmentFixInstalled = true;
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event.getID() != java.awt.event.ContainerEvent.COMPONENT_ADDED) {
                return;
            }
            java.awt.event.ContainerEvent added = (java.awt.event.ContainerEvent) event;
            java.awt.Container parent = added.getContainer();
            if (!(parent instanceof javax.swing.JPanel)
                    || !(added.getChild() instanceof javax.swing.JComponent)
                    || !(parent.getLayout() instanceof javax.swing.BoxLayout)) {
                return;
            }
            int axis = ((javax.swing.BoxLayout) parent.getLayout()).getAxis();
            if (axis == javax.swing.BoxLayout.Y_AXIS || axis == javax.swing.BoxLayout.PAGE_AXIS) {
                ((javax.swing.JComponent) added.getChild())
                        .setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            }
        }, java.awt.AWTEvent.CONTAINER_EVENT_MASK);
    }

    private static void loadFonts() {
        if (regularFont != null) {
            return;
        }

        Font regular = loadFont("Poppins-Regular.ttf");
        Font semibold = loadFont("Poppins-SemiBold.ttf");
        if (regular == null) {
            regular = new Font("SansSerif", Font.PLAIN, 12);
        }
        if (semibold == null) {
            semibold = regular.deriveFont(Font.BOLD);
        }
        java.awt.GraphicsEnvironment environment =
                java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
        environment.registerFont(regular);
        environment.registerFont(semibold);
        regularFont = regular;
        semiboldFont = semibold;
    }

    private static Font loadFont(String name) {
        try (InputStream resource = AppTheme.class.getResourceAsStream("/src/assets/" + name)) {
            if (resource != null) {
                return Font.createFont(Font.TRUETYPE_FONT, resource);
            }
        } catch (IOException | java.awt.FontFormatException exception) {
            System.err.println("Unable to load " + name + ": " + exception.getMessage());
        }

        Path fontPath = Path.of("src", "assets", name);
        if (Files.isRegularFile(fontPath)) {
            try (InputStream font = Files.newInputStream(fontPath)) {
                return Font.createFont(Font.TRUETYPE_FONT, font);
            } catch (IOException | java.awt.FontFormatException exception) {
                System.err.println("Unable to load " + fontPath + ": " + exception.getMessage());
            }
        }

        return null;
    }
}
