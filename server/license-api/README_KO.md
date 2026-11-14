# Guardian License API · 운영 준비 서버

Node.js 22 + PostgreSQL 기반의 라이선스 발급/기기 활성화 API와 모바일 관리자 웹 화면입니다.

## 포함 기능
- 관리자 인증이 필요한 Pro 라이선스 발급, 최근 200건 조회, 최근 100건 감사 기록 조회, 정지/활성 기기 해제
- 라이선스 키는 화면에 발급 순간 한 번만 노출합니다. DB에는 임의 salt 기반 scrypt 해시와 서버 HMAC 조회 인덱스만 저장합니다.
- 기기별 활성화 수 제한, 동일 기기 재활성화, 만료/정지 라이선스 거부
- 앱 검증기와 호환되는 정확한 UTF-8 key=value payload 및 RSA-SHA256 PKCS#1 v1.5 서명 토큰
- 24시간 수명 토큰, 서버 재접속 시 refresh로 갱신
- 감사 이벤트, 파라미터 바인딩 SQL, 16KB 요청 제한, no-store 응답
- 라이선스 발급/활성화/갱신/상태 및 정지 API에 프로세스 단위 요청 제한과 `429 Retry-After` 응답
- 반응형 모바일 관리자 페이지: `/admin`. 관리자 토큰은 페이지 메모리에서만 유지하고 localStorage/sessionStorage에 저장하지 않습니다.

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

운영에서는 반드시 HTTPS reverse proxy 또는 HTTPS를 제공하는 호스팅 플랫폼 뒤에 배치하고, 관리자 페이지도 HTTPS로만 접근시켜야 합니다. 앱 내부 제한은 단일 프로세스의 보조 방어이며 재시작·다중 인스턴스 사이에 공유되지 않습니다. 프록시 뒤에서는 소켓 peer 기준이 프록시 주소가 될 수 있으므로, 공개 운영 환경에서는 프록시/WAF에서 실제 클라이언트 IP 기준 분산 rate limit을 반드시 적용해야 합니다. `GUARDIAN_RATE_LIMIT_WINDOW_MS`(기본 60000)와 `GUARDIAN_RATE_LIMIT_MAX`(기본 30)로 앱 내부 창과 요청 수를 조정할 수 있습니다. 데이터베이스 백업, Secret Manager, 로그 접근통제, 분산 rate limit/WAF, 모니터링, 복구 계획을 추가로 구성한 후 공개해야 합니다. 이 저장소의 서버는 운영 호스팅에 배포된 상태가 아닙니다.

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

`schema.sql`은 빈 데이터 볼륨 최초 생성 시에만 자동 실행됩니다. 이후 스키마 변경은 별도 migration으로 적용해야 합니다. 실제 결제 연동, 운영 도메인, TLS 인증서, Secret Manager, 백업 복구 리허설 및 다중 운영자 권한은 배포 환경에서 별도 구성해야 합니다.

## API
- `POST /v1/licenses/activate`: `{ "licenseKey": "...", "deviceBinding": "<64 hex>", "appVersion": "..." }`
- `POST /v1/licenses/refresh`: `{ "deviceBinding": "<64 hex>", "licenseRef": "..." }`
- `POST /v1/licenses/deactivate`: `{ "deviceBinding": "<64 hex>", "licenseRef": "..." }`
- `POST /v1/licenses/status`: `{ "deviceBinding": "<64 hex>", "licenseRef": "..." }`
- `GET /v1/admin/licenses`: `Authorization: Bearer <GUARDIAN_ADMIN_TOKEN>`
- `GET /v1/admin/events`: 최근 발급/활성화/비활성화/정지 감사 기록 100건
- `POST /v1/admin/licenses`: 관리자 인증 + `{ "validDays": 365, "maxDevices": 1 }`
- `POST /v1/admin/licenses/{uuid}/revoke`: 관리자 인증
- `GET /health`: 프로세스 생존 상태
- `GET /ready`: DB 연결, 관리자 토큰, HMAC 비밀값 및 RSA 2048비트 이상 서명키 준비 상태 확인. Docker health check에서 사용

활성화/갱신 성공 시 `payload`와 `signature`를 반환합니다. Android 앱에는 개인키가 아닌 동일 RSA 키쌍의 X.509 공개키를 `GUARDIAN_LICENSE_PUBLIC_KEY` 빌드 환경 변수로 주입해야 합니다. 앱은 서명, 상품, 권한, 만료 시각, 설치별 deviceBinding을 검증합니다.

## 보안 및 출시 전 남은 작업
- 이 첫 서버 버전은 운영용 rate limiting, 관리자 다중 사용자/RBAC, 이메일/구매 복구, Google Play Billing 및 결제 webhook, 자동 환불/차지백 동기화가 없습니다. 공개 전 반드시 rate limiting/WAF 및 운영자 접근 정책을 보강해야 합니다.
- status/refresh는 deviceBinding만으로 해당 기기의 활성 라이선스를 조회합니다. 설치 UUID 재설치 시 새 기기 취급되며 관리자 초기화 정책을 따라야 합니다.
- DB 스키마는 신규 설치용 초기 스키마입니다. 기존 DB에 적용할 경우 별도 migration 검토가 필요합니다.
- 운영 RSA 개인키와 관리자/HMAC 비밀값, DATABASE_URL은 절대 저장소/앱/채팅에 커밋하지 않습니다.
- 실제 결제 연결 전에는 수동 발급용 관리 도구이며, 실제 유료 판매/결제 완료를 주장하지 않습니다.
