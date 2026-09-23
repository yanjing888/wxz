@echo off
chcp 65001 >nul
setlocal

cd /d "%~dp0.."
set "ROOT=%CD%"
call "%~dp0_read-ports.bat"
call "%~dp0_read-dify.bat"
call "%~dp0_read-mysql.bat"

if defined MAVEN_HOME (
  set "MVN=%MAVEN_HOME%\bin\mvn.cmd"
) else (
  set "MVN=mvn"
)

echo Backend port %BACKEND_PORT% (%ROOT%\config\ports.env)
echo Bench camera IP %BENCH_CAMERA_IP% (%ROOT%\config\ports.env)
echo Dify base %DIFY_BASE_URL% (%ROOT%\config\dify.env)
if /I "%MYSQL_ENABLED%"=="true" (
  echo Database MySQL %MYSQL_USER%@%MYSQL_HOST%:%MYSQL_PORT%/%MYSQL_DATABASE% [%ROOT%\config\mysql.env]
  set "PROFILE_ARG=-Dspring-boot.run.profiles=mysql"
) else (
  echo Database H2 local file [set MYSQL_ENABLED=true in config\mysql.env to use MySQL]
  set "PROFILE_ARG="
)

python -m pip install -q -r "%ROOT%\backend\scripts\requirements-uvc.txt" 2>nul

cd /d "%ROOT%\backend"
"%MVN%" -DskipTests spring-boot:run %PROFILE_ARG% -Dspring-boot.run.arguments=--server.port=%BACKEND_PORT%
if errorlevel 1 pause
