# 批次 E1–E4 测试报告：对外集成与物联网设备接入（M5 收官）

- 批次：方案 [unified-auth-tenant-platform-remediation-plan.md](../../../08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md) 批次 E1/E2/E3/E4
- 执行日期：2026-11-01 至 2026-11-02
- 环境：macOS、JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`；双库演练容器 MySQL 9.0 / PostgreSQL 15
- 状态：**实施完成，门禁结果见 §4**；M5 为方案最后一个里程碑

## 1. 已交付变更

### E1 集成域与连接器框架

| 项 | 内容 |
| --- | --- |
| 模块 | `apex-integration` 业务库（`connector` 与 `device` 双子域包结构隔离）+ `apex-integration-service` 运行模块（业务 8094 / 管理 8097） |
| 连接器 SPI | `IntegrationConnector`（type / healthCheck / execute）+ `ConnectorLoader`：**ServiceLoader（第三方 JAR 免改库）+ Spring Bean 双注册**，重复 type 显式拒绝 |
| 执行编排 | `ConnectorExecutionService`：连接器查找 → 幂等键重试（指数退避 2 次）→ 未提供幂等键不重试（防非幂等副作用重复） |
| 凭据 | `int_credential` 表：AES-256-GCM 密文 + IV + 版本号/轮换时间；**明文不落库、不返前端、不写日志** |
| 迁移 | integration V1（连接器配置/凭据/出站审计/入站事件/webhook 去重）+ V2（IoT）+ V3（Seata undo_log），MySQL/PG 成对 |
| 组件入口 | `IntegrationComponentAutoConfiguration`（C7 组件模型标准，`apex.component.integration.enabled`） |

### E2 系统集成连接器与 Webhook

- 内置连接器：`HttpConnector`（通用 REST + 认证配置）、`WebhookOutboundConnector`、`WeChatConnector`、创蓝短信适配
- 入站：`WebhookInboundService` + `WebhookSignatures`（HMAC 签名 + 时间戳防重放）+ `WebhookDedupRepository`（`int_webhook_dedup` 唯一键含 tenant_id）
- 出站：熔断/超时/指数退避重试/幂等键 + `int_outbound_call_log` 审计

### E3 物联网设备接入

- 设备模型：`iot_device` / `iot_device_shadow`（desired/reported + 乐观锁）/ `iot_telemetry` / `iot_command`（幂等 commandId + 超时扫描 `DeviceCommandTimeoutScanner`）/ `iot_rule`
- 接入：MQTT（`IotMqttAutoConfiguration`，客户端依赖 optional，无依赖时零 Bean）；topic 命名空间 `apex/{tenantCode}/{deviceKey}/up|down`
- 认证：`/internal/v1/iot/mqtt/auth`（EMQX 钩子，设备凭据校验 + 租户解析）
- 命令：`/internal/v1/iot/devices/{key}/commands`（幂等下发 → ACK/超时状态机）
- 规则：结构化条件（字段/操作符/阈值）+ 三动作（NOTIFY 接 D1 / WEBHOOK 接 E2 / EVENT 转领域事件），无脚本注入面

### E4 组件模型回归与交付物

- `apex-boot-all` 已装配 `apex-integration`，`BootAllAssemblyTest` 覆盖 **6 个组件入口**（含 integration connector/device/mqtt）与核心类可见性
- 权限种子 V83（`integration:*`、`iot:*`）
- 四语言 i18n：`apex-integration-service/src/main/resources/i18n/` 19 条消息 × 4 语言 + `IntegrationI18nConfig`
- API 契约：[集成中心与设备接入 API 契约](../../../03-api/integration-iot-api.md)

## 2. 测试

| 测试 | 结果 | 覆盖 |
| --- | --- | --- |
| `IntegrationDualDatabaseMigrationIntegrationTest`（新增） | **2/2** | 真实 MySQL 9 + PG 15：V1–V3 全链迁移、10 张业务表 tenant_id、租户维度唯一键 |
| `SystemDualDatabaseMigrationIntegrationTest`（扩展） | **2/2** | 补 V81–V84 权限种子断言 + V80 菜单可达性 |
| `BootAllAssemblyTest`（扩展） | **4/4** | 6 组件入口（含 integration）装配完整性 |
| `IntegrationI18nConsistencyTest`（新增） | **3/3** | 四语言 key 一致、高频错误码齐全、无未翻译照抄 |
| `ConnectorExecutionServiceTest` | **5/5** | 未知连接器拒绝、幂等键重试、无幂等键不重试、重复类型拒绝、SPI 空加载 |
| `WebhookInboundServiceTest` / `DeviceCommandServiceTest` / `DeviceQueryServiceTest` | 通过 | 签名防重放、命令幂等超时、设备查询 |
| 六模块及上游 `test` | **BUILD SUCCESS** | integration / integration-service / boot-all / system / office / 上游 |

## 3. 过程缺陷与修复记录

1. **`apex-integration` 未注册进 reactor** → 补根 POM `<module>`。
2. **缺 lombok 依赖** → 补 `provided`。
3. **连接器重试测试不稳定**：Mockito `thenReturn(A, B)` 返回的 `Mono` 实例被复用（`Mono.error` 单次订阅即终止，无法二次订阅）→ 生产代码改为 `Mono.defer(...)` 保证每次订阅重新调用连接器，测试改 `thenAnswer` 按调用次数返回。
4. **integration 缺 Testcontainers/Flyway 测试依赖** → 补 5 个 test scope 依赖。
5. 双库演练首版未过 spotless → `spotless:apply` 修正。

## 4. 结果

- **双库演练 2 套全部通过**：system（124.5s，含 V76–V84 全链）、integration（43.2s，V1–V3）。
- 六模块及上游 `test`：**BUILD SUCCESS**。
- 全仓 `spotless:check`：**通过**；`git diff --check`：**干净**；doc-links：**0 断裂**。

## 5. 未验证项与边界

- **真实第三方联调未执行**：HTTP/Webhook 连接器未对真实外部端点联调；微信/创蓝沿用既有客户端测试。
- **EMQX 生产集群未验证**：MQTT 认证钩子与 topic ACL 已在代码层实现，未在真实 EMQX 集群验证设备连通、订阅与 ACL 隔离。
- **遥测吞吐未压测**：无 SLA 承诺。
- **前端页面不在范围**：设备管理 / 连接器管理 UI 依用户要求排除；后端 API 契约见 `docs/03-api/integration-iot-api.md`。
- **i18n 覆盖范围有限**：仅 19 条 Handler 层高频错误消息；86 处领域内部 `BusinessException` 仍为硬编码中文（领域校验消息不面向最终用户，改造收益低风险高）。
