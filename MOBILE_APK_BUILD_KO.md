# Guardian AdBlock Android — 모바일 설치 및 소스 패키지 안내 (0.7.20-dev)

이 문서는 Android 스마트폰·태블릿용 개발 APK와 동일 커밋의 전체 소스 ZIP을 받는 방법을 설명합니다. 이 버전은 개발용 debug 서명본이며 production 릴리스가 아닙니다.

## APK + 전체 소스 패키지 받기

1. GitHub 저장소 justinhblee-prog/guardian의 **Actions** 탭에서 **Guardian AdBlock Android APK** workflow를 엽니다.
2. main 브랜치의 성공한 실행을 선택하거나 **Run workflow**로 main에서 수동 실행합니다.
3. 전체 workflow가 **Success**인지 확인한 뒤 Artifacts에서 guardian-android-debug를 다운로드합니다.
4. 압축을 풀면 다음 항목이 포함됩니다.
   - android/app/build/outputs/apk/debug/app-debug.apk: 설치용 Android APK
   - android/app/build/outputs/apk/debug/SHA256SUMS.txt: APK 전용 SHA-256
   - release/Guardian-AdBlock-Source-v0.7.20-dev.zip: APK를 만든 동일 커밋의 전체 소스
   - release/SHA256SUMS.txt: APK와 전체 소스 ZIP의 SHA-256
   - release/VERIFICATION_SUMMARY_KO.md: 빌드 및 소스 패키지 검증 요약

소스 ZIP에는 Android 앱 코드와 테스트, 공통 DNS 규칙, License API/관리자 화면, 필터 배포 서버, 운영 스크립트, CI workflow 및 문서가 포함됩니다. Git 저장소의 정확한 APK 빌드 커밋에서 생성되며 .git 폴더와 GitHub Actions 실행 산출물은 포함하지 않습니다.

## SHA-256 확인 (Windows PowerShell)

압축을 푼 artifact의 루트 폴더에서 실행합니다.

~~~powershell
Get-FileHash .\android\app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256
Get-FileHash .\release\Guardian-AdBlock-Source-v0.7.20-dev.zip -Algorithm SHA256
~~~

각 결과가 release\SHA256SUMS.txt의 APK 및 Source ZIP 항목과 일치해야 합니다. APK 자체의 android\app\build\outputs\apk\debug\SHA256SUMS.txt와도 APK 해시가 일치해야 합니다. 파일 누락, 해시 불일치 또는 Actions 실패가 있으면 해당 패키지를 설치/배포하지 마십시오.

## Android 설치

1. 검증된 app-debug.apk를 Android 기기에서 엽니다.
2. 설치가 차단되면 Android 안내에 따라 해당 파일 앱/브라우저의 '알 수 없는 앱 설치' 권한을 허용합니다.
3. Guardian AdBlock을 설치하고 실행합니다.
4. **보호 시작**을 누르고 Android VPN 연결 승인 창에서 허용합니다.
5. 보호 화면과 활동 화면에서 DNS 질의·차단·전달·실패 통계를 확인합니다. 터널이 켜졌다는 표시만으로 광고 차단이 검증된 것은 아닙니다.

## 개발용 자동 검증 범위

- Android unit tests 및 lint
- APK 조립, 패키지명·versionCode·versionName 검사
- 이전 개발 APK에서 현재 APK로의 업데이트 설치
- Android API 35 에뮬레이터 설치·실행
- VPN 권한 승인 후 실제 DNS 허용·차단 E2E 확인
- APK와 전체 소스 ZIP의 SHA-256 및 ZIP 무결성 검사

에뮬레이터 성공은 실제 스마트폰·태블릿의 VPN/TUN 라우팅, 네트워크 전환 또는 실제 광고 제거율을 증명하지 않습니다. 실기기 수용 검증은 별도 단계입니다.

## 기기 수용 테스트 항목 (개발 완료 후)

- 차단 목록 도메인, 사용자 허용 목록 도메인 및 일반 도메인 조회
- 보호 시작/중지 반복 및 다른 VPN 앱과의 충돌
- Wi-Fi와 모바일 데이터 전환, 화면 잠금, 기기 재부팅 후 복구
- Private DNS가 자동/호스트명으로 설정된 경우 우회 가능성 확인
- IPv4/IPv6 환경에서 DNS 질의·차단·전달·실패 수치 확인
- 앱별 VPN 제외 설정과 백그라운드 장시간 유지 확인

## 지원 범위와 제한

- APK는 Android debug 서명본이며 Play 스토어 배포용 production 서명본이 아닙니다.
- Android는 사용자 프로필당 VPN을 하나만 활성화할 수 있으므로 다른 VPN 앱은 중지해야 할 수 있습니다.
- Guardian 업스트림은 UDP/53, TCP/53, DNS-over-TLS(DoT/853), DNS-over-HTTPS(DoH/443)를 지원합니다.
- 앱이 VPN을 우회해 직접 전송하는 DoH/DoT 요청은 가로채지 못할 수 있습니다. Android Private DNS 및 앱별 동작에 대한 별도 검증이 필요합니다.
- DNS-over-TCP(53)는 지원하지만 IPv6 extension header와 IP fragmentation/reassembly는 지원하지 않습니다.
- DNS 필터링 자체는 웹페이지 요소를 숨기지 않습니다. Guardian Browser는 지원되는 CSS 코스메틱 규칙을 자체 WebView 안에서만 적용합니다. 외부 브라우저/다른 앱의 화면 요소, scriptlet/procedural 규칙, HTTPS 내부 트래픽 및 앱 자체 UI 광고 제거는 지원하지 않으며 first-party 광고 차단을 보장하지 않습니다.
- 정식 production APK/AAB는 운영 keystore, 보호된 CI secrets, 운영 공개키·API 주소 및 서버 배포 설정이 완료된 후 별도로 생성해야 합니다.

## 정식 배포 채널 선택

정식 출시 시에는 목적에 맞는 별도 산출물을 사용합니다.

- **직접 설치 APK:** Guardian 공식 GitHub Release의 `guardian-android.apk`입니다. Android에서 알 수 없는 앱 설치를 허용하면 앱 내부에서 서명과 SHA-256을 확인한 업데이트를 제안할 수 있으며, 설치 자체는 Android 확인 화면에서 사용자가 승인합니다.
- **Google Play AAB:** `bundleRelease`에서 생성한 `app-release.aab`입니다. 이 변형은 `REQUEST_INSTALL_PACKAGES`와 GitHub 자체 업데이트 기능을 포함하지 않으며 Play Store가 업데이트를 관리합니다. 직접 설치 APK를 Play Console에 제출하지 않습니다.
- **앱 전체 조회 권한:** 앱별 보호 제외를 위해 `QUERY_ALL_PACKAGES`를 사용합니다. Play 등록 시 권한 선언과 심사가 필요하며 승인 여부는 보장되지 않습니다.

정식 production 서명 APK/AAB는 운영 keystore와 운영 서버 설정이 준비되고 릴리스 검증이 통과한 경우에만 배포합니다.
