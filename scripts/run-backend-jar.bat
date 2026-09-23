@echo off
setlocal EnableDelayedExpansion

cd /d "%~dp0.."
set "ROOT=%CD%"

call "%~dp0_read-ports.bat"
call "%~dp0_read-dify.bat"
call "%~dp0_read-mysql.bat"

set "JAR=%ROOT%\backend\target\wuxiaozhi-backend-1.0.0.jar"
if not exist "%JAR%" (
  echo Jar not found: %JAR%
  pause
  exit /b 1
)

where java >nul 2>&1
if errorlevel 1 (
  echo Java not found. Install JDK 17+.
  pause
  exit /b 1
)

echo ========================================
echo  WXZ Backend (jar)
echo  Port %BACKEND_PORT%
if /I "!MYSQL_ENABLED!"=="true" (
  echo  MySQL !MYSQL_USER!@!MYSQL_HOST!:!MYSQL_PORT!/!MYSQL_DATABASE!
  set "PROFILE_ARG=--spring.profiles.active=mysql"
  set "JAVA_PROPS=-DMYSQL_HOST=!MYSQL_HOST! -DMYSQL_PORT=!MYSQL_PORT! -DMYSQL_DATABASE=!MYSQL_DATABASE! -DMYSQL_USER=!MYSQL_USER! -DMYSQL_PASSWORD=!MYSQL_PASSWORD!"
) else (
  echo  Database H2 local file
  set "PROFILE_ARG="
  set "JAVA_PROPS="
)
echo ========================================
echo.

python -m pip install -q -r "%ROOT%\backend\scripts\requirements-uvc.txt" 2>nul

cd /d "%ROOT%"
java -jar "%JAR%" !PROFILE_ARG! !JAVA_PROPS! --server.port=%BACKEND_PORT%
if errorlevel 1 pause
