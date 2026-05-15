@echo off
chcp 65001 >nul
setlocal EnableExtensions
cd /d "%~dp0"
node "scripts\package-release.mjs" --interactive
set "EXIT_CODE=%ERRORLEVEL%"
echo.
if not "%EXIT_CODE%"=="0" echo Package failed, exit code: %EXIT_CODE%
echo Press any key to continue...
pause >nul
exit /b %EXIT_CODE%
