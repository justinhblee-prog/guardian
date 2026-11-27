#Requires -Version 7.2
[CmdletBinding()]
param(
  [string]$Repository = 'justinhblee-prog/guardian',
  [string]$NeonProjectId = 'wispy-surf-33052072',
  [string]$NeonBranchId = 'br-shiny-mountain-bi4ia7g2',
  [string]$NeonInvocationUrl = 'https://br-shiny-mountain-bi4ia7g2-guardianapi.compute.c-5.ap-southeast-1.aws.neon.tech'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
. (Join-Path $PSScriptRoot 'Guardian-Paths.ps1')
$paths = Get-GuardianPaths -RepositoryRoot $repoRoot
$envPath = $paths.LicenseEnv
$publicPath = Join-Path $paths.Secrets 'guardian-license-public.x509.b64'

if (-not (Get-Command 'gh' -ErrorAction SilentlyContinue)) { throw 'gh 명령을 찾을 수 없습니다.' }
if (-not (Test-Path -LiteralPath $envPath -PathType Leaf)) {
  throw "기존 라이선스 운영 .env가 없습니다: $envPath. 새 키를 자동 생성하지 않습니다."
}
if (-not (Test-Path -LiteralPath $publicPath -PathType Leaf)) {
  throw "기존 라이선스 공개키 파일이 없습니다: $publicPath. 키를 새로 만들거나 교체하지 않습니다."
}
& gh auth status --hostname github.com *> $null
if ($LASTEXITCODE -ne 0) { throw 'GitHub CLI 인증이 필요합니다. gh auth login 후 다시 실행하세요.' }

# Parse only the three values required by the Neon runtime. Never print them.
$envValues = @{}
foreach ($line in Get-Content -LiteralPath $envPath) {
  if ($line -match '^\s*(GUARDIAN_ADMIN_TOKEN|GUARDIAN_LICENSE_LOOKUP_SECRET|GUARDIAN_LICENSE_PRIVATE_KEY)=(.*)$') {
    $envValues[$Matches[1]] = $Matches[2].Trim()
  }
}
foreach ($name in @('GUARDIAN_ADMIN_TOKEN','GUARDIAN_LICENSE_LOOKUP_SECRET','GUARDIAN_LICENSE_PRIVATE_KEY')) {
  if ([string]::IsNullOrWhiteSpace([string]$envValues[$name])) { throw "운영 .env에 $name 값이 없습니다. GitHub 설정은 변경하지 않았습니다." }
}
if ($envValues.GUARDIAN_ADMIN_TOKEN.Length -lt 32) { throw 'GUARDIAN_ADMIN_TOKEN 길이가 32자 미만입니다.' }
if ($envValues.GUARDIAN_LICENSE_LOOKUP_SECRET.Length -lt 32) { throw 'GUARDIAN_LICENSE_LOOKUP_SECRET 길이가 32자 미만입니다.' }

$privatePem = $envValues.GUARDIAN_LICENSE_PRIVATE_KEY -replace '\\n', [Environment]::NewLine
$publicB64 = (Get-Content -Raw -LiteralPath $publicPath).Trim()
$rsa = [Security.Cryptography.RSA]::Create()
try {
  $rsa.ImportFromPem($privatePem)
  if ($rsa.KeySize -lt 2048 -or $rsa.KeySize -gt 4096) { throw '라이선스 RSA 개인키 크기가 허용 범위(2048~4096)가 아닙니다.' }
  $derivedPublic = [Convert]::ToBase64String($rsa.ExportSubjectPublicKeyInfo())
  if ($derivedPublic -cne $publicB64) {
    throw '기존 라이선스 개인키와 공개키가 일치하지 않습니다. Android 앱 고정 공개키와 호환성 확인 전에는 등록하지 않습니다.'
  }
} finally { $rsa.Dispose() }

# Collect the Neon API key securely; it is never written to disk or echoed.
$secureNeonKey = Read-Host 'Neon API Key (입력값은 표시되지 않습니다)' -AsSecureString
$neonPtr = [IntPtr]::Zero
try {
  $neonPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureNeonKey)
  $neonApiKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($neonPtr)
} finally {
  if ($neonPtr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($neonPtr) }
}
if ([string]::IsNullOrWhiteSpace($neonApiKey)) { throw 'Neon API Key가 비어 있습니다. GitHub 설정은 변경하지 않았습니다.' }

function Set-GitHubSecret([string]$Name, [string]$Value) {
  $psi = [Diagnostics.ProcessStartInfo]::new()
  $psi.FileName = (Get-Command gh).Source
  foreach ($arg in @('secret','set',$Name,'--repo',$Repository)) { [void]$psi.ArgumentList.Add($arg) }
  $psi.UseShellExecute = $false
  $psi.RedirectStandardInput = $true
  $psi.RedirectStandardOutput = $true
  $psi.RedirectStandardError = $true
  $process = [Diagnostics.Process]::Start($psi)
  try {
    $process.StandardInput.Write($Value)
    $process.StandardInput.Close()
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw "GitHub Secret $Name 등록 실패: $stderr $stdout" }
  } finally { $process.Dispose() }
}
function Set-GitHubVariable([string]$Name, [string]$Value) {
  & gh variable set $Name --body $Value --repo $Repository
  if ($LASTEXITCODE -ne 0) { throw "GitHub Variable $Name 등록 실패" }
}

# All local preflight checks complete. This changes only the named GitHub
# Actions secrets/variables; it does not deploy or rotate any key.
Set-GitHubSecret 'NEON_API_KEY' $neonApiKey
Set-GitHubSecret 'GUARDIAN_ADMIN_TOKEN' $envValues.GUARDIAN_ADMIN_TOKEN
Set-GitHubSecret 'GUARDIAN_LICENSE_LOOKUP_SECRET' $envValues.GUARDIAN_LICENSE_LOOKUP_SECRET
Set-GitHubSecret 'GUARDIAN_LICENSE_PRIVATE_KEY' $privatePem

Set-GitHubVariable 'NEON_PROJECT_ID' $NeonProjectId
Set-GitHubVariable 'NEON_BRANCH_ID' $NeonBranchId
Set-GitHubVariable 'GUARDIAN_LICENSE_PUBLIC_KEY' $publicB64
Set-GitHubVariable 'GUARDIAN_LICENSE_API_BASE_URL' $NeonInvocationUrl.TrimEnd('/')

$neonApiKey = $null
$privatePem = $null
$envValues.Clear()
Write-Host 'Neon 배포용 GitHub Actions 설정 등록을 완료했습니다.'
Write-Host "대상 저장소: $Repository"
Write-Host "Neon 프로젝트/브랜치: $NeonProjectId / $NeonBranchId"
Write-Host "API 주소: $($NeonInvocationUrl.TrimEnd('/'))"
Write-Host '개인키·토큰 값은 출력하거나 저장하지 않았으며, Neon 배포는 실행하지 않았습니다.'
