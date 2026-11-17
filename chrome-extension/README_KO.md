# Guardian Chrome 확장 프로그램 (Manifest V3)

## 현재 구현 범위

- Chrome Manifest V3 서비스 워커 기반 확장 프로그램 골격
- `declarativeNetRequest` 동적 규칙으로 도메인 차단
- `shared/filter_rules.txt`를 단일 원본으로 사용하고 빌드 스크립트가 확장 프로그램용 `rules.txt`를 생성
- 허용 규칙은 차단 규칙보다 높은 우선순위로 변환
- Node.js 테스트로 Manifest V3 선언, 공유 규칙 동기화, DNR 변환 및 잘못된 규칙 거부를 검사

## 빌드 및 테스트

저장소 루트에서 Node.js 22 이상:

```bash
node scripts/build_chrome_extension.mjs
node scripts/test_chrome_mv3.mjs
```

Chrome에서 개발자 모드로 압축해제된 확장 프로그램을 로드할 때는 `chrome-extension/` 디렉터리를 선택합니다.

## 제한 및 다음 작업

- 현재는 저장소의 공통 기본 규칙만 번들링합니다. Android/Windows의 전체 구독 필터를 자동으로 모두 변환하지 않습니다.
- 서명된 원격 manifest 다운로드, 공개키 고정, 버전 anti-rollback 및 원자적 규칙 교체는 다음 통합 단계입니다.
- 팝업/옵션 UI, 예외 목록 편집, 통계, Chrome Web Store 제출 자료가 아직 없습니다.
- 광고 요청 차단은 DNS 차단과 다르게 브라우저 요청 단계에서 동작하지만, 동일 출처 광고·페이지 요소 숨김은 별도 cosmetic filtering이 필요합니다.
- Firefox/Safari 호환성을 의미하지 않습니다.
