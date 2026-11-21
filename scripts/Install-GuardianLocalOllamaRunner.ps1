[CmdletBinding()]
param(
    [string]$Repository = 'justinhblee-prog/guardian',
    [string]$RunnerRoot = (Join-Path $env:LOCALAPPDATA 'Guardian\actions-runner'),
    [string]$RunnerName = "$env:COMPUTERNAME-Guardian",
    [string]$OllamaModel = 'qwen2.5-coder:7b',
    [switch]$NoAutoStart
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Assert-Command {
    param([Parameter(Mandatory)][string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "필수 명령을 찾을 수 없습니다: $Name"
    }
}

function Invoke-GhText {
    param([Parameter(Mandatory)][string[]]$Arguments)
    $result = & gh @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "GitHub CLI 요청 실패: $($Arguments -join ' ')$([Environment]::NewLine)$($result | Out-String)"
    }
    return (($result | Out-String).Trim())
}

function Get-GhRunnerToken {
    param([Parameter(Mandatory)][ValidateSet('registration-token','remove-token')][string]$Kind)
    $route = "repos/$Repository/actions/runners/$Kind"
    return (Invoke-GhText -Arguments @('api', '--method', 'POST', $route, '--jq', '.token'))
}

function Test-OllamaModel {
    param([Parameter(Mandatory)][string]$Model)
    $tags = Invoke-RestMethod -Uri 'http://127.0.0.1:11434/api/tags' -TimeoutSec 15
    $names = @($tags.models | ForEach-Object {
        if ($_.name) { [string]$_.name }
        if ($_.model) { [string]$_.model }
    })
    if ($names -notcontains $Model) {
        throw "로컬 Ollama 모델 '$Model'이 설치되어 있지 않습니다. 설치된 모델: $($names -join ', ')"
    }
}

if ($env:OS -ne 'Windows_NT') { throw '이 설치 스크립트는 Windows에서만 실행할 수 있습니다.' }
if ([string]$Repository -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') {
    throw 'Repository는 owner/name 형식이어야 합니다.'
}
if ([string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
    throw 'LOCALAPPDATA를 확인할 수 없습니다. 일반 Windows 사용자 세션에서 실행하세요.'
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Write-Host 'Guardian 저장소와 로컬 Ollama runner 사전 점검을 시작합니다.'
Assert-Command -Name 'git'
Assert-Command -Name 'gh'

$currentBranch = (& git -C $repoRoot branch --show-current 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $currentBranch -ne 'main') {
    throw "저장소 main branch에서 실행해야 합니다. 현재 branch: $currentBranch"
}
$originUrl = (& git -C $repoRoot remote get-url origin 2>&1 | Out-String).Trim()
$expectedRemotePattern = 'github\.com[:/]' + [regex]::Escape($Repository) + '(\.git)?$'
if ($LASTEXITCODE -ne 0 -or $originUrl -notmatch $expectedRemotePattern) {
    throw "origin remote가 요청한 저장소와 일치하지 않습니다: $originUrl"
}
$workingTree = & git -C $repoRoot status --porcelain
if ($LASTEXITCODE -ne 0) { throw 'Git working tree 상태를 확인할 수 없습니다.' }
if (-not [string]::IsNullOrWhiteSpace(($workingTree | Out-String))) {
    throw '로컬 변경사항이 있습니다. 먼저 commit 또는 stash한 뒤 다시 실행하세요. 설치 스크립트는 로컬 변경사항을 덮어쓰지 않습니다.'
}
$null = Invoke-GhText -Arguments @('auth', 'status', '--hostname', 'github.com')
$null = Invoke-GhText -Arguments @('auth', 'setup-git')
& git -C $repoRoot fetch origin main --prune
if ($LASTEXITCODE -ne 0) { throw 'origin/main을 안전하게 fetch하지 못했습니다.' }
& git -C $repoRoot merge --ff-only origin/main
if ($LASTEXITCODE -ne 0) { throw '현재 main branch를 origin/main으로 fast-forward할 수 없습니다. 로컬 commit을 보존하고 수동 확인이 필요합니다.' }
$localHead = (& git -C $repoRoot rev-parse HEAD).Trim()
$remoteHead = (& git -C $repoRoot rev-parse origin/main).Trim()
if ($LASTEXITCODE -ne 0 -or $localHead -ne $remoteHead) {
    throw '로컬 main이 origin/main과 일치하지 않습니다.'
}

$multiFlowScript = Join-Path $repoRoot 'scripts\guardian-multiflow.ps1'
$multiFlowWorker = Join-Path $repoRoot 'scripts\guardian_multiflow.py'
$legacyWorker = Join-Path $repoRoot 'scripts\guardian-copilot-worker.ps1'
$gitAgent = Join-Path $repoRoot 'scripts\guardian-git-agent.ps1'
foreach ($requiredPath in @($multiFlowScript, $multiFlowWorker, $legacyWorker, $gitAgent)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Local Ollama multi-flow files are missing: $requiredPath"
    }
}
$legacyWorkerContent = Get-Content -LiteralPath $legacyWorker -Raw
if ($legacyWorkerContent -match 'copilot\.Source|winget install --id GitHub\.Copilot|--autopilot') {
    throw 'Refusing to install a scheduled local agent while a Copilot/remote fallback is still present.'
}
$runnerFullPath = [IO.Path]::GetFullPath($RunnerRoot)
if ($runnerFullPath.StartsWith($repoRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'RunnerRoot는 Git 저장소 바깥에 있어야 합니다.'
}
if (-not $runnerFullPath.StartsWith([IO.Path]::GetFullPath($env:LOCALAPPDATA), [StringComparison]::OrdinalIgnoreCase)) {
    throw '보안상 RunnerRoot는 현재 사용자의 LOCALAPPDATA 아래에만 설치할 수 있습니다.'
}

Write-Host '로컬 Python, Ollama 및 runner 환경을 확인합니다.'
$pythonCommand = Get-Command python -ErrorAction SilentlyContinue
$pyCommand = Get-Command py -ErrorAction SilentlyContinue
if (-not $pythonCommand -and -not $pyCommand) {
    throw 'Python 3.10 이상이 필요합니다. python.exe 또는 py.exe를 PATH에 등록하세요.'
}
Assert-Command -Name 'ollama'

if ($pythonCommand) {
    $pythonVersionText = (& $pythonCommand.Source --version 2>&1 | Out-String).Trim()
} else {
    $pythonVersionText = (& $pyCommand.Source -3 --version 2>&1 | Out-String).Trim()
}
if ($LASTEXITCODE -ne 0 -or $pythonVersionText -notmatch 'Python 3\.(1[0-9]|[2-9][0-9])') {
    throw "Python 3.10 이상이 필요합니다: $pythonVersionText"
}
$repoAdmin = Invoke-GhText -Arguments @('api', "repos/$Repository", '--jq', '.permissions.admin')
if ($repoAdmin -ne 'true') {
    throw '현재 gh 인증 계정에 이 저장소의 runner 관리 권한이 없습니다. Repository Administration 권한이 필요합니다.'
}

# The multi-flow worker calls the local Ollama HTTP API directly; Aider is not required.
Test-OllamaModel -Model $OllamaModel

$taskName = 'GuardianLocalOllamaRunner'
$currentUser = [Security.Principal.WindowsIdentity]::GetCurrent().Name
New-Item -ItemType Directory -Path $runnerFullPath -Force | Out-Null

$configPath = Join-Path $runnerFullPath '.runner'
if (Test-Path -LiteralPath $configPath) {
    Write-Host '기존 Guardian runner 등록을 안전하게 갱신합니다.'
    $removeToken = Get-GhRunnerToken -Kind 'remove-token'
    $configCommand = Join-Path $runnerFullPath 'config.cmd'
    & $configCommand remove --unattended --token $removeToken
    if ($LASTEXITCODE -ne 0) { throw '기존 self-hosted runner 등록 해제에 실패했습니다.' }
}

$runnerExecutable = Join-Path $runnerFullPath 'run.cmd'
if (-not (Test-Path -LiteralPath $runnerExecutable)) {
    Write-Host '공식 GitHub Actions runner 패키지를 내려받습니다.'
    $release = Invoke-RestMethod -Uri 'https://api.github.com/repos/actions/runner/releases/latest' -TimeoutSec 30
    $asset = @($release.assets | Where-Object { $_.name -match '^actions-runner-win-x64-[0-9.]+\.zip$' } | Select-Object -First 1)
    if ($asset.Count -ne 1) { throw '공식 Windows x64 runner ZIP asset을 찾을 수 없습니다.' }
    $digest = [string]$asset[0].digest
    if ($digest -notmatch '^sha256:([a-fA-F0-9]{64})$') {
        throw 'GitHub release metadata에 신뢰할 수 있는 SHA-256 digest가 없습니다. 검증되지 않은 runner를 설치하지 않습니다.'
    }
    $expectedDigest = $Matches[1].ToLowerInvariant()
    $zipPath = Join-Path $env:TEMP $asset[0].name
    Invoke-WebRequest -Uri $asset[0].browser_download_url -OutFile $zipPath -TimeoutSec 180
    $actualDigest = (Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualDigest -ne $expectedDigest) {
        Remove-Item -LiteralPath $zipPath -Force -ErrorAction SilentlyContinue
        throw 'GitHub Actions runner ZIP SHA-256 검증에 실패했습니다.'
    }
    Expand-Archive -LiteralPath $zipPath -DestinationPath $runnerFullPath -Force
    Remove-Item -LiteralPath $zipPath -Force
}

$registrationToken = Get-GhRunnerToken -Kind 'registration-token'
$configCommand = Join-Path $runnerFullPath 'config.cmd'
$configArguments = @('--unattended','--url',"https://github.com/$Repository",'--token',$registrationToken,'--name',$RunnerName,'--labels','guardian-windows,ollama','--work','_work','--replace')
& $configCommand @configArguments
if ($LASTEXITCODE -ne 0) { throw 'Guardian Windows runner 등록에 실패했습니다.' }

# Use the current user's interactive session so Ollama/Aider user installs remain visible.
# Do not install a LocalSystem service, which may not see the user's model environment.
$action = New-ScheduledTaskAction -Execute $env:ComSpec -Argument ('/d /c "' + $runnerExecutable + '"') -WorkingDirectory $runnerFullPath
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $currentUser
$principal = New-ScheduledTaskPrincipal -UserId $currentUser -LogonType Interactive -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -RestartCount 5 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero)
Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Description 'Guardian repository-scoped Windows runner for local Ollama tasks.' -Force | Out-Null

if (-not $NoAutoStart) {
    Start-ScheduledTask -TaskName $taskName
    $runnerOnline = $false
    $runnerStatusQuery = '.runners[] | select(.name == "' + $RunnerName + '") | .status'
    for ($attempt = 1; $attempt -le 12; $attempt++) {
        try {
            $status = Invoke-GhText -Arguments @('api', "repos/$Repository/actions/runners", '--jq', $runnerStatusQuery)
            if ($status -eq 'online') {
                $runnerOnline = $true
                break
            }
        } catch {
            Write-Verbose "Runner status check attempt $attempt did not return a status."
        }
        Start-Sleep -Seconds 5
    }
    if ($runnerOnline) {
        Write-Host 'GitHub reports this Guardian runner as Online.'
    } else {
        Write-Warning 'Runner is registered, but GitHub has not reported it Online within 60 seconds. Check Ollama, the scheduled task and GitHub Settings > Actions > Runners.'
    }
}

Write-Host ''
Write-Host 'Guardian local Ollama runner 설정이 완료되었습니다.'
Write-Host "저장 위치: $runnerFullPath"
Write-Host "Runner 이름: $RunnerName"
Write-Host 'Runner labels: self-hosted, Windows, X64, guardian-windows, ollama'
Write-Host "자동 시작 작업: $taskName (현재 사용자 로그인 시 실행)"
Write-Host '현재 GitHub runner 상태는 GitHub Actions > Runners에서 Online으로 확인해야 합니다.'
Write-Host '기존 Git CLI scheduled agent를 설치해 local Ollama 작업을 15분 주기로 실행합니다.'
& $gitAgent -InstallScheduledTask
if (-not $?) { throw 'Guardian local Git CLI scheduled agent installation failed.' }
Write-Host 'Local agent: maximum 3 independent Ollama workstreams; blocked tasks remain safely idle.'
Write-Host '주의: PC가 절전/종료되거나 Ollama가 중지되면 로컬 AI 작업도 대기합니다.'
