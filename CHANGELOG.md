# Changelog

## v0.4.0
- Local UDP/TCP DNS development proxy with block/allow rules and bounded TTL cache.

## v0.4.1-dev — Windows protection foundation
- Added Windows DNS adapter inspection, DPAPI-protected backup, restore and explicit IPv4 loopback configuration commands.
- Added a pywin32 Windows Service host that supervises the DNS engine child process and checks the localhost TCP listener before reporting startup.
- Added a hard safety guard that refuses IPv4 activation when IPv6 DNS servers are configured.
- Added unit tests for safe import behavior, IPv6 bypass refusal, missing IPv4 adapter refusal and port-53 config generation.
- Not a complete Windows system protection release: service hosting, IPv6 listener, lifecycle recovery and real Windows acceptance testing remain outstanding.

## v0.5.0-dev — Android VPN implementation scaffold
- Added native Android Kotlin project using VpnService.
- Added IPv4 UDP DNS packet parsing and synthetic NXDOMAIN responses for embedded blocked domains.
- Added protected upstream DNS forwarding to 1.1.1.1 for non-blocked domains.
- Added foreground notification and start/stop controls.
- Added JVM unit tests for domain matching, DNS parsing, and IPv4 UDP parsing.
- Not production-ready: Android device validation, full common-filter integration, IPv6/TCP DNS, and dynamic lists remain open.

## v0.5.1-dev — Android DNS hardening
- DNS 질의 헤더를 검증해 응답 패킷, 비표준 opcode, 복수 질문을 거부합니다.
- NXDOMAIN 응답은 검증된 단일 질문만 포함하고 RD 비트를 보존합니다.
- IPv4 조각화 패킷과 잘못된 UDP 길이를 거부합니다.
- Android 단위 회귀 테스트를 추가했습니다.

## v0.6.0-dev — Common filter rules integration
- Added canonical cross-platform `shared/filter_rules.txt` rule format.
- Android loads shared block/allow rules from packaged assets.
- Windows system DNS configuration enables the same shared rule source.
- Python DNS engine validates and loads shared rules; allow rules take precedence.
- Added regression tests for shared rule loading and allowlist precedence.

## v0.6.0-dev — verification hardening (2026-11-13)
- Windows activation now requires a decryptable DPAPI backup and a live localhost DNS NXDOMAIN probe before adapter changes.
- Partial Windows adapter activation failures trigger rollback from the protected backup.
- Android shared-rule loading fails closed when no valid block rule exists; VPN startup fails closed if rules or VPN establishment fail.
- Added Windows regression coverage and platform build/verification helper scripts.
- Android/Windows physical integration and installer packaging remain release gates, not claimed as completed.


## 2026-11-13 — Android upstream DNS response validation
- Validate upstream DNS transaction ID, QR/opcode, question count, and exact question bytes before returning a response to the VPN client.
- Reject truncated replies because TCP DNS fallback is not implemented yet.
- Verify the UDP reply originates from the configured upstream resolver endpoint.
- Added Android JUnit regression coverage for mismatched/spoofed response rejection.

### Additional verification hardening
- Added regression coverage for truncated upstream DNS replies, mismatched question fields, and inconsistent IPv4/UDP lengths.
- Added a standalone Kotlin DNS-core compile/smoke-check script (8 assertions); this is supplemental and does not replace Android Gradle/JUnit or device testing.
- Added repository ignore rules and removed transient pytest cache files from the release archive.

### Windows rollback safety hardening
- Before changing adapter DNS, verify that the DPAPI backup includes a matching snapshot for every current IPv4 adapter (interface index and alias).
- Refuse activation before any `netsh` changes when a rollback snapshot is absent or incomplete.
- Added two regression tests for missing IPv4 snapshots and newly present adapters not covered by the backup.

## 2026-11-13 — Browser extension filter hardening
- Reduced custom dynamic filter rules to 4,000 and reserved 1,000 dynamic-rule slots for allowlist entries, keeping the combined total within the standard 5,000-rule quota.
- Added strict hostname-label validation and normalized/deduplicated allowlist entries.
- Added 15-second filter download timeout, 5 MB response-size limit, HTTPS redirect validation, and credential-free HTTPS URL validation.
- Reject an empty/unsupported downloaded filter before replacing active rules; the previous working rules remain active.
- Turning off the custom filter now removes only custom filter rules while preserving user allowlist rules.
- Added five Node.js regression tests for parsing, validation, rule quotas, disabling filters, and safe replacement.
- Updated the browser extension manifest version to 0.6.0.


## 2026-11-13 — Browser filter download memory-safety hardening
- Enforce the 5 MB filter-list cap during streamed reads and cancel the response immediately when exceeded, rather than buffering an oversized body before checking its size.
- Keep options-page allowlist validation consistent with background DNS-label validation.
- Add regression tests for oversized streamed responses and HTTPS-to-HTTP redirects.


## 2026-11-13 — Least-privilege browser filter permissions
- Removed broad install-time `<all_urls>` host access from the Chrome extension.
- Added optional HTTPS host permissions; the options page requests access only to the exact origin selected for the custom filter after the user clicks Update.
- The background worker verifies that permission before downloading; missing permission leaves active rules untouched.
- Reject cross-origin HTTPS redirects so a filter source cannot silently expand the extension's host access.
- Added three Node.js regression tests for optional permission scope, missing permission, and cross-origin redirect rejection.

### Security hardening — 2026-11-13
- Centralized filter URL validation in the background worker.
- Normalize filter URLs and remove fragments before saving.
- Check optional host permission before saving an enabled filter configuration, preventing a denied permission from leaving a broken enabled state.
- Added regression tests for insecure schemes, embedded credentials, and URL normalization.

## v0.6.1-dev — Android user-managed rules and status
- Added on-device custom block and allow lists to the Android app, with hostname validation, duplicate normalization, and a 1,000-entry cap per list.
- Persisted user rules locally and reload them into the active VPN service without restarting the tunnel.
- Added visible protection state and service failure messages, with an app-scoped status broadcast.
- Made rule replacement atomic by swapping one immutable block/allow snapshot; invalid rule replacements keep the previously active rules.
- Added Kotlin core smoke and JUnit regression coverage for custom rule normalization, allow precedence, and failed reload retention.
- Bumped Android app version to 0.6.1-dev (versionCode 8).
- Full Android Gradle/JUnit build, APK packaging, and physical-device validation remain unverified because this workspace has no Android SDK/Gradle installation and cannot resolve the Gradle distribution host.


## v0.6.2-dev — Android upstream failure behavior
- When an unblocked DNS query cannot obtain a valid upstream UDP response, the VPN now returns a validated DNS SERVFAIL response instead of silently dropping the query and leaving apps waiting for a timeout.
- Added a reusable validated DNS error-response builder, preserving opcode, RD and CD while setting QR/RA and clearing answer sections.
- Added JUnit and standalone Kotlin smoke regression coverage for SERVFAIL flags, section counts, and malformed-query rejection.
- Bumped Android app version to 0.6.2-dev (versionCode 9). Android Gradle/JUnit build, APK creation and physical-device verification remain pending.


## v0.6.3-dev — Android IPv6 DNS and upstream TCP fallback (2026-11-13)
- Added a dedicated IPv6 virtual DNS endpoint and /128 route alongside the existing IPv4 DNS endpoint.
- Added strict IPv6 UDP/53 parsing, mandatory UDP checksum validation, extension-header/fragment rejection, and response checksum generation.
- Added upstream DNS-over-TCP retry when a UDP response is truncated (RFC 7766 length framing, bounded connect/read timeout, transaction/question validation).
- Added standalone Kotlin regression checks for IPv6 query parsing, generated responses, and invalid checksum rejection.
- Client-side DNS-over-TCP interception, IPv6 extension-header/fragment reassembly, DoH/DoT, Android APK build, and device validation remain outstanding.

## 2026-11-13 — Android packet-loop resilience and IPv4 length validation
- Coalesced status broadcasts to at most one UI refresh per 500 ms while persisting every DNS event.
- Published a clear inactive state if the VPN packet-processing thread exits unexpectedly.
- Added explicit IPv4 TUN buffer-bound checks and required the IPv4 total-length field to match the complete packet length.
- Added a regression test for oversized caller lengths and trailing packet bytes.
- The IPv4 length hardening is awaiting a CI run on the latest main commit; do not treat it as part of the previously verified APK until that run succeeds.


## 2026-11-13 — Rules tab feedback isolation
- Separated custom-rule validation/save feedback from the dashboard VPN status view.
- Prevented status refresh broadcasts from replacing rule-editor success and validation messages.
- Verified with Android unit-test task and debug APK CI build (run 43421570913).


## v0.6.5-dev — Android lifecycle, DNS validation and release polish (2026-11-13)
- Confirm active protection only when the current app process owns a live VPN tunnel; persisted stale flags no longer make the dashboard claim protection is running after process restart.
- Show a dedicated live protection badge and disable the start action while the tunnel is active.
- Reject DNS wire QNAME labels containing literal dot bytes to prevent ambiguous conversion into dotted hostnames used for domain matching.
- Add regression tests for ambiguous DNS labels and case-insensitive QNAME response matching while requiring exact QTYPE/QCLASS.
- Add Guardian adaptive and legacy launcher icons.
- Bump Android version to 0.6.5-dev (versionCode 12).
- Strengthen CI with VPN manifest/foreground-service declaration checks, launcher asset checks, APK package identity/version validation, SHA-256 and artifact upload.
- Real-device VPN lifecycle and network-transition acceptance remain required before production release.


## v0.6.5-dev — stale protection-state recovery (2026-11-13)
- Extracted a testable protection-state guard: persisted state alone never represents a live VPN session.
- When the dashboard detects a stale active flag after process restart, it now clears the persisted active flag and stores the recovery message.
- User rule saves now request live rule reload only when the current process actually owns an active VPN tunnel; stale sessions save rules for the next start instead.
- Added two regression tests for stale persisted state and live tunnel requirements.


## v0.7.0-dev — AdGuard-style filtering and diagnostics (2026-11-13)
- Identified the previous effective-blocking gap: the built-in baseline contained only ten domains, and no downloaded AdGuard DNS filter or Adblock rule parser existed.
- Added a DNS-compatible Adblock/hosts parser for anchored domain block rules, exception rules, hosts entries, and badfilter handling. Unsupported contextual/rewriting modifiers are ignored rather than guessed.
- Added download, validation, app-private caching, safe cache replacement, and configurable 6h/12h/24h/7-day app-launch updates for selectable official DNS filter profiles.
- Added optional HaGeZi Threat Intelligence malware/phishing list.
- Added upstream DNS choices (Cloudflare, Google, Quad9, AdGuard DNS, AdGuard Family DNS, custom IPv4/IPv6 literals), installed-app VPN exclusions, live DNS activity/diagnostics, and log reset.
- Added source-isolated badfilter composition and regression tests so one filter list cannot cancel another list's matching domain rule.
- Added optional direct DNS capture for common public resolvers. Because Android VPN routes are IP-based rather than port-based, the UI exposes this as an advanced option and warns that non-DNS traffic to those resolver IPs may also be captured.
- Optimized block/allow matching to use bounded DNS-label suffix lookups instead of scanning the full downloaded list for every query.
- Added an in-app end-to-end DNS self-test that checks a blocked domain using the selected DNS capture mode and a normal domain through the virtual DNS endpoint.
- Moved large filter parsing and reload work off the VPN service main thread.
- Changed push CI to run tests only; final APK packaging is gated behind a manual release workflow.
- Physical-device verification and final APK are intentionally pending.


### 0.7.0-dev 추가 변경
- 앱 제외 검색, 업데이트 주기 선택, 커스텀 DNS 라우트, 필터 준비 확인을 추가했습니다.


### 0.7.0-dev — VPN activation and filter readiness hardening
- Serialized the final TUN activation commit against stop/revoke cancellation to close a check-then-activate race.
- Added deterministic regression tests for cancelled activation rejection and activation/stop ordering.
- Require the optional malware/phishing list cache before protection starts when that layer is enabled.
- Commit selected filter-profile metadata synchronously after atomic cache replacement.
- Latest source: 10 test suites / 52 JUnit methods; CI pending; no APK generated.


### 0.7.0-dev — Adblock important modifier priority
- Preserve `$important` domain blocks separately and evaluate them before allow exceptions.
- Keep important-rule behavior isolated by source and allow `$badfilter` to disable matching important blocks within that source.
- Ignore unsupported important exceptions rather than applying incorrect allow semantics.
- Include important rules in downloaded-cache validation and visible block counts.
- Add parser and composed-engine regression tests; latest source CI pending.


### CI fix — `$important,badfilter` modifier parsing
- Fixed detection of `$badfilter` when it appears after another comma-separated modifier.
- Latest Android unit-test CI passed: 59 tests completed, 0 failures (run 43433588312).


### VPN routing safety
- Make direct interception of public DNS resolver IPs opt-in because IP-prefix VPN routes can capture non-DNS traffic to those same addresses.
- Keep the self-test aligned with the safe default and display the advanced-routing side-effect warning.
- Add a regression test for the default configuration.


### Reliability and UI polish — 2026-11-13
- Restore a previously active VPN service after Android reclaims its process (`START_STICKY`), while respecting explicit stop state.
- Keep the dashboard honest during a service restart and offer a reconnect action rather than clearing the persisted restore intent.
- Replace the app-owned virtual-DNS UDP self-test (which bypasses the app's own VPN route on Android) with an accurately labelled in-process rule-engine test.
- Apply consistent dark styling to DNS/profile/update-interval selectors and avoid refreshing detached home views.
- Serialize CI per branch and cancel superseded push runs.
- Latest CI: run 43436954231 passed; 12 suites / 67 JUnit test methods.


## Latest verification update

- Explicit user stop now clears the persisted sticky-service restart flag even if VPN startup has not yet committed a TUN descriptor. Routine service cleanup without a live tunnel does not overwrite the original startup failure reason.
- Added two unit tests for this stop/start race policy.
- [GitHub Actions run 43436954231](https://github.com/justinhblee-prog/guardian/actions/runs/43436954231): **success** (Android unit tests passed; APK assembly intentionally skipped on push).
- Test inventory: 12 suites / 67 test methods.


## 0.7.0-dev — persistent filter refresh

- Add WorkManager 2.12.0 periodic background filter refresh with network constraint and selectable cadence.
- Cancel scheduled refresh when automatic updates are disabled; retry transient failures with exponential backoff.
- Refresh active filter sources and reload an active VPN service after successful updates.
- Serialize cache downloads to avoid manual/background write races.
- Add interval validation tests; inventory grows to 12 suites / 67 test methods.


## Latest WorkManager verification

- [GitHub Actions run 43437792450](https://github.com/justinhblee-prog/guardian/actions/runs/43437792450): **success**
- Source commit: `5d7624fd445edadb1a1ed24a482b9e2d25f07d41`
- Android unit tests: **passed**; APK assembly was skipped because this was a push-triggered validation run.
- Test inventory: **12 suites / 67 test methods**.
