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
