# Guardian 운영 서명키 구성 및 현황

기준 저장소: `justinhblee-prog/guardian`  
목표: Android 앱 서명, 필터 매니페스트 서명, PRO 라이선스 서명을 서로 독립된 신뢰 영역으로 운영한다.

## 1. 키 체계

| 구분 | 용도 | 개인키 보관 위치 | 공개키 배포 위치 | 현재 확인된 상태 |
|---|---|---|---|---|
| Android Release keystore | APK/AAB에 앱 개발자 서명. 기존 설치본 업데이트의 앱 서명자 신원을 유지 | 운영자 Windows의 Guardian 비밀 폴더 + GitHub Actions Secret | 앱 설치 파일에 인증서가 포함됨 | 생성·GitHub 등록을 수행하는 스크립트는 있음. 실제 keystore 존재 및 등록 여부는 원격에서 비밀값을 조회할 수 없어 미확인 |
| Filter manifest RSA | 필터 파일/매니페스트의 무결성 및 출처 검증 | 클라우드 게시 작업 전용 GitHub Actions Secret 권장 | Android 앱에 고정된 필터 공개키 및 GitHub Actions Variable | 로컬 생성·DPAPI 보관 코드 있음. GitHub 게시용 개인키 Secret 등록 경로는 별도 확인/완성이 필요 |
| PRO license RSA | 라이선스 entitlement 서명 및 앱의 오프라인 검증 | Neon Function의 `GUARDIAN_LICENSE_PRIVATE_KEY` Secret | Android 앱에 고정된 라이선스 공개키 및 GitHub Actions Variable | 로컬 초기화 스크립트는 개인키를 API .env에 저장. Neon에는 현재 `GUARDIAN_ADMIN_TOKEN`만 확인되며 개인키 미설정 |
| Admin bearer token | 라이선스 관리자 API 인증 | Neon Function Secret | 없음 | Neon 환경변수 이름 확인됨. 이전 노출 이력이 있는 토큰은 재사용하지 말고 회전해야 함 |
| License lookup HMAC secret | DB 라이선스 조회 인덱스용 HMAC | Neon Function Secret | 없음 | Neon 미설정 |
| PostgreSQL credential | DB 연결 인증 | Neon이 주입하는 `DATABASE_URL` | 없음 | Neon Function에서 자동 주입되는 구조 |

**중요:** Android keystore, 필터 RSA, 라이선스 RSA는 절대로 하나의 키로 합치지 않는다. 앱에 들어가는 것은 공개키뿐이다. 개인키·비밀번호·HMAC·관리자 토큰은 APK, 저장소, 로그, 일반 문서에 넣지 않는다.

## 2. Android 앱 서명키

GitHub Actions 운영 Release workflow가 사용하는 Secret 이름:

- `GUARDIAN_RELEASE_KEYSTORE_BASE64`
- `GUARDIAN_RELEASE_STORE_PASSWORD`
- `GUARDIAN_RELEASE_KEY_ALIAS`
- `GUARDIAN_RELEASE_KEY_PASSWORD`

운영자 로컬 보관 파일(현재 스크립트 기준):

- `%LOCALAPPDATA%\Guardian\secrets\guardian-release.jks`
- `guardian-release-store-password.dpapi`
- `guardian-release-key-password.dpapi`

준비 스크립트:

```powershell
pwsh -File .\server\license-api\windows\Prepare-GuardianProductionRelease.ps1 -SigningOnly
```

이 명령은 로컬 keystore가 없을 때 생성하고, 로컬에서 검증한 뒤 GitHub Secret에 등록한다. 이미 일부 keystore 파일이 존재하면 덮어쓰지 않고 중단한다. **기존 정식 APK가 배포된 경우 새 키를 만들지 말고, 반드시 기존 APK의 signer SHA-256과 기존 keystore의 인증서 지문을 먼저 대조한다.** 서명자가 바뀌면 일반적인 앱 업데이트가 불가능할 수 있다.

## 3. 필터 서명키

초기 생성 스크립트 `Setup-GuardianServer.ps1`는 RSA 3072 키쌍을 생성한다. 필터 개인키는 Windows DPAPI로 암호화한 `guardian-filter-private.dpapi`로 보관하고, 공개키는 PEM/DER/Base64 파일로 보관한다.

예상 로컬 경로:

- `%LOCALAPPDATA%\Guardian\secrets\guardian-filter-private.dpapi`
- `guardian-filter-public.x509.b64`

클라우드의 일일 게시 workflow가 사용할 Secret:

- `GUARDIAN_FILTER_SIGNING_PRIVATE_KEY`

공개키 변수:

- `GUARDIAN_MANIFEST_PUBLIC_KEY`

**현재 구성 차이:** 로컬 초기화는 필터 개인키를 DPAPI로 보관하지만, 일일 GitHub 게시 workflow가 요구하는 개인키 Secret을 이 초기화 흐름만으로 자동 등록한다고 확인되지 않았다. 이 연결을 별도로 안전하게 구성하기 전에는 매일 게시 workflow가 성공하더라도 실제 서명·게시가 완료된 것으로 간주하지 않는다. 기존 공개키에 대응하는 개인키를 확인하지 않은 채 새 키를 만들거나 공개키를 교체하지 않는다.

## 4. PRO 라이선스 서명키

서버가 사용하는 환경변수:

- `GUARDIAN_LICENSE_PRIVATE_KEY` — RSA PKCS#8 PEM 개인키
- `GUARDIAN_LICENSE_LOOKUP_SECRET` — 독립적인 무작위 HMAC 비밀값
- `GUARDIAN_ADMIN_TOKEN` — 독립적인 관리자 bearer token
- `DATABASE_URL` — Neon 자동 주입

클라이언트 공개키 변수/빌드 입력:

- `GUARDIAN_LICENSE_PUBLIC_KEY`

서명 개인키는 Neon Function Secret에만 둔다. GitHub에는 공개키만 둔다. Android에 개인키를 넣지 않는다.

2026-11-26 확인 결과 Neon Function `guardianapi`의 마지막 배포는 실패 상태이며, 환경변수 목록에는 `GUARDIAN_ADMIN_TOKEN`만 있었다. 실패 원인은 키 검증 이전의 번들 진입점 오류(`index.mjs`가 ZIP 루트에 없음)였다. 따라서 라이선스 운영 서명은 아직 서비스 상태가 아니다.


## 5. 로그인 및 권한 분리 정책

Guardian Android 앱은 일반 사용자 로그인을 요구하지 않는다. 앱 업데이트 확인은 공개 GitHub Release의 읽기 전용 메타데이터와 APK 자산을 사용하므로 계정/비밀번호 입력 없이 동작해야 한다.

| 사용자/기능 | 인증 정책 | 허용 범위 |
|---|---|---|
| 일반 사용자 앱 사용 | 앱 로그인 없음 | 보호 기능, 필터 설정, 안정판 업데이트 확인 |
| 개발판 검증자 | 앱 로그인 없음 | 개발 빌드에서만 개발 Release 확인 및 APK 검증 |
| 개발판/정식판 게시 | GitHub 계정 권한 및 보호된 Actions Secret | 승인된 저장소 운영자/워크플로만 Release 게시 |
| PRO 사용자 | 로그인 대신 라이선스 키 활성화 및 entitlement 검증 | 구매/발급된 PRO 권한 확인 |
| 라이선스 운영 관리자 | 서버 API의 별도 관리자 Bearer 인증 | 라이선스 발급·회수·운영 조회 등 허가된 관리 API |

운영 원칙:

- 개발/정식 업데이트 채널 선택은 빌드 유형과 배포 정책으로 결정한다. 업데이트 확인을 위해 Guardian 계정 로그인을 추가하지 않는다.
- 개발판은 내부 검증 빌드에서만 노출하고, 일반 정식판 사용자에게 개발판 Release를 제시하지 않는다.
- GitHub Release 게시 권한은 앱 내부 화면이나 앱 사용자 계정과 연결하지 않는다. GitHub 권한 및 저장소의 보호된 Secret/Environment에서 통제한다.
- PRO 라이선스 활성화는 관리자 로그인과 다르다. 사용자는 라이선스 키를 제출하고 서버가 서명된 entitlement를 반환하며, 앱은 내장 공개키로 검증한다.
- 관리자 Bearer token, HMAC secret, RSA 개인키는 Android 앱에 절대 포함하지 않는다. 관리자 작업은 별도 운영 도구/보안된 서버 측 호출로 제한한다.
- 향후 관리자 웹 콘솔이 필요해지면 별도 운영자 인증(개인별 계정, 최소 권한, 감사 로그, 토큰 회전)을 구축한다. 이를 일반 사용자 앱 로그인으로 대체하지 않는다.
- 인증이 필요한 관리자 API는 인증 누락/오류 시 fail-closed로 거부한다. 공개 업데이트 확인 API에는 관리자 토큰을 요구하지 않는다.

## 6. 등록 및 검증 순서

1. 기존 설치/배포 APK의 signer SHA-256과 기존 keystore 보유 여부를 확인한다. 일치하는 기존 운영 keystore가 없으면 앱 서명키 교체를 진행하지 않는다.
2. 필터 공개키가 Android 앱에 고정된 키와 일치하는지 확인하고, 그 공개키에 대응하는 개인키를 안전한 로컬 환경에서 검증한다.
3. 필터 개인키를 GitHub Actions Secret `GUARDIAN_FILTER_SIGNING_PRIVATE_KEY`로 등록하고, 공개키는 `GUARDIAN_MANIFEST_PUBLIC_KEY` 변수로 설정한다. 등록 후 테스트 서명/검증을 통과해야 한다.
4. 라이선스 개인키의 공개키 지문을 Android에 고정된 라이선스 공개키와 대조한다. 일치하는 경우에만 개인키를 Neon Function Secret에 등록한다.
5. Neon의 `GUARDIAN_LICENSE_LOOKUP_SECRET`과 `GUARDIAN_ADMIN_TOKEN`을 서로 독립적으로 생성·등록한다. 과거 노출된 관리자 토큰은 회전하고 폐기한다.
6. Android Release keystore의 네 GitHub Secrets를 한 묶음으로 등록하고, 서명된 Direct APK와 Play AAB를 생성한다.
7. 각 서명 산출물의 인증서 지문, 앱 버전, versionCode, SHA-256을 기록한다. 운영 배포는 별도 수동 승인 workflow에서만 실행한다.

## 7. 백업 및 회전 정책

- Android keystore와 두 RSA 개인키는 서로 다른 암호화 백업 세트로 보관한다. 백업 복구 가능 여부를 정기적으로 검증하되 개인키를 채팅/이메일/저장소로 전송하지 않는다.
- 키 파일은 Git 저장소 및 OneDrive 밖의 Guardian 비밀 폴더에 두고 Windows ACL을 적용한다. DPAPI 파일은 해당 Windows 사용자/기기 종속이므로, DPAPI 파일 하나만을 유일한 백업으로 간주하지 않는다.
- 키 회전은 새 키 생성 → 앱에 새 공개키를 포함한 호환 릴리스 → 서버/게시 측 전환 → 구키 만료 및 폐기 순서로 수행한다. 기존 앱이 새 공개키를 알기 전에 서버 키만 바꾸지 않는다.
- 키를 잃어버렸거나 노출이 의심되면 임의로 덮어쓰지 말고, 영향 범위와 기존 설치 앱의 업데이트 가능성을 먼저 평가한다.
- Secret 값 자체는 조회/출력하지 않는다. 설정 존재 여부, 키 지문 일치, 실제 서명·검증 결과만 보고한다.

## 8. 현재 운영 판정

- Android 개발용 debug APK 서명: CI 검증에 사용된 개발 서명과 운영 keystore는 별개다.
- Android 운영 서명: 준비 스크립트는 있으나 실제 Secret/기존 keystore 상태 미확인.
- 필터 운영 서명: 로컬 키 생성 흐름은 있으나 클라우드 게시 개인키 Secret 연결 미확인.
- PRO 라이선스 운영 서명: Neon 개인키/HMAC 미설정, Function 배포도 실패 상태.
- 필터 실게시/라이선스 실발급: 운영키 설정과 실제 end-to-end 검증 전까지 미완료로 유지한다.

이 문서는 키의 역할과 운영 절차만 기록한다. 실제 비밀값, 개인키, keystore, 비밀번호, 토큰, HMAC 값은 포함하지 않는다.
