package skm.ui;

import skm.engine.Simulation;
import skm.model.ProcState;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Supplier;

/** Scrolling timeline: one lane per process showing idle / waiting / in-CS over the last seconds. */
final class TimelinePanel extends JPanel {
    private static final double WINDOW = 24.0;
    private final Supplier<Simulation> sim;

    TimelinePanel(Supplier<Simulation> sim) {
        this.sim = sim;
        setBackground(Theme.PANEL);
        setPreferredSize(new Dimension(640, 170));
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Theme.quality(g);
        g.setColor(Theme.PANEL); g.fillRect(0, 0, getWidth(), getHeight());
        Simulation s = sim.get();
        int n = s.n, left = 40, right = 14, top = 26, bottom = 8;
        double laneH = Math.min(18, (getHeight() - top - bottom) / (double) n);
        double pw = getWidth() - left - right;
        double t1 = s.time, t0 = t1 - WINDOW;
        g.setFont(Theme.sans(Font.BOLD, 12f)); g.setColor(Theme.TEXT);
        g.drawString("Timeline", 14, 17);
        g.setFont(Theme.mono(Font.PLAIN, 10f)); g.setColor(Theme.MUTED);
        for (double t = Math.ceil(Math.max(0, t0) / 4) * 4; t <= t1; t += 4) {           // time grid
            int x = (int) (left + (t - t0) / WINDOW * pw);
            g.setColor(new Color(255, 255, 255, 16)); g.drawLine(x, top - 4, x, (int) (top + laneH * n));
            g.setColor(Theme.MUTED); g.drawString(String.format("%.0fs", t), x - 8, (int) (top + laneH * n) + 14);
        }
        for (int i = 0; i < n; i++) {                                         // lane backgrounds + labels
            double y = top + i * laneH;
            g.setColor(new Color(0x18243B)); g.fill(new RoundRectangle2D.Double(left, y + 2, pw, laneH - 4, 6, 6));
            g.setColor(Theme.MUTED); g.setFont(Theme.sans(Font.BOLD, 11f));
            g.drawString("P" + i, 12, (int) (y + laneH / 2 + 4));
        }
        double[] end = new double[n]; boolean[] done = new boolean[n]; int remaining = n;
        java.util.Arrays.fill(end, t1);
        for (int k = s.trans.size() - 1; k >= 0 && remaining > 0; k--) {
            Simulation.Tr tr = s.trans.get(k);
            if (done[tr.proc]) continue;
            double a = Math.max(tr.time, t0), b = end[tr.proc];
            if (tr.state != ProcState.IDLE && b > a) {
                double x1 = left + (a - t0) / WINDOW * pw, x2 = left + (b - t0) / WINDOW * pw;
                double y = top + tr.proc * laneH;
                g.setColor(Theme.state(tr.state));
                g.fill(new RoundRectangle2D.Double(x1, y + 3, Math.max(2, x2 - x1), laneH - 6, 6, 6));
            }
            end[tr.proc] = tr.time;
            if (tr.time <= t0) { done[tr.proc] = true; remaining--; }
        }
        g.setColor(Theme.TEXT); g.setStroke(new BasicStroke(2f));                 // "now" marker
        int nx = (int) (left + pw);
        g.drawLine(nx, top - 2, nx, (int) (top + laneH * n) + 2);
        g.dispose();
    }
}
