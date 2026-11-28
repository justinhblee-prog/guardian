# Guardian 필터 자동 수집·서명·배포 운영

## 자동 실행

- 매일 한국시간 오전 4시 (UTC 19:00) GitHub Actions가 실행됩니다.
- 실행 위치: `.github/workflows/guardian-daily-filter-publish.yml`
- 수집·검증·서명: `server/filter-distribution/scripts/update-filters.mjs`
- 공개 배포 위치: `guardian-filter-data` 브랜치
- Android 다운로드 기본 원본: `https://raw.githubusercontent.com/justinhblee-prog/guardian/guardian-filter-data/`

노트북, 모바일, 개인 PC가 켜져 있을 필요가 없습니다. GitHub Actions 예약 실행이 수집부터 게시까지 수행합니다.

## 일회성 저장소 구성값

GitHub 저장소의 Settings → Secrets and variables → Actions에 다음 값을 안전하게 등록해야 합니다.

### Repository secret

- `GUARDIAN_FILTER_SIGNING_PRIVATE_KEY`: 현재 Android 앱에 고정된 공개키와 짝을 이루는 RSA 개인키 PEM. 기존 운영키를 재사용하며 새 키를 임의 생성하거나 교체하지 않습니다.
- `GUARDIAN_MANIFEST_PUBLIC_KEY`: Android 앱 빌드에 고정된 필터 RSA 공개키(Base64). secret 또는 variable로 등록할 수 있으며, workflow는 개인키에서 추출한 공개키와 일치하지 않으면 게시를 거부합니다.

### Repository variable

- `GUARDIAN_FILTER_SOURCES_JSON`: 실제 재배포 권리 검토가 완료된 source 목록을 포함하는 JSON. `sources.example.json`을 기준으로 하되 각 항목에 검토 완료 사실을 확인한 `redistributionApproved: true` 및 추적 가능한 `redistributionApprovalRef`가 있어야 합니다.

이 값이 없거나 권리 검토가 누락된 source가 있으면 workflow는 upstream 다운로드와 공개 게시 전에 실패하도록 fail-closed로 동작합니다. 공개 라이선스 이름만으로 재배포 권한을 추정하거나 승인 플래그를 자동으로 true로 바꾸지 않습니다.

## 처리 단계

1. 기존 `guardian-filter-data` 브랜치의 manifest와 버전 파일을 가져와 anti-rollback 기준으로 사용
2. 승인된 upstream 목록만 HTTPS로 수집
3. UTF-8, NUL, 파일 크기, 최소 규칙 수, 전체 배치 크기 검사
4. SHA-256 계산 및 RSA-SHA256 manifest 서명
5. 개인키에서 공개키를 추출해 Android 신뢰 공개키와의 일치 여부를 검증할 수 있도록 배포
6. manifest 서명과 필터 SHA-256 재검증
7. 버전 고정 필터 파일과 서명을 먼저 게시하고 manifest를 마지막 커밋으로 게시
8. 이전 버전 파일은 유지해 롤백 지원

개인키, 원본 설정, 임시 파일은 공개 브랜치에 게시하지 않습니다.

## 상태 확인

- Workflow: GitHub Actions → Guardian Daily Filter Build and Publish
- 공개 origin: GitHub 저장소의 `guardian-filter-data` 브랜치
- manifest 예: `https://raw.githubusercontent.com/justinhblee-prog/guardian/guardian-filter-data/adguard.manifest`

예약 실행은 main 브랜치에 workflow가 반영된 후 활성화됩니다. 최초 실제 실행 성공, 공개 manifest 접근, Android 실기기 다운로드·서명 검증까지 확인하기 전에는 운영 자동화 완료로 표시하지 않습니다.
