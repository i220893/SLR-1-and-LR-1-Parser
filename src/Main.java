import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Main.java
 * Entry point for the SLR(1) / LR(1) parser suite.
 *
 * Usage:
 *   java Main <grammarFile> <inputFile> [--slr | --lr1 | --both]
 *
 * grammarFile : path to grammar definition file
 * inputFile   : path to file containing one input string per line
 *               (tokens separated by spaces, one string per line)
 * mode        : --slr  → run only SLR(1)
 *               --lr1  → run only LR(1)
 *               --both → run both and compare (default)
 *
 * All output is also written to the output/ directory.
 */
public class Main {

    // ── Output directory ──────────────────────────────────────────────────
    private static final String OUT_DIR = "output";

    public static void main(String[] args) throws IOException {

        // ── Parse arguments ────────────────────────────────────────────────
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }
        String grammarFile = args[0];
        String inputFile   = args[1];
        String mode        = (args.length >= 3) ? args[2].toLowerCase() : "--both";

        boolean runSLR = mode.equals("--slr")  || mode.equals("--both");
        boolean runLR1 = mode.equals("--lr1")  || mode.equals("--both");
        if (!runSLR && !runLR1) { printUsage(); System.exit(1); }

        // ── Ensure output directory exists ────────────────────────────────
        Files.createDirectories(Paths.get(OUT_DIR));

        // ── Load grammar ───────────────────────────────────────────────────
        System.out.println("=".repeat(70));
        System.out.println("  CS4031 – Compiler Construction  |  Assignment 3");
        System.out.println("  Bottom-Up Parser: SLR(1) + LR(1)");
        System.out.println("=".repeat(70));
        System.out.println("\nLoading grammar from: " + grammarFile);

        Grammar grammar = new Grammar();
        try {
            grammar.readFromFile(grammarFile);
        } catch (IOException e) {
            System.err.println("ERROR: Cannot read grammar file: " + e.getMessage());
            System.exit(1);
        }

        // ── Display & save augmented grammar ──────────────────────────────
        String augDisplay = grammar.displayAugmented();
        String firstFollow = grammar.displayFirstFollow();
        System.out.println(augDisplay);
        System.out.println(firstFollow);
        writeFile(OUT_DIR + "/augmented_grammar.txt",
                  augDisplay + "\n" + firstFollow);

        // ── Load input strings ─────────────────────────────────────────────
        List<String> inputLines = loadInputStrings(inputFile);
        System.out.println("\nInput strings to parse (" + inputLines.size() + " strings):");
        for (int i = 0; i < inputLines.size(); i++)
            System.out.printf("  [%d] %s%n", i + 1, inputLines.get(i));

        // ── Build parsers ──────────────────────────────────────────────────
        SLRParser slr = null;
        LR1Parser lr1 = null;
        long slrBuildMs = 0, lr1BuildMs = 0;

        if (runSLR) {
            System.out.println("\n" + "─".repeat(70));
            System.out.println("  Building SLR(1) Parser...");
            long t0 = System.currentTimeMillis();
            slr = new SLRParser(grammar);
            slr.build();
            slrBuildMs = System.currentTimeMillis() - t0;
            System.out.printf("  Done in %d ms.  States: %d%n",
                              slrBuildMs, slr.getStates().size());
            if (slr.getTable().hasConflicts()) {
                System.out.println("  ⚠ SLR(1) has conflicts — grammar is NOT SLR(1)!");
                for (String c : slr.getTable().getConflicts())
                    System.out.println("    • " + c);
            } else {
                System.out.println("  ✔ Grammar is SLR(1).");
            }

            // Write SLR items and table
            String slrItems = slr.displayItems();
            String slrTable = slr.displayTable();
            System.out.println("\n" + slrItems);
            System.out.println(slrTable);
            writeFile(OUT_DIR + "/slr_items.txt",   slrItems);
            writeFile(OUT_DIR + "/slr_parsing_table.txt", slrTable);
        }

        if (runLR1) {
            System.out.println("\n" + "─".repeat(70));
            System.out.println("  Building LR(1) Parser...");
            long t0 = System.currentTimeMillis();
            lr1 = new LR1Parser(grammar);
            lr1.build();
            lr1BuildMs = System.currentTimeMillis() - t0;
            System.out.printf("  Done in %d ms.  States: %d%n",
                              lr1BuildMs, lr1.getStates().size());
            if (lr1.getTable().hasConflicts()) {
                System.out.println("  ⚠ LR(1) has conflicts — grammar is NOT LR(1)!");
                for (String c : lr1.getTable().getConflicts())
                    System.out.println("    • " + c);
            } else {
                System.out.println("  ✔ Grammar is LR(1).");
            }

            String lr1Items = lr1.displayItems();
            String lr1Table = lr1.displayTable();
            System.out.println("\n" + lr1Items);
            System.out.println(lr1Table);
            writeFile(OUT_DIR + "/lr1_items.txt",          lr1Items);
            writeFile(OUT_DIR + "/lr1_parsing_table.txt",  lr1Table);
        }

        // ── Parse each input string ────────────────────────────────────────
        StringBuilder slrTraceAll  = new StringBuilder();
        StringBuilder lr1TraceAll  = new StringBuilder();
        StringBuilder treeOutput   = new StringBuilder();

        System.out.println("\n" + "=".repeat(70));
        System.out.println("  PARSING INPUT STRINGS");
        System.out.println("=".repeat(70));

        for (int i = 0; i < inputLines.size(); i++) {
            String line = inputLines.get(i);
            List<String> tokens = tokenise(line);
            String header = String.format("%n[String %d] tokens: %s%n", i + 1, tokens);

            System.out.println(header);

            if (runSLR) {
                System.out.println("  --- SLR(1) ---");
                long pt0 = System.currentTimeMillis();
                SLRParser.ParseResult res = slr.parse(tokens);
                long parseMsSlr = System.currentTimeMillis() - pt0;
                String result = header + "=== SLR(1) ===\n" + res.toString()
                              + String.format("(parse time: %d ms)%n", parseMsSlr);
                System.out.print(res.toString());
                slrTraceAll.append(result).append("\n");

                if (res.accepted && res.parseTree != null)
                    treeOutput.append("SLR(1) - String ").append(i + 1).append(":\n")
                              .append(Tree.prettyPrint(res.parseTree)).append("\n");
            }

            if (runLR1) {
                System.out.println("  --- LR(1) ---");
                long pt0 = System.currentTimeMillis();
                SLRParser.ParseResult res = lr1.parse(tokens);
                long parseMsLr1 = System.currentTimeMillis() - pt0;
                String result = header + "=== LR(1) ===\n" + res.toString()
                              + String.format("(parse time: %d ms)%n", parseMsLr1);
                System.out.print(res.toString());
                lr1TraceAll.append(result).append("\n");

                if (res.accepted && res.parseTree != null)
                    treeOutput.append("LR(1) - String ").append(i + 1).append(":\n")
                              .append(Tree.prettyPrint(res.parseTree)).append("\n");
            }
        }

        // Write trace and tree files
        if (runSLR) writeFile(OUT_DIR + "/slr_trace.txt",   slrTraceAll.toString());
        if (runLR1) writeFile(OUT_DIR + "/lr1_trace.txt",   lr1TraceAll.toString());
        writeFile(OUT_DIR + "/parse_trees.txt", treeOutput.toString());

        // ── Comparison ────────────────────────────────────────────────────
        if (runSLR && runLR1) {
            String comparison = buildComparison(slr, lr1, slrBuildMs, lr1BuildMs);
            System.out.println("\n" + comparison);
            writeFile(OUT_DIR + "/comparison.txt", comparison);
        }

        System.out.println("\n✔ All output written to " + OUT_DIR + "/");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static List<String> loadInputStrings(String path) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String l;
            while ((l = br.readLine()) != null) {
                l = l.trim();
                if (!l.isEmpty() && !l.startsWith("#")) lines.add(l);
            }
        }
        return lines;
    }

    /** Split an input line into tokens (already space-separated). */
    private static List<String> tokenise(String line) {
        if (line.trim().isEmpty()) return Collections.emptyList();
        return Arrays.asList(line.trim().split("\\s+"));
    }

    private static void writeFile(String path, String content) throws IOException {
        Files.writeString(Paths.get(path), content);
    }

    private static String buildComparison(SLRParser slr, LR1Parser lr1,
                                          long slrMs, long lr1Ms) {
        int slrStates = slr.getStates().size();
        int lr1States = lr1.getStates().size();
        int slrActions = slr.getTable().getNumActions();
        int lr1Actions = lr1.getTable().getNumActions();
        int slrGotos   = slr.getTable().getNumGotoEntries();
        int lr1Gotos   = lr1.getTable().getNumGotoEntries();
        boolean slrConflicts = slr.getTable().hasConflicts();
        boolean lr1Conflicts = lr1.getTable().hasConflicts();

        StringBuilder sb = new StringBuilder();
        sb.append("=".repeat(70)).append("\n");
        sb.append("  COMPARISON: SLR(1) vs LR(1)\n");
        sb.append("=".repeat(70)).append("\n\n");
        sb.append(String.format("%-30s  %-15s  %-15s%n", "Metric", "SLR(1)", "LR(1)"));
        sb.append("-".repeat(62)).append("\n");
        sb.append(row("Number of states",       slrStates, lr1States));
        sb.append(row("ACTION entries",         slrActions, lr1Actions));
        sb.append(row("GOTO entries",           slrGotos,   lr1Gotos));
        sb.append(row("Total table entries",    slrActions+slrGotos, lr1Actions+lr1Gotos));
        sb.append(String.format("%-30s  %-15d  %-15d ms%n",
                                "Build time (ms)", slrMs, lr1Ms));
        sb.append(String.format("%-30s  %-15s  %-15s%n",
                                "Has conflicts",
                                slrConflicts ? "YES ⚠" : "No",
                                lr1Conflicts ? "YES ⚠" : "No"));
        sb.append("-".repeat(62)).append("\n\n");

        sb.append("Analysis:\n");
        if (!slrConflicts && !lr1Conflicts) {
            sb.append("  Both parsers handle this grammar without conflicts.\n");
            sb.append("  SLR(1) uses fewer states (").append(slrStates)
              .append(") versus LR(1) (").append(lr1States).append(").\n");
        } else if (slrConflicts && !lr1Conflicts) {
            sb.append("  ✔ This grammar demonstrates LR(1) superiority:\n");
            sb.append("    SLR(1) has ").append(slr.getTable().getConflicts().size())
              .append(" conflict(s), but LR(1) resolves them using precise lookaheads.\n");
        } else if (slrConflicts && lr1Conflicts) {
            sb.append("  Both parsers have conflicts. This grammar may be inherently ambiguous.\n");
        }
        sb.append("\nConclusion:\n");
        sb.append("  LR(1) is strictly more powerful than SLR(1) because it uses\n");
        sb.append("  per-item lookaheads instead of global FOLLOW sets for reductions.\n");
        sb.append("  This precision avoids spurious conflicts at the cost of a potentially\n");
        sb.append("  larger number of states.\n");

        return sb.toString();
    }

    private static String row(String label, int slrVal, int lr1Val) {
        return String.format("%-30s  %-15d  %-15d%n", label, slrVal, lr1Val);
    }

    private static void printUsage() {
        System.out.println("Usage: java Main <grammarFile> <inputFile> [--slr | --lr1 | --both]");
        System.out.println("  grammarFile : path to grammar file (see input/ directory)");
        System.out.println("  inputFile   : path to file with one token string per line");
        System.out.println("  mode        : --slr, --lr1, or --both (default: --both)");
    }
}
