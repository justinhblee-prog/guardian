# Windows 노트북 운영 서버

Guardian 라이선스 API와 PostgreSQL을 사용자의 Windows 노트북에서 운영하기 위한 스크립트입니다. Docker Compose가 DB와 API의 재시작 정책을 관리하고, Windows 로그인 시 작업 스케줄러가 서버 시작 스크립트를 실행하도록 구성합니다.

## 준비물
- Windows 10/11 64-bit
- Docker Desktop (WSL 2 backend 권장)
- OpenSSL 명령행 도구
- 충분한 저장 공간, 안정적인 전원과 인터넷 연결

## 최초 설치
PowerShell에서 이 디렉터리로 이동해 실행합니다.

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\Setup-GuardianServer.ps1
```

설치 스크립트는 RSA 3072-bit 서명 키쌍과 독립된 DB 비밀번호·관리자 토큰·HMAC 비밀값을 생성하고, Git에 포함되지 않는 `.env`와 `secrets/`에 저장합니다. 기존 `.env`가 있으면 덮어쓰지 않고 중단합니다. 설치 후 API와 DB를 시작하고 `/ready` 응답을 확인합니다.

## 관리
- 시작: `./Start-GuardianServer.ps1`
- 중지: `./Stop-GuardianServer.ps1`
- DB 백업: `./Backup-GuardianServer.ps1`
- 관리자 화면: http://127.0.0.1:8787/admin
- 준비 상태: http://127.0.0.1:8787/ready

Docker Desktop 설정에서 Windows 로그인 시 Docker Desktop 자동 시작을 활성화해야 합니다. 설치 스크립트는 로그인 시 Guardian Compose 스택을 시작하는 예약 작업을 등록합니다. Compose의 `restart: unless-stopped`는 Docker 엔진 재시작 후 컨테이너 재기동을 돕습니다. 노트북이 꺼져 있거나 절전 상태이면 서버도 접속할 수 없습니다. 운영 중 절전 방지와 네트워크 안정성을 별도로 설정해야 합니다.

## 네트워크·보안 기본값
API는 `127.0.0.1:8787`에만 노출되어 노트북 자체에서만 접근할 수 있습니다. 공유기 포트포워딩이나 Windows 방화벽 전체 공개를 하지 마세요. Android 기기에서 외부 네트워크로 접근하려면 고정 도메인, HTTPS 역방향 프록시, 인증된 사설 VPN/터널, 접근 제어를 먼저 구성해야 합니다. 단순한 LAN IP 또는 HTTP 주소를 인터넷 운영에 사용하는 것은 안전하지 않습니다.

앱의 운영 서버 주소 변경은 클라이언트가 접속할 URL을 교체하는 기능입니다. 주소를 바꾼다고 노트북의 공인 접속 경로, TLS 인증서, 방화벽 또는 터널이 자동 구성되지는 않습니다. 공개 HTTPS 도메인 또는 검증된 사설 HTTPS 경로를 구성한 뒤 앱에 입력해야 합니다.

## 키와 비밀값
- `secrets/guardian-license-public.pem`: 앱 빌드 설정에 사용할 공개키
- `secrets/guardian-license-private.pem`: 서버 전용 개인키
- `.env`: 관리자 토큰, DB 비밀번호, HMAC 비밀값 및 개인키 포함

개인키와 `.env`는 절대 공유하거나 저장소에 커밋하지 않습니다. DB 백업 파일도 민감정보로 취급하고 별도 암호화 저장소에 보관하세요.

## 운영 전 검증
HTTPS/TLS, 접근제어, 공개키 pinning과 앱 빌드 키 일치, 정기 백업·복구 리허설, 노트북 절전·재부팅·네트워크 장애 복구, 실제 Android 기기 라이선스 발급/활성화/갱신/해제 E2E 테스트가 필요합니다.

이 패키지는 노트북 로컬 운영 환경을 구성하는 배포 도구입니다. 인터넷 공개 운영이나 Android 실기기 E2E 검증을 완료했다는 의미는 아닙니다.
