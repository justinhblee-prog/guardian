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

## Android CI verification update — 2026-11-13

Latest confirmed successful CI run before the IPv4 packet-length hardening:
- Workflow run: [43419311161](https://github.com/justinhblee-prog/guardian/actions/runs/43419311161)
- Tested commit: `ccc5beba3ae8658733ca215708a77115158d78f1`
- Gradle: `testDebugUnitTest assembleDebug` — **BUILD SUCCESSFUL**
- APK packaging and artifact upload — **PASS**
- APK SHA-256: `40593421ba6b8fdf62fd41225a4c8055266da89bd5b17f56eb416085afb85178`
- The JUnit task completed successfully; this report does not infer a test count from the task summary.

### Current Android implementation scope
- IPv4 UDP/53 and IPv6 UDP/53 DNS packet parsing through Android `VpnService`.
- IPv6 UDP checksum validation and response checksum generation.
- DNS filtering with shared and user-managed block/allow rules; allow rules take precedence.
- Validated upstream UDP response matching and TCP/53 retry when the upstream UDP reply has TC set.
- SERVFAIL response for a valid unblocked query when upstream resolution fails.
- DNS counters and recent activity persisted locally; UI broadcasts are coalesced.
- IPv4 fragmentation, IPv6 extension headers/fragments, and client-originated TCP DNS interception are unsupported.
- DoH/DoT traffic is not filtered; the bundled blocklist is a small development baseline.

### Outstanding acceptance gates
- The IPv4 TUN packet-length consistency hardening added after commit `ccc5beba` requires its own CI run before it is included in a verified APK.
- Android physical-device installation, VPN permission/start/stop, IPv4/IPv6 DNS behavior, network switching, and real-world blocking remain **NOT VERIFIED**.
- Windows live adapter/service acceptance and Windows installer remain **NOT VERIFIED / NOT BUILT**.

The successful CI build proves the source at the recorded commit compiled, its configured JUnit task passed, and an APK was packaged. It does not prove runtime behavior on a physical Android device or comprehensive ad blocking.


## Latest Android CI verification — 2026-11-13 (supersedes pending gate above)

- Workflow: [43420229693](https://github.com/justinhblee-prog/guardian/actions/runs/43420229693)
- Source commit: `d6191812b51cf97a288186901435608280bab1f8`
- `testDebugUnitTest assembleDebug`: **PASS**
- APK checksum verification step: **PASS**
- Artifact upload: **PASS**, artifact ID `14034125411`
- Artifact ZIP size: 839,169 bytes; ZIP integrity test: **PASS**
- APK size: 845,986 bytes
- APK SHA-256: `c17a91a518e39e9ec9681c8cb90d645f589ebbbb0276152299299d5c321bfff9`

The IPv4 length hardening and its regression tests are now included in a successful CI-built APK. The test covers caller length beyond the backing buffer, trailing bytes beyond the IPv4 total length, and a valid packet with IPv4 options.

Physical-device acceptance and Windows validation remain outstanding.


## Latest Android build — 2026-11-13 (IPv6 regression included)

- Workflow: [43420975704](https://github.com/justinhblee-prog/guardian/actions/runs/43420975704)
- Source commit: `5b93b93ec36b86c2312c09b95562e396ec6dd890`
- Gradle command: `clean testDebugUnitTest assembleDebug` — **BUILD SUCCESSFUL**
- APK checksum step and artifact upload — **PASS**
- Artifact ID: `14034191808`
- Artifact ZIP SHA-256: `d691be00fe2d716df9351b5f7183c2cc968533a38d9059dc2977b08e6c7e7c10`
- APK size: 846,262 bytes
- APK SHA-256: `9d8ba26f6c9cadad5250e15d3d46456262476b4d5aeb8ac27f023e5eea2fb92b`
- Downloaded artifact ZIP integrity: **PASS**
- Extracted APK SHA-256 matched the workflow's `SHA256SUMS.txt`: **PASS**

This build includes IPv4 packet framing hardening, case-insensitive DNS question matching, and IPv6 UDP checksum/packet validation regression coverage. The first IPv6 test run exposed a test-fixture bug (the checksum field was not zeroed before recomputation); that fixture was corrected and the subsequent complete CI run passed.

Physical-device acceptance and Windows validation remain outstanding.
