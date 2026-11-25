$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
Push-Location $repoRoot
try {
    docker version | Out-Null
    docker build --pull -f android/build-env/Dockerfile -t guardian-android-build-env .
    if ($LASTEXITCODE -ne 0) { throw "Android build environment image creation failed." }

    docker run --rm --volume "${repoRoot}:/workspace" --workdir /workspace guardian-android-build-env
    if ($LASTEXITCODE -ne 0) { throw "Android Gradle validation failed; inspect the task output above." }
}
finally {
    Pop-Location
}
