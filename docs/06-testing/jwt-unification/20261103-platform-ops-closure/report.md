# 中台运营闭环补齐报告：能力目录 / 分档限流与审计 / 四维度计量 / 服务侧二次校验 / 主数据分发

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

- 范围：接续 20261102-gap-closure 评审确认的 5 项中台偏差（能力目录文档页、client 限流档与审计 client 维度、
  计量四维度、服务侧能力二次校验、主数据事件分发消费）
- 执行日期：2026-11-03
- 状态：**5 项全部落地，1 项既有缺陷顺带修复；开放链路端到端调用仍为未验证项（见 §8）**

## 1. 项① 能力目录文档页 + 契约测试（D4 文档侧）

| 变更 | 内容 |
| --- | --- |
| `docs/02-architecture/capability-catalog.md` | 新建能力目录页：8 项能力（编码/名称/中心/组件/开放 scope/计量维度/说明）、四维度计量口径、能力与组件关系、变更流程；登记入 `docs/02-architecture/README.md` 索引 |
| `CapabilityCatalogDocContractTest` | 新增契约测试：解析文档 §1 能力表，断言能力编码（含顺序）、名称、中心、组件、开放 scope 与代码目录完全一致——任一侧变更未同步即失败 |
| `CapabilityCatalog` | 修复"插入顺序稳定"语义失真：`Map.copyOf` 不保序，改为 `Collections.unmodifiableMap(LinkedHashMap)`，`all()` 与目录页/前端展示顺序回归登记顺序 |

验证：`CapabilityCatalogTest` 5/5 + `CapabilityCatalogDocContractTest` 1/1。

## 2. 项② client 限流档 + 审计 client 维度（D2）

| 变更 | 内容 |
| --- | --- |
| `OpenAppAdminService.getRateLimitTier` | 新增限流档查询：读开放应用元数据 `openPlatform.rateLimitTier`；非开放应用/未声明/未知客户端/元数据损坏一律 `default`（fail-open） |
| `InternalOpenAppHandler` / `InternalOpenAppRouter` | 新增内部端点 `GET /internal/v1/open-apps/{clientId}/rate-limit-tier`；`SystemConstants.INTERNAL_OPEN_APP_PATHS` + system 安全链放行 |
| `ClientTierClient` + `OpenAppTierProperties` | 网关档位查询客户端（TTL 缓存 60s，超时/失败 fail-open `default`） |
| `GatewayRateLimitProperties` | 新增 `open-api-tiers`（档位 → capacity/refillRate 映射，未知档回退 `default` 档，缺省字段回退全局默认） |
| `RequestRateLimitFilter` | `/open-api/**` 固定按应用维度 `client:{clientId}` 判定（开放应用令牌无用户身份，租户维度会令同租户所有应用共享配额）；阈值取档位映射；无法识别 client_id 回退既有 tenant→user→IP 维度 |
| 审计 client 维度 | V87（mysql+pg 条件式）：`sys_gateway_access_log` 新增 `client_id` 列 + 索引；`AccessLogRecord`/`RequestLoggingFilter`（从已验签令牌 `client_id` claim 取值）/`R2dbcAccessLogRepository` 全链路写入 |
| `OpenApiScopeGatewayFilter` | 放行时注入 `X-Client-Id`；`JwtSignatureFilter` 剥离外部伪造值 |

验证：`ClientTierClientTest` 5/5、`RequestRateLimitFilterTest` 13/13（含 4 个新分档用例）、
`OpenApiScopeGatewayFilterTest` 20/20（含 X-Client-Id 注入断言）、`OpenAppAdminServiceTest` 15/15（含 3 个新档位用例）、
`FlywayMigrationVersionTest` 4/4（V87 双库配对一致）。

## 3. 项③ 计量四维度（D4：调用次数 / 存储用量 / 设备数 / 消息量）

| 变更 | 内容 |
| --- | --- |
| `CapabilityUsageDTO` | 新增 `storageUsageBytes` / `deviceCount` / `messageCount` 存量维度（可空：单租户查询才填充，平台全量视角与源失败均为 `null`） |
| `CapabilityUsageProperties` | 新增 `metrics` 映射（能力 → 数据源/URI 模板/字段）+ 超时与缓存配置；基址复用 `apex.overview.sources` |
| `CapabilityMetricClient` | 新增存量指标客户端：WebClient 拉取域内统计端点，TTL 缓存，**按源降级**（失败→空 Mono→字段 null，不阻断用量查询） |
| office 内部端点 | 新增 `GET /internal/v1/office/messages/stats?tenantId=`（`OfficeInternalHandler/Router/StatsRepository`，JdbcTemplate 显式 SQL 绕开租户拦截器的无上下文 fail-closed，租户条件显式携带） |
| iot 统计端点 | `IotInternalHandler.stats` 支持 `?tenantId=` 参数（与 file 域口径对齐）；`DeviceRepositoryImpl.count` 在执行线程内显式绑定租户上下文（无上下文调用路径可满足租户拦截器） |
| `OverviewFacadeService` | **顺带修复既有缺陷**：integration 源调用补 `tenantId` 参数——此前该端点依赖租户上下文而聚合门面不携带，integration 源恒为 DEGRADED |

验证：`CapabilityUsageServiceTest` 10/10（含单租户补充存量维度、平台视角不填充、源失败降级）、
`OverviewFacadeServiceTest` 6/6、`OfficeInternalHandlerTest` 3/3、`IotInternalHandlerStatsTest` 2/2。

## 4. 项④ 服务侧能力二次校验（D4 共用判定）

| 变更 | 内容 |
| --- | --- |
| `apex-common` 新增 `common/openapi` 包 | `TenantCapabilityVerifier`（TTL 缓存拉取租户订阅，fail-closed）+ `OpenApiCapabilityGuardFilter`（WebFilter，order -200）+ `OpenApiGuardProperties` + `OpenApiGuardAutoConfiguration`（imports 登记，默认关闭） |
| 触发语义 | 仅对携带网关注入标记头 `X-Open-Api-Capability` 的请求生效：缺可信租户头 → 403；共享 `CapabilityGate` 判定未订阅 → 403 `CAPABILITY_NOT_SUBSCRIBED`；订阅查询失败 → 503（fail-closed）。普通业务流量零影响 |
| 网关配合 | `OpenApiScopeGatewayFilter` 放行时注入 `X-Open-Api-Capability`；`JwtSignatureFilter` 剥离外部伪造值 |
| 服务启用 | file/workflow/office/system 四服务 `application.yml` 开启 `apex.open-api.guard.enabled=true`（`base-url` 经 `APEX_SYSTEM_BASE_URL` 注入） |

验证：`OpenApiCapabilityGuardFilterTest` 6/6（透传/缺头 403/放行/未订阅 403/失败 503/TTL 复用）。

## 5. 项⑤ 主数据分发：MQ 桥 + 消费方（D5）

| 变更 | 内容 |
| --- | --- |
| `DistributedDomainEvent`（apex-common） | 新增标记接口：实现者经 Outbox 进程内重放点跨服务广播，tag=事件类型；未实现者保持进程内语义 |
| 契约 | `UserChangedEvent` / `TenantChangedEvent` 实现标记接口；`MasterDataEventContractTest` 补标记与 tag 断言（8/8） |
| `DomainEventMqBridge`（messaging） | Outbox 重放点旁路异步发送至 `apex.mq.domain-event-topic`（默认 `apex-domain-event`）；`@ConditionalOnBean(RocketMQTemplate)` + `apex.events.distributed-bridge.enabled`（默认 true）；发送失败仅 ERROR 日志（失效+回源消费模型可容忍） |
| `TenantChangedEventListener`（auth-service） | 新增消费方：tag 订阅 `TENANT_CHANGED` → 失效 `RemoteTenantStatusAdapter` 判定缓存——租户禁用/过期后**新登录最迟一个事件投递周期内被拒绝**（此前须等 60s TTL）；重复消费天然幂等 |
| 既有缺陷修复 | `RemoteTenantStatusAdapter`：业务拒绝（租户不存在/过期/不可用）此前被 fail-closed 兜底改写为"租户状态暂不可用"（审计无法区分"查不到"与"确认拒绝"）；现业务拒绝原样传播，兜底只针对回源失败 |

验证：`DomainEventMqBridgeTest` 3/3、`MasterDataEventContractTest` 8/8、`TenantChangedEventListenerTest` 3/3、
`RemoteTenantStatusAdapterTest` 4/4、`AuthenticationApplicationServiceTest` 11/11。

**范围说明（如实）**：方案 D5 列举的订阅方为 office/file/integration；本批消费方落在 auth-service（登录租户门禁，
真实读者）。office/file/integration 当前**没有用户/租户数据的本地缓存或冗余投影读者**（事件载荷按契约仅含
非敏感标识，无本地缓存可失效），为其接入消费方需先确立投影读者，避免出现"消费但无人读"的空转组件——
列为后续项而非本批伪交付。

## 6. 门禁结果

| 项 | 结果 |
| --- | --- |
| 受影响 12 模块（common/api-contract/messaging/gateway/system/system-service/auth-service/file-service/office-service/workflow-service/integration/integration-service）`test` | **BUILD SUCCESS**，Failures: 0 / Errors: 0 |
| `spotless:check`（全仓） | 通过（先 apply 后 check；apply 同时格式化了并行会话遗留的既有违例文件） |
| `git diff --check` | 干净 |
| `check-doc-links.sh` | 仓库文档 0 断裂（14 条断链全部位于 `.workbuddy/` 本地目录，目标被 gitignore，非本仓内容） |
| `FlywayMigrationVersionTest` | V87 双库版本配对一致 |

## 7. 契约文档同步

- `docs/03-api/open-platform-integration.md` §4/§7：限流与二次校验更新为已实现口径（应用维度判定、
  档位查询端点、能力标记头、V87 审计列），"未实现项"改为"边界与明确不做项"，**如实标注下游承接路径未验证**。
- `docs/02-architecture/capability-catalog.md`：新建（见 §1）。

## 8. 未验证项与边界（如实保留）

| 项 | 原因 | 建议 |
| --- | --- | --- |
| **开放链路端到端调用** | 网关 `/open-api/**` 以原路径转发，业务服务尚无 `/open-api/**` 承接路由，且下游身份中继过滤器只认用户令牌（不认 client_credentials 应用令牌）——开放调用目前止于网关三道断言，到不了业务 handler | 下一批：网关 RewritePath 映射 + 下游开放应用令牌认证语义 + 承接路由 |
| **MQ 桥 / 监听器未接真实 RocketMQ** | 单测以 mock 模板与桩适配器验证；真实 broker 投递与消费需部署环境 | 部署阶段按 `run-mqtt-integration.sh` 模式补 MQ 联调 |
| **存量指标真实回源** | `CapabilityMetricClient` 单测以桩响应验证；真实跨服务拉取需六服务栈 | 随 Compose 全栈冒烟一并验证 |
| **office/file/integration 主数据订阅** | 见 §5 范围说明 | 先确立本地投影/缓存读者再接消费方 |
| **前端页面** | 按用户明确要求排除 | 独立前端仓库实施 |
