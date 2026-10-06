package skm.engine;

/** Counters shown in the metrics panel. */
public final class Metrics {
    public int requests, csEntries, immediate, sentReq, sentTok;
    public double totalWait, maxWait;

    public Metrics copy() {
        Metrics m = new Metrics();
        m.requests = requests; m.csEntries = csEntries; m.immediate = immediate;
        m.sentReq = sentReq; m.sentTok = sentTok; m.totalWait = totalWait; m.maxWait = maxWait;
        return m;
    }

    public double avgWait() { return csEntries == 0 ? 0 : totalWait / csEntries; }

    public double msgsPerEntry() { return csEntries == 0 ? 0 : (double) (sentReq + sentTok) / csEntries; }
}
