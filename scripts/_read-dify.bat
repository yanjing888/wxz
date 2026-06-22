@echo off
rem Read config\dify.env into DIFY_* environment variables (caller must set ROOT).
set "DIFY_BASE_URL="
set "DIFY_APP_MODE=chat"
set "DIFY_API_KEY="
set "DIFY_WF_VISION_CORRECTION="
set "DIFY_WF_TEXT_ASSIST="
set "DIFY_WF_ENV_CHECK="
set "DIFY_WF_ENV_CHECK_MODE="
set "DIFY_WF_REPORT_GENERATE="
set "DIFY_KB_API_KEY="
set "DIFY_KB_TOP_K=5"

if not defined ROOT goto :eof
if not exist "%ROOT%\config\dify.env" goto :eof

for /f "usebackq eol=# tokens=1,* delims==" %%a in ("%ROOT%\config\dify.env") do (
  if /i "%%a"=="DIFY_BASE_URL" for /f "tokens=*" %%v in ("%%b") do set "DIFY_BASE_URL=%%v"
  if /i "%%a"=="DIFY_APP_MODE" for /f "tokens=*" %%v in ("%%b") do set "DIFY_APP_MODE=%%v"
  if /i "%%a"=="DIFY_API_KEY" for /f "tokens=*" %%v in ("%%b") do set "DIFY_API_KEY=%%v"
  if /i "%%a"=="DIFY_WF_VISION_CORRECTION" for /f "tokens=*" %%v in ("%%b") do set "DIFY_WF_VISION_CORRECTION=%%v"
  if /i "%%a"=="DIFY_WF_TEXT_ASSIST" for /f "tokens=*" %%v in ("%%b") do set "DIFY_WF_TEXT_ASSIST=%%v"
  if /i "%%a"=="DIFY_WF_ENV_CHECK" for /f "tokens=*" %%v in ("%%b") do set "DIFY_WF_ENV_CHECK=%%v"
  if /i "%%a"=="DIFY_WF_ENV_CHECK_MODE" for /f "tokens=*" %%v in ("%%b") do set "DIFY_WF_ENV_CHECK_MODE=%%v"
  if /i "%%a"=="DIFY_WF_REPORT_GENERATE" for /f "tokens=*" %%v in ("%%b") do set "DIFY_WF_REPORT_GENERATE=%%v"
  if /i "%%a"=="DIFY_KB_API_KEY" for /f "tokens=*" %%v in ("%%b") do set "DIFY_KB_API_KEY=%%v"
  if /i "%%a"=="DIFY_KB_TOP_K" for /f "tokens=*" %%v in ("%%b") do set "DIFY_KB_TOP_K=%%v"
)
