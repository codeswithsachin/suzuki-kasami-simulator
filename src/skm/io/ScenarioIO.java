package skm.io;

import skm.engine.Config;
import skm.model.Action;

import java.io.*;
import java.util.*;

/** Ready-made demo scenarios and saving / loading of scenarios (.skm files). */
public final class ScenarioIO {
    public static final class Scenario {
        public final String name; public final Config cfg; public final List<Action> actions;
        Scenario(String name, Config cfg, List<Action> actions) { this.name = name; this.cfg = cfg; this.actions = actions; }
    }

    public static List<String> builtInNames() {
        return Arrays.asList("Three simultaneous requests", "Token holder requests again",
                "Heavy contention (random workload)", "Out-of-order network (non-FIFO)", "Empty system (free play)");
    }

    private static Action req(double t, int p) { return new Action(t, Action.Kind.REQUEST, p); }

    public static Scenario builtIn(String name) {
        Config c = new Config();
        List<Action> a = new ArrayList<>();
        switch (builtInNames().indexOf(name)) {
            case 0: a.add(req(0.5, 1)); a.add(req(0.9, 2)); a.add(req(1.3, 3)); a.add(req(6.0, 4)); break;
            case 1: a.add(req(0.5, 0)); a.add(req(1.0, 2)); a.add(req(6.0, 0)); a.add(req(6.5, 4)); break;
            case 2: c.n = 6; c.autoWorkload = true; c.meanInterval = 2.5; c.holdTime = 1.2; c.minDelay = 0.4; c.maxDelay = 1.0; break;
            case 3: c.n = 5; c.fifo = false; c.minDelay = 0.3; c.maxDelay = 2.6; c.autoWorkload = true; c.meanInterval = 3.5; c.seed = 7; break;
            default: break;
        }
        return new Scenario(name, c, a);
    }

    public static void save(File f, Config cfg, List<Action> actions) throws IOException {
        Properties p = new Properties();
        cfg.toProps(p);
        for (int i = 0; i < actions.size(); i++) {
            Action a = actions.get(i);
            p.setProperty("action." + i, String.format(Locale.ROOT, "%.4f,%s,%d", a.time, a.kind, a.proc));
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8")) { p.store(w, "Suzuki-Kasami scenario"); }
    }

    public static Scenario load(File f) throws IOException {
        Properties p = new Properties();
        try (Reader r = new InputStreamReader(new FileInputStream(f), "UTF-8")) { p.load(r); }
        Config c = Config.fromProps(p);
        List<Action> list = new ArrayList<>();
        for (int i = 0; p.getProperty("action." + i) != null; i++) {
            String[] t = p.getProperty("action." + i).split(",");
            list.add(new Action(Double.parseDouble(t[0]), Action.Kind.valueOf(t[1]), Integer.parseInt(t[2])));
        }
        return new Scenario(f.getName(), c, list);
    }
}
