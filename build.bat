@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul

if not defined JAVA_HOME set JAVA_HOME=D:\Dev\Env\Jdk\jdk-17.0.17
set PATH=%JAVA_HOME%\bin;%PATH%

set PROJECT_NAME=cloud-album-publisher
set VERSION=1.0.0
set DIST_DIR=dist\%PROJECT_NAME%-%VERSION%
set MAVEN_REPO=%~dp0.m2\repository

echo ============================================================
echo  智控云影 ^| 打包发布脚本
echo  输出目录: %DIST_DIR%
echo ============================================================
echo.

:: ── 环境检查 ──────────────────────────────────────────────────
where java >nul 2>&1 || (echo [ERROR] 未找到 java，请先安装 JDK 17+ && exit /b 1)
where mvn  >nul 2>&1 || (echo [ERROR] 未找到 mvn，请先安装 Maven && exit /b 1)
where node >nul 2>&1 || (echo [ERROR] 未找到 node，请先安装 Node.js 18+ && exit /b 1)
where npm  >nul 2>&1 || (echo [ERROR] 未找到 npm && exit /b 1)

:: ── 清理旧产物 ────────────────────────────────────────────────
echo [1/4] 清理旧构建产物...
if exist dist rmdir /s /q dist
mkdir "%DIST_DIR%"

:: ── 前端构建 ──────────────────────────────────────────────────
echo.
echo [2/4] 构建前端...
cd frontend
call npm install --prefer-offline
if errorlevel 1 (echo [ERROR] npm install 失败 && exit /b 1)
call npm run build
if errorlevel 1 (echo [ERROR] vite build 失败 && exit /b 1)
cd ..

:: ── 将前端静态文件复制到 Spring Boot static 目录 ────────────────
echo       复制静态资源...
if not exist src\main\resources\static mkdir src\main\resources\static
xcopy /s /e /y /q frontend\dist\* src\main\resources\static\ >nul

:: ── 后端构建 ──────────────────────────────────────────────────
echo.
echo [3/4] 构建后端...
call mvn -Dmaven.repo.local="%MAVEN_REPO%" clean package -DskipTests -q
if errorlevel 1 (echo [ERROR] Maven 构建失败 && exit /b 1)

:: ── 组装发布包 ────────────────────────────────────────────────
echo.
echo [4/4] 组装发布包...

:: JAR
for %%f in (target\%PROJECT_NAME%-%VERSION%*.jar) do (
    copy /y "%%f" "%DIST_DIR%\app.jar" >nul
)

:: 启动脚本
copy /y run.bat "%DIST_DIR%\run.bat" >nul

:: 配置文件（让用户可以直接覆盖）
mkdir "%DIST_DIR%\config"
copy /y src\main\resources\application.yml      "%DIST_DIR%\config\application.yml" >nul
copy /y src\main\resources\application-dev.yml  "%DIST_DIR%\config\application-dev.yml" >nul
copy /y src\main\resources\application-prod.yml "%DIST_DIR%\config\application-prod.yml" >nul

:: 数据目录占位
mkdir "%DIST_DIR%\data"
echo This directory stores H2 database files.> "%DIST_DIR%\data\.gitkeep"

:: 日志目录占位
mkdir "%DIST_DIR%\logs"
echo This directory stores application logs.> "%DIST_DIR%\logs\.gitkeep"

echo.
echo ============================================================
echo  构建完成！
echo  发布包位于: %DIST_DIR%\
echo  运行方式  : cd %DIST_DIR% ^&^& run.bat
echo ============================================================
endlocal
