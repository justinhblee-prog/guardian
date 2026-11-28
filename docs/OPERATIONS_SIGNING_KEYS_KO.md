# Guardian 운영 서명키 및 비밀값 구성

이 문서는 서로 다른 신뢰 영역의 키를 분리하고, 현재 확인된 운영 준비 상태를 기록합니다. **개인키·비밀번호·토큰 원문은 이 문서, Git 저장소, APK, CI 로그에 기록하지 않습니다.**

## 1. 키 종류와 보관 위치

| 구분 | 용도 | 개인키/비밀값 보관 위치 | 공개키 사용처 |
|---|---|---|---|
| Android 앱 배포 서명키 | 직접 설치 APK와 정식 배포 APK/AAB의 앱 업데이트 서명 | GitHub Actions Secrets의 Base64 JKS 및 별도 암호 Secret. 원본 JKS와 암호 백업은 오프라인 암호화 보관 | Android 패키지 서명 인증서. 공개 인증서/지문만 공유 |
| PRO 라이선스 서명키 | 라이선스 entitlement RSA-SHA256 서명·검증 | Neon Function 환경변수 `GUARDIAN_LICENSE_PRIVATE_KEY` | Android에 고정된 `GUARDIAN_LICENSE_PUBLIC_KEY`와 GitHub Repository Variable |
| 필터 manifest 서명키 | 필터 manifest와 파일 해시의 RSA 서명·검증 | 현재 설계상 Windows 운영 데이터 경로의 DPAPI 보호 개인키. 클라우드 서명 방식으로 전환한다면 별도 GitHub Secret으로 이전해야 하며 두 보관 방식을 혼용하지 않음 | Android의 `GUARDIAN_MANIFEST_PUBLIC_KEY` |

세 키는 서로 대체할 수 없습니다. Android APK 서명키를 라이선스 또는 필터 서명에 재사용하지 않습니다. Android debug keystore는 개발 업데이트 호환성용이며 운영 배포 서명키로 사용하지 않습니다.

## 2. Android 정식 배포 서명키

- 생성 스크립트: `server/license-api/windows/Prepare-GuardianProductionRelease.ps1`
- 전용 모드: `./Prepare-GuardianProductionRelease.ps1 -SigningOnly`
- 알고리즘/크기: RSA 3072-bit, JKS, alias `guardian-release`
- 로컬 원본: `%LOCALAPPDATA%\Guardian\secrets\guardian-release.jks`
- 로컬 암호 보관: 같은 보호 폴더의 DPAPI 암호 파일 2개
- GitHub Actions Secrets:
  - `GUARDIAN_RELEASE_KEYSTORE_BASE64`
  - `GUARDIAN_RELEASE_STORE_PASSWORD`
  - `GUARDIAN_RELEASE_KEY_ALIAS`
  - `GUARDIAN_RELEASE_KEY_PASSWORD`
- 정식 APK/AAB workflow: `.github/workflows/android-production-release.yml`
- 정식 versionCode는 개발 빌드와 충돌하지 않도록 2,000,000 이상을 사용합니다.

SigningOnly 모드는 필터 재배포 승인 및 Tailscale API 상태를 완료한 것으로 간주하지 않습니다. 이 모드는 필터 승인 변수를 모두 거부(false)로 설정하고 라이선스 API URL을 변경하지 않습니다. 따라서 키 등록만으로 정식 릴리스가 활성화되지는 않습니다.

### 키 생성 및 등록 시 안전 조건

1. Windows PowerShell 7.2, GitHub CLI 로그인, JDK keytool을 확인합니다.
2. 기존 키 저장소 또는 DPAPI 암호 파일이 있으면 재생성하지 않고 기존 키를 조사합니다. 일부 파일만 있으면 중단하고 복구합니다.
3. 키 생성 후 GitHub Secrets에 표준 입력 방식으로만 등록합니다. 명령행 인수, 로그, Git 파일에 비밀값을 넣지 않습니다.
4. JKS 원본과 암호는 서로 분리된 암호화 오프라인 백업에 보관하고 복구 가능성을 확인합니다.
5. 첫 운영 빌드에서 APK와 AAB의 실제 서명 인증서 지문을 추출하고 이후 배포에서도 동일한지 검증합니다.
6. 운영 키가 분실·유출된 경우 새 키로 조용히 교체하지 않습니다. 기존 설치본 업데이트 호환성 및 서명키 교체 정책을 먼저 수립합니다.

## 3. PRO 라이선스 서명 및 조회 비밀값

Neon Function `guardianapi`에서 필요한 값:

- `GUARDIAN_LICENSE_PRIVATE_KEY`: Android에 고정된 라이선스 공개키와 정확히 쌍을 이루는 RSA 개인키
- `GUARDIAN_LICENSE_LOOKUP_SECRET`: 최소 32바이트 이상의 독립 난수 비밀값
- `GUARDIAN_ADMIN_TOKEN`: 최소 32바이트 이상의 독립 관리자 토큰
- Neon이 제공하는 `DATABASE_URL`: DB 연결용 자동 주입 값

GitHub 수동 배포 workflow `.github/workflows/guardian-neon-production-deploy.yml`의 입력 계약:

- GitHub Secrets: `NEON_API_KEY`, `GUARDIAN_ADMIN_TOKEN`, `GUARDIAN_LICENSE_LOOKUP_SECRET`, `GUARDIAN_LICENSE_PRIVATE_KEY`
- GitHub Variables: `NEON_PROJECT_ID`, `NEON_BRANCH_ID`, `GUARDIAN_LICENSE_PUBLIC_KEY`, `GUARDIAN_LICENSE_API_BASE_URL`

라이선스 개인키와 공개키는 다음 관계를 검증해야 합니다.

`publicKeyFrom(GUARDIAN_LICENSE_PRIVATE_KEY) == Android-pinned GUARDIAN_LICENSE_PUBLIC_KEY`

일치 검증이 통과하기 전에는 실제 라이선스 발급/활성화 성공을 선언하지 않습니다. 운영 키를 임의 생성해 Android 공개키를 바꾸는 방식으로 우회하지 않습니다.

## 4. 필터 manifest 서명키

현재 Windows 설치 스크립트 `Setup-GuardianServer.ps1`은 별도의 RSA 3072-bit 필터 키쌍을 만들고 개인키를 Windows DPAPI로 보호합니다. 이 키는 Android 앱의 필터 manifest 공개키와 쌍이 맞아야 합니다. 필터 소스별 재배포 권리 승인과 게시 브랜치 검증은 서명키 존재 여부와 별개인 독립 배포 게이트입니다.

운영 노트북이 꺼져 있어도 필터를 매일 게시해야 하는 요구사항을 충족하려면 장기적으로 서명 개인키를 GitHub Actions Secret에 안전하게 보관하고 GitHub-hosted workflow가 서명·게시하는 구조가 적합합니다. 단, 현재 workflow가 Windows DPAPI 키를 자동으로 가져다 쓰는 것은 아닙니다. 실제 이전 전에는 키쌍 일치, 비밀값 주입, 키 백업, 게시 검증을 별도 구현하고 검증해야 합니다.

## 5. 현재 확인된 상태 (2026-11-26)

| 영역 | 확인 결과 | 상태 |
|---|---|---|
| Android 개발 서명 | Debug APK는 고정 개발 인증서로 서명됨. 지문은 `edaeecc9e4a27e64f07d92643fb85140725083abed553f912a0dee0387fbd82c` | 개발 전용 |
| Android 운영 서명 | 정식 릴리스 workflow는 GitHub Secrets의 JKS 및 암호 4종을 요구. 이 연결에서 Secret의 실제 등록 여부와 로컬 JKS 백업은 조회할 수 없음 | 미검증 |
| Neon 라이선스 API | 현재 함수 배포 정보에서 `GUARDIAN_ADMIN_TOKEN`만 환경변수로 확인됨. 마지막 배포는 root `index.mjs` 누락으로 로드 실패 | 미완료 |
| 필터 서명/게시 | CI의 승인 게이트 검사는 통과하지만 7개 upstream source의 재배포 승인 증빙과 실제 게시용 개인키가 준비되지 않아 운영 게시 차단 | 미완료 |
| 정식 APK/AAB | 임시 CI 키를 사용한 빌드 검증은 실제 운영 서명키 검증이 아님 | 미완료 |

GitHub Secret 값은 보안상 조회하거나 이 문서에 복사하지 않습니다. Secret 등록 여부는 GitHub Actions의 실제 수동 배포 검증 결과로만 확인합니다. Neon 함수 환경변수 이름을 확인하는 것은 개인키의 유효성이나 공개키와의 일치를 증명하지 않습니다.

## 6. 운영 완료 판정 기준

- [ ] Android 운영 JKS가 안전한 위치에 한 번만 생성되고 복구 가능한 암호화 백업이 존재함
- [ ] GitHub Actions에 Android JKS Base64와 별도 암호 Secret 3종이 등록됨
- [ ] 실제 정식 APK와 AAB의 서명 인증서 지문이 동일하며 후속 버전에서도 유지됨
- [ ] Neon에 라이선스 개인키·조회 비밀값·관리자 토큰이 안전하게 주입됨
- [ ] 라이선스 개인키에서 도출한 공개키가 Android 고정 공개키와 바이트 단위로 일치함
- [ ] Neon 배포 후 /health 및 /ready 통과, 실제 라이선스 발급·활성화·갱신·해제 E2E 통과
- [ ] 필터 소스별 권리 승인, 필터 서명키 일치, manifest 게시 및 Android 검증 통과

운영 서명키 설정과 실제 운영 배포 검증은 별개의 완료 항목입니다. 임시 키를 사용한 CI 테스트, workflow YAML 통과, 키 생성 스크립트 존재만으로 운영 준비 완료를 선언하지 않습니다.
