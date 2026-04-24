import java.io.*;
import java.util.*;

/**
 * Grammar.java
 * Reads a Context-Free Grammar from a file, augments it with a new start symbol,
 * and computes FIRST and FOLLOW sets.
 *
 * Grammar file format:
 *   NonTerminal -> production1 | production2 | ...
 *   Use 'epsilon' or '@' for epsilon productions.
 *   Non-terminals: multi-character names starting with uppercase (e.g., Expr, Term).
 *   Terminals: lowercase letters, operators, keywords, single-char symbols.
 */
public class Grammar {

    // ── Nested type: one production rule ──────────────────────────────────────
    public static class Production {
        public final String lhs;              // left-hand side non-terminal
        public final List<String> rhs;        // right-hand side symbols (may contain "epsilon")
        public final int id;                  // unique production index (0-based)

        public Production(int id, String lhs, List<String> rhs) {
            this.id  = id;
            this.lhs = lhs;
            this.rhs = Collections.unmodifiableList(rhs);
        }

        @Override public String toString() {
            return lhs + " -> " + (rhs.isEmpty() ? "ε" : String.join(" ", rhs));
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────
    private final List<Production>       productions    = new ArrayList<>();
    private final LinkedHashSet<String>  nonTerminals   = new LinkedHashSet<>();
    private final LinkedHashSet<String>  terminals      = new LinkedHashSet<>();
    private String                       startSymbol;
    private String                       augStartSymbol; // S' name

    // Computed lazily
    private Map<String, Set<String>> firstSets  = null;
    private Map<String, Set<String>> followSets = null;

    public static final String EPSILON = "epsilon";
    public static final String EOF     = "$";

    // ── Construction ─────────────────────────────────────────────────────────
    public Grammar() {}

    /** Read the grammar from a file and augment it. */
    public void readFromFile(String filename) throws IOException {
        List<String[]> rawProds = new ArrayList<>(); // [lhs, rhs-token...]

        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                // Support format: "Lhs -> alt1 | alt2" on one line
                // or just "Lhs -> alt1" with | split
                int arrowIdx = line.indexOf("->");
                if (arrowIdx < 0) continue; // skip lines without arrow

                String lhs = line.substring(0, arrowIdx).trim();
                String rhsPart = line.substring(arrowIdx + 2).trim();

                // Split alternatives by '|'
                String[] alternatives = rhsPart.split("\\|");
                for (String alt : alternatives) {
                    String[] tokens = alt.trim().split("\\s+");
                    String[] entry  = new String[tokens.length + 1];
                    entry[0] = lhs;
                    System.arraycopy(tokens, 0, entry, 1, tokens.length);
                    rawProds.add(entry);
                }
            }
        }

        if (rawProds.isEmpty())
            throw new IOException("Grammar file is empty or has no productions.");

        // First production's LHS is the start symbol
        startSymbol = rawProds.get(0)[0];

        // Collect all non-terminals first (anything appearing as LHS)
        for (String[] raw : rawProds)
            nonTerminals.add(raw[0]);

        // Build productions and collect terminals
        for (String[] raw : rawProds) {
            String lhs = raw[0];
            List<String> rhs = new ArrayList<>();
            for (int i = 1; i < raw.length; i++) {
                String sym = raw[i].trim();
                if (sym.isEmpty()) continue;
                // normalise epsilon
                if (sym.equals("@") || sym.equalsIgnoreCase("epsilon")) {
                    sym = EPSILON;
                }
                rhs.add(sym);
                if (!nonTerminals.contains(sym) && !sym.equals(EPSILON))
                    terminals.add(sym);
            }
            productions.add(new Production(productions.size(), lhs, rhs));
        }

        // Augment the grammar
        augmentGrammar();
    }

    /** Augment: add S' -> S at position 0. */
    private void augmentGrammar() {
        // Choose S' name that doesn't clash
        augStartSymbol = startSymbol + "Prime";
        while (nonTerminals.contains(augStartSymbol))
            augStartSymbol += "Prime";

        // Shift existing production IDs by 1
        List<Production> old = new ArrayList<>(productions);
        productions.clear();

        Production augProd = new Production(0, augStartSymbol,
                                            Collections.singletonList(startSymbol));
        productions.add(augProd);

        for (Production p : old) {
            productions.add(new Production(productions.size(), p.lhs, new ArrayList<>(p.rhs)));
        }

        nonTerminals.add(augStartSymbol);
        // Make augStartSymbol appear first in iteration order
        LinkedHashSet<String> reordered = new LinkedHashSet<>();
        reordered.add(augStartSymbol);
        reordered.addAll(nonTerminals);
        nonTerminals.clear();
        nonTerminals.addAll(reordered);

        // Reset sets so they will be re-computed
        firstSets  = null;
        followSets = null;
    }

    // ── FIRST ─────────────────────────────────────────────────────────────────

    /** Compute FIRST(symbol). Returns a mutable set. */
    public Set<String> first(String symbol) {
        if (firstSets == null) computeFirstSets();
        return firstSets.getOrDefault(symbol, new HashSet<>());
    }

    /** Compute FIRST(alpha) where alpha is a list of symbols. */
    public Set<String> firstOfSequence(List<String> alpha, int from) {
        Set<String> result = new HashSet<>();
        if (from >= alpha.size()) {
            result.add(EPSILON);
            return result;
        }
        for (int i = from; i < alpha.size(); i++) {
            String sym = alpha.get(i);
            if (sym.equals(EPSILON)) {
                result.add(EPSILON);
                break;
            }
            Set<String> fi = first(sym);
            result.addAll(fi);
            result.remove(EPSILON);
            if (!fi.contains(EPSILON)) break;
            if (i == alpha.size() - 1) result.add(EPSILON);
        }
        return result;
    }

    private void computeFirstSets() {
        firstSets = new HashMap<>();

        // Initialise for terminals and epsilon
        for (String t : terminals) {
            Set<String> s = new HashSet<>();
            s.add(t);
            firstSets.put(t, s);
        }
        firstSets.put(EPSILON, new HashSet<>(Collections.singleton(EPSILON)));
        firstSets.put(EOF,     new HashSet<>(Collections.singleton(EOF)));

        for (String nt : nonTerminals)
            firstSets.put(nt, new HashSet<>());

        boolean changed = true;
        while (changed) {
            changed = false;
            for (Production p : productions) {
                Set<String> lhsFirst = firstSets.get(p.lhs);
                int before = lhsFirst.size();

                List<String> rhs = p.rhs;
                if (rhs.isEmpty() || (rhs.size() == 1 && rhs.get(0).equals(EPSILON))) {
                    lhsFirst.add(EPSILON);
                } else {
                    boolean allCanBeEmpty = true;
                    for (String sym : rhs) {
                        Set<String> fi = firstSets.getOrDefault(sym, new HashSet<>());
                        lhsFirst.addAll(fi);
                        lhsFirst.remove(EPSILON);
                        if (!fi.contains(EPSILON)) {
                            allCanBeEmpty = false;
                            break;
                        }
                    }
                    if (allCanBeEmpty) lhsFirst.add(EPSILON);
                }
                if (lhsFirst.size() != before) changed = true;
            }
        }
    }

    // ── FOLLOW ────────────────────────────────────────────────────────────────

    public Set<String> follow(String nonTerminal) {
        if (followSets == null) computeFollowSets();
        return followSets.getOrDefault(nonTerminal, new HashSet<>());
    }

    private void computeFollowSets() {
        if (firstSets == null) computeFirstSets();
        followSets = new HashMap<>();

        for (String nt : nonTerminals)
            followSets.put(nt, new HashSet<>());

        // FOLLOW(S') = {$}
        followSets.get(augStartSymbol).add(EOF);

        boolean changed = true;
        while (changed) {
            changed = false;
            for (Production p : productions) {
                List<String> rhs = p.rhs;
                for (int i = 0; i < rhs.size(); i++) {
                    String B = rhs.get(i);
                    if (!nonTerminals.contains(B)) continue;

                    Set<String> followB = followSets.get(B);
                    int before = followB.size();

                    // Compute FIRST(beta) where beta = rhs[i+1..]
                    Set<String> firstBeta = firstOfSequence(rhs, i + 1);

                    followB.addAll(firstBeta);
                    followB.remove(EPSILON);

                    if (firstBeta.contains(EPSILON)) {
                        followB.addAll(followSets.get(p.lhs));
                    }

                    if (followB.size() != before) changed = true;
                }
            }
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public List<Production>      getProductions()    { return Collections.unmodifiableList(productions); }
    public Set<String>           getNonTerminals()   { return Collections.unmodifiableSet(nonTerminals); }
    public Set<String>           getTerminals()      { return Collections.unmodifiableSet(terminals); }
    public String                getStartSymbol()    { return startSymbol; }
    public String                getAugStartSymbol() { return augStartSymbol; }

    public boolean isNonTerminal(String sym) { return nonTerminals.contains(sym); }
    public boolean isTerminal(String sym)    { return terminals.contains(sym) || sym.equals(EOF); }

    /** Return productions whose LHS is the given non-terminal. */
    public List<Production> productionsFor(String nt) {
        List<Production> result = new ArrayList<>();
        for (Production p : productions)
            if (p.lhs.equals(nt)) result.add(p);
        return result;
    }

    // ── Display ───────────────────────────────────────────────────────────────

    public String displayAugmented() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Augmented Grammar ===\n");
        for (Production p : productions)
            sb.append(String.format("  (%d) %s\n", p.id, p));
        sb.append("\nTerminals   : ").append(terminals).append("\n");
        sb.append("NonTerminals: ").append(nonTerminals).append("\n");
        sb.append("Start Symbol: ").append(augStartSymbol).append("\n");
        return sb.toString();
    }

    public String displayFirstFollow() {
        if (firstSets  == null) computeFirstSets();
        if (followSets == null) computeFollowSets();
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== FIRST Sets ===\n");
        for (String nt : nonTerminals)
            sb.append(String.format("  FIRST(%-15s) = %s\n", nt, sortedSet(firstSets.get(nt))));
        sb.append("\n=== FOLLOW Sets ===\n");
        for (String nt : nonTerminals)
            sb.append(String.format("  FOLLOW(%-15s) = %s\n", nt, sortedSet(followSets.get(nt))));
        return sb.toString();
    }

    private static List<String> sortedSet(Set<String> s) {
        List<String> l = new ArrayList<>(s);
        Collections.sort(l);
        return l;
    }
}
