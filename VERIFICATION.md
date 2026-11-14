# Guardian AdBlock Android — Verification Record

기준 버전: 0.7.3-dev  
기준일: 2026-11-14

## 현재 회귀 테스트 인벤토리

최신 소스 기준 실제 테스트 파일 인벤토리는 아래의 2026-11-14 갱신 항목을 참조하세요.

| 테스트 스위트 | 테스트 메서드 |
|---|---:|
| DomainFilterTest | 17 |
| Ipv4UdpTest | 6 |
| Ipv6UdpTest | 4 |
| ProtectionStateTest | 5 |
| FilterRuleParserTest | 11 |
| DnsProbeTest | 4 |
| FilterRuleComposerTest | 3 |
| DnsAddressValidatorTest | 3 |
| FilterListProfilesTest | 8 |
| VpnConfigurationDefaultsTest | 1 |
| VpnStartCancellationGateTest | 4 |
| FilterUpdateSchedulePolicyTest | 4 |
| **합계** | **70** |

총 12개 Kotlin 테스트 스위트 / 70개 JUnit 테스트 메서드입니다.

## 최신 성공 CI

- Workflow: https://github.com/justinhblee-prog/guardian/actions/runs/43434091375
- 소스 커밋: `419356a1ef43ba20c33256b353566da7f42b8f61`
- Android unit-test 단계: **Success**
- APK assemble / upload: push CI 정책에 따라 skipped
- 이전 Adblock parser 복구 CI: https://github.com/justinhblee-prog/guardian/actions/runs/43433588312 — 59 tests completed, 0 failures

## 내부 검증된 설계 및 코드 경로

- AdGuard DNS Filter, 1Hosts Lite, OISD Big, HaGeZi Normal/Pro 프로필
- 선택형 HaGeZi Threat Intelligence 및 AWAvenue Android Ads Rule
- 원자적 파일 교체와 마지막 정상 필터 캐시 보존
- 선택 프로필 및 켜진 보조 필터 캐시가 유효하지 않으면 보호 시작 차단
- VPN 서비스 시작 직전 실제 캐시 파일 재파싱 및 최소 규칙 수 확인
- `$important` 차단 규칙은 allow 예외보다 우선 적용
- `$badfilter`는 같은 출처의 일치하는 modifier 변형만 해제
- 도메인 차단/허용은 DNS label suffix hash lookup 사용
- DNS 응답 transaction ID / 질문 이름 / QTYPE / QCLASS 확인
- DNS 규칙 엔진 자체 테스트는 현재 활성 규칙 매칭과 NXDOMAIN 생성만 검증하며 TUN 네트워크 경로 검증과 구분
- VPN 시작/중지 취소 경합 회귀 테스트
- 공용 DNS IP 직접 포착은 기본 비활성화하여 비DNS 트래픽의 부수적 차단 위험 완화

## 미검증 / 미지원 범위

- 실제 Android 기기 설치 및 VPN 권한 승인
- 실기기에서 필터 목록 다운로드, TUN 라우팅 및 실제 광고 차단 성공
- Wi-Fi↔모바일 데이터 전환, 장시간 실행, 화면 잠금/복귀
- DoH/DoT 업스트림 전송 경로는 구현되어 있으나 실제 제공자 연결·인증서 검증은 CI만으로 실기기 검증되지 않음; TCP/53은 순서형 스트림 및 중복 요청/SYN 재전송 대응을 포함하지만 타이머 기반 재전송·패킷 손실 복구는 미지원
- IPv6 확장 헤더 및 IP fragment 재조립
- 웹페이지 요소 숨김, 앱 내부/first-party 광고 제거
- 앱별 제외 및 고급 공용 DNS 직접 포착의 실기기 검증

공용 DNS 직접 포착은 Android VPN의 IP 단위 라우팅을 사용합니다. DNS 서버 IP로 가는 비DNS 트래픽도 TUN에 들어올 수 있으며 앱은 이를 중계하지 않습니다. 이 기능은 기본 비활성화입니다.

## APK 정책

- 개발 중 push CI에서는 테스트만 수행합니다.
- 개발 완료 전 중간 APK를 최종본으로 제공하지 않습니다.
- 최종 APK는 별도 수동 릴리스 workflow에서만 생성합니다.
- APK 생성 시 package/version, APK/ZIP 무결성, SHA-256을 확인합니다.


## Self-test correction and sticky VPN recovery

- Removed the misleading app-owned UDP socket probe: Android excludes the VPN app's own sockets from its VPN route, so querying the virtual DNS IP from the app itself does not prove packets traversed the TUN.
- The self-test now truthfully validates loaded rule matching, NXDOMAIN RCODE generation, and non-blocking of `example.com` in-process. It does not claim to verify network routing.
- VPN service now returns `START_STICKY` and rebuilds its TUN only when Android recreates it with a null intent and the persisted protection state was active. Explicit stop remains non-restoring.
- Latest source CI: https://github.com/justinhblee-prog/guardian/actions/runs/43435146415
- Source commit under test: `ea97e9482afa009f7f1bc5ff718428ce6c282ec7`
- Android unit-test step: **success**; full workflow cleanup was still running when recorded.
- Test inventory at this commit: 12 suites / 68 test methods.


## Latest CI verification

- [GitHub Actions run 43436954231](https://github.com/justinhblee-prog/guardian/actions/runs/43436954231): **success**
- Source commit: `57f30f68484fa4d6ef716a557efe8a75a4446407`
- Android unit tests: passed; APK assembly/upload skipped because this was a push-triggered validation run.
- Inventory: 12 test suites / 68 test methods.


## UI polish verification

- DNS upstream, filter profile and update interval selectors now use dark themed selected/dropdown rows consistent with the Guardian dashboard palette.
- Status refreshes no longer retain/update detached home-screen views while the user is on other tabs.


## Latest verification update

- Explicit user stop now clears the persisted sticky-service restart flag even if VPN startup has not yet committed a TUN descriptor. Routine service cleanup without a live tunnel does not overwrite the original startup failure reason.
- Added two unit tests for this stop/start race policy.
- [GitHub Actions run 43436954231](https://github.com/justinhblee-prog/guardian/actions/runs/43436954231): **success** (Android unit tests passed; APK assembly intentionally skipped on push).
- Test inventory: 12 suites / 68 test methods.


## WorkManager background refresh

- Added WorkManager 2.12.0 periodic filter worker with connected-network constraint, user-configured 6/12/24-hour or 7-day cadence, exponential retry, and cancellation when auto-update is disabled.
- Worker refreshes selected base list and enabled optional lists, then asks an active VPN service to reload rules.
- Filter cache downloads are synchronized so manual and periodic workers cannot write the same temporary cache simultaneously.
- Added two interval policy tests. Current source inventory: 12 suites / 68 test methods.


## Latest WorkManager verification

- [GitHub Actions run 43437792450](https://github.com/justinhblee-prog/guardian/actions/runs/43437792450): **success**
- Source commit: `5d7624fd445edadb1a1ed24a482b9e2d25f07d41`
- Android unit tests: **passed**; APK assembly was skipped because this was a push-triggered validation run.
- Test inventory: **12 suites / 68 test methods**.


## IPv6 resolver preset coverage
- Added IPv6 resolver presets for Cloudflare, Google, Quad9, and AdGuard DNS default/family protection.
- Added AdGuard default IPv6 addresses to the optional direct-DNS capture route set.
- Added IPv6 literal validation regression test; device/network IPv6 reachability remains unverified.


## Latest IPv6 resolver verification
- GitHub Actions: https://github.com/justinhblee-prog/guardian/actions/runs/43443209122 — **success**
- Source commit: `f518711e559bfb8873128f82c07a1a157a5f2068`
- Android unit tests: passed; inventory **12 suites / 68 JUnit methods**.
- APK assembly and upload: skipped because this was a push-triggered validation run.


## Latest CI verification — interval selection regression

- GitHub Actions: https://github.com/justinhblee-prog/guardian/actions/runs/43444407063 — **success**
- Source commit: `1a27d0627dc4e6448baf7eb4b271371cfd60708e`
- Android unit-test step: **passed**.
- Regression coverage: settings spinner preserves the selected 6-hour and 12-hour values and falls back to the daily default for an unknown stored interval.
- Test inventory independently counted from all test source files: **12 suites / 70 JUnit test methods**.
- APK assemble and upload: **skipped by design** for push-triggered CI; final APK remains gated on development completion.
 

## Final APK build verification — 2026-11-13

- GitHub Actions run: https://github.com/justinhblee-prog/guardian/actions/runs/43445545156 — **success**
- Source commit: `29a10e46f27dd1bafdde72a62ddd984b9965a537`
- Android unit tests: **12 suites / 70 JUnit test methods passed**
- Debug APK assembly: **success**
- Package identity: `com.guardian.adblock`, versionCode `14`, versionName `0.7.1-dev` — verified by CI `aapt dump badging`
- APK size: 2,978,276 bytes
- APK SHA-256: `4ee62c89678f0cef2b75d96a019a5700064538efaff4c82e9768a9bbf28ddb75`
- APK ZIP integrity: verified locally with `unzip -t`; no archive errors.
- Artifact: `Guardian-AdBlock-Android-APK-v0.7.1-dev` (GitHub artifact ID `14048365232`)
- This is an installable debug-signed APK. Physical Android installation, VPN route validation, and real ad-block effectiveness remain unverified.


## 0.7.2-dev DNS truncation regression verification

- Source under test: `5a3bd8843a74e4c74d4f89926774460736b1bfba`
- GitHub Actions run: https://github.com/justinhblee-prog/guardian/actions/runs/43446556136
- Android unit-test step: **success** (12 suites / 72 JUnit test methods).
- Debug APK assembly, package identity check and SHA-256 step: **success** for the source under test (version still 0.7.1-dev at this commit).
- Regression: 1232-byte UDP DNS response ceiling and TC response generation for oversized upstream answers.
- New version 0.7.2-dev is being packaged separately after versionCode/versionName bump; this CI run does not represent that version.


## Final 0.7.2-dev APK — verified

- GitHub Actions: https://github.com/justinhblee-prog/guardian/actions/runs/43447121533 — **success**
- Final source commit: `50e768e253ae3ee7d77d3cdc07b5f23ccd970eab`
- Android unit tests: **12 suites / 72 JUnit test methods passed**
- Debug APK assembly: **success**
- Package identity: `com.guardian.adblock`, versionCode `15`, versionName `0.7.2-dev` — CI verification passed
- APK size: **2,978,452 bytes**
- APK SHA-256: `a1d41cf8937b2c09f7c4ca8d0b02ddf252e95f935b1cee3ed1d485c5470f55d8`
- Artifact: `Guardian-AdBlock-Android-APK-v0.7.2-dev`, ID `14049565251`
- Downloaded artifact ZIP integrity: `unzip -t` passed; APK and checksum file extracted and independently verified.
- This is a debug-signed installable APK. Physical-device VPN routing and real-world ad-block effectiveness remain unverified.


## DNS-over-TCP/53 — latest source verification

- GitHub Actions run: https://github.com/justinhblee-prog/guardian/actions/runs/43452443314 — **success**
- Source commit: `09502f426d8c51c03398927a6e10235ec4b23874`
- Android unit tests: **14 suites / 88 JUnit test methods passed**
- Debug APK assembly, package identity and SHA-256 checks: **success**
- Package: `com.guardian.adblock`, versionCode `15`, versionName `0.7.2-dev`
- APK SHA-256: `bd9a985de52090d0c9729266b4c3e43058f5c99e1f3b6440100f90243dec9eb5`
- Push CI intentionally skipped artifact upload; this is build verification, not a newly published install file.
- Regression coverage includes IPv4/IPv6 TCP packet checksums, TCP sequence advancement after SYN, DNS-over-TCP framing, partial-frame reassembly, multiple queries per segment, large-response segmentation, and FIN+payload.
- Device-level VPN/resolver routing and real-world ad blocking remain unverified. Exact duplicate client request segments replay cached DNS replies; autonomous retransmission timers/partial-ACK recovery, IPv6 extension headers and IP fragmentation remain unsupported.

- Protection tab quick controls and recovery button behavior are included in source commit `af9b8714b4c56f98c9541a065e9b217a6c33551d`; CI run 43459666939 passed.

- Android lint: `lintDebug` **passed** in CI run 43459666939 after marking the app-private receiver `RECEIVER_NOT_EXPORTED` through `ContextCompat.registerReceiver`.

- Self-test scope: local rule matching and NXDOMAIN generation only; it does not verify the real resolver network path or in-app ad removal.

- DNS upstream modes added: UDP/53, TCP/53 and DoT/853. Provider host mapping and custom TLS-host validation are unit-tested; external TLS handshake/provider connectivity is not device-tested.
- Latest CI run 43459666939: 16 suites / 99 JUnit tests, lintDebug, debug APK assembly, package identity and SHA-256 checks passed.

- DNS-over-HTTPS codec tests cover binary POST request framing, Content-Length, chunked response decoding, redirects, media type, truncation and size limits.
- CI run 43459666939: 16 suites / 99 JUnit tests, lintDebug, debug APK assembly, package identity and SHA-256 checks passed.
- DoH/DoT external server handshake and live DNS provider connectivity are not verified by unit tests or CI.

- Latest source commit `af9b8714b4c56f98c9541a065e9b217a6c33551d` passed CI run 43459666939: 16 suites / 99 tests, lint, APK assembly, package identity and checksum.

- IPv6 TCP session integration test: **passed**. Latest CI run 43459666939: 16 suites / 99 JUnit tests; lintDebug and debug APK package/checksum checks passed.


## Latest CI — 2026-11-13

- Workflow: https://github.com/justinhblee-prog/guardian/actions/runs/43525073201
- Source commit: `827198814d0b4441afe72c2ddb81b6d1a41a8f19`
- Android unit tests: **passed**
- Android lint: **passed**
- Debug APK assembly: **passed**
- Package identity/version: `com.guardian.adblock`, versionCode `15`, versionName `0.7.2-dev` — **passed**
- APK file existence and SHA-256: **passed**
- SHA-256: `c40229d5c3a25173092233b2e23ace19d9c3d6f831837d6d6684c20f156495ee`
- Push-triggered workflow skipped the manual-release-only artifact upload. No new installable artifact was published by this run.
- CI does not validate actual Android VPN routing, live upstream reachability on the user's device, TCP/53 resolver fallback, or real-world ad removal.


## 2026-11-14 — TCP DNS 세션 안정성 검증 갱신

- Source commit: `4c8511770039665da0eb61b018c460d86a8031e2`
- GitHub Actions: [run 43530335902](https://github.com/justinhblee-prog/guardian/actions/runs/43530335902) — success
- Android unit tests, lintDebug, assembleDebug, package identity/version 및 APK checksum 단계 모두 성공
- 테스트 인벤토리 재집계: **17개 Kotlin 테스트 스위트 / 104개 JUnit 테스트 메서드**
- APK: `com.guardian.adblock` / versionCode 15 / versionName `0.7.2-dev`
- CI 생성 APK SHA-256: `071fd9fc18288ba3144c747fb2a92beb9a93e3f58557ee49e9b086811f97f7ec`
- 새 회귀 테스트는 연결 완료 후 도착한 동일 SYN이 세션 상태를 초기화하지 않는지 확인합니다.
- CI 검증은 실기기 설치, 실제 VPN 라우팅, 실제 광고 제거 성공을 대체하지 않습니다.


## 2026-11-14 — HTTPS 필터 리디렉션 정책 검증

- Source commit: `7988ced0500324df91900017993882e7cb10893b`
- [GitHub Actions run 43531095663](https://github.com/justinhblee-prog/guardian/actions/runs/43531095663): **success**
- Android unit tests: passed; lintDebug: passed; assembleDebug: passed; package/version/checksum checks: passed.
- Test inventory: **18 Kotlin test suites / 110 JUnit test methods**.
- Filter downloads now follow redirects manually and only over HTTPS, reject embedded URL credentials, unsupported redirect statuses, missing/invalid locations, and chains longer than five hops.
- CI APK package/version: `com.guardian.adblock` / `15` / `0.7.2-dev`.
- CI APK SHA-256: `902b8045209dc0d73ae34bb6d8740673b7f3fc45df0da9d5f6a6d3331826aada`.
- Push workflow intentionally skipped artifact upload. Physical Android VPN and ad-blocking acceptance remain unverified.


## 2026-11-14 — Dynamic Android version display

- Source commit: `1616c8fdd11c92c1c982c3b24c4661242433d366`
- GitHub Actions run: https://github.com/justinhblee-prog/guardian/actions/runs/43531690119 — **success**
- `testDebugUnitTest`: passed.
- `lintDebug`: passed.
- `assembleDebug`: passed.
- APK package/version check: `com.guardian.adblock`, versionCode `15`, versionName `0.7.2-dev` — passed.
- APK file existence and SHA-256 calculation: passed.
- BuildConfig generation is explicitly enabled; Settings reads `BuildConfig.VERSION_NAME`.
- Push workflow intentionally skipped artifact upload. No physical-device validation is implied.


## 2026-11-14 — DNS-over-TCP FIN 재전송 신뢰성

- Source commit: `a76491c4846f4eeb01bc0fc9cf2c0dd1e01fb183`
- GitHub Actions run: https://github.com/justinhblee-prog/guardian/actions/runs/43533452981 — **success**
- `testDebugUnitTest`: passed.
- `lintDebug`: passed.
- `assembleDebug`: passed.
- APK package/version: `com.guardian.adblock`, versionCode `15`, versionName `0.7.2-dev` — passed.
- APK existence and SHA-256: passed; CI output hash `b7a35a98f76ce20aa272358c9b0d2f5584a8ec7dd6f88308427840fbe7613b89`.
- DNS-over-TCP half-close now retains session state until the client's ACK of the server FIN; exact retransmissions of the final client data/FIN replay the prior server response instead of resolving the DNS query again.
- Added regression coverage for retransmitted data+FIN and retransmitted client FIN while the server FIN remains unacknowledged.
- Push CI skipped APK artifact upload. Device installation, VPN routing, live resolver behavior, and real app ad removal remain unverified.


## 2026-11-14 — 관측 기반 보호 상태 진단

- Replaced the unconditional “system DNS is passing through Guardian” claim with status text derived from live tunnel state, observed DNS query counters, and Android Private DNS mode.
- An active tunnel with zero observed DNS requests now explicitly reports that no DNS traffic has been observed and asks the user to generate traffic; it does not claim filtering is working.
- Opportunistic and hostname Private DNS modes now appear in the protection detail as possible encrypted-DNS bypass conditions.
- Added five unit tests covering zero traffic, observed counters, both Private DNS modes, and inactive tunnel behavior.
- CI validation is pending for this commit. No real-device or actual ad-removal validation is implied.


## 2026-11-14 — 보호 상세 상태 갱신 경로 수정

- Follow-up review found that the Protection screen's refresh path preferred the persisted generic service message over the new observed-traffic diagnostic. This could hide the real DNS counters even though the initial screen render was correct.
- Changed active-session detail to always derive from observed DNS counters and Private DNS mode; inactive sessions still preserve a specific service failure message.
- Added two regression tests for active generic-message override and inactive failure-message preservation.
- Previous commit `745751208cc4e970818aa0a3bd730643541f03e0` CI run 43535030810 succeeded: unit tests, lint, debug APK assembly, package/version verification, and SHA-256 generation all passed. APK SHA-256: `b222fcc3b89d7c47265eb0caa9781beb628fe4c097666ea927b01f6c9bb3e75a`. This checksum belongs to that source revision, not the follow-up fix.


## 2026-11-14 — Guardian 0.7.3-dev APK 최종 CI 검증

- GitHub Actions: [run 43562402511](https://github.com/justinhblee-prog/guardian/actions/runs/43562402511) — **success**
- 소스 커밋: `484670a6c6db36bf3cce11c3d438dd2d17741bad`
- Android 단위 테스트: **19개 Kotlin 테스트 스위트 / 127개 JUnit 테스트 메서드 통과**
- Android `lintDebug`: **통과**
- Android `assembleDebug`: **통과**
- 패키지: `com.guardian.adblock`, versionCode `16`, versionName `0.7.3-dev`
- APK 파일 존재, 패키지 식별, SHA-256 계산 및 artifact 업로드: **통과**
- APK SHA-256: `355080e2a1d8f64fc9720858c889368229db7b4365cc753732275c88f6c3cf11`
- GitHub artifact: `Guardian-AdBlock-Android-APK-v0.7.3-dev`, ID `14100743136`
- Artifact ZIP SHA-256: `0666514555d869c5e3c25a4e44d1d9aa423c9d7143fcef4960b286d66f1b1aff`
- README 변경이 push 경로 필터에서 누락되어 APK 업로드 workflow가 실행되지 않던 문제를 수정했습니다. README를 workflow paths에 포함했고, `[final-apk]` 커밋에서 전체 검증 및 artifact 업로드가 실행되도록 확인했습니다.
- 릴리스 구분: 설치 가능한 debug 서명 APK입니다. production keystore 서명 및 Play Store 배포 검증은 포함하지 않습니다.
- **실기기 미검증:** VPN 권한 승인, 실제 DNS 패킷 경로, 실제 필터 다운로드/적용, Wi-Fi↔모바일 데이터 전환, 절전/장시간 실행, 앱별 제외 및 실제 광고 제거 효과는 CI로 검증되지 않았습니다.


## 2026-11-14 — 다중 탭 보호 시작/권한 거부 회귀 검증

- Source commit: `019a4bdf576e56c0f893b68822efcc4c77846c41`
- [GitHub Actions run 43563225658](https://github.com/justinhblee-prog/guardian/actions/runs/43563225658): **success**
- `testDebugUnitTest`: passed.
- `lintDebug`: passed.
- `assembleDebug`: passed.
- APK package/version: `com.guardian.adblock`, versionCode `16`, versionName `0.7.3-dev` — passed.
- APK existence and SHA-256 generation: passed.
- Fixed Home-only view access from Protection/other tabs during protection start and VPN permission denial. Status refresh now guards Home-only views and updates Protection-specific status views separately.
- The workflow skipped artifact upload because this was a normal push, not a manual release dispatch. CI assembly is not a published APK artifact.
- Physical-device VPN routing, actual DNS filtering, and real ad-removal acceptance remain unverified.


## 2026-11-14 — 보안 필터 설정 즉시 반영 회귀 검증

- Source commit: `d2e3689212280e5149c9b44012285edf323a971d`
- GitHub Actions: https://github.com/justinhblee-prog/guardian/actions/runs/43567779995 — **success**
- Android unit tests: **passed**
- `lintDebug`: **passed**
- `assembleDebug`: **passed**
- Package/version: `com.guardian.adblock`, versionCode `16`, versionName `0.7.3-dev` — passed
- APK existence and SHA-256 generation: **passed**
- Settings의 악성코드·피싱 보안 필터 스위치를 변경할 때 활성 VPN의 규칙을 재적용하도록 수정했습니다. 보안 목록 캐시가 없을 때는 다운로드를 시작하며, 캐시가 준비된 경우 보호 세션을 중단하지 않고 규칙을 다시 불러옵니다.
- Push CI이므로 APK artifact 업로드는 manual release dispatch 전용 정책에 따라 skip되었습니다. 이 run은 APK 빌드 검증이며 배포용 artifact 발행을 의미하지 않습니다.
- 실기기 설치, VPN 라우팅, 실제 광고 차단 효과는 검증 범위 밖입니다.


## 2026-11-14 — 보안 필터 중복 재적용 제거 회귀 검증

- Source commit: `170b4e96371b9f4072570ac06fb2e4d69f8f173e`
- [GitHub Actions run 43568131394](https://github.com/justinhblee-prog/guardian/actions/runs/43568131394): **success**
- Android unit tests, `lintDebug`, `assembleDebug`, package/version check, APK existence and SHA-256: **passed**.
- Removed redundant VPN rule reload before starting a missing security-list download. Cached-list toggles still reload immediately; download-required toggles reload after the update path completes.
- Artifact upload skipped for normal push; no new APK artifact was published.
- Physical-device VPN behavior and real ad-removal effectiveness remain unverified.


## 2026-11-14 — 설정 화면 초기 이벤트 회귀 수정

- 소스 커밋 `2d96698`: 필터 업데이트 주기 Spinner의 초기 선택 콜백이 같은 주기의 WorkManager 작업을 불필요하게 재예약하지 않도록 수정했습니다. 실제 선택값이 변경된 경우에만 설정을 저장하고 `FilterUpdateScheduler.sync()`를 호출합니다.
- 회귀 검토 중 프로필 변경은 이미 다운로드 진행 중 차단되고 있음을 확인했습니다.
- [GitHub Actions run 43569003248](https://github.com/justinhblee-prog/guardian/actions/runs/43569003248): Android 단위 테스트, Lint, Debug APK 조립, 패키지명·버전 확인 및 APK SHA-256 생성 단계가 모두 성공했습니다. 일반 push이므로 APK artifact 업로드는 건너뛰었습니다.
- APK package: `com.guardian.adblock`, versionCode `16`, versionName `0.7.3-dev`.
- 이 검증은 자동화 CI 결과이며 실기기 설치·VPN 라우팅·실제 광고 차단 검증은 아닙니다.


## 2026-11-14 — DNS 필터 규칙 및 설정 회귀 검증

- `5db62fa`: Settings 화면을 열 때 DNS 선택 Spinner의 초기 콜백이 사용자 지정 DoT TLS 호스트명을 기본값으로 덮어쓰지 않도록 수정했습니다. [CI 43568689717](https://github.com/justinhblee-prog/guardian/actions/runs/43568689717) 성공.
- `2d966987`: Filters 화면 진입 시 업데이트 주기 Spinner의 초기 콜백이 WorkManager 주기 작업을 불필요하게 재예약하지 않도록 수정했습니다. [CI 43569003248](https://github.com/justinhblee-prog/guardian/actions/runs/43569003248) 성공.
- 두 CI 모두 Android 단위 테스트, Lint, Debug APK 조립, package identity/version 검사, APK 존재 및 SHA-256 검사를 통과했습니다. 일반 push이므로 배포용 artifact 업로드는 수행하지 않았습니다.
- `83501c1` / `d5fea43`: DNS 규칙으로 표현할 수 없는 URL 경로·후행 조건 규칙을 전체 도메인 규칙으로 넓혀 적용하지 않도록 파서를 보완하고 회귀 테스트를 추가했습니다. CI 실행 결과 확인 전입니다.


## 2026-11-14 — DNS parser path-constrained rule regression

- Parser behavior: URL path and suffix constraints after the Adblock separator are not representable as DNS hostname rules and are rejected rather than broadened to a whole-domain block.
- Regression test covers `||path.example^/ads/`, `||suffix.example^tracker`, `@@||trusted.example^/login`, and valid `||safe.example^`. Only `safe.example` is expected in the blocked set; three unsupported rules are counted as ignored.
- CI [43569255957](https://github.com/justinhblee-prog/guardian/actions/runs/43569255957) completed successfully. Unit tests, Android lint, debug APK assembly, package identity/version validation, and APK checksum generation all passed. Artifact upload was skipped on this ordinary push.
- CI does not replace physical-device VPN, live DNS routing, or real-world ad-removal testing.


## 2026-11-14 — Per-source filter refresh retry regression

- Worker now checks each enabled source independently against cache validity and its own `updatedAt` timestamp. A retry caused by a failed optional source no longer re-downloads a base or optional list that already succeeded and remains fresh.
- Tests cover missing cache/timestamp, fresh cache retry, stale cache boundary, future timestamps and WorkManager minimum interval clamping.
- [CI run 43570828960](https://github.com/justinhblee-prog/guardian/actions/runs/43570828960) completed successfully: Android unit tests, `lintDebug`, `assembleDebug`, package identity/version and checksum checks passed.
- Verified package: `com.guardian.adblock`, versionCode `17`, versionName `0.7.4-dev`.
- CI debug APK SHA-256: `d60df611f004d5114d9e1aaeb832a5837ce4287c5dd1b7c51c5909c8a4cfb577`. Artifact upload was skipped under the push workflow policy.
- This does not constitute physical-device acceptance or proof of real-world ad removal.


## 2026-11-14 — IPv6 address normalization safety and protection-scope copy

- IPv6 `Inet6Address.hostAddress` is handled as nullable during normalization; null is returned safely rather than dereferenced.
- Home dashboard wording now distinguishes encrypted DNS sent directly by apps (not intercepted) from Guardian's own encrypted upstream transport options.
- CI [43571389815](https://github.com/justinhblee-prog/guardian/actions/runs/43571389815) succeeded: unit tests, Android lint, debug APK assembly, package identity/version and SHA-256 checks all passed.
- Package: `com.guardian.adblock`, versionCode `18`, versionName `0.7.5-dev`.
- CI debug APK SHA-256: `f756ab65ce8d74cc7a7da45457b0cc2987b83acc83e9c993eab0a3f624492e1a`. Push CI skipped artifact upload.


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
