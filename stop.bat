@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0acquisition-service\stop.ps1"
call "%~dp0platform\scripts\stop.bat" --no-pause
pause
