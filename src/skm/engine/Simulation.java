package skm.engine;

import skm.model.*;

import java.util.*;

/**
 * The Suzuki-Kasami algorithm inside a discrete-event simulator.
 *
 * Time is virtual (seconds). Nothing here knows about the GUI. The state is:
 *   processes + the token (held by one process or travelling inside a message) + messages in flight.
 * The next event is always the earliest of: a message arrival, an automatic CS exit,
 * a scripted action, or an automatic (random) request. Because everything is driven by a seeded
 * random generator, the same settings always give the same run.
 */
public final class Simulation {

    // ---- visual / bookkeeping records read by the UI ----
    public enum Kind { REQUEST, SEND_REQ, RECV_REQ, SEND_TOKEN, RECV_TOKEN, ENTER_CS, EXIT_CS }

    public static final class Effect {
        public final double time; public final int proc; public final Kind kind;
        Effect(double time, int proc, Kind kind) { this.time = time; this.proc = proc; this.kind = kind; }
    }

    public static final class Tr {
        public final double time; public final int proc; public final ProcState state;
        Tr(double time, int proc, ProcState state) { this.time = time; this.proc = proc; this.state = state; }
    }

    private static final double INF = Double.POSITIVE_INFINITY;
    private static final int ARRIVE = 0, EXIT = 1, SCRIPT = 2, AUTO = 3;
    private static final int MAX_HISTORY = 3000;

    public final Config cfg;
    public final int n;
    public double time;
    public Site[] procs;
    public Token token;                 // non-null while a process holds the token
    public int holder;                  // index of holder, -1 while the token is in transit
    public final ArrayList<Message> flight = new ArrayList<>();
    public final ArrayList<Effect> effects = new ArrayList<>();
    public final ArrayList<Tr> trans = new ArrayList<>();
    public final ArrayList<String> log = new ArrayList<>();
    public final ArrayList<Action> recorded = new ArrayList<>();
    public Metrics m = new Metrics();
    public int violations;
    public String violation;
    public long version;                // increases on every change (UI uses it to know when to redraw text)

    private Rng rng;
    private double[][] lastArrive;
    private int nextMsgId;
    private final List<Action> script;
    private int scriptPos;
    private final ArrayDeque<Snap> history = new ArrayDeque<>();

    public Simulation(Config c, List<Action> scriptActions) {
        cfg = c.copy();
        cfg.sanitize();
        n = cfg.n;
        script = new ArrayList<>();
        if (scriptActions != null) script.addAll(scriptActions);
        script.sort(Comparator.comparingDouble(a -> a.time));
        rng = new Rng(cfg.seed);
        lastArrive = new double[n][n];
        procs = new Site[n];
        for (int i = 0; i < n; i++) {
            procs[i] = new Site(i, n);
            trans.add(new Tr(0, i, ProcState.IDLE));
        }
        holder = cfg.initialHolder;
        token = new Token(n);
        applyLiveConfig();
        log("Start: " + n + " processes. P" + holder + " holds the token.");
        version++;
    }

    // ------------------------------------------------------------------
    // The algorithm
    // ------------------------------------------------------------------

    /** Pi wants to enter the critical section. */
    private void doRequest(int i) {
        Site p = procs[i];
        if (p.state != ProcState.IDLE) return;
        m.requests++;
        p.requestTime = time;
        effect(i, Kind.REQUEST);
        if (holder == i) {                       // already has the token: no messages needed
            log("P" + i + " requests the CS and already holds the token (0 messages).");
            enterCS(i, true);
            return;
        }
        p.rn[i]++;                               // RN_i[i] := RN_i[i] + 1
        setState(p, ProcState.WAITING);
        log("P" + i + " requests the CS: broadcasts REQUEST(" + i + ", " + p.rn[i] + ").");
        for (int k = 0; k < n; k++)
            if (k != i) { send(MsgType.REQUEST, i, k, p.rn[i], null); m.sentReq++; }
        effect(i, Kind.SEND_REQ);
    }

    private void enterCS(int i, boolean immediate) {
        Site p = procs[i];
        setState(p, ProcState.IN_CS);
        double w = time - p.requestTime;
        m.csEntries++; m.totalWait += w; m.maxWait = Math.max(m.maxWait, w);
        if (immediate) m.immediate++;
        p.csCount++;
        if (cfg.autoRelease) p.csExitAt = time + cfg.holdTime;
        effect(i, Kind.ENTER_CS);
        log("P" + i + " ENTERS the critical section (waited " + String.format(Locale.ROOT, "%.2f", w) + " s).");
    }

    /** Pi leaves the critical section and passes the token on. */
    private void doRelease(int i) {
        Site p = procs[i];
        if (p.state != ProcState.IN_CS) return;
        token.ln[i] = p.rn[i];                   // LN[i] := RN_i[i]
        p.csExitAt = INF;
        setState(p, ProcState.IDLE);
        effect(i, Kind.EXIT_CS);
        log("P" + i + " LEAVES the CS, LN[" + i + "] = " + token.ln[i] + ".");
        for (int d = 1; d < n; d++) {            // add every outstanding request to the token queue
            int k = (i + d) % n;
            if (!token.queue.contains(k) && p.rn[k] == token.ln[k] + 1) {
                token.queue.addLast(k);
                log("P" + k + " added to queue Q (RN[" + k + "] = LN[" + k + "] + 1).");
            }
        }
        if (!token.queue.isEmpty()) sendToken(i, token.queue.pollFirst());
        else log("Queue Q is empty: P" + i + " keeps the token.");
    }

    private void sendToken(int from, int to) {
        send(MsgType.TOKEN, from, to, 0, token);
        token = null; holder = -1;
        m.sentTok++;
        effect(from, Kind.SEND_TOKEN);
        log("P" + from + " sends the TOKEN to P" + to + ".");
    }

    private void deliver(Message msg) {
        flight.remove(msg);
        Site q = procs[msg.to];
        if (msg.type == MsgType.REQUEST) {
            q.rn[msg.from] = Math.max(q.rn[msg.from], msg.sn);
            effect(msg.to, Kind.RECV_REQ);
            log("P" + msg.to + " receives REQUEST(" + msg.from + ", " + msg.sn + "): RN[" + msg.from + "] = " + q.rn[msg.from] + ".");
            if (holder == msg.to && q.state == ProcState.IDLE && q.rn[msg.from] == token.ln[msg.from] + 1) {
                log("P" + msg.to + " is idle and has the token, so it sends it at once.");
                sendToken(msg.to, msg.from);
            }
        } else {
            token = msg.token; holder = msg.to;
            effect(msg.to, Kind.RECV_TOKEN);
            log("P" + msg.to + " receives the TOKEN.");
            if (q.state == ProcState.WAITING) enterCS(msg.to, false);
        }
    }

    private void send(MsgType t, int from, int to, int sn, Token tok) {
        double arr = time + rng.range(cfg.minDelay, cfg.maxDelay);
        if (cfg.fifo) { arr = Math.max(arr, lastArrive[from][to] + 1e-6); lastArrive[from][to] = arr; }
        flight.add(new Message(nextMsgId++, t, from, to, sn, time, arr, tok));
    }

    // ------------------------------------------------------------------
    // Event loop
    // ------------------------------------------------------------------
    private double evT; private int evKind, evIdx; private boolean found;

    private void cand(double t, int k, int i) {
        if (t == INF) return;
        if (!found || t < evT || (t == evT && (k < evKind || (k == evKind && i < evIdx)))) {
            evT = t; evKind = k; evIdx = i; found = true;
        }
    }

    private boolean findNext() {
        found = false;
        for (Message x : flight) cand(x.arriveTime, ARRIVE, x.id);
        for (Site p : procs) { cand(p.csExitAt, EXIT, p.id); cand(p.nextReqAt, AUTO, p.id); }
        if (scriptPos < script.size()) cand(script.get(scriptPos).time, SCRIPT, scriptPos);
        return found;
    }

    /** Time of the next event, or +infinity if nothing is scheduled. */
    public double nextEventTime() { return findNext() ? evT : INF; }

    /** Processes exactly one event (the earliest). Returns false if there is none. */
    public boolean processNext() {
        if (!findNext()) return false;
        pushHistory();
        if (evT > time) time = evT;
        switch (evKind) {
            case ARRIVE:
                for (Message x : new ArrayList<>(flight)) if (x.id == evIdx) { deliver(x); break; }
                break;
            case EXIT: doRelease(evIdx); break;
            case SCRIPT: apply(script.get(scriptPos++)); break;
            default: {
                Site p = procs[evIdx];
                p.nextReqAt = cfg.autoWorkload ? time + rng.exp(cfg.meanInterval) : INF;
                doRequest(evIdx);
            }
        }
        afterChange();
        return true;
    }

    /** Moves the clock to t, processing every event on the way. */
    public void advance(double t) {
        while (findNext() && evT <= t) processNext();
        if (t > time) time = t;
    }

    private void apply(Action a) {
        recorded.add(new Action(time, a.kind, a.proc));
        if (a.kind == Action.Kind.REQUEST) doRequest(a.proc); else doRelease(a.proc);
    }

    // ------------------------------------------------------------------
    // Manual actions (buttons in the UI)
    // ------------------------------------------------------------------
    public boolean manualRequest(int i) {
        if (procs[i].state != ProcState.IDLE) return false;
        pushHistory(); recorded.add(new Action(time, Action.Kind.REQUEST, i));
        doRequest(i); afterChange();
        return true;
    }

    public boolean manualRelease(int i) {
        if (procs[i].state != ProcState.IN_CS) return false;
        pushHistory(); recorded.add(new Action(time, Action.Kind.RELEASE, i));
        doRelease(i); afterChange();
        return true;
    }

    /** Call after changing cfg.autoRelease / autoWorkload / holdTime / meanInterval while running. */
    public void applyLiveConfig() {
        for (Site p : procs) {
            if (!cfg.autoRelease) p.csExitAt = INF;
            else if (p.state == ProcState.IN_CS && p.csExitAt == INF) p.csExitAt = time + cfg.holdTime;
            if (!cfg.autoWorkload) p.nextReqAt = INF;
            else if (p.nextReqAt == INF) p.nextReqAt = time + rng.exp(cfg.meanInterval);
        }
        version++;
    }

    // ------------------------------------------------------------------
    // Step back (undo) using snapshots
    // ------------------------------------------------------------------
    public boolean canStepBack() { return !history.isEmpty(); }

    public boolean stepBack() {
        if (history.isEmpty()) return false;
        history.pollLast().restore(this);
        version++;
        return true;
    }

    private void pushHistory() {
        history.addLast(new Snap(this));
        if (history.size() > MAX_HISTORY) history.pollFirst();
    }

    private static final class Snap {
        final double time; final Site[] procs; final Token token; final int holder;
        final ArrayList<Message> flight = new ArrayList<>(); final Metrics m; final Rng rng;
        final double[][] lastArrive; final int nextMsgId, scriptPos, violations;
        final String violation; final int effN, trN, logN, recN;

        Snap(Simulation s) {
            time = s.time; holder = s.holder; token = s.token == null ? null : s.token.copy();
            procs = new Site[s.n];
            for (int i = 0; i < s.n; i++) procs[i] = s.procs[i].copy();
            for (Message x : s.flight) flight.add(x.copy());
            m = s.m.copy(); rng = s.rng.copy();
            lastArrive = new double[s.n][];
            for (int i = 0; i < s.n; i++) lastArrive[i] = s.lastArrive[i].clone();
            nextMsgId = s.nextMsgId; scriptPos = s.scriptPos; violations = s.violations; violation = s.violation;
            effN = s.effects.size(); trN = s.trans.size(); logN = s.log.size(); recN = s.recorded.size();
        }

        void restore(Simulation s) {
            s.time = time; s.procs = procs; s.token = token; s.holder = holder;
            s.flight.clear(); s.flight.addAll(flight);
            s.m = m; s.rng = rng; s.lastArrive = lastArrive; s.nextMsgId = nextMsgId;
            s.scriptPos = scriptPos; s.violations = violations; s.violation = violation;
            s.effects.subList(effN, s.effects.size()).clear();
            s.trans.subList(trN, s.trans.size()).clear();
            s.log.subList(logN, s.log.size()).clear();
            s.recorded.subList(recN, s.recorded.size()).clear();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    private void setState(Site p, ProcState st) { p.state = st; trans.add(new Tr(time, p.id, st)); }

    private void effect(int proc, Kind k) { effects.add(new Effect(time, proc, k)); }

    private void log(String s) { log.add(String.format(Locale.ROOT, "[%6.2fs] %s", time, s)); }

    private void afterChange() { checkSafety(); version++; }

    /** Live safety monitor: at most one process in the CS and exactly one token in the system. */
    private void checkSafety() {
        int inCs = 0, tokens = token != null ? 1 : 0;
        for (Site p : procs) if (p.state == ProcState.IN_CS) inCs++;
        for (Message x : flight) if (x.type == MsgType.TOKEN) tokens++;
        String v = null;
        if (inCs > 1) v = inCs + " processes are in the CS at the same time";
        else if (tokens != 1) v = "there are " + tokens + " tokens in the system";
        else if (inCs == 1 && (holder < 0 || procs[holder].state != ProcState.IN_CS)) v = "CS entered without the token";
        if (v != null) { violations++; violation = v; log("SAFETY VIOLATION: " + v); }
    }

    public Token currentToken() {
        if (token != null) return token;
        for (Message x : flight) if (x.type == MsgType.TOKEN) return x.token;
        return null;
    }

    /** 0..1 position of a message on its way (used for smooth animation). */
    public double progress(Message x) {
        double d = x.arriveTime - x.sendTime;
        return d <= 0 ? 1 : Math.max(0, Math.min(1, (time - x.sendTime) / d));
    }

    /** Text summary of the whole state; used by tests to check that "step back" restores everything. */
    public String fingerprint() {
        StringBuilder b = new StringBuilder();
        b.append(String.format(Locale.ROOT, "%.6f|%d|", time, holder));
        for (Site p : procs) b.append(p.state).append(Arrays.toString(p.rn)).append(p.csExitAt).append(p.nextReqAt).append(';');
        Token t = currentToken();
        if (t != null) b.append(Arrays.toString(t.ln)).append(t.queue);
        for (Message x : flight) b.append('|').append(x.id).append(x.type).append(x.from).append(x.to).append(x.arriveTime);
        b.append('|').append(m.csEntries).append(',').append(m.sentReq).append(',').append(m.sentTok).append(',').append(m.requests);
        b.append('|').append(log.size()).append(',').append(effects.size()).append(',').append(trans.size());
        return b.toString();
    }
}
