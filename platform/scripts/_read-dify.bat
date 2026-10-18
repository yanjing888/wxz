@echo off
rem Read config\dify.env into DIFY_* environment variables (caller must set ROOT).
rem Any key starting with DIFY_ is passed through, so adding a workflow only needs a dify.env edit.

for /f "usebackq tokens=1 delims==" %%a in (`set DIFY_ 2^>nul`) do set "%%a="
set "DIFY_APP_MODE=chat"
set "DIFY_KB_TOP_K=5"

if not defined ROOT goto :eof
if not exist "%ROOT%\config\dify.env" goto :eof

for /f "usebackq eol=# tokens=1,* delims==" %%a in ("%ROOT%\config\dify.env") do call :set_dify "%%a" "%%b"
goto :eof

:set_dify
set "DIFY_KEY=%~1"
if /i not "%DIFY_KEY:~0,5%"=="DIFY_" goto :eof
set "%DIFY_KEY%="
for /f "tokens=*" %%v in ("%~2") do set "%DIFY_KEY%=%%v"
set "DIFY_KEY="
goto :eof
