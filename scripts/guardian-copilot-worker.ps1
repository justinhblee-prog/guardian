[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$RepoRoot,
    [Parameter(Mandatory = $true)][string]$LogRoot
)
$ErrorActionPreference = 'Stop'
$OwnerRepo = 'justinhblee-prog/guardian'
$StatePath = Join-Path $LogRoot 'copilot-worker-state.json'
$WorktreeRoot = Join-Path $LogRoot 'copilot-worktrees'
$DailyAiCreditLimit = 1
$MaxAutopilotContinues = 3
New-Item -ItemType Directory -Force -Path $LogRoot, $WorktreeRoot | Out-Null

function Write-WorkerLog([string]$Message) {
    $line = '[{0}] [copilot-worker] {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message
    Add-Content -LiteralPath (Join-Path $LogRoot 'agent.log') -Value $line -Encoding UTF8
    Write-Host $line
}
function Invoke-WorkerGit([string[]]$Arguments, [switch]$AllowFailure) {
    $gitRoot = if ($script:GitRoot) { $script:GitRoot } else { $RepoRoot }
    $output = & git -C $gitRoot @Arguments 2>&1
    $code = $LASTEXITCODE
    if ($code -ne 0 -and -not $AllowFailure) {
        throw ('git {0} failed ({1}): {2}' -f ($Arguments -join ' '), $code, ($output -join [Environment]::NewLine))
    }
    return ,@{ Code = $code; Output = ($output -join [Environment]::NewLine) }
}
$state = @{}
if (Test-Path -LiteralPath $StatePath) {
    try {
        $saved = Get-Content -LiteralPath $StatePath -Raw | ConvertFrom-Json
        foreach ($property in $saved.PSObject.Properties) { $state[$property.Name] = $property.Value }
    } catch {
        Write-WorkerLog 'Previous state file is unreadable; creating a fresh state record.'
    }
}
function Save-WorkerState {
    $state | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $StatePath -Encoding UTF8
}

$today = Get-Date -Format 'yyyy-MM-dd'
if ([string]$state['lastAttemptDate'] -eq $today) {
    Write-WorkerLog 'Daily coding-agent limit already used; no duplicate AI session started.'
    return
}
$copilot = Get-Command copilot -ErrorAction SilentlyContinue
if (-not $copilot) {
    $lastProbe = $null
    if ($state['copilotMissingLoggedAt']) {
        try { $lastProbe = [DateTimeOffset]::Parse([string]$state['copilotMissingLoggedAt']) } catch {}
    }
    if (-not $lastProbe -or ([DateTimeOffset]::UtcNow - $lastProbe).TotalHours -ge 6) {
        Write-WorkerLog 'Copilot CLI is not installed or not in PATH. No AI credit was consumed; source/CI work continues independently.'
        $state['copilotMissingLoggedAt'] = [DateTimeOffset]::UtcNow.ToString('o')
        Save-WorkerState
    }
    return
}
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-WorkerLog 'GitHub CLI is unavailable; coding agent cannot inspect release issues or open a review PR.'
    return
}
& gh auth status --hostname github.com *> $null
if ($LASTEXITCODE -ne 0) {
    Write-WorkerLog 'GitHub CLI is not authenticated. Copilot CLI can reuse a valid gh login; no interactive login was attempted.'
    return
}
& gh auth setup-git *> $null
if ($LASTEXITCODE -ne 0) {
    Write-WorkerLog 'GitHub CLI could not configure Git authentication; no coding worktree was created.'
    return
}

$branchName = 'automation/copilot-guardian-' + (Get-Date -Format 'yyyyMMdd')
$existingRemote = & git -C $RepoRoot ls-remote --heads origin $branchName 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-WorkerLog 'Could not check remote coding-agent branch; refusing to create a duplicate task.'
    return
}
if ($existingRemote) {
    Write-WorkerLog "Remote coding-agent branch already exists ($branchName); waiting for its PR/CI lifecycle."
    $state['lastAttemptDate'] = $today
    Save-WorkerState
    return
}
Invoke-WorkerGit @('fetch', 'origin', 'main', '--prune') | Out-Null
$worktree = Join-Path $WorktreeRoot (Get-Date -Format 'yyyyMMdd-HHmmss')
$promptLog = Join-Path $LogRoot ('copilot-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
$state['lastAttemptDate'] = $today
$state['lastAttemptUtc'] = [DateTimeOffset]::UtcNow.ToString('o')
$state['lastBranch'] = $branchName
Save-WorkerState

try {
    Invoke-WorkerGit @('worktree', 'add', '-b', $branchName, $worktree, 'origin/main') | Out-Null
    $script:GitRoot = $worktree
    Write-WorkerLog "Created isolated code worktree: $worktree (base=origin/main)."
    $prompt = @'
You are the autonomous implementation engineer for Guardian AdBlock. Work only inside this isolated Git worktree.

Before editing:
1. Read AGENTS.md or repository instructions if present, README.md, docs/REMAINING_WORKFLOW_KO.md, and GitHub Issues #141, #142, #143, #144 using gh issue view.
2. Inspect the actual current source and tests. Select exactly ONE highest-priority incomplete task that can be completed entirely by changing repository source, tests, or documentation.
3. Do not select a task that requires a Samsung/physical handset, user-provided stack trace, redistribution rights approval, production keystore/private key, production secret, customer data, external payment event, or modification of the user's live server/database. Leave those blockers explicit.

Implementation:
- Make the smallest complete code change for the selected task.
- Add or update regression tests that fail before the fix and pass after it.
- Run the narrowest relevant tests, then broader checks where available.
- Update the relevant documentation and issue checklist only when the code evidence genuinely proves completion. Never claim physical-device, production, legal, or live-server verification.
- Do not invent configuration values, credentials, signing keys, source approvals, endpoints, or test results.
- Do not ask the user questions. Make conservative engineering decisions and stop if the task cannot be safely completed.

Safety:
- Do not run git commit, git push, gh pr create, gh pr merge, gh issue close, destructive reset/clean commands, production deployment commands, or commands that modify a live service.
- Do not edit .env files, secret stores, keys, keystores, certificates, user data, or generated release artifacts.
- Keep all changes in the current repository worktree. Do not modify files outside it.
- Do not expand scope into visual redesign or unrelated refactoring.
- At the end, print a compact report with: selected issue/task, root cause, files changed, tests actually run with exact outcomes, any remaining blockers, and whether no safe source-only task was available.
'@
    Write-WorkerLog "Starting bounded Copilot Autopilot session (AI credit soft cap=$DailyAiCreditLimit per response; max continuations=$MaxAutopilotContinues)."
    $copilotArgs = @(
        '--autopilot',
        '--no-ask-user',
        '--assisted-approval',
        '--max-autopilot-continues', "$MaxAutopilotContinues",
        '--max-ai-credits', "$DailyAiCreditLimit",
        '--available-tools', 'powershell,view,edit,create,apply_patch,grep,glob',
        '--deny-tool', 'shell(git commit)',
        '--deny-tool', 'shell(git push)',
        '--deny-tool', 'shell(gh pr create)',
        '--deny-tool', 'shell(gh pr merge)',
        '--deny-tool', 'shell(gh issue close)',
        '-p', $prompt
    )
    $agentOutput = & $copilot.Source @copilotArgs 2>&1
    $copilotExit = $LASTEXITCODE
    $agentOutput | Tee-Object -FilePath $promptLog
    if ($copilotExit -ne 0) {
        Write-WorkerLog "Copilot CLI exited with code $copilotExit. Worktree is retained for diagnosis; no changes were pushed."
        return
    }

    $status = (Invoke-WorkerGit @('status', '--porcelain')).Output.Trim()
    if ([string]::IsNullOrWhiteSpace($status)) {
        Write-WorkerLog 'Copilot completed without source changes. No empty commit or PR was created.'
        return
    }
    $check = Invoke-WorkerGit @('diff', '--check', 'HEAD') -AllowFailure
    if ($check.Code -ne 0) {
        Write-WorkerLog "Diff whitespace validation failed; no push. $($check.Output)"
        return
    }

    $changed = @((Invoke-WorkerGit @('diff', 'HEAD', '--name-only')).Output -split "\r?\n" | Where-Object { $_ })
    $untracked = @((Invoke-WorkerGit @('ls-files', '--others', '--exclude-standard')).Output -split "\r?\n" | Where-Object { $_ })
    $changed = @($changed + $untracked | Sort-Object -Unique)
    $allowedRoots = @('android/', 'server/', 'shared/', 'scripts/', 'docs/', '.github/workflows/')
    foreach ($file in $changed) {
        if (-not ($allowedRoots | Where-Object { $file.StartsWith($_, [StringComparison]::OrdinalIgnoreCase) })) {
            Write-WorkerLog "Blocked out-of-scope file: $file. No push."
            return
        }
        if ($file -match '(^|/)(\.env[^/]*|secrets/)|\.(jks|keystore|p12|pfx|dpapi|pem)$' -or
            $file -match '(^|/)(dist|logs|outputs)/' -or
            $file -eq 'shared/filter_sources.json') {
            Write-WorkerLog "Blocked protected/operational file: $file. No push."
            return
        }
    }
    if ($changed.Count -gt 25) {
        Write-WorkerLog "Agent changed $($changed.Count) files, exceeding the 25-file safety bound. No push."
        return
    }

    if ($changed | Where-Object { $_ -like 'android/*' }) {
        $gradle = Get-Command gradle -ErrorAction SilentlyContinue
        if ($gradle) {
            Write-WorkerLog 'Running Android unit tests, lint and debug assembly before PR creation.'
            & gradle -p (Join-Path $worktree 'android') --no-daemon testDebugUnitTest lintDebug assembleDebug 2>&1 |
                Tee-Object -FilePath (Join-Path $LogRoot 'copilot-android-validation.log')
            if ($LASTEXITCODE -ne 0) {
                Write-WorkerLog 'Local Android verification failed; no PR was pushed.'
                return
            }
        } else {
            Write-WorkerLog 'Gradle CLI is unavailable locally; leaving Android validation to the PR CI gate.'
        }
    }
    if ($changed | Where-Object { $_ -like 'server/*' }) {
        foreach ($dir in @('server/license-api', 'server/filter-distribution')) {
            if (Test-Path (Join-Path $worktree (Join-Path $dir 'package.json'))) {
                $npm = Get-Command npm -ErrorAction SilentlyContinue
                if (-not $npm) {
                    Write-WorkerLog 'npm is unavailable locally; leaving backend validation to the PR CI gate.'
                    break
                }
                Write-WorkerLog "Running backend tests in $dir."
                Push-Location (Join-Path $worktree $dir)
                try {
                    & npm test 2>&1 | Tee-Object -FilePath (Join-Path $LogRoot ('copilot-' + ($dir -replace '/', '-') + '-test.log'))
                    if ($LASTEXITCODE -ne 0) {
                        Write-WorkerLog "Backend tests failed in $dir; no PR was pushed."
                        return
                    }
                } finally { Pop-Location }
            }
        }
    }
    if (Get-Command pwsh -ErrorAction SilentlyContinue) {
        foreach ($file in ($changed | Where-Object { $_ -like '*.ps1' })) {
            $fullPath = Join-Path $worktree $file
            $tokens = $null
            $errors = $null
            [System.Management.Automation.Language.Parser]::ParseFile($fullPath, [ref]$tokens, [ref]$errors) | Out-Null
            if ($errors.Count -gt 0) {
                Write-WorkerLog "PowerShell parser rejected $file; no PR was pushed. $($errors | Out-String)"
                return
            }
        }
    }

    Invoke-WorkerGit @('add', '--all') | Out-Null
    $staged = (Invoke-WorkerGit @('diff', '--cached', '--name-only')).Output
    if ([string]::IsNullOrWhiteSpace($staged)) {
        Write-WorkerLog 'No safe staged source changes remained after validation.'
        return
    }
    Invoke-WorkerGit @('-c', 'user.name=Guardian Automation', '-c', 'user.email=guardian-automation@users.noreply.github.com', 'commit', '-m', 'feat(guardian): implement bounded release task') | Out-Null
    Invoke-WorkerGit @('push', '--set-upstream', 'origin', $branchName) | Out-Null
    $body = @"
## Autonomous implementation task

- Isolated base: current origin/main
- Coding executor: GitHub Copilot CLI Autopilot (local, bounded run)
- Validation: see the linked Actions checks and the local execution log.
- No production credentials, signing keys, redistribution approvals, or live services were modified.
- This PR is intentionally draft and requires review; it is never auto-merged.
"@
    $prOutput = & gh pr create --repo $OwnerRepo --base main --head $branchName --draft --title ('Guardian autonomous code task ' + (Get-Date -Format 'yyyy-MM-dd')) --body $body 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-WorkerLog "Changes were pushed but PR creation failed. Branch=$branchName; $($prOutput -join [Environment]::NewLine)"
        return
    }
    Write-WorkerLog "Draft implementation PR created: $($prOutput -join ' ')"
    $state['lastPrUrl'] = ($prOutput | Select-Object -Last 1).ToString()
    Save-WorkerState
} catch {
    Write-WorkerLog "Coding worker failed safely: $($_.Exception.Message)"
} finally {
    if (Test-Path -LiteralPath $worktree) {
        $script:GitRoot = $worktree
        $hasChanges = (Invoke-WorkerGit @('status', '--porcelain') -AllowFailure).Output.Trim()
        if ([string]::IsNullOrWhiteSpace($hasChanges)) {
            $script:GitRoot = $RepoRoot
            Invoke-WorkerGit @('worktree', 'remove', '--force', $worktree) -AllowFailure | Out-Null
            Invoke-WorkerGit @('worktree', 'prune') -AllowFailure | Out-Null
        } else {
            Write-WorkerLog "Retained non-empty isolated worktree for inspection: $worktree"
        }
    }
}
