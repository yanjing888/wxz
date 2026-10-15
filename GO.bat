@echo off
setlocal EnableDelayedExpansion

cd /d "%~dp0"
set "ROOT=%CD%"
if not exist "%ROOT%\logs" mkdir "%ROOT%\logs"
echo [%date% %time%] GO.bat>>"%ROOT%\logs\go.log"

echo ========================================
echo  Wuxiaozhi - one-click deploy start
echo  Root: %ROOT%
echo ========================================
echo.

echo [1/7] Unblock scripts ...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-ChildItem -Path '%ROOT%' -Recurse -Include *.bat,*.cmd,*.vbs -File | Unblock-File -ErrorAction SilentlyContinue" >>"%ROOT%\logs\go.log" 2>&1
echo   done
echo.

echo [2/7] Java ...
where java >nul 2>&1
if errorlevel 1 (
  echo   FAIL - Java not found. Install JDK 17+ and reopen this window.
  echo FAIL java>>"%ROOT%\logs\go.log"
  goto :fail
)
for /f "delims=" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do echo   OK  %%v
echo.

echo [3/7] Node npm ...
where npm.cmd >nul 2>&1
if errorlevel 1 (
  echo   FAIL - npm.cmd not found. Install Node.js 18+.
  echo FAIL npm>>"%ROOT%\logs\go.log"
  goto :fail
)
for /f "delims=" %%v in ('npm.cmd -v 2^>nul') do echo   OK  npm %%v
echo.

echo [4/7] Backend jar ...
set "JAR=%ROOT%\backend\target\wuxiaozhi-backend-1.0.0.jar"
if not exist "%JAR%" (
  echo   FAIL - jar not found:
  echo   %JAR%
  echo FAIL jar>>"%ROOT%\logs\go.log"
  goto :fail
)
echo   OK  %JAR%
echo.

echo [5/7] Frontend deps ...
if exist "%ROOT%\frontend\node_modules" (
  echo   OK  node_modules exists
) else (
  echo   Installing npm packages, please wait ...
  cd /d "%ROOT%\frontend"
  call npm.cmd install
  if errorlevel 1 (
    echo   FAIL - npm install failed
    cd /d "%ROOT%"
    goto :fail
  )
  cd /d "%ROOT%"
  echo   OK  npm install done
)
echo.

echo [6/7] Config ...
call "%ROOT%\scripts\_read-ports.bat"
call "%ROOT%\scripts\_read-mysql.bat"
echo   Backend port %BACKEND_PORT%  Frontend port %FRONTEND_PORT%
if /I "%MYSQL_ENABLED%"=="true" (
  echo   MySQL %MYSQL_USER%@%MYSQL_HOST%:%MYSQL_PORT%/%MYSQL_DATABASE%
  if /I "%MYSQL_HOST%"=="127.0.0.1" (
    echo   WARN - MYSQL_HOST is 127.0.0.1
    echo   WARN - On deploy PC set config\mysql.env MYSQL_HOST to DB server IP
  )
) else (
  echo   Database H2 local file
)
echo.

echo [7/7] Starting backend and frontend ...
echo   Two cmd windows will open. Do NOT close them.
echo   Browser: http://localhost:%FRONTEND_PORT%/
echo.

start "WXZ-Backend-JAR" /D "%ROOT%" cmd.exe /k "scripts\run-backend-jar.bat"
timeout /t 3 /nobreak >nul
start "WXZ-Frontend" /D "%ROOT%" cmd.exe /k "scripts\run-frontend.bat"

echo   Launch sent. Check taskbar for WXZ-Backend-JAR and WXZ-Frontend.
echo   If still no window, run in THIS cmd:
echo     scripts\run-backend-jar.bat
echo     scripts\run-frontend.bat
echo.
echo   Logs: logs\backend.log  logs\go.log
echo.
pause
exit /b 0

:fail
echo.
echo ========================================
echo  START FAILED - see messages above
echo  Full log: logs\go.log
echo  Run scripts\diagnose.bat and send logs\diagnose.txt
echo ========================================
echo.
pause
exit /b 1
