# SIGMA Code Lab Android signing

A persistent Android release signing identity has been created outside GitHub and is not committed to this repository.

## Frozen public certificate identity

- Subject: `CN=SIGMA Code Lab, OU=Public Benefit Software, O=SIGMA, C=IT`
- Validity: 2026-09-25 to 2054-02-10
- SHA-256 certificate fingerprint:
  `5A:A7:93:D3:2E:25:99:DB:52:C6:2A:47:98:5B:7D:1B:F3:02:1D:7B:C2:A2:C5:D3:A9:19:D5:81:85:95:65:86`

## Required GitHub Actions secrets

Configure these repository secrets before any stable/in-place-upgrade release:

- `CODELAB_KEYSTORE_B64`
- `CODELAB_KEYSTORE_PASSWORD`
- `CODELAB_KEY_ALIAS`
- `CODELAB_KEY_PASSWORD`

The keystore itself must never be committed. `CODELAB_KEYSTORE_B64` is the base64 encoding of the PKCS#12 file.

## Release invariant

A stable APK release must fail closed unless:

1. all four secrets are present;
2. the APK verifies with Android `apksigner`;
3. the signer certificate SHA-256 equals the frozen fingerprint above;
4. package name is `org.sigma.codelab`;
5. the APK declares zero permissions unless a future release explicitly changes the capability contract;
6. tests and source/manifest audits pass.

The public 0.1.0 prerelease used an ephemeral development identity and is not part of the stable in-place-update signing lineage.
