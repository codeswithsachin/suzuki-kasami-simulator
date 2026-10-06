package skm.ui;

import skm.engine.Config;
import skm.engine.Simulation;
import skm.io.ScenarioIO;
import skm.model.ProcState;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.List;

/**
 * Main window. A single 60 fps timer converts real time into virtual time and drives the simulation:
 *   PLAYING  - clock runs continuously at the chosen speed
 *   STEPPING - clock glides smoothly to the next event, processes it and pauses
 *   PAUSED   - clock is frozen (nothing moves, nothing is redrawn)
 */
public final class MainFrame extends JFrame {
    private enum Mode { PAUSED, PLAYING, STEPPING }

    private Simulation sim = new Simulation(new Config(), null);
    private Mode mode = Mode.PAUSED;
    private double speed = 1.0, stepTarget;
    private long lastNanos = System.nanoTime(), shownVersion = -1;
    private int logShown;
    private String status = "";

    private final NetworkPanel net = new NetworkPanel(() -> sim, this::nodeMenu);
    private final TimelinePanel timeline = new TimelinePanel(() -> sim);
    private final InfoPanel info = new InfoPanel(() -> sim);
    private final JTextArea logArea = new JTextArea();
    private final TopBar top = new TopBar();
    private final Btn backBtn = new Btn("Step back", new Color(0x2A3958)), playBtn = new Btn("Play", new Color(0x0F766E)),
            stepBtn = new Btn("Step", new Color(0x1D4ED8)), reqBtn = new Btn("Request CS", new Color(0xC2410C)),
            relBtn = new Btn("Release CS", new Color(0x15803D)), setupBtn = new Btn("Setup...", new Color(0x2A3958)),
            scenBtn = new Btn("Scenarios", new Color(0x2A3958)), resetBtn = new Btn("Restart", new Color(0x2A3958));
    private final JSlider speedSlider = new JSlider(25, 400, 100);
    private final JLabel speedLabel = new JLabel("Speed 1.00x");
    private final JComboBox<String> procBox = new JComboBox<>();
    private final JCheckBox autoRelease = new JCheckBox("Auto leave CS"), workload = new JCheckBox("Random requests");
    private final JSplitPane split;

    public MainFrame() {
        super("Suzuki-Kasami Algorithm Simulator");
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());

        logArea.setEditable(false); logArea.setLineWrap(true); logArea.setWrapStyleWord(true);
        logArea.setBackground(new Color(0x0F1729)); logArea.setForeground(new Color(0xC7D2E5));
        logArea.setFont(Theme.mono(Font.PLAIN, 12f)); logArea.setMargin(new Insets(8, 10, 8, 10));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Theme.LINE), " Event log ",
                0, 0, Theme.sans(Font.BOLD, 12f), Theme.TEXT));
        logScroll.setBackground(Theme.BG);
        JScrollPane infoScroll = new JScrollPane(info, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        infoScroll.setBorder(null); infoScroll.getViewport().setBackground(Theme.BG);
        infoScroll.getVerticalScrollBar().setUnitIncrement(16);
        split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, infoScroll, logScroll);
        split.setBorder(null); split.setDividerSize(6); split.setContinuousLayout(true); split.setBackground(Theme.BG);
        split.setPreferredSize(new Dimension(420, 600));

        JPanel left = new JPanel(new BorderLayout());
        left.add(net, BorderLayout.CENTER); left.add(timeline, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH); add(left, BorderLayout.CENTER); add(split, BorderLayout.EAST); add(controls(), BorderLayout.SOUTH);

        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(1320, scr.width - 40), Math.min(860, scr.height - 60));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        bindKeys();
        newSimulation(new Config(), null);
        Timer timer = new Timer(15, e -> tick());
        timer.setCoalesce(true);
        timer.start();
    }

    // ------------------------------------------------------------------
    // Layout of the control area
    // ------------------------------------------------------------------
    private JPanel controls() {
        JPanel p = new JPanel(new GridLayout(2, 1));
        p.setBackground(Theme.PANEL);
        p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE));

        speedSlider.setOpaque(false); speedSlider.setFocusable(false); speedSlider.setPreferredSize(new Dimension(170, 26));
        speedLabel.setForeground(Theme.MUTED); speedLabel.setFont(Theme.mono(Font.PLAIN, 12f));
        speedLabel.setPreferredSize(new Dimension(96, 20));
        speedSlider.addChangeListener(e -> { speed = speedSlider.getValue() / 100.0; speedLabel.setText(String.format("Speed %.2fx", speed)); });

        procBox.setFocusable(false); procBox.setPreferredSize(new Dimension(70, 30));
        for (JCheckBox c : new JCheckBox[]{autoRelease, workload}) { c.setOpaque(false); c.setForeground(Theme.TEXT); c.setFocusable(false); }

        JPanel r1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8)); r1.setOpaque(false);
        r1.add(backBtn); r1.add(playBtn); r1.add(stepBtn); r1.add(speedSlider); r1.add(speedLabel);
        r1.add(label("   Process")); r1.add(procBox); r1.add(reqBtn); r1.add(relBtn);
        JPanel r2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4)); r2.setOpaque(false);
        r2.add(autoRelease); r2.add(workload); r2.add(setupBtn); r2.add(scenBtn); r2.add(resetBtn);
        r2.add(label("   Keys: Space = play/pause,  Right = step,  Left = step back"));
        p.add(r1); p.add(r2);

        playBtn.addActionListener(e -> togglePlay());
        stepBtn.addActionListener(e -> doStep());
        backBtn.addActionListener(e -> doBack());
        reqBtn.addActionListener(e -> sim.manualRequest(procBox.getSelectedIndex()));
        relBtn.addActionListener(e -> sim.manualRelease(procBox.getSelectedIndex()));
        autoRelease.addActionListener(e -> { sim.cfg.autoRelease = autoRelease.isSelected(); sim.applyLiveConfig(); });
        workload.addActionListener(e -> { sim.cfg.autoWorkload = workload.isSelected(); sim.applyLiveConfig(); });
        resetBtn.addActionListener(e -> newSimulation(sim.cfg, null));
        setupBtn.addActionListener(e -> { Config c = SetupDialog.show(this, sim.cfg); if (c != null) newSimulation(c, null); });
        scenBtn.addActionListener(e -> scenarioMenu());
        return p;
    }

    private static JLabel label(String s) { JLabel l = new JLabel(s); l.setForeground(Theme.MUTED); l.setFont(Theme.sans(Font.PLAIN, 12f)); return l; }

    private void bindKeys() {
        JRootPane rp = getRootPane();
        bind(rp, KeyEvent.VK_SPACE, "play", this::togglePlay);
        bind(rp, KeyEvent.VK_RIGHT, "step", this::doStep);
        bind(rp, KeyEvent.VK_LEFT, "back", this::doBack);
    }

    private static void bind(JRootPane rp, int key, String name, Runnable r) {
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0), name);
        rp.getActionMap().put(name, new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { r.run(); } });
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------
    private void newSimulation(Config c, List<skm.model.Action> script) {
        sim = new Simulation(c, script);
        mode = Mode.PAUSED; status = "";
        logShown = 0; logArea.setText("");
        procBox.removeAllItems();
        for (int i = 0; i < sim.n; i++) procBox.addItem("P" + i);
        autoRelease.setSelected(sim.cfg.autoRelease); workload.setSelected(sim.cfg.autoWorkload);
        playBtn.setText("Play");
        info.revalidate();
        split.setDividerLocation(Math.min(info.getPreferredSize().height + 4, Math.max(300, getHeight() - 340)));
        shownVersion = -1;
        repaint();
    }

    private void togglePlay() {
        mode = mode == Mode.PLAYING ? Mode.PAUSED : Mode.PLAYING;
        status = "";
        playBtn.setText(mode == Mode.PLAYING ? "Pause" : "Play");
        lastNanos = System.nanoTime();
        top.repaint();
    }

    private void doStep() {
        if (mode == Mode.STEPPING) sim.advance(stepTarget);       // finish the running step instantly
        mode = Mode.PAUSED; playBtn.setText("Play");
        double t = sim.nextEventTime();
        if (Double.isInfinite(t)) { status = "Nothing is scheduled. Click a process (or 'Request CS') to start something."; top.repaint(); return; }
        status = "";
        stepTarget = t; mode = Mode.STEPPING; lastNanos = System.nanoTime();
    }

    private void doBack() {
        if (mode == Mode.STEPPING) mode = Mode.PAUSED;
        mode = Mode.PAUSED; playBtn.setText("Play");
        status = sim.stepBack() ? "" : "Nothing to undo.";
        top.repaint();
    }

    private void nodeMenu(int i, Point pt) {
        JPopupMenu m = new JPopupMenu();
        JMenuItem rq = new JMenuItem("P" + i + ": Request CS"), rl = new JMenuItem("P" + i + ": Release CS");
        rq.setEnabled(sim.procs[i].state == ProcState.IDLE); rl.setEnabled(sim.procs[i].state == ProcState.IN_CS);
        rq.addActionListener(e -> sim.manualRequest(i)); rl.addActionListener(e -> sim.manualRelease(i));
        m.add(rq); m.add(rl);
        m.show(net, pt.x + 6, pt.y + 6);
    }

    private void scenarioMenu() {
        JPopupMenu m = new JPopupMenu();
        for (String name : ScenarioIO.builtInNames()) {
            JMenuItem it = new JMenuItem(name);
            it.addActionListener(e -> { ScenarioIO.Scenario s = ScenarioIO.builtIn(name); newSimulation(s.cfg, s.actions); });
            m.add(it);
        }
        m.addSeparator();
        JMenuItem open = new JMenuItem("Open scenario file..."), save = new JMenuItem("Save this run as scenario...");
        open.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                try { ScenarioIO.Scenario s = ScenarioIO.load(fc.getSelectedFile()); newSimulation(s.cfg, s.actions); }
                catch (Exception ex) { JOptionPane.showMessageDialog(this, "Could not read file: " + ex.getMessage()); }
            }
        });
        save.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(); fc.setSelectedFile(new File("my-scenario.skm"));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try { ScenarioIO.save(fc.getSelectedFile(), sim.cfg, sim.recorded); }
                catch (Exception ex) { JOptionPane.showMessageDialog(this, "Could not save: " + ex.getMessage()); }
            }
        });
        m.add(open); m.add(save);
        m.show(scenBtn, 0, -m.getPreferredSize().height);
    }

    // ------------------------------------------------------------------
    // Frame loop
    // ------------------------------------------------------------------
    private void tick() {
        long now = System.nanoTime();
        double dt = Math.min(0.05, (now - lastNanos) / 1e9);
        lastNanos = now;
        if (mode == Mode.PLAYING) {
            sim.advance(sim.time + dt * speed);
        } else if (mode == Mode.STEPPING) {
            double remaining = stepTarget - sim.time;
            double dv = Math.max(speed, remaining / 0.35) * dt;   // glide to the event in about 0.35 s
            if (remaining <= 1e-9 || dv >= remaining) { sim.advance(stepTarget); mode = Mode.PAUSED; top.repaint(); }
            else sim.advance(sim.time + dv);
        }
        boolean changed = sim.version != shownVersion;
        if (changed) {
            shownVersion = sim.version;
            syncLog();
            info.repaint();
            backBtn.setEnabled(sim.canStepBack());
            int sel = procBox.getSelectedIndex();
            reqBtn.setEnabled(sel >= 0 && sim.procs[sel].state == ProcState.IDLE);
            relBtn.setEnabled(sel >= 0 && sim.procs[sel].state == ProcState.IN_CS);
        }
        if (mode != Mode.PAUSED || changed) { net.repaint(); timeline.repaint(); top.repaint(); Toolkit.getDefaultToolkit().sync(); }
    }

    private void syncLog() {
        int size = sim.log.size();
        if (size < logShown || logShown == 0 || size - logShown > 400) {
            StringBuilder b = new StringBuilder();
            for (int k = Math.max(0, size - 1500); k < size; k++) b.append(sim.log.get(k)).append('\n');
            logArea.setText(b.toString());
        } else {
            StringBuilder b = new StringBuilder();
            for (int k = logShown; k < size; k++) b.append(sim.log.get(k)).append('\n');
            logArea.append(b.toString());
            if (logArea.getLineCount() > 2500) {
                try { logArea.getDocument().remove(0, logArea.getLineStartOffset(1000)); } catch (Exception ignored) { }
            }
        }
        logShown = size;
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    // ------------------------------------------------------------------
    // Header bar
    // ------------------------------------------------------------------
    private final class TopBar extends JPanel {
        TopBar() { setPreferredSize(new Dimension(800, 62)); setBackground(Theme.PANEL); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Theme.quality(g);
            g.setColor(Theme.PANEL); g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(Theme.LINE); g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            g.setFont(Theme.sans(Font.BOLD, 20f)); g.setColor(Theme.TEXT);
            g.drawString("Suzuki\u2013Kasami Algorithm Simulator", 18, 28);
            g.setFont(Theme.sans(Font.PLAIN, 12f)); g.setColor(Theme.MUTED);
            g.drawString("Token-based distributed mutual exclusion", 18, 48);

            g.setFont(Theme.mono(Font.BOLD, 22f)); g.setColor(Theme.TEAL);
            String clock = String.format("t = %7.2f s", sim.time);
            int cw = g.getFontMetrics().stringWidth(clock);
            g.drawString(clock, getWidth() / 2 - cw / 2, 30);
            g.setFont(Theme.sans(Font.PLAIN, 12f)); g.setColor(status.isEmpty() ? Theme.MUTED : Theme.ORANGE);
            String sub = status.isEmpty() ? "virtual time" : status;
            g.drawString(sub, getWidth() / 2 - g.getFontMetrics().stringWidth(sub) / 2, 48);

            String m = mode == Mode.PLAYING ? "PLAYING" : mode == Mode.STEPPING ? "STEPPING" : "PAUSED";
            Color mc = mode == Mode.PAUSED ? Theme.MUTED : Theme.GREEN;
            pill(g, m, mc, getWidth() - 18, 12);
            boolean ok = sim.violations == 0;
            pill(g, ok ? "SAFE" : "VIOLATION", ok ? Theme.GREEN : Theme.RED, getWidth() - 18 - 112, 12);
            g.dispose();
        }

        private void pill(Graphics2D g, String s, Color c, int rightX, int y) {
            g.setFont(Theme.sans(Font.BOLD, 12f));
            int w = 100;
            g.setColor(Theme.alpha(c, 40)); g.fillRoundRect(rightX - w, y, w, 26, 26, 26);
            g.setColor(c); Theme.centered(g, s, rightX - w / 2f, y + 17);
        }
    }
}
