import java.util.*;

/**
 * Stack.java
 * A parsing stack that holds (symbol, stateId) pairs.
 * Used by both SLR(1) and LR(1) parsers.
 */
public class Stack {

    // ── Entry ─────────────────────────────────────────────────────────────
    public static class Entry {
        public final String symbol;
        public final int    state;

        public Entry(String symbol, int state) {
            this.symbol = symbol;
            this.state  = state;
        }

        @Override public String toString() {
            return symbol + ":" + state;
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────
    private final Deque<Entry> deque = new ArrayDeque<>();

    // ── Operations ────────────────────────────────────────────────────────

    /** Push a (symbol, state) pair onto the stack. */
    public void push(String symbol, int state) {
        deque.push(new Entry(symbol, state));
    }

    /** Pop the top entry. */
    public Entry pop() {
        if (deque.isEmpty()) throw new EmptyStackException();
        return deque.pop();
    }

    /** Pop n entries and return them in bottom-to-top order (i.e., reversed). */
    public List<Entry> pop(int n) {
        List<Entry> popped = new ArrayList<>(n);
        for (int i = 0; i < n; i++) popped.add(pop());
        Collections.reverse(popped); // bottom-first order
        return popped;
    }

    /** Look at the top entry without removing it. */
    public Entry peek() {
        if (deque.isEmpty()) throw new EmptyStackException();
        return deque.peek();
    }

    /** Get the state at the top of the stack. */
    public int topState() {
        return peek().state;
    }

    public boolean isEmpty() { return deque.isEmpty(); }
    public int     size()    { return deque.size(); }

    /**
     * Display the stack as a readable string, bottom → top.
     * E.g.: [0] [id:5] [Factor:3]
     */
    public String display() {
        List<Entry> list = new ArrayList<>(deque);
        Collections.reverse(list); // show bottom-to-top
        StringBuilder sb = new StringBuilder();
        for (Entry e : list) {
            sb.append("[").append(e.symbol).append(":").append(e.state).append("] ");
        }
        return sb.toString().trim();
    }

    /**
     * Display only symbols (no states), bottom → top.
     * Used for compact trace output.
     */
    public String displaySymbols() {
        List<Entry> list = new ArrayList<>(deque);
        Collections.reverse(list);
        StringBuilder sb = new StringBuilder();
        for (Entry e : list) sb.append(e.symbol).append(" ");
        return sb.toString().trim();
    }
}
