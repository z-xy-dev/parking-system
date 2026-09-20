# 共享停车位系统（parking-system）

一个前后端分离的共享停车位租赁平台：业主把闲置车位挂出来，车主在线选位、预订、使用、结算付费。
后端是 Spring Cloud Alibaba 微服务，前端是 Vue 3 + Vite，基础设施（Nacos / MySQL / Redis）用 Docker Compose 一键拉起，服务统一经 Spring Cloud Gateway 入口。

![首页](docs/screenshots/01-home.png)

---

## 目录

- [功能特性](#功能特性)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [模块与端口](#模块与端口)
- [数据库设计](#数据库设计)
- [快速开始](#快速开始)
- [环境变量](#环境变量)
- [关键设计](#关键设计)
- [主要接口](#主要接口)
- [安全须知](#安全须知)
- [常见问题](#常见问题)
- [项目结构](#项目结构)
- [许可证](#许可证)

---

## 功能特性

- **用户**：注册 / 登录（JWT）、资料维护（手机号、真实姓名、车牌），角色区分 `USER` 普通车主 / `OWNER` 车位业主。
- **车位**：业主发布车位（单价、总车位数、经纬度、图文描述）、下架 / 重新上架、查看自己名下车位。
- **选位**：停车场拆成独立车位明细（`parking_spot`），下单时按编号精确占位，支持查看某个停车场实时车位占用情况。
- **订单**：创建预订、取消、开始使用、完成结算；完成订单时按实际时长 + 超时时长一次性扣款。
- **支付**：订单完成时由订单服务调用支付服务生成支付流水（微信 / 支付宝占位实现），支持结算前费用预览。

---

## 技术栈

| 层 | 选型 |
| --- | --- |
| 语言 / 运行时 | Java 17 |
| 基础框架 | Spring Boot 3.2.4 |
| 微服务 | Spring Cloud 2023.0.1、Spring Cloud Alibaba 2023.0.1.0 |
| 注册中心 | Nacos 2.5.1（单机，配置中心默认关闭） |
| 网关 | Spring Cloud Gateway + 自定义全局鉴权过滤器 |
| 服务调用 | OpenFeign + Resilience4j（熔断 / 重试 / Fallback） |
| 持久层 | MyBatis-Plus 3.5.6、MySQL 8.0 |
| 缓存 / 锁 | Redis 7（单点）、Redisson 分布式锁 |
| 认证 | JWT（jjwt 0.12.5），BCrypt 存密码 |
| 接口文档 | SpringDoc OpenAPI + Knife4j 网关聚合 |
| 前端 | Vue 3.4 + Vue Router 4 + Element Plus 2.7 + Axios + Vite 5 |
| 基础设施 | Docker Compose |

---

## 系统架构

```
                浏览器 (Vue 3, :5173)
                         │  /api  (Vite proxy → :8080)
                         ▼
                Gateway :8080  (JWT 鉴权 + 路由)
                         │  lb://  (Nacos 服务发现 + LoadBalancer)
       ┌──────────┬──────┴──────┬──────────────┐
       ▼          ▼             ▼              ▼
 user-service  parking-    order-service   payment-service
   8081        service     8085            8084
              8082
       │             │            │               │
    user_db      parking_db    order_db       payment_db   (MySQL 单库多库)
                                 │
                           Redis (幂等 / Redisson 锁 / 缓存)

  旁路：Nacos (:8848) 统一注册与发现
```

**鉴权链路**：网关 `AuthFilter` 校验 `Authorization: Bearer <jwt>`，解析后向后端透传 `X-User-Id` / `X-User-Role` / `X-User-Plate`（先清除客户端自带的同名头，防止伪造越权）。后端不再各自验签。

---

## 模块与端口

| 模块 | 说明 | 端口 |
| --- | --- | --- |
| `gateway-service` | 统一入口、路由、JWT 鉴权、Knife4j 聚合 | 8080 |
| `user-service` | 用户注册登录、资料、内部用户信息查询 | 8081 |
| `parking-service` | 车位发布 / 上下架、车位明细与占用 | 8082 |
| `order-service` | 下单、取消、结算、超时计费，编排各服务 | 8085 |
| `payment-service` | 支付流水生成与查询 | 8084 |
| `parking-common` | 统一响应体、异常、JWT 工具、Swagger 配置（非独立服务） | — |
| `parking-web` | 前端 SPA | 5173 |

基础设施默认端口：Nacos `8848`（控制台 `/nacos`），MySQL `3306`，Redis `6379`。

---

## 数据库设计

四个库按服务拆分，脚本见 [`sql/init.sql`](sql/init.sql)（增量脚本：`sql/migration_parking_spot.sql`、`sql/migration_pay_status.sql`）。

| 库 | 表 | 用途 |
| --- | --- | --- |
| `user_db` | `user` | 用户、角色、车牌 |
| `parking_db` | `parking_space` | 停车场（单价、总数 / 可用数、状态） |
| `parking_db` | `parking_image` | 车位图片 |
| `parking_db` | `parking_spot` | 独立车位明细，唯一键 `(space_id, spot_number)` + `version` 乐观锁 |
| `order_db` | `booking_order` | 订单（时长、金额、超时时长/费用、订单状态、支付状态） |
| `payment_db` | `payment_record` | 支付流水（交易号唯一、状态、支付时间） |

初始化脚本内置两个测试账号（密码均为 `123456`）：`admin`（OWNER）、`user1`（USER），以及两个广州的示例停车场，并自动生成对应车位明细。

---

## 快速开始

### 前置条件

- JDK 17、Maven 3.8+
- Node.js 18+（前端）
- Docker Desktop（跑基础设施；也可复用本机已有的 MySQL / Redis / Nacos）
- MySQL 8.0、Redis 7、Nacos 2.x 至少能连通其一

### 1. 初始化数据库

```bash
# 方式 A：用本机 MySQL
mysql -u root -p < sql/init.sql

# 方式 B：用 Docker 基础设施（首次启动会自动执行 sql/ 下脚本）
docker compose -f docker-compose.infra.yml up -d
```

> Nacos 控制台默认账号 `nacos`；compose 里 MySQL root 密码占位为 `changeme`，请改成你自己的值并同步到环境变量。

### 2. 构建后端

```bash
mvn clean package -DskipTests     # 或 mvn clean package 跑单测
```

产物为各模块 `target/<module>-1.0.0.jar`。

### 3. 启动服务

**手动启动（Windows / Linux / macOS 通用）**——先导出上一节的环境变量，然后开 5 个终端：

```bash
java -jar user-service/target/user-service-1.0.0.jar      # 8081
java -jar parking-service/target/parking-service-1.0.0.jar  # 8082
java -jar payment-service/target/payment-service-1.0.0.jar  # 8084
java -jar order-service/target/order-service-1.0.0.jar      # 8085
java -jar gateway-service/target/gateway-service-1.0.0.jar  # 8080（最后启动）
```

内存吃紧时加上 JVM 参数，或直接用命令行覆盖单个配置：

```bash
java -Xms128m -Xmx320m -XX:+UseG1GC -jar user-service/target/user-service-1.0.0.jar
java -jar user-service/target/user-service-1.0.0.jar --spring.datasource.password=你的密码
```

**Windows 想一键启动？** 仓库里有一个不含任何凭据的模板，复制后自己填：

```bat
copy start-services.example.bat start-services.bat
REM 编辑 start-services.bat 填上自己的密码，然后双击运行
```

`start-services.bat` 已被 `.gitignore` 忽略（规则 `*.bat`），所以你填进去的真实密码永远不会进仓库；模板 `start-services.example.bat` 才是入库的那份。

### 4. 启动前端

```bash
cd parking-web
npm install
npm run dev        # http://localhost:5173
```

Vite 已把 `/api` 代理到 `http://localhost:8080`。

### 5. 验证

| 地址 | 说明 |
| --- | --- |
| http://localhost:5173 | 前端 |
| http://localhost:8080/api/doc.html | Knife4j 聚合接口文档 |
| http://localhost:8848/nacos | Nacos 控制台 |
| http://localhost:8080/actuator/health/readiness | 就绪探针 |

截图示例：

| 首页 | 车位详情 | 订单详情 |
| --- | --- | --- |
| ![首页](docs/screenshots/01-home.png) | ![车位详情](docs/screenshots/02-parking-detail.png) | ![订单详情](docs/screenshots/03-order-detail.png) |

---

## 环境变量

复制 `.env.example` 为 `.env` 并按本机情况填写（`.env` 已被 `.gitignore` 忽略，切勿提交）。各服务通过 `${VAR:默认值}` 读取，也可直接导出到终端。

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `DB_HOST` / `DB_PORT` | `localhost` / `3306` | MySQL 地址 |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / `changeme` | MySQL 凭据 |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis 单点 |
| `NACOS_SERVER_ADDR` | `localhost:8848` | Nacos 地址 |
| `NACOS_USERNAME` / `NACOS_PASSWORD` | `nacos` / `changeme` | Nacos 认证 |

---

## 关键设计

**防超卖（下单占车位）：三层防护叠加**
1. Redis 幂等：同一用户对同一车位的重复提交请求直接拦截，防止手抖 / 重发造成重复下单；
2. Redisson 分布式锁 `parking:lock:space:{spaceId}:{spotNumber}`，保证跨实例互斥；
3. `parking_spot` 的 `version` 乐观锁 + 状态条件更新（`UPDATE ... WHERE status='AVAILABLE'`），杜绝并发写坏数据；`available_spots` 由车位明细实时计算，占位 / 释放走原子接口（DB 行锁兜底），保证计数与明细一致。

**服务容错**：order-service 调用 parking / payment / user 全部走 OpenFeign + Resilience4j 熔断（失败率 50% 熔断、半开探测）与重试（3 次），每个 Feign 客户端配 `FallbackFactory`，下游不可用时返回可读错误而不是 500；占位成功但后续步骤失败时自动释放车位。

**内部接口隔离**：所有 `/internal/**` 端点在网关层直接返回 403，只能由服务间 Feign 直连调用，避免外部绕过业务校验直接改车位状态或发起支付。

**网关鉴权白名单**：`/api/user/login`、`/api/user/register`、`/api/parking/list`、`/api/parking/detail` 免登录；Knife4j / Swagger 相关路径放行；其余一律校验 JWT，过期或非法返回 401（前端拦截器会自动跳转登录页）。

**支付与结算**：下单时不扣款（`pay_status=UNPAID`）；完成订单时按"预订时长 + 超时时长"计算总额，调用支付服务落流水并把订单置为 `PAID`，结算前可用 `/api/order/settle/preview/{id}` 预览费用；支付接口幂等，重复结算不产生二次扣款。

**订单状态机**：`RESERVED`（已预订）→ `USING`（使用中）→ `COMPLETED`（已完成）；`RESERVED` 也可直接 `CANCELED`（已取消）。预订后车辆到位应调用 `PUT /api/order/use/{id}` 进入 `USING`，标记车位正在使用；`complete` 允许从 `RESERVED` 或 `USING` 任一状态完成结算（超时计费以 `end_time` 与当前时间为准）。`startUse` 仅放行 `RESERVED`，且要求操作人是订单主人，避免越权或重复置位。

**鉴权头安全**：网关在透传身份前先 `remove` 客户端自带的 `X-User-Id` / `X-User-Role` / `X-User-Plate`，防止通过伪造请求头越权；车牌经 URL 编码后透传，避免中文头被按 Latin-1 解析成乱码。

---

## 主要接口

统一前缀走网关：`http://localhost:8080`，除白名单外均需 `Authorization: Bearer <token>`。

**用户** `/api/user`
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/login` | 登录返回 JWT |
| POST | `/register` | 注册 |
| PUT | `/update` | 更新资料 |
| GET | `/internal/{id}` | 内部：查用户 |

**车位** `/api/parking`
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/list` | 车位列表（免登录） |
| GET | `/detail/{id}` | 车位详情（免登录） |
| POST | `/publish` | 业主发布车位 |
| PUT | `/update` `/unpublish/{id}` `/republish/{id}` | 修改 / 下架 / 上架 |
| GET | `/owner` | 我发布的车位 |
| GET | `/spots/{spaceId}` | 车位明细占用情况 |

**订单** `/api/order`
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/create` | 创建预订 |
| PUT | `/cancel/{id}` `/use/{id}` `/complete/{id}` | 取消 / 开始使用（预订转使用中）/ 完成结算 |
| GET | `/detail/{id}` `/my` `/space` | 订单详情 / 我的订单 / 按车位查订单 |
| GET | `/settle/preview/{id}` | 结算费用预览 |
| DELETE | `/{id}` | 删除订单 |

**支付** `/api/payment`
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/records/{orderId}` | 支付流水查询 |

完整参数与模型见 http://localhost:8080/api/doc.html。

---

## 安全须知

仓库里出现的口令**全部是本地演示用示例值**，不是真实凭据，请勿直接用于任何共享或生产环境：

| 位置 | 示例值 | 说明 |
| --- | --- | --- |
| 各服务 `application.properties` | `changeme` | MySQL / Nacos 默认回退值，应通过环境变量覆盖 |
| `docker-compose.infra.yml` | `${MYSQL_ROOT_PASSWORD:-changeme}` | 未设环境变量时的回退值 |
| `sql/init.sql` | 密码 `123456` | 初始化测试账号 `admin` / `user1` |
| `start-services.example.bat` | `changeme` | 占位默认值，外部有定义时自动让位；你的 `start-services.bat` 不入库 |

- 真实配置请写入 `.env`（已被 `.gitignore` 忽略）或用环境变量注入，**不要提交**。
- 上线前至少还要做：更换 JWT 密钥（`JWT_SECRET`）、开启 Nacos 鉴权并改掉默认口令、接入真实支付网关、为数据库账号设置最小权限。

## 常见问题

**启动报端口被占用**：`docker-compose.infra.yml` 会占用 3306 / 6379 / 8848，若本机已装原生 MySQL / Redis / Nacos，先停掉它们（或改 compose 里的 published 端口）。

**服务注册不上 Nacos**：检查 `NACOS_SERVER_ADDR` 与账号密码；Nacos 2.x 除 8848 外还需放行 gRPC 端口 9848 / 9849（compose 已映射）。

**接口返回 401**：除白名单外都需带 JWT；前端 token 存在 `localStorage`，登出或过期会自动清掉并跳转 `/login`。

**接口返回 403**：说明打到了 `/internal/**`，这些端点只能服务间调用，请改走网关暴露的业务接口。

**改了代码前端没生效**：Vite 有缓存，必要时 `npm run dev -- --force` 或清浏览器缓存。

---

## 项目结构

```
parking-system/
├── docker-compose.infra.yml    # Nacos 单机 + MySQL 单库 + Redis 单点
├── sql/                        # 建库建表与增量迁移脚本
├── scripts/                    # start-infra.sh（基础设施启停）
├── gateway-service/            # 网关：路由、鉴权、文档聚合
├── user-service/               # 用户与认证
├── parking-service/            # 车位与车位明细
├── order-service/              # 订单编排（Feign + 熔断 + Redisson 锁）
├── payment-service/            # 支付流水
├── parking-common/             # 公共结果体、异常、JWT、Swagger 配置
├── parking-web/                # Vue 3 前端
├── docs/screenshots/           # 界面截图
├── start-services.example.bat  # Windows 启动脚本模板（占位值，入库）
├── start-services.bat          # 本地实际使用的那份（含真实配置，已被 .gitignore 忽略）
└── pom.xml                     # 父 POM（Java 17 / Boot 3.2.4 / Cloud 2023.0.1）
```

---

## 许可证

[MIT License](LICENSE)
