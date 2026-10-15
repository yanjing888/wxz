@echo off
if not defined ROOT exit /b 0
if not exist "%ROOT%\logs" mkdir "%ROOT%\logs" >nul 2>&1
exit /b 0
