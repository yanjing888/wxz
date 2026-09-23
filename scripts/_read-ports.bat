@echo off
rem Load BACKEND_PORT, FRONTEND_PORT from config\ports.env (caller must set ROOT).
set "BACKEND_PORT=8082"
set "FRONTEND_PORT=5174"
set "BENCH_CAMERA_IP=188.18.31.195"
set "UVC_CAMERA_PORT=8765"
if not exist "%ROOT%\config\ports.env" goto :eof
for /f "usebackq eol=# tokens=1,* delims==" %%a in ("%ROOT%\config\ports.env") do (
  if /i "%%a"=="BACKEND_PORT" for /f "tokens=*" %%v in ("%%b") do set "BACKEND_PORT=%%v"
  if /i "%%a"=="FRONTEND_PORT" for /f "tokens=*" %%v in ("%%b") do set "FRONTEND_PORT=%%v"
  if /i "%%a"=="BENCH_CAMERA_IP" for /f "tokens=*" %%v in ("%%b") do set "BENCH_CAMERA_IP=%%v"
  if /i "%%a"=="UVC_CAMERA_PORT" for /f "tokens=*" %%v in ("%%b") do set "UVC_CAMERA_PORT=%%v"
  if /i "%%a"=="UVC_CAMERA_NAME_KEYWORD" for /f "tokens=*" %%v in ("%%b") do set "UVC_CAMERA_NAME_KEYWORD=%%v"
)
