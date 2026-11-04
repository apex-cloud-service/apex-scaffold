# 工作日报（2026-11-03）

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

**项目**：all-projects（apex-scaffold 后端 / apex-admin-web 前端 / cold-chain 冷链）

今日重心为平台运营能力全链路收口。apex 后端一次提交（156 文件）完成开放平台按应用分档限流与审计归属、服务侧能力二次校验、能力计量四维度、跨域总览单入口与主数据 MQ 分发，并补齐接口文档与权限注解；验证为模块子集测试与静态检查，全仓 clean verify 未执行。前端一次提交（29 文件）交付租户套餐、能力运营、开放平台、集成中心、物联网、统一通知、在线会话、OAuth2 同意页等九个新页面并接入 MFA 二步登录；无类型检查与构建证据。冷链当日约 10 次提交：监控大屏铺满与走马灯轮播、三端调拨单展示调拨种类与服务费承担方、W 端仓满移库申请前端。风险：开放链路端到端未打通；apex 在途 57 项未验证。次日切口：全仓 clean verify 定基线、按簇提交在途改动、前后端权限码与 V86 菜单种子逐项核对。

---

## 附：当日按仓详版（存档合并，原拆分文件已并入本文件）

### 一、apex-scaffold（后端）

# 工作日报（2026-11-03）

**项目**：apex-scaffold（后端）
**主线**：平台运营闭环收口——开放平台按应用分档限流与服务侧能力二次校验打通纵深防御，能力计量扩至四维度，主数据经 MQ 跨服务分发；同时全量补齐 Handler 的 springdoc 注解与权限/审计注解，并把周报/月报并入统一的 `periodic-reports` 目录。

## 一、今日完成

当日 1 次提交（`08b3748e`，10:47），156 个唯一文件、156 条变更（57 新增 / 93 修改 / 2 删除 / 4 纯重命名），覆盖 12 个 `apex-*` 模块 + 36 份文档 + 5 个根级与配置项（`README.md`、`.env.example`、`.gitignore`、`podman-compose.yml`、`config/nacos/apex-gateway.yaml`）。新增主源码 25 个（含 6 个双方言迁移）、新增测试 7 个。

### 1. 开放平台按应用分档限流与审计 client 维度（D2）
- 新增 `OpenAppAdminService.getRateLimitTier` 与内部端点 `GET /internal/v1/open-apps/{clientId}/rate-limit-tier`；网关侧 `ClientTierClient`（TTL 缓存 60s）+ `OpenAppTierProperties` 查询档位，`GatewayRateLimitProperties` 增加 `open-api-tiers` 档位 → 容量/补充速率映射。
- `RequestRateLimitFilter` 对 `/open-api/**` 固定按 `client:{clientId}` 判定，无法识别时回退既有 tenant→user→IP 维度。
- **解决了什么**：开放应用令牌无用户身份，此前按租户维度限流会让**同一租户下所有应用共享一份配额**——单个应用打满即可挤占同租户其他应用；改为应用维度后配额互不侵占。档位查询全链路 fail-open 回退 `default`，避免限流设施自身成为可用性单点。
- 审计补齐 client 维度：V87（MySQL/PG 条件式）为 `sys_gateway_access_log` 增 `client_id` 列与索引，`AccessLogRecord`/`RequestLoggingFilter`/`R2dbcAccessLogRepository` 全链路写入，`OpenApiScopeGatewayFilter` 放行时注入 `X-Client-Id` 并由 `JwtSignatureFilter` 剥离外部伪造值。
- **解决了什么**：开放调用此前在访问日志中**无法归属到具体应用**，出现争议流量时只能定位到租户；补齐后可追责到应用，且伪造头被网关统一剔除。

### 2. 服务侧能力二次校验（D4 纵深防御）
- `apex-common` 新增 `common/openapi` 包：`TenantCapabilityVerifier`（TTL 缓存拉取租户订阅，fail-closed）、`OpenApiCapabilityGuardFilter`（WebFilter，order -200）、`OpenApiGuardProperties`、`OpenApiGuardAutoConfiguration`（登记 AutoConfiguration.imports，默认关闭）。
- 仅对携带网关注入标记头 `X-Open-Api-Capability` 的请求生效：缺可信租户头 → 403；未订阅 → 403 `CAPABILITY_NOT_SUBSCRIBED`；订阅查询失败 → 503。普通业务流量零影响。
- **解决了什么**：能力订阅此前**只靠网关一道断言**，绕过网关直连服务端口即可越权调用未订阅能力；服务侧二次校验把该绕过面收敛，且用标记头而非无条件拦截，避免对非开放流量引入额外依赖。

### 3. 能力计量扩至四维度（D4）
- `CapabilityUsageDTO` 增加 `storageUsageBytes` / `deviceCount` / `messageCount` 存量维度（单租户查询才填充，平台全量视角与源失败为 `null`，不用 0 冒充）。
- 新增 `CapabilityMetricClient`（WebClient 拉域内统计端点，TTL 缓存，**按源降级**：失败即字段为 null，不阻断用量查询）与 `CapabilityUsageProperties.metrics` 映射。
- office 补 `GET /internal/v1/office/messages/stats?tenantId=`（`OfficeInternalHandler/Router/StatsRepository`，JdbcTemplate 显式 SQL 绕开租户拦截器在无上下文时的 fail-closed，租户条件显式携带）；iot 统计端点支持 `?tenantId=`，`DeviceRepositoryImpl.count` 在执行线程内显式绑定租户上下文。
- 顺带修复 `OverviewFacadeService` 的既有缺陷：integration 源调用漏传 `tenantId`，而该端点依赖租户上下文，**导致 integration 源长期恒为 DEGRADED**——此前被当作"源不可用"而非缺陷。
- **解决了什么**：能力运营此前只有"调用次数"一个维度，租户的存储/设备/消息存量不可见；四维度补齐后运营视图可用，且降级语义明确（缺数据 ≠ 零用量）。

### 4. 跨域只读聚合统一入口（D5）
- `PlatformOverviewRouter` / `PlatformOverviewHandler` + `OverviewFacadeService` / `OverviewProperties` / `OverviewResponse`，按业务端与内部端点双出口提供聚合总览，单源失败降级为 DEGRADED 而非整响应失败。
- **解决了什么**：前端总览页此前需并发打多个域端点并自行拼接、各自定义失败语义；收敛为单入口后，降级状态由后端统一表达。

### 5. 主数据跨服务分发（D5）
- `DistributedDomainEvent` 标记接口（`apex-common`）：实现者经 Outbox 重放点跨服务广播，tag 取事件类型；未实现者保持进程内语义。`UserChangedEvent` / `TenantChangedEvent` 实现该接口，`MasterDataEventContractTest` 补标记与 tag 断言。
- `DomainEventMqBridge`（messaging）在 Outbox 重放点旁路异步发送至 `apex.mq.domain-event-topic`（默认 `apex-domain-event`），`@ConditionalOnBean(RocketMQTemplate)`，发送失败仅 ERROR 日志。
- `TenantChangedEventListener`（auth-service）订阅 `TENANT_CHANGED` 失效租户状态判定缓存；`RemoteTenantStatusAdapter` 修正：业务拒绝（租户不存在/过期/不可用）此前被 fail-closed 兜底改写为"租户状态暂不可用"，现原样传播，兜底只针对回源失败。
- **解决了什么**：租户被禁用/过期后，此前须等 60s TTL 才拒绝新登录；事件驱动失效把窗口压到一个事件投递周期，重复消费天然幂等。审计上也能区分"查不到"与"确认拒绝"——此前两者在日志中不可分辨。

### 6. IoT 管理面补强（E3）
- 新增 `DeviceShadowResponse`；设备、命令、规则仓储补齐分页/计数；`DeviceRuleManageService` 与 `IotDeviceRouter`/`IotRuleHandler` 补管理端点与文档注解。
- 修正 `IotRuleHandler.get` 权限码 `manage` → `read`。
- **解决了什么**：规则查询此前要求 `manage` 权限，**只读账户无法查看规则**，是一处越权收紧过度的缺陷，已按读写语义纠正。

### 7. 接口文档与功能性注解统一
- 全量 Handler 补齐 springdoc 注解（`@Operation`/`@Parameters`/`@RequestBody`/`@ApiResponses`），请求体与响应体均标注具体 schema；`apex-integration` 补 springdoc 依赖声明。
- office 补齐缺失的 `@RequirePermission` 43 处（审批/日历/文档/组织/消息）；租户套餐创建/更新补 `@OperationLog`。
- 新建 `docs/02-architecture/capability-catalog.md`（8 项能力的编码/中心/组件/开放 scope/计量维度/变更流程）并附 `CapabilityCatalogDocContractTest`——解析文档能力表与代码目录逐项比对，任一侧漏改即失败。
- 修复 `CapabilityCatalog` 的"插入顺序稳定"语义失真：`Map.copyOf` 不保序，改为 `Collections.unmodifiableMap(LinkedHashMap)`。
- **解决了什么**：office 43 处管理接口此前**注解缺失即权限不生效**（切面依赖注解），属实际越权面；契约测试把"文档与代码不同步"从人工评审转为门禁。

### 8. 文档体系收敛
- `docs/08-project-management/weekly-reports/` 整体并入 `periodic-reports/{daily,weekly,monthly}`，4 个文件 100% 重命名（保留历史）、删除旧 `weekly-reports/README.md`；新增 `periodic-reports/README.md`、`daily/README.md`、`weekly/README.md`、`monthly/README.md`、`reports/README.md` 与全项目汇总报告。
- 补齐 10-27/10-28/10-29/10-31/11-01/11-02 日报与 2026-10 月报、W44 周报入库。
- **解决了什么**：日报/周报/月报此前分散且旧索引指向已迁移路径；收敛为单一目录树后索引可用，也消除了"文档按时间建目录"与结构标准的冲突。

## 二、验证

**本次日报生成过程未执行构建或测试**。只读采集命令为 `git log --since/--until`、`git log --name-status`、`git status --porcelain`、`find`、`awk` 统计，不产生构建产物、不改动工作区。未执行的原因为：全量 `clean verify` 耗时且可能污染当日工作区，而当日提交与随附测试报告已带门禁记载。

当日提交与 `docs/06-testing/jwt-unification/20261103-platform-ops-closure/report.md` 记载的门禁如下，**均为提交者所述，本次未复跑**：

| 证据 | 记载结果 | 口径 |
| --- | --- | --- |
| `mvnd compile` | BUILD SUCCESS | 编译，不含测试 |
| 受影响 12 模块 `test` | BUILD SUCCESS，Failures 0 / Errors 0 | 模块子集，非全仓 |
| `spotless:check` | 全仓通过（先 apply 后 check） | 静态检查 |
| `git diff --check` | 干净 | 静态检查 |
| `check-doc-links.sh` | 仓库文档 0 断裂（14 条断链均在 `.workbuddy/` 本地目录） | 静态检查 |
| `FlywayMigrationVersionTest` | 4/4，V87 双库配对一致 | 单元 |
| `OpenApiCapabilityGuardFilterTest` / `ClientTierClientTest` / `DomainEventMqBridgeTest` / `TenantChangedEventListenerTest` / `OfficeInternalHandlerTest` / `IotInternalHandlerStatsTest` / `CapabilityCatalogDocContractTest` | 6/6、5/5、3/3、3/3、3/3、2/2、1/1 | 单元 |
| `RequestRateLimitFilterTest` / `OpenApiScopeGatewayFilterTest` / `OpenAppAdminServiceTest` / `CapabilityUsageServiceTest` / `MasterDataEventContractTest` | 13/13、20/20、15/15、10/10、8/8 | 单元 |

**口径警示**：上表为**模块子集 `test`** 与静态检查，**不等于全仓 `mvnd -T4 -B clean verify`**；引用时不得升格为"全仓验证通过"。开放链路端到端调用、MQ 真实投递、存量指标真实回源均**未验证**（提交报告 §8 已如实标注）。

## 三、风险与待办

1. **开放链路端到端仍未打通**（当日最大功能缺口）。网关 `/open-api/**` 以原路径转发，业务服务尚无 `/open-api/**` 承接路由，下游身份中继过滤器只认用户令牌、不认 `client_credentials` 应用令牌——开放调用目前止于网关三道断言，**到不了业务 Handler**。分档限流与二次校验在本日已做扎实，但整条链路仍不可对外承诺可用。
2. **工作树 57 项未提交（42 修改 + 15 未跟踪）**，与 `MEMORY.md` 记载的 27 项存在口径漂移。在途集中在三簇：① workflow 事件 Outbox（`WorkflowEventOutboxDispatcher`、`JdbcWorkflowEventOutbox`、`WorkflowEventOutbox` + **V6 双方言迁移**，另含 V6 之外 10 个 workflow-service 修改）；② `apex-integration-service` 本地安全切面（`IntegrationSecurityAspect` 等 4 个主源码 + 1 个测试，修复集成库 Handler 上 `@RequirePermission`/`@OperationLog` 仅作元数据不执行的问题）；③ `apex-system` 组件入口 `SystemComponentAutoConfiguration` + `AutoConfiguration.imports`（M3/C7 补齐）。**均未验证，不计入交付**；提交时不可只取迁移或只取代码，否则出现迁移与实现错配。
3. **全仓 `clean verify` 当日仍无记录**，且 11-01 记录的基线失败（`apex-system` 的 `OpenApiEndpointsIntegrationTest` / `ApiCallLogIntegrationTest` 因 `JobMapper` 无 Bean）**未见修复证据**。本日 `apex-system` 改动量大（43 个文件），该断言需在干净工作区确认。
4. **真实环境项挂账**：`DomainEventMqBridge` 与 `TenantChangedEventListener` 仅以 mock 模板/桩适配器验证，未接真实 RocketMQ；`CapabilityMetricClient` 存量指标未真实跨服务回源；office/file/integration 未接主数据消费方（提交报告已说明：三域当前无本地缓存或冗余投影读者，接入会形成"消费但无人读"的空转组件，故列为后续项而非伪交付）。
5. **C4 未执行**，M3 无法收官：`apex-system` interfaces 层仍在业务库。本日新增的 `SystemComponentAutoConfiguration` 是装配侧的权宜补齐，不替代 C4 的物理迁移。
6. **次日切口（按优先级）**：① 干净工作区全仓 `clean verify`，先定 `JobMapper` 基线；② 按簇提交在途改动，workflow V6 迁移与其实现同提交；③ 开放链路补 `RewritePath` 映射 + 下游应用令牌认证语义 + 承接路由；④ 启动 C4。

### 二、apex-admin-web（前端）

# 工作日报（2026-11-03）

**项目**：apex-admin-web（前端）
**主线**：一次性交付八类平台运营管理页（租户套餐、能力运营、开放平台、集成中心、物联网、统一通知、在线会话、跨域总览）与 OAuth2 授权同意页，并接入 MFA 二步登录，把后端 11-01～11-03 已就绪但无入口的能力补上界面。

## 一、今日完成

当日 1 次提交（`d839a04`，07:08），29 个唯一文件、29 条变更（19 新增 / 10 修改），涉及 9 个新增页面、8 个新增 API 模块、1 个新增 API 测试、3 个既有页面改造、5 个基础设施文件（路由、状态、类型、AGENTS、记忆卡）。

### 1. 租户套餐管理（`system/tenant-package`、`api/tenant-package.ts`）
- 套餐列表、创建、更新界面；按"空集合 = 全量能力"语义渲染能力勾选，并对父级套餐做父链补全。
- **解决了什么**：套餐与功能裁剪此前只有后端契约，运营侧无法自助配置；"空集合 = 全量"若按普通数组语义渲染会显示为"未选任何能力"，父链补全避免继承关系在界面上断裂。

### 2. 能力运营（`system/capability`、`api/capability.ts`）
- 能力目录与四维度用量展示页。
- **解决了什么**：能力目录、调用/存储/设备/消息四类用量此前无可视入口，运营无法判断租户实际用量分布。

### 3. 开放平台应用管理（`system/open-app`、`api/open-app.ts`）
- 开放应用列表与创建，`clientSecret` 仅在创建成功时一次性展示。
- **解决了什么**：开放应用此前只能经后端接口申请；一次性展示 secret 的交互减少凭据在前端可反复读取的暴露面。

### 4. 集成中心与物联网（`integration/connector`、`iot/device`、`iot/device-rule`、`api/integration.ts`、`api/iot.ts`）
- 连接器与凭据管理、设备管理、设备规则管理三个页面。
- **解决了什么**：11-02 入库的 `apex-integration` connector/device 双子域与 EMQX 接入能力此前完全没有管理界面，实际处于"能用但不可运营"状态。

### 5. 统一通知与在线会话（`office/notify`、`monitor/session`、`api/notify.ts`、`api/session.ts`）
- 通知模板管理与投递留痕留档页面、在线会话查看页。
- **解决了什么**：统一消息门面的模板与投递结果缺少观察手段，投递异常只能查日志；在线会话同样无观测入口。

### 6. 跨域数据总览（`api/overview.ts`、`views/dashboard/index.vue`）
- 工作台新增跨域聚合总览卡片，消费后端 `OverviewFacade` 单入口。
- **解决了什么**：总览此前由前端并发打多个域端点自行拼接，失败语义各页不一致；改为消费单入口后，后端 DEGRADED 降级状态可被前端如实呈现，而非整卡片空白。

### 7. 认证链路：MFA 二步与租户门禁（`views/login/index.vue`、`api/auth.ts`、`stores/user.ts`）
- 登录流程接入 MFA 二步验证（`challengeToken` 换取正式令牌）；租户门禁失败给出可读指引；新建租户支持套餐选择与管理员账号初始化。
- **解决了什么**：后端 MFA 会话接线与认证期租户校验已具备，但前端登录页没有挑战步骤，**MFA 实际无法走通**；门禁错误此前只呈现通用失败提示，运维无法判断是凭据错误还是租户被禁用/过期。

### 8. OAuth2 授权同意页（`views/oauth2/consent.vue`、`api/oauth2.ts`、`router/index.ts`）
- 授权同意页以 public 匿名静态路由挂载。
- **解决了什么**：OAuth2 授权码流程缺同意页，第三方应用授权链路在浏览器端断裂；匿名静态路由避免把同意页置于登录守卫之后造成循环跳转。

### 9. 路由与工程化
- `router/dynamic.ts` 的 `VIEW_REGISTRY` 同步登记全部新增页面；`types/index.ts` 补契约类型。
- 按注释规范完成注释一致性整改（`api/iot`、`api/session`、`types` 及 7 个页面头注）；新增 `MEMORY-CARD.md`，`AGENTS.md` 同步本批页面与契约口径。
- **解决了什么**：动态路由未登记会导致页面"已开发但菜单不可达"；类型与注释统一降低了后续契约漂移成本。

## 二、验证

**未执行**。本次日报生成过程只做只读采集（前端仓库 `git log --since/--until`、`git log --name-status`、`git status --porcelain`），未运行类型检查、构建或测试命令，故不推断任何门禁通过。

**当日提交未记载任何验证结果**——提交正文无 `验证` 章节，无 `vue-tsc` / `vite build` / 单测执行的记录。新增的 `src/api/platform.test.ts` 仅为新增测试文件，**无证据表明其被执行**。

## 三、风险与待办

1. **前后端提交时序倒置**：前端 `d839a04` 于 07:08 提交，后端同日 `08b3748e` 于 10:47 才入库，且后端该次提交包含 V86「前端管理菜单」权限种子、office 43 处 `@RequirePermission` 补齐、以及 `IotRuleHandler.get` 权限码由 `manage` 修正为 `read`。前端页面的权限码与菜单项**是否与 V86 种子一致尚未核对**，属本批最可能的联调断点。
2. **开放平台页面无可用后端链路**：后端已确认 `/open-api/**` 止于网关三道断言、无业务承接路由（见同日后端日报风险 1）。因此开放平台页面的"应用可用性"在端到端层面无法验证，只能验证元数据管理。
3. **零构建与零类型检查证据**：29 个文件的批次（含 8 个新 API 模块、9 个新页面）未记录 `vue-tsc` 或打包结果，类型错配风险未被门禁拦截；建议次日先跑类型检查再联调。
4. **工作树**：仅 `MEMORY-CARD.md` 1 项修改未提交，代码工作树干净。
5. **次日切口（按优先级）**：① 跑类型检查与生产构建；② 与后端 V86 菜单种子、权限码逐项核对（重点 `iot` 规则的 read/manage 语义）；③ 联调租户套餐"空集合 = 全量"与父链补全的真实响应；④ MFA 二步与租户门禁错误提示在真实认证服务上走通一遍。
