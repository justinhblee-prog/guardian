## 2026-11-24 — 수동 보안 프로필에서도 종량제 자동 갱신 차단

- 자동 네트워크 프로필은 metered 링크에서 자동 갱신을 비활성화하지만, 수동 프로필은 고정 허용값을 사용해 WorkManager `CONNECTED` 조건만으로 종량제 모바일망에서 갱신할 수 있었습니다.
- 주기 Worker constraint를 `UNMETERED`로 강화하고, Worker 시작 시 실제 active NetworkCapabilities의 `NET_CAPABILITY_NOT_METERED`도 확인합니다. 사용자 전역 설정/프로필 허용값과 세 조건이 모두 true일 때만 실행합니다.
- Worker는 각 필터 원천 처리 경계에서도 `isStopped` 및 네트워크/프로필 gate를 재확인해 프로필 전환이나 constraint 취소 이후 다음 필터 다운로드를 시작하지 않도록 했습니다. 첫 실행 수동 필터 준비 경로에는 영향을 주지 않습니다.
- 사용자/프로필/네트워크 3중 게이트 정책 테스트를 추가했습니다. Android WorkManager/ConnectivityManager 및 기기 네트워크 전환은 미실행입니다.

## 2026-11-24 — 보안 프로필 미리보기 제한 안내 정합성

- 보안 프로필 UI에 로밍 감지와 반복 필터 갱신 스케줄러가 아직 미연결이라는 오래된 안내가 남아 있어 현재 구현과 일치하도록 수정했습니다.
- 실제 남은 제한을 Android 9+ 셀룰러 capability 기반 판정의 OEM/통신사 실기기 검증, first-run filter population과 recurring WorkManager gate의 구분, PRO entitlement/Release 등록 대기로 명확히 표시합니다.

## 2026-11-24 — 셀룰러 로밍 감지 기반 여행 프로필 자동 적용

- SecurityProfileMonitor가 `roaming=false`를 고정하던 부분을 Android 9(API 28)+ `NET_CAPABILITY_NOT_ROAMING` capability로 대체했습니다.
- 셀룰러에서 API capability가 있고 NOT_ROAMING도 있으면 비로밍으로 판단합니다. capability가 없거나 셀룰러가 아닌 경우는 별도 규칙으로 처리하며, API 26~27에서는 capability 신호가 없으므로 로밍 판정을 하지 않습니다.
- Android 9+에서 셀룰러 capability가 확인되지 않으면 여행 프로필을 선택하는 보수 정책 helper와 네 가지 경계 테스트를 추가했습니다. 실제 통신사·OEM capability 전달은 에뮬레이터/실기기 검증이 필요합니다.

## 2026-11-24 — 자동 필터 갱신 허용 정책 순수 함수 분리

- WorkManager 의존성을 가진 scheduler 객체에서 사용자 opt-in과 프로필 허용값의 AND gate를 순수 `FilterUpdateSchedulingPolicy`로 분리했습니다. 실제 예약/worker는 동일 helper의 정책 결과를 사용합니다.
- 기존 정책 테스트를 Android WorkManager 구현 대신 순수 helper에 직접 연결해 독립 Kotlin/JVM 실행이 가능하도록 했습니다.

## 2026-11-24 — 보안 프로필 기반 자동 필터 갱신 스케줄러 연결

- 종량제 네트워크에서 프로필이 `automaticFilterUpdates=false`를 저장했지만 WorkManager 예약/실행에는 반영되지 않던 미연결 경로를 보완했습니다.
- 주기 갱신 예약은 사용자 전역 자동 업데이트 설정과 현재 보안 프로필의 허용 값이 모두 true일 때만 생성합니다. 둘 중 하나라도 false이면 unique periodic work를 취소하며, 이미 실행 대기 중인 worker도 시작 직전에 같은 게이트를 다시 검사합니다.
- 프로필 전환으로 허용 값이 바뀔 때에만 scheduler를 재동기화해, 동일 상태의 NetworkCallback이 매번 주기 작업의 최초 지연 시간을 초기화하지 않도록 했습니다.
- 첫 실행에 필요한 기본/활성 옵션 필터 다운로드는 `FilterStartupPolicy` 경로를 유지해 자동 업데이트 제한과 분리합니다. 기본 보안 프로필의 허용 기본값도 STRICT 프로필 정의와 일치시켰습니다.
- 두 조건의 4가지 조합과 초기 필수 필터 다운로드 비차단을 확인하는 정책 테스트를 추가했습니다. WorkManager/Android Gradle/JUnit·단말 실행은 미실행입니다.

## 2026-11-24 — 보안 프로필 수동 적용 UI 추가

- DEBUG 개발 미리보기에 집·업무·공용 Wi-Fi·모바일 데이터·여행·엄격 보호 6개 프로필을 직접 선택하는 Spinner와 적용 버튼을 추가했습니다.
- 수동 적용은 자동 전환 옵션을 저장 단계에서 끄고 NetworkCallback을 해제한 다음 선택한 필터/보호 정책을 반영합니다. VPN이 이미 동작 중이면 기존 규칙 reload 동작을 사용합니다.
- 6개 프로필 라벨/고유성을 확인하는 정책 테스트를 추가했습니다. Release 빌드에서는 기존대로 자동 전환 UI/동작을 활성화하지 않고 PRO 권한 게이트도 열지 않습니다.
- Android Gradle/JUnit·에뮬레이터·실기기 검증은 미실행입니다.

# Changelog

## 2026-11-24 — VPN 하위 물리 네트워크 capability 판별 보강
- Guardian VPN 자체가 기본 네트워크로 보고되는 경우 TRANSPORT_VPN capability에만 의존하지 않고, ConnectivityManager.allNetworks에서 VPN이 아닌 물리 네트워크를 찾아 Wi-Fi/모바일/종량제 상태를 판별합니다.
- 물리 네트워크를 찾지 못하면 기존 보고 capability를 보수적으로 사용해 미확인 네트워크를 엄격 보호로 처리합니다.
- Android API Stub 기반 Kotlin 컴파일 smoke 통과. 실제 VPN 중 OEM별 allNetworks 노출 및 NetworkCallback 동작은 에뮬레이터/실기기 검증이 필요합니다.

## 2026-11-24 — 보안 프로필 네트워크 자동 전환 엔진 연결
- Android ConnectivityManager 기본 네트워크 callback을 연결해 Wi-Fi/모바일/이더넷, 인터넷 검증, captive portal, 종량제 여부에 따라 보안 프로필을 자동 선택합니다.
- 선택된 프로필은 실제 FilterListManager 설정에 반영하고, 보호 중이면 DNS 규칙 재로드를 요청합니다.
- SSID/BSSID 및 네트워크 식별자는 저장하지 않습니다. 알 수 없는 Wi-Fi는 공용 Wi-Fi, captive/unvalidated/기타 네트워크는 엄격 보호로 처리합니다.
- 종량제 링크에서는 자동 필터 다운로드 허용 정책을 false로 저장합니다. 다만 자동 다운로드 스케줄러 연동은 미완료입니다.
- DEBUG 빌드에서만 자동 전환을 활성화하고, Premium 개발 미리보기에 사용자가 켜고 끌 수 있는 토글을 추가했습니다. Release는 entitlement/기기 검증 전까지 비활성입니다.
- 독립 Kotlin/JVM smoke 통과: captive/unvalidated, public Wi-Fi, cellular metered, roaming, unknown transport 분기. Android Gradle/JUnit, emulator/실기기 NetworkCallback 검증은 미완료입니다.

## 2026-11-23 — Windows 조용한 DNS 실행 및 반응형 설정 탐색
- Windows DNS 어댑터 조회·변경에 사용하는 PowerShell 실행에 CREATE_NO_WINDOW 및 STARTF_USESHOWWINDOW를 적용해 보호 시작/중지 중 콘솔 창이 표시되지 않도록 수정했습니다.
- Windows 설정 카테고리는 항목 선택만으로 자동 축소되지 않으며, 사용자가 접기 버튼을 누를 때만 아이콘 레일로 전환됩니다.
- Android 휴대폰 설정은 좌우 분할/아이콘 레일을 제거하고, 전체 폭 카테고리 목록과 전체 폭 상세 화면을 순서대로 표시하도록 변경했습니다. 태블릿은 분할 레이아웃을 유지하되 카테고리 선택만으로 자동 축소하지 않습니다.
- Windows 기본 창 크기를 최대 1200×760으로 조정하고 화면 크기에 맞춰 축소되도록 변경했습니다.
- Windows PowerShell 숨김 실행과 설정 메뉴 유지 동작에 회귀 테스트를 추가했습니다. CI/에뮬레이터 검증 전에는 실행 확인으로 간주하지 않습니다.

## 2026-11-23 — 개발 버전 기준값 단일화 및 회귀 방지
- Android VPN E2E가 이전 개발 APK의 `앱 제외` 메뉴를 현재 `앱 제외 관리`와 구분하도록 접근성 트리 기반 메뉴 별칭 해석과 회귀 테스트를 추가했습니다.
- 이번 수정 전 런타임 검증은 VPN 시작 전 메뉴 탐색에서 중단됐으며, VPN 차단 동작의 통과 근거로 사용하지 않습니다.
- Android 개발 버전 계산기의 기본 기준을 마지막 개발 릴리스 `0.7.48-dev / versionCode 61`로 맞췄습니다.
- CI workflow에서 별도 버전 기준값을 중복 전달하지 않고, 계산기의 단일 기준값을 사용하도록 변경했습니다.
- 회귀 테스트가 계산기 기준에서 산출되는 다음 버전(`0.7.49 / versionCode 62`)과 Android Gradle fallback의 일치를 검사하도록 추가했습니다.
- 이 변경은 버전 기준값의 일관성을 검사하는 자동화이며, APK 빌드·실기기 설치·업데이트 검증 완료를 의미하지 않습니다.

## v0.7.10-dev — DNS fallback and upgrade verification (2026-11-16)
- Retry the next configured DNS resolver when an upstream returns a matching SERVFAIL or REFUSED response instead of treating that error packet as a usable answer.
- Require a successful A-record resolution before installing DNS-only VPN routes; the connectivity diagnostic now uses the same success criteria.
- Add regression coverage for retryable SERVFAIL/REFUSED versus a valid NXDOMAIN answer.
- Bump Android versionCode to 23 and versionName to 0.7.10-dev; CI verifies an in-place signed upgrade from v0.7.9-dev.
- CI run #651: unit tests and lint passed; emulator install/upgrade/launch verification is in progress at this changelog update.

## 2026-11-15 — Android integration
- Merge verified Android license device-release and wide-tablet dashboard changes (PR #54/#55).
- Follow-up license mutation serialization and server-revocation handling is under CI in PR #56.


## v0.7.4-dev — Filter cache rollback safety (2026-11-14)
- Replace downloaded filter caches with a backup-preserving swap; if metadata persistence fails, restore the previous known-good cache.
- Recover a cache interrupted between the backup and replacement moves before cache reads and cache-validity checks.
- Add JVM regression tests for successful replacement, metadata failure rollback, and interrupted-swap recovery.
- CI verification is running for this change; physical-device behavior remains unverified.

## v0.7.3-dev — Android protection reliability candidate (2026-11-14)
- Prevent optional security and Android-ad filter switches from changing while their cache snapshot is being downloaded and validated.
- Reapply the locked state when the Protection screen is recreated during an active filter update, and clear stale view references when switching tabs.
- Keep filter update feedback synchronized across the Protection and Filters screens.
- Automatically disable optional lists only when their first download fails and no last-known-good cache exists; preserve existing valid caches on update failure.
- CI release candidate increments Android versionCode to 16 and versionName to 0.7.3-dev.
- Limitations remain: no autonomous DNS-over-TCP retransmission timer/partial-ACK recovery, no IPv6 extension-header/IP-fragment reassembly, and no real-device acceptance evidence yet.


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


## v0.7.1-dev — AdGuard-style filtering and diagnostics (2026-11-13)
- Identified the previous effective-blocking gap: the built-in baseline contained only ten domains, and no downloaded AdGuard DNS filter or Adblock rule parser existed.
- Added a DNS-compatible Adblock/hosts parser for anchored domain block rules, exception rules, hosts entries, and badfilter handling. Unsupported contextual/rewriting modifiers are ignored rather than guessed.
- Added download, validation, app-private caching, safe cache replacement, and configurable 6h/12h/24h/7-day app-launch updates for selectable official DNS filter profiles.
- Added optional HaGeZi Threat Intelligence malware/phishing list.
- Added upstream DNS choices (Cloudflare, Google, Quad9, AdGuard DNS, AdGuard Family DNS, custom IPv4/IPv6 literals), installed-app VPN exclusions, live DNS activity/diagnostics, and log reset.
- Added source-isolated badfilter composition and regression tests so one filter list cannot cancel another list's matching domain rule.
- Added optional direct DNS capture for common public resolvers. Because Android VPN routes are IP-based rather than port-based, the UI exposes this as an advanced option and warns that non-DNS traffic to those resolver IPs may also be captured.
- Optimized block/allow matching to use bounded DNS-label suffix lookups instead of scanning the full downloaded list for every query.
- Added an in-process DNS rule-engine self-test for active block matching, NXDOMAIN response generation, and normal-domain allow behavior; it does not claim to validate the VPN network path.
- Moved large filter parsing and reload work off the VPN service main thread.
- Changed push CI to run tests only; final APK packaging is gated behind a manual release workflow.
- Physical-device verification and final APK are intentionally pending.


### 0.7.1-dev 추가 변경
- 앱 제외 검색, 업데이트 주기 선택, 커스텀 DNS 라우트, 필터 준비 확인을 추가했습니다.


### 0.7.1-dev — VPN activation and filter readiness hardening
- Serialized the final TUN activation commit against stop/revoke cancellation to close a check-then-activate race.
- Added deterministic regression tests for cancelled activation rejection and activation/stop ordering.
- Require the optional malware/phishing list cache before protection starts when that layer is enabled.
- Commit selected filter-profile metadata synchronously after atomic cache replacement.
- Latest source: 10 test suites / 52 JUnit methods; CI pending; no APK generated.


### 0.7.1-dev — Adblock important modifier priority
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
- Latest CI: run 43436954231 passed; 12 suites / 68 JUnit test methods.


## Latest verification update

- Explicit user stop now clears the persisted sticky-service restart flag even if VPN startup has not yet committed a TUN descriptor. Routine service cleanup without a live tunnel does not overwrite the original startup failure reason.
- Added two unit tests for this stop/start race policy.
- [GitHub Actions run 43436954231](https://github.com/justinhblee-prog/guardian/actions/runs/43436954231): **success** (Android unit tests passed; APK assembly intentionally skipped on push).
- Test inventory: 12 suites / 68 test methods.


## 0.7.1-dev — background filter refresh and lifecycle reliability

- Add WorkManager 2.12.0 periodic background filter refresh with network constraint and selectable cadence.
- Cancel scheduled refresh when automatic updates are disabled; retry transient failures with exponential backoff.
- Refresh active filter sources and reload an active VPN service after successful updates.
- Serialize cache downloads to avoid manual/background write races.
- Add interval validation tests; inventory grows to 12 suites / 68 test methods.


## Latest WorkManager verification

- [GitHub Actions run 43437792450](https://github.com/justinhblee-prog/guardian/actions/runs/43437792450): **success**
- Source commit: `5d7624fd445edadb1a1ed24a482b9e2d25f07d41`
- Android unit tests: **passed**; APK assembly was skipped because this was a push-triggered validation run.
- Test inventory: **12 suites / 68 test methods**.


## IPv6 DNS upstream presets
- Add IPv6 presets for Cloudflare, Google, Quad9 and AdGuard DNS (default and family protection).
- Include AdGuard default IPv6 resolver addresses in optional direct-DNS capture routes.
- Add regression coverage for the official IPv6 resolver literals.


### IPv6 DNS presets — CI verified
- Added IPv6 upstream presets for Cloudflare, Google, Quad9 and AdGuard DNS default/family protection.
- Added AdGuard default IPv6 addresses to optional direct-DNS capture routes and IPv6 validation regression coverage.
- [CI run 43443209122](https://github.com/justinhblee-prog/guardian/actions/runs/43443209122) passed on `f518711e559bfb8873128f82c07a1a157a5f2068`; 12 suites / 68 methods.
- APK build intentionally skipped on push.


## 2026-11-13 — Continuous Android build verification
- Every push now runs Android unit tests and assembles the debug APK, then verifies package ID, versionCode/versionName, file presence, and SHA-256.
- Push builds remain internal CI checks; only a manual release dispatch uploads the installable APK artifact.


## 2026-11-13 — Verified Android APK artifact
- Every push now runs Android unit tests, assembles the debug APK, and checks package/version identity and SHA-256.
- Published the installable `0.7.1-dev` debug APK artifact from successful run 43445545156.
- 12 Kotlin test suites / 70 JUnit methods passed.
- Physical-device acceptance remains outstanding; this artifact is a debug build, not a Play Store production-signed release.


## 0.7.2-dev — Safe UDP DNS response sizing
- Cap DNS UDP replies to 1232 bytes to respect the IPv6 minimum-MTU safe DNS payload.
- When upstream replies exceed the cap, build a standards-shaped TC response retaining the validated DNS question instead of writing an oversized IP packet to TUN.
- Add regression tests for TC/QR/RA/RD/CD flags, question preservation, and malformed query rejection.
- Client-side TCP/53 retry remains unsupported and is documented as a limitation.


## 2026-11-13 — 0.7.2-dev verified APK
- VersionCode 15 / versionName 0.7.2-dev.
- 12 Kotlin test suites / 72 JUnit tests passed.
- Debug APK assembled, package identity and SHA-256 verified, and installable artifact uploaded.
- Final CI run: https://github.com/justinhblee-prog/guardian/actions/runs/43447121533
- APK SHA-256: `a1d41cf8937b2c09f7c4ca8d0b02ddf252e95f935b1cee3ed1d485c5470f55d8`


## Unreleased — Android DNS-over-TCP/53

- Add a defensive IPv4/IPv6 TCP packet codec for DNS traffic to the virtual resolver.
- Integrate a bounded TCP session manager into the VPN packet loop.
- Add DNS-over-TCP length framing, ordered partial-stream reassembly, multi-query parsing, and segmented DNS replies.
- Apply the existing domain block/allow engine and configured upstream resolver to TCP DNS requests.
- Correct server sequence advancement after SYN and handle payload+FIN segments.
- Add regression tests for IPv4/IPv6 TCP checksums, handshake sequence numbers, DNS framing, partial-frame reassembly, and FIN cleanup.
- CI run 43452443314 passed: 16 suites / 99 JUnit tests; debug APK assembly, package identity and SHA-256 checks passed.
- TCP retransmission/loss recovery, IPv6 extension headers, IP fragmentation, DoH/DoT and physical-device validation remain outstanding.

- Improve the Protection tab with live start/stop control, quick filter switches, and navigation to filter/app exclusion settings.

- Add Android `lintDebug` to CI and fix the app-private broadcast receiver registration flag.

- Clarify that the DNS self-test validates local rules/NXDOMAIN generation only, not live resolver routing or in-app ads.

- Add selectable UDP/TCP/DoT upstream transport, provider TLS host defaults, custom TLS hostname validation, SNI and certificate hostname verification.

- Add DNS-over-HTTPS upstream transport using TLS/443 and HTTP POST binary DNS messages; parse bounded Content-Length and chunked responses, reject redirects and invalid media types.

- Fix DoH TLS-host settings visibility when reopening the settings screen with DoH already selected.

- Add an end-to-end IPv6 TCP DNS session regression test.


## 0.7.2-dev — CI verification refresh (2026-11-13)
- Re-ran Android unit tests and lint on source commit `827198814d0b4441afe72c2ddb81b6d1a41a8f19`.
- Debug APK assembly, package identity/version check, and SHA-256 verification passed in GitHub Actions run 43525073201.
- CI-built APK SHA-256: `c40229d5c3a25173092233b2e23ace19d9c3d6f831837d6d6684c20f156495ee`.
- Push workflow intentionally skipped manual-release artifact upload; this is not a new installable APK release.


## v0.7.2-dev — TCP DNS 세션 안정성 및 진단 보완 (2026-11-14)
- 이미 연결된 DNS TCP 세션에 지연 재전송된 동일 SYN이 도착해도 기존 sequence state를 보존하고 동일 SYNACK으로 응답합니다.
- 지연 SYN 이후 새로운 DNS TCP 질의가 계속 처리되는 회귀 테스트를 추가했습니다.
- 모순된 TCP 제어 플래그(SYN+FIN/RST, RST+FIN/PSH)를 거부합니다.
- Android 비공개 DNS 자동/호스트명 모드를 홈·보호 화면에서 진단해, 암호화 DNS 우회 가능성을 사용자에게 안내합니다.
- GitHub Actions run 43530335902: Android unit tests, lint, debug APK assembly, package/version and SHA-256 checks all succeeded.
- Test inventory: 17 suites / 104 JUnit test methods.
- CI artifact upload was skipped by push policy; this is not a final APK release. Device-level VPN and ad-blocking acceptance remains outstanding.


## 2026-11-14 — Filter download redirect security
- Replaced automatic HTTPS connection redirects with a bounded manual redirect policy.
- Reject HTTP downgrade redirects, embedded URL credentials, missing or malformed locations, unsupported 3xx responses, and redirect chains exceeding five hops.
- Added six unit tests for relative/absolute HTTPS redirects and rejection cases.
- GitHub Actions run 43531095663: 18 test suites / 110 test methods passed; lint, debug APK assembly, package identity and checksum checks succeeded.
- APK artifact upload remains disabled for push-triggered CI; this is build verification, not a final APK release.


## 2026-11-14 — Android version display and build configuration
- Settings now displays the app's actual Gradle `versionName` through `BuildConfig.VERSION_NAME` instead of a hard-coded version string.
- Explicitly enabled Android `BuildConfig` generation in the app module.
- Verified by GitHub Actions run 43531690119: Android unit tests, lintDebug, assembleDebug, package/version identity, and APK SHA-256 checks all passed for commit `1616c8fdd11c92c1c982c3b24c4661242433d366`.


## 2026-11-14 — DNS-over-TCP FIN retransmission reliability
- Keep a half-closed DNS-over-TCP session until the client acknowledges the server FIN, allowing a lost final response/FIN to be replayed on an exact retransmission.
- Replay cached server packets for retransmitted final client data/FIN segments, avoiding duplicate DNS resolution.
- Added regression tests for data+FIN and pure FIN retransmission paths.
- Verified by GitHub Actions run 43533452981: `testDebugUnitTest`, `lintDebug`, `assembleDebug`, package/version checks, and APK SHA-256 generation passed. APK SHA-256: `b7a35a98f76ce20aa272358c9b0d2f5584a8ec7dd6f88308427840fbe7613b89`.
- Push workflow skipped artifact upload; no device-level ad-blocking claim is made.


## 2026-11-14 — Truthful protection status diagnostics
- Protection screen detail now reflects observed DNS request, block, forward, and failure counters instead of claiming DNS is being filtered solely because the VPN tunnel is active.
- Zero observed DNS requests are clearly reported; opportunistic/hostname Android Private DNS modes warn about possible encrypted DNS bypass.
- Added five unit tests for diagnostic state combinations. CI validation pending.


## 2026-11-14 — Protection detail refresh consistency
- Fixed the Protection tab refresh path so an active VPN session cannot replace observed DNS counters with a generic persisted service message.
- Inactive sessions continue to show specific service failure messages.
- Added two regression tests for both paths.


## 0.7.4-dev — Filter refresh retry efficiency (2026-11-14)
- Refresh each enabled filter source only when its own cache is missing/invalid or its configured update interval has elapsed.
- Avoid downloading already-successful, still-fresh lists again when WorkManager retries after an optional source failure.
- Add regression coverage for retry freshness, stale boundaries, missing metadata, future timestamps and minimum interval behavior.
- Advance Android package to versionCode 17 / versionName `0.7.4-dev`.
- CI run 43570828960 passed unit tests, lint, debug APK assembly, package identity and checksum validation. APK SHA-256: `d60df611f004d5114d9e1aaeb832a5837ce4287c5dd1b7c51c5909c8a4cfb577`. Push CI skipped artifact upload.


## 0.7.5-dev — DNS address safety and protection scope copy (2026-11-14)
- Safely handle a nullable IPv6 `hostAddress` during numeric resolver normalization.
- Clarify in the home protection-scope card that app-originated DoH/DoT bypass requests are not intercepted; this is distinct from Guardian's own encrypted upstream transports.
- CI run 43571389815 passed unit tests, lint, debug APK assembly, package identity and checksum validation.
- Package: versionCode 18 / `0.7.5-dev`; CI APK SHA-256: `f756ab65ce8d74cc7a7da45457b0cc2987b83acc83e9c993eab0a3f624492e1a`. Push CI skipped artifact upload.


## 0.7.6-dev — DNS-over-TCP resolver exception isolation (2026-11-14)
- Catch unexpected resolver callback exceptions inside the DNS TCP session manager and convert them to a DNS SERVFAIL response instead of allowing the packet loop to terminate the entire VPN service.
- Add regression coverage proving a thrown resolver exception returns SERVFAIL and the established TCP session continues to process the next DNS request.
- Package versionCode 19 / `0.7.6-dev`. CI verification pending.


## 0.7.6-dev — DNS-over-TCP resolver exception isolation (2026-11-14)
- DNS TCP session manager now catches unexpected exceptions from the resolver callback and returns a standards-shaped SERVFAIL response; a resolver exception no longer escapes to the VPN packet loop and terminates the tunnel.
- Regression test confirms SERVFAIL and successful processing of a subsequent query in the same TCP session.
- CI [43572032012](https://github.com/justinhblee-prog/guardian/actions/runs/43572032012) completed successfully: shared DNS rules and manifest checks, Android unit tests, Android lint, Debug APK assembly, package identity/version and APK checksum checks passed.
- Package: `com.guardian.adblock`, versionCode `19`, versionName `0.7.6-dev`.
- CI debug APK SHA-256: `b5ed0b9ee635eadc1ba5c337f94eed9c47767215198e2b60df5748fbf53e1e91` (about 2.9 MB). Ordinary push workflow skipped artifact upload.
- This is automated CI evidence, not physical-device validation; APK is debug-signed and not a production-signed release.


## 0.7.7-dev — Filter screen option race prevention (2026-11-14)
- The Rules/Filters screen uses CheckBox controls for optional security and Android-ad filters. Previously, an update started from that screen only disabled the corresponding Switch controls on the Protection screen, leaving the CheckBoxes interactive while the worker snapshot was downloading. This could change an option after its cache snapshot and leave an enabled filter without a validated cache.
- Track and disable both screen variants during the complete update, restore interaction afterward, and synchronize the control off when a first-time optional-list download fails.
- Package versionCode 20 / `0.7.7-dev`. CI verification pending.


## 0.7.7-dev — Filter option update-race fix (2026-11-14)
- Prevent changes to optional security and Android-app advertising filter toggles while a filter update is in progress. Both the Protection screen Switches and Filters screen CheckBoxes are disabled, including when navigating between screens during an update; controls are restored on success or failure.
- When a first-time optional list download fails and no valid cache exists, the matching control is switched off so the UI and persisted configuration remain aligned.
- CI [43572504150](https://github.com/justinhblee-prog/guardian/actions/runs/43572504150) completed successfully: unit tests, lint, debug APK assembly, package identity/version and checksum checks passed.
- Package: `com.guardian.adblock`, versionCode `20`, versionName `0.7.7-dev`.
- CI debug APK SHA-256: `1dbfd5b33e134ad25c949e24c0750793c9d43c83ec9888230f107e93c49754c4` (about 2.9 MB). This ordinary push run skipped artifact upload.
- Final package workflow now creates a source ZIP containing source, tests, workflow and project documentation, plus a Korean verification summary alongside the installable debug APK and SHA-256 file.


## 최종 산출물 패키지 생성 완료 — 2026-11-14

- 최종 CI 실행: [43572937308](https://github.com/justinhblee-prog/guardian/actions/runs/43572937308) — 성공. Android 단위 테스트, lintDebug, assembleDebug, 패키지명·버전 확인, APK checksum, 소스 패키징 및 artifact 업로드가 모두 통과했습니다.
- 커밋: `94bab42e1a9dd7e10b61fad52f2546fc075dbdcf`
- 앱: `com.guardian.adblock`, versionCode `20`, versionName `0.7.7-dev`.
- 설치 APK: `app-debug.apk`, 3,014,104 bytes. SHA-256: `4797b47bad0c92574e0b7f9b8bf347960c46e688c95be43ee6158b2d1a4a93e7`.
- 전체 소스 ZIP: `Guardian-AdBlock-Source-v0.7.7-dev.zip` (161,340 bytes, 98 entries; Android 소스·테스트·공유 규칙·워크플로·설명서 포함).
- 검증 요약: `release/VERIFICATION_SUMMARY_KO.md`.
- GitHub Actions artifact: `Guardian-AdBlock-Android-Full-Package-v0.7.7-dev`, artifact ID `14105371888`, archive SHA-256 `d5f4555603756f5b0cf97a6725ecc5ff942e98ede9c626ef91227bfa2cf12d3f`, 만료 예정 2026-12-14.
- APK는 Android debug 서명 빌드입니다. production 서명과 실기기 VPN/DNS 라우팅·Wi-Fi/모바일 전환·실제 광고 제거 수용 테스트는 수행하지 않았습니다. DNS 수준 차단 범위 및 앱 자체 DoH/DoT 우회 등 알려진 한계는 검증 문서에 명시했습니다.


## 0.7.8-dev — TCP/IP reserved-header validation (2026-11-14)
- Hardened the DNS TCP packet parser: reject IPv4 packets with the reserved fragmentation flag set, and reject TCP headers with nonzero reserved/NS bits instead of silently accepting unsupported header semantics.
- Added regression coverage for both malformed-header cases.
- Package versionCode 21 / `0.7.8-dev`.
- CI run: https://github.com/justinhblee-prog/guardian/actions/runs/43573908206 — pending at documentation update time. Do not treat this source change as verified until that run completes successfully.
- This is parser hardening only; it does not prove physical-device VPN routing or real ad-removal effectiveness.
