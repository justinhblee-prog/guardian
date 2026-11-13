# Guardian AdBlock v0.6.3-dev 최종 인수인계

작성일: 2026-11-13

## 릴리스 상태

이 패키지는 v0.6.2-dev 소스를 기반으로 IPv6 DNS 처리와 업스트림 DNS-over-TCP 재시도를 추가한 통합 개발 릴리스입니다. 소스와 자동화 검증은 패키징했지만, Android SDK/Gradle이 없는 현재 빌드 환경에서 Android APK 및 Windows EXE를 만들 수 없었습니다. 따라서 설치 실행파일을 포함한 상용/운영 최종판이라고 표시하지 않습니다.

## v0.6.3-dev 주요 구현

1. Android VPN에 IPv6 가상 DNS 주소 `fd13:37::2/128` 및 전용 경로 추가
2. IPv6 UDP/53 패킷의 길이, next-header, 목적지 포트 검증
3. IPv6 UDP checksum 필수 검증 및 응답 checksum 생성
4. IPv6 확장 헤더 및 fragment는 미지원으로 명시적 거부
5. 업스트림 UDP DNS 응답에 TC 비트가 있으면 보호된 TCP/53 연결로 재시도
6. TCP DNS 응답 길이, transaction ID, QR/opcode, 질문 섹션 및 질문 바이트 검증
7. Android 앱 버전 `0.6.3-dev`, versionCode `10`
8. 브라우저 확장 manifest 버전 `0.6.3`

## 검증 결과

- Python DNS 엔진: 6 PASS
- Windows 보호 안전성 테스트: 12 PASS
- Browser extension Node.js 회귀 테스트: 11 PASS
- Kotlin 순수 DNS 핵심부: 18 assertions PASS
- Python compileall: PASS
- Browser JavaScript syntax: PASS
- JSON 및 AndroidManifest XML 파싱: PASS

위 테스트는 로컬 자동화 검증입니다. 실제 Android 기기, Chrome 브라우저, Windows 네트워크 어댑터에서의 수용 테스트를 의미하지 않습니다.

## 아직 실행하지 못한 출시 게이트

- Android Gradle/JUnit 16개: 미실행
- Android 앱 전체 컴파일 및 APK 생성: 미실행
- Android 실기기 VPN/DNS/IPv6 테스트: 미실행
- Windows 서비스/관리자 권한 DNS 변경 및 복구 테스트: 미실행
- Windows EXE/설치 프로그램: 미생성
- 클라이언트 방향 TCP DNS 가로채기: 미구현
- IPv6 확장 헤더/fragment 재조립: 미구현
- DoH/DoT 필터링: 미구현

## Android 빌드

필요: Android Studio, JDK 17+, Android SDK Platform 35, Android Gradle Plugin 8.7.3 및 Kotlin plugin 2.0.21 의존성을 내려받을 수 있는 네트워크.

1. `android` 폴더를 Android Studio에서 엽니다.
2. Gradle Sync를 완료합니다.
3. `:app:testDebugUnitTest`를 실행합니다.
4. `:app:assembleDebug`를 실행합니다.
5. APK 예상 위치: `android/app/build/outputs/apk/debug/app-debug.apk`.
6. 실제 기기에서 VPN 권한, IPv4/IPv6 DNS, 차단/허용, 업스트림 장애, 네트워크 전환 및 화면 잠금 후 동작을 확인합니다.

이 경로는 빌드 후 예상 산출 위치이며 현재 ZIP에 APK가 들어 있다는 뜻이 아닙니다.

## Windows

Windows 호스트에서 Python 의존성 설치 후 `VERIFY_WINDOWS_TESTS.ps1`을 먼저 실행하십시오. 시스템 DNS 변경은 별도 관리자 승인·백업·복구 절차가 필요합니다. 이 패키지에는 Windows EXE 설치 프로그램이 포함되어 있지 않습니다.

## 최종 판단

개발 소스와 자동화 테스트 패키지는 생성했습니다. APK와 Windows 설치파일, 실제 기기/OS 통합 검증이 완료되어야 사용자 설치용 정식 최종판으로 승격할 수 있습니다.