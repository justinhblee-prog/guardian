@echo off
setlocal
cd /d "%~dp0\.."

where pwsh.exe >nul 2>nul
if errorlevel 1 (
    echo PowerShell 7.2 or newer is required. Install PowerShell 7 and try again.
    echo https://learn.microsoft.com/powershell/scripting/install/installing-powershell-on-windows
    pause
    exit /b 1
)

pwsh.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0Install-GuardianLocalOllamaRunner.ps1" %*
set "RUNNER_EXIT=%ERRORLEVEL%"
if not "%RUNNER_EXIT%"=="0" (
    echo.
    echo Guardian local runner setup failed with exit code %RUNNER_EXIT%.
    pause
    exit /b %RUNNER_EXIT%
)

echo.
echo Guardian local runner and scheduled local agent setup finished.
pause
exit /b 0
