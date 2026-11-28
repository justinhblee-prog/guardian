# Guardian 클라우드 운영 전환 기준

## 목표 구조

- **GitHub Actions:** 매일 필터 원본 수집, 정합성 검사, RSA 서명, 정적 필터/manifest 게시
- **Neon Functions + Neon PostgreSQL:** 라이선스 발급, 활성화, 갱신, 관리자 API와 DB
- **Android:** 앱에 공개키만 포함하고 필터 서명과 라이선스 서명을 별도로 검증
- **노트북:** 개발·비상 복구용일 뿐 운영 서버나 매일 작업의 필수 구성요소가 아님

노트북 Tailscale API와 Docker Compose는 기존 자체 호스팅 경로로만 취급합니다. 클라우드 운영으로 전환할 때 Android의 라이선스 API 주소는 Neon Function HTTPS URL을 사용해야 합니다.

## Neon 배포 자동화

`.github/workflows/guardian-neon-production-deploy.yml`은 수동 실행 전용입니다. main 브랜치에서만 배포하며 다음 순서로 동작합니다.

1. 격리된 PostgreSQL에 스키마와 migration 002~005를 적용합니다.
2. 라이선스 API·암호화·Neon 어댑터 테스트를 실행합니다.
3. Neon이 요구하는 실행 번들을 esbuild로 Node.js 24 ESM 단일 파일로 묶고, ZIP 최상위에 `index.mjs` 하나만 포함하는지 검사합니다. `pg` 등 CommonJS 의존성의 런타임 호환 배너를 적용하고, 번들 자체의 `/health` 및 fail-closed `/ready` 스모크 테스트를 통과해야 다음 단계로 진행합니다.
4. Neon API로 Function `guardianapi`를 배포합니다.
5. Function 배포 상태가 `completed`인지 확인합니다.
6. 실제 `/ready` 응답에서 `ok=true`를 확인해야만 성공으로 종료합니다.

이 워크플로는 PR 검증이나 일반 push에서 운영 Neon에 자동 배포하지 않습니다. 배포는 의도적으로 수동 실행이며 동시 배포는 직렬화됩니다.

### GitHub 설정에 필요한 항목

Repository → Settings → Secrets and variables → Actions에서 아래 항목을 준비합니다.

**Repository Secrets**
- `NEON_API_KEY`: Neon API 접근 키
- `GUARDIAN_ADMIN_TOKEN`: 32자 이상의 무작위 관리자 토큰
- `GUARDIAN_LICENSE_LOOKUP_SECRET`: 관리자 토큰과 다른 32자 이상의 무작위 HMAC 비밀값
- `GUARDIAN_LICENSE_PRIVATE_KEY`: 앱에 이미 고정된 라이선스 공개키와 짝을 이루는 RSA 개인키 PEM

**Repository Variables**
- `NEON_PROJECT_ID`: 대상 Neon 프로젝트 ID
- `NEON_BRANCH_ID`: 대상 Neon 브랜치 ID
- `GUARDIAN_LICENSE_PUBLIC_KEY` (선택): Android Gradle 기본 공개키와 동일해야 합니다.
- `GUARDIAN_LICENSE_API_BASE_URL` (선택): 실제 배포될 Neon Function URL과 동일해야 합니다. 미설정 시 Android Gradle 기본값을 사용합니다.

비밀값은 채팅, Git, 로그, APK, 공개 필터 브랜치에 입력하지 않습니다. 이 워크플로는 누락된 값이나 잘못된 RSA 키를 감지하면 배포 전에 중단합니다. Neon API 키는 배포 요청 인증에만 사용하며 함수 환경변수로 전달하지 않습니다. Neon이 제공하는 `DATABASE_URL`은 함수 런타임에 자동 주입됩니다.

Neon 프로젝트/브랜치 ID는 코드에 고정하지 않아 잘못된 프로젝트로 배포하는 것을 막고 환경별로 지정할 수 있게 했습니다. 운영 프로젝트와 브랜치를 별도로 확인한 후 등록해야 합니다.

## 키 관리 및 회전

현재 Android 앱에는 라이선스 검증 공개키가 포함되어 있습니다. 따라서 기존 공개키와 일치하는 개인키가 안전한 저장소에 존재하는지 먼저 확인해야 합니다.

- 개인키가 보존되어 있다면 그 키를 그대로 사용합니다.
- 기존 개인키를 찾을 수 없다면 임의의 새 개인키를 Neon에 등록하지 않습니다. 앱 공개키 교체와 앱 업데이트를 포함하는 명시적 키 회전 계획이 먼저 필요합니다.
- 필터 서명키는 라이선스 키와 별도로 유지합니다. Android에 필터 공개키가 고정되지 않은 빌드는 운영 필터 업데이트 검증본이 아닙니다.
- 향후에는 오프라인 루트 공개키가 서명한 버전형 keyset으로 필터/라이선스 온라인 키를 회전하는 구조를 별도 구현합니다. 현재 앱에 없는 keyset 검증 기능이 이미 존재한다고 간주하지 않습니다.

키를 새로 생성할 경우 기존 공개키와 다른 키가 됩니다. 기존 고객의 라이선스 서명 검증에 영향을 줄 수 있으므로 기존 키 보존 여부를 확인하기 전에는 생성·교체하지 않습니다.

## 필터 재배포 권리 게이트

필터 목록의 저장소 라이선스가 모든 포함 upstream 데이터의 재배포 권리를 자동으로 증명하지는 않습니다. 각 공급원에 대해 다음을 기록하고 확인해야 합니다.

- `upstreamRepository`: 원본 프로젝트 저장소
- `licenseUrl` 및 `licenseId`: 원본 LICENSE/NOTICE 위치와 SPDX 식별자
- `upstreamVersion`: registry/upstream이 공개한 목록 버전(고정 commit이 아니면 그 사실을 명시)
- `thirdPartyProvenanceReviewed`: 집계된 원본 내부의 제3자 입력 출처까지 검토했는지
- `complianceEvidenceRef`: 소스별 검토 기록·고지 사본·대응 소스 패키지 점검 기록을 가리키는 참조
- `redistributionApproved` 및 `redistributionApprovalRef`: 위 검토를 마친 뒤 Guardian 내부 배포 게이트를 승인했는지와 그 기록

GPL-3.0/MPL-2.0은 그 자체로 상업적 배포를 일괄 금지하는 표시는 아닙니다. 다만 실제 배포물의 형태, 원본·수정 여부, 앱과의 결합 관계에 따라 라이선스 사본, 저작권/출처 고지, 적용되는 대응 소스 등의 의무가 생길 수 있습니다. `redistributionApproved`는 원저작권자의 별도 허가를 받았다는 뜻이 아니라, Guardian이 해당 라이선스 의무를 충족할 수 있다고 내부 검토했다는 뜻입니다.

검토가 끝나지 않은 소스는 계속 차단합니다. 공개 라이선스가 확인된 것과 앱 번들 재배포 의무를 검토한 것을 같은 상태로 취급하지 않습니다. 검토되지 않은 공급원에 대해 `thirdPartyProvenanceReviewed=true` 또는 `redistributionApproved=true`를 일괄 설정하지 않습니다.

## 무료 운영 및 한도

Neon Free와 GitHub Actions 무료 사용 범위 내에서 시작할 수 있지만 무제한 가용성이나 SLA를 의미하지 않습니다. 사용량 제한 도달, 함수 절전, DB 절전, 배포 실패 시 라이선스 발급/갱신은 안전하게 실패하도록 유지합니다. 과금형 자동 업그레이드나 유료 리소스 생성은 별도 승인 없이는 하지 않습니다.

## 현재 완료 판정 기준

다음 항목을 모두 실제로 확인하기 전에는 클라우드 운영 완료로 보고하지 않습니다.

- GitHub Secret/Variable의 필수 항목이 설정되어 있음
- 기존 라이선스 개인키와 앱 공개키의 쌍이 일치함
- Neon Function의 배포 상태가 `completed`
- `/health` 및 `/ready` 실제 HTTP 확인 성공
- 실제 라이선스 발급, 활성화, 갱신, 비활성화 E2E
- Android가 동일한 Neon API 주소와 공개키로 실기기 검증 통과
- 필터 원본별 재배포 권리와 고지 파일 검토 완료
- GitHub 필터 게시 워크플로가 서명 manifest를 실제 공개하고 Android 앱이 이를 검증

검증 자료에는 실행 ID, commit SHA, function deployment ID, API 응답 결과, APK 버전 및 SHA-256을 기록합니다.
