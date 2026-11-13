# Guardian AdBlock v0.6.3-dev 인수인계

작성일: 2026-11-13

## 1. 현재 릴리스 판정

Android APK를 GitHub Actions에서 빌드하고 업로드한 이력이 확인됩니다. 다만 현재 main에는 그 성공 빌드 이후 IPv4 패킷 길이 검증 수정이 추가되어 있습니다. 이 최신 수정까지 포함한 빌드가 다시 성공하기 전에는 아래 APK를 최신 main의 APK라고 간주하면 안 됩니다.

- 확인된 성공 CI: [run 43419311161](https://github.com/justinhblee-prog/guardian/actions/runs/43419311161)
- 해당 CI 커밋: `ccc5beba3ae8658733ca215708a77115158d78f1`
- Gradle: `testDebugUnitTest assembleDebug` 성공
- artifact: `Guardian-AdBlock-Android-APK-v0.6.3-dev`
- APK SHA-256: `40593421ba6b8fdf62fd41225a4c8055266da89bd5b17f56eb416085afb85178`

## 2. Android 구현 범위

1. Android `VpnService` 기반 로컬 DNS 필터링
2. IPv4 UDP/53 및 IPv6 UDP/53 처리
3. IPv6 UDP 체크섬 검증 및 응답 체크섬 생성
4. 공용 규칙 파일과 기기 내 사용자 차단/허용 목록 지원
5. 허용 규칙이 차단 규칙보다 우선
6. 업스트림 DNS 응답의 transaction ID 및 질문 일치 검증
7. 업스트림 UDP 응답이 잘린 경우 보호된 TCP/53으로 재시도
8. 업스트림 장애 시 유효한 DNS 질의에 SERVFAIL 응답
9. DNS 통계와 최근 활동 저장, UI 갱신 브로드캐스트 제한

## 3. 명시적 제한

- 앱이 직접 수신하는 DNS TCP 질의 가로채기 미지원
- DoH(HTTPS DNS) 및 DoT(TLS DNS) 필터링 미지원
- IPv6 확장 헤더 및 IP fragment 재조립 미지원
- 번들 차단 목록은 소규모 개발용 기본 목록이며 포괄적인 광고 DB가 아님
- 일부 앱 내부 광고, 자체 DoH를 사용하는 앱, DNS 이외 방식의 광고 요청은 차단되지 않을 수 있음

## 4. 검증 현황

| 항목 | 상태 |
|---|---|
| Python / Browser / Kotlin 핵심부 자동화 검증 | 이전 검증 기록 PASS |
| Android Gradle JUnit task | CI PASS (위 커밋 기준) |
| Android APK 빌드 및 업로드 | CI PASS (위 커밋 기준) |
| APK 체크섬 | 위 커밋 산출물 확인 |
| 이후 IPv4 TUN 길이 방어 수정 | 후속 CI 확인 필요 |
| Android 실기기 설치 및 VPN 시작/중지 | 미검증 |
| 실제 IPv4/IPv6 DNS 및 광고 차단 | 미검증 |
| Windows 실환경 DNS 어댑터/서비스 | 미검증 |
| Windows EXE/설치 프로그램 | 미생성 |

## 5. Android 설치 절차

1. 성공한 GitHub Actions 실행에서 APK artifact를 내려받습니다.
2. Android에서 APK를 열고 필요 시 해당 다운로드 앱의 '알 수 없는 앱 설치' 권한을 허용합니다.
3. Guardian AdBlock을 실행하고 '보호 시작'을 누릅니다.
4. Android VPN 연결 승인 창에서 허용합니다.
5. 화면의 보호 상태가 실행 중으로 바뀌는지 확인합니다.
6. 문제가 있으면 다른 VPN 앱이 활성화되어 있는지 확인합니다. Android는 일반적으로 사용자 프로필당 활성 VPN을 하나만 허용합니다.

## 6. 다음 우선순위

1. 최신 main 커밋의 Android JUnit 및 APK 빌드 재검증
2. TUN 패킷 처리와 VPN 서비스 수명주기 회귀 검토
3. 실기기 검증 체크리스트를 따라 설치·권한·DNS·네트워크 변경 확인
4. 실제 동작 증거를 반영해 릴리스 상태와 변경 이력 갱신
5. Windows 기능은 별도 Windows 호스트에서 검증하고 설치 패키지를 별도 제작

## 7. 최종 주의

GitHub Actions 성공은 컴파일, 자동화 테스트 task 및 APK 패키징 성공을 의미합니다. Android 기기에서 VPN이 정상 동작하거나 모든 광고가 차단된다는 보증은 아닙니다. 실기기 수용 테스트 전에는 운영 최종판 또는 상용 수준으로 표시하지 않습니다.


## 최신 인수인계 갱신 — 2026-11-13

이 섹션은 위의 이전 CI 대기 문구를 대체합니다.

- 최신 CI: [43420229693](https://github.com/justinhblee-prog/guardian/actions/runs/43420229693)
- 최신 소스 커밋: `d6191812b51cf97a288186901435608280bab1f8`
- Android JUnit task + Debug APK 빌드: **PASS**
- APK ZIP 무결성 검사: **PASS**
- Artifact ID: `14034125411`
- APK 크기: 845,986 bytes
- APK SHA-256: `c17a91a518e39e9ec9681c8cb90d645f589ebbbb0276152299299d5c321bfff9`
- IPv4 입력 버퍼 경계, IP total-length 불일치, IPv4 options 케이스 회귀 테스트가 최신 빌드에 포함되어 통과했습니다.

실기기 설치·VPN 승인·보호 시작/중지·IPv4/IPv6 실제 DNS·네트워크 변경 안정성 및 Windows 통합 검증은 미완료입니다. 따라서 현재 산출물은 검증된 Android 개발 APK이지, 모든 환경에서 동작이 확인된 상용 최종판은 아닙니다.


## 최신 성공 빌드 — 2026-11-13

- [GitHub Actions 43420975704](https://github.com/justinhblee-prog/guardian/actions/runs/43420975704)
- 소스 커밋: `5b93b93ec36b86c2312c09b95562e396ec6dd890`
- Gradle `clean testDebugUnitTest assembleDebug`: **BUILD SUCCESSFUL**
- Artifact ID: `14034191808`
- APK: 846,262 bytes
- APK SHA-256: `9d8ba26f6c9cadad5250e15d3d46456262476b4d5aeb8ac27f023e5eea2fb92b`
- Artifact ZIP SHA-256: `d691be00fe2d716df9351b5f7183c2cc968533a38d9059dc2977b08e6c7e7c10`
- 다운로드 ZIP 무결성 및 내부 APK 체크섬 일치 확인 완료
- IPv4 packet framing, DNS question case-insensitive matching, IPv6 UDP checksum regression tests included

실기기 설치/VPN/실제 광고 차단 검증과 Windows 통합 검증은 여전히 별도 수용 테스트 항목입니다.
