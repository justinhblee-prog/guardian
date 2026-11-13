$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Android = Join-Path $Root 'android'
Set-Location $Android
if (Test-Path '.\gradlew.bat') {
    .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
} elseif (Get-Command gradle -ErrorAction SilentlyContinue) {
    gradle :app:testDebugUnitTest :app:assembleDebug
} else {
    throw 'Gradle was not found. Install Android Studio/Gradle, open the android folder, sync Gradle, then run :app:testDebugUnitTest :app:assembleDebug.'
}
$Apk = Join-Path $Android 'app\build\outputs\apk\debug\app-debug.apk'
if (!(Test-Path $Apk)) { throw "Build completed but APK was not found: $Apk" }
Write-Host "APK: $Apk"