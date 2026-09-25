#!/usr/bin/env python3
from pathlib import Path
import json, re, sys

root = Path(__file__).resolve().parents[1]
java = "\n".join(p.read_text(encoding="utf-8") for p in (root / "app/src/main/java").rglob("*.java"))
gradle = (root / "app/build.gradle").read_text(encoding="utf-8")

forbidden_literals = {
    "java_net": "java.net.",
    "javax_net": "javax.net.",
    "socket": "Socket(",
    "http_url_connection": "HttpURLConnection",
    "webview": "WebView",
    "runtime_exec": "Runtime.getRuntime().exec",
    "process_builder": "ProcessBuilder",
}
hits = [name for name, literal in forbidden_literals.items() if literal in java]

dep = re.search(r"dependencies\s*\{(?P<body>.*?)\}", gradle, re.S)
body = dep.group("body") if dep else ""
body = "\n".join(line.split("//", 1)[0] for line in body.splitlines()).strip()

checks = {
    "no_network_or_process_api_patterns": not hits,
    "dependencies_block_empty": body == "",
    "bounded_engine_limits_present": all(
        x in java for x in ["maxSteps", "maxOutputChars", "maxLoopIterations", "deadlineMs"]
    ),
    "cancel_path_present": "execution cancelled" in java,
}

result = "PASS" if all(checks.values()) else "FAIL"
print(json.dumps(
    {"pattern_hits": hits, "checks": checks, "result": result},
    indent=2,
    sort_keys=True
))
sys.exit(0 if result == "PASS" else 1)
