@echo off
setlocal
cd /d "%~dp0"
start "WXZ-Acquisition" /D "%~dp0acquisition-service" cmd.exe /k "start.bat"
call "%~dp0platform\scripts\start.bat"
