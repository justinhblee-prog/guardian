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
