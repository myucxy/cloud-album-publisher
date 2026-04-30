@echo off
setlocal
cd /d "%~dp0"

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-docker.ps1" %*
exit /b %ERRORLEVEL%
