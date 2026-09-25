# SIGMA Code Lab 0.1.0

SIGMA Code Lab is a small offline Android coding workspace developed inside the existing SIGMA engineering lineage.

## Current scope

Version 0.1.0 provides:

- a local source editor;
- deterministic local script execution;
- numbers, strings and booleans;
- variables and reassignment;
- arithmetic and comparisons;
- `if / else`, `repeat` and `while`;
- logical operators `and`, `or`, `not`;
- built-in constants `pi` and `e`;
- numeric/string utility functions;
- examples and an in-app reference;
- copyable source and output;
- private-app autosave using Android SharedPreferences;
- explicit Run/Stop controls;
- bounded steps, loop iterations, output and wall time.

The Android manifest declares no permissions. The application contains no network or subprocess API path.

## Language example

```text
let width = 12
let height = 5
let area = width * height

if area >= 50 {
  print "area = " + area
  print "diagonal = " + sqrt(width * width + height * height)
}
```

## Built-ins

`sqrt abs sin cos tan log exp floor ceil round pow min max clamp len str num type`

## Execution boundaries

The interpreter is deliberately bounded. Default app execution limits are:

- 500,000 execution steps;
- 100,000 loop iterations;
- 65,536 output characters;
- 5 seconds wall-time budget;
- explicit user cancellation.

These are containment limits for this interpreter, not a general Android sandbox or security proof.

## Development model

The app will be expanded through use. Planned directions include structured collections, user-defined functions, import/export through Android's document picker, multi-file workspaces, a small standard library, richer diagnostics and reproducible examples.

Features are added only after tests and build/audit gates pass.

## License

The original source under this `codelab/` directory is released under the MIT License in `codelab/LICENSE`.
