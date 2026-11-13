# Guardian AdBlock v0.6.3-dev — verification report

Date: 2026-11-13

## Executed in this package workspace
- Python DNS engine + Windows protection unit tests: **18 passed**.
- Python compile check (`python -m compileall -q dns_engine windows_engine`): **passed**.
- Browser extension Node.js regression tests: **11 passed** (`node --test tests/browser_rules.test.cjs`).
- Browser extension JavaScript syntax checks: **passed** (`node --check` for background, popup, and options scripts).
- Pure Kotlin DNS core compile + smoke checks: **18 assertions passed**, including IPv6 UDP checksum validation (`./VERIFY_ANDROID_CORE.sh`).
- Manifest version and JSON validation: **passed**.
- Android manifest XML parse: **passed**.
- Android JUnit test methods present: **16 authored; NOT RUN** (Gradle/Android SDK unavailable).
- Android Activity and VPN service Android-SDK compilation: **NOT RUN**.
- Android APK build: **NOT RUN; no APK included** (Gradle and Android SDK are absent; external Gradle host DNS lookup failed in this workspace).
- Windows live adapter DNS/service acceptance: **NOT RUN** (not a Windows host; no administrator/system integration test).
- Windows EXE/installer: **NOT BUILT**.
- Chrome/Chromium live extension validation: **NOT RUN** (Node.js tests use mocked Chrome APIs).

## Android v0.6.1-dev changes
- Added user-managed block and allow lists in the app UI, stored locally in app-private SharedPreferences.
- Accepts ASCII DNS hostnames only; rejects invalid labels, whitespace, URLs, ports, and wildcards; de-duplicates normalized values; max 1,000 entries per list.
- Exact-domain and subdomain matches are applied. Allow rules override block rules.
- Saving rules while protection is running requests a live rule reload without tearing down the VPN tunnel.
- Protection state and service startup failures are reflected in the app UI through an app-scoped broadcast.
- Rule sets are replaced through a single immutable volatile snapshot so the packet loop does not observe a mixed old/new allow/block state.
- An invalid/empty replacement does not replace the current active in-memory rules.
- Android app version: `0.6.1-dev`, `versionCode=8`.

## Browser extension hardening carried forward
- Custom dynamic rules are capped at 4,000 and allowlist rules at 1,000 to fit the standard 5,000 dynamic-rule quota.
- Filter downloads are limited to 5 MB while streaming (oversized bodies are cancelled before full buffering) and 15 seconds; HTTPS redirects are enforced and embedded URL credentials are rejected.
- Host labels are validated strictly; duplicate allowlist entries are removed.
- Empty/unsupported downloaded lists do not replace the currently active filter rules.
- Disabling the custom filter removes only custom filter rules and leaves allowlist entries intact.
- Only the selected HTTPS origin is requested as optional host access; cross-origin redirects are rejected.

## Windows protection hardening carried forward
- Activation requires a decryptable DPAPI backup before adapter changes.
- Rollback snapshot coverage must match every current IPv4 adapter (interface index + alias).
- The local resolver must return the expected NXDOMAIN probe response before DNS changes.
- IPv6 DNS configuration blocks IPv4-only activation to prevent silent bypass.
- Partial adapter activation failure attempts rollback from the protected backup.

## Shared rule contract
- Canonical file: `shared/filter_rules.txt`.
- Syntax: `block=domain.example`, `allow=domain.example`; comments begin with `#`.
- Exact-domain and subdomain matches are supported.
- Allow rules override block rules.
- Android packages the shared file as an app asset; Python DNS engine loads it when `use_shared_rules` is enabled.
- Android user rules are local additions and do not modify the shared source file.
- Browser declarative rules remain a separate adapter because browser URL matching differs from DNS suffix matching; remote-list parser supports a limited domain-rule subset, not full EasyList semantics.

## Current technical scope / limitations
- Android: IPv4 UDP DNS only, `VpnService`, protected upstream UDP socket to Cloudflare `1.1.1.1`; only virtual DNS `/32` is routed through the tunnel.
- Android user lists can be edited locally, but there is no remote list auto-update, IPv6 DNS, DNS-over-TCP, DoH/DoT filtering, IP fragment reassembly, or per-app exclusion.
- Windows: local DNS engine integration helpers, DPAPI backup/restore, explicit opt-in activation; IPv6 DNS bypass is refused.
- The bundled blocklist is a small development list, not a comprehensive commercial ad-blocking database.
- DNS upstream timeout can result in no response to the requester.

## Release gate
Do not mark v0.6.1 as production-ready until Android Gradle/JUnit + APK + physical-device tests and Windows administrator/service/restore tests pass on target environments. A Windows installer/EXE must be engineered and tested separately; these items are not represented as complete.

## v0.6.2-dev follow-up verification (2026-11-13)

- `./VERIFY_ANDROID_CORE.sh`: PASS — Kotlin DNS core compiles with `kotlinc`; 15 standalone assertions pass, including SERVFAIL construction and flags.
- `pytest -q`: PASS — 18 tests.
- `node --test tests/browser_rules.test.cjs`: PASS — 11 tests.
- `python -m compileall -q dns_engine windows_engine`: PASS.
- Android Gradle/JUnit and APK build: NOT RUN — no Gradle executable or Android SDK/`android.jar` is installed in this workspace. No APK is included or claimed.
- Android device, Windows service/adapter integration, and live DNS behavior: NOT RUN.

### Change under test
When an unblocked DNS query cannot obtain a validated upstream UDP answer, Android now emits a validated DNS SERVFAIL response rather than silently dropping the request. The pure Kotlin core regression tests verify QR/RD/RA/SERVFAIL flags, question preservation, zero answer sections, and malformed-query rejection. This does not add DNS-over-TCP, IPv6, DoH, or DoT support.


## v0.6.3-dev verification (2026-11-13)

- Python DNS engine tests: **6 passed**.
- Windows protection safety tests: **12 passed**.
- Browser extension Node.js regression tests: **11 passed**.
- Pure Kotlin DNS core: **18 assertions passed**, including IPv6 UDP parsing/checksum validation, response construction, and invalid-checksum rejection.
- Python compile, browser JavaScript syntax, JSON and Android manifest XML checks: **PASS**.
- Android Gradle/JUnit, full Android APK build, Android device acceptance: **NOT RUN** (Android SDK/Gradle unavailable).
- Windows adapter/service acceptance and Windows EXE/installer: **NOT RUN / NOT BUILT** (not a Windows host; PyInstaller unavailable).

### v0.6.3-dev implementation
- Added Android virtual IPv6 DNS endpoint `fd13:37::2/128` alongside IPv4.
- Added IPv6 UDP/53 packet validation; only base IPv6 + UDP is accepted. Extension headers and fragments are rejected rather than partially interpreted.
- Mandatory IPv6 UDP checksum is validated on ingress and generated on responses.
- Upstream UDP truncation triggers a protected TCP/53 retry to `1.1.1.1`; response length and DNS transaction/question are validated.
- Client-originated TCP DNS interception remains unsupported. This release is not production-certified.