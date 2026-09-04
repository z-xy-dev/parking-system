#!/usr/bin/env bash
# 停车系统 - 基础设施集群启停（Nacos×3 / MySQL 主从 / Redis 哨兵）
# 用法:
#   bash scripts/start-infra.sh up      # 启动并等待就绪
#   bash scripts/start-infra.sh down    # 停止并移除容器（保留数据卷）
#   bash scripts/start-infra.sh logs    # 跟踪日志
set -e
cd "$(dirname "$0")/.."
COMPOSE="docker-compose.infra.yml"

case "$1" in
  up)
    echo ">>> 启动基础设施集群（首次会拉取镜像，可能较慢）"
    docker compose -f "$COMPOSE" up -d
    echo ">>> 等待组件就绪 (约 30s)..."
    sleep 30
    echo ">>> Nacos 集群健康:"
    curl -s --max-time 6 "http://localhost:8848/nacos/v1/ns/operator/cluster/health" || echo "(nacos 未就绪，稍后重试)"
    echo
    echo ">>> MySQL 主从状态:"
    docker exec -i mysql-master mysql -uroot -p"${MYSQL_ROOT_PASSWORD:-changeme}" -e "SHOW REPLICAS\G" 2>/dev/null || echo "(mysql 未就绪)"
    echo ">>> Redis 哨兵主节点:"
    docker exec -i redis-sentinel-1 redis-cli -p 26379 sentinel get-master-addr-by-name mymaster 2>/dev/null || echo "(sentinel 未就绪)"
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
