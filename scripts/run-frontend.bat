@echo off
chcp 65001 >nul
setlocal

cd /d "%~dp0.."
set "ROOT=%CD%"
call "%~dp0_read-ports.bat"

echo Frontend port %FRONTEND_PORT% (%ROOT%\config\ports.env)

cd /d "%ROOT%\frontend"
npm run dev -- --host 0.0.0.0 --port %FRONTEND_PORT%
if errorlevel 1 pause
