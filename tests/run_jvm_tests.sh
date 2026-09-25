#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/.jvm-test-classes"
rm -rf "$OUT" && mkdir -p "$OUT"
javac --release 17 -d "$OUT" \
  "$ROOT/source/app/src/main/java/org/sigma/mobileprobe/CpuParsers.java" \
  "$ROOT/source/app/src/main/java/org/sigma/mobileprobe/BenchmarkEngine.java" \
  "$ROOT/source/app/src/main/java/org/sigma/mobileprobe/Stats.java" \
  "$ROOT/tests/PureJvmTests.java"
java -cp "$OUT" PureJvmTests
rm -rf "$OUT"
