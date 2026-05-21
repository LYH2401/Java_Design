#!/bin/bash
# ============================================
# 校园智能服务小助手 - Docker 启动脚本 (Mac/Linux)
# ============================================
set -e

echo "========================================"
echo "  校园智能服务小助手 - Docker 启动脚本"
echo "========================================"
echo ""

echo "[检查] Docker 环境..."
if ! command -v docker &> /dev/null; then
    echo "[错误] 请先安装 Docker"
    echo "       下载地址: https://www.docker.com/products/docker-desktop/"
    exit 1
fi
echo "[通过] Docker 已安装"

echo ""

if [ ! -f .env ]; then
    echo "[提示] 未找到 .env 文件，正在从 .env.example 复制..."
    cp .env.example .env
    echo "[重要] 请编辑 .env 文件，填入你的阿里云百炼 API Key:"
    echo "       AI_DASHSCOPE_API_KEY=sk-your-api-key-here"
    echo ""
    echo "编辑完成后请重新运行本脚本。"
    exit 1
fi

echo "[检查] .env 文件已存在"

echo ""
echo "[构建] 正在构建 Docker 镜像..."
docker-compose build

echo ""
echo "[启动] 正在启动服务（MySQL + SpringBoot）..."
docker-compose up -d

echo ""
echo "========================================"
echo "  启动完成！"
echo ""
echo "  前端页面: http://localhost:8080"
echo "  Swagger:  http://localhost:8080/swagger-ui.html"
echo ""
echo "  管理命令:"
echo "    查看日志: docker-compose logs -f"
echo "    停止服务: docker-compose down"
echo "    重启服务: docker-compose restart"
echo "========================================"
