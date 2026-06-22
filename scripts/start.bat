@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

cd /d "%~dp0.."
set "ROOT=%CD%"
call "%~dp0_read-ports.bat"

echo Ports: backend=%BACKEND_PORT% frontend=%FRONTEND_PORT%
echo Config: %ROOT%\config\ports.env
echo Freeing ports...
call "%~dp0_free-port.bat" %BACKEND_PORT% backend
call "%~dp0_free-port.bat" %FRONTEND_PORT% frontend
timeout /t 1 /nobreak >nul

echo Starting backend...
start "WXZ-Backend" cmd /k call "%~dp0run-backend.bat"

timeout /t 2 /nobreak >nul

echo Starting frontend...
start "WXZ-Frontend" cmd /k call "%~dp0run-frontend.bat"

exit /b 0
