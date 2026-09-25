# CircleCI acceptance gate

A CircleCI run may be promoted from `REMOTE_RUN_PENDING` only when all are observed from the same pipeline run:

- `PureJvmTests: PASS`;
- manifest audit `PASS`;
- source audit `PASS`;
- Gradle `:app:assembleDebug` exit code 0;
- `app-debug.apk` exists;
- APK SHA-256 recorded;
- APK permissions audit contains no `uses-permission` declaration;
- artifact download is the exact APK whose hash appears in the receipt.

Then classify:

`APK_BUILT_IN_CIRCLECI / BUILD_EVIDENCE_ONLY / NOT_YET_EXECUTED_ON_TARGET_DEVICE`.

Do not classify `MEASURED_ON_DEVICE` until that exact APK is installed and the read-only probe is run on device `24115RA8EG`.
