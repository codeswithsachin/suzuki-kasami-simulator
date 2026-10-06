package skm.model;

import java.util.ArrayDeque;

/**
 * The single token of the algorithm.
 * ln[j]  = request number of the last request of Pj that has been served
 * queue  = processes waiting for the token, in FIFO order
 */
public final class Token {
    public final int[] ln;
    public final ArrayDeque<Integer> queue;

    public Token(int n) { ln = new int[n]; queue = new ArrayDeque<>(); }

    private Token(Token o) { ln = o.ln.clone(); queue = new ArrayDeque<>(o.queue); }

    public Token copy() { return new Token(this); }
}
