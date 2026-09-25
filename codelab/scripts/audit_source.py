#!/usr/bin/env python3
from pathlib import Path
import json, re, sys

root = Path(__file__).resolve().parents[1]
java_root = root / "app/src/main/java"
all_java = list(java_root.rglob("*.java"))
js_engine_path = java_root / "org/sigma/codelab/LocalJavaScriptEngine.java"

ordinary_text = "\n".join(
    p.read_text(encoding="utf-8")
    for p in all_java
    if p != js_engine_path
)
js_text = js_engine_path.read_text(encoding="utf-8") if js_engine_path.exists() else ""
all_text = ordinary_text + "\n" + js_text
gradle = (root / "app/build.gradle").read_text(encoding="utf-8")

ordinary_forbidden = {
    "java_net": "java.net.",
    "javax_net": "javax.net.",
    "socket": "Socket(",
    "http_url_connection": "HttpURLConnection",
    "webview_import_outside_js_engine": "import android.webkit.WebView",
    "webview_instantiation_outside_js_engine": "new WebView(",
    "runtime_exec": "Runtime.getRuntime().exec",
    "process_builder": "ProcessBuilder",
}
ordinary_hits = [
    name for name, literal in ordinary_forbidden.items()
    if literal in ordinary_text
]

js_required = {
    "javascript_enabled": "setJavaScriptEnabled(true)",
    "network_loads_blocked": "setBlockNetworkLoads(true)",
    "file_access_disabled": "setAllowFileAccess(false)",
    "content_access_disabled": "setAllowContentAccess(false)",
    "dom_storage_disabled": "setDomStorageEnabled(false)",
    "database_disabled": "setDatabaseEnabled(false)",
    "geolocation_disabled": "setGeolocationEnabled(false)",
    "mixed_content_never": "MIXED_CONTENT_NEVER_ALLOW",
    "external_navigation_blocked": "shouldOverrideUrlLoading",
    "evaluate_javascript": "evaluateJavascript(",
    "renderer_termination": "getWebViewRenderProcess()",
    "renderer_loss_callback": "onRenderProcessGone(",
    "renderer_loss_fail_closed": "RendererExitPolicy.message(",
    "renderer_loss_handled": "return true;",
    "debugging_disabled": "setWebContentsDebuggingEnabled(false)",
}

js_forbidden = {
    "javascript_interface_bridge": "addJavascriptInterface",
    "network_unblocked": "setBlockNetworkLoads(false)",
    "file_access_enabled": "setAllowFileAccess(true)",
    "content_access_enabled": "setAllowContentAccess(true)",
    "dom_storage_enabled": "setDomStorageEnabled(true)",
    "database_enabled": "setDatabaseEnabled(true)",
    "runtime_exec": "Runtime.getRuntime().exec",
    "process_builder": "ProcessBuilder",
    "java_net": "java.net.",
    "javax_net": "javax.net.",
}

js_required_missing = [name for name, literal in js_required.items() if literal not in js_text]
js_forbidden_hits = [name for name, literal in js_forbidden.items() if literal in js_text]

dep = re.search(r"dependencies\s*\{(?P<body>.*?)\}", gradle, re.S)
body = dep.group("body") if dep else ""
body = "\n".join(line.split("//", 1)[0] for line in body.splitlines()).strip()

checks = {
    "ordinary_code_has_no_network_process_or_webview_api": not ordinary_hits,
    "javascript_engine_file_present": js_engine_path.exists(),
    "javascript_engine_hardening_complete": not js_required_missing,
    "javascript_engine_forbidden_paths_absent": not js_forbidden_hits,
    "renderer_exit_policy_present": "class RendererExitPolicy" in ordinary_text,
    "dependencies_block_empty": body == "",
    "bounded_sigma_engine_limits_present": all(
        x in all_text for x in ["maxSteps", "maxOutputChars", "maxLoopIterations", "deadlineMs"]
    ),
    "sigma_cancel_path_present": "execution cancelled" in all_text,
}

result = "PASS" if all(checks.values()) else "FAIL"
print(json.dumps(
    {
        "ordinary_hits": ordinary_hits,
        "js_required_missing": js_required_missing,
        "js_forbidden_hits": js_forbidden_hits,
        "checks": checks,
        "result": result,
    },
    indent=2,
    sort_keys=True
))
sys.exit(0 if result == "PASS" else 1)
