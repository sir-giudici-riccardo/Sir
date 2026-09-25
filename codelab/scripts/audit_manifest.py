#!/usr/bin/env python3
from pathlib import Path
import json, re, sys

root = Path(__file__).resolve().parents[1]
manifest = root / "app/src/main/AndroidManifest.xml"
text = manifest.read_text(encoding="utf-8")
permissions = re.findall(r'<uses-permission\\b[^>]*android:name="([^"]+)"', text)
checks = {
    "manifest_exists": manifest.exists(),
    "zero_declared_permissions": len(permissions) == 0,
    "internet_absent": "android.permission.INTERNET" not in permissions,
    "cleartext_disabled": 'android:usesCleartextTraffic="false"' in text,
    "backup_disabled": 'android:allowBackup="false"' in text,
    "package_not_exporting_services_or_receivers": "<service" not in text and "<receiver" not in text,
}
result = "PASS" if all(checks.values()) else "FAIL"
print(json.dumps({"permissions": permissions, "checks": checks, "result": result}, indent=2, sort_keys=True))
sys.exit(0 if result == "PASS" else 1)
