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
echo Dify base %DIFY_BASE_URL% (%ROOT%\config\dify.env)
if /I "%MYSQL_ENABLED%"=="true" (
  echo Database MySQL %MYSQL_USER%@%MYSQL_HOST%:%MYSQL_PORT%/%MYSQL_DATABASE% (%ROOT%\config\mysql.env)
) else (
  echo Database H2 local file ^(set MYSQL_ENABLED=true in config\mysql.env to use MySQL^)
)

cd /d "%ROOT%\backend"
"%MVN%" spring-boot:run -Dspring-boot.run.arguments=--server.port=%BACKEND_PORT%
if errorlevel 1 pause
