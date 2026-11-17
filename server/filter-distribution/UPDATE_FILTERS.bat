@echo off
setlocal
set "PUBLISH_SCRIPT=%~dp0..\license-api\windows\Update-And-Publish-GuardianFilters.ps1"

if not exist "%PUBLISH_SCRIPT%" (
  echo [Guardian] Laptop publisher script is missing: "%PUBLISH_SCRIPT%"
  exit /b 1
)

where powershell.exe >nul 2>nul
if errorlevel 1 (
  echo [Guardian] Windows PowerShell is required.
  exit /b 1
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PUBLISH_SCRIPT%"
set "RESULT=%ERRORLEVEL%"
if not "%RESULT%"=="0" (
  echo [Guardian] Signed update or cloud publication failed with exit code %RESULT%.
) else (
  echo [Guardian] Signed filters were verified and published to the static cloud origin.
)
exit /b %RESULT%
