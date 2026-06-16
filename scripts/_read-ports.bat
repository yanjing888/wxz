@echo off
rem 从 config\ports.env 读取端口到 BACKEND_PORT、FRONTEND_PORT（调用方需已 setlocal）
set "BACKEND_PORT=8082"
set "FRONTEND_PORT=5174"
if not exist "%ROOT%\config\ports.env" goto :eof
for /f "usebackq eol=# tokens=1,* delims==" %%a in ("%ROOT%\config\ports.env") do (
  if /i "%%a"=="BACKEND_PORT" for /f "tokens=*" %%v in ("%%b") do set "BACKEND_PORT=%%v"
  if /i "%%a"=="FRONTEND_PORT" for /f "tokens=*" %%v in ("%%b") do set "FRONTEND_PORT=%%v"
)
