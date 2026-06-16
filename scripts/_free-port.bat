@echo off
rem 释放指定端口（循环结束所有占用该端口的进程）
set "PORT=%~1"
set "LABEL=%~2"

:again
set "FOUND=0"
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /C:":%PORT% " ^| findstr /I "LISTENING"') do (
  set "FOUND=1"
  echo 释放端口 %PORT% (%LABEL%)：结束 PID %%p
  taskkill /F /PID %%p >nul 2>&1
)
if "%FOUND%"=="1" (
  timeout /t 1 /nobreak >nul
  goto again
)
