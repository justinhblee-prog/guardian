[CmdletBinding()]
param(
    [ValidateRange(1,3)][int]$MaxTasks = 1,
    [string]$TaskId = '',
    [switch]$ValidateOnly,
    [switch]$SmokeTest
)
$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Worker = Join-Path $PSScriptRoot 'guardian_multiflow.py'
if (-not (Test-Path -LiteralPath $Worker -PathType Leaf)) {
    throw "Guardian local Ollama worker was not found: $Worker"
}
$python = Get-Command python -ErrorAction SilentlyContinue
if (-not $python) { $python = Get-Command py -ErrorAction SilentlyContinue }
if (-not $python) {
    throw 'Python 3 is required. Install Python 3.10+ and make python.exe or py.exe visible to this Windows account.'
}
$arguments = @()
if ($python.Name -eq 'py.exe' -or $python.Name -eq 'py') {
    $arguments += @('-3', $Worker)
} else {
    $arguments += $Worker
}
$arguments += @('--repo-root', $RepoRoot, '--max-tasks', [string]$MaxTasks)
if ($TaskId) { $arguments += @('--task-id', $TaskId) }
if ($ValidateOnly) { $arguments += '--validate-only' }
if ($SmokeTest) { $arguments += '--smoke-test' }
Write-Host 'Guardian local multi-flow: local Ollama only; automatic merge/deployment disabled.'
& $python.Source @arguments
$exitCode = $LASTEXITCODE
if ($exitCode -ne 0) { throw "Guardian local multi-flow exited with code $exitCode." }
