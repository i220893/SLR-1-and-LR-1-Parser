import java.util.*;

/**
 * ParsingTable.java
 * Holds the ACTION and GOTO tables for SLR(1) or LR(1) parsers.
 * Detects and records shift/reduce and reduce/reduce conflicts.
 */
public class ParsingTable {

    // ── Action entry types ─────────────────────────────────────────────────
    public enum ActionType { SHIFT, REDUCE, ACCEPT, ERROR }

    public static class Action {
        public final ActionType type;
        public final int        number;  // shift → state id; reduce → production id
        public final String     repr;    // human-readable

        private Action(ActionType t, int n, String r) { type = t; number = n; repr = r; }

        public static Action shift(int state) {
            return new Action(ActionType.SHIFT,  state, "s" + state);
        }
        public static Action reduce(int prodId, Grammar.Production p) {
            return new Action(ActionType.REDUCE, prodId, "r" + prodId);
        }
        public static Action accept() {
            return new Action(ActionType.ACCEPT, -1, "acc");
        }

        @Override public String toString() { return repr; }
    }

    // ── Fields ────────────────────────────────────────────────────────────
    private final int                                    numStates;
    private final Map<Integer, Map<String, Action>>      actionTable = new LinkedHashMap<>();
    private final Map<Integer, Map<String, Integer>>     gotoTable   = new LinkedHashMap<>();
    private final List<String>                           conflicts   = new ArrayList<>();
    private final List<Grammar.Production>               productions;
    private       List<String>                           terminals   = new ArrayList<>();
    private       List<String>                           nonTerminals= new ArrayList<>();

    public ParsingTable(int numStates, List<Grammar.Production> productions) {
        this.numStates   = numStates;
        this.productions = productions;
        for (int i = 0; i < numStates; i++) {
            actionTable.put(i, new LinkedHashMap<>());
            gotoTable.put(i, new LinkedHashMap<>());
        }
    }

    // ── Setters ───────────────────────────────────────────────────────────

    /** Set the ordered column lists for display purposes. */
    public void setColumns(List<String> terminals, List<String> nonTerminals) {
        this.terminals    = new ArrayList<>(terminals);
        this.nonTerminals = new ArrayList<>(nonTerminals);
    }

    /**
     * Add an action entry. If a conflict is detected, the existing entry is
     * retained (first-come-first-served) and the conflict is recorded.
     */
    public void addAction(int state, String terminal, Action action) {
        Map<String, Action> row = actionTable.get(state);
        if (row.containsKey(terminal)) {
            Action existing = row.get(terminal);
            if (!existing.repr.equals(action.repr)) {
                String conflictType = (existing.type == ActionType.SHIFT
                                       || action.type == ActionType.SHIFT)
                                      ? "Shift/Reduce" : "Reduce/Reduce";
                String msg = String.format(
                    "%s conflict in state %d on '%s': existing='%s', new='%s'",
                    conflictType, state, terminal, existing.repr, action.repr);
                conflicts.add(msg);
                // For SLR(1): we keep both as a string to mark conflict
                row.put(terminal, conflictType.startsWith("Shift")
                        ? Action.shift(-999) : action); // mark / keep existing
                // Actually encode as a conflict marker string
                row.put(terminal, new Action(ActionType.ERROR, -1,
                        existing.repr + "/" + action.repr));
            }
            return;
        }
        row.put(terminal, action);
    }

    public void addGoto(int state, String nonTerminal, int targetState) {
        gotoTable.get(state).put(nonTerminal, targetState);
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public Action getAction(int state, String terminal) {
        Map<String, Action> row = actionTable.get(state);
        if (row == null) return null;
        return row.get(terminal);
    }

    public Integer getGoto(int state, String nonTerminal) {
        Map<String, Integer> row = gotoTable.get(state);
        if (row == null) return null;
        return row.get(nonTerminal);
    }

    public List<String> getConflicts()          { return Collections.unmodifiableList(conflicts); }
    public boolean      hasConflicts()          { return !conflicts.isEmpty(); }
    public int          getNumStates()          { return numStates; }
    public int          getNumActions()         {
        int cnt = 0;
        for (Map<String,Action> row : actionTable.values()) cnt += row.size();
        return cnt;
    }
    public int          getNumGotoEntries()     {
        int cnt = 0;
        for (Map<String,Integer> row : gotoTable.values()) cnt += row.size();
        return cnt;
    }

    // ── Display ───────────────────────────────────────────────────────────

    /** Pretty-print the complete parsing table. */
    public String display(String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(title).append(" ===\n\n");

        // Build column list (exclude augmented start from GOTO display if desired)
        List<String> termCols = new ArrayList<>(terminals);
        if (!termCols.contains(Grammar.EOF)) termCols.add(Grammar.EOF);
        List<String> ntCols = new ArrayList<>();
        for (String nt : nonTerminals) {
            // show only non-augmented start in GOTO
            ntCols.add(nt);
        }

        // Compute column widths
        int stateW = 6;
        Map<String, Integer> colW = new LinkedHashMap<>();
        for (String t  : termCols) colW.put(t,  Math.max(t.length(),  4));
        for (String nt : ntCols)   colW.put(nt, Math.max(nt.length(), 4));

        for (int state = 0; state < numStates; state++) {
            Map<String, Action>   actRow  = actionTable.get(state);
            Map<String, Integer>  gotoRow = gotoTable.get(state);
            for (String t  : termCols) {
                Action a = actRow.get(t);
                if (a != null) colW.put(t, Math.max(colW.get(t), a.repr.length()));
            }
            for (String nt : ntCols) {
                Integer g = gotoRow.get(nt);
                if (g != null) colW.put(nt, Math.max(colW.get(nt), String.valueOf(g).length()));
            }
        }

        // Header separator
        int totalW = stateW;
        for (int w : colW.values()) totalW += w + 1;
        String sep = "-".repeat(totalW + termCols.size() + ntCols.size());

        // ACTION / GOTO banner
        int actionW = 0;
        for (String t : termCols) actionW += colW.get(t) + 1;
        int gotoW = 0;
        for (String nt : ntCols) gotoW += colW.get(nt) + 1;

        sb.append(String.format("%-" + stateW + "s", ""));
        sb.append(centred("ACTION", actionW)).append("|");
        sb.append(centred("GOTO",   gotoW)).append("\n");

        // Column headers
        sb.append(String.format("%-" + stateW + "s", "State"));
        for (String t  : termCols) sb.append("|").append(centred(t,  colW.get(t)));
        sb.append("|");
        for (String nt : ntCols)   sb.append(centred(nt, colW.get(nt))).append("|");
        sb.append("\n").append(sep).append("\n");

        // Rows
        for (int state = 0; state < numStates; state++) {
            sb.append(String.format("%-" + stateW + "s", "I" + state));
            Map<String, Action>  actRow  = actionTable.get(state);
            Map<String, Integer> gotoRow = gotoTable.get(state);
            for (String t : termCols) {
                Action a = actRow.get(t);
                sb.append("|").append(centred(a == null ? "" : a.repr, colW.get(t)));
            }
            sb.append("|");
            for (String nt : ntCols) {
                Integer g = gotoRow.get(nt);
                sb.append(centred(g == null ? "" : String.valueOf(g), colW.get(nt))).append("|");
            }
            sb.append("\n");
        }
        sb.append(sep).append("\n");

        // Production key
        sb.append("\nProduction Rules:\n");
        for (Grammar.Production p : productions)
            sb.append(String.format("  r%-3d %s\n", p.id, p));

        // Conflict report
        if (!conflicts.isEmpty()) {
            sb.append("\n⚠ CONFLICTS DETECTED (").append(conflicts.size()).append("):\n");
            for (String c : conflicts) sb.append("  * ").append(c).append("\n");
        } else {
            sb.append("\n✔ No conflicts — grammar is ").append(title.contains("LR(1)") ? "LR(1)" : "SLR(1)").append(".\n");
        }

        return sb.toString();
    }

    private static String centred(String s, int width) {
        if (s.length() >= width) return s;
        int left  = (width - s.length()) / 2;
        int right = width - s.length() - left;
        return " ".repeat(left) + s + " ".repeat(right);
    }
}
