package skm.model;

/** One site of the distributed system. */
public final class Site {
    public final int id;
    public ProcState state = ProcState.IDLE;
    /** rn[j] = highest request number this process has seen from Pj. */
    public final int[] rn;
    public double requestTime;                               // when the current request was made
    public double csExitAt = Double.POSITIVE_INFINITY;       // scheduled automatic release
    public double nextReqAt = Double.POSITIVE_INFINITY;      // next automatic request (workload)
    public int csCount;

    public Site(int id, int n) { this.id = id; this.rn = new int[n]; }

    private Site(Site o) {
        id = o.id; state = o.state; rn = o.rn.clone(); requestTime = o.requestTime;
        csExitAt = o.csExitAt; nextReqAt = o.nextReqAt; csCount = o.csCount;
    }

    public Site copy() { return new Site(this); }
}
