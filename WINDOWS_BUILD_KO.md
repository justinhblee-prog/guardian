# Guardian AdBlock Windows — 개발 빌드

현재 Windows 개발 클라이언트는 Python 공통 DNS 규칙 엔진, IPv4/IPv6 loopback UDP DNS 프록시, Windows DNS 어댑터 설정 백업·복구, Tkinter 데스크톱 제어 화면 및 부팅 시 실행되는 백그라운드 DNS 작업으로 구성됩니다.

## 개발 환경

- Windows 10/11 x64
- Python 3.11 이상
- Inno Setup 6 (설치 프로그램 생성 시)

## 로컬 검증

```powershell
py -m venv .venv
.\.venv\Scripts\Activate.ps1
py -m pip install -r requirements-dev.txt
py -m pytest -q dns_engine windows_engine
py -m compileall -q dns_engine windows_engine
```

## EXE 및 설치 프로그램 빌드

```powershell
./scripts/build_windows.ps1
```

빌드 산출물:
- `dist/GuardianAdBlock/GuardianAdBlock.exe`: 데스크톱 제어 화면
- `dist/GuardianDnsProxy/GuardianDnsProxy.exe`: 백그라운드 DNS 필터 프로세스
- `dist/installer/Guardian-AdBlock-Windows-Setup-{VERSION}-b{BUILD}.exe`: 개발용 설치 프로그램

## 공통 버전 및 Windows 프로그램 업데이트

- 저장소 루트의 `VERSION` 파일이 제품 버전과 단조 증가 빌드 번호의 단일 기준입니다. 첫 줄은 제품 버전(예: `0.7.42-dev`), 둘째 줄은 빌드 번호(예: `55`)입니다.
- Android PR 빌드와 Windows 빌드는 같은 값을 사용합니다. main에 새 버전을 배포할 때 Android 릴리스 resolver와 공통 VERSION 값이 일치하는지 검증합니다.
- Windows 앱의 설정 > 정보 화면은 설치된 버전과 빌드 번호를 표시합니다.
- 설정 > 정보 > Windows 프로그램 업데이트에서 공식 GitHub Windows 개발 릴리스를 확인하고, 설치 파일을 내려받아 SHA-256을 검증한 뒤 설치 프로그램을 실행할 수 있습니다.
- main에서 VERSION이 변경된 빌드만 `guardian-windows-dev-v{BUILD}` 사전 릴리스와 `guardian-windows-update.json` manifest로 게시합니다. 동일 버전의 일반 코드 수정 빌드는 새 릴리스 번호를 소비하지 않습니다.
- 업데이트 manifest는 제품/플랫폼/버전/빌드/공식 설치 URL/ SHA-256을 검증합니다. 다운로드한 설치 프로그램은 사용자가 직접 실행합니다.
- 개발 패키지는 코드 서명이 없을 수 있습니다. SHA-256은 파일 무결성을 확인하지만 게시자 신뢰를 대신하지 않습니다. 정식 자동 업데이트와 SmartScreen 신뢰 배포에는 유효한 Authenticode 코드 서명 인증서가 필요합니다.

## 필터 업데이트 설정

설정 > 필터링 화면의 '필터 서버 설정'에서 HTTPS manifest origin, source ID 및 RSA X.509 SPKI 공개키 Base64를 입력합니다. 공개키는 배포 서버의 오프라인 서명 개인키에 대응하는 값이어야 합니다. 앱은 manifest RSA 서명, 같은 HTTPS origin의 filter URL, SHA-256, 최소 규칙 수, 버전 anti-rollback을 확인한 뒤 원자적으로 캐시를 교체합니다. 수동/자동 업데이트는 프로세스 간 잠금으로 직렬화됩니다.

## 네트워크 보호 안전성

- DNS 변경 전 활성 물리 어댑터의 IPv4/IPv6 DNS 주소와 DHCP 상태를 ProgramData 아래에 원자적으로 백업합니다.
- 로컬 DNS 프록시의 IPv4 및 IPv6 loopback 소켓을 먼저 바인딩한 후에만 어댑터 DNS를 변경합니다.
- 일부 어댑터 변경이 실패하면 변경된 항목을 역순 복구합니다.
- 보호 중 데스크톱 창을 닫아도 백그라운드 DNS 작업은 유지됩니다. 부팅 시 작업 스케줄러가 상태 파일을 확인하고 이전 보호 상태를 복구합니다.
- 보호 중지 시 원래 DNS 설정을 먼저 복구한 뒤 백그라운드 DNS 작업을 종료합니다.
- DNS 복구에 실패하면 백그라운드 프록시와 백업 파일을 유지해 네트워크가 로컬 DNS를 계속 사용할 수 있도록 합니다.
- 제거 프로그램은 DNS 복구가 성공하지 않으면 제거를 중단하도록 설계했습니다.
- 자동화 테스트는 PowerShell 호출을 mock 처리하며 실제 Windows 네트워크 어댑터 설정을 변경하지 않습니다.

## 아직 남은 검증

- 실제 Windows 10/11 PC에서 설치/제거 및 관리자 권한 흐름
- 실제 Ethernet/Wi-Fi 어댑터의 IPv4/IPv6 DNS 전환과 원상 복구
- 재부팅 후 보호 복구, 절전/네트워크 전환, VPN 및 보안 제품 공존성
- 운영 서명 필터 manifest/공개키 연결, 자동 필터 업데이트, 라이선스 연동
- 코드 서명 인증서 적용 및 정식 배포 패키지

현재 Windows 설치 파일은 개발용이며 코드 서명되지 않았습니다. Windows 프로그램 업데이트 확인/다운로드 UI와 SHA-256 검증 경로는 구현되어 있지만, 새 Windows 개발 릴리스가 main에 게시되기 전에는 앱에서 새 버전을 찾을 수 없습니다. starter filter rules만 포함하므로 정식 광고 차단 필터 배포와 실사용 승인 테스트를 통과한 정식 릴리스가 아닙니다.
