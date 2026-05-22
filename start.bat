@echo off
chcp 65001 >nul
echo ========================================
echo   校园智能服务小助手 - 启动脚本
echo ========================================
echo.
echo 请选择启动模式:
echo   [1] 直接启动 (推荐, 无需Docker)
echo   [2] Docker 启动 (需要Docker Desktop)
echo   [3] 退出
echo.
set /p MODE="请输入选项 [1/2/3]: "

if "%MODE%"=="1" goto DIRECT_START
if "%MODE%"=="2" goto DOCKER_START
if "%MODE%"=="3" exit /b 0
echo 无效选项，请输入 1、2 或 3
goto END

:DIRECT_START
echo.
echo ========================================
echo   直接启动模式
echo ========================================
echo.
echo [前提] 请确保:
echo   1. MySQL 8.0 已在本地运行 (端口 3306)
echo   2. JDK 17+ 已安装
echo   3. 数据库 campus_assistant 已创建
echo   4. Maven 已安装 (如JAR不存在需重新构建)
echo.

:: 检查 .env 文件
if not exist .env (
    echo [提示] 未找到 .env 文件，正在从 .env.example 复制...
    copy .env.example .env >nul
    echo [提示] 请编辑 .env 文件，填入必要配置后重新运行本脚本
    pause
    exit /b 1
)

:: 从 .env 读取 MySQL 密码
for /f "tokens=2 delims==" %%a in ('findstr "MYSQL_ROOT_PASSWORD" .env') do set MYSQL_PASS=%%a
if "%MYSQL_PASS%"=="" (
    echo [警告] 未在 .env 中找到 MYSQL_ROOT_PASSWORD，使用默认密码 campus123
    set MYSQL_PASS=campus123
)

:: 检查 JAR 是否存在
if not exist "target\campus-assistant-1.0.0.jar" (
    echo [错误] 未找到 JAR 文件，正在用 Maven 构建...
    call mvn package -DskipTests -q
    if %errorlevel% neq 0 (
        echo [错误] Maven 构建失败！
        pause
        exit /b 1
    )
    echo [完成] Maven 构建成功
)

echo.
echo [启动] 正在启动 Spring Boot 应用...
echo         URL: http://localhost:8080
echo         Swagger: http://localhost:8080/swagger-ui.html
echo.
echo 按 Ctrl+C 停止服务
echo ========================================
echo.

:: 使用 JVM 系统属性传递密码（比环境变量更可靠）
"E:\JDK\jdk-25_windows-x64_bin\jdk-25.0.2\bin\java.exe" -Dspring.datasource.password=%MYSQL_PASS% -jar target\campus-assistant-1.0.0.jar
goto END

:DOCKER_START
echo.
echo ========================================
echo   Docker 启动模式
echo ========================================
echo.

echo [检查] Docker 环境...
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 请先安装 Docker Desktop
    echo        下载地址: https://www.docker.com/products/docker-desktop/
    pause
    exit /b 1
)
echo [通过] Docker 已安装

echo.

if not exist .env (
    echo [提示] 未找到 .env 文件，正在从 .env.example 复制...
    copy .env.example .env >nul
    echo [重要] 请编辑 .env 文件，填入你的阿里云百炼 API Key:
    echo        AI_DASHSCOPE_API_KEY=sk-your-api-key-here
    echo.
    echo 编辑完成后请重新运行本脚本。
    pause
    exit /b 1
)

echo [检查] .env 文件已存在

echo.
echo [构建] 正在构建 Docker 镜像...
docker compose build
if %errorlevel% neq 0 (
    echo [错误] Docker 镜像构建失败！
    pause
    exit /b 1
)

echo.
echo [启动] 正在启动服务...
docker compose up -d
if %errorlevel% neq 0 (
    echo [错误] 服务启动失败！
    pause
    exit /b 1
)

echo.
echo ========================================
echo   启动完成！
echo.
echo   前端页面: http://localhost:8080
echo   Swagger:  http://localhost:8080/swagger-ui.html
echo.
echo   管理命令:
echo     查看日志: docker compose logs -f
echo     停止服务: docker compose down
echo     重启服务: docker compose restart
echo ========================================

:END
pause
