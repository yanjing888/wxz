@echo off
cd /d "%~dp0.."
set "ROOT=%CD%"
if not exist "%ROOT%\logs" mkdir "%ROOT%\logs"
echo [%date% %time%] test-window ok>>"%ROOT%\logs\startup.log"
echo.
echo ========================================
echo  Window test OK - if you see this, bat works
echo  Root: %ROOT%
echo  Log: logs\startup.log
echo ========================================
echo.
pause
