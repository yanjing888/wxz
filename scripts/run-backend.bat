@echo off
chcp 65001 >nul
setlocal

cd /d "%~dp0.."
set "ROOT=%CD%"
call "%~dp0_read-ports.bat"
call "%~dp0_read-dify.bat"

if defined MAVEN_HOME (
  set "MVN=%MAVEN_HOME%\bin\mvn.cmd"
) else (
  set "MVN=mvn"
)

echo Backend port %BACKEND_PORT% (%ROOT%\config\ports.env)
echo Dify base %DIFY_BASE_URL% (%ROOT%\config\dify.env)

cd /d "%ROOT%\backend"
"%MVN%" spring-boot:run -Dspring-boot.run.arguments=--server.port=%BACKEND_PORT%
if errorlevel 1 pause
