# 최신 Android 빌드 기준 — 2026-11-18

- 현재 main 기준: `com.guardian.adblock`, versionCode `27`, versionName `0.7.14-dev`.
- 최신 main 커밋: `1e5ec6c4c943e56c36accaac49220eecdbdec98f`.
- Android main CI [run 44270330488](https://github.com/justinhblee-prog/guardian/actions/runs/44270330488): 단위 테스트, lint, Debug APK 조립·패키지/버전·필터 asset·SHA-256 검사, Android API 35 에뮬레이터 설치/실행·업그레이드 및 VPN DNS E2E 전체 성공.
- 해당 run의 APK는 검증용 Debug 빌드이며 운영 배포용 Release 서명본이 아닙니다. PR 검증 run에서는 artifact 업로드가 생략될 수 있으므로 실제 전달 시에는 성공한 main push 또는 수동 실행의 artifact를 사용합니다.
- PR #146의 안전한 시작 실패 스택 요약 기능은 main에 병합됐고, 해당 변경의 전체 CI 및 에뮬레이터 검증 [run 44270330488](https://github.com/justinhblee-prog/guardian/actions/runs/44270330488)이 통과했습니다.
- 운영 Release APK/AAB는 소유자 관리 production keystore와 GitHub Actions secrets/variables가 실제 설정된 경우에만 생성합니다. 비밀키나 비밀번호를 저장소/채팅에 올리지 않습니다.
- CI 성공은 실기기 VPN/DNS 라우팅 및 실제 광고 제거 효과를 증명하지 않습니다. 실기기에서 보고된 오류의 실제 호출 위치도 실기기 진단 기록 없이는 확정하지 않습니다.

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
