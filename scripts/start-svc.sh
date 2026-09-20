#!/usr/bin/env bash
# 前台常驻方式启动单个微服务：java 不退出，任务不结束，进程就不会被回收
# 用法：bash scripts/start-svc.sh <jar相对路径> <端口>
export PATH="/usr/bin:/bin:/mingw64/bin:$PATH"
cd "." || exit 1

# WorkBuddy 会话注入 SERVER__PORT=0 会被 Spring 映射成 server.port=0（随机端口）
unset SERVER__PORT SERVER__HOST server__port

export DB_USERNAME=root
export DB_PASSWORD=$(cat scripts/secrets.txt | tr -d '\r\n')
export NACOS_USERNAME=nacos
export NACOS_PASSWORD="$DB_PASSWORD"
export REDIS_HOST=localhost
export REDIS_PORT=6379

exec "java" \
  -jar "$1" \
  --server.port="$2" \
  --spring.cloud.nacos.discovery.ip=127.0.0.1
