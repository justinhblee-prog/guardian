# Guardian Windows 로컬 Ollama Runner 설치

## 목적

Guardian 저장소의 로컬 멀티플로우 자동화와 Aider/Qwen 검증을 사용자의 Windows 노트북에서 실행하기 위한 저장소 전용 GitHub Actions self-hosted runner를 설치합니다.

이 runner는 GitHub Actions hosted runner와 별개입니다. 노트북이 켜져 있고 사용자가 로그인해 있으며 Ollama가 실행 중일 때만 로컬 모델 작업을 받을 수 있습니다.

## 사전 조건

- Windows 10/11 x64
- PowerShell 7.2 이상
- Git for Windows, GitHub CLI(`gh`), Python 3.10 이상
- Aider 설치 및 실행 가능
- Ollama 서버가 `http://127.0.0.1:11434`에서 응답
- `qwen2.5-coder:7b` 모델 설치
- `justinhblee-prog/guardian` 저장소에 대한 GitHub runner 관리 권한

GitHub CLI 로그인은 사용자 본인이 직접 완료해야 합니다. 개인 액세스 토큰이나 runner 등록 토큰을 스크립트 인수, 저장소, 채팅에 붙여 넣지 마세요.

## 최초 설치

저장소를 로컬로 clone한 뒤 저장소 루트에서 PowerShell 7.2를 열어 실행합니다.

```powershell
gh auth login
```

그 다음 저장소의 `scripts\Install-GuardianLocalOllamaRunner.cmd`를 더블클릭합니다. 또는 PowerShell에서 직접 실행할 수 있습니다.

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Install-GuardianLocalOllamaRunner.ps1
```

설치 스크립트는 다음 작업을 자동 수행합니다.

1. Git 저장소가 올바른 `main` branch인지, 원격 URL이 Guardian 저장소인지, working tree가 깨끗한지 확인하고 `origin/main`으로 안전하게 fast-forward 동기화
2. Windows, Python, Git, GitHub CLI, Aider, Ollama 및 지정 모델을 사전 점검합니다.
3. 현재 GitHub CLI 계정에 저장소 runner 관리 권한이 있는지 확인합니다.
4. GitHub 공식 Actions runner release의 Windows x64 ZIP을 내려받고 API가 제공하는 SHA-256 digest와 대조합니다.
5. 일회용 runner 등록 토큰을 요청해 이 저장소 전용 runner로 등록합니다.
6. `guardian-windows`, `ollama` 라벨을 설정합니다.
7. 현재 Windows 사용자 권한으로 실행되는 로그인 예약 작업을 등록하고 즉시 시작합니다.
8. local-only Git CLI agent의 15분 예약 작업도 설치하고 즉시 실행합니다. 최대 3개의 독립 Ollama 작업을 처리하며, blocked task는 임의로 실행하지 않습니다.

Runner 바이너리와 작업 디렉터리는 기본적으로 `%LOCALAPPDATA%\Guardian\actions-runner` 아래에 둡니다. LocalSystem 서비스로 설치하지 않습니다. 현재 사용자의 Ollama/Aider 설치와 모델 환경을 그대로 사용하기 위해서입니다.

## 연결 확인

설치 스크립트는 GitHub runner와 기존 Git CLI agent의 예약 작업을 모두 설치합니다. Git CLI agent는 사용자의 로그인 세션에서 local Ollama worker를 호출하므로, GitHub self-hosted runner가 잠시 오프라인이어도 로컬 작업 루프 자체는 계속 동작할 수 있습니다. GitHub runner는 Aider/Qwen workflow의 실제 추론 검증에 별도로 사용됩니다.

GitHub 저장소의 **Settings → Actions → Runners**에서 다음 상태를 확인합니다.

- Runner name: `<컴퓨터이름>-Guardian`
- Status: `Online`
- Labels: `self-hosted`, `Windows`, `X64`, `guardian-windows`, `ollama`

온라인 확인 전에는 로컬 AI 작업이 시작됐다고 간주하지 않습니다. 작업 상태가 `pending`이면 runner가 아직 GitHub에 연결되지 않았거나, 컴퓨터가 절전/종료 상태이거나, Ollama/Aider 사전 조건을 충족하지 못한 것입니다.

## 보안 경계

- Runner는 `justinhblee-prog/guardian` 저장소에만 등록합니다.
- 로컬 PC에서 실행되는 workflow는 전용 `guardian-windows` 및 `ollama` 라벨을 요구합니다.
- Aider/Qwen 로컬 검증 workflow는 신뢰된 `main` push 또는 수동 `workflow_dispatch`에서만 실행합니다. PR 이벤트는 로컬 PC에서 실행하지 않습니다.
- 로컬 runner는 저장소 workflow가 실행하는 코드를 사용자의 Windows 계정 권한으로 실행할 수 있습니다. 신뢰하지 않는 브랜치/워크플로를 main에 병합하지 마세요.
- 등록 토큰은 GitHub에서 일회용으로 발급받으며 스크립트가 파일에 저장하지 않습니다. Runner의 자체 인증 파일은 사용자 프로필 아래에 보관됩니다.
- 이 스크립트는 GitHub Actions 요금제, 결제 설정, repository secrets, production signing keys, 라이선스 개인키 또는 Windows 방화벽을 변경하지 않습니다.

## 실행 조건 및 제한

- 노트북이 꺼져 있거나 절전 상태면 로컬 작업은 실행되지 않습니다.
- Windows 로그인 세션에서 예약 작업이 실행되어야 합니다.
- Ollama 서버와 지정 모델이 사용 가능해야 합니다.
- GitHub hosted CI는 로컬 runner와 별도로 계속 실행됩니다.
- 이 설치는 production license server나 Android/Windows release signing을 구성하지 않습니다.

## 제거

예약 작업을 먼저 중지·삭제하고 runner 등록을 해제해야 합니다. 저장소 runner 관리 권한이 있는 PowerShell에서 아래 명령을 실행합니다.

```powershell
& .\scripts\guardian-git-agent.ps1 -UninstallScheduledTask
$root = Join-Path $env:LOCALAPPDATA 'Guardian\actions-runner'
Unregister-ScheduledTask -TaskName 'GuardianLocalOllamaRunner' -Confirm:$false -ErrorAction SilentlyContinue
$token = gh api --method POST repos/justinhblee-prog/guardian/actions/runners/remove-token --jq .token
& (Join-Path $root 'config.cmd') remove --unattended --token $token
Remove-Item -LiteralPath $root -Recurse -Force
```

제거 후 GitHub **Settings → Actions → Runners**에서 해당 runner가 목록에서 사라졌는지 확인합니다.
