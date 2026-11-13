# Guardian AdBlock Android — 릴리스 상태

기준 버전: **0.6.4-dev**  
기준일: 2026-11-13

## 현재 개발 범위

현재 저장소의 실제 구현 범위는 Android 앱과 공유 DNS 규칙 파일입니다. 이 저장소에 포함되지 않은 Windows 서비스, 브라우저 확장, Python DNS 엔진은 본 릴리스의 완료 항목으로 계산하지 않습니다.

## 기능 상태

| 영역 | 구현 상태 |
|---|---|
| Android VpnService 기반 보호 | 구현 |
| IPv4 UDP DNS | 구현 및 패킷 경계 회귀 테스트 |
| IPv6 UDP DNS | 구현, 필수 체크섬 검증 |
| 업스트림 UDP DNS | 구현, endpoint/transaction/question 검증 |
| 잘린 응답의 upstream TCP 재시도 | 구현 |
| NXDOMAIN / SERVFAIL | 구현 |
| 공용 차단 규칙 / 사용자 차단·허용 규칙 | 구현 |
| 규칙 입력 검증 및 실행 중 재적용 | 구현 |
| 보호 상태 / DNS 통계 / 최근 활동 | 구현 |
| 프로세스 재시작 후 stale 보호 표시 방지 | 구현 |
| Guardian adaptive launcher icon | 구현 |
| DoH/DoT 및 client-side TCP DNS | 미지원 |
| IPv6 확장 헤더 및 fragment 재조립 | 미지원 |

## 최종 검증 게이트

- [ ] 최신 main 커밋의 Android JUnit 테스트 및 APK 빌드 성공
- [ ] manifest / FGS special-use / 패키지 ID / versionName 검사 성공
- [ ] artifact ZIP 무결성 및 APK SHA-256 확인
- [ ] 실기기 설치 및 VPN 권한 승인
- [ ] 보호 시작·중지와 앱 재시작 상태 검증
- [ ] Wi-Fi↔모바일 데이터 전환 및 장시간 실행 검증
- [ ] IPv4/IPv6 실제 DNS 차단·허용·일반 질의 검증

**릴리스 판정:** 자동화 검증이 통과하더라도 실기기 수용 테스트 전에는 운영/상용 최종판으로 표시하지 않습니다. APK는 개발 설치본으로 명확히 표시합니다.
