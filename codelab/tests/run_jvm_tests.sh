#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/.jvm-test-classes"
rm -rf "$OUT" && mkdir -p "$OUT"
javac --release 17 -d "$OUT" \
  "$ROOT/app/src/main/java/org/sigma/codelab/ScriptEngine.java" \
  "$ROOT/app/src/main/java/org/sigma/codelab/RendererExitPolicy.java" \
  "$ROOT/app/src/main/java/org/sigma/codelab/SymbolShortcutIndex.java" \
  "$ROOT/app/src/main/java/org/sigma/codelab/SymbolEditHelper.java" \
  "$ROOT/app/src/main/java/org/sigma/codelab/RunDiagnostic.java" \
  "$ROOT/tests/ScriptEngineTests.java"
java -cp "$OUT" ScriptEngineTests "$ROOT/app/src/main/assets/latex_gboard_dictionary.txt"
rm -rf "$OUT"
