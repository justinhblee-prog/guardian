$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Guardian-Paths.ps1')
$repoRoot=(Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$paths=Get-GuardianPaths -RepositoryRoot $repoRoot
$filterDir=(Resolve-Path (Join-Path $PSScriptRoot '..\..\filter-distribution')).Path
$protectedPath=Join-Path $paths.Secrets 'guardian-filter-private.dpapi'
if (-not(Test-Path $protectedPath)) {throw '외부 보호 필터 서명키가 없습니다. 운영 서버 최초 설치를 먼저 실행하세요.'}
if (-not(Test-Path $paths.FilterSources)) {throw '외부 sources.json이 없습니다. Setup-GuardianServer.ps1을 먼저 실행하세요.'}
$protectedText=Get-Content -Raw $protectedPath
$secureKey=ConvertTo-SecureString -String $protectedText
$pointer=[IntPtr]::Zero
try {
  $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
  $plainKey=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
  $env:GUARDIAN_FILTER_SIGNING_PRIVATE_KEY=$plainKey
  New-Item -ItemType Directory -Force -Path $paths.FilterLogs | Out-Null
  $logPath=Join-Path $paths.FilterLogs ((Get-Date -Format 'yyyyMMdd')+'.log')
  Push-Location $filterDir
  try {
    npm run update -- $paths.FilterSources *>&1 | Tee-Object -FilePath $logPath -Append
    if ($LASTEXITCODE -ne 0) {throw "필터 다운로드/서명 발행 실패 (exit $LASTEXITCODE). 로그: $logPath"}
  } finally {Pop-Location}
} finally {
  Remove-Item Env:\GUARDIAN_FILTER_SIGNING_PRIVATE_KEY -ErrorAction SilentlyContinue
  $plainKey=$null
  if ($pointer -ne [IntPtr]::Zero) {[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)}
}
