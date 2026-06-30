@echo off
rem Read config\mysql.env. MYSQL_ENABLED=true activates the mysql Spring profile.
set "MYSQL_ENABLED=false"
set "MYSQL_HOST=127.0.0.1"
set "MYSQL_PORT=3306"
set "MYSQL_DATABASE=wuxiaozhi"
set "MYSQL_USER=root"
set "MYSQL_PASSWORD="

if not defined ROOT goto :eof
if not exist "%ROOT%\config\mysql.env" goto :eof

for /f "usebackq eol=# tokens=1,* delims==" %%a in ("%ROOT%\config\mysql.env") do (
  if /i "%%a"=="MYSQL_ENABLED" for /f "tokens=*" %%v in ("%%b") do set "MYSQL_ENABLED=%%v"
  if /i "%%a"=="MYSQL_HOST" for /f "tokens=*" %%v in ("%%b") do set "MYSQL_HOST=%%v"
  if /i "%%a"=="MYSQL_PORT" for /f "tokens=*" %%v in ("%%b") do set "MYSQL_PORT=%%v"
  if /i "%%a"=="MYSQL_DATABASE" for /f "tokens=*" %%v in ("%%b") do set "MYSQL_DATABASE=%%v"
  if /i "%%a"=="MYSQL_USER" for /f "tokens=*" %%v in ("%%b") do set "MYSQL_USER=%%v"
  if /i "%%a"=="MYSQL_PASSWORD" for /f "tokens=*" %%v in ("%%b") do set "MYSQL_PASSWORD=%%v"
)

if /I "%MYSQL_ENABLED%"=="true" (
  set "SPRING_PROFILES_ACTIVE=mysql"
)
