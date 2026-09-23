@echo off
setlocal

cd /d "%~dp0.."
set "ROOT=%CD%"

echo ========================================
echo   Wuxiaozhi - build deploy zip
echo ========================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0pack-deploy.ps1" -Root "%ROOT%"
if errorlevel 1 (
  echo.
  echo Pack failed.
  pause
  exit /b 1
)

echo.
pause
