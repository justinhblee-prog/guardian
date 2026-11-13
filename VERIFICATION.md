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
| VpnConfigurationDefaultsTest | 1 |
| VpnStartCancellationGateTest | 4 |
| **합계** | **60** |

총 11개 Kotlin 테스트 스위트입니다.

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
- DNS 자가 테스트는 VPN 터널 활성 상태에서만 수행
- VPN 시작/중지 취소 경합 회귀 테스트
- 공용 DNS IP 직접 포착은 기본 비활성화하여 비DNS 트래픽의 부수적 차단 위험 완화

## 미검증 / 미지원 범위

- 실제 Android 기기 설치 및 VPN 권한 승인
- 실기기에서 필터 목록 다운로드, TUN 라우팅 및 실제 광고 차단 성공
- Wi-Fi↔모바일 데이터 전환, 장시간 실행, 화면 잠금/복귀
- DoH/DoT, 클라이언트 방향 TCP DNS
- IPv6 확장 헤더 및 IP fragment 재조립
- 웹페이지 요소 숨김, 앱 내부/first-party 광고 제거
- 앱별 제외 및 고급 공용 DNS 직접 포착의 실기기 검증

공용 DNS 직접 포착은 Android VPN의 IP 단위 라우팅을 사용합니다. DNS 서버 IP로 가는 비DNS 트래픽도 TUN에 들어올 수 있으며 앱은 이를 중계하지 않습니다. 이 기능은 기본 비활성화입니다.

## APK 정책

- 개발 중 push CI에서는 테스트만 수행합니다.
- 개발 완료 전 중간 APK를 최종본으로 제공하지 않습니다.
- 최종 APK는 별도 수동 릴리스 workflow에서만 생성합니다.
- APK 생성 시 package/version, APK/ZIP 무결성, SHA-256을 확인합니다.
