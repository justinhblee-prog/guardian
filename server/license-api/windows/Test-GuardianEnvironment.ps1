[CmdletBinding()]
param(
  [switch]$ForAndroidBuild,
  [switch]$ForFilterUpdate
)

$ErrorActionPreference = 'Stop'
$ServerDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'Guardian-Paths.ps1')
$FilterDir = (Resolve-Path (Join-Path $ServerDir '..\filter-distribution')).Path
$checks = [System.Collections.Generic.List[object]]::new()

function Add-Check([string]$Name, [bool]$Passed, [string]$Detail, [bool]$Required = $true) {
  $checks.Add([pscustomobject]@{
    Name = $Name
    Passed = $Passed
    Required = $Required
    Detail = $Detail
  })
}

function Test-Command([string]$Name) {
  return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

# The laptop server itself requires Docker Desktop and OpenSSL.
if ($ForAndroidBuild -or $ForFilterUpdate) {
  $powerShellReady = $PSVersionTable.PSVersion -ge [Version]'7.2'
  Add-Check 'PowerShell 7.2+' $powerShellReady '공개키 일치 검증 및 RSA 서명 게시에는 pwsh.exe 7.2 이상이 필요합니다.'
}
$dockerAvailable = Test-Command 'docker'
if (-not $dockerAvailable) {
  Add-Check 'Docker CLI' $false 'Docker Desktop을 설치하고 PATH에서 docker 명령을 사용할 수 있게 하세요.'
} else {
  docker info *> $null
  Add-Check 'Docker Engine' ($LASTEXITCODE -eq 0) 'Docker Desktop 실행 상태'
}
$opensslAvailable = Test-Command 'openssl'
Add-Check 'OpenSSL' $opensslAvailable 'RSA 3072-bit 운영 키 생성에 필요'

if ($ForAndroidBuild) {
  $javaAvailable = Test-Command 'java'
  $javacAvailable = Test-Command 'javac'
  $javaVersion = ''
  if ($javaAvailable) { $javaVersion = (& java -version 2>&1 | Out-String) }
  $versionMatch = [regex]::Match($javaVersion, 'version\s+"(\d+)')
  $java17 = $versionMatch.Success -and [int]$versionMatch.Groups[1].Value -ge 17
  Add-Check 'JDK 17+' ($javaAvailable -and $javacAvailable -and $java17) 'java와 javac 모두 필요하며 Java 주 버전은 17 이상이어야 합니다.'

  $sdkPath = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
  $sdkReady = -not [string]::IsNullOrWhiteSpace($sdkPath) -and
    (Test-Path (Join-Path $sdkPath 'platforms\android-35')) -and
    (Test-Path (Join-Path $sdkPath 'build-tools\35.0.0'))
  Add-Check 'Android SDK 35' $sdkReady 'ANDROID_HOME 또는 ANDROID_SDK_ROOT 아래에 platforms\android-35와 build-tools\35.0.0이 필요합니다.'

  $gradleAvailable = Test-Command 'gradle'
  $gradleReady = $false
  if ($gradleAvailable) {
    $gradleVersion = (& gradle --version 2>&1 | Out-String)
    $gradleReady = $LASTEXITCODE -eq 0 -and $gradleVersion -match 'Gradle 8\.9'
  }
  Add-Check 'Gradle 8.9' $gradleReady '현재 저장소에는 Gradle Wrapper가 없으므로 Gradle 8.9가 PATH에 필요합니다.'
}

if ($ForFilterUpdate) {
  $nodeAvailable = Test-Command 'node'
  $npmAvailable = Test-Command 'npm'
  $nodeMajor = 0
  if ($nodeAvailable) {
    $nodeMajorText = & node -p "process.versions.node.split('.')[0]"
    if ($LASTEXITCODE -eq 0) { [void][int]::TryParse("$nodeMajorText", [ref]$nodeMajor) }
  }
  Add-Check 'Node.js 22+ / npm' ($nodeAvailable -and $npmAvailable -and $nodeMajor -ge 22) '서명 필터 공급원 다운로드 및 manifest 발행에 필요.'

  $protectedKey = Join-Path $GuardianSecretsDir 'guardian-filter-private.dpapi'
  Add-Check '외부 보안 데이터 경로' ($GuardianDataDir -notlike ($GuardianRepoRoot + '*')) "Guardian 데이터: $GuardianDataDir"
  Add-Check 'DPAPI 필터 서명키' (Test-Path $protectedKey) '현재 Windows 사용자 계정으로 생성된 보호 키가 필요합니다.'
  $sourcesPath = Join-Path $FilterDir 'sources.json'
  $sourcesReady = $false
  if (Test-Path $sourcesPath) {
    try {
      $sources = Get-Content -Raw $sourcesPath | ConvertFrom-Json
      $base = [Uri]$sources.baseUrl
      $sourcesReady = $base.Scheme -eq 'https' -and
        -not [string]::IsNullOrWhiteSpace($base.Host) -and
        $base.Host -ne 'example.com' -and
        -not $base.Host.EndsWith('.example.com', [StringComparison]::OrdinalIgnoreCase) -and
        $sources.sources.Count -gt 0
    } catch { $sourcesReady = $false }
  }
  Add-Check '실제 HTTPS 필터 배포 주소' $sourcesReady 'sources.json의 baseUrl을 실제 HTTPS 도메인으로 설정하고 공급원을 1개 이상 지정하세요.'
}

$checks | ForEach-Object {
  $state = if ($_.Passed) { 'PASS' } elseif ($_.Required) { 'BLOCK' } else { 'WARN' }
  $color = if ($_.Passed) { 'Green' } elseif ($_.Required) { 'Red' } else { 'Yellow' }
  Write-Host ('[{0}] {1}: {2}' -f $state, $_.Name, $_.Detail) -ForegroundColor $color
}
$failed = @($checks | Where-Object { $_.Required -and -not $_.Passed })
Write-Host ''
if ($failed.Count -gt 0) {
  Write-Host ("필수 준비 항목 {0}개가 충족되지 않았습니다." -f $failed.Count) -ForegroundColor Red
  exit 1
}
Write-Host '선택한 작업의 필수 준비 항목을 통과했습니다.' -ForegroundColor Green
