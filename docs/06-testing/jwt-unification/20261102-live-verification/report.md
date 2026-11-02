# 未验证事项收口报告：真实环境验证

- 范围：方案 v3.1 各批次报告 §5 中标注的"未验证项"，逐项做真实环境验证
- 执行日期：2026-11-02
- 环境：macOS、JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`、Docker（容器可用）
- 状态：**4 项已收口，剩余项按 §4 如实保留**

## 1. 已收口项

### 1.1 HTTP/Webhook 连接器真实端点联调（原 M5-E2 未验证项）

新增 `HttpConnectorLiveEndpointTest`：以 JDK 内置 `HttpServer` 启动**真实 HTTP 服务**（真实 TCP + WebClient 网络栈），断言 **6/6 通过**：

| 用例 | 验证事实 |
| --- | --- |
| bearer 认证 | 认证头在真实请求中送达服务端（服务端侧快照断言），JSON 响应正确解析 |
| basic + 自定义头 | `Authorization: Basic ...` 与 `X-Custom` 均送达；201 状态正常处理 |
| GET 查询参数 | payload 拍平为查询参数 |
| 非 2xx | 500 响应映射为 `BusinessException` |
| healthCheck | 走配置的 `healthPath` |
| 不可达端点 | 异常传播，**不静默成功** |

### 1.2 EMQX 真实 MQTT 端到端（原 M5-E3 未验证项）

启动真实 **EMQX 5.8.6** 容器（`docker run -p 1883:1883 emqx/emqx:5.8`）后执行 `IotMqttBrokerLiveTest`，**3/3 通过（未跳过）**：

| 用例 | 验证事实 |
| --- | --- |
| 设备上行往返 | 真实 broker 上发布 → 订阅方收到**完整载荷**（QoS 1） |
| 租户 topic 隔离 | **租户 A 收不到租户 B 的消息**（真实 2 秒观察窗口内无泄漏）；命名空间策略双向判定正确 |
| 共享订阅 | `$share/{group}/apex/+/+/up/#` 可消费任意设备上行（水平扩展路径可用） |

> 测试采用 `assumeTrue` 可跳过设计：broker 不可达时跳过而非失败，符合方案"dev/测试档可内嵌或跳过"口径。

### 1.3 遥测上行吞吐基线（原 M5-E3 基线项）

`IotMqttThroughputBaselineTest` 在真实 EMQX 上测得基线：

```
【吞吐基线】broker=127.0.0.1:1883, 消息数=1000, QoS=1, 耗时=3.022s, 吞吐=331 msg/s
```

- 1000 条消息 **0 丢失**（功能性断言）
- 本机单连接单订阅方基线约 **331 msg/s**；仅作容量规划参考，**不构成 SLA 承诺**（未做多连接/多订阅方/更大消息体压测）

### 1.4 多租户隔离契约验证（原 M2-B7 未验证项）

新增 `TenantSqlRewriteDialectTest`，**5/5 通过**：

| 用例 | 验证事实 |
| --- | --- |
| 租户字面量 | 有上下文时返回当前租户 `'tenant-a'` / `'tenant-b'`（SQL 改写输入源头正确） |
| fail-closed | 上下文缺失或空白时**抛异常**，不回退默认租户、不放行全量 |
| 白名单语义 | 平台表（`sys_menu`/`sys_permission`/`sys_dict`/`sys_tenant`/`sys_tenant_package`/`sys_config`/`flyway_schema_history`）不隔离；业务表（`sys_user`/`sys_dept`/`sys_role`/`sys_role_permission`/`sys_tenant_config`/`off_message`/`iot_device`/`int_connector_config`）全部参与隔离 |
| 大小写不敏感 | `SYS_MENU`/`Sys_Menu` 归一化正确 |
| JSqlParser 可解析 | 代表性 SELECT/UPDATE/DELETE/INSERT 均可解析（改写前置条件） |

**补充说明**：SQL 实际改写行为另由 `TenantLineE2eTest`（H2 MySQL 兼容模式，10 用例）+ 真实 MySQL 9 / PostgreSQL 15 迁移演练（`SystemDualDatabaseMigrationIntegrationTest` 2/2、`IntegrationDualDatabaseMigrationIntegrationTest` 2/2）共同保证。

## 2. 门禁结果

| 项 | 结果 |
| --- | --- |
| `HttpConnectorLiveEndpointTest` | **6/6** |
| `IotMqttBrokerLiveTest`（真实 EMQX 5.8.6） | **3/3**（未跳过） |
| `IotMqttThroughputBaselineTest` | **1/1**（基线 331 msg/s，0 丢失） |
| `TenantSqlRewriteDialectTest` | **5/5** |
| 三模块及上游 `test` | **BUILD SUCCESS** |
| `spotless:check` | 通过 |

## 3. 复现方式

```bash
# 真实 MQTT 验证（可选，未启动 broker 时测试自动跳过）
docker run -d --rm --name emqx -p 1883:1883 emqx/emqx:5.8
source scripts/build/init-build-env.sh
mvnd -pl apex-integration test -Dtest='IotMqtt*Test,HttpConnectorLiveEndpointTest'
mvnd -pl apex-system test -Dtest='TenantSqlRewriteDialectTest'
```

## 4. 仍未验证项（如实保留）

| 项 | 原因 | 建议 |
| --- | --- | --- |
| **EMQX topic ACL 强制隔离** | 本次验证的是"客户端仅订阅本租户 topic 时不泄漏"，未配置 EMQX 侧 ACL 规则强制拒绝越权订阅 | 生产部署时在 EMQX 配置 `acl.conf` 或认证钩子返回 ACL，再做越权订阅拒绝测试 |
| **多连接/多订阅方吞吐与压测** | 仅测单连接单订阅方基线 | 需要容量规划时按连接数 × 消息速率矩阵压测 |
| **Compose 全栈端到端冒烟** | 未启动完整六服务栈 | 部署阶段执行：登录 → 网关验签 → 租户隔离 → 设备上行 → 通知投递全链路 |
| **第三方真实业务系统联调** | 无可用真实外部系统（微信/创蓝/第三方 REST 均为沙箱或模拟） | 交付前按客户实际系统联调 |
| **前端页面** | 按用户明确要求排除 | 独立前端仓库实施（契约见 `docs/03-api/integration-iot-api.md`） |
