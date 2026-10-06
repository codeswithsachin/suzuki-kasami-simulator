package skm.test;

import skm.engine.*;
import skm.model.*;

import java.util.Random;

/**
 * Stress test of the algorithm. Runs hundreds of random simulations (different sizes, delays,
 * FIFO / non-FIFO networks, heavy random workloads) and checks after every single event that:
 *   - at most one process is in the critical section and exactly one token exists (safety),
 *   - "step back" restores the exact previous state,
 * and at the end that every request was served (no deadlock, no starvation).
 *
 * Run:  java -cp out skm.test.EngineSelfTest
 */
public final class EngineSelfTest {
    public static void main(String[] args) {
        Random r = new Random(7);
        int runs = 400, events = 0, entries = 0, failures = 0;
        for (int run = 0; run < runs; run++) {
            Config c = new Config();
            c.n = 2 + r.nextInt(9);
            c.initialHolder = r.nextInt(c.n);
            c.seed = r.nextInt(100000);
            c.minDelay = 0.05 + r.nextDouble();
            c.maxDelay = c.minDelay + r.nextDouble() * 3;
            c.fifo = r.nextBoolean();
            c.holdTime = 0.1 + r.nextDouble() * 2;
            c.autoRelease = true;
            c.autoWorkload = true;
            c.meanInterval = 0.2 + r.nextDouble() * 2;
            Simulation s = new Simulation(c, null);
            try {
                for (int step = 0; step < 3000; step++) {
                    if (r.nextInt(25) == 0) {                       // random manual actions too
                        int p = r.nextInt(c.n);
                        if (r.nextBoolean()) s.manualRequest(p); else s.manualRelease(p);
                    }
                    if (r.nextInt(40) == 0) {                       // undo test
                        String before = s.fingerprint();
                        int k = 1 + r.nextInt(4), done = 0;
                        while (done < k && s.processNext()) done++;
                        for (int i = 0; i < done; i++) s.stepBack();
                        check(before.equals(s.fingerprint()), "step back did not restore the state");
                    }
                    if (!s.processNext()) break;
                    events++;
                    check(s.violations == 0, "safety violated: " + s.violation);
                }
                c = s.cfg; c.autoWorkload = false; s.applyLiveConfig();   // stop new requests, drain the system
                int guard = 0;
                while (s.processNext() && guard++ < 100000) check(s.violations == 0, "safety violated while draining");
                for (Site p : s.procs) {
                    check(p.state == ProcState.IDLE, "P" + p.id + " never got the CS (" + p.state + ") in run " + run);
                    entries += p.csCount;
                }
                check(s.holder >= 0 && s.flight.isEmpty(), "token or messages left in transit");
                check(s.m.csEntries == s.m.requests, "requests " + s.m.requests + " != entries " + s.m.csEntries);
            } catch (AssertionError e) {
                failures++;
                System.out.println("FAIL run " + run + " (n=" + c.n + ", fifo=" + c.fifo + "): " + e.getMessage());
            }
        }
        System.out.println(runs + " simulations, " + events + " events, " + entries + " CS entries, " + failures + " failures.");
        if (failures > 0) System.exit(1);
        System.out.println("ALL TESTS PASSED");
    }

    private static void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }
}
