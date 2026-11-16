# Guardian AdBlock — Android 0.7.13-dev / Windows client

## 과거 통합 기록 (이후 최신 검증 기록 우선)

### Android
- 패키지 `com.guardian.adblock`, versionCode `23`, versionName `0.7.10-dev`.
- APK: 3,070,507 bytes; SHA-256 `04958ebbe6a4d57d6e0dc603cfd1b36af0085ad82cb307183e98e73c76f1b739`.
- [Android APK + source + verification artifact](https://github.com/justinhblee-prog/guardian/actions/runs/43781110905).
- API 35 에뮬레이터에서 v0.7.9-dev → v0.7.10-dev 업데이트 설치 및 앱 실행, Android unit tests, Lint, APK 생성 및 filter asset 검사 통과.
- Debug 개발용 서명본입니다. 운영 keystore/secrets가 없어 production APK/AAB는 생성하지 않았습니다.
- Android 실기기 VPN/TUN 라우팅, 실제 광고 제거, 네트워크 전환, 장시간 안정성 및 태블릿 실기기 검증은 미실시입니다.

### Windows
- 공통 DNS 필터 엔진, IPv4/IPv6 UDP·TCP loopback DNS proxy, upstream 검증 및 truncated UDP의 TCP fallback을 구현했습니다.
- Windows 데스크톱 UI, DNS 어댑터 설정 백업·복구/rollback, 부팅 시 자동 시작 백그라운드 DNS 작업, 서명 필터 manifest updater를 구현했습니다.
- 22개 Windows 엔진 테스트가 Ubuntu 및 Windows 2022에서 통과했습니다([CI run 43785636135](https://github.com/justinhblee-prog/guardian/actions/runs/43785636135)).
- Windows EXE 및 설치 프로그램의 빌드·설치·실행·제거 smoke test는 진행 중입니다([run 43785686270](https://github.com/justinhblee-prog/guardian/actions/runs/43785686270)). 성공 확인 전까지 Windows 패키지를 검증 완료로 간주하지 않습니다.
- 현재 Windows 보호는 DNS 기반이며 WFP 커널 드라이버가 아닙니다. 실제 Windows PC의 어댑터 변경, 재부팅 복구 및 VPN/보안 제품 공존성은 미검증입니다.
- 서명 필터 자동 업데이트는 HTTPS 서버 주소와 운영 pinned 공개키가 설정되어야 활성화됩니다. 현재 운영 서버/키는 미설정입니다.

상세 Windows 빌드 안내: [WINDOWS_BUILD_KO.md](WINDOWS_BUILD_KO.md)

## 개발 및 배포 정책

CI의 디버그 APK 조립·에뮬레이터 성공은 실제 기기의 광고 차단 수용 테스트나 서명된 정식 릴리스 완료를 뜻하지 않습니다. 검증된 항목과 미검증 항목을 구분해 기록합니다. 운영 개인키·keystore·비밀값을 저장소나 채팅에 올리지 않습니다.

Guardian AdBlock은 Android `VpnService` 기반 DNS 필터링 앱입니다. Android의 VPN 가상 인터페이스로 전달되는 DNS 질의를 로컬 규칙으로 판정하고, 허용된 질의는 선택한 업스트림 DNS로 전달합니다.

> 개발·배포 정책: CI의 디버그 APK 조립 성공은 빌드 산출물 검증이지, 실제 기기에서의 광고 차단 수용 테스트나 서명된 정식 릴리스 완료를 뜻하지 않습니다. 실기기 검증이 끝나기 전에는 최종 릴리스로 표시하지 않습니다.

## 멀티 디바이스 지원 계획

Windows PC, Android 스마트폰·태블릿을 하나의 필터/라이선스 체계로 제공하는 설계입니다. Android 태블릿은 동일 APK의 반응형 UI로 지원하고, Windows는 별도 DNS 기반 설치형 개발 클라이언트가 구현되었습니다. Windows 설치 패키지 검증, 태블릿 실기기 최적화, 운영 서버/공개키 및 관리 콘솔은 계속 검증·구축해야 합니다.

상세 설계: [멀티 디바이스 로드맵](docs/MULTI_DEVICE_ROADMAP_KO.md)

## 현재 구현 범위

- Android `VpnService` 기반 IPv4/IPv6 UDP DNS 처리
- 가상 DNS 주소(`10.13.37.2`, `fd13:37::2`)로 지정된 시스템 DNS 질의 필터링
- 가상 DNS 엔드포인트로 들어오는 클라이언트 DNS-over-TCP(53) 처리: TCP 세션, 길이 프레이밍, 순서가 맞는 스트림, 재전송 및 FIN 처리
- 업스트림 전송 방식: UDP/53, TCP/53, DNS-over-TLS(DoT/853), DNS-over-HTTPS(DoH/443)
- UDP 업스트림 응답이 잘린 경우 TCP DNS로 재시도
- A real upstream DNS query is validated before installing VPN routes; if all configured/fallback resolvers fail, Guardian leaves the normal network path untouched instead of activating a broken DNS tunnel.
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


## 2026-11-14 기준 정정 — v0.7.7-dev

이 절은 이전 문서에 남아 있는 v0.7.3-dev 및 v0.7.4-dev 기록보다 우선합니다.

- 최신 소스 커밋: `c8f60ef3f7f5af44e332defe570d4f475951162a` (릴리스 문서 정정 커밋)
- APK를 실제 빌드한 소스 커밋: `94bab42e1a9dd7e10b61fad52f2546fc075dbdcf`
- APK: `com.guardian.adblock`, versionCode `20`, versionName `0.7.7-dev`
- APK SHA-256: `4797b47bad0c92574e0b7f9b8bf347960c46e688c95be43ee6158b2d1a4a93e7`
- GitHub Actions: https://github.com/justinhblee-prog/guardian/actions/runs/43572937308
- 자동화 검증: Android 단위 테스트, lintDebug, assembleDebug, 패키지 식별/버전, 체크섬 및 소스 ZIP 패키징 성공
- 서명: Android debug 서명. Production 서명 아님.
- 미검증: 실기기 설치 및 VPN 승인, 실제 DNS 경로/광고 차단 효과, 네트워크 전환, 장시간 안정성.

주의: CI 자동화 성공은 실기기 수용 시험의 대체가 아닙니다. 정식 출시 완료로 판정하지 않습니다.

## 2026-11-15 개발 진행 기록

- Android 태블릿 반응형 레이아웃 및 서명 필터 manifest 버전/무결성 표시가 추가되었습니다.
- 라이선스 서명 검증기, 설치별 기기 바인딩 및 검증된 entitlement 저장/재검증이 구현되었습니다. 앱 설정은 검증된 토큰만 PRO로 표시하며 DNS 보호는 라이선스와 독립적으로 유지합니다.
- `server/license-api`에 Node.js/PostgreSQL 라이선스 API와 모바일 관리자 화면 소스가 추가되었습니다. API 전용 CI의 구문 검사 및 암호학 테스트가 통과했습니다.
- Android 설정 화면에 라이선스 키 활성화·수동 갱신·기기 해제 UI와 HTTPS API 클라이언트를 연결했습니다. 다만 운영 서버/DB/키 배포 및 실제 서버 end-to-end 검증, 결제 연동과 실기기 검증은 아직 남아 있습니다.
- 최신 Android 자동화 검증은 현재 진행 중이며, 최종 APK는 전체 개발 및 검증 완료 전에는 배포 산출물로 만들지 않습니다.

라이선스 서버 운영 준비: [서버 설치 및 API 안내](server/license-api/README_KO.md)


Android 0.7.9-dev adds categorized DNS block counters (ad/tracker/other), an activity bar chart, and a clearly labeled estimated data-savings figure. Category labels are heuristic domain-name classifications; estimated savings assumes 24 KB per blocked request and is not a measurement of actual transferred bytes.

## Windows 노트북 운영 서버

라이선스 API와 PostgreSQL을 개인 Windows 노트북에서 구동하는 설치·시작·중지·백업 스크립트가 추가되었습니다. 설치 안내: [Windows 노트북 운영 서버](server/license-api/windows/README_KO.md). 기본 API 바인딩은 localhost 전용이며, HTTPS 및 인증된 사설 접속 경로를 구성하기 전에는 외부 공개하지 않습니다. 현재는 저장소에 배포 도구만 추가된 상태이며 실제 노트북 설치·운영 검증은 아직 수행되지 않았습니다.


## 2026-11-16 개발/검증 정정 — Android v0.7.12-dev 및 노트북 운영 구성

이 절은 위의 이전 버전·CI 기록보다 우선합니다.

### Android 빌드
- APK: `com.guardian.adblock`, versionCode `25`, versionName `0.7.12-dev`
- APK를 실제 빌드한 커밋: `ad2aeb286617cfddbafe18bd8f9088f9f4ade11a`
- GitHub Actions: https://github.com/justinhblee-prog/guardian/actions/runs/43798215766
- Android 단위 테스트, lint, assembleDebug, 이전 개발 APK에서 업데이트 설치, API 35 에뮬레이터 실행, APK 메타데이터 및 SHA-256 검증: 통과
- APK SHA-256: `fd108cb542f67acd70b15f38ef1b666bc4eb798a7f77116caf4ac9959a0131dd`
- 서명: 기존 설치 업데이트를 지원하는 Android 개발용 서명. Production 서명 아님.
- 실기기 VPN 승인·실제 DNS 트래픽·광고 제거 효과는 아직 미검증.

Android 설정에서 라이선스 API 주소와 서명 필터 배포 주소를 각각 HTTPS로 변경할 수 있습니다. 필터 배포 주소만 바꾸어도 서명 신뢰가 바뀌지는 않으며, APK에 포함된 필터 공개키와 서버의 서명 개인키가 반드시 한 쌍이어야 합니다.

### Windows 노트북 운영 서버
- `Setup-GuardianServer.ps1`: 라이선스 RSA 3072-bit 키, 필터 RSA 3072-bit 키, DB 비밀번호 및 관리자 비밀값을 생성합니다. 필터 서명 개인키는 현재 Windows 사용자 DPAPI로 보호하며, 비밀 폴더와 `.env`는 Windows ACL로 제한합니다.
- 공개키 Base64 파일은 `server/license-api/secrets/guardian-license-public.x509.b64` 및 `guardian-filter-public.x509.b64`입니다.
- `Build-GuardianAndroid-Debug.ps1`: 노트북에서 생성한 공개키를 빌드 프로세스에 주입해 단위 테스트·Lint·Debug APK를 빌드합니다. JDK 17, Android SDK, Gradle 8.9가 필요합니다. APK와 체크섬은 `server/license-api/outputs/`에 생성됩니다.
- `Start-All-GuardianServices.ps1` / `Stop-All-GuardianServices.ps1`: 라이선스 API/PostgreSQL 및 필터 배포 서버를 함께 시작·중지합니다.
- `Invoke-GuardianFilterUpdate.ps1`와 `Register-GuardianFilterUpdateTask.ps1`: DPAPI 보호키로 필터를 검증·서명하고 매일 예약 업데이트합니다. Node.js 22 이상과 실제 HTTPS 도메인 설정이 필요합니다.
- PostgreSQL 백업은 PowerShell 텍스트 파이프라인을 통하지 않는 custom-format dump로 저장하며 백업 폴더 접근 권한을 제한합니다.
- 운영 API와 필터 서버의 호스트 포트는 기본적으로 localhost 전용입니다. 실제 휴대폰 원격 연결에는 도메인, HTTPS 및 인증된 터널/역방향 프록시 구성이 별도로 필요합니다.

### 서버 및 배포 CI
- 라이선스 API/PostgreSQL 통합 테스트 및 Windows PowerShell 스크립트 구문 검사: 통과. 최신 실행: https://github.com/justinhblee-prog/guardian/actions/runs/43799678983
- 서명 필터 배포 서버 테스트 및 Docker Compose 구성 검사: 통과. 실행: https://github.com/justinhblee-prog/guardian/actions/runs/43798651189
- 실제 사용자 노트북에서 Docker 설치·서버 실행, 외부 HTTPS 연결, 라이선스 발급, 예약 업데이트, 휴대폰 E2E는 아직 수행되지 않았습니다.

## 2026-11-16 최신 Android 검증 — v0.7.13-dev

이 절이 Android APK 관련 이전 버전 및 CI 기록보다 우선합니다.

- 버전: `com.guardian.adblock`, versionCode `26`, versionName `0.7.13-dev`.
- 소스 커밋: `4ae0da811d579f2a1495fbc89c1e94641cd5348d`.
- GitHub Actions 실행 #704: https://github.com/justinhblee-prog/guardian/actions/runs/43815516892
- Android unit tests, lint, 이전 개발 APK 업그레이드 설치, 현재 APK 조립, DNS 필터 라이브러리 확인, API 35 에뮬레이터 설치·실행, 패키지 식별/버전 및 APK 체크섬, 소스/검증 요약 패키징, Artifact 업로드: 모두 성공.
- 검증 Artifact: `guardian-android-debug` (2,999,902 bytes, ZIP SHA-256 `00cd2311ea2fa23c64dbb1dc7e993012e18bc10b9d81c2219cb2238e2a729eb2`).
- APK: 3,080,949 bytes; SHA-256 `32a549ac197101c8b514bb39b8e31fa21030caa4ae587a7ab81b6ac296571e71`.
- 서명: 기존 개발 설치 업데이트 호환용 Android debug 인증서. Production 서명 아님.
- 이 검증은 API 35 에뮬레이터 기준입니다. 실제 Android 스마트폰/태블릿의 VPN 권한·TUN DNS 경로·광고 차단 수용 테스트, Wi-Fi↔모바일 전환 및 장시간 안정성은 별도 미검증입니다.
- 노트북 운영 서버 설치/외부 HTTPS 공개, 실제 라이선스 발급/결제, 실제 공개 필터 배포 경로 연결도 미검증입니다.

### 다음 Android 출시 차단 항목

1. 실기기 VPN 승인 및 DNS 질의/차단/허용 동작 확인.
2. Wi-Fi↔모바일 데이터 전환, IPv4/IPv6 및 Private DNS 조합, 다른 VPN/앱 제외 동작 확인.
3. 필터 다운로드·서명 검증·업데이트·오류 복구를 운영 공개키/HTTPS 서버에 연결해 end-to-end 검증.
4. 운영 keystore와 보호된 CI secrets, 운영 API/필터 HTTPS 주소 및 공개키를 구성한 뒤 별도 production APK/AAB 생성.
5. 라이선스 정책/결제 연동은 수동 라이선스 발급과 실제 유료 결제를 구분해 완료 판정.

개발용 APK 다운로드는 위 Actions 실행의 `guardian-android-debug` Artifact에서 가능합니다. Artifact 보존 만료일은 2026-11-30입니다.
