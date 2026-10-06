package skm.ui;

import javax.swing.*;
import java.awt.*;

/** Flat rounded button that matches the dark theme. */
final class Btn extends JButton {
    private final Color base;

    Btn(String text, Color base) {
        super(text);
        this.base = base;
        setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false); setFocusable(false);
        setForeground(Color.WHITE); setFont(Theme.sans(Font.BOLD, 13f));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    @Override public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(fm.stringWidth(getText()) + 30, 34);
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Theme.quality(g);
        Color c = !isEnabled() ? new Color(0x1B2740) : getModel().isPressed() ? base.darker()
                : getModel().isRollover() ? base.brighter() : base;
        g.setColor(c);
        g.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
        g.setColor(isEnabled() ? getForeground() : new Color(0x5B6B88));
        g.setFont(getFont());
        Theme.centered(g, getText(), getWidth() / 2f, (getHeight() + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2f);
        g.dispose();
    }
}
