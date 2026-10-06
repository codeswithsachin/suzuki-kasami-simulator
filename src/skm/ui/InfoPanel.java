package skm.ui;

import skm.engine.Metrics;
import skm.engine.Simulation;
import skm.model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.Locale;
import java.util.function.Supplier;

/** Right-hand side cards: RN table of every process, the token (LN and queue Q) and live metrics. */
final class InfoPanel extends JPanel {
    private final Supplier<Simulation> sim;

    InfoPanel(Supplier<Simulation> sim) { this.sim = sim; setBackground(Theme.BG); }

    @Override public Dimension getPreferredSize() {
        int n = sim.get().n;
        return new Dimension(400, 12 + (44 + 22 + n * 22 + 10) + 10 + 108 + 10 + 178 + 6);
    }

    private void card(Graphics2D g, int x, int y, int w, int h, String title) {
        g.setColor(Theme.CARD); g.fill(new RoundRectangle2D.Double(x, y, w, h, 14, 14));
        g.setColor(Theme.LINE); g.setStroke(new BasicStroke(1f)); g.draw(new RoundRectangle2D.Double(x + .5, y + .5, w - 1, h - 1, 14, 14));
        g.setColor(Theme.TEXT); g.setFont(Theme.sans(Font.BOLD, 13f)); g.drawString(title, x + 14, y + 24);
    }

    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        Theme.quality(g);
        Simulation s = sim.get();
        int w = getWidth() - 12, x = 6, y = 6;
        y = processes(g, s, x, y, w) + 10;
        y = token(g, s, x, y, w) + 10;
        metrics(g, s, x, y, w);
        g.dispose();
    }

    private int processes(Graphics2D g, Simulation s, int x, int y, int w) {
        int n = s.n, h = 44 + 22 + n * 22 + 10;
        card(g, x, y, w, h, "Processes and their RN arrays");
        int c0 = x + 14, c1 = c0 + 34, c2 = c1 + 74, cw = Math.max(20, (x + w - 12 - c2) / n);
        int hy = y + 44 + 12;
        g.setFont(Theme.mono(Font.PLAIN, 11f)); g.setColor(Theme.MUTED);
        g.drawString("State", c1, hy);
        for (int j = 0; j < n; j++) Theme.centered(g, "P" + j, c2 + cw * j + cw / 2f, hy);
        for (int i = 0; i < n; i++) {
            Site p = s.procs[i];
            int ry = y + 44 + 22 + i * 22;
            if (p.state != ProcState.IDLE) {
                g.setColor(Theme.alpha(Theme.state(p.state), 36));
                g.fill(new RoundRectangle2D.Double(x + 6, ry - 2, w - 12, 21, 8, 8));
            }
            g.setFont(Theme.sans(Font.BOLD, 12f)); g.setColor(Theme.TEXT); g.drawString("P" + i, c0, ry + 13);
            if (s.holder == i) { g.setColor(Theme.GOLD); g.fillOval(c0 + 20, ry + 4, 9, 9); }
            g.setColor(Theme.state(p.state) == Theme.IDLE ? Theme.MUTED : Theme.state(p.state));
            g.setFont(Theme.sans(Font.BOLD, 11f));
            g.drawString(p.state == ProcState.IN_CS ? "IN CS" : p.state == ProcState.WAITING ? "WAITING" : "idle", c1, ry + 13);
            g.setFont(Theme.mono(Font.PLAIN, 12f));
            for (int j = 0; j < n; j++) {
                g.setColor(j == i ? Theme.TEAL : p.rn[j] > 0 ? Theme.TEXT : new Color(0x51617F));
                Theme.centered(g, "" + p.rn[j], c2 + cw * j + cw / 2f, ry + 13);
            }
        }
        return y + h;
    }

    private int token(Graphics2D g, Simulation s, int x, int y, int w) {
        int h = 108;
        card(g, x, y, w, h, "Token");
        Token t = s.currentToken();
        String where = s.holder >= 0 ? "held by P" + s.holder : "in transit";
        for (Message m : s.flight) if (m.type == MsgType.TOKEN) where = "in transit  P" + m.from + " \u2192 P" + m.to;
        g.setFont(Theme.mono(Font.PLAIN, 12f));
        g.setColor(Theme.MUTED); g.drawString("location", x + 14, y + 46);
        g.setColor(Theme.GOLD); g.drawString(where, x + 84, y + 46);
        g.setColor(Theme.MUTED); g.drawString("LN", x + 14, y + 68);
        g.setColor(Theme.TEXT); g.drawString(t == null ? "-" : java.util.Arrays.toString(t.ln), x + 84, y + 68);
        g.setColor(Theme.MUTED); g.drawString("queue Q", x + 14, y + 92);
        int cx = x + 84;
        if (t == null || t.queue.isEmpty()) { g.setColor(new Color(0x51617F)); g.drawString("empty", cx, y + 92); }
        else for (int k : t.queue) {
            g.setColor(Theme.alpha(Theme.ORANGE, 60)); g.fill(new RoundRectangle2D.Double(cx, y + 78, 32, 20, 10, 10));
            g.setColor(Theme.ORANGE); g.setFont(Theme.sans(Font.BOLD, 12f)); Theme.centered(g, "P" + k, cx + 16, y + 92);
            cx += 38;
        }
        return y + h;
    }

    private void row(Graphics2D g, int x, int w, int y, String k, String v, Color c) {
        g.setFont(Theme.sans(Font.PLAIN, 12f)); g.setColor(Theme.MUTED); g.drawString(k, x + 14, y);
        g.setFont(Theme.mono(Font.BOLD, 12f)); g.setColor(c);
        g.drawString(v, x + w - 14 - g.getFontMetrics().stringWidth(v), y);
    }

    private void metrics(Graphics2D g, Simulation s, int x, int y, int w) {
        int h = 178;
        card(g, x, y, w, h, "Live metrics");
        Metrics m = s.m;
        int ry = y + 46;
        row(g, x, w, ry, "Messages sent (request + token)", (m.sentReq + m.sentTok) + "  (" + m.sentReq + " + " + m.sentTok + ")", Theme.TEXT); ry += 20;
        row(g, x, w, ry, "CS entries (without messages)", m.csEntries + "  (" + m.immediate + ")", Theme.TEXT); ry += 20;
        row(g, x, w, ry, "Messages per CS entry", String.format(Locale.ROOT, "%.2f   max N = %d", m.msgsPerEntry(), s.n), Theme.TEAL); ry += 20;
        row(g, x, w, ry, "Ricart-Agrawala would need", "2(N-1) = " + 2 * (s.n - 1) + " per entry", Theme.MUTED); ry += 20;
        row(g, x, w, ry, "Waiting time  avg / max", String.format(Locale.ROOT, "%.2f s / %.2f s", m.avgWait(), m.maxWait), Theme.TEXT); ry += 24;
        boolean ok = s.violations == 0;
        g.setColor(Theme.alpha(ok ? Theme.GREEN : Theme.RED, 40));
        g.fill(new RoundRectangle2D.Double(x + 10, ry - 15, w - 20, 24, 10, 10));
        g.setFont(Theme.sans(Font.BOLD, 12f)); g.setColor(ok ? Theme.GREEN : Theme.RED);
        g.drawString(ok ? "SAFE: 1 token, mutual exclusion holds" : "VIOLATION: " + s.violation, x + 20, ry + 2);
    }
}
