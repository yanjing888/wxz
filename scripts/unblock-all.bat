@echo off
cd /d "%~dp0.."
set "ROOT=%CD%"

echo Unblock scripts blocked by Windows after zip extract
echo Root: %ROOT%
echo.

powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-ChildItem -Path '%ROOT%' -Recurse -Include *.bat,*.cmd,*.vbs -File | Unblock-File -ErrorAction SilentlyContinue"

echo.
echo Done. Run scripts\test-window.bat to verify.
echo.
pause
