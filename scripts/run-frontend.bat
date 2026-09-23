@echo off
setlocal

cd /d "%~dp0.."
set "ROOT=%CD%"
call "%~dp0_read-ports.bat"

where npm.cmd >nul 2>&1
if errorlevel 1 (
  echo npm.cmd not found. Install Node.js 18+.
  pause
  exit /b 1
)

if not exist "%ROOT%\frontend\node_modules" (
  echo frontend\node_modules missing. Run scripts\npm-install.bat first.
  pause
  exit /b 1
)

echo ========================================
echo  WXZ Frontend (dev)
echo  Port %FRONTEND_PORT%
echo  http://localhost:%FRONTEND_PORT%/
echo ========================================
echo.

cd /d "%ROOT%\frontend"
call npm.cmd run dev -- --host 0.0.0.0 --port %FRONTEND_PORT%
echo.
echo Frontend exited with code %ERRORLEVEL%
pause
