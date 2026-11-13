# Guardian AdBlock Android 0.7.2-dev

## 제품 개요

Guardian은 Android `VpnService`를 사용해 기기 안에서 DNS 질의를 필터링하는 로컬 DNS 보호 앱입니다. 별도 VPN 서버나 사용자 계정은 사용하지 않습니다.

- 패키지: `com.guardian.adblock`
- 최소 Android: API 26
- compile/target SDK: 35
- 빌드 JDK: 17
- 현재 버전: `0.7.2-dev` (versionCode 15)

## 현재 구현 기능

### DNS 보호 엔진
- Android VPN 권한 승인 후 로컬 TUN을 구성하고 보호 시작/중지
- 시스템 가상 DNS 주소를 기본 경로로 사용
- IPv4/IPv6 UDP DNS 질의 파싱, 응답 패킷 생성 및 IPv6 UDP checksum 처리
- 차단 도메인은 NXDOMAIN, 업스트림 장애는 SERVFAIL 응답
- Cloudflare, Google, Quad9, AdGuard DNS 및 사용자 지정 IPv4/IPv6 숫자 주소를 업스트림으로 사용
- 보호된 업스트림 UDP 소켓, 응답 발신지·transaction ID·질문 검증
- 업스트림 UDP 응답이 잘린 경우 TCP/53으로 재시도
- 1232바이트를 초과하는 UDP DNS 응답은 IPv6 최소 MTU를 고려해 TC(truncated) 응답으로 축소
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
- 앱 실행 시 만료 목록 확인 + WorkManager를 이용한 네트워크 연결 시 주기적 백그라운드 업데이트
- 6/12/24시간, 7일 주기 설정 및 자동 업데이트 예약 즉시 반영
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

GitHub Actions의 `Guardian AdBlock Android APK` 워크플로는 모든 push에서 Android 단위 테스트와 debug APK 조립·패키지명·버전·SHA-256 검증을 수행합니다. 일반 push는 artifact를 공개하지 않으며, 수동 실행 또는 `[final-apk]` 커밋에서 설치 APK artifact를 업로드합니다.

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

- 현재 필터링은 시스템 가상 DNS를 통한 IPv4/IPv6 UDP/53 및 TCP/53 DNS 질의를 처리합니다.
- DoH/DoT, IPv6 확장 헤더 및 IP 조각화 재조립은 지원하지 않습니다. TCP/53은 제한된 세션 처리이며 재전송·패킷 손실 복구까지 구현한 완전한 TCP 스택은 아닙니다.
- 공용 DNS 직접 포착은 IP 단위 라우트이므로 DNS가 아닌 트래픽까지 TUN에 들어올 수 있어 기본 꺼짐입니다. Guardian은 DNS 이외 트래픽을 중계하지 않습니다.
- DNS 수준 필터이므로 웹페이지의 cosmetic filtering, HTTPS 내부 광고 요소 제거, 앱에 직접 포함된 광고 제거를 제공하지 않습니다. 모든 광고 차단을 보장하지 않습니다.
- 백그라운드 필터 업데이트는 WorkManager의 네트워크 연결 조건과 주기 작업 정책을 사용합니다. Android 전원 관리에 따라 실제 실행 시각은 설정한 시각과 정확히 일치하지 않을 수 있습니다.
- 현재 CI는 12개 테스트 스위트 / 72개 테스트 메서드를 실행하고, 모든 변경에서 debug APK 조립·패키지 정체성·SHA-256 검증까지 수행합니다. push에서는 APK artifact를 공개하지 않으며, 수동 릴리스 실행에서만 설치 파일을 업로드합니다. CI 통과는 실기기 네트워크 검증과 다릅니다.
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

업스트림에서 MTU보다 큰 DNS UDP 응답을 받으면 패킷 루프가 과대 IP 패킷을 TUN에 쓰지 않도록, 1232바이트 안전 한도를 적용하고 원본 질문을 유지한 TC 응답을 생성합니다. 이 동작은 단위 테스트로 검증합니다. 현재 소스에는 클라이언트 TCP/53 재시도 처리를 추가했습니다. 단순 SYN/ACK 연결, 순서형 스트림 재조립, DNS 길이 프레이밍 및 응답 분할을 처리합니다. 동일한 요청 TCP 세그먼트가 재전송되면 저장된 DNS 응답을 다시 보냅니다. 다만 자체 재전송 타이머·부분 ACK 복구와 IPv6 확장 헤더/조각화는 미지원입니다.


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
- [GitHub Actions run 43452443314](https://github.com/justinhblee-prog/guardian/actions/runs/43452443314): **success**, 15개 테스트 스위트 / 92개 테스트 메서드.
- 이 push CI는 versionCode 15 / 0.7.2-dev APK를 조립·식별·SHA-256 검증했지만 artifact 업로드는 생략했습니다. 기존 배포 artifact는 이 TCP/53 변경 이전 빌드입니다.
- 실기기에서 Android resolver가 TCP/53으로 재시도하는지 및 실제 DNS 응답 경로는 아직 검증되지 않았습니다.


## 보호 화면 개선

- Protection 탭에 실시간 보호 시작/중지, 자동 업데이트, 악성코드·피싱, Android 광고 SDK, 고급 DNS 포착 스위치를 추가하고 필터/앱별 제외 관리로 바로 이동할 수 있도록 했습니다.
- 각 스위치는 기존 설정 저장·필터 재적용·업데이트 작업과 연결되어 있으며, 공용 DNS 직접 포착은 재시작이 필요하다는 경고를 표시합니다.

- CI now also runs `lintDebug`; latest run 43457043232 passed Android unit tests, lint, debug APK assembly, package identity and checksum verification.

- DNS 규칙 엔진 자체 테스트의 안내문을 명확히 했습니다. 로컬 규칙 매칭/NXDOMAIN 생성만 검사하며 실제 외부 DNS 경로와 앱 내부 광고 차단 검증은 아닙니다.


## 암호화 DNS 업스트림

- DNS 업스트림 전송 방식을 UDP/53, TCP/53, DNS-over-TLS/853 중 선택할 수 있습니다. Cloudflare·Google·Quad9·AdGuard 계열은 TLS 호스트 이름을 자동 선택하고, 사용자 지정 IP는 인증서 호스트 이름을 별도로 입력할 수 있습니다.
- DoT는 SNI 및 TLS 인증서 호스트 이름 검증을 수행합니다. VPN 서비스 자체 소켓을 보호해 터널 재귀를 피합니다.
- 이는 업스트림 연결만 암호화합니다. 앱이 직접 사용하는 DoH/DoT 우회 요청을 가로채거나 차단하는 기능은 아닙니다. 외부 DNS 서버와의 실기기 TLS 연결은 아직 별도 검증이 필요합니다.
