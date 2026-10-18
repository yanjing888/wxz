@echo off
setlocal

cd /d "%~dp0..\frontend"
if not exist "package.json" (
  echo package.json not found. Run this script from the source project.
  pause
  exit /b 1
)

where npm.cmd >nul 2>&1
if errorlevel 1 (
  echo npm.cmd not found. Install Node.js 18+.
  pause
  exit /b 1
)

echo Installing frontend deps via npm.cmd ...
call npm.cmd install
if errorlevel 1 (
  echo.
  echo npm install failed.
  pause
  exit /b 1
)

echo.
echo Done. Run scripts\start.bat from the project root to start.
pause
