# Guardian AdBlock Android 0.7.0-dev — 개발 인수인계

작성일: 2026-11-13

## 1. 현재 상태

- 저장소: justinhblee-prog/guardian, main
- 앱 패키지: com.guardian.adblock
- 개발 버전: 0.7.0-dev / versionCode 13
- 현재 상태: 개발 진행 중. 최종 APK는 아직 생성하지 않음.
- push CI는 Android 단위 테스트만 실행하며, 최종 APK 생성은 수동 릴리스 workflow로 제한함.

## 2. 기존 차단이 작동하지 않았던 핵심 원인

이전 0.6.5-dev의 기본 차단 규칙은 10개 도메인에 불과했습니다. AdGuard DNS Filter 같은 외부 필터 목록을 가져오거나 Adblock 규칙을 해석하는 기능도 없었습니다. 따라서 VPN 표시가 활성화되어도 실제 광고/추적 도메인 대부분이 차단 목록에 존재하지 않았습니다. 또한 일부 앱이 공용 DNS 서버 주소를 직접 지정하면 VPN의 가상 DNS 주소로만 구성된 라우트 밖으로 나갈 수 있었습니다.

## 3. 주요 소스 파일

- GuardianVpnService.kt: VPN 수명주기, TUN 패킷 루프, IPv4/IPv6 DNS, upstream DNS, 주요 public DNS 직접 질의 라우트, 앱 제외 설정
- DomainFilter.kt: DNS 질의/응답 검증, block/allow 적용, NXDOMAIN/SERVFAIL
- FilterRuleParser.kt: Adblock domain-anchor, exception, hosts, badfilter parser
- FilterListManager.kt: 공식 목록 다운로드, 최소 규칙 수 검증, 앱 내부 캐시, 안전한 캐시 교체, 업데이트 시각/규칙 수 저장
- DnsProbe.kt: 가상 DNS 및 public DNS 직접 질의 self-test query builder
- Ipv4Udp.kt / Ipv6Udp.kt: UDP DNS packet parse/response checksum
- MainActivity.kt: 홈/보호/활동/필터/설정 UI, DNS upstream, auto update, security filter, app exclusion
- shared/filter_rules.txt: 최소 bootstrap block rules
- android/app/src/test: parser, DNS, IPv4/IPv6, lifecycle, self-test regression tests

## 4. 현재 제공 옵션

- AdGuard DNS Filter 목록 자동/수동 업데이트
- 선택형 HaGeZi Threat Intelligence malware/phishing 필터
- Cloudflare / Google / Quad9 / AdGuard DNS upstream 선택
- 사용자 차단/허용 도메인 목록
- 설치된 앱의 VPN 제외
- DNS 활동 통계와 최근 요청, 무시된 패킷 및 마지막 진단
- 활동 기록 초기화
- 앱 내부 DNS 차단 자가 테스트

## 5. 필터 처리 경계

지원: Guardian block=/allow= 형식, Adblock anchored domain rules (||domain^), exceptions (@@||domain^), hosts entries, badfilter, supported safe modifiers.

지원하지 않는 고급 문맥/재작성 규칙은 DNS 수준에서 추측 적용하지 않고 제외 수로 기록합니다. 목록의 모든 규칙을 AdGuard 전체 엔진과 동일하게 처리한다고 주장하지 않습니다.

목록 출처:
- AdGuard DNS Filter: https://adguardteam.github.io/HostlistsRegistry/assets/filter_1.txt
- HaGeZi Threat Intelligence: https://adguardteam.github.io/HostlistsRegistry/assets/filter_44.txt

## 6. DNS self-test

홈/활동 화면에서 self-test를 실행합니다.
- doubleclick.net → 8.8.8.8:53 직접 질의 → NXDOMAIN 기대
- example.com → Guardian virtual DNS → NOERROR 기대

이 검사는 앱의 TUN → parser → matcher → response packet 경로를 검증하기 위한 내부 기능입니다. 물리적 기기에서 아직 실행되지 않았으므로 실기기 결과는 미확인입니다.

## 7. 제한 사항

- DoH/DoT 및 앱 자체 HTTPS DNS 미지원
- 클라이언트 방향 TCP/53 DNS 미지원
- 목록에 없는 사용자 지정 DNS 서버 주소는 직접 라우팅하지 않음
- IPv6 extension headers / IP fragments 미지원
- 전체 IP 트래픽을 프록시하는 full-tunnel VPN 아님
- 웹페이지 요소 숨김이나 first-party/app-embedded 광고 제거 미지원
- 실기기 설치, VPN 권한, 네트워크 전환, 장시간 실행 및 실제 광고 차단 효과는 아직 검증되지 않음

## 8. 최종 완료 게이트

1. 최신 main 소스의 Android unit tests 성공
2. Parser 지원/미지원 규칙 회귀 테스트 성공
3. DNS self-test query encoding 및 응답 매칭 테스트 성공
4. VPN lifecycle/rule reload race 및 stale state 검증
5. 다운로드 실패 시 이전 정상 필터 캐시 유지 확인
6. UI 옵션이 실제 서비스 설정과 연결되는지 확인
7. 모든 내부 테스트 완료 후 version bump 및 수동 APK build
8. APK package/version, ZIP integrity, SHA-256 검증
9. 실기기 수용 테스트를 별도 항목으로 정확히 보고

완료되지 않은 기능을 완료로 표시하지 않습니다. 중간 APK를 최종본으로 제공하지 않습니다.
