## 2026-11-17 최신 통합 상태 — main 기준

- Android: `com.guardian.adblock`, versionCode `27`, versionName `0.7.14-dev`.
- main 커밋: `28abc77342f34898dd03c198fef942c0b49a7fe0`.
- PR #78: 운영자 라이선스 대시보드 개선 병합. License API main CI [44059072829](https://github.com/justinhblee-prog/guardian/actions/runs/44059072829) 성공.
- PR #79: Android APK와 동일 커밋의 전체 소스 ZIP, SHA-256 manifest, 자동 artifact 감사를 추가해 병합.
- Android CI [44060999851](https://github.com/justinhblee-prog/guardian/actions/runs/44060999851) 성공: unit tests, lint, APK build, 이전 개발 버전 업데이트 설치, API 35 emulator VPN/DNS E2E. 로그: protection_active=true, dns_queries=8, dns_forwarded=7, dns_blocked=1, dns_ad_blocked=1, dns_failed=0. example.com은 정상 IP 응답, doubleclick.net은 차단(unknown host) 확인.
- APK artifact [guardian-android-debug](https://github.com/justinhblee-prog/guardian/actions/runs/44060999851), artifact ID `14317588578`.
- APK: 3,119,618 bytes, SHA-256 `24db2c1db46a66d2117cb35f7541c893b3321ee7de46363b615fd1a585319c78`.
- 전체 소스 ZIP: 446,281 bytes, SHA-256 `a22d74b994b919e19de4125a719243786b76fa50dbb2d19d85200345afdcee17`.
- 자동 artifact 감사 [44062116800](https://github.com/justinhblee-prog/guardian/actions/runs/44062116800) 성공: APK/source SHA-256 및 ZIP 무결성 모두 통과.
- Signed filter distribution CI [44052664109](https://github.com/justinhblee-prog/guardian/actions/runs/44052664109), [44052665884](https://github.com/justinhblee-prog/guardian/actions/runs/44052665884) 성공.
- Production release 설정/임시 서명 smoke test [44052664040](https://github.com/justinhblee-prog/guardian/actions/runs/44052664040) 성공. production signing job은 운영 keystore/secrets 부재로 skipped이며 production APK/AAB는 미생성입니다.
- 실제 Android 스마트폰/태블릿 VPN/TUN·네트워크 매트릭스·광고 제거율, 운영 HTTPS 필터 게시, License API/DB/TLS/Tailscale 배포는 미검증입니다.
- Windows EXE/설치 프로그램은 Android 및 공통 기능 이후 마지막 단계로 보류합니다.

# 최신 개발 인수인계 — 2026-11-17

> 이 섹션이 아래의 과거 버전별 인수인계보다 우선합니다.

- 저장소: https://github.com/justinhblee-prog/guardian
- 현재 기준: Android 0.7.14-dev / versionCode 27, main commit `28abc77342f34898dd03c198fef942c0b49a7fe0`.
- Android license/device management, signed filter client/publisher, License API/PostgreSQL 및 운영자 웹 대시보드의 코드와 CI는 반영되었습니다.
- APK + 동일 커밋 전체 소스 ZIP artifact가 main에 생성되고 별도 이벤트 감사에서 해시·ZIP 무결성까지 검증되었습니다.
- 개발용 debug APK는 확보했지만 production keystore/secrets가 없어 production APK/AAB는 없습니다.
- 사용자 우선순위: Android 모바일과 공통 기능을 먼저 마무리하고, 실제 기기/운영 환경 검증은 개발 작업 완료 후 진행하며, Windows 패키징은 마지막에 수행합니다.

## 다음 내부 개발 순서

1. Android 모바일/태블릿 및 공통 라이선스·필터 기능의 남은 코드 작업을 마감합니다.
2. 운영 서명 필터 공개키·HTTPS 배포와 License API/DB/TLS 운영 배포에 필요한 구성을 문서·스크립트 수준에서 마무리합니다. 개인 운영키는 저장소/채팅에 넣지 않습니다.
3. production signing workflow의 보호된 secrets 입력 경로를 유지하고, 운영 keystore가 안전한 환경에 준비되기 전에는 production APK/AAB를 생성하지 않습니다.
4. 위 개발 항목이 끝난 뒤 실제 Android 기기 VPN/DNS, Wi-Fi↔모바일, IPv4/IPv6, Private DNS, 앱 제외, 광고 차단 수용 시험을 수행합니다.
5. Windows EXE/설치 프로그램 빌드·설치·제거·DNS 복구·재부팅 검증은 마지막에 진행합니다.
6. Android/Windows별 검증 결과와 미지원 범위를 분리해 통합 릴리스 판정을 기록합니다.

실기기 검증 전에는 에뮬레이터 성공을 실제 기기 통과로 표현하지 않습니다.
# Guardian AdBlock Android 0.7.1-dev — 개발 인수인계

작성일: 2026-11-13

## 1. 프로젝트 상태

- GitHub: https://github.com/justinhblee-prog/guardian
- Branch: main
- Package: `com.guardian.adblock`
- Version: `0.7.1-dev` / versionCode `14`
- 상태: 개발 진행 중
- 최종 APK: 아직 생성하지 않음
- push CI: Android unit test 전용. APK는 개발 완료 후 수동 릴리스 workflow에서 생성

## 2. 기존 광고 차단이 작동하지 않았던 이유

0.6.5-dev는 기본 차단 규칙이 10개 도메인뿐이었고, 외부 필터 목록 다운로드와 Adblock 규칙 해석이 없었습니다. VPN 상태가 켜져 있어도 실제 광고 도메인의 대부분이 차단 목록에 없었습니다.

0.7.1-dev에서는 공식 필터 목록 다운로드·검증·캐시·적용 경로를 구현하고, 선택한 필터 구성의 캐시가 검증된 경우에만 VPN 보호를 시작하도록 변경했습니다.

## 3. 구현 기능

### 필터 / 규칙
- 프로필: AdGuard DNS Filter, 1Hosts Lite, OISD Big, HaGeZi Normal, HaGeZi Pro
- 선택형 HaGeZi Threat Intelligence 악성코드·피싱 목록
- 선택형 AWAvenue Ads Rule Android 앱 광고 SDK 목록
- 6시간 / 12시간 / 24시간 / 7일 업데이트 확인 주기
- 앱 실행 시 자동 업데이트 확인 및 수동 업데이트
- 사용자 차단/허용 도메인 규칙
- Adblock 도메인 앵커, 예외, hosts, `$important`, 출처별 `$badfilter`
- 미지원 문맥/재작성 규칙은 임의 해석하지 않고 제외 수로 기록
- UTF-8 BOM 처리 및 대형 목록 도메인 suffix lookup

### DNS / VPN
- Android VpnService 가상 DNS
- IPv4 / IPv6 UDP DNS 처리
- NXDOMAIN / SERVFAIL 응답
- 업스트림 응답의 transaction ID, 질문 이름, QTYPE, QCLASS 검증
- 잘린 업스트림 UDP 응답에 대한 TCP fallback
- Cloudflare / Google / Quad9 / AdGuard DNS / AdGuard Family DNS upstream
- 사용자 지정 IPv4 / IPv6 DNS 주소
- 공용 DNS 직접 포착 고급 옵션(기본 꺼짐)
- VPN 시작/중지/재시작 취소 경합 보호
- DNS 규칙 엔진 자체 테스트

### UI / 옵션
- 홈: 보호 상태, 시작/중지, 필터 준비 상태, 통계, DNS 자체 테스트
- 보호: IPv4/IPv6 DNS 범위 및 미지원 범위
- 활동: 질의/차단/전달/실패/무시, 최근 요청, 진단, 초기화
- 필터: 프로필, 보안 목록, Android 광고 SDK 목록, 업데이트 설정, 사용자 규칙
- 설정: DNS upstream, 사용자 지정 DNS, 앱별 VPN 제외 검색

## 4. 캐시 및 보호 시작 안전장치

1. 필터는 임시 파일로 다운로드합니다.
2. 파서가 인식한 유효 차단 도메인 수와 파일 크기를 검증합니다.
3. 검증된 파일만 원자적으로 캐시 교체합니다.
4. 실패 시 이전 정상 캐시를 보존합니다.
5. 선택 프로필과 캐시 프로필이 다르면 보호 시작을 거부합니다.
6. 켜진 보안/Android 광고 목록도 각각 캐시 준비 상태를 확인합니다.
7. VPN 서비스 시작 시 실제 캐시 파일을 다시 파싱해 최소 규칙 수를 확인합니다.
8. 조건을 만족하지 못하면 TUN을 활성화하지 않습니다.

## 5. 주요 소스 파일

- `GuardianVpnService.kt`: VPN 수명주기, TUN, DNS 전달, 라우팅, 규칙 적용
- `VpnStartCancellationGate.kt`: 시작/중지 취소 순서 제어
- `DomainFilter.kt`: DNS 질문/응답 검증, 도메인 매칭, NXDOMAIN/SERVFAIL
- `FilterRuleParser.kt`: Adblock / hosts / 예외 / important / badfilter 파서
- `FilterRuleComposer.kt`: 출처별 규칙 조합과 badfilter 격리
- `FilterListManager.kt`: 공식 목록, 프로필, 다운로드, 캐시 및 무결성
- `DnsAddressValidator.kt`: 사용자 지정 DNS 주소 검증
- `DnsProbe.kt`: DNS 자체 테스트 질의
- `Ipv4Udp.kt` / `Ipv6Udp.kt`: IPv4/IPv6 UDP 패킷 처리
- `MainActivity.kt`: 홈, 보호, 활동, 필터, 설정 UI
- `android/app/src/test`: **12개 테스트 스위트 / 68개 JUnit 테스트 메서드**

## 6. 필터 출처

- AdGuard DNS Filter: https://adguardteam.github.io/HostlistsRegistry/assets/filter_1.txt
- 1Hosts Lite: https://adguardteam.github.io/HostlistsRegistry/assets/filter_24.txt
- OISD Big: https://adguardteam.github.io/HostlistsRegistry/assets/filter_27.txt
- HaGeZi Normal: https://adguardteam.github.io/HostlistsRegistry/assets/filter_34.txt
- HaGeZi Pro: https://adguardteam.github.io/HostlistsRegistry/assets/filter_48.txt
- HaGeZi Threat Intelligence: https://adguardteam.github.io/HostlistsRegistry/assets/filter_44.txt
- AWAvenue Ads Rule: https://adguardteam.github.io/HostlistsRegistry/assets/filter_53.txt

## 7. 최신 내부 검증

- 성공 CI: https://github.com/justinhblee-prog/guardian/actions/runs/43434091375
- 소스 커밋: `419356a1ef43ba20c33256b353566da7f42b8f61`
- Android unit-test 단계: **Success**
- 테스트 소스 인벤토리: 12 suites / 68 test methods
- APK build/upload: push CI 정책에 따라 skipped
- 최신 후속 UI 안내문 수정 커밋은 CI 재검증 중일 수 있으므로 최종 상태를 Actions에서 확인해야 합니다.

CI 성공은 실기기 VPN 동작이나 실제 광고 차단 성공을 증명하지 않습니다.

## 8. 제한 및 미검증 범위

- DoH / DoT 미지원; TCP/53은 제한된 순서형 DNS 스트림 구현이며 TCP 재전송·손실 복구 미지원
- IPv6 extension headers 및 IP fragment 재조립 미지원
- 전체 IP 트래픽을 프록시하는 full-tunnel VPN 아님
- 웹페이지 요소 숨김 및 앱 내부/first-party 광고 제거 미지원
- 실기기 설치, VPN 권한, 실제 필터 다운로드, DNS 라우팅, 실제 광고 차단 효과 미검증
- Wi-Fi↔모바일 데이터 전환, 장시간 실행, 화면 잠금/복귀 미검증
- 공용 DNS 직접 포착은 IP 단위 라우팅 부작용 때문에 기본 비활성화

## 9. 최종 완료 게이트

1. 최신 main 소스의 모든 내부 테스트 성공
2. 필터 다운로드 / 파싱 / 캐시 / 선택 프로필 준비 조건 검증
3. DNS 패킷 및 응답 검증
4. VPN 시작/중지/재시작 및 취소 경합 검증
5. UI 저장 옵션과 서비스 동작 연결 확인
6. 내부 개발 완료 후 안정 버전 및 versionCode 확정
7. 수동 APK 빌드, package/version, ZIP 무결성, SHA-256 확인
8. 실기기 수용 테스트는 CI와 구분해 별도 보고

개발 완료 전 중간 APK를 최종본으로 제공하지 않습니다. 검증되지 않은 기능을 완료로 표시하지 않습니다.


## 10. DNS 자체 테스트의 정확한 범위와 VPN 자동 복구

- Android VPN 앱 자신이 생성한 소켓은 기본적으로 자체 TUN 경로를 우회합니다. 기존의 앱 내부 UDP 소켓으로 가상 DNS 주소를 조회하는 방식은 실제 TUN 경로를 검증하지 못하므로 제거했습니다.
- 현재 자체 테스트는 로드된 차단 규칙이 `doubleclick.net`에 일치하는지, NXDOMAIN 응답 생성기가 RCODE 3을 만드는지, `example.com`을 차단하지 않는지 검사합니다. 네트워크/TUN 경로 시험으로 표시하지 않습니다.
- 서비스는 `START_STICKY`로 동작하고, Android가 서비스를 재생성하면서 null intent를 전달했을 때 이전 보호 상태가 활성인 경우에만 VPN 터널을 다시 구성합니다. 사용자가 중지한 경우는 자동 재시작하지 않습니다.
- 이 복구 정책은 CI 단위 테스트 대상이며 실기기 장시간 유지 여부는 아직 미검증입니다.


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


## WorkManager 백그라운드 필터 업데이트

- WorkManager 2.12.0을 추가해 6/12/24시간 또는 7일 주기의 네트워크 연결 조건부 업데이트를 예약합니다.
- 자동 업데이트를 끄면 고유 예약 작업을 취소하고, 다시 켜면 설정 간격으로 예약을 갱신합니다.
- 앱 실행 시 만료 필터 업데이트도 기존대로 유지합니다.
- 업데이트 Worker는 활성화된 기본/보안/Android 앱 광고 목록을 검증·갱신하며, 활성 VPN 서비스에 새 규칙을 다시 읽도록 요청합니다.
- 수동/백그라운드 업데이트 간 캐시 파일 경합을 막기 위해 필터 다운로드를 동기화했습니다.
- 설정 주기 검증 테스트 2개를 추가했습니다. 현재 인벤토리 12개 스위트 / 68개 메서드.


## Latest WorkManager verification

- [GitHub Actions run 43437792450](https://github.com/justinhblee-prog/guardian/actions/runs/43437792450): **success**
- Source commit: `5d7624fd445edadb1a1ed24a482b9e2d25f07d41`
- Android unit tests: **passed**; APK assembly was skipped because this was a push-triggered validation run.
- Test inventory: **12 suites / 68 test methods**.


## IPv6 업스트림 옵션 추가
- DNS 설정에 Cloudflare/Google/Quad9/AdGuard DNS의 IPv6 프리셋을 추가했습니다.
- AdGuard 기본 IPv6 주소를 고급 직접 DNS 포착 주소에 포함했습니다.
- IPv6 리터럴 검증 회귀 테스트를 추가했습니다. 네트워크별 IPv6 연결 가능 여부는 실기기에서 확인해야 합니다.


## 최신 인계 검증 — IPv6 DNS 프리셋
- [GitHub Actions run 43443209122](https://github.com/justinhblee-prog/guardian/actions/runs/43443209122): **success**
- 검증 소스 커밋: `f518711e559bfb8873128f82c07a1a157a5f2068`
- Android 단위 테스트 12개 스위트 / 68개 메서드 통과.
- push 실행이므로 APK 생성·패키지 확인·SHA-256·artifact 업로드는 실행하지 않았습니다.


## TCP/53 DNS implementation — source verified

- TCP/53 codec/session manager is integrated in the VPN packet loop for IPv4 and IPv6 base headers.
- Handles DNS length-prefix framing, ordered stream reassembly, bounded sessions/buffers, DNS filtering/upstream forwarding, segmented replies, and FIN/RST cleanup.
- CI run 43452443314 passed: 14 test suites / 88 JUnit methods; debug APK assembly, package identity and SHA-256 checks passed.
- Verified package remains `com.guardian.adblock` versionCode 15 / `0.7.2-dev`; push CI did not upload an artifact.
- Not a complete TCP implementation: retransmission/loss recovery, IPv6 extension headers and IP fragmentation are unsupported. Real Android device resolver behavior remains unverified.

- Protection tab now exposes immediate start/stop and filter quick settings; recovery action text matches its reconnect behavior.

- CI includes Android lint; the receiver export-flag lint error was fixed and the latest lint run passed.

- The DNS rule-engine self-test now explicitly disclaims live network-path and in-app ad-blocking verification.

- Added optional DNS-over-TLS upstream transport (port 853) with SNI and certificate hostname verification. External provider connectivity and on-device DNS resolution still require device validation.

- Added DoH upstream over TLS/443 with HTTP POST binary DNS messages, strict content-type/status validation, bounded Content-Length/chunked parsing and TLS hostname verification. External provider connectivity remains unverified.

- DoH settings persistence/visibility was corrected and verified in CI run 43459666939.

- IPv6 TCP SYN-to-DNS-response integration is now covered by regression tests and CI.


## 2026-11-14 — 최신 개발 인계 (v0.7.4-dev)

- 최신 검증 소스 커밋: `66ca82b38ce989a7e45be4b472d0933034a05cb2`
- Android 패키지: `com.guardian.adblock`, versionCode `17`, versionName `0.7.4-dev`.
- WorkManager 재시도 효율 개선: 각 필터 소스의 캐시 유효성/갱신 시각을 독립 확인하고, 한 선택 목록의 실패 재시도 시 이미 성공한 최신 캐시를 다시 다운로드하지 않습니다.
- 회귀 테스트 추가: 캐시 누락, 신선한 캐시 재시도, 만료 경계, 미래 시각, 최소 업데이트 간격.
- [CI 43570828960](https://github.com/justinhblee-prog/guardian/actions/runs/43570828960): 단위 테스트, lint, Debug APK 조립, 패키지/버전 확인, SHA-256 생성 성공.
- 검증 APK SHA-256: `d60df611f004d5114d9e1aaeb832a5837ce4287c5dd1b7c51c5909c8a4cfb577`. 일반 push CI이므로 artifact 업로드는 생략했습니다.
- APK는 debug 서명 검증 빌드이며 최종 설치 패키지 배포가 아닙니다. 아직 실기기 VPN 승인/라우팅, Wi-Fi↔모바일 전환, 장시간 안정성, 앱별 제외 효과 및 실제 앱 광고 제거를 물리 기기에서 확인하지 않았습니다.
- 계속 진행할 작업: UI/옵션 완성도 점검, VPN/DNS 재연결 및 예외 경로 검증, 전체 회귀와 문서/패키지 통합, 최종 APK 수동 배포 workflow 실행 및 산출물 무결성 확인.


## 2026-11-14 — 최신 개발 인계 (v0.7.5-dev)

- 최신 검증 소스 커밋: `beed7f96e35f714ff9263a26a9f3fe1c4efc0918`
- Android package: `com.guardian.adblock`, versionCode `18`, versionName `0.7.5-dev`.
- IPv6 resolver normalization now safely handles nullable `Inet6Address.hostAddress`.
- Home dashboard explicitly explains that app-originated DoH/DoT bypass traffic is not intercepted, separate from Guardian's own DoH/DoT upstream modes.
- [CI 43571389815](https://github.com/justinhblee-prog/guardian/actions/runs/43571389815): unit tests, lint, debug APK assembly, package/version and SHA-256 checks passed.
- CI APK SHA-256: `f756ab65ce8d74cc7a7da45457b0cc2987b83acc83e9c993eab0a3f624492e1a`. Push CI skipped artifact upload.
- Remaining acceptance boundary: physical Android installation, VPN permission and DNS routing, Wi-Fi/mobile handoff, long-run stability, app exclusions and actual ad-removal effectiveness remain unverified.


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
