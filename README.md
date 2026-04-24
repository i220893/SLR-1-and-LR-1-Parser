# CS4031 – Compiler Construction | Assignment 3
## Bottom-Up Parser: SLR(1) & LR(1)

---

## Team Members
| Roll Number | Name |
|---|---|
| 22i-XXXX | Member 1 |
| 22i-YYYY | Member 2 |

**Section:** A  
**Language:** Java

---

## Project Structure

```
Assignment3/
├── src/
│   ├── Main.java          ← Entry point & orchestration
│   ├── Grammar.java       ← CFG model, FIRST/FOLLOW sets
│   ├── Items.java         ← LR(0)/LR(1) items, CLOSURE, GOTO, canonical collections
│   ├── ParsingTable.java  ← ACTION/GOTO table, conflict detection
│   ├── SLRParser.java     ← SLR(1) table builder + parser
│   ├── LR1Parser.java     ← LR(1) table builder + parser
│   ├── Stack.java         ← Parsing stack (symbol, state) pairs
│   └── Tree.java          ← Parse tree node + pretty printer
├── input/
│   ├── grammar1.txt             ← Simple expression grammar
│   ├── grammar2.txt             ← Expression with * and ()
│   ├── grammar3.txt             ← Classic SLR conflict (LR(1) only)
│   ├── grammar_with_conflict.txt← Dangling else
│   ├── input_valid.txt          ← Valid test strings
│   └── input_invalid.txt        ← Invalid test strings
├── output/                      ← Auto-generated at runtime
│   ├── augmented_grammar.txt
│   ├── slr_items.txt
│   ├── slr_parsing_table.txt
│   ├── slr_trace.txt
│   ├── lr1_items.txt
│   ├── lr1_parsing_table.txt
│   ├── lr1_trace.txt
│   ├── comparison.txt
│   └── parse_trees.txt
└── README.md
```

---

## Compilation

Compile all Java source files from the project root:

```bash
mkdir -p out
javac -d out src/*.java
```

On Windows:
```powershell
New-Item -ItemType Directory -Force out
javac -d out src\*.java
```

---

## Execution

### Run Both Parsers (default)
```bash
java -cp out Main input/grammar2.txt input/input_valid.txt --both
```

### Run SLR(1) Only
```bash
java -cp out Main input/grammar2.txt input/input_valid.txt --slr
```

### Run LR(1) Only
```bash
java -cp out Main input/grammar2.txt input/input_valid.txt --lr1
```

### Test SLR(1) Conflict Grammar
```bash
java -cp out Main input/grammar3.txt input/input_valid.txt --both
```

### Test Invalid Strings
```bash
java -cp out Main input/grammar2.txt input/input_invalid.txt --both
```

---

## Grammar File Format

```
# Lines starting with # are comments (ignored)
NonTerminal -> production1 | production2 | ...
```

- **Non-terminals:** Multi-character names starting with uppercase (e.g., `Expr`, `Term`, `Factor`)
- **Terminals:** Lowercase words or operator symbols (e.g., `id`, `+`, `*`, `(`, `)`)
- **Epsilon:** Use `epsilon` or `@`
- **Arrow:** `->` (with spaces)
- **Alternatives:** `|` on the same line

**Example (`grammar2.txt`):**
```
Expr -> Expr + Term | Term
Term -> Term * Factor | Factor
Factor -> ( Expr ) | id
```

---

## Input String File Format

- One token string per line
- Tokens separated by spaces
- Lines starting with `#` are comments

**Example (`input_valid.txt`):**
```
id
id + id
id * id
( id + id ) * id
```

---

## Known Limitations

- Grammar 4 (dangling else) is inherently ambiguous; both parsers will report conflicts. This is expected behaviour.
- Grammar 3 demonstrates SLR(1) conflicts that LR(1) resolves — use `--both` to see the comparison.
- Very long input strings may produce wide trace output; redirect to a file for readability.

---

## Sample Commands

```bash
# Compile
javac -d out src/*.java

# Run Grammar 1 (simple expression)
java -cp out Main input/grammar1.txt input/input_valid.txt --both

# Run Grammar 2 (full expression with * and parentheses)
java -cp out Main input/grammar2.txt input/input_valid.txt --both

# Demonstrate SLR(1) vs LR(1) conflict resolution
java -cp out Main input/grammar3.txt input/input_valid.txt --both

# Test with invalid strings
java -cp out Main input/grammar2.txt input/input_invalid.txt --both
```
