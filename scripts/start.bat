@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

cd /d "%~dp0.."
set "ROOT=%CD%"
call "%~dp0_read-ports.bat"

echo Ports: backend=%BACKEND_PORT% frontend=%FRONTEND_PORT%
echo Starting...

start "WXZ-Backend" cmd /k call "%~dp0run-backend.bat"
start "WXZ-Frontend" cmd /k call "%~dp0run-frontend.bat"
exit /b 0
