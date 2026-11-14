# Guardian Signed Filter Distribution

Node.js 22 기반의 서명 manifest 및 버전 고정 필터 배포 서버입니다. 앱의 `SignedManifestHttpClient`가 기대하는 `X-Guardian-Signature` 응답 헤더와 manifest 형식을 그대로 제공합니다.

## 구성

- `scripts/publish-manifest.mjs`: 오프라인 서명 환경에서 필터 파일의 SHA-256과 RSA-SHA256 detached signature를 생성합니다.
- `src/server.mjs`: manifest, signature header, 버전 고정 필터 파일을 읽기 전용으로 제공합니다.
- `docker-compose.yml`: 비루트 Node 컨테이너를 localhost에만 바인딩합니다.
- 개인키는 배포 서버 컨테이너에 넣지 않습니다. 공개키만 Android 앱 빌드에 포함합니다.

## 1. 서명키 준비 (오프라인 서명 PC)

RSA 3072비트 키를 생성합니다. Publisher는 RSA 2048비트 미만 키와 RSA 이외의 키 유형을 거부합니다. 개인키는 암호화된 이동식 저장소 또는 비밀 관리자에 보관하고 Git, 서버 컨테이너, Android APK, 채팅에 올리지 마세요.

```sh
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out guardian-filter-private.pem
```

Publisher는 PEM 개인키를 `GUARDIAN_FILTER_SIGNING_PRIVATE_KEY` 환경 변수로 받습니다. Linux/macOS 예:

```sh
export GUARDIAN_FILTER_SIGNING_PRIVATE_KEY="$(awk '{printf "%s\\n", $0}' guardian-filter-private.pem)"
```

## 2-A. 자동 업데이트 (upstream 다운로드 + 검증 + 서명)

수동 발행과 자동 수집을 모두 지원합니다. `sources.example.json`을 복사해 `sources.json`을 만들고, 신뢰할 upstream HTTPS 주소와 각 필터의 최소 규칙 수를 등록합니다. 예제에는 검증된 형식의 AdGuard DNS Filter 주소 1개만 넣었습니다. 나머지 필터 주소는 공급자 공식 배포처를 확인한 뒤 직접 추가하세요.

```sh
cp sources.example.json sources.json
export GUARDIAN_FILTER_SIGNING_PRIVATE_KEY="$(awk '{printf "%s\\n", $0}' guardian-filter-private.pem)"
npm run update -- ./sources.json
```

Updater는 HTTPS만 허용하고 redirect는 거부하며, 요청당 30초 timeout, 10 MiB 상한, UTF-8/NUL 검사, 최소 규칙 수 검사를 통과한 파일만 서명합니다. 버전은 UTC 날짜와 콘텐츠 SHA-256 앞 12자리 조합으로 생성하므로 같은 날짜의 변경도 별도 immutable 버전으로 발행됩니다. 실패한 upstream은 기존 manifest를 교체하지 않습니다.

정기 실행은 배포/서명 PC의 작업 스케줄러에 위 명령을 등록하면 됩니다. Windows에서는 `UPDATE_FILTERS.bat`을 작업 스케줄러에 등록해 매일 실행할 수 있습니다. 실행 계정의 환경 변수 `GUARDIAN_FILTER_SIGNING_PRIVATE_KEY`에 개인키를 안전하게 주입하세요. 배치 파일이나 작업 인수에 PEM/비밀번호를 직접 적지 마세요.

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

| 앱 필터 | sourceId | 최소 규칙 |
|---|---|---:|
| AdGuard DNS Filter | `adguard` | 1000 |
| 1Hosts Lite | `1hosts_lite` | 100 |
| OISD Big | `oisd_big` | 100 |
| HaGeZi Normal | `hagezi_normal` | 100 |
| HaGeZi Pro | `hagezi_pro` | 100 |
| 보안 필터 | `security` | 100 |
| Android 앱 광고 | `android_ads` | 100 |

각 sourceId/version에 대해 같은 버전의 다른 콘텐츠로 덮어쓰는 것을 publisher가 거부합니다. 새 필터는 반드시 새 sourceVersion으로 발행하세요. 동일 초 내 연속 발행도 anti-rollback 조건을 만족하도록 generatedAt을 증가시킵니다.

Publisher는 output directory 안에 source별 배타적 lock 파일(`.<sourceId>.publish.lock`)을 만들어 같은 source의 동시 발행을 거부합니다. 정상 종료 시 lock을 제거합니다. 프로세스 강제 종료로 lock이 남으면 해당 source의 발행 프로세스가 실행 중이지 않음을 확인한 뒤에만 수동 삭제하세요. 다른 발행 프로세스의 lock을 잘못 제거할 위험 때문에 자동 stale-lock 회수는 하지 않습니다.

Publisher가 출력하는 `publicKey` 또는 `guardian-filter-public-key.x509.b64` 내용은 X.509 SubjectPublicKeyInfo Base64 공개키입니다. 이 값만 앱 빌드의 `GUARDIAN_MANIFEST_PUBLIC_KEY`에 설정합니다. `GUARDIAN_MANIFEST_BASE_URL`에는 `https://filters.example.com`을 설정합니다. 공개키를 바꿀 때는 앱 업데이트와 키 교체 절차를 함께 계획하세요.

## 3. 배포 서버 시작

```sh
cp .env.example .env
mkdir -p dist
docker compose up -d --build
docker compose ps
curl -f http://127.0.0.1:8788/health
```

Compose는 API를 `127.0.0.1:8788`에만 바인딩합니다. 인터넷에 공개하기 전에 HTTPS reverse proxy/WAF를 구성하고 `filters.example.com` 도메인에 TLS 인증서를 연결하세요. manifest 경로는 `/{sourceId}.manifest`, 콘텐츠 경로는 `/filters/{sourceId}/{sourceVersion}.txt`입니다. reverse proxy가 이 경로와 `X-Guardian-Signature` 헤더를 변경하지 않도록 설정해야 합니다.

## 보안 및 운영

- 서명은 manifest 원문 UTF-8 바이트에 RSA-SHA256을 적용합니다. 서버는 manifest와 detached signature를 함께 읽고 signature를 `X-Guardian-Signature`로 전달합니다.
- manifest는 `no-store`, 버전 고정 필터 파일은 `immutable` 캐시 정책을 사용합니다.
- 새 버전 콘텐츠와 `sourceId.sourceVersion.generatedAt.sig` 형식의 버전별 signature를 먼저 기록하고 manifest를 마지막에 원자적으로 교체합니다. 이전 manifest는 이전 버전 signature를 계속 참조하므로 발행 중에도 signature/manifest 불일치 구간을 만들지 않습니다. manifest는 공개 배포의 commit marker입니다.
- 필터 파일 최대 10 MiB, manifest 최대 16 KiB, sourceId/version 경로는 허용 문자로 제한합니다.
- 서명 개인키는 오프라인 발행 환경에서만 사용합니다. 운영 웹 서버에는 개인키가 필요하지 않습니다.
- 필터 콘텐츠의 실제 DNS 규칙 수와 유효성은 앱의 parser가 다시 검사합니다. Publisher의 `minimumRules` 값은 앱이 검증할 기준값이며, 앱은 기준 미달 콘텐츠를 거부합니다.
- `dist/`, 개인키, 환경 파일은 저장소에 커밋하지 마세요. 배포 서버에는 서명 완료된 공개 산출물만 복사합니다.
- 서버 호스팅, 도메인, TLS 인증서, DNS, 백업 및 키 보관소는 운영자가 별도로 준비해야 합니다. 이 저장소는 실제 공개 서버를 자동 생성하거나 배포하지 않습니다.
