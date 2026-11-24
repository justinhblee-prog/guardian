## 2026-11-24 — Android 업데이트 화면 복원 누락 수정
- MainActivity는 설정 하위의 `settings-updates`를 저장·복원 대상으로 처리하지만 ActivityScreenState 허용 목록에는 빠져 있어 Activity 재생성 시 홈으로 떨어질 수 있었습니다.
- 허용 탭 목록과 복원 단위 테스트에 `settings-updates`를 추가했습니다.
- 소스 및 테스트 파일 원격 반영을 확인했습니다. Android Gradle/JUnit 및 기기 테스트는 실행하지 않았습니다.

## 2026-11-24 — DNS TCP stale RST 세션 종료 회귀 테스트 추가
- 연결 직후 지연 도착한 이전 시퀀스의 RST가 현재 DNS TCP 세션을 종료하지 않는지 확인하는 회귀 테스트를 추가했습니다.
- 같은 테스트에서 정상 clientNext 시퀀스의 RST는 세션을 정리하는지도 함께 확인하도록 구성했습니다.
- 변경은 테스트 코드에 한정됩니다. Android Gradle/JUnit 실행, 에뮬레이터·실기기 검증 및 GitHub Actions 실행은 하지 않았습니다.

## 2026-11-24 — 지원 진단 복사에도 안전한 DNS 상태 포함
- 별도 지원 진단 복사 기능에도 마지막 DNS 진단의 안전한 요약을 추가했습니다. 이제 '개인정보 제외 진단 정보 복사'와 '최근 오류 내용 복사' 모두 동일한 DNS 진단 축약 함수를 사용합니다.
- 서버 IP/URL은 원본 문자열에서 추출하거나 복사하지 않으며, 허용된 전송 유형과 성공/실패 분류만 표시합니다.
- 서버 IP와 URL 비노출을 확인하는 지원 진단 보고서 회귀 테스트를 추가했습니다.
- Android Gradle/JUnit 및 실기기 검증은 아직 실행하지 않았습니다. GitHub Actions는 실행하지 않았습니다.

## 2026-11-24 — 최근 오류 복사에 안전한 DNS 진단 추가
- 최근 오류 복사 보고서에 마지막 DNS 진단 요약을 포함하도록 연결했습니다. 기존에는 보호 시작 오류·호출 경로·필터 오류만 복사되어, DNS 업스트림 장애의 마지막 원인 분류가 빠졌습니다.
- 원본 진단 문자열에는 DNS 서버 IP, URL 및 네트워크 세부정보가 들어갈 수 있어 그대로 복사하지 않습니다. UDP/TCP/DoT/DoH 유형과 성공/실패 등 허용된 고정 문구만 보고서에 기록합니다.
- 회귀 테스트: 업스트림 오류 보고서에 전송 유형은 남고 서버 IP/URL은 노출되지 않는지, 성공 테스트는 지연시간 등 원문 상세를 제외하는지 확인하도록 추가했습니다.
- Android JUnit/Gradle 및 기기 검증은 아직 실행하지 않았습니다. GitHub Actions도 실행하지 않았습니다.

## 2026-11-24 — DNS TCP 미확인 응답 패킷 큐 상한
- 아주 작은 peer MSS로 분할 패킷이 급증하는 상황을 방어하기 위해 세션당 미확인 서버 패킷을 최대 256개로 제한했습니다.
- 전송 묶음은 기존 최대 64개를 유지하되, 큐 여유 공간만큼만 배출합니다. 누적 ACK가 패킷 슬롯을 회수하면 대기 DNS 응답을 이어서 전송합니다. FIN도 동일한 큐 상한을 준수합니다.
- MSS 1바이트를 광고하는 상대가 반복 ACK를 보내도 고유 미확인 응답 패킷이 256개를 넘지 않고, 누적 ACK 후 전송이 재개되는 회귀 테스트를 추가했습니다.
- MSS 테스트의 DNS 프레임 길이 접두부 2바이트를 반영해 기대 전송량을 3,002바이트로 정정했습니다.
- 원격 소스·테스트·문서 반영 확인만 수행했습니다. Android Gradle/JUnit, 에뮬레이터, 실기기 검증은 미실행이며 Actions 실행도 하지 않았습니다.

## 2026-11-24 — DNS-over-TCP 클라이언트 MSS 협상 반영
- SYN 패킷의 TCP 옵션을 길이 경계 검증 후 파싱하고, 유효한 MSS(Maximum Segment Size)를 세션별 응답 세그먼트 크기에 반영합니다. IPv4 기본 MSS 536, IPv6 기본 MSS 1220을 적용하고 Guardian의 패킷 크기 상한 1200바이트를 유지합니다.
- MSS 옵션 길이 오류·중복·0 값은 세션 생성 전에 거부합니다. 매우 작은 MSS가 응답 패킷 수를 폭증시키지 않도록 한 번의 drain에서 최대 64개 세그먼트만 배출하고 나머지는 후속 ACK/윈도 갱신 시 처리합니다.
- 회귀 테스트 추가: MSS 512 협상 시 모든 응답 payload가 512바이트 이하인지, 잘못된 MSS 옵션이 세션을 만들지 않는지 확인하도록 작성했습니다.
- 원격 브랜치에 소스/테스트 반영 및 재조회 확인. Android Gradle/JUnit 실행, 에뮬레이터·실기기 검증은 미실행이며 GitHub Actions는 실행하지 않았습니다.

## 2026-11-24 DNS probe Answer 섹션 범위 회귀 테스트 추가
- A/IN 레코드가 Additional 섹션에만 존재하는 응답은 IPv4 이름 해석 성공으로 인정하지 않는 회귀 테스트를 추가했습니다. 실제 질의 응답의 Answer 섹션에 주소가 포함되어야 합니다.
- 변경 커밋: `564502a4f12a782b9a4d80f3569fc291bb61a736`.
- 소스 및 테스트 파일 반영 확인만 완료했습니다. JUnit/Gradle 실행과 APK/기기 검증은 미실행입니다.

## 2026-11-24 DNS probe 회귀 테스트 fixture 정합성 보정
- 잘못된 A 레코드 길이 테스트의 RDLENGTH 바이트 위치를 DNS RR 헤더 기준으로 다시 맞췄습니다. 테스트 응답은 RDLENGTH=3 및 실제 RDATA 3바이트가 되도록 구성되어, 단순 패킷 절단과 구분해 잘못된 A 레코드 길이 자체를 검증합니다.
- 변경 커밋: `619bb4a838bfe3c1cf1be2c50f3a08471c31b6b6`.
- 테스트 소스 수정만 원격 반영 확인했으며 JUnit 실행은 하지 않았습니다.

## 2026-11-24 Android DNS 시작 전 업스트림 응답 검증 보강
- `DnsUpstreamProbe.isSuccessfulResolution`이 DNS 패킷 구조와 응답 레코드 경계만 확인하던 부분을 보완했습니다. 시작 전 probe는 A/IN 질의이므로, 완전한 TXT 응답이나 CNAME만 있는 응답을 IPv4 DNS 사용 가능 상태로 오인하지 않고 Answer 섹션에 실제 A/IN 주소 레코드(RDLENGTH=4)가 있는지 확인합니다.
- CNAME 체인은 최종 A 레코드가 Answer 섹션에 포함된 경우 계속 허용합니다. Authority/Additional 섹션의 A 레코드만으로 성공 처리하지 않습니다.
- 회귀 테스트 추가: 완전한 TXT 응답 거부, 잘못된 길이의 A 응답 거부. 기존 정상 A 응답, truncated RR, 압축 포인터 및 RDATA 경계 테스트를 유지합니다.
- 변경 커밋: `04f111e58a4246354e90b8e9cdc82b5145cfd3af` (검증 로직), `9b9176021b4da30f71ab4b29cff9b658a6c31fd3` (테스트 fixture 보정).
- 이 턴에서는 Android Gradle/JUnit 실행, 에뮬레이터 또는 실기기 검증, APK 생성은 수행하지 않았습니다. 따라서 코드/테스트 소스 변경이며 실행 검증 통과로 간주하지 않습니다.
- GitHub Actions는 실행하지 않았고 runner·billing·repository settings를 변경하지 않았습니다.

## 2026-11-18 정식 출시 준비 최신 보충 (PR #155 / #156)
- Main code commit: `794f98f7a5e2acc8443658b599e6ff00eb8a128f`.
- PR #155 merged: production GitHub Actions now rechecks source-level license ID, explicit redistribution approval and traceable reference from `GUARDIAN_FILTER_APPROVALS_JSON`; it requires the signed static origin branch `guardian-filter-data` before production signing.
- PR #156 merged: production defaults to `1.0.0 (versionCode 30)`, greater than current development `0.7.16-dev (29)`; local and CI checks reject non-monotonic release version codes.
- First switch from development signature to production signature requires uninstall/reinstall because Android rejects in-place APK updates when certificates differ. Subsequent production updates signed by the same production key can update in place.
- PR validations: #155 production workflow [44285423084](https://github.com/justinhblee-prog/guardian/actions/runs/44285423084), filter tests [44285423164](https://github.com/justinhblee-prog/guardian/actions/runs/44285423164), publisher tests [44285423259](https://github.com/justinhblee-prog/guardian/actions/runs/44285423259): success. #156 release validation [44286941377](https://github.com/justinhblee-prog/guardian/actions/runs/44286941377) and License API [44286941353](https://github.com/justinhblee-prog/guardian/actions/runs/44286941353): success.
- Main License API post-merge [44287304874](https://github.com/justinhblee-prog/guardian/actions/runs/44287304874): success. Main release validation [44287304813](https://github.com/justinhblee-prog/guardian/actions/runs/44287304813): check its live status before claiming final success.
- Current hard blockers: the public `guardian-filter-data` branch is absent; all seven example filters remain unapproved with no approval references; actual Windows/Tailscale/PostgreSQL deployment and production signing credentials have not been verified; physical-device VPN/DNS acceptance and the reported handset error remain outstanding.
- No production key, secret, billing option, runner configuration or user-local service has been created or changed.

## 2026-11-18 최신 통합 상태 — main 기준

### Android
- 최신 main 커밋: `bb52dbb8ea9b4b881b21b2b2db1d65e3ad765915` (PR #151 artifact 감사 워크플로 개선 포함). Android 앱 버전은 `0.7.14-dev`, versionCode `27`.
- Android main CI [run 44274922223](https://github.com/justinhblee-prog/guardian/actions/runs/44274922223): unit tests, lint, Debug APK build, filter asset identity, package/version, checksum, source archive integrity/secret exclusion, Android API 35 emulator upgrade/install/launch 및 VPN DNS E2E 전체 성공.
- 검증 Debug APK: 3,138,734 bytes, SHA-256 `b6d41d7c6511d34e49b4fc32516b942ba8e066222355b772d4458e0d2fc0394c`. 전체 소스 ZIP: 481,877 bytes, SHA-256 `9d0d468735244621a5badadbf51322e970d85f79d95858735242f02d783422e8`.
- Artifact: [guardian-android-debug](https://github.com/justinhblee-prog/guardian/actions/runs/44274922223) (artifact ID `14415100858`); 최종 에뮬레이터 결과 보고서 artifact ID `14414318118`.
- PR #146의 안전한 예외 종류/호출 스택 프레임 진단이 포함된 빌드이며, 개인정보 경로·URL·오류 메시지는 진단 요약에서 제외합니다.
- PR #150은 재배포 승인이 없는 필터 원본이 있으면 production signing 준비가 keystore 생성 및 GitHub secret 기록 전에 중단되도록 fail-closed gate를 추가했습니다. 현재 예시 원본 7종은 모두 재배포 승인 증빙이 없어 정식 필터 배포를 차단합니다.
- PR #151은 artifact 감사가 특정 버전 파일명에 종속되지 않도록 개선했습니다. 검증 요약의 versionName/versionCode/package를 읽어 일치하는 source ZIP을 찾고, 정확한 manifest 경로의 APK/source SHA-256과 ZIP 무결성을 검사합니다. 실제 CI artifact와 합성 미래 버전으로 로컬 검증했습니다. 병합 후 workflow_run 이벤트 기반 실행은 다음 Android main CI 완료 시 확인합니다.
- 사용자가 보고한 실기기 `Library file not found` 오류는 아직 실기기 로그로 재현·원인 확인되지 않았습니다. 에뮬레이터 성공을 실기기 해결로 간주하지 않습니다.
- APK는 Debug 개발 서명본입니다. production keystore/secrets, production signer fingerprint, Release APK/AAB 및 실제 배포 설치 검증은 아직 없습니다.
- 실기기 VPN/TUN 라우팅·네트워크 전환·실제 광고 제거 수용 테스트는 미실시입니다.

### 공통 운영 준비
- 서명 필터 배포 서버와 License API/PostgreSQL 구현 및 설정 스크립트는 저장소에 있으나, 운영 도메인·TLS·서명 개인키·공개키·DB가 실제 호스팅되어 end-to-end 검증된 상태는 아닙니다.
- 운영 keystore·개인키·비밀값을 저장소나 채팅에 추가하지 않았습니다.
- 필터 예시 원본의 재배포 승인 및 승인 참조가 비어 있어 production 필터 게시 게이트는 fail-closed입니다.
- 실제 라이선스 API/DB/Tailscale 운영 연결은 사용자 PC 환경 검증이 필요합니다.
- 운영 설정 스크립트는 사용자 PC의 로컬 키/서비스와 GitHub secrets/variables를 변경할 수 있으므로, 명시적 실행 권한과 로컬 사전조건 확인 없이 실행하지 않습니다.
- Windows 상세 안내: [WINDOWS_BUILD_KO.md](WINDOWS_BUILD_KO.md).

### Windows
- Windows DNS 엔진, desktop UI, daemon, filter updater 및 installer 구현은 저장소에 유지됩니다.
- 이전 main CI [run 44101373822](https://github.com/justinhblee-prog/guardian/actions/runs/44101373822)에서 개발용 EXE/installer 빌드, 설치·self-test·DNS 복구·제거 smoke test가 통과했습니다.
- 개발용 패키지는 unsigned이며, 실제 Windows PC의 어댑터 DNS 변경/복구, 재부팅, 네트워크 전환 및 VPN/보안 제품 공존성은 미검증입니다. Android 우선순위에 따라 최신 Windows 패키징은 후속 단계입니다.

### 다음 출시 판정 기준
- Android 1.0 필수 조건은 [issue #141](https://github.com/justinhblee-prog/guardian/issues/141), 앱 런타임 트랙은 [#142](https://github.com/justinhblee-prog/guardian/issues/142), 필터/라이선스 트랙은 [#143](https://github.com/justinhblee-prog/guardian/issues/143), 서명/배포 트랙은 [#144](https://github.com/justinhblee-prog/guardian/issues/144)에서 추적합니다.
- Debug/CI/에뮬레이터 성공만으로 Android 1.0 완료 또는 실기기 보호 정상 동작을 선언하지 않습니다.

# Guardian AdBlock Android — 개발/릴리스 상태

기준 버전: **0.7.10-dev**  
기준일: 2026-11-16  
저장소: https://github.com/justinhblee-prog/guardian

## 현재 기준 — 0.7.10-dev (2026-11-16)

- Android package: `com.guardian.adblock`, versionCode `23`, versionName `0.7.10-dev`.
- 최신 main 소스 커밋(문서 갱신 시점): `6d25f1d7d67abaea81931d1ced31c8dcab54bccb`.
- 직전 검증 패키지는 0.7.9-dev debug APK입니다. v0.7.10-dev artifact는 CI run #651 완료 후 SHA-256까지 재검증합니다.
- PR #25의 CI run `43690905433` 성공: Bash 구문 검사, Android unit tests, lintDebug, assembleDebug, API 35 emulator 설치·앱 실행, package/version 및 SHA-256 검증 통과.
- PR #38의 DNS-over-TCP sequence wraparound 회귀 테스트는 CI run `43694693871` 성공 후 main에 병합했습니다.
- License API strict UTF-8 및 malformed byte 입력 거부, signed filter 배포 파일 크기 제한, publisher 단일 작성자 잠금, Android signed-cache 무결성, HTTPS origin 검증, 라이선스 refresh/deactivation 경쟁 조건, 관리자별 기기 해제 기능은 각각 통합 테스트를 거쳐 main에 병합했습니다.
- 관리자 개별 기기 해제는 라이선스 전체 정지와 분리되며, 지정 기기만 해제하고 감사 이벤트를 남깁니다. 운영 관리자 토큰 및 운영 DB에서의 실제 배포 검증은 별도입니다.
- Production signing workflow의 설정 검증은 PR #40 run `43695827496` 성공. production signing job은 의도적으로 skipped이며, 운영 keystore/secrets 미설정 상태에서 서명본을 만들지 않았습니다.
- PR #40의 Android CI run `43695827493`은 문서 갱신 시점에 에뮬레이터 설치/실행 단계를 진행 중이므로 성공으로 기록하지 않습니다.
- main의 release workflow push 실행 실패는 PR #40이 아직 병합되지 않은 상태에서 발생한 기존 main workflow의 실행 결과입니다. PR #40은 main push 조건과 signing gate를 검증하도록 수정 중이며, 병합 후 main push 실행 결과를 다시 확인해야 합니다.
- CI 검증은 실기기 VPN/TUN 라우팅, Wi-Fi↔모바일 전환, 실제 광고 제거율 또는 외부 DoH/DoT provider 연결을 입증하지 않습니다.

## 현재 개발 작업 및 미완료 범위

- PR #40: production release 설정 검증을 main push에서도 실행하고 production 서명 작업은 수동 실행으로 제한합니다. 설정 검증은 성공했으며 Android emulator CI 완료 후 병합 여부를 판단합니다.
- Windows 설치 프로그램은 사용자 요청에 따라 마지막 단계로 보류합니다.
- 실기기 VPN/TUN 수용 테스트는 사용자 요청에 따라 추후 진행합니다.
- 운영 keystore, release secrets, 공개 HTTPS 서비스 주소, 공개키 및 운영 DB/도메인 연결은 실제 운영 환경에 설정·검증되지 않았습니다.
- 실제 광고 제거 효과, 네트워크 전환, 장시간 안정성 및 외부 DNS provider 연결은 별도 실기기/운영 검증이 필요합니다.

## 기존 차단 실패 원인

이전 0.6.5-dev는 기본 차단 규칙이 10개 도메인뿐이었고, 공식 광고 필터 목록 다운로드 및 Adblock 규칙 해석이 없었습니다. VPN 상태가 켜져 있어도 대부분의 광고 도메인이 차단 목록에 없었습니다.

## 구현 현황

| 영역 | 현재 구현 |
|---|---|
| DNS 보호 | Android VpnService, IPv4/IPv6 UDP 및 TCP DNS |
| DNS 응답 | NXDOMAIN / SERVFAIL, upstream transaction/question 검증, truncated upstream UDP의 TCP fallback 및 클라이언트 응답 1232바이트 제한/TC 응답 |
| 필터 프로필 | AdGuard DNS Filter, 1Hosts Lite, OISD Big, HaGeZi Normal, HaGeZi Pro |
| 보안 목록 | 선택형 HaGeZi Threat Intelligence |
| 앱 광고 SDK 목록 | 선택형 AWAvenue Ads Rule |
| 규칙 엔진 | Adblock 도메인 앵커, 예외, hosts, important, 출처별 badfilter, 사용자 block/allow |
| 캐시 | 크기/규칙 수 검증, 원자적 교체, 이전 정상 캐시 보존, 서비스 시작 직전 실제 파일 재검증 |
| DNS 업스트림 | Cloudflare, Google, Quad9, AdGuard DNS, AdGuard Family DNS, 사용자 지정 IPv4/IPv6 |
| 직접 DNS | 공용 DNS 직접 포착 고급 옵션(기본 비활성화), 시스템 가상 DNS |
| 자동 업데이트 | 앱 실행 시 만료 확인 + WorkManager 네트워크 연결 조건부 6/12/24시간 또는 7일 주기 백그라운드 갱신 |
| 앱 제어 | 설치 앱 검색 및 VPN 보호 제외 |
| 사용자 규칙 | 차단/허용 도메인 |
| 활동/진단 | DNS 활동, 통계, 최근 요청, 진단, 기록 초기화 |
| 자체 테스트 | 활성 VPN에서 활성 규칙 매칭 및 NXDOMAIN 생성 엔진 자체 테스트 |
| UI | 홈, 보호, 활동, 필터, 설정의 5개 화면 |

앱 실행 시 만료 여부를 확인하고, WorkManager가 네트워크 연결 시 백그라운드 갱신을 예약합니다. Android 전원 관리에 따라 실행 시각은 지연될 수 있습니다.

공용 DNS 직접 포착은 IP 단위 라우팅입니다. DNS 서버 IP로 향하는 비DNS 연결도 TUN에 들어올 수 있으며 앱은 이를 중계하지 않습니다. 이 때문에 기본값은 꺼져 있고 고급 옵션으로만 켤 수 있습니다.

## 최신 내부 검증

- [GitHub Actions 43434091375](https://github.com/justinhblee-prog/guardian/actions/runs/43434091375): **Success**
- 소스 커밋: `419356a1ef43ba20c33256b353566da7f42b8f61`
- Android unit-test 단계: **통과**
- 테스트 인벤토리(이후 변경 전 기록): **12개 스위트 / 68개 JUnit 테스트 메서드**
- APK 생성/업로드: push CI에서는 의도적으로 생략
- 직전 파서 오류 원인: `$important,badfilter`처럼 쉼표로 구분된 modifier에서 badfilter 토큰을 인식하지 못했습니다. modifier를 분리해 각각 인식하도록 수정했고 59개 테스트가 통과했습니다. 이후 직접 DNS 안전 기본값 테스트를 추가해 현재 인벤토리는 65개입니다.

## 남은 검증 및 제한

- 실기기 설치, VPN 권한, DNS 라우팅 및 실제 광고 차단 효과 미검증
- Wi-Fi↔모바일 데이터 전환, 화면 잠금, 장시간 실행 미검증
- DoH/DoT 미지원; TCP/53은 순서형 DNS 스트림 중심의 제한 구현이며 재전송/손실 복구는 미지원
- IPv6 확장 헤더 및 IP fragment 재조립 미지원
- 웹페이지 요소 숨김 및 앱 내부/first-party 광고 제거 미지원
- 공용 DNS 직접 포착의 실기기 라우팅 부작용 검증 필요

## APK 생성 및 릴리스 정책

- 모든 소스 push에서 CI가 Android 테스트와 debug APK 조립·패키지 식별·SHA-256 검증을 수행합니다.
- 일반 push에서는 APK artifact를 공개하지 않습니다. 수동 workflow 실행 또는 `[final-apk]` 커밋에서만 설치 APK artifact를 업로드합니다.
- 현재 최신 산출물은 0.7.2-dev 설치 가능 debug APK이며, Play Store production 서명본은 아닙니다.
- CI 성공은 실기기 VPN 라우팅이나 실제 광고 차단 효과를 증명하지 않습니다.


## DNS 테스트 정확성 및 서비스 복구

- 앱 자체 소켓은 Android VPN TUN을 우회하므로, 가상 DNS IP에 앱 소켓으로 질의하는 기존 자체 테스트 방식은 제거했습니다.
- 현재 테스트는 실제 네트워크 경로라고 주장하지 않고 활성 차단 규칙 매칭, NXDOMAIN 생성, 정상 도메인 비차단을 확인합니다.
- VPN 서비스는 `START_STICKY` 및 persisted-active 확인을 통해 OS 프로세스 회수 후 재생성 시 보호를 복구하도록 변경했습니다. 사용자가 중지한 상태는 복구하지 않습니다.
- 최신 테스트 인벤토리: 12개 suite / 68개 메서드. 실기기 복구 안정성은 아직 미검증입니다.


## Latest CI verification

- [GitHub Actions run 43436954231](https://github.com/justinhblee-prog/guardian/actions/runs/43436954231): **success**
- Source commit: `57f30f68484fa4d6ef716a557efe8a75a4446407`
- Android unit tests: passed; APK assembly/upload skipped because this was a push-triggered validation run.
- Inventory: 12 test suites / 68 test methods.


## UI polish verification

- DNS upstream, filter profile and update interval selectors now use dark themed selected/dropdown rows consistent with the Guardian dashboard palette.
- Status refreshes no longer retain/update detached home-screen views while the user is on other tabs.


## Latest verification update

- Explicit user stop now clears the persisted sticky-service restart flag even if VPN startup has not yet committed a TUN descriptor. Routine service cleanup without a live tunnel does not overwrite the original startup failure reason.
- Added two unit tests for this stop/start race policy.
- [GitHub Actions run 43436954231](https://github.com/justinhblee-prog/guardian/actions/runs/43436954231): **success** (Android unit tests passed; APK assembly intentionally skipped on push).
- Test inventory: 12 suites / 68 test methods.


## 백그라운드 필터 업데이트

WorkManager 2.12.0을 사용해 6/12/24시간 또는 7일 주기의 네트워크 연결 조건부 필터 갱신을 추가했습니다. 자동 업데이트 비활성화 시 예약 작업을 취소하며, 기본/활성화된 선택 필터를 검증·갱신합니다. 업데이트 중인 캐시 파일은 수동/백그라운드 작업 간 동시 교체되지 않도록 직렬화했습니다. 주기 설정 정책 단위 테스트 2개를 추가했습니다.


## Latest WorkManager verification

- [GitHub Actions run 43437792450](https://github.com/justinhblee-prog/guardian/actions/runs/43437792450): **success**
- Source commit: `5d7624fd445edadb1a1ed24a482b9e2d25f07d41`
- Android unit tests: **passed**; APK assembly was skipped because this was a push-triggered validation run.
- Test inventory: **12 suites / 68 test methods**.


## 최신 DNS 설정 변경
Cloudflare, Google, Quad9, AdGuard DNS IPv6 업스트림 프리셋 및 AdGuard 기본 IPv6 직접 포착 경로를 추가했습니다. IPv6 리터럴 검증 테스트를 포함했으며 CI 결과는 최신 실행 확인 후 기록합니다.


## 최신 CI — IPv6 DNS 프리셋
- 실행: [43443209122](https://github.com/justinhblee-prog/guardian/actions/runs/43443209122) — **성공**
- 소스 커밋: `f518711e559bfb8873128f82c07a1a157a5f2068`
- Android 테스트: 12개 스위트 / 68개 JUnit 메서드 통과
- APK 빌드: push 실행이라 생략. 최종 개발 완료 전 수동 APK 빌드를 실행하지 않음.


## CI 빌드 검증 정책 보강

- 모든 push에서 Android 단위 테스트 이후 debug APK를 실제 조립하고 package/version 및 SHA-256을 검증하도록 workflow를 변경했습니다.
- push 실행은 APK를 artifact로 공개하지 않으며, 수동 릴리스 실행만 APK를 업로드합니다.
- 최신 테스트 기준: 12개 스위트 / 70개 JUnit 메서드.
- 변경 커밋 `cc9f8bc0530015b16200f2ee3b141610fd9dd22d`의 빌드 검증 CI 실행 결과는 진행 중입니다.


## 최종 APK 빌드 산출물 — 2026-11-13
- [최종 빌드 실행](https://github.com/justinhblee-prog/guardian/actions/runs/43445545156): **success**
- 최종 소스 커밋: `29a10e46f27dd1bafdde72a62ddd984b9965a537`
- 테스트: **12개 스위트 / 70개 테스트 통과**
- APK 조립 및 패키지 식별 검증: 성공 (`com.guardian.adblock`, versionCode 14, `0.7.1-dev`)
- APK SHA-256: `4ee62c89678f0cef2b75d96a019a5700064538efaff4c82e9768a9bbf28ddb75`
- GitHub artifact: `Guardian-AdBlock-Android-APK-v0.7.1-dev`
- APK ZIP 무결성: 로컬 추출 후 `unzip -t` 통과
- 릴리스 판정: 설치 가능한 debug APK 산출 완료. 단, 실기기 설치·VPN 권한·Wi-Fi/모바일 전환·실제 광고 차단률은 아직 미검증이며, 완전한 AdGuard 대체품이나 모든 광고 차단을 보장하지 않습니다.


## 0.7.2-dev DNS UDP 응답 크기 안전성

- IPv6 최소 MTU를 고려해 UDP DNS payload 상한을 1232바이트로 제한합니다.
- 업스트림 응답이 상한을 넘으면 검증된 질문 섹션을 보존한 TC 응답을 생성해 TUN에 과대 패킷을 쓰지 않습니다.
- 클라이언트 방향 TCP/53 연결·프레이밍·순서형 스트림 처리를 소스에 추가했습니다. 다만 TCP 재전송/손실 복구는 구현하지 않아 네트워크 손실 상황까지 보장하지 않으며 실기기 resolver 재시도는 미검증입니다.
- 추가 회귀 테스트 2개 포함, 전체 12개 스위트 / 72개 테스트.


## 최종 0.7.2-dev APK — 2026-11-13

- [GitHub Actions 최종 실행](https://github.com/justinhblee-prog/guardian/actions/runs/43447121533): **success**
- 소스 커밋: `50e768e253ae3ee7d77d3cdc07b5f23ccd970eab`
- 테스트: **12개 스위트 / 72개 JUnit 테스트 통과**
- APK: `com.guardian.adblock`, versionCode 15, `0.7.2-dev`
- APK 크기: 2,978,452 bytes
- SHA-256: `a1d41cf8937b2c09f7c4ca8d0b02ddf252e95f935b1cee3ed1d485c5470f55d8`
- GitHub artifact: `Guardian-AdBlock-Android-APK-v0.7.2-dev` (ID `14049565251`)
- Artifact ZIP 무결성 검사 및 APK 체크섬 독립 확인 완료.
- 릴리스 유형: 설치 가능한 debug 서명 APK. Play Store production 서명 빌드는 아닙니다.
- 실기기 VPN 권한/라우팅, 네트워크 전환 및 실제 광고 차단률은 별도 미검증입니다.


## TCP/53 소스 구현 및 CI 검증

- TCP/53 codec 및 bounded session manager를 VPN 패킷 루프에 연결했습니다.
- SYN/ACK 시퀀스 처리, IPv4/IPv6 checksum, 분할 DNS 프레임 재조립, 다중 질의, DNS 응답 분할, FIN+payload 처리를 회귀 테스트합니다.
- [GitHub Actions 43452443314](https://github.com/justinhblee-prog/guardian/actions/runs/43452443314): **success** — 16 suites / 99 JUnit tests; debug APK assembly, package identity and SHA-256 checks also passed.
- 빌드된 검증 APK는 versionCode 15 / 0.7.2-dev이며 push CI 정책상 artifact 업로드는 생략했습니다. 이전 수동 배포 artifact는 TCP/53 변경 전 소스입니다.
- 제한: 동일 요청 세그먼트의 캐시 응답 재전송은 지원하지만 자체 재전송 타이머·부분 ACK 복구, IPv6 extension header, IP fragmentation, DoH/DoT, 실기기 통신 및 실제 광고 차단률은 미검증/미지원입니다.


## Protection UI

- Protection 탭에 실시간 보호 시작/중지, 자동 업데이트, 악성코드·피싱, Android 광고 SDK, 고급 DNS 포착 스위치를 추가하고 필터/앱별 제외 관리로 바로 이동할 수 있도록 했습니다.
- Push CI run 43459666939 passed with 14 suites / 86 tests and debug APK assembly/package/SHA-256 verification.

- Android lint is now part of CI; latest run 43459666939 passed lint with the broadcast receiver export flag corrected.

- DNS self-test is explicitly labeled as a local rule-engine check, not a live network-route or in-app ad-blocking test.


## DNS upstream transport

- DNS 업스트림 전송 방식을 UDP/53, TCP/53, DNS-over-TLS/853 중 선택할 수 있습니다. Cloudflare·Google·Quad9·AdGuard 계열은 TLS 호스트 이름을 자동 선택하고, 사용자 지정 IP는 인증서 호스트 이름을 별도로 입력할 수 있습니다.
- DoT endpoint handshake is implemented with SNI and certificate hostname verification; real provider connectivity/device testing is not yet verified. App-originated DoH/DoT bypass traffic remains outside the supported filter path.


## Encrypted DNS upstream modes

- 업스트림은 UDP/53, TCP/53, DNS-over-TLS/853, DNS-over-HTTPS/443 중 선택할 수 있습니다. DoH는 HTTP POST의 application/dns-message 본문을 사용하며 Content-Length와 chunked 응답을 제한된 크기로 검증합니다.
- HTTP parser rejects redirects, wrong media types, truncated/oversized bodies, and supports bounded chunked responses. TLS certificate hostname verification is enabled.
- CI validates build, lint and codecs; real provider TLS connectivity/on-device resolver operation remains unverified. App-originated DoH/DoT bypass is not intercepted.

- Fixed the saved-DoH-mode settings screen so TLS host controls remain visible after reopening; CI run 43459666939 passed.

- Added an end-to-end IPv6 DNS-over-TCP session regression test; latest CI run 43459666939 passed 16 suites / 99 tests, lint, APK identity and SHA-256 verification.


## 최신 CI 확인 — 2026-11-13

- [Run 43525073201](https://github.com/justinhblee-prog/guardian/actions/runs/43525073201) — **success**
- Android unit tests / lint / debug APK assemble / package identity / SHA-256 검증 성공
- 검증 소스: `827198814d0b4441afe72c2ddb81b6d1a41a8f19`
- 산출 APK 메타데이터: `com.guardian.adblock`, versionCode 15, `0.7.2-dev`
- SHA-256: `c40229d5c3a25173092233b2e23ace19d9c3d6f831837d6d6684c20f156495ee`
- 이 push 실행에서는 수동 배포 artifact 업로드가 skip되어 별도 설치 파일을 배포하지 않았습니다.
- CI 빌드 성공은 실기기 동작 검증이나 실제 광고 차단 성공을 의미하지 않습니다.


## 2026-11-14 — TCP DNS 세션 회귀 수정 및 빌드 검증

- 변경 커밋: `4c8511770039665da0eb61b018c460d86a8031e2`
- GitHub Actions: [run 43530335902](https://github.com/justinhblee-prog/guardian/actions/runs/43530335902) — **success**
- Android JUnit 단위 테스트: 통과
- Android lintDebug: 통과
- assembleDebug: 통과
- 패키지 확인: `com.guardian.adblock`, versionCode `15`, versionName `0.7.2-dev`
- 생성 APK SHA-256: `071fd9fc18288ba3144c747fb2a92beb9a93e3f58557ee49e9b086811f97f7ec`
- APK artifact 업로드: push 검증 정책에 따라 생략
- 이번 회귀 수정: 이미 연결된 DNS TCP 세션에 지연 도착한 동일 SYN이 들어와도 세션의 sequence state를 초기화하지 않도록 수정하고, 동일 SYNACK 재응답 및 이후 DNS 질의 처리를 검증하는 테스트를 추가했습니다. TCP SYN/FIN/RST의 모순된 조합도 거부합니다.
- Android 비공개 DNS 모드가 자동/호스트명으로 설정된 경우 DNS 필터 우회 가능성을 화면에 안내합니다.
- 위 결과는 CI 내부 검증이며, 실기기 설치·VPN 라우팅·실제 광고 차단 성공을 의미하지 않습니다.


## 2026-11-14 — 필터 다운로드 리디렉션 보안 강화 및 전체 CI 검증

- Source commit: `7988ced0500324df91900017993882e7cb10893b`
- GitHub Actions: [run 43531095663](https://github.com/justinhblee-prog/guardian/actions/runs/43531095663) — **success**
- Android unit tests: 통과 (**18개 테스트 스위트 / 110개 JUnit 테스트 메서드**)
- Android lintDebug: 통과
- assembleDebug: 통과
- APK package: `com.guardian.adblock`, versionCode `15`, versionName `0.7.2-dev`
- CI 생성 APK SHA-256: `902b8045209dc0d73ae34bb6d8740673b7f3fc45df0da9d5f6a6d3331826aada`
- APK artifact upload: push 검증 정책에 따라 생략
- 필터 다운로드는 HTTPS 전용 수동 리디렉션을 사용하며 HTTP 다운그레이드, URL 사용자정보, 미지원 리디렉션 상태 및 5회 초과 체인을 거부합니다. 관련 정책 단위 테스트 6개 추가.
- CI 성공은 실기기 설치, VPN 라우팅 또는 실제 광고 차단 효과 검증을 의미하지 않습니다.


## 2026-11-14 최신 내부 빌드 검증

- GitHub Actions: https://github.com/justinhblee-prog/guardian/actions/runs/43531690119 — **success**
- 검증 소스 커밋: `1616c8fdd11c92c1c982c3b24c4661242433d366`
- Android 단위 테스트: **통과** (`testDebugUnitTest`)
- Android 린트: **통과** (`lintDebug`)
- Debug APK 조립: **통과** (`assembleDebug`)
- 패키지/버전: `com.guardian.adblock`, versionCode `15`, versionName `0.7.2-dev` 확인
- APK 존재 및 SHA-256 산출: **통과**
- 설정 화면의 앱 버전 표기를 고정 문자열에서 `BuildConfig.VERSION_NAME`으로 변경하고 BuildConfig 생성을 활성화했습니다.
- 이번 push CI는 설치 APK artifact 업로드를 생략했습니다. 실제 기기 설치·VPN 라우팅·광고 차단 효과는 여전히 미검증입니다.


## 2026-11-14 DNS-over-TCP 재전송 안정성 검증

- GitHub Actions: https://github.com/justinhblee-prog/guardian/actions/runs/43533452981 — **success**
- 검증 소스 커밋: `a76491c4846f4eeb01bc0fc9cf2c0dd1e01fb183`
- Android 단위 테스트(`testDebugUnitTest`): **통과**
- Android 린트(`lintDebug`): **통과**
- Debug APK 조립(`assembleDebug`): **통과**
- 패키지/버전: `com.guardian.adblock`, versionCode `15`, versionName `0.7.2-dev`
- CI APK SHA-256: `b7a35a98f76ce20aa272358c9b0d2f5584a8ec7dd6f88308427840fbe7613b89`
- DNS-over-TCP 마지막 데이터/FIN 응답이 유실된 경우, 클라이언트의 정확한 재전송에 기존 응답 패킷을 재전송하도록 수정했습니다. 서버 FIN에 대한 ACK를 받을 때까지 half-close 세션을 유지합니다.
- 재전송 회귀 테스트 2개를 추가했습니다.
- push CI에서는 APK artifact 업로드를 생략했습니다. 실기기 설치, VPN 라우팅, 실제 광고 차단 효과는 검증되지 않았습니다.


## 2026-11-14 보호 상태 진단 정확도 개선

- VPN 터널 활성화만으로 DNS 필터링이 실제 진행 중이라고 단정하던 문구를 제거했습니다.
- 실제 관측 DNS 질의·차단·업스트림 전달·실패 건수를 표시하도록 변경했습니다.
- 질의가 0건이면 트래픽 미관측 상태로 표시하고, 앱/웹 트래픽을 발생시켜 확인하도록 안내합니다.
- Android 비공개 DNS 자동/호스트명 모드에서 암호화 DNS 우회 가능성을 보호 상세 상태에도 표시합니다.
- 진단 로직 조합에 대한 단위 테스트 5개를 추가했습니다. CI 검증 대기 중이며 실기기 광고 제거 검증은 포함하지 않습니다.


## 2026-11-14 보호 상세 상태 갱신 경로 수정

- 추가 코드 점검에서 보호 화면 최초 진입 시에는 관측 DNS 진단을 표시하지만, 이후 갱신에서는 저장된 일반 서비스 문구가 진단을 덮어쓸 수 있는 문제를 발견했습니다.
- 보호 중에는 실제 DNS 질의·차단·전달·실패 건수와 비공개 DNS 모드를 기준으로 상세 문구를 갱신하도록 수정했습니다.
- 보호가 꺼진 경우에는 VPN 권한 거부 등 구체적인 서비스 오류 문구를 유지합니다.
- 활성/비활성 갱신 경로 회귀 테스트 2개를 추가했습니다.
- 직전 진단 커밋 CI run 43535030810은 단위 테스트, lint, debug APK 조립, package/version, SHA-256 검증 모두 성공했습니다. 후속 수정 커밋의 CI는 별도 검증 예정입니다.


## 2026-11-14 — 다중 탭 보호 시작/권한 거부 크래시 수정

- 수정 커밋: `019a4bdf576e56c0f893b68822efcc4c77846c41` (이전 관련 수정: `f22cc99d02b80d38916bafaa3fe943be581a68c3`)
- GitHub Actions: [run 43563225658](https://github.com/justinhblee-prog/guardian/actions/runs/43563225658) — **success**
- Android 단위 테스트, `lintDebug`, `assembleDebug`, 패키지 식별 및 APK SHA-256 확인 단계 모두 성공
- 패키지: `com.guardian.adblock`, versionCode `16`, versionName `0.7.3-dev`
- Home 탭 전용 lateinit 상태 뷰가 아직 생성되지 않은 상태에서 Protection 등 다른 탭에서 보호 시작 또는 VPN 권한 거부를 처리할 때 발생할 수 있는 크래시를 수정했습니다.
- 필터 캐시 미준비 시 필터 탭을 먼저 생성한 뒤 안내하도록 변경했습니다. 보호 시작/실패/권한 거부 메시지는 현재 탭에 존재하는 UI만 갱신합니다.
- `refreshStatus()`도 Home 전용 뷰의 초기화 여부와 현재 탭을 확인하며, Protection 화면은 별도 상태 위젯을 갱신합니다.
- 이 push 실행은 수동 배포 전용 APK artifact 업로드를 skip했습니다. CI 조립 성공은 이 커밋의 APK가 사용자에게 배포되었다는 뜻이 아닙니다.
- 실기기 VPN 권한, 실제 DNS 라우팅, 앱별 동작 및 광고 제거 효과는 여전히 미검증입니다.


## 2026-11-14 — 보안 필터 즉시 재적용 회귀 수정

- Source commit: `d2e3689212280e5149c9b44012285edf323a971d`
- GitHub Actions: [run 43567779995](https://github.com/justinhblee-prog/guardian/actions/runs/43567779995) — **success**
- Android unit tests: **passed** (전체 테스트 단계 성공)
- Android `lintDebug`: **passed**
- Android `assembleDebug`: **passed**
- APK package/version: `com.guardian.adblock`, versionCode `16`, versionName `0.7.3-dev` — verified
- APK existence and SHA-256 generation: **passed**
- 보안 필터를 설정 화면에서 켜거나 끌 때 활성 VPN 규칙을 즉시 다시 불러오도록 수정했습니다. 캐시가 없으면 다운로드를 시작하고, 캐시가 있으면 VPN을 중단하지 않고 현재 규칙을 재적용합니다.
- 일반 push 검증이므로 artifact 업로드는 의도적으로 skip되었습니다. 이 CI 결과만으로 새 APK 파일이 배포된 것은 아닙니다.
- 실기기 VPN/DNS 경로와 실제 광고 제거 효과는 여전히 미검증입니다.


## 2026-11-14 — 보안 필터 중복 재적용 제거 및 검증

- Source commit: `170b4e96371b9f4072570ac06fb2e4d69f8f173e`
- GitHub Actions: [run 43568131394](https://github.com/justinhblee-prog/guardian/actions/runs/43568131394) — **success**
- Android unit tests, `lintDebug`, `assembleDebug`, package/version 검사 및 APK SHA-256 생성: **all passed**
- 패키지: `com.guardian.adblock`, versionCode `16`, versionName `0.7.3-dev`
- 보안 필터 캐시가 없어 다운로드가 필요한 경우 다운로드 전에 중복 VPN 규칙 재적용 요청을 보내던 경로를 제거했습니다. 캐시가 있는 경우에는 즉시 재적용하고, 다운로드가 필요한 경우에는 다운로드 완료 후 재적용합니다.
- 일반 push이므로 artifact 업로드는 skip되었습니다. 새 APK 배포는 아닙니다.
- 실기기 VPN/DNS 및 실제 광고 차단 효과는 미검증입니다.


## 2026-11-14 — 설정 화면 초기 이벤트 회귀 수정

- 소스 커밋 `2d96698`: 필터 업데이트 주기 Spinner의 초기 선택 콜백이 같은 주기의 WorkManager 작업을 불필요하게 재예약하지 않도록 수정했습니다. 실제 선택값이 변경된 경우에만 설정을 저장하고 `FilterUpdateScheduler.sync()`를 호출합니다.
- 회귀 검토 중 프로필 변경은 이미 다운로드 진행 중 차단되고 있음을 확인했습니다.
- [GitHub Actions run 43569003248](https://github.com/justinhblee-prog/guardian/actions/runs/43569003248): Android 단위 테스트, Lint, Debug APK 조립, 패키지명·버전 확인 및 APK SHA-256 생성 단계가 모두 성공했습니다. 일반 push이므로 APK artifact 업로드는 건너뛰었습니다.
- APK package: `com.guardian.adblock`, versionCode `16`, versionName `0.7.3-dev`.
- 이 검증은 자동화 CI 결과이며 실기기 설치·VPN 라우팅·실제 광고 차단 검증은 아닙니다.


## 2026-11-14 — DNS 필터 규칙 및 설정 회귀 검증

- `5db62fa`: Settings 화면을 열 때 DNS 선택 Spinner의 초기 콜백이 사용자 지정 DoT TLS 호스트명을 기본값으로 덮어쓰지 않도록 수정했습니다. [CI 43568689717](https://github.com/justinhblee-prog/guardian/actions/runs/43568689717) 성공.
- `2d966987`: Filters 화면 진입 시 업데이트 주기 Spinner의 초기 콜백이 WorkManager 주기 작업을 불필요하게 재예약하지 않도록 수정했습니다. [CI 43569003248](https://github.com/justinhblee-prog/guardian/actions/runs/43569003248) 성공.
- 두 CI 모두 Android 단위 테스트, Lint, Debug APK 조립, package identity/version 검사, APK 존재 및 SHA-256 검사를 통과했습니다. 일반 push이므로 배포용 artifact 업로드는 수행하지 않았습니다.
- `83501c1` / `d5fea43`: DNS 규칙으로 표현할 수 없는 URL 경로·후행 조건 규칙을 전체 도메인 규칙으로 넓혀 적용하지 않도록 파서를 보완하고 회귀 테스트를 추가했습니다. CI 실행 결과 확인 전입니다.


## 2026-11-14 — DNS 경로 제한 규칙 과잉 차단 회귀 수정

- `83501c1`: `||domain^/path` 및 `||domain^suffix`처럼 URL 경로·구분자 뒤 조건이 붙은 규칙을 DNS 전체 도메인 차단으로 확대하지 않도록 파서에서 제외했습니다.
- `d5fea43`: 경로 조건, 후행 조건, 예외 규칙, 정상 호스트 앵커를 함께 검증하는 회귀 테스트를 추가했습니다. 기대 결과는 정상 호스트 규칙만 차단 목록에 남고 나머지 3개는 무시되는 것입니다.
- [GitHub Actions run 43569255957](https://github.com/justinhblee-prog/guardian/actions/runs/43569255957) 성공: Android 단위 테스트, Lint, Debug APK 조립, 패키지 식별/버전 검사 및 APK 존재·SHA-256 검사가 통과했습니다. 일반 push이므로 APK artifact 업로드는 생략되었습니다.
- 자동화 CI 검증이며 실기기 네트워크·실제 광고 제거 효과를 입증하지는 않습니다.


## 2026-11-14 — 필터 업데이트 재시도 중 중복 다운로드 방지 / v0.7.4-dev

- `12c3cbf`: 백그라운드 Worker가 기본·보안·Android 앱 광고 목록의 캐시 유효성과 각 목록의 마지막 갱신 시각을 개별 판단하도록 변경했습니다. 선택 프로필이 바뀌었거나 캐시가 없거나 설정 주기를 넘긴 목록만 갱신합니다. 선택 목록 하나가 실패해 재시도하더라도 이미 성공한 다른 목록을 다시 내려받지 않습니다.
- 회귀 테스트에서 캐시 없음/메타데이터 없음, 주기 내 재시도, 갱신 주기 도달, 미래 시각, WorkManager 최소 간격을 확인했습니다.
- 버전: `com.guardian.adblock`, versionCode `17`, versionName `0.7.4-dev`.
- [GitHub Actions run 43570828960](https://github.com/justinhblee-prog/guardian/actions/runs/43570828960): 성공. Android 단위 테스트, Android lint, Debug APK 조립, package/version 검사 및 APK SHA-256 생성 모두 통과했습니다.
- CI 검증 APK SHA-256: `d60df611f004d5114d9e1aaeb832a5837ce4287c5dd1b7c51c5909c8a4cfb577` (약 2.9MB). 일반 push이므로 설치용 artifact 업로드는 건너뛰었습니다.
- 해당 APK는 CI 검증용 debug 빌드입니다. 최종 배포 APK가 아니며 실기기 VPN/DNS·실제 광고 제거 효과는 아직 검증하지 않았습니다.


## 2026-11-14 — DNS 주소 안전성 및 보호 범위 UI 문구 정리 / v0.7.5-dev

- `df7cfbd`: IPv6 주소 정규화 결과의 `hostAddress`가 nullable일 수 있는 경로를 안전하게 처리했습니다. Kotlin 컴파일 경고도 제거했습니다.
- `39eec62`: 홈 대시보드의 보호 범위 설명을 명확히 했습니다. Guardian 업스트림 DoT/DoH 지원과 앱 자체 암호화 DNS 우회 요청 미지원이 혼동되지 않도록, 앱이 직접 사용하는 DoH/DoT는 필터링하지 않는다고 구체화했습니다.
- 버전: `com.guardian.adblock`, versionCode `18`, versionName `0.7.5-dev`.
- [GitHub Actions run 43571389815](https://github.com/justinhblee-prog/guardian/actions/runs/43571389815): 성공. Android 단위 테스트, lint, Debug APK 조립, 패키지 식별/버전 및 SHA-256 검사 통과.
- CI 검증 APK SHA-256: `f756ab65ce8d74cc7a7da45457b0cc2987b83acc83e9c993eab0a3f624492e1a`. 일반 push이므로 artifact 업로드는 생략했습니다.
- 실기기 VPN·실제 광고 제거 효과는 여전히 미검증입니다.


## 0.7.6-dev — DNS-over-TCP resolver exception isolation (2026-11-14)
- Catch unexpected resolver callback exceptions inside the DNS TCP session manager and convert them to a DNS SERVFAIL response instead of allowing the packet loop to terminate the entire VPN service.
- Add regression coverage proving a thrown resolver exception returns SERVFAIL and the established TCP session continues to process the next DNS request.
- Package versionCode 19 / `0.7.6-dev`. CI verification pending.


## 0.7.6-dev — DNS-over-TCP resolver exception isolation (2026-11-14)
- DNS TCP session manager now catches unexpected exceptions from the resolver callback and returns a standards-shaped SERVFAIL response; a resolver exception no longer escapes to the VPN packet loop and terminates the tunnel.
- Regression test confirms SERVFAIL and successful processing of a subsequent query in the same TCP session.
- CI [43572032012](https://github.com/justinhblee-prog/guardian/actions/runs/43572032012) completed successfully: shared DNS rules and manifest checks, Android unit tests, Android lint, Debug APK assembly, package identity/version and APK checksum checks passed.
- Package: `com.guardian.adblock`, versionCode `19`, versionName `0.7.6-dev`.
- CI debug APK SHA-256: `b5ed0b9ee635eadc1ba5c337f94eed9c47767215198e2b60df5748fbf53e1e91` (about 2.9 MB). Ordinary push workflow skipped artifact upload.
- This is automated CI evidence, not physical-device validation; APK is debug-signed and not a production-signed release.


## 0.7.7-dev — Filter screen option race prevention (2026-11-14)
- The Rules/Filters screen uses CheckBox controls for optional security and Android-ad filters. Previously, an update started from that screen only disabled the corresponding Switch controls on the Protection screen, leaving the CheckBoxes interactive while the worker snapshot was downloading. This could change an option after its cache snapshot and leave an enabled filter without a validated cache.
- Track and disable both screen variants during the complete update, restore interaction afterward, and synchronize the control off when a first-time optional-list download fails.
- Package versionCode 20 / `0.7.7-dev`. CI verification pending.


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


## 2026-11-14 기준 정정 — v0.7.7-dev

이 절은 이전 문서에 남아 있는 v0.7.3-dev 및 v0.7.4-dev 기록보다 우선합니다.

- 최신 소스 커밋: `c8f60ef3f7f5af44e332defe570d4f475951162a` (릴리스 문서 정정 커밋)
- APK를 실제 빌드한 소스 커밋: `94bab42e1a9dd7e10b61fad52f2546fc075dbdcf`
- APK: `com.guardian.adblock`, versionCode `20`, versionName `0.7.7-dev`
- APK SHA-256: `4797b47bad0c92574e0b7f9b8bf347960c46e688c95be43ee6158b2d1a4a93e7`
- GitHub Actions: https://github.com/justinhblee-prog/guardian/actions/runs/43572937308
- 자동화 검증: Android 단위 테스트, lintDebug, assembleDebug, 패키지 식별/버전, 체크섬 및 소스 ZIP 패키징 성공
- 서명: Android debug 서명. Production 서명 아님.
- 미검증: 실기기 설치 및 VPN 승인, 실제 DNS 경로/광고 차단 효과, 네트워크 전환, 장시간 안정성.

주의: CI 자동화 성공은 실기기 수용 시험의 대체가 아닙니다. 정식 출시 완료로 판정하지 않습니다.


## 0.7.8-dev — TCP/IP reserved-header validation (2026-11-14)
- Hardened the DNS TCP packet parser: reject IPv4 packets with the reserved fragmentation flag set, and reject TCP headers with nonzero reserved/NS bits instead of silently accepting unsupported header semantics.
- Added regression coverage for both malformed-header cases.
- Package versionCode 21 / `0.7.8-dev`.
- CI run: https://github.com/justinhblee-prog/guardian/actions/runs/43573908206 — pending at documentation update time. Do not treat this source change as verified until that run completes successfully.
- This is parser hardening only; it does not prove physical-device VPN routing or real ad-removal effectiveness.


## Android v0.7.13-dev — VPN 시작 안정화 및 DNS E2E (2026-11-17)

- PR #73 squash merge: `99ad460bf309fe55b7503fa828d8eb4be25386ea`.
- Android CI [run 43918433672](https://github.com/justinhblee-prog/guardian/actions/runs/43918433672): unit tests, lint, debug APK, filter asset validation, emulator install/upgrade, VPN activation, allowed DNS resolution, blocked DNS rejection 모두 성공.
- 로그 통계: active=true, queries=4, forwarded=3, blocked=1, failed=0. 허용 `example.com` → `104.20.23.154`; 차단 `guardian-ci-43918433672.doubleclick.net` → unknown host.
- main merge 검증 run [44047861059](https://github.com/justinhblee-prog/guardian/actions/runs/44047861059)은 별도 실행 중으로, 성공 확인 전 main 최종 APK로 확정하지 않습니다.
- 미완료: 실기기 수용 테스트, 네트워크 전환/Private DNS/앱 제외 매트릭스, 실제 운영 필터/라이선스 서버 연결, 운영 keystore 기반 production 서명.


## Android 0.7.14-dev — 모바일 클라이언트 및 서버 도구 통합 (2026-11-17)

- PR #77 squash merge: `30a765cf539885f94a5924680a8d055d083afc79`.
- Android PR run [44051352597](https://github.com/justinhblee-prog/guardian/actions/runs/44051352597) 성공: unit tests, lint, 0.7.13→0.7.14 업데이트, APK identity/checksum, API 35 emulator VPN/DNS E2E.
- APK `com.guardian.adblock`, versionCode 27, versionName `0.7.14-dev`, 3,119,618 bytes; SHA-256 `24db2c1db46a66d2117cb35f7541c893b3321ee7de46363b615fd1a585319c78`.
- VPN E2E: active=true, DNS queries=6, forwarded=5, blocked=1, ad-blocked=1, failed=0. `example.com` 허용 응답, 테스트용 `doubleclick.net` 차단 확인.
- Signed filter distribution run 44052664109, filter distribution CI run 44052665884, License API/PostgreSQL integration run 44052664268 성공.
- PR #77에는 Android manifest/서명/해시 검증 클라이언트, Windows DPAPI 서명·공개 배포 스크립트, Tailscale private access 및 License API 운영 스크립트가 포함됩니다.
- 실제 Windows 노트북에서 private key 생성/배포, public CDN branch 게시, Tailscale 운영 연결, production keystore 설정은 아직 미수행입니다. Production-release job은 운영 키 부재로 skip되며 APK는 debug 서명본입니다.
- 실제 스마트폰·태블릿 네트워크 전환/제조사별 수용 테스트는 미수행입니다. main branch 재검증 run 44052664083 및 production configuration run 44052664040 결과는 완료 후 반영합니다.


## Android v0.7.15-dev — 개발판 앱 내 자동 업데이트 bootstrap (2026-11-18)

- PR #152 merged; updater implementation commit `d90d710678efbc09b56c0b7e7d981fd76f89d3b4`.
- Debug-only updater checks the GitHub development prerelease feed at app startup (six-hour throttle), plus a manual Settings action.
- Download verification: manifest SHA-256 and signer-certificate fingerprint must match the downloaded APK and currently installed development certificate. Android package installer still requires user approval.
- APK `com.guardian.adblock`, versionCode 28, versionName `0.7.15-dev`, 3,149,375 bytes; SHA-256 `90b0b92a12867c602aa5b305cd60f529ab907c9feb7e0df41d5370820fb0d701`.
- Development prerelease `guardian-dev-v28` includes `guardian-android-dev.apk` and `guardian-android-dev.json`.
- Follow-up `0.7.16-dev`, versionCode 29, prerelease `guardian-dev-v29`, was published with the same asset names for an immediate in-app update test.
- Main CI run [44282676162](https://github.com/justinhblee-prog/guardian/actions/runs/44282676162) completed success: unit tests, lint, APK build/checksum, previous-version upgrade, emulator install/launch, VPN DNS runtime verification and prerelease publication passed.
- CI/emulator verification only. Real handset download/install has not yet been observed; bootstrap v28 must be installed once over existing v27, without uninstalling Guardian.



## 2026-11-21 19:10 KST — Android 0.7.40-dev 및 공통 라이선스 최신 확인

- 최신 Android 소스 커밋: `dcf4ed4bc70a6edbbd766f118a9d8f5df98fbf06`.
- Android workflow [44892153515](https://github.com/justinhblee-prog/guardian/actions/runs/44892153515)에서 unit tests, lintDebug, assembleDebug 및 패키지/소스 ZIP 검증을 통과하고 artifact `guardian-android-debug` (ID `14702312109`)를 생성했습니다.
- APK: `com.guardian.adblock`, versionCode `53`, versionName `0.7.40-dev`, 3,213,935 bytes, SHA-256 `8634e59d73baa59cb103c3534afe027b877eac3e610e6cb0d1edb17c94e99fa4`.
- Source ZIP: `Guardian-AdBlock-Source-0.7.40-dev.zip`, 724,342 bytes, SHA-256 `9a1c6f71f6b97f47bd0804e8c076b234b0546b06a7118a37f5ce5a2ef229cfce`.
- Emulator runtime stage `Install and launch APK on Android emulator`는 문서 갱신 시점에 진행 중입니다. 따라서 VPN/DNS E2E 최종 결과와 런타임 검증 완료 판정은 보류합니다. Debug APK는 production 서명본이 아닙니다.
- Android workflow [44888566506](https://github.com/justinhblee-prog/guardian/actions/runs/44888566506)은 별도 실행에서 성공했습니다. 후속 커밋의 최신 run과 혼동하지 않습니다.
- Engineering Orchestrator [44892153570](https://github.com/justinhblee-prog/guardian/actions/runs/44892153570)은 repository integrity, Android build/unit tests/lint, backend/API/deployment configuration 및 verification decision 모두 성공했습니다.
- Local runner bootstrap validation [44892153506](https://github.com/justinhblee-prog/guardian/actions/runs/44892153506)은 PowerShell 구문·보안 정책 검사만 수행해 성공했습니다. 실제 노트북 runner 등록/연결 또는 Ollama 실행을 검증한 결과가 아닙니다.
- Android 필터 업데이트 오류 저장 경로에서 예외 원문 대신 안정된 오류 코드만 저장하고 UI에는 안전한 한국어 설명을 표시하도록 수정했습니다(커밋 `14064d2772fa3f0224bad7d7ead7079687674ec4`).
- Windows 공통 라이선스 개발 EXE/설치 패키지 빌드 및 설치 smoke test는 [44887248268](https://github.com/justinhblee-prog/guardian/actions/runs/44887248268)에서 통과했고, Windows 라이선스 테스트는 [44887248317](https://github.com/justinhblee-prog/guardian/actions/runs/44887248317)에서 통과했습니다. 개발 패키지는 Authenticode 서명본이 아닙니다.
- 공통 라이선스 제품 코드 `guardian-pro`와 Android/Windows 클라이언트 구현은 저장소에 반영됐지만, 실제 운영 RSA 키·Android production keystore·GitHub secrets/variables 등록, License API/PostgreSQL/TLS 배포 및 양 플랫폼 실제 기기 활성화 E2E는 아직 완료로 판정할 수 없습니다. Production workflow [44888522131](https://github.com/justinhblee-prog/guardian/actions/runs/44888522131)은 설정 검증 성공, production signing job skipped입니다.
- 정식 1.0.0 릴리스는 아직 아닙니다. 운영 키/서비스 배포, 승인된 서명 필터 게시, production signing, Android 실기기 VPN/DNS 수용 테스트, Windows 실제 PC DNS 설치·복구 테스트 및 두 플랫폼 공통 라이선스 E2E가 남아 있습니다.


### 2026-11-21 19:12 KST 추가 확인 — 0.7.40-dev 에뮬레이터 최종 성공

- 앞 절의 “에뮬레이터 runtime stage 진행 중” 상태는 완료 후 정정합니다.
- Android run [44892153515](https://github.com/justinhblee-prog/guardian/actions/runs/44892153515) 전체 성공. `Install and launch APK on Android emulator`, `Write final runtime verification result`, `Upload final verification report`, `Publish development APK update release` 모두 성공했습니다.
- 최종 검증 artifact ID `14703101227`의 `FINAL_VERIFICATION_KO.md`에서 API 35 emulator upgrade 및 VPN DNS E2E `success`를 확인했습니다.
- APK 및 source ZIP의 SHA-256은 앞 절에 기록한 값과 일치합니다. APK는 개발 Debug 서명본이며 실기기 수용 테스트 및 production 서명과는 별개입니다.


## 2026-11-21 최신 통합 검증 및 버전 정합성 보충

- Android 기준 개발 릴리스: `0.7.40-dev`, versionCode `53`, source commit `dcf4ed4bc70a6edbbd766f118a9d8f5df98fbf06` (GitHub prerelease `guardian-dev-v53`).
- 직전 Engineering Orchestrator run `44893925847`은 unit tests/lint/assemble 및 backend/API·Compose 검사 모두 성공했지만, APK가 Gradle 기본값 `0.7.34-dev / 47`로 빌드되는 버전 불일치를 확인했습니다. 해당 run의 APK는 최신 설치 업데이트용으로 사용하지 않습니다.
- 이 문제를 수정해 Engineering Orchestrator가 GitHub 개발 릴리스 기록에서 다음 단조 증가 버전을 계산하고, APK 내부 package/versionName/versionCode가 계산값과 일치하지 않으면 실패하도록 했습니다. 변경 commit: `fd97b1bedb0a8cefd7917c8bd3d26c0379ca9aa1`.
- 수정 후 통합 검증 run `44894398239`: version resolver 성공, Android unit tests 성공, backend/API/Compose tests 성공, repository integrity/automation safety 성공. Lint 및 APK 재빌드 결과는 해당 run 완료 후 별도 확인합니다.
- Windows 운영 서버 bootstrap은 `Setup-GuardianServer.ps1 -SkipAutoStart`를 지원하도록 개선했습니다. PowerShell 구문 및 Docker 독립 키 준비 정책 검증 [run 44893924995](https://github.com/justinhblee-prog/guardian/actions/runs/44893924995) 성공. 실제 로컬 키 생성·서버 배포를 수행했다는 뜻은 아닙니다.
- Signed filter distribution 및 CI [run 44893626225](https://github.com/justinhblee-prog/guardian/actions/runs/44893626225), [run 44893626235](https://github.com/justinhblee-prog/guardian/actions/runs/44893626235) 성공. License API CI [run 44893767261](https://github.com/justinhblee-prog/guardian/actions/runs/44893767261) 성공.
- Production release validation [run 44893767289](https://github.com/justinhblee-prog/guardian/actions/runs/44893767289) 성공: 설정·보안 게이트, 임시 키 기반 Direct APK/AAB 빌드 및 source archive 검증 통과. 운영 release keystore/secrets가 없어서 실제 production signing job은 skipped이며 정식 배포 파일은 생성되지 않았습니다.
- 남은 정식 1.0 차단 조건: 운영 RSA/Android keystore 생성 및 안전한 등록, License API/PostgreSQL/TLS 실제 배포, 필터 7종의 재배포 승인 증빙, Android/Windows 실운영 라이선스 E2E, 스마트폰·태블릿 및 Windows PC 실기기 수용 테스트, production 서명본 생성·설치 검증.


### 2026-11-21 통합 버전 수정 검증 완료

- Engineering Orchestrator [run 44894398239](https://github.com/justinhblee-prog/guardian/actions/runs/44894398239): **success**. Android unit tests, lint, APK assemble, backend/API/Compose tests, repository integrity 및 최종 fail-closed decision 모두 통과.
- APK metadata: package `com.guardian.adblock`, versionName `0.7.41-dev`, versionCode `54`; source commit `fd97b1bedb0a8cefd7917c8bd3d26c0379ca9aa1`.
- APK: 3,213,347 bytes, SHA-256 `510ef2a0cdef626e8d393f284483b1d6ecec06512820c8e263afb2a46f7ec685`. Artifact ID `14703292854` (`orchestrator-android-evidence`).
- 이 0.7.41-dev artifact는 통합 빌드의 버전 정합성·단위 테스트·lint·패키징 확인용 Debug 서명본입니다. 이 run에는 Android emulator VPN/DNS E2E가 포함되지 않으며, 0.7.40-dev에서 별도로 성공한 emulator E2E를 새 0.7.41-dev 빌드의 실기기/에뮬레이터 수용 테스트로 간주하지 않습니다.
- 직전 0.7.34-dev artifact는 versionCode rollback 위험 때문에 최신 업데이트 패키지로 사용하지 않습니다.


## 2026-11-24 정식 Android 릴리스 재점검 및 최신 개발 반영

### 이번 개발 반영
- VPN TUN 세션 소유권 generation guard를 추가했습니다. 이전 패킷 스레드가 늦게 종료되어 새 세션의 `running` 상태를 해제하거나 보호 상태를 중지시키는 교차 세션 정리 경쟁을 차단합니다.
- 패킷 루프 종료 요청을 해당 세션의 `startId`에 한정해 새 보호 시작 요청을 이전 종료가 취소하지 않도록 했습니다.
- 서명 필터 manifest timestamp의 복구 경로도 fsync 후 atomic publish로 통일했습니다.
- 휴대폰 설정 상세 화면에서 Android 시스템 뒤로가기가 설정 카테고리 목록으로 돌아오도록 수정했습니다.
- VPN session guard는 독립 Kotlin smoke test에서 PASS했습니다. Android Gradle/JUnit·lint·에뮬레이터·실기기 검증은 아직 실행하지 않았고, 이번 변경의 APK도 생성하지 않았습니다.

### 2026-11-24 확인된 정식 출시 차단 조건
- 최신 공개 개발 프리릴리스는 `guardian-dev-v58`이며 정식 production release가 아닙니다.
- `.github/workflows/android-production-release.yml`의 실제 production job은 `workflow_dispatch` 수동 실행으로만 동작합니다. 운영 keystore 및 비밀번호, filter/license 공개키, HTTPS manifest/API 주소, versionName/versionCode, filter approval 변수가 모두 필요합니다.
- production workflow가 요구하는 `guardian-filter-data` 브랜치는 현재 원격 저장소에서 확인되지 않았습니다. 빈 브랜치를 만들어 게이트만 통과시키지 않았습니다.
- 기존 정식 출시 상태 기록의 7개 필터 원본 재배포 승인 및 승인 참조 미확보가 계속 차단 조건입니다. 승인 증빙 없이 필터 데이터를 게시하거나 production 서명을 진행하지 않습니다.
- 실제 License API/PostgreSQL/TLS 배포 및 Android 실기기 VPN/DNS 수용 테스트는 여전히 완료 증거가 없습니다.
- GitHub Secrets/Variables의 값은 이 연결에서 읽을 수 없으므로 설정 완료로 추정하지 않습니다.

### 릴리스 판정
- **정식 1.0.0 완료 아님.** 현재까지의 코드 반영 및 독립 smoke test는 Android Gradle/JUnit·lint·에뮬레이터·실기기·production signing·설치 검증을 대체하지 않습니다.
- GitHub Actions는 기존 runner 시작 실패 이력과 비용/설정 변경 금지 조건에 따라 이번 작업에서 실행하지 않았습니다.
- 다음 통합 검증은 승인된 filter origin과 운영 signing/service prerequisites가 실제로 준비된 뒤에만 진행할 수 있습니다.


- 필터 업데이트 UI에서 Android 런타임 구성요소 로딩 실패가 발생할 경우 진행 플래그·컨트롤이 고정될 수 있는 경로를 보완했습니다. 선택 필터의 기존 캐시 보존 및 재시도 UI 복구를 반영했습니다.


- WorkManager 예약 필터 갱신에도 Android 구성요소 로딩 실패 처리 및 재시도/상태 알림 경로를 추가했습니다. 실제 WorkManager 실행 검증은 미실행입니다.


- TUN 커밋 이후 상태 게시/패킷 스레드 시작 실패 시 해당 세션을 롤백하는 처리를 추가했습니다. 이 변경은 코드 검토 반영이며 Android 빌드·실기기 검증은 미실행입니다.


- 취소된 VPN 시작 작업의 지연된 오류가 새 시작 요청의 TUN/상태/foreground 알림을 정리할 수 있는 경합을 보완했습니다. 시작 세대 게이트로 실패 게시 및 descriptor 정리를 직렬화하고 회귀 테스트 2개를 추가했습니다. Android Gradle/JUnit 및 기기 검증은 미실행입니다.


- VPN 시작 세대 게이트 동시성 회귀 테스트를 추가하고 순수 Kotlin/JVM smoke harness에서 잠금 직렬화·stale generation 거부를 실행 확인했습니다(`VPN_START_GATE_CONCURRENCY_SMOKE=PASS`). 이는 독립 정책 하네스 결과이며 Android Gradle/JUnit/앱 빌드 검증은 아닙니다.



## 2026-11-24 — DNS 업스트림 UDP/TCP·기기 DNS 복구 경로
- 무료 Android 소스 재점검에서 기본 UDP 업스트림이 실패할 때 동일 서버 TCP/53 재시도와 물리 네트워크 DNS 후보 탐색이 실제 `GuardianVpnService` 호출 경로에 연결되어 있지 않은 것을 확인해 복구했습니다.
- 현재 순서는 선택한 DNS의 UDP/53 → 동일 DNS의 TCP/53 → 물리 네트워크 DNS 최대 2개 → 공용 DNS fallback(Cloudflare/Google)입니다. 각 후보는 주소/응답 검증을 거치며, Guardian 가상 DNS 주소(IPv4/IPv6), loopback, multicast, link-local 및 잘못된 주소는 후보에서 제외합니다.
- 사용자가 TCP/DoT/DoH를 명시 선택한 경우 UDP로 강등하지 않습니다. UDP의 TC 응답도 중복 TCP 소켓을 열지 않고 상위 fallback 순서에서 TCP를 한 번만 시도하도록 정리했습니다.
- Android `ConnectivityManager`에서 물리 DNS를 읽기 위해 `ACCESS_NETWORK_STATE` 권한을 선언했습니다. 네트워크 정보 조회 실패는 보호 패킷 처리 중단 대신 기기 DNS fallback 생략으로 처리합니다.
- 순수 DNS 후보/순서 정책 테스트 4개를 추가했고 별도 Kotlin/JVM smoke harness에서 주소 정규화·IPv6 가상 DNS 제외·후보 순서·전송방식 보존을 실행 확인했습니다(`DNS_UPSTREAM_FALLBACK_POLICY_SMOKE=PASS`). Android Gradle/JUnit, 실제 Wi-Fi/모바일 DNS 연결은 미검증입니다.

- Android 필터 캐시 교체에서 백업 복구 이동 자체가 실패하는 경우, `.bak` 파일을 보존해 다음 프로세스 시작 시 `recover()`가 이전 정상 세대를 복구하는 회귀 테스트를 추가했습니다. 테스트 소스 반영이며 Android JUnit/Gradle 실행은 아직 미실시입니다.

- DNS upstream 응답 검증의 압축 이름 포인터가 DNS 헤더 영역(오프셋 0–11)을 가리키는 비정상 패킷을 거부하도록 보강했습니다. 해당 패킷을 재현하는 회귀 테스트를 추가했습니다. 소스 반영은 원격 재조회로 확인했으며 Android JUnit/Gradle 실행은 미실시입니다.

## 2026-11-24 — 개발판 APK 다운로드 캐시 원자적 교체
- 개발 업데이트 다운로드가 최종 설치 경로에 직접 기록하던 문제를 수정했습니다. 다운로드 중 앱 프로세스가 종료되면 부분 APK가 최종 파일명으로 남을 수 있었습니다.
- 이제 임시 .part 파일에 다운로드하고 크기·SHA-256·패키지·버전·서명 검증을 모두 통과한 뒤 GuardianApkCacheStore로 원자적 교체합니다. 기존 검증 APK는 새 파일 커밋 전까지 백업으로 보존됩니다.
- 캐시 디렉터리 생성 실패도 조기에 명시적으로 처리합니다. Android Gradle/JUnit, APK 다운로드 및 실기기 설치 검증은 실행하지 않았습니다.

## 2026-11-24 — 개발 업데이트 리디렉션 사전 검증
- 개발 업데이트 다운로드와 manifest/API 응답은 자동 리디렉션을 켠 뒤 최종 호스트를 검사하고 있어, 최초 연결 과정에서 허용되지 않은 리디렉션 대상에 먼저 접속할 수 있었습니다.
- 정식 업데이트 경로와 동일하게 리디렉션을 수동 처리하도록 변경했습니다. 각 Location을 다음 HTTPS 연결 전에 검사하고, 승인된 GitHub/API·Release asset 호스트만 허용하며 리디렉션은 최대 5회로 제한합니다. 개발 APK 다운로드는 API 호스트를 제외한 공식 Release asset 호스트만 허용합니다.
- 허용 CDN, HTTP, API 호스트의 APK 다운로드 사용, 위장 도메인, 사용자 정보, 비표준 포트 및 쿼리 문자열을 확인하는 URL 정책 회귀 테스트를 추가했습니다. Android Gradle/JUnit 및 네트워크 통합 테스트는 실행하지 않았습니다.

## 2026-11-24 — 업데이트 설치 대기 상태의 빈 APK 등록 차단
- 업데이트 상태 저장 계층에서 설치 대기 APK가 일반 파일이며 크기가 1바이트 이상인지 다시 확인하도록 방어 검사를 추가했습니다. 누락/빈 파일은 다운로드 완료 상태로 게시되지 않습니다.
- 회귀 테스트 `refusesToPublishMissingOrEmptyApkAsInstallable` 추가.
- 변경 커밋: `22930aa0090f424a438fbb4f4ff16352ad080a60` (상태 검증), `30d4032d4536026a6fdc5495ebf8a24d9bd7c613` (회귀 테스트).
- 독립 Kotlin/JVM smoke harness: `UPDATE_APK_STATE_SMOKE=PASS` (빈 파일 거부, 유효한 비어 있지 않은 파일 수락). Android Gradle/JUnit, APK 빌드, 실기기 설치 검증은 아직 실행하지 않았습니다.

## 2026-11-24 — 무료 Android 필터 업데이트의 영구 LinkageError 즉시 재시도 루프 방지
- 선택형 보안/Android 광고 필터 갱신 중 `LinkageError`는 네트워크 일시 실패가 아닌 런타임 기능 로딩 실패이므로, 같은 WorkManager 실행에서 즉시 재시도하도록 표시하지 않게 수정했습니다.
- 기존 유효 캐시가 있으면 이를 유지하고, 캐시가 없으면 기존 정책대로 해당 선택형 필터를 비활성화합니다. 일반 `Exception`의 일시 실패 재시도 동작은 유지합니다.
- 회귀 테스트 `permanentLinkageFailureDoesNotScheduleImmediateOptionalRetry` 추가.
- 변경 커밋: `4de637922f98bac41efdcda9013fd104176d6834` (Worker/policy), `d78435360afcfa70e647b038f7f86576e12bb915` (테스트).
- 독립 Kotlin/JVM 정책 smoke: `FILTER_LINKAGE_RETRY_POLICY_SMOKE=PASS`. Android Gradle/JUnit, APK 및 실기기 검증은 미실행.

## 2026-11-24 — 무료 Android 멀티 트랙 수정: DNS probe 및 앱 업데이트 동시성
- DNS/VPN 시작 검증 트랙: 완전한 A 응답처럼 보이더라도 DNS TC(Truncated) 플래그가 설정된 응답은 정상 IPv4 DNS probe 성공으로 인정하지 않도록 수정했습니다. 잘린 UDP 응답으로 DNS 경로를 정상 판정하지 않고 TCP fallback 경로가 처리하도록 합니다.
- 회귀 테스트 `rejectsTruncatedFlagEvenWhenTheIncludedAnswerLooksComplete` 추가.
- 앱 업데이트 트랙: 여러 화면/스레드에서 동시에 업데이트 확인을 시작해도 AtomicReference CAS gate가 단 하나의 실행만 허용하는지 확인하는 동시성 회귀 테스트 `concurrentUpdateChecksAllowExactlyOneOwner` 추가.
- 변경 커밋: `671bfb89ecc314eba745d5e44919de3a3cabcaa9` (DNS probe), `53e2d39196899fe9fe55d177a0fc7de1bc689937` (DNS 테스트), `de6473bc21c29ce758c56f6179759125c32a205e` (업데이트 동시성 테스트).
- 세 파일 모두 원격 브랜치 재조회로 변경 반영을 확인했습니다. Android Gradle/JUnit, 전체 APK 빌드 및 실기기 검증은 미실행입니다.

## 2026-11-24 — 무료 Android 멀티 트랙: 업데이트 시도 소유권 경합 방지
- 앱 업데이트 화면/상태 트랙에서 실제 경쟁 조건을 수정했습니다. 이전 확인 작업의 늦은 `finally`/예외/완료 콜백이 다음 확인 작업의 상태를 덮어쓸 수 있었으므로, `GuardianAppUpdateState.begin`이 시도별 ID를 발급하고 `updateStatus/complete/noUpdate/fail`이 현재 시도 ID와 일치할 때만 상태를 변경하도록 했습니다.
- `MainActivity`의 개발판/정식판 확인, 다운로드, 실패, 최종 정리 경로 모두 시작 시 발급받은 동일한 시도 ID를 전달합니다.
- 회귀 테스트는 오래된 시도의 실패/최신 응답이 새 확인 상태를 덮어쓰지 않는지, 동시 8개 시작 요청 중 하나만 소유권을 획득하는지, APK 상태 유지 및 비어 있는 APK 거부를 검사합니다.
- 커밋: `35ddb58e7e0b2a35335adf905d2a00f1d9ba3d27`, `9057ebc09c704be98365dfad19a729c0ade023d6`, `3e2a42efd51a537030f024857a0e5cb038c861e9`, `e11cd87ca46273f90a822fe92d0c79712e662a73`.
- 원격 소스 재조회 완료. 상태 소유권 핵심 로직을 복제한 격리 Kotlin/JVM 동시성 smoke: `APP_UPDATE_ATTEMPT_OWNERSHIP_SMOKE=PASS`. 이는 Android 모듈 컴파일/JUnit/Gradle/APK/실기기 검증을 대체하지 않습니다.
- 추가 회귀 범위: 이전 시도의 늦은 진행 상태, APK 완료, 실패, 최신 응답 콜백이 새 시도 상태를 덮어쓰지 않는지 모두 테스트하도록 확장했습니다. 테스트 커밋 `567f3c8afe16e4790879444acc143f52b5f3e0ea`.


## 2026-11-24 — 무료 Android 보호 시작 사전 실패 복구 및 선택 기능 격리
- VPN 서비스가 비동기 TUN 시작 전에 포그라운드 서비스 승격을 수행하는 경로에서 예외가 그대로 onStartCommand 밖으로 전파될 수 있었습니다. 이 경우 복구 의도 플래그가 남아 sticky 재시작 때 같은 실패가 반복될 수 있어, 실패를 명시적으로 저장하고 복구 플래그/활성 상태를 정리한 뒤 해당 서비스 시작 요청을 중단하도록 수정했습니다.
- 포그라운드 승격 실패 시 안전한 알림 정리, 개인정보를 노출하지 않는 예외 진단 저장, 상태 브로드캐스트를 수행합니다. 일반 시작, sticky 복구, 자동 일시중지 설정 변경 경로에 모두 적용했습니다.
- 자동 일시중지 대상 앱 감시 초기화는 선택 기능이므로, 패키지 조회/권한/모니터 초기화 예외가 핵심 VPN 시작까지 막지 않도록 격리했습니다. 부분 초기화된 모니터를 정리하고 선택 기능만 끈 뒤 VPN 보호 시작 경로를 계속 진행합니다.
- 커밋: 6e7f3e55479ed153c205afc95fbf1b2e526eb380 (포그라운드 승격 실패 처리), 42b1618f69a429a05c80ae367389b1d9996230db 및 d8f4df839d2998b0fa4d71a21b2303e2bf3db2b6 (선택 모니터 예외 격리 및 부분 초기화 정리).
- 원격 소스 재조회 및 세 호출 지점/복구 플래그 정리/선택 모니터 격리 정적 확인 완료. Android Gradle/JUnit, APK 빌드, VPN 권한 승인 및 실기기 시작·DNS 차단 검증은 미실행입니다.


- 후속 점검에서 자동 일시중지 모니터의 stop 예외가 VPN 시작/명시적 중지 요청을 중단시킬 수 있는 경로를 추가로 차단했습니다. 모니터 세대를 먼저 무효화하고 참조를 분리한 뒤 정리 실패는 선택 기능 오류로만 기록합니다. 서비스 종료 중 Android 포그라운드 알림 제거가 실패해도 TUN 종료·상태 정리·stopSelf가 계속 진행되도록 보호했습니다.
- 추가 커밋: 521efc36375216a26ed0f2dcc0b63adb6052d572 (선택 모니터 종료 격리), 6857d51c048b7ff4f9bc34010ac75816a33bb529 (VPN 종료 경로 보강). 원격 재조회 및 정적 흐름 확인 완료. Android 빌드/JUnit/실기기 테스트는 미실행입니다.


- 무료 Android 필터 재적용 경로에서 일반 예외뿐 아니라 런타임 `LinkageError`도 명시적으로 처리하도록 보강했습니다. 파서/선택 구성요소의 링크 오류가 규칙 재적용 백그라운드 스레드의 미처리 오류로 남지 않으며, 기존 메모리 규칙과 실행 중인 VPN 세션을 유지하고 화면에 적용 실패 상태를 게시합니다. 로그에는 예외 코드만 남깁니다.
- 코드 커밋: `55e4511b8bc5685656cb084a0768940a6c9ea8d1`. 정적 소스 확인 및 원격 재조회 예정이며 Android Gradle/JUnit·APK·실기기 검증은 미실행입니다.


- DNS 처리 트랙에서 업스트림 resolver 호출 중 `LinkageError`가 발생하면 기존에는 일반 예외 처리 경계를 벗어나 DNS 패킷 워커 전체가 종료될 수 있었습니다. 이를 요청 단위 실패로 격리해 해당 질의에는 SERVFAIL을 반환하고 VPN 패킷 루프는 계속 유지하도록 수정했습니다.
- 회귀 테스트 `upstreamLinkageErrorReturnsServfailWithoutEscapingPacketHandler` 추가. 코드 커밋: `607b4f6fc9f856311db502336f5b60d99cf85330`, 테스트 커밋: `93e1d278f5a8ef1f2634b61a513c40a9f21816a7`. Android Gradle/JUnit 실행은 아직 미실행입니다.


- VPN 시작 시 필터 적재와 사용자 규칙 재적용이 서로 다른 worker thread에서 겹칠 수 있는 경로를 보완했습니다. 전체 설정 읽기·캐시 파싱·규칙 스냅샷 교체를 서비스 인스턴스 단위로 직렬화해, 느리게 끝난 이전 로드가 다른 로드와 진단 상태를 교차 변경하지 않도록 했습니다. `DomainFilter`의 단일 volatile 규칙 스냅샷 교체 방식은 유지합니다.
- 코드 커밋: `7a027cc6a0361a0eb3c332743c22e358b68e1996`. Android 서비스 통합 테스트/실기기 동시 갱신 검증은 미실행입니다.


- 선택 필터 피드 로더의 예외 경계를 정리했습니다. 기존 `runCatching`은 `OutOfMemoryError` 같은 VM 치명 오류까지 삼켜 빈 규칙으로 계속 진행할 수 있었습니다. 이제 일반 예외와 선택 구성요소의 `LinkageError`만 해당 선택 피드 실패로 격리하고, VM 치명 오류는 상위 오류 처리로 전파합니다.
- 회귀 테스트 추가: `skipsOptionalFeedWhenParserLinkageFailsButKeepsVpnStartupAvailable`, `doesNotSwallowVirtualMachineFatalErrorsFromOptionalFeed`. 코드 커밋: `09d056590f15cb5c7df7f68e48c93613f5f7a3d0`, 테스트 커밋: `b274bfe9ef0677b39d7f5afc646d1cb639486000`. Android Gradle/JUnit 실행은 미실행입니다.

- 로컬 Kotlin/JVM 격리 스모크 실행 결과: `PASS skipsOptionalFeedWhenParserLinkageFailsButKeepsVpnStartupAvailable`, `PASS doesNotSwallowVirtualMachineFatalErrorsFromOptionalFeed`. 추가로 정상 피드 수용 및 최소 규칙 수 미달 피드 제외 경로도 PASS. 실행 환경에 JUnit/Android SDK/Gradle 프로젝트 런타임이 없어 실제 Android JUnit 클래스나 Gradle 테스트 태스크 실행은 아직 아닙니다. 이 결과는 격리된 Kotlin/JVM 실행 검증으로만 분류합니다.
