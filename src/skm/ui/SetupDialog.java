package skm.ui;

import skm.engine.Config;

import javax.swing.*;
import java.awt.*;

/** Dialog to change every parameter of the simulation. */
final class SetupDialog {
    static Config show(JFrame owner, Config cur) {
        JDialog d = new JDialog(owner, "Simulation setup", true);
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Theme.PANEL);
        p.setBorder(BorderFactory.createEmptyBorder(16, 18, 12, 18));

        JSpinner n = new JSpinner(new SpinnerNumberModel(cur.n, 2, 10, 1));
        JSpinner holder = new JSpinner(new SpinnerNumberModel(cur.initialHolder, 0, 9, 1));
        JSpinner seed = new JSpinner(new SpinnerNumberModel((int) cur.seed, 0, 999999, 1));
        JSpinner minD = new JSpinner(new SpinnerNumberModel(cur.minDelay, 0.05, 20.0, 0.1));
        JSpinner maxD = new JSpinner(new SpinnerNumberModel(cur.maxDelay, 0.05, 20.0, 0.1));
        JCheckBox fifo = check("FIFO channels (messages never overtake each other)", cur.fifo);
        JCheckBox auto = check("Leave the CS automatically", cur.autoRelease);
        JSpinner hold = new JSpinner(new SpinnerNumberModel(cur.holdTime, 0.1, 60.0, 0.5));
        JCheckBox work = check("Random workload (processes request by themselves)", cur.autoWorkload);
        JSpinner mean = new JSpinner(new SpinnerNumberModel(cur.meanInterval, 0.2, 60.0, 0.5));

        int y = 0;
        y = row(p, y, "Number of processes (2 to 10)", n);
        y = row(p, y, "Process that starts with the token", holder);
        y = row(p, y, "Random seed (same seed = same run)", seed);
        y = row(p, y, "Network delay: minimum (s)", minD);
        y = row(p, y, "Network delay: maximum (s)", maxD);
        y = wide(p, y, fifo);
        y = wide(p, y, auto);
        y = row(p, y, "Time spent inside the CS (s)", hold);
        y = wide(p, y, work);
        y = row(p, y, "Mean time between random requests (s)", mean);

        Config[] out = {null};
        Btn ok = new Btn("Apply and restart", Theme.TEAL.darker());
        Btn cancel = new Btn("Cancel", new Color(0x2A3958));
        ok.addActionListener(e -> {
            Config c = new Config();
            c.n = (Integer) n.getValue(); c.initialHolder = (Integer) holder.getValue(); c.seed = (Integer) seed.getValue();
            c.minDelay = (Double) minD.getValue(); c.maxDelay = (Double) maxD.getValue(); c.fifo = fifo.isSelected();
            c.autoRelease = auto.isSelected(); c.holdTime = (Double) hold.getValue();
            c.autoWorkload = work.isSelected(); c.meanInterval = (Double) mean.getValue();
            c.sanitize(); out[0] = c; d.dispose();
        });
        cancel.addActionListener(e -> d.dispose());
        JPanel bp = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); bp.setOpaque(false);
        bp.add(cancel); bp.add(ok);
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.gridy = y; gc.gridwidth = 2; gc.fill = GridBagConstraints.HORIZONTAL; gc.insets = new Insets(14, 0, 0, 0);
        p.add(bp, gc);
        d.setContentPane(p); d.pack(); d.setLocationRelativeTo(owner); d.setVisible(true);
        return out[0];
    }

    private static JCheckBox check(String t, boolean v) {
        JCheckBox c = new JCheckBox(t, v); c.setOpaque(false); c.setForeground(Theme.TEXT); c.setFocusPainted(false); return c;
    }

    private static int row(JPanel p, int y, String label, JComponent c) {
        JLabel l = new JLabel(label); l.setForeground(Theme.TEXT);
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.gridy = y; g.anchor = GridBagConstraints.WEST; g.insets = new Insets(4, 0, 4, 18); p.add(l, g);
        g.gridx = 1; g.insets = new Insets(4, 0, 4, 0); g.fill = GridBagConstraints.HORIZONTAL;
        ((JSpinner) c).setPreferredSize(new Dimension(90, 26)); p.add(c, g);
        return y + 1;
    }

    private static int wide(JPanel p, int y, JComponent c) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.gridy = y; g.gridwidth = 2; g.anchor = GridBagConstraints.WEST; g.insets = new Insets(4, 0, 4, 0);
        p.add(c, g); return y + 1;
    }
}
