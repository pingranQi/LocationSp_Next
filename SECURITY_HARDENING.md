# Security hardening notes

This branch applies defensive changes based on the source review:

- Disable Android Auto Backup for local location, radio-environment, and credential data.
- Protect the exported spoof-control receiver with a signature-level permission.
- Encrypt user-supplied map and cloud-service credentials with an Android Keystore AES-GCM key, migrating legacy plaintext values on first read.
- Remove automatic destructive migration on database upgrades; unsupported upgrades now fail closed instead of silently deleting the database. Downgrades remain destructive.
- Wait for a fresh Wi-Fi scan completion broadcast and return no results when Android throttles/rejects the scan or the request times out.
- Bound Root command execution time and retained diagnostic output; validate package names before using them as shell arguments.
- Keep foreground-service running state false when foreground promotion fails.
- Close HTTP responses and configure bounded timeouts for OpenCellID and WiGLE requests.
- Cap imported JSON packages at 20 MiB.
- Remove unnecessary GitHub Actions write scopes, avoid direct shell interpolation of workflow input, and add the Xposed JVM test task to the debug CI flow.

## Remaining validation

These changes still require a successful CI run and Android device testing. In particular:

- Validate Keystore migration on an upgrade from a release containing plaintext credentials.
- Exercise supported Android versions and OEM ROMs for Wi-Fi scan broadcasts and foreground-service behavior.
- Add historical Room migrations for every schema version that the project intends to support. Unsupported upgrades intentionally fail rather than wipe user data.
- Pin third-party GitHub Actions to verified full commit SHAs before treating the release pipeline as fully hardened.
- Add instrumentation / system-level regression tests for Xposed behavior; JVM tests do not prove system-server compatibility.
- The OpenCellID API currently requires its API key in the query string; this remains an exposure consideration in intermediary URL logs.
- Build-time API keys inserted into APK manifest metadata are extractable from the package. Restrict those keys by package/signing certificate and service/API scope; Keystore encryption protects only credentials entered and stored at runtime.

This note does not certify the application or its distributed APKs as secure.
