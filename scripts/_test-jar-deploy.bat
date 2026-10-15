@echo off
cd /d "%~dp0.."
set ROOT=%CD%
call scripts\_read-mysql.bat
call scripts\_read-ports.bat
cd /d "%ROOT%\backend"
echo Testing jar with env from mysql.env ...
echo MYSQL_HOST=%MYSQL_HOST%
echo MYSQL_PASSWORD length check: 
if "%MYSQL_PASSWORD%"=="" (echo EMPTY) else (echo SET)
java -jar target\wuxiaozhi-backend-1.0.0.jar --spring.profiles.active=mysql --server.port=8098 -DMYSQL_HOST=%MYSQL_HOST% -DMYSQL_PORT=%MYSQL_PORT% -DMYSQL_DATABASE=%MYSQL_DATABASE% -DMYSQL_USER=%MYSQL_USER% -DMYSQL_PASSWORD=%MYSQL_PASSWORD%
