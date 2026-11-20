#Requires -Version 7.2
[CmdletBinding()]
param([switch]$InstallToConnectedDevice)
$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$AndroidDir = Join-Path $RepoRoot 'android'
. (Join-Path $PSScriptRoot 'Guardian-Paths.ps1')
$paths=Get-GuardianPaths -RepositoryRoot $repoRoot
$SecretDir = $paths.Secrets
$licensePublicPath = Join-Path $SecretDir 'guardian-license-public.x509.b64'
$filterPublicPath = Join-Path $SecretDir 'guardian-filter-public.x509.b64'

foreach ($required in @($licensePublicPath, $filterPublicPath)) {
  if (-not (Test-Path $required)) { throw "Android 빌드 공개키 파일이 없습니다: $required. Setup-GuardianServer.ps1을 먼저 실행하세요." }
}

# Ensure the filter public key embedded in this APK belongs to the DPAPI-protected
# private key that signs the cloud manifests. A stale public key would make every
# otherwise valid cloud update fail verification on the device.
$protectedFilterKeyPath = Join-Path $SecretDir 'guardian-filter-private.dpapi'
if (-not (Test-Path -LiteralPath $protectedFilterKeyPath -PathType Leaf)) {
  throw 'DPAPI 보호 필터 서명키가 없습니다. Setup-GuardianServer.ps1을 먼저 실행하세요.'
}
$protectedFilterKey = ConvertTo-SecureString -String (Get-Content -Raw -LiteralPath $protectedFilterKeyPath)
$keyPointer = [IntPtr]::Zero
$filterRsa = [System.Security.Cryptography.RSA]::Create()
try {
  $keyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($protectedFilterKey)
  $filterPrivatePem = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPointer)
  $filterRsa.ImportFromPem($filterPrivatePem)
  if ($filterRsa.KeySize -lt 2048) { throw '필터 서명 RSA 키가 2048비트보다 짧습니다.' }
  $derivedFilterPublicKey = [Convert]::ToBase64String($filterRsa.ExportSubjectPublicKeyInfo())
  $configuredFilterPublicKey = (Get-Content -Raw -LiteralPath $filterPublicPath).Trim()
  if ($derivedFilterPublicKey -cne $configuredFilterPublicKey) {
    throw 'Android 빌드 공개키가 DPAPI 필터 서명 개인키와 일치하지 않습니다. Setup-GuardianServer.ps1로 키 구성을 복구하세요.'
  }
} finally {
  $filterRsa.Dispose()
  $filterPrivatePem = $null
  if ($keyPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPointer) }
}
$javaCommand = Get-Command java -ErrorAction SilentlyContinue
$javacCommand = Get-Command javac -ErrorAction SilentlyContinue
if (-not $javaCommand -or -not $javacCommand) { throw 'JDK 17 이상이 필요합니다. java와 javac가 모두 PATH에 있어야 합니다.' }
$javaVersionText = (& java -version 2>&1 | Out-String)
$javaVersionMatch = [regex]::Match($javaVersionText, 'version\s+"(\d+)')
if ($LASTEXITCODE -ne 0 -or -not $javaVersionMatch.Success -or @('17', '21') -notcontains $javaVersionMatch.Groups[1].Value) {
  throw 'Guardian Android 빌드에는 JDK 17 또는 21이 필요합니다. 현재 java 버전을 확인하세요.'
}
$sdkPath = if ($env:ANDROID_SDK_ROOT) {
  $env:ANDROID_SDK_ROOT
} elseif ($env:ANDROID_HOME) {
  $env:ANDROID_HOME
} elseif ($env:LOCALAPPDATA -and (Test-Path (Join-Path $env:LOCALAPPDATA 'Android\Sdk'))) {
  Join-Path $env:LOCALAPPDATA 'Android\Sdk'
} else {
  $null
}
if ([string]::IsNullOrWhiteSpace($sdkPath)) {
  throw 'Android SDK 경로를 찾을 수 없습니다. ANDROID_SDK_ROOT 또는 ANDROID_HOME을 설정하거나 기본 Android SDK 경로에 설치하세요.'
}
if (-not (Test-Path (Join-Path $sdkPath 'platforms\android-35')) -or
    -not (Test-Path (Join-Path $sdkPath 'build-tools\35.0.0'))) {
  throw 'Android SDK Platform 35와 Build Tools 35.0.0이 필요합니다. Android SDK Manager에서 설치하세요.'
}
$aaptPath = Join-Path $sdkPath 'build-tools\35.0.0\aapt.exe'
if (-not (Test-Path -LiteralPath $aaptPath -PathType Leaf)) {
  throw "Android Build Tools aapt.exe를 찾을 수 없습니다: $aaptPath"
}
if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) { throw 'Gradle 8.9를 설치하고 PATH에 등록하세요. 이 저장소에는 Gradle Wrapper가 포함되어 있지 않습니다.' }
if ($InstallToConnectedDevice -and -not (Get-Command adb -ErrorAction SilentlyContinue)) { throw '실기기 설치/화면 캡처에는 Android SDK Platform Tools(adb)가 필요합니다.' }
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

$outputDir = $paths.Outputs
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
$apkSource = Join-Path $AndroidDir 'app\build\outputs\apk\debug\app-debug.apk'
$apkTarget = Join-Path $outputDir 'Guardian-AdBlock-Debug.apk'
if (-not (Test-Path $apkSource)) { throw 'Gradle 빌드가 끝났지만 APK 파일을 찾을 수 없습니다.' }
if ((Get-Item -LiteralPath $apkSource).Length -le 0) { throw 'Gradle이 만든 APK 파일이 비어 있습니다.' }
$apkEntries = (& $aaptPath list $apkSource 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) { throw 'aapt가 APK 내부 파일 목록을 읽지 못했습니다.' }
if ($apkEntries -notmatch '(?m)^assets/filter_rules\.txt\s*
$hash = (Get-FileHash -Algorithm SHA256 $apkTarget).Hash.ToLowerInvariant()
$checksum = $hash + '  Guardian-AdBlock-Debug.apk' + [Environment]::NewLine
[System.IO.File]::WriteAllText((Join-Path $outputDir 'SHA256SUMS.txt'), $checksum, [System.Text.UTF8Encoding]::new($false))
Write-Host "Android debug APK: $apkTarget"
Write-Host "SHA-256: $hash"
Write-Host '이 APK는 노트북에서 생성한 서명 공개키를 포함합니다. 앱 설정에서 공개 HTTPS 서버 주소를 입력한 뒤 서명 필터/라이선스 연동을 사용할 수 있습니다.'

if ($InstallToConnectedDevice) {
  $adb = (Get-Command adb -ErrorAction Stop).Source
  $deviceLines = @(& $adb devices)
  if ($LASTEXITCODE -ne 0) { throw 'adb devices 실행에 실패했습니다.' }
  $connected = @($deviceLines | Select-Object -Skip 1 | Where-Object { $_ -match '^\S+\s+device(?:\s|$)' })
  if ($connected.Count -ne 1) {
    throw "실기기 자동 검증은 adb 연결 기기가 정확히 1대일 때만 진행합니다. 현재 연결된 기기 수: $($connected.Count)"
  }
  $deviceId = ($connected[0] -split '\s+')[0]
  $manufacturer = (& $adb -s $deviceId shell getprop ro.product.manufacturer).Trim()
  if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($manufacturer)) { throw '연결 기기의 제조사 확인에 실패했습니다.' }
  $model = (& $adb -s $deviceId shell getprop ro.product.model).Trim()
  if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($model)) { throw '연결 기기의 모델 확인에 실패했습니다.' }
  $androidRelease = (& $adb -s $deviceId shell getprop ro.build.version.release).Trim()
  if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($androidRelease)) { throw '연결 기기의 Android 버전 확인에 실패했습니다.' }
  if ($manufacturer -notmatch 'Samsung') {
    Write-Warning "연결 기기는 Samsung이 아닙니다: $manufacturer $model. 일반 Android 설치 확인만 수행합니다."
  }
  & $adb -s $deviceId install -r $apkTarget
  if ($LASTEXITCODE -ne 0) { throw '연결 기기에 Guardian debug APK 설치 실패' }
  & $adb -s $deviceId shell am force-stop com.guardian.adblock
  if ($LASTEXITCODE -ne 0) { throw '기존 Guardian 프로세스 종료 실패' }
  $launchOutput = @(& $adb -s $deviceId shell am start -W -n com.guardian.adblock/.MainActivity)
  if ($LASTEXITCODE -ne 0 -or ($launchOutput -join "`n") -notmatch 'Status:\s*ok') {
    throw "Guardian MainActivity 실행 실패: $($launchOutput -join ' ')"
  }
  Start-Sleep -Seconds 5
  $processId = (& $adb -s $deviceId shell pidof com.guardian.adblock).Trim()
  if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($processId)) {
    $processList = @(& $adb -s $deviceId shell ps -A)
    if ($LASTEXITCODE -ne 0 -or -not ($processList | Where-Object { $_ -match '\bcom\.guardian\.adblock\b' })) {
      throw 'Guardian 화면 실행 후 앱 프로세스가 유지되지 않습니다. 앱 시작 직후 종료되었을 수 있습니다.'
    }
  }
  $captureDir = Join-Path $outputDir ('DeviceCapture-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
  New-Item -ItemType Directory -Force -Path $captureDir | Out-Null
  $remoteShot = '/sdcard/guardian-debug-screen.png'
  $localShot = Join-Path $captureDir 'main-screen.png'
  try {
    & $adb -s $deviceId shell screencap -p $remoteShot
    if ($LASTEXITCODE -ne 0) { throw 'Android 화면 캡처 실패' }
    & $adb -s $deviceId pull $remoteShot $localShot
    if ($LASTEXITCODE -ne 0) { throw '화면 캡처 파일을 노트북으로 가져오지 못했습니다.' }
    $shot = [IO.File]::ReadAllBytes($localShot)
    if ($shot.Length -lt 8 -or [Convert]::ToHexString($shot[0..7]) -ne '89504E470D0A1A0A') {
      throw '가져온 화면 캡처가 유효한 PNG 파일이 아닙니다.'
    }
  } finally {
    & $adb -s $deviceId shell rm -f $remoteShot | Out-Null
  }
  $deviceReport = @(
    "Manufacturer: $manufacturer"
    "Model: $model"
    "Android: $androidRelease"
    "APK SHA-256: $hash"
    "Installed, launched and process alive: PASS"
    "Main screen capture: main-screen.png"
    "Note: screenshot capture confirms launch/render availability only; VPN, DNS blocking, license activation and billing still require separate functional checks."
  ) -join [Environment]::NewLine
  [IO.File]::WriteAllText((Join-Path $captureDir 'device-verification.txt'), $deviceReport + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
  Write-Host "Device verification report: $(Join-Path $captureDir 'device-verification.txt')"
  Write-Host "Main screen screenshot: $(Join-Path $captureDir 'main-screen.png')"
}
) {
  throw 'APK에 assets/filter_rules.txt가 없습니다. 필수 기본 DNS 규칙을 앱에서 읽을 수 없습니다.'
}
$apkBadging = (& $aaptPath dump badging $apkSource 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) { throw 'aapt가 APK 패키지 메타데이터를 읽지 못했습니다.' }
$packageLine = ($apkBadging -split '\r?\n' | Where-Object { $_ -match '^package:' } | Select-Object -First 1)
if ($packageLine -notmatch "^package:\s+name='com\.guardian\.adblock'") {
  throw "APK 패키지 ID가 Guardian과 일치하지 않습니다: $packageLine"
}
Copy-Item $apkSource $apkTarget -Force
$hash = (Get-FileHash -Algorithm SHA256 $apkTarget).Hash.ToLowerInvariant()
$checksum = $hash + '  Guardian-AdBlock-Debug.apk' + [Environment]::NewLine
[System.IO.File]::WriteAllText((Join-Path $outputDir 'SHA256SUMS.txt'), $checksum, [System.Text.UTF8Encoding]::new($false))
Write-Host "Android debug APK: $apkTarget"
Write-Host "SHA-256: $hash"
Write-Host '이 APK는 노트북에서 생성한 서명 공개키를 포함합니다. 앱 설정에서 공개 HTTPS 서버 주소를 입력한 뒤 서명 필터/라이선스 연동을 사용할 수 있습니다.'

if ($InstallToConnectedDevice) {
  $adb = (Get-Command adb -ErrorAction Stop).Source
  $deviceLines = @(& $adb devices)
  if ($LASTEXITCODE -ne 0) { throw 'adb devices 실행에 실패했습니다.' }
  $connected = @($deviceLines | Select-Object -Skip 1 | Where-Object { $_ -match '^\S+\s+device(?:\s|$)' })
  if ($connected.Count -ne 1) {
    throw "실기기 자동 검증은 adb 연결 기기가 정확히 1대일 때만 진행합니다. 현재 연결된 기기 수: $($connected.Count)"
  }
  $deviceId = ($connected[0] -split '\s+')[0]
  $manufacturer = (& $adb -s $deviceId shell getprop ro.product.manufacturer).Trim()
  if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($manufacturer)) { throw '연결 기기의 제조사 확인에 실패했습니다.' }
  $model = (& $adb -s $deviceId shell getprop ro.product.model).Trim()
  if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($model)) { throw '연결 기기의 모델 확인에 실패했습니다.' }
  $androidRelease = (& $adb -s $deviceId shell getprop ro.build.version.release).Trim()
  if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($androidRelease)) { throw '연결 기기의 Android 버전 확인에 실패했습니다.' }
  if ($manufacturer -notmatch 'Samsung') {
    Write-Warning "연결 기기는 Samsung이 아닙니다: $manufacturer $model. 일반 Android 설치 확인만 수행합니다."
  }
  & $adb -s $deviceId install -r $apkTarget
  if ($LASTEXITCODE -ne 0) { throw '연결 기기에 Guardian debug APK 설치 실패' }
  & $adb -s $deviceId shell am force-stop com.guardian.adblock
  if ($LASTEXITCODE -ne 0) { throw '기존 Guardian 프로세스 종료 실패' }
  $launchOutput = @(& $adb -s $deviceId shell am start -W -n com.guardian.adblock/.MainActivity)
  if ($LASTEXITCODE -ne 0 -or ($launchOutput -join "`n") -notmatch 'Status:\s*ok') {
    throw "Guardian MainActivity 실행 실패: $($launchOutput -join ' ')"
  }
  Start-Sleep -Seconds 5
  $processId = (& $adb -s $deviceId shell pidof com.guardian.adblock).Trim()
  if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($processId)) {
    $processList = @(& $adb -s $deviceId shell ps -A)
    if ($LASTEXITCODE -ne 0 -or -not ($processList | Where-Object { $_ -match '\bcom\.guardian\.adblock\b' })) {
      throw 'Guardian 화면 실행 후 앱 프로세스가 유지되지 않습니다. 앱 시작 직후 종료되었을 수 있습니다.'
    }
  }
  $captureDir = Join-Path $outputDir ('DeviceCapture-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
  New-Item -ItemType Directory -Force -Path $captureDir | Out-Null
  $remoteShot = '/sdcard/guardian-debug-screen.png'
  $localShot = Join-Path $captureDir 'main-screen.png'
  try {
    & $adb -s $deviceId shell screencap -p $remoteShot
    if ($LASTEXITCODE -ne 0) { throw 'Android 화면 캡처 실패' }
    & $adb -s $deviceId pull $remoteShot $localShot
    if ($LASTEXITCODE -ne 0) { throw '화면 캡처 파일을 노트북으로 가져오지 못했습니다.' }
    $shot = [IO.File]::ReadAllBytes($localShot)
    if ($shot.Length -lt 8 -or [Convert]::ToHexString($shot[0..7]) -ne '89504E470D0A1A0A') {
      throw '가져온 화면 캡처가 유효한 PNG 파일이 아닙니다.'
    }
  } finally {
    & $adb -s $deviceId shell rm -f $remoteShot | Out-Null
  }
  $deviceReport = @(
    "Manufacturer: $manufacturer"
    "Model: $model"
    "Android: $androidRelease"
    "APK SHA-256: $hash"
    "Installed, launched and process alive: PASS"
    "Main screen capture: main-screen.png"
    "Note: screenshot capture confirms launch/render availability only; VPN, DNS blocking, license activation and billing still require separate functional checks."
  ) -join [Environment]::NewLine
  [IO.File]::WriteAllText((Join-Path $captureDir 'device-verification.txt'), $deviceReport + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
  Write-Host "Device verification report: $(Join-Path $captureDir 'device-verification.txt')"
  Write-Host "Main screen screenshot: $(Join-Path $captureDir 'main-screen.png')"
}
