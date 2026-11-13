# Guardian AdBlock Android — Verification Record

기준 버전: 0.7.0-dev  
기준일: 2026-11-13

## 현재 회귀 테스트 인벤토리

| 테스트 스위트 | 테스트 메서드 |
|---|---:|
| DomainFilterTest | 17 |
| Ipv4UdpTest | 6 |
| Ipv6UdpTest | 4 |
| ProtectionStateTest | 5 |
| FilterRuleParserTest | 11 |
| DnsProbeTest | 4 |
| FilterRuleComposerTest | 3 |
| DnsAddressValidatorTest | 2 |
| FilterListProfilesTest | 8 |
| VpnConfigurationDefaultsTest | 1 |
| VpnStartCancellationGateTest | 4 |
| **합계** | **65** |

총 11개 Kotlin 테스트 스위트 / 65개 JUnit 테스트 메서드입니다.

## 최신 성공 CI

- Workflow: https://github.com/justinhblee-prog/guardian/actions/runs/43434091375
- 소스 커밋: `419356a1ef43ba20c33256b353566da7f42b8f61`
- Android unit-test 단계: **Success**
- APK assemble / upload: push CI 정책에 따라 skipped
- 이전 Adblock parser 복구 CI: https://github.com/justinhblee-prog/guardian/actions/runs/43433588312 — 59 tests completed, 0 failures

## 내부 검증된 설계 및 코드 경로

- AdGuard DNS Filter, 1Hosts Lite, OISD Big, HaGeZi Normal/Pro 프로필
- 선택형 HaGeZi Threat Intelligence 및 AWAvenue Android Ads Rule
- 원자적 파일 교체와 마지막 정상 필터 캐시 보존
- 선택 프로필 및 켜진 보조 필터 캐시가 유효하지 않으면 보호 시작 차단
- VPN 서비스 시작 직전 실제 캐시 파일 재파싱 및 최소 규칙 수 확인
- `$important` 차단 규칙은 allow 예외보다 우선 적용
- `$badfilter`는 같은 출처의 일치하는 modifier 변형만 해제
- 도메인 차단/허용은 DNS label suffix hash lookup 사용
- DNS 응답 transaction ID / 질문 이름 / QTYPE / QCLASS 확인
- DNS 규칙 엔진 자체 테스트는 현재 활성 규칙 매칭과 NXDOMAIN 생성만 검증하며 TUN 네트워크 경로 검증과 구분
- VPN 시작/중지 취소 경합 회귀 테스트
- 공용 DNS IP 직접 포착은 기본 비활성화하여 비DNS 트래픽의 부수적 차단 위험 완화

## 미검증 / 미지원 범위

- 실제 Android 기기 설치 및 VPN 권한 승인
- 실기기에서 필터 목록 다운로드, TUN 라우팅 및 실제 광고 차단 성공
- Wi-Fi↔모바일 데이터 전환, 장시간 실행, 화면 잠금/복귀
- DoH/DoT, 클라이언트 방향 TCP DNS
- IPv6 확장 헤더 및 IP fragment 재조립
- 웹페이지 요소 숨김, 앱 내부/first-party 광고 제거
- 앱별 제외 및 고급 공용 DNS 직접 포착의 실기기 검증

공용 DNS 직접 포착은 Android VPN의 IP 단위 라우팅을 사용합니다. DNS 서버 IP로 가는 비DNS 트래픽도 TUN에 들어올 수 있으며 앱은 이를 중계하지 않습니다. 이 기능은 기본 비활성화입니다.

## APK 정책

- 개발 중 push CI에서는 테스트만 수행합니다.
- 개발 완료 전 중간 APK를 최종본으로 제공하지 않습니다.
- 최종 APK는 별도 수동 릴리스 workflow에서만 생성합니다.
- APK 생성 시 package/version, APK/ZIP 무결성, SHA-256을 확인합니다.


## Self-test correction and sticky VPN recovery

- Removed the misleading app-owned UDP socket probe: Android excludes the VPN app's own sockets from its VPN route, so querying the virtual DNS IP from the app itself does not prove packets traversed the TUN.
- The self-test now truthfully validates loaded rule matching, NXDOMAIN RCODE generation, and non-blocking of `example.com` in-process. It does not claim to verify network routing.
- VPN service now returns `START_STICKY` and rebuilds its TUN only when Android recreates it with a null intent and the persisted protection state was active. Explicit stop remains non-restoring.
- Latest source CI: https://github.com/justinhblee-prog/guardian/actions/runs/43435146415
- Source commit under test: `ea97e9482afa009f7f1bc5ff718428ce6c282ec7`
- Android unit-test step: **success**; full workflow cleanup was still running when recorded.
- Test inventory at this commit: 11 suites / 65 test methods.


## Latest CI verification

- [GitHub Actions run 43436954231](https://github.com/justinhblee-prog/guardian/actions/runs/43436954231): **success**
- Source commit: `da7e9fab22a38fc90e1c20739358239a5c994a0b`
- Android unit tests: passed; APK assembly/upload skipped because this was a push-triggered validation run.
- Inventory: 11 test suites / 65 test methods.


## UI polish verification

- DNS upstream, filter profile and update interval selectors now use dark themed selected/dropdown rows consistent with the Guardian dashboard palette.
- Status refreshes no longer retain/update detached home-screen views while the user is on other tabs.


## Latest verification update

- Explicit user stop now clears the persisted sticky-service restart flag even if VPN startup has not yet committed a TUN descriptor. Routine service cleanup without a live tunnel does not overwrite the original startup failure reason.
- Added two unit tests for this stop/start race policy.
- [GitHub Actions run 43436954231](https://github.com/justinhblee-prog/guardian/actions/runs/43436954231): **success** (Android unit tests passed; APK assembly intentionally skipped on push).
- Test inventory: 11 suites / 65 test methods.
