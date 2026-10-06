package skm.engine;

import java.util.Properties;

/** All settings of a simulation. Everything is user-controlled, nothing is hard-coded in the engine. */
public final class Config {
    public int n = 5;                    // number of processes (2..10)
    public int initialHolder = 0;        // who owns the token at start
    public long seed = 42;               // random seed (same seed = same run)
    public double minDelay = 0.8;        // network delay range (virtual seconds)
    public double maxDelay = 1.6;
    public boolean fifo = true;          // false = messages on one channel may overtake each other
    public boolean autoRelease = true;   // leave the CS automatically after holdTime
    public double holdTime = 2.0;
    public boolean autoWorkload = false; // processes request the CS at random times
    public double meanInterval = 3.0;    // mean time between random requests of one process

    public Config copy() {
        Config c = new Config();
        c.n = n; c.initialHolder = initialHolder; c.seed = seed; c.minDelay = minDelay; c.maxDelay = maxDelay;
        c.fifo = fifo; c.autoRelease = autoRelease; c.holdTime = holdTime;
        c.autoWorkload = autoWorkload; c.meanInterval = meanInterval;
        return c;
    }

    public void sanitize() {
        n = Math.max(2, Math.min(10, n));
        initialHolder = Math.max(0, Math.min(n - 1, initialHolder));
        minDelay = Math.max(0.05, minDelay);
        maxDelay = Math.max(minDelay, maxDelay);
        holdTime = Math.max(0.1, holdTime);
        meanInterval = Math.max(0.2, meanInterval);
    }

    public void toProps(Properties p) {
        p.setProperty("n", "" + n); p.setProperty("initialHolder", "" + initialHolder);
        p.setProperty("seed", "" + seed); p.setProperty("minDelay", "" + minDelay);
        p.setProperty("maxDelay", "" + maxDelay); p.setProperty("fifo", "" + fifo);
        p.setProperty("autoRelease", "" + autoRelease); p.setProperty("holdTime", "" + holdTime);
        p.setProperty("autoWorkload", "" + autoWorkload); p.setProperty("meanInterval", "" + meanInterval);
    }

    public static Config fromProps(Properties p) {
        Config c = new Config();
        c.n = Integer.parseInt(p.getProperty("n", "5"));
        c.initialHolder = Integer.parseInt(p.getProperty("initialHolder", "0"));
        c.seed = Long.parseLong(p.getProperty("seed", "42"));
        c.minDelay = Double.parseDouble(p.getProperty("minDelay", "0.8"));
        c.maxDelay = Double.parseDouble(p.getProperty("maxDelay", "1.6"));
        c.fifo = Boolean.parseBoolean(p.getProperty("fifo", "true"));
        c.autoRelease = Boolean.parseBoolean(p.getProperty("autoRelease", "true"));
        c.holdTime = Double.parseDouble(p.getProperty("holdTime", "2.0"));
        c.autoWorkload = Boolean.parseBoolean(p.getProperty("autoWorkload", "false"));
        c.meanInterval = Double.parseDouble(p.getProperty("meanInterval", "3.0"));
        c.sanitize();
        return c;
    }
}
