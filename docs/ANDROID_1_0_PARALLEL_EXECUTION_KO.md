# Guardian Android 1.0 병렬 실행 워크플로

- 기준일: 2026-11-20
- 기준 브랜치: main
- 작업 브랜치: feature/android-support-center-mainline
- 목표: Android 1.0 출시 게이트를 닫고 검증 가능한 APK를 확보한다.
- 원칙: 독립 작업은 병렬 진행, 공유 계약/동일 파일 변경은 순차 통합, 미실행 검증은 미완료로 기록한다.

## 1. 실행 구조

| 트랙 | 범위 | 병렬 가능 범위 | 완료 산출물 / 통과 기준 |
|---|---|---|---|
| A — Android 보호 엔진 | VPN lifecycle, startup gate, DNS forwarding, 앱별 제외, 자동 일시중지/복구, UI 상태 | B/C와 독립 파일 단위로 작업. VPN service와 같은 파일 변경은 단일 통합 담당만 수정 | 컴파일, 단위 테스트, lint, API 35 emulator VPN/DNS 시나리오 통과 |
| B — 필터·무결성 | manifest 서명, feed fetch, cache rollback, readiness/diagnostics, filter update UX | A의 VPN 인터페이스를 바꾸지 않고 filter 모듈/테스트에서 병렬 작업 | 정상/손상/오래된 manifest, 다운로드 실패, 캐시 복구 테스트 통과. 승인되지 않은 원본은 배포 차단 |
| C — 라이선스·고객지원 | Android License API client, entitlement, support tickets, Node API, migrations, admin UI | Android client와 server/admin/migration은 독립 파일에서 병렬 작업. API 계약 변경은 문서/테스트와 함께 순차 통합 | HTTP/Postgres/Admin 테스트, Android client compile, QA lifecycle 및 앱 연결 증거 |
| D — Release/공급망 | versionCode, signing validation, release configuration, APK/AAB metadata, checksum, install/upgrade | A/B/C와 분리해 Gradle/workflow/docs만 작업. release 빌드는 A/B/C 통합 고정 후 수행 | Production signer 지문, package/version, APK/AAB SHA-256, 신규 설치 및 업그레이드 증거 |
| E — 실기기 수용 | Samsung Library file not found, VPN 승인/시작/중지, 실제 DNS 차단, 네트워크 전환/복구 | 저장소 코드 검토와 병렬로 수용시험 절차/진단 개선. 실기기 실행 자체는 실제 단말이 필요 | 단말 모델/OS/app version, 개인정보 제거 로그, 재현/수정 후 결과 |

## 2. 의존성 및 통합 순서

```text
A Android engine ───────┐
B Filter integrity ─────┼──> Integration freeze ──> Full regression ──> D Production build
C License + support ────┘                                  │                    │
                                                           └──> E Samsung test ─┘
```

- A/B/C는 서로의 전체 완료를 기다리지 않고 독립 범위에서 진행한다.
- 공유 API/데이터 모델/상태 이름 변경은 먼저 계약을 확정하고 관련 트랙이 같은 계약을 사용하도록 맞춘다.
- 통합 시 동일 파일은 한 번에 한 작업만 수정한다. 각 트랙은 변경 파일과 커밋을 남긴다.
- D의 Production build는 통합 회귀가 통과하고 운영 서명·서비스 설정이 확보된 뒤 수행한다.
- E의 실제 단말 결과는 emulator 결과와 분리 기록한다. Library file not found 원인은 단말 stack summary 확보 전까지 미확정이다.

## 3. 작업 상태 스냅샷

| 트랙 | 현재 상태 | 확인된 사실 | 다음 작업 |
|---|---|---|---|
| A | 구현 존재 / 검증 대기 | VPN baseline startup, service recovery, app pause logic 및 테스트 소스가 있음 | 최신 통합 소스 컴파일·unit/lint 및 emulator 실행 |
| B | 구현 일부 / 운영 차단 | 캐시 atomic replace/recovery 코드가 있음. 예시 feed 7개는 재배포 승인 근거가 없음 | manifest/cache 테스트와 update UI 확인, 승인 feed만 배포하도록 게이트 유지 |
| A/B 테스트 보강 | 소스 추가 / 실행 대기 | FilterUpdatePolicyTest에 캐시 누락, 주기 경계, 시계 역행, 최소 주기, optional feed 첫 다운로드 실패 정책 케이스 추가 | Android unit test job에서 컴파일·실행 확인 |
| 필터 실패 복구 결함 수정 | 코드 반영 / 실행 대기 | 이전 다운로드·검증 오류가 있어도 유효 캐시의 갱신 시각만 보고 업데이트를 건너뛰던 문제 수정. 기본/보안/Android 광고 소스별 오류 플래그가 있으면 재시도하도록 변경하고 회귀 테스트 2개 추가 | Android unit test에서 컴파일·실행 확인 |
| 필수 번들 DNS 규칙 파일 빌드 가드 | 구현 반영 / 실행 대기 | `GuardianVpnService`가 시작 시 읽는 `shared/filter_rules.txt`가 누락·빈 파일이면 Android `preBuild`에서 실패하도록 Gradle 검증 추가. 수동 소스 게이트에도 파일 및 가드 확인 추가 | 실제 Gradle preBuild 실행 및 APK 내 assets 포함 확인 필요 |
| DNS 패킷 파서 회귀 테스트 | 소스 추가 / 실행 대기 | IPv4 UDP 체크섬이 0이 아닌 정상 패킷을 pseudo-header 포함 검증 후 허용하는 케이스 추가. 기존 잘못된 체크섬 거부 테스트와 함께 양방향 확인 | Android unit test에서 컴파일·실행 확인 |

| IPv4 테스트 컴파일 결함 수정 | 소스 수정 / 실행 대기 | UDP checksum helper가 `sealHeader` 내부에 중첩되어 외부 테스트에서 호출할 수 없는 구조를 발견. 두 helper를 클래스 수준으로 분리해 호출 범위 오류 수정 | Android unit test compile 및 전체 테스트 실행 필요 |
| C | 구현 존재 / 통합 검증 대기 | QA License API lifecycle은 노트북에서 통과. support ticket API/UI/migration 구현이 feature branch에 있음 | server HTTP/Postgres/admin 테스트 실행, migration 003 사전 점검, Android 연동 검증 |
| D | 설정 준비 / 정식 산출물 없음 | Gradle은 Production signing 및 service public key/base URL을 요구. versionCode 43 기준 다음 release 최소 44 | 운영키/공개 endpoint 준비 후 release assemble, signer/checksum 및 설치 확인 |
| E | 외부 단말 증거 대기 | Samsung 오류 보고가 있으나 정확한 호출 stack 미확정 | 진단용 privacy-filtered stack summary로 재현·수정·재시험 |

- D 정적 검토: 기존 Android APK workflow의 source ZIP SHA-256 추출식이 0.7.29-dev를 찾는 오류를 확인하고, 실제 생성 파일 0.7.30-dev에 맞게 수정했습니다. 실행 검증은 아직 없습니다.

## 4. 수동 병렬 검증 워크플로

- GitHub Actions 파일: .github/workflows/android-1-0-parallel-gates.yml
- Android unit/lint, DB 독립 License API 테스트, PostgreSQL 16 기반 통합 테스트, source/script contract를 별도 job으로 병렬 실행하도록 구성했습니다.
- 사전검사 job이 최소 1개 트랙 선택을 강제하며, 결과 요약은 사전검사 실패를 성공으로 처리하지 않습니다.
- workflow_dispatch 전용이며 push/PR/schedule 자동 실행은 없습니다. 생성만 했고 실행하지 않았습니다. Actions runner 문제 및 사용량 비용을 피하기 위해 사용자의 기존 제한을 유지합니다.
- GitHub의 수동 실행 버튼은 workflow 파일이 기본 브랜치에 반영된 뒤 사용할 수 있습니다. 현재는 feature 브랜치에만 저장되어 있으며, PR 생성/merge로 기존 Android PR 검증을 자동 실행시키지 않았습니다.
- 이 workflow는 APK를 만들지 않으며 emulator E2E, Samsung 실기기 검증도 수행한다고 주장하지 않습니다. PostgreSQL 통합 테스트는 임시 CI DB에서 실행하도록 구성했지만 아직 실제 실행 결과는 없습니다.

## 5. 실행 규칙

1. 독립 트랙의 저장소 조사와 파일 변경은 가능한 한 병렬로 수행한다.
2. 같은 파일, migration 순서, API contract, shared model은 충돌을 피하기 위해 직렬화한다.
3. 각 변경은 작업 브랜치에 커밋하고, 통합 브랜치는 항상 main보다 뒤처지지 않게 확인한다.
4. 사용자 로컬 노트북/휴대폰에서만 가능한 작업을 이미 수행한 것처럼 보고하지 않는다.
5. Actions runner/billing/usage 설정을 바꾸지 않는다. 사용자가 비용 금지 지시를 유지하는 동안 Actions workflow를 실행하거나 PR을 열어 자동 실행을 유발하지 않는다.
6. 정적 검토, 소스 테스트 작성, 실제 테스트 실행, emulator, Samsung 단말, Production 배포를 서로 다른 증거 단계로 기록한다.
7. 실패 시 실패한 단계, 재현 조건, 로그 근거, 수정 커밋, 재검증 결과를 함께 남긴다. 증거가 없으면 상태는 검증 대기다.
8. 운영 RSA 키/DB는 보존한다. 키 교체, DB 초기화, 운영 migration은 백업과 명시적 운영 절차 없이 수행하지 않는다.

## 6. Android 1.0 종료 게이트

- [ ] A: Gradle compile + unit tests + lint 통과
- [ ] B: filter manifest/signature/cache/update failure tests 통과
- [ ] C: server HTTP + PostgreSQL integration + admin UI tests 통과
- [ ] C: Android client가 공개 HTTPS License API에 연결해 activation/refresh/deactivation 확인
- [ ] A/B/C: API 35 emulator end-to-end 회귀 통과
- [ ] E: Samsung 실기기 VPN start/stop, 실제 DNS 차단/허용, 앱 재시작/네트워크 변경 복구 통과
- [ ] D: 사용자 소유 Production signer로 APK 생성, 인증서 지문·패키지·버전·SHA-256 기록
- [ ] D: 신규 설치 및 기존 버전 업그레이드 확인
- [ ] 문서: 설치/복구/제한사항/라이선스/고객지원 절차 업데이트

현재 판정: 구현 일부 및 QA 서버 lifecycle 확인 단계. 통합 테스트 실행, emulator, Samsung 수용, Production APK는 아직 완료 증거가 없다.

## 2026-11-20 추가 병렬 구현 통합 기록

| 트랙 | 구현 결과 | 검증 근거 / 제한 |
|---|---|---|
| B — 필터 갱신 | 동기 Worker 취소 경계를 각 feed 및 VPN reload 앞에 추가. 취소 중 성공한 기본 feed는 오류 플래그를 먼저 정리한 뒤 후속 feed를 중단. | Worker 소스 9개 취소 경계 정적 확인. Android WorkManager/JUnit 미실행 |
| B — 로그 보안 | 필터 다운로드 시작 로그에서 전체 URL 제거. | 원격 소스에서 URL 로그 문자열 제거 확인 |
| C — 라이선스 UI | 라이선스 및 프리미엄 화면의 잘못된 자기 화면 뒤로가기 수정. | MainActivity callback 정적 확인 |
| C — 고객지원 UI | 접수/조회 응답 도착 시 Activity 종료 여부를 확인해 폐기된 Activity에서 UI를 갱신하지 않도록 수정. | 두 비동기 callback guard 정적 확인. Android UI instrumentation 미실행 |
| UI — 설정 탐색 | 중첩 화면에서도 설정 최상위 메뉴 선택 상태 유지. 규칙 화면의 시스템 Back은 필터링 설정으로 복귀. | ResponsiveLayoutPolicy Kotlin/JVM harness: 메뉴 선택 11개 경로 및 뒤로가기 7개 경로 PASS. Android JUnit 미실행 |

- 관련 변경은 `feature/android-support-center-mainline`에 독립 파일 단위 커밋으로 반영했습니다.
- 위 검증은 소스 재조회/순수 Kotlin/JVM harness 증거이며, Android Gradle compile, JUnit, lint, APK, emulator 및 Samsung 실기기 결과로 확대 해석하지 않습니다.
- GitHub Actions는 실행하지 않았으며 runner/billing/usage 설정을 변경하지 않았습니다.


## 2026-11-20 — Release/Debug APK 서명 검증 보강

- 두 Windows 빌드 스크립트에 Android Build Tools `apksigner.bat` 존재 확인 및 `verify --verbose --print-certs` 검증을 추가.
- 서명 검증이 성공해도 인증서 SHA-256 지문이 출력되지 않으면 실패 처리하고, 지문을 검증 보고서와 콘솔에 남기도록 함.
- 기존 APK package/version, 번들 필터 asset, 파일 크기, APK SHA-256 검증은 유지.
- 원격 소스 재조회로 변경 내용과 정적 구조 확인 완료. PowerShell/Android SDK/Gradle 실행환경이 없어 실제 스크립트 파싱·APK 생성·서명 검증은 미실행 상태이며, Debug 서명 지문은 Production 서명 지문을 대체하지 않음.


## 2026-11-20 — Signed manifest endpoint validation

- 필터 manifest URL의 HTTPS URI 포트 범위를 명시적으로 검사하도록 수정(포트 생략 또는 1–65535만 허용).
- 서명이 정상이어도 범위를 벗어난 포트를 가진 URL을 거부하는 회귀 테스트 추가.
- 원격 소스 재조회로 코드/테스트 반영을 확인했으며, Android JUnit 실행은 미실행.


## 2026-11-20 — Redirect hardening

- HTTPS 필터 리디렉션이 fragment 또는 범위를 벗어난 port를 포함하면 요청을 시작하지 않고 거부하도록 보강.
- 잘못된 포트와 fragment 리디렉션 회귀 테스트 추가. 원격 소스 재조회 확인 완료, Android JUnit 미실행.


## 2026-11-20 — Signed manifest client URL boundary

- Track B에서 manifest client의 첫 요청 URL과 detached signature sidecar URL에도 공통 HTTPS URL 검증을 적용했습니다. URL의 포트 범위·fragment·host/userinfo를 연결 전에 검사하고, sidecar가 원본 manifest와 동일한 host/port를 유지하도록 확인합니다.
- 회귀 테스트: sidecar manifest URL의 99999 포트 및 fragment 거부, 초기 manifest URL의 99999 포트 거부.
- 원격 소스 재조회 확인 완료. Java 21 URL parser 입력 확인 외에 Android JUnit/Gradle 및 실제 HTTPS 서버 호출은 미실행입니다.
