# Guardian AdBlock Android — 개발/릴리스 상태

기준 버전: **0.7.2-dev**  
기준일: 2026-11-13  
저장소: https://github.com/justinhblee-prog/guardian

## 기존 차단 실패 원인

이전 0.6.5-dev는 기본 차단 규칙이 10개 도메인뿐이었고, 공식 광고 필터 목록 다운로드 및 Adblock 규칙 해석이 없었습니다. VPN 상태가 켜져 있어도 대부분의 광고 도메인이 차단 목록에 없었습니다.

## 구현 현황

| 영역 | 현재 구현 |
|---|---|
| DNS 보호 | Android VpnService, IPv4/IPv6 UDP 및 TCP DNS |
| DNS 응답 | NXDOMAIN / SERVFAIL, upstream transaction/question 검증, truncated upstream UDP의 TCP fallback 및 클라이언트 응답 1232바이트 제한/TC 응답 |
| 필터 프로필 | AdGuard DNS Filter, 1Hosts Lite, OISD Big, HaGeZi Normal, HaGeZi Pro |
| 보안 목록 | 선택형 HaGeZi Threat Intelligence |
| 앱 광고 SDK 목록 | 선택형 AWAvenue Ads Rule |
| 규칙 엔진 | Adblock 도메인 앵커, 예외, hosts, important, 출처별 badfilter, 사용자 block/allow |
| 캐시 | 크기/규칙 수 검증, 원자적 교체, 이전 정상 캐시 보존, 서비스 시작 직전 실제 파일 재검증 |
| DNS 업스트림 | Cloudflare, Google, Quad9, AdGuard DNS, AdGuard Family DNS, 사용자 지정 IPv4/IPv6 |
| 직접 DNS | 공용 DNS 직접 포착 고급 옵션(기본 비활성화), 시스템 가상 DNS |
| 자동 업데이트 | 앱 실행 시 만료 확인 + WorkManager 네트워크 연결 조건부 6/12/24시간 또는 7일 주기 백그라운드 갱신 |
| 앱 제어 | 설치 앱 검색 및 VPN 보호 제외 |
| 사용자 규칙 | 차단/허용 도메인 |
| 활동/진단 | DNS 활동, 통계, 최근 요청, 진단, 기록 초기화 |
| 자체 테스트 | 활성 VPN에서 활성 규칙 매칭 및 NXDOMAIN 생성 엔진 자체 테스트 |
| UI | 홈, 보호, 활동, 필터, 설정의 5개 화면 |

앱 실행 시 만료 여부를 확인하고, WorkManager가 네트워크 연결 시 백그라운드 갱신을 예약합니다. Android 전원 관리에 따라 실행 시각은 지연될 수 있습니다.

공용 DNS 직접 포착은 IP 단위 라우팅입니다. DNS 서버 IP로 향하는 비DNS 연결도 TUN에 들어올 수 있으며 앱은 이를 중계하지 않습니다. 이 때문에 기본값은 꺼져 있고 고급 옵션으로만 켤 수 있습니다.

## 최신 내부 검증

- [GitHub Actions 43434091375](https://github.com/justinhblee-prog/guardian/actions/runs/43434091375): **Success**
- 소스 커밋: `419356a1ef43ba20c33256b353566da7f42b8f61`
- Android unit-test 단계: **통과**
- 테스트 인벤토리(이후 변경 전 기록): **12개 스위트 / 68개 JUnit 테스트 메서드**
- APK 생성/업로드: push CI에서는 의도적으로 생략
- 직전 파서 오류 원인: `$important,badfilter`처럼 쉼표로 구분된 modifier에서 badfilter 토큰을 인식하지 못했습니다. modifier를 분리해 각각 인식하도록 수정했고 59개 테스트가 통과했습니다. 이후 직접 DNS 안전 기본값 테스트를 추가해 현재 인벤토리는 65개입니다.

## 남은 검증 및 제한

- 실기기 설치, VPN 권한, DNS 라우팅 및 실제 광고 차단 효과 미검증
- Wi-Fi↔모바일 데이터 전환, 화면 잠금, 장시간 실행 미검증
- DoH/DoT 미지원; TCP/53은 순서형 DNS 스트림 중심의 제한 구현이며 재전송/손실 복구는 미지원
- IPv6 확장 헤더 및 IP fragment 재조립 미지원
- 웹페이지 요소 숨김 및 앱 내부/first-party 광고 제거 미지원
- 공용 DNS 직접 포착의 실기기 라우팅 부작용 검증 필요

## APK 생성 및 릴리스 정책

- 모든 소스 push에서 CI가 Android 테스트와 debug APK 조립·패키지 식별·SHA-256 검증을 수행합니다.
- 일반 push에서는 APK artifact를 공개하지 않습니다. 수동 workflow 실행 또는 `[final-apk]` 커밋에서만 설치 APK artifact를 업로드합니다.
- 현재 최신 산출물은 0.7.2-dev 설치 가능 debug APK이며, Play Store production 서명본은 아닙니다.
- CI 성공은 실기기 VPN 라우팅이나 실제 광고 차단 효과를 증명하지 않습니다.


## DNS 테스트 정확성 및 서비스 복구

- 앱 자체 소켓은 Android VPN TUN을 우회하므로, 가상 DNS IP에 앱 소켓으로 질의하는 기존 자체 테스트 방식은 제거했습니다.
- 현재 테스트는 실제 네트워크 경로라고 주장하지 않고 활성 차단 규칙 매칭, NXDOMAIN 생성, 정상 도메인 비차단을 확인합니다.
- VPN 서비스는 `START_STICKY` 및 persisted-active 확인을 통해 OS 프로세스 회수 후 재생성 시 보호를 복구하도록 변경했습니다. 사용자가 중지한 상태는 복구하지 않습니다.
- 최신 테스트 인벤토리: 12개 suite / 68개 메서드. 실기기 복구 안정성은 아직 미검증입니다.


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


## 백그라운드 필터 업데이트

WorkManager 2.12.0을 사용해 6/12/24시간 또는 7일 주기의 네트워크 연결 조건부 필터 갱신을 추가했습니다. 자동 업데이트 비활성화 시 예약 작업을 취소하며, 기본/활성화된 선택 필터를 검증·갱신합니다. 업데이트 중인 캐시 파일은 수동/백그라운드 작업 간 동시 교체되지 않도록 직렬화했습니다. 주기 설정 정책 단위 테스트 2개를 추가했습니다.


## Latest WorkManager verification

- [GitHub Actions run 43437792450](https://github.com/justinhblee-prog/guardian/actions/runs/43437792450): **success**
- Source commit: `5d7624fd445edadb1a1ed24a482b9e2d25f07d41`
- Android unit tests: **passed**; APK assembly was skipped because this was a push-triggered validation run.
- Test inventory: **12 suites / 68 test methods**.


## 최신 DNS 설정 변경
Cloudflare, Google, Quad9, AdGuard DNS IPv6 업스트림 프리셋 및 AdGuard 기본 IPv6 직접 포착 경로를 추가했습니다. IPv6 리터럴 검증 테스트를 포함했으며 CI 결과는 최신 실행 확인 후 기록합니다.


## 최신 CI — IPv6 DNS 프리셋
- 실행: [43443209122](https://github.com/justinhblee-prog/guardian/actions/runs/43443209122) — **성공**
- 소스 커밋: `f518711e559bfb8873128f82c07a1a157a5f2068`
- Android 테스트: 12개 스위트 / 68개 JUnit 메서드 통과
- APK 빌드: push 실행이라 생략. 최종 개발 완료 전 수동 APK 빌드를 실행하지 않음.


## CI 빌드 검증 정책 보강

- 모든 push에서 Android 단위 테스트 이후 debug APK를 실제 조립하고 package/version 및 SHA-256을 검증하도록 workflow를 변경했습니다.
- push 실행은 APK를 artifact로 공개하지 않으며, 수동 릴리스 실행만 APK를 업로드합니다.
- 최신 테스트 기준: 12개 스위트 / 70개 JUnit 메서드.
- 변경 커밋 `cc9f8bc0530015b16200f2ee3b141610fd9dd22d`의 빌드 검증 CI 실행 결과는 진행 중입니다.


## 최종 APK 빌드 산출물 — 2026-11-13
- [최종 빌드 실행](https://github.com/justinhblee-prog/guardian/actions/runs/43445545156): **success**
- 최종 소스 커밋: `29a10e46f27dd1bafdde72a62ddd984b9965a537`
- 테스트: **12개 스위트 / 70개 테스트 통과**
- APK 조립 및 패키지 식별 검증: 성공 (`com.guardian.adblock`, versionCode 14, `0.7.1-dev`)
- APK SHA-256: `4ee62c89678f0cef2b75d96a019a5700064538efaff4c82e9768a9bbf28ddb75`
- GitHub artifact: `Guardian-AdBlock-Android-APK-v0.7.1-dev`
- APK ZIP 무결성: 로컬 추출 후 `unzip -t` 통과
- 릴리스 판정: 설치 가능한 debug APK 산출 완료. 단, 실기기 설치·VPN 권한·Wi-Fi/모바일 전환·실제 광고 차단률은 아직 미검증이며, 완전한 AdGuard 대체품이나 모든 광고 차단을 보장하지 않습니다.


## 0.7.2-dev DNS UDP 응답 크기 안전성

- IPv6 최소 MTU를 고려해 UDP DNS payload 상한을 1232바이트로 제한합니다.
- 업스트림 응답이 상한을 넘으면 검증된 질문 섹션을 보존한 TC 응답을 생성해 TUN에 과대 패킷을 쓰지 않습니다.
- 클라이언트 방향 TCP/53 연결·프레이밍·순서형 스트림 처리를 소스에 추가했습니다. 다만 TCP 재전송/손실 복구는 구현하지 않아 네트워크 손실 상황까지 보장하지 않으며 실기기 resolver 재시도는 미검증입니다.
- 추가 회귀 테스트 2개 포함, 전체 12개 스위트 / 72개 테스트.


## 최종 0.7.2-dev APK — 2026-11-13

- [GitHub Actions 최종 실행](https://github.com/justinhblee-prog/guardian/actions/runs/43447121533): **success**
- 소스 커밋: `50e768e253ae3ee7d77d3cdc07b5f23ccd970eab`
- 테스트: **12개 스위트 / 72개 JUnit 테스트 통과**
- APK: `com.guardian.adblock`, versionCode 15, `0.7.2-dev`
- APK 크기: 2,978,452 bytes
- SHA-256: `a1d41cf8937b2c09f7c4ca8d0b02ddf252e95f935b1cee3ed1d485c5470f55d8`
- GitHub artifact: `Guardian-AdBlock-Android-APK-v0.7.2-dev` (ID `14049565251`)
- Artifact ZIP 무결성 검사 및 APK 체크섬 독립 확인 완료.
- 릴리스 유형: 설치 가능한 debug 서명 APK. Play Store production 서명 빌드는 아닙니다.
- 실기기 VPN 권한/라우팅, 네트워크 전환 및 실제 광고 차단률은 별도 미검증입니다.


## TCP/53 소스 구현 및 CI 검증

- TCP/53 codec 및 bounded session manager를 VPN 패킷 루프에 연결했습니다.
- SYN/ACK 시퀀스 처리, IPv4/IPv6 checksum, 분할 DNS 프레임 재조립, 다중 질의, DNS 응답 분할, FIN+payload 처리를 회귀 테스트합니다.
- [GitHub Actions 43452443314](https://github.com/justinhblee-prog/guardian/actions/runs/43452443314): **success** — 15 suites / 92 JUnit tests; debug APK assembly, package identity and SHA-256 checks also passed.
- 빌드된 검증 APK는 versionCode 15 / 0.7.2-dev이며 push CI 정책상 artifact 업로드는 생략했습니다. 이전 수동 배포 artifact는 TCP/53 변경 전 소스입니다.
- 제한: 동일 요청 세그먼트의 캐시 응답 재전송은 지원하지만 자체 재전송 타이머·부분 ACK 복구, IPv6 extension header, IP fragmentation, DoH/DoT, 실기기 통신 및 실제 광고 차단률은 미검증/미지원입니다.


## Protection UI

- Protection 탭에 실시간 보호 시작/중지, 자동 업데이트, 악성코드·피싱, Android 광고 SDK, 고급 DNS 포착 스위치를 추가하고 필터/앱별 제외 관리로 바로 이동할 수 있도록 했습니다.
- Push CI run 43457043232 passed with 14 suites / 86 tests and debug APK assembly/package/SHA-256 verification.

- Android lint is now part of CI; latest run 43457043232 passed lint with the broadcast receiver export flag corrected.

- DNS self-test is explicitly labeled as a local rule-engine check, not a live network-route or in-app ad-blocking test.


## DNS upstream transport

- DNS 업스트림 전송 방식을 UDP/53, TCP/53, DNS-over-TLS/853 중 선택할 수 있습니다. Cloudflare·Google·Quad9·AdGuard 계열은 TLS 호스트 이름을 자동 선택하고, 사용자 지정 IP는 인증서 호스트 이름을 별도로 입력할 수 있습니다.
- DoT endpoint handshake is implemented with SNI and certificate hostname verification; real provider connectivity/device testing is not yet verified. App-originated DoH/DoT bypass traffic remains outside the supported filter path.
