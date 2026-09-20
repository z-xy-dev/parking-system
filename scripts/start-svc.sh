#!/usr/bin/env bash
# 前台常驻方式启动单个微服务（进程不退出，便于在终端里直接看日志）
# 用法：bash scripts/start-svc.sh <jar相对路径> <端口>
#   例：bash scripts/start-svc.sh order-service/target/order-service-1.0.0.jar 8085
set -e
cd "$(dirname "$0")/.."

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi
export DB_HOST=${DB_HOST:-localhost}
export DB_PORT=${DB_PORT:-3306}
export DB_USERNAME=${DB_USERNAME:-root}
export DB_PASSWORD=${DB_PASSWORD:-changeme}
export NACOS_SERVER_ADDR=${NACOS_SERVER_ADDR:-localhost:8848}
export NACOS_USERNAME=${NACOS_USERNAME:-nacos}
export NACOS_PASSWORD=${NACOS_PASSWORD:-changeme}
export REDIS_HOST=${REDIS_HOST:-localhost}
export REDIS_PORT=${REDIS_PORT:-6379}

# 某些会话会注入 SERVER__PORT=0，会被 Spring 映射成 server.port=0（随机端口）
unset SERVER__PORT SERVER__HOST server__port

JAVA=${JAVA_HOME:+$JAVA_HOME/bin/java}
JAVA=${JAVA:-java}

exec "$JAVA" \
  -jar "$1" \
  --server.port="$2" \
  --spring.cloud.nacos.discovery.ip=127.0.0.1
