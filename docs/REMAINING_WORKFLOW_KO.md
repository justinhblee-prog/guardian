# Guardian Android 1.0 — 출시 기준 및 실행 계획

기준 main 커밋: `10b2bded2cda6b9ef1cd825a5c16aaa1712dfd55`
최신 main 검증: [GitHub Actions #44263635299](https://github.com/justinhblee-prog/guardian/actions/runs/44263635299) — 30/30 단계 성공
APK: `com.guardian.adblock`, versionCode `27`, versionName `0.7.14-dev`, 3,137,042 bytes
APK SHA-256: `01fac9f6e3a3126ccae21abda06516082470466e88c404b431fff09cf49cee45`
소스 ZIP: 488,972 bytes, SHA-256 `2b55d60065f1a6c84c5ac3a89677b45b8f3b3eb1ef19d2c158e977c067549678`
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
| License API/DB/Tailscale 서버 | 노트북 운영 환경 및 tailnet HTTPS 연결 검증 완료; Android 앱의 실기기 연동은 별도 미검증 | `/ready` HTTP 200, DB/signer ready, 실제 발급·서명 활성화·상태 조회·갱신·비활성화·회수 통합 테스트 |

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


## 8. License API 실운영 통합 검증 — 2026-11-20

- Windows 노트북에서 Docker Compose로 License API 및 PostgreSQL을 실행했습니다. DB와 API 컨테이너가 healthy 상태이며 API 로그에서 8787 포트 수신을 확인했습니다.
- 노트북 로컬 및 Tailscale HTTPS 주소의 `/ready`가 모두 HTTP 200을 반환했고, 응답에서 database와 signer가 ready임을 확인했습니다. Tailscale 주소는 tailnet 내부 접근 경로이며 공개 인터넷 서비스로 간주하지 않습니다.
- 실제 서버 DB 및 기존 운영 서명키를 사용하는 QA 통합 흐름에서 다음 항목이 모두 통과했습니다.
  - QA 라이선스 발급
  - 기기 활성화 및 서명 entitlement 발급
  - 활성 상태 조회
  - entitlement 갱신
  - 기기 비활성화
  - 비활성화 후 Free 상태 확인
  - QA 라이선스 회수
- 따라서 License API 서버의 발급·서명·기기 lifecycle 계약과 서버/DB 연결은 검증 완료입니다. 이 결과만으로 Android 앱의 실기기 연결, 결제 웹훅, 삼성 실결제 또는 상용 판매 준비가 검증된 것은 아닙니다.
- 기존 DB 백업과 RSA 운영 서명키는 유지했습니다. 키 교체·회전, DB 초기화, GitHub Actions 비용/runner 설정 변경은 수행하지 않았습니다.
- 관리자 조회 결과 기존 고객/라이선스 목록은 QA 수행 전 비어 있었습니다. QA 수행 후 레코드 및 감사 이벤트의 잔존 상태는 별도 조회 결과가 확보되기 전까지 단정하지 않습니다.
