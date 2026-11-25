# Guardian License API · 운영 준비 서버

Node.js 22 + PostgreSQL 기반의 라이선스 발급/기기 활성화 API와 반응형 관리자 웹 화면입니다. Windows와 Android는 동일한 `guardian-pro` 제품, 라이선스 DB, RSA 서명 공개키 체계를 공유합니다.

## 포함 기능
- 관리자 인증이 필요한 Pro 라이선스 발급, 최근 200건 조회, 고객명·이메일 기반 고객 디렉터리(최대 200명), 최근 100건 감사 기록 조회, 정지/활성 기기 해제
- 라이선스 키는 화면에 발급 순간 한 번만 노출합니다. DB에는 임의 salt 기반 scrypt 해시와 서버 HMAC 조회 인덱스만 저장합니다.
- 기기별 활성화 수 제한, 동일 기기 재활성화, 만료/정지 라이선스 거부
- 앱 검증기와 호환되는 정확한 UTF-8 key=value payload 및 RSA-SHA256 PKCS#1 v1.5 서명 토큰
- 서명된 오프라인 entitlement는 라이선스 만료 시각과 최대 24시간 중 더 이른 시각에 만료되며, 응답의 `expiresIn`은 서명 payload의 실제 남은 수명과 일치합니다. 서버 재접속 시 refresh로 갱신
- 감사 이벤트, 파라미터 바인딩 SQL, 16KB 요청 제한, no-store 응답
- HTTP 헤더 10초/요청 15초/keep-alive 5초/최대 헤더 32개 제한, JSON 응답 `X-Content-Type-Options: nosniff`
- PostgreSQL 연결 5초, idle 연결 30초, statement 10초, query 12초, idle-in-transaction 15초 timeout
- API JSON 본문은 최상위 객체만 허용하며, `null`·배열·문자열·숫자 등 잘못된 본문 형태는 HTTP 400으로 거부
- 라이선스 발급/활성화/갱신/상태 및 관리자 기기 관리 API에 PostgreSQL 공유 요청 제한과 `429 Retry-After` 응답. 동일 DB를 사용하는 다중 API 인스턴스에서도 요청 카운터를 공유
- 반응형 라이선스 관리자 대시보드: `/admin`. 전체/사용 가능/만료/정지 요약, 라이선스 검색·상태 필터·정렬, 발급·정지, 기기별 조회·해제, 최근 감사 기록을 제공합니다. 관리자 토큰은 페이지 메모리에서만 유지하고 브라우저 저장소에 저장하지 않습니다.

## 환경 변수
- `DATABASE_URL`: PostgreSQL 접속 URL
- `GUARDIAN_ADMIN_TOKEN`: 최소 32자 이상의 무작위 관리자 bearer token
- `GUARDIAN_LICENSE_LOOKUP_SECRET`: 최소 32자 이상의 별도 무작위 HMAC 비밀값
- `GUARDIAN_LICENSE_PRIVATE_KEY`: RSA 2048비트 이상 개인키 PEM. Secret Manager에서 주입하고 Git/앱에 넣지 않습니다. 환경 변수의 줄바꿈은 `\n` 형태를 지원합니다.
- `PORT`: 기본 8787
- `PGSSLMODE=require`: PostgreSQL TLS 사용 시 설정

## 초기 구동
```sh
cd server/license-api
npm install
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f schema.sql
npm test
npm start
```

운영에서는 반드시 HTTPS reverse proxy 또는 HTTPS를 제공하는 호스팅 플랫폼 뒤에 배치하고, 관리자 페이지도 HTTPS로만 접근시켜야 합니다. API의 rate limit 카운터는 PostgreSQL에 저장되어 동일 DB를 사용하는 여러 API 인스턴스 사이에서 공유됩니다. 프록시 뒤에서는 소켓 peer 기준이 프록시 주소가 될 수 있으므로, 공개 운영 환경에서는 프록시/WAF에서 신뢰 가능한 실제 클라이언트 IP 기준 추가 분산 rate limit을 반드시 적용해야 합니다. `GUARDIAN_RATE_LIMIT_WINDOW_MS`(기본 60000)와 `GUARDIAN_RATE_LIMIT_MAX`(기본 30)로 앱 내부 창과 요청 수를 조정할 수 있습니다. 데이터베이스 백업, Secret Manager, 로그 접근통제, 분산 rate limit/WAF, 모니터링, 복구 계획을 추가로 구성한 후 공개해야 합니다. 이 저장소의 서버는 운영 호스팅에 배포된 상태가 아닙니다.

## Docker Compose 배포 준비

이 저장소에는 API와 PostgreSQL을 함께 구동하는 컨테이너 구성이 포함되어 있습니다. 실제 공개 배포가 아니라 **운영 환경에 올리기 전 자체 서버에서 검증하는 배포 템플릿**입니다.

1. Docker Engine과 Docker Compose 플러그인을 설치합니다.
2. 이 디렉터리에서 `.env.example`을 `.env`로 복사합니다.
3. `.env`의 모든 `REPLACE_...` 값을 서로 다른 무작위 비밀값으로 교체합니다. PostgreSQL 비밀번호는 URL 인코딩 문제가 없도록 영문·숫자만 사용합니다.
4. RSA 개인키를 생성하고 PEM 전체를 `GUARDIAN_LICENSE_PRIVATE_KEY`에 `\n` 줄바꿈 형태로 넣습니다. 개인키는 이 서버의 Secret Manager 또는 보호된 환경 파일에만 두고 Git에 커밋하지 않습니다.
5. `docker compose up -d --build`로 시작한 뒤 `docker compose ps`와 `curl http://127.0.0.1:8787/health`는 프로세스 생존, `curl http://127.0.0.1:8787/ready`는 DB·서명키까지 포함한 준비 상태를 확인합니다.

RSA 키 생성 예:
```sh
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out guardian-license-private.pem
openssl pkey -in guardian-license-private.pem -pubout -out guardian-license-public.pem
```

공개키는 앱 빌드 시 `GUARDIAN_LICENSE_PUBLIC_KEY`에 주입하고, 서버 주소는 HTTPS 도메인으로 `GUARDIAN_LICENSE_API_BASE_URL`에 주입합니다. 개인키를 앱에 포함하지 않습니다. Compose는 API 포트를 `127.0.0.1:8787`에만 노출하므로 외부 공개 전 HTTPS reverse proxy/WAF를 별도로 구성해야 합니다. 프록시에서 실제 클라이언트 IP 기준 분산 rate limit을 설정합니다.

데이터베이스 백업 예:
```sh
docker compose exec -T db pg_dump -U guardian guardian > guardian-license-backup.sql
```

`schema.sql`은 빈 데이터 볼륨 최초 생성 시에만 자동 실행됩니다. 기존 Windows 노트북 운영 DB에는 새 API 배포 전에 반드시 migration을 적용해야 합니다.

PowerShell 7.2에서 저장소의 `server/license-api/windows` 폴더에서 다음 스크립트를 실행합니다.

```powershell
./Apply-GuardianLicenseMigrations.ps1
```

이 스크립트는 PostgreSQL 준비 상태 확인 → 외부 보호 경로에 DB 백업 생성 → `002_shared_rate_limit_buckets.sql`, `003_support_tickets.sql`, `004_customers.sql` 순차 적용 → 필수 테이블 존재 확인 → 최신 API 이미지 재빌드 → `/ready` 확인까지 수행합니다. SQL migration은 재실행해도 안전하도록 `IF NOT EXISTS`를 사용합니다. 자동 스크립트를 사용할 수 없는 환경에서는 `docker compose exec -T db psql -v ON_ERROR_STOP=1 -U guardian -d guardian`에 migration SQL을 순서대로 전달할 수 있지만, 반드시 먼저 백업하십시오. migration 실패 시 백업을 보존하고 원인을 확인한 뒤 재시도하며, 운영 DB를 임의로 삭제하거나 초기화하지 마십시오.

실제 결제 연동, 운영 도메인, TLS 인증서, Secret Manager, 백업 복구 리허설 및 다중 운영자 권한은 배포 환경에서 별도 구성해야 합니다.

## 무료 운영 구성(Neon Functions + Neon PostgreSQL)

라이선스 API의 기본 무료 운영 대상은 Neon Functions(Node.js 24)와 같은 Neon 프로젝트의 PostgreSQL입니다. 노트북이나 별도 상시 서버가 필요하지 않으며, 기존 Node.js API 로직·DB 스키마·RSA 서명 형식을 그대로 공유합니다. 함수는 `functions/guardianapi/index.mjs`에서 Fetch 요청을 받아 기존 API 런타임으로 전달합니다. 관리자 화면과 ES module 파일도 함수에 번들되어 `/admin`, `/admin.js`, `/admin-model.mjs`로 제공합니다.

### 무료 사용량과 한계

- Neon Free PostgreSQL: 프로젝트당 1GB 저장 공간, 월 100 CU-hours, 5GB 네트워크 전송량. 5분 비활성 후 DB 컴퓨트가 절전되므로 첫 요청은 지연될 수 있습니다.
- Neon Functions Free: 월 1,000,000회 호출, 활성 컴퓨트 10시간, 대기 컴퓨트 400시간. 함수는 Node.js 24 런타임으로 실행됩니다.
- Functions 사용 가능 리전은 제한되어 있습니다. 한국 사용자를 위해 지원 리전 중 가까운 AWS Asia Pacific (Singapore, `aws-ap-southeast-1`)을 선택합니다.
- 무료 한도 초과 시 과금으로 자동 전환하지 않고, 해당 리소스가 한도에 도달해 일시 중단될 수 있습니다. 무료 플랜은 상시 DB 컴퓨트, SLA, 완전한 운영 백업 체계를 보장하지 않습니다.
- 공개 유료 서비스로 확대하기 전에는 별도 백업/복구 리허설, 사용량 감시, 장애 대응, 관리자 MFA/RBAC, 보안 테스트를 완료해야 합니다. 무료 구성은 초기 소규모 운영 기준입니다.

### 최초 구성 및 배포

1. Neon 계정에서 Singapore 리전에 Free 프로젝트를 생성하고 PostgreSQL을 활성화합니다. 유료 플랜이나 결제 수단을 전제로 하지 않습니다.
2. 로컬에서 이 디렉터리로 이동해 Neon CLI에 로그인하고 프로젝트를 연결합니다.
   ```sh
   npx neon@latest link
   npx neon@latest env pull --file .env
   ```
   로그인/프로젝트 선택은 Neon 계정 소유자 승인이 필요합니다. `.env`는 로컬 비밀정보 파일이므로 Git에 커밋하지 않습니다.
3. Neon SQL Editor 또는 `psql`의 `DATABASE_URL_UNPOOLED` 연결로 신규 프로젝트의 `schema.sql`을 실행하고 migration `002_shared_rate_limit_buckets.sql`부터 `005_shared_guardian_product.sql`까지 순서대로 적용합니다. 기존 DB에 적용할 때는 먼저 백업/스냅샷을 확보하고 이미 적용된 migration을 재실행하지 않습니다.
4. RSA 3072비트 개인키와 공개키를 생성합니다.
   ```sh
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out guardian-license-private.pem
   openssl pkey -in guardian-license-private.pem -pubout -out guardian-license-public.pem
   ```
   개인키는 안전한 로컬 비밀정보 파일에만 두고 GitHub, 앱, 저장소에 커밋하지 않습니다.
5. 독립적인 관리자 토큰과 HMAC 비밀값을 생성해 `.env`의 `GUARDIAN_ADMIN_TOKEN`, `GUARDIAN_LICENSE_LOOKUP_SECRET`에 넣고, 개인키 PEM 전체를 `GUARDIAN_LICENSE_PRIVATE_KEY`에 넣습니다. `neon.ts`는 세 필수 값이 없으면 배포를 거부하도록 설정되어 있습니다.
   ```sh
   node -e "console.log(require('node:crypto').randomBytes(48).toString('base64url'))"
   ```
6. 테스트 후 Neon Functions를 배포합니다.
   ```sh
   npm install
   npm test
   npm run neon:deploy
   npx neon@latest functions get guardianapi
   ```
   배포 결과의 `invocation_url`이 실제 API HTTPS 주소입니다. 배포 후 `/health`와 `/ready`가 모두 HTTP 200인지 확인합니다. `/ready`는 DB 스키마와 RSA 서명키까지 검사합니다.
7. Android 빌드에 `GUARDIAN_LICENSE_API_BASE_URL`을 발급된 함수 URL로, `GUARDIAN_LICENSE_PUBLIC_KEY`를 RSA 공개키로 설정합니다. 앱에는 공개키만 포함하며 개인키는 절대 포함하지 않습니다.
8. 관리자 화면은 같은 주소의 `/admin`에서 사용합니다. 관리자 토큰은 안전하게 보관하고 브라우저 저장소에 영구 저장하지 않습니다.

### 보안·배포 원칙

- 함수 런타임의 DB 주소는 Neon이 주입하는 `DATABASE_URL_UNPOOLED`(트랜잭션 사용) 또는 `DATABASE_URL`을 사용합니다. DB 주소와 RSA 개인키, 관리자 토큰, HMAC 비밀값을 소스에 저장하지 않습니다.
- GitHub Actions는 단위/통합 테스트만 수행하며 운영 Neon 프로젝트에 자동 배포하거나 운영 DB를 변경하지 않습니다.
- DB 연결, 서명키, 관리자 인증 설정이 없거나 무료 사용량이 소진되면 발급/갱신은 실패 처리합니다. 검증 우회나 임의 PRO 권한 부여는 하지 않습니다.
- Neon 계정 연결, 프로젝트 생성 및 최초 배포는 소유자 계정의 인증이 필요합니다. 저장소만으로 외부 계정을 만들거나 비밀값을 추정해 배포하지 않습니다.

## API
- `POST /v1/licenses/activate`: `{ "licenseKey": "...", "deviceBinding": "<64 hex>", "appVersion": "..." }`
- `POST /v1/licenses/refresh`: `{ "deviceBinding": "<64 hex>", "licenseRef": "..." }`
- `POST /v1/licenses/deactivate`: `{ "deviceBinding": "<64 hex>", "licenseRef": "..." }`
- `POST /v1/licenses/status`: `{ "deviceBinding": "<64 hex>", "licenseRef": "..." }`
- `GET /v1/admin/licenses`: `Authorization: Bearer <GUARDIAN_ADMIN_TOKEN>`
- `GET /v1/admin/events`: 최근 발급/활성화/비활성화/정지 감사 기록 100건
- `POST /v1/admin/licenses`: 관리자 인증 + `{ "validDays": 365, "maxDevices": 1 }`
- `POST /v1/admin/licenses/{uuid}/revoke`: `Authorization: Bearer <GUARDIAN_ADMIN_TOKEN>` 및 UUID 형식의 라이선스 ID 필요
- `GET /v1/admin/licenses/{uuid}/activations`: 라이선스의 최근 활성화 기록 최대 50건 조회. 기기 식별자는 해시 마지막 8자리만 반환
- `POST /v1/admin/licenses/{uuid}/activations/{activationUuid}/deactivate`: 지정한 기기 등록만 해제하고 감사 이벤트 기록. 다른 기기와 라이선스 상태는 유지
- `/admin`: 라이선스 발급·정지, 기기별 최근 확인 정보 조회 및 개별 기기 해제. 고객명·이메일·선택 주문번호를 발급 시 연결하고 고객별 라이선스 수 및 사용 가능 건수를 조회합니다. 고객 목록은 관리자 인증이 필요합니다.
- `GET /v1/admin/customers?q=<검색어>`: 관리자 인증 후 고객명/이메일 검색, 고객별 전체 라이선스 수·사용 가능 라이선스 수·최근 발급일 조회(최대 200명)
- 기존 고객 정보가 없는 과거 라이선스는 자동으로 고객을 추정하지 않습니다. 마이그레이션 004 이후 신규 발급부터 고객 연결이 저장됩니다.
- `GET /health`: 프로세스 생존 상태
- `GET /ready`: DB 연결, 관리자 토큰, HMAC 비밀값 및 RSA 2048비트 이상 서명키 준비 상태 확인. Docker health check에서 사용

활성화/갱신 성공 시 `payload`, `signature`, `expiresIn`을 반환합니다. 서버는 `Bearer` 인증 스킴을 엄격히 요구하며, `expiresIn`은 서명된 `expiresAt - issuedAt`과 일치합니다. Windows와 Android 클라이언트에는 같은 RSA 키쌍의 X.509 공개키를 배포하고 개인키는 API 서버에만 보관합니다. 신규 토큰의 제품 claim은 플랫폼 중립적인 `guardian`이며, Android 검증기는 기존 `guardian-android` 토큰도 호환합니다. 각 클라이언트는 서명, 상품, 권한, 만료 시각, 설치별 deviceBinding을 검증해야 합니다.

## 고객 디렉터리 DB 마이그레이션
기존 데이터베이스에는 `migrations/004_customers.sql` 및 `migrations/005_shared_guardian_product.sql`을 한 번 적용해야 합니다. 005는 공통 `guardian-pro` 제품 레코드를 추가하며 기존 Android 라이선스와 제품 레코드를 삭제하거나 변환하지 않습니다. 이 migration은 customers 테이블과 선택적 license.customer_id/order_ref 컬럼 및 인덱스를 추가하며 기존 라이선스·키 해시·활성화 기록을 삭제하거나 변경하지 않습니다. 적용 전 백업을 만들고, 적용 후 `/ready`와 관리자 고객 목록 API를 검증합니다.

## 보안 및 출시 전 남은 작업
- API 인스턴스 간 rate limiting은 PostgreSQL 공유 카운터로 구현했지만, 프록시/WAF의 신뢰 IP 기반 방어와 DDoS 완화는 별도 구성해야 합니다. 관리자 토큰은 Bearer 방식이며 브라우저 메모리에만 보관합니다. 공개 운영 전 MFA 또는 SSO, 관리자별 RBAC, 토큰 교체·폐기 절차, 접근 감사, 비밀값 저장소, WAF 및 침투 테스트를 추가해야 합니다. 관리자 다중 사용자/RBAC, 이메일/구매 복구, Google Play Billing 및 결제 webhook, 자동 환불/차지백 동기화도 공개 전 별도 구현이 필요합니다.
- status/refresh는 deviceBinding만으로 해당 기기의 활성 라이선스를 조회합니다. 설치 UUID 재설치 시 새 기기 취급되며 관리자 초기화 정책을 따라야 합니다.
- 기기 비활성화/라이선스 정지는 서버의 활성화 슬롯과 이후 refresh를 즉시 차단하지만, 이미 발급된 오프라인 서명 토큰을 원격에서 소급 폐기할 수는 없습니다. 기존 토큰은 서명된 expiresAt까지(최대 24시간) 검증될 수 있으므로 즉시 원격 차단이 필요한 제품 정책에는 더 짧은 토큰 TTL 또는 온라인 재검증이 필요합니다.
- DB 스키마는 신규 설치용 초기 스키마입니다. 기존 DB에 적용할 경우 별도 migration 검토가 필요합니다.
- 운영 RSA 개인키와 관리자/HMAC 비밀값, DATABASE_URL은 절대 저장소/앱/채팅에 커밋하지 않습니다.
- 실제 결제 연결 전에는 수동 발급용 관리 도구이며, 실제 유료 판매/결제 완료를 주장하지 않습니다.
