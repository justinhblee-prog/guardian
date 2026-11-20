@echo off
setlocal
set "SCRIPT=%~dp0BUILD_ANDROID.ps1"
if not exist "%SCRIPT%" (
  echo Guardian Android build script was not found: "%SCRIPT%"
  exit /b 2
)
where powershell.exe >nul 2>nul
if errorlevel 1 (
  echo Windows PowerShell 5.1 was not found.
  exit /b 2
)
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%"
set "BUILD_EXIT=%ERRORLEVEL%"
if not "%BUILD_EXIT%"=="0" (
  echo Guardian Android verification failed with exit code %BUILD_EXIT%.
  exit /b %BUILD_EXIT%
)
exit /b 0
