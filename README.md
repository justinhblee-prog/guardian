# Guardian AdBlock Android — 0.6.4-dev

Guardian AdBlock은 Android `VpnService`를 이용해 기기 DNS 질의를 로컬에서 검사하고, 지정된 광고·추적 도메인을 차단하는 개발 버전입니다.

## 구현된 기능

- Android VPN 권한을 받은 뒤 동작하는 로컬 DNS 보호 서비스
- IPv4 및 IPv6 UDP/53 DNS 질의 파싱
- IPv6 UDP 필수 체크섬 검증 및 응답 체크섬 생성
- 업스트림 Cloudflare DNS(1.1.1.1) 전달과 응답 transaction/question 검증
- 업스트림 UDP 응답이 잘린 경우 TCP/53 재시도
- 차단 도메인에 NXDOMAIN, 업스트림 실패 시 SERVFAIL 응답
- 공용 규칙 파일 `shared/filter_rules.txt` 및 기기 내 사용자 차단/허용 목록
- 허용 규칙 우선 적용, 규칙 입력 검증, 보호 중 규칙 재적용
- DNS 통계 및 최근 활동 저장
- 앱 프로세스가 재시작되어 VPN이 끊겼는데 저장된 상태만 남아 있는 경우 잘못된 '보호 중' 표시 방지
- Guardian 전용 adaptive launcher icon

## 규칙 형식

```text
block=doubleclick.net
allow=trusted.doubleclick.net
```

규칙은 정확한 도메인과 하위 도메인에 적용됩니다. 허용 규칙은 차단 규칙보다 우선합니다. 사용자 규칙은 목록별 최대 1,000개이며 기기 내부에 저장됩니다.

## 현재 미지원 범위

- 클라이언트 방향 TCP/53 DNS 가로채기
- DNS-over-HTTPS(DoH), DNS-over-TLS(DoT)
- IPv6 확장 헤더 및 IP fragment 재조립
- 앱별 제외/허용, 원격 필터 목록 자동 업데이트
- 포괄적인 상용 광고 차단 목록 및 모든 앱 내부 광고 차단

따라서 모든 광고가 차단되는 제품으로 간주하면 안 됩니다. DNS 기반 필터링을 우회하는 앱이나 자체 암호화 DNS를 쓰는 앱은 차단되지 않을 수 있습니다.

## 빌드

필요 환경: JDK 17, Android SDK Platform 35, Build Tools 35.0.0, Gradle 8.9.

```bash
gradle -p android --no-daemon clean testDebugUnitTest assembleDebug
```

APK 경로: `android/app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions는 Android 단위 테스트, APK 빌드, VPN manifest 선언, launcher asset, 패키지 ID/버전, APK SHA-256을 확인한 후 설치용 Debug APK를 artifact로 보관합니다.

## 설치

1. Android 기기에 최종 제공된 APK를 다운로드합니다.
2. Android가 요청하면 다운로드 앱의 알 수 없는 앱 설치 권한을 허용합니다.
3. Guardian AdBlock을 실행하고 **보호 시작**을 누릅니다.
4. Android VPN 연결 승인 창에서 허용합니다.
5. 앱에 표시되는 보호 상태를 확인합니다. 다른 VPN 앱이 실행 중이면 먼저 중지해야 할 수 있습니다.

## 검증 경계

CI 성공은 소스 컴파일, 자동화 테스트, APK 패키징 및 checksum 확인을 의미합니다. 실기기 설치, VPN 승인/시작/중지, Wi-Fi↔모바일 데이터 전환, 장시간 실행, 실제 광고 차단 범위는 별도 기기 수용 테스트가 필요합니다. 검증하지 않은 기기 동작을 완료로 표시하지 않습니다.
