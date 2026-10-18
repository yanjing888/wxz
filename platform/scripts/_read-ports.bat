@echo off
rem Development scripts and Vite read the same external YAML configuration.
set "BACKEND_PORT="
set "FRONTEND_PORT="
for /f "tokens=1,* delims==" %%a in ('node "%ROOT%\config\loadPorts.mjs" --cmd') do set "%%a=%%b"
if not defined BACKEND_PORT (
  echo Could not read config\application.yml. Check the file and install frontend dependencies.
  exit /b 1
)
exit /b 0
