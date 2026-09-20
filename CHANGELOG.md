# 变更记录

本项目采用 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 格式，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [1.1.0] - 2026-09-20

### 变更（对齐简历 / 降级为单实例演示版）
- 移除以「企业级高可用」为目标的演示外壳，降级为单机 / 单实例演示版，使项目复杂度与简历描述一致、便于讲解：
  - 基础设施 `docker-compose.infra.yml`：Nacos 3 节点集群 → 单机（内置 Derby 存储）；MySQL GTID 主从 → 单库；Redis 哨兵（1 主 2 从 3 哨兵）→ 单点。
  - 删除高可用启动脚本 `scripts/start-ha.sh` / `scripts/stop-ha.ps1` 与 Nginx 网关 VIP（`nginx/`）；`scripts/start-infra.sh` 简化为单实例启停。
  - `README.md`：移除「高可用部署」章节、双实例端口、哨兵 / 集群环境变量与相关常见问题；架构图、模块端口、技术栈、项目结构统一改为单实例。
  - `.env.example`：移除 Redis 哨兵相关变量。
- 业务代码保持不变：仍为 5 微服务 + 网关 + Vue 前端，三层防超卖（Redis 幂等 → Redisson 分布式锁 → DB 条件更新）、OpenFeign + Resilience4j 熔断降级、下单占位 / 完成结算 / 超时 1.5 倍计费 / 支付幂等，均与简历一致。

## [未发布]

### 新增
- 订单状态机补完：`order-service` 新增 `PUT /api/order/use/{id}` 开始使用接口（`startUse`），把 `RESERVED` 订单转为 `USING`，闭合此前 `USING` 成为"死状态"的缺口；`complete` 仍兼容从 `RESERVED` / `USING` 两种状态完成结算。接口含操作人身份校验与状态校验，越权或非 `RESERVED` 状态会被拦截。
- `order-service` 单测补充 `startUse` 覆盖：成功流转、越权拒绝、非 `RESERVED` 状态拒绝。

### 文档
- 重写 `README.md`：功能特性、技术栈、架构图、模块与端口、数据库设计、快速开始、高可用部署、环境变量、关键设计、接口清单、常见问题。
- `README.md` 接口清单补充 `PUT /use/{id}`；关键设计新增「订单状态机」一节，明确 `RESERVED → USING → COMPLETED` 流转与 `CANCELED` 分支。
- 新增 `.editorconfig`；`.gitattributes` 补充 `*.bat` / `*.cmd` 强制 CRLF。

### 修复
- 两个启动脚本（`start-services.bat` / `start-services.example.bat`）统一为纯 ASCII 注释 + CRLF 换行：cmd 按 ANSI(GBK) 解析 bat，UTF-8 中文注释会被咬碎成乱码"命令"导致脚本报错。
- **车位可用数漂移修复**：移除旧的手工 `decrement` / `increment` 内部接口（`parking-service` 与 order 侧 Feign / Fallback），`available_spots` 改为永远由 `parking_spot` 明细经 `syncAvailableCount` 实查重算的派生值，杜绝"计数与车位明细脱钩"。`order-service` 释放车位时对无编号的旧订单改走 `sync-available` 重算，而非盲 +1；新增 `PUT /api/parking/internal/sync-available/{spaceId}` 内部端点。`parking-service` 单测移除对旧接口的 3 个用例。

### 架构决策（本期保留未动）
- **MySQL 主从未做读写分离**：维持原状。理由：① 本机离线 Maven 仓库不含 `dynamic-datasource-spring-boot-starter`，引入新依赖会直接导致构建失败；② 本系统每个微服务各持独立库，GTID 主从在此拓扑中的定位是**高可用故障切换**（可提升级为主库），而非读流量卸载，逐服务引入读写分离配置收益有限且显著增大配置面与出错面。若后续确有读扩展需求，再按服务维度接入 dynamic-datasource + `@DS` 路由，属独立增量任务。

### 安全
- 启动脚本拆成两份：`start-services.example.bat`（纯占位值，**入库**，用 `%~dp0` 定位、环境变量优先）与 `start-services.bat`（本机真实配置，**不再入库**）。`.gitignore` 由白名单 `!start-services.bat` 改为 `!start-services.example.bat`，`*.bat` 默认忽略。
- README 的启动说明改为以「手动 `java -jar` 启动」为主，一键脚本只作为可选的复制模板，避免引导他人把真实密码写进入库文件。

## [1.0.0] - 2026-09-07

### 新增
- 后端 5 个微服务：`gateway-service` / `user-service` / `parking-service` / `order-service` / `payment-service`，基于 Spring Boot 3.2.4 + Spring Cloud Alibaba 2023.0.1.0，Nacos 注册发现。
- 网关统一鉴权：JWT 校验 + 身份头透传（透传前清除客户端伪造头）、`/internal/**` 对外 403。
- 车位模型：停车场 + 独立车位明细，下单精确占位；Redisson 分布式锁 + 乐观锁防超卖。
- 订单编排：OpenFeign 调用 + Resilience4j 熔断重试 + FallbackFactory 降级。
- 支付与结算：完成订单时按「预订时长 + 超时时长」一次性扣款，支持结算前费用预览。
- 前端 `parking-web`：Vue 3 + Element Plus，登录注册 / 车位列表与详情 / 下单 / 我的订单 / 我的车位。
- 高可用方案：Nacos 3 节点 + MySQL GTID 主从 + Redis 哨兵（`docker-compose.infra.yml`），每服务双实例（`scripts/start-ha.sh`），Nginx 网关 VIP。
- GitHub Actions CI：JDK 17 + `mvn -B clean package -DskipTests`。

### 修复
- `sql/init.sql` 补齐 `user.car_plate` 列。
- 网关响应中文乱码：properties 强制 UTF-8、车牌头 URL 编码透传。
- 补充 JWT 使用安全提示。

### 变更
- 启动脚本纳入版本控制（当时入库的是 `start-services.bat`；后续已改为只入库占位模板，见「未发布」）。
