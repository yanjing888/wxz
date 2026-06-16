@echo off
rem Kill processes listening on PORT (%~1). Optional label (%~2) for log only.
set "PORT=%~1"
set "LABEL=%~2"

:again
set "FOUND=0"
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /C:":%PORT% " ^| findstr /I "LISTENING"') do (
  set "FOUND=1"
  echo Free port %PORT% label=%LABEL% pid=%%p
  taskkill /F /PID %%p >nul 2>&1
)
if "%FOUND%"=="1" (
  timeout /t 1 /nobreak >nul
  goto again
)
