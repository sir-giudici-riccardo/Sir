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

## Later candidates
- optional language adapters kept separate from the deterministic core;
- configurable execution profiles;
- external tooling bridges only behind explicit capability gates;
- desktop/CLI companion if justified by use.

No network dependency is required for the 0.1 runtime.
