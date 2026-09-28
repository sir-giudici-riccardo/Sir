# SIGMA Code Lab roadmap

This is an incremental roadmap, not a promise that every item will ship.

## 0.1.x — usable local core
- stabilize editor/runtime UX;
- preserve deterministic tests;
- improve errors and line reporting;
- add more examples and documentation;
- gather real on-device usage failures.

## 0.2.x — structured programs
- lists and indexed access;
- user-defined functions;
- richer string operations;
- structured return values;
- import/export via Android Storage Access Framework without broad storage permission.

## 0.3.x — project workflow
- multiple local files/projects;
- dependency-free standard modules;
- run history and reproducibility receipts;
- code formatting and lightweight static checks.

## 0.4.x — scientific workbench hardening
- preserve SIGMA and local JavaScript/WebView as separate execution modes;
- bounded symbol helper with explicit third-party provenance;
- SYSTEM/LIGHT/DARK presentation without semantic effects;
- accessibility typography and settings as UI-only state;
- `DELTA_HYBRID2_TYPED_VIEW_V1`: typed scientific operator lab with hybrid depth `<=2`;
- exact rational finite-matrix checks where implemented;
- symbolic/fail-closed routing for Qp, quaternion, hyperreal and surreal branches until native arithmetic exists;
- no promotion of operator-lab contracts into SIGMA syntax before `SPEC -> GRAMMAR -> SEMANTICS -> LIMITS -> TESTS -> NEGATIVE_TESTS -> COMPATIBILITY -> ANDROID/GLOBAL CONFORMANCE`.

## Later candidates
- optional language adapters kept separate from the deterministic core;
- configurable execution profiles;
- external tooling bridges only behind explicit capability gates;
- native complex arithmetic after a dedicated branch/compatibility audit;
- domain-native p-adic and finite-field modules only if a concrete scientific workload justifies them;
- desktop/CLI companion if justified by use.

No network dependency is required for the local SIGMA runtime.

`OPERATOR_LAB != SIGMA_LANGUAGE_SEMANTICS` and `TEST_PASS != THEOREM_PROOF` remain binding roadmap constraints.
