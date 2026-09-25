# Local JavaScript/WebView backend boundaries — 0.4.0-dev

The optional JavaScript mode is separate from SIGMA Script.

## Enforced application configuration

- no Android permissions are declared;
- WebView network loads are blocked;
- file and content access are disabled;
- DOM storage, Web SQL database and geolocation are disabled;
- mixed content is disallowed;
- external navigation is rejected;
- Web contents debugging is disabled;
- no `addJavascriptInterface` bridge is installed;
- each run has a 5 second watchdog;
- on timeout/cancel the app attempts to terminate the associated WebView renderer on API 29+ and destroys the WebView;
- `onRenderProcessGone()` is handled explicitly: renderer crash/kill is classified, the active run fails closed, the dead WebView is cleaned up, and the host process remains eligible to create a new session.

## Important limits

This is not a proof of universal JavaScript sandboxing.

The JavaScript engine is supplied by the installed Android WebView provider. The app reports the provider package and version after each run rather than hard-coding an engine identity.

A successful JVM policy test plus Android compilation does not simulate an actual renderer crash. Real-device renderer-death validation remains a separate gate.

The WebView backend is optional. SIGMA Script remains available as the deterministic native interpreter.
