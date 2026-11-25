# Guardian 프로젝트 통합 인수인계서

> 기준일: 2026-11-21 (KST)  
> 저장소: `justinhblee-prog/guardian` / 기본 브랜치 `main`  
> 단일 Android 출시 게이트: [Issue #141](https://github.com/justinhblee-prog/guardian/issues/141)

## 1. 제품 목표와 진행 순서

Guardian은 Android 및 Windows에서 DNS 기반 광고·추적·악성 도메인 보호와 공통 라이선스 정책을 제공하는 제품입니다.

출시 순서는 **Android 1.0 완료 → Windows 정식 버전 완료 → 태블릿/PC 멀티 디바이스 통합**으로 고정합니다. Android 1.0 범위는 Issue #141에 고정하고, 출시를 직접 막는 보안·보호·복구 결함이 아닌 추가 UI/기능은 출시 후 백로그로 보냅니다.

## 2. 작업·검증 원칙

- 가능한 저장소 조사, 코드 수정, 문서화, GitHub PR/CI 작업은 사용자에게 반복 실행을 요청하지 않고 수행합니다.
- 실제 노트북의 로컬 파일·Docker·휴대폰·운영 비밀값은 이 작업 환경에서 직접 접근할 수 없으므로, 그 결과를 확인한 것처럼 말하지 않습니다.
- 구현, 자동화 테스트, 에뮬레이터, 실기기, 운영환경 검증을 구분합니다.
- Debug APK, 임시 서명 Release APK/AAB, 운영키로 서명한 Production APK/AAB는 서로 다른 산출물입니다.
- 운영 개인키, keystore, 관리자 토큰, 환경파일 및 고객 데이터는 저장소/문서/채팅에 기록하지 않습니다.
- Actions runner/billing/usage 설정과 비용 발생 설정은 사용자 승인 없이 변경하지 않습니다.
- 한 번 확인한 실패 원인을 반복하지 않고, 근본 원인과 재현 가능한 검증을 우선합니다.

## 3. Android 1.0 출시 필수 조건

Issue #141만을 최종 완료 기준으로 사용합니다.

1. VPN 보호 시작/중지, 앱 재실행·서비스 재시작·재부팅 후 상태 복구
2. 허용 DNS 응답, 차단 DNS 차단, 통계/카운터 일관성
3. 사용자 규칙 저장, 화면 회전·앱 재시작 후 보존 및 VPN 적용
4. 필터 다운로드, 서명·해시 검증, 캐시, 실패 시 마지막 정상 데이터 유지
5. 실제 라이선스 API 연결, entitlement 확인 및 기기 등록/갱신/해제
6. 사용자 소유 운영 서명키를 안전하게 사용한 Production Release APK
7. 인증서 지문, 패키지 ID, 버전, APK SHA-256 검증
8. 설치·업그레이드·핵심 회귀 검증 및 설치 문서

에뮬레이터 성공은 실기기 수용시험을 대체하지 않습니다. 실제 Samsung 기기에서 보고된 `Library file not found`는 기기 로그를 통한 원인 확인이 필요합니다.

## 4. 2026-11-20 현재 확인된 상태

### 4.1 Android 앱

- 최신 확인된 개발 APK: `com.guardian.adblock`, `0.7.34-dev`, versionCode 47, 3,210,279 bytes.
- APK SHA-256: `9daa4663b90b98e5800fe66dbc50978bebee6a1aa74ce853af8974a6c52c7804`.
- Source ZIP: `Guardian-AdBlock-Source-v0.7.34-dev.zip`, 674,030 bytes; SHA-256 `c2306943346b534bd1dc95f0a159ba4020964004e9b1a787648d8cb00cec7a67`.
- Exact-head Android workflow [44766753106](https://github.com/justinhblee-prog/guardian/actions/runs/44766753106) passed unit tests, lint, APK identity/checksum, API 35 emulator upgrade/install, VPN DNS allow/block, custom rules, app exclusions, update recovery, Support Center FAQ/form/private lookup, foreground-app auto-pause/resume and Home protection start/stop.
- This is a development-signed Debug APK and emulator evidence, not a production-signed release or Samsung physical-device acceptance.
- The historical handset-only `Library file not found` message remains undiagnosed. The inspected APK includes the bundled `assets/filter_rules.txt`; the app does not bundle app-owned JNI/native libraries. The actual caller must be identified from the handset's safe `GuardianProtection` stack summary and installed app version.
- The Android Settings-entry update check and cooldown policy are integrated. The v0.7.34 exact-head build and emulator acceptance are verified; stable production update remains gated by production signing and endpoint configuration.
- The local AI coding worker is implemented in `scripts/guardian-copilot-worker.ps1` and wired into the Git agent, but it is **not yet proven operational**: the Windows self-hosted Aider/Qwen runner has not produced a successful real-inference log and generated draft PR.

### 4.2 License API / PostgreSQL

2026-11-20 사용자가 제공한 Windows 노트북 실행 결과:

- Docker API 및 PostgreSQL 컨테이너가 healthy 상태.
- 로컬 및 Tailscale HTTPS `/ready` 응답 HTTP 200, database와 signer ready.
- QA 흐름에서 라이선스 발급, 기기 활성화 및 서명 entitlement, active 조회, entitlement 갱신, 기기 비활성화, Free 상태 확인, QA 라이선스 회수까지 7개 항목 모두 통과.
- 기존 DB 백업과 운영 RSA 키는 유지했습니다. 키 회전, DB 초기화, Actions 비용/runner 설정 변경은 하지 않았습니다.

서버 측 License API/DB lifecycle 검증은 완료로 기록하며 동일 테스트를 반복하지 않습니다. PR #224는 최신 고객 디렉터리와 Support Center/admin inbox 통합을 main에 반영했고, License API/지원 흐름의 CI PostgreSQL 테스트도 통과한 이력이 있습니다. Android 앱의 실제 운영 endpoint 연결, 결제 webhook, Samsung 실결제 검증은 별도 항목입니다.

### 4.3 필터 배포

- 공개 `guardian-filter-data` 브랜치 생성/게시 여부는 최신 확인에서 미완료입니다.
- 설정된 7개 예시 필터 원본은 재배포 승인값이 false이고 승인 참조가 비어 있습니다.
- 권리자 승인 근거를 확인하기 전에는 상용 배포용 필터로 게시하거나 승인 상태를 임의 변경하지 않습니다.

### 4.4 Production 서명 / 결제

- 1.0.0, versionCode 43 기준의 Production 빌드 설정 및 임시 서명 검증 경로가 CI에서 확인된 이력이 있습니다.
- 사용자 소유 운영 keystore/secrets가 없으므로 Production 서명 APK/AAB는 아직 확인되지 않았습니다.
- 상품 코드 및 관리자 수동 발급은 결제 연동이 아닙니다. 서명된 결제 webhook, 중복 이벤트 방지, 환불/취소/갱신 동기화와 체험판 중복 발급 방지는 상용 판매 전 별도 구현·검증이 필요합니다.
- Lifetime은 진정한 무기한 entitlement와 폐기/갱신/오프라인 검증이 구현되기 전에는 발급하지 않습니다.

## 5. 고객지원센터 / 라이선스 운영 통합

- PR #224 `Add parallel Guardian engineering orchestrator`가 2026-11-20 main에 merge commit `1eb4bc55b21cf6352c55923b491502b920630abb`로 반영되었습니다.
- 현재 고객 디렉터리/라이선스 관리 흐름을 보존한 Support Center, FAQ/문의 접수/비밀 코드 조회, 관리자 문의함, 라이선스 상품 기간 검증, migration 003/004 통합이 main에 포함됩니다.
- 기존 PR #205는 58개 이상 커밋이 분기되고 mergeable=false였으며, 변경 경로 19개 중 18개가 PR #224에 포함되어 2026-11-21 superseded 상태로 종료했습니다. 유일하게 남은 handoff 문서는 main의 이 문서를 별도로 갱신합니다.
- 이전 PR #220/#223도 PR #224의 통합 결과로 대체되어 종료했습니다. 이 종료는 병합을 의미하지 않습니다.
- 노트북 DB에 migration을 다시 적용하거나 기존 DB를 초기화하지 않았습니다. 사용자 제공 결과의 실제 운영 DB 상태를 보존합니다.
- 관리자 이메일 알림, 결제 webhook, 중복 결제 이벤트 처리, 실결제 및 Lifetime entitlement는 상용 판매 전 별도 검증이 필요합니다.

## 5.1 병렬 개발 자동화 상태

- `.github/workflows/guardian-engineering-orchestrator.yml`은 Android build/unit/lint, backend/API/Compose, repository integrity를 독립 job으로 검증하고 aggregate decision은 fail-closed로 동작합니다.
- `scripts/guardian-git-agent.ps1`은 PR 유지관리와 CI 로그 수집을 수행하며, `scripts/guardian-copilot-worker.ps1`을 주기 실행에 연결합니다.
- 코딩 worker는 로컬 Aider/Ollama Qwen을 우선 확인하고, 준비되지 않았을 때 Copilot CLI를 보조 경로로 사용합니다. Copilot의 credit soft limit은 무료 사용 보증이 아닙니다.
- Windows PC에서 실제 Qwen 추론 성공 로그 또는 Copilot 작업 로그와 생성된 draft PR이 확인되기 전에는 로컬 AI 구현 자동화가 운영 중이라고 판정하지 않습니다.
- GitHub Actions는 build/test/verification만 수행합니다. GitHub Actions가 스스로 AI 코드를 작성한다고 설명하지 않습니다.

## 6. Android 출시 이후 Windows 작업

Android 1.0 게이트가 닫힌 뒤 Windows를 진행합니다.

1. Windows 소스, 공통 DNS 필터 엔진, 서비스/UI/updater/installer 전수 조사
2. DNS 처리, IPv4/IPv6, upstream timeout/fallback, 네트워크 변경 및 서비스 재시작 검증
3. DNS 어댑터 설정 백업·적용·rollback·재부팅 복구
4. 서명 manifest, 필터 무결성, 캐시 rollback, 라이선스 연동
5. 보호 상태·시작/중지·업데이트·진단·설정 UI 품질 감사
6. 실제 EXE 및 설치 패키지 생성 후 새 설치/업그레이드/실행/제거/네트워크 원복 검증

현재 Windows 보호 구현을 WFP 커널 드라이버로 표현하지 않습니다. 실제 아키텍처와 제품 안내를 일치시킵니다.

## 7. 기준 문서

- [Android 1.0 잔여 작업 및 검증 계획](REMAINING_WORKFLOW_KO.md)
- [Android 1.0 단일 출시 게이트](https://github.com/justinhblee-prog/guardian/issues/141)
- [고객지원/라이선스 운영 PR](https://github.com/justinhblee-prog/guardian/pull/205)

매 작업 완료 시 날짜, commit/PR/Actions, 변경 파일, 검증 종류와 결과, 미완료 blocker를 이 문서 또는 세부 문서에 기록합니다. 과거 APK 버전·체크섬은 이력으로 유지하고 최신 산출물로 재사용하지 않습니다.


## 5.2 Android VPN 제외 설정 재적용과 명시적 중지의 직렬화 (2026-11-25)

- `GuardianVpnService`의 앱 제외 재적용은 기존 TUN을 닫고 패킷 워커 종료를 기다린 뒤 새 TUN을 생성합니다.
- 기존 구현에서는 제외 재적용 스레드와 사용자의 명시적 중지 요청이 서로 다른 실행 경로였습니다. 재적용이 복구 의도를 먼저 읽은 뒤 사용자가 중지를 요청하면, 재적용이 새 세션을 시작하려는 경합이 발생할 수 있었습니다.
- `reconfigureExcludedApplications`를 `@Synchronized`로 보호하고, 명시적 중지의 복구 의도 변경도 동일한 `stopProtection` 임계 구역 안에서 수행하도록 정리했습니다. 따라서 중지와 제외 재적용은 서비스 인스턴스 기준으로 순서가 결정되며, 중지가 먼저 처리되면 재적용은 저장된 복구 의도(false)를 확인하고 종료합니다.
- 변경 커밋: `e5e29137eb2733fb72ad1346197d9baf6f432b68`
- 원격 소스 재조회로 변경 내용을 확인했습니다. Android JUnit/Gradle 실행, 에뮬레이터 및 실기기 VPN 재연결 검증은 아직 수행하지 않았습니다.
- 네트워크 전환은 현재 서비스가 매 DNS 전달마다 실제 네트워크 DNS 후보를 다시 조회하고 upstream 소켓을 새로 생성하는 구조입니다. 다만 Wi-Fi↔모바일 데이터 전환 중 기존 TUN/라우팅의 OEM별 동작은 기기 E2E 검증이 필요하며, 이를 소스 검토만으로 완료 처리하지 않습니다.


### 앱 제외 적용 상태의 세션 커밋 일치성 (2026-11-25)

- 기존에는 `Builder.establish()`가 TUN descriptor를 반환한 직후 `KEY_EXCLUDED_APPS_APPLIED`와 `KEY_EXCLUDED_APPS_ERROR`를 저장했습니다. 이후 start generation 검증에서 취소되면 descriptor는 닫히지만, 적용 목록은 성공한 VPN 세션이 없는 상태로 남을 수 있었습니다.
- 새 시작 세대가 소유권을 얻으면 이전 세션의 적용/실패 목록을 먼저 비우고, 새 TUN이 `tryCommitStart` 및 `tunnelSessionGuard.activate`를 통과한 뒤에만 적용/실패 목록을 기록하도록 순서를 변경했습니다.
- 변경 커밋: `7c1feba16c06434636612173d4cbd005601c5fdc`
- 원격 파일 재조회에서 이전의 establish 직후 저장 구문이 제거되고, 세션 활성화 커밋 경로에 저장이 위치하는 것을 확인했습니다. JUnit/Gradle 및 기기 검증은 아직 실행하지 않았습니다.


---

**완료 판정:** 소스 구현, 자동화 테스트, 에뮬레이터, 실기기, 운영환경, Production 산출물의 증거가 각각 확보되어야 해당 단계만 완료 처리합니다. Android 1.0 필수 조건이 모두 충족되기 전에는 정식 출시 완료라고 선언하지 않습니다.


### 물리 네트워크 DNS 후보 선택의 결정성 개선 (2026-11-25)

- 기존 `systemDnsServers()`는 VPN이 아닌 `allNetworks`의 DNS 서버를 반환 순서 그대로 평탄화하고 최대 2개를 선택했습니다. 인터넷 capability가 없는 네트워크의 DNS가 먼저 포함되면 현재 사용 가능한 후보가 뒤로 밀릴 수 있었습니다.
- 네트워크별 `NET_CAPABILITY_INTERNET`을 확인하고, Android가 물리 네트워크를 `activeNetwork`로 노출하는 경우 우선합니다. VPN이 활성화되어 물리 active network를 직접 식별하지 못하면 validated 물리 네트워크를 우선합니다. 주소 정규화·중복 제거 후 최대 2개만 전달합니다.
- 정책 테스트 2개를 추가했습니다: 활성 물리 네트워크 우선 및 인터넷 capability 없는 네트워크 제외, 잘못된/중복 주소 제거 및 후보 수 제한.
- 소스 및 테스트 코드 변경이며 Android JUnit 실행, Gradle 빌드, Wi-Fi↔모바일 실기기 전환 검증은 아직 수행하지 않았습니다. Android API 반환 동작과 실제 전환 복구 시간은 통합 검증 대상입니다.


### DoH 응답 파서의 단일 읽기 기한 적용 (2026-11-25)

- DoH HTTP 응답 파서는 헤더와 chunk framing을 바이트 단위로 여러 번 읽습니다. 기존 소켓 읽기 제한 3500ms는 각 개별 read에 적용되므로, 원격 서버가 매 read 제한 직전에 한 바이트씩 보내면 응답 전체가 제한 시간보다 훨씬 오래 걸릴 수 있었습니다.
- DnsDohHttpCodec.readResponse에 read 전 검사 콜백을 추가하고, 서비스는 응답 파싱 시작 시각을 기준으로 남은 시간을 매 read마다 소켓 읽기 제한에 다시 설정합니다. 응답 헤더와 본문 전체가 3.5초의 단일 읽기 예산을 공유하므로 느린 바이트 전송으로 제한을 우회할 수 없습니다.
- 테스트 코드에 매 read 검사 콜백 호출 및 기한 만료 시 즉시 중단하는 케이스를 추가했습니다.
- 이는 DoH 응답 파싱 구간의 제한이며, DNS 전체 upstream 후보 순회에 대한 총 지연 상한을 의미하지 않습니다. 연결/TLS handshake 및 UDP/TCP 후보 전체 예산은 별도 검토 대상입니다.
- Android JUnit/Gradle 실행, Lint, APK 빌드 및 네트워크 E2E는 이 환경에서 실행하지 않았습니다.


### DNS-over-TCP 및 DoT 응답의 단일 읽기 기한 적용 (2026-11-25)

- DNS-over-TCP와 DNS-over-TLS(DoT)는 2바이트 길이 접두부와 DNS 본문을 순차적으로 읽습니다. 기존 `SO_TIMEOUT=3500ms`는 개별 blocking read 제한이어서, 응답 길이 접두부/본문을 조금씩 보내는 서버가 전체 응답 처리 시간을 늘릴 수 있었습니다.
- DoH 응답 파서와 같은 원리로 `DnsDohHttpCodec.readDnsTcpResponse`를 추가했습니다. 길이 접두부를 검증한 뒤 DNS 본문을 읽으며, 각 read 전에 동일한 3.5초 응답 기한의 남은 시간을 소켓 제한에 반영합니다.
- 일반 DNS-over-TCP와 DoT 경로가 새 공통 reader를 사용하도록 변경했습니다. UDP 경로와 연결 수립 시간 제한은 변경하지 않았습니다.
- 정상 프레임, 잘못된 길이, 공유 기한 만료 테스트를 추가했습니다. 기존 DNS 응답 트랜잭션/질문 검증은 읽기 완료 후 그대로 수행합니다.
- 이 변경은 소스·테스트 반영 확인이며 Android JUnit/Gradle, Lint, APK, 실제 TCP/DoT 네트워크 시험은 실행하지 않았습니다.


### Upstream 후보 순회 지연 상한 분석 (2026-11-25)

- UDP 모드 후보는 설정 DNS 1개 + 물리 네트워크 DNS 최대 2개 + 공개 fallback 2개로 최대 5개입니다. 중복 주소는 제거됩니다.
- 기본 시도는 설정 DNS의 UDP/TCP 2회와 나머지 후보의 UDP 최대 4회로 최대 6회입니다. 각 후속 UDP 후보가 TC(truncated) 응답을 반환하면 해당 주소에 TCP 재시도를 추가할 수 있어 최대 10회까지 늘어납니다.
- 일반 TCP 시도는 connect 3500ms와 응답 읽기 3500ms가 별도 구간입니다. 따라서 단순히 10회 × 3500ms로 전체 지연을 계산하면 실제 최악 지연을 과소평가합니다. TCP 재시도가 모두 필요한 상황은 이론상 약 52.5초 수준까지 누적될 수 있습니다(각 TCP connect/read가 모두 timeout이고 4개 후속 UDP 후보가 모두 TC를 반환하는 보수적 상한).
- 이번 변경은 DoH, DoT, 일반 TCP의 개별 응답 읽기 구간을 각각 3.5초 단일 예산으로 제한했지만, 후보 전체를 묶는 전역 deadline은 아직 적용하지 않았습니다.
- 전역 deadline은 남은 시간을 UDP receive, TCP connect/read, TLS connect/handshake/read에 일관되게 전달해야 하므로 단순히 후보 수를 자르거나 모든 socket timeout을 낮추는 방식으로 처리하지 않습니다. 다음 작업에서 전송별 timeout 인자와 TLS handshake 구간을 함께 설계·검증합니다.


## DNS 질의 전체 업스트림 시간 예산 적용 (2026-11-25)

- 단일 DNS 질의 시작 시 monotonic clock 기반 `DnsQueryDeadline`을 만들고, 기본 전체 예산을 8,000ms로 설정했습니다.
- 설정된 리졸버 → 물리 네트워크 DNS → 공개 DNS 후보 순서, UDP 우선/TC 응답의 TCP 재시도, 명시적 TCP·DoT·DoH의 전송 강제 정책은 유지하면서 모든 후보와 재시도가 같은 deadline을 공유합니다.
- UDP receive timeout, TCP connect/read, DoT·DoH connect/handshake/read가 남은 예산을 사용합니다. TLS 핸드셰이크와 소켓 쓰기는 SO_TIMEOUT만으로 trickle/blocked 동작을 완전히 제한할 수 없으므로, deadline 만료 시 활성 소켓을 닫는 daemon watchdog도 연결했습니다.
- 후보 실행 전 deadline이 소진되면 추가 연결을 시작하지 않고, 전체 예산 소진을 진단에 남깁니다. 후보마다 3.5초를 새로 부여하던 누적 지연 구조를 제거했습니다. 완료된 소켓의 취소된 watchdog 작업은 스케줄러 지연 큐에서 즉시 제거해 DNS 트래픽이 많을 때 작업이 누적되지 않게 했습니다. UDP 잘림 응답에 따른 별도 TCP 재시도 경로도 응답 직후 deadline을 다시 확인해, 늦게 도착한 응답을 성공으로 반환하지 않도록 했습니다.
- 순수 Kotlin 테스트 `DnsQueryDeadlineTest`에 남은 시간 감소/올림, 만료 후 재시도 차단, 소켓 timeout 상한, 0 이하 예산 거부, watchdog 만료 시 소켓 종료 검증을 추가했습니다.
- **실행 상태:** 별도 Kotlin 컴파일러로 `DnsQueryDeadline.kt`를 컴파일하고 시간 예산 감소·만료·watchdog 소켓 종료·watchdog 취소를 실행하는 독립 하네스를 통과시켰습니다. Android SDK/Gradle 실행기가 없어 저장소 JUnit은 실행하지 않았고, Android 앱 전체 컴파일도 미실행입니다. GitHub Actions도 과거 runner 할당 단계 실패가 재현된 이력이 있어 이번 변경에서는 실행하지 않았습니다.
- **기기 검증 잔여:** Android에서 실제 UDP/TCP/DoT/DoH timeout, TLS handshake 중단, 네트워크 전환 및 정상 응답 지연을 측정해야 합니다. 8초는 전체 질의 상한이며 실제 단말에서 성공률/지연을 측정한 값은 아닙니다.


## Android 보호 시작의 기본 필터 라이브러리 로딩 경로 점검 (2026-11-25)

- Android Gradle main assets source는 `android/app/../../shared`이며, 이는 저장소 루트의 `shared/` 디렉터리입니다. `preBuild`에 연결된 `validateGuardianSharedFilterAsset`가 `shared/filter_rules.txt`의 존재·비어 있지 않음·유효한 block 규칙 포함 여부를 검사합니다.
- 현재 브랜치에서 `shared/filter_rules.txt`가 존재하며 1,308 bytes입니다. 실제 파일 내용을 독립 Kotlin 컴파일러로 `FilterRuleParser`에 넣어 확인했고, 54개 차단 도메인이 파싱되며 무시된 줄은 0개였습니다.
- 홈 화면의 시작 전 점검과 서비스의 `loadConfiguredRules()`는 내장 asset을 읽지 못하거나 규칙이 비어 있으면 `DomainFilter.bootstrapRuleLines()`의 긴급 기본 규칙으로 전환합니다. 다운로드 캐시 및 보안/Android 광고 필터는 선택 사항이며, 사용할 수 없어도 기본 규칙으로 VPN 시작을 진행하도록 구성되어 있습니다.
- `loadConfiguredRules()`는 `@Synchronized`로 보호되어 시작 로딩과 사용자 규칙 재적용이 동시에 규칙 스냅샷을 교체하지 않습니다.
- **현재 판단:** 저장소 소스에서 기본 asset 누락이 보호 시작을 반드시 중단시키는 경로는 확인되지 않았습니다. 실제 APK에 asset이 패키징되었는지와 단말의 로더 오류는 APK 빌드·설치·진단 로그 없이는 확정할 수 없습니다. 사용자가 이전에 본 “Library file not found” 메시지의 실제 발생 지점도 현재 소스만으로 특정하지 못했습니다.
- **검증 상태:** Gradle asset 검증 task와 Android 시작 경로는 소스 점검만 했으며 실행하지 않았습니다. 별도 Kotlin parser 하네스에서 공유 baseline 54개 규칙 파싱은 통과했습니다. APK 내부 asset 확인과 실기기 시작 테스트는 통합 검증 단계에 남아 있습니다.

## Android 자동 일시중지 종료 대기 중 서비스 잠금 점유 수정 (2026-11-25)

- `pauseProtectionForCompatibility()`가 `@Synchronized` 전체 메서드로 선언되어 TUN 닫기와 패킷 작업자 `join(5초)` 동안 서비스 모니터를 계속 점유했습니다. 그 사이 명시적 보호 중지 요청이 같은 모니터를 기다려 최대 5초 지연될 수 있었습니다.
- 메서드 전체 동기화를 제거하고, 명시적 중지와 경합해야 하는 시작 의도 확인·취소 세대 갱신·TUN 분리·일시중지 의도 기록만 짧은 `synchronized(this@GuardianVpnService)` 구간으로 옮겼습니다.
- 일시중지 의도를 작업자 대기 전에 저장해 중복 포그라운드 이벤트가 같은 종료 작업을 반복하지 않게 했습니다. TUN 닫기와 작업자 join은 잠금 밖에서 수행합니다.
- 대기 후에는 복원 의도, 일시중지 상태, 대상 패키지를 다시 확인합니다. 그 사이 사용자가 보호를 중지했거나 자동 재개가 시작됐다면 오래된 콜백이 일시중지 상태/알림을 덮어쓰지 않고 종료합니다.
- 추가 경합도 수정했습니다. 최종 일시중지 상태 저장과 foreground 알림 승격을 같은 짧은 서비스 잠금 구간에 넣어, 명시적 중지가 먼저 완료된 뒤 오래된 일시중지 콜백이 foreground 서비스를 다시 올리는 상황을 차단했습니다.
- 최소 Kotlin 컴파일 확인: 동기화 블록의 비지역 조기 반환 및 nullable worker 추론 통과.
- 동시성 하네스 실행: 자동 일시중지 스레드가 작업자 종료를 기다리는 동안 명시적 중지 스레드가 서비스 모니터를 획득하고 상태를 갱신하는 것을 확인했습니다.
- **검증 한계:** 이 하네스는 잠금 프로토콜을 독립 모델로 검증한 것입니다. Android 서비스 전체 컴파일, 기기 이벤트 순서, JUnit/Lint/APK는 실행하지 않았습니다. GitHub Actions도 실행하지 않았습니다.

## Android VPN 명시적 중지와 앱 제외 재구성의 직렬화 (2026-11-25)

- 기존 `reconfigureExcludedApplications()` 전체에 `@Synchronized`가 적용된 것만으로는 중지 경로와 일관된 직렬화가 보장되지 않았다. 명시적 중지의 복원 의도 변경과 재구성의 마지막 복원 의도 확인/시작 세대 발급이 동일한 서비스 모니터를 사용하도록 경계를 조정했다.
- 제외 재구성은 시작 시 복원 의도를 확인한 뒤 기존 TUN을 닫고 패킷 작업자 종료를 기다린다. 이 대기 중 사용자가 보호 중지를 요청하면 복원 의도는 false가 되지만, 기존 재구성 작업이 마지막에 새 시작 세대를 발급해 VPN을 다시 시작할 수 있었다.
- `stopProtection()`은 `@Synchronized`로 복원 의도 변경·세대 취소·TUN 무효화를 직렬화한다. 제외 재구성은 패킷 작업자 대기와 DNS 사전 연결(최대 8초) 동안 서비스 모니터를 점유하지 않고, 마지막 복원 의도 재확인과 시작 세대 발급만 같은 모니터 안에서 수행한다. 중지가 먼저 실행되면 재구성은 false 복원 의도를 확인하고 종료하며, 세대 발급 뒤 중지가 실행되면 취소 세대 검사가 TUN 커밋을 차단한다. 따라서 중지 요청이 재구성의 5초 작업자 대기나 DNS 사전 연결 때문에 서비스 모니터에서 장시간 대기하지 않는다.
- 이 변경은 서비스 내부 작업 순서를 보장하는 소스 수정이다. Android 런타임/JUnit 및 실기기에서 중지-재구성 동시 실행을 검증한 것은 아니며, 통합 검증 단계에서 확인해야 한다.
- 추가로 자동 일시중지 해제 후 재연결 worker가 복원 의도를 확인한 직후 중지가 끼어들면, 취소 이후 새 시작 세대를 발급할 수 있는 경로를 확인했다. 복원 의도 재확인·일시중지 플래그 해제·시작 세대 발급을 `stopProtection()`과 같은 서비스 모니터 안에서 처리하고, `startProtection()` 호출은 잠금 밖으로 이동했다. 따라서 중지가 먼저 실행되면 복원 의도 검사에서 재연결이 중단되고, 세대 발급 뒤 중지가 실행되면 취소 세대 검사가 TUN 커밋을 차단한다.

## Android VPN 종료와 전면 앱 감시 스레드의 잠금 경합 수정 (2026-11-25)

### 확인한 문제
- `stopProtection()`은 서비스 인스턴스 모니터를 보유한 채 `stopForegroundAppMonitor()`를 호출한다.
- 기존 `ForegroundAppMonitor.stop()`은 모니터 스레드에 interrupt를 보낸 뒤 최대 1초 join했다.
- 모니터 스레드가 서비스 모니터를 필요로 하는 콜백을 실행하는 중이면 종료 스레드의 join과 잠금 경합이 발생할 수 있었다.
- 초기 수정 후속 점검에서 콜백 전체를 서비스 모니터로 감싸면 `pauseProtectionForCompatibility()` 내부의 패킷 워커 join(최대 5초)까지 모니터를 점유한다는 추가 결함을 발견했다. 이 방식은 종료 지연을 다시 유발하므로 수정했다.

### 최종 적용
- `ForegroundAppMonitor.stop(waitForExit)`를 추가했다. 일반 재구성 경로는 최대 1초 종료 대기를 유지하고, VPN 종료 경로는 `waitForExit=false`로 interrupt/detach만 수행한다.
- `stopProtection()`은 서비스 모니터를 보유한 상태에서 감시 스레드를 join하지 않는다.
- 전면 앱 변경 콜백은 서비스 모니터를 잡은 채 pause 전체 작업을 호출하지 않는다. `pauseProtectionForCompatibility(packageName, expectedMonitorGeneration)`가 TUN 분리/일시중지 의사결정과 최종 상태 커밋의 짧은 임계 구역에서 감시 세대를 각각 재확인한다. 패킷 워커 join은 서비스 모니터 밖에서 수행한다.
- 재개 콜백도 예상 감시 세대를 `resumeProtectionAfterCompatibility(expectedMonitorGeneration)`에 전달한다. 실제 재개 세대 발급 직전에 서비스 모니터 안에서 세대를 다시 확인하므로 종료 뒤 도착한 콜백은 재개 요청을 발급하지 못한다.
- 사용 권한 해제 콜백은 짧은 설정 변경만 동기화하고, 재개 작업은 비동기 처리한다.
- 모니터 스레드는 daemon이며, 진행 중인 UsageStats 조회/콜백이 반환된 후 `running=false`를 확인하고 종료한다.

### 변경 파일과 커밋
- `GuardianVpnService.kt`: 초기 수정 `cfb0fa3977ef185a11a1595a47880041b83d486c`, 후속 잠금 범위 수정 `f4554a940a0a3f3579cfb7958f205ffa0eae4515`, 세대 전달 보완 `07355f0e1405a4047a42a92705441f64e85f9755`
- `ForegroundAppMonitor.kt`: `0bc5c68ab00e39608ee7c40ca269434418b8d66c`

### 실행한 검증
- 독립 Kotlin 동시성 하네스 컴파일 및 실행 성공:
  - `PASS: explicit stop acquires service monitor while compatibility pause waits outside it`
  - `PASS: stale compatibility pause cannot commit after monitor generation invalidation`
- 위 하네스는 잠금/세대 무효화 순서를 재현한 단위 시뮬레이션이며 Android 서비스 통합 테스트는 아니다.
- Android Gradle/JUnit/Lint, APK 빌드, 에뮬레이터 및 실기기 VPN 종료 검증은 아직 실행하지 않았다.

### 같은 종료 경로의 DNS 패킷 워커 정리 순서 추가 점검
- `stopProtection()`은 현재 TUN descriptor를 먼저 분리하고 닫으며, 세션 generation을 무효화한다. 서비스 모니터를 잡은 채 패킷 워커 join을 수행하지 않는다.
- 패킷 워커의 `packetLoop()` finally는 재전송 스케줄러에 `shutdownNow()`를 보내고 최대 500ms만 종료를 기다린 다음, 세션 generation이 여전히 유효한 경우에만 활성 상태를 정리한다.
- 재전송 스케줄러는 daemon thread이며 출력은 패킷 워커와 동일한 output monitor로 직렬화된다. 패킷 워커 finally는 output monitor를 보유하지 않은 상태에서 scheduler 종료를 기다리므로 확인한 범위에서 역방향 잠금 대기는 없다.
- 명시적 종료는 TUN close로 blocking packet read를 해제하고 generation을 무효화한다. 이전 패킷 워커의 늦은 finally가 새 VPN 세션의 상태를 변경하지 못하도록 하는 기존 session guard가 유지된다.
- 이 부분은 소스 경로 분석에서 추가 수정이 필요한 확정 결함을 찾지 못했다. Android 기기에서 descriptor close/read 해제 타이밍과 실제 재시작 연속 동작은 통합 검증이 필요하다.


## Android VPN 시작 요청·취소·세션 재시작 경합 점검 (2026-11-25)

### 점검 범위와 결과
- onStartCommand()의 ACTION_START / ACTION_STOP / START_STICKY 복구 경로, startProtection()의 단일 시작 작업자 및 pendingStart 합류, startCancellation 세대, tunnelSessionGuard의 패킷 루프 소유권, 시작 실패 정리와 패킷 루프 finally를 다시 대조했습니다.
- 새 시작 요청은 시작 세대를 먼저 발급합니다. 이미 시작 작업자가 있으면 가장 최신의 현재 세대만 pending 슬롯에 보관하고, 시작 작업자 종료 시 세대가 여전히 유효한 요청만 재시도합니다. 중지는 pending을 비우고 세대를 취소하므로 이전 요청은 TUN 커밋을 통과할 수 없습니다.
- TUN 커밋은 tryCommitStart 게이트 안에서 수행되고, 활성 패킷 루프의 generation 소유권은 VpnTunnelSessionGuard가 별도로 보호합니다. 중지와 자동 일시중지 모두 취소 게이트 → 세션 가드 순서로 무효화합니다. 패킷 루프 finally는 세션 가드 소유권을 얻은 경우에만 현재 세션의 상태와 descriptor를 정리하므로 오래된 worker가 새 TUN을 닫지 않습니다.
- 시작 실패 정리도 현재 요청 세대의 게이트 안에서 실행됩니다. 따라서 오래된 실패 경로가 새 요청의 foreground 알림, 상태, descriptor 또는 stopSelf(startId)를 덮어쓰지 않습니다. stopSelf(startId)는 더 최신 startId가 들어온 경우 Android가 서비스를 종료하지 않는 startId 조건도 확인했습니다.
- 시작 작업자의 starting=false와 pending 인계 사이에 새 요청이 끼어들 수는 있지만, 단일 CAS와 세대 검사가 겹친 작업자를 직렬화합니다. 경쟁에서 밀린 오래된 pending은 현재 세대 검사를 통과하지 못하며 최신 요청이 기존 요청을 대체합니다. 소스에서 중복 TUN 생성 또는 오래된 worker의 새 세션 정리를 허용하는 확정 결함은 찾지 못했습니다.

### 추가 테스트 및 실행
- VpnStartCancellationGateTest.stopCancellationWaitsForAnInFlightTunnelCommit를 추가했습니다. 시작 커밋이 게이트를 점유한 동안 중지 취소가 중간에 끼어들지 못하고, 커밋 직후 세션 소유권을 무효화하는 순서를 검사합니다.
- 독립 Kotlin 하네스에서 저장소의 두 순수 Kotlin 게이트 구현을 그대로 컴파일·실행했습니다.
  - PASS: stop cancellation serializes after an in-flight TUN commit and leaves no owned session
  - PASS: 20,000 concurrent start/cancel/commit attempts remain serialized (커밋 9,411회, 거부 10,589회)
- 테스트 파일은 저장소에 반영했지만 Android JUnit/Gradle로 실행한 결과는 아닙니다. 하네스는 Android Service lifecycle, Android VpnService.Builder, foreground service 정책 또는 실제 TUN descriptor 동작을 검증하지 않습니다.
- **검증 잔여:** Android 전체 컴파일 및 JUnit/Lint, APK 빌드, 에뮬레이터/실기기의 빠른 시작-중지 반복, 시작 중 취소, sticky 재시작, 자동 일시중지와 재개 동시 실행은 통합 QA 단계에서 실행해야 합니다. GitHub Actions는 실행하지 않았습니다.


## Android VPN 시작 DNS 사전검사 취소 및 소켓 정리 (2026-11-25)

### 확인된 문제와 수정
- 이전에는 시작 세대가 취소되어도 DNS 사전검사 함수가 반환될 때까지 이전 resolver socket이 최대 8초의 전체 질의 제한시간 동안 살아 있을 수 있었습니다. 결과를 받은 뒤 세대를 검사해 오래된 TUN 커밋은 막았지만, 중지/새 시작에 대한 네트워크 작업 자체는 즉시 취소되지 않았습니다.
- 시작 전용 DnsQueryDeadline 참조를 서비스에 추가했습니다. 새 시작 세대 발급, 명시적 중지, 자동 호환성 일시중지 및 앱 제외 재구성 시 이전 preflight deadline을 취소합니다.
- 취소 순서는 먼저 시작 세대를 무효화하고 그다음 소켓을 닫도록 했습니다. 소켓 종료 예외가 이전 worker에 전달되는 순간과 세대 무효화 사이의 틈에서 오래된 요청이 실패 상태를 게시하지 못하게 합니다.
- DnsQueryDeadline.cancel()은 해당 질의에 등록된 현재 UDP/TCP/DoT/DoH 소켓을 닫고, 남은 시간 검사를 실패시켜 다음 resolver 후보가 시작되지 않게 합니다. 소켓 등록과 취소가 동시에 일어나는 경우에도 늦게 등록된 소켓을 즉시 닫도록 했습니다.
- deadline watchdog 등록과 취소가 교차하는 경합도 보강했습니다. CopyOnWriteArrayList를 Kotlin toList()로 인덱싱하는 기존 보조 구현에서 실제 동시 제거 시 ArrayIndexOutOfBoundsException이 발생하는 것을 20,000회 하네스에서 발견했고, 안정적인 iterator 순회로 수정했습니다. watchdog 미래 작업도 취소 경합에서 정리되도록 했습니다.
- 일반 패킷 처리의 DNS 쿼리는 기존 8초 전체 예산을 그대로 사용합니다. 서비스 시작 preflight만 외부에서 소유한 취소 가능한 deadline을 전달합니다. 이전 시작의 finally는 compareAndSet으로 자기 deadline만 제거하므로 새 참조를 지우지 않습니다.

### 테스트 및 실행 결과
- DnsQueryDeadlineTest에 다음을 추가했습니다.
  - 명시적 취소가 등록된 소켓을 즉시 닫는지
  - 이미 취소된 deadline에 늦게 등록된 소켓도 닫히는지
  - 취소와 소켓 등록 경합에서 close callback이 정확히 한 번 실행되는지(100회)
- 실제 DnsQueryDeadline 순수 Kotlin 구현을 kotlinc로 컴파일하고 JVM 하네스를 실행했습니다.
  - PASS: explicit cancellation closes active upstream socket immediately
  - PASS: socket registered after cancellation is closed exactly once
  - PASS: 20,000 concurrent cancel/socket-registration races close exactly once
- 이 하네스는 순수 Kotlin deadline/소켓 정리 동작만 검증합니다. Android VpnService lifecycle, 실제 기기 네트워크 변경, VPN 소켓 보호 동작 또는 DNS upstream 연결성은 검증하지 않습니다.
- Android Gradle/JUnit/Lint/APK/에뮬레이터/실기기 검증은 아직 실행하지 않았습니다. GitHub Actions도 실행하지 않았습니다.


## Android DNS 사전검사 응답 소유자 및 CNAME 체인 검증 (2026-11-25)

### 확인한 문제와 수정
- DNS 사전검사는 `example.com A/IN` 질의를 사용하고 응답의 트랜잭션 ID·질문·RCODE·레코드 경계를 확인하고 있었습니다. 그러나 기존 성공 판정은 Answer 섹션 어딘가에 A/IN 레코드가 하나라도 있으면 성공으로 처리했습니다.
- 따라서 질의한 `example.com`과 관계없는 소유자 이름의 A 레코드가 Answer에 포함된 비정상 응답도 업스트림 연결 성공으로 오인할 수 있었습니다. 이는 실제로 확인된 판정 누락입니다.
- 성공 판정을 변경해 질의 이름에서 시작하는 Answer 섹션의 CNAME 연결을 따라가고, 그 연결에 도달한 이름의 A/IN 레코드가 있어야 성공으로 인정합니다. 무관한 A 레코드, 끊긴 CNAME, 순환 CNAME, 잘못된 CNAME RDATA 길이는 성공 근거로 인정하지 않습니다.
- DNS 압축 이름 디코더에 확장 이름 길이 255바이트 제한과 라벨 문자/경계 검사를 추가했습니다. Answer뿐 아니라 Authority/Additional 레코드의 이름 경계 검사에도 동일 디코더를 사용합니다.
- 정상적인 직접 A 응답과 정상 CNAME→A 응답은 계속 허용합니다. 이 검사는 시작 전 연결성 probe에만 적용되며, 일반 DNS 질의의 정상적인 NXDOMAIN 응답을 차단하지 않습니다.

### 네트워크 전환 및 후보 목록 점검
- 물리 DNS 서버 목록은 서비스 시작 때 고정 저장하지 않습니다. 각 UDP 질의의 `forwardDns()` 진입 시 `ConnectivityManager.allNetworks`, 각 네트워크의 현재 LinkProperties DNS, 현재 activeNetwork 및 validated/INTERNET capability를 다시 읽어 후보를 구성합니다. 따라서 이전 Wi-Fi DNS 후보 목록을 앱 메모리에 계속 캐시하는 stale-list 결함은 확인되지 않았습니다.
- VPN 자체 네트워크는 후보에서 제외하고, INTERNET capability가 없는 네트워크도 제외합니다. 후보는 configured resolver → 현재 물리 네트워크 DNS → 공용 fallback 순으로 시도하며, SERVFAIL/REFUSED는 다음 후보로 넘어갑니다. UDP TC 응답은 동일 resolver TCP 재시도를 수행합니다.
- 별도 NetworkCallback 캐시를 추가하지 않았습니다. 현재 구현은 매 질의 시 최신 LinkProperties를 조회하므로 별도 캐시는 오히려 네트워크 전환 후 stale DNS를 유지할 위험이 있습니다.
- 다만 실제 Wi-Fi↔모바일 데이터 전환, IPv4-only/IPv6-only 통신망, 통신사 DNS의 SERVFAIL/REFUSED 동작은 Android 기기에서 확인해야 합니다. 소스 점검만으로 통신사 라우팅·방화벽·NAT64 동작을 보증하지 않습니다.

### 테스트 및 실행 결과
- `DnsUpstreamProbeTest`에 다음 회귀 테스트를 추가했습니다.
  - 질의 이름과 무관한 A 응답 거부
  - 정상 CNAME→A 체인 허용
  - 잘못된 CNAME RDATA 길이 거부
- 변경된 probe 로직을 순수 Kotlin/JVM 하네스로 컴파일·실행했습니다.
  - PASS: direct A answer accepted
  - PASS: unrelated A answer rejected
  - PASS: valid CNAME-to-A chain accepted
  - PASS: malformed CNAME target rejected
- 하네스는 probe 판정 로직을 대상으로 한 JVM 실행이며 Android 전체 소스/Gradle/JUnit 실행은 아닙니다. Android Gradle/JUnit/Lint, APK, 에뮬레이터 및 실기기 검증은 여전히 미실행입니다. GitHub Actions는 실행하지 않았습니다.


## Android 필터 규칙·캐시·우선순위 정적 감사 (2026-11-25)

### 감사 범위
- `FilterRuleParser`: hosts/adblock DNS 규칙, 예외, important, badfilter, 지원하지 않는 modifier, BOM, 대용량 Reader 파싱
- `DomainFilter`: 규칙 snapshot 원자 교체, 부모 도메인 일치, 차단 우선순위, 사용자 규칙 병합
- `FilterStartupGate` 및 `GuardianVpnService.loadConfiguredRules()`: 필수 번들 baseline과 선택 필터 분리, 선택 캐시 실패 격리
- `FilterCacheStore` 및 `FilterListManager`: 임시 파일 fsync, 백업/복구, 메타데이터 commit 실패 롤백, 서명 캐시 해시 및 anti-rollback 복구

### 확인 결과
- DNS 규칙 우선순위는 important block → allow → 일반 block이며, 중요 차단 규칙은 사용자 allow로 해제되지 않습니다.
- 서브도메인은 부모 규칙을 상속합니다. 사용자 규칙과 다운로드 규칙은 새 RuleSet을 완성한 뒤 volatile 참조 한 번으로 교체합니다.
- 선택 필터는 누락·손상·최소 규칙 수 미달 시 해당 소스만 건너뛰고, 필수 번들/긴급 baseline으로 VPN 시작을 유지합니다.
- 캐시 교체는 새 파일 fsync 후 교체하며, 메타데이터 commit 실패 시 기존 캐시 바이트와 메타데이터 복구를 시도합니다. signed cache는 저장된 SHA-256과 anti-rollback watermark를 함께 확인합니다.
- 초기에 의심한 context-scoped `badfilter` 취소 문제는 실제 결함이 아니었습니다. `adblockDomain()`이 이미 지원하지 않는 modifier(예: `third-party`)를 거부하므로 DNS-wide 규칙 취소로 확대되지 않습니다. 이를 중복 보강하려던 임시 수정은 영향이 없도록 되돌렸습니다. 불필요한 변경은 최종 소스에 남기지 않았습니다.

### 검증 범위와 미확인 항목
- 이번 단계에서는 실제 소스와 테스트 케이스를 정적으로 대조했습니다. Android Gradle/JUnit, Lint, APK 및 기기 검증을 실행했다고 간주하지 않습니다.
- 파일 시스템 강제 종료 시점별 복구, 대용량 실제 필터의 메모리 사용량, signed manifest 서버의 운영 응답, 사용자 규칙을 편집하는 동안의 실제 VPN 트래픽 반영은 별도 통합/기기 검증이 필요합니다.
- GitHub Actions는 실행하지 않았습니다.


## Android 필터 업데이트 테스트 컴파일 오류 수정 (2026-11-25)

- `FilterUpdateSchedulePolicyTest`의 `shouldDisableOptionalFilter` 호출 3곳이 존재하지 않는 named argument `hadValidCache`를 사용하고 있었습니다.
- 실제 함수 시그니처는 `hasUsableCache`이므로 Kotlin named-argument 해석 단계에서 테스트 소스가 컴파일되지 않는 명확한 불일치였습니다.
- 세 호출의 인자명을 실제 구현 시그니처와 일치하도록 수정했습니다.
- 수정 후 원격 소스를 다시 가져와 구현 시그니처와 세 테스트 호출을 대조했고, 남은 `hadValidCache` 사용이 0건임을 확인했습니다.
- 이는 테스트 소스의 정적 불일치 수정입니다. Android Gradle/JUnit을 실행한 것은 아니며 전체 테스트 컴파일 성공을 의미하지 않습니다.


## Android 설정 화면의 자동 필터 업데이트 사용자 선택 보존 (2026-11-25)

- 필터링 설정의 `백그라운드에서 필터 자동 업데이트` 체크박스가 자동 업데이트 설정값만 저장하고, `filter_auto_update_user_choice_v1` 사용자 선택 기록은 저장하지 않던 문제를 수정했다.
- 이 누락 때문에 해당 체크박스에서 자동 업데이트를 끈 사용자는 이후 앱 실행 시 기본값 마이그레이션이 이를 사용자 선택으로 인식하지 못하고 자동 업데이트를 다시 켤 수 있었다.
- 체크박스와 보호 설정 화면의 스위치 모두 설정값과 사용자 선택 기록을 함께 저장하도록 정렬했다.
- `FilterAutoUpdatePreferencePolicy.shouldEnableDefault`를 분리하고, 미마이그레이션/기존 사용자 선택 여부 4가지 조합을 검증하는 `FilterAutoUpdatePreferencePolicyTest`를 추가했다.
- 실제 Android JUnit/Gradle 실행은 아직 수행하지 않았다. 정책 함수의 실제 Kotlin 소스를 분리해 JVM 컴파일 및 네 가지 조건 독립 실행은 통과했다.
