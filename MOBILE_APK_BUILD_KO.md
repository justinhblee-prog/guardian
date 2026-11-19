# Guardian AdBlock Android — 설치 및 소스 패키지 안내

이 문서는 GitHub Actions에서 생성한 Guardian Android 개발 APK와 동일 커밋의 소스 패키지를 확인하는 절차입니다. Actions 산출물은 실행마다 버전과 체크섬이 달라지므로 과거 버전 번호나 해시를 최신 파일의 기준으로 사용하지 마십시오.

## APK 및 소스 패키지 받기

1. GitHub 저장소 `justinhblee-prog/guardian`의 **Actions** 탭에서 **Guardian AdBlock Android APK** workflow를 엽니다.
2. `main` 브랜치의 최신 성공 실행을 선택합니다. 실행의 commit SHA가 확인하려는 소스와 일치하는지 확인합니다.
3. 전체 workflow가 **Success**인지 확인한 뒤 Artifacts에서 `guardian-android-debug`를 다운로드합니다.
4. ZIP 안에 설치 APK, APK checksum 파일, 동일 커밋의 source ZIP, source checksum 및 한국어 검증 요약이 있는지 확인합니다. 실제 파일명과 버전은 해당 실행의 artifact manifest/summary를 기준으로 합니다.

파일 누락, workflow 실패, commit 불일치 또는 해시 불일치가 있으면 설치/배포하지 마십시오. Debug APK는 개발·검증용이며 Production Release가 아닙니다.

## SHA-256 확인 (Windows PowerShell)

압축을 푼 artifact 루트에서 실제 ZIP 안의 경로/파일명을 확인한 다음 실행합니다.

~~~powershell
Get-FileHash .\android\app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256
Get-FileHash .\release\Guardian-AdBlock-Source-<version>.zip -Algorithm SHA256
~~~

각 값은 동일 artifact의 checksum manifest에 기록된 값과 일치해야 합니다. 파일명은 workflow summary의 실제 산출물과 일치하도록 사용합니다.

## Android 설치 및 기본 확인

1. 검증된 `app-debug.apk`를 Android 기기에서 엽니다.
2. 설치가 차단되면 Android 안내에 따라 해당 파일 앱/브라우저의 '알 수 없는 앱 설치' 권한을 허용합니다.
3. Guardian을 설치하고 실행합니다.
4. **보호 시작**을 누르고 Android VPN 연결 승인 창에서 허용합니다.
5. 보호 상태와 DNS 질의·차단·전달·실패 통계를 확인합니다. VPN 아이콘이 표시된다는 사실만으로 DNS 차단 성공이 증명되는 것은 아닙니다.

## CI/에뮬레이터 검증 범위

Workflow 설정과 실행 결과에 따라 다음 항목을 확인합니다.

- Android unit tests 및 lint
- APK 조립 및 package/version/checksum 검사
- 이전 개발 APK에서의 업그레이드 설치
- Android API 에뮬레이터 설치·실행
- VPN 권한 승인 후 DNS 허용/차단 E2E
- APK와 소스 ZIP의 SHA-256 및 ZIP 무결성

에뮬레이터 성공은 실제 스마트폰·태블릿의 VPN/TUN 라우팅, 네트워크 전환, 장시간 안정성 또는 실제 광고 제거율을 증명하지 않습니다.

## 실기기 수용 테스트

- 보호 시작/중지 반복 및 앱/서비스 재시작 후 복구
- 차단 도메인, 허용 도메인 및 일반 도메인 조회
- Wi-Fi와 모바일 데이터 전환, 화면 잠금 및 재부팅 후 복구
- IPv4/IPv6 환경에서 DNS 질의·차단·전달·실패 통계 확인
- 앱별 VPN 제외 저장 및 실제 라우팅 반영
- 선택 앱 자동 일시중지/종료 후 재개 정책과 전체 VPN 보호 영향 확인
- Samsung Wallet/Android Auto 등 실제 기기 호환성 확인
- 오류가 발생하면 앱의 개인정보 제외 진단 보고서를 저장해 지원 절차로 전달

과거 보고된 `Library file not found`는 실기기 stack trace 없이 해결된 것으로 간주하지 않습니다.

## 지원 범위와 제한

- 개발 APK는 debug 서명본이며 Production 배포용이 아닙니다.
- Android 사용자 프로필당 VPN은 하나만 활성화할 수 있으므로 다른 VPN 앱과 충돌할 수 있습니다.
- Guardian은 DNS 기반 도메인 필터링을 수행합니다. 웹페이지 요소 숨김, 앱 내부 광고, 자체 DoH/DoT 우회 요청 및 이미 내려받은 콘텐츠 광고를 모두 차단한다고 보장하지 않습니다.
- IPv6 확장 헤더 및 IP fragmentation/reassembly 등 지원하지 않는 네트워크 조건이 있을 수 있습니다. 제품의 최신 기술 제한 문서를 확인하십시오.
- 운영키로 서명된 Production APK/AAB만 정식 배포에 사용합니다.

## Production 배포 채널

- **직접 설치 APK:** Production release workflow가 사용자 소유 운영 keystore로 생성하고 검증한 `guardian-android.apk`를 사용합니다.
- **Google Play AAB:** 별도 Play 배포 변형인 `bundleRelease` 산출물을 사용합니다. 직접 설치 APK를 AAB 대신 제출하지 않습니다.
- Production APK/AAB는 package ID, version, signer fingerprint, SHA-256, 설치/업그레이드 검증 증거를 모두 확인한 뒤 배포합니다.
