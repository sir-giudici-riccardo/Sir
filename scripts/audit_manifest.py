#!/usr/bin/env python3
from pathlib import Path
import json, re, sys
root=Path(__file__).resolve().parents[1]
manifest=root/'source/app/src/main/AndroidManifest.xml'
text=manifest.read_text(encoding='utf-8')
permissions=re.findall(r'<uses-permission\\b[^>]*android:name="([^"]+)"', text)
checks={
 'manifest_exists': manifest.exists(),
 'zero_declared_permissions': len(permissions)==0,
 'internet_absent': 'android.permission.INTERNET' not in permissions,
 'cleartext_disabled': 'android:usesCleartextTraffic="false"' in text,
 'backup_disabled': 'android:allowBackup="false"' in text,
}
out={'permissions':permissions,'checks':checks,'result':'PASS' if all(checks.values()) else 'FAIL'}
print(json.dumps(out,indent=2,sort_keys=True))
sys.exit(0 if out['result']=='PASS' else 1)
