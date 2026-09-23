@echo off
setlocal EnableDelayedExpansion

cd /d "%~dp0.."
set "ROOT=%CD%"
call "%~dp0_read-ports.bat"

echo Ports: backend=%BACKEND_PORT% frontend=%FRONTEND_PORT%
echo Free ports if already in use ...
call "%~dp0_free-port.bat" %BACKEND_PORT% backend
call "%~dp0_free-port.bat" %FRONTEND_PORT% frontend
echo Starting...

start "WXZ-Backend" /D "%ROOT%" cmd.exe /k "scripts\run-backend.bat"
start "WXZ-Frontend" /D "%ROOT%" cmd.exe /k "scripts\run-frontend.bat"
exit /b 0
