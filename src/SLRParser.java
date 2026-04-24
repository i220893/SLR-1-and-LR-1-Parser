import java.util.*;

/**
 * SLRParser.java
 * Builds the SLR(1) parsing table from the canonical LR(0) collection
 * and FOLLOW sets, then parses input token sequences using the
 * shift-reduce algorithm, recording a trace and building a parse tree.
 */
public class SLRParser {

    private final Grammar             grammar;
    private       List<Items.ItemSet> states;
    private       Map<Integer, Map<String, Integer>> gotoMap = new LinkedHashMap<>();
    private       ParsingTable        table;

    // Column lists for display
    private List<String> terminalCols;
    private List<String> ntCols;

    public SLRParser(Grammar g) {
        this.grammar = g;
    }

    // ── Build ─────────────────────────────────────────────────────────────

    /** Build the canonical LR(0) collection and SLR(1) parsing table. */
    public void build() {
        states = Items.buildLR0Collection(grammar, gotoMap);
        table  = new ParsingTable(states.size(), grammar.getProductions());

        // Column ordering for display
        terminalCols = new ArrayList<>(grammar.getTerminals());
        terminalCols.add(Grammar.EOF);
        ntCols = new ArrayList<>(grammar.getNonTerminals());
        // Remove augmented start from GOTO columns (it never appears in GOTO)
        ntCols.remove(grammar.getAugStartSymbol());

        table.setColumns(terminalCols, ntCols);

        Grammar.Production augProd = grammar.getProductions().get(0); // S' -> S

        for (Items.ItemSet I : states) {
            int s = I.stateId;

            for (Items.LR0Item item : I.lr0Items) {
                String afterDot = item.symbolAfterDot();

                if (afterDot != null && grammar.isTerminal(afterDot)) {
                    // Shift action
                    Integer target = gotoMap.getOrDefault(s, Collections.emptyMap()).get(afterDot);
                    if (target != null) {
                        table.addAction(s, afterDot,
                                        ParsingTable.Action.shift(target));
                    }
                } else if (afterDot != null && grammar.isNonTerminal(afterDot)) {
                    // GOTO for non-terminals (filled separately)
                    Integer target = gotoMap.getOrDefault(s, Collections.emptyMap()).get(afterDot);
                    if (target != null) table.addGoto(s, afterDot, target);
                } else if (item.isComplete()) {
                    // Dot at end
                    if (item.production.id == augProd.id) {
                        // S' -> S •  → accept
                        table.addAction(s, Grammar.EOF, ParsingTable.Action.accept());
                    } else {
                        // Reduce on every terminal in FOLLOW(lhs)
                        Set<String> follow = grammar.follow(item.production.lhs);
                        for (String t : follow) {
                            table.addAction(s, t,
                                    ParsingTable.Action.reduce(item.production.id, item.production));
                        }
                    }
                }
            }

            // Fill GOTO entries from gotoMap for non-terminals
            Map<String, Integer> gotos = gotoMap.getOrDefault(s, Collections.emptyMap());
            for (Map.Entry<String, Integer> e : gotos.entrySet()) {
                if (grammar.isNonTerminal(e.getKey())) {
                    table.addGoto(s, e.getKey(), e.getValue());
                }
            }
        }
    }

    // ── Parse ─────────────────────────────────────────────────────────────

    /**
     * Parse a list of tokens using the SLR(1) table.
     * Returns a ParseResult containing the trace, parse tree, and status.
     */
    public ParseResult parse(List<String> tokens) {
        // Append $ if not present
        List<String> input = new ArrayList<>(tokens);
        if (input.isEmpty() || !input.get(input.size() - 1).equals(Grammar.EOF))
            input.add(Grammar.EOF);

        Stack             stack     = new Stack();
        Deque<Tree.TreeNode> treeStack = new ArrayDeque<>();
        stack.push("$", 0);

        StringBuilder trace = new StringBuilder();
        trace.append(traceHeader());

        int  ip      = 0;    // input pointer
        int  step    = 1;
        boolean accepted = false;
        String  errorMsg = null;

        while (true) {
            int    state = stack.topState();
            String sym   = input.get(ip);

            ParsingTable.Action action = table.getAction(state, sym);

            // Build remaining input string
            String remaining = String.join(" ", input.subList(ip, input.size()));
            String stackStr  = stack.display();

            if (action == null || action.type == ParsingTable.ActionType.ERROR) {
                String actionStr = action != null ? "CONFLICT: " + action.repr : "error";
                trace.append(traceRow(step, stackStr, remaining, actionStr));
                errorMsg = String.format("Syntax error at step %d: unexpected '%s' in state %d",
                                         step, sym, state);
                break;
            }

            switch (action.type) {
                case SHIFT: {
                    int nextState = action.number;
                    trace.append(traceRow(step, stackStr, remaining, "shift " + nextState));
                    stack.push(sym, nextState);
                    treeStack.push(new Tree.TreeNode(sym)); // leaf node
                    ip++;
                    break;
                }
                case REDUCE: {
                    Grammar.Production prod = grammar.getProductions().get(action.number);
                    int rhsLen = (prod.rhs.size() == 1 && prod.rhs.get(0).equals(Grammar.EPSILON))
                                  ? 0 : prod.rhs.size();
                    String actionStr = "reduce " + prod;
                    trace.append(traceRow(step, stackStr, remaining, actionStr));

                    // Pop 2*rhsLen symbols from parsing stack
                    List<Tree.TreeNode> children = new ArrayList<>();
                    for (int i = 0; i < rhsLen; i++) {
                        stack.pop();
                        children.add(0, treeStack.pop()); // reverse to get left-to-right
                    }

                    // If epsilon production, create epsilon leaf
                    if (rhsLen == 0) {
                        children.add(new Tree.TreeNode("ε"));
                    }

                    // Push lhs with GOTO state
                    int topState2  = stack.topState();
                    Integer gotoState = table.getGoto(topState2, prod.lhs);
                    if (gotoState == null) {
                        errorMsg = "GOTO error after reducing to " + prod.lhs;
                        break;
                    }
                    stack.push(prod.lhs, gotoState);
                    treeStack.push(new Tree.TreeNode(prod.lhs, children));
                    break;
                }
                case ACCEPT: {
                    trace.append(traceRow(step, stackStr, remaining, "accept"));
                    accepted = true;
                    break;
                }
                default: break;
            }
            step++;
            if (accepted || errorMsg != null) break;
        }

        Tree.TreeNode root = (!treeStack.isEmpty()) ? treeStack.peek() : null;
        return new ParseResult(accepted, errorMsg, trace.toString(), root);
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public ParsingTable        getTable()  { return table; }
    public List<Items.ItemSet> getStates() { return states; }

    public String displayItems() {
        return Items.displayCollection(states, "Canonical LR(0) Item Sets (SLR(1))");
    }

    public String displayTable() {
        return table.display("SLR(1) Parsing Table");
    }

    // ── Trace helpers ─────────────────────────────────────────────────────

    private static final int SW = 5, STW = 40, IW = 30, AW = 35;

    private String traceHeader() {
        return String.format("%-" + SW + "s | %-" + STW + "s | %-" + IW + "s | %-" + AW + "s%n",
                             "Step", "Stack (symbol:state)", "Remaining Input", "Action")
             + "-".repeat(SW + STW + IW + AW + 9) + "\n";
    }

    private String traceRow(int step, String stack, String input, String action) {
        // Truncate long strings gracefully
        if (stack.length() > STW) stack = "..." + stack.substring(stack.length() - (STW - 3));
        if (input.length() > IW)  input = input.substring(0, IW - 3) + "...";
        return String.format("%-" + SW + "d | %-" + STW + "s | %-" + IW + "s | %-" + AW + "s%n",
                             step, stack, input, action);
    }

    // ── ParseResult ───────────────────────────────────────────────────────

    public static class ParseResult {
        public final boolean       accepted;
        public final String        errorMessage;
        public final String        trace;
        public final Tree.TreeNode parseTree;

        public ParseResult(boolean accepted, String errorMessage,
                           String trace, Tree.TreeNode parseTree) {
            this.accepted     = accepted;
            this.errorMessage = errorMessage;
            this.trace        = trace;
            this.parseTree    = parseTree;
        }

        @Override public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(trace);
            if (accepted) {
                sb.append("\n✔ String ACCEPTED\n");
                if (parseTree != null) {
                    sb.append("\nParse Tree:\n");
                    sb.append(Tree.prettyPrint(parseTree));
                    sb.append("\nS-Expression: ").append(Tree.toSExpression(parseTree)).append("\n");
                }
            } else {
                sb.append("\n✘ String REJECTED\n");
                if (errorMessage != null) sb.append("  Error: ").append(errorMessage).append("\n");
            }
            return sb.toString();
        }
    }
}
