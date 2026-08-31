@echo off
rem Kill processes listening on PORT (%~1). Optional label (%~2) for log only.
rem Caps retries so a stuck System PID / failed taskkill cannot freeze start.bat.
setlocal EnableDelayedExpansion
set "PORT=%~1"
set "LABEL=%~2"
if "%PORT%"=="" exit /b 1
if "%LABEL%"=="" set "LABEL=port"

set /a TRIES=0
:again
set /a TRIES+=1
if !TRIES! GTR 8 (
  echo [warn] %LABEL% port %PORT% still in use after 8 tries, continue anyway.
  exit /b 0
)

echo Checking %LABEL% port %PORT% ...
set "FOUND=0"
for /f "tokens=5" %%p in ('netstat -ano -p TCP 2^>nul ^| findstr /R /C:":%PORT%[ ]" ^| findstr /I "LISTENING"') do (
  if not "%%p"=="0" if not "%%p"=="4" (
    set "FOUND=1"
    echo Free %LABEL% port %PORT% pid=%%p  (try !TRIES!/8)
    taskkill /F /PID %%p
  )
)
if "!FOUND!"=="1" (
  timeout /t 1 /nobreak >nul
  goto again
)
exit /b 0
