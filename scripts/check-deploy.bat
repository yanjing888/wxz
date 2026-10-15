@echo off
setlocal EnableDelayedExpansion

cd /d "%~dp0.."
set "ROOT=%CD%"
call "%~dp0_read-ports.bat"
call "%~dp0_read-mysql.bat"

set "OK=1"

echo ========================================
echo  Wuxiaozhi deploy check
echo  Root: %ROOT%
echo ========================================
echo.

echo [1/6] Backend jar
set "JAR=%ROOT%\backend\target\wuxiaozhi-backend-1.0.0.jar"
if exist "%JAR%" (
  echo   OK  %JAR%
) else (
  echo   FAIL  jar not found
  echo         run scripts\pack-deploy.bat on dev machine
  set "OK=0"
)
echo.

echo [2/6] Java
where java >nul 2>&1
if errorlevel 1 (
  echo   FAIL  java not found, install JDK 17+
  set "OK=0"
) else (
  for /f "delims=" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do echo   OK  %%v
)
echo.

echo [3/6] Node.js / npm
where npm.cmd >nul 2>&1
if errorlevel 1 (
  echo   FAIL  npm.cmd not found, install Node.js 18+
  set "OK=0"
) else (
  for /f "delims=" %%v in ('npm.cmd -v 2^>nul') do echo   OK  npm %%v
)
echo.

echo [4/6] frontend node_modules
if exist "%ROOT%\frontend\node_modules" (
  echo   OK  frontend\node_modules exists
) else (
  echo   FAIL  run scripts\npm-install.bat
  set "OK=0"
)
echo.

echo [5/6] Config files
if exist "%ROOT%\config\mysql.env" (
  echo   OK  config\mysql.env
  if /I "%MYSQL_ENABLED%"=="true" (
    echo       MySQL %MYSQL_USER%@%MYSQL_HOST%:%MYSQL_PORT%/%MYSQL_DATABASE%
  ) else (
    echo       MySQL disabled, using H2
  )
) else (
  echo   WARN  config\mysql.env missing
)
if exist "%ROOT%\config\ports.env" (
  echo   OK  frontend %FRONTEND_PORT%  backend %BACKEND_PORT%
) else (
  echo   OK  default ports frontend 5174 backend 8082
)
echo.

echo [6/6] Port usage
netstat -ano -p TCP 2>nul | findstr /R /C:":%BACKEND_PORT% " | findstr /I "LISTENING" >nul
if errorlevel 1 (
  echo   OK  backend port %BACKEND_PORT% free
) else (
  echo   WARN  backend port %BACKEND_PORT% in use
)
netstat -ano -p TCP 2>nul | findstr /R /C:":%FRONTEND_PORT% " | findstr /I "LISTENING" >nul
if errorlevel 1 (
  echo   OK  frontend port %FRONTEND_PORT% free
) else (
  echo   WARN  frontend port %FRONTEND_PORT% in use
)
echo.

echo ========================================
if "%OK%"=="1" (
  echo  PASS - run scripts\start-deploy.bat
) else (
  echo  FAIL - fix items above first
)
echo ========================================
echo.
pause
exit /b %OK%
