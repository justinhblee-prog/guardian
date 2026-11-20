[CmdletBinding()]
param([switch]$RunLoop)

$ErrorActionPreference = 'Stop'
$Repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$AgentScript = Join-Path $PSScriptRoot 'guardian-git-agent.ps1'
$LogRoot = Join-Path $env:LOCALAPPDATA 'GuardianGitAgent'
$LogFile = Join-Path $LogRoot 'agent.log'
$LockFile = Join-Path $LogRoot 'agent.lock'
New-Item -ItemType Directory -Force -Path $LogRoot | Out-Null

function Write-BootstrapLog([string]$Message) {
    $line = '[{0}] [bootstrap] {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message
    Add-Content -LiteralPath $LogFile -Value $line -Encoding UTF8
    Write-Host $line
}

function Sync-CleanMain {
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        Write-BootstrapLog 'Git CLI is unavailable; cannot refresh the local agent.'
        return
    }
    $fetch = & git -C $Repo fetch origin main --prune 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-BootstrapLog ("Fetch failed; existing local files are preserved. " + ($fetch -join [Environment]::NewLine))
        return
    }
    $branch = (& git -C $Repo branch --show-current 2>&1 | Out-String).Trim()
    $dirty = (& git -C $Repo status --porcelain 2>&1 | Out-String).Trim()
    if ($branch -ne 'main') {
        Write-BootstrapLog "Local checkout is '$branch'; refusing to switch branches or overwrite user work."
        return
    }
    if (-not [string]::IsNullOrWhiteSpace($dirty)) {
        Write-BootstrapLog 'Local main has uncommitted changes; refusing to overwrite the working tree.'
        return
    }
    $merge = & git -C $Repo merge --ff-only origin/main 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-BootstrapLog ("Fast-forward update failed; local branch was not force-reset. " + ($merge -join [Environment]::NewLine))
        return
    }
    Write-BootstrapLog 'Clean local main synchronized to origin/main before the agent process starts.'
}

function Stop-LegacyAgentLoopSafely {
    $stream = $null
    $deadline = (Get-Date).AddSeconds(120)
    while (-not $stream -and (Get-Date) -lt $deadline) {
        try {
            $stream = [System.IO.File]::Open($LockFile, [System.IO.FileMode]::OpenOrCreate, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
        } catch [System.IO.IOException] {
            Start-Sleep -Seconds 2
        }
    }
    if (-not $stream) {
        Write-BootstrapLog 'Agent lock remained busy for 120 seconds; leaving existing process untouched.'
        return
    }
    try {
        $agentPath = [System.IO.Path]::GetFullPath($AgentScript)
        try {
            $processes = @(Get-CimInstance Win32_Process -Filter "Name='powershell.exe' OR Name='pwsh.exe'" -ErrorAction Stop)
            foreach ($process in $processes) {
                if ([int]$process.ProcessId -eq $PID) { continue }
                $commandLine = [string]$process.CommandLine
                if ($commandLine -and $commandLine.IndexOf($agentPath, [StringComparison]::OrdinalIgnoreCase) -ge 0 -and $commandLine -match '(?i)(^|\s)-RunLoop(\s|$)') {
                    try {
                        Stop-Process -Id ([int]$process.ProcessId) -Force -ErrorAction Stop
                        Write-BootstrapLog "Stopped legacy RunLoop process $($process.ProcessId) after obtaining the shared agent lock."
                    } catch {
                        Write-BootstrapLog "Could not stop legacy RunLoop process $($process.ProcessId): $($_.Exception.Message)"
                    }
                }
            }
        } catch {
            Write-BootstrapLog "Legacy process discovery unavailable; shared lock still prevents concurrent Git mutation. $($_.Exception.Message)"
        }
    } finally {
        $stream.Dispose()
    }
}

if (-not (Test-Path -LiteralPath $AgentScript)) {
    Write-BootstrapLog "Agent script not found: $AgentScript"
    exit 1
}

Sync-CleanMain
Stop-LegacyAgentLoopSafely

$enginePath = (Get-Process -Id $PID).Path
$arguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"{0}"' -f $AgentScript))
if ($RunLoop) { $arguments += '-RunLoop' }
Start-Process -FilePath $enginePath -ArgumentList $arguments -WorkingDirectory $Repo -WindowStyle Hidden
$mode = if ($RunLoop) { 'RunLoop fallback' } else { 'single scheduled cycle' }
Write-BootstrapLog "Started the current on-disk Guardian Git agent ($mode)."
