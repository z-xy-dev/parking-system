#!/usr/bin/env bash
# ============================================================================
# 高可用启动脚本 (Git Bash)
# 为每个后端服务启动 2 个实例、网关启动 2 个实例，全部注册到同一个 Nacos，
# 由 Nacos + 网关 LoadBalancer 自动做负载均衡与故障转移。
# 启动后用 Nginx(nginx/nginx.conf) 在 :80 提供网关统一 VIP，消除网关单点。
#
# 前置：MySQL(3306) / Redis(6379) / Nacos(8848) 已在运行。
# 用法：  cd <项目根目录>
#         bash scripts/start-ha.sh
# ============================================================================
set -e
cd "$(dirname "$0")/.."
ROOT=$(pwd)
LOGDIR="$ROOT/logs"; mkdir -p "$LOGDIR"

# 先停掉本脚本之前启动的实例，避免端口冲突
powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -like '*parking-system*' -or \$_.CommandLine -like '*-jar *service/target*' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }" 2>/dev/null || true
sleep 3

# 单机跑 10 个实例，控制堆内存
JVM="-Xms128m -Xmx320m -XX:+UseG1GC"
ENV="env -u SERVER__PORT -u SERVER__HOST"

# 校验 jar 是否都已构建（用相对路径，避免 Windows 版 java 不识别 /c/... 绝对路径）
for m in gateway-service user-service parking-service payment-service order-service; do
  if [ ! -f "$m/target/$m-1.0.0.jar" ]; then
    echo "缺少 $m/target/$m-1.0.0.jar，请先 mvn 打包"; exit 1
  fi
done

start() {  # name port  (jar 用相对路径：Windows 版 java 无法识别 /c/... 这样的绝对路径)
  local name="$1" port="$2"
  local jar="$name/target/$name-1.0.0.jar"
  $ENV nohup java $JVM -jar "$jar" --server.port="$port" > "$LOGDIR/$name-$port.log" 2>&1 &
  echo "started $name on :$port (pid $!)"
}

# ---- 网关 x2 (网关高可用，前置 Nginx 做 VIP) ----
start gateway-service 8080
start gateway-service 8086
# ---- user-service x2 ----
start user-service 8081
start user-service 8091
# ---- parking-service x2 ----
start parking-service 8082
start parking-service 8092
# ---- payment-service x2 ----
start payment-service 8084
start payment-service 8094
# ---- order-service x2 (Redisson 分布式锁保证跨实例不超卖) ----
start order-service 8085
start order-service 8095

echo "=== 等待 Nacos 注册与健康检查 (约 45s) ==="
sleep 45

echo "=== Nacos 各服务实例端口 ==="
for s in user-service parking-service payment-service order-service gateway-service; do
  echo -n "$s -> "
  curl -s --max-time 6 "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=DEFAULT_GROUP@@$s" \
    | grep -o '"port":[0-9]*' | tr '\n' ' '; echo
done

echo "=== 健康检查探针 (readiness 应全部 UP) ==="
for p in 8080 8086 8081 8091 8082 8092 8084 8094 8085 8095; do
  echo -n ":$p -> "; curl -s --max-time 5 "http://localhost:$p/actuator/health/readiness" \
    | grep -o '"status":"[A-Z]*"' | head -1; echo
done

echo
echo "=== 网关高可用 VIP：用 Nginx 在 :80 统一入口 ==="
echo "  若已安装 Nginx(Windows 版)，在项目管理目录执行："
echo "    nginx -p nginx -c nginx/nginx.conf"
echo "  验证：  curl -s http://localhost/api/parking/list   (走 80 -> 任一网关 -> 后端 LB)"
echo "本机直接验证也可分别打两个网关：:8080 与 :8086"
