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
- `dist/installer/Guardian-AdBlock-Windows-Setup-0.1.0-dev.exe`: 개발용 설치 프로그램

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

현재 Windows 설치 파일은 개발용이며 코드 서명되지 않았습니다. starter filter rules만 포함하므로 정식 광고 차단 필터 배포와 실사용 승인 테스트를 통과한 정식 릴리스가 아닙니다.
