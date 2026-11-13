# Guardian AdBlock Android — 개발/릴리스 상태

기준 버전: **0.7.0-dev**  
기준일: 2026-11-13

## 확인된 문제 원인

이전 0.6.5-dev 구현은 실제 차단 목록이 10개 도메인에 불과했고, AdGuard DNS Filter와 같은 Adblock 형식의 목록을 다운로드하거나 해석하는 기능이 없었습니다. 따라서 VPN 상태가 활성으로 표시되어도 대부분의 광고 도메인은 목록에 없어서 차단되지 않는 구조였습니다. 일부 앱이 8.8.8.8 등 공용 DNS 서버를 직접 지정하면 VPN 라우트 밖으로 나갈 가능성도 있었습니다.

이번 개발에서는 필터 목록을 실제로 확보·해석하고, 주요 공용 DNS 서버의 UDP/53 경로를 VPN으로 유도하며, 결과를 확인하는 내부 DNS 자가 테스트를 추가하고 있습니다.

## 0.7.0-dev 구현 항목

| 영역 | 상태 |
|---|---|
| Android VpnService 기반 DNS 보호 | 구현 |
| IPv4 / IPv6 UDP DNS 파싱 | 구현 |
| 주요 공용 DNS 서버 직접 UDP/53 질의 라우팅 | 구현 |
| NXDOMAIN / SERVFAIL | 구현 |
| upstream 응답 endpoint / transaction / question 검증 | 구현 |
| 잘린 upstream UDP 응답의 TCP fallback | 구현 |
| AdGuard DNS Filter 다운로드 및 캐시 | 구현 중 / CI 확인 필요 |
| Adblock 도메인 규칙 및 hosts 형식 파싱 | 구현 중 / CI 확인 필요 |
| 안전하게 처리할 수 없는 고급 규칙 제외 및 개수 표시 | 구현 중 / CI 확인 필요 |
| 선택형 HaGeZi 악성코드·피싱 목록 | 구현 중 / CI 확인 필요 |
| DNS 업스트림 선택 | 구현 |
| 24시간 경과 후 앱 실행 시 자동 업데이트 | 구현 |
| 사용자 차단/허용 목록 | 구현 |
| 앱별 VPN 제외 | 구현 |
| DNS 활동/통계/진단/초기화 | 구현 |
| 가상 DNS 및 직접 공용 DNS 자가 테스트 | 구현 중 / 실기기 확인 필요 |
| DoH/DoT 및 client-side TCP DNS | 미지원 |
| IPv6 확장 헤더 및 fragment 재조립 | 미지원 |

## APK 생성 정책

- 개발 중 push CI는 테스트만 실행합니다.
- 개발이 끝나기 전에는 APK를 만들거나 중간 APK를 최종본으로 제공하지 않습니다.
- 최종 릴리스 수동 workflow 실행에서만 APK를 빌드합니다.
- APK 생성 후 package/version, ZIP 무결성, SHA-256, 설치 패키지를 확인합니다.

## 내부 검증 체크리스트

- [ ] 최신 main 소스의 Android JUnit 테스트 성공
- [ ] Adblock parser: 차단/예외/hosts/badfilter/unsupported modifier 회귀 테스트 통과
- [ ] DNS 자가 테스트 query/response 패킷 형식 및 매칭 테스트 통과
- [ ] VPN start/stop/restart race 및 stale 상태 검증
- [ ] filter cache 교체 실패 시 이전 캐시 유지
- [ ] 주요 공용 DNS 직접 질의 라우팅 회귀 확인
- [ ] UI의 모든 저장 옵션이 실제 VPN 엔진에 반영되는지 확인
- [ ] 최종 APK 수동 빌드, package/version 및 checksum 검증

## 실기기 수용 테스트

- [ ] APK 설치 및 VPN 권한 승인
- [ ] 보호 시작/중지 반복
- [ ] DNS 자가 테스트 통과
- [ ] 브라우저와 일반 앱에서 실제 차단 확인
- [ ] 앱별 제외 동작 확인
- [ ] Wi-Fi↔모바일 데이터 전환
- [ ] IPv4 / IPv6 환경 각각 확인
- [ ] 화면 잠금 및 장시간 실행

**현재 판정:** 개발 진행 중. 최종 APK는 아직 생성하지 않습니다. 자동화 테스트 및 실기기 검증 전에는 광고 차단 기능 완료 또는 상용 제품 완료로 표시하지 않습니다.
