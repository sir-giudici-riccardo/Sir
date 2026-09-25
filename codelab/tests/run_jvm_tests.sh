#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/.jvm-test-classes"
rm -rf "$OUT" && mkdir -p "$OUT"
javac --release 17 -d "$OUT" \
  "$ROOT/app/src/main/java/org/sigma/codelab/ScriptEngine.java" \
  "$ROOT/tests/ScriptEngineTests.java"
java -cp "$OUT" ScriptEngineTests
rm -rf "$OUT"
