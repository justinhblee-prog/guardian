# Guardian AdBlock Android 0.7.0-dev — 개발 인수인계

작성일: 2026-11-13

## 1. 프로젝트 상태

- GitHub: https://github.com/justinhblee-prog/guardian
- Branch: main
- Package: `com.guardian.adblock`
- Version: `0.7.0-dev` / versionCode `13`
- 상태: 개발 진행 중
- 최종 APK: 아직 생성하지 않음
- push CI: Android unit test 전용. APK는 개발 완료 후 수동 릴리스 workflow에서 생성

## 2. 기존 광고 차단이 작동하지 않았던 이유

0.6.5-dev는 기본 차단 규칙이 10개 도메인뿐이었고, 외부 필터 목록 다운로드와 Adblock 규칙 해석이 없었습니다. VPN 상태가 켜져 있어도 실제 광고 도메인의 대부분이 차단 목록에 없었습니다.

0.7.0-dev에서는 공식 필터 목록 다운로드·검증·캐시·적용 경로를 구현하고, 선택한 필터 구성의 캐시가 검증된 경우에만 VPN 보호를 시작하도록 변경했습니다.

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
- DNS 자가 테스트

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
- `android/app/src/test`: **11개 테스트 스위트 / 60개 JUnit 테스트 메서드**

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
- 테스트 소스 인벤토리: 11 suites / 60 test methods
- APK build/upload: push CI 정책에 따라 skipped
- 최신 후속 UI 안내문 수정 커밋은 CI 재검증 중일 수 있으므로 최종 상태를 Actions에서 확인해야 합니다.

CI 성공은 실기기 VPN 동작이나 실제 광고 차단 성공을 증명하지 않습니다.

## 8. 제한 및 미검증 범위

- DoH / DoT 및 클라이언트 방향 TCP DNS 미지원
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
