package skm.model;

/** A user (or scripted) action at a given virtual time: request or release the CS. */
public final class Action {
    public enum Kind { REQUEST, RELEASE }

    public final double time;
    public final Kind kind;
    public final int proc;

    public Action(double time, Kind kind, int proc) { this.time = time; this.kind = kind; this.proc = proc; }
}
