# Guardian AdBlock Android — 0.7.0-dev

Guardian AdBlock은 Android VpnService 기반 DNS 필터링 앱입니다. 공식 DNS 필터 목록을 내려받아 검증하고, 가상 DNS 경로로 들어오는 도메인 질의를 차단합니다.

> 개발 정책: 기능 개발과 내부 검증이 끝나기 전에는 최종 APK를 만들지 않습니다. GitHub push CI는 테스트만 수행하며, APK 패키징은 최종 릴리스 수동 실행으로 제한합니다.

## 구현 범위

- Android VpnService 기반 IPv4 / IPv6 UDP DNS 처리
- Android 가상 DNS 엔드포인트를 통한 시스템 DNS 필터링
- 선택형 고급 공용 DNS 직접 포착(기본 꺼짐)
- 차단 도메인에 NXDOMAIN, 업스트림 실패 시 SERVFAIL 응답
- upstream DNS 선택: Cloudflare, Google, Quad9, AdGuard DNS, AdGuard Family DNS, 사용자 지정 IPv4/IPv6
- 필터 프로필: AdGuard DNS Filter, 1Hosts Lite, OISD Big, HaGeZi Normal, HaGeZi Pro
- 선택형 HaGeZi Threat Intelligence 악성코드·피싱 목록
- 선택형 AWAvenue Ads Rule Android 광고 SDK 목록
- Adblock 도메인 규칙, 예외, `$important`, `$badfilter`, hosts, Guardian `block=` / `allow=` 규칙 지원
- 사용자 차단/허용 도메인 목록, 앱별 VPN 제외
- 필터 다운로드 검증, 임시 파일 기반 원자적 캐시 교체, 이전 정상 캐시 보존
- 선택 프로필 및 활성화된 보조 목록의 캐시 준비 여부 확인
- DNS 활동, 차단/전달/실패/무시 통계, 최근 요청, 진단 및 기록 초기화
- DNS 자가 테스트: 선택된 DNS 경로에서 차단 도메인과 정상 도메인 응답 확인

## 필터 출처

- AdGuard DNS Filter: https://adguardteam.github.io/HostlistsRegistry/assets/filter_1.txt
- HaGeZi Threat Intelligence: https://adguardteam.github.io/HostlistsRegistry/assets/filter_44.txt
- AWAvenue Ads Rule: https://adguardteam.github.io/HostlistsRegistry/assets/filter_53.txt
- 추가 프로필은 AdGuard Hostlists Registry의 공식 배포 파일을 사용합니다.

AdGuard DNS Filter는 DNS 필터링에 맞게 단순화된 목록입니다. Guardian은 안전하게 해석할 수 있는 도메인 기반 규칙을 적용하고, 문맥 의존·재작성 등 미지원 규칙은 임의 해석하지 않고 제외 수를 기록합니다.

## DNS 자가 테스트

홈 또는 활동 화면에서 **DNS 차단 기능 테스트**를 실행합니다.

1. 현재 설정된 DNS 경로로 `doubleclick.net`을 질의해 NXDOMAIN 차단 응답을 확인합니다.
2. `example.com`을 질의해 정상 NOERROR 응답을 확인합니다.
3. 실패하면 활동 화면의 DNS 통계와 마지막 패킷 진단을 확인합니다.

기본 경로는 시스템 가상 DNS입니다. 고급 공용 DNS 직접 포착을 켠 경우에만 공용 DNS IP를 대상으로 시험합니다. 이 내부 테스트는 TUN → DNS parser → rule matcher → 응답 경로를 확인하지만, 실제 앱·웹의 모든 광고 제거를 증명하지는 않습니다.

## 화면 및 옵션

- 홈: 보호 상태, 시작/중지, DNS 자가 테스트, 필터 준비 상태 및 통계
- 보호: DNS 보호 범위와 미지원 범위
- 활동: DNS 질의/차단/전달/실패/무시, 최근 요청, 진단, 기록 초기화
- 필터: 프로필 선택, 보안/Android 앱 광고 목록, 자동 업데이트, 업데이트 주기, 사용자 차단/허용 규칙
- 설정: DNS 업스트림, 사용자 지정 DNS, 앱별 VPN 보호 제외

자동 업데이트 확인은 앱 실행 시 수행됩니다. 앱이 닫힌 동안의 예약 백그라운드 업데이트는 아직 지원하지 않습니다.

## 현재 제한 / 미지원

- 클라이언트 방향 TCP/53 DNS 가로채기
- DNS-over-HTTPS(DoH), DNS-over-TLS(DoT), 앱 자체 DNS 터널
- 목록에 없는 사용자 지정 DNS 주소로 직접 전송된 질의 가로채기
- IPv6 확장 헤더 및 IP fragment 재조립
- 전체 IP 트래픽의 VPN 프록시/전달
- 웹페이지 요소 숨김, 동일 도메인(first-party) 광고 제거, 앱 화면 내부 광고 제거
- 모든 광고가 차단되는 것을 보장하지 않음

공용 DNS 직접 포착은 Android VPN의 IP 단위 경로를 사용합니다. DNS 서버 IP로 향하는 비DNS 연결도 TUN에 들어올 수 있으므로 기본값은 꺼져 있으며, 고급 옵션에서만 켤 수 있습니다. 비DNS 연결에 문제가 생기면 해당 옵션을 끄십시오.

## 개발 및 검증

필요 환경: JDK 17, Android SDK Platform 35, Build Tools 35.0.0, Gradle 8.9.

로컬 테스트:
```bash
gradle -p android --no-daemon clean testDebugUnitTest
```

현재 테스트 소스 인벤토리: **11개 Kotlin 테스트 스위트 / 60개 JUnit 테스트 메서드**.

최근 성공 CI:
- https://github.com/justinhblee-prog/guardian/actions/runs/43434091375
- 소스 커밋: `419356a1ef43ba20c33256b353566da7f42b8f61`
- Android unit-test 단계: 성공

## 실기기 검증 경계

CI 테스트 통과는 소스 컴파일과 자동화 테스트 통과를 의미합니다. 실기기 VPN 승인/연결, 실제 필터 다운로드, DNS 라우팅, 광고 차단 효과, 앱 제외, Wi-Fi↔모바일 데이터 전환 및 장시간 실행은 별도 확인이 필요합니다. 실제 기기에서 실행하지 않은 항목은 완료로 보고하지 않습니다.
