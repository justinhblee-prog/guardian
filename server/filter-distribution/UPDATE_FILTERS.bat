@echo off
setlocal
set "PUBLISH_SCRIPT=%~dp0..\license-api\windows\Update-And-Publish-GuardianFilters.ps1"

if not exist "%PUBLISH_SCRIPT%" (
  echo [Guardian] Laptop publisher script is missing: "%PUBLISH_SCRIPT%"
  exit /b 1
)

where pwsh.exe >nul 2>nul
if errorlevel 1 (
  echo [Guardian] PowerShell 7.2 or later (pwsh.exe) is required.
  exit /b 1
)

pwsh.exe -NoProfile -ExecutionPolicy Bypass -File "%PUBLISH_SCRIPT%"
set "RESULT=%ERRORLEVEL%"
if not "%RESULT%"=="0" (
  echo [Guardian] Signed update or cloud publication failed with exit code %RESULT%.
) else (
  echo [Guardian] Signed filters were verified and published to the static cloud origin.
)
exit /b %RESULT%
