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
| AdGuard DNS Filter 및 선택 프로필 다운로드/캐시 | 구현 / 실기기 다운로드 확인 필요 |
| Adblock 도메인 규칙 및 hosts 형식 파싱 | 구현 / 회귀 테스트 통과 |
| 안전하게 처리할 수 없는 고급 규칙 제외 및 개수 표시 | 구현 / 회귀 테스트 통과 |
| 선택형 HaGeZi 악성코드·피싱 목록 | 구현 / 실기기 다운로드 확인 필요 |
| DNS 업스트림 선택(AdGuard Family 포함) 및 사용자 지정 IPv4/IPv6 주소 | 구현 |
| 6시간/12시간/24시간/7일 자동 업데이트 주기 | 구현 |
| 사용자 차단/허용 목록 | 구현 |
| 앱별 VPN 제외 및 앱 이름/패키지 검색 | 구현 |
| DNS 활동/통계/진단/초기화 | 구현 |
| 가상 DNS 및 직접 공용 DNS 자가 테스트 | 구현 / 실기기 확인 필요 |
| DoH/DoT 및 client-side TCP DNS | 미지원 |
| IPv6 확장 헤더 및 fragment 재조립 | 미지원 |

## APK 생성 정책

- 개발 중 push CI는 테스트만 실행합니다.
- 개발이 끝나기 전에는 APK를 만들거나 중간 APK를 최종본으로 제공하지 않습니다.
- 최종 릴리스 수동 workflow 실행에서만 APK를 빌드합니다.
- APK 생성 후 package/version, ZIP 무결성, SHA-256, 설치 패키지를 확인합니다.

## 내부 검증 체크리스트

- [ ] 최신 main 소스의 Android JUnit 테스트 성공 (45개, 최신 CI 대기)
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


## 최신 내부 CI 결과

- [GitHub Actions 43430515269](https://github.com/justinhblee-prog/guardian/actions/runs/43430515269): **Success**
- 소스 커밋: 33bb38ed0abae177b8b24f74890a816a273c5351
- Gradle clean + testDebugUnitTest: **BUILD SUCCESSFUL**
- 해당 성공 CI 당시 회귀 테스트: 9개 suite / 44개 테스트 메서드
- APK 빌드/업로드: **정책에 따라 생략** (push CI는 테스트 전용)

실기기 VPN/광고 차단 수용 테스트는 아직 미완료입니다.

## 추가 반영 사항 (0.7.0-dev)

- 앱별 보호 제외 목록에 앱 이름/패키지명 검색 추가
- 필터 자동 업데이트 주기: 6시간 / 12시간 / 24시간 / 7일
- 사용자 지정 IPv4/IPv6 DNS 주소로 향하는 직접 DNS 질의 포착은 고급 옵션으로 제공(비-DNS 트래픽 영향 경고 포함)
- 공식 AdGuard DNS 필터 캐시가 검증되기 전에는 보호 시작을 제한
- DNS 자체 테스트는 실제 VPN 터널이 활성화된 경우에만 실행
- 다운로드된 대형 차단 목록에서 도메인별 suffix hash lookup을 사용해 전체 목록 선형 검색을 제거


## 필터 프로필 및 UI 옵션 확장

- 차단 프로필: AdGuard DNS Filter(권장), 1Hosts Lite, OISD Big, HaGeZi Normal, HaGeZi Pro
- 프로필 변경 시 새 목록을 내려받고, 마지막 정상 캐시와 선택 프로필을 분리해 표시
- 업데이트 실패 시 이전 정상 목록을 유지하고 오류 원인을 화면에 표시
- 자동 업데이트 주기: 6시간 / 12시간 / 24시간 / 7일
- 업스트림 DNS에 AdGuard Family DNS 추가
- 설치 앱 제외 목록 검색 및 홈 대시보드의 필터 준비 상태 카드

현재 테스트 소스: 9개 테스트 스위트 / 47개 JUnit 테스트 메서드. 최신 BOM 처리 및 필터 프로필 캐시 보호 변경분의 CI 검증은 대기 중입니다. APK는 개발 완료 전까지 생성하지 않습니다.


## 최신 44개 테스트 CI

- [GitHub Actions 43430515269](https://github.com/justinhblee-prog/guardian/actions/runs/43430515269): **Success**
- 소스 커밋: 33bb38ed0abae177b8b24f74890a816a273c5351
- Gradle unit tests: **BUILD SUCCESSFUL**
- APK 빌드/업로드: 개발 중 push CI 정책에 따라 생략


- DNS 질문 도메인 대소문자 정규화는 터키어 시스템 로케일에서도 Locale.ROOT 기준으로 처리하도록 수정하고 회귀 테스트를 추가했습니다.


## 최신 추가 수정 — CI 대기

- UTF-8 BOM이 포함된 원격 필터 파일의 첫 번째 규칙도 정상 해석하도록 파서 수정 및 회귀 테스트 추가
- 선택한 필터 프로필과 캐시 프로필이 다르면 보호 시작을 막도록 시작 조건 강화 및 회귀 테스트 추가
- 현재 테스트 소스: 10개 suite / 49개 JUnit 테스트 메서드
- 최신 변경분 CI 성공은 아직 확인되지 않았으며, 최종 APK는 생성하지 않음


## VPN 시작/중지 경합 수정 — CI 대기

- ACTION_START 직후 백그라운드 시작 스레드가 실행되기 전에 ACTION_STOP이 들어오면 취소 플래그가 다시 초기화될 수 있는 경합을 수정했습니다.
- 시작 요청 시점에만 취소 상태를 초기화하고, 중지 요청은 원자적 게이트에 보존하도록 분리했습니다.
- 시작 작업 전 중지 및 명시적 재시작 시나리오 회귀 테스트 2개 추가
- 최신 테스트 소스: 10개 suite / 49개 테스트 메서드. CI 성공은 아직 확인되지 않았습니다.
