# 릴리스 상태 — Guardian AdBlock v0.6.3-dev

기준일: 2026-11-13

## 확인된 결과
- Android 소스의 Gradle `testDebugUnitTest assembleDebug`: **성공** (커밋 `ccc5beba3ae8658733ca215708a77115158d78f1`)
- Android Debug APK 생성 및 GitHub Actions artifact 업로드: **성공**
- APK SHA-256: `40593421ba6b8fdf62fd41225a4c8055266da89bd5b17f56eb416085afb85178`
- Python / Browser / Kotlin 핵심부 자동화 테스트: 기존 패키지 검증 기록 참조
- 최신 추가 IPv4 패킷 길이 검증 수정: **후속 CI 검증 대기**

## 구현 범위
- Android IPv4 및 IPv6 UDP DNS 필터링
- 차단/허용 도메인 규칙 및 사용자 규칙 저장
- 업스트림 UDP 응답 검증, 잘림(TC) 응답에 대한 TCP/53 재시도
- 업스트림 장애 시 DNS SERVFAIL 응답
- DNS 통계 저장 및 UI 갱신 브로드캐스트 제한

## 미완료 출시 게이트
- 최신 IPv4 길이 검증 수정이 포함된 CI 빌드 성공 확인
- Android 실기기 설치, VPN 권한·시작·중지, IPv4/IPv6 실제 DNS 검증
- 화면 잠금·네트워크 변경·장시간 실행 안정성 확인
- DoH/DoT, 클라이언트 방향 TCP DNS, IPv6 확장 헤더/조각 재조립 미지원
- Windows 실환경 어댑터/서비스 검증 및 Windows EXE/설치 프로그램 미생성

**판정:** APK를 빌드할 수 있는 개발 릴리스입니다. 실기기 및 Windows 통합 검증 전에는 상용/운영 최종판으로 판정하지 않습니다.


## 최신 빌드 갱신 — 2026-11-13

- CI 실행: [43420229693](https://github.com/justinhblee-prog/guardian/actions/runs/43420229693)
- 소스 커밋: `d6191812b51cf97a288186901435608280bab1f8`
- Gradle `testDebugUnitTest assembleDebug`: **PASS**
- APK 체크섬 검사 및 Artifact 업로드: **PASS**
- Artifact ID: `14034125411`
- APK 크기: 845,986 bytes
- APK SHA-256: `c17a91a518e39e9ec9681c8cb90d645f589ebbbb0276152299299d5c321bfff9`
- IPv4 TUN 패킷 길이 회귀 테스트: 최신 CI에 포함되어 통과

**갱신 판정:** 최신 IPv4 길이 검증 수정까지 포함한 Android 개발 APK 빌드/자동화 검증 성공. 실기기 VPN·실제 광고 차단 검증은 여전히 미완료이며, 운영 최종판 판정은 보류합니다.


## 최신 Android APK — 2026-11-13

- CI 실행: [43420975704](https://github.com/justinhblee-prog/guardian/actions/runs/43420975704)
- 소스 커밋: `5b93b93ec36b86c2312c09b95562e396ec6dd890`
- `clean testDebugUnitTest assembleDebug`: **BUILD SUCCESSFUL**
- Artifact ID: `14034191808`
- APK 크기: 846,262 bytes
- APK SHA-256: `9d8ba26f6c9cadad5250e15d3d46456262476b4d5aeb8ac27f023e5eea2fb92b`
- ZIP 무결성 및 추출 APK 체크섬 대조: **PASS**
- IPv4 패킷 프레이밍, DNS 질문 대소문자 비구분 비교, IPv6 UDP 체크섬 회귀 테스트 포함

**판정:** 위 커밋의 Android 개발 APK 빌드와 설정된 자동화 테스트가 통과했습니다. 실기기 VPN 동작 및 실제 광고 차단은 미검증이며 운영 최종판은 아닙니다.


## 최신 UI 수정 빌드 — 2026-11-13

- CI: [43421570913](https://github.com/justinhblee-prog/guardian/actions/runs/43421570913)
- 소스 커밋: `d438442ea45754be0b8faff777497c3308d3cfc7`
- Gradle `clean testDebugUnitTest assembleDebug`: **PASS**
- Artifact ID: `14034222440`
- APK 크기: 846,278 bytes
- APK SHA-256: `3d8e5aa6b6effe815dbed6f2f8d846d44098eb498de93ddd0807d2eedd2be340`
- ZIP 무결성 및 내부 APK 체크섬 대조: **PASS**
- 규칙 탭의 저장/검증 메시지를 VPN 보호 상태 메시지와 분리하는 UI 버그 수정 포함

실기기 설치 및 실제 VPN/광고 차단 검증은 여전히 미완료입니다.
