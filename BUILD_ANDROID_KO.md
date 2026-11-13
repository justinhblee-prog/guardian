# Guardian AdBlock Android 0.6.4-dev — 빌드 안내

## 필수 환경

- JDK 17
- Android SDK Platform 35
- Android SDK Build Tools 35.0.0
- Gradle 8.9
- 인터넷 연결(Gradle/Android 빌드 의존성 설치)

## 자동화 빌드

프로젝트 루트에서 실행:

```bash
gradle -p android --no-daemon clean testDebugUnitTest assembleDebug
```

## Android Studio

1. 프로젝트의 `android` 폴더를 Android Studio에서 엽니다.
2. Gradle JDK를 17로 지정합니다.
3. Gradle Sync를 완료합니다.
4. `:app:testDebugUnitTest`를 실행합니다.
5. `:app:assembleDebug`를 실행합니다.

APK 위치:
`android/app/build/outputs/apk/debug/app-debug.apk`

## GitHub Actions

저장소 main에 Android 소스가 반영되면 CI가 자동으로 실행됩니다. 테스트/빌드, VPN manifest 권한과 special-use 선언, launcher icon 파일, APK package ID/version, SHA-256을 확인하고 Debug APK artifact를 업로드합니다.

## 설치 및 기기 확인

1. APK를 Android 기기에 복사해 설치합니다.
2. Guardian AdBlock을 실행하고 보호 시작을 누릅니다.
3. Android VPN 승인 창에서 허용합니다.
4. 보호 시작/중지, 차단/허용 도메인, 네트워크 전환, 화면 잠금 후 복귀를 확인합니다.

CI 성공만으로 실기기 동작을 보증하지 않습니다. DoH/DoT, client-side TCP DNS, IPv6 extension header/fragment reassembly는 미지원입니다.
