# Guardian AdBlock Android — 0.7.3-dev

Guardian AdBlock은 Android `VpnService` 기반 DNS 필터링 앱입니다. Android의 VPN 가상 인터페이스로 전달되는 DNS 질의를 로컬 규칙으로 판정하고, 허용된 질의는 선택한 업스트림 DNS로 전달합니다.

> 개발·배포 정책: CI의 디버그 APK 조립 성공은 빌드 산출물 검증이지, 실제 기기에서의 광고 차단 수용 테스트나 서명된 정식 릴리스 완료를 뜻하지 않습니다. 실기기 검증이 끝나기 전에는 최종 릴리스로 표시하지 않습니다.

## 현재 구현 범위

- Android `VpnService` 기반 IPv4/IPv6 UDP DNS 처리
- 가상 DNS 주소(`10.13.37.2`, `fd13:37::2`)로 지정된 시스템 DNS 질의 필터링
- 가상 DNS 엔드포인트로 들어오는 클라이언트 DNS-over-TCP(53) 처리: TCP 세션, 길이 프레이밍, 순서가 맞는 스트림, 재전송 및 FIN 처리
- 업스트림 전송 방식: UDP/53, TCP/53, DNS-over-TLS(DoT/853), DNS-over-HTTPS(DoH/443)
- UDP 업스트림 응답이 잘린 경우 TCP DNS로 재시도
- DoT/DoH TLS 인증서 호스트명 검증 및 SNI 설정
- DoH HTTP 응답의 상태 코드, 헤더 중복·형식, 본문 길이, chunked framing 및 trailer 검증
- UDP DNS 응답은 안전한 페이로드 한도(1232바이트)를 넘으면 TC 응답으로 대체
- 차단 도메인에는 NXDOMAIN, 업스트림 실패에는 SERVFAIL 응답
- 업스트림 DNS 선택: Cloudflare, Google, Quad9, AdGuard DNS 및 사용자 지정 IPv4/IPv6 주소
- 필터 프로필: AdGuard DNS Filter, 1Hosts Lite, OISD Big, HaGeZi Normal, HaGeZi Pro
- 선택형 HaGeZi Threat Intelligence 악성코드·피싱 목록
- 선택형 AWAvenue Android 광고 SDK 목록
- 도메인 규칙, 예외, `$important`, `$badfilter`, hosts, Guardian `block=` / `allow=` 규칙 지원
- 사용자 차단·허용 도메인 및 앱별 VPN 제외
- 필터 다운로드 HTTPS 강제, 제한된 안전 리디렉션, 다운로드 크기·규칙 수 검증, 임시 파일 기반 원자적 캐시 교체 및 이전 정상 캐시 보존
- WorkManager 네트워크 제약 기반 주기 업데이트, 사용자 설정에 따른 예약 취소 및 수동·백그라운드 다운로드 직렬화
- DNS 활동·차단·전달·실패 통계, 최근 요청, 업스트림 연결 테스트 및 진단
- Android Private DNS 설정 진단 표시(기기 전역 Private DNS 설정을 앱이 변경하지는 않음)

## 필터 출처

- AdGuard DNS Filter: https://adguardteam.github.io/HostlistsRegistry/assets/filter_1.txt
- HaGeZi Threat Intelligence: https://adguardteam.github.io/HostlistsRegistry/assets/filter_44.txt
- AWAvenue Ads Rule: https://adguardteam.github.io/HostlistsRegistry/assets/filter_53.txt
- 추가 프로필은 AdGuard Hostlists Registry의 공식 배포 파일을 사용합니다.

Guardian은 DNS 필터링에 안전하게 적용할 수 있는 도메인 기반 규칙을 해석합니다. 문맥 의존·재작성 등 지원하지 않는 규칙은 임의로 변환하지 않고 제외 수에 반영합니다.

## 화면과 주요 옵션

- **홈:** 보호 상태, 보호 시작·중지, DNS 규칙 엔진 자체 테스트, 필터 준비 상태 및 통계
- **보호:** 보호 범위, Private DNS 진단, 업스트림 연결 테스트 및 미지원 범위 안내
- **활동:** DNS 질의·차단·전달·실패·무시 통계, 최근 요청, 진단 및 기록 초기화
- **필터:** 프로필 선택, 보안·Android 앱 광고 목록, 자동 업데이트 및 업데이트 주기, 사용자 차단·허용 규칙
- **설정:** 업스트림 DNS 주소·전송 방식·TLS 호스트명, 사용자 지정 DNS 및 앱별 VPN 제외

공용 DNS 직접 포착은 선택형 고급 기능이며 기본값은 꺼짐입니다. IP 주소 기반 경로이므로 해당 DNS 서버 IP로 향하는 비DNS 연결도 VPN에 들어올 수 있습니다. 이 기능은 포트 53 트래픽만을 분리해서 포착하는 기능이 아닙니다.

## DNS 자체 테스트의 범위

홈 화면의 **DNS 규칙 엔진 테스트**는 현재 로드된 규칙의 도메인 매칭과 NXDOMAIN 응답 생성 여부를 검사합니다. VPN TUN을 통한 실제 네트워크 질의나 광고가 표시되지 않는지를 시험하는 테스트는 아닙니다.

보호 화면의 업스트림 연결 테스트는 설정된 업스트림 전송 방식으로 실제 DNS 질의를 전송하고 응답을 검증합니다. 이 테스트 역시 타 앱 트래픽의 광고 차단 수용 테스트와는 구분됩니다.

## 현재 제한 및 미검증 범위

- 앱 자체 DoH/DoT 터널, VPN 외부로 직접 전송되는 앱별 암호화 DNS는 가로채지 못할 수 있습니다.
- 일반 웹 트래픽의 프록시·전달 기능은 제공하지 않습니다.
- IPv6 확장 헤더 및 IP fragment 재조립은 지원하지 않습니다.
- 웹페이지 요소 숨김, 동일 도메인(first-party) 광고 제거, 앱 화면 내부 광고 제거는 DNS 필터만으로 보장할 수 없습니다.
- 모든 광고·추적 요청이 차단되는 것을 보장하지 않습니다.
- 실제 기기에서 VPN 승인·연결, 필터 다운로드, DNS 경로, 앱 제외, Wi-Fi↔모바일 데이터 전환, Private DNS 조합 및 장시간 안정성은 별도 수용 테스트가 필요합니다.

## 개발 환경 및 검증

필요 환경: JDK 17, Android SDK Platform 35, Build Tools 35.0.0, Gradle 8.9.

로컬 테스트:

```bash
gradle -p android --no-daemon clean testDebugUnitTest
gradle -p android --no-daemon lintDebug
gradle -p android --no-daemon assembleDebug
```

현재 테스트 소스 인벤토리: **19개 Kotlin 테스트 파일 / 127개 JUnit 테스트 메서드**.

### 최신 CI 검증

- 실행: https://github.com/justinhblee-prog/guardian/actions/runs/43562045324
- 소스 커밋: `8537fe013e45b7d901410606867feabf6cf96533`
- Android 단위 테스트: 성공
- Android lint: 성공
- Debug APK 조립: 성공
- 패키지 식별자·버전 확인: 성공 (`com.guardian.adblock`, versionCode `16`, versionName `0.7.3-dev`)
- APK 파일 존재 및 SHA-256 체크섬 생성: 성공
- 이 push 실행에서는 APK 업로드 단계가 조건에 따라 건너뛰어졌습니다. 따라서 이 실행의 APK 바이너리를 다운로드할 수 있는 릴리스 아티팩트가 생성된 것은 아닙니다.

## APK 및 검증의 의미

CI에서 생성되는 `app-debug.apk`는 Android 디버그 키로 서명된 설치 검증용 APK입니다. 정식 배포용 키로 서명된 릴리스 APK와는 다릅니다. 실기기 설치 및 광고 차단 수용 테스트, 릴리스 서명 절차를 마치기 전에는 정식 최종 APK라고 표시하지 않습니다.

CI 통과는 소스의 자동화 테스트·정적 검사·APK 조립과 메타데이터 확인을 뜻합니다. 실제 기기에서의 VPN 동작이나 광고 차단 효과가 확인되었다는 뜻은 아닙니다.
