$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$buildEnvDir = Join-Path $repoRoot "android/build-env"
$resultsDir = Join-Path $buildEnvDir "results"
New-Item -ItemType Directory -Force -Path $resultsDir | Out-Null

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$logPath = Join-Path $resultsDir "guardian-android-validation-$stamp.log"
$summaryPath = Join-Path $resultsDir "guardian-android-validation-$stamp-summary.txt"
$bundlePath = Join-Path $resultsDir "guardian-android-validation-$stamp.zip"
$validationSucceeded = $false
$failureMessage = $null

function Write-LogLine {
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Message)
    Add-Content -LiteralPath $logPath -Value $Message -Encoding UTF8
    Write-Host $Message
}

function Invoke-LoggedNative {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    Write-LogLine ""
    Write-LogLine ("> " + $FilePath + " " + ($Arguments -join " "))
    & $FilePath @Arguments 2>&1 | ForEach-Object {
        $line = [string]$_
        Add-Content -LiteralPath $logPath -Value $line -Encoding UTF8
        Write-Host $line
    }
    $nativeExitCode = $LASTEXITCODE
    Write-LogLine ("Exit code: " + $nativeExitCode)
    return [int]$nativeExitCode
}

Push-Location $repoRoot
try {
    Write-LogLine "Repository: $repoRoot"
    Write-LogLine ("Branch: " + (git branch --show-current 2>&1 | Out-String).Trim())
    Write-LogLine ("Commit: " + (git rev-parse HEAD 2>&1 | Out-String).Trim())
    Write-LogLine ("PowerShell: " + $PSVersionTable.PSVersion.ToString())

    if ((Invoke-LoggedNative -FilePath "docker" -Arguments @("version")) -ne 0) {
        throw "Docker Desktop is not running or the Docker CLI is unavailable."
    }

    if ((Invoke-LoggedNative -FilePath "docker" -Arguments @("buildx", "build", "--pull", "--progress=plain", "--load", "-f", "android/build-env/Dockerfile", "-t", "guardian-android-build-env", ".")) -ne 0) {
        throw "Android build environment image creation failed. See the Docker build output above."
    }

    if ((Invoke-LoggedNative -FilePath "docker" -Arguments @("run", "--rm", "--volume", "${repoRoot}:/workspace", "--workdir", "/workspace", "guardian-android-build-env")) -ne 0) {
        throw "Android Gradle validation failed. The log now contains the container's complete stdout/stderr."
    }

    $apkPath = Join-Path $repoRoot "android/app/build/outputs/apk/debug/app-debug.apk"
    if (!(Test-Path -LiteralPath $apkPath -PathType Leaf)) {
        throw "Gradle returned success but the expected Debug APK is missing."
    }

    $apkHash = (Get-FileHash -Algorithm SHA256 $apkPath).Hash
    $apkSize = (Get-Item -LiteralPath $apkPath).Length
    @(
        "Result: PASS"
        "Commit: $(git rev-parse HEAD)"
        "APK: $apkPath"
        "APK size: $apkSize bytes"
        "APK SHA-256: $apkHash"
        "JUnit: PASS (Gradle task completed)"
        "Lint: PASS (Gradle task completed)"
        "assembleDebug: PASS"
    ) | Set-Content -LiteralPath $summaryPath -Encoding UTF8

    $validationSucceeded = $true
    Write-LogLine "VALIDATION PASSED"
    Write-LogLine "APK size: $apkSize bytes"
    Write-LogLine "APK SHA-256: $apkHash"
}
catch {
    $failureMessage = $_.Exception.Message
    Write-LogLine ("VALIDATION FAILED: " + $failureMessage)
    @(
        "Result: FAIL"
        "Commit: $(git rev-parse HEAD)"
        "Failure: $failureMessage"
        "See log: $logPath"
    ) | Set-Content -LiteralPath $summaryPath -Encoding UTF8
}
finally {
    Pop-Location
}

$archiveInputs = @($logPath, $summaryPath)
$apk = Join-Path $repoRoot "android/app/build/outputs/apk/debug/app-debug.apk"
if ($validationSucceeded -and (Test-Path -LiteralPath $apk -PathType Leaf)) {
    $archiveInputs += $apk
}
Compress-Archive -Path $archiveInputs -DestinationPath $bundlePath -Force
Write-Host ""
Write-Host "Result bundle: $bundlePath"

if (!$validationSucceeded) {
    Write-Host "Validation did not pass. Send the complete log or ZIP for diagnosis."
    exit 1
}
