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
- Android JUnit: `testDebugUnitTest` passed in GitHub Actions for commit `ccc5beba3ae8658733ca215708a77115158d78f1`.
- Android debug APK: built successfully with Gradle 8.9 / JDK 17 / Android SDK 35; uploaded as the `Guardian-AdBlock-Android-APK-v0.6.3-dev` workflow artifact. The APK SHA-256 is `40593421ba6b8fdf62fd41225a4c8055266da89bd5b17f56eb416085afb85178`.
- APK archive integrity and checksum: verified after download.
- Android physical-device installation, VPN startup, and real-world ad-blocking acceptance: not yet verified.
- Windows live adapter/service acceptance: not executed on a Windows host.
- Windows EXE/installer: not built or included.

Android client-side TCP DNS interception is not implemented; only upstream TCP fallback for truncated UDP answers is implemented. IPv6 extension headers and fragment reassembly are not implemented. DNS-over-HTTPS (DoH) and DNS-over-TLS (DoT) are not filtered. This is a development build, not a production-ready or comprehensive ad-blocking product. Read `VERIFICATION.md` and `NEXT_STAGE_GUIDE_KO.md` before enabling system protection.

## Android APK installation

1. Download the APK artifact from the successful [GitHub Actions run](https://github.com/justinhblee-prog/guardian/actions/runs/43419311161).
2. On the Android device, open the downloaded APK and allow installation from the source app if Android requests it.
3. Launch **Guardian AdBlock**, tap **보호 시작**, and approve Android's VPN permission prompt.
4. Confirm the app shows the active protection state. Android permits one active VPN connection per user; stop any other VPN app first if connection setup fails.

The CI build proves compilation, unit tests, and APK packaging only. It does not prove that VPN protection starts successfully on every device or that all in-app advertisements are blocked.


### Browser filter download safety
Custom filter downloads are read with a 5 MB streaming cap and a 15-second timeout. Oversized response streams are cancelled before the complete body is buffered. The extension accepts only HTTPS filter URLs and rejects redirects that end on non-HTTPS URLs. The supported filter syntax is intentionally a limited domain-rule subset, not full EasyList compatibility.


### Browser extension permissions
The extension no longer requests access to every website during installation. When you configure a remote filter, Chrome asks for optional access to that filter's HTTPS origin only. If you deny access, the download is not attempted and the currently active rules remain unchanged.

### Latest verified Android APK — 2026-11-13

The latest Android CI run is [43420229693](https://github.com/justinhblee-prog/guardian/actions/runs/43420229693), built from commit `d6191812b51cf97a288186901435608280bab1f8`. The configured Android unit-test task and debug APK build passed, the APK checksum step passed, and the artifact was uploaded successfully (artifact ID `14034125411`).

- APK size: 845,986 bytes
- SHA-256: `c17a91a518e39e9ec9681c8cb90d645f589ebbbb0276152299299d5c321bfff9`

This build includes IPv4 TUN packet-length hardening and regression tests. Physical-device VPN behavior and real-world ad blocking remain unverified.


### Latest Android APK (IPv6 regression build)

- CI: [43420975704](https://github.com/justinhblee-prog/guardian/actions/runs/43420975704)
- Source: `5b93b93ec36b86c2312c09b95562e396ec6dd890`
- `clean testDebugUnitTest assembleDebug`: successful
- Artifact ID: `14034191808`
- APK size: 846,262 bytes
- APK SHA-256: `9d8ba26f6c9cadad5250e15d3d46456262476b4d5aeb8ac27f023e5eea2fb92b`

The artifact ZIP was integrity-checked and the extracted APK checksum matched the workflow's checksum file. This is a CI-verified development APK; physical-device VPN behavior and real-world ad blocking remain unverified.
