@echo off
setlocal
cd /d "%~dp0android"
if exist gradlew.bat (
  call gradlew.bat :app:testDebugUnitTest :app:assembleDebug
) else (
  where gradle >nul 2>nul
  if errorlevel 1 (
    echo Gradle not found. Install Android Studio or Gradle, open this android folder, and sync the project.
    exit /b 1
  )
  call gradle :app:testDebugUnitTest :app:assembleDebug
)
if errorlevel 1 exit /b 1
if not exist "app\build\outputs\apk\debug\app-debug.apk" (
  echo Build returned success but the expected APK is missing.
  exit /b 1
)
echo APK: %CD%\app\build\outputs\apk\debug\app-debug.apk