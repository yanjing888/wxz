@echo off
setlocal
node "%~dp0test-mysql-connect.mjs"
if errorlevel 1 echo Check MySQL settings in config\application.yml.
pause
