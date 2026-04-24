import java.util.*;

/**
 * LR1Parser.java
 * Builds the LR(1) parsing table from the canonical LR(1) collection
 * (with embedded lookaheads), then parses input token sequences using
 * the same shift-reduce algorithm as SLRParser.
 *
 * Key difference from SLR(1):
 *   Reductions happen ONLY on the specific lookahead in the LR(1) item,
 *   not on all of FOLLOW(A).  This gives more precise conflict resolution.
 */
public class LR1Parser {

    private final Grammar             grammar;
    private       List<Items.ItemSet> states;
    private       Map<Integer, Map<String, Integer>> gotoMap = new LinkedHashMap<>();
    private       ParsingTable        table;

    private List<String> terminalCols;
    private List<String> ntCols;

    public LR1Parser(Grammar g) {
        this.grammar = g;
    }

    // ── Build ─────────────────────────────────────────────────────────────

    /** Build the canonical LR(1) collection and LR(1) parsing table. */
    public void build() {
        states = Items.buildLR1Collection(grammar, gotoMap);
        table  = new ParsingTable(states.size(), grammar.getProductions());

        terminalCols = new ArrayList<>(grammar.getTerminals());
        terminalCols.add(Grammar.EOF);
        ntCols = new ArrayList<>(grammar.getNonTerminals());
        ntCols.remove(grammar.getAugStartSymbol());

        table.setColumns(terminalCols, ntCols);

        Grammar.Production augProd = grammar.getProductions().get(0);

        for (Items.ItemSet I : states) {
            int s = I.stateId;

            for (Items.LR1Item lr1item : I.lr1Items) {
                Items.LR0Item item    = lr1item.core;
                String        afterDot = item.symbolAfterDot();

                if (afterDot != null && grammar.isTerminal(afterDot)) {
                    // Shift
                    Integer target = gotoMap.getOrDefault(s, Collections.emptyMap()).get(afterDot);
                    if (target != null)
                        table.addAction(s, afterDot, ParsingTable.Action.shift(target));
                } else if (afterDot != null && grammar.isNonTerminal(afterDot)) {
                    // GOTO
                    Integer target = gotoMap.getOrDefault(s, Collections.emptyMap()).get(afterDot);
                    if (target != null) table.addGoto(s, afterDot, target);
                } else if (item.isComplete()) {
                    // Reduce
                    if (item.production.id == augProd.id) {
                        // S' -> S •, $ → accept
                        if (lr1item.lookaheads.contains(Grammar.EOF))
                            table.addAction(s, Grammar.EOF, ParsingTable.Action.accept());
                    } else {
                        // Only on the specific lookaheads in the LR(1) item
                        for (String la : lr1item.lookaheads) {
                            table.addAction(s, la,
                                    ParsingTable.Action.reduce(item.production.id, item.production));
                        }
                    }
                }
            }

            // Fill GOTO entries from gotoMap for non-terminals
            Map<String, Integer> gotos = gotoMap.getOrDefault(s, Collections.emptyMap());
            for (Map.Entry<String, Integer> e : gotos.entrySet()) {
                if (grammar.isNonTerminal(e.getKey()))
                    table.addGoto(s, e.getKey(), e.getValue());
            }
        }
    }

    // ── Parse ─────────────────────────────────────────────────────────────

    /** Parse using the LR(1) table (algorithm identical to SLR). */
    public SLRParser.ParseResult parse(List<String> tokens) {
        List<String> input = new ArrayList<>(tokens);
        if (input.isEmpty() || !input.get(input.size() - 1).equals(Grammar.EOF))
            input.add(Grammar.EOF);

        Stack             stack     = new Stack();
        Deque<Tree.TreeNode> treeStack = new ArrayDeque<>();
        stack.push("$", 0);

        StringBuilder trace = new StringBuilder();
        trace.append(traceHeader());

        int  ip       = 0;
        int  step     = 1;
        boolean accepted  = false;
        String  errorMsg  = null;

        while (true) {
            int    state = stack.topState();
            String sym   = input.get(ip);

            ParsingTable.Action action = table.getAction(state, sym);
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
                    treeStack.push(new Tree.TreeNode(sym));
                    ip++;
                    break;
                }
                case REDUCE: {
                    Grammar.Production prod = grammar.getProductions().get(action.number);
                    int rhsLen = (prod.rhs.size() == 1 && prod.rhs.get(0).equals(Grammar.EPSILON))
                                  ? 0 : prod.rhs.size();
                    trace.append(traceRow(step, stackStr, remaining, "reduce " + prod));

                    List<Tree.TreeNode> children = new ArrayList<>();
                    for (int i = 0; i < rhsLen; i++) {
                        stack.pop();
                        children.add(0, treeStack.pop());
                    }
                    if (rhsLen == 0) children.add(new Tree.TreeNode("ε"));

                    int     topState2 = stack.topState();
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
        return new SLRParser.ParseResult(accepted, errorMsg, trace.toString(), root);
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public ParsingTable        getTable()  { return table; }
    public List<Items.ItemSet> getStates() { return states; }

    public String displayItems() {
        return Items.displayCollection(states, "Canonical LR(1) Item Sets");
    }

    public String displayTable() {
        return table.display("LR(1) Parsing Table");
    }

    // ── Trace helpers ─────────────────────────────────────────────────────

    private static final int SW = 5, STW = 40, IW = 30, AW = 35;

    private String traceHeader() {
        return String.format("%-" + SW + "s | %-" + STW + "s | %-" + IW + "s | %-" + AW + "s%n",
                             "Step", "Stack (symbol:state)", "Remaining Input", "Action")
             + "-".repeat(SW + STW + IW + AW + 9) + "\n";
    }

    private String traceRow(int step, String stack, String input, String action) {
        if (stack.length() > STW) stack = "..." + stack.substring(stack.length() - (STW - 3));
        if (input.length() > IW)  input = input.substring(0, IW - 3) + "...";
        return String.format("%-" + SW + "d | %-" + STW + "s | %-" + IW + "s | %-" + AW + "s%n",
                             step, stack, input, action);
    }
}
