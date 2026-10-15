@echo off
cd /d "%~dp0"
if not exist "logs" mkdir "logs"
echo [%date% %time%] start-backend.cmd>>"logs\startup.log"
start "WXZ-Backend-JAR" /D "%CD%" cmd.exe /k "scripts\run-backend-jar.bat"
