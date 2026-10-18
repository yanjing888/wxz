@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0.."
set "ROOT=%CD%"
if not exist "%ROOT%\config\application.yml" (
  echo Copy config\application.yml.example to config\application.yml and configure MySQL first.
  pause
  exit /b 1
)
if defined MAVEN_HOME (set "MVN=%MAVEN_HOME%\bin\mvn.cmd") else (set "MVN=mvn")
echo Backend configuration: %ROOT%\config\application.yml
cd /d "%ROOT%\backend"
"%MVN%" -DskipTests spring-boot:run
if errorlevel 1 pause
