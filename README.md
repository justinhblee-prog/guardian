# Guardian AdBlock v0.6.3-dev

Cross-platform ad/tracker domain filtering development package: browser extension, shared domain rules, Python DNS engine, guarded Windows DNS integration, and Android `VpnService` app. Android handles IPv4/IPv6 UDP DNS; truncated upstream UDP replies retry over TCP.

## Quick verification

```bash
python -m pip install -r requirements-dev.txt
./RUN_TESTS.sh
```

`RUN_TESTS.sh` runs Python tests, browser extension Node.js tests (when Node.js is installed), and the pure Kotlin DNS-core smoke check (when Kotlin/JDK are installed).

On Windows PowerShell, `VERIFY_WINDOWS_TESTS.ps1` runs the same checks and does **not** change adapter DNS settings.

## Shared filter rules

Canonical file: `shared/filter_rules.txt`

```text
block=ads.example
allow=trusted.ads.example
```

Rules match exact domains and subdomains. `allow=` takes precedence over `block=`. The Python DNS engine and Android app consume this shared file; browser declarative rules remain a separate adapter.

## Components

- `dns_engine/`: local Python UDP/TCP DNS proxy. Default development listener is `127.0.0.1:5353`.
- `windows_engine/`: Windows DPAPI backup/restore, explicit DNS activation guard, and pywin32 service host.
- `android/`: Kotlin Android VPN DNS filtering app with on-device custom block/allow lists and live rule reload; requires Android Studio/SDK and JDK 17+ to build.
- Browser extension: Manifest V3 static and dynamic rule support. The custom filter adapter accepts a safe subset of domain rules (full EasyList syntax is not supported), caps custom rules at 4,000 while reserving 1,000 allowlist slots, limits downloads to 5 MB/15 seconds, requests optional access only to the selected HTTPS filter host, rejects cross-origin redirects, and preserves previous active rules if a downloaded list has no supported rules.

## Platform status

- Python automated tests: 18 passing in the packaged workspace.
- Browser extension parser/limit/state/permission regression tests: 11 passing with Node.js.
- Android JUnit: 16 tests authored; not executed in this build environment.
- Pure Kotlin DNS core compile/smoke check: 18 assertions passed, including IPv6 UDP checksum and response checks, with `./VERIFY_ANDROID_CORE.sh`.
- Android APK: not built or included.
- Windows live adapter/service acceptance: not executed on a Windows host.
- Windows EXE/installer: not built or included.

Android client-side TCP DNS interception is not implemented; only upstream TCP fallback for truncated UDP answers is implemented. IPv6 extension headers and fragment reassembly are not implemented. This is a development release, not a production-ready or comprehensive ad-blocking product. Read `VERIFICATION.md` and `NEXT_STAGE_GUIDE_KO.md` before enabling system protection.


### Browser filter download safety
Custom filter downloads are read with a 5 MB streaming cap and a 15-second timeout. Oversized response streams are cancelled before the complete body is buffered. The extension accepts only HTTPS filter URLs and rejects redirects that end on non-HTTPS URLs. The supported filter syntax is intentionally a limited domain-rule subset, not full EasyList compatibility.


### Browser extension permissions
The extension no longer requests access to every website during installation. When you configure a remote filter, Chrome asks for optional access to that filter's HTTPS origin only. If you deny access, the download is not attempted and the currently active rules remain unchanged.