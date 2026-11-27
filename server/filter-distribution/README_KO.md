# Guardian Signed Filter Distribution

Node.js 22 기반의 서명 manifest 및 버전 고정 필터 배포 서버입니다. 앱의 `SignedManifestHttpClient`가 기대하는 `X-Guardian-Signature` 응답 헤더와 manifest 형식을 그대로 제공합니다.

## 구성

- `scripts/publish-manifest.mjs`: 오프라인 서명 환경에서 필터 파일의 SHA-256과 RSA-SHA256 detached signature를 생성합니다.
- `src/server.mjs`: manifest, signature header, 버전 고정 필터 파일을 읽기 전용으로 제공합니다.
- `docker-compose.yml`: 비루트 Node 컨테이너를 사용하며 호스트 포트는 localhost에만 공개합니다.
- 개인키는 배포 서버 컨테이너에 넣지 않습니다. 공개키만 Android 앱 빌드에 포함합니다.

## 1. 서명키 준비 (오프라인 서명 PC)

RSA 3072비트 키를 생성합니다. Publisher와 Updater는 RSA 2048~4096비트 키만 허용하며, 범위를 벗어나거나 RSA 이외의 키 유형은 거부합니다. 개인키는 암호화된 이동식 저장소 또는 비밀 관리자에 보관하고 Git, 서버 컨테이너, Android APK, 채팅에 올리지 마세요.

```sh
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out guardian-filter-private.pem
```

Publisher는 PEM 개인키를 `GUARDIAN_FILTER_SIGNING_PRIVATE_KEY` 환경 변수로 받습니다. Linux/macOS 예:

```sh
export GUARDIAN_FILTER_SIGNING_PRIVATE_KEY="$(awk '{printf "%s\\n", $0}' guardian-filter-private.pem)"
```

## 2-A. 자동 업데이트 (upstream 다운로드 + 검증 + 서명)

수동 발행과 자동 수집을 모두 지원합니다. `sources.example.json`을 복사해 `sources.json`을 만들 수 있습니다. 예제에는 AdGuard DNS Filter, 1Hosts Lite, OISD Big, HaGeZi Normal, HaGeZi Pro, Security, Android 앱 광고 등 7개 공급원 URL과 공개 라이선스 식별자가 기록되어 있지만, 모든 항목은 기본적으로 `redistributionApproved: false`로 잠겨 있습니다. **라이선스 검토 및 상용 재배포 승인 기록이 없는 source는 다운로드·서명·게시할 수 없습니다.** 검토가 완료된 개별 source에 한해 `redistributionApproved: true`와 추적 가능한 `redistributionApprovalRef`(예: 사내 법무 티켓/승인 문서 ID)를 기록하세요. 승인 참조를 공란 또는 공백으로 두면 게시가 거부됩니다. 기술적 다운로드 성공이나 공개 라이선스 이름만으로 승인된 것으로 간주하지 마세요. 예제 URL과 최소 규칙 수도 실제 운영 전에 원본 배포처와 대조해야 합니다.

```sh
cp sources.example.json sources.json
export GUARDIAN_FILTER_SIGNING_PRIVATE_KEY="$(awk '{printf "%s\\n", $0}' guardian-filter-private.pem)"
npm run update -- ./sources.json
```

Updater는 네트워크 요청 전에 RSA 2048~4096비트 서명키 존재 여부, 유형 및 크기와 각 source의 `licenseId`, 전체 `upstreamCommitSha`와 URL 내 동일 커밋 일치, 명시적 재배포 승인 플래그, 추적 가능한 승인 참조를 확인합니다. Android 정식 Release 워크플로도 별도 fail-closed 검사를 수행합니다. Windows `Prepare-GuardianProductionRelease.ps1`이 실제 운영 `sources.json`에서 승인 메타데이터만 추출해 GitHub Repository Variable `GUARDIAN_FILTER_APPROVALS_JSON`에 기록하며, Actions는 모든 source를 다시 검증합니다. 이 값이 없거나 승인/참조가 빠진 source가 하나라도 있으면 서명 Release APK/AAB 게시가 중단됩니다. 공개 클라우드 원본 브랜치 `guardian-filter-data`가 존재하는지도 확인합니다. 이 검사는 법적 권리 검토를 대신하지 않으며, 승인 플래그와 참조는 실제 권리 검토 완료 후에만 기록해야 합니다. 승인되지 않은 source가 하나라도 있으면 upstream에 접속하거나 manifest를 쓰기 전에 전체 배치를 중단합니다. HTTPS만 허용하고 redirect는 거부하며, 요청당 30초 timeout, 파일당 10 MiB 및 배치 전체 64 MiB 상한, 최대 100개 source 제한, UTF-8/NUL 검사, 최소 규칙 수 검사를 통과한 파일만 서명합니다. 버전은 UTC 날짜와 콘텐츠 SHA-256 앞 12자리 조합으로 생성하므로 같은 날짜의 변경도 별도 immutable 버전으로 발행됩니다. 모든 upstream 응답을 먼저 수집·검증하고 임시 파일로 준비한 뒤에만 게시를 시작하므로, 뒤쪽 upstream 하나라도 다운로드·검증에 실패하면 해당 실행에서는 어떤 manifest도 갱신하지 않습니다. 임시 파일은 전용 권한(0600)으로 생성하고 게시 전에 파일 내용을 동기화합니다. 단, 게시 단계에서 파일시스템 오류가 발생한 경우 여러 source 전체를 하나의 트랜잭션으로 되돌리지는 못하므로 source별 게시 결과를 확인해야 합니다. 동일 output 디렉터리의 자동 업데이트 실행은 `.guardian-update.lock`으로 직렬화합니다. 실행 중 잠금이 이미 있으면 네트워크 요청 전에 중단합니다. 잠금 해제 시에는 현재 경로가 해당 프로세스가 만든 파일과 동일한 파일 객체인지 확인한 뒤에만 제거하여, 잠금 경로가 교체된 경우 다른 실행의 잠금을 지우지 않습니다. 강제 종료·전원 차단으로 잠금 파일이 남을 수 있으므로, 다른 updater 프로세스가 실행 중이지 않은 것을 확인한 뒤 운영자가 잠금 파일을 수동으로 제거해야 합니다. 잠금은 자동 만료시키지 않아 실제 실행 중인 작업을 잘못 중복 실행하지 않도록 합니다.

정기 실행은 배포/서명 PC에서만 구성합니다. Windows 작업 스케줄러에서는 작업의 **프로그램/스크립트**에 `UPDATE_FILTERS.bat`의 전체 경로를 지정하고, **시작 위치**에는 `server\\filter-distribution` 폴더를 지정합니다. 트리거는 원하는 일일 시간(예: 04:17)으로 설정하고, 작업 실행 계정에 Node.js 22 이상과 npm이 설치되어 있어야 합니다. `sources.example.json`을 `sources.json`으로 복사해 upstream 주소와 최소 규칙 수를 확인한 뒤 실행하세요. 운영 승인 source는 `upstreamCommitSha`에 전체 Git commit SHA(40~64자리)를 기록하고, 실제 `url`은 `upstreamRepository`와 동일한 GitHub 저장소를 가리키는 `raw.githubusercontent.com/{owner}/{repo}/{sha}/...` 또는 `cdn.jsdelivr.net/gh/{owner}/{repo}@{sha}/...` 형식이어야 합니다. 호스트·owner·repo·commit SHA가 모두 일치하지 않으면 승인 및 다운로드 전에 차단됩니다. `upstreamVersion`은 사람이 읽는 버전 표기일 뿐이므로 커밋 고정을 대신하지 않습니다. 실행 시 `GUARDIAN_FILTER_SIGNING_PRIVATE_KEY` 환경 변수가 해당 작업 프로세스에 제공되어야 합니다. 조직의 secret manager 또는 보호된 서명 환경을 통해 주입하고, PEM을 배치 파일·작업 인수·로그·저장소에 직접 기록하지 마세요. 이 변수가 없으면 배치 파일은 업데이트를 시작하지 않고 실패 코드로 종료합니다. 개인키를 Windows 운영 웹 서버나 Android 기기에 복사하지 마세요.

Linux cron 예시는 다음과 같습니다.

```cron
17 4 * * * cd /opt/guardian/server/filter-distribution && /usr/bin/npm run update -- ./sources.json >> /var/log/guardian-filter-update.log 2>&1
```

이 자동 수집 방식은 필터 파일을 가져오고 서명하는 단계까지 자동화합니다. 실제 인터넷 배포 서버 반영은 별도의 안전한 배포 절차로 `dist/` 산출물을 복사해야 합니다. 개인키를 운영 웹 서버나 Android 앱에 복사하지 마세요.

## 2. 필터 파일 준비 및 manifest 서명

필터 파일은 HTTPS upstream에서 별도 검토·다운로드한 UTF-8 원본이어야 합니다. 예:

```sh
curl -fL --proto '=https' --tlsv1.2 \
  https://adguardteam.github.io/HostlistsRegistry/assets/filter_1.txt \
  -o adguard-filter.txt
```

배포 서버의 공개 HTTPS 기본 주소를 사용해 서명합니다:

```sh
node scripts/publish-manifest.mjs \
  --source-id adguard \
  --source-version 2026.11.15 \
  --input ./adguard-filter.txt \
  --base-url https://filters.example.com \
  --minimum-rules 1000 \
  --output-dir ./dist \
  --public-key-out ./guardian-filter-public-key.x509.b64
```

앱이 사용하는 sourceId와 최소 규칙 수:

| 앱 필터 | sourceId | 최소 규칙 | 라이선스 식별자 |
|---|---|---:|---|
| AdGuard DNS Filter | `adguard` | 1000 | GPL-3.0 |
| 1Hosts Lite | `1hosts_lite` | 100 | MPL-2.0 |
| OISD Big | `oisd_big` | 100 | GPL-3.0 |
| HaGeZi Normal | `hagezi_normal` | 100 | GPL-3.0 |
| HaGeZi Pro | `hagezi_pro` | 100 | GPL-3.0 |
| 보안 필터 | `security` | 100 | GPL-3.0 |
| Android 앱 광고 | `android_ads` | 100 | GPL-3.0 |

각 sourceId/version에 대해 같은 버전의 다른 콘텐츠로 덮어쓰는 것을 publisher가 거부합니다. 새 필터는 반드시 새 sourceVersion으로 발행하세요. 동일 초 내 연속 발행도 anti-rollback 조건을 만족하도록 generatedAt을 증가시킵니다.

Publisher가 출력하는 `publicKey` 또는 `guardian-filter-public-key.x509.b64` 내용은 X.509 SubjectPublicKeyInfo Base64 공개키입니다. 이 값만 앱 빌드의 `GUARDIAN_MANIFEST_PUBLIC_KEY`에 설정합니다. 빌드 기본 주소는 `GUARDIAN_MANIFEST_BASE_URL`에 지정할 수 있으며, 설치 후에는 Android 앱 설정의 **서명 필터 배포 서버** 항목에서 HTTPS 주소를 변경할 수 있습니다. 주소 변경은 서버 위치만 바꾸며 서명 신뢰 키는 변경하지 않습니다. 서명 키를 교체할 때는 앱 업데이트와 키 교체 절차를 함께 계획하세요.

## 3. 배포 서버 시작

```sh
cp .env.example .env
mkdir -p dist
docker compose up -d --build
docker compose ps
curl -f http://127.0.0.1:8788/health
```

Docker 컨테이너 내부 프로세스는 포트 포워딩을 위해 `0.0.0.0:8788`에 바인딩하지만, Compose의 호스트 포트는 `127.0.0.1:8788`에만 공개됩니다. 따라서 Docker 호스트 외부에서 직접 8788 포트에 접근할 수 없습니다. 컨테이너 밖에서 Node 서버를 직접 실행할 때 기본 HOST는 `127.0.0.1`이며, 외부 공개가 필요하면 신뢰된 reverse proxy 뒤에서만 별도 설정하세요. 인터넷 공개 전 HTTPS reverse proxy/WAF를 구성하고 `filters.example.com` 도메인에 TLS 인증서를 연결해야 합니다. manifest 경로는 `/{sourceId}.manifest`, 콘텐츠 경로는 `/filters/{sourceId}/{sourceVersion}.txt`입니다. reverse proxy가 이 경로와 `X-Guardian-Signature` 헤더를 변경하지 않도록 설정해야 합니다.

## 제3자 필터 라이선스와 재배포 승인

현재 7개 예시 원본의 upstream 프로젝트/라이선스 표기와 별도 주의사항은 [THIRD_PARTY_NOTICES_KO.md](THIRD_PARTY_NOTICES_KO.md)에 정리합니다. 이 목록은 예비 조사이며 재배포 승인서가 아닙니다. 각 원본을 유료 앱에 포함하고 서명 CDN으로 다시 배포할 권리가 실제 배포 버전 및 포함된 제3자 데이터까지 확인되기 전에는 `redistributionApproved=false`를 유지해야 합니다. 권리 검토가 끝난 source만 추적 가능한 법무/승인 참조를 기록하세요.

## 보안 및 운영

- 서명은 manifest 원문 UTF-8 바이트에 RSA-SHA256을 적용합니다. 서버는 manifest와 detached signature를 함께 읽고 signature를 `X-Guardian-Signature`로 전달합니다.
- manifest는 `no-store`, 버전 고정 필터 파일은 `immutable` 캐시 정책을 사용합니다.
- 배포 서버는 detached RSA 서명 Base64의 canonical 형식과 2048~4096비트 RSA 서명 길이를 확인합니다. sourceId는 영문·숫자·밑줄·하이픈만 허용하며 publisher와 updater의 검증 규칙도 동일하게 적용합니다.
- 새 버전 콘텐츠와 `sourceId.sourceVersion.generatedAt.sig` 형식의 버전별 signature를 먼저 기록하고 manifest를 마지막에 원자적으로 교체합니다. 이전 manifest는 이전 버전 signature를 계속 참조하므로 발행 중에도 signature/manifest 불일치 구간을 만들지 않습니다. manifest는 공개 배포의 commit marker입니다.
- 필터 파일 최대 10 MiB, manifest 최대 16 KiB, sourceId/version 경로는 허용 문자로 제한합니다. sourceId는 영문·숫자·밑줄·하이픈만 허용해 점 세그먼트 경로를 차단합니다.
- 서버는 배포 루트 아래 파일의 부모 디렉터리와 최종 파일의 심볼릭 링크를 거부하고, 동일 파일 디스크립터에서 크기 확인과 읽기를 수행합니다. 배포 디렉터리는 신뢰된 운영 계정만 쓰도록 권한을 제한하세요.
- HTTP 서버는 헤더 수를 32개로 제한하고 headers timeout 10초, request timeout 15초, keep-alive timeout 5초를 적용합니다.
- 서명 개인키는 오프라인 발행 환경에서만 사용합니다. 운영 웹 서버에는 개인키가 필요하지 않습니다.
- 필터 콘텐츠의 실제 DNS 규칙 수와 유효성은 앱의 parser가 다시 검사합니다. Publisher의 `minimumRules` 값은 앱이 검증할 기준값이며, 앱은 기준 미달 콘텐츠를 거부합니다.
- `dist/`, 개인키, 환경 파일은 저장소에 커밋하지 마세요. 배포 서버에는 서명 완료된 공개 산출물만 복사합니다.
- 서버 호스팅, 도메인, TLS 인증서, DNS, 백업 및 키 보관소는 운영자가 별도로 준비해야 합니다. 이 저장소는 실제 공개 서버를 자동 생성하거나 배포하지 않습니다.

## 동시 manifest 발행 잠금

Publisher는 동일 output 디렉터리에서 source별 배타적 lock 파일(`.<sourceId>.publish.lock`)을 사용해 동시 발행을 거부합니다. 잠금은 같은 디렉터리/공유 파일시스템을 사용하는 프로세스만 조정하며, 별도 ephemeral volume을 사용하는 독립 replica 사이에는 적용되지 않습니다. 강제 종료로 lock이 남으면 publisher 프로세스가 실행 중이지 않은지 확인한 후 운영자가 수동으로 제거해야 합니다. 자동 stale-lock 회수는 수행하지 않습니다. 잠금 경로 정리는 신뢰된 운영 계정만 output 디렉터리에 쓸 수 있다는 전제를 둡니다.


## 4. 노트북 서명 + GitHub 정적 클라우드 배포

운영 필터 파일은 개인 Windows 노트북에서 upstream 다운로드, 규칙 수/UTF-8 검증, RSA-SHA256 서명을 수행한 뒤 GitHub 공개 저장소의 전용 `guardian-filter-data` 브랜치에 정적 파일로 게시할 수 있습니다. 별도 웹 서버나 유료 스토리지 없이 GitHub raw CDN에서 모바일 앱이 직접 내려받습니다.

- 정적 원본: `https://raw.githubusercontent.com/justinhblee-prog/guardian/guardian-filter-data`
- 게시 스크립트: `server/license-api/windows/Publish-GuardianFilterToCloud.ps1`
- 전체 수동 실행: `server/license-api/windows/Update-And-Publish-GuardianFilters.ps1`
- 자동 작업: `Register-GuardianFilterUpdateTask.ps1`가 매일 다운로드 → 검증 → 서명 → 클라우드 게시까지 실행합니다.
- `UPDATE_FILTERS.bat`도 Windows 노트북용 게시 wrapper를 호출합니다.

게시 전에 노트북은 DPAPI 보호 개인키에서 공개키를 추출하고, 각 manifest의 RSA-SHA256 detached signature와 참조 필터의 SHA-256을 다시 검증합니다. 공개 브랜치에는 필터 원문, manifest, 버전별 `.sig`, 공개키 파일만 올라갑니다. 개인 서명키, `.env`, 라이선스 DB, `sources.json`, 로그는 게시 대상이 아니며 게시 스크립트가 허용 파일 경로와 파일 크기를 검사합니다.

정적 CDN은 사용자 지정 `X-Guardian-Signature` 응답 헤더를 제공하지 않으므로 Android 앱은 manifest 응답 헤더가 없을 때 `sourceId.sourceVersion.generatedAt.sig` sidecar를 같은 HTTPS 디렉터리에서 받아옵니다. 앱은 고정된 RSA 공개키로 manifest 서명을 검증한 다음 필터 SHA-256을 검증합니다. 현재 manifest의 CDN 캐시 지연은 GitHub raw 요청의 cache-busting query로 줄이고, 버전 고정 필터와 서명 파일은 immutable 경로를 사용합니다.

GitHub 정적 원본은 공개 저장소의 공개 파일입니다. 필터 목록이 공개되어도 괜찮다는 전제에서만 사용하세요. 라이선스 API는 계속 노트북에서 실행하고 Tailscale Serve를 통해 tailnet 내부에만 공개합니다. 필터 업데이트 다운로드는 노트북이 꺼져 있어도 가능하지만 새 필터 수집·서명·게시 작업은 노트북이 켜져 있어야 합니다.

`.github/workflows/filter-cloud-origin.yml`은 `guardian-filter-data` 브랜치에 게시된 각 manifest의 RSA 서명, SHA-256, 파일 경로와 공개 파일 허용 목록을 자동 검증합니다. 실제 클라우드 최초 게시 및 모바일 실기기 다운로드 검증은 노트북의 DPAPI 키와 GitHub 쓰기 인증으로 게시한 후 별도로 확인해야 합니다.
