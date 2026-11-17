$ErrorActionPreference = 'Stop'
$FilterDir = (Resolve-Path (Join-Path $PSScriptRoot '..\..\filter-distribution')).Path
. (Join-Path $PSScriptRoot 'Guardian-Paths.ps1')
$SecretDir = $script:GuardianSecretsDir
$protectedPath = Join-Path $SecretDir 'guardian-filter-private.dpapi'
if (-not (Test-Path $protectedPath)) { throw '보호된 필터 서명키가 없습니다. 운영 서버 최초 설치를 먼저 실행하세요.' }
if (-not (Test-Path (Join-Path $FilterDir 'sources.json'))) { throw 'sources.json이 없습니다. sources.example.json을 복사하고 HTTPS 주소를 설정하세요.' }

$protectedText = Get-Content -Raw $protectedPath
$secureKey = ConvertTo-SecureString -String $protectedText
$pointer = [IntPtr]::Zero
try {
  $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
  $plainKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
  $env:GUARDIAN_FILTER_SIGNING_PRIVATE_KEY = $plainKey
  $logDir = Join-Path $FilterDir 'logs'
  New-Item -ItemType Directory -Force -Path $logDir | Out-Null
  $logPath = Join-Path $logDir ((Get-Date -Format 'yyyyMMdd') + '.log')
  Push-Location $FilterDir
  try {
    npm run update -- .\sources.json *>&1 | Tee-Object -FilePath $logPath -Append
    if ($LASTEXITCODE -ne 0) { throw "필터 다운로드/서명 발행 실패 (exit $LASTEXITCODE). 로그: $logPath" }
  } finally { Pop-Location }
} finally {
  Remove-Item Env:\GUARDIAN_FILTER_SIGNING_PRIVATE_KEY -ErrorAction SilentlyContinue
  $plainKey = $null
  if ($pointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}
