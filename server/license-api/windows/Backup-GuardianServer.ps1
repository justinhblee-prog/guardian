$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Guardian-Paths.ps1')
$repoRoot=(Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$paths=Get-GuardianPaths -RepositoryRoot $repoRoot
if (-not (Test-Path -LiteralPath $paths.LicenseEnv -PathType Leaf)) {throw '외부 운영 .env가 없습니다.'}
New-Item -ItemType Directory -Force -Path $paths.Backups | Out-Null
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
$outFile=Join-Path $paths.Backups "guardian-license-$stamp.dump"
$serverDir=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$psi=[Diagnostics.ProcessStartInfo]::new()
$psi.FileName=(Get-Command docker).Source
foreach($arg in @('compose','--env-file',$paths.LicenseEnv,'exec','-T','db','pg_dump','-Fc','-U','guardian','guardian')) {[void]$psi.ArgumentList.Add($arg)}
$psi.WorkingDirectory=$serverDir
$psi.UseShellExecute=$false
$psi.RedirectStandardOutput=$true
$psi.RedirectStandardError=$true
$process=[Diagnostics.Process]::new()
$process.StartInfo=$psi
$fileStream=$null
try {
  $fileStream=[IO.File]::Open($outFile,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
  if(-not $process.Start()){throw 'Docker 백업 프로세스를 시작하지 못했습니다.'}
  $copyTask=$process.StandardOutput.BaseStream.CopyToAsync($fileStream)
  $stderrTask=$process.StandardError.ReadToEndAsync()
  $process.WaitForExit()
  $copyTask.GetAwaiter().GetResult()
  $stderr=$stderrTask.GetAwaiter().GetResult()
  $fileStream.Flush()
  if($process.ExitCode -ne 0 -or (Get-Item $outFile).Length -lt 128) {throw "DB 백업 실패: $stderr"}
} catch {
  Remove-Item $outFile -Force -ErrorAction SilentlyContinue
  throw
} finally {
  if($fileStream){$fileStream.Dispose()}
  $process.Dispose()
}
$identity=[Security.Principal.WindowsIdentity]::GetCurrent().Name
icacls.exe $outFile /inheritance:r /grant:r ($identity+':F') '*S-1-5-18:F' | Out-Null
if ($LASTEXITCODE -ne 0) {Remove-Item $outFile -Force;throw '백업 파일 ACL 설정 실패'}
Write-Host "외부 보호 경로에 PostgreSQL 백업 완료: $outFile"
Write-Host '복구 전 pg_restore --list로 파일을 확인하고 별도 DB에서 복구 리허설을 수행하세요.'
