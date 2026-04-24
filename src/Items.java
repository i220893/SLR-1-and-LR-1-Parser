import java.util.*;

/**
 * Items.java
 * Builds LR(0) and LR(1) items, computes CLOSURE and GOTO,
 * and constructs the canonical collections of item sets.
 */
public class Items {

    // ═══════════════════════════════════════════════════════════════════════
    // LR(0) Item: [Production, dotPosition]
    // ═══════════════════════════════════════════════════════════════════════
    public static class LR0Item {
        public final Grammar.Production production;
        public final int                dotPos;      // index before which the dot sits

        public LR0Item(Grammar.Production prod, int dotPos) {
            this.production = prod;
            this.dotPos     = dotPos;
        }

        /** Symbol immediately after the dot, or null if dot is at end. */
        public String symbolAfterDot() {
            List<String> rhs = production.rhs;
            if (isComplete()) return null;
            String s = rhs.get(dotPos);
            return s.equals(Grammar.EPSILON) ? null : s;
        }

        /** True when the dot is at the right end (or rhs is epsilon). */
        public boolean isComplete() {
            List<String> rhs = production.rhs;
            return dotPos >= rhs.size() ||
                   (rhs.size() == 1 && rhs.get(0).equals(Grammar.EPSILON));
        }

        /** Advance dot one position. */
        public LR0Item advance() {
            return new LR0Item(production, dotPos + 1);
        }

        /** Symbols after the dot (β in A → α • β). */
        public List<String> afterDot() {
            if (isComplete()) return Collections.emptyList();
            return production.rhs.subList(dotPos, production.rhs.size());
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof LR0Item)) return false;
            LR0Item other = (LR0Item) o;
            return production.id == other.production.id && dotPos == other.dotPos;
        }
        @Override public int hashCode() { return 31 * production.id + dotPos; }

        @Override public String toString() {
            List<String> rhs = production.rhs;
            List<String> parts = new ArrayList<>(rhs);
            // Handle epsilon rhs
            if (parts.size() == 1 && parts.get(0).equals(Grammar.EPSILON)) {
                return production.lhs + " -> • ε";
            }
            parts.add(dotPos, "•");
            return production.lhs + " -> " + String.join(" ", parts);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LR(1) Item: LR(0) item + set of lookahead terminals
    // ═══════════════════════════════════════════════════════════════════════
    public static class LR1Item {
        public final LR0Item        core;
        public final Set<String>    lookaheads; // mutable so we can merge

        public LR1Item(LR0Item core, Set<String> lookaheads) {
            this.core       = core;
            this.lookaheads = new LinkedHashSet<>(lookaheads);
        }

        public LR1Item(LR0Item core, String lookahead) {
            this(core, new LinkedHashSet<>(Collections.singleton(lookahead)));
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof LR1Item)) return false;
            LR1Item other = (LR1Item) o;
            return core.equals(other.core) && lookaheads.equals(other.lookaheads);
        }
        @Override public int hashCode() { return 31 * core.hashCode() + lookaheads.hashCode(); }

        @Override public String toString() {
            return core.toString() + ", " + lookaheads;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ItemSet: a numbered set of items (LR(0) or LR(1))
    // ═══════════════════════════════════════════════════════════════════════
    public static class ItemSet {
        public int            stateId;
        public List<LR0Item>  lr0Items  = new ArrayList<>();
        public List<LR1Item>  lr1Items  = new ArrayList<>();
        public boolean        isLR1;

        public ItemSet(int id, boolean isLR1) {
            this.stateId = id;
            this.isLR1   = isLR1;
        }

        // For LR(0) equality (used for deduplication in canonical collection)
        public boolean lr0Equals(ItemSet other) {
            if (isLR1 || other.isLR1) return false;
            return new HashSet<>(lr0Items).equals(new HashSet<>(other.lr0Items));
        }

        // For LR(1) equality
        public boolean lr1Equals(ItemSet other) {
            if (!isLR1 || !other.isLR1) return false;
            return lr1ItemsAsMap().equals(other.lr1ItemsAsMap());
        }

        // Map from core item to merged lookaheads (used for equality check)
        private Map<LR0Item, Set<String>> lr1ItemsAsMap() {
            Map<LR0Item, Set<String>> m = new HashMap<>();
            for (LR1Item it : lr1Items) m.put(it.core, it.lookaheads);
            return m;
        }

        @Override public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("I").append(stateId).append(":\n");
            if (isLR1) {
                for (LR1Item it : lr1Items) sb.append("  ").append(it).append("\n");
            } else {
                for (LR0Item it : lr0Items) sb.append("  ").append(it).append("\n");
            }
            return sb.toString();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CLOSURE and GOTO for LR(0)
    // ═══════════════════════════════════════════════════════════════════════

    /** LR(0) CLOSURE */
    public static List<LR0Item> closureLR0(List<LR0Item> items, Grammar g) {
        List<LR0Item>  closure = new ArrayList<>(items);
        Set<LR0Item>   inSet   = new LinkedHashSet<>(items);

        boolean changed = true;
        while (changed) {
            changed = false;
            List<LR0Item> snapshot = new ArrayList<>(closure);
            for (LR0Item item : snapshot) {
                String B = item.symbolAfterDot();
                if (B == null || !g.isNonTerminal(B)) continue;
                for (Grammar.Production prod : g.productionsFor(B)) {
                    LR0Item newItem = new LR0Item(prod, 0);
                    if (inSet.add(newItem)) {
                        closure.add(newItem);
                        changed = true;
                    }
                }
            }
        }
        return closure;
    }

    /** LR(0) GOTO(I, X) */
    public static List<LR0Item> gotoLR0(List<LR0Item> items, String X, Grammar g) {
        List<LR0Item> kernel = new ArrayList<>();
        for (LR0Item item : items) {
            if (X.equals(item.symbolAfterDot()))
                kernel.add(item.advance());
        }
        if (kernel.isEmpty()) return Collections.emptyList();
        return closureLR0(kernel, g);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CLOSURE and GOTO for LR(1)
    // ═══════════════════════════════════════════════════════════════════════

    /** LR(1) CLOSURE */
    public static List<LR1Item> closureLR1(List<LR1Item> items, Grammar g) {
        // Use map: core item -> merged LR1Item (to accumulate lookaheads)
        Map<LR0Item, LR1Item> map = new LinkedHashMap<>();
        for (LR1Item it : items) {
            if (map.containsKey(it.core)) {
                map.get(it.core).lookaheads.addAll(it.lookaheads);
            } else {
                map.put(it.core, new LR1Item(it.core, new LinkedHashSet<>(it.lookaheads)));
            }
        }

        Queue<LR1Item> worklist = new ArrayDeque<>(map.values());

        while (!worklist.isEmpty()) {
            LR1Item cur = worklist.poll();
            String B = cur.core.symbolAfterDot();
            if (B == null || !g.isNonTerminal(B)) continue;

            // β = symbols after B in the item
            List<String> afterB = cur.core.afterDot();
            // β is afterB[1..] (skip B itself)
            List<String> beta = afterB.size() > 1
                    ? afterB.subList(1, afterB.size())
                    : Collections.emptyList();

            for (Grammar.Production prod : g.productionsFor(B)) {
                LR0Item newCore = new LR0Item(prod, 0);

                // Compute FIRST(β a) for each lookahead a
                Set<String> newLookaheads = new LinkedHashSet<>();
                for (String a : cur.lookaheads) {
                    List<String> betaA = new ArrayList<>(beta);
                    if (!a.equals(Grammar.EPSILON)) betaA.add(a);
                    Set<String> firstBetaA = g.firstOfSequence(betaA, 0);
                    firstBetaA.remove(Grammar.EPSILON);
                    newLookaheads.addAll(firstBetaA);
                    if (beta.isEmpty() || g.firstOfSequence(beta, 0).contains(Grammar.EPSILON)) {
                        newLookaheads.add(a);
                    }
                }

                if (map.containsKey(newCore)) {
                    LR1Item existing = map.get(newCore);
                    int before = existing.lookaheads.size();
                    existing.lookaheads.addAll(newLookaheads);
                    if (existing.lookaheads.size() != before) worklist.add(existing);
                } else {
                    LR1Item newItem = new LR1Item(newCore, newLookaheads);
                    map.put(newCore, newItem);
                    worklist.add(newItem);
                }
            }
        }

        return new ArrayList<>(map.values());
    }

    /** LR(1) GOTO(I, X) */
    public static List<LR1Item> gotoLR1(List<LR1Item> items, String X, Grammar g) {
        List<LR1Item> kernel = new ArrayList<>();
        for (LR1Item item : items) {
            if (X.equals(item.core.symbolAfterDot()))
                kernel.add(new LR1Item(item.core.advance(), item.lookaheads));
        }
        if (kernel.isEmpty()) return Collections.emptyList();
        return closureLR1(kernel, g);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Canonical Collection builders
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Build the canonical collection of LR(0) item sets.
     * Returns the list of states and populates gotoMap[stateId][symbol] = targetStateId.
     */
    public static List<ItemSet> buildLR0Collection(Grammar g,
                                                   Map<Integer, Map<String,Integer>> gotoMap) {
        List<ItemSet> collection = new ArrayList<>();

        // Collect all grammar symbols
        Set<String> allSymbols = new LinkedHashSet<>();
        allSymbols.addAll(g.getTerminals());
        allSymbols.addAll(g.getNonTerminals());

        // I0: closure({S' -> • S})
        Grammar.Production augProd = g.getProductions().get(0); // guaranteed augmented
        LR0Item startItem = new LR0Item(augProd, 0);
        ItemSet I0 = new ItemSet(0, false);
        I0.lr0Items = closureLR0(Collections.singletonList(startItem), g);
        collection.add(I0);
        gotoMap.put(0, new LinkedHashMap<>());

        Queue<ItemSet> worklist = new ArrayDeque<>();
        worklist.add(I0);

        while (!worklist.isEmpty()) {
            ItemSet I = worklist.poll();
            for (String X : allSymbols) {
                List<LR0Item> nextItems = gotoLR0(I.lr0Items, X, g);
                if (nextItems.isEmpty()) continue;

                // Check if this item set already exists
                ItemSet existing = null;
                for (ItemSet s : collection) {
                    if (new HashSet<>(s.lr0Items).equals(new HashSet<>(nextItems))) {
                        existing = s;
                        break;
                    }
                }
                if (existing == null) {
                    existing = new ItemSet(collection.size(), false);
                    existing.lr0Items = nextItems;
                    collection.add(existing);
                    gotoMap.put(existing.stateId, new LinkedHashMap<>());
                    worklist.add(existing);
                }
                gotoMap.get(I.stateId).put(X, existing.stateId);
            }
        }
        return collection;
    }

    /**
     * Build the canonical collection of LR(1) item sets.
     */
    public static List<ItemSet> buildLR1Collection(Grammar g,
                                                   Map<Integer, Map<String,Integer>> gotoMap) {
        List<ItemSet> collection = new ArrayList<>();

        Set<String> allSymbols = new LinkedHashSet<>();
        allSymbols.addAll(g.getTerminals());
        allSymbols.addAll(g.getNonTerminals());

        // I0: closure({[S' -> • S, $]})
        Grammar.Production augProd = g.getProductions().get(0);
        LR1Item startItem = new LR1Item(new LR0Item(augProd, 0), Grammar.EOF);
        ItemSet I0 = new ItemSet(0, true);
        I0.lr1Items = closureLR1(Collections.singletonList(startItem), g);
        collection.add(I0);
        gotoMap.put(0, new LinkedHashMap<>());

        Queue<ItemSet> worklist = new ArrayDeque<>();
        worklist.add(I0);

        while (!worklist.isEmpty()) {
            ItemSet I = worklist.poll();
            for (String X : allSymbols) {
                List<LR1Item> nextItems = gotoLR1(I.lr1Items, X, g);
                if (nextItems.isEmpty()) continue;

                // Find matching state by core+lookaheads
                ItemSet existing = findMatchingLR1State(collection, nextItems);

                if (existing == null) {
                    existing = new ItemSet(collection.size(), true);
                    existing.lr1Items = nextItems;
                    collection.add(existing);
                    gotoMap.put(existing.stateId, new LinkedHashMap<>());
                    worklist.add(existing);
                }
                gotoMap.get(I.stateId).put(X, existing.stateId);
            }
        }
        return collection;
    }

    private static ItemSet findMatchingLR1State(List<ItemSet> collection, List<LR1Item> items) {
        // Build a map of core->lookaheads for the candidate
        Map<LR0Item, Set<String>> candidateMap = new HashMap<>();
        for (LR1Item it : items) candidateMap.put(it.core, it.lookaheads);

        outer:
        for (ItemSet s : collection) {
            if (!s.isLR1 || s.lr1Items.size() != items.size()) continue;
            Map<LR0Item, Set<String>> stateMap = new HashMap<>();
            for (LR1Item it : s.lr1Items) stateMap.put(it.core, it.lookaheads);
            if (stateMap.equals(candidateMap)) return s;
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Display helpers
    // ═══════════════════════════════════════════════════════════════════════

    public static String displayCollection(List<ItemSet> collection, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(title).append(" ===\n");
        sb.append("Total states: ").append(collection.size()).append("\n\n");
        for (ItemSet s : collection) sb.append(s).append("\n");
        return sb.toString();
    }
}
