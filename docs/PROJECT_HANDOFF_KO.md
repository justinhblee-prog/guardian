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
