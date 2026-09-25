#!/usr/bin/env python3
from pathlib import Path
import json, re, sys

root = Path(__file__).resolve().parents[1]
java = "\n".join(p.read_text(encoding="utf-8") for p in (root / "app/src/main/java").rglob("*.java"))
gradle = (root / "app/build.gradle").read_text(encoding="utf-8")
patterns = {
    "java_net": r"\\bjava\\.net\\.",
    "javax_net": r"\\bjavax\\.net\\.",
    "socket": r"\\bSocket\\s*\\(",
    "http_url_connection": r"\\bHttpURLConnection\\b",
    "webview": r"\\bWebView\\b",
    "runtime_exec": r"Runtime\\.getRuntime\\(\\)\\.exec",
    "process_builder": r"\\bProcessBuilder\\b",
}
hits = [name for name, pat in patterns.items() if re.search(pat, java)]
dep = re.search(r"dependencies\\s*\\{(?P<body>.*?)\\}", gradle, re.S)
body = dep.group("body") if dep else ""
body = "\n".join(line.split("//", 1)[0] for line in body.splitlines()).strip()
checks = {
    "no_network_or_process_api_patterns": not hits,
    "dependencies_block_empty": body == "",
    "bounded_engine_limits_present": all(x in java for x in ["maxSteps", "maxOutputChars", "maxLoopIterations", "deadlineMs"]),
    "cancel_path_present": "execution cancelled" in java,
}
result = "PASS" if all(checks.values()) else "FAIL"
print(json.dumps({"pattern_hits": hits, "checks": checks, "result": result}, indent=2, sort_keys=True))
sys.exit(0 if result == "PASS" else 1)
