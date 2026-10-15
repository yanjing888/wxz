@echo off
cd /d "%~dp0"
if not exist "logs" mkdir "logs"
echo [%date% %time%] 启动后端.cmd>>"logs\startup.log"
start "WXZ-Backend-JAR" cmd.exe /k "cd /d ""%CD%"" && call ""%CD%\scripts\run-backend-jar.bat"""
