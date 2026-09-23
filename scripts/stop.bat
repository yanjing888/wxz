@echo off
setlocal EnableDelayedExpansion

cd /d "%~dp0.."
set "ROOT=%CD%"
call "%~dp0_read-ports.bat"

echo Stopping services on ports %BACKEND_PORT% and %FRONTEND_PORT% ...

for /f "tokens=5" %%p in ('netstat -ano -p TCP 2^>nul ^| findstr /R /C:":%BACKEND_PORT% " ^| findstr LISTENING') do (
  if not "%%p"=="0" if not "%%p"=="4" (
    echo   kill backend PID %%p
    taskkill /F /PID %%p >nul 2>&1
  )
)

for /f "tokens=5" %%p in ('netstat -ano -p TCP 2^>nul ^| findstr /R /C:":%FRONTEND_PORT% " ^| findstr LISTENING') do (
  if not "%%p"=="0" if not "%%p"=="4" (
    echo   kill frontend PID %%p
    taskkill /F /PID %%p >nul 2>&1
  )
)

echo Done.
pause
