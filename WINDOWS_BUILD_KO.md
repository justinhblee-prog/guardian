# Guardian AdBlock Windows — 개발 빌드 안내

현재 Windows 구현은 Python 기반 공통 DNS 필터 엔진과 로컬 UDP DNS 프록시 초기 버전입니다. Windows DNS 설정 변경, Windows Service 등록, WFP 드라이버, GUI 및 설치 프로그램은 아직 포함하지 않습니다.

## 개발 환경
- Windows 10/11 x64
- Python 3.11 이상

## 개발 테스트
```powershell
py -m venv .venv
.\.venv\Scripts\Activate.ps1
py -m pip install -r requirements-dev.txt
py -m pytest -q dns_engine\test_dns_engine.py windows_engine\test_windows_protection.py
py -m compileall -q dns_engine windows_engine
```

개발 단계에서는 로컬 포트를 5300으로 지정해 테스트할 수 있으며 실제 Windows DNS 설정은 변경하지 않습니다. 정식 설치 전에는 어댑터별 기존 DNS 설정 백업, 서비스 중지, 원상 복구 및 관리자 권한 검증이 필요합니다.
