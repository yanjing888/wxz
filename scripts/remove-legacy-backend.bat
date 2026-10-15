@echo off
rem 若 backend\scripts 被旧 Java/Python 进程占用，先结束相关进程再运行本脚本。
cd /d "%~dp0.."
if not exist "backend" (
  echo backend 目录已不存在。
  exit /b 0
)
echo Removing leftover backend ...
rmdir /s /q "backend" 2>nul
if exist "backend" (
  echo 仍有文件被占用，请关闭旧后端后重试。
  exit /b 1
)
echo Done.
