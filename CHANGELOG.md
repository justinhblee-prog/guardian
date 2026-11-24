## 2026-11-24 — DNS 업스트림 진단 스레드 간 오염 차단

- 보호 시작 전 DNS 사전검사, 수동 업스트림 테스트, 실제 앱 DNS packet worker가 공유하던 전역 `lastUpstreamFailure` 값은 동시에 실행되는 작업끼리 실패 원인을 덮어쓸 수 있었습니다.
- 기존 읽기/쓰기 호출부는 유지하면서 backing value를 ThreadLocal 기반 `DnsThreadFailureContext`로 바꿨습니다. `forwardDns()`의 null 초기화는 현재 작업 스레드의 과거 오류만 제거하고, 한 스레드 안의 서버 후보 fallback 및 UDP→TCP 재시도 진단은 그대로 이어집니다.
- 동시에 두 DNS 작업이 서로 다른 실패 원인을 유지하는지, 한 스레드의 clear가 다른 스레드 값에 영향을 주지 않는지 테스트를 추가했습니다. Android 서비스/실제 네트워크 동시성 검증은 미실행입니다.

## 2026-11-24 — 최초 필터 복구 종료 상태 및 캐시 유효성 JVM 검증

- 초기 필터 복구 재시도 예산 소진 시 전용 오류 코드를 저장하고 설정/보호 상태 브로드캐스트를 보내도록 변경했습니다. 설정 화면은 이를 '선택한 필터 캐시 없음 · 내장 기본 규칙으로 제한 보호 중' 상태로 알립니다.
- 일반 필터 업데이트 실패도 현재 선택 프로필과 캐시 프로필 키가 일치하는 유효 캐시가 있을 때만 '이전 목록 유지'라고 표시합니다. 캐시가 없거나 다른 프로필이면 이전 목록이 적용 중이라는 잘못된 인상을 주지 않습니다.
- 실제 FilterUpdatePolicy의 핵심 helper를 복제한 독립 Kotlin/JVM harness PASS 19/19: 초기 복구 강제/자동 업데이트 opt-out, 캐시 프로필 일치/불일치, 재시도 상한, 선택 필터 캐시 보존/비활성화, 오류 지우기 조건, 캐시 due 상태, 3가지 UI 오류 문구.
- 독립 정책 smoke이며 Android Gradle/JUnit, WorkManager 실제 스케줄링, MainActivity 컴파일/화면, 네트워크 복귀·실기기 검증은 미실행입니다.

## 2026-11-24 — 최초 필터 복구 중단 상태 진단 표시

- 초기 필터 복구 작업이 초기 1회 + 최대 5회 재시도 한도를 소진하면 로그만 남기고 UI 상태는 갱신하지 않던 문제를 수정했습니다. Worker가 전용 진단 코드 `initial-filter-cache-retry-exhausted`를 저장하고 설정 상태 브로드캐스트를 발생시킵니다.
- 설정/보호 화면은 현재 선택 프로필의 cache key까지 일치하는 유효 캐시가 있을 때만 '이전 목록 유지'라고 표시합니다. 선택 목록이 없거나 프로필이 달라 캐시를 사용할 수 없다면 '내장 기본 규칙으로 제한 보호 중'이라고 명확히 표시합니다.
- 최초 복구 재시도 한도 소진, 캐시 있음/없음 및 오류 없음 상태의 표시 정책 테스트를 추가했습니다.
- Android Gradle/JUnit·WorkManager 실제 스케줄링·앱 화면 및 네트워크 복구 실기기 검증은 미실행입니다.

## 2026-11-24 — 앱 업데이트 설정 진입점 독립 노출

- Guardian 앱 자체 업데이트는 기존에 설정 > 정보 화면 안쪽에만 있었고 필터 목록 갱신 버튼과 구분되지만 독립 메뉴로는 찾기 어려웠습니다.
- 설정 첫 화면에 `앱 업데이트` 항목을 별도로 추가해 휴대전화 메뉴와 태블릿/넓은 화면 사이드바 모두에서 바로 진입하도록 연결했습니다. `settings-updates`를 화면 복원 경로에도 추가했고, 정보 화면의 기존 바로가기는 유지했습니다.
- 기존 업데이트 확인·APK 다운로드/검증·Android 시스템 설치 화면 흐름을 재사용합니다. Android UI/화면 회귀 검증은 미실행입니다.

## 2026-11-24 — 최초 필터 캐시 자동 복구 및 재시도 상한

- 메인 필터 캐시가 없는 최초 다운로드 실패 또는 선택 프로필과 저장 캐시 프로필이 다른 경우, NetworkType.CONNECTED 조건의 unique one-time WorkManager 복구 작업을 30초 뒤 예약합니다. 네트워크 복구 후 WorkManager exponential backoff가 재시도합니다.
- 최초 복구는 recurring auto-update 설정과 분리되어 사용자가 자동 업데이트를 꺼도 동작합니다. 기존 정상 캐시가 있는 일반 갱신 실패는 예약하지 않습니다.
- 최초 복구는 초기 실행 1회 + 최대 5회 재시도(총 6회)로 제한합니다. 반복 예약은 unique KEEP으로 합치며, 전경 수동 업데이트가 시작되면 대기 작업을 취소하고 다시 실패할 경우에만 재예약합니다.
- 현재 선택 프로필과 cache profile key가 일치하는 경우에만 이전 정상 캐시로 인정합니다. 다른 프로필의 파일은 새 프로필의 업데이트 실패를 보호하지 않습니다.
- 독립 Kotlin/JVM smoke PASS 12/12: opt-out과 강제 최초 복구, 프로필 일치/불일치, 재시도 6회 상한, 일반 periodic work 예산 분리.
- Android Gradle/JUnit·WorkManager 실제 스케줄링·네트워크 복구 에뮬레이터/실기기 검증은 미실행입니다.



## 2026-11-24 — 최초 필터 캐시 실패 자동 복구 작업

- 첫 설치/프로필 전환 등으로 메인 필터 캐시가 없을 때 즉시 다운로드가 실패하면 기존에는 기본 내장 규칙으로 보호만 시작하고, 다음 수동 시도나 주기 작업까지 추가 필터 캐시가 비어 있을 수 있었습니다.
- 메인 캐시가 없는 최초 다운로드 실패에만 네트워크 연결(NetworkType.CONNECTED) 제약의 unique one-time WorkManager 작업을 30초 지연으로 예약합니다. 네트워크가 돌아오면 WorkManager exponential backoff로 재시도하며 중복 예약은 KEEP으로 합칩니다.
- 해당 최초 복구 작업은 사용자가 recurring automatic update를 꺼 두었어도 실행합니다. 일반 주기 업데이트 opt-out은 그대로 존중합니다. 기존 정상 캐시가 있는 업데이트 실패에는 최초 캐시 복구 작업을 예약하지 않습니다.
- 사용자 수동 업데이트가 성공하면 대기 중인 최초 복구 작업을 취소해 중복 다운로드를 막습니다. 정책 테스트를 추가했습니다. Android Gradle/JUnit·실기기/네트워크 전환 검증은 미실행입니다.

## 2026-11-24 — DNS TCP 요청 재전송 예산 상한 테스트

- exact DNS 요청 중복 재생 경로가 공통 `MAX_RETRANSMISSIONS=5` 한도를 실제로 준수하는지 확인하는 테스트를 추가했습니다.
- 원본 요청 재전송 5회는 미확인 응답 세그먼트를 다시 보내고, 6번째 요청 재전송에서는 세션이 종료되며 추가 응답이나 DNS 재조회가 발생하지 않는 시나리오를 고정했습니다.
- Android Gradle/JUnit 실행은 미실행입니다.

## 2026-11-24 — DNS TCP 요청 재전송과 RTO 예산 동기화

- 동일한 원본 DNS 요청 세그먼트가 다시 도착해 응답을 재전송할 때, 기존 구현은 응답 바이트만 복사해 보내고 `lastSentAtMs`·`retransmissionCount`를 갱신하지 않았습니다. 그 결과 RTO가 즉시 뒤따라 중복 송신하거나 재전송 예산을 우회할 수 있었습니다.
- exact request replay도 `markRetransmitted()`를 사용해 공통 retry budget 및 RTO 타이머를 갱신합니다. 미확인 세그먼트 중 하나라도 재시도 한도를 소진했다면 세션을 종료해 무제한 replay를 막습니다. 이미 누적 ACK된 데이터는 기존대로 재생하지 않습니다.
- 원본 요청 재전송 후 1초 시점에는 RTO가 발화하지 않고, 재전송 횟수 증가에 따른 2초 백오프가 재전송 시점부터 적용되는 fake-clock 회귀 테스트를 추가했습니다.
- Android Gradle/JUnit·실기기 검증은 미실행입니다.

## 2026-11-24 — DNS TCP 32비트 sequence wrap-around 회귀 테스트

- 클라이언트 ISN과 서버 ISN을 각각 0xFFFFFFFF 경계에 배치하고 DNS 질의 수신/응답 분할이 32비트 순번 0을 넘어가는 경우를 검증하는 회귀 테스트를 추가했습니다.
- 서버 첫 응답 세그먼트가 sequence `0xFFFFFFFF`에서 시작해 0을 지나고, 누적 ACK가 1199로 wrap된 뒤 첫 outstanding 패킷을 제거하고 다음 세그먼트의 fast retransmit 대상으로 선택하는지 확인합니다.
- 실제 Android Gradle/JUnit은 미실행이며, 독립 Kotlin/JVM 세션 하네스에도 같은 경계 시나리오를 실행할 예정입니다.

## 2026-11-24 — 필수 필터 asset 도메인 길이 상한 일치

- Gradle preBuild validator도 런타임 `FilterRuleParser`와 같이 정규화된 DNS 이름 전체 길이를 253자로 제한하도록 보강했습니다. 라벨별 63자 제한과 연속 점/공백/잘못된 문자 거부는 기존 도메인 정규식이 확인합니다.

## 2026-11-24 — 기본 필터 asset 빌드 전 검증

- `shared/filter_rules.txt`를 Android `main` assets에 추가하는 sourceSet은 있었지만, 파일이 누락·빈 파일·차단 규칙 없는 파일이어도 APK asset 처리 단계까지 진행될 수 있었습니다.
- Gradle `preBuild` dependency로 검증 task를 추가해 파일 존재·비어 있지 않음·최소 한 개의 `block=` 또는 `block-important=` 지시문과 유효한 DNS label 문법을 확인합니다. 조건이 깨지면 원인 파일 경로를 알려주고 빌드를 중단합니다.
- 런타임 응급 규칙 대체는 유지하되, 잘못 패키징된 APK가 출시 산출물로 나오는 것을 빌드 단계에서 먼저 차단합니다. Gradle 실행은 환경 제한으로 미실행입니다.

## 2026-11-24 — DNS 통계 카운터 오버플로/손상값 방어

- 요청·차단·전달·실패·광고·추적·기타 및 형식 오류 DNS 카운터가 단순 `Long + 1`을 사용해 Long.MAX_VALUE에서 음수로 overflow하거나 저장값이 음수일 때 계속 음수로 유지될 수 있던 경로를 보완했습니다.
- 모든 통계 증가에 `DnsStatsCounterPolicy.add`를 사용해 음수 기존값은 0으로 복구하고, 음수 증가량은 무시하며, Long.MAX_VALUE에서 포화하도록 했습니다.
- 일반 증가, 0 증가, 음수 저장값 복구, Long 경계 포화, 음수 증가량 테스트를 추가했습니다. Android SharedPreferences/JUnit 통합은 미실행입니다.

## 2026-11-24 — TCP 응답 주소 길이 입력 검증

- `DnsTcpPacket.response()`가 source 주소 길이만 확인하고 destination 주소 길이는 `arraycopy` 중 예외에 맡기던 부분을 보강했습니다. IPv4는 source/destination 모두 4바이트, IPv6는 모두 16바이트여야 합니다.
- 잘못 구성된 Parsed IPv4 destination 및 IPv6 source 주소 길이를 거부하는 테스트를 추가했습니다.

## 2026-11-24 — TCP 응답 패킷 IP 버전별 길이 상한 수정

- `DnsTcpPacket.response()`가 IPv4와 IPv6에 동일한 TCP payload 상한 65,515바이트를 적용하던 문제를 수정했습니다. IPv4는 IP 헤더까지 포함하는 16비트 total_length이므로 TCP payload 최대 65,495바이트, IPv6는 TCP 헤더를 포함한 payload_length 한도에 따라 65,515바이트를 허용합니다.
- IPv4/IPv6 각각 최대 허용 길이에서 생성된 응답의 IP 길이 필드와 재파싱 성공, 1바이트 초과 시 거부를 확인하는 테스트를 추가했습니다. 현재 TCP 세션은 MSS 1,200바이트로 분할하므로 이 수정은 코덱 경계 검증을 강화합니다.
- Android Gradle/JUnit·기기 패킷 검증은 미실행입니다.

## 2026-11-24 — DNS TCP 재전송 스케줄러 종료 동기화

- VPN packet loop 종료 시 재전송 스케줄러 `shutdownNow()`만 호출하던 것을 보강해 최대 500ms 동안 작업 종료를 기다린 뒤 TUN descriptor 정리로 넘어가도록 했습니다.
- 종료 대기 중 인터럽트가 발생하면 인터럽트 상태를 복원합니다. 이는 서비스 정지 직후 background RTO worker가 descriptor에 늦게 쓰는 경합을 줄이기 위한 정리입니다.
- Android service lifecycle 및 실기기 네트워크 검증은 미실행입니다.

## 2026-11-24 — 모바일 하단 내비게이션 인셋 중복 제거

- 휴대전화 화면에서 root layout과 bottom navigation이 모두 navigation-bar bottom inset을 padding에 반영하던 중복을 제거했습니다.
- 휴대전화/컴팩트 태블릿(839dp 이하)은 하단 메뉴만 bottom inset을 소유하고, 840dp 이상 내비게이션 레일 레이아웃은 root가 소유합니다. 상단 status bar inset 동작은 유지합니다.
- Android 30 이상 systemBars와 이전 API systemWindowInset 경로 모두 동일한 단일 소유 정책을 사용합니다.
- 393/839/840/1200dp 네 경계 테스트를 추가했습니다. 실제 폰/태블릿 화면 렌더링 검증은 미실행입니다.

## 2026-11-24 — DNS TCP RST 순번 검증

- 기존 세션 매니저는 4-tuple이 일치하기만 하면 TCP RST의 sequence 검증 없이 세션을 즉시 삭제했습니다.
- 현재 기대 수신 순번(clientNext)과 일치하는 RST만 세션을 종료하도록 보강했습니다. 지연된 오래된 RST는 세션을 유지하고 무응답 처리합니다.
- stale RST 이후 기존 DNS 요청 재전송이 계속 응답을 재전송하는 테스트와 정확한 순번 RST가 세션을 종료하는 테스트를 추가했습니다.
- Android Gradle/JUnit·실기기 검증은 미실행입니다.

## 2026-11-24 — DNS TCP ACK 진행 시 RTO 타이머 재시작

- 여러 TCP 세그먼트를 한 번에 보낸 뒤 첫 세그먼트 ACK가 도착했을 때 다음 세그먼트의 최초 송신 시각이 이미 RTO를 넘었다는 이유로 즉시 재전송하던 문제를 수정했습니다.
- 누적 ACK가 앞으로 진행하면 새 oldest outstanding segment의 RTO 기준 시각을 ACK 수신 시각으로 재설정합니다. ACK가 전혀 진행하지 않는 손실 상황에서는 기존 지수형 backoff가 유지됩니다.
- 부분 ACK/완전 ACK 진행 후 다음 세그먼트가 새 1초 RTO를 기다리는지 확인하도록 deterministic clock 테스트를 보정했습니다.
- 스케줄러의 출력 람다를 명시적으로 바꾸고 background writer 실패를 서비스 종료 진단에서 우선 보존합니다.

## 2026-11-24 — DNS TCP 타이머 기반 재전송(RTO)

- 기존 중복 ACK 빠른 재전송만으로는 마지막 응답 세그먼트 손실처럼 추가 ACK가 도착하지 않는 경우 복구할 수 없었습니다.
- 세션별 oldest outstanding server packet에 송신 시각과 재전송 횟수를 기록하고, 1초 시작·2/4/8초 지수형 backoff, 최대 5회 재전송 후 세션 제거 정책을 추가했습니다. 한 세션에서 가장 오래된 미확인 세그먼트만 재전송합니다.
- 빠른 재전송도 같은 타이머 상태를 갱신해 중복 ACK 직후 RTO가 곧바로 중복 송신하지 않도록 했습니다. 누적 ACK로 확인된 패킷은 재전송 목록에서 제거됩니다.
- GuardianVpnService에 250ms 주기 daemon scheduler를 연결하고 TUN output lock으로 패킷 루프와 쓰기를 직렬화합니다. packet loop 종료 시 scheduler를 정리합니다.
- 테스트: 1/2/4/8초 backoff, 5회 한도, 세션 종료, ACK 확인 후 oldest packet timer 전환. Android Gradle/JUnit·emulator·실기기 검증은 미실행입니다.

## 2026-11-24 — DNS TCP 윈도 업데이트와 중복 ACK 구분

- 수신 윈도가 바뀐 ACK도 중복 ACK로 계산할 수 있던 경로를 바로잡았습니다. 빠른 재전송은 ACK 번호와 광고 윈도가 모두 동일하고 payload/FIN이 없는 연속 ACK에서만 계산합니다.
- payload가 있거나 윈도 값이 바뀌면 중복 ACK 연속 카운트를 초기화해 일반 윈도 업데이트와 빠른 재전송 조건을 분리했습니다.
- 첫 ACK에서 윈도 값 변경 후 동일 윈도 ACK가 세 번 도착할 때에만 재전송되는 회귀 테스트를 추가했습니다.
- Android Gradle/JUnit 및 실기기 테스트는 미실행입니다.

## 2026-11-24 — DNS TCP 빠른 재전송 중복 발사 방지

- 중복 ACK 카운터가 임계값 3에 고정된 뒤 매 ACK마다 같은 패킷을 반복 재전송하던 동작을 막았습니다. 동일 누적 ACK 번호마다 세 번째 중복 ACK에서 한 번만 빠른 재전송하며, ACK 진행 시 카운터가 초기화됩니다.
- 네 번째 중복 ACK에는 같은 응답 세그먼트를 다시 보내지 않는 회귀 검증을 추가했습니다.

## 2026-11-24 — DNS TCP 중복 ACK 빠른 재전송

- DNS TCP 응답을 보낸 뒤 첫 세그먼트가 손실되면, 클라이언트의 중복 누적 ACK 3회에도 기존 세션 매니저가 재전송하지 않던 문제를 보완했습니다.
- 아직 누적 확인되지 않은 데이터가 있고 payload/FIN 없는 동일 누적 ACK가 세 번 도착하면 가장 오래된 미확인 응답 세그먼트를 다시 전송합니다. ACK가 앞으로 진행되면 중복 ACK 카운터를 초기화합니다.
- 첫 세그먼트 재전송과 부분 누적 ACK 이후 다음 세그먼트 재전송을 검증하는 테스트를 추가했습니다.
- 이는 TCP fast retransmit 보완이며 타이머 기반 RTO 전체 구현을 대체하지 않습니다. Android Gradle/JUnit·실기기 검증은 미실행입니다.

## 2026-11-24 — DoT/DoH 업스트림 실패 원인 구분

- DoT 응답 길이 오류와 DNS transaction ID/질문 불일치는 각각 구체적인 진단값을 남기도록 수정했습니다.
- DoH TLS host 및 HTTP 요청 검증, HTTP 상태·헤더·Content-Type·본문 framing 실패, DNS 응답 transaction ID/질문 불일치도 구분해 기록합니다. 실제 TLS handshake/소켓 오류는 예외 유형 기반 안전 요약을 유지합니다.
- TCP upstream의 잘못된 query length 진단도 추가했습니다. 연결·호스트 인증·리다이렉트 거부·본문 크기 검증 동작 자체는 변경하지 않았습니다.
- 정적 소스 반영만 수행했으며 Android Gradle/JUnit·실기기 네트워크 검증은 미실행입니다.

## 2026-11-24 — DNS TCP 회귀 테스트 정밀화

- DNS TCP 최대 프레임 병합 회귀 테스트가 resolver 호출 횟수만 확인하던 것을 보완해 첫 번째 프레임이 정확히 65,535바이트로 전달되고, 같은 세그먼트의 두 번째 프레임 바이트가 원본 질의와 일치하는지 직접 비교하도록 했습니다.
- DNS TCP ACK 검증부에 중복 문구가 들어간 주석을 바로잡았습니다. 실행 로직 변경은 없습니다.
- Android Gradle/JUnit 실행은 미실행입니다.

## 2026-11-24 — DNS TCP 프레임 병합 경계 수정

- 최대 크기 DNS TCP 프레임이 앞선 세그먼트의 대기 데이터와 결합해 완성되고, 같은 후속 세그먼트에 다음 질의 프레임까지 포함되는 정상 스트림에서 전체 임시 버퍼 크기만으로 RST하던 처리를 수정했습니다.
- 입력 세그먼트와 기존 잔여 바이트를 결합해 프레임 단위로 처리한 뒤, 아직 완성되지 않은 단일 잔여 프레임에만 65,537바이트 상한을 적용합니다. 응답 큐의 기존 상한은 유지합니다.
- 최대 65,535바이트 DNS 메시지의 마지막 조각과 다음 DNS 질의를 한 세그먼트로 보내 두 질의가 모두 처리되는 회귀 테스트를 추가했습니다.
- Android Gradle/JUnit 및 기기 실행 검증은 미실행입니다.

## 2026-11-24 — DNS TCP ACK 수용 순번 엄격화

- 미래 순번뿐 아니라 현재 수신 순번과 다르고 마지막으로 수락한 세그먼트의 정확한 재전송도 아닌 오래된 세그먼트의 ACK 필드도 무시하도록 보강했습니다.
- 정상 요청 재전송은 그대로 응답 재전송 트리거로 허용하되, 무관한 stale/future ACK가 미확인 DNS 응답 데이터를 제거할 수 없도록 했습니다.
- 회귀 테스트에 미래 순번 ACK와 오래된 비중복 ACK를 연속 전송한 뒤 원래 요청 재전송 시 모든 미확인 응답 세그먼트가 보존되는 경우를 추가했습니다.
- Android Gradle/JUnit 및 실기기 검증은 미실행입니다.

## 2026-11-24 — DNS TCP out-of-order ACK 처리 방어

- 클라이언트의 미래 TCP 순번(out-of-order) 세그먼트는 현재 수신 순서 밖의 패킷이므로 ACK 필드를 처리하기 전에 거부하도록 수정했습니다.
- 종전에는 미래 순번 패킷이 서버 응답의 누적 ACK를 먼저 반영해 재전송 대기 패킷을 삭제할 수 있었습니다. 해당 경로에서 응답 데이터가 유실될 수 있는 회귀 테스트를 추가했습니다.
- 검증 범위: 소스·테스트·문서 원격 반영. Android Gradle/JUnit, lint, emulator, 실기기 및 APK 검증은 미실행입니다. GitHub Actions는 실행하지 않았습니다.

## 2026-11-24 — DNS TCP 수신 윈도 순서 검증 강화
- 클라이언트의 다음 수신 시퀀스 위치와 일치하는 세그먼트만 광고 수신 윈도 업데이트에 반영하도록 제한했습니다.
- 미래/순서가 어긋난 ACK가 윈도 업데이트 순서를 선점해 정상 ACK의 응답 전송을 막는 상황을 방지합니다.
- 미래 시퀀스 ACK 뒤에 정상 윈도 업데이트가 응답 데이터를 배출하는 회귀 테스트를 추가했습니다.
- 원격 소스·테스트·변경 이력 반영만 확인했습니다. Android Gradle/JUnit 및 기기 실행 검증은 미실행입니다.

## 2026-11-24 — DNS TCP 누적 ACK 이후 지연 중복 응답 방지
- 클라이언트가 DNS 응답 전체를 누적 ACK한 뒤 원래 요청 세그먼트가 늦게 중복 도착하면, 이미 확인된 응답 패킷을 다시 보내던 동작을 수정했습니다.
- 재전송 요청에는 미확인(outstanding) 서버 패킷만 재전송하고, 모두 ACK된 경우 현재 수신 위치를 담은 ACK만 반환합니다.
- 누적 ACK 후 지연 중복 요청이 응답 payload를 재생하지 않는 회귀 테스트를 추가했습니다.
- 소스/테스트 정적 반영만 수행했습니다. Android Gradle/JUnit, 에뮬레이터 및 실기기 검증은 미실행이며 Actions 실행은 시작하지 않았습니다.

## 2026-11-24 — DNS TCP 초기 ACK 검증 강화
- TCP DNS 핸드셰이크 직후 첫 데이터 패킷은 SYN-ACK의 다음 서버 시퀀스 번호를 정확히 확인한 ACK만 허용하도록 수정했습니다.
- 기존에는 미래 ACK는 거부했지만 오래된 ACK는 허용해, 핸드셰이크가 완결되지 않은 상태에서 DNS 질의가 resolver까지 전달될 수 있었습니다.
- 오래된 ACK로 전송된 DNS payload 거부 및 올바른 ACK 재전송 허용 테스트를 추가했습니다.
- Android Gradle/JUnit 실행 및 네트워크 실기기 검증은 미실행입니다.

## 2026-11-24 — IPv6 확장 헤더 파서 구조 재확인
- 추가 조사 결과 `Ipv6ExtensionHeaderParser`는 누락된 것이 아니라 `DnsTcpPacket.kt` 하단에 이미 정의되어 UDP/TCP DNS 경로가 공유하고 있었습니다.
- 별도 파일로 중복 구현을 추가한 시도는 즉시 되돌려 중복 선언을 제거했습니다. 기존 공유 파서는 유지합니다.
- 이 항목은 컴파일 오류 수정이 아니며, Gradle/JUnit 빌드도 아직 실행하지 않았습니다.

## 2026-11-24 — 업스트림 UDP DNS 수신 크기 보완
- 업스트림 UDP DNS 응답 수신 버퍼를 임의의 4,096바이트에서 DNS 메시지 최대 크기인 65,535바이트로 확장했습니다. 큰 응답이 TC 비트 없이 잘려 전달되는 문제를 방지합니다.
- VPN 클라이언트에 반환하는 UDP DNS 응답은 기존처럼 IPv6 최소 MTU 안전 한도 1,232바이트를 유지하며, 초과 응답은 TC 응답으로 TCP 재시도를 유도합니다.
- 실제 UDP 소켓 수신 및 실기기 DNSSEC/대용량 응답 검증은 아직 실행하지 않았습니다.

## 2026-11-24 — Android DNS 최대 크기 TCP/DoT/DoH 응답 지원
- DNS-over-TCP 세션의 메시지 상한을 DNS 16비트 길이 필드 최대값(65,535바이트)으로 확장하고, 최대 프레임 1개를 처리할 수 있도록 수신 버퍼를 65,537바이트로 제한했습니다.
- 응답은 IPv6 최소 MTU보다 작은 1,200바이트 TCP payload 단위로 분할해 전송합니다.
- 업스트림 TCP DNS, DNS-over-TLS, DNS-over-HTTPS의 질의/응답 길이 제한도 65,535바이트로 맞췄습니다. UDP 응답 버퍼는 TC 응답 후 TCP 재시도 동작을 유지하도록 변경하지 않았습니다.
- 4KB 초과 TCP DNS 응답 분할 및 DoH 요청/응답 처리 회귀 테스트를 추가했습니다.
- Android Gradle/JUnit 실행, 에뮬레이터 및 실기기 네트워크 검증은 아직 수행하지 않았습니다.

# Changelog

## 2026-11-24 — 정식 updater 허용 포트 제한
- 공식 HTTPS 호스트라도 기본 포트(443) 외 포트로 리디렉션하면 연결하지 않도록 제한했습니다.
- Kotlin/JVM smoke 통과: 공식 GitHub asset host, HTTP 차단, 비허용 host, userinfo, 비표준 포트 차단.

## 2026-11-24 — 정식 updater 리디렉션 사전 차단
- HttpURLConnection 자동 리디렉션을 끄고 HTTPS 3xx Location을 직접 따라가도록 변경했습니다. 다음 호스트에 연결하기 전에 URL scheme/host allowlist를 검사하므로 허용되지 않은 외부 호스트에는 요청하지 않습니다.
- 최대 5회 리디렉션을 허용하며, HTTPS가 아닌 URL·userinfo 포함 URL·허용되지 않은 host는 연결 전에 거부합니다.

## 2026-11-24 — 정식 updater 버전 태그 입력 상한
- 정식 Release semver 비교는 각 숫자 구성요소를 최대 9자리로 제한하고, 비정상적으로 큰 숫자 태그는 stable 후보에서 제외합니다.
- Kotlin/JVM smoke 통과: v1.10.0/v1.9.9 숫자 정렬, 메이저 버전 비교, prerelease 제외, 과도한 숫자 태그 거부.

## 2026-11-24 — 정식 Android 업데이트 검증 강화
- APK 및 manifest 다운로드의 HTTPS 리디렉션 최종 호스트를 GitHub 공식 asset 호스트로 제한했습니다. 예상하지 못한 외부 호스트로 이동하면 다운로드/검증을 중단합니다.
- GitHub Release 응답 순서가 semver 순서와 다를 수 있어, 정식 업데이트 후보를 버전 태그의 숫자 비교로 정렬한 뒤 최신 stable release manifest를 검증합니다.
- 설치 순서는 manifest의 서명된 versionCode를 기준으로 판단하며, Release tag와 manifest versionName 일치도 확인합니다.
- 독립 Kotlin/JVM smoke 통과: semver 정렬과 APK redirect allowlist. 실제 HTTP 리디렉션, APK 다운로드/서명/설치 및 Android Gradle/JUnit 검증은 미완료입니다.

## 2026-11-24 — 무료 보호 시작 진단의 긴급 규칙 fallback 표시 개선
- `filter_rules.txt`가 누락되어 내장 긴급 DNS 규칙으로 시작한 경우, 기존 진단 요약이 이를 일반적인 확인 불가 상태로 숨길 수 있던 문제를 수정했습니다.
- 지원 진단에는 내장 긴급 규칙 사용 및 기본 필터 파일 복구 필요를 표시하고, 예외 클래스/원시 오류 문자열은 노출하지 않습니다.
- 광고·추적/보안/Android 앱 광고 선택 필터의 누락 상태도 함께 표시하며, 임의 도메인 문자열은 계속 허용 목록 밖으로 걸러냅니다.
- 실제 Android Gradle/JUnit 및 에뮬레이터 검증은 아직 미실행입니다. 변경 함수와 경계 시나리오는 독립 Kotlin/JVM smoke로 통과했습니다.

## 2026-11-24 — Android 무료 기본 제품 복구 트랙
- 라이선스 활성화 화면의 명시적 Premium 복귀 버튼과 system Back 동작을 추가했습니다. 첫 Back은 IME를 닫고, 다음 Back은 Premium 화면으로 복귀하며, 화면 재생성 시 복귀 대상을 보존합니다.
- filter_rules.txt 누락 시 보호 시작 전 검사와 VPN 서비스 내부 규칙 로더 모두 10개 내장 긴급 DNS 규칙으로 전환하도록 보강했습니다. 이때 제한 모드와 원인 코드를 표시하며, 정상 asset 패키징 검증은 계속 필수입니다.
- Android VPN emulator E2E에 활성화 화면의 키보드 닫기, system Back, 명시적 복귀 버튼 검증을 추가했습니다.
- 변경된 Android 소스/테스트는 아직 Gradle/JUnit 및 에뮬레이터에서 실행되지 않았습니다. 이 브랜치 커밋의 APK는 아직 생성·검증되지 않았습니다.

## 2026-11-23 — Windows 조용한 DNS 실행 및 반응형 설정 탐색
- Windows DNS 어댑터 조회·변경에 사용하는 PowerShell 실행에 CREATE_NO_WINDOW 및 STARTF_USESHOWWINDOW를 적용해 보호 시작/중지 중 콘솔 창이 표시되지 않도록 수정했습니다.
- Windows 설정 카테고리는 항목 선택만으로 자동 축소되지 않으며, 사용자가 접기 버튼을 누를 때만 아이콘 레일로 전환됩니다.
- Android 휴대폰 설정은 좌우 분할/아이콘 레일을 제거하고, 전체 폭 카테고리 목록과 전체 폭 상세 화면을 순서대로 표시하도록 변경했습니다. 태블릿은 분할 레이아웃을 유지하되 카테고리 선택만으로 자동 축소하지 않습니다.
- Windows 기본 창 크기를 최대 1200×760으로 조정하고 화면 크기에 맞춰 축소되도록 변경했습니다.
- Windows PowerShell 숨김 실행과 설정 메뉴 유지 동작에 회귀 테스트를 추가했습니다. CI/에뮬레이터 검증 전에는 실행 확인으로 간주하지 않습니다.

## 2026-11-23 — 개발 버전 기준값 단일화 및 회귀 방지
- Android VPN E2E가 이전 개발 APK의 `앱 제외` 메뉴를 현재 `앱 제외 관리`와 구분하도록 접근성 트리 기반 메뉴 별칭 해석과 회귀 테스트를 추가했습니다.
- 이번 수정 전 런타임 검증은 VPN 시작 전 메뉴 탐색에서 중단됐으며, VPN 차단 동작의 통과 근거로 사용하지 않습니다.
- Android 개발 버전 계산기의 기본 기준을 마지막 개발 릴리스 `0.7.48-dev / versionCode 61`로 맞췄습니다.
- CI workflow에서 별도 버전 기준값을 중복 전달하지 않고, 계산기의 단일 기준값을 사용하도록 변경했습니다.
- 회귀 테스트가 계산기 기준에서 산출되는 다음 버전(`0.7.49 / versionCode 62`)과 Android Gradle fallback의 일치를 검사하도록 추가했습니다.
- 이 변경은 버전 기준값의 일관성을 검사하는 자동화이며, APK 빌드·실기기 설치·업데이트 검증 완료를 의미하지 않습니다.

## v0.7.10-dev — DNS fallback and upgrade verification (2026-11-16)
- Retry the next configured DNS resolver when an upstream returns a matching SERVFAIL or REFUSED response instead of treating that error packet as a usable answer.
- Require a successful A-record resolution before installing DNS-only VPN routes; the connectivity diagnostic now uses the same success criteria.
- Add regression coverage for retryable SERVFAIL/REFUSED versus a valid NXDOMAIN answer.
- Bump Android versionCode to 23 and versionName to 0.7.10-dev; CI verifies an in-place signed upgrade from v0.7.9-dev.
- CI run #651: unit tests and lint passed; emulator install/upgrade/launch verification is in progress at this changelog update.

## 2026-11-15 — Android integration
- Merge verified Android license device-release and wide-tablet dashboard changes (PR #54/#55).
- Follow-up license mutation serialization and server-revocation handling is under CI in PR #56.


## v0.7.4-dev — Filter cache rollback safety (2026-11-14)
- Replace downloaded filter caches with a backup-preserving swap; if metadata persistence fails, restore the previous known-good cache.
- Recover a cache interrupted between the backup and replacement moves before cache reads and cache-validity checks.
- Add JVM regression tests for successful replacement, metadata failure rollback, and interrupted-swap recovery.
- CI verification is running for this change; physical-device behavior remains unverified.

## v0.7.3-dev — Android protection reliability candidate (2026-11-14)
- Prevent optional security and Android-ad filter switches from changing while their cache snapshot is being downloaded and validated.
- Reapply the locked state when the Protection screen is recreated during an active filter update, and clear stale view references when switching tabs.
- Keep filter update feedback synchronized across the Protection and Filters screens.
- Automatically disable optional lists only when their first download fails and no last-known-good cache exists; preserve existing valid caches on update failure.
- CI release candidate increments Android versionCode to 16 and versionName to 0.7.3-dev.
- Limitations remain: no autonomous DNS-over-TCP retransmission timer/partial-ACK recovery, no IPv6 extension-header/IP-fragment reassembly, and no real-device acceptance evidence yet.


## v0.4.0
- Local UDP/TCP DNS development proxy with block/allow rules and bounded TTL cache.

## v0.4.1-dev — Windows protection foundation
- Added Windows DNS adapter inspection, DPAPI-protected backup, restore and explicit IPv4 loopback configuration commands.
- Added a pywin32 Windows Service host that supervises the DNS engine child process and checks the localhost TCP listener before reporting startup.
- Added a hard safety guard that refuses IPv4 activation when IPv6 DNS servers are configured.
- Added unit tests for safe import behavior, IPv6 bypass refusal, missing IPv4 adapter refusal and port-53 config generation.
- Not a complete Windows system protection release: service hosting, IPv6 listener, lifecycle recovery and real Windows acceptance testing remain outstanding.

## v0.5.0-dev — Android VPN implementation scaffold
- Added native Android Kotlin project using VpnService.
- Added IPv4 UDP DNS packet parsing and synthetic NXDOMAIN responses for embedded blocked domains.
- Added protected upstream DNS forwarding to 1.1.1.1 for non-blocked domains.
- Added foreground notification and start/stop controls.
- Added JVM unit tests for domain matching, DNS parsing, and IPv4 UDP parsing.
- Not production-ready: Android device validation, full common-filter integration, IPv6/TCP DNS, and dynamic lists remain open.

## v0.5.1-dev — Android DNS hardening
- DNS 질의 헤더를 검증해 응답 패킷, 비표준 opcode, 복수 질문을 거부합니다.
- NXDOMAIN 응답은 검증된 단일 질문만 포함하고 RD 비트를 보존합니다.
- IPv4 조각화 패킷과 잘못된 UDP 길이를 거부합니다.
- Android 단위 회귀 테스트를 추가했습니다.

## v0.6.0-dev — Common filter rules integration
- Added canonical cross-platform `shared/filter_rules.txt` rule format.
- Android loads shared block/allow rules from packaged assets.
- Windows system DNS configuration enables the same shared rule source.
- Python DNS engine validates and loads shared rules; allow rules take precedence.
- Added regression tests for shared rule loading and allowlist precedence.

## v0.6.0-dev — verification hardening (2026-11-13)
- Windows activation now requires a decryptable DPAPI backup and a live localhost DNS NXDOMAIN probe before adapter changes.
- Partial Windows adapter activation failures trigger rollback from the protected backup.
- Android shared-rule loading fails closed when no valid block rule exists; VPN startup fails closed if rules or VPN establishment fail.
- Added Windows regression coverage and platform build/verification helper scripts.
- Android/Windows physical integration and installer packaging remain release gates, not claimed as completed.


## 2026-11-13 — Android upstream DNS response validation
- Validate upstream DNS transaction ID, QR/opcode, question count, and exact question bytes before returning a response to the VPN client.
- Reject truncated replies because TCP DNS fallback is not implemented yet.
- Verify the UDP reply originates from the configured upstream resolver endpoint.
- Added Android JUnit regression coverage for mismatched/spoofed response rejection.

### Additional verification hardening
- Added regression coverage for truncated upstream DNS replies, mismatched question fields, and inconsistent IPv4/UDP lengths.
- Added a standalone Kotlin DNS-core compile/smoke-check script (8 assertions); this is supplemental and does not replace Android Gradle/JUnit or device testing.
- Added repository ignore rules and removed transient pytest cache files from the release archive.

### Windows rollback safety hardening
- Before changing adapter DNS, verify that the DPAPI backup includes a matching snapshot for every current IPv4 adapter (interface index and alias).
- Refuse activation before any `netsh` changes when a rollback snapshot is absent or incomplete.
- Added two regression tests for missing IPv4 snapshots and newly present adapters not covered by the backup.

## 2026-11-13 — Browser extension filter hardening
- Reduced custom dynamic filter rules to 4,000 and reserved 1,000 dynamic-rule slots for allowlist entries, keeping the combined total within the standard 5,000-rule quota.
- Added strict hostname-label validation and normalized/deduplicated allowlist entries.
- Added 15-second filter download timeout, 5 MB response-size limit, HTTPS redirect validation, and credential-free HTTPS URL validation.
- Reject an empty/unsupported downloaded filter before replacing active rules; the previous working rules remain active.
- Turning off the custom filter now removes only custom filter rules while preserving user allowlist rules.
- Added five Node.js regression tests for parsing, validation, rule quotas, disabling filters, and safe replacement.
- Updated the browser extension manifest version to 0.6.0.


## 2026-11-13 — Browser filter download memory-safety hardening
- Enforce the 5 MB filter-list cap during streamed reads and cancel the response immediately when exceeded, rather than buffering an oversized body before checking its size.
- Keep options-page allowlist validation consistent with background DNS-label validation.
- Add regression tests for oversized streamed responses and HTTPS-to-HTTP redirects.


## 2026-11-13 — Least-privilege browser filter permissions
- Removed broad install-time `<all_urls>` host access from the Chrome extension.
- Added optional HTTPS host permissions; the options page requests access only to the exact origin selected for the custom filter after the user clicks Update.
- The background worker verifies that permission before downloading; missing permission leaves active rules untouched.
- Reject cross-origin HTTPS redirects so a filter source cannot silently expand the extension's host access.
- Added three Node.js regression tests for optional permission scope, missing permission, and cross-origin redirect rejection.

### Security hardening — 2026-11-13
- Centralized filter URL validation in the background worker.
- Normalize filter URLs and remove fragments before saving.
- Check optional host permission before saving an enabled filter configuration, preventing a denied permission from leaving a broken enabled state.
- Added regression tests for insecure schemes, embedded credentials, and URL normalization.

## v0.6.1-dev — Android user-managed rules and status
- Added on-device custom block and allow lists to the Android app, with hostname validation, duplicate normalization, and a 1,000-entry cap per list.
- Persisted user rules locally and reload them into the active VPN service without restarting the tunnel.
- Added visible protection state and service failure messages, with an app-scoped status broadcast.
- Made rule replacement atomic by swapping one immutable block/allow snapshot; invalid rule replacements keep the previously active rules.
- Added Kotlin core smoke and JUnit regression coverage for custom rule normalization, allow precedence, and failed reload retention.
- Bumped Android app version to 0.6.1-dev (versionCode 8).
- Full Android Gradle/JUnit build, APK packaging, and physical-device validation remain unverified because this workspace has no Android SDK/Gradle installation and cannot resolve the Gradle distribution host.


## v0.6.2-dev — Android upstream failure behavior
- When an unblocked DNS query cannot obtain a valid upstream UDP response, the VPN now returns a validated DNS SERVFAIL response instead of silently dropping the query and leaving apps waiting for a timeout.
- Added a reusable validated DNS error-response builder, preserving opcode, RD and CD while setting QR/RA and clearing answer sections.
- Added JUnit and standalone Kotlin smoke regression coverage for SERVFAIL flags, section counts, and malformed-query rejection.
- Bumped Android app version to 0.6.2-dev (versionCode 9). Android Gradle/JUnit build, APK creation and physical-device verification remain pending.


## v0.6.3-dev — Android IPv6 DNS and upstream TCP fallback (2026-11-13)
- Added a dedicated IPv6 virtual DNS endpoint and /128 route alongside the existing IPv4 DNS endpoint.
- Added strict IPv6 UDP/53 parsing, mandatory UDP checksum validation, extension-header/fragment rejection, and response checksum generation.
- Added upstream DNS-over-TCP retry when a UDP response is truncated (RFC 7766 length framing, bounded connect/read timeout, transaction/question validation).
- Added standalone Kotlin regression checks for IPv6 query parsing, generated responses, and invalid checksum rejection.
- Client-side DNS-over-TCP interception, IPv6 extension-header/fragment reassembly, DoH/DoT, Android APK build, and device validation remain outstanding.

## 2026-11-13 — Android packet-loop resilience and IPv4 length validation
- Coalesced status broadcasts to at most one UI refresh per 500 ms while persisting every DNS event.
- Published a clear inactive state if the VPN packet-processing thread exits unexpectedly.
- Added explicit IPv4 TUN buffer-bound checks and required the IPv4 total-length field to match the complete packet length.
- Added a regression test for oversized caller lengths and trailing packet bytes.
- The IPv4 length hardening is awaiting a CI run on the latest main commit; do not treat it as part of the previously verified APK until that run succeeds.


## 2026-11-13 — Rules tab feedback isolation
- Separated custom-rule validation/save feedback from the dashboard VPN status view.
- Prevented status refresh broadcasts from replacing rule-editor success and validation messages.
- Verified with Android unit-test task and debug APK CI build (run 43421570913).


## v0.6.5-dev — Android lifecycle, DNS validation and release polish (2026-11-13)
- Confirm active protection only when the current app process owns a live VPN tunnel; persisted stale flags no longer make the dashboard claim protection is running after process restart.
- Show a dedicated live protection badge and disable the start action while the tunnel is active.
- Reject DNS wire QNAME labels containing literal dot bytes to prevent ambiguous conversion into dotted hostnames used for domain matching.
- Add regression tests for ambiguous DNS labels and case-insensitive QNAME response matching while requiring exact QTYPE/QCLASS.
- Add Guardian adaptive and legacy launcher icons.
- Bump Android version to 0.6.5-dev (versionCode 12).
- Strengthen CI with VPN manifest/foreground-service declaration checks, launcher asset checks, APK package identity/version validation, SHA-256 and artifact upload.
- Real-device VPN lifecycle and network-transition acceptance remain required before production release.


## v0.6.5-dev — stale protection-state recovery (2026-11-13)
- Extracted a testable protection-state guard: persisted state alone never represents a live VPN session.
- When the dashboard detects a stale active flag after process restart, it now clears the persisted active flag and stores the recovery message.
- User rule saves now request live rule reload only when the current process actually owns an active VPN tunnel; stale sessions save rules for the next start instead.
- Added two regression tests for stale persisted state and live tunnel requirements.


## v0.7.1-dev — AdGuard-style filtering and diagnostics (2026-11-13)
- Identified the previous effective-blocking gap: the built-in baseline contained only ten domains, and no downloaded AdGuard DNS filter or Adblock rule parser existed.
- Added a DNS-compatible Adblock/hosts parser for anchored domain block rules, exception rules, hosts entries, and badfilter handling. Unsupported contextual/rewriting modifiers are ignored rather than guessed.
- Added download, validation, app-private caching, safe cache replacement, and configurable 6h/12h/24h/7-day app-launch updates for selectable official DNS filter profiles.
- Added optional HaGeZi Threat Intelligence malware/phishing list.
- Added upstream DNS choices (Cloudflare, Google, Quad9, AdGuard DNS, AdGuard Family DNS, custom IPv4/IPv6 literals), installed-app VPN exclusions, live DNS activity/diagnostics, and log reset.
- Added source-isolated badfilter composition and regression tests so one filter list cannot cancel another list's matching domain rule.
- Added optional direct DNS capture for common public resolvers. Because Android VPN routes are IP-based rather than port-based, the UI exposes this as an advanced option and warns that non-DNS traffic to those resolver IPs may also be captured.
- Optimized block/allow matching to use bounded DNS-label suffix lookups instead of scanning the full downloaded list for every query.
- Added an in-process DNS rule-engine self-test for active block matching, NXDOMAIN response generation, and normal-domain allow behavior; it does not claim to validate the VPN network path.
- Moved large filter parsing and reload work off the VPN service main thread.
- Changed push CI to run tests only; final APK packaging is gated behind a manual release workflow.
- Physical-device verification and final APK are intentionally pending.


### 0.7.1-dev 추가 변경
- 앱 제외 검색, 업데이트 주기 선택, 커스텀 DNS 라우트, 필터 준비 확인을 추가했습니다.


### 0.7.1-dev — VPN activation and filter readiness hardening
- Serialized the final TUN activation commit against stop/revoke cancellation to close a check-then-activate race.
- Added deterministic regression tests for cancelled activation rejection and activation/stop ordering.
- Require the optional malware/phishing list cache before protection starts when that layer is enabled.
- Commit selected filter-profile metadata synchronously after atomic cache replacement.
- Latest source: 10 test suites / 52 JUnit methods; CI pending; no APK generated.


### 0.7.1-dev — Adblock important modifier priority
- Preserve `$important` domain blocks separately and evaluate them before allow exceptions.
- Keep important-rule behavior isolated by source and allow `$badfilter` to disable matching important blocks within that source.
- Ignore unsupported important exceptions rather than applying incorrect allow semantics.
- Include important rules in downloaded-cache validation and visible block counts.
- Add parser and composed-engine regression tests; latest source CI pending.


### CI fix — `$important,badfilter` modifier parsing
- Fixed detection of `$badfilter` when it appears after another comma-separated modifier.
- Latest Android unit-test CI passed: 59 tests completed, 0 failures (run 43433588312).


### VPN routing safety
- Make direct interception of public DNS resolver IPs opt-in because IP-prefix VPN routes can capture non-DNS traffic to those same addresses.
- Keep the self-test aligned with the safe default and display the advanced-routing side-effect warning.
- Add a regression test for the default configuration.


### Reliability and UI polish — 2026-11-13
- Restore a previously active VPN service after Android reclaims its process (`START_STICKY`), while respecting explicit stop state.
- Keep the dashboard honest during a service restart and offer a reconnect action rather than clearing the persisted restore intent.
- Replace the app-owned virtual-DNS UDP self-test (which bypasses the app's own VPN route on Android) with an accurately labelled in-process rule-engine test.
- Apply consistent dark styling to DNS/profile/update-interval selectors and avoid refreshing detached home views.
- Serialize CI per branch and cancel superseded push runs.
- Latest CI: run 43436954231 passed; 12 suites / 68 JUnit test methods.


## Latest verification update

- Explicit user stop now clears the persisted sticky-service restart flag even if VPN startup has not yet committed a TUN descriptor. Routine service cleanup without a live tunnel does not overwrite the original startup failure reason.
- Added two unit tests for this stop/start race policy.
- [GitHub Actions run 43436954231](https://github.com/justinhblee-prog/guardian/actions/runs/43436954231): **success** (Android unit tests passed; APK assembly intentionally skipped on push).
- Test inventory: 12 suites / 68 test methods.


## 0.7.1-dev — background filter refresh and lifecycle reliability

- Add WorkManager 2.12.0 periodic background filter refresh with network constraint and selectable cadence.
- Cancel scheduled refresh when automatic updates are disabled; retry transient failures with exponential backoff.
- Refresh active filter sources and reload an active VPN service after successful updates.
- Serialize cache downloads to avoid manual/background write races.
- Add interval validation tests; inventory grows to 12 suites / 68 test methods.


## Latest WorkManager verification

- [GitHub Actions run 43437792450](https://github.com/justinhblee-prog/guardian/actions/runs/43437792450): **success**
- Source commit: `5d7624fd445edadb1a1ed24a482b9e2d25f07d41`
- Android unit tests: **passed**; APK assembly was skipped because this was a push-triggered validation run.
- Test inventory: **12 suites / 68 test methods**.


## IPv6 DNS upstream presets
- Add IPv6 presets for Cloudflare, Google, Quad9 and AdGuard DNS (default and family protection).
- Include AdGuard default IPv6 resolver addresses in optional direct-DNS capture routes.
- Add regression coverage for the official IPv6 resolver literals.


### IPv6 DNS presets — CI verified
- Added IPv6 upstream presets for Cloudflare, Google, Quad9 and AdGuard DNS default/family protection.
- Added AdGuard default IPv6 addresses to optional direct-DNS capture routes and IPv6 validation regression coverage.
- [CI run 43443209122](https://github.com/justinhblee-prog/guardian/actions/runs/43443209122) passed on `f518711e559bfb8873128f82c07a1a157a5f2068`; 12 suites / 68 methods.
- APK build intentionally skipped on push.


## 2026-11-13 — Continuous Android build verification
- Every push now runs Android unit tests and assembles the debug APK, then verifies package ID, versionCode/versionName, file presence, and SHA-256.
- Push builds remain internal CI checks; only a manual release dispatch uploads the installable APK artifact.


## 2026-11-13 — Verified Android APK artifact
- Every push now runs Android unit tests, assembles the debug APK, and checks package/version identity and SHA-256.
- Published the installable `0.7.1-dev` debug APK artifact from successful run 43445545156.
- 12 Kotlin test suites / 70 JUnit methods passed.
- Physical-device acceptance remains outstanding; this artifact is a debug build, not a Play Store production-signed release.


## 0.7.2-dev — Safe UDP DNS response sizing
- Cap DNS UDP replies to 1232 bytes to respect the IPv6 minimum-MTU safe DNS payload.
- When upstream replies exceed the cap, build a standards-shaped TC response retaining the validated DNS question instead of writing an oversized IP packet to TUN.
- Add regression tests for TC/QR/RA/RD/CD flags, question preservation, and malformed query rejection.
- Client-side TCP/53 retry remains unsupported and is documented as a limitation.


## 2026-11-13 — 0.7.2-dev verified APK
- VersionCode 15 / versionName 0.7.2-dev.
- 12 Kotlin test suites / 72 JUnit tests passed.
- Debug APK assembled, package identity and SHA-256 verified, and installable artifact uploaded.
- Final CI run: https://github.com/justinhblee-prog/guardian/actions/runs/43447121533
- APK SHA-256: `a1d41cf8937b2c09f7c4ca8d0b02ddf252e95f935b1cee3ed1d485c5470f55d8`


## Unreleased — Android DNS-over-TCP/53

- Add a defensive IPv4/IPv6 TCP packet codec for DNS traffic to the virtual resolver.
- Integrate a bounded TCP session manager into the VPN packet loop.
- Add DNS-over-TCP length framing, ordered partial-stream reassembly, multi-query parsing, and segmented DNS replies.
- Apply the existing domain block/allow engine and configured upstream resolver to TCP DNS requests.
- Correct server sequence advancement after SYN and handle payload+FIN segments.
- Add regression tests for IPv4/IPv6 TCP checksums, handshake sequence numbers, DNS framing, partial-frame reassembly, and FIN cleanup.
- CI run 43452443314 passed: 16 suites / 99 JUnit tests; debug APK assembly, package identity and SHA-256 checks passed.
- TCP retransmission/loss recovery, IPv6 extension headers, IP fragmentation, DoH/DoT and physical-device validation remain outstanding.

- Improve the Protection tab with live start/stop control, quick filter switches, and navigation to filter/app exclusion settings.

- Add Android `lintDebug` to CI and fix the app-private broadcast receiver registration flag.

- Clarify that the DNS self-test validates local rules/NXDOMAIN generation only, not live resolver routing or in-app ads.

- Add selectable UDP/TCP/DoT upstream transport, provider TLS host defaults, custom TLS hostname validation, SNI and certificate hostname verification.

- Add DNS-over-HTTPS upstream transport using TLS/443 and HTTP POST binary DNS messages; parse bounded Content-Length and chunked responses, reject redirects and invalid media types.

- Fix DoH TLS-host settings visibility when reopening the settings screen with DoH already selected.

- Add an end-to-end IPv6 TCP DNS session regression test.


## 0.7.2-dev — CI verification refresh (2026-11-13)
- Re-ran Android unit tests and lint on source commit `827198814d0b4441afe72c2ddb81b6d1a41a8f19`.
- Debug APK assembly, package identity/version check, and SHA-256 verification passed in GitHub Actions run 43525073201.
- CI-built APK SHA-256: `c40229d5c3a25173092233b2e23ace19d9c3d6f831837d6d6684c20f156495ee`.
- Push workflow intentionally skipped manual-release artifact upload; this is not a new installable APK release.


## v0.7.2-dev — TCP DNS 세션 안정성 및 진단 보완 (2026-11-14)
- 이미 연결된 DNS TCP 세션에 지연 재전송된 동일 SYN이 도착해도 기존 sequence state를 보존하고 동일 SYNACK으로 응답합니다.
- 지연 SYN 이후 새로운 DNS TCP 질의가 계속 처리되는 회귀 테스트를 추가했습니다.
- 모순된 TCP 제어 플래그(SYN+FIN/RST, RST+FIN/PSH)를 거부합니다.
- Android 비공개 DNS 자동/호스트명 모드를 홈·보호 화면에서 진단해, 암호화 DNS 우회 가능성을 사용자에게 안내합니다.
- GitHub Actions run 43530335902: Android unit tests, lint, debug APK assembly, package/version and SHA-256 checks all succeeded.
- Test inventory: 17 suites / 104 JUnit test methods.
- CI artifact upload was skipped by push policy; this is not a final APK release. Device-level VPN and ad-blocking acceptance remains outstanding.


## 2026-11-14 — Filter download redirect security
- Replaced automatic HTTPS connection redirects with a bounded manual redirect policy.
- Reject HTTP downgrade redirects, embedded URL credentials, missing or malformed locations, unsupported 3xx responses, and redirect chains exceeding five hops.
- Added six unit tests for relative/absolute HTTPS redirects and rejection cases.
- GitHub Actions run 43531095663: 18 test suites / 110 test methods passed; lint, debug APK assembly, package identity and checksum checks succeeded.
- APK artifact upload remains disabled for push-triggered CI; this is build verification, not a final APK release.


## 2026-11-14 — Android version display and build configuration
- Settings now displays the app's actual Gradle `versionName` through `BuildConfig.VERSION_NAME` instead of a hard-coded version string.
- Explicitly enabled Android `BuildConfig` generation in the app module.
- Verified by GitHub Actions run 43531690119: Android unit tests, lintDebug, assembleDebug, package/version identity, and APK SHA-256 checks all passed for commit `1616c8fdd11c92c1c982c3b24c4661242433d366`.


## 2026-11-14 — DNS-over-TCP FIN retransmission reliability
- Keep a half-closed DNS-over-TCP session until the client acknowledges the server FIN, allowing a lost final response/FIN to be replayed on an exact retransmission.
- Replay cached server packets for retransmitted final client data/FIN segments, avoiding duplicate DNS resolution.
- Added regression tests for data+FIN and pure FIN retransmission paths.
- Verified by GitHub Actions run 43533452981: `testDebugUnitTest`, `lintDebug`, `assembleDebug`, package/version checks, and APK SHA-256 generation passed. APK SHA-256: `b7a35a98f76ce20aa272358c9b0d2f5584a8ec7dd6f88308427840fbe7613b89`.
- Push workflow skipped artifact upload; no device-level ad-blocking claim is made.


## 2026-11-14 — Truthful protection status diagnostics
- Protection screen detail now reflects observed DNS request, block, forward, and failure counters instead of claiming DNS is being filtered solely because the VPN tunnel is active.
- Zero observed DNS requests are clearly reported; opportunistic/hostname Android Private DNS modes warn about possible encrypted DNS bypass.
- Added five unit tests for diagnostic state combinations. CI validation pending.


## 2026-11-14 — Protection detail refresh consistency
- Fixed the Protection tab refresh path so an active VPN session cannot replace observed DNS counters with a generic persisted service message.
- Inactive sessions continue to show specific service failure messages.
- Added two regression tests for both paths.


## 0.7.4-dev — Filter refresh retry efficiency (2026-11-14)
- Refresh each enabled filter source only when its own cache is missing/invalid or its configured update interval has elapsed.
- Avoid downloading already-successful, still-fresh lists again when WorkManager retries after an optional source failure.
- Add regression coverage for retry freshness, stale boundaries, missing metadata, future timestamps and minimum interval behavior.
- Advance Android package to versionCode 17 / versionName `0.7.4-dev`.
- CI run 43570828960 passed unit tests, lint, debug APK assembly, package identity and checksum validation. APK SHA-256: `d60df611f004d5114d9e1aaeb832a5837ce4287c5dd1b7c51c5909c8a4cfb577`. Push CI skipped artifact upload.


## 0.7.5-dev — DNS address safety and protection scope copy (2026-11-14)
- Safely handle a nullable IPv6 `hostAddress` during numeric resolver normalization.
- Clarify in the home protection-scope card that app-originated DoH/DoT bypass requests are not intercepted; this is distinct from Guardian's own encrypted upstream transports.
- CI run 43571389815 passed unit tests, lint, debug APK assembly, package identity and checksum validation.
- Package: versionCode 18 / `0.7.5-dev`; CI APK SHA-256: `f756ab65ce8d74cc7a7da45457b0cc2987b83acc83e9c993eab0a3f624492e1a`. Push CI skipped artifact upload.


## 0.7.6-dev — DNS-over-TCP resolver exception isolation (2026-11-14)
- Catch unexpected resolver callback exceptions inside the DNS TCP session manager and convert them to a DNS SERVFAIL response instead of allowing the packet loop to terminate the entire VPN service.
- Add regression coverage proving a thrown resolver exception returns SERVFAIL and the established TCP session continues to process the next DNS request.
- Package versionCode 19 / `0.7.6-dev`. CI verification pending.


## 0.7.6-dev — DNS-over-TCP resolver exception isolation (2026-11-14)
- DNS TCP session manager now catches unexpected exceptions from the resolver callback and returns a standards-shaped SERVFAIL response; a resolver exception no longer escapes to the VPN packet loop and terminates the tunnel.
- Regression test confirms SERVFAIL and successful processing of a subsequent query in the same TCP session.
- CI [43572032012](https://github.com/justinhblee-prog/guardian/actions/runs/43572032012) completed successfully: shared DNS rules and manifest checks, Android unit tests, Android lint, Debug APK assembly, package identity/version and APK checksum checks passed.
- Package: `com.guardian.adblock`, versionCode `19`, versionName `0.7.6-dev`.
- CI debug APK SHA-256: `b5ed0b9ee635eadc1ba5c337f94eed9c47767215198e2b60df5748fbf53e1e91` (about 2.9 MB). Ordinary push workflow skipped artifact upload.
- This is automated CI evidence, not physical-device validation; APK is debug-signed and not a production-signed release.


## 0.7.7-dev — Filter screen option race prevention (2026-11-14)
- The Rules/Filters screen uses CheckBox controls for optional security and Android-ad filters. Previously, an update started from that screen only disabled the corresponding Switch controls on the Protection screen, leaving the CheckBoxes interactive while the worker snapshot was downloading. This could change an option after its cache snapshot and leave an enabled filter without a validated cache.
- Track and disable both screen variants during the complete update, restore interaction afterward, and synchronize the control off when a first-time optional-list download fails.
- Package versionCode 20 / `0.7.7-dev`. CI verification pending.


## 0.7.7-dev — Filter option update-race fix (2026-11-14)
- Prevent changes to optional security and Android-app advertising filter toggles while a filter update is in progress. Both the Protection screen Switches and Filters screen CheckBoxes are disabled, including when navigating between screens during an update; controls are restored on success or failure.
- When a first-time optional list download fails and no valid cache exists, the matching control is switched off so the UI and persisted configuration remain aligned.
- CI [43572504150](https://github.com/justinhblee-prog/guardian/actions/runs/43572504150) completed successfully: unit tests, lint, debug APK assembly, package identity/version and checksum checks passed.
- Package: `com.guardian.adblock`, versionCode `20`, versionName `0.7.7-dev`.
- CI debug APK SHA-256: `1dbfd5b33e134ad25c949e24c0750793c9d43c83ec9888230f107e93c49754c4` (about 2.9 MB). This ordinary push run skipped artifact upload.
- Final package workflow now creates a source ZIP containing source, tests, workflow and project documentation, plus a Korean verification summary alongside the installable debug APK and SHA-256 file.


## 최종 산출물 패키지 생성 완료 — 2026-11-14

- 최종 CI 실행: [43572937308](https://github.com/justinhblee-prog/guardian/actions/runs/43572937308) — 성공. Android 단위 테스트, lintDebug, assembleDebug, 패키지명·버전 확인, APK checksum, 소스 패키징 및 artifact 업로드가 모두 통과했습니다.
- 커밋: `94bab42e1a9dd7e10b61fad52f2546fc075dbdcf`
- 앱: `com.guardian.adblock`, versionCode `20`, versionName `0.7.7-dev`.
- 설치 APK: `app-debug.apk`, 3,014,104 bytes. SHA-256: `4797b47bad0c92574e0b7f9b8bf347960c46e688c95be43ee6158b2d1a4a93e7`.
- 전체 소스 ZIP: `Guardian-AdBlock-Source-v0.7.7-dev.zip` (161,340 bytes, 98 entries; Android 소스·테스트·공유 규칙·워크플로·설명서 포함).
- 검증 요약: `release/VERIFICATION_SUMMARY_KO.md`.
- GitHub Actions artifact: `Guardian-AdBlock-Android-Full-Package-v0.7.7-dev`, artifact ID `14105371888`, archive SHA-256 `d5f4555603756f5b0cf97a6725ecc5ff942e98ede9c626ef91227bfa2cf12d3f`, 만료 예정 2026-12-14.
- APK는 Android debug 서명 빌드입니다. production 서명과 실기기 VPN/DNS 라우팅·Wi-Fi/모바일 전환·실제 광고 제거 수용 테스트는 수행하지 않았습니다. DNS 수준 차단 범위 및 앱 자체 DoH/DoT 우회 등 알려진 한계는 검증 문서에 명시했습니다.


## 0.7.8-dev — TCP/IP reserved-header validation (2026-11-14)
- Hardened the DNS TCP packet parser: reject IPv4 packets with the reserved fragmentation flag set, and reject TCP headers with nonzero reserved/NS bits instead of silently accepting unsupported header semantics.
- Added regression coverage for both malformed-header cases.
- Package versionCode 21 / `0.7.8-dev`.
- CI run: https://github.com/justinhblee-prog/guardian/actions/runs/43573908206 — pending at documentation update time. Do not treat this source change as verified until that run completes successfully.
- This is parser hardening only; it does not prove physical-device VPN routing or real ad-removal effectiveness.


## Android free-track — emergency filter fallback regression coverage (2026-11-24)
- Added regression tests proving the built-in emergency DNS rules replace the previous active rule set, contain exactly the expected 10 parseable block entries, preserve subdomain matching, and do not retain custom/allow rules from the replaced set.
- This covers the pure Kotlin rule-engine behavior only. Android asset-open failure handling, VPN startup, Gradle/JUnit, emulator, and physical-device behavior remain unverified until the Android test environment is available.
- No APK was built and no GitHub Actions run was triggered.


## 2026-11-24 Android 무료 버전 — 손상된 기본 필터 자산 복구 경로

- 기본 `filter_rules.txt`가 누락된 경우뿐 아니라 파일이 존재해도 유효한 차단 규칙이 하나도 없는 경우를 보호 시작 전 점검에서 감지하도록 수정했습니다.
- MainActivity는 손상된 기본 자산을 최소 내장 긴급 규칙으로 대체하고 사용자에게 제한된 보호 모드임을 표시합니다.
- GuardianVpnService도 동일한 유효성 검사를 수행해 사전 점검과 실제 VPN 규칙 로딩의 판단이 어긋나지 않도록 했습니다.
- 회귀 테스트는 주석/잘못된 규칙만 포함된 자산을 거부하고 내장 긴급 규칙은 시작 가능한지 확인합니다.
- 검증: 변경 소스 재조회 및 테스트 케이스 추가. Android Gradle/JUnit 실행, APK 생성, 에뮬레이터·실기기 검증은 아직 미실행입니다.


## 2026-11-24 Android 무료 버전 — 필터 준비 판정 오버플로 방지

- 기본 필터의 유효 규칙 수 판정에서 두 정수 카운트를 더하던 방식을 개별 양수 검사로 변경했습니다. 극단적으로 큰 규칙 수가 정수 오버플로로 잘못된 시작 불가 판정을 만들지 않습니다.
- `Int.MAX_VALUE`, 음수 극단값 조합에 대한 회귀 테스트를 추가했습니다.
- 검증: 소스 및 테스트 변경을 원격 브랜치에 반영. Android Gradle/JUnit 실행은 아직 미실행입니다.


## 2026-11-24 Android 무료 버전 — 정식 업데이트 자산 경로 고정
- 정식 Release manifest와 APK URL이 단순히 Guardian 저장소 도메인에 속하는지만 확인하던 검증을 강화했습니다.
- 각 자산 URL이 현재 선택된 stable tag 및 예상 파일명(`guardian-android.json`, `guardian-android.apk`)과 정확히 일치해야 합니다. 다른 태그, 파일명, 추가 경로, query/fragment, 비표준 포트, 비공식 호스트는 거부합니다.
- 회귀 테스트 8개 케이스를 추가했습니다.
- Kotlin/JVM smoke 6개 assertion 통과. Android Gradle/JUnit, lint, APK, GitHub Release 실제 CDN redirect 및 설치 검증은 미실행입니다. CI 비용 발생 작업은 실행하지 않았습니다.


## 2026-11-24 Android 무료 버전 — 업데이트 확인 결과 문구 정확성
- 공식 stable Release 후보가 하나도 없을 때 이를 '최신 버전'으로 잘못 표시하지 않고, 정식 Release 미게시 오류로 표시하도록 수정했습니다.
- 설치 버전이 최신 공개 정식 버전과 같거나 더 높은 경우에는 '최신 공개 버전 이상'이라고 안내해 버전 관계를 과장하지 않습니다.
- Android Gradle/JUnit 및 UI 런타임 검증은 아직 실행하지 않았습니다.


## 2026-11-24 Android 무료 버전 — 필터 규칙 수 검증 오버플로 방지
- 필수 최소 규칙 수 비교에서 두 Int 개수를 더할 때 발생할 수 있는 정수 오버플로를 Long 합산으로 바꿨습니다.
- 선택 필터 캐시와 서명/일반 다운로드 필터의 최소 규칙 검증을 같은 안전한 산술 기준으로 정리했습니다.
- 경계값 회귀 테스트를 추가했습니다. Android Gradle/JUnit 실행은 아직 하지 않았습니다.


## 2026-11-24 Android 무료 버전 — DNS TCP 수신 윈도우 기반 응답 전송
- DNS TCP 세션이 클라이언트가 광고한 16비트 수신 윈도우를 추적하고, 아직 ACK되지 않은 전송량을 제외한 윈도우 여유만큼만 응답 데이터를 전송하도록 수정했습니다.
- 수신 윈도우가 0이거나 응답 전체를 담지 못하면 나머지 DNS 응답 바이트를 세션별 bounded DNS 프레임 한도 내에서 대기시키고, 후속 ACK/윈도우 업데이트를 받으면 이어 전송합니다. 세그먼트는 기존 1,200바이트 상한을 유지합니다.
- 클라이언트 FIN이 먼저 도착해도 대기 중인 응답 데이터가 전송되기 전 서버 FIN을 앞질러 보내지 않도록 종료 순서를 정리했습니다. 윈도우 스케일 옵션은 협상하지 않으므로 raw 16-bit window 기준으로 동작합니다.
- 0 윈도우에서 대기 후 2,048바이트 단위 윈도우 업데이트로 5KB DNS 응답을 모두 전송하는 회귀 테스트를 추가했습니다.
- 검증 한계: 원격 소스/테스트 반영만 확인. Android Gradle/JUnit, lint, 에뮬레이터, 실기기 검증은 미실행이며 GitHub Actions 및 APK 빌드는 시작하지 않았습니다.


## 2026-11-24 Android 무료 버전 — DNS TCP 수신 윈도우 갱신 순서 검증
- TCP 수신 윈도우 값이 도착 패킷의 순서와 무관하게 덮어써지던 문제를 수정했습니다. 최신 `SEG.SEQ` 또는 동일한 `SEG.SEQ`에서 더 오래되지 않은 `SEG.ACK`인 경우에만 윈도우를 갱신합니다.
- 이전 시퀀스 패킷 및 동일 시퀀스의 오래된 ACK가 0 윈도우를 광고해도 이미 열린 수신 윈도우와 대기 응답 전송을 되돌리지 않는 회귀 테스트를 추가했습니다.
- 검증: 원격 소스/테스트/문서 반영. Android Gradle/JUnit, lint, 에뮬레이터, 실기기 검증은 미실행이며 GitHub Actions와 APK 빌드는 실행하지 않았습니다.


## 2026-11-24 Android 무료 버전 — DNS TCP 미확인 응답 세그먼트 재전송 보존
- 수신 윈도우가 열린 뒤 전송한 DNS TCP 응답 세그먼트를 누적 ACK가 확인할 때까지 세션별로 보관하도록 수정했습니다.
- 클라이언트가 응답을 받지 못해 원래 DNS 질의를 재전송하면 아직 ACK되지 않은 응답 세그먼트를 재전송합니다. ACK가 확인한 세그먼트는 재전송 목록에서 제거합니다.
- 0 윈도우 대기 후 전송된 응답의 손실/질의 재전송 경로에 대한 회귀 테스트를 추가했습니다.
- 검증: 원격 소스/테스트/문서 반영. Android Gradle/JUnit, lint, 에뮬레이터, 실기기 검증 및 CI/APK 빌드는 미실행입니다.


## 2026-11-24 Android 무료 버전 — DNS TCP 대기 응답 메모리 상한
- 한 TCP 요청 세그먼트에 다수의 DNS 프레임이 포함될 때 응답 큐가 입력 버퍼 한도를 초과해 커질 수 있던 경로를 차단했습니다. 세션별 대기 응답 바이트도 65,537바이트 이내로 제한하고 초과 시 해당 세션을 RST로 종료합니다.
- 다중 대형 DNS 응답으로 큐 상한을 넘는 회귀 테스트를 추가했습니다.
- 검증: 원격 소스/테스트/문서 반영만 확인. Android Gradle/JUnit, lint, 에뮬레이터, 실기기, CI/APK 검증은 미실행입니다.


## 2026-11-24 Android 무료 버전 — DNS 실패 통계 그래프 표시
- 활동 그래프가 광고/추적/기타 차단과 정상 전달만 표시하고, 실시간 통계에 별도로 표시되는 업스트림 실패 건수는 시각화하지 않던 불일치를 수정했습니다.
- 그래프를 DNS 처리 결과 그래프로 명확히 바꾸고 업스트림 실패 항목을 추가했습니다. 그래프 갱신 시 SharedPreferences의 `KEY_DNS_FAILED` 값을 전달하며, 5개 항목에 맞춰 행 높이를 동적으로 계산합니다.
- 검증: 원격 UI 소스와 문서 반영 확인. Android 컴파일/테스트 및 화면 실기기 검증은 미실행입니다.

- DNS 통계 화면에 실패(SERVFAIL)와 무시(형식/질문 검증 실패)의 의미를 설명해 두 수치가 혼동되지 않도록 했습니다.
