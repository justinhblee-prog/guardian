# 최신 Android 빌드 기준 — 2026-11-15

- 현재 개발 기준: `com.guardian.adblock`, versionCode `21`, `0.7.8-dev`.
- PR #41 Android CI run `43696306115` 성공: unit tests, lintDebug, assembleDebug, API 35 emulator 설치·실행, package/version/SHA-256.
- PR #43 Android CI run `43696645579` 성공. Production Release 설정 run `43696645556` 성공, production signing은 운영 secrets/keystore 미설정으로 의도적 skipped.
- PR #44 License API CI run `43697583427` 성공.
- 위 세 PR은 검증 성공했으나 아직 main 미병합입니다.
- push/PR CI의 debug APK는 검증 산출물이며 production 서명본이 아닙니다.
- 최종 소스 ZIP/APK artifact는 최종 main build의 artifact 목록과 checksum을 확인한 뒤에만 전달합니다. 과거 0.7.7-dev artifact를 현재 0.7.8-dev 산출물로 혼동하지 않습니다.
- production APK/AAB 서명은 수동 workflow와 소유자 관리 release keystore/secrets가 필요합니다. 비밀키/비밀번호를 저장소나 채팅에 올리지 않습니다.
- CI 성공은 실기기 VPN/DNS 라우팅 및 실제 광고 제거 효과를 증명하지 않습니다.

---
---

# Guardian AdBlock Android 0.6.5-dev — 빌드 안내

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
