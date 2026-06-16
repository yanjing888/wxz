@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

cd /d "%~dp0.."
set "ROOT=%CD%"
call "%~dp0_read-ports.bat"

if defined MAVEN_HOME (
  set "MVN=%MAVEN_HOME%\bin\mvn.cmd"
) else (
  set "MVN=mvn"
)

echo 配置端口：后端 %BACKEND_PORT%，前端 %FRONTEND_PORT%（%ROOT%\config\ports.env）
echo 正在确保配置端口可用（释放占用该端口的其他进程）...
call "%~dp0_free-port.bat" %BACKEND_PORT% 后端
call "%~dp0_free-port.bat" %FRONTEND_PORT% 前端
timeout /t 1 /nobreak >nul

echo 启动后端...
start "物小智-后端" cmd /k "cd /d "%ROOT%\backend" && set BACKEND_PORT=%BACKEND_PORT% && "%MVN%" spring-boot:run -Dspring-boot.run.arguments=--server.port=%BACKEND_PORT%"

timeout /t 2 /nobreak >nul

echo 启动前端...
start "物小智-前端" cmd /k "cd /d "%ROOT%\frontend" && npm run dev -- --host 0.0.0.0 --port %FRONTEND_PORT%"

echo.
echo 已启动：
echo   前端 http://localhost:%FRONTEND_PORT%/
echo   后端 http://localhost:%BACKEND_PORT%/
exit /b 0
