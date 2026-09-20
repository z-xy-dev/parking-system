#!/usr/bin/env bash
# 一键拉起 5 个微服务（依赖：MySQL 3306 本机、Nacos 8848 + Redis 6379 容器）
# 说明：DB / Nacos 凭据写在这里，避免出现在命令行里
export PATH="/usr/bin:/bin:/mingw64/bin:$PATH"
cd "." || exit 1
mkdir -p logs

# WorkBuddy 会话注入 SERVER__PORT=0，会被 Spring 映射成 server.port=0（随机端口），必须清掉
unset SERVER__PORT SERVER__HOST server__port

export DB_USERNAME=root
export DB_PASSWORD=changeme
export NACOS_USERNAME=nacos
export NACOS_PASSWORD=nacos
export REDIS_HOST=localhost
export REDIS_PORT=6379

J="java"

start() {
  nohup "$J" -jar "$1" --server.port="$2" > "logs/$3.log" 2>&1 &
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
netstat -ano -p tcp | grep LISTENING | grep -E ":(8080|8081|8082|8084|8085) "
