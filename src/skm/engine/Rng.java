package skm.engine;

/** Small copyable random generator (xorshift). Needed so "step back" can restore the exact random state. */
public final class Rng {
    private long s;

    public Rng(long seed) {
        s = seed * 0x9E3779B97F4A7C15L + 0xBF58476D1CE4E5B9L;
        if (s == 0) s = 1;
        for (int i = 0; i < 4; i++) next();
    }

    private Rng(long s, boolean raw) { this.s = s; }

    public Rng copy() { return new Rng(s, true); }

    public long next() {
        s ^= s >>> 12; s ^= s << 25; s ^= s >>> 27;
        return s * 0x2545F4914F6CDD1DL;
    }

    public double nextDouble() { return (next() >>> 11) * (1.0 / (1L << 53)); }

    public double range(double a, double b) { return a + (b - a) * nextDouble(); }

    /** Exponential random value with the given mean (used for random request arrivals). */
    public double exp(double mean) { return -mean * Math.log(1.0 - nextDouble()); }
}
