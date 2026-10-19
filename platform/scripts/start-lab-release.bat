@echo off
chcp 65001 >nul
cd /d "%~dp0"
where java >nul 2>&1
if errorlevel 1 (
  echo Install Java 17 or newer x64 and add Java to PATH first.
  pause
  exit /b 1
)
java -jar "%~dp0app.jar"
if errorlevel 1 pause
