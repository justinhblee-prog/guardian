$ErrorActionPreference = 'Stop'
$ServerDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'Guardian-Paths.ps1')
Initialize-GuardianPrivateDataDirectory
Set-Location $ServerDir
$backupDir = $script:GuardianBackupsDir
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outFile = Join-Path $backupDir "guardian-license-$stamp.dump"

# Use cmd.exe redirection so pg_dump's custom-format binary stream is not
# decoded/re-encoded by a PowerShell text pipeline.
$command = 'docker compose --env-file "' + $script:GuardianLicenseEnvPath + '" exec -T db pg_dump -Fc -U guardian guardian'
cmd.exe /d /s /c "$command > `"$outFile`""
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $outFile) -or (Get-Item $outFile).Length -lt 128) {
  Remove-Item $outFile -ErrorAction SilentlyContinue
  throw 'DB 백업 실패: Docker 상태와 PostgreSQL 컨테이너를 확인하세요.'
}
Write-Host "PostgreSQL custom-format 백업 완료: $outFile"
Write-Host '복구 전 pg_restore --list로 파일을 확인하고 별도 DB에서 복구 리허설을 수행하세요.'
