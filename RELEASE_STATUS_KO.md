# Guardian AdBlock — 개발/릴리스 상태

기준 버전: **0.7.8-dev**  
기준일: 2026-11-15  
저장소: https://github.com/justinhblee-prog/guardian

## 현재 기준 — 0.7.8-dev

- Android package: `com.guardian.adblock`, versionCode `21`, versionName `0.7.8-dev`.
- 기준 main 커밋: `e168b8658af6f10a9fe56b999760251ec5e2be13`.
- 기존 최종 artifact는 0.7.7-dev debug APK입니다. 0.7.8-dev의 새 최종 APK/source ZIP artifact가 생성되었다고 간주하지 않습니다.
- PR #25 Android CI run `43690905433` 성공: 단위 테스트, lint, assembleDebug, API 35 에뮬레이터 설치·실행, package/version/SHA-256.
- PR #38 DNS-over-TCP sequence wraparound 회귀 테스트 run `43694693871` 성공 후 main 병합.
- PR #41 Android CI run `43696306115` 전체 성공: 단위 테스트, lint, debug APK, 에뮬레이터 설치·실행, package/version/SHA-256. 미병합.
- PR #43 Android CI run `43696645579` 및 Production Release 설정 run `43696645556` 성공. 설정 검증은 통과했으며 production signing은 운영 secrets/keystore 미설정으로 의도적 skipped. 미병합.
- PR #44 최종 License API CI run `43697583427` 성공: Node 구문 검사, Docker Compose 검증, PostgreSQL 스키마 초기화, 암호화/HTTP/PostgreSQL 테스트. 미병합.
- PR #46 Filter Distribution CI run `43698002553` 성공: publisher/updater/distribution Node 테스트 및 컨테이너 설정 검증. 미병합.
- PR #47 Android CI run `43699497055` 성공: 단위 테스트, lint, debug APK, API 35 emulator 설치·실행, package/version/SHA-256. 미병합.
- PR #48 Android CI run `43698680084` 성공: 단위 테스트, lint, debug APK, API 35 emulator 설치·실행, package/version/SHA-256. 미병합.
- PR #49 Android CI run `43699268819` 성공: 단위 테스트, lint, debug APK, API 35 emulator 설치·실행, package/version/SHA-256. 미병합.
- PR #50 License API CI run `43700615872` 성공: PostgreSQL 공유 rate-limit 동시성/만료 bucket cleanup 테스트, API/DB timeout 및 nosniff 테스트 포함. 미병합.
- PR #16/#40은 각각 최신 main 재적용본 #41/#43으로 대체되어 닫혔습니다.
- main에 병합된 변경과 PR 브랜치의 미병합 변경은 구분합니다. PR CI 성공을 main 통합이나 운영 배포 성공으로 표시하지 않습니다.
- CI 검증은 실기기 VPN/TUN 라우팅, 실제 광고 제거율, Wi-Fi↔모바일 전환 또는 장시간 안정성을 입증하지 않습니다.

## 현재 미완료 범위

- PR #41: 만료된 서명 토큰은 FREE로 유지하면서도 기기 등록 해제를 가능하게 하는 변경. CI 성공, 미병합.
- PR #43: main push 릴리스 설정 검증 및 수동 production signing gate. CI 성공, 미병합.
- PR #44: License API HTTP timeout/header 제한 및 nosniff 보안 헤더. CI 성공, 미병합.
- PR #46: signed filter publisher source-lock cleanup 소유권 확인. CI 성공, 미병합.
- 운영 keystore, release secrets, manifest/license 공개키, HTTPS API/distribution endpoint, PostgreSQL, DNS/TLS 도메인은 실제 운영 환경에 설정·검증되지 않았습니다.
- 실기기 Android VPN/DNS 수용 테스트는 추후 수행합니다.
- Windows 클라이언트/설치 프로그램과 Windows 네트워크 변경·복구 실환경 검증은 마지막 단계로 보류합니다.

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
- [GitHub Actions 43452443314](https://github.com/justinhblee-prog/guardian/actions/runs/43452443314): **success** — 16 suites / 99 JUnit tests; debug APK assembly, package identity and SHA-256 checks also passed.
- 빌드된 검증 APK는 versionCode 15 / 0.7.2-dev이며 push CI 정책상 artifact 업로드는 생략했습니다. 이전 수동 배포 artifact는 TCP/53 변경 전 소스입니다.
- 제한: 동일 요청 세그먼트의 캐시 응답 재전송은 지원하지만 자체 재전송 타이머·부분 ACK 복구, IPv6 extension header, IP fragmentation, DoH/DoT, 실기기 통신 및 실제 광고 차단률은 미검증/미지원입니다.


## Protection UI

- Protection 탭에 실시간 보호 시작/중지, 자동 업데이트, 악성코드·피싱, Android 광고 SDK, 고급 DNS 포착 스위치를 추가하고 필터/앱별 제외 관리로 바로 이동할 수 있도록 했습니다.
- Push CI run 43459666939 passed with 14 suites / 86 tests and debug APK assembly/package/SHA-256 verification.

- Android lint is now part of CI; latest run 43459666939 passed lint with the broadcast receiver export flag corrected.

- DNS self-test is explicitly labeled as a local rule-engine check, not a live network-route or in-app ad-blocking test.


## DNS upstream transport

- DNS 업스트림 전송 방식을 UDP/53, TCP/53, DNS-over-TLS/853 중 선택할 수 있습니다. Cloudflare·Google·Quad9·AdGuard 계열은 TLS 호스트 이름을 자동 선택하고, 사용자 지정 IP는 인증서 호스트 이름을 별도로 입력할 수 있습니다.
- DoT endpoint handshake is implemented with SNI and certificate hostname verification; real provider connectivity/device testing is not yet verified. App-originated DoH/DoT bypass traffic remains outside the supported filter path.


## Encrypted DNS upstream modes

- 업스트림은 UDP/53, TCP/53, DNS-over-TLS/853, DNS-over-HTTPS/443 중 선택할 수 있습니다. DoH는 HTTP POST의 application/dns-message 본문을 사용하며 Content-Length와 chunked 응답을 제한된 크기로 검증합니다.
- HTTP parser rejects redirects, wrong media types, truncated/oversized bodies, and supports bounded chunked responses. TLS certificate hostname verification is enabled.
- CI validates build, lint and codecs; real provider TLS connectivity/on-device resolver operation remains unverified. App-originated DoH/DoT bypass is not intercepted.

- Fixed the saved-DoH-mode settings screen so TLS host controls remain visible after reopening; CI run 43459666939 passed.

- Added an end-to-end IPv6 DNS-over-TCP session regression test; latest CI run 43459666939 passed 16 suites / 99 tests, lint, APK identity and SHA-256 verification.


## 최신 CI 확인 — 2026-11-13

- [Run 43525073201](https://github.com/justinhblee-prog/guardian/actions/runs/43525073201) — **success**
- Android unit tests / lint / debug APK assemble / package identity / SHA-256 검증 성공
- 검증 소스: `827198814d0b4441afe72c2ddb81b6d1a41a8f19`
- 산출 APK 메타데이터: `com.guardian.adblock`, versionCode 15, `0.7.2-dev`
- SHA-256: `c40229d5c3a25173092233b2e23ace19d9c3d6f831837d6d6684c20f156495ee`
- 이 push 실행에서는 수동 배포 artifact 업로드가 skip되어 별도 설치 파일을 배포하지 않았습니다.
- CI 빌드 성공은 실기기 동작 검증이나 실제 광고 차단 성공을 의미하지 않습니다.


## 2026-11-14 — TCP DNS 세션 회귀 수정 및 빌드 검증

- 변경 커밋: `4c8511770039665da0eb61b018c460d86a8031e2`
- GitHub Actions: [run 43530335902](https://github.com/justinhblee-prog/guardian/actions/runs/43530335902) — **success**
- Android JUnit 단위 테스트: 통과
- Android lintDebug: 통과
- assembleDebug: 통과
- 패키지 확인: `com.guardian.adblock`, versionCode `15`, versionName `0.7.2-dev`
- 생성 APK SHA-256: `071fd9fc18288ba3144c747fb2a92beb9a93e3f58557ee49e9b086811f97f7ec`
- APK artifact 업로드: push 검증 정책에 따라 생략
- 이번 회귀 수정: 이미 연결된 DNS TCP 세션에 지연 도착한 동일 SYN이 들어와도 세션의 sequence state를 초기화하지 않도록 수정하고, 동일 SYNACK 재응답 및 이후 DNS 질의 처리를 검증하는 테스트를 추가했습니다. TCP SYN/FIN/RST의 모순된 조합도 거부합니다.
- Android 비공개 DNS 모드가 자동/호스트명으로 설정된 경우 DNS 필터 우회 가능성을 화면에 안내합니다.
- 위 결과는 CI 내부 검증이며, 실기기 설치·VPN 라우팅·실제 광고 차단 성공을 의미하지 않습니다.


## 2026-11-14 — 필터 다운로드 리디렉션 보안 강화 및 전체 CI 검증

- Source commit: `7988ced0500324df91900017993882e7cb10893b`
- GitHub Actions: [run 43531095663](https://github.com/justinhblee-prog/guardian/actions/runs/43531095663) — **success**
- Android unit tests: 통과 (**18개 테스트 스위트 / 110개 JUnit 테스트 메서드**)
- Android lintDebug: 통과
- assembleDebug: 통과
- APK package: `com.guardian.adblock`, versionCode `15`, versionName `0.7.2-dev`
- CI 생성 APK SHA-256: `902b8045209dc0d73ae34bb6d8740673b7f3fc45df0da9d5f6a6d3331826aada`
- APK artifact upload: push 검증 정책에 따라 생략
- 필터 다운로드는 HTTPS 전용 수동 리디렉션을 사용하며 HTTP 다운그레이드, URL 사용자정보, 미지원 리디렉션 상태 및 5회 초과 체인을 거부합니다. 관련 정책 단위 테스트 6개 추가.
- CI 성공은 실기기 설치, VPN 라우팅 또는 실제 광고 차단 효과 검증을 의미하지 않습니다.


## 2026-11-14 최신 내부 빌드 검증

- GitHub Actions: https://github.com/justinhblee-prog/guardian/actions/runs/43531690119 — **success**
- 검증 소스 커밋: `1616c8fdd11c92c1c982c3b24c4661242433d366`
- Android 단위 테스트: **통과** (`testDebugUnitTest`)
- Android 린트: **통과** (`lintDebug`)
- Debug APK 조립: **통과** (`assembleDebug`)
- 패키지/버전: `com.guardian.adblock`, versionCode `15`, versionName `0.7.2-dev` 확인
- APK 존재 및 SHA-256 산출: **통과**
- 설정 화면의 앱 버전 표기를 고정 문자열에서 `BuildConfig.VERSION_NAME`으로 변경하고 BuildConfig 생성을 활성화했습니다.
- 이번 push CI는 설치 APK artifact 업로드를 생략했습니다. 실제 기기 설치·VPN 라우팅·광고 차단 효과는 여전히 미검증입니다.


## 2026-11-14 DNS-over-TCP 재전송 안정성 검증

- GitHub Actions: https://github.com/justinhblee-prog/guardian/actions/runs/43533452981 — **success**
- 검증 소스 커밋: `a76491c4846f4eeb01bc0fc9cf2c0dd1e01fb183`
- Android 단위 테스트(`testDebugUnitTest`): **통과**
- Android 린트(`lintDebug`): **통과**
- Debug APK 조립(`assembleDebug`): **통과**
- 패키지/버전: `com.guardian.adblock`, versionCode `15`, versionName `0.7.2-dev`
- CI APK SHA-256: `b7a35a98f76ce20aa272358c9b0d2f5584a8ec7dd6f88308427840fbe7613b89`
- DNS-over-TCP 마지막 데이터/FIN 응답이 유실된 경우, 클라이언트의 정확한 재전송에 기존 응답 패킷을 재전송하도록 수정했습니다. 서버 FIN에 대한 ACK를 받을 때까지 half-close 세션을 유지합니다.
- 재전송 회귀 테스트 2개를 추가했습니다.
- push CI에서는 APK artifact 업로드를 생략했습니다. 실기기 설치, VPN 라우팅, 실제 광고 차단 효과는 검증되지 않았습니다.


## 2026-11-14 보호 상태 진단 정확도 개선

- VPN 터널 활성화만으로 DNS 필터링이 실제 진행 중이라고 단정하던 문구를 제거했습니다.
- 실제 관측 DNS 질의·차단·업스트림 전달·실패 건수를 표시하도록 변경했습니다.
- 질의가 0건이면 트래픽 미관측 상태로 표시하고, 앱/웹 트래픽을 발생시켜 확인하도록 안내합니다.
- Android 비공개 DNS 자동/호스트명 모드에서 암호화 DNS 우회 가능성을 보호 상세 상태에도 표시합니다.
- 진단 로직 조합에 대한 단위 테스트 5개를 추가했습니다. CI 검증 대기 중이며 실기기 광고 제거 검증은 포함하지 않습니다.


## 2026-11-14 보호 상세 상태 갱신 경로 수정

- 추가 코드 점검에서 보호 화면 최초 진입 시에는 관측 DNS 진단을 표시하지만, 이후 갱신에서는 저장된 일반 서비스 문구가 진단을 덮어쓸 수 있는 문제를 발견했습니다.
- 보호 중에는 실제 DNS 질의·차단·전달·실패 건수와 비공개 DNS 모드를 기준으로 상세 문구를 갱신하도록 수정했습니다.
- 보호가 꺼진 경우에는 VPN 권한 거부 등 구체적인 서비스 오류 문구를 유지합니다.
- 활성/비활성 갱신 경로 회귀 테스트 2개를 추가했습니다.
- 직전 진단 커밋 CI run 43535030810은 단위 테스트, lint, debug APK 조립, package/version, SHA-256 검증 모두 성공했습니다. 후속 수정 커밋의 CI는 별도 검증 예정입니다.


## 2026-11-14 — 다중 탭 보호 시작/권한 거부 크래시 수정

- 수정 커밋: `019a4bdf576e56c0f893b68822efcc4c77846c41` (이전 관련 수정: `f22cc99d02b80d38916bafaa3fe943be581a68c3`)
- GitHub Actions: [run 43563225658](https://github.com/justinhblee-prog/guardian/actions/runs/43563225658) — **success**
- Android 단위 테스트, `lintDebug`, `assembleDebug`, 패키지 식별 및 APK SHA-256 확인 단계 모두 성공
- 패키지: `com.guardian.adblock`, versionCode `16`, versionName `0.7.3-dev`
- Home 탭 전용 lateinit 상태 뷰가 아직 생성되지 않은 상태에서 Protection 등 다른 탭에서 보호 시작 또는 VPN 권한 거부를 처리할 때 발생할 수 있는 크래시를 수정했습니다.
- 필터 캐시 미준비 시 필터 탭을 먼저 생성한 뒤 안내하도록 변경했습니다. 보호 시작/실패/권한 거부 메시지는 현재 탭에 존재하는 UI만 갱신합니다.
- `refreshStatus()`도 Home 전용 뷰의 초기화 여부와 현재 탭을 확인하며, Protection 화면은 별도 상태 위젯을 갱신합니다.
- 이 push 실행은 수동 배포 전용 APK artifact 업로드를 skip했습니다. CI 조립 성공은 이 커밋의 APK가 사용자에게 배포되었다는 뜻이 아닙니다.
- 실기기 VPN 권한, 실제 DNS 라우팅, 앱별 동작 및 광고 제거 효과는 여전히 미검증입니다.


## 2026-11-14 — 보안 필터 즉시 재적용 회귀 수정

- Source commit: `d2e3689212280e5149c9b44012285edf323a971d`
- GitHub Actions: [run 43567779995](https://github.com/justinhblee-prog/guardian/actions/runs/43567779995) — **success**
- Android unit tests: **passed** (전체 테스트 단계 성공)
- Android `lintDebug`: **passed**
- Android `assembleDebug`: **passed**
- APK package/version: `com.guardian.adblock`, versionCode `16`, versionName `0.7.3-dev` — verified
- APK existence and SHA-256 generation: **passed**
- 보안 필터를 설정 화면에서 켜거나 끌 때 활성 VPN 규칙을 즉시 다시 불러오도록 수정했습니다. 캐시가 없으면 다운로드를 시작하고, 캐시가 있으면 VPN을 중단하지 않고 현재 규칙을 재적용합니다.
- 일반 push 검증이므로 artifact 업로드는 의도적으로 skip되었습니다. 이 CI 결과만으로 새 APK 파일이 배포된 것은 아닙니다.
- 실기기 VPN/DNS 경로와 실제 광고 제거 효과는 여전히 미검증입니다.


## 2026-11-14 — 보안 필터 중복 재적용 제거 및 검증

- Source commit: `170b4e96371b9f4072570ac06fb2e4d69f8f173e`
- GitHub Actions: [run 43568131394](https://github.com/justinhblee-prog/guardian/actions/runs/43568131394) — **success**
- Android unit tests, `lintDebug`, `assembleDebug`, package/version 검사 및 APK SHA-256 생성: **all passed**
- 패키지: `com.guardian.adblock`, versionCode `16`, versionName `0.7.3-dev`
- 보안 필터 캐시가 없어 다운로드가 필요한 경우 다운로드 전에 중복 VPN 규칙 재적용 요청을 보내던 경로를 제거했습니다. 캐시가 있는 경우에는 즉시 재적용하고, 다운로드가 필요한 경우에는 다운로드 완료 후 재적용합니다.
- 일반 push이므로 artifact 업로드는 skip되었습니다. 새 APK 배포는 아닙니다.
- 실기기 VPN/DNS 및 실제 광고 차단 효과는 미검증입니다.


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


## 2026-11-14 — DNS 경로 제한 규칙 과잉 차단 회귀 수정

- `83501c1`: `||domain^/path` 및 `||domain^suffix`처럼 URL 경로·구분자 뒤 조건이 붙은 규칙을 DNS 전체 도메인 차단으로 확대하지 않도록 파서에서 제외했습니다.
- `d5fea43`: 경로 조건, 후행 조건, 예외 규칙, 정상 호스트 앵커를 함께 검증하는 회귀 테스트를 추가했습니다. 기대 결과는 정상 호스트 규칙만 차단 목록에 남고 나머지 3개는 무시되는 것입니다.
- [GitHub Actions run 43569255957](https://github.com/justinhblee-prog/guardian/actions/runs/43569255957) 성공: Android 단위 테스트, Lint, Debug APK 조립, 패키지 식별/버전 검사 및 APK 존재·SHA-256 검사가 통과했습니다. 일반 push이므로 APK artifact 업로드는 생략되었습니다.
- 자동화 CI 검증이며 실기기 네트워크·실제 광고 제거 효과를 입증하지는 않습니다.


## 2026-11-14 — 필터 업데이트 재시도 중 중복 다운로드 방지 / v0.7.4-dev

- `12c3cbf`: 백그라운드 Worker가 기본·보안·Android 앱 광고 목록의 캐시 유효성과 각 목록의 마지막 갱신 시각을 개별 판단하도록 변경했습니다. 선택 프로필이 바뀌었거나 캐시가 없거나 설정 주기를 넘긴 목록만 갱신합니다. 선택 목록 하나가 실패해 재시도하더라도 이미 성공한 다른 목록을 다시 내려받지 않습니다.
- 회귀 테스트에서 캐시 없음/메타데이터 없음, 주기 내 재시도, 갱신 주기 도달, 미래 시각, WorkManager 최소 간격을 확인했습니다.
- 버전: `com.guardian.adblock`, versionCode `17`, versionName `0.7.4-dev`.
- [GitHub Actions run 43570828960](https://github.com/justinhblee-prog/guardian/actions/runs/43570828960): 성공. Android 단위 테스트, Android lint, Debug APK 조립, package/version 검사 및 APK SHA-256 생성 모두 통과했습니다.
- CI 검증 APK SHA-256: `d60df611f004d5114d9e1aaeb832a5837ce4287c5dd1b7c51c5909c8a4cfb577` (약 2.9MB). 일반 push이므로 설치용 artifact 업로드는 건너뛰었습니다.
- 해당 APK는 CI 검증용 debug 빌드입니다. 최종 배포 APK가 아니며 실기기 VPN/DNS·실제 광고 제거 효과는 아직 검증하지 않았습니다.


## 2026-11-14 — DNS 주소 안전성 및 보호 범위 UI 문구 정리 / v0.7.5-dev

- `df7cfbd`: IPv6 주소 정규화 결과의 `hostAddress`가 nullable일 수 있는 경로를 안전하게 처리했습니다. Kotlin 컴파일 경고도 제거했습니다.
- `39eec62`: 홈 대시보드의 보호 범위 설명을 명확히 했습니다. Guardian 업스트림 DoT/DoH 지원과 앱 자체 암호화 DNS 우회 요청 미지원이 혼동되지 않도록, 앱이 직접 사용하는 DoH/DoT는 필터링하지 않는다고 구체화했습니다.
- 버전: `com.guardian.adblock`, versionCode `18`, versionName `0.7.5-dev`.
- [GitHub Actions run 43571389815](https://github.com/justinhblee-prog/guardian/actions/runs/43571389815): 성공. Android 단위 테스트, lint, Debug APK 조립, 패키지 식별/버전 및 SHA-256 검사 통과.
- CI 검증 APK SHA-256: `f756ab65ce8d74cc7a7da45457b0cc2987b83acc83e9c993eab0a3f624492e1a`. 일반 push이므로 artifact 업로드는 생략했습니다.
- 실기기 VPN·실제 광고 제거 효과는 여전히 미검증입니다.


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


## 2026-11-14 기준 정정 — v0.7.7-dev

이 절은 이전 문서에 남아 있는 v0.7.3-dev 및 v0.7.4-dev 기록보다 우선합니다.

- 최신 소스 커밋: `c8f60ef3f7f5af44e332defe570d4f475951162a` (릴리스 문서 정정 커밋)
- APK를 실제 빌드한 소스 커밋: `94bab42e1a9dd7e10b61fad52f2546fc075dbdcf`
- APK: `com.guardian.adblock`, versionCode `20`, versionName `0.7.7-dev`
- APK SHA-256: `4797b47bad0c92574e0b7f9b8bf347960c46e688c95be43ee6158b2d1a4a93e7`
- GitHub Actions: https://github.com/justinhblee-prog/guardian/actions/runs/43572937308
- 자동화 검증: Android 단위 테스트, lintDebug, assembleDebug, 패키지 식별/버전, 체크섬 및 소스 ZIP 패키징 성공
- 서명: Android debug 서명. Production 서명 아님.
- 미검증: 실기기 설치 및 VPN 승인, 실제 DNS 경로/광고 차단 효과, 네트워크 전환, 장시간 안정성.

주의: CI 자동화 성공은 실기기 수용 시험의 대체가 아닙니다. 정식 출시 완료로 판정하지 않습니다.


## 0.7.8-dev — TCP/IP reserved-header validation (2026-11-14)
- Hardened the DNS TCP packet parser: reject IPv4 packets with the reserved fragmentation flag set, and reject TCP headers with nonzero reserved/NS bits instead of silently accepting unsupported header semantics.
- Added regression coverage for both malformed-header cases.
- Package versionCode 21 / `0.7.8-dev`.
- CI run: https://github.com/justinhblee-prog/guardian/actions/runs/43573908206 — pending at documentation update time. Do not treat this source change as verified until that run completes successfully.
- This is parser hardening only; it does not prove physical-device VPN routing or real ad-removal effectiveness.
