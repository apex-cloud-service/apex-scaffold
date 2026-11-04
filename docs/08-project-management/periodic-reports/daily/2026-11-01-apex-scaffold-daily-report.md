# 工作日报（2026-11-01）

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

**项目**：apex-scaffold（本仓库后端）
**主线**：《统一授权与多租户平台整治方案》v3.1 —— B 批（多租户）收官、C 批（组件化服务化）除 C4 外贯通、D 批（中台能力）前四项落地

## 一、今日完成

当日提交 **10 次**，涉及 **89 个唯一文件**（90 条变更：46 新增 / 30 修改 / 14 删除；差额为 C0–C2 报告同日先建后修），覆盖 **15 个 `apex-*` 模块**、13 份文档与 3 个仓库根文件。

### 1. 多租户完整化收口（B7 / B8 / B9，M2 收官）

- **B7 隔离默认开启与 office 拦截链收敛**（`e6aece83`）：`apex.tenant.enabled` 由"仅 enterprise 档开启"改为三服务（system/file/office）`application.yml` 全档默认 `true`，basic 档显式置 `false` 保留离线最小档；删除 office 专用的 `MessageTenantConfig`，改由共享 `MybatisPlusConfig` 的 `DefaultTenantLineHandler` 统一接管。**解决的问题**：此前 office 与共享配置并存两个 `MybatisPlusInterceptor` Bean，且隔离策略与 system/file 口径不一致，存在漏隔离表与拦截链行为分叉的风险；统一后"不进白名单即参与隔离"，口径与其余业务库一致。
- **B8 租户管理员自助管理**（`08f811bd`）：新增 MySQL/PostgreSQL 成对迁移 `V80__register_tenant_self_service_menus.sql`，注册 `tenant-user`/`tenant-role`/`tenant-dept` 三项菜单与权限码；`TenantGuard.assertPlatformTenant()` 落地"平台租户"第二道边界，`PlatformTenantHandler` 的 create / changeStatus / updateQuota 三个写入口接入。**解决的问题**：租户管理员此前无自助入口、且平台级写操作仅有权限码一道防线；同时通过迁移尾部重算 `platform-full` 套餐种子，消除了"新增菜单必须回改历史迁移 V78"的僵局（Flyway 禁改历史迁移）。
- **B9 租户化缓存键助手**（`b9057479`）：新增 `TenantCacheKeys`，统一键规则 `<prefix>:tenant:<tenantCode>:<segments>`，四个 API（`of` / `forTenant` / `platform` / `isTenantScoped`），**不允许生成无租户维度的键**（上下文缺失 fail-closed）。**解决的问题**：各模块自行拼接缓存键导致键空间漂移与跨租户串读的隐患；平台级缓存须显式声明 `platform` 段，便于审计识别跨租户缓存。此批为 M2 多租户完整化的收官项。

### 2. 组件化服务化（C0–C3 / C7–C9，M3 除 C4 外贯通）

- **C0 DDD 基线**（`3870c433`）：新增限界上下文映射 `docs/02-architecture/context-map.md`（6 上下文、关系图、合作模式表）与 `ContextBoundaryArchTest`。**关键取舍**：ArchUnit 字节码导入不可行（无任何模块同时依赖多个业务库），改为源码级 import 扫描；首次运行即通过，反证现有跨上下文耦合全部经 `domain/gateway` 契约与 `/internal/v1/**` 防腐层。**解决的问题**：DDD 分层从"文档约定"变为可执行门禁。
- **C1 starter JAR 级裁剪**（`3870c433`）：`xxl-job-core` 与 `seata-all` 改为 `<optional>true</optional>`，对应自动配置加 `@ConditionalOnClass(name = "...")` 字符串形式。**解决的问题**：此前 basic 档只是"Bean 不装配"，实现 JAR 仍常驻 classpath、无法真正裁剪；改用字符串形式避免类缺失时的加载期 `NoClassDefFoundError`。
- **C2 api-contract 去死代码**（`3870c433`）：删除 3 组 `@FeignClient` + 3 个 FallbackFactory + `FeignReactiveBridge` / `FeignTenantTraceConfiguration` / `FeignClientConstants` 及 5 个测试，移除 openfeign 依赖，同步清理 2 处 javadoc 引用。**解决的问题**：全仓 `@FeignClient` 外部消费为零的僵尸契约长期占据共享契约模块，误导"内部调用走 Feign"的错误认知。
- **C7 业务库组件入口自动装配**（`c9a471fb`）：auth/file/workflow/office 四库新增 `*ComponentAutoConfiguration` + `AutoConfiguration.imports`，采用"总开关 `apex.component.<name>.enabled` + 协议层子开关 `interfaces-enabled`"双层结构。**解决的问题**：消费方不必再手工 `scanBasePackages`；`interfaces-enabled` 为组合部署提供"只装配领域/应用/基础设施、HTTP 入口由其它组件承载"的能力。过程踩到并修掉两个真实缺陷：组件根包扫描会把入口类自身扫入而触发 Spring 循环 `@Import`（需显式排除 `autoconfigure..*`）、切片测试被组件装配污染导致 Mapper Bean 缺失。
- **C3 存储 SPI 扩展点 / C8 事件通道自适应**（`eb1610a8`）：`StorageProviderLoader` 以 `ServiceLoader` 发现第三方 `ObjectStorage`，第三方以独立 JAR + `META-INF/services` 声明即可接入，加载失败逐条跳过不阻断内置后端；新增 `EventChannelMode`（AUTO/LOCAL/REMOTE）与 `apex.events.channel`，`AUTO` 依"是否具备跨服务投递能力"自动判定。**解决的问题**：新增存储后端（华为 OBS、Azure Blob 等）此前必须改 starter 源码；事件投递此前靠各上下文各自的 `@ConditionalOnBean` 隐式约定，切换拆分/组合部署需改代码，现在业务代码零感知、非法配置值回退 `AUTO` 不阻断启动。
- **C9 组合部署拓扑 `apex-boot-all`**（`b6e44a29`）：新增单进程装配全部业务组件的部署档，`BootAllApplication` **刻意不使用 `scanBasePackages` 全根扫描** —— 组件全部经 C7 的自动装配入口进入，启动类本身就是组件化能力的验证载体；已注册进根 POM。**解决的问题**：basic/示例/演示档此前无可用的单进程形态，且"组件化是否真的自洽"缺少端到端证据。

### 3. 中台能力（D1 / D2 / D4 / D6）

`ddd2a25b` 一次性交付四项，构成 M4 中台的首个可用切片：

- **D1 统一消息门面**：`NotifyApplicationService` + `NotifyChannelPort` 收敛为唯一发放入口，`InboxNotifyChannel` 复用既有 `OffMessageService` 落库路径避免双写，`ImNotifyChannel` 覆盖 FEISHU/DINGTALK/WECOM，模板渲染走 `NotifyTemplateRenderer`（缺失参数原样保留、不静默输出空串、无表达式执行）。**解决的问题**：站内信/IM/短信/邮件此前各自为政、无统一投递契约与失败语义。
- **D2 开放平台**：`OpenAppScopes` 注册 6 个开放 scope 并给出 scope→服务侧权限码映射与 `covers` 收窄校验（RFC 6749 §3.3），`OpenAppRouter` / `OpenAppHandler` 提供 `/admin-api/v1/open-apps` 的管理面。**设计取舍**：复用认证中心既有 `OAUTH2_CLIENT` 注册表，不新建第二套客户端体系。
- **D4 能力目录**：`CapabilityCatalog` 登记 8 项能力（含归属中心、依赖组件、开放 scope），`CapabilityRouter` 暴露 `/api/v2/capabilities` 与 `/subscribed`，订阅判定复用 B3 的套餐 `features`（空 = 全量）。**过程纠偏**：初版 `subscribedByPackage` 恒返回 `true`（占位实现），已改为真实读取套餐 features —— 避免"看起来能用但实际不裁剪"的伪实现。
- **D6 接入契约文档**：新增 `docs/03-api/open-platform-integration.md`，并**如实声明** `/open-api/**` 网关路由与 scope 断言尚未实现。

### 4. 文档与上下文治理

- 方案主文档 `unified-auth-tenant-platform-remediation-plan.md` 随每批次同步更新（今日 6 次），批次状态与代码保持同源。
- `MEMORY-CARD.md` 入库（`cf140efa`），**单文件原子提交**，其余 12 项在途变更未动；入库前先修正卡片自身工作树计数漂移，并回改 `CARDS.md`、`PROJECTS.md` 保持三处同源。**解决的问题**：卡片此前在仓库根目录却未被跟踪，与"卡片随仓走"的规则自相矛盾。
- `2c8a003a` 修正 C0–C2 报告中上下文映射文档的相对路径（多退一级的断链）。

### 未提交改动（工作区 12 项，与上述已提交内容严格区分）

- **修改 4 项**：`pom.xml`（新增 `<module>apex-integration</module>`）、`.gitignore`（报告产物忽略规则由 `daily-report-*` 改为根目录锚定 `/daily-report-*`，避免误伤归档目录下的 `.md/.html/.docx`）、`README.md`、`docs/08-project-management/README.md`。
- **删除 5 项**：`docs/08-project-management/weekly-reports/` 下 `2026-W43-apex-scaffold.md`、`2026-W43-cold-chain-park-supply.{md,doc,docx}` 与 `README.md` —— 周报归档目录重构，尚未提交。
- **新增未跟踪 3 项**：`apex-integration/`（E1 连接器框架 + V1 迁移）、`docs/08-project-management/periodic-reports/`（周期报告新目录）、`docs/08-project-management/reports/README.md`。

> 注：`pom.xml` 已声明 `apex-integration` 模块但模块目录尚未跟踪，当前工作区处于"根 POM 与目录状态不一致"的中间态，提交前需整体入库，不可只提交 POM。

## 二、验证

**未执行。** 本次日报生成按要求不运行全量构建或测试（耗时且可能污染工作区、干扰在途改动），因此**不对当日代码质量作任何通过性结论**。以下仅为各批次测试报告中**已记录**的证据，属报告记载而非本次复跑：

| 批次 | 报告记载的门禁 | 口径 |
| --- | --- | --- |
| B8 | 双库演练 2/2（63.98s）；六模块及上游 `test` BUILD SUCCESS | MySQL 9.0 / PostgreSQL 15 容器 |
| B9 | `TenantCacheKeysTest` 5/5；七模块及上游 `test` BUILD SUCCESS；spotless 通过 | 模块子集 `test` |
| C0–C2 | `ContextBoundaryArchTest` 1/1；七模块及上游 `test` BUILD SUCCESS；doc-links 0 断裂 | 模块子集 `test` |
| C3/C8 | `StorageProviderLoaderTest` 4/4、`EventChannelModeTest` 4/4；四模块 BUILD SUCCESS | 模块子集 `test` |
| C7 | 四业务库+四 service BUILD SUCCESS | 四 service `-am test` |
| C9 | `BootAllAssemblyTest` 4/4；`apex-boot-all -am test` BUILD SUCCESS | 模块子集 `test` |
| D1/D2/D4/D6 | `NotifyFacadeTest` 5/5、`CapabilityCatalogTest` 5/5、`TenantPackageServiceTest` 6/6；五模块 `test` BUILD SUCCESS | 模块子集 `test` |

**口径警示**：上表全部为**模块子集 `test`**，不等同于全仓 `mvnd -T4 -B clean verify`。截至日报生成时，当日**无任何全仓构建通过的记录**。

## 三、风险与待办

### 阻塞与未覆盖项

1. **`apex-system` 存在基线失败测试**：`OpenApiEndpointsIntegrationTest` / `ApiCallLogIntegrationTest` 在 HEAD 基线即失败（`JobMapper` 无 Bean，由 `e17fe4c` 引入），C7 批次已用 `git stash` 验证与本批无关。这意味着**全仓 `clean verify` 当前不可能为绿**，任何"全量通过"的表述都不成立。该缺陷已是跨日遗留，建议优先修复。
2. **开放平台"可注册不可调用"**：D2 只交付注册与管理接口，`/open-api/**` 网关路由组、scope 断言过滤器、client 限流档均未实现。这是 M4 的关键断点，也是 `open-platform-integration.md` 中已声明的缺口。
3. **C9 组合拓扑的核心验证缺失**：只验证了装配前提（类可见性、装配清单、组件扫描），未做 `java -jar` 真实启动冒烟；五组 Router 同进程的路径/端口冲突、五组件 `application.yml` 属性合并均"预期无冲突但未实测"。
4. **C4 未执行 → M3 未收官**：`apex-system` 仍是四层合一巨库（`interfaces`/`application`/`domain`/`infrastructure` 同包，21 Router / 44 Handler 量级），`apex-system` 也因此无法获得 C7 组件入口（其 interfaces 包名在业务根包之外）。
5. **E1 `apex-integration` 未跟踪、无测试报告**，不得计入已交付；`pom.xml` 已引用该模块，处于中间态。
6. **B9 既有缓存键未迁移**：仅交付助手与规范，`UnifiedAuthorizationService` 的手拼 base64 键等既有缓存点未逐一改造（改造涉及失效策略与滚动升级兼容性，需独立批次）；对应架构守卫因"先迁后守"的顺序约束未新增。
7. **C1 JAR 级裁剪的实证构建未执行**：方案要求的"basic 档排除 xxl/seata 后 `clean verify` 通过"未做，已顺延至 C7/C9 验证项且仍未闭环。
8. **P0 迁移验证仍未准出**：`verified_union` 滚动覆盖未达 586/586，Java 21 正式切换结论未收口。属口径问题而非代码问题，但不能以"已迁移 232 个文件"替代该结论。
9. **归档目录重构未提交**：weekly-reports 删除与 periodic-reports 新增同处工作区，索引引用面尚未回归；`2026-10-31-apex-scaffold-daily-report.docx` 与重写后的 md 内容已不一致，待决定重生成或清理。

### 次日切口（建议顺序）

1. 修 `JobMapper` 缺失 Bean 的基线失败 —— 这是恢复全仓门禁可信度的前置条件。
2. 提交 E1（含 `apex-services-bom` 装配、集成库迁移与测试报告），并与 `/open-api` 网关路由同批验收。
3. 执行 C4，收 M3；随后补 C9 真实启动冒烟与拓扑矩阵。
4. 启动 D5 共享数据服务层，形成 M4 完整切片。
