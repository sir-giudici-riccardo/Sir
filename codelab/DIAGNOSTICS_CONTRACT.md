# SIGMA Code Lab diagnostics contract — R1

The in-app diagnostic receipt is intentionally metadata-only.

Included:
- schema version and UTC timestamp;
- app version;
- execution engine;
- PASS/STOPPED status;
- elapsed time;
- SIGMA step count when applicable;
- WebView provider when applicable;
- selected theme;
- source character count;
- output character count.

Excluded:
- source text;
- output text;
- clipboard history;
- filenames;
- device identifiers;
- network information.

The user must explicitly press `COPY DIAG JSON` to place the receipt on the clipboard.

`DIAGNOSTIC_METADATA != SOURCE_CONTENT`
`DIAGNOSTIC_COPY != NETWORK_UPLOAD`
