#!/usr/bin/env bash
# 停车系统 - 本地基础设施启停（Nacos 单机 / MySQL 单库 / Redis 单点）
# 用法:
#   bash scripts/start-infra.sh up      # 启动并等待就绪
#   bash scripts/start-infra.sh down    # 停止并移除容器（保留数据卷）
#   bash scripts/start-infra.sh logs    # 跟踪日志
set -e
cd "$(dirname "$0")/.."
COMPOSE="docker-compose.infra.yml"

case "$1" in
  up)
    echo ">>> 启动本地基础设施（首次会拉取镜像，可能较慢）"
    docker compose -f "$COMPOSE" up -d
    echo ">>> 等待组件就绪 (约 20s)..."
    sleep 20
    echo ">>> Nacos 控制台: http://localhost:8848/nacos"
    echo ">>> MySQL / Redis 已就绪（应用库由 sql/ 下脚本自动初始化）"
    ;;
  down)
    docker compose -f "$COMPOSE" down
    ;;
  logs)
    docker compose -f "$COMPOSE" logs -f
    ;;
  *)
    echo "用法: bash scripts/start-infra.sh {up|down|logs}"
    exit 1
    ;;
esac
