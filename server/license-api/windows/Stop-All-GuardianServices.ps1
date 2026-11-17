$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Guardian-Paths.ps1')
$repoRoot=(Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$paths=Get-GuardianPaths -RepositoryRoot $repoRoot
Set-GuardianComposeEnvironment -Paths $paths
$licenseDir=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$filterDir=(Resolve-Path (Join-Path $licenseDir '..\filter-distribution')).Path
foreach ($entry in @(@{Dir=$filterDir;Env=$paths.FilterEnv},@{Dir=$licenseDir;Env=$paths.LicenseEnv})) {
  Push-Location $entry.Dir
  try {docker compose --env-file $entry.Env stop;if($LASTEXITCODE -ne 0){throw "서비스 중지 실패: $($entry.Dir)"}}
  finally {Pop-Location}
}
Write-Host 'Guardian API와 필터 배포 서버가 중지되었습니다. DB 볼륨과 외부 배포 파일은 유지됩니다.'
