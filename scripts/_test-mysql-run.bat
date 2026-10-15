@echo off
cd /d "%~dp0..\backend"
set MYSQL_ENABLED=true
set MYSQL_HOST=188.18.52.54
set MYSQL_PORT=3306
set MYSQL_DATABASE=wuxiaozhi
set MYSQL_USER=root
set MYSQL_PASSWORD=root12345678
java -jar target\wuxiaozhi-backend-1.0.0.jar --spring.profiles.active=mysql --server.port=8099
