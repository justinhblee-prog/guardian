# Windows 노트북 Guardian 운영 서버

Guardian 라이선스 API/PostgreSQL과 서명 필터 배포 서버를 개인 Windows 노트북에서 실행합니다. 두 서비스는 별도 Docker Compose 스택으로 격리되며 각각 localhost 포트만 사용합니다.

## 운영 데이터 저장 위치 (보안 필수)

최신 설치 스크립트는 비밀정보와 런타임 데이터를 Git 저장소/OneDrive 외부에 보관합니다. 기본 경로는 `%LOCALAPPDATA%\Guardian` (일반적으로 `C:\Users\<사용자>\AppData\Local\Guardian`)입니다. 라이선스 개인키, 필터 DPAPI 키, Android release JKS/암호, Docker 환경 파일, 필터 설정·산출물·로그, Android Debug APK, DB 백업이 이 경로 아래에 저장됩니다.

다른 로컬 디스크를 사용하려면 설치 및 운영 스크립트를 실행하는 Windows 사용자 환경에 `GUARDIAN_DATA_DIR`를 영구 환경 변수로 설정하십시오. 예: `D:\GuardianData`. 저장소 폴더나 OneDrive 폴더 안의 경로는 보안상 거부됩니다. 설치 전 기존 저장소 내부 `secrets` 또는 `.env`가 있다면 임의로 복사/삭제하지 말고 백업 및 키 보존 여부를 먼저 확인하십시오.

## 준비물
- Windows 10/11 64-bit
- Docker Desktop (WSL 2 backend 권장), Windows 로그인 시 자동 시작 활성화
- OpenSSL 명령행 도구
- PowerShell 7.2 이상 (`pwsh.exe`; 서명 필터 게시 및 공개키 일치 검증에 필요)
- Node.js 22 이상 (필터 업데이트/서명 발행 작업을 이 노트북에서 실행할 경우)

## 사전 점검
PowerShell 7.2 이상(`pwsh`)에서 `server/license-api/windows` 폴더로 이동해 필요한 작업에 맞는 준비 상태를 확인합니다. Windows 기본 `powershell.exe`(5.1)가 아니라 `pwsh.exe`를 사용하세요.

```powershell
# Docker Desktop 및 OpenSSL: 노트북 서버 설치에 필요
.\Test-GuardianEnvironment.ps1

# Android APK를 노트북에서 다시 빌드할 때 추가 확인
.\Test-GuardianEnvironment.ps1 -ForAndroidBuild

# 필터 공급원 다운로드/서명 예약 작업을 구성할 때 추가 확인
.\Test-GuardianEnvironment.ps1 -ForFilterUpdate
```

준비가 되지 않은 필수 항목은 `BLOCK`으로 표시되고 종료 코드 1을 반환합니다. 이 검사는 환경을 변경하거나 서버를 공개하지 않습니다.

## 최초 설치
PowerShell에서 `server/license-api/windows` 폴더로 이동해 실행합니다.

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\Setup-GuardianServer.ps1
```

운영 키와 외부 데이터 경로만 먼저 준비하고 Docker 실행은 나중으로 미루려면 다음처럼 실행할 수 있습니다. 이 모드는 OpenSSL만 필요하며 Docker Desktop을 검사하거나 컨테이너/예약 작업을 시작하지 않습니다. 나중에 Docker Desktop을 실행한 뒤 `Start-All-GuardianServices.ps1` 및 `Register-GuardianStartupTask.ps1`을 별도로 실행합니다.

```powershell
./Setup-GuardianServer.ps1 -SkipAutoStart
```

설치 스크립트는 라이선스 API의 RSA 3072-bit 키쌍, 필터 서명 RSA 3072-bit 키쌍, DB 비밀번호·관리자 토큰·HMAC 비밀값을 생성합니다. 외부 데이터 경로에 ACL을 적용하고 기존 `.env`는 덮어쓰지 않습니다. 필터 서명 개인키는 현재 Windows 사용자 DPAPI로 암호화해 `%LOCALAPPDATA%\Guardian\secrets\guardian-filter-private.dpapi`에 보관하고 평문 PEM 임시 파일은 삭제합니다. Docker 환경 파일, 필터 설정·dist·로그와 백업도 외부 경로에 저장합니다.

## 노트북에서 공개키를 포함한 Android APK 다시 빌드
최초 설치 스크립트가 만든 두 공개키를 앱에 고정하려면 Android Studio/JDK 17, Android SDK 및 Gradle 8.9가 설치되고 `gradle` 명령이 PATH에 등록된 노트북에서 다음을 실행합니다.

```powershell
./Build-GuardianAndroid-Debug.ps1
```

이 스크립트는 공개키를 빌드 프로세스에만 주입하고 단위 테스트·Lint·Debug APK 빌드를 실행합니다. APK와 SHA-256은 `%LOCALAPPDATA%\Guardian\outputs/`에 생성됩니다. 개인키는 APK에 포함되지 않습니다. 이 스크립트 산출물은 기존 개발용 서명 인증서를 사용하는 Debug APK이며, 상용 배포용 Production 서명 APK가 아닙니다. Release 서명은 별도 보호된 keystore와 Release 빌드 검증 절차가 필요합니다.

## 운영 명령
- 전체 시작 및 상태 확인: `./Start-All-GuardianServices.ps1`
- 전체 중지: `./Stop-All-GuardianServices.ps1`
- 라이선스 DB 백업: `./Backup-GuardianServer.ps1`
- 라이선스 관리자 화면: http://127.0.0.1:8787/admin
- 라이선스 API 준비 상태: http://127.0.0.1:8787/ready
- 필터 배포 상태: http://127.0.0.1:8788/health

 
### 노트북과 모바일 양쪽에서 라이선스 발급

관리자 화면은 반응형 웹 화면이므로 별도의 모바일 앱이나 별도 라이선스 DB를 만들지 않습니다. 노트북과 모바일이 동일한 라이선스 API/PostgreSQL을 사용합니다.

- **노트북:** `http://127.0.0.1:8787/admin`에 접속해 관리자 토큰으로 인증합니다.
- **모바일:** Tailscale에 로그인하고 노트북과 같은 tailnet에 연결한 뒤 `https://<노트북 MagicDNS 이름>/admin`에 접속합니다. `Enable-GuardianPrivateAccess.ps1`가 출력하는 HTTPS 주소 뒤에 `/admin`을 붙이면 됩니다.
- 두 화면에서 발급·조회·정지·기기 해제한 결과는 같은 서버에 즉시 반영됩니다. 발급 키는 생성 직후 한 번만 표시되며, 관리자 토큰은 브라우저 저장소에 저장되지 않습니다.
- 라이선스 관리 API는 tailnet 내부에서만 접근합니다. 공유기 포트포워딩이나 Tailscale Funnel로 공개하지 마세요.


## Windows 앱의 공통 Guardian Pro 라이선스\n\nWindows 데스크톱 앱은 **라이선스** 메뉴에서 공통 License API HTTPS 주소와 `GUARDIAN_LICENSE_PUBLIC_KEY`와 동일한 X.509 공개키를 설정한 뒤 발급된 `GDN-...` 키를 활성화합니다. 개인 서명키는 서버에만 보관합니다. Windows와 Android는 `guardian-pro` 제품 및 같은 라이선스 DB를 사용하며 `maxDevices` 활성화 한도를 함께 계산합니다. Windows는 설치별 임의 deviceBinding을 생성해 `license-device-id`에 유지하고, 서명 entitlement는 `license-entitlement.json`에 저장해 매 실행 시 RSA-SHA256 서명·만료·기기 바인딩을 다시 검증합니다. 앱의 라이선스 메뉴에서 갱신과 이 기기 해제를 수행할 수 있습니다.\n\n기존 Android `guardian-android` 토큰도 호환되며 신규 발급은 플랫폼 공통 `guardian` product claim을 사용합니다. 기존 운영 DB는 `Apply-GuardianLicenseMigrations.ps1` 실행 시 005 migration으로 `guardian-pro` 제품이 추가됩니다.\n\n## 필터 배포
필터 updater/publisher는 `server/filter-distribution`에 있습니다. upstream 필터 다운로드, 규칙 수 검증, 버전 생성, SHA-256, RSA-SHA256 manifest 서명까지 지원합니다. 저장소의 `sources.example.json`을 외부 데이터 경로의 `filter-distribution/sources.json`으로 복사해 실제 HTTPS 배포 주소와 공식 HTTPS 공급원/최소 규칙 수를 확인합니다. 최초 설치 스크립트가 필터 서명 개인키를 현재 사용자 DPAPI로 보호해 저장하므로 PEM을 환경변수·작업 인수·로그에 직접 기록하지 않습니다. 아래의 Tailscale/클라우드 연결 설정을 실행하면 `sources.json`의 GitHub 정적 원본 주소 설정, 최초 서명 게시, Tailscale 비공개 API 연결과 함께 매일 04:17 업데이트 및 클라우드 게시 작업이 자동 등록됩니다. 실행 시각을 변경하려면 `./Enable-GuardianPrivateAccess.ps1 -DailyAt '05:30'`처럼 지정하거나 `./Register-GuardianFilterUpdateTask.ps1 -DailyAt '05:30'`으로 예약 작업만 변경할 수 있습니다. 이 작업은 매일 지정 시각 및 사용자 로그인 시 실행하며 upstream 다운로드 → 규칙 수/UTF-8 검증 → RSA 서명 → manifest/필터 해시 검증 → GitHub 정적 클라우드 게시까지 수행합니다. 로그는 `%LOCALAPPDATA%\Guardian\filter-distribution\logs/`에 기록됩니다. 수동 실행은 `./Update-And-Publish-GuardianFilters.ps1`입니다. Node.js 22 이상이 필요합니다. 서명 개인키는 공개 웹 컨테이너나 Android 앱에 넣지 않습니다. `dist`만 읽기 전용으로 제공됩니다.

Android 앱의 **운영 서버 주소 (노트북)**는 라이선스 API에 연결하고, **서명 필터 배포 서버**는 GitHub 정적 클라우드 원본에 연결합니다. 필터 원본은 `https://raw.githubusercontent.com/justinhblee-prog/guardian/guardian-filter-data`이며, 공개된 필터 콘텐츠·manifest·RSA detached signature만 저장합니다. 라이선스 API와 개인 서명키는 노트북에 남습니다.

## Android 정식 릴리스 서명 준비

먼저 `Setup-GuardianServer.ps1`로 운영 공개키와 로컬 서버를 준비합니다. 필터를 공개 게시하지 않고 라이선스 API만 tailnet에 연결하려면 `Enable-GuardianPrivateApiOnly.ps1`을 실행합니다. 이 스크립트는 `127.0.0.1:8787` License API만 Tailscale Serve로 연결하며 필터 파일 게시, 서명, 예약 업데이트 또는 Funnel 설정은 하지 않습니다.

```powershell
./Enable-GuardianPrivateApiOnly.ps1 -RegisterGitHubVariable
```

이 옵션은 로컬 및 Tailscale HTTPS 주소의 `/ready` JSON에서 `ok=true`, `database=ready`, `signer=ready`를 확인합니다. HTTPS 인증서·라우팅 전파 지연을 위해 외부 검증은 최대 5회 재시도하며, 검증 성공 후 GitHub repository variable `GUARDIAN_LICENSE_API_BASE_URL` 하나만 등록합니다. 검증 실패 시 GitHub variable은 변경하지 않습니다. 필터 source 승인, 공개 filter branch 게시, Production release 실행, Tailscale Funnel 설정은 하지 않습니다. `gh auth login` 및 repository variable 수정 권한이 필요합니다.

그 다음 Git for Windows, GitHub CLI(`gh`) 로그인 및 JDK `keytool`이 준비된 PowerShell 7.2에서 아래 signing-only 모드를 실행합니다.

먼저 Tailscale 로그인이나 라이선스 API 연결을 기다리지 않고 운영 APK 서명키와 GitHub 서명 Secret/공개키 Variable을 준비하려면 다음 모드를 사용합니다. 이 모드는 `GUARDIAN_LICENSE_API_BASE_URL`을 변경하지 않으며, 필터 승인 변수를 모두 `false`로 설정해 기존의 오래된 승인값이 정식 배포를 통과시키지 못하도록 합니다. 따라서 이 단계만으로 Release를 실행할 수는 없습니다. 이후 Tailscale HTTPS API가 준비되고 필터 재배포 승인·공개 게시가 완료되면 전체 운영 설정 모드를 실행해야 합니다.

```powershell
./Prepare-GuardianProductionRelease.ps1 -SigningOnly
```

필터 재배포 권리 검토, 승인 증빙, `guardian-filter-data` 공개 브랜치 게시까지 완료한 후에만 전체 운영 설정 모드를 실행합니다.

```powershell
./Prepare-GuardianProductionRelease.ps1
```

이 스크립트는 기존 운영 라이선스/필터 공개키와 Tailscale HTTPS `/ready` 응답을 확인한 뒤 Android production keystore를 최초 1회 생성합니다. 키 저장소와 DPAPI 암호 파일은 외부 Guardian 데이터 경로의 `secrets/` 아래에 두고 현재 Windows 사용자와 SYSTEM만 읽을 수 있도록 ACL을 제한합니다. keystore를 새로 만들거나 덮어쓰지 않으며, 같은 노트북에서 재실행하면 기존 키를 재사용합니다.

GitHub CLI의 표준 입력으로 keystore Base64와 비밀번호를 Actions Secrets에 등록하고, 공개키·HTTPS 주소·버전 `1.0.0`과 현재 Android 개발 versionCode보다 1 큰 값을 Repository Variables에 설정합니다. `Prepare-GuardianProductionRelease.ps1`의 기본 versionCode는 `0`(자동 산정)이며, 실행 시 Gradle 개발 versionCode를 읽어 정식 versionCode를 자동으로 결정합니다. 또한 실제 운영 `sources.json`의 source ID, licenseId, 승인 플래그와 승인 참조만 포함한 `GUARDIAN_FILTER_APPROVALS_JSON`을 자동 등록합니다. 전체 운영 설정 모드는 source 하나라도 재배포 승인 증빙이 없으면 keystore 생성 및 GitHub 설정 변경 전에 중단합니다. `-SigningOnly` 모드는 서명키와 GitHub Secrets/Variables만 설정하고, 필터 승인 변수는 모두 거부(false)로 재설정합니다. Actions의 정식 Release 워크플로는 이 승인 변수를 다시 검증합니다. 비밀값을 콘솔이나 Git 저장소에 출력하지 않습니다. 실행 후 GitHub Actions의 **Guardian AdBlock Production Release** 워크플로를 수동 실행하면 서명 APK/AAB와 전체 소스 ZIP, SHA-256 검증 자료를 생성합니다. 공개 `guardian-filter-data` 브랜치가 없거나 승인 변수가 없으면 배포는 계속되지 않습니다.

**중요:** `guardian-release.jks`와 DPAPI 암호 파일은 노트북의 암호화된 오프라인 백업에도 보관하세요. 이 keystore를 분실하면 기존 설치본을 동일한 앱 서명으로 업데이트할 수 없습니다. keystore 파일이나 비밀번호를 메신저·Git 저장소에 올리지 마세요. 운영 노트북과 Tailscale HTTPS 서비스가 아직 준비되지 않았다면 이 스크립트는 의도적으로 중단합니다.

## 노트북과 모바일의 비공개 연결 (권장)

라이선스 API와 서명키는 개인 Windows 노트북에서 운영합니다. Android 앱은 라이선스 API에만 Tailscale Serve HTTPS로 접속합니다. 필터 배포는 별도 정적 클라우드 원본(GitHub 공개 저장소의 전용 `guardian-filter-data` 브랜치)을 사용하므로 노트북이 꺼져 있어도 앱에서 업데이트를 받을 수 있습니다. 클라우드에는 필터 텍스트, manifest, detached signature만 올라가며 라이선스 DB, 개인키, `.env`는 게시하지 않습니다.

1. Windows 노트북과 Android 모바일에 Tailscale을 설치하고 같은 Tailscale 계정/tailnet에 로그인합니다.
2. Tailscale 관리 콘솔에서 MagicDNS와 HTTPS certificates를 활성화합니다. HTTPS 인증서 발급 시 기기 이름과 tailnet DNS 이름이 공개 인증서 투명성 로그에 기록될 수 있습니다.
3. 노트북에서 Docker Desktop을 시작한 뒤 `Start-All-GuardianServices.ps1`로 라이선스 API와 로컬 필터 서버의 상태가 정상인지 확인합니다.
4. 관리자 PowerShell에서 다음 스크립트를 실행합니다. 이 스크립트는 Tailscale Serve로 라이선스 API만 비공개 연결하고, `sources.json`의 `baseUrl`을 GitHub 정적 클라우드 주소로 설정한 뒤 DPAPI 보호 서명키로 필터를 다운로드·서명·발행하고 클라우드에 게시합니다. 기존 외부 `sources.json` 백업은 `filter-distribution/sources.json.before-private-access.bak`에 남깁니다.

```powershell
.\Enable-GuardianPrivateAccess.ps1
```

5. 스크립트가 출력하는 라이선스 API HTTPS 주소를 Android 앱의 **운영 서버 주소 (노트북)**에 입력합니다.
6. Android 앱의 **서명 필터 배포 서버**에는 `https://raw.githubusercontent.com/justinhblee-prog/guardian/guardian-filter-data`를 입력합니다. 정적 GitHub 원본은 공개 읽기 CDN이며 앱은 manifest의 RSA 서명 sidecar와 필터 SHA-256을 검증합니다.
7. 모바일의 Tailscale 연결을 켠 상태에서 라이선스 연결을 확인하고, 필터 업데이트는 일반 인터넷 연결에서 확인합니다.

라이선스 API 연결은 공유기 포트포워딩, 공용 IP, Cloudflare Tunnel 또는 Tailscale Funnel 없이 tailnet 내부에서만 동작합니다. 라이선스 API를 사용할 때는 노트북이 켜져 있고 Tailscale에 연결되어 있어야 합니다. 필터 파일은 공개 CDN에서 읽으므로 노트북 전원과 무관하게 업데이트할 수 있습니다. 필터 콘텐츠는 공개 정보이며 무결성과 출처는 앱에 고정된 RSA 공개키로 검증합니다.

**주의:** 이 스크립트는 기존 Tailscale Serve의 HTTPS 443 설정을 Guardian 라이선스 API용으로 설정합니다. 같은 포트를 이미 다른 용도로 사용 중이라면 실행 전에 기존 Serve 구성을 확인하세요. 비공개 연결을 종료할 때는 `tailscale serve status`로 현재 구성을 확인한 후 해당 포트만 해제하세요. `tailscale funnel`을 실행하지 마세요.

## 자동 시작과 절전
예약 작업은 Windows 로그인 시 전체 서비스를 시작합니다. Docker Compose의 `restart: unless-stopped`는 Docker 엔진 재시작 후 컨테이너 복구를 돕습니다. 로그인 전 부팅 단계부터 운영하려면 별도 서비스 계정과 권한 설계가 필요합니다. 노트북이 꺼져 있거나 절전 상태이면 서버도 접속할 수 없습니다. 운영 중 절전 방지, 전원 연결, 네트워크 안정성 및 Windows 업데이트 재부팅 정책을 별도로 관리해야 합니다.

## 네트워크 보안
라이선스 API 컨테이너 호스트 포트는 `127.0.0.1`에만 바인딩하고 Tailscale Serve를 통해 tailnet 내부에서만 접근합니다. 필터 CDN은 공개 읽기 전용이며 공개 데이터만 포함합니다. 공유기 포트포워딩, Windows 방화벽의 공용 포트 개방, Tailscale Funnel을 사용하지 마세요. 앱의 서버 주소 설정은 접속 URL만 저장하며 Tailscale 설치·로그인이나 노트북 전원/네트워크 상태를 대신 관리하지 않습니다.

## 키와 백업
- `%LOCALAPPDATA%\Guardian\secrets/`: 공개키, DPAPI 필터 개인키, Android release JKS 및 DPAPI 암호 파일
- `%LOCALAPPDATA%\Guardian\license-api/.env`: 관리자 토큰, DB 비밀번호, 라이선스 개인키
- `%LOCALAPPDATA%\Guardian\filter-distribution/.env`: 필터 서버 설정
- `%LOCALAPPDATA%\Guardian\filter-distribution/sources.json`, `dist/`, `logs/`: 외부 필터 설정·배포 산출물·로그
- `%LOCALAPPDATA%\Guardian\backups/`: PostgreSQL 백업 파일

개인키와 `.env`는 절대 공유하거나 커밋하지 않습니다. DB 백업 파일은 암호화된 별도 위치에 보관하고 복구 리허설을 수행하세요. 필터 배포물은 GitHub `guardian-filter-data` 브랜치에도 보관되며, 노트북 고장 시에도 모바일 앱이 공개된 최신 manifest와 버전 고정 필터를 내려받을 수 있습니다.

## 완료 전 검증
HTTPS/TLS, 접근제어, Android 앱의 공개키 일치, 실제 필터 갱신, 라이선스 발급/활성화/갱신/해제, 노트북 재부팅·절전·네트워크 장애 복구, 실제 Android 기기 E2E 테스트가 필요합니다. 이 문서는 배포 도구 설명이며 실제 노트북 설치, GitHub 클라우드 브랜치 최초 게시, Android 모바일 E2E 검증이 끝났다는 의미는 아닙니다.

## 클라우드 운영 방식: Neon (노트북 상시 가동 불필요)

현재 목표 운영 방식은 **Neon 라이선스 API + GitHub Actions 배포**입니다. 이 방식을 선택하면 Windows 노트북은 최초 비밀값 등록을 위한 관리 단말일 뿐이며, 라이선스 API를 제공하거나 계속 켜 둘 필요가 없습니다. 기존 Tailscale/노트북 운영 절차는 로컬 개발·대체 구성으로만 취급합니다. Android 운영 API 주소는 Neon Function URL이어야 하며 Tailscale 주소를 함께 사용하면 안 됩니다.

Neon 프로젝트/브랜치의 기본값과 API 주소는 다음과 같습니다.

- Project: `wispy-surf-33052072`
- Branch: `br-shiny-mountain-bi4ia7g2`
- Function: `guardianapi`
- API URL: `https://br-shiny-mountain-bi4ia7g2-guardianapi.compute.c-5.ap-southeast-1.aws.neon.tech`

기존 운영 키가 로컬에 이미 구성된 경우 저장소 루트에서 다음 스크립트를 실행해 GitHub Actions 설정을 등록합니다.

```powershell
pwsh -File .\server\license-api\windows\Prepare-GuardianNeonProduction.ps1
```

이 스크립트는 기존 `%LOCALAPPDATA%\Guardian\license-api\.env`의 관리자 토큰·조회용 비밀키·라이선스 개인키와 기존 공개키 파일을 읽습니다. 개인키와 공개키가 실제로 일치하는지 확인한 후에만 진행하며, Neon API Key는 숨김 입력으로 받습니다. 다음 GitHub Actions **Secrets** 4개와 **Variables** 4개를 설정합니다.

- Secrets: `NEON_API_KEY`, `GUARDIAN_ADMIN_TOKEN`, `GUARDIAN_LICENSE_LOOKUP_SECRET`, `GUARDIAN_LICENSE_PRIVATE_KEY`
- Variables: `NEON_PROJECT_ID`, `NEON_BRANCH_ID`, `GUARDIAN_LICENSE_PUBLIC_KEY`, `GUARDIAN_LICENSE_API_BASE_URL`

키를 새로 생성·교체하거나 기존 값을 콘솔에 출력하지 않습니다. 로컬 키 파일이 없거나 키 쌍이 일치하지 않으면 중단합니다. GitHub 설정 등록과 Neon 배포는 별개이며, 이 스크립트는 **배포를 실행하지 않습니다**. 등록 후에도 Neon 배포 워크플로를 별도로 실행하고 `/health`와 `/ready` 실서비스 검증이 통과해야 운영 전환이 완료됩니다.

Android 정식 릴리스는 동일한 `GUARDIAN_LICENSE_PUBLIC_KEY` 변수를 빌드에 주입해야 하므로, Neon 서버 개인키와 앱 공개키의 일치 검증 없이 키를 교체하지 마세요. GitHub Actions Secret 값은 조회할 수 없도록 설계되어 있으므로 등록 여부는 비밀값을 출력하는 대신 배포 사전검사 및 실제 readiness 결과로 확인합니다.
