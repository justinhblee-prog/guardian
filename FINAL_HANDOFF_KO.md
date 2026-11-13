# Guardian AdBlock Android 0.7.0-dev — 개발 인수인계

작성일: 2026-11-13

## 1. 프로젝트 상태

- GitHub: https://github.com/justinhblee-prog/guardian
- Branch: main
- Package: `com.guardian.adblock`
- Version: `0.7.0-dev` / versionCode `13`
- 상태: 개발 진행 중
- 최종 APK: 아직 생성하지 않음
- push CI: 테스트 전용. APK는 개발 완료 후 수동 릴리스 workflow에서만 생성

## 2. 기존 광고 차단이 작동하지 않았던 이유

0.6.5-dev는 기본 차단 규칙이 10개 도메인뿐이었고, 외부 필터 목록 다운로드와 Adblock 규칙 해석이 없었습니다. VPN 상태가 켜져 있어도 실제 광고 도메인의 대부분이 목록에 없어 차단되지 않았습니다.

0.7.0-dev는 공식 필터 목록을 다운로드·검증·캐시하고, 선택한 프로필이 실제로 적용 가능한 상태인지 확인한 뒤에만 보호를 시작하도록 변경했습니다.

## 3. 구현 기능

### 필터 / 규칙
- 필터 프로필: AdGuard DNS Filter(권장), 1Hosts Lite, OISD Big, HaGeZi Normal, HaGeZi Pro
- 선택형 HaGeZi Threat Intelligence 악성코드·피싱 필터
- 6시간 / 12시간 / 24시간 / 7일 자동 업데이트 주기
- 자동 업데이트 확인은 앱 실행 시 수행. 앱이 닫힌 동안 백그라운드 업데이트는 아직 미지원
- 사용자 block / allow 도메인 규칙
- Adblock 도메인 앵커, 예외, hosts, badfilter(출처별 격리), 지원 가능한 modifier
- 지원하지 않는 문맥 규칙은 무리하게 적용하지 않고 제외 수로 기록
- UTF-8 BOM 파일 처리
- 대형 목록은 도메인 label suffix hash lookup으로 매칭

### DNS / VPN
- Android VpnService 기반 가상 DNS
- IPv4 / IPv6 UDP DNS 처리
- NXDOMAIN / SERVFAIL 응답
- upstream 응답의 transaction ID, 질문 이름, QTYPE, QCLASS 검증
- 잘린 UDP 응답에 대한 upstream TCP fallback
- Cloudflare / Google / Quad9 / AdGuard DNS / AdGuard Family DNS upstream
- 사용자 지정 IPv4 / IPv6 DNS 주소
- 공용 DNS 직접 포착 고급 옵션
- DNS 자가 테스트는 VPN 터널이 활성화된 경우에만 수행
- 시작/중지 취소 경합 및 재시작 순서 보호

### 앱 UI / 옵션
- 홈 대시보드의 필터 준비 상태
- 필터 탭에서 프로필, 보안 필터, 자동 업데이트, 업데이트 주기, 고급 DNS 포착 관리
- 설정 탭에서 upstream DNS 및 사용자 지정 DNS 관리
- 설치 앱 이름/패키지명 검색 및 VPN 보호 제외
- DNS 활동, 통계, 최근 요청, 진단 및 기록 초기화

## 4. 캐시 및 보호 시작 안전장치

1. 필터 파일은 임시 파일로 다운로드합니다.
2. 규칙 수를 검증한 후 캐시 파일을 교체합니다.
3. 다운로드 실패 시 마지막 정상 캐시를 보존합니다.
4. 선택한 프로필과 마지막으로 정상 저장된 프로필이 다르면 보호 시작을 거부합니다.
5. 보안 필터가 켜져 있으면 보안 목록 캐시도 필요합니다.
6. VPN 서비스가 시작될 때 메타데이터뿐 아니라 실제 캐시 파일을 다시 파싱하고 최소 규칙 수를 확인합니다.
7. 유효하지 않은 캐시로는 TUN을 활성화하지 않습니다.

## 5. 주요 소스 파일

- `GuardianVpnService.kt`: VPN 수명주기, TUN, DNS 전달, 라우팅, 규칙 적용
- `VpnStartCancellationGate.kt`: 시작/중지 취소 순서 제어
- `DomainFilter.kt`: DNS 질문/응답 검증, 도메인 매칭, NXDOMAIN/SERVFAIL
- `FilterRuleParser.kt`: Adblock / hosts / 예외 / badfilter 파서
- `FilterRuleComposer.kt`: 출처별 규칙 조합과 badfilter 격리
- `FilterListManager.kt`: 공식 목록, 프로필, 다운로드, 캐시, 무결성 및 준비 상태
- `DnsAddressValidator.kt`: 사용자 지정 DNS 주소 검증
- `DnsProbe.kt`: DNS 자체 테스트 질의
- `Ipv4Udp.kt` / `Ipv6Udp.kt`: IPv4/IPv6 UDP 패킷 처리
- `MainActivity.kt`: 홈, 보호, 활동, 필터, 설정 UI
- `android/app/src/test`: 10개 테스트 스위트, 53개 JUnit 테스트 메서드

## 6. 필터 출처

- AdGuard DNS Filter: https://adguardteam.github.io/HostlistsRegistry/assets/filter_1.txt
- 1Hosts Lite: https://adguardteam.github.io/HostlistsRegistry/assets/filter_24.txt
- OISD Big: https://adguardteam.github.io/HostlistsRegistry/assets/filter_27.txt
- HaGeZi Normal: https://adguardteam.github.io/HostlistsRegistry/assets/filter_34.txt
- HaGeZi Pro: https://adguardteam.github.io/HostlistsRegistry/assets/filter_48.txt
- HaGeZi Threat Intelligence: https://adguardteam.github.io/HostlistsRegistry/assets/filter_44.txt

## 7. 최신 내부 검증

- CI: https://github.com/justinhblee-prog/guardian/actions/runs/43432189671
- Source commit: `0213bc28e97e7553d621a75d43108d4f885da67a`
- 결과: Success
- Gradle `clean testDebugUnitTest`: BUILD SUCCESSFUL
- 테스트: 10 suites / 53 test methods
- APK build/upload: push CI 정책에 따라 skipped

CI 성공은 실기기 VPN 동작이나 실제 광고 차단 성공을 증명하지 않습니다.

## 8. 제한 및 미검증 범위

- DoH / DoT 및 클라이언트 방향 TCP DNS 미지원
- IPv6 extension headers 및 IP fragment 재조립 미지원
- 전체 IP 트래픽을 프록시하는 full-tunnel VPN 아님
- 웹페이지 요소 숨김 및 앱 내부/first-party 광고 제거 미지원
- 실기기 설치, VPN 권한, 실제 필터 다운로드, DNS 라우팅, 실제 광고 차단 효과 미검증
- Wi-Fi↔모바일 데이터 전환, 장시간 실행, 화면 잠금/복귀 미검증

공용 DNS 직접 포착은 IP 단위 라우팅입니다. 따라서 해당 DNS 서버 IP로 향하는 비-DNS 트래픽도 TUN에 들어올 수 있으며 앱은 이를 중계하지 않습니다. 이 기능은 고급 옵션으로 노출하고 끌 수 있게 했습니다.

## 9. 최종 완료 게이트

1. 최신 main 소스의 모든 내부 테스트 성공
2. 필터 다운로드 / 파싱 / 캐시 / 선택 프로필 준비 조건 검증
3. DNS 패킷 및 응답 검증
4. VPN 시작/중지/재시작 및 취소 경합 검증
5. UI 저장 옵션과 서비스 동작 연결 확인
6. 내부 개발 완료 후 버전 증가 및 수동 APK 빌드
7. APK package/version, ZIP 무결성, SHA-256 확인
8. 실기기 수용 테스트 결과는 내부 CI와 구분해 별도 보고

개발 완료 전 중간 APK를 최종본으로 제공하지 않습니다. 검증되지 않은 기능을 완료로 표시하지 않습니다.
