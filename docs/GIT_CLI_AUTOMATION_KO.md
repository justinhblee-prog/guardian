# Guardian Git CLI 자동화 운영 기준

## 목표

Windows PC에 설치된 Git CLI와 GitHub CLI만으로 다음 작업을 반복 수행합니다.

1. 원격 main을 가져오고, 로컬 작업 트리를 보호하면서 fast-forward 가능한 경우에만 동기화합니다.
2. main 대상 오픈 PR을 조회하고, 같은 저장소 브랜치만 대상으로 합니다.
3. PR마다 별도 detached worktree를 만들어 최신 origin/main 기준으로 rebase합니다.
4. 충돌, 브랜치 보호, 동시 업데이트, 권한 문제는 자동 해결을 시도하지 않고 해당 PR만 보류합니다.
5. 변경된 브랜치는 --force-with-lease로만 push합니다. 일반 force push나 hard reset은 사용하지 않습니다.
6. 최근 GitHub Actions 실패 실행과 실패 로그를 로컬 감사 파일로 수집합니다. Runner 할당/시작 실패는 앱 소스 실패로 간주하거나 무한 재실행하지 않습니다.

## 1회 설치 (Windows PowerShell)

저장소를 최신 main으로 동기화한 뒤 저장소 루트에서 실행합니다.

```powershell
git pull --ff-only origin main
.\scripts\guardian-git-agent.ps1 -InstallScheduledTask
```

설치 시 현재 Windows 사용자 계정으로 작업 스케줄러 작업 Guardian-Git-Automation을 등록합니다. 작업은 먼저 별도 bootstrap 스크립트를 실행해 깨끗한 로컬 main만 origin/main으로 fast-forward 동기화한 뒤 현재 버전의 Git agent를 시작합니다. 로그인 시 한 번 실행하고, 이후 15분 간격으로 반복합니다. 작업 스케줄러 권한이 거부되면 Startup 폴더의 VBS가 같은 bootstrap을 실행하고 agent의 15분 RunLoop를 시작합니다. 별도의 서버, 유료 API, GitHub Actions runner를 상시 실행하지 않습니다.

필수 사전 조건:
- Git CLI 설치 및 PATH 등록
- GitHub CLI(gh) 설치
- 같은 Windows 사용자로 gh auth login 완료
- 해당 사용자가 저장소 PR 브랜치에 push할 권한 보유
- 저장소 remote 이름은 origin, 원격 저장소는 justinhblee-prog/guardian

GitHub CLI 인증은 최초 1회만 수행합니다.

```powershell
gh auth login
gh auth setup-git
```

## 업데이트 및 부트스트랩 동작

- `guardian-git-agent-bootstrap.ps1`은 agent보다 먼저 실행됩니다. Git CLI로 origin/main을 가져오고, 현재 branch가 `main`이며 working tree가 깨끗할 때만 `git merge --ff-only origin/main`으로 최신 파일을 반영합니다. `reset --hard`, 강제 checkout, dirty working tree 덮어쓰기는 사용하지 않습니다.
- bootstrap은 공유 파일 잠금을 확보한 뒤 이전 버전의 `-RunLoop` agent 프로세스를 정리하고, 디스크에 반영된 최신 agent를 실행합니다. 기존 agent의 Git 작업이 잠금을 보유 중이면 최대 120초 기다리며, 잠금을 얻지 못하면 기존 프로세스를 강제 종료하지 않습니다.
- 따라서 기존 Startup fallback을 사용하는 PC는 새 bootstrap 방식이 반영된 저장소를 한 번 동기화하고 `-InstallScheduledTask`를 다시 실행해야 Startup launcher가 교체됩니다. 이 최초 1회 로컬 설치 이후에는 예약된 bootstrap 또는 Startup fallback의 agent RunLoop가 주기적으로 origin/main을 확인합니다.

## 수동 실행 및 제거

```powershell
# 즉시 1회 실행
.\scripts\guardian-git-agent.ps1

# 자동 실행 작업 제거 (저장소/브랜치/커밋은 변경하지 않음)
.\scripts\guardian-git-agent.ps1 -UninstallScheduledTask
```

## 안전 규칙

- 로컬 main에 미커밋 변경이 있으면 pull하지 않습니다.
- 현재 checkout이 main이 아니면 사용자의 브랜치/작업 트리를 변경하지 않습니다.
- PR별 작업은 %LOCALAPPDATA%\GuardianGitAgent\worktrees의 독립 worktree에서 처리합니다.
- 동일 PC에서 수동 실행과 예약 실행이 겹치면 파일 잠금으로 후속 실행을 건너뜁니다.
- fork PR, 삭제된 head 저장소, 충돌, 보호 브랜치 거부, lease 불일치는 push하지 않습니다.
- rebase 충돌 시 abort하고 원래 원격 PR 브랜치를 그대로 둡니다.
- CI 실패를 임의로 재실행하지 않습니다. 특히 runner 할당 전 실패는 저장소 코드 결함과 분리합니다.
- GitHub Actions billing/usage, repository settings, secrets 및 결제 설정은 수정하지 않습니다.

## 로그 및 판정

| 파일 | 내용 |
|---|---|
| %LOCALAPPDATA%\GuardianGitAgent\agent.log | 실행 시각, main 동기화, PR별 업데이트/보류/오류 |
| %LOCALAPPDATA%\GuardianGitAgent\latest-ci-failures.log | 최근 30개 Actions 실행 중 실패한 실행과 가능한 실패 로그 |

PR별 최종 상태는 UPDATED, already based, BLOCKED, skipped, ERROR로 기록됩니다. 충돌이나 보호 규칙을 우회하지 않습니다.

## GitHub 검증 워크플로

.github/workflows/guardian.yml은 PR 기준 커밋과 main의 ahead/behind 수치를 Git CLI로 계산하고 PowerShell 스크립트의 구문/안전 가드를 검사합니다. PR이 main보다 뒤처져 있으면 실패로 위장하지 않고 경고를 남깁니다. 실제 rebase/push는 로컬 예약 에이전트가 수행합니다.

.github/workflows/guardian-git-automation.yml은 에이전트 변경 시 PowerShell 구문 및 안전 가드를 추가 검증합니다.

## 제한 및 외부 의존성

- 이 자동화는 PR 브랜치 동기화와 CI 결과 수집을 자동화합니다. 충돌의 의미를 판단해 코드를 임의 수정하거나 기능 구현을 대신하지 않습니다.
- Android 빌드/에뮬레이터 검증은 기존 android-apk.yml이 수행합니다.
- GitHub hosted runner가 할당되지 않거나 시작하지 못한 경우에는 runner 인프라 문제로 분류합니다. runner 설정/요금제 변경 또는 자동 무한 재시도는 하지 않습니다.
- Samsung 실기기, 운영 서명키, 사용자 노트북 DB 및 실제 결제처럼 외부 증거가 필요한 작업은 이 Git CLI 에이전트가 완료 처리하지 않습니다.


## 로컬 Ollama 멀티플로우 구현 트랙 (유료 AI API 미사용)

기존 Git agent의 Copilot/Aider 혼합 단일 worker를 Guardian 전용 작업 명세 기반 멀티플로우로 교체했습니다. 모델 추론은 Windows PC의 `127.0.0.1:11434` Ollama만 사용합니다. 원격 AI API, Copilot CLI fallback, WinGet Copilot 자동 설치는 이 경로에 존재하지 않습니다.

### 구성 파일

- `.guardian/automation/tasks.json`: Issue #142 Android 진단, #143 필터 승인 fail-closed, #144 Production 서명 게이트의 범위·우선순위·완료 조건·검증 프로필을 명시합니다.
- `scripts/guardian_multiflow.py`: 작업 선택, 로컬 Ollama 호출, patch 검증, lease, worktree, allowlist 검증, commit/push 및 Draft PR을 수행합니다.
- `scripts/guardian-multiflow.ps1`: 기존 Windows Git agent와 같은 Windows 사용자 환경에서 Python worker를 실행하는 진입점입니다. Runner 서비스 계정과 Windows 예약 Agent는 동일한 Windows 사용자 계정을 사용해야 로컬 lease/state 및 Ollama slot을 공유합니다.
- `.github/workflows/guardian-windows-multiflow.yml`: Windows self-hosted runner에서 매시간 또는 수동으로 최대 1~3개 독립 작업을 처리합니다.
- `.github/workflows/aider-qwen-local.yml`: 기존 파일 경로를 유지하되 이제 Aider가 아니라 실제 localhost Ollama inference를 검증합니다.

### Windows 사전 조건

- Git for Windows 및 GitHub CLI(`gh`)
- Python 3.10 이상
- Ollama for Windows와 `qwen2.5-coder:7b`
- Guardian 저장소 push 및 Draft PR 생성 권한
- Android 작업은 JDK 17, Android SDK Platform 35, Gradle 8.9가 추가로 필요합니다.
- 필터 배포 작업은 Node.js 22 이상이 필요합니다.

최초 설정/검증:

```powershell
ollama pull qwen2.5-coder:7b
Invoke-RestMethod http://127.0.0.1:11434/api/tags
gh auth login
gh auth setup-git

# manifest 및 검증 명령 allowlist 검사
.\scripts\guardian-multiflow.ps1 -ValidateOnly

# 지정 모델이 실제 응답하는지 로컬 추론 검사
.\scripts\guardian-multiflow.ps1 -SmokeTest
```

`/api/tags` 확인만으로 Agent 연결 성공으로 판정하지 않습니다. `-SmokeTest`가 실제 `/api/chat` 호출을 보내고 기대 응답 토큰을 확인해야 합니다.

### GitHub self-hosted runner 연결

`.github/workflows/guardian-windows-multiflow.yml`의 `runs-on`은 기존 Guardian runner와 동일하게 `[self-hosted, Windows]`입니다. GitHub 저장소의 Windows Runner가 Online이어야 하며, Runner 서비스 계정에서 Python·Git·GitHub CLI·Ollama 모델을 볼 수 있어야 합니다.

Actions workflow는 main의 관련 변경 push, `workflow_dispatch` 및 매시간 schedule로 실행됩니다. 수동 실행 입력은 다음과 같습니다.

- `max_tasks`: 기본값 3. 서로 파일 범위가 겹치지 않는 독립 작업을 최대 3개까지 동시에 실행하며, `--max-tasks 1`로 직렬 실행도 선택 가능
- `task_id`: manifest의 특정 작업 ID. 비워 두면 우선순위 순서로 선택

Workflow는 `contents: write`와 `pull-requests: write` 권한으로 task branch를 push하고 Draft PR만 생성합니다. PR 자동 병합·Production 배포·Repository settings/Actions billing 변경은 하지 않습니다. Fork PR 코드는 Windows self-hosted runner에서 실행하지 않습니다.

### 멀티 작업 격리와 lease

1. Manifest에서 `status=ready`이고 모든 선행 작업의 PR이 main에 통합된 것으로 확인된 작업만 선택합니다.
2. 각 작업은 고정된 task ID별 lease를 획득하고 독립 Git branch/worktree를 만듭니다.
3. 같은 PC의 Windows 예약 Agent와 GitHub workflow가 겹쳐도 같은 task lease를 동시에 획득할 수 없습니다.
4. Ollama는 공용 3-slot semaphore를 사용하므로 두 실행 경로가 겹쳐도 로컬 모델 동시 호출이 3개를 넘지 않습니다.
5. 모델은 shell/PowerShell을 실행하지 않습니다. 작업 범위에 포함된 파일 내용만 받고 unified diff만 반환합니다.
6. wrapper는 diff 경로·scope·보호 파일·심볼릭 링크·binary patch·파일 수/크기를 검증한 뒤 적용합니다.
7. manifest에 명시된 고정 allowlist 검증 명령만 실행합니다. 검증이 모두 통과한 경우에만 commit/push/Draft PR을 수행합니다.
8. 실패한 worktree는 자동 삭제하지 않고 진단용으로 보존합니다. 사용자 작업 트리의 reset/clean/stash는 실행하지 않습니다.

상태 파일·lease·worktree·검증 로그 위치:

```text
%LOCALAPPDATA%\GuardianAutomation\
  leases\
  ollama-slots\
  worktrees\
  logs\
  multiflow-state.json
```

### 최초 운영 순서

```powershell
# 1개 작업으로 manifest / 실제 모델 / branch·PR 흐름 검증
.\scripts\guardian-multiflow.ps1 -ValidateOnly
.\scripts\guardian-multiflow.ps1 -SmokeTest
.\scripts\guardian-multiflow.ps1 -MaxTasks 1

# 별도 실행 경로: GitHub Actions → Guardian Windows Local Ollama Multi-Flow
```

기존 `Guardian-Git-Automation` 15분 예약 루프도 새 `guardian-multiflow.ps1 -MaxTasks 1` worker를 호출하도록 연결했습니다. 기존 Copilot worker 파일은 호환용 진입점만 남기고 local Ollama wrapper로 전환했습니다.

### 완료 판정의 경계

- manifest 검사 성공: 작업 명세와 allowlist가 유효함
- smoke test 성공: Windows runner 계정이 localhost Ollama의 지정 모델에 실제 추론 요청을 보냄
- Draft PR 생성: 모델 변경이 scope·테스트를 통과하고 branch push/PR 생성까지 완료됨
- 멀티플로우 운영 완료: 3개 작업이 실제 독립 worktree/lease에서 동시에 실행되고 각 PR CI까지 검증된 상태

이 단계들은 서로 다릅니다. 현재 CI workflow가 저장소에 병합됐다는 사실만으로 사용자의 Windows PC에서 Runner Online, Ollama model inference, 실제 branch push 또는 Draft PR 생성이 성공했다고 주장하지 않습니다. 해당 증거는 Windows workflow run 및 생성 PR에서 별도로 확인합니다.

실기기 Samsung 오류, 원본 재배포 권리 승인, 운영 서명키, 실제 고객/운영 서버처럼 외부 증거가 필요한 항목은 manifest에서 임의로 완료하지 않습니다.
