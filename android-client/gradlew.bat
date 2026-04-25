@echo off
set "DIR=%~dp0"
set "APP_BASE_NAME=%~n0"
set "GRADLE_USER_HOME=%DIR%.gradle-user-home"
set "LOCAL_GRADLE=%DIR%.tools\gradle-8.13-clean\bin\gradle.bat"
if not exist "%GRADLE_USER_HOME%" mkdir "%GRADLE_USER_HOME%"
if exist "%LOCAL_GRADLE%" (
  call "%LOCAL_GRADLE%" -p "%DIR%." %*
) else (
  gradle -p "%DIR%." %*
)
