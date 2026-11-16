# Guardian 이벤트 기반 자동 진행 파이프라인

## 목적

정기적으로 상태를 조회하는 대신 GitHub Actions의 완료 이벤트(`workflow_run: completed`)를 받아 직전 Android 작업 결과를 자동 분류하고, 성공한 경우 산출물을 후속 검증합니다.

## 흐름

1. `Guardian AdBlock Android APK` 워크플로가 Android 단위 테스트, lint, APK 조립, 에뮬레이터 업그레이드 설치·실행, 패키지 정보 및 SHA-256 확인을 수행합니다.
2. 워크플로가 완료되면 `Guardian Event-Driven Continuation`이 자동 실행됩니다.
3. 후속 워크플로는 원본 실행의 작업/단계 결과를 조회하고 성공, 취소/대체, 실행 시작 실패, 단계 실패를 구분합니다.
4. 원본 실행이 성공한 경우에만 `guardian-android-debug` 산출물을 내려받아 APK 존재·크기·ZIP 무결성·SHA-256·검증 요약 파일을 확인합니다.
5. 원본 실행이 실패 또는 취소된 경우 앱 코드 오류라고 단정하지 않고 분류 결과와 원본 실행 링크를 감사 보고서로 남깁니다.
6. 후속 감사 보고서는 `guardian-event-audit-<run-id>` 아티팩트로 30일간 보관합니다.

## 현재 자동화가 수행하지 않는 작업

- GitHub Actions는 빌드/테스트 완료 이벤트에 반응해 검증을 자동 연결하지만, 실패한 애플리케이션 코드를 스스로 수정하거나 새 기능을 설계·구현하지 않습니다.
- AI 코딩 에이전트의 무인 코드 작성·커밋까지 연결하려면 별도 에이전트 런타임과 인증/권한 구성이 필요합니다. 현재 저장소에는 해당 외부 에이전트 자격 증명이나 유료 API 호출을 추가하지 않았습니다.
- GitHub Actions 요금제, 사용량, 결제 설정은 이 파이프라인에서 변경하지 않습니다.
- CI/에뮬레이터 성공은 실제 Android 휴대전화의 VPN 승인, DNS 경로, 광고·트래커 차단 효과, 네트워크 전환 및 장시간 안정성 검증을 대체하지 않습니다.

## 수동 감사

워크플로 화면에서 `Guardian Event-Driven Continuation`을 `workflow_dispatch`로 실행할 수 있습니다. 이 경우 감사할 Android workflow run ID를 입력해야 합니다.

## 관련 워크플로

- [Android APK build and verification](../.github/workflows/android-apk.yml)
- [Event-driven continuation and artifact audit](../.github/workflows/guardian-event-orchestrator.yml)
