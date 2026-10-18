@echo off
setlocal

cd /d "%~dp0.."
set "ROOT=%CD%"
call "%~dp0_read-ports.bat"
if errorlevel 1 exit /b 1

where node.exe >nul 2>&1
if errorlevel 1 (
  echo node.exe not found. Install Node.js 18+.
  pause
  exit /b 1
)

if not exist "%ROOT%\frontend\node_modules" (
  echo frontend\node_modules missing. Run scripts\npm-install.bat first.
  pause
  exit /b 1
)

echo ========================================
echo  WXZ Frontend (dev)
echo  Port %FRONTEND_PORT%
echo  http://localhost:%FRONTEND_PORT%/
echo ========================================
echo.

rem Run directly in this window, as in the original two-console startup.
rem Do not detach through WMI or depend on a hidden supervisor.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\check-service-owner.ps1" -Port %FRONTEND_PORT% -Role frontend
set "FRONTEND_STATE=%ERRORLEVEL%"
if "%FRONTEND_STATE%"=="0" (
  echo Frontend already running: http://localhost:%FRONTEND_PORT%/
  exit /b 0
)
if not "%FRONTEND_STATE%"=="1" (
  pause
  exit /b %FRONTEND_STATE%
)
cd /d "%ROOT%\frontend"
set "CI=true"
echo Keep this frontend window open. You may minimize it.
echo Closing this window stops the frontend.
set "NODE_STABILITY_FLAG="
for /f %%V in ('node.exe -p "Number(process.versions.node.split('.')[0])"') do if %%V GEQ 22 set "NODE_STABILITY_FLAG=--no-maglev"
set "FRONTEND_RETRIES=0"
set "STOP_FILE=%ROOT%\logs\.frontend-%FRONTEND_PORT%.stop"
if exist "%STOP_FILE%" del /q "%STOP_FILE%"
:run_frontend
node.exe %NODE_STABILITY_FLAG% "%ROOT%\frontend\scripts\run-vite.mjs" --host 0.0.0.0 --port %FRONTEND_PORT% --strictPort
set "FRONTEND_RESULT=%ERRORLEVEL%"
>>"%ROOT%\logs\frontend-launcher.log" echo %DATE% %TIME% EXIT code=%FRONTEND_RESULT% retry=%FRONTEND_RETRIES%
if exist "%STOP_FILE%" goto frontend_finished
if "%FRONTEND_RESULT%"=="-1073740791" goto retry_native_crash
if "%FRONTEND_RESULT%"=="-1073741819" goto retry_native_crash
goto frontend_finished
:retry_native_crash
set /a FRONTEND_RETRIES+=1
if %FRONTEND_RETRIES% GTR 3 goto frontend_finished
echo Native runtime crash. Restarting frontend in 2 seconds, attempt %FRONTEND_RETRIES%/3...
powershell.exe -NoProfile -Command "Start-Sleep -Seconds 2"
if exist "%STOP_FILE%" goto frontend_finished
goto run_frontend
:frontend_finished
echo.
echo Frontend exited with code %FRONTEND_RESULT%
pause
exit /b %FRONTEND_RESULT%
