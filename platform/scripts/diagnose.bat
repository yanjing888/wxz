@echo off
setlocal EnableDelayedExpansion

cd /d "%~dp0.."
set "ROOT=%CD%"
if not exist "%ROOT%\logs" mkdir "%ROOT%\logs"
set "OUT=%ROOT%\logs\diagnose.txt"

echo Wuxiaozhi diagnose > "%OUT%"
echo Root: %ROOT%>>"%OUT%"
echo Time: %date% %time%>>"%OUT%"
echo.>>"%OUT%"

echo === Java ===>>"%OUT%"
where java>>"%OUT%" 2>&1
java -version>>"%OUT%" 2>&1
echo.>>"%OUT%"

echo === npm ===>>"%OUT%"
where npm.cmd>>"%OUT%" 2>&1
npm.cmd -v>>"%OUT%" 2>&1
echo.>>"%OUT%"

echo === Jar ===>>"%OUT%"
if exist "%ROOT%\backend\target\wuxiaozhi-backend-1.0.0.jar" (
  echo OK backend\target\wuxiaozhi-backend-1.0.0.jar>>"%OUT%"
  dir "%ROOT%\backend\target\wuxiaozhi-backend-1.0.0.jar">>"%OUT%"
) else (
  echo MISSING jar>>"%OUT%"
)
echo.>>"%OUT%"

echo === node_modules ===>>"%OUT%"
if exist "%ROOT%\frontend\node_modules" (
  echo OK frontend\node_modules>>"%OUT%"
) else (
  echo MISSING frontend\node_modules>>"%OUT%"
)
echo.>>"%OUT%"

echo === mysql.env ===>>"%OUT%"
if exist "%ROOT%\config\mysql.env" type "%ROOT%\config\mysql.env">>"%OUT%"
echo.>>"%OUT%"

echo === ports ===>>"%OUT%"
netstat -ano -p TCP | findstr /R /C:":8082 " | findstr LISTENING>>"%OUT%" 2>&1
netstat -ano -p TCP | findstr /R /C:":5174 " | findstr LISTENING>>"%OUT%" 2>&1
echo.>>"%OUT%"

echo === backend.log tail ===>>"%OUT%"
if exist "%ROOT%\logs\backend.log" powershell -NoProfile -Command "Get-Content -Path '%ROOT%\logs\backend.log' -Tail 40">>"%OUT%" 2>&1
echo.>>"%OUT%"

echo === go.log ===>>"%OUT%"
if exist "%ROOT%\logs\go.log" type "%ROOT%\logs\go.log">>"%OUT%"

echo.
echo Report saved: %OUT%
echo Send this file if you need help.
echo.
type "%OUT%"
echo.
pause
