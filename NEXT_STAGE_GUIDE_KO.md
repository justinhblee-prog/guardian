# Guardian AdBlock v0.6.3-dev — 다음 단계 및 실행 안내

## 1. 현재 패키지에서 확인된 내용
- 공통 규칙 파일 `shared/filter_rules.txt`를 Windows DNS 엔진과 Android VPN 앱이 함께 사용합니다.
- 허용 규칙(`allow=`)은 차단 규칙(`block=`)보다 우선합니다.
- Windows DNS 변경은 명시적 `APPLY` 입력이 있어야 수행됩니다.
- Windows DNS 변경 전 DPAPI 백업 복호화와 로컬 DNS 필터의 실제 NXDOMAIN 응답 확인을 요구합니다.
- 여러 어댑터 중 일부 변경이 실패하면 저장된 백업으로 자동 복구를 시도합니다.
- Android 공통 규칙이 없거나 유효한 차단 규칙이 없으면 VPN 보호를 시작하지 않습니다.
- Android 앱에서 사용자 차단/허용 목록을 각각 최대 1,000개까지 저장할 수 있으며, 보호 중 저장하면 실행 중인 필터 규칙을 즉시 재적재합니다.
- 규칙 재적재는 하나의 불변 규칙 스냅샷으로 교체해 allow/block 적용 중간 상태가 노출되지 않도록 합니다.

## 2. Windows 개발환경 검증
PowerShell에서 프로젝트 폴더를 연 뒤 실행:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\VERIFY_WINDOWS_TESTS.ps1
```

위 스크립트는 Python 테스트와 컴파일만 수행하며, 네트워크 어댑터 DNS 설정을 변경하지 않습니다.

## 3. Windows 보호 기능을 실제로 시험할 때
**관리자 권한이 필요하며, 네트워크가 끊길 수 있습니다. 먼저 복구 수단을 준비하십시오.**

1. Python 및 `windows_engine/requirements-windows.txt` 의존성을 설치합니다.
2. `dns_engine/config.json` 및 `shared/filter_rules.txt`를 확인합니다.
3. 관리자 PowerShell에서 `python windows_engine/windows_protection.py backup`을 실행합니다.
4. `python windows_engine/windows_protection.py write-config`를 실행합니다.
5. Windows 서비스 호스트를 설치·시작하고 `127.0.0.1:53`에서 필터가 응답하는지 확인합니다.
6. `python windows_engine/windows_protection.py apply-ipv4`를 실행하고 `APPLY`를 입력합니다.
7. 차단 도메인 NXDOMAIN, 허용/일반 도메인 정상 조회, 재부팅 후 서비스 복구를 확인합니다.
8. 시험 종료 시 `python windows_engine/windows_protection.py restore` 후 `RESTORE`를 입력합니다.

Windows 보호 엔진은 IPv6 DNS가 설정된 어댑터에서 우회 가능성을 막기 위해 활성화를 거부합니다. Android는 가상 IPv6 DNS 주소(`/128`)를 추가하고 IPv6 UDP/53 패킷을 필터링합니다. IPv6 확장 헤더와 조각화 패킷 재조립은 아직 지원하지 않습니다.

## 4. Android 빌드 및 기기 검증
1. Android Studio 최신 안정판, JDK 17+, Android SDK Platform 35를 준비합니다.
2. `android` 폴더를 Android Studio에서 엽니다.
3. Gradle Sync 후 `:app:testDebugUnitTest`를 실행합니다.
4. `:app:assembleDebug`를 실행하거나 `BUILD_ANDROID.bat` / `BUILD_ANDROID.ps1`을 실행합니다.
5. APK를 실제 Android 기기에 설치하고 VPN 권한을 승인합니다.
6. 차단 도메인, 일반 도메인, 허용 예외, Wi-Fi/모바일 데이터 전환, 화면 잠금 후 지속성, 앱 강제 종료/재시작을 시험합니다.

APK 예상 경로: `android/app/build/outputs/apk/debug/app-debug.apk`

## 5. 현재 범위 밖 / 출시 전 필수
- Android JUnit 실행, APK 빌드, 실기기 검증은 이 개발환경에서 실행되지 않았습니다.
- Windows 실환경 DNS 변경/복구 및 Windows 서비스 설치 검증도 실행되지 않았습니다.
- Windows EXE 설치 프로그램은 포함하지 않았습니다.
- Android 클라이언트 방향 TCP DNS 가로채기, DoH/DoT, IPv6 확장 헤더/조각화 재조립, 앱별 제외, 원격 필터 목록 갱신, 포괄적인 상용 광고 차단 목록은 미구현입니다. Android 업스트림 DNS 응답이 UDP에서 잘리면 TCP로 재시도합니다.
- 이 버전은 개발/검증용입니다. 실기기와 실제 Windows 환경의 승인 테스트 전에는 일상 보호용으로 배포하지 마십시오.


## Kotlin DNS 핵심부 빠른 사전검증

JDK와 Kotlin compiler(`kotlinc`)가 설치된 환경에서는 프로젝트 루트에서 `./VERIFY_ANDROID_CORE.sh`를 실행해 Android DNS 순수 Kotlin 코드의 컴파일과 18개 스모크 검사를 먼저 수행할 수 있습니다. 이 검사는 Android SDK/Gradle 빌드 및 실기기 검증을 대체하지 않습니다.


## Chrome 확장판 최소 권한 승인
1. 확장 프로그램을 로드한 뒤 옵션 화면을 엽니다.
2. 필터 URL을 입력하고 `필터 업데이트`를 누릅니다.
3. Chrome 권한 대화상자에서 해당 HTTPS 필터 서버 접근을 허용합니다.
4. 다른 서버로 리디렉션되는 필터 URL은 거부되므로 최종 HTTPS 주소를 직접 입력합니다.
5. 실제 Chrome에서 권한 거부/허용, 필터 업데이트, 광고 요청 차단, 허용 목록 동작을 확인합니다.