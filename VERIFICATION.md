# Guardian AdBlock Android — Verification Record

기준 버전: 0.7.0-dev  
기준일: 2026-11-13

## 현재 회귀 테스트 인벤토리

| 테스트 스위트 | 테스트 메서드 |
|---|---:|
| DomainFilterTest | 17 |
| Ipv4UdpTest | 6 |
| Ipv6UdpTest | 4 |
| ProtectionStateTest | 2 |
| FilterRuleParserTest | 11 |
| DnsProbeTest | 2 |
| FilterRuleComposerTest | 3 |
| DnsAddressValidatorTest | 2 |
| FilterListProfilesTest | 8 |
| VpnStartCancellationGateTest | 4 |
| **합계** | **53** |

## 최신 성공 CI

- Workflow: https://github.com/justinhblee-prog/guardian/actions/runs/43432189671
- 소스 커밋: `0213bc28e97e7553d621a75d43108d4f885da67a`
- 결과: **Success**
- `clean testDebugUnitTest`: **BUILD SUCCESSFUL**
- APK assemble / upload: push CI 정책에 따라 **skipped**
- 검증된 범위: DNS 파서·응답 검증, IPv4/IPv6 UDP 패킷, 필터 규칙 파싱/예외, 필터 프로필 캐시 준비 조건, 대형 목록 도메인 매칭, DNS 주소 검증, VPN 시작/중지 취소 게이트.

## 내부 검증된 설계 및 코드 경로

- 공식 DNS 필터 프로필: AdGuard DNS Filter, 1Hosts Lite, OISD Big, HaGeZi Normal, HaGeZi Pro
- 원자적 파일 교체와 마지막 정상 필터 캐시 보존
- 선택 프로필 및 보안 필터 캐시가 유효하지 않으면 보호 시작 차단
- VPN 서비스 시작 직전 실제 캐시 파일을 다시 파싱하고 최소 유효 규칙 수 확인
- badfilter는 해당 목록 출처 안에서만 적용
- 도메인 차단/허용은 전체 규칙 선형 검색 대신 DNS label suffix hash lookup 사용
- DNS 응답 transaction ID / 질문 이름 / QTYPE / QCLASS 확인
- DNS 자가 테스트는 VPN 터널 활성 상태에서만 수행
- VPN 시작/중지 순서 및 취소 경합에 대한 회귀 테스트
- Android 시스템 바 인셋 처리 및 앱 제외 검색 UI

## 미검증 / 미지원 범위

- 실제 Android 기기 설치 및 VPN 권한 승인
- 실기기에서 필터 목록 다운로드, TUN 라우팅 및 실제 광고 차단 성공
- Wi-Fi↔모바일 데이터 전환, 장시간 실행, 화면 잠금/복귀
- DoH/DoT, 클라이언트 방향 TCP DNS
- IPv6 확장 헤더 및 IP fragment 재조립
- 웹페이지 요소 숨김, 앱 내부/first-party 광고 제거
- 앱별 제외와 고급 공용 DNS 직접 포착의 실기기 검증

공용 DNS 직접 포착은 Android VPN의 IP 단위 라우팅을 사용합니다. 선택된 DNS 서버 IP로 가는 비-DNS 트래픽도 TUN에 들어올 수 있으며, 앱은 이를 중계하지 않습니다. 이 기능은 고급 옵션으로 노출하고 비활성화 방법을 안내합니다.

## APK 정책

- 개발 중 push CI에서는 테스트만 수행합니다.
- 개발 완료 전 중간 APK를 최종본으로 제공하지 않습니다.
- 최종 APK는 별도 수동 릴리스 workflow에서만 생성합니다.
- APK 생성 시 package/version, APK/ZIP 무결성, SHA-256 및 설치 패키지를 확인합니다.


## Adblock `$important` priority support — CI pending

- Parsed important block rules are preserved separately from ordinary block rules and remain isolated per source during composition.
- DNS matching now evaluates important blocks before allow exceptions, matching Adblock priority semantics; unsupported important exceptions are ignored rather than silently misapplied.
- `$badfilter` removes matching important block rules from the same source.
- Cache validation and displayed block counts include important rules.
- Added parser and composed-engine regression tests. Current source inventory: 10 suites / 59 test methods. CI pending.


## Modifier-specific `$badfilter` semantics — CI pending

- `$badfilter` now cancels only the matching normal or `$important` variant, rather than disabling every rule for the same hostname.
- Added a regression test proving normal and important variants remain independent.
- Current source inventory: 10 suites / 57 test methods. CI pending.


## Optional AWAvenue Android app ads list — CI pending

- Added an opt-in DNS filter based on the AWAvenue Ads Rule registry asset (`filter_53.txt`).
- When enabled, the list is downloaded, validated against a minimum rule count, cached atomically, included in cache-readiness checks, and composed into the active VPN rule set.
- Added cache-readiness and official-source catalog regression tests.
- Current source inventory: 10 suites / 59 test methods. CI pending.


## Latest CI recovery — Adblock important/badfilter parser

- Workflow: https://github.com/justinhblee-prog/guardian/actions/runs/43433588312
- Commit: `fbc3f5a392cbab666ec11c605fa662c3d30baae0`
- Result: **success**. `:app:testDebugUnitTest` completed successfully; Gradle reported 59 tests completed with no failures.
- Root cause of the previous failures: `$badfilter` detection searched for the exact token immediately after `$`, so it missed `$important,badfilter` modifier lists. Modifier parsing now tokenizes the comma-separated list and recognizes each modifier.
- APK assembly and upload were not run; this was push-triggered test CI only.
