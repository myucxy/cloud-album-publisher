@echo off
setlocal
cd /d "%~dp0"
echo 云影发布打包工具
echo 默认选项是一键升版本并全量打包。
echo.
node "scripts\package-release.mjs" --interactive
set "EXIT_CODE=%ERRORLEVEL%"
echo.
if not "%EXIT_CODE%"=="0" echo 打包失败，退出码：%EXIT_CODE%
pause
exit /b %EXIT_CODE%
