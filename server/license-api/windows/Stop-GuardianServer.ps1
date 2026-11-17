$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Guardian-Paths.ps1')
$repoRoot=(Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$paths=Get-GuardianPaths -RepositoryRoot $repoRoot
$serverDir=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $serverDir
try { docker compose --env-file $paths.LicenseEnv stop; if ($LASTEXITCODE -ne 0) {throw 'Guardian 서버 중지 실패'} }
finally {Pop-Location}
Write-Host 'Guardian 서버가 중지되었습니다. 데이터 볼륨은 유지됩니다.'
