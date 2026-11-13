# Guardian AdBlock Android — 0.7.0-dev

Guardian AdBlock은 Android VpnService 기반 DNS 필터링 앱입니다. 현재 개발 목표는 실제 차단 엔진의 작동 여부를 확인할 수 있게 만들고, AdGuard 스타일의 필터·DNS·앱별 제어와 활동 화면을 갖추는 것입니다.

> 개발 중 정책: 기능 개발과 내부 검증이 끝나기 전에는 APK 릴리스 패키지를 만들지 않습니다. GitHub push CI는 테스트만 수행하며, APK 패키징은 최종 릴리스 수동 실행으로 제한합니다.

## 현재 구현 범위

- Android VPN 인터페이스 기반 IPv4 / IPv6 UDP DNS 필터링
- Android 가상 DNS 엔드포인트뿐 아니라 주요 공용 DNS 서버(Cloudflare, Google, Quad9, AdGuard DNS, OpenDNS)의 직접 UDP/53 질의 경로도 VPN으로 유도
- DNS 질의 도메인과 필터 규칙 일치 시 NXDOMAIN 반환
- 허용 규칙 우선 적용, 업스트림 오류 시 SERVFAIL 반환
- 업스트림 DNS 선택: Cloudflare, Google, Quad9, AdGuard DNS 및 사용자 지정 IPv4/IPv6 주소
- 공식 AdGuard DNS Filter 목록 다운로드 및 로컬 캐시
- DNS 호환 Adblock 도메인 규칙(||domain^, @@||domain^), hosts 형식, Guardian block=/allow= 형식 지원
- 안전하게 해석할 수 없는 문맥/재작성 규칙은 추측 적용하지 않고 제외 수를 기록
- 선택형 HaGeZi Threat Intelligence malware/phishing 목록
- 24시간 경과 후 앱 실행 시 자동 필터 업데이트 옵션 및 수동 업데이트
- 사용자 차단/허용 목록, 앱별 VPN 제외 목록
- DNS 활동, 차단/전달/실패/무시 통계, 최근 요청 기록 및 기록 초기화
- 패킷 파싱 실패 원인 진단
- 앱 내부 DNS 자가 테스트: 공용 DNS 직접 질의 차단과 정상 도메인 응답 확인
- IPv6 UDP 필수 checksum 검사, 잘린 upstream UDP 응답의 TCP fallback

## 필터 목록 출처

- AdGuard DNS Filter: https://adguardteam.github.io/HostlistsRegistry/assets/filter_1.txt
- HaGeZi Threat Intelligence Feeds: https://adguardteam.github.io/HostlistsRegistry/assets/filter_44.txt
- AdGuard DNS Filter는 DNS 필터링에 맞게 단순화된 목록이며 기본 Adblock 도메인 규칙 문법 지원이 필요합니다. Guardian은 안전한 도메인 기반 규칙을 지원하고, 처리할 수 없는 고급 규칙은 무시합니다.
- 목록은 앱 실행 기기의 비공개 저장소에 캐시되며, 다운로드 검증이 실패하면 기존 정상 캐시를 유지합니다.

## DNS 자가 테스트

홈 또는 활동 화면의 DNS 차단 기능 테스트를 실행합니다.

1. doubleclick.net을 8.8.8.8:53으로 직접 질의하여 NXDOMAIN 차단 응답이 오는지 확인합니다.
2. example.com을 Guardian 가상 DNS로 질의하여 정상 NOERROR 응답이 오는지 확인합니다.
3. 결과가 실패하면 활동 화면의 DNS 통계와 마지막 패킷 진단을 확인합니다.

이 테스트는 기기의 실제 광고 노출 전부를 검증하지는 않지만, VPN TUN → DNS parser → rule matcher → 응답 패킷 경로를 확인하는 내부 기능 테스트입니다.

## 현재 제한 / 미지원

- 클라이언트 방향 TCP/53 DNS 가로채기
- DNS-over-HTTPS(DoH), DNS-over-TLS(DoT) 차단 및 앱 자체 DNS 터널 차단
- 목록에 없는 사용자 지정 DNS 서버 주소로 직접 전송된 질의 가로채기
- IPv6 확장 헤더 및 IP fragment 재조립
- 모든 IP 트래픽의 전체 VPN 프록시/전달
- 웹페이지 요소 숨김, 동일 도메인(first-party) 광고 제거, 앱 화면 내부 광고 제거
- 모든 광고가 차단되는 것을 보장하지 않음

DNS 필터는 광고·추적 도메인 질의를 막는 방식입니다. 웹페이지 요소를 숨기는 브라우저 확장이나 HTTPS 내부 콘텐츠를 수정하는 방식과는 다릅니다.

## 앱 화면 및 옵션

- 홈: 보호 상태, 시작/중지, DNS 자가 테스트, 통계 요약
- 보호: 현재 DNS 보호 범위와 미지원 범위
- 활동: DNS 질의/차단/전달/실패/무시, 최근 요청, 진단, 기록 초기화
- 필터: 사용자 차단 목록과 허용 목록
- 설정: DNS 업스트림 선택, AdGuard 필터 업데이트, 자동 업데이트, 선택형 보안 필터, 앱별 보호 제외

## 개발 및 검증

필요 환경: JDK 17, Android SDK Platform 35, Build Tools 35.0.0, Gradle 8.9.

로컬 테스트 명령: gradle -p android --no-daemon clean testDebugUnitTest

GitHub Actions push 실행은 Android JUnit 테스트만 수행합니다. 최종 APK는 개발 항목과 내부 테스트가 완료된 후 수동 릴리스 실행으로만 패키징합니다.

## 실기기 검증 경계

CI 테스트 통과는 소스 컴파일과 자동화 테스트 통과를 의미합니다. 실기기에서 VPN 승인/연결, 공용 DNS 직접 질의, 앱별 제외, Wi-Fi↔모바일 데이터 전환, 장시간 실행 및 광고 차단 범위는 별도로 확인해야 합니다. 실제 기기에서 실행하지 않은 항목은 완료로 보고하지 않습니다.
