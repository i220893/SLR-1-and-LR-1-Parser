@echo off
echo Compiling project...
if not exist out mkdir out
javac -d out src\*.java

if %ERRORLEVEL% neq 0 (
    echo Compilation failed!
    pause
    exit /b %ERRORLEVEL%
)

echo Compilation successful!
echo.

:: ── Helper: copy output to a named subfolder ──────────────────────────────
::   Usage: CALL :SAVE_OUTPUT <subfolder-name>
goto :MAIN

:SAVE_OUTPUT
    set DEST=output\%~1
    if not exist "%DEST%" mkdir "%DEST%"
    for %%F in (output\augmented_grammar.txt output\slr_items.txt output\slr_parsing_table.txt output\slr_trace.txt output\lr1_items.txt output\lr1_parsing_table.txt output\lr1_trace.txt output\comparison.txt output\parse_trees.txt) do (
        if exist "%%F" copy /Y "%%F" "%DEST%\" >nul
    )
    echo   [Saved to %DEST%]
goto :EOF

:MAIN

echo ==============================================
echo  Grammar 1: Simple Expression
echo  (only + and id — no * or parentheses)
echo  -- Valid strings --
echo ==============================================
java -cp out Main input\grammar1.txt input\input_grammar1_valid.txt --both
CALL :SAVE_OUTPUT grammar1_valid

echo.
echo ==============================================
echo  Grammar 1: Simple Expression
echo  -- Invalid strings --
echo ==============================================
java -cp out Main input\grammar1.txt input\input_grammar1_invalid.txt --both
CALL :SAVE_OUTPUT grammar1_invalid

echo.
echo ==============================================
echo  Grammar 2: Full Expression (+ * and parens)
echo  -- Valid strings --
echo ==============================================
java -cp out Main input\grammar2.txt input\input_grammar2_valid.txt --both
CALL :SAVE_OUTPUT grammar2_valid

echo.
echo ==============================================
echo  Grammar 2: Full Expression
echo  -- Invalid strings --
echo ==============================================
java -cp out Main input\grammar2.txt input\input_grammar2_invalid.txt --both
CALL :SAVE_OUTPUT grammar2_invalid

echo.
echo ==============================================
echo  Grammar 3: Classic SLR(1) Conflict Example
echo  (LR(1) succeeds, SLR(1) has conflicts)
echo  -- Valid strings --
echo ==============================================
java -cp out Main input\grammar3.txt input\input_grammar3_valid.txt --both
CALL :SAVE_OUTPUT grammar3_valid

echo.
echo ==============================================
echo  Grammar 3: Classic SLR(1) Conflict Example
echo  -- Invalid strings --
echo ==============================================
java -cp out Main input\grammar3.txt input\input_grammar3_invalid.txt --both
CALL :SAVE_OUTPUT grammar3_invalid

echo.
echo ==============================================
echo  Grammar 4: Dangling Else (ambiguous grammar)
echo  -- Valid strings --
echo ==============================================
java -cp out Main input\grammar_with_conflict.txt input\input_grammar4_valid.txt --both
CALL :SAVE_OUTPUT grammar4_valid

echo.
echo ==============================================
echo  Grammar 4: Dangling Else (ambiguous grammar)
echo  -- Invalid strings --
echo ==============================================
java -cp out Main input\grammar_with_conflict.txt input\input_grammar4_invalid.txt --both
CALL :SAVE_OUTPUT grammar4_invalid

echo.
echo Cleaning up loose root-level output files (kept in grammar subfolders)...
for %%F in (output\augmented_grammar.txt output\slr_items.txt output\slr_parsing_table.txt output\slr_trace.txt output\lr1_items.txt output\lr1_parsing_table.txt output\lr1_trace.txt output\comparison.txt output\parse_trees.txt) do (
    if exist "%%F" del /Q "%%F"
)

echo.
echo All scripts executed successfully.
echo Output for each grammar saved in output\grammar*\ subdirectories.
pause
