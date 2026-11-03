# 工作日报（2026-11-02）

**项目**：apex-scaffold（后端）
**主线**：E 批集成与 IoT 从在途转收口（E1–E4 全部入库），D 批以 D5 数据服务层 + 开放路由门禁收官，并在真实 EMQX / MySQL / Redis 上补齐此前以单测替代的验证项。

## 一、今日完成

当日 5 次提交（10:04–15:43），303 个唯一文件、309 条变更（251 新增 / 58 修改）、16 个 `apex-*` 模块 + 7 份文档 + 4 个根级文件（`pom.xml`、`AGENTS.md`、`.env.example`、`config/nacos`）。无删除文件。

### 1. 集成域骨架与连接器框架（E1，`b055c55f`）
- 新建 `apex-integration` 业务库（`connector` 与 `device` 双子域包结构隔离，主源码 106 个文件）与 `apex-integration-service` 运行模块，根 POM 由 33 模块扩至 **34**，运行入口增至 **10**。
- 定义 `IntegrationConnector` SPI 与 `ConnectorLoader`（ServiceLoader + Spring Bean 双注册，重复 type 显式拒绝），**解决第三方 JAR 接入必须改库的问题**——外部连接器可独立打包加载。
- `ConnectorExecutionService` 以幂等键触发指数退避重试，无幂等键不重试，**避免非幂等副作用被重复执行**。
- 租户级凭据 AES-256-GCM 密文存储并支持版本轮换，明文不落库、不返前端、不写日志，**闭合了凭据外泄面**。
- V1/V2/V3 双方言迁移（MySQL/PG 成对）与组件入口 AutoConfiguration（C7 标准），使集成能力可按组件裁剪装配。

### 2. 系统集成、Webhook 与出站治理（E2，`b055c55f`）
- 落地 `HttpConnector`、`WebhookOutboundConnector`、微信与创蓝连接器。
- 入站侧 HMAC 签名 + 时间戳防重放 + 去重表（唯一键含 `tenant_id`），**解决了重放与跨租户串单**；出站侧熔断/超时/重试/幂等键 + 出站审计表。

### 3. 物联网设备接入（E3，`b055c55f`）
- 设备、影子（乐观锁）、遥测、命令（幂等 + 超时扫描）、规则全模型；`device` 子域 64 个主源码文件。
- MQTT 接入客户端保持 optional（classpath 无依赖时零 Bean，生产由外部 EMQX 提供），topic 采用租户命名空间，**解决了设备数据无租户边界的问题**。
- 规则支持结构化三动作，命令下发幂等。

### 4. 集成组件回归与交付物（E4，`b055c55f`）
- `apex-boot-all` 装配 integration，`BootAllAssemblyTest` 覆盖 6 个组件入口。
- 权限种子 V83、四语言 i18n（19 条 × 4）+ `IntegrationI18nConfig` + 一致性测试；API 契约 `docs/03-api/integration-iot-api.md`。

### 5. 数据服务层与开放平台补强（D5 收尾 + M4/M5，`0d8cda0a`）
- **D5 轻量数据服务层**：新增 `UserChangedEvent`/`TenantChangedEvent` 主数据事件契约（api-contract），`OverviewFacadeService` + `InternalOverviewRouter`（`GET /internal/v1/overview`）聚合各域只读统计，短 TTL 缓存 + 部分降级；file/workflow 各自补齐只读统计端点。**解决了跨域统计各自为政、无统一只读入口的问题。**
- **D2 开放路由**：`OpenApiScopeGatewayFilter` + `TenantCapabilityProperties`，`/open-api/**` 强制开放应用令牌、scope 断言、按 clientId 限流档与能力订阅门禁；`JwtSignatureFilter` 剥离外部伪造的 `X-Client-Scopes`。**闭合了开放接口越权与 scope 伪造面**，即 M4 遗留的网关路由项。
- **D4 能力运营**：`CapabilityUsageService` + 查询仓储 + V84 权限，按租户 × 能力只读用量查询（不计量计费）。
- **D1 消息门面持久化**：`NotifyTemplate` / `NotifyRequestRecord` 领域与仓储 + office V8 迁移，`OfficeNotifyRouter`（管理端）与 `InternalNotifyRouter`（内部投递）。
- TOTP 重放防护：`V61__add_totp_last_used_step`（MySQL/PG）+ PO/仓储/服务改造。
- 权限种子 V81/V82 与 system 四语言 i18n 同步；`AGENTS.md` 更新为六业务库/七运行模块/34 模块并补集成子域说明。

### 6. 真实环境验证收口（`321a11ff`）
针对方案各批次报告 §5 长期挂账的「未验证」项补齐真实链路，**把此前只能靠单测推断的结论换成可复现证据**：
- `HttpConnectorLiveEndpointTest`（JDK HttpServer 起真实服务）：bearer/basic 认证头真实送达、GET 查询参数、201/500 处理、`healthCheck` 走 `healthPath`、不可达端点异常传播不静默成功。
- `IotMqttBrokerLiveTest`（真实 EMQX 5.8.6）：设备上行 QoS 1 载荷完整、租户 topic 隔离（A 收不到 B）、共享订阅 `$share/...` 可消费；采用可跳过设计，broker 不可达时跳过而非失败。
- `IotMqttThroughputBaselineTest`：1000 条 QoS=1 消息零丢失，单连接 331 msg/s（仅容量参考）。
- `TenantSqlRewriteDialectTest`：租户字面量解析、上下文缺失 fail-closed、平台表/业务表白名单、表名大小写不敏感、JSqlParser 可解析性。

### 7. EMQX ACL 缺口与真实启动缺陷修复（`bd818960`）
- **发现 broker 层无 ACL 强制隔离的真实缺口**：此前租户隔离仅靠 topic 命名空间，越权客户端可自行订阅他租户 topic。新增 `MqttAclRequest` 与 `IotInternalHandler.mqttAcl`，按 `username={tenantCode}/{deviceKey}` 解析身份调 `authorizeTopic`，返回 EMQX 规范 `{result: allow|deny}`，解析失败或越权一律 deny（fail-closed），并注册 `POST /internal/v1/iot/mqtt/acl`。真实 EMQX 配置 HTTP 授权源后确认 broker 实际拒绝（`authorization_permission_denied, source: http`）。
- **修复 4 个仅真实进程启动才暴露的装配缺陷**（单测无法发现）：`CredentialReferenceResolver` 缺 `@Component` 无 Bean；`RemoteApiClientConfig` 未声明 `@EnableConfigurationProperties(RemoteApiProperties)`，依赖消费方偶然覆盖；`ApiCallLogService` 未被扫描，补精确限定 `logging` 包的 `ComponentScan`（全包扫描会与 ConfigurationProperties 重复注册）—— **该项影响所有未扫描该包的消费方，受益面超出 integration-service**；`application.yml` 缺 `dynamic-datasource` primary 声明。
- 全栈冒烟（真实进程 + MySQL 9 + Redis 7）：50.5s 就绪、Flyway 建 5 张 iot 表、未认证 401、ACL 端点 allow/deny 正确。

### 8. JWT 篡改用例无效告警修正（`e3512557`）
原用例改写 JWT 末位字符模拟签名篡改，但 RS256 签名段 Base64URL 长度 342（`342 % 4 = 2`），末位字符只有低位有效，**改写末位可解码出相同签名字节，令牌仍合法**——是以编码特性制造的假阴性，不是验签漏洞。改为篡改签名段中间字符并补 payload 段用例，`IdentityRelayAuthenticationFilterTest` 11/11。

## 二、验证

**未执行**。本次日报生成过程只做只读采集（`git log`、`git status`、`.workbuddy/memory/` 与 `.workbuddy/knowledge/` 阅读），未运行任何构建或测试命令，故不推断任何门禁为通过。

当日提交信息中记录的验证证据如下，**均为提交者所述，本次未复跑**：

| 证据 | 记载结果 | 口径 |
| --- | --- | --- |
| `IntegrationDualDatabaseMigrationIntegrationTest` | 2/2 | 真实 MySQL 9 + PostgreSQL 15，10 表租户列与租户维度唯一键 |
| system 双库演练（扩展 V81–V84 断言） | 2/2 | 模块子集 |
| `ConnectorExecutionServiceTest` | 5/5 | 单元 |
| `BootAllAssemblyTest` | 4/4 | 覆盖 6 组件入口 |
| `IntegrationI18nConsistencyTest` | 3/3 | 19 条 × 4 语言 |
| `HttpConnectorLiveEndpointTest` | 6/6 | 真实 JDK HttpServer |
| `IotMqttBrokerLiveTest` | 3/3 | 真实 EMQX 5.8.6，未跳过 |
| `IotMqttThroughputBaselineTest` | 1/1 | 单连接 331 msg/s，零丢失 |
| `MqttTopicAclEnforcementTest` | 3/3 | 15 类越权订阅矩阵 |
| `IdentityRelayAuthenticationFilterTest` | 11/11 | 单模块 |
| 多连接并发基线 | 10 设备 × 100 消息 = 1000 条，零丢失，907 msg/s | 仅容量参考 |
| 全栈冒烟 | 50.5s 就绪、Flyway 5 表、401、ACL allow/deny | 真实进程 + MySQL 9 + Redis 7 |
| spotless / doc-links | 全仓通过 / 0 断裂 | 静态检查 |

**口径警示**：上表为**模块子集 `test`/`verify`** 与单次冒烟，**不等于全仓 `mvnd -T4 -B clean verify`**；引用时不得升格为「全仓验证通过」。吞吐数字不构成 SLA。

## 三、风险与待办

1. **全仓 `clean verify` 仍无当日记录**，且上期（11-01）发现的基线失败尚未见修复证据：`apex-system` 的 `OpenApiEndpointsIntegrationTest` / `ApiCallLogIntegrationTest` 因 `JobMapper` 无 Bean（源自 `e17fe4c`）在 HEAD 失败。今次 `bd818960` 补了 `ApiCallLogService` 扫描，可能与之相关但**未验证**，次日第一切口应是在干净工作区跑一次全仓 verify 确认该断言。
2. **工作区为文档重构中间态（未提交）**：`git status` 10 项（3 修改 + 5 删除 + 2 未跟踪），实际未跟踪 25 个文件——`weekly-reports/` 5 个文件被删、`periodic-reports/{daily,weekly,monthly}` 与 `reports/README.md` 新增。`README.md`、`.gitignore`、`docs/08-project-management/README.md` 同步改动。**不可只提交其中一部分**，否则索引与目录结构断裂。
3. **知识库与长期记忆漂移**：`MEMORY.md`「当前主线」仍写「E1 在途未跟踪」，`knowledge/06` 仍标 E1 未跟踪、reactor 33 模块、9 个入口；实际今日已 E1–E4 全入库、34 模块、10 入口、D 批 D5 完成。需回写（本次日报按边界未改这两处）。
4. **C4 未执行**，M3 无法收官：`apex-system` interfaces 层（21 Router / 44 Handler 量级）仍在业务库，四层合一结构未变。
5. **真实环境验证仍有保留项**：EMQX ACL 在**多连接压测**下的强制效果、Compose 全栈冒烟、第三方真实联调（微信/创蓝）、前端页面均未验证；`IotMqttBrokerLiveTest` 为可跳过设计，CI 无 broker 时会静默跳过，**不可将「未失败」当作「已通过」**。
6. **前端未跟进**：`apex-admin-web` 当日 0 提交，工作区 5 修改 + 10 新增未提交（`api/capability.ts`、`integration.ts`、`iot.ts`、`notify.ts`、`open-app.ts`、`session.ts`、`tenant-package.ts`，`views/monitor/session.vue`、`views/oauth2/consent.vue` 等）。后端今日已开放的 integration/iot、能力用量、开放路由改动用例在**前端尚未联调**，是端到端风险最集中的一段。
7. **次日切口（按优先级）**：① 干净工作区全仓 `clean verify`，先定 `JobMapper` 基线问题；② 提交文档重构中间态；③ 回写 `MEMORY.md` 与 `knowledge/06` 的批次状态与结构性计数；④ 启动 C4。
