# Guardian AdBlock Android 0.6.3-dev

## 포함된 기능
- Android `VpnService` 기반 로컬 DNS 필터링
- Android VPN 권한 동의 후 보호 시작/중지
- 실행 상태와 시작 실패 사유를 앱 화면에 표시
- 내장 도메인 목록 및 사용자가 직접 입력한 차단/허용 규칙
- 규칙은 기기 내부 `SharedPreferences`에 저장되며, 보호 중 저장하면 즉시 다시 읽어 적용
- 차단 규칙은 정확한 도메인과 하위 도메인에 적용되고, 허용 규칙이 차단 규칙보다 우선
- 공통 규칙(`shared/filter_rules.txt`)을 Windows DNS 엔진과 공유
- 내장 차단 도메인에 NXDOMAIN 응답
- 허용 도메인은 Cloudflare DNS(1.1.1.1)로 전달
- DNS 전달용 소켓은 `VpnService.protect()` 처리하여 VPN 루프 방지
- 표준 단일 질의만 수락하고 DNS 응답 패킷·압축 QNAME·복수 질문·잘못된 패킷을 거부
- IPv6 가상 DNS 주소(`/128`)와 UDP/53 필터링 지원, UDP checksum 검증 및 응답 checksum 생성
- IPv6 확장 헤더와 조각화 패킷은 재조립하지 않고 거부
- 업스트림 UDP 응답이 잘리면 TCP/53으로 재시도하며 응답 길이·DNS transaction/question 검증

## 사용자 규칙 사용법
1. 앱에서 `사용자 차단 목록`에 차단할 도메인을 한 줄에 하나씩 입력합니다.
2. 예외로 허용할 도메인은 `허용 목록`에 입력합니다.
3. `사용자 규칙 저장 및 적용`을 누릅니다.
4. 보호 실행 중이면 현재 VPN 필터에 다시 불러오고, 꺼져 있으면 다음 보호 시작 때 적용합니다.

도메인은 ASCII DNS hostname 형식으로 입력해야 합니다. 공백, URL 경로, 포트, 와일드카드는 허용하지 않습니다. 한 목록당 최대 1,000개까지 등록할 수 있으며, 중복은 자동 정리됩니다. 예를 들어 `ads.example.com`을 차단하면 `img.ads.example.com`도 차단됩니다. `allow=trusted.ads.example.com`에 해당하는 허용 규칙은 그 아래 하위 도메인에도 우선 적용됩니다.

## 중요한 제한
- IPv4/IPv6 UDP/53 DNS를 처리합니다. 업스트림 UDP 응답이 잘릴 때만 TCP/53으로 재시도합니다. 클라이언트 방향 TCP DNS 가로채기, IPv6 확장 헤더/조각화 재조립, DoH/DoT, 앱별 제외, 원격 목록 업데이트는 미구현입니다.
- 내장 차단 목록은 개발용 소규모 목록입니다. 광고 전체 차단을 보장하지 않습니다.
- DNS 이외의 트래픽은 VPN 라우팅 대상이 아닙니다.
- DNS 업스트림 장애 시 유효한 SERVFAIL 응답을 반환합니다.
- 실제 Android 기기에서 패킷 호환성, 백그라운드 지속성, 배터리, Android 15 이상 정책 검증이 필요합니다.

## 빌드
공통 규칙 원본은 프로젝트의 `shared/filter_rules.txt`입니다. Gradle은 이 파일을 Android assets에 포함합니다. Android Studio에서 `android` 폴더를 열고 Gradle Sync 후 `:app:testDebugUnitTest` 및 `:app:assembleDebug`를 실행합니다.
APK 경로: `android/app/build/outputs/apk/debug/app-debug.apk`

필수 환경: Android Studio, Android SDK Platform 35, JDK 17 이상, 인터넷 연결(Gradle 플러그인 다운로드).

## 설치
Debug APK를 Android 기기로 복사한 후 설치합니다. 출처를 알 수 없는 앱 설치 허용이 필요할 수 있습니다.
앱 실행 → 보호 시작 → Android VPN 연결 승인.