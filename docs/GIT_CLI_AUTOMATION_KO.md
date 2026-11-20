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


## 로컬 AI 코드 구현 트랙 (Copilot CLI)

PR/브랜치 동기화만 하던 Git agent 뒤에 선택형 AI 코드 구현 트랙을 연결합니다. 새 트랙은 기존 15분 Git 유지보수 cycle 마지막에 실행되며, 별도 격리 worktree에서 하루 최대 1회 구현 작업을 시도합니다.

실행 경로:
1. GitHub CLI 인증 상태와 Copilot CLI 설치 여부를 비대화형으로 확인합니다. Copilot CLI는 저장된 로그인 또는 GitHub CLI 인증 토큰을 사용할 수 있습니다.
2. 최신 origin/main을 기준으로 `automation/copilot-guardian-YYYYMMDD` worktree를 만듭니다.
3. Issue #141~#144와 실제 소스/테스트를 읽고, 물리 기기·승인·운영키·실제 서버가 필요 없는 미완료 코드 작업 하나를 선택합니다.
4. Copilot Autopilot이 구현·회귀 테스트를 수행합니다. 질문을 하지 않고, continuation 및 AI credit 한도를 설정합니다.
5. 보호 경로·비밀 파일·운영 필터 승인 파일 변경을 차단합니다. 검증을 통과한 변경만 새 draft PR로 push합니다.
6. 자동 병합하지 않습니다. Android 실기기, production, 법적 재배포 승인 등 외부 증거는 완료 처리하지 않습니다.

로컬 의존성:
- GitHub CLI 인증: `gh auth status`가 성공해야 합니다.
- GitHub Copilot CLI: `copilot --version`으로 설치 여부를 확인합니다.
- Copilot CLI는 저장된 Copilot 로그인을 사용하거나, 별도 환경 토큰이 없을 때 인증된 GitHub CLI 토큰을 fallback으로 사용할 수 있습니다.
- Copilot 구독/조직 정책이 허용하지 않으면 AI 호출은 실패하고 로컬 로그에 사유를 남깁니다. 이 자동화는 별도 API 키나 Vercel secret을 만들지 않습니다.

AI 사용 제한:
- 실행 횟수: 로컬 날짜 기준 하루 최대 1회.
- `--max-ai-credits 1`: 응답당 soft limit입니다. 서비스의 hard billing cap이나 무료 사용 보증으로 해석하지 않습니다.
- `--max-autopilot-continues 3`: 자동 연속 응답을 제한합니다.
- blanket `--yolo` / `--allow-all`은 사용하지 않습니다. PowerShell/file 도구 목록을 제한하고 commit/push/PR 생성/merge/issue close는 모델 도구에서 금지합니다. 최종 commit·push·draft PR 생성은 검증 가드가 있는 로컬 wrapper가 수행합니다.
- 하루 1회 트리거는 반복 호출을 방지합니다. Copilot CLI가 없으면 WinGet이 설치를 시도합니다. 설치/인증 단계에서는 AI credit을 소비하지 않습니다. Copilot CLI가 없거나 인증/라이선스가 없으면 6시간 간격으로 설치·인증 상태만 재확인합니다.

로그:
- `%LOCALAPPDATA%\\GuardianGitAgent\\copilot-worker-state.json`: 하루 실행 중복 방지 및 마지막 PR 상태
- `%LOCALAPPDATA%\\GuardianGitAgent\\copilot-YYYYMMDD-HHMMSS.log`: Copilot CLI의 실행 결과
- Android/백엔드 검증 로그는 같은 GuardianGitAgent 로그 폴더에 기록합니다.

중요한 구분: GitHub Actions의 병렬 테스트/빌드 워크플로가 통과해도 이 로컬 코딩 agent가 설치·인증되어 실제 코드를 작성했다는 뜻은 아닙니다. Copilot CLI가 현재 PC에 있고 인증된 뒤 실행 로그와 draft PR이 생성되어야 AI 구현 트랙의 작동이 확인됩니다.
