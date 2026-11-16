# Guardian AdBlock Windows — 개발 빌드 안내

Windows 초기 엔진은 공통 DNS 규칙 모듈, IPv4/IPv6 loopback UDP DNS 프록시, 업스트림 응답 검증/대체 DNS 및 Windows 어댑터 DNS 설정 백업·복구 모듈을 포함합니다.

## 개발 환경
- Windows 10/11 x64
- Python 3.11 이상

## 개발 테스트
```powershell
py -m venv .venv
.\.venv\Scripts\Activate.ps1
py -m pip install -r requirements-dev.txt
py -m pytest -q dns_engine windows_engine
py -m compileall -q dns_engine windows_engine
```

## 네트워크 보호 안전성
- 어댑터 DNS 변경 전 IPv4/IPv6의 원래 DNS 주소와 DHCP 상태를 ProgramData 아래 JSON 파일로 원자적으로 백업합니다.
- 일부 어댑터 적용에 실패하면 변경된 어댑터를 역순 복구합니다.
- 정상 중지 시 저장한 DNS 설정을 복구하며, 복구가 일부 실패하면 백업 파일을 지우지 않습니다.
- 실제 Windows DNS 설정 변경은 관리자 권한과 사용자 보호 시작 동작이 필요한 기능입니다. 테스트는 시스템 DNS를 변경하지 않습니다.

아직 Windows 서비스 등록, GUI, 자동 복구 UI, 코드 서명, EXE/설치 프로그램 및 실기기 네트워크 수용 검증은 미완료입니다.
