@echo off
set DIR=%~dp0
set APP_BASE_NAME=%~n0
set GRADLE_USER_HOME=%DIR%.gradle-user-home
if not exist "%GRADLE_USER_HOME%" mkdir "%GRADLE_USER_HOME%"
gradle -p "%DIR%" %*
