# Guardian Android 1.0 — 잔여 작업 및 실행 기준

> 갱신: 2026-11-20 (KST)  
> 단일 출시 판정: [Issue #141](https://github.com/justinhblee-prog/guardian/issues/141)  
> 세부 트랙: [#142 Android 보호 엔진](https://github.com/justinhblee-prog/guardian/issues/142) · [#143 필터/라이선스 계약](https://github.com/justinhblee-prog/guardian/issues/143) · [#144 Production 배포](https://github.com/justinhblee-prog/guardian/issues/144)

## 1. 범위 고정

Android 1.0은 Issue #141의 필수 항목만 완료 기준으로 사용합니다. 추가 대시보드, 광범위한 UI 꾸미기, 출시 후 확장 기능을 출시 조건에 계속 추가하지 않습니다. 보안 결함, 데이터 손상, VPN/DNS 보호 및 복구 실패만 예외적으로 필수 범위에 포함합니다.

작업 트랙은 서로 독립적으로 병렬 진행합니다.

| 트랙 | 담당 및 경계 | 완료 증거 |
|---|---|---|
| A. Android 앱/보호 엔진 | 저장소에서 가능한 코드 분석·수정·단위 테스트·lint·에뮬레이터 테스트 | commit, CI 결과, 에뮬레이터 증거 |
| B. 필터 배포/앱 라이선스 계약 | 앱 코드의 검증·갱신 계약은 개발 트랙에서 처리. 운영키·고객 발급 정책·실제 노트북 DB 운영은 사용자와 별도 병행 | 서명/해시/캐시 테스트, 실제 API 연동 증거 |
| C. Production 배포 | release workflow, 버전/서명 검증, 산출물 및 설치 문서 준비 | 운영키 서명 APK/AAB, signer fingerprint, SHA-256, 설치/업그레이드 검증 |
| D. 외부 실기기 수용 | 실제 Android/Samsung 기기 및 사용자의 재현 로그가 필요한 부분 | 실제 기기 버전·오류 로그·VPN/DNS·복구 결과 |

## 2. 현재 확인된 증거와 제한

### Android 보호 엔진
- Main Android workflow는 unit tests, lint, debug APK build, 에뮬레이터 설치/업그레이드 및 VPN/DNS E2E를 수행하도록 구성되어 있습니다.
- PR #205의 테스트 기록에는 앱 제외, DNS 허용/차단, 앱 업데이트 후 VPN 복구, 선택 앱 실행 중 자동 일시중지/재개 및 Home 보호 시작/중지가 포함되어 있습니다.
- PR #205에서 고객지원 UI acceptance가 Settings의 이전 스크롤 위치 때문에 실패한 이력이 있고, head에는 Settings 상단 복귀를 추가한 수정 커밋 `d8b1d0f1f676555163f802a33650eb2be796cdc4`가 포함되어 있습니다. 이 수정 이후 최신 PR head의 CI 성공은 별도 확인되지 않았습니다.
- Samsung 실기기의 `Library file not found` 원인은 미확정입니다. 기존 APK 조사에서는 앱 자체 JNI/native library 누락이 확인되지 않았으나, 실제 단말의 privacy-filtered GuardianProtection stack summary가 있어야 호출자를 특정할 수 있습니다.
- 에뮬레이터 테스트 성공을 Samsung 실기기 수용 완료로 취급하지 않습니다.

### 필터 배포
- `server/filter-distribution/sources.example.json`의 7개 예시 원본은 모두 `redistributionApproved=false`, 승인 참조 빈 값입니다.
- 원본별 재배포 권리 근거를 확인하기 전에는 승인값을 바꾸거나 상용 필터 데이터를 게시하지 않습니다.
- 필터 배포 브랜치 `guardian-filter-data`는 2026-11-20 저장소 branch 검색에서 확인되지 않았습니다.

### License API — 서버 측 검증 완료, 운영 협업은 별도
2026-11-20 사용자가 노트북에서 수행한 결과:
- Docker API 및 PostgreSQL 컨테이너 healthy.
- 로컬 및 Tailscale HTTPS `/ready` HTTP 200, DB 및 signer ready.
- QA 발급 → 기기 활성화 및 signed entitlement → active 조회 → refresh → 기기 해제 → Free 상태 확인 → QA 라이선스 회수 흐름 통과.
- 이 증거는 서버/DB lifecycle 계약 검증입니다. Android 실기기 연결, 결제 webhook, 삼성 실결제 또는 상용 판매는 검증하지 않습니다.
- 이후 운영키 발급/백업, 고객 상품 정책, 관리자 발급 절차, 결제 연동은 Android 코드 작업과 분리해 사용자와 노트북에서 진행합니다. 이미 통과한 서버 lifecycle 테스트를 반복하지 않습니다.

### Production 배포
- Android Gradle 설정은 운영 keystore와 서비스 endpoint/public key 설정이 없으면 Release APK/AAB 조립을 거부하도록 되어 있습니다.
- Production release workflow의 ephemeral signer는 설정 검증용이며 사용자 소유 Production 서명키가 아닙니다.
- 실제 운영 keystore/secrets가 제공되지 않았으므로 Production APK/AAB, signer fingerprint 및 production checksum은 아직 없습니다.
- Debug APK와 임시 서명 검증 APK를 정식 배포 산출물로 취급하지 않습니다.

## 3. Android 1.0 필수 완료 체크

- [ ] 실제 기기에서 VPN 보호 시작/중지 및 서비스/앱 재시작 복구
- [ ] 실제 기기에서 허용 DNS 응답, 차단 DNS 차단, 카운터 일관성
- [ ] 사용자 규칙 저장·화면 회전·앱 재시작 후 보존 및 VPN 적용
- [ ] 필터 다운로드, 서명/해시 검증, 캐시 및 업데이트 실패 시 마지막 정상 캐시 유지
- [ ] 승인된 원본만 Production 필터 배포
- [ ] 앱에서 실제 License API HTTPS 연결, signed entitlement 검증, 기기 등록/갱신/정지
- [ ] 사용자 소유 운영키로 Production Release APK 생성
- [ ] APK package ID, version, signer fingerprint 및 SHA-256 검증
- [ ] 신규 설치/업그레이드 및 핵심 회귀 테스트
- [ ] 한국어 설치·복구·제한사항 문서 최신화

## 4. 병렬 진행 규칙

1. 저장소 접근으로 해결 가능한 코드/문서/CI 작업은 별도 승인 대기 없이 수행합니다.
2. 동일 파일/API 계약 변경은 충돌 방지를 위해 순차 처리하고, 독립 파일 작업은 병렬 처리합니다.
3. 라이선스 고객 운영·운영키 생성은 사용자와 별도 세션/노트북에서 진행하며 Android 코드 트랙을 막지 않습니다.
4. 사용자 기기 로그, 권리자 재배포 승인, 운영 서명키처럼 외부에서만 얻을 수 있는 증거는 외부 blocker로 유지합니다.
5. GitHub Actions billing, runner, usage 설정 변경이나 유료 리소스 사용은 하지 않습니다.
6. 성공 판정에는 commit/CI/실제 산출물/기기/서버 증거를 연결합니다. 확인하지 못한 결과를 완료로 표시하지 않습니다.

## 5. Windows 순서

Android 1.0 출시 필수 조건이 닫힌 뒤 Windows 보호 엔진과 설치 패키지를 진행합니다. Android 출시 차단과 무관한 Windows 작업을 Android 범위에 끌어오지 않습니다.

## 2026-11-20 최신 저장소 확인 — main `ef0d75e7551477e9aa2d3b004d14bf867249302a`

- Git 자동화 관련 workflow: [Guardian Git CLI Automation #8](https://github.com/justinhblee-prog/guardian/actions/runs/44709774724), [Git Consistency Gate #5](https://github.com/justinhblee-prog/guardian/actions/runs/44709774731), [Event-Driven Continuation #923](https://github.com/justinhblee-prog/guardian/actions/runs/44710914353) 모두 성공.
- 최신으로 확인된 Android APK workflow #1621은 성공했지만 head SHA가 `25a5dbaf9ba9baea0c421f2e8d9fec953a156b17`입니다. 현재 main `ef0d75e7` 빌드가 아니므로 현재 main 산출물의 검증으로 간주하지 않습니다. 해당 run의 artifact는 debug APK ZIP ID `14616472344`, final verification ID `14617700552`입니다.
- 현재 main과 PR #205 head의 비교 결과: PR head는 59 commits ahead / 62 commits behind로 diverged. PR을 그대로 병합하면 main의 관리자 고객 검색/고객 연결 발급 흐름이 사라질 위험이 확인되어 PR 대화에 통합 주의사항을 기록했습니다. 고객지원 기능은 main의 고객 관리 기능을 보존하는 방식으로 재통합해야 합니다.
- PR #202는 emulator verification script 충돌, PR #75는 광범위한 브랜치 divergence와 충돌로 미통합 상태입니다. 충돌 브랜치에 force-push하지 않았습니다.
- 운영 차단 항목은 여전히 실기기 Samsung 검증, 필터 원본 재배포 권리 승인 및 배포 브랜치, 사용자 소유 production signing key/secrets, 실제 결제 webhook 검증입니다. Debug APK 성공은 이 항목들을 대체하지 않습니다.
- 다음 CI 판정은 이 문서 변경이 반영된 새 commit SHA에서 Android workflow가 실제로 실행·완료된 뒤 기록합니다.
