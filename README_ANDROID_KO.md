# Guardian AdBlock Android 0.7.28-dev

## 제품 개요

Guardian은 Android `VpnService`를 사용해 기기 안에서 DNS 질의를 필터링하는 로컬 DNS 보호 앱입니다. 별도 VPN 서버나 사용자 계정은 사용하지 않습니다.

- 패키지: `com.guardian.adblock`
- 최소 Android: API 26
- compile/target SDK: 35
- 빌드 JDK: 17
- 현재 버전: `0.7.28-dev` (versionCode 41)

## 현재 구현 기능

### DNS 보호 엔진
- Android VPN 권한 승인 후 로컬 TUN을 구성하고 보호 시작/중지
- 시스템 가상 DNS 주소를 기본 경로로 사용
- IPv4/IPv6 UDP DNS 질의 파싱, 응답 패킷 생성 및 IPv6 UDP checksum 처리
- 차단 도메인은 NXDOMAIN, 업스트림 장애는 SERVFAIL 응답
- Cloudflare, Google, Quad9, AdGuard DNS 및 사용자 지정 IPv4/IPv6 숫자 주소를 업스트림으로 사용
- 보호된 업스트림 UDP 소켓, 응답 발신지·transaction ID·질문 검증
- 업스트림 UDP 응답이 잘린 경우 TCP/53으로 재시도
- VPN 경로를 적용하기 전에 실제 업스트림 DNS 질의를 사전 검증하며, 모든 후보 DNS가 응답하지 않으면 VPN 라우트를 활성화하지 않아 일반 인터넷 연결을 유지
- 1232바이트를 초과하는 UDP DNS 응답은 IPv6 최소 MTU를 고려해 TC(truncated) 응답으로 축소
- VPN 시작/중지 경합 제어 및 Android 프로세스 회수 후 sticky service 복구
- 사용자가 시작 중 보호 중지를 누른 경우에도 저장된 자동 복구 상태를 해제
- 패킷 처리 루프가 중단되면 활성 상태를 해제하고 실패 사유 기록

### 필터와 사용자 옵션
- 공식 DNS 필터 프로필: AdGuard DNS Filter, 1Hosts Lite, OISD Big, HaGeZi Normal, HaGeZi Pro
- PRO 전용 HaGeZi Threat Intelligence 악성코드·피싱 도메인 목록 (무료 버전에서는 잠금 안내 표시)
- 기본 활성화된 AWAvenue Android Ads Rule SDK 도메인 목록 (신규 설치 시 자동 다운로드)
- 사용자 차단/허용 도메인 목록(각 최대 1,000개)
- 앱별 VPN 보호 제외 설정
- 공용 DNS 직접 질의 포착은 고급 선택 옵션이며 기본값은 꺼짐
- 앱 실행 시 만료 목록 확인 + WorkManager를 이용한 네트워크 연결 시 주기적 백그라운드 업데이트
- 6/12/24시간, 7일 주기 설정 및 자동 업데이트 예약 즉시 반영
- 다운로드 크기·유효 규칙 수 검증, 임시 파일 교체 및 기존 정상 캐시 보존
- 프로필/캐시 불일치 또는 보안 목록 미준비 시 잘못된 필터 조합으로 보호가 시작되지 않도록 차단

### 앱 화면
- 홈 상단 좌측 Guardian 브랜드, 우측 유진컨설팅 회사 로고
- 설정 화면의 제작자/개발사 및 연락처 표기
- Android 13 이상 알림 권한을 요청하고 보호 중 지속 알림을 표시
- 홈 보호 대시보드와 실시간 DNS 통계
- 보호 범위 및 제한 안내
- 필터 프로필·보안 옵션·사용자 규칙 관리
- 활동 기록, 최근 DNS 질의 및 패킷 진단
- 업스트림 DNS 및 앱별 보호 제외 설정
- Guardian 내부 WebView 브라우저와 CSS 기반 코스메틱 규칙 적용 옵션
- 어두운 프리미엄 스타일의 다크 UI

## DNS 테스트의 의미

홈/활동 탭의 **DNS 규칙 엔진 테스트**는 다음 항목을 앱 내부에서 검사합니다.

1. 현재 로드된 규칙에서 `doubleclick.net` 차단 여부
2. NXDOMAIN 응답 생성기의 RCODE가 3인지
3. `example.com`이 차단되지 않는지

Android는 VPN 앱 자신이 만든 소켓을 자체 TUN 경로에서 제외합니다. 따라서 앱 소켓으로 가상 DNS IP에 질의하는 방식은 실제 VPN 경로 검증이 아닙니다. 이 테스트는 규칙 엔진과 응답 생성기만 확인하며 실제 네트워크/TUN 통과를 증명하지 않습니다.

## 설치 및 실행

1. APK 설치 후 앱을 실행합니다.
2. 필터 탭에서 사용할 프로필을 선택하고 필터 목록을 업데이트합니다.
3. Android 앱 광고 SDK 필터는 기본 활성화되며 목록 다운로드와 검증이 완료됐는지 확인합니다. 악성코드·피싱 보호는 현재 앱 구현에서 모든 사용자가 사용할 수 있습니다(향후 PRO 라이선스 게이트 복원 여부는 별도 제품 정책으로 결정).
4. 홈에서 보호 시작을 누르고 Android VPN 권한을 승인합니다.
5. 활동 탭에서 실제 DNS 질의·차단·전달·실패 통계를 확인합니다.

## 빌드

GitHub Actions의 `Guardian AdBlock Android APK` 워크플로는 PR과 push에서 Android 단위 테스트, lint, debug APK 조립·에뮬레이터 설치/실행·패키지명·버전·SHA-256 검증을 수행합니다. 성공한 `main` push와 수동 `workflow_dispatch`에서는 설치 APK, 동일 커밋 전체 소스 ZIP, 두 파일의 SHA-256 및 한국어 검증 요약을 `guardian-android-debug` artifact로 업로드합니다. PR 검증은 빌드/에뮬레이터 검사만 수행하고 artifact는 업로드하지 않습니다.

로컬 빌드 환경:
- JDK 17
- Android SDK Platform 35 / Build Tools 35.0.0
- Gradle 8.9

명령:
```bash
gradle -p android --no-daemon clean testDebugUnitTest
gradle -p android --no-daemon assembleDebug
```

APK 출력:
`android/app/build/outputs/apk/debug/app-debug.apk`

## Production release signing

Debug APK 검증에는 별도 키가 필요하지 않습니다. 정식 배포는 두 산출물로 분리합니다.

- **직접 설치용 APK:** `assembleDirect`로 빌드하며 `REQUEST_INSTALL_PACKAGES`를 포함합니다. 사용자가 다운로드 후 Android 설치 확인 화면에서 직접 승인하고, 정식 GitHub Release 앱 내 업데이트 기능을 사용할 수 있습니다.
- **Google Play용 AAB:** `bundleRelease`로 빌드하며 `REQUEST_INSTALL_PACKAGES`와 GitHub 자체 업데이트 기능을 포함하지 않습니다. Play Store가 업데이트를 관리합니다. Play 정책은 이 권한을 앱 자체 업데이트 용도로 허용하지 않습니다.
- 두 배포 변형은 동일한 보호 엔진을 사용하되, 직접 설치용만 GitHub stable updater를 표시합니다. 개발 `debug` APK의 개발 업데이트 기능은 별도입니다.

두 정식 변형은 개인 운영 keystore가 반드시 필요하며, 키가 설정되지 않은 상태에서는 `assembleDirect`, `assembleRelease` 및 `bundleRelease`가 의도적으로 실패합니다.

1. 보안된 PC에서 RSA 3072비트 keystore를 생성합니다.

```bash
keytool -genkeypair -v -keystore guardian-release.jks -keyalg RSA -keysize 3072 -validity 10000 -alias guardian-release
```

2. `guardian-release.jks`를 저장소 밖의 암호화된 보관 위치에 보관하고 별도 백업합니다. Git, APK, 공유 ZIP 또는 채팅에 올리지 않습니다.
3. 빌드 환경에 다음 환경 변수를 안전하게 설정합니다.

```text
GUARDIAN_RELEASE_STORE_FILE=guardian-release.jks
GUARDIAN_RELEASE_STORE_PASSWORD=<keystore password>
GUARDIAN_RELEASE_KEY_ALIAS=guardian-release
GUARDIAN_RELEASE_KEY_PASSWORD=<key password>
```

`GUARDIAN_RELEASE_STORE_FILE`의 상대 경로 기준은 `android/app` 모듈 디렉터리입니다. CI에서는 위 값을 GitHub Actions Secrets에 안전하게 등록한 뒤 release job에 주입해야 합니다. 추가로 Repository Variables에 `GUARDIAN_MANIFEST_BASE_URL`, `GUARDIAN_MANIFEST_PUBLIC_KEY`, `GUARDIAN_LICENSE_PUBLIC_KEY`, `GUARDIAN_LICENSE_API_BASE_URL`을 설정해야 합니다. `GUARDIAN_FILTER_APPROVALS_JSON`은 `Prepare-GuardianProductionRelease.ps1`이 실제 로컬 `sources.json`에서 source ID, licenseId, 명시적 승인 여부 및 승인 참조를 추출해 자동 등록합니다. 이를 수동으로 승인 상태로 바꾸거나 예시 값을 복사하지 마세요. production workflow는 모든 source의 승인 플래그와 추적 가능한 참조를 다시 검사하고, 값이 없거나 승인되지 않은 source가 있으면 Release 빌드/게시 전에 중단합니다. 전용 `guardian-filter-data` 브랜치가 없을 때도 중단합니다. 실제 배포 버전도 `GUARDIAN_VERSION_NAME`(예: `1.0.0`)과 `GUARDIAN_VERSION_CODE`(현재 개발 versionCode보다 큰 양의 정수)로 명시해야 하며, 현재 개발 빌드 `0.7.28-dev` / `41`보다 큰 값인 `42`부터 정식 버전에 사용할 수 있습니다. 준비 스크립트와 CI 모두 개발 versionCode 이하의 정식 버전을 거부합니다. 배포 워크플로는 manifest/라이선스 URL의 HTTPS 여부와 두 공개키의 DER/Base64 형식 및 RSA 2048·3072·4096비트 크기를 확인하고, 설정이 없으면 production release를 중단합니다. APK는 `apksigner`로, AAB는 `jarsigner`/`keytool`로 서명을 각각 검증하고, 두 산출물의 인증서 SHA-256 지문이 제공된 keystore 인증서와 동일한지 확인합니다. APK/AAB SHA-256, 서명 검증 기록, 동적 버전명이 반영된 소스 ZIP 및 한국어 검증 요약을 artifact로 묶습니다. 작업 종료 시 runner 작업공간의 keystore 파일도 제거합니다. 실제 운영 키를 저장소에 커밋하거나 로그에 출력하지 마세요.

4. 아래 명령으로 서명된 release APK/AAB를 빌드합니다.

```bash
gradle -p android --no-daemon clean testDebugUnitTest testDirectUnitTest testReleaseUnitTest assembleDirect assembleRelease bundleRelease
```

Release 서명 설정과 키 파일 존재 여부는 Gradle task에서 검사합니다. 현재 운영 keystore는 아직 제공/설정되지 않았으므로 production-signed APK/AAB를 만들었다고 간주하지 않습니다.

**개발 빌드에서 첫 정식 빌드로 전환:** Debug APK는 개발용 인증서로 서명되고 Release APK는 별도의 운영 keystore로 서명되므로, Android는 개발 APK 위에 정식 APK를 덮어 설치하도록 허용하지 않습니다. 첫 정식 설치 전 개발판의 사용자 지정 규칙 등 필요한 설정을 별도로 기록하고 개발판을 제거한 뒤 정식 APK를 새로 설치해야 합니다. 앱 제거 시 로컬 앱 데이터는 삭제됩니다. 이후 동일한 운영 keystore로 서명한 정식 버전끼리는 정상 업그레이드할 수 있습니다.

## 직접 설치와 Google Play 배포의 권한 분리

- `android/app/src/main/AndroidManifest.xml`에는 `QUERY_ALL_PACKAGES`가 남아 있습니다. 앱별 보호 제외는 런처 아이콘이 없는 앱·시스템 앱도 검색해야 하는 기기 보안 기능이므로 전체 설치 앱 조회를 사용합니다. 앱 목록은 사용자의 동의 후 기기 내부에서만 사용하며 광고·분석·서버 업로드에 사용하지 않습니다.
- Google Play는 `QUERY_ALL_PACKAGES`를 민감 권한으로 제한합니다. 보안/백신 앱 등 전체 앱 가시성이 핵심 기능에 필요한 경우 권한 선언 심사를 신청할 수 있으나, Guardian의 승인 여부는 Play 심사 전까지 보장되지 않습니다.
- `REQUEST_INSTALL_PACKAGES`는 직접 설치용 `direct` 변형 및 개발 `debug` 변형에만 선언합니다. Play 제출용 `release` AAB에는 이 권한이 없고 GitHub 자체 업데이트 UI/실행 경로도 노출하지 않습니다.
- 직접 배포용 stable APK는 GitHub 정식 Release에서 내려받은 APK의 SHA-256·패키지·버전·서명 지문을 검증한 뒤 Android 사용자 승인 설치 화면을 엽니다. Play 버전은 Play Store 업데이트를 사용합니다.
- Play Console 정책은 `REQUEST_INSTALL_PACKAGES`를 앱 자체 업데이트에 사용하는 것을 허용하지 않습니다. 직접 설치 APK를 Play에 업로드하지 않고, Play 제출에는 `bundleRelease`로 만든 권한 제거 AAB만 사용합니다.
- 공식 정책: [QUERY_ALL_PACKAGES](https://support.google.com/googleplay/android-developer/answer/10158779), [REQUEST_INSTALL_PACKAGES](https://support.google.com/googleplay/android-developer/answer/12085295).

## 정식 버전 앱 내 자동 업데이트

- 직접 설치용 정식 APK만 `vX.Y.Z` 태그의 GitHub 정식 배포를 조회합니다. Play AAB에는 자체 업데이트 UI가 없고 Play Store가 업데이트를 관리합니다. 개발 prerelease와 draft는 안정판 앱 업데이트 대상에서 제외합니다.
- 정식 배포 워크플로는 운영 서명 APK를 `guardian-android.apk`로 함께 게시하고, 패키지 ID·versionName·versionCode·APK SHA-256·운영 서명 인증서 SHA-256을 담은 `guardian-android.json`을 생성합니다.
- 직접 설치용 정식 앱은 실행 시 하루 한 번 안정판 업데이트를 확인하고, 설정에서 수동 확인도 지원합니다. 새 버전이 있으면 APK를 자동 다운로드한 뒤 SHA-256, 패키지 ID, 버전, APK 인증서 및 설치된 앱의 인증서를 비교합니다.
- 검증 후 Android 패키지 설치 화면을 열며, **최종 설치는 Android가 표시하는 사용자 승인 절차를 거칩니다.** 일반 Android 앱은 보안상 사용자 동의 없이 자기 자신을 조용히 교체 설치할 수 없습니다.
- 같은 운영 keystore로 서명된 이후 정식 버전은 사용자 데이터와 설정을 유지하며 앱 내 업그레이드할 수 있습니다.
- 개발판은 개발 서명 인증서로 서명되므로 첫 정식판으로 전환할 때는 기존 개발판을 제거하고 정식 APK를 새로 설치해야 합니다. 앱 제거 시 로컬 데이터가 삭제되므로 사용자 규칙 등 필요한 데이터는 먼저 별도 보관해야 합니다.
- 운영 keystore가 없는 PR/CI 테스트에서는 임시 서명만 사용하며, 실제 정식 업데이트 자산은 생성·게시하지 않습니다.

## 명확한 제한 및 미검증 사항

- 현재 필터링은 시스템 가상 DNS를 통한 IPv4/IPv6 UDP/53 및 TCP/53 DNS 질의를 처리합니다.
- DoH/DoT 직접 우회 요청은 Guardian의 DNS 필터로 가로채지 못합니다. IPv6 UDP/TCP DNS는 Hop-by-Hop, Routing, Destination Options, AH 등 제한된 확장 헤더 체인을 최대 8개/2KB까지 파싱하지만 Fragment 헤더와 IP 조각화 재조립은 지원하지 않습니다. TCP/53은 제한된 세션 처리이며 재전송·패킷 손실 복구까지 구현한 완전한 TCP 스택은 아닙니다.
- 공용 DNS 직접 포착은 IP 단위 라우트이므로 DNS가 아닌 트래픽까지 TUN에 들어올 수 있어 기본 꺼짐입니다. Guardian은 DNS 이외 트래픽을 중계하지 않습니다.
- DNS 필터는 도메인 수준에서 동작합니다. 별도 Guardian Browser는 캐시된 필터의 지원 CSS 코스메틱 규칙과 기본 광고 슬롯 규칙을 자체 WebView 안에서만 적용합니다. 외부 Chrome/다른 앱의 화면 요소, HTTPS 내부 트래픽, scriptlet/procedural 규칙 또는 앱 자체 UI 광고를 제거하지 않으며 모든 광고 차단을 보장하지 않습니다.
- 백그라운드 필터 업데이트는 WorkManager의 네트워크 연결 조건과 주기 작업 정책을 사용합니다. Android 전원 관리에 따라 실제 실행 시각은 설정한 시각과 정확히 일치하지 않을 수 있습니다.
- CI는 Android 단위 테스트, lint, debug APK 조립, 에뮬레이터 설치·실행, 패키지 정체성 및 SHA-256 검증을 수행합니다. 성공한 main push 또는 수동 workflow_dispatch에서 APK artifact를 업로드합니다. CI 통과는 실기기 네트워크 검증과 다릅니다.
- 실제 Android 기기에서 VPN 권한, Wi-Fi/모바일 전환, 장시간 백그라운드 유지, 배터리 최적화, 실제 광고 차단률 및 제조사별 동작은 아직 검증되지 않았습니다.


## Background filter refresh

WorkManager 2.12.0의 고유 주기 작업이 설정한 6/12/24시간 또는 7일 간격으로 필터를 갱신합니다. 네트워크 연결이 없으면 실행을 미루고, 실패하면 지수형 backoff로 재시도합니다. 자동 업데이트를 끄면 예약 작업을 취소합니다. 수동 업데이트와 백그라운드 업데이트가 동일한 캐시 임시 파일을 동시에 교체하지 않도록 다운로드를 직렬화했습니다.


## Latest WorkManager verification

- [GitHub Actions run 43437792450](https://github.com/justinhblee-prog/guardian/actions/runs/43437792450): **success**
- Source commit: `5d7624fd445edadb1a1ed24a482b9e2d25f07d41`
- Android unit tests: **passed**; APK assembly was skipped because this was a push-triggered validation run.
- Test inventory: **12 suites / 68 test methods**.


## IPv6 DNS 업스트림 선택
설정에서 Cloudflare, Google, Quad9 및 AdGuard DNS의 IPv4/IPv6 업스트림 프리셋을 선택할 수 있습니다. AdGuard 기본 IPv6 주소도 고급 직접 DNS 포착 경로 목록에 포함했습니다. IPv6 업스트림은 현재 네트워크에서 IPv6 연결을 제공해야 정상 동작합니다.


## 최신 검증 — IPv6 DNS 프리셋
- [GitHub Actions run 43443209122](https://github.com/justinhblee-prog/guardian/actions/runs/43443209122): **성공**
- 검증 소스 커밋: `f518711e559bfb8873128f82c07a1a157a5f2068`
- Android 단위 테스트: **통과**. push 검증이므로 APK 조립/업로드는 의도적으로 생략했습니다.
- 테스트 인벤토리: **12개 스위트 / 68개 JUnit 테스트 메서드**.


## Latest source verification

- [GitHub Actions run 43444407063](https://github.com/justinhblee-prog/guardian/actions/runs/43444407063): 12 suites / 70 JUnit tests passed.
- CI policy update: every push now also assembles and verifies the debug APK; only manual release dispatch uploads the installable artifact.


## 최종 APK 빌드 확인
- [GitHub Actions 최종 빌드](https://github.com/justinhblee-prog/guardian/actions/runs/43445545156): **성공**
- APK: `com.guardian.adblock` / versionCode 14 / `0.7.1-dev`
- 테스트: 12개 스위트 / 70개 JUnit 테스트 통과
- APK 조립, 패키지 식별, SHA-256 확인 완료. 설치 파일은 GitHub Actions artifact로 업로드했습니다.
- 실기기 설치 및 실제 광고 차단 검증은 별도이며, CI 성공만으로 이를 완료했다고 보지 않습니다.


## 0.7.2-dev DNS 응답 크기 안전성

업스트림에서 MTU보다 큰 DNS UDP 응답을 받으면 패킷 루프가 과대 IP 패킷을 TUN에 쓰지 않도록, 1232바이트 안전 한도를 적용하고 원본 질문을 유지한 TC 응답을 생성합니다. 이 동작은 단위 테스트로 검증합니다. 현재 소스에는 클라이언트 TCP/53 재시도 처리를 추가했습니다. 단순 SYN/ACK 연결, 순서형 스트림 재조립, DNS 길이 프레이밍 및 응답 분할을 처리합니다. 동일한 요청 TCP 세그먼트가 재전송되면 저장된 DNS 응답을 다시 보냅니다. 다만 자체 재전송 타이머·부분 ACK 복구 및 IPv6 Fragment 헤더/IP 조각화 재조립은 미지원입니다. IPv6의 제한된 비조각 확장 헤더 체인은 UDP/TCP 모두 파싱합니다.


## 최종 0.7.2-dev APK 빌드
- [최종 GitHub Actions 실행](https://github.com/justinhblee-prog/guardian/actions/runs/43447121533): **성공**
- 소스 커밋: `50e768e253ae3ee7d77d3cdc07b5f23ccd970eab`
- APK: `com.guardian.adblock` / versionCode 15 / `0.7.2-dev`
- 단위 테스트: 12개 스위트 / 72개 JUnit 테스트 통과
- APK 조립·패키지 식별·SHA-256 검증·artifact 업로드 성공
- SHA-256: `a1d41cf8937b2c09f7c4ca8d0b02ddf252e95f935b1cee3ed1d485c5470f55d8`
- 실기기 설치 및 광고 차단 효과는 아직 미검증입니다.


## DNS-over-TCP/53 — source CI verified

- TCP/53 패킷 codec과 제한된 연결 상태 관리자를 추가했습니다.
- SYN/SYN-ACK, ACK, FIN/RST, DNS length-prefix 조립, 분할된 질의 재조립, 다중 DNS 메시지 처리 및 응답 분할을 지원합니다.
- SYN 이후 서버 시퀀스 번호, payload와 FIN이 함께 온 세그먼트, IPv4/IPv6 TCP checksum을 회귀 테스트합니다.
- [GitHub Actions run 43452443314](https://github.com/justinhblee-prog/guardian/actions/runs/43452443314): **success**, 16개 테스트 스위트 / 99개 테스트 메서드.
- 이 push CI는 versionCode 15 / 0.7.2-dev APK를 조립·식별·SHA-256 검증했지만 artifact 업로드는 생략했습니다. 기존 배포 artifact는 이 TCP/53 변경 이전 빌드입니다.
- 실기기에서 Android resolver가 TCP/53으로 재시도하는지 및 실제 DNS 응답 경로는 아직 검증되지 않았습니다.


## 보호 화면 개선

- Protection 탭에 실시간 보호 시작/중지, 자동 업데이트, 악성코드·피싱, Android 광고 SDK, 고급 DNS 포착 스위치를 추가하고 필터/앱별 제외 관리로 바로 이동할 수 있도록 했습니다.
- 각 스위치는 기존 설정 저장·필터 재적용·업데이트 작업과 연결되어 있으며, 공용 DNS 직접 포착은 재시작이 필요하다는 경고를 표시합니다.

- CI now also runs `lintDebug`; latest run 43459666939 passed Android unit tests, lint, debug APK assembly, package identity and checksum verification.

- DNS 규칙 엔진 자체 테스트의 안내문을 명확히 했습니다. 로컬 규칙 매칭/NXDOMAIN 생성만 검사하며 실제 외부 DNS 경로와 앱 내부 광고 차단 검증은 아닙니다.


## 암호화 DNS 업스트림

- DNS 업스트림 전송 방식을 UDP/53, TCP/53, DNS-over-TLS/853 중 선택할 수 있습니다. Cloudflare·Google·Quad9·AdGuard 계열은 TLS 호스트 이름을 자동 선택하고, 사용자 지정 IP는 인증서 호스트 이름을 별도로 입력할 수 있습니다.
- DoT는 SNI 및 TLS 인증서 호스트 이름 검증을 수행합니다. VPN 서비스 자체 소켓을 보호해 터널 재귀를 피합니다.
- 이는 업스트림 연결만 암호화합니다. 앱이 직접 사용하는 DoH/DoT 우회 요청을 가로채거나 차단하는 기능은 아닙니다. 외부 DNS 서버와의 실기기 TLS 연결은 아직 별도 검증이 필요합니다.


## DNS-over-HTTPS 업스트림

- 업스트림은 UDP/53, TCP/53, DNS-over-TLS/853, DNS-over-HTTPS/443 중 선택할 수 있습니다. DoH는 HTTP POST의 application/dns-message 본문을 사용하며 Content-Length와 chunked 응답을 제한된 크기로 검증합니다.
- DoH/DoT는 TLS SNI 및 인증서 호스트 이름을 검증하고, VPN 서비스 소켓을 보호해 DNS 터널 재귀를 방지합니다.
- DoH/DoT는 Guardian이 선택한 업스트림으로 나가는 연결만 암호화합니다. 앱 자체 DoH/DoT 우회 요청을 가로채거나 차단하는 기능은 아닙니다. 외부 제공자 TLS 연결은 실기기에서 별도 검증해야 합니다.

- 저장된 DoH 모드로 설정 화면을 다시 열 때도 TLS 호스트 입력·저장 컨트롤이 보이도록 초기 표시 상태를 수정하고 CI에서 재검증했습니다.

- IPv6 TCP 세션의 SYN→DNS 질의→응답 흐름을 통합 회귀 테스트에 추가했습니다.


## 최신 통합 CI 검증 — 2026-11-13

- [GitHub Actions run 43525073201](https://github.com/justinhblee-prog/guardian/actions/runs/43525073201): **success**
- Android unit tests, Android lint, debug APK assemble, package identity/version check 및 SHA-256 검증 모두 통과
- 검증 커밋: `827198814d0b4441afe72c2ddb81b6d1a41a8f19`
- 검증 APK 식별: `com.guardian.adblock` / versionCode 15 / `0.7.2-dev`
- 검증 APK SHA-256: `c40229d5c3a25173092233b2e23ace19d9c3d6f831837d6d6684c20f156495ee`
- Push CI는 수동 배포용 artifact 업로드를 건너뜁니다. 따라서 이 검증으로 새 설치용 APK가 공개/업로드된 것은 아닙니다.
- 실제 Android 기기에서 VPN 라우팅, Android resolver TCP 재시도, 앱별 광고 제거 효과는 아직 미검증입니다.


## 0.7.4-dev — 백그라운드 필터 업데이트 재시도 최적화

WorkManager가 선택 목록 다운로드 실패로 재시도할 때, 이미 정상적으로 갱신된 다른 목록을 다시 내려받지 않습니다. 기본 프로필, 보안 목록, Android 앱 광고 SDK 목록의 캐시 유효성과 마지막 갱신 시각을 각각 확인하고, 캐시가 없거나 손상/만료되었거나 선택 프로필이 달라진 목록만 갱신합니다.

- 버전: `com.guardian.adblock` / versionCode 17 / `0.7.4-dev`
- CI: [43570828960](https://github.com/justinhblee-prog/guardian/actions/runs/43570828960) — 단위 테스트, lint, Debug APK 조립, 패키지 식별 및 SHA-256 검증 통과
- CI 검증 APK SHA-256: `d60df611f004d5114d9e1aaeb832a5837ce4287c5dd1b7c51c5909c8a4cfb577`
- push 검증은 artifact 업로드를 생략합니다. 실기기 설치와 실제 광고 차단 효과는 별도 검증 대상입니다.


## 0.7.5-dev — DNS 주소 처리 및 보호 범위 안내 개선

IPv6 DNS 업스트림 주소를 정규화할 때 주소 문자열이 제공되지 않는 예외 경로를 안전하게 처리합니다. 홈 화면의 보호 범위 안내도 더 구체화해, Guardian이 자체 업스트림 DNS를 DoT/DoH로 암호화할 수 있다는 점과 각 앱이 직접 보내는 DoH/DoT 요청을 Guardian이 가로채지 못한다는 점을 구분합니다.

- 버전: `com.guardian.adblock` / versionCode 18 / `0.7.5-dev`
- CI: [43571389815](https://github.com/justinhblee-prog/guardian/actions/runs/43571389815) — 단위 테스트, lint, Debug APK 조립, 패키지 식별 및 SHA-256 검증 통과
- CI APK SHA-256: `f756ab65ce8d74cc7a7da45457b0cc2987b83acc83e9c993eab0a3f624492e1a`
- 실기기 VPN/광고 차단 효과 검증은 별도입니다.


## 0.7.6-dev — DNS-over-TCP resolver exception isolation (2026-11-14)
- DNS TCP session manager now catches unexpected exceptions from the resolver callback and returns a standards-shaped SERVFAIL response; a resolver exception no longer escapes to the VPN packet loop and terminates the tunnel.
- Regression test confirms SERVFAIL and successful processing of a subsequent query in the same TCP session.
- CI [43572032012](https://github.com/justinhblee-prog/guardian/actions/runs/43572032012) completed successfully: shared DNS rules and manifest checks, Android unit tests, Android lint, Debug APK assembly, package identity/version and APK checksum checks passed.
- Package: `com.guardian.adblock`, versionCode `19`, versionName `0.7.6-dev`.
- CI debug APK SHA-256: `b5ed0b9ee635eadc1ba5c337f94eed9c47767215198e2b60df5748fbf53e1e91` (about 2.9 MB). Ordinary push workflow skipped artifact upload.
- This is automated CI evidence, not physical-device validation; APK is debug-signed and not a production-signed release.


## 0.7.7-dev — Filter option update-race fix (2026-11-14)
- Prevent changes to optional security and Android-app advertising filter toggles while a filter update is in progress. Both the Protection screen Switches and Filters screen CheckBoxes are disabled, including when navigating between screens during an update; controls are restored on success or failure.
- When a first-time optional list download fails and no valid cache exists, the matching control is switched off so the UI and persisted configuration remain aligned.
- CI [43572504150](https://github.com/justinhblee-prog/guardian/actions/runs/43572504150) completed successfully: unit tests, lint, debug APK assembly, package identity/version and checksum checks passed.
- Package: `com.guardian.adblock`, versionCode `20`, versionName `0.7.7-dev`.
- CI debug APK SHA-256: `1dbfd5b33e134ad25c949e24c0750793c9d43c83ec9888230f107e93c49754c4` (about 2.9 MB). This ordinary push run skipped artifact upload.
- Final package workflow now creates a source ZIP containing source, tests, workflow and project documentation, plus a Korean verification summary alongside the installable debug APK and SHA-256 file.


## 최종 산출물 패키지 생성 완료 — 2026-11-14

- 최종 CI 실행: [43572937308](https://github.com/justinhblee-prog/guardian/actions/runs/43572937308) — 성공. Android 단위 테스트, lintDebug, assembleDebug, 패키지명·버전 확인, APK checksum, 소스 패키징 및 artifact 업로드가 모두 통과했습니다.
- 커밋: `94bab42e1a9dd7e10b61fad52f2546fc075dbdcf`
- 앱: `com.guardian.adblock`, versionCode `20`, versionName `0.7.7-dev`.
- 설치 APK: `app-debug.apk`, 3,014,104 bytes. SHA-256: `4797b47bad0c92574e0b7f9b8bf347960c46e688c95be43ee6158b2d1a4a93e7`.
- 전체 소스 ZIP: `Guardian-AdBlock-Source-v0.7.7-dev.zip` (161,340 bytes, 98 entries; Android 소스·테스트·공유 규칙·워크플로·설명서 포함).
- 검증 요약: `release/VERIFICATION_SUMMARY_KO.md`.
- GitHub Actions artifact: `Guardian-AdBlock-Android-Full-Package-v0.7.7-dev`, artifact ID `14105371888`, archive SHA-256 `d5f4555603756f5b0cf97a6725ecc5ff942e98ede9c626ef91227bfa2cf12d3f`, 만료 예정 2026-12-14.
- APK는 Android debug 서명 빌드입니다. production 서명과 실기기 VPN/DNS 라우팅·Wi-Fi/모바일 전환·실제 광고 제거 수용 테스트는 수행하지 않았습니다. DNS 수준 차단 범위 및 앱 자체 DoH/DoT 우회 등 알려진 한계는 검증 문서에 명시했습니다.


## 0.7.8-dev — Android 최종 패키지 검증 (2026-11-15)

- 메인 커밋: `26b79f5f5b4cf03e8ae23093ff3a5911702eecc4`.
- GitHub Actions [43726745032](https://github.com/justinhblee-prog/guardian/actions/runs/43726745032): **success**.
- Android 단위 테스트, lint, debug APK 조립, APK 내부 DNS 필터 파일 일치, Android API 35 에뮬레이터 설치·실행, 패키지 식별 및 SHA-256 검사, 소스 ZIP 패키징, artifact 업로드가 모두 통과했습니다.
- APK: `com.guardian.adblock`, versionCode `21`, versionName `0.7.8-dev`, 3,054,240 bytes.
- APK SHA-256: `4349d51155b299736fe751465cbb1234545916124f4702d67a550ea5c7d1a630`.
- 전체 artifact: `Guardian-AdBlock-Android-Full-Package-v0.7.8-dev`, artifact ID `14167411329`, ZIP 크기 3,236,385 bytes. GitHub artifact archive digest: `sha256:b0a55967f1741e991bbfbfc6079b0d45101d53246ecbadba65872e1b682cc7ad`. GitHub 보관 만료 예정일: 2026-12-15.
- APK는 **debug 서명**이며 production 서명 APK/AAB가 아닙니다. 에뮬레이터 설치·실행은 통과했지만 실기기 VPN 권한, 실제 DNS 라우팅/차단률, Wi-Fi·모바일 전환 및 제조사별 백그라운드 동작은 아직 검증되지 않았습니다.


## 기존 설치본 업데이트 호환성

- 개발용 APK는 `android/guardian-dev.keystore`의 고정 개발 인증서로 서명합니다. 따라서 이 변경 이후 생성되는 개발 APK끼리는 같은 서명 인증서를 유지해 기존 앱 위에 `adb install -r` 방식으로 업데이트할 수 있습니다.
- 개발 인증서 SHA-256: `ED:AE:EC:C9:E4:A2:7E:64:F0:7D:92:64:3F:B8:51:40:72:50:83:AB:ED:55:3F:91:2A:0D:EE:03:87:FB:D8:2C`.
- 이 키는 소스 저장소에 포함된 개발 전용 키이며 production 배포에 절대 사용하지 않습니다. 운영 배포는 별도의 비공개 keystore를 사용해야 합니다.
- 이전 0.7.8-dev APK가 GitHub runner의 임시 debug 인증서로 서명되어 있다면 새 고정 인증서와 달라 Android가 덮어쓰기를 거부합니다. 그 경우 새 개발 서명 체계로 옮기기 위한 최초 1회에 한해 기존 앱 제거가 필요할 수 있으며, 그 뒤부터는 새 인증서로 서명된 APK끼리 데이터 유지 업데이트가 가능합니다. 기존 설치본 인증서와의 일치 여부는 실제 기기 APK 인증서를 확인해야 확정할 수 있습니다.
- CI는 동일한 고정 개발 인증서로 서명한 0.7.8-dev APK를 먼저 에뮬레이터에 설치한 뒤 0.7.9-dev APK를 `adb install -r`로 업데이트하여 패키지 교체를 검증합니다.


### 광고·추적 차단 통계 (0.7.9-dev)
- DNS 차단 요청을 광고 도메인, 추적 도메인, 기타로 분류해 누적 집계합니다.
- 활동 화면에 분류별 막대 그래프와 정상 전달 건수를 표시합니다.
- 광고/추적 분류는 도메인 이름 기반의 휴리스틱 추정이며, 원본 필터 규칙에 정식 taxonomy가 포함된 것은 아닙니다.
- 절약 데이터는 차단 요청 1건당 24KB를 가정한 추정치입니다. 실제 응답 바이트 또는 페이지 로딩 데이터 절감량을 측정한 값이 아닙니다.
- 분류별 집계는 기능 적용 이후부터 쌓이며, 기존 전체 차단 건수를 소급 분류하지 않습니다.


## 0.7.14-dev — signed filter distribution + VPN E2E (2026-11-17)

- 통합 커밋: PR #73 `99ad460` (VPN 시작 fallback/대용량 필터 파싱), PR #77 `30a765c` (signed filter client, 배포 서버 도구, 라이선스 API 운영 스크립트, 릴리스 검증).
- 패키지: `com.guardian.adblock`, versionCode `27`, versionName `0.7.14-dev`.
- Android PR 검증 [run 44051352597](https://github.com/justinhblee-prog/guardian/actions/runs/44051352597): unit tests, lint, 0.7.13→0.7.14 업그레이드 설치, API 35 에뮬레이터 VPN/DNS E2E 성공.
- APK 크기: 3,119,618 bytes. SHA-256: `24db2c1db46a66d2117cb35f7541c893b3321ee7de46363b615fd1a585319c78`.
- E2E 로그: `protection_active=true`, `dns_queries=6`, `dns_forwarded=5`, `dns_blocked=1`, `dns_ad_blocked=1`, `dns_failed=0`. 허용 `example.com` → `172.66.147.243`; 차단 `guardian-ci-44051352597.doubleclick.net` → `unknown host`.
- 에뮬레이터는 Android 15 / API 35입니다. ICMP ping 응답은 손실됐지만 DNS 응답과 차단 판정은 성공했습니다.
- Signed Filter Distribution [run 44052664109](https://github.com/justinhblee-prog/guardian/actions/runs/44052664109), Distribution CI [run 44052665884](https://github.com/justinhblee-prog/guardian/actions/runs/44052665884), License API/PostgreSQL integration [run 44052664268](https://github.com/justinhblee-prog/guardian/actions/runs/44052664268) 성공.
- main Android 재검증 [run 44052664083](https://github.com/justinhblee-prog/guardian/actions/runs/44052664083)과 production release configuration 검증 [run 44052664040](https://github.com/justinhblee-prog/guardian/actions/runs/44052664040)은 문서 갱신 시점에 실행 중입니다.
- 서명 필터·라이선스 서버 코드는 반영됐지만, Windows 노트북에서 DPAPI 개인키 생성, 실제 공개 배포 branch 게시, Tailscale private API 운영 배포는 아직 수행하지 않았습니다.
- production keystore/secrets가 없어 production-release job은 skip됩니다. APK는 개발용 debug 서명본이며 production 릴리스가 아닙니다.
- 실제 Android 스마트폰/태블릿 설치, Wi-Fi↔모바일 전환, IPv4/IPv6, Private DNS, 다른 VPN과의 충돌, 장시간 백그라운드 유지 및 실제 광고 제거율은 별도 실기기 수용 테스트가 필요합니다.