@echo off
setlocal EnableDelayedExpansion

cd /d "%~dp0.."
set "ROOT=%CD%"
call "%~dp0_read-ports.bat"
if errorlevel 1 exit /b 1

echo Ports: backend=%BACKEND_PORT% frontend=%FRONTEND_PORT%
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\check-service-owner.ps1" -Port %BACKEND_PORT% -Role backend
set "BACKEND_STATE=%ERRORLEVEL%"
if "%BACKEND_STATE%"=="2" (
  pause
  exit /b 1
)
if "%BACKEND_STATE%"=="0" (
  start "WXZ-Backend" /D "%ROOT%" powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\watch-backend.ps1" -Port %BACKEND_PORT%
) else (
  start "WXZ-Backend" /D "%ROOT%" cmd.exe /k "scripts\run-backend.bat"
)
start "WXZ-Frontend" /D "%ROOT%" cmd.exe /k "scripts\run-frontend.bat"
exit /b 0
