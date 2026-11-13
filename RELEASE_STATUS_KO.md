# Guardian AdBlock Android — 개발/릴리스 상태

기준 버전: **0.7.0-dev**  
기준일: 2026-11-13  
저장소: https://github.com/justinhblee-prog/guardian

## 기존 차단 실패 원인

이전 0.6.5-dev는 기본 차단 규칙이 10개 도메인뿐이었고, 공식 광고 필터 목록을 내려받거나 Adblock 형식을 해석하지 못했습니다. VPN이 켜져 있어도 대부분의 광고 도메인이 차단 목록에 없었습니다.

0.7.0-dev에서는 실제 DNS 필터 목록 다운로드·검증·캐시·적용 경로와 필터 준비 확인을 추가했습니다.

## 구현된 기능

| 영역 | 현재 구현 |
|---|---|
| DNS 보호 | Android VpnService, IPv4/IPv6 UDP DNS |
| DNS 응답 | NXDOMAIN / SERVFAIL, upstream 응답·transaction·question 검증, truncated UDP의 TCP fallback |
| 필터 프로필 | AdGuard DNS Filter(권장), 1Hosts Lite, OISD Big, HaGeZi Normal, HaGeZi Pro |
| 보안 필터 | 선택형 HaGeZi Threat Intelligence 악성코드·피싱 목록 |
| 규칙 엔진 | Adblock 도메인 앵커, 예외 규칙, hosts, badfilter 출처 격리, 사용자 block/allow |
| 캐시 안전성 | 임시 파일 다운로드, 규칙 수 검증, 원자적 교체, 이전 정상 캐시 보존, 서비스 시작 직전 실제 파일 재검증 |
| DNS 업스트림 | Cloudflare, Google, Quad9, AdGuard DNS, AdGuard Family DNS, 사용자 지정 IPv4/IPv6 |
| 직접 DNS | 공용 DNS 직접 포착 고급 옵션, 시스템 가상 DNS 경로 |
| 자동 업데이트 | 앱 실행 시 6시간 / 12시간 / 24시간 / 7일 주기 확인 |
| 앱 제어 | 설치 앱 이름/패키지 검색 및 VPN 보호 제외 |
| 사용자 규칙 | 사용자 차단/허용 도메인 |
| 활동/진단 | DNS 활동, 통계, 최근 요청, 진단 메시지, 기록 초기화 |
| 자체 테스트 | 활성 VPN에서 차단 도메인과 정상 도메인 DNS 테스트 |
| UI | 홈 필터 준비 상태, 필터 프로필/보안/업데이트 옵션, DNS 설정, 앱 제외 검색 |

강력한 차단 프로필은 정상 서비스까지 차단할 가능성이 높아질 수 있습니다. 필터 프로필을 바꾸면 새 목록을 다운로드하고 캐시 프로필 검증이 끝난 뒤에만 새 구성으로 보호를 시작합니다.

자동 업데이트 확인은 앱을 실행할 때 수행됩니다. 앱을 닫은 동안의 백그라운드 예약 업데이트는 아직 지원하지 않습니다.

공용 DNS 직접 포착은 IP 단위 라우팅으로 동작하므로 해당 DNS 서버 IP로 향하는 비-DNS 연결도 TUN에 들어올 수 있습니다. 비-DNS 트래픽은 중계하지 않으므로 고급 옵션에서 끌 수 있습니다.

## 최신 내부 검증

- [GitHub Actions 43432189671](https://github.com/justinhblee-prog/guardian/actions/runs/43432189671): **Success**
- 소스 커밋: `0213bc28e97e7553d621a75d43108d4f885da67a`
- Android JUnit: **53개 테스트 메서드 / 10개 스위트 통과**
- Gradle: **BUILD SUCCESSFUL**
- APK 생성/업로드: push CI에서는 의도적으로 생략

## 남은 검증 및 제한

- 실기기 설치, VPN 권한, DNS 라우팅 및 실제 광고 차단 효과 미검증
- Wi-Fi↔모바일 데이터 전환, 화면 잠금, 장시간 실행 미검증
- DoH/DoT 및 클라이언트 TCP DNS 미지원
- IPv6 확장 헤더 및 IP fragment 재조립 미지원
- 웹페이지 요소 숨김 및 앱 내부/first-party 광고 제거 미지원
- 공용 DNS 직접 포착은 IP 라우팅 부작용이 있어 실기기에서 반드시 확인 필요

## APK 생성 정책

사용자 요청대로 개발 완료 전에는 APK를 생성하지 않습니다. 내부 구현·회귀 테스트·문서 검증을 마친 뒤 최종 수동 릴리스 workflow로 APK와 설치 패키지를 만들고, package/version, SHA-256 및 ZIP 무결성을 확인합니다.

**현재 판정: 개발 진행 중. 최종 APK는 아직 생성하지 않았습니다.** CI 성공은 실기기 광고 차단 성공을 의미하지 않습니다.


## Adblock important 우선순위 처리 — CI 대기

- `$important` 차단 규칙을 일반 차단 규칙과 분리해 보존하고, 허용 예외보다 우선 적용하도록 엔진을 수정했습니다.
- `$badfilter`는 같은 출처의 important 규칙도 정확히 해제합니다.
- 구현하지 않은 important 허용 예외는 임의로 일반 허용 규칙으로 처리하지 않고 미지원 규칙으로 제외합니다.
- 필터 목록 검증 수와 UI 차단 규칙 수에 important 차단 규칙도 포함합니다.
- 현재 테스트 소스: 10개 suite / 56개 테스트 메서드. 최신 변경 CI 대기.


- 추가 수정: `$badfilter`가 같은 도메인의 일반 차단 규칙과 `$important` 규칙을 구분하여 해당 modifier 변형만 해제하도록 수정하고 회귀 테스트를 추가했습니다. 최신 테스트 소스는 10개 suite / 57개 메서드이며 CI 대기 중입니다.
