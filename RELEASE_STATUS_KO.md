# Guardian AdBlock Android — 개발/릴리스 상태

기준 버전: **0.7.1-dev**  
기준일: 2026-11-13  
저장소: https://github.com/justinhblee-prog/guardian

## 기존 차단 실패 원인

이전 0.6.5-dev는 기본 차단 규칙이 10개 도메인뿐이었고, 공식 광고 필터 목록 다운로드 및 Adblock 규칙 해석이 없었습니다. VPN 상태가 켜져 있어도 대부분의 광고 도메인이 차단 목록에 없었습니다.

## 구현 현황

| 영역 | 현재 구현 |
|---|---|
| DNS 보호 | Android VpnService, IPv4/IPv6 UDP DNS |
| DNS 응답 | NXDOMAIN / SERVFAIL, upstream transaction/question 검증, truncated UDP의 TCP fallback |
| 필터 프로필 | AdGuard DNS Filter, 1Hosts Lite, OISD Big, HaGeZi Normal, HaGeZi Pro |
| 보안 목록 | 선택형 HaGeZi Threat Intelligence |
| 앱 광고 SDK 목록 | 선택형 AWAvenue Ads Rule |
| 규칙 엔진 | Adblock 도메인 앵커, 예외, hosts, important, 출처별 badfilter, 사용자 block/allow |
| 캐시 | 크기/규칙 수 검증, 원자적 교체, 이전 정상 캐시 보존, 서비스 시작 직전 실제 파일 재검증 |
| DNS 업스트림 | Cloudflare, Google, Quad9, AdGuard DNS, AdGuard Family DNS, 사용자 지정 IPv4/IPv6 |
| 직접 DNS | 공용 DNS 직접 포착 고급 옵션(기본 비활성화), 시스템 가상 DNS |
| 자동 업데이트 | 앱 실행 시 6/12/24시간 또는 7일 주기 확인 |
| 앱 제어 | 설치 앱 검색 및 VPN 보호 제외 |
| 사용자 규칙 | 차단/허용 도메인 |
| 활동/진단 | DNS 활동, 통계, 최근 요청, 진단, 기록 초기화 |
| 자체 테스트 | 활성 VPN에서 활성 규칙 매칭 및 NXDOMAIN 생성 엔진 자체 테스트 |
| UI | 홈, 보호, 활동, 필터, 설정의 5개 화면 |

자동 업데이트 확인은 앱 실행 시 수행됩니다. 앱을 닫은 동안의 백그라운드 예약 업데이트는 아직 지원하지 않습니다.

공용 DNS 직접 포착은 IP 단위 라우팅입니다. DNS 서버 IP로 향하는 비DNS 연결도 TUN에 들어올 수 있으며 앱은 이를 중계하지 않습니다. 이 때문에 기본값은 꺼져 있고 고급 옵션으로만 켤 수 있습니다.

## 최신 내부 검증

- [GitHub Actions 43434091375](https://github.com/justinhblee-prog/guardian/actions/runs/43434091375): **Success**
- 소스 커밋: `419356a1ef43ba20c33256b353566da7f42b8f61`
- Android unit-test 단계: **통과**
- 테스트 인벤토리: **12개 스위트 / 68개 JUnit 테스트 메서드**
- APK 생성/업로드: push CI에서는 의도적으로 생략
- 직전 파서 오류 원인: `$important,badfilter`처럼 쉼표로 구분된 modifier에서 badfilter 토큰을 인식하지 못했습니다. modifier를 분리해 각각 인식하도록 수정했고 59개 테스트가 통과했습니다. 이후 직접 DNS 안전 기본값 테스트를 추가해 현재 인벤토리는 65개입니다.

## 남은 검증 및 제한

- 실기기 설치, VPN 권한, DNS 라우팅 및 실제 광고 차단 효과 미검증
- Wi-Fi↔모바일 데이터 전환, 화면 잠금, 장시간 실행 미검증
- DoH/DoT 및 클라이언트 TCP DNS 미지원
- IPv6 확장 헤더 및 IP fragment 재조립 미지원
- 웹페이지 요소 숨김 및 앱 내부/first-party 광고 제거 미지원
- 공용 DNS 직접 포착의 실기기 라우팅 부작용 검증 필요

## APK 생성 정책

개발 완료 전에는 APK를 생성하지 않습니다. 내부 구현·회귀 테스트·문서 검증을 마친 뒤 최종 수동 릴리스 workflow로 APK를 만들고 package/version, SHA-256 및 ZIP 무결성을 확인합니다.

**현재 판정: 개발 진행 중. 최종 APK는 아직 생성하지 않았습니다.** CI 성공은 실기기 광고 차단 성공을 의미하지 않습니다.


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
