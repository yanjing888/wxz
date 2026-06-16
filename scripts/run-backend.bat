@echo off
chcp 65001 >nul
setlocal

cd /d "%~dp0.."
set "ROOT=%CD%"
call "%~dp0_read-ports.bat"

if defined MAVEN_HOME (
  set "MVN=%MAVEN_HOME%\bin\mvn.cmd"
) else (
  set "MVN=mvn"
)

set "BACKEND_PORT=%BACKEND_PORT%"
echo 后端端口 %BACKEND_PORT%（%ROOT%\config\ports.env）

cd /d "%ROOT%\backend"
"%MVN%" spring-boot:run -Dspring-boot.run.arguments=--server.port=%BACKEND_PORT%
