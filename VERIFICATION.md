# Guardian AdBlock Android — Verification Record

기준 버전: 0.7.1-dev  
기준일: 2026-11-13

## 현재 회귀 테스트 인벤토리

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
- DoH/DoT; TCP/53은 기본 순서형 스트림 지원만 제공하며 재전송·손실 복구는 미지원
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

- Protection tab quick controls and recovery button behavior are included in source commit `ccbb8e63c5bb8908d942a92c1012a3751a918ccb`; CI run 43458721003 passed.

- Android lint: `lintDebug` **passed** in CI run 43458721003 after marking the app-private receiver `RECEIVER_NOT_EXPORTED` through `ContextCompat.registerReceiver`.

- Self-test scope: local rule matching and NXDOMAIN generation only; it does not verify the real resolver network path or in-app ad removal.

- DNS upstream modes added: UDP/53, TCP/53 and DoT/853. Provider host mapping and custom TLS-host validation are unit-tested; external TLS handshake/provider connectivity is not device-tested.
- Latest CI run 43458721003: 16 suites / 98 JUnit tests, lintDebug, debug APK assembly, package identity and SHA-256 checks passed.

- DNS-over-HTTPS codec tests cover binary POST request framing, Content-Length, chunked response decoding, redirects, media type, truncation and size limits.
- CI run 43458721003: 16 suites / 98 JUnit tests, lintDebug, debug APK assembly, package identity and SHA-256 checks passed.
- DoH/DoT external server handshake and live DNS provider connectivity are not verified by unit tests or CI.

- Latest source commit `ccbb8e63c5bb8908d942a92c1012a3751a918ccb` passed CI run 43458721003: 16 suites / 98 tests, lint, APK assembly, package identity and checksum.
