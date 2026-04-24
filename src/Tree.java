import java.util.*;

/**
 * Tree.java
 * Parse tree node and display utilities.
 * During parsing, each reduce step creates an internal node whose children
 * are the RHS symbols being reduced.
 */
public class Tree {

    // ── Node ──────────────────────────────────────────────────────────────
    public static class TreeNode {
        public final String          label;
        public final List<TreeNode>  children;
        public final boolean         isLeaf;

        /** Leaf node (terminal). */
        public TreeNode(String label) {
            this.label    = label;
            this.children = Collections.emptyList();
            this.isLeaf   = true;
        }

        /** Internal node (non-terminal) with children. */
        public TreeNode(String label, List<TreeNode> children) {
            this.label    = label;
            this.children = Collections.unmodifiableList(children);
            this.isLeaf   = false;
        }
    }

    // ── Pretty printer ────────────────────────────────────────────────────

    /**
     * Returns a text-based tree drawing, e.g.:
     *
     *   Expr
     *   ├── Expr
     *   │   └── Term
     *   │       └── Factor
     *   │           └── id
     *   ├── +
     *   └── Term
     *       └── ...
     */
    public static String prettyPrint(TreeNode root) {
        StringBuilder sb = new StringBuilder();
        printNode(root, "", "", sb);
        return sb.toString();
    }

    private static void printNode(TreeNode node, String prefix, String childPrefix,
                                  StringBuilder sb) {
        sb.append(prefix).append(node.label).append("\n");
        for (int i = 0; i < node.children.size(); i++) {
            TreeNode child = node.children.get(i);
            boolean  last  = (i == node.children.size() - 1);
            printNode(child,
                      childPrefix + (last ? "└── " : "├── "),
                      childPrefix + (last ? "    " : "│   "),
                      sb);
        }
    }

    /**
     * Compact single-line S-expression representation, e.g.:
     *   (Expr (Expr (Term (Factor id))) + (Term (Factor id)))
     */
    public static String toSExpression(TreeNode root) {
        if (root.isLeaf) return root.label;
        StringBuilder sb = new StringBuilder("(").append(root.label);
        for (TreeNode child : root.children) {
            sb.append(" ").append(toSExpression(child));
        }
        sb.append(")");
        return sb.toString();
    }
}
