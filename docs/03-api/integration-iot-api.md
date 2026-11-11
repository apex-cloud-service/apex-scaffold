# 集成中心与设备接入 API 契约（M5 / E1–E4）

本文件描述集成能力中心（连接器 / 凭据 / webhook）与物联网设备接入（设备 / 遥测 / 命令 / 规则）的对外契约。实现位置：`apex-integration` 业务库（connector 与 device 双子域），`apex-integration-service` 运行模块。

## 1. 模块与拓扑

| 项 | 说明 |
| --- | --- |
| 业务库 | `apex-integration`：`...integration.connector.*` 与 `...integration.device.*` 包结构隔离 |
| 运行模块 | `apex-integration-service`：默认业务端口 8094、管理端口 8097 |
| 组件入口 | `IntegrationComponentAutoConfiguration`（`apex.component.integration.enabled`，默认 true） |
| 对外协议 | 一律 `WebClient`（HTTP）；**不引入 Feign**；MQTT 客户端依赖 `optional`，classpath 无依赖时零 Bean |
| 租户隔离 | 全部业务表 `tenant_id NOT NULL` + 拦截器默认开启（自建库即满足标准，已在真实 MySQL 9 / PG 15 演练验证） |

## 2. 连接器管理

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/integration/v1/connectors` | 连接器配置列表（租户内） |
| POST | `/integration/v1/connectors` | 创建连接器配置 |
| GET | `/integration/v1/connectors/{id}` | 连接器详情 |
| PUT | `/integration/v1/connectors/{id}` | 更新连接器配置 |
| DELETE | `/integration/v1/connectors/{id}` | 删除（逻辑删除） |

**连接器类型**：内置 `HTTP`、`WEBHOOK`、`WECHAT`、`CHUANGLAN`；第三方以 `IntegrationConnector` SPI + `META-INF/services` 声明免改库接入。

## 3. 凭据管理

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/integration/v1/credentials` | 凭据列表（**仅返回元数据，不返回密文**） |
| POST | `/integration/v1/credentials` | 创建凭据（AES-256-GCM 加密存储） |
| GET | `/integration/v1/credentials/{id}` | 凭据元数据 |
| PUT | `/integration/v1/credentials/{id}/rotate` | 轮换凭据（版本递增，旧版本失效） |
| DELETE | `/integration/v1/credentials/{id}` | 删除凭据 |

**安全约束**：明文不落库、不返前端、不写日志；轮换后旧凭据立即失效。

## 4. 入站 Webhook

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/integration/v1/webhook/{connector}` | 接收第三方回调 |

**校验链**：HMAC 签名验证 → 时间戳防重放窗口 → `int_webhook_dedup` 幂等去重 → 转领域事件（Outbox）→ 订阅方消费。去重键唯一约束含 `tenant_id`，重复投递返回成功但不再处理。

## 5. 设备接入（IoT）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/integration/v1/iot/devices` | 注册设备（返回 deviceKey + 一次性凭据） |
| GET | `/integration/v1/iot/devices` | 设备列表（分页） |
| GET | `/integration/v1/iot/devices/{id}` | 设备详情（基础字段，**不含影子**；影子走 `/shadow` 子资源） |
| DELETE | `/integration/v1/iot/devices/{id}` | 注销设备 |
| GET | `/integration/v1/iot/devices/{id}/telemetry` | 遥测历史 |
| GET | `/integration/v1/iot/devices/{id}/shadow` | 设备影子（desired/reported JSON + version 乐观锁，只读视图） |
| GET | `/integration/v1/iot/devices/{id}/commands` | 命令历史分页（pageNum/pageSize） |
| POST | `/integration/v1/iot/devices/{id}/commands` | 命令下发（commandId 幂等，权限码 `iot:command:send`） |
| GET | `/integration/v1/iot/rules` | 规则分页（keyword/enabled/pageNum/pageSize） |
| POST | `/integration/v1/iot/rules` | 创建规则（结构化条件 + 动作） |
| GET | `/integration/v1/iot/rules/{id}` | 规则详情 |
| POST | `/integration/v1/iot/rules/{id}/toggle` | 启停规则（请求体 `enabled` 为字符串 `"true"`/`"false"`） |

**内部端点**（集群内直连，不经网关）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/internal/v1/iot/mqtt/auth` | EMQX 认证钩子（设备凭据校验 + 租户解析） |
| POST | `/internal/v1/iot/mqtt/acl` | **EMQX HTTP Authorize 钩子**：pub/sub 时按租户命名空间返回 `{"result":"allow"\|"deny"}`；越权一律 deny（fail-closed） |
| POST | `/internal/v1/iot/devices/{key}/commands` | 命令下发（幂等 commandId） |
| GET | `/internal/v1/iot/devices/stats` | 设备统计（供 D5 聚合门面消费） |

## 6. 设备通信约定

**EMQX 授权配置（生产必须）**：将 EMQX 授权源指向 `POST /internal/v1/iot/mqtt/acl`，并设 `authorization.no_match = deny`：

```hocon
authorization {
  no_match = deny
  sources = [
    {
      type = http
      enable = true
      url  = "http://apex-integration-service:8094/internal/v1/iot/mqtt/acl"
      method = post
      body {
        username = "${username}"
        topic    = "${topic}"
        action   = "${action}"
      }
    }
  ]
}
```

已验证：未配置该授权源时跨租户订阅在 broker 层畅通（仅应用层策略生效）；配置后 EMQX 实际拒绝越权订阅（`authorization_permission_denied`，source: http）。

| 项 | 约定 |
| --- | --- |
| 协议 | MQTT 3.1.1 / 5 over TLS（生产外部 EMQX，dev 可内嵌或跳过） |
| Topic | `apex/{tenantCode}/{deviceKey}/up`（遥测上行）、`.../down`（命令下行） |
| ACL | 设备仅可访问本租户命名空间（EMQX 侧按租户 + deviceKey 授权） |
| 凭据 | secret 哈希存储 / X.509 指纹；一次性下发，不落日志 |
| 限额 | 设备级连接数与消息速率限额（broker 侧 + 应用侧双层） |

## 7. 规则动作

| 动作 | 说明 | 依赖 |
| --- | --- | --- |
| `NOTIFY` | 经统一消息门面发送通知 | D1 `NotifyChannelPort` |
| `WEBHOOK` | 经出站 webhook 连接器回调外部系统 | E2 `WebhookOutboundConnector` |
| `EVENT` | 转领域事件供业务订阅方消费 | Outbox + `DomainEventChannel` |

**结构化条件**（字段 / 操作符 / 阈值）不执行自由脚本，无注入面。

## 8. 权限码

| 权限码 | 说明 |
| --- | --- |
| `integration:connector:read` / `create` / `update` / `delete` | 连接器管理 |
| `integration:credential:read` / `manage` | 凭据查询 / 注册与轮换删除 |
| `iot:device:read` / `iot:device:manage` | 设备查询（含影子与命令历史）/ 注册注销 |
| `iot:telemetry:read` | 遥测查询 |
| `iot:command:send` | 命令下发（V85 登记） |
| `iot:rule:read` / `iot:rule:manage` | 规则列表与详情（V85）/ 创建与启停（V83） |

种子见 `apex-system/src/main/resources/db/migration/{mysql,postgresql}/V83__register_integration_iot_permissions.sql` 与 `V85__register_iot_rule_read_and_command_permissions.sql`；菜单种子见 `V86__register_frontend_management_menus.sql`。

## 9. 事件契约

| 事件 | 说明 |
| --- | --- |
| `WebhookEventReceived` | 入站 webhook 校验通过后发出 |
| `DeviceTelemetryReceived` | 遥测上行落库后发出（触发规则引擎） |

## 10. 未实现项（如实声明）

- **HTTP/Webhook 连接器的真实外部联调**：契约与实现已就位，未对真实第三方端点做端到端联调。
- **EMQX 生产集群**：本地 EMQX 5.8.6 已验证认证钩子与 HTTP 授权钩子（越权订阅被 broker 拒绝）；多节点集群与 ACL 缓存策略未验证。
- **吞吐基线**：本地实测单连接 331 msg/s、10 设备并发 907 msg/s（QoS 1，0 丢失）；仅容量参考，不构成 SLA。
- **影子 desired 写接口**：影子当前为只读查询；desired 的管理端写入未实现（下行生效依赖设备侧上报）。
