$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Android = Join-Path $Root 'android'
$ExpectedVersionName = if ($env:GUARDIAN_VERSION_NAME) { $env:GUARDIAN_VERSION_NAME } else { '0.7.30-dev' }
$ExpectedVersionCode = if ($env:GUARDIAN_VERSION_CODE) { $env:GUARDIAN_VERSION_CODE } else { '43' }

if (!(Test-Path (Join-Path $Android 'settings.gradle.kts'))) {
    throw "Android project settings were not found: $Android"
}

$JavaVersionOutput = (& java -version 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) { throw 'Java could not be executed. Install JDK 17 or 21 and configure PATH/JAVA_HOME.' }
$JavaMatch = [regex]::Match($JavaVersionOutput, 'version\s+"(?<major>\d+)')
if (!$JavaMatch.Success -or @('17', '21') -notcontains $JavaMatch.Groups['major'].Value) {
    throw "Guardian Android requires JDK 17 or 21. Detected: $JavaVersionOutput"
}

$Sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:LOCALAPPDATA -and (Test-Path (Join-Path $env:LOCALAPPDATA 'Android\Sdk'))) { Join-Path $env:LOCALAPPDATA 'Android\Sdk' } else { $null }
if ([string]::IsNullOrWhiteSpace($Sdk) -or !(Test-Path (Join-Path $Sdk 'platforms\android-35\android.jar'))) {
    throw 'Android SDK Platform 35 was not found. Set ANDROID_SDK_ROOT (or ANDROID_HOME) and install Android SDK Platform 35.'
}
$Aapt = Join-Path $Sdk 'build-tools\35.0.0\aapt.exe'
if (!(Test-Path $Aapt)) {
    throw "Android Build Tools 35.0.0 (aapt.exe) were not found: $Aapt"
}

Set-Location $Android
$Wrapper = Join-Path $Android 'gradlew.bat'
if (Test-Path $Wrapper) {
    if (!(Test-Path (Join-Path $Android 'gradle\wrapper\gradle-wrapper.jar'))) {
        throw 'gradlew.bat exists but gradle-wrapper.jar is missing.'
    }
    $GradleCommand = $Wrapper
} elseif (Get-Command gradle -ErrorAction SilentlyContinue) {
    $GradleCommand = (Get-Command gradle).Source
} else {
    throw 'Gradle 8.9 was not found. Install Gradle 8.9 or provide a complete Gradle Wrapper.'
}

$Stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$OutputRoot = Join-Path $env:LOCALAPPDATA "Guardian\outputs\Android-Debug-$Stamp"
New-Item -ItemType Directory -Path $OutputRoot -Force | Out-Null
$VersionLog = Join-Path $OutputRoot 'gradle-version.txt'
$BuildLog = Join-Path $OutputRoot 'build.log'

& $GradleCommand --version 2>&1 | Tee-Object -FilePath $VersionLog
if ($LASTEXITCODE -ne 0) { throw "Gradle version check failed. See $VersionLog" }
$GradleVersionText = Get-Content -Raw $VersionLog
if ($GradleVersionText -notmatch '(?m)^Gradle 8\.9\s*$') {
    throw "Guardian Android requires Gradle 8.9. Detected version output is saved at $VersionLog"
}

$Tasks = @(':app:clean', ':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug', '--no-daemon')
& $GradleCommand @Tasks 2>&1 | Tee-Object -FilePath $BuildLog
$BuildExitCode = $LASTEXITCODE
if ($BuildExitCode -ne 0) {
    throw "Android tests/lint/build failed with exit code $BuildExitCode. See $BuildLog"
}

$Apk = Join-Path $Android 'app\build\outputs\apk\debug\app-debug.apk'
if (!(Test-Path $Apk)) { throw "Build returned success but APK was not found: $Apk" }
$ApkItem = Get-Item $Apk
if ($ApkItem.Length -le 0) { throw "APK exists but is empty: $Apk" }

$Badging = (& $Aapt dump badging $Apk 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) { throw "aapt could not inspect the APK. See $BuildLog" }
$PackageLine = ($Badging -split '\r?\n' | Where-Object { $_ -match "^package:" } | Select-Object -First 1)
if ([string]::IsNullOrWhiteSpace($PackageLine)) { throw 'APK package metadata is missing from aapt output.' }
$PackageMatch = [regex]::Match($PackageLine, "^package:\s+name='(?<name>[^']+)'\s+versionCode='(?<code>[^']+)'\s+versionName='(?<version>[^']+)'")
if (!$PackageMatch.Success) { throw "APK package metadata could not be parsed: $PackageLine" }
if ($PackageMatch.Groups['name'].Value -ne 'com.guardian.adblock' -or
    $PackageMatch.Groups['code'].Value -ne $ExpectedVersionCode -or
    $PackageMatch.Groups['version'].Value -ne $ExpectedVersionName) {
    throw "APK metadata mismatch. Expected com.guardian.adblock $ExpectedVersionName ($ExpectedVersionCode); got $PackageLine"
}

$ArtifactApk = Join-Path $OutputRoot 'Guardian-debug.apk'
Copy-Item -LiteralPath $Apk -Destination $ArtifactApk
$Hash = (Get-FileHash -LiteralPath $ArtifactApk -Algorithm SHA256).Hash.ToLowerInvariant()
$GitCommit = 'unavailable'
if (Get-Command git -ErrorAction SilentlyContinue) {
    $GitCommitCandidate = (& git -C $Root rev-parse HEAD 2>$null | Out-String).Trim()
    if ($LASTEXITCODE -eq 0 -and $GitCommitCandidate) { $GitCommit = $GitCommitCandidate }
}
$Report = @(
    'Guardian Android Debug Build Verification'
    "Timestamp: $(Get-Date -Format o)"
    "Git commit: $GitCommit"
    "Java: $($JavaMatch.Groups['major'].Value)"
    'Gradle: 8.9'
    'Android SDK platform: 35'
    "Package: $($PackageMatch.Groups['name'].Value)"
    "Version name: $($PackageMatch.Groups['version'].Value)"
    "Version code: $($PackageMatch.Groups['code'].Value)"
    "APK size bytes: $((Get-Item $ArtifactApk).Length)"
    "APK SHA-256: $Hash"
    'Unit tests: Gradle task completed successfully'
    'Lint: Gradle task completed successfully'
    'APK assembly: completed successfully'
    'Device verification: NOT RUN by this script'
)
$ReportPath = Join-Path $OutputRoot 'verification.txt'
$Report | Set-Content -LiteralPath $ReportPath -Encoding UTF8
Write-Host ''
Write-Host '[PASS] Android unit tests, lint, APK assembly and metadata verification'
Write-Host "APK: $ArtifactApk"
Write-Host "SHA-256: $Hash"
Write-Host "Evidence: $OutputRoot"
