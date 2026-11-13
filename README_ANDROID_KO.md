# Guardian AdBlock Android 0.7.0-dev

## 제품 개요

Guardian은 Android `VpnService`를 사용해 기기 안에서 DNS 질의를 필터링하는 로컬 DNS 보호 앱입니다. 별도 VPN 서버나 사용자 계정은 사용하지 않습니다.

- 패키지: `com.guardian.adblock`
- 최소 Android: API 26
- compile/target SDK: 35
- 빌드 JDK: 17
- 현재 버전: `0.7.0-dev` (versionCode 13)

## 현재 구현 기능

### DNS 보호 엔진
- Android VPN 권한 승인 후 로컬 TUN을 구성하고 보호 시작/중지
- 시스템 가상 DNS 주소를 기본 경로로 사용
- IPv4/IPv6 UDP DNS 질의 파싱, 응답 패킷 생성 및 IPv6 UDP checksum 처리
- 차단 도메인은 NXDOMAIN, 업스트림 장애는 SERVFAIL 응답
- Cloudflare, Google, Quad9, AdGuard DNS 및 사용자 지정 IPv4/IPv6 숫자 주소를 업스트림으로 사용
- 보호된 업스트림 UDP 소켓, 응답 발신지·transaction ID·질문 검증
- UDP 응답이 잘린 경우 업스트림 TCP/53으로 재시도
- VPN 시작/중지 경합 제어 및 Android 프로세스 회수 후 sticky service 복구
- 사용자가 시작 중 보호 중지를 누른 경우에도 저장된 자동 복구 상태를 해제
- 패킷 처리 루프가 중단되면 활성 상태를 해제하고 실패 사유 기록

### 필터와 사용자 옵션
- 공식 DNS 필터 프로필: AdGuard DNS Filter, 1Hosts Lite, OISD Big, HaGeZi Normal, HaGeZi Pro
- 선택형 HaGeZi Threat Intelligence 악성코드·피싱 도메인 목록
- 선택형 AWAvenue Android Ads Rule SDK 도메인 목록
- 사용자 차단/허용 도메인 목록(각 최대 1,000개)
- 앱별 VPN 보호 제외 설정
- 공용 DNS 직접 질의 포착은 고급 선택 옵션이며 기본값은 꺼짐
- 앱 실행 시 자동 업데이트 확인 및 6/12/24시간, 7일 주기 설정
- 다운로드 크기·유효 규칙 수 검증, 임시 파일 교체 및 기존 정상 캐시 보존
- 프로필/캐시 불일치 또는 보안 목록 미준비 시 잘못된 필터 조합으로 보호가 시작되지 않도록 차단

### 앱 화면
- 홈 보호 대시보드와 실시간 DNS 통계
- 보호 범위 및 제한 안내
- 필터 프로필·보안 옵션·사용자 규칙 관리
- 활동 기록, 최근 DNS 질의 및 패킷 진단
- 업스트림 DNS 및 앱별 보호 제외 설정
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
3. 선택형 보안/Android 앱 광고 목록을 켰다면 해당 목록 다운로드와 검증이 완료됐는지 확인합니다.
4. 홈에서 보호 시작을 누르고 Android VPN 권한을 승인합니다.
5. 활동 탭에서 실제 DNS 질의·차단·전달·실패 통계를 확인합니다.

## 빌드

GitHub Actions의 `Guardian AdBlock Android APK` 워크플로는 push 시 Android 단위 테스트를 수행합니다. `workflow_dispatch` 실행은 별도로 APK를 조립하고 패키지명·버전·SHA-256을 검증한 뒤 APK artifact를 업로드합니다.

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

## 명확한 제한 및 미검증 사항

- 현재 필터링은 시스템 가상 DNS를 통한 IPv4/IPv6 UDP/53 질의가 중심입니다.
- 클라이언트 방향 TCP DNS, DoH/DoT, IPv6 확장 헤더 및 IP 조각화 재조립은 지원하지 않습니다.
- 공용 DNS 직접 포착은 IP 단위 라우트이므로 DNS가 아닌 트래픽까지 TUN에 들어올 수 있어 기본 꺼짐입니다. Guardian은 DNS 이외 트래픽을 중계하지 않습니다.
- DNS 수준 필터이므로 웹페이지의 cosmetic filtering, HTTPS 내부 광고 요소 제거, 앱에 직접 포함된 광고 제거를 제공하지 않습니다. 모든 광고 차단을 보장하지 않습니다.
- 필터 자동 업데이트는 앱 실행 시 확인하는 방식이며, 앱이 닫힌 동안의 주기적 백그라운드 업데이트는 아직 구현되지 않았습니다.
- 현재 CI 단위 테스트는 11개 테스트 스위트 / 65개 테스트 메서드를 대상으로 합니다. CI 통과는 실기기 네트워크 검증과 다릅니다.
- 실제 Android 기기에서 VPN 권한, Wi-Fi/모바일 전환, 장시간 백그라운드 유지, 배터리 최적화, 실제 광고 차단률 및 제조사별 동작은 아직 검증되지 않았습니다.
