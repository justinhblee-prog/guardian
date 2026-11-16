$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$AndroidDir = Join-Path $RepoRoot 'android'
$SecretDir = Join-Path (Split-Path $PSScriptRoot -Parent) 'secrets'
$licensePublicPath = Join-Path $SecretDir 'guardian-license-public.x509.b64'
$filterPublicPath = Join-Path $SecretDir 'guardian-filter-public.x509.b64'

foreach ($required in @($licensePublicPath, $filterPublicPath)) {
  if (-not (Test-Path $required)) { throw "Android 빌드 공개키 파일이 없습니다: $required. Setup-GuardianServer.ps1을 먼저 실행하세요." }
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Java 17 이상이 필요합니다. Android Studio 또는 JDK 17을 설치하세요.' }
if (-not ($env:ANDROID_HOME -or $env:ANDROID_SDK_ROOT)) { throw 'ANDROID_HOME 또는 ANDROID_SDK_ROOT를 Android SDK 경로로 설정하세요.' }
if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) { throw 'Gradle 8.9를 설치하고 PATH에 등록하세요. 이 저장소에는 Gradle Wrapper가 포함되어 있지 않습니다.' }
$gradleVersion = (& gradle --version 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or $gradleVersion -notmatch 'Gradle 8\.9') { throw '이 빌드는 Gradle 8.9가 필요합니다.' }
$env:GUARDIAN_LICENSE_PUBLIC_KEY = (Get-Content -Raw $licensePublicPath).Trim()
$env:GUARDIAN_MANIFEST_PUBLIC_KEY = (Get-Content -Raw $filterPublicPath).Trim()
# Server addresses remain editable in the Android app after installation.
$env:GUARDIAN_LICENSE_API_BASE_URL = ''
$env:GUARDIAN_MANIFEST_BASE_URL = ''

Push-Location $AndroidDir
try {
  & gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
  if ($LASTEXITCODE -ne 0) { throw "Android 검증/빌드 실패 (exit $LASTEXITCODE)" }
} finally { Pop-Location }

$outputDir = Join-Path (Split-Path $PSScriptRoot -Parent) 'outputs'
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
$apkSource = Join-Path $AndroidDir 'app\build\outputs\apk\debug\app-debug.apk'
$apkTarget = Join-Path $outputDir 'Guardian-AdBlock-Debug.apk'
if (-not (Test-Path $apkSource)) { throw 'Gradle 빌드가 끝났지만 APK 파일을 찾을 수 없습니다.' }
Copy-Item $apkSource $apkTarget -Force
$hash = (Get-FileHash -Algorithm SHA256 $apkTarget).Hash.ToLowerInvariant()
$checksum = $hash + '  Guardian-AdBlock-Debug.apk' + [Environment]::NewLine
[System.IO.File]::WriteAllText((Join-Path $outputDir 'SHA256SUMS.txt'), $checksum, [System.Text.UTF8Encoding]::new($false))
Write-Host "Android debug APK: $apkTarget"
Write-Host "SHA-256: $hash"
Write-Host '이 APK는 노트북에서 생성한 서명 공개키를 포함합니다. 앱 설정에서 공개 HTTPS 서버 주소를 입력한 뒤 서명 필터/라이선스 연동을 사용할 수 있습니다.'
