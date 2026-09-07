# 共享停车位系统 · 上线部署文档

> 本文档面向**生产 / 公网部署**场景，覆盖从服务器准备、数据库初始化、服务启动到 Nginx 反向代理的完整流程。
> 本地开发与快速启动说明见 Git 历史中的原 README。

基于 Spring Cloud Alibaba 微服务架构的共享停车位预订平台，实现用户注册登录、车位发布与搜索、订单管理、模拟支付等完整业务闭环。

## 一、技术栈

| 层面 | 技术 |
|------|------|
| 微服务框架 | Spring Boot 3.2 + Spring Cloud 2023 + Spring Cloud Alibaba 2023 |
| 注册中心 | Nacos 2.x/3.x（standalone） |
| API 网关 | Spring Cloud Gateway（JWT 统一鉴权） |
| 服务调用 | OpenFeign + LoadBalancer |
| 分布式锁 | Redisson（Redis 分布式锁） |
| 熔断降级 | Resilience4j（Feign 熔断 + Fallback） |
| 数据库 | MySQL 8.0（每服务独立数据库） |
| ORM | MyBatis-Plus 3.5 |
| 认证 | JWT + BCrypt |
| 前端 | Vue 3 + Element Plus + Vite |

## 二、服务拓扑

| 服务 | 端口 | 说明 |
|------|------|------|
| gateway-service | 8080 | API 网关（路由分发、JWT 鉴权） |
| user-service | 8081 | 用户服务（注册、登录、角色管理） |
| parking-service | 8082 | 车位服务（发布、搜索、管理） |
| payment-service | 8084 | 支付服务（模拟支付） |
| order-service | 8085 | 订单服务（预订、取消、完成） |
| parking-web | 5173(dev) / 静态(prod) | Vue 3 前端 |

```
客户端 → Nginx(:80) → Gateway(:8080) → 各业务服务(Nacos 注册发现)
                                        ├── Redis(:6379) 分布式锁/幂等
                                        └── MySQL(:3306) 每服务独立库
```

## 三、功能清单

- **用户体系**：注册绑定车牌、USER/OWNER 双角色、JWT 网关统一鉴权
- **车位管理**：业主发布车位自动生成 N 个独立车位、关键词搜索、可视化选位（前端网格 + 5s 自动刷新）
- **订单业务**：一人一单防恶意占位、三层并发防超卖（Redis 幂等 → Redisson 分布式锁 → DB 原子更新）、超时按 1.5 倍单价计费
- **支付结算**：下单只占位不扣款、出场一次性结算、支付幂等防重复扣款（模拟实现，未接真实渠道）
- **容错**：Feign 熔断降级、Redis 异常自动降级为纯 DB 流程、占位失败自动补偿释放

## 四、生产环境要求

| 软件 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | 推荐 Temurin / OpenJDK 17 |
| Maven | 3.8+ | 仅构建时需要 |
| MySQL | 8.0+ | 生产建议云 RDS 或主从 |
| Redis | 6.x+ | 生产建议云 Redis 或哨兵版 |
| Nacos | 2.x/3.x | standalone 模式，3.x 控制台端口 8083 |
| Node.js | 18+ | 仅构建前端时需要 |
| Nginx | 1.2x | 反向代理 + 静态资源托管 |

**服务器最低配置（单机部署）**：2C4G，40G 磁盘；公网开放 80/443。

## 五、部署流程

### 5.1 初始化数据库

将 `sql/` 目录上传到服务器，执行：

```bash
mysql -uroot -p --default-character-set=utf8mb4 < sql/init.sql
```

脚本创建 `user_db` / `parking_db` / `order_db` / `payment_db` 四个库及表结构，并插入测试数据。

> 已有存量数据时，按需执行 `sql/migration_parking_spot.sql`（独立车位明细表）、`sql/migration_pay_status.sql`（支付状态字段）。

### 5.2 配置生产环境变量

所有敏感配置均通过环境变量注入，源码默认值为占位符，**生产必须覆盖**：

```bash
# 数据库
export DB_HOST=127.0.0.1
export DB_PORT=3306
export DB_USERNAME=root
export DB_PASSWORD='你的强密码'

# Redis
export REDIS_HOST=127.0.0.1
export REDIS_PORT=6379

# Nacos
export NACOS_HOST=127.0.0.1
export NACOS_PORT=8848
export NACOS_PASSWORD='你的Nacos密码'

# JWT 密钥（生产必须改为 ≥32 字节随机字符串，否则 token 可被伪造）
export JWT_SECRET='你的32位以上随机密钥'
```

> ⚠️ **安全提示**：所有服务（gateway/user/parking/order/payment）必须使用**同一个** `JWT_SECRET`，token 才能互通；不同服务不要混用密钥。

### 5.3 构建后端

```bash
# 本机构建后上传 jar，或直接在服务器构建
mvn clean package -DskipTests
```

产物：各服务 `target/*.jar`。

### 5.4 构建前端

```bash
cd parking-web
npm install
npm run build
# 产物在 parking-web/dist/，上传到服务器，由 Nginx 托管
```

### 5.5 启动后端服务

按顺序启动（每服务间隔 3-5 秒，确保注册到 Nacos 后再启动下一个）：user → parking → payment → order → gateway（网关最后启动）。

**方式 A：nohup 脚本（简单）**

```bash
#!/bin/bash
# start-prod.sh
export DB_PASSWORD='你的强密码' NACOS_PASSWORD='你的Nacos密码' JWT_SECRET='你的32位以上随机密钥'

nohup java -jar user-service/target/user-service-1.0.0.jar    --server.port=8081 >> logs/user.log 2>&1 &
nohup java -jar parking-service/target/parking-service-1.0.0.jar --server.port=8082 >> logs/parking.log 2>&1 &
nohup java -jar payment-service/target/payment-service-1.0.0.jar --server.port=8084 >> logs/payment.log 2>&1 &
nohup java -jar order-service/target/order-service-1.0.0.jar  --server.port=8085 >> logs/order.log 2>&1 &
sleep 15
nohup java -jar gateway-service/target/gateway-service-1.0.0.jar --server.port=8080 >> logs/gateway.log 2>&1 &
```

**方式 B：systemd 托管（推荐生产）**

以 order-service 为例，`/etc/systemd/system/parking-order.service`：

```ini
[Unit]
Description=Parking Order Service
After=network.target

[Service]
User=root
Environment=DB_HOST=127.0.0.1
Environment=DB_PORT=3306
Environment=DB_USERNAME=root
Environment=DB_PASSWORD=你的强密码
Environment=REDIS_HOST=127.0.0.1
Environment=REDIS_PORT=6379
Environment=NACOS_HOST=127.0.0.1
Environment=NACOS_PORT=8848
Environment=NACOS_PASSWORD=你的Nacos密码
Environment=JWT_SECRET=你的32位以上随机密钥
ExecStart=/usr/local/jdk-17/bin/java -jar /opt/parking/order-service-1.0.0.jar --server.port=8085
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
systemctl daemon-reload
systemctl enable --now parking-order
# 其余服务（user/parking/payment/gateway）按同样方式创建
```

### 5.6 Nginx 反向代理

前端静态资源 + API 统一入口：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 前端静态资源
    root /opt/parking-web/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 反代到网关
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

```bash
nginx -t && nginx -s reload
```

> 生产建议：配置 HTTPS（Let's Encrypt / 云证书），并在网关/防火墙层限制仅 80/443 对外暴露，8080-8085 端口不对公网开放。

### 5.7 访问验证

- 前端：`http://your-domain.com/`
- 接口文档：`http://your-domain.com/api/doc.html`（Knife4j，经网关聚合）
- Nacos 控制台：`http://server-ip:8083/nacos`（3.x）确认 5 个服务均在线

测试账号（仅演示环境，**上线后务必修改或删除**）：

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | 123456 | 业主 |
| user1 | 123456 | 普通用户 |

## 六、环境变量一览

| 环境变量 | 默认值 | 说明 |
|----------|--------|------|
| `DB_HOST` | localhost | MySQL 地址 |
| `DB_PORT` | 3306 | MySQL 端口 |
| `DB_USERNAME` | root | MySQL 用户名 |
| `DB_PASSWORD` | changeme | MySQL 密码（生产必改） |
| `REDIS_HOST` | localhost | Redis 地址 |
| `REDIS_PORT` | 6379 | Redis 端口 |
| `NACOS_HOST` | localhost | Nacos 地址 |
| `NACOS_PORT` | 8848 | Nacos 服务发现端口 |
| `NACOS_PASSWORD` | changeme | Nacos 密码（3.x，生产必改） |
| `JWT_SECRET` | 公开默认值 | JWT 签名密钥（生产必改，≥32 字节随机串） |

## 七、运维命令

```bash
# 健康检查（每个服务均暴露）
curl http://127.0.0.1:8085/actuator/health

# 查看日志
tail -f logs/order.log

# 优雅停止（systemd）
systemctl stop parking-order

# 一键停止全部 Java 服务
ps -ef | grep 'service/target' | grep -v grep | awk '{print $2}' | xargs -r kill
```

## 八、常见问题

| 现象 | 原因与处理 |
|------|-----------|
| 服务连不上数据库 | 检查 `DB_PASSWORD` 是否已注入且与 MySQL 实际密码一致 |
| 网关路由全部失效 | 各服务是否已注册到 Nacos；服务端口是否被 `SERVER__PORT` 等环境变量干扰 |
| 前端页面 404 | Nginx 未配置 `try_files` 回退到 index.html（前端为 History 路由） |
| 接口文档打不开 | 确认网关已启动且服务已注册，Knife4j 路径经网关聚合 |
| 登录 token 无效 | 检查所有服务 `JWT_SECRET` 是否一致 |
| 中文数据乱码 | 初始化 SQL 需带 `--default-character-set=utf8mb4` |

## 九、上线检查清单

- [ ] `DB_PASSWORD` / `NACOS_PASSWORD` 已改为强密码（非 changeme）
- [ ] `JWT_SECRET` 已改为 ≥32 字节随机字符串，且 5 个服务一致
- [ ] 测试账号已修改或删除
- [ ] 8080-8085 端口未对公网开放（仅开放 80/443）
- [ ] HTTPS 已配置
- [ ] 数据库已定时备份（建议每日全量 + binlog）
- [ ] 支付为模拟实现，接入真实渠道前请替换 `PaymentChannel` 实现
