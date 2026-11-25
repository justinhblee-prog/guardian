# Guardian Premium 피싱 스캐너

## 현재 구현 범위

`PremiumPhishingScanPolicy`는 네트워크 연결 없이 URL 구조에서 위험 신호를 분류하는 1차 정책 엔진입니다.

- HTTP/HTTPS URL 형식 검증 및 기본 HTTPS 스킴 보완
- URL 사용자 정보(userinfo) 삽입 감지
- IP 리터럴 호스트 감지
- 퓨니코드 및 혼합 문자 도메인 위험 신호 감지
- 과도한 하위 도메인 및 URL 경로 내 중첩 URL 패턴 감지
- INVALID / LOW / ELEVATED / HIGH 위험도와 탐지 근거 반환

## 보안상 제한

- 이 정책은 평판 DB, Safe Browsing, 실시간 위협 인텔리전스에 질의하지 않습니다.
- 정상(LOW) 결과는 안전 판정이나 악성 사이트 부재를 보증하지 않습니다.
- 현재 앱 화면·공유 인텐트·클립보드와의 사용자 흐름 연결은 아직 구현되지 않았습니다.
- APK에 통합하기 전 Android 단위 테스트와 실제 입력 흐름 검증이 필요합니다.
- 앱 패키지/코드 정적 분석은 이 URL 검사 엔진의 범위가 아닙니다.

## 테스트

추가된 `PremiumPhishingScanPolicyTest`는 일반 URL, 사용자 정보 삽입, IP 호스트, 퓨니코드, 깊은 하위 도메인, 비웹 스킴 및 잘못된 입력을 검증합니다.

저장소 루트에서 Android 검증 스크립트를 실행해 테스트·lint·Debug APK 빌드를 확인해야 합니다. 이번 소스 반영 시점에는 해당 스크립트를 실행하지 않았으므로 통과로 간주하지 않습니다.
