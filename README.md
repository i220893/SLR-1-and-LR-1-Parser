# CS4031 – Compiler Construction | Assignment 3
## Bottom-Up Parser: SLR(1) & LR(1)

---

## Team Members
| Roll Number | Name |
|---|---|
| 22i-0893 | Hussain Waseem Syed |
| 22i-1409 | Mohammad Abbas |

**Section:** C  
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
│   ├── input_grammarX_valid.txt ← Valid test strings for each grammar
│   └── input_grammarX_invalid.txt ← Invalid test strings for each grammar
├── output/                      ← Auto-generated at runtime
│   ├── grammar1_valid/          ← Results organised per grammar & validity
│   ├── grammar1_invalid/
│   └── ...
├── docs/
│   └── report.tex               ← LaTeX detailed project report
├── run_all.bat                  ← Windows batch script to run entire test suite
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

The easiest way to execute all grammars with valid and invalid input strings, and save all the outputs into organised subfolders, is to run the batch script on Windows:

```cmd
.\run_all.bat
```

### Manual Execution: Run Both Parsers (default)
```bash
java -cp out Main input/grammar2.txt input/input_grammar2_valid.txt --both
```

### Manual Execution: Run SLR(1) Only
```bash
java -cp out Main input/grammar2.txt input/input_grammar2_valid.txt --slr
```

### Manual Execution: Run LR(1) Only
```bash
java -cp out Main input/grammar2.txt input/input_grammar2_valid.txt --lr1
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

**Example (`input_grammar2_valid.txt`):**
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
- Very long input strings may produce wide trace output; redirect to a file for readability (or check the generated `output/` subfolders).
