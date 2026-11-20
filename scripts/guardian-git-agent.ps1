[CmdletBinding()]
param([switch]$InstallScheduledTask,[switch]$UninstallScheduledTask,[switch]$Once)
$ErrorActionPreference = 'Stop'
$Repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$TaskName = 'Guardian-Git-Automation'
$LogRoot = Join-Path $env:LOCALAPPDATA 'GuardianGitAgent'
$WorktreeRoot = Join-Path $LogRoot 'worktrees'
$LogFile = Join-Path $LogRoot 'agent.log'
$OwnerRepo = 'justinhblee-prog/guardian'
New-Item -ItemType Directory -Force -Path $LogRoot, $WorktreeRoot | Out-Null
function Write-Log([string]$Message) {
    $line = '[{0}] {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message
    Add-Content -LiteralPath $LogFile -Value $line -Encoding UTF8
    Write-Host $line
}
function Invoke-Git([string]$Path, [string[]]$Arguments, [switch]$AllowFailure) {
    $output = & git -C $Path @Arguments 2>&1
    $code = $LASTEXITCODE
    if ($code -ne 0 -and -not $AllowFailure) {
        throw ('git -C "{0}" {1} failed ({2}): {3}' -f $Path, ($Arguments -join ' '), $code, ($output -join [Environment]::NewLine))
    }
    return ,@{ Code = $code; Output = ($output -join [Environment]::NewLine) }
}
function Install-AgentTask {
    $pwsh = (Get-Command pwsh.exe -ErrorAction SilentlyContinue).Source
    if (-not $pwsh) { $pwsh = (Get-Command powershell.exe -ErrorAction Stop).Source }
    $script = Join-Path $PSScriptRoot 'guardian-git-agent.ps1'
    $action = New-ScheduledTaskAction -Execute $pwsh -Argument ('-NoProfile -ExecutionPolicy Bypass -File "{0}" -Once' -f $script) -WorkingDirectory $Repo
    $trigger = New-ScheduledTaskTrigger -AtLogOn
    $repeat = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(2) -RepetitionInterval (New-TimeSpan -Minutes 15) -RepetitionDuration (New-TimeSpan -Days 3650)
    Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger @($trigger, $repeat) -Description 'Guardian Git CLI: fetch main, audit open PRs, safely rebase same-repository branches, and push with lease.' -Settings (New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Minutes 20)) -Force | Out-Null
    Write-Log "Scheduled task installed: runs at logon and every 15 minutes. Repo=$Repo"
}
function Invoke-Agent {
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'Git CLI is not installed or not in PATH.' }
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'GitHub CLI (gh) is not installed or not in PATH.' }
    & gh auth status *> $null
    if ($LASTEXITCODE -ne 0) { throw 'GitHub CLI is not authenticated for this Windows user. Run gh auth login once.' }
    & gh auth setup-git *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Could not configure Git to use the authenticated GitHub CLI.' }
    Write-Log 'Starting Git CLI automation.'
    Invoke-Git $Repo @('fetch', 'origin', '--prune') | Out-Null

    $runsJson = & gh run list --repo $OwnerRepo --limit 30 --json databaseId,name,status,conclusion,headBranch,headSha,url
    if ($LASTEXITCODE -ne 0) { throw 'Could not retrieve recent GitHub Actions runs.' }
    $runs = @($runsJson | ConvertFrom-Json)
    $failedRuns = @($runs | Where-Object { $_.status -eq 'completed' -and $_.conclusion -eq 'failure' })
    $failureLog = Join-Path $LogRoot 'latest-ci-failures.log'
    Set-Content -LiteralPath $failureLog -Value ('Guardian CI failure audit: ' + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')) -Encoding UTF8
    foreach ($run in $failedRuns) {
        Add-Content -LiteralPath $failureLog -Value (([Environment]::NewLine + '## Run {0}: {1} | branch={2} | commit={3} | {4}') -f $run.databaseId, $run.name, $run.headBranch, $run.headSha, $run.url) -Encoding UTF8
        $failedLog = & gh run view ([string]$run.databaseId) --repo $OwnerRepo --log-failed 2>&1
        $logExit = $LASTEXITCODE
        if ($logExit -eq 0) {
            Add-Content -LiteralPath $failureLog -Value ($failedLog -join [Environment]::NewLine) -Encoding UTF8
            Write-Log ('Captured failed CI logs for run {0}.' -f $run.databaseId)
        } else {
            Add-Content -LiteralPath $failureLog -Value ('Failed to retrieve logs (exit {0}): {1}' -f $logExit, ($failedLog -join [Environment]::NewLine)) -Encoding UTF8
            Write-Log ('CI run {0} failed; log retrieval also failed.' -f $run.databaseId)
        }
    }
    Write-Log ('Recent CI runs inspected: {0}; failed runs with logs requested: {1}.' -f $runs.Count, $failedRuns.Count)
    $branch = (Invoke-Git $Repo @('branch', '--show-current')).Output.Trim()
    $dirty = (Invoke-Git $Repo @('status', '--porcelain')).Output.Trim()
    if ($branch -eq 'main' -and -not $dirty) {
        Invoke-Git $Repo @('pull', '--ff-only', 'origin', 'main') | Out-Null
        Write-Log 'Local main fast-forward synchronization passed.'
    } elseif ($branch -eq 'main' -and $dirty) {
        Write-Log 'Local main has uncommitted changes; skipped pull to protect the working tree.'
    } else {
        Write-Log "Local checkout is on '$branch'; left it untouched. PR maintenance uses isolated worktrees."
    }
    $prsJson = & gh pr list --repo $OwnerRepo --state open --base main --limit 200 --json number,headRefName,headRepository,baseRefName,isDraft
    if ($LASTEXITCODE -ne 0) { throw 'Could not retrieve open PR list with gh CLI.' }
    $prs = @($prsJson | ConvertFrom-Json)
    Write-Log ("Open PRs targeting main: {0}" -f $prs.Count)
    foreach ($pr in $prs) {
        $number = [int]$pr.number
        $branchName = [string]$pr.headRefName
        $headRepo = [string]$pr.headRepository.nameWithOwner
        if ($headRepo -ne $OwnerRepo) {
            Write-Log "PR #$number skipped: fork branch ($headRepo) cannot be safely pushed with this automation."
            continue
        }
        if ([string]::IsNullOrWhiteSpace($branchName) -or $branchName -eq 'main') {
            Write-Log "PR #$number skipped: invalid/protected branch name."
            continue
        }
        $safeName = ($branchName -replace '[^A-Za-z0-9._-]', '_')
        $worktree = Join-Path $WorktreeRoot ("pr-{0}-{1}" -f $number, $safeName)
        if (Test-Path $worktree) {
            Invoke-Git $Repo @('worktree', 'remove', '--force', $worktree) -AllowFailure | Out-Null
            if (Test-Path $worktree) { Remove-Item -LiteralPath $worktree -Recurse -Force }
        }
        try {
            Invoke-Git $Repo @('worktree', 'add', '--detach', $worktree, "origin/$branchName") | Out-Null
            # Keep the isolated worktree detached. This avoids collisions when the same PR branch
            # already exists in the primary checkout or another linked worktree.
            Invoke-Git $worktree @('switch', '--detach') | Out-Null
            $counts = (Invoke-Git $worktree @('rev-list', '--left-right', '--count', "origin/main...HEAD")).Output.Trim()
            $parts = $counts -split '\s+'
            $behind = [int]$parts[0]
            $ahead = [int]$parts[1]
            if ($behind -eq 0) {
                Write-Log "PR #$number ($branchName): already based on current main; ahead=$ahead."
                continue
            }
            $mergeBase = (Invoke-Git $worktree @('merge-base', 'HEAD', 'origin/main')).Output.Trim()
            if (-not $mergeBase) { throw "PR #$number has no merge base with origin/main." }
            Write-Log "PR #$number ($branchName): rebasing; behind=$behind ahead=$ahead."
            $rebase = Invoke-Git $worktree @('rebase', '--rebase-merges', 'origin/main') -AllowFailure
            if ($rebase.Code -ne 0) {
                Invoke-Git $worktree @('rebase', '--abort') -AllowFailure | Out-Null
                Write-Log "PR #$number BLOCKED: rebase conflict; branch left unchanged. $($rebase.Output)"
                continue
            }
            $push = Invoke-Git $worktree @('push', '--force-with-lease', 'origin', "HEAD:refs/heads/$branchName") -AllowFailure
            if ($push.Code -ne 0) {
                Write-Log "PR #$number BLOCKED: safe push rejected (branch protection or concurrent update). $($push.Output)"
            } else {
                Write-Log "PR #$number UPDATED: rebased onto current main and pushed with lease."
            }
        } catch {
            Write-Log "PR #$number ERROR: $($_.Exception.Message)"
        } finally {
            if (Test-Path $worktree) {
                Invoke-Git $Repo @('worktree', 'remove', '--force', $worktree) -AllowFailure | Out-Null
            }
        }
    }
    Invoke-Git $Repo @('worktree', 'prune') -AllowFailure | Out-Null
    Write-Log 'Automation cycle finished. See log for updated, skipped, and blocked PRs.'
}
if ($InstallScheduledTask) {
    Install-AgentTask
    Invoke-Agent
} elseif ($UninstallScheduledTask) {
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
    Write-Log 'Scheduled task removed.'
} else {
    Invoke-Agent
}
