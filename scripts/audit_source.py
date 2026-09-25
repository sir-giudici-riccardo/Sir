#!/usr/bin/env python3
from pathlib import Path
import json,re,sys
root=Path(__file__).resolve().parents[1]
java='\n'.join(p.read_text(encoding='utf-8') for p in (root/'source/app/src/main/java').rglob('*.java'))
gradle=(root/'source/app/build.gradle').read_text(encoding='utf-8')
manifest=(root/'source/app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
network_patterns=[
    r'\bjava\.net\.', r'\bjavax\.net\.', r'\bokhttp\b', r'\bretrofit\b',
    r'\bHttpURLConnection\b', r'\bSocket\s*\(', r'\bWebView\b'
]
hits=[pat for pat in network_patterns if re.search(pat,java,re.I)]
dep_block=re.search(r'dependencies\s*\{(?P<body>.*?)\}',gradle,re.S)
body=dep_block.group('body') if dep_block else ''
body='\n'.join(line.split('//',1)[0] for line in body.splitlines()).strip()
checks={
  'no_network_api_patterns': not hits,
  'dependencies_block_empty': body=='',
  'no_uses_permission': '<uses-permission' not in manifest,
  'no_internet_permission_literal_in_manifest': 'android.permission.INTERNET' not in manifest,
}
out={'network_pattern_hits':hits,'dependencies_body_after_comment_strip':body,'checks':checks,
     'result':'PASS' if all(checks.values()) else 'FAIL'}
print(json.dumps(out,indent=2,sort_keys=True))
sys.exit(0 if out['result']=='PASS' else 1)
