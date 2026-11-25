# Guardian Android 검증 환경

이 디렉터리는 Android SDK/Gradle이 설치되지 않은 PC에서도 동일한 Android 검증 환경을 만들 수 있도록 Docker 기반 빌드 환경을 제공합니다.

## 포함 도구

- Ubuntu 24.04
- OpenJDK 17
- Android command-line tools
- Android SDK Platform 35
- Android Build Tools 35.0.0
- Android Platform Tools
- Gradle 8.9

버전은 현재 .github/workflows/android-apk.yml의 JDK/SDK/Gradle 설정과 맞췄습니다.

## Windows 11에서 실행

Docker Desktop이 설치되어 있고 Linux 컨테이너 모드여야 합니다. 저장소 루트에서 PowerShell로 실행합니다.

    powershell -ExecutionPolicy Bypass -File .\android\build-env\run-validation.ps1

첫 실행은 Android SDK와 Gradle 배포판을 내려받으므로 인터넷 연결과 충분한 디스크 공간이 필요합니다. Gradle 의존성 다운로드에도 인터넷 연결이 필요할 수 있습니다.

## 수행 작업

1. Android 환경 및 SDK 설치 상태 출력
2. gradle -p android --no-daemon clean testDebugUnitTest
3. gradle -p android --no-daemon lintDebug
4. gradle -p android --no-daemon assembleDebug
5. APK 존재·크기·SHA-256 확인

APK 결과물은 저장소의 android/app/build/outputs/apk/debug/app-debug.apk에 생성됩니다.

## 보안 및 범위

- Debug APK만 생성합니다. 릴리스 서명 키나 라이선스 비밀값을 컨테이너에 넣지 않습니다.
- Docker 컨테이너는 호스트 저장소를 작업 디렉터리로 마운트하므로 빌드 산출물이 PC에 남습니다.
- 이 환경을 추가한 것 자체는 테스트 통과나 APK 생성의 증거가 아닙니다. 실제 실행 로그와 산출물을 확인해야 합니다.
- 이 구성은 GitHub Actions를 실행하거나 설정·과금을 변경하지 않습니다.
- 에뮬레이터와 실기기 VPN/DNS E2E는 별도의 Android Emulator/기기 환경에서 수행해야 합니다.
