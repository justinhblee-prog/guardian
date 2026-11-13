# Guardian AdBlock Android 0.6.5-dev — 인수인계

작성일: 2026-11-13

## 1. 제품 범위

현재 GitHub 저장소 `justinhblee-prog/guardian`의 실제 코드 범위는 Android 앱과 공유 DNS 규칙 파일입니다. 오래된 문서에 있던 Windows 엔진·브라우저 확장·Python DNS 서버는 현재 저장소 트리에 없으므로 이번 Android 인수인계 범위에서 제외합니다.

## 2. 주요 소스

- `android/app/src/main/java/com/guardian/adblock/GuardianVpnService.kt`: VPN 수명주기, DNS 패킷 루프, 업스트림 전달
- `DomainFilter.kt`: DNS 질의 검증, 차단/허용 규칙, NXDOMAIN/SERVFAIL, 응답 매칭
- `Ipv4Udp.kt`: IPv4 UDP DNS 패킷 파싱/응답 생성
- `Ipv6Udp.kt`: IPv6 UDP DNS 파싱, 체크섬 검증/응답 생성
- `MainActivity.kt`: 홈·보호·규칙·설정 UI
- `shared/filter_rules.txt`: 공용 DNS 도메인 규칙
- `android/app/src/test/`: Android JUnit 회귀 테스트
- `android/core-checks/CoreSmoke.kt`: 순수 Kotlin DNS 핵심부 스모크 테스트

## 3. 동작 구조

1. 사용자가 Android VPN 권한을 승인하고 보호 시작을 누릅니다.
2. VPN 인터페이스에 IPv4/IPv6 가상 DNS 주소와 DNS 호스트 라우트를 등록합니다.
3. TUN에서 IPv4/IPv6 UDP DNS 질의를 읽고 단일 DNS 질문을 검증합니다.
4. 차단 규칙이면 NXDOMAIN을 반환합니다.
5. 허용/일반 질의는 보호된 소켓으로 1.1.1.1에 전달하고 응답 출처·transaction ID·질문을 검증합니다.
6. 업스트림 UDP 응답이 잘리면 TCP/53으로 재시도합니다.
7. 업스트림 실패 시 SERVFAIL을 반환합니다.
8. DNS 통계와 최근 활동을 저장하고 UI 갱신 브로드캐스트를 제한합니다.

## 4. 최근 수정 사항

- IPv4 TUN 입력 길이와 IP total length 일치 검증
- IPv6 UDP 필수 checksum 검증 및 응답 checksum 생성
- DNS QNAME은 대소문자 비구분으로 응답 매칭하되 QTYPE/QCLASS는 정확히 비교
- DNS wire label 안에 literal dot이 들어가 hostname 문자열로 모호해지는 입력 거부
- 잘린 UDP upstream 응답의 TCP fallback
- 사용자 규칙 화면 메시지와 VPN 보호 상태 메시지 분리
- Android 프로세스가 죽어 저장 상태만 남은 경우 보호가 실행 중이라고 표시하지 않도록 수정
- Guardian 전용 adaptive launcher icon과 버전 0.6.5-dev

## 5. 명시적 제한

- DoH/DoT 미지원
- 클라이언트 방향 TCP DNS 미지원 (업스트림 truncated UDP fallback TCP는 지원)
- IPv6 extension header 및 IP fragment 재조립 미지원
- 앱별 제어, 원격 목록 자동 업데이트, 포괄적 광고 목록 미지원
- 실기기에서 VPN 동작 및 광고 차단 결과는 별도 검증 필요

## 6. 빌드 및 테스트

```bash
gradle -p android --no-daemon clean testDebugUnitTest assembleDebug
```

산출 APK:
`android/app/build/outputs/apk/debug/app-debug.apk`

JDK 17, Android SDK Platform 35, Build Tools 35.0.0, Gradle 8.9가 필요합니다. GitHub Actions는 JUnit task, APK assemble, manifest/launcher asset, APK package ID/version, checksum 검사를 수행합니다.

## 7. 기기 수용 테스트

- APK 설치와 VPN 권한 승인
- 보호 시작·중지 반복
- 앱 강제 종료/프로세스 재시작 후 상태 표시
- 차단 도메인 NXDOMAIN 확인
- 허용 도메인 및 일반 도메인 정상 응답
- 업스트림 DNS 장애 시 SERVFAIL 확인
- Wi-Fi와 모바일 데이터 간 전환
- 화면 잠금 후 복귀 및 장시간 실행
- IPv4 및 IPv6 네트워크 환경 각각 확인

실제 Android 기기에서 실행하지 않은 항목은 완료로 처리하지 않습니다.


## 8. 0.6.5-dev 최종 CI 빌드 산출물

- CI 실행: [43423112883](https://github.com/justinhblee-prog/guardian/actions/runs/43423112883) — **Success**
- 빌드 대상 소스 커밋: `05dd104a97ea4f9f2bcf908514a6c82bf0e3b5f1`
- Android Gradle test + assemble: **BUILD SUCCESSFUL**
- Manifest / launcher resources / package ID / version 검사: **PASS**
- Package: `com.guardian.adblock`
- Version: `0.6.5-dev` / versionCode `11`
- Artifact ID: `14034534107`
- APK size: 849,811 bytes
- APK SHA-256: `daded536157e3a71fe205dd0a47513228e8ecacb88be784cda87925c4ee48130`
- Artifact ZIP SHA-256: `7b24f3abe7bd676389c32efc0afb1d26f6aa5fdc0038bfedc65c076cf9ab9942`
- ZIP test and extracted APK checksum match: **PASS**

25개 Android JUnit 테스트 메서드가 DNS/IPv4/IPv6 영역에 포함되어 있으며 Gradle test task가 성공했습니다. APK는 설치 가능한 Debug APK입니다. 실기기 수용 테스트는 별도이며 아직 완료로 간주하지 않습니다.


## 9. 0.6.5-dev 변경 사항
- `ProtectionState.kt`로 실제 VPN 터널 상태 판정을 분리하고 단위 테스트 추가.
- 앱 재시작 후 stale active flag 감지 시 저장된 active flag를 즉시 false로 정리.
- 사용자 규칙 저장 시 실제 VPN 터널이 실행 중인 경우에만 실시간 reload를 요청하도록 수정.
- 버전 0.6.5-dev / versionCode 12.


## 10. 0.6.5-dev 검증 산출물

- CI 실행: [43423810481](https://github.com/justinhblee-prog/guardian/actions/runs/43423810481) — **Success**
- 소스 커밋: `7fe4620c141e17e5735e18101982d6eb5059adf9`
- Gradle unit tests + APK assemble: **BUILD SUCCESSFUL**
- Package: `com.guardian.adblock`, versionCode `12`, versionName `0.6.5-dev`
- Artifact ID: `14035456605`
- APK size: 850,111 bytes; SHA-256: `df0180f41fa697be32bf0c83cbe0ffffc2147314159d4a3e0e482d7166051ed6`
- ZIP size: 842,081 bytes; SHA-256: `8208a69ccd413d8e46f3bd45fa1bea47be336b372ff7931cdf45f1cb5c5f106d`
- ZIP integrity and extracted APK checksum: **PASS**
- ProtectionState regression tests included and test task passed.

실기기 VPN 승인, 연결, 네트워크 전환 및 광고 차단 효과는 아직 검증되지 않았습니다.
