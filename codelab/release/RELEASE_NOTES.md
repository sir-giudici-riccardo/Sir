# SIGMA Code Lab 0.1.0

First public prerelease of the offline Android coding workspace.

Included:
- local editor and script runner;
- bounded deterministic interpreter;
- numbers, strings and booleans;
- variables and reassignment;
- conditions and bounded loops;
- mathematical and conversion built-ins;
- examples, in-app reference and copyable output;
- private draft autosave;
- zero declared Android permissions;
- JVM regression tests and APK permission/signature audit in CI.

Known limits:
- one draft buffer;
- no lists or user-defined functions yet;
- no import/export yet;
- no general filesystem, network or subprocess APIs;
- the 0.1.0 APK uses a development signing identity. Seamless APK upgrades are not claimed until a persistent update-signing identity is frozen; a later experimental build may require reinstalling the app.

The source under codelab/ is licensed under the MIT License.
