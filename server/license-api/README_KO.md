# Guardian License API · 운영 준비 서버

Node.js 22 + PostgreSQL 기반의 라이선스 발급/기기 활성화 API와 모바일 관리자 웹 화면입니다.

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
- 고객지원 기능: Android FAQ/문의 접수/비밀 조회 코드 기반 답변 확인과 관리자 문의함/답변 처리를 제공합니다. 문의는 별도 진단 정보 없이 텍스트로 접수합니다.
- 라이선스 기간 프리셋: 7/30/90/365일 및 custom 기간을 화면과 서버에서 검증하고 발급 이벤트에 상품 코드를 기록합니다. Lifetime과 결제 처리는 아직 지원하지 않습니다.

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

`schema.sql`은 빈 데이터 볼륨 최초 생성 시에만 자동 실행됩니다. 기존 DB에는 배포 전에 migration 002(공유 rate-limit), 003(고객지원 티켓), 004(고객 디렉터리), 005(활성화 요청 멱등성)를 순서대로 적용해야 합니다. Windows 운영 스크립트 `windows/Apply-GuardianLicenseMigrations.ps1`는 백업 후 002~005를 순서대로 적용하고 필수 테이블을 검증합니다. 이후 스키마 변경도 별도 migration으로 적용해야 합니다. 실제 결제 연동, 운영 도메인, TLS 인증서, Secret Manager, 백업 복구 리허설 및 다중 운영자 권한은 배포 환경에서 별도 구성해야 합니다.

## API
- `POST /v1/licenses/activate`: `{ "requestId": "<UUID v4>", "licenseKey": "...", "deviceBinding": "<64 hex>", "appVersion": "..." }`. 모바일 앱은 요청 ID를 로컬에 먼저 영속화하고, 서명 토큰 저장이 성공할 때까지 재시도에 같은 ID를 사용합니다. 구형 클라이언트는 requestId 생략이 가능하지만 응답 유실 복구 멱등성은 제공되지 않습니다.
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

활성화/갱신 성공 시 `payload`, `signature`, `expiresIn`을 반환합니다. 서버는 `Bearer` 인증 스킴을 엄격히 요구하며, `expiresIn`은 서명된 `expiresAt - issuedAt`과 일치합니다. Android 앱에는 개인키가 아닌 동일 RSA 키쌍의 X.509 공개키를 `GUARDIAN_LICENSE_PUBLIC_KEY` 빌드 환경 변수로 주입해야 합니다. 앱은 서명, 상품, 권한, 만료 시각, 설치별 deviceBinding을 검증합니다.

## 고객 디렉터리 DB 마이그레이션
기존 데이터베이스에는 `migrations/004_customers.sql` 및 `migrations/005_activation_idempotency.sql`을 각각 한 번 적용해야 합니다. 이 migration은 customers 테이블과 선택적 license.customer_id/order_ref 컬럼 및 인덱스를 추가하며 기존 라이선스·키 해시·활성화 기록을 삭제하거나 변경하지 않습니다. 적용 전 백업을 만들고, 적용 후 `/ready`와 관리자 고객 목록 API를 검증합니다.

## 라이선스 상품 및 기간 검증
관리자 발급 화면은 다음 고정 상품과 직접 입력(custom)을 제공합니다.

| 상품 코드 | 유효 기간 |
|---|---:|
| short_7d | 7일 |
| monthly_30d | 30일 |
| quarterly_90d | 90일 |
| annual_365d | 365일 |
| custom | 1~36500일 직접 입력 |

고정 상품을 선택하면 기간 입력을 잠그고, API도 `planCode`와 `validDays` 조합을 다시 검사합니다. 기간이 다르거나 알 수 없는 상품 코드(예: Lifetime)는 HTTP 400으로 거부합니다. 발급 감사 이벤트에는 선택 상품 코드가 저장됩니다. 상품별 가격, 결제 웹훅, 자동 갱신 및 단기권 중복 발급 방지는 아직 결제 시스템과 별도이며, 이 API의 수동 발급만으로 결제 완료를 뜻하지 않습니다. Lifetime은 무기한 entitlement와 갱신·정지 모델을 구현하기 전까지 발급할 수 없습니다.

## 고객지원 센터와 문의함
- `POST /v1/support/tickets`: 질문, 오류 신고, 기능 제안, 결제/라이선스 문의 접수
- `POST /v1/support/tickets/lookup`: 접수 번호와 1회 표시되는 조회 코드를 사용해 본인 문의 및 답변 확인
- `GET /v1/admin/support/tickets`: 관리자 인증 후 최근 문의 최대 300건 조회
- `POST /v1/admin/support/tickets/{uuid}/reply`: 관리자 답변 및 처리 상태 저장
- 조회 코드는 256비트 난수이며 원문을 DB에 저장하지 않고 SHA-256 해시만 저장합니다. 고객 문의와 선택 이메일은 답변 처리를 위해 서버 DB에 저장됩니다. 진단 정보는 고객이 별도로 선택·동의하지 않는 한 자동 첨부하지 않습니다.
- 이메일 자동 발송은 아직 구현되지 않았습니다. 입력 이메일은 관리자 문의함에 표시되는 참고 연락처이며, 고객은 접수 번호와 조회 코드로 앱에서 답변을 직접 조회합니다.
- 공개 접수, 조회 및 관리자 경로는 서버 IP 기준 rate limit을 적용합니다. 관리자 답변 경로는 ticket ID별로 제한을 우회할 수 없도록 하나의 공유 bucket으로 묶습니다.

## 기존 운영 DB의 고객지원 migration
기존 노트북 운영 DB에는 migration `003_support_tickets.sql`을 적용해야 합니다. PowerShell 7.2에서 저장소의 `server/license-api/windows` 폴더로 이동한 뒤 다음을 실행합니다.

```powershell
./Apply-GuardianLicenseMigrations.ps1
```

이 스크립트는 PostgreSQL 준비 상태 확인 → 외부 데이터 경로에 백업 → migration 002 및 003 순차 적용 → 필수 테이블 확인 → API 이미지 재빌드 → `/ready` 확인을 수행합니다. 002/003은 재실행 가능한 SQL로 작성되어 있습니다. 이미 migration 004를 적용한 운영 DB도 기존 데이터와 키를 삭제하거나 초기화하지 않고 002/003을 추가 적용할 수 있습니다. 실행 실패 시 백업 파일을 보존하고 오류를 확인하십시오.

## 보안 및 출시 전 남은 작업
- API 인스턴스 간 rate limiting은 PostgreSQL 공유 카운터로 구현했지만, 프록시/WAF의 신뢰 IP 기반 방어와 DDoS 완화는 별도 구성해야 합니다. 관리자 토큰은 Bearer 방식이며 브라우저 메모리에만 보관합니다. 공개 운영 전 MFA 또는 SSO, 관리자별 RBAC, 토큰 교체·폐기 절차, 접근 감사, 비밀값 저장소, WAF 및 침투 테스트를 추가해야 합니다. 관리자 다중 사용자/RBAC, 이메일/구매 복구, Google Play Billing 및 결제 webhook, 자동 환불/차지백 동기화도 공개 전 별도 구현이 필요합니다.
- status/refresh는 deviceBinding만으로 해당 기기의 활성 라이선스를 조회합니다. 설치 UUID 재설치 시 새 기기 취급되며 관리자 초기화 정책을 따라야 합니다.
- 기기 비활성화/라이선스 정지는 서버의 활성화 슬롯과 이후 refresh를 즉시 차단하지만, 이미 발급된 오프라인 서명 토큰을 원격에서 소급 폐기할 수는 없습니다. 기존 토큰은 서명된 expiresAt까지(최대 24시간) 검증될 수 있으므로 즉시 원격 차단이 필요한 제품 정책에는 더 짧은 토큰 TTL 또는 온라인 재검증이 필요합니다.
- DB 스키마는 신규 설치용 초기 스키마입니다. 기존 DB에 적용할 경우 별도 migration 검토가 필요합니다.
- 운영 RSA 개인키와 관리자/HMAC 비밀값, DATABASE_URL은 절대 저장소/앱/채팅에 커밋하지 않습니다.
- 실제 결제 연결 전에는 수동 발급용 관리 도구이며, 실제 유료 판매/결제 완료를 주장하지 않습니다.


## 활성화 요청 멱등성 migration
기존 DB에는 `migrations/005_activation_idempotency.sql`을 한 번 적용해야 합니다. 이 테이블은 UUID 요청 ID, 기기 바인딩, 라이선스 키 HMAC 조회값 기반 fingerprint, 최초 서명 응답을 저장합니다. 동일 요청 재전송에는 저장된 동일 응답을 반환하며 요청 ID의 다른 기기/키 재사용은 거부합니다. 응답 본문에는 기기 바인딩된 서명 entitlement가 포함되므로 DB 접근 권한과 백업 접근을 제한하고 TLS 및 DB 암호화를 유지해야 합니다. 오래된 요청 레코드 정리 정책은 현재 미구현이며, 운영 적용 전 보존 기간·정리 작업을 정해야 합니다.
