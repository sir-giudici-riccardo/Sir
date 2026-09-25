# Local JavaScript/WebView backend boundaries — 0.3.0-dev

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
- on timeout/cancel the app attempts to terminate the associated WebView renderer on API 29+ and destroys the WebView.

## Important limits

This is not a proof of universal JavaScript sandboxing.

The JavaScript engine is supplied by the installed Android WebView provider. The app reports the provider package and version after each run rather than hard-coding an engine identity.

The WebView backend is optional. SIGMA Script remains available as the deterministic native interpreter.
