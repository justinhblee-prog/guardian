# Guardian AdBlock v0.6.3-dev — Android 빌드 안내

## 필요한 환경
- Android Studio 안정판
- JDK 17 이상
- Android SDK Platform 35 및 Android SDK Build-Tools
- Gradle Wrapper가 없는 프로젝트이므로 Android Studio 내 Gradle 설치 또는 시스템 Gradle 8.x

## 빌드
1. ZIP을 Windows PC에 압축 해제합니다.
2. `android` 폴더를 Android Studio에서 엽니다.
3. Gradle JDK를 JDK 17 이상으로 지정하고 Sync를 완료합니다.
4. `:app:testDebugUnitTest` 실행 후 `:app:assembleDebug` 실행.
5. APK 예상 위치: `android/app/build/outputs/apk/debug/app-debug.apk`.

PowerShell에서 시스템 Gradle을 사용할 경우:
```powershell
cd android
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

## 기기 검증 전 주의
- APK 설치 후 VPN 권한을 승인해야 합니다.
- Wi-Fi/모바일 데이터 전환, 차단/허용 규칙, 업스트림 DNS 장애 시 SERVFAIL, 화면 잠금/복귀를 시험하십시오.
- Android는 IPv4/IPv6 UDP DNS 질의를 처리합니다. 업스트림 UDP 응답이 잘린 경우에 한해 DNS-over-TCP로 재시도합니다. 클라이언트 방향 TCP DNS 가로채기, IPv6 확장 헤더/조각화 재조립, DoH/DoT 및 실기기 검증은 미완료입니다.
- 이 개발환경에서는 Android SDK/Gradle이 없어 APK를 생성하지 않았습니다. 위 APK 경로는 빌드 성공 후 생성되는 예상 경로입니다.