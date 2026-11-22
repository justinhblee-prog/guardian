# Guardian AdBlock v0.6.1-dev 인수인계

- 기준일: 2026-11-13
- 기반: 사용자가 제공한 `Guardian_AdBlock_v0.6.0-dev_FINAL(2).zip`
- Android 버전: `0.6.1-dev` / versionCode `8`

## 이번 반영
1. Android 앱 화면에 사용자 차단 목록과 허용 목록 입력란을 추가했습니다.
2. 사용자 규칙을 앱 내부 SharedPreferences에 저장합니다. 각 목록은 최대 1,000개이며, ASCII DNS 도메인 형식만 허용하고 중복은 정리합니다.
3. 보호 실행 중 규칙 저장 시 VPN 터널을 재시작하지 않고 규칙만 다시 읽습니다.
4. 보호 시작/중지 및 시작 실패 메시지를 앱 화면에 표시하도록 상태 저장과 앱 내부 브로드캐스트를 추가했습니다.
5. DomainFilter의 차단/허용 집합을 단일 불변 스냅샷으로 교체해 업데이트 중간에 섞인 규칙이 읽히지 않게 했습니다.
6. 잘못된 규칙 세트의 재적재가 실패하면 기존 규칙은 유지됩니다.

## 검증 결과
- Python 테스트: 18 passed
- Browser Node.js 테스트: 11 passed
- Kotlin 순수 DNS 코어 컴파일/스모크: 13 assertions passed
- Python compileall / JavaScript syntax / JSON / Android Manifest XML: passed
- Android JUnit: 16개 작성, 미실행
- Android Gradle 빌드와 APK 생성: 미실행
- Android 실기기 테스트: 미실행
- Windows 시스템 통합 테스트와 EXE 설치 패키지: 미실행

현재 워크스페이스에는 Gradle CLI와 Android SDK가 없고 Gradle 배포 서버 DNS 조회도 실패했습니다. 따라서 APK를 생성했다고 보고하지 않습니다.

## 다음 우선순위
1. Android Studio / Android SDK Platform 35 / JDK 17+ 환경에서 `android` 폴더를 열고 Gradle Sync를 실행합니다.
2. `:app:testDebugUnitTest`를 실행해 16개 JUnit 테스트를 확인합니다.
3. `:app:assembleDebug`를 실행해 `android/app/build/outputs/apk/debug/app-debug.apk`를 생성합니다.
4. 실제 Android 기기에서 VPN 승인, 차단/허용 도메인, 앱 재실행, 화면 잠금, Wi-Fi/모바일 전환, 규칙 실시간 변경을 확인합니다.
5. IPv6와 DNS-over-TCP 지원 및 Windows EXE/설치 프로그램은 별도 기능 작업과 실제 OS 검증이 필요합니다.

상세한 검증 범위와 제한 사항은 `VERIFICATION.md`, 설치·테스트 순서는 `NEXT_STAGE_GUIDE_KO.md`, Android 사용법은 `README_ANDROID_KO.md`를 참조합니다.

## v0.6.2-dev 후속 변경
- 업스트림 DNS 타임아웃 또는 응답 검증 실패 시 Android VPN이 요청을 무응답 폐기하지 않고 DNS SERVFAIL을 반환합니다.
- DNS 오류 응답 생성기는 유효한 단일 질문만 포함하며 opcode/RD/CD를 보존하고 QR/RA 및 오류 코드를 설정합니다.
- Kotlin 단독 검증 15개, Python 18개, 브라우저 회귀 11개 통과.
- Android Gradle/JUnit 및 APK 빌드는 Gradle/Android SDK 부재로 미실행입니다. 실기기 및 Windows 실환경 테스트도 미실행입니다.