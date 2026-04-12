@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul

if not defined JAVA_HOME set JAVA_HOME=D:\Dev\Env\Jdk\jdk-17.0.17
set PATH=%JAVA_HOME%\bin;%PATH%

:: ── 可在此处修改默认值，也可以通过环境变量覆盖 ────────────────────
if not defined SPRING_PROFILES_ACTIVE set SPRING_PROFILES_ACTIVE=dev
if not defined SERVER_PORT               set SERVER_PORT=8080
if not defined JVM_OPTS                  set JVM_OPTS=-Xms256m -Xmx512m

:: ── 检查 JAR ────────────────────────────────────────────────────
if not exist "%~dp0app.jar" (
    echo [ERROR] 未找到 app.jar，请先运行 build.bat 生成发布包
    pause
    exit /b 1
)

:: ── 检查 JRE ────────────────────────────────────────────────────
where java >nul 2>&1
if errorlevel 1 (
    echo [ERROR] 未找到 java.exe，请安装 JRE 17+
    pause
    exit /b 1
)

echo ============================================================
echo  智控云影 · 启动中
echo  Profile : %SPRING_PROFILES_ACTIVE%
echo  Port    : %SERVER_PORT%
echo  JVM     : %JVM_OPTS%
echo ============================================================
echo  访问地址 : http://localhost:%SERVER_PORT%
echo  Swagger  : http://localhost:%SERVER_PORT%/swagger-ui.html
echo  H2 控制台: http://localhost:%SERVER_PORT%/h2-console  (dev模式)
echo  按 Ctrl+C 停止服务
echo ============================================================
echo.

:: ── 将 config/ 子目录作为外部配置源，优先级高于 JAR 内部配置 ────
java %JVM_OPTS% ^
  -jar "%~dp0app.jar" ^
  --spring.profiles.active=%SPRING_PROFILES_ACTIVE% ^
  --server.port=%SERVER_PORT% ^
  --spring.config.additional-location=optional:file:%~dp0config/

endlocal
