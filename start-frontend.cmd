@echo off
cd /d "%~dp0"
if not exist "logs" mkdir "logs"
echo [%date% %time%] start-frontend.cmd>>"logs\startup.log"
start "WXZ-Frontend" /D "%CD%" cmd.exe /k "scripts\run-frontend.bat"
