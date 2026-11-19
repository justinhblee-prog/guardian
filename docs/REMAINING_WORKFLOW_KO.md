# Guardian Android 1.0 — 출시 기준 및 실행 계획

기준 main 커밋: `6e95058b7286d76a8fea92a169e3fb75fbab5e80` (2026-11-20 KST)
현재 Android 개발 버전: `com.guardian.adblock`, versionName `0.7.29-dev`, versionCode `42`
정식 Android 1.0.0: versionName `1.0.0`, versionCode `43` 정책 검증 완료; production-signed APK/AAB는 아직 최종 확보·검증 전
최신 License API 수정 PR #207: API CI 성공; Android emulator job 실행 중
갱신일: 2026-11-20

## 1. 단일 출시 게이트

Android 1.0의 유일한 완료 체크리스트는 [Issue #141](https://github.com/justinhblee-prog/guardian/issues/141)입니다. 이 목록에 없는 신규 개선은 원칙적으로 출시 후 백로그에 둡니다. 보안 취약점, 데이터 손상, VPN/네트워크 복구 실패 등 출시를 직접 막는 결함만 예외적으로 현재 범위에 추가합니다.

## 2. 세 개의 독립 트랙

| 트랙 | 범위 | 추적 |
|---|---|---|
| A — Android 보호 엔진 | VPN 시작/중지, DNS 차단, 사용자 규칙, 필터 readiness, 오류 진단, 실기기 수용 | [Issue #142](https://github.com/justinhblee-prog/guardian/issues/142) |
| B — 필터·라이선스 | 서명 manifest, SHA-256, 업데이트/캐시, 오프라인 fallback, entitlement 및 기기 lifecycle, 재배포 승인 | [Issue #143](https://github.com/justinhblee-prog/guardian/issues/143) |
| C — 배포·검증 | CI, production signing, APK/AAB, signer fingerprint, checksum, 설치/업그레이드, 문서 | [Issue #144](https://github.com/justinhblee-prog/guardian/issues/144) |

### 병렬 실행 규칙

- 서로 다른 파일과 API 계약을 다루는 작업은 별도 브랜치에서 병렬 진행합니다.
- 같은 파일, 데이터 모델, API 계약을 변경하는 작업은 충돌 방지를 위해 순차 처리합니다.
- 각 트랙은 PR 단위로 검증하고, 통합 후 main 브랜치 CI를 다시 실행합니다.
- 한 트랙의 비차단 항목 때문에 다른 트랙의 독립 작업을 중단하지 않습니다.
- 진행 중인 작업만 진행 중으로 표시합니다. 단순 계획이나 대기 항목을 실행 중으로 표현하지 않습니다.

## 3. Android 1.0 필수 완료 기준

- [ ] 실제 Android 기기에서 VPN 보호 시작/중지 및 서비스 재시작 복구
- [ ] 허용 DNS 실제 응답, 차단 DNS 거부, 카운터 일관성
- [ ] 사용자 규칙 저장·화면 회전·앱 재시작 후 보존 및 VPN 적용
- [ ] 필터 다운로드, 서명/해시 검증, 캐시, 오프라인 fallback 및 업데이트 실패 복구
- [ ] 승인된 필터 원본만 production 배포
- [ ] 실제 라이선스 API 연결, entitlement 확인, 기기 등록·갱신·정지
- [ ] 운영용 서명키로 Release APK 생성
- [ ] signer fingerprint, package ID, version, APK SHA-256 검증
- [ ] 설치·업그레이드·핵심 회귀 테스트 및 사용자 문서 완료

에뮬레이터 성공은 실기기 수용 테스트를 대체하지 않습니다. Debug APK는 production Release로 간주하지 않습니다.

## 4. 현재 확인된 결과

- PR #140의 DoH/DoT 우회 한계 안내 변경은 main run #44263635299에서 단위 테스트, Lint, APK 빌드, 소스 패키징, 에뮬레이터 업그레이드 및 VPN DNS E2E를 포함한 30개 단계가 모두 성공했습니다.
- 검증 APK와 소스 ZIP의 파일 존재·크기·SHA-256을 다운로드 후 독립적으로 다시 확인했습니다.
- APK 내부에 `lib/*.so` 네이티브 라이브러리는 없습니다. 따라서 현재 검증 APK만으로는 과거의 `Library file not found`가 네이티브 라이브러리 누락 때문이라고 볼 근거가 없습니다.
- 기존 실기기 `Library file not found`의 실제 원인은 미확정입니다.
- 에뮬레이터 VPN DNS E2E는 성공했으나, 실기기 VPN, 광고 표시 제거율, 네트워크 전환 및 장시간 안정성은 미검증입니다.

## 5. 외부 의존 blocker

| 항목 | 상태 | 완료 증거 |
|---|---|---|
| 실기기 오류 원인 | 기기 로그 없음, 미확정 | 실제 기기의 개인정보 제외 지원 보고서 및 오류 재현 로그 |
| 필터 재배포 승인 | 예시 원본 승인 미완료 | 원본별 재배포 허용 근거와 승인 참조 |
| 운영 Release 서명 | production keystore/secrets 미설정 | 안전한 외부 비밀 저장소의 운영 키와 signer fingerprint |
| License API/DB/Tailscale | 사용자 Windows 운영 환경 미검증 | 실제 연결과 기기 lifecycle E2E 결과 |

이 항목들은 코드 수정만으로 완료 처리할 수 없습니다. 사용자의 PC, 실제 기기, 운영 키 또는 권리자 승인에 의존하는 부분은 외부 blocker로 명확히 유지합니다.

## 6. 출시 후 백로그

다음은 Android 1.0 출시 차단 조건으로 자동 승격하지 않습니다.

- 고급 통계/대시보드 및 추가 UI 장식
- 추가 차단 카테고리 및 편의 규칙
- 앱 내부 광고 요소 제거와 모든 자체 DoH/DoT 구현을 포괄하는 광범위한 차단
- Windows/PC 및 태블릿 클라이언트 확장
- 필수 회귀 테스트를 깨뜨리지 않는 추가 성능 튜닝

## 7. 안전·비용 제한

- 운영 개인키, keystore, 관리자 토큰, 환경 파일 및 사용자 데이터는 저장소에 커밋하지 않습니다.
- GitHub Actions billing, runner settings, usage limit을 변경하지 않습니다.
- 유료 리소스나 외부 API 비용이 필요한 작업은 실행하지 않습니다.
- CI 인프라 문제와 앱 코드 실패를 구분합니다.
- 완료 표시는 커밋, CI, 산출물, 체크섬, 기기 또는 서버 검증 증거가 있을 때만 허용합니다.
## 8. 최신 병렬 작업 상태 — 2026-11-20

이 절은 과거 검증 기록을 대체하지 않고, 현재 출시 판정을 위해 최신 상태를 구분한다. Android 1.0의 유일한 범위 게이트는 Issue #141이며 새 개선 항목을 무제한으로 추가하지 않는다.

### Track A — Android 보호 엔진
- 이전 main/PR 검증에서 Android API 35 emulator의 앱별 VPN 제외 저장·실제 재구성, 허용 DNS 응답, built-in 및 사용자 차단 규칙, 앱 업데이트 후 VPN 자동 복구, foreground 자동 일시중지/재연결, 홈 보호 시작·중지가 통과한 기록이 있다(예: run #1542). 이는 해당 커밋의 에뮬레이터 증거이며 현재 실기기 검증을 대체하지 않는다.
- PR #205의 최신 고객지원 UI 포함 E2E run `44573951849`에서는 앱별 제외 및 DNS 허용/차단과 앱 업데이트 후 VPN 복구가 통과했다. 고객지원 UI 검사에서 Settings 스크롤 위치가 유지된 상태로 상단 고객지원 카드를 찾지 못해 workflow가 중단됐으며, 이 때문에 해당 실행의 후속 auto-pause 검사는 수행되지 않았다.
- 고객지원 UI 검사 전에 Settings ScrollView를 상단으로 되돌리도록 스크립트를 수정했다. commit `d8b1d0f1f676555163f802a33650eb2be796cdc4`. 해당 수정 commit에 대한 새 Actions 검증은 아직 확인되지 않았다.
- Samsung 실기기에서 `Library file not found`, VPN 시작/중지, 앱별 제외, 네트워크 전환 및 장시간 복구를 재현·확인한 증거는 아직 없다.

### Track B — 필터·라이선스·고객지원
- 고객 디렉터리 migration 004와 `customers`, `licenses.customer_id`, `licenses.order_ref`는 사용자 노트북 PostgreSQL에서 확인됐다.
- 사용자 노트북 License API `/ready`는 현재 503이다. 원인은 literal `\\n` PEM 줄바꿈을 실제 개행으로 바꾸지 못한 API 코드로 확인됐다. 컨테이너에서 정규화 후 개인키 파싱은 RSA 3072-bit로 성공했다.
- PR #207 (`https://github.com/justinhblee-prog/guardian/pull/207`)에서 PEM newline normalization 한 줄을 수정했다. 해당 head의 License API workflow는 성공했고 Android APK/emulator workflow는 마지막 확인 시 실행 중이었다. PR merge, API 재빌드 및 실제 `/ready` 200은 아직 미확인이다.
- 고객지원 API/화면과 migration 003은 Draft PR #205에만 포함돼 있다. 운영 DB에 migration 003을 적용하지 않았으며, PR 변경분을 main과 안전하게 동기화하고 테스트한 뒤 적용한다.
- 필터 원격 브랜치 `guardian-filter-data`는 아직 존재하지 않는다. `sources.example.json`의 7개 공급원은 모두 `redistributionApproved=false`이며 승인 참조가 비어 있다. 재배포 권리 검토 및 추적 가능한 승인 증빙 전에는 승인 플래그를 변경하거나 필터를 게시하지 않는다.

### Track C — 1.0 배포·서명
- Release workflow의 PR/push 검증에서 임시 키를 사용한 Direct APK 및 Play AAB 빌드·메타데이터·서명 경로 검증이 통과한 기록이 있다. 이는 운영 서명 산출물이 아니다.
- 실제 1.0.0 versionCode 정책은 개발 versionCode 42보다 큰 43으로 설정된다.
- 정식 Release 완료 증거는 운영 keystore로 서명된 APK/AAB, 인증서 SHA-256 지문, package/version, 다운로드 후 재계산한 APK SHA-256, 설치·업그레이드 검증이다. 현재 대화/CI 증거만으로 이 전체 묶음이 완료됐다고 표시하지 않는다.

### 병렬 PR 통합 규칙
- PR #205는 main 대비 53 commits ahead / 3 commits behind로 diverged 상태이며 현재 mergeable=false다. 그대로 병합하지 않는다. PR #206에서 main에 추가된 customer migration 004와 PR #207의 PEM 수정이 PR #205의 server/schema/admin 변경과 충돌하지 않도록 재동기화 후 전체 테스트를 다시 실행한다.
- PR #207은 License API 수정만을 위한 별도 PR로 유지한다. API CI 성공만으로 운영 서버 배포 완료로 표시하지 않는다.
- 필터 재배포 승인은 코드/CI로 대신할 수 없는 법적 외부 의존성이다. 미승인 상태에서는 출시용 필터 배포를 fail-closed로 유지한다.

### 1.0 완료 판정
- Issue #141의 필수 조건 전부에 대해 구현, 자동화, emulator, 실기기, 운영환경 증거를 각각 연결한다.
- 실기기/운영키/권리자 승인처럼 외부 환경이 필요한 항목은 코드 작업을 반복하지 말고 외부 blocker로 유지한다.
- Android 1.0이 실제로 서명·설치·보호 동작까지 확인된 뒤에만 Windows 제품화 트랙으로 이동한다.
