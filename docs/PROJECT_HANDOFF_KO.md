# Guardian 프로젝트 통합 인수인계서

> 문서 목적: 대화창과 작업 담당자가 바뀌어도 기획·설계·개발·검증의 맥락과 완료 기준을 보존하는 단일 기준 문서.
> 마지막 확인: 2026-11-20 (KST)
> 저장소: `justinhblee-prog/guardian`
> 현재 작업 브랜치: `feature/support-center-license-plans`
> 관련 PR: [#205 고객지원센터 및 라이선스 운영](https://github.com/justinhblee-prog/guardian/pull/205)

## 1. 프로젝트 목표와 출시 순서

Guardian은 Android 스마트폰·태블릿과 Windows PC에서 공통 필터 정책 및 라이선스 체계를 제공하는 광고·추적·악성 도메인 DNS 보호 제품이다.

출시 순서는 다음과 같이 고정한다.

1. Android 1.0 정식 출시 가능한 상태로 마무리한다.
2. Android 1.0의 기능·보안·운영·배포 증거를 확정하고 릴리스한다.
3. 이어서 Windows 버전을 제품 수준으로 마무리하고 설치형 패키지(EXE/installer)를 빌드·설치·실행·제거까지 검증한다.
4. Android 태블릿과 Windows PC를 포함한 멀티 디바이스 경험을 통합한다.

완료 선언은 계획/소스 작성만으로 하지 않는다. 각 항목을 **구현됨 / 자동화 테스트 통과 / 에뮬레이터 검증 / 실기기 검증 / 운영환경 검증 / 미검증**으로 구분한다.

## 2. 작업 원칙

- 사용자가 별도 진행 지시를 반복하지 않아도 현재 작업의 다음 미완료 항목을 이어서 처리한다. 단, 실제 백그라운드 작업이 지속되는 것처럼 주장하지 않는다.
- 정기적인 중간보고와 승인 대기 없이 가능한 작업은 연속 수행한다. 사용자 소유의 운영 비밀값·실기기 결제 등 접근이 필요한 항목만 명확한 외부 의존성으로 표시한다.
- 이미 실패한 접근을 반복하지 말고 로그와 근본 원인을 분석한다.
- 자동화 테스트 성공을 실기기·운영 검증 성공으로 바꾸어 말하지 않는다.
- 운영 개인키, keystore, 비밀번호, 관리자 토큰, GitHub Secret 값은 채팅이나 저장소에 기록하지 않는다.
- 개발용 debug APK와 production 서명 APK/AAB를 구분한다.
- 모든 주요 코드 변경, 설계 결정, 테스트 결과, 릴리스 차단 항목을 이 문서와 세부 문서에 갱신한다.
- 대화창은 작업 기록의 원본 저장소가 아니다. GitHub의 문서·코드·PR·Actions 증거를 기준으로 인수인계한다.

## 3. 제품 요구사항 / 범위

### 3.1 Android 핵심 보호
- Android `VpnService` 기반 DNS 보호 및 차단.
- IPv4/IPv6 DNS 경로, UDP/TCP DNS 처리 및 설정된 upstream(DNS/DoT/DoH) 검증.
- 광고·추적·악성코드·피싱 필터 프로필, 사용자 차단/허용 규칙.
- 필터 자동 업데이트, 서명 및 무결성 확인, 실패 시 이전 정상 캐시 보존.
- 앱별 VPN 제외: 설치된 앱을 폭넓게 조회하고 검색·선택·저장하며 실제 VPN 라우팅에 반영.
- 선택 앱 실행 시 전체 VPN 자동 일시중지 및 앱 종료 후 자동 재연결 기능은 별도 정책/경고와 함께 검증한다. 전체 VPN을 중지하면 그 시간 동안 모든 앱이 VPN DNS 보호에서 벗어난다는 점을 명확히 표시한다.
- 보호 시작/중지 단일화, 업데이트 상태에 따른 버튼 활성화, 화면 간 중복 동작 제거, 명확한 메뉴 이름과 즉각적인 화면 반응.
- 앱 업데이트 다운로드·설치·보호 자동 재연결 경로를 검증한다.
- 진단 정보는 사용자가 명시적으로 선택한 경우에만 공유한다.

### 3.2 라이선스 / 고객지원
- Free 및 기간제 Pro 라이선스, 기기 수 제한, 서버 발급·검증·갱신·해제·취소 정책.
- 고객 앱 내 FAQ, 문의/오류/결제/라이선스/기능 제안 접수, 접수번호와 비밀 조회 코드로 답변 확인.
- 노트북·모바일에서 이용 가능한 관리자 문의함 및 라이선스 발급 화면.
- 라이선스 기간 상품: 7일, 30일, 90일, 365일, 사용자 지정. 상품 코드와 기간은 서버에서 검증한다.
- 실제 유료 판매 전에 결제 공급자, 서버 검증 웹훅, 멱등성, 환불/취소/갱신 흐름을 구현·검증한다.
- Lifetime은 만료일 없는 entitlement, 갱신/폐기/오프라인 검증 정책이 모든 계층에 구현되기 전까지 발급하지 않는다. 100년짜리 기간으로 대체하지 않는다.
- 체험판 중복 발급 방지 정책을 상용 판매 전 확정한다.

### 3.3 운영·배포
- 운영 라이선스 API와 PostgreSQL, 필터 manifest 배포 서버를 안전하게 운영한다.
- 필터 배포 서명 개인키와 앱의 pinned 공개키가 일치해야 한다.
- 운영 Android keystore는 사용자 소유 노트북/모바일의 안전한 절차로 생성·보관한다.
- GitHub Actions production secrets는 사용자 환경에서 안전하게 등록한다. 실제 등록이 확인되기 전에는 운영 서명 설정 완료로 표시하지 않는다.
- APK 및 Google Play AAB 빌드 경로를 구분하고, Play 등록 여부를 향후 선택할 수 있도록 패키징을 유지한다.

## 4. 현재 아키텍처 개요

### Android
- Kotlin 기반 Android 앱, 패키지 `com.guardian.adblock`.
- Android `VpnService`가 가상 인터페이스를 구성하고 DNS 요청을 로컬 필터 엔진으로 전달한다.
- 필터 규칙은 도메인 기반 DNS 정책으로 적용한다. 웹페이지 요소 숨김, 동일 도메인 광고, 앱 내부 광고 제거는 DNS 방식만으로 보장하지 않는다.
- Android의 앱별 VPN 제외는 선택 앱 트래픽을 VPN에서 우회시키는 것이며 VPN 자체를 끄는 동작과는 다르다.
- 금융 앱이 VPN 연결 자체를 감지하는 경우 앱별 트래픽 제외만으로 경고가 사라진다고 보장하지 않는다. Samsung Wallet은 실기기에서 별도 확인한다.

### License API / Admin
- 경로: `server/license-api`.
- Node.js API 및 PostgreSQL 저장소/마이그레이션.
- 라이선스 서명 토큰, 설치별 기기 바인딩, 관리자 인증, 고객지원 티켓.
- 고객지원 조회 비밀값은 DB에 SHA-256 해시만 저장하고 조회 요청 본문으로 전달한다.
- 운영 DB migration은 코드에 추가한 것과 실제 노트북 운영 DB에 적용한 것을 별도로 기록한다.

### Windows
- 공통 DNS 필터 엔진 및 Windows DNS proxy/클라이언트 구현이 저장소에 존재한다.
- 현재 Windows 보호는 DNS 기반이며 WFP 커널 드라이버가 아니다.
- Windows UI, DNS 어댑터 변경/백업/복구, 부팅 자동 시작, 서명 필터 manifest updater 및 설치 패키지를 제품 수준으로 검증해야 한다.
- 실제 Windows PC에서 어댑터 변경, 재부팅 후 복구, VPN/보안 제품 공존, 설치·업데이트·제거를 확인해야 한다.

## 5. Android 1.0 현황 — 확인된 사실과 미완료

### 5.1 현재 진행 기준
- Android 1.0 출시 범위 및 세부 트랙은 저장소 README에서 Issue [#141](https://github.com/justinhblee-prog/guardian/issues/141), [#142](https://github.com/justinhblee-prog/guardian/issues/142), [#143](https://github.com/justinhblee-prog/guardian/issues/143), [#144](https://github.com/justinhblee-prog/guardian/issues/144)를 기준으로 관리한다.
- 기존 문서에 남은 과거 버전·체크섬·CI 기록은 당시의 이력이며 현재 최신 APK로 취급하지 않는다.
- README에 기록된 과거 Android APK들은 debug 서명본이며 production release가 아니다.
- Android API 35 에뮬레이터에서 VPN/DNS E2E 성공 기록이 존재하지만 실기기 수용시험을 대체하지 않는다.
- 실기기에서 보고된 `Library file not found` 오류는 원인 특정 및 재현/해결 확인이 필요하다.

### 5.2 현재 PR #205 — 고객지원/라이선스 운영
- PR: [#205](https://github.com/justinhblee-prog/guardian/pull/205)
- 브랜치: `feature/support-center-license-plans`
- Draft 상태이며 아직 main에 병합되지 않았다.
- 구현 내용: Android 고객지원 센터, 티켓 접수/비밀 코드 조회, 관리자 문의함, 라이선스 7/30/90/365일 및 사용자 지정 상품 코드 검증, 감사 기록, PR APK artifact upload 활성화.
- License API 테스트 51/51 통과 기록이 있다. PostgreSQL 고객지원 흐름 및 상품 코드 검증 포함.
- 해당 PR 최신 Android 빌드/아티팩트와 동일 커밋의 최신 런타임 검증은 별도로 확인해야 한다.
- 운영 DB에 migration 003을 실제 적용하지 않았다면 운영 반영 완료가 아니다.
- 실제 결제 연동, 체험판 중복 방지, Lifetime 모델은 아직 구현/검증되지 않았다.
- Android 고객지원 UI와 관리자 문의함의 실제 폰/노트북 테스트가 남아 있다.

### 5.3 출시 차단 검증 목록
아래 항목은 실제 증거를 확보한 뒤 상태를 갱신한다.

| 영역 | 확인할 내용 | 완료 증거 |
|---|---|---|
| 앱별 제외 | 전체 설치 앱 검색, 선택, 저장, 재실행 후 유지, VPN 라우팅 제외 | Android 실기기 테스트 |
| 자동 일시중지 | 대상 앱 실행 시 VPN 중지, 종료 시 자동 재연결, 예외/충돌 | 실기기 로그 및 동작 |
| Samsung Wallet | 보호 제외 및 자동 일시중지 정책별 경고/실제 결제 | Samsung 실기기 결제 결과 |
| 필터 | 다운로드, 서명 검증, 적용, 예약 갱신, 실패 복구 | 운영 HTTPS 경로 E2E |
| 업데이트 | 업데이트 감지, 다운로드, 설치, 설치 후 보호 자동 재연결 | 실기기 업데이트 E2E |
| 네트워크 | Wi-Fi↔모바일 전환, IPv4/IPv6, Private DNS, 재부팅, 장시간 안정성 | 실기기 테스트 |
| 고객지원 | 문의 접수, 비밀 코드 조회, 관리자 답변/상태 변경 | 실제 API 및 UI 테스트 |
| 라이선스 | 발급, 활성화, 기기 제한, 갱신, 해제, 만료/폐기 | 운영 서버 E2E |
| 운영 보안 | keystore, 서명키, 공개키, secrets, TLS | 키 생성/등록/서명 검증 증거 |
| 릴리스 | APK/AAB, 설치/업그레이드, 버전/서명/체크섬 | production artifact 및 검증 로그 |

### 5.4 PR #205 최신 작업 대기 항목
1. 최신 Android PR run의 job 생성·완료 여부를 확인하고 APK/source artifact를 내려받아 APK 존재, 크기, SHA-256을 검증한다.
2. Production Release validation의 결과를 확인한다. 임시 서명 검증 빌드는 운영 서명 릴리스와 구분한다.
3. PR의 base 변경/분기 상태를 확인하고 안전하게 최신 main과 동기화한다. 충돌/검토 상태를 확인하지 않고 병합하지 않는다.
4. PR 테스트와 검토가 통과하면 main 반영 여부를 결정하고, 병합 후 main 기준 전체 빌드를 다시 검증한다.

## 6. 라이선스 상세 정책 및 미완료 설계

### 현재 상품 코드
- `short_7d`: 7일
- `monthly_30d`: 30일
- `quarterly_90d`: 90일
- `annual_365d`: 365일
- `custom`: 관리자 지정 기간

현재 라이선스 테이블은 `expires_at NOT NULL`이며 서명 토큰에도 유한한 `expiresAt`이 필요하다. 그러므로 Lifetime은 아직 발급 불가다. 실제 Lifetime을 제공하려면 DB nullable 정책, 서버 권한 검사, 토큰 모델, Android 오프라인 검증/화면, 폐기/갱신, 테스트를 함께 변경해야 한다.

### 결제
현재 상품 코드 검증 및 관리자 수동 발급은 결제 처리 기능이 아니다. 공급자 선정, 결제 생성/승인, 서명된 webhook 검증, 이벤트 중복 방지, 취소/환불/갱신 및 라이선스 상태 동기화를 별도 개발해야 한다. 공급자와 운영 자격증명이 준비되기 전에는 결제 완료로 표시하지 않는다.

### 키 관리
Android 빌드 서명 입력 변수:
- `GUARDIAN_RELEASE_STORE_FILE`
- `GUARDIAN_RELEASE_STORE_PASSWORD`
- `GUARDIAN_RELEASE_KEY_ALIAS`
- `GUARDIAN_RELEASE_KEY_PASSWORD`

서비스 구성 값:
- `GUARDIAN_MANIFEST_BASE_URL`
- `GUARDIAN_MANIFEST_PUBLIC_KEY`
- `GUARDIAN_LICENSE_API_BASE_URL`
- `GUARDIAN_LICENSE_PUBLIC_KEY`

운영 키는 노트북에서 생성/보관하고 비밀값은 채팅으로 전달하지 않는다. 준비 스크립트 `server/license-api/windows/Prepare-GuardianProductionRelease.ps1` 및 관련 README를 먼저 검토한다. 키 생성과 GitHub Secrets 등록은 실제 사용자 환경의 완료 증거가 있어야 완료 처리한다.

## 7. Windows 버전 완료 계획

Android 1.0 종료 후 다음 트랙으로 진행한다.

### W1. 소스 및 요구사항 기준선
- Windows 관련 프로젝트, 공통 DNS 필터 엔진, UI, 서비스, updater, 설치 스크립트 및 CI를 전수 조사한다.
- Android와 공통화할 규칙 파서/필터 모델/manifest/라이선스 검증 범위를 확정한다.
- Windows 보호의 DNS 기반 한계와 WFP 드라이버 미포함 사실을 제품 문구에 반영한다.

### W2. 보호 엔진 및 복구 안정성
- IPv4/IPv6, UDP/TCP DNS, upstream timeout/fallback, malformed packet, 서비스 재시작, 네트워크 변경을 테스트한다.
- DNS 어댑터 변경 전 설정 백업, 적용 실패 시 rollback, 비정상 종료/재부팅 복구를 검증한다.
- 관리자 권한, Windows Defender/방화벽, 타 VPN·보안 제품 공존성 확인.
- 부팅 자동 시작과 백그라운드 서비스의 상태 감시 및 안전한 실패 동작.

### W3. 필터/업데이트/라이선스
- 서명된 manifest와 필터 payload 무결성 검증.
- 업데이트 원자성, 캐시 rollback, 서명 오류/오래된 manifest/네트워크 단절 처리.
- Android와 동일한 정책/상품/기기 관리 계약을 적용하되 OS별 장치 바인딩은 명시적으로 설계한다.
- 운영 개인키를 Windows 앱 바이너리에 포함하지 않는다.

### W4. Windows UI 및 접근성
- 보호 상태, 시작/중지, 필터 업데이트, 로그/진단, 설정, 라이선스/지원 메뉴.
- 보호 시작 실패 원인과 복구 조치를 사용자에게 구체적으로 안내한다.
- 중복 버튼, 상태 불일치, 업데이트 버튼 상태, 고해상도/배율/키보드 조작을 점검한다.

### W5. 패키징 및 설치 검증
- 실제 EXE와 설치 패키지 생성.
- 새 설치, 기존 버전 업그레이드, 설정 보존, 서비스 설치/시작, 제거, 제거 후 DNS 원복.
- 코드 서명 준비 여부를 분리 기록하고, 미서명 빌드를 정식 배포본으로 표시하지 않는다.
- Windows 10/11 대상 버전별 테스트 및 산출물 SHA-256 기록.

## 8. 문서 체계와 갱신 규칙

이 문서는 전체 인수인계의 목차/기준점이다. 상세 내용은 아래 문서에 분리하고 상호 링크한다.

- `docs/PROJECT_HANDOFF_KO.md`: 전체 기획·설계·현재 상태·우선순위·인수인계 기준.
- `docs/REMAINING_WORKFLOW_KO.md`: Android 1.0 잔여 업무와 완료 조건.
- `docs/LICENSE_AND_CUSTOMER_SUPPORT_KO.md`: 상품 정책, 라이선스 및 고객지원 운영.
- `docs/MULTI_DEVICE_ROADMAP_KO.md`: Android/Windows 공통화 및 멀티 디바이스 방향.
- `WINDOWS_BUILD_KO.md`: Windows 개발/빌드/설치 안내.

매 작업 단위 완료 시 다음을 업데이트한다.
1. 날짜 및 관련 commit/PR/Issue/Actions URL.
2. 변경한 설계·코드·DB/API/UI 경로.
3. 구현 여부와 테스트 명령/결과.
4. 실기기·운영환경 검증 여부.
5. 남은 문제, 원인, 다음 처리 우선순위.
6. 사용자 환경에서만 가능한 작업 및 안전한 실행 방법.

기록은 기존 내용을 무분별하게 덮어쓰지 않고, 최신 상태 요약과 날짜별 변경 이력을 함께 유지한다. 오래된 APK 해시/CI 결과는 과거 이력으로 남기고 현재 산출물과 혼동하지 않는다.

## 9. 다음 실행 우선순위

1. PR #205 최신 Android run/artifact 및 production validation 결과 확정.
2. PR #205와 다른 열린 Android UI/기능 PR의 변경 충돌·의존성·검증 상태 정리.
3. Android 1.0 필수 범위를 기준으로 코드/화면/설정/상태 전이 전수 감사.
4. 앱 제외, 자동 일시중지, 필터 업데이트, 앱 업데이트 후 자동 재연결, 지원센터를 우선적으로 재현 가능한 테스트로 고정.
5. 운영 서버/키/서명/라이선스와 Samsung 실기기 검증의 외부 의존성 분리 및 증거 수집.
6. Android production APK/AAB와 설치/업그레이드/보호 동작을 확인하고 1.0 출시 판정.
7. Android 완료 후 Windows W1~W5를 연속 수행하고 설치형 산출물을 검증.

---

**완료 판정 원칙:** “코드가 있다”는 구현, “CI가 통과했다”는 자동화 검증, “에뮬레이터에서 동작했다”는 에뮬레이터 검증, “실제 폰/PC에서 동작했다”는 실기기 검증, “실제 운영 키·서버·결제·배포가 동작했다”는 운영 검증이다. 서로 대체하지 않는다.


## 10. 작업 이력 — 2026-11-20 업데이트

### 10.1 Android CI / APK 증거
- Android workflow run [44568623163](https://github.com/justinhblee-prog/guardian/actions/runs/44568623163), run #1542, PR branch `feature/support-center-license-plans`.
- Job `Test and assemble debug APK`: 단위 테스트, lintDebug, 개발 서명 인증서 확인, 이전 버전 업그레이드용 APK 빌드, 현재 debug APK 빌드 및 APK 메타데이터 검사 단계가 통과한 뒤 Android API 35 에뮬레이터 설치/실행 검증 단계에 진입했다. 마지막 확인 시 에뮬레이터 설치·실행 단계가 진행 중이었으므로 런타임 통과로 표시하지 않는다.
- Artifact `guardian-android-debug`, artifact ID `14553407194`, 크기 3,648,351 bytes. ZIP SHA-256: `dedd0bab96d7be7c2863ab9ade523903ea57655d56ad0b4d4b06f9920866fa57`.
- ZIP 내부 APK: `android/app/build/outputs/apk/debug/app-debug.apk`, 크기 3,203,979 bytes. APK SHA-256: `00d6b867d81ac3791f5b8b759349360084899b3be17ee295623287d8836e61fe`.
- APK 자체 SHA-256은 ZIP 내부 `SHA256SUMS.txt` 및 workflow summary와 일치했다. 패키지 `com.guardian.adblock`, versionCode 42, versionName `0.7.29-dev`.
- Source archive SHA-256: `e8c7eef4a9419e34e3c3a2809514ad909aa3b77b5f094dfa76797d8e5391ab6c`.
- 이 산출물은 개발용 debug APK이며 Android 1.0 production 서명본이 아니다. workflow summary에 기록된 소스 commit은 `070a8635340f0bf5d937e912e8a16cb1c45d4eb8`; GitHub artifact run metadata의 head SHA는 `e63a9d5ab0fb1faf3e867ad9eee068a9cbb26049`. 두 식별자가 다르므로 provenance를 최종 릴리스 전에 추가 확인한다.
- APK ZIP 및 내부 APK의 파일 존재·크기·SHA-256을 다운로드 후 직접 확인했다. 이 환경에는 Android `apksigner`/emulator 도구가 없어 별도 로컬 서명검증 및 설치는 수행하지 않았다.

### 10.2 License API 및 Production Release 검증
- License API run [44569842160](https://github.com/justinhblee-prog/guardian/actions/runs/44569842160), run #373: PostgreSQL 통합 테스트 job 성공.
- Production Release run [44569842126](https://github.com/justinhblee-prog/guardian/actions/runs/44569842126), run #490: release configuration validation 진행 중이었고 `production-release` job은 운영 secrets 미설정으로 skip. secrets 없이 수행하는 ephemeral APK/AAB build 단계 진행 중이었다.
- 이전 run [44568623171](https://github.com/justinhblee-prog/guardian/actions/runs/44568623171), run #489: 설정 검증 및 secrets 없는 ephemeral direct APK/Play AAB 빌드와 소스 archive 검증까지 성공. production-release job은 skip. 이는 production 서명 릴리스가 아니다.

### 10.3 최신 상태 및 차단
- 인수인계 문서 반영 커밋 `20d97921fa9f67b56e483acfe96cdf942fd466f3`에 대한 새 Android run #1543은 마지막 확인 시 job 생성 전 pending 상태였다.
- PR #205는 Draft, main 대비 69 commits ahead / 1 behind 상태였다. 자동 병합이나 rebase는 수행하지 않았다.
- 다음 순서: run #1542 emulator 단계 완료/실패 로그 확인 → run #1543 최신 HEAD 검증 완료 및 최신 artifact 확보 → PR 변경을 main과 안전하게 동기화 → 앱 제외/자동 일시중지/필터 업데이트/자동 재연결 및 고객지원 UI를 실기기 수용시험 → 운영키/Secrets와 production APK/AAB 확보.
