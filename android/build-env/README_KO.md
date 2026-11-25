# Guardian Android 검증 환경

이 디렉터리는 Docker가 설치된 Windows 11 PC에서 Android SDK/Gradle 검증을 실행할 수 있도록 합니다.

## 포함 도구

- Ubuntu 24.04 / OpenJDK 17
- Android command-line tools
- Android SDK Platform 35 / Build Tools 35.0.0 / Platform Tools
- Gradle 8.9

버전은 .github/workflows/android-apk.yml의 JDK/SDK/Gradle 구성에 맞췄습니다.

## Windows 11에서 실행

Docker Desktop이 실행 중이고 Linux 컨테이너 모드인지 확인한 뒤, 저장소 루트에서 PowerShell을 열어 다음 한 줄을 실행합니다.

    powershell -ExecutionPolicy Bypass -File .\android\build-env\run-validation.ps1

이미 Docker가 설치되어 있으므로 별도 Android Studio나 로컬 Android SDK 설치는 필요하지 않습니다. 첫 실행은 기본 이미지, Android SDK 및 Gradle 배포판을 다운로드하므로 인터넷 연결과 디스크 공간이 필요합니다. Gradle 의존성 다운로드에도 인터넷이 필요할 수 있습니다.

## 자동 수행 및 결과 전달

1. Docker 빌드 환경 이미지 생성
2. Android JUnit: testDebugUnitTest
3. Android Lint: lintDebug
4. Debug APK: assembleDebug
5. APK 파일 크기와 SHA-256 확인
6. 로그, 요약, 성공한 경우 APK를 ZIP으로 묶음

결과는 android/build-env/results/guardian-android-validation-날짜시간.zip 에 생성됩니다. 실패하더라도 로그 ZIP은 생성되며, 실패한 빌드에서 이전 APK를 성공 산출물로 잘못 포함하지 않습니다. 이 ZIP을 이 대화에 첨부하면 제가 로그를 분석하고 수정 작업을 이어갈 수 있습니다.

## 보안과 검증 범위

- Debug APK만 빌드하며 릴리스 서명 키/비밀값은 사용하지 않습니다.
- GitHub Actions를 실행하거나 Actions 설정/과금을 변경하지 않습니다.
- 환경 파일을 추가한 것 자체는 테스트 통과 증거가 아닙니다. ZIP 안의 실제 Gradle 로그와 APK 해시를 확인해야 합니다.
- 에뮬레이터·실기기 VPN/DNS E2E는 별도 기기 검증입니다.
