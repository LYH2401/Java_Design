@echo off
chcp 65001 >nul
echo ========================================
echo   校园智能服务小助手 - Docker 启动脚本
echo ========================================
echo.

echo [检查] Docker 环境...
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 请先安装 Docker Desktop
    echo        下载地址: https://www.docker.com/products/docker-desktop/
    pause && exit /b 1
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
    pause && exit /b 1
)

echo [检查] .env 文件已存在

echo.
echo [构建] 正在构建 Docker 镜像...
docker-compose build
if %errorlevel% neq 0 (
    echo [错误] Docker 镜像构建失败！
    pause && exit /b 1
)

echo.
echo [启动] 正在启动服务（MySQL + SpringBoot）...
docker-compose up -d
if %errorlevel% neq 0 (
    echo [错误] 服务启动失败！
    pause && exit /b 1
)

echo.
echo ========================================
echo   启动完成！
echo.
echo   前端页面: http://localhost:8080
echo   Swagger:  http://localhost:8080/swagger-ui.html
echo.
echo   管理命令:
echo     查看日志: docker-compose logs -f
echo     停止服务: docker-compose down
echo     重启服务: docker-compose restart
echo ========================================
pause
