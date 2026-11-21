[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$RepoRoot,
    [Parameter(Mandatory = $true)][string]$LogRoot
)
$ErrorActionPreference = 'Stop'
# Compatibility entry point only. Guardian no longer starts Copilot CLI or
# falls back to a paid/remote AI provider from its scheduled local worker.
$worker = Join-Path $PSScriptRoot 'guardian-multiflow.ps1'
if (-not (Test-Path -LiteralPath $worker -PathType Leaf)) {
    throw "Guardian local Ollama multi-flow worker is missing: $worker"
}
Write-Host 'Legacy worker entry point redirected to local Ollama multi-flow; no Copilot fallback.'
& $worker -MaxTasks 3
if ($LASTEXITCODE -ne 0) {
    throw "Guardian local Ollama multi-flow failed with exit code $LASTEXITCODE."
}
