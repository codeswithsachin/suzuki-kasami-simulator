package skm.ui;

import skm.engine.Simulation;
import skm.model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Animated network view. Every message is drawn at the exact position given by the simulation's
 * virtual clock, so animation and algorithm can never get out of sync (and pausing freezes everything).
 */
final class NetworkPanel extends JPanel {
    private final Supplier<Simulation> sim;
    private final java.util.function.BiConsumer<Integer, Point> onNodeClick;
    private BufferedImage bg;
    private Point2D.Double[] pos = new Point2D.Double[0];
    private int radius = 32, hover = -1;

    NetworkPanel(Supplier<Simulation> sim, java.util.function.BiConsumer<Integer, Point> onNodeClick) {
        this.sim = sim; this.onNodeClick = onNodeClick;
        setBackground(Theme.BG);
        setPreferredSize(new Dimension(640, 470));
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int h = nodeAt(e.getPoint());
                if (h != hover) { hover = h; setCursor(Cursor.getPredefinedCursor(h >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR)); }
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int h = nodeAt(e.getPoint());
                if (h >= 0) NetworkPanel.this.onNodeClick.accept(h, e.getPoint());
            }
        });
    }

    private int nodeAt(Point p) {
        for (int i = 0; i < pos.length; i++) if (pos[i] != null && pos[i].distance(p) <= radius + 4) return i;
        return -1;
    }

    private void layoutNodes(int n) {
        int w = getWidth(), h = getHeight();
        double R = Math.min(w, h) * 0.36;
        radius = (int) Math.max(20, Math.min(36, 2 * Math.PI * R / n * 0.34));
        pos = new Point2D.Double[n];
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(-90 + 360.0 * i / n);
            pos[i] = new Point2D.Double(w / 2.0 + R * Math.cos(a), h / 2.0 + 4 + R * Math.sin(a));
        }
    }

    private void paintBackground(Graphics2D g) {
        int w = getWidth(), h = getHeight();
        if (bg == null || bg.getWidth() != w || bg.getHeight() != h) {
            bg = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
            Graphics2D b = bg.createGraphics();
            Theme.quality(b);
            b.setPaint(new GradientPaint(0, 0, new Color(0x0E1729), 0, h, new Color(0x090F1B)));
            b.fillRect(0, 0, w, h);
            b.setPaint(new RadialGradientPaint(w / 2f, h / 2f, Math.max(w, h) / 1.6f, new float[]{0f, 1f},
                    new Color[]{new Color(0x1B2B4A, false), new Color(0x090F1B)}));
            b.setComposite(AlphaComposite.SrcOver.derive(0.5f));
            b.fillRect(0, 0, w, h);
            b.setComposite(AlphaComposite.SrcOver);
            b.setColor(new Color(255, 255, 255, 14));
            for (int x = 16; x < w; x += 32) for (int y = 16; y < h; y += 32) b.fillRect(x, y, 1, 1);
            b.dispose();
        }
        g.drawImage(bg, 0, 0, null);
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Theme.quality(g);
        paintBackground(g);
        Simulation s = sim.get();
        int n = s.n;
        layoutNodes(n);

        // complete graph edges
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(0x22314D));
        for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++)
            g.draw(new Line2D.Double(pos[i], pos[j]));

        // messages in flight
        for (Message m : s.flight) drawMessage(g, s, m);

        // expanding rings for recent events
        for (int k = s.effects.size() - 1; k >= 0; k--) {
            Simulation.Effect e = s.effects.get(k);
            double age = (s.time - e.time) / 0.7;
            if (age > 1) break;
            if (age < 0) continue;
            Color c = switch (e.kind) {
                case RECV_REQ, SEND_REQ -> Theme.TEAL;
                case RECV_TOKEN, SEND_TOKEN -> Theme.GOLD;
                case ENTER_CS -> Theme.GREEN;
                case EXIT_CS -> Theme.BLUE;
                default -> Theme.ORANGE;
            };
            double rr = radius + 4 + age * 26;
            g.setStroke(new BasicStroke((float) (3 - 2 * age)));
            g.setColor(Theme.alpha(c, (int) (200 * (1 - age))));
            g.draw(new Ellipse2D.Double(pos[e.proc].x - rr, pos[e.proc].y - rr, rr * 2, rr * 2));
        }

        for (int i = 0; i < n; i++) drawNode(g, s, i);
        drawLegend(g);
        g.dispose();
    }

    private void drawMessage(Graphics2D g, Simulation s, Message m) {
        Point2D.Double a = pos[m.from], b = pos[m.to];
        double p = s.progress(m);
        p = p * p * (3 - 2 * p);                                  // smooth start and stop
        double dx = b.x - a.x, dy = b.y - a.y, len = Math.max(1, Math.hypot(dx, dy));
        double off = m.type == MsgType.TOKEN ? 0 : (m.from < m.to ? 7 : -7);
        double ox = -dy / len * off, oy = dx / len * off;
        double sx = a.x + ox, sy = a.y + oy, x = sx + dx * p, y = sy + dy * p;
        boolean tok = m.type == MsgType.TOKEN;
        Color c = tok ? Theme.GOLD : Theme.TEAL;
        g.setStroke(new BasicStroke(tok ? 3f : 1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(Theme.alpha(c, tok ? 110 : 70));
        g.draw(new Line2D.Double(sx, sy, x, y));
        if (tok) {
            g.setPaint(new RadialGradientPaint((float) x, (float) y, 26f, new float[]{0f, 1f},
                    new Color[]{Theme.alpha(Theme.GOLD, 150), Theme.alpha(Theme.GOLD, 0)}));
            g.fill(new Ellipse2D.Double(x - 26, y - 26, 52, 52));
            g.setColor(Theme.GOLD);
            g.fill(new Ellipse2D.Double(x - 12, y - 12, 24, 24));
            g.setColor(new Color(0x3B2A00)); g.setFont(Theme.sans(Font.BOLD, 13f));
            Theme.centered(g, "T", (float) x, (float) y + 5);
        } else {
            g.setColor(Theme.TEAL);
            g.fill(new Ellipse2D.Double(x - 9, y - 9, 18, 18));
            g.setColor(new Color(0x052824)); g.setFont(Theme.sans(Font.BOLD, 11f));
            Theme.centered(g, "" + m.sn, (float) x, (float) y + 4);
        }
    }

    private void drawNode(Graphics2D g, Simulation s, int i) {
        Site p = s.procs[i];
        double x = pos[i].x, y = pos[i].y, r = radius;
        Color sc = Theme.state(p.state);
        if (p.state != ProcState.IDLE) {                           // soft glow
            double pulse = p.state == ProcState.IN_CS ? 0.6 + 0.4 * Math.sin(s.time * 5) : 1;
            g.setPaint(new RadialGradientPaint((float) x, (float) y, (float) r + 22, new float[]{0.6f, 1f},
                    new Color[]{Theme.alpha(sc, (int) (90 * pulse)), Theme.alpha(sc, 0)}));
            g.fill(new Ellipse2D.Double(x - r - 22, y - r - 22, 2 * (r + 22), 2 * (r + 22)));
        }
        g.setPaint(new GradientPaint(0, (float) (y - r), new Color(0x1E2F50), 0, (float) (y + r), new Color(0x121C33)));
        g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        g.setStroke(new BasicStroke(p.state == ProcState.IDLE ? 2.5f : 4.5f));
        g.setColor(sc);
        g.draw(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        if (s.holder == i) {                                       // token holder ring
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{5f, 4f}, 0f));
            g.setColor(Theme.GOLD);
            g.draw(new Ellipse2D.Double(x - r - 8, y - r - 8, 2 * (r + 8), 2 * (r + 8)));
            g.setColor(Theme.GOLD); g.fill(new Ellipse2D.Double(x + r * 0.55, y - r * 1.15, 22, 22));
            g.setColor(new Color(0x3B2A00)); g.setFont(Theme.sans(Font.BOLD, 13f));
            Theme.centered(g, "T", (float) (x + r * 0.55 + 11), (float) (y - r * 1.15 + 16));
        }
        g.setColor(Theme.TEXT); g.setFont(Theme.sans(Font.BOLD, radius > 26 ? 17f : 14f));
        Theme.centered(g, "P" + i, (float) x, (float) y + 6);
        g.setFont(Theme.sans(Font.BOLD, 12f)); g.setColor(sc == Theme.IDLE ? Theme.MUTED : sc);
        String label = p.state == ProcState.IN_CS ? "IN CS" : p.state == ProcState.WAITING ? "WAITING" : "idle";
        Theme.centered(g, label, (float) x, (float) (y + r + 17));
        g.setFont(Theme.mono(Font.PLAIN, 11f)); g.setColor(Theme.MUTED);
        Theme.centered(g, "seq " + p.rn[i], (float) x, (float) (y + r + 31));
    }

    private void drawLegend(Graphics2D g) {
        int x = 16, y = getHeight() - 16;
        g.setFont(Theme.sans(Font.PLAIN, 12f));
        Object[][] items = {{Theme.TEAL, "REQUEST(i, seq)"}, {Theme.GOLD, "TOKEN"}, {Theme.ORANGE, "waiting"}, {Theme.GREEN, "in CS"}};
        for (Object[] it : items) {
            g.setColor((Color) it[0]); g.fill(new Ellipse2D.Double(x, y - 9, 10, 10));
            g.setColor(Theme.MUTED); g.drawString((String) it[1], x + 16, y);
            x += 26 + g.getFontMetrics().stringWidth((String) it[1]);
        }
        g.setColor(Theme.MUTED);
        String hint = "click a process to request / release";
        g.drawString(hint, getWidth() - g.getFontMetrics().stringWidth(hint) - 16, y);
    }
}
