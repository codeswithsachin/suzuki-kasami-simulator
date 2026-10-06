package skm.ui;

import skm.model.ProcState;

import java.awt.*;

/** Colours, fonts and drawing helpers shared by all panels (dark theme). */
final class Theme {
    static final Color BG = new Color(0x0B1220), PANEL = new Color(0x111A2C), CARD = new Color(0x16233A),
            LINE = new Color(0x24344F), TEXT = new Color(0xE6EDF7), MUTED = new Color(0x8FA1BC),
            TEAL = new Color(0x2DD4BF), GOLD = new Color(0xFBBF24), ORANGE = new Color(0xFB923C),
            GREEN = new Color(0x34D399), RED = new Color(0xF87171), BLUE = new Color(0x60A5FA),
            IDLE = new Color(0x3B4A68);

    static Font sans(int style, float size) { return new Font(Font.SANS_SERIF, style, 12).deriveFont(size); }

    static Font mono(int style, float size) { return new Font(Font.MONOSPACED, style, 12).deriveFont(size); }

    static Color state(ProcState s) { return s == ProcState.IN_CS ? GREEN : s == ProcState.WAITING ? ORANGE : IDLE; }

    static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a))); }

    static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    static void centered(Graphics2D g, String s, float cx, float baseline) {
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2f, baseline);
    }
}
