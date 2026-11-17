# Windows 노트북 Guardian 운영 서버

Guardian 라이선스 API/PostgreSQL과 서명 필터 배포 서버를 개인 Windows 노트북에서 실행합니다. 두 서비스는 별도 Docker Compose 스택으로 격리되며 각각 localhost 포트만 사용합니다.

## 준비물
- Windows 10/11 64-bit
- Docker Desktop (WSL 2 backend 권장), Windows 로그인 시 자동 시작 활성화
- OpenSSL 명령행 도구
- Node.js 22 이상 (필터 업데이트/서명 발행 작업을 이 노트북에서 실행할 경우)

## 사전 점검
PowerShell에서 `server/license-api/windows` 폴더로 이동해 필요한 작업에 맞는 준비 상태를 확인합니다.

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

설치 스크립트는 라이선스 API의 RSA 3072-bit 키쌍, 필터 서명 RSA 3072-bit 키쌍, DB 비밀번호·관리자 토큰·HMAC 비밀값을 생성합니다. 기존 `.env`는 덮어쓰지 않습니다. 필터 서명 개인키는 현재 Windows 사용자 DPAPI로 암호화해 `secrets/guardian-filter-private.dpapi`에 보관하고, 평문 PEM 임시 파일은 삭제합니다. 필터 배포 서버용 `.env`와 `dist` 폴더도 준비하며, 로그인 시 두 서버를 함께 시작하는 예약 작업을 등록합니다. 개인키와 `.env`는 Git에 저장하거나 다른 사용자에게 공유하지 않습니다.

## 노트북에서 공개키를 포함한 Android APK 다시 빌드
최초 설치 스크립트가 만든 두 공개키를 앱에 고정하려면 Android Studio/JDK 17, Android SDK 및 Gradle 8.9가 설치되고 `gradle` 명령이 PATH에 등록된 노트북에서 다음을 실행합니다.

```powershell
./Build-GuardianAndroid-Debug.ps1
```

이 스크립트는 공개키를 빌드 프로세스에만 주입하고 단위 테스트·Lint·Debug APK 빌드를 실행합니다. APK와 SHA-256은 `server/license-api/outputs/`에 생성됩니다. 개인키는 APK에 포함되지 않습니다. 이 스크립트 산출물은 기존 개발용 서명 인증서를 사용하는 Debug APK이며, 상용 배포용 Production 서명 APK가 아닙니다. Release 서명은 별도 보호된 keystore와 Release 빌드 검증 절차가 필요합니다.

## 운영 명령
- 전체 시작 및 상태 확인: `./Start-All-GuardianServices.ps1`
- 전체 중지: `./Stop-All-GuardianServices.ps1`
- 라이선스 DB 백업: `./Backup-GuardianServer.ps1`
- 라이선스 관리자 화면: http://127.0.0.1:8787/admin
- 라이선스 API 준비 상태: http://127.0.0.1:8787/ready
- 필터 배포 상태: http://127.0.0.1:8788/health

## 필터 배포
필터 updater/publisher는 `server/filter-distribution`에 있습니다. upstream 필터 다운로드, 규칙 수 검증, 버전 생성, SHA-256, RSA-SHA256 manifest 서명까지 지원합니다. `sources.example.json`을 `sources.json`으로 복사해 실제 HTTPS 배포 주소와 공식 HTTPS 공급원/최소 규칙 수를 확인합니다. 최초 설치 스크립트가 필터 서명 개인키를 현재 사용자 DPAPI로 보호해 저장하므로 PEM을 환경변수·작업 인수·로그에 직접 기록하지 않습니다. 매일 04:17 업데이트 작업 등록: `./Register-GuardianFilterUpdateTask.ps1 -DailyAt '04:17'`. 이 작업은 매일 지정 시각 및 사용자 로그인 시 실행하고 결과를 `server/filter-distribution/logs/`에 기록합니다. 수동 실행은 `./Invoke-GuardianFilterUpdate.ps1`입니다. Node.js 22 이상이 필요합니다. 서명 개인키는 공개 웹 컨테이너나 Android 앱에 넣지 않습니다. `dist`만 읽기 전용으로 제공됩니다.

Android 앱의 설정에서 **운영 서버 주소 (노트북)**에는 라이선스 API의 HTTPS 주소를, **서명 필터 배포 서버**에는 필터 서버의 HTTPS 주소를 각각 저장합니다. `sources.json`의 `baseUrl`은 앱의 필터 서버 주소와 정확히 일치해야 합니다. 아래의 비공개 Tailscale 연결을 사용하면 공용 서버를 별도로 호스팅하지 않고도 노트북의 서비스를 모바일에서 안전하게 사용할 수 있습니다.

## 노트북과 모바일의 비공개 연결 (권장)

Guardian 운영 서버는 개인 Windows 노트북에서 실행하고 Android 모바일 앱이 접속하는 구조입니다. 서버 컨테이너는 계속 `127.0.0.1`에만 바인딩하며, Tailscale Serve가 HTTPS를 종료해 노트북 내부 포트로 전달합니다. Tailscale Serve는 같은 tailnet 기기만 접근할 수 있는 비공개 서비스이며, Funnel과 달리 인터넷 전체에 공개하지 않습니다.

1. Windows 노트북과 Android 모바일에 Tailscale을 설치하고 같은 Tailscale 계정/tailnet에 로그인합니다.
2. Tailscale 관리 콘솔에서 MagicDNS와 HTTPS certificates를 활성화합니다. HTTPS 인증서 발급 시 기기 이름과 tailnet DNS 이름이 공개 인증서 투명성 로그에 기록될 수 있습니다.
3. 노트북에서 Docker Desktop을 시작한 뒤 `Start-All-GuardianServices.ps1`로 라이선스 API와 필터 서버의 상태가 정상인지 확인합니다.
4. 관리자 PowerShell에서 다음 스크립트를 실행합니다. 이 스크립트는 Tailscale Serve를 설정하고 `sources.json`의 `baseUrl`을 노트북의 필터 HTTPS 주소로 바꾼 뒤, DPAPI 보호 서명키로 필터를 다시 다운로드·서명·발행합니다. 기존 `sources.json` 백업은 `.sources.json.before-private-access.bak`에 남깁니다.

```powershell
.\\Enable-GuardianPrivateAccess.ps1
```

5. 스크립트가 출력하는 두 HTTPS 주소를 Android 앱 설정에 각각 입력합니다.
   - **운영 서버 주소 (노트북)**: `https://<노트북-MagicDNS-이름>` (HTTPS 443 → localhost:8787)
   - **서명 필터 배포 서버**: `https://<노트북-MagicDNS-이름>:8443` (HTTPS 8443 → localhost:8788)
6. 모바일의 Tailscale 연결을 켠 상태에서 앱의 라이선스 연결 및 필터 업데이트를 확인합니다.

이 방식은 공유기 포트포워딩, 공용 IP, 외부 공개 DNS, Cloudflare Tunnel 또는 Tailscale Funnel을 요구하지 않습니다. 노트북이 켜져 있고 절전 상태가 아니며 Tailscale에 연결되어 있어야 합니다. 두 HTTPS 주소는 tailnet에 가입된 기기만 접근할 수 있습니다. Tailscale Serve의 HTTPS 포트별 프록시 기능을 사용합니다.

**주의:** 이 스크립트는 기존 Tailscale Serve의 HTTPS 443 및 8443 설정을 Guardian용으로 설정합니다. 같은 포트를 이미 다른 용도로 사용 중이라면 실행 전에 기존 Serve 구성을 확인하세요. 비공개 연결을 종료할 때는 `tailscale serve status`로 현재 구성을 확인한 후 해당 포트만 해제하세요. `tailscale funnel`을 실행하지 마세요.

## 자동 시작과 절전
예약 작업은 Windows 로그인 시 전체 서비스를 시작합니다. Docker Compose의 `restart: unless-stopped`는 Docker 엔진 재시작 후 컨테이너 복구를 돕습니다. 로그인 전 부팅 단계부터 운영하려면 별도 서비스 계정과 권한 설계가 필요합니다. 노트북이 꺼져 있거나 절전 상태이면 서버도 접속할 수 없습니다. 운영 중 절전 방지, 전원 연결, 네트워크 안정성 및 Windows 업데이트 재부팅 정책을 별도로 관리해야 합니다.

## 네트워크 보안
두 서비스의 호스트 포트는 계속 `127.0.0.1`에만 바인딩됩니다. Android 접속은 Tailscale Serve가 제공하는 HTTPS와 tailnet 접근 제어를 사용합니다. 공유기 포트포워딩, Windows 방화벽의 공용 포트 개방, Tailscale Funnel을 사용하지 마세요. 앱의 서버 주소 설정은 접속 URL만 저장하며 Tailscale 설치·로그인이나 노트북 전원/네트워크 상태를 대신 관리하지 않습니다. 다른 사설 VPN/터널을 선택하는 경우에도 동일하게 유효한 HTTPS 인증서와 접근제어를 검증해야 합니다.

## 키와 백업
- `server/license-api/secrets/guardian-license-public.pem`: 라이선스 서명 공개키
- `server/license-api/secrets/guardian-license-private.pem`: 라이선스 개인키
- 필터 서명 개인키: `server/license-api/secrets/guardian-filter-private.dpapi` (현재 Windows 사용자 DPAPI 보호; 최초 설치 스크립트가 생성)
- `.env` 파일: 관리자 토큰, DB 비밀번호 등 비밀값 포함
- `server/filter-distribution/dist/`: 서명된 manifest, signature, 필터 파일

개인키와 `.env`는 절대 공유하거나 커밋하지 않습니다. DB 백업 파일은 암호화된 별도 위치에 보관하고 복구 리허설을 수행하세요. 필터 배포물도 노트북 고장에 대비해 별도 백업해야 합니다.

## 완료 전 검증
HTTPS/TLS, 접근제어, Android 앱의 공개키 일치, 실제 필터 갱신, 라이선스 발급/활성화/갱신/해제, 노트북 재부팅·절전·네트워크 장애 복구, 실제 Android 기기 E2E 테스트가 필요합니다. 이 문서는 배포 도구 설명이며 실제 노트북 설치, 외부 HTTPS 공개, 휴대폰 E2E 검증이 끝났다는 의미는 아닙니다.
