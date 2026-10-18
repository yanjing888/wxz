@echo off
setlocal EnableDelayedExpansion

cd /d "%~dp0.."
set "ROOT=%CD%"
call "%~dp0_read-ports.bat"
call "%~dp0_read-mysql.bat"

echo ========================================
echo   Wuxiaozhi deploy start
echo   Root: %ROOT%
echo ========================================
echo.

set "JAR=%ROOT%\backend\target\wuxiaozhi-backend-1.0.0.jar"
if not exist "%JAR%" (
  echo [ERROR] Jar not found:
  echo   %JAR%
  echo Run scripts\pack-deploy.bat on dev machine first.
  echo.
  pause
  exit /b 1
)

where java >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Java not found. Install JDK 17+.
  echo.
  pause
  exit /b 1
)

where npm.cmd >nul 2>&1
if errorlevel 1 (
  echo [ERROR] npm.cmd not found. Install Node.js 18+.
  echo.
  pause
  exit /b 1
)

if not exist "%ROOT%\frontend\node_modules" (
  echo [ERROR] frontend\node_modules missing.
  echo Run scripts\npm-install.bat first.
  echo.
  pause
  exit /b 1
)

echo Backend jar : %JAR%
echo Backend port: %BACKEND_PORT%
echo Frontend port: %FRONTEND_PORT%
if /I "%MYSQL_ENABLED%"=="true" (
  echo Database    : MySQL %MYSQL_USER%@%MYSQL_HOST%:%MYSQL_PORT%/%MYSQL_DATABASE%
) else (
  echo Database    : H2 local file
)
echo.
echo Service windows: WXZ-Backend-JAR and WXZ-Frontend. Keep both open.
echo Open browser: http://localhost:%FRONTEND_PORT%/
echo.
echo If nothing appears, run scripts\unblock-all.bat then scripts\check-deploy.bat
echo.
pause

start "WXZ-Backend-JAR" cmd.exe /k "cd /d ""%ROOT%"" && call ""%ROOT%\scripts\run-backend-jar.bat"""
timeout /t 2 /nobreak >nul
start "WXZ-Frontend" /D "%ROOT%" cmd.exe /k "scripts\run-frontend.bat"

echo.
echo Launch commands sent. Check taskbar for WXZ windows.
echo.
pause
exit /b 0
