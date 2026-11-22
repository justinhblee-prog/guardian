# 최신 인수인계 기준 — 2026-11-17

이 섹션은 아래의 과거 버전별 안내보다 우선합니다.

- 저장소: justinhblee-prog/guardian, branch main.
- 현재 Android: com.guardian.adblock, versionCode 27, versionName 0.7.14-dev.
- main commit: 28abc77342f34898dd03c198fef942c0b49a7fe0.
- PR #78: 반응형 운영자 라이선스 관리 대시보드 병합 및 License API CI 성공.
- PR #79: APK와 동일 커밋의 전체 소스 ZIP, 두 산출물 SHA-256, ZIP 무결성 및 자동 artifact 감사 병합.
- Android CI [44060999851](https://github.com/justinhblee-prog/guardian/actions/runs/44060999851) 성공: unit tests, lint, APK build, 이전 버전 업데이트 설치, API 35 emulator VPN/DNS E2E. 런타임 로그: protection_active=true, dns_queries=8, dns_forwarded=7, dns_blocked=1, dns_ad_blocked=1, dns_failed=0.
- Artifact [guardian-android-debug](https://github.com/justinhblee-prog/guardian/actions/runs/44060999851), APK SHA-256 24db2c1db46a66d2117cb35f7541c893b3321ee7de46363b615fd1a585319c78, source ZIP SHA-256 a22d74b994b919e19de4125a719243786b76fa50dbb2d19d85200345afdcee17.
- 후속 감사 [44062116800](https://github.com/justinhblee-prog/guardian/actions/runs/44062116800)에서 APK/source 체크섬과 ZIP 무결성 검증 성공.
- Signed filter CI [44052664109](https://github.com/justinhblee-prog/guardian/actions/runs/44052664109), [44052665884](https://github.com/justinhblee-prog/guardian/actions/runs/44052665884), License API CI [44059072829](https://github.com/justinhblee-prog/guardian/actions/runs/44059072829) 성공.
- Production release 설정 검증 [44052664040](https://github.com/justinhblee-prog/guardian/actions/runs/44052664040) 성공. 실제 운영 keystore/secrets가 없어 production signing job은 skipped이며 production APK/AAB는 없습니다.
- 실기기 VPN/TUN·광고 제거율·운영 HTTPS/TLS 서버 검증은 미완료입니다. Windows EXE/설치 프로그램은 마지막 단계입니다.

## 다음 내부 개발 순서

1. Android 모바일/태블릿과 공통 필터·라이선스 기능의 남은 코드 작업을 마감합니다.
2. 운영 필터 배포와 License API/DB/TLS에 필요한 설정·스크립트를 안전하게 정리합니다. 운영 개인키는 저장소나 채팅에 저장하지 않습니다.
3. production signing workflow는 유지하되, 운영 keystore/secrets가 준비되기 전에는 정식 서명본을 만들지 않습니다.
4. 개발 작업 완료 후 실제 Android 기기 VPN/DNS, 네트워크 전환, Private DNS 및 광고 차단 수용 시험을 진행합니다.
5. Windows EXE/설치 프로그램과 PC 실환경 설치·복구 검증은 마지막에 진행합니다.
6. 플랫폼별 최종 패키지·체크섬·검증 보고서를 통합합니다.

개발 단계의 코드 회귀 테스트/CI는 계속 수행하되, 실제 기기·운영 서버 테스트는 개발 완료 전 단계의 대체 조건으로 사용하지 않습니다.
---

# Guardian AdBlock Android v0.7.4-dev — 설치 및 기기 수용 테스트

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
- Android는 클라이언트 DNS UDP/53 및 TCP/53, IPv4/IPv6 기본 헤더를 처리하고, 업스트림 전송은 UDP/TCP/DoT/DoH를 지원합니다. 단, 앱이 자체 TLS 연결로 직접 전송하는 DoH/DoT를 가로채는 기능, IPv6 확장 헤더/IP 조각화 재조립, 비DNS 트래픽 처리, 포괄적인 웹 요소·앱 내부 광고 제거는 지원하지 않습니다. 앱별 VPN 제외와 공식 DNS 필터 목록 갱신은 구현되어 있습니다.
- 이 버전은 개발/검증용입니다. 실기기와 실제 Windows 환경의 승인 테스트 전에는 일상 보호용으로 배포하지 마십시오.


## Kotlin DNS 핵심부 빠른 사전검증

JDK와 Kotlin compiler(`kotlinc`)가 설치된 환경에서는 프로젝트 루트에서 `./VERIFY_ANDROID_CORE.sh`를 실행해 Android DNS 순수 Kotlin 코드의 컴파일과 18개 스모크 검사를 먼저 수행할 수 있습니다. 이 검사는 Android SDK/Gradle 빌드 및 실기기 검증을 대체하지 않습니다.


## Chrome 확장판 최소 권한 승인
1. 확장 프로그램을 로드한 뒤 옵션 화면을 엽니다.
2. 필터 URL을 입력하고 `필터 업데이트`를 누릅니다.
3. Chrome 권한 대화상자에서 해당 HTTPS 필터 서버 접근을 허용합니다.
4. 다른 서버로 리디렉션되는 필터 URL은 거부되므로 최종 HTTPS 주소를 직접 입력합니다.
5. 실제 Chrome에서 권한 거부/허용, 필터 업데이트, 광고 요청 차단, 허용 목록 동작을 확인합니다.

## 0.7.4-dev 최신 상태 보충 (2026-11-14)

- Android TCP/53은 제한된 DNS TCP 세션 구현이며, 전체 TCP 스택이 아닙니다. 재전송/손실 복구는 제한적이고 IPv6 확장 헤더 및 IP 조각화는 지원하지 않습니다.
- DoT/DoH는 Guardian에서 지정한 업스트림 DNS 서버로 나가는 요청을 암호화합니다. 앱이 직접 사용하는 DoH/DoT 우회 연결을 차단하거나 가로채지 않습니다.
- WorkManager 필터 업데이트는 소스별 캐시 유효성/갱신 시각을 판단하며, 선택 목록 하나가 실패해 재시도할 때 성공한 최신 목록을 중복 다운로드하지 않습니다.
- 최신 CI [43570828960](https://github.com/justinhblee-prog/guardian/actions/runs/43570828960): 단위 테스트, lint, debug APK 조립, package/version 및 SHA-256 검사 성공. `com.guardian.adblock` versionCode 17 / `0.7.4-dev`.
- CI debug APK SHA-256: `d60df611f004d5114d9e1aaeb832a5837ce4287c5dd1b7c51c5909c8a4cfb577`. 실기기 검증은 미완료입니다.


## 0.7.5-dev 보충 — 2026-11-14

- IPv6 업스트림 주소 정규화에서 nullable host-address를 안전하게 처리합니다.
- 홈 화면에서 앱 자체 DoH/DoT 우회 미지원 범위를 명확하게 안내합니다.
- [CI 43571389815](https://github.com/justinhblee-prog/guardian/actions/runs/43571389815) 성공: Android unit tests, lint, assembleDebug, package/version, SHA-256 검사 통과.
- 패키지 versionCode 18 / `0.7.5-dev`; CI debug APK SHA-256 `f756ab65ce8d74cc7a7da45457b0cc2987b83acc83e9c993eab0a3f624492e1a`.


## 최종 산출물 패키지 생성 완료 — 2026-11-14

- 최종 CI 실행: [43572937308](https://github.com/justinhblee-prog/guardian/actions/runs/43572937308) — 성공. Android 단위 테스트, lintDebug, assembleDebug, 패키지명·버전 확인, APK checksum, 소스 패키징 및 artifact 업로드가 모두 통과했습니다.
- 커밋: `94bab42e1a9dd7e10b61fad52f2546fc075dbdcf`
- 앱: `com.guardian.adblock`, versionCode `20`, versionName `0.7.7-dev`.
- 설치 APK: `app-debug.apk`, 3,014,104 bytes. SHA-256: `4797b47bad0c92574e0b7f9b8bf347960c46e688c95be43ee6158b2d1a4a93e7`.
- 전체 소스 ZIP: `Guardian-AdBlock-Source-v0.7.7-dev.zip` (161,340 bytes, 98 entries; Android 소스·테스트·공유 규칙·워크플로·설명서 포함).
- 검증 요약: `release/VERIFICATION_SUMMARY_KO.md`.
- GitHub Actions artifact: `Guardian-AdBlock-Android-Full-Package-v0.7.7-dev`, artifact ID `14105371888`, archive SHA-256 `d5f4555603756f5b0cf97a6725ecc5ff942e98ede9c626ef91227bfa2cf12d3f`, 만료 예정 2026-12-14.
- APK는 Android debug 서명 빌드입니다. production 서명과 실기기 VPN/DNS 라우팅·Wi-Fi/모바일 전환·실제 광고 제거 수용 테스트는 수행하지 않았습니다. DNS 수준 차단 범위 및 앱 자체 DoH/DoT 우회 등 알려진 한계는 검증 문서에 명시했습니다.


# 0.7.7-dev 기준 실제 잔여 6단계 실행표 (2026-11-14)

이 절이 문서 앞부분의 0.7.4-dev 설치 안내보다 우선합니다. CI 자동화 성공과 실기기 수용 테스트를 구분합니다.

1. **소스/빌드 기준 고정** — 완료: main 최신 문서와 0.7.7-dev APK 빌드 커밋을 구분하고 README 및 RELEASE_STATUS_KO.md의 오래된 버전 표기를 정정했습니다.
2. **실기기 설치 및 VPN 승인** — 미완료: Android 물리 기기에서 APK 설치, VPN 승인, 보호 시작/중지, 재부팅 후 상태 확인이 필요합니다. CI 에뮬레이터/빌드만으로 대체하지 않습니다.
3. **실제 DNS 차단/허용 검증** — 미완료: 기기에서 차단 테스트 도메인의 NXDOMAIN, 허용 예외와 일반 도메인의 정상 해석, 업스트림 장애 시 SERVFAIL 및 앱별 제외를 확인해야 합니다.
4. **네트워크/수명주기 안정성** — 미완료: Wi-Fi↔모바일 데이터, 화면 잠금/해제, 비행기 모드, VPN revoke, 앱/프로세스 재시작 및 장시간 실행을 확인해야 합니다.
5. **결함 수정 및 회귀 검증** — 진행 기준: 2~4단계에서 관찰된 로그와 재현 절차를 근거로 수정하고 Android unit tests, lintDebug, assembleDebug 및 APK 체크섬을 다시 통과시킵니다. 관찰되지 않은 실기기 결함을 통과했다고 기록하지 않습니다.
6. **정식 서명 및 최종 패키지** — 미완료: 소유자가 안전하게 보관한 Android release keystore로 서명된 APK/AAB를 생성하고 서명 검증, 패키지/version 확인, SHA-256 및 설치/업데이트 호환성을 확인합니다. 서명 키나 비밀번호를 채팅/저장소에 올리지 않습니다.

## 자동화 검증 기준

- 2026-11-14 CI run 43572937308: Android 단위 테스트, lintDebug, assembleDebug, package/version, SHA-256 및 소스 패키지 업로드 성공.
- 해당 APK는 com.guardian.adblock, versionCode 20, versionName 0.7.7-dev의 debug 서명본입니다.
- 실기기 테스트 결과, production keystore 서명, 실제 광고 제거 성능은 위 CI에 포함되지 않습니다.
- 2~4단계는 연결된 실제 Android 기기와 기기에서 수집한 로그가 있어야 완료 판정할 수 있습니다. 6단계는 release keystore 없이는 production 서명 완료 판정할 수 없습니다.


## 0.7.8-dev 소스 보안 보강 (2026-11-14)
- DNS TCP 패킷 파서가 IPv4 reserved fragmentation flag 및 TCP reserved/NS 비트를 0이 아닌 경우 거부하도록 수정했습니다.
- 두 malformed header 유형에 대한 회귀 테스트를 추가했습니다.
- versionCode 21 / 0.7.8-dev.
- CI run 43573908206은 문서 반영 시점에 실행 중입니다. 성공 확인 전에는 검증 완료로 표시하지 않습니다.
