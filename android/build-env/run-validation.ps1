$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$buildEnvDir = Join-Path $repoRoot "android/build-env"
$resultsDir = Join-Path $buildEnvDir "results"
New-Item -ItemType Directory -Force -Path $resultsDir | Out-Null

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$logPath = Join-Path $resultsDir "guardian-android-validation-$stamp.log"
$bundlePath = Join-Path $resultsDir "guardian-android-validation-$stamp.zip"

Push-Location $repoRoot
try {
    Start-Transcript -Path $logPath -Force | Out-Null
    try {
        Write-Host "Repository: $repoRoot"
        Write-Host "Branch/commit:"
        git branch --show-current
        git rev-parse HEAD

        docker version
        if ($LASTEXITCODE -ne 0) { throw "Docker Desktop is not running or the Docker CLI is unavailable." }

        docker build --pull -f android/build-env/Dockerfile -t guardian-android-build-env .
        if ($LASTEXITCODE -ne 0) { throw "Android build environment image creation failed." }

        docker run --rm --volume "${repoRoot}:/workspace" --workdir /workspace guardian-android-build-env
        if ($LASTEXITCODE -ne 0) { throw "Android Gradle validation failed; see this transcript for the failing task." }

        $apkPath = Join-Path $repoRoot "android/app/build/outputs/apk/debug/app-debug.apk"
        if (!(Test-Path $apkPath -PathType Leaf)) { throw "Gradle returned success but the expected Debug APK is missing." }

        $apkHash = (Get-FileHash -Algorithm SHA256 $apkPath).Hash
        $apkSize = (Get-Item $apkPath).Length
        @(
            "Commit: $(git rev-parse HEAD)"
            "APK: $apkPath"
            "APK size: $apkSize bytes"
            "APK SHA-256: $apkHash"
            "JUnit: PASS (Gradle task completed)"
            "Lint: PASS (Gradle task completed)"
            "assembleDebug: PASS"
        ) | Set-Content -Encoding UTF8 (Join-Path $resultsDir "guardian-android-validation-$stamp-summary.txt")

        Write-Host "VALIDATION PASSED"
        Write-Host "APK size: $apkSize bytes"
        Write-Host "APK SHA-256: $apkHash"
    }
    finally {
        Stop-Transcript | Out-Null
    }
}
finally {
    Pop-Location
}

$summaryPath = Join-Path $resultsDir "guardian-android-validation-$stamp-summary.txt"
$archiveInputs = @($logPath)
if (Test-Path $summaryPath) { $archiveInputs += $summaryPath }
$apk = Join-Path $repoRoot "android/app/build/outputs/apk/debug/app-debug.apk"
if (Test-Path $apk) { $archiveInputs += $apk }
Compress-Archive -Path $archiveInputs -DestinationPath $bundlePath -Force
Write-Host "Result bundle: $bundlePath"
