# Guardian 프로젝트 통합 인수인계서

> 기준일: 2026-11-20 (KST)  
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

- PR #205 설명에 기록된 Android 개발 APK: `com.guardian.adblock`, `0.7.29-dev`, versionCode 42, SHA-256 `00d6b867d81ac3791f5b8b759349360084899b3be17ee295623287d8836e61fe`. 개발용 Debug 서명본이며 Production 1.0 APK가 아닙니다.
- PR #205의 Android API 35 에뮬레이터 테스트 기록에는 앱 제외, 사용자 DNS 규칙, 앱 업데이트 후 VPN 복구, 선택 앱 실행 중 자동 일시중지/종료 후 재개, Home 보호 시작/중지가 포함됩니다.
- PR #205 고객지원 UI 수용 테스트는 Settings 스크롤 위치 때문에 실패한 이력이 있습니다. 스크롤을 상단으로 초기화하는 수정 커밋 `d8b1d0f1f676555163f802a33650eb2be796cdc4`가 PR head의 조상 커밋임을 확인했습니다. 이 수정이 포함된 최신 head의 후속 Actions 성공은 아직 확인되지 않았습니다.
- 실제 Samsung 기기 VPN/DNS, `Library file not found`, 네트워크 전환 및 장시간 복구는 별도 미검증입니다.

### 4.2 License API / PostgreSQL

2026-11-20 사용자가 제공한 Windows 노트북 실행 결과:

- Docker API 및 PostgreSQL 컨테이너가 healthy 상태.
- 로컬 및 Tailscale HTTPS `/ready` 응답 HTTP 200, database와 signer ready.
- 운영 DB/기존 RSA 서명키를 사용한 QA 흐름에서 라이선스 발급, 기기 활성화 및 서명 entitlement 발급, 활성 상태 조회, entitlement 갱신, 기기 비활성화, Free 상태 확인, QA 라이선스 회수까지 통과.
- 기존 DB 백업과 운영 RSA 키는 유지했습니다. 키 회전, DB 초기화, Actions 비용/runner 설정 변경은 하지 않았습니다.

이는 License API 서버 측 lifecycle 및 DB 연결 검증입니다. Android 앱 실기기 연동, 결제 웹훅, Samsung 실결제 검증은 아닙니다. QA 고객/회수 라이선스/감사 이벤트의 최종 잔존 건수는 별도 조회 결과가 없으므로 단정하지 않습니다.

### 4.3 필터 배포

- 공개 `guardian-filter-data` 브랜치 생성/게시 여부는 최신 확인에서 미완료입니다.
- 설정된 7개 예시 필터 원본은 재배포 승인값이 false이고 승인 참조가 비어 있습니다.
- 권리자 승인 근거를 확인하기 전에는 상용 배포용 필터로 게시하거나 승인 상태를 임의 변경하지 않습니다.

### 4.4 Production 서명 / 결제

- 1.0.0, versionCode 43 기준의 Production 빌드 설정 및 임시 서명 검증 경로가 CI에서 확인된 이력이 있습니다.
- 사용자 소유 운영 keystore/secrets가 없으므로 Production 서명 APK/AAB는 아직 확인되지 않았습니다.
- 상품 코드 및 관리자 수동 발급은 결제 연동이 아닙니다. 서명된 결제 webhook, 중복 이벤트 방지, 환불/취소/갱신 동기화와 체험판 중복 발급 방지는 상용 판매 전 별도 구현·검증이 필요합니다.
- Lifetime은 진정한 무기한 entitlement와 폐기/갱신/오프라인 검증이 구현되기 전에는 발급하지 않습니다.

## 5. PR #205 — 고객지원센터 및 라이선스 운영

- PR: [#205](https://github.com/justinhblee-prog/guardian/pull/205), Draft, branch `feature/support-center-license-plans`.
- 구현 범위: Android FAQ/문의 접수/비밀 코드 조회, 서버 support ticket API, 관리자 문의함, 라이선스 기간 상품 코드 검증, migration 003 및 Windows migration runner.
- PR head: `9f70290ae90cab8f305a5dabb019d5ba98f29b8b`.
- 현재 main 대비 58 commits ahead / 5 behind, GitHub mergeable=false로 확인했습니다. 이 상태로 병합하지 않습니다.
- PR 설명은 License API 53/53 테스트, PowerShell 스크립트 파싱, Android Debug/에뮬레이터 검증을 기록합니다. 다만 고객지원 UI acceptance 실패 후 수정이 포함된 head의 후속 Actions 완료는 별도 확인이 필요합니다.
- migration 003은 PR에 추가되어 있지만 운영 노트북 DB에 적용 완료로 간주하지 않습니다. 실제 적용 전 백업·마이그레이션·검증 절차가 필요합니다.
- 다음 처리: main과 변경사항을 파일/API/schema 단위로 재조정하고, migration 004 및 PEM 수정과 충돌을 해결한 통합 브랜치에서 테스트를 재실행합니다. 단순히 PR의 base를 바꾸거나 mergeable 상태를 강제하지 않습니다.

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

---

**완료 판정:** 소스 구현, 자동화 테스트, 에뮬레이터, 실기기, 운영환경, Production 산출물의 증거가 각각 확보되어야 해당 단계만 완료 처리합니다. Android 1.0 필수 조건이 모두 충족되기 전에는 정식 출시 완료라고 선언하지 않습니다.
