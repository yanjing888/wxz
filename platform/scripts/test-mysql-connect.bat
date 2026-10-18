@echo off
setlocal

cd /d "%~dp0.."
set "ROOT=%CD%"
call "%~dp0_read-mysql.bat"

if /I not "%MYSQL_ENABLED%"=="true" (
  echo MYSQL_ENABLED is not true in config\mysql.env
  pause
  exit /b 1
)

echo ========================================
echo  MySQL connectivity test
echo  Target: %MYSQL_USER%@%MYSQL_HOST%:%MYSQL_PORT%/%MYSQL_DATABASE%
echo  Password: 
if "%MYSQL_PASSWORD%"=="" (echo   EMPTY - fix config\mysql.env) else (echo   set)
echo ========================================
echo.

echo [1] TCP port %MYSQL_PORT% on %MYSQL_HOST% ...
powershell -NoProfile -Command "try { $r = Test-NetConnection -ComputerName '%MYSQL_HOST%' -Port %MYSQL_PORT% -WarningAction SilentlyContinue; Write-Host ('  TcpTestSucceeded=' + $r.TcpTestSucceeded); if ($r.TcpTestSucceeded) { exit 0 } else { exit 1 } } catch { Write-Host $_.Exception.Message; exit 1 }"
if errorlevel 1 (
  echo   FAIL - network blocked or MySQL not listening
  echo   On DB server run as admin:
  echo     netsh advfirewall firewall add rule name="MySQL3306" dir=in action=allow protocol=TCP localport=3306
  goto :done
)
echo   OK
echo.

echo [2] MySQL login ...
where mysql >nul 2>&1
if errorlevel 1 (
  echo   SKIP mysql.exe not in PATH
  echo   Install MySQL client or read logs\backend-console.log after backend start
) else (
  mysql -h %MYSQL_HOST% -P %MYSQL_PORT% -u %MYSQL_USER% -p%MYSQL_PASSWORD% -e "SELECT 1 AS ok;" %MYSQL_DATABASE% 2>&1
  if errorlevel 1 (
    echo   FAIL - wrong password or user not allowed from this PC
    echo   On DB server MySQL run:
    echo     GRANT ALL ON wuxiaozhi.* TO 'root'@'%%' IDENTIFIED BY 'your_password';
    echo     FLUSH PRIVILEGES;
  ) else (
    echo   OK - login success
  )
)

:done
echo.
pause
