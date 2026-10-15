@echo off
cd /d "%~dp0"
if not exist "logs" mkdir "logs"
echo [%date% %time%] 启动前端.cmd>>"logs\startup.log"
start "WXZ-Frontend" cmd.exe /k "cd /d ""%CD%"" && call ""%CD%\scripts\run-frontend.bat"""
