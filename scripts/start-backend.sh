#!/usr/bin/env bash
# 一键拉起 5 个微服务（后台运行，日志落到 logs/）
# 依赖：MySQL 3306、Nacos 8848、Redis 6379（可用 bash scripts/start-infra.sh up 拉起）
#
# 用法：bash scripts/start-backend.sh
#
# 凭据来源（优先级从高到低）：
#   1. 已有环境变量
#   2. 项目根目录的 .env（复制 .env.example 得到，已被 .gitignore 忽略）
#   3. 下面的占位默认值 -> 请务必先改成你自己的
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

# 某些 IDE/agent 会话会注入 SERVER__PORT=0，会被 Spring 映射成 server.port=0（随机端口）
unset SERVER__PORT SERVER__HOST server__port

JAVA=${JAVA_HOME:+$JAVA_HOME/bin/java}
JAVA=${JAVA:-java}
mkdir -p logs

start() {
  nohup "$JAVA" -jar "$1" --server.port="$2" > "logs/$3.log" 2>&1 &
  echo "[start] $3 -> $2 (pid $!)"
}

start "user-service/target/user-service-1.0.0.jar" 8081 user
sleep 10
start "parking-service/target/parking-service-1.0.0.jar" 8082 parking
sleep 10
start "payment-service/target/payment-service-1.0.0.jar" 8084 payment
sleep 10
start "order-service/target/order-service-1.0.0.jar" 8085 order
sleep 12
start "gateway-service/target/gateway-service-1.0.0.jar" 8080 gateway
sleep 18

echo "--- listening ---"
if command -v netstat >/dev/null 2>&1; then
  netstat -ano -p tcp 2>/dev/null | grep LISTENING | grep -E ":(8080|8081|8082|8084|8085) " || true
else
  ss -ltn 2>/dev/null | grep -E ":(8080|8081|8082|8084|8085) " || true
fi
