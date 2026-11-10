# 中台架构评审报告（2026-11-09）

本报告按当前源码、模块依赖、契约与装配配置评审 Apex Scaffold 是否按"中台"方式建设，不以历史文档、迁移阶段命名或已通过的测试记录作为依据。评审只做取证与判断，不修改实现；对应整改建议见 [中台架构整改方案](../plans/middle-platform-remediation-plan.md)（待批准）。

## 1. 评审口径

"微服务化"回答的是**服务怎么拆、怎么通、怎么部署**；"中台"回答的是**能力是否被多个业务稳定复用，并且可治理、可演进、可运营**。两者不是同一件事，本报告分开判断：

| 概念 | 判定依据 | 不能替代中台的理由 |
| --- | --- | --- |
| 微服务 | 服务拆分、网关、远程通信、独立部署 | 拆得再细，共享能力仍可能无人消费或无治理 |
| DDD | 限界上下文、领域归属、端口/适配器 | 分层正确不等于能力被多业务复用 |
| 共享 JAR / Starter | 编译期复用、自动装配 | 复用无契约门禁、无 owner、无版本策略时只是代码共享 |

中台能力的判据取八项：**能力目录、多业务复用、领域边界、技术底座、数据与权限、可观测与稳定性、交付质量、运营治理**。每项只按已取得的代码证据下结论，证据不足单列为"未取得"，不写成"不存在"。

不使用百分制或成熟度评分：本仓库没有可支撑分数的度量基线，任何数字都会变成无依据的性能宣称。

## 2. 结论摘要

- **服务化与 DDD 分层已落地**：35 个模块的 POM 声明层无循环依赖，六业务库 + 七运行模块 + 五个共享设施能力 JAR 的边界与 [AGENTS.md](../../../AGENTS.md) 记录一致，`apex-infrastructure` 仅作旧坐标兼容壳。
- **"能力 + 门禁"这条中台主干链路是真实存在的**：开放平台从网关验签、上下文中继、scope 断言、租户能力订阅门禁，到服务侧二次校验形成纵深防御，两端判定收敛到共享的 `CapabilityGate.subscribed()`，能力快照来源唯一。
- **差距集中在"治理"而不是"拆分"**：能力目录缺少 owner / 版本 / 消费者 / SLO 字段；服务侧纵深防御默认关闭；两端能力客户端逻辑重复；已有架构守卫的覆盖范围小于其命名暗示的范围；auth 域存在 Domain→Application 反向依赖与演示态服务实现。
- **多项内容属于既有裁决而非缺陷**：D07（system 接口层留在库内）、C7（integration 自含组件试点）、D21/D22/D24/D25 等已记录取舍，本报告不重复判错。

## 3. 八维度评估

### 3.1 能力目录

符合：`CapabilityCatalog` 定义了八项能力编码（`identity-auth`、`user-org`、`workflow`、`file-storage`、`office-message`、`tenant-governance`、`integration-connector`、`iot-device`），开放 scope 与能力编码的对应关系由 `OpenAppScopes` 维护，六个 scope 到能力的映射可读。

差距（已证实）：能力条目是纯 `record`，只有编码、名称一类展示字段，**没有 owner、版本、消费者清单、SLO、生命周期状态**。能力编码因此只够做"开关判定"，不足以做"能力运营"——无法回答谁负责、谁在用、下一版什么时候能下线旧行为。后三项能力 `openScopes` 为空，说明它们尚未对外暴露，但目录里没有字段表达这一状态。

差距（证据不足）：是否另有未入库的能力台账（表格、外部系统）无法从仓库确认。

### 3.2 多业务复用

符合：`apex-api-contract` 承载跨服务内部 API 与 DTO；五个共享设施能力 JAR（persistence / cache / messaging / remote / integration）按能力切分而非按层打包；`CapabilityGate`、`TenantFeatures`、`BlockingIoSchedulers` 等被多个模块共同引用。

差距（已证实）：**复用缺少可核对的消费关系**。仓库内没有"能力 → 消费方"的登记，评审只能逐处反查引用，无法在变更前判断影响面。`apex-example` 的 POM 直接依赖 `system`、不含 `workflow`（连 test scope 也没有），与 [AGENTS.md](../../../AGENTS.md) 依赖表的表述已经漂移——这本身说明依赖关系没有被机器校验。

差距（证据不足）：真实业务方是否在仓外消费这些能力，无证据。

### 3.3 领域边界

符合：六业务库各自持有领域模型与端口，运行模块只做装配；`apex-boot-all` 只装配不含业务实现；`apex-common`、共享 domain/infrastructure、`apex-api-contract` 没有反向依赖具体业务库（POM 声明层核对通过）。

差距（已证实）：

1. `apex-auth` 的 `domain/service/ResourcePermissionService` 反向依赖 `application` 层的 `UnifiedAuthorizationQueryService`，并在 domain 内直接持有 `ReactiveRedisTemplate`。这是分层倒置，不是"Maven 模块环"——同一模块内的包依赖，不构成 reactor 环，但对领域层的可测试性与替换性有实质影响。
2. 该类的 `authorizationQueryService == null` 分支直接返回 `true`（放行），`checkOrganizationPermission()` 一律返回 `false`。**这是 fail-open 与未实现两种语义混在同一个类里**，且没有标记为未实现或抛出异常。
3. `apex-auth` 的 `application/service/ResourcePermissionService` 是演示态实现：硬编码示例权限、CRUD 为空、缓存键 `resource:permission:{userId}:{type}:{id}:{action}` 不含租户维度、生产代码零调用方（仅测试实例化）。

差距（守卫覆盖不足）：`PersistenceContractTest.domainDoesNotDependOnPersistenceOrFrameworks` 没有禁止 domain 依赖 application，也没有禁止 Spring Redis，因此上面第 1 项能通过现有门禁。`ContextBoundaryArchTest` 的 `CONTEXTS` 只覆盖 auth / workflow / file / office 四域，未覆盖 system 与 integration，且目录缺失时直接 `continue`——守卫的实际覆盖小于名称暗示。

### 3.4 技术底座

符合：Java 21 + Spring Boot + WebFlux + Reactor 的响应式技术栈贯穿网关与业务服务；函数式 Router + Handler 为新增 HTTP 接口的既定形态；阻塞 I/O 统一收敛到 `BlockingIoSchedulers` 并在调用边界 `subscribeOn`；MyBatis-Plus + Flyway 按域独立版本链；RocketMQ + Transactional Outbox 提供跨服务一致性出口；Seata 四模式默认关闭、按需开启。

差距（已证实）：两端能力客户端 `TenantCapabilityVerifier`（`apex-common`）与 `TenantCapabilityClient`（`apex-gateway`）几乎逐行重复——`Snapshot` 记录、`parse()`、`fresh()`、`fetch()` 各自实现一份，只有 `CapabilityGate` 是真正共享的。**这就是"口径统一"声明与实际复用的落差**：判定函数统一了，解析与缓存没有统一，任一侧调整响应结构都会产生单边漂移。

### 3.5 数据与权限

符合：租户作用域来自受信任认证上下文，网关在 `JwtSignatureFilter` 之后由上下文中继写入，外部伪造的可信头被剥离；多租户行级隔离 `DefaultTenantLineHandler` 默认开启；workflow / auth 为 JdbcTemplate 持久化，保留手写的 fail-closed 租户访问器；JWT / 开放令牌的授权码 + PKCE 流程已接入。

差距（已证实）：**两端开关默认值相反**。

| 位置 | 配置键 | 默认值 | 关闭后行为 |
| --- | --- | --- | --- |
| 网关 | `apex.gateway.capability.enabled` | `true` | `/open-api/**` 仅做 scope 断言，不再查能力订阅 |
| 服务侧 | `apex.open-api.guard.enabled` | `false` | 守卫不生效，标记头与路径推导都跳过 |

服务侧 `OpenApiGuardProperties` 的 javadoc 声明"外部伪造值由网关剥离，业务服务据此做能力订阅二次校验"，但默认关闭意味着**默认配置下纵深防御只有一层**，绕过网关直连服务即可跳过能力门禁。这与"纵深防御已具备"的表述在默认部署下不一致，应登记为"能力已具备、默认未开启"，而不是"已生效"。

差距（证据不足）：`JwtSignatureFilter` 剥离外部伪造头的具体实现细节、`auth_config` 作为开放应用注册签发单源（D22）的实现、能力计量入口与计量维度，均未在本轮取得代码证据。

### 3.6 可观测与稳定性

符合：网关过滤器顺序明确（验签 10 → 上下文中继 20 → scope 与能力 25 → 限流 30），拒绝路径有独立的 `ResultCode`（`OPEN_PERSONAL_TOKEN_REJECTED`、`OPEN_SCOPE_INSUFFICIENT`、`CAPABILITY_NOT_SUBSCRIBED`），能力查询失败按 503 fail-closed；`CapabilityGate` 对 `exists=false` 的租户返回未订阅。

差距（已证实）：能力快照缓存 TTL 两侧都是 30s，`exists=true` 但 `features` 为空的租户会被判为**订阅全部能力**（`CapabilityGate` 中 `codes.isEmpty()` 即全量），`unbounded=true` 同样全放行。这是有意设计的"不限量租户"语义，但它与"未配置订阅即全部可用"共用同一个返回值，只在 `exists=false` 时才拒绝——配置侧一旦漏填 `features`，结果是从"未订阅"变成"全订阅"，没有日志能区分这两种情况。

差距（证据不足）：能力门禁拒绝率、缓存命中率、快照拉取失败率是否有面板与告警；备份恢复、RocketMQ / EMQX / Seata TC 的真实验收记录；告警触达链路。这些都需要运行期证据，仓库内未取得。

### 3.7 交付质量

符合：父 POM 绑定 Spotless、PMD(p3c)、JaCoCo、Failsafe；架构守卫覆盖分层、租户隔离、时间字段类型、网关包边界、扫描声明可加载性等；Flyway 有 MySQL/PostgreSQL 版本集合一致校验。

差距（已证实）：守卫的**命名范围大于实际范围**是系统性问题，不只是 auth 一处：

- `PersistenceContractTest` 只管持久化边界，不管 domain→application 反向依赖。
- `ContextBoundaryArchTest` 只覆盖四域，缺目录静默跳过。
- `SystemServiceBoundaryTest`、`IntegrationServiceBoundaryTest` 只校验扫描声明与类可加载，不启动服务。

因此"A 检查通过"不能推出"B 约束成立"。评审中所有"符合"结论只在其被测范围内成立。

### 3.8 运营治理

差距（已证实）：能力目录无 owner、无版本、无消费者、无 SLO、无下线策略；没有能力准入/准出流程的落地物（评审单、变更记录、灰度策略）；开放应用注册虽有 D22 裁决，但注册源到能力授权的链路缺少可核对的审计记录。

差距（证据不足）：是否有仓外的治理流程（会议、台账、工单），无证据。

## 4. 符合项（可核对证据）

| 编号 | 符合项 | 证据位置 |
| --- | --- | --- |
| C1 | 35 模块 POM 声明层无循环依赖，无反向依赖业务库 | 各模块 `pom.xml` |
| C2 | 六业务库 + 七运行模块 + 五共享设施 JAR 的边界与 AGENTS.md 一致 | 根 [pom.xml](../../../pom.xml)、[AGENTS.md](../../../AGENTS.md) |
| C3 | 开放链路纵深防御顺序明确：验签 → 上下文中继 → scope/能力 → 限流 | `JwtSignatureFilter`、`OpenApiScopeGatewayFilter`（ORDER=25） |
| C4 | 两端能力判定收敛到同一实现 | `CapabilityGate.subscribed(TenantFeatures, String)` |
| C5 | 能力快照单一来源 | system `GET /internal/v1/tenants/{code}/capabilities` |
| C6 | scope 断言 fail-closed（无元数据或无 scope 即 403） | `OpenApiScopeGatewayFilter` 的 `scopeSatisfied` 计算 |
| C7 | 服务侧按路径独立推导能力，防标记头篡改 | `OpenApiCapabilityGuardFilter`（ORDER=-200）的 `resolveRouteCapability()` |
| C8 | 阻塞 I/O 隔离到专用调度器 | `BlockingIoSchedulers` 及 `subscribeOn` 用法 |
| C9 | Flyway 按域独立版本链 + 双数据库版本集合校验 | 各域 `db/migration/{mysql,postgresql}` |

## 5. 已证实差距

按影响面排序，不按修复难度：

| 编号 | 差距 | 性质 | 影响 |
| --- | --- | --- | --- |
| G1 | 服务侧 `apex.open-api.guard.enabled` 默认 `false`，与网关 `true` 相反 | 默认值不对称 | 默认部署下纵深防御只有一层，绕过网关即可跳过能力门禁 |
| G2 | auth domain→application 反向依赖 + domain 持有 `ReactiveRedisTemplate`，守卫未覆盖 | 分层倒置 + 守卫缺口 | 领域层不可独立替换；该类含 fail-open 分支 |
| G3 | `ResourcePermissionService`（application 层）为演示残留，缓存键缺租户维度 | 死代码 + 潜在越权 | 一旦被接入即产生跨租户权限缓存串用 |
| G4 | 能力目录缺 owner / 版本 / 消费者 / SLO / 生命周期 | 治理缺失 | 无法评估影响面、无法下线旧行为 |
| G5 | 两端能力客户端重复实现（Snapshot/parse/fetch） | 重复代码 | 响应结构变更易产生单边漂移 |
| G6 | `CapabilityGate` 将"空 features"与"全量订阅"合并为同一返回值 | 语义合并 | 配置漏填时从"未订阅"变成"全订阅"，无日志区分 |
| G7 | 架构守卫覆盖小于命名范围（四域 vs 全量、边界测试不启动服务） | 门禁覆盖不足 | 通过门禁不能推出约束成立 |
| G8 | `apex-example` POM 依赖与 AGENTS.md 依赖表漂移 | 文档/代码漂移 | 依赖关系无机器校验 |

## 6. 合理取舍（不判错）

| 编号 | 取舍 | 判断依据 |
| --- | --- | --- |
| D07 | system 的 Router/Handler 留在业务库内，不迁到运行模块 | 既有裁决，装配链已按此验证，迁移收益低于风险 |
| C7 | integration 采用自含组件试点，受 `apex.component.integration.interfaces-enabled` 开关守护 | 试点形态明确、开关可关闭，不是边界失守 |
| D21 | 开放主体统一用 `subjectUserId` / `sub_uid` / `X-Open-Api-Subject` | 单一标识，避免多套主体字段并存 |
| D22 | `auth_config` 为开放应用注册签发单源 | 单源注册，避免多处写凭据 |
| D23 | 普通装配与 exec 产物分离，boot-all 用分域子上下文 | 避免单进程装配时域间 Bean 串扰 |
| D24 | file 开放写以绑定主体为属主 | 由授权主体决定归属，而非客户端传参 |
| D25 | 一次性迁移 + 一个发布周期只读比对窗口 | 迁移策略明确，非长期双写 |

`apex-infrastructure` 仅保留旧坐标兼容壳、`apex-tools-*` 不进运行期装配，同属有意的结构取舍，不计入差距。

## 7. 证据不足（本轮未取得）

以下项目**不能**写成"已通过"或"不存在"，只表示本轮未取得可核对证据：

1. `JwtSignatureFilter` 剥离外部伪造头的实现细节（仅由 javadoc 与过滤器顺序间接证明）。
2. `auth_config` 作为开放应用注册签发单源（D22）的实现代码。
3. 能力计量入口与计量维度（是否按 tenant + capability + client 计量）。
4. UserChanged / TenantChanged 事件的同事务 Outbox 写入、消费幂等与失败重试。
5. workflow 发布事件与仓内消费者的对应关系。
6. `apex-boot-all` 六域子上下文的数据源、Flyway、安全隔离及对应测试。
7. RocketMQ / EMQX / Seata TC 真实环境联调、告警触达、备份恢复验收。
8. 多业务真实消费方、能力 owner、SLO、版本与下线治理的运营证据。
9. 组织层级权限（`checkOrganizationPermission()` 一律 false）应视为未实现还是有意拒绝，需业务确认。

## 8. 本轮验证范围

- 已执行：源码、POM、配置类、架构测试、既有评审报告的静态核对。
- 未执行：Java 构建、单元/集成测试、服务启动、真实联调、文档链接检查、`git diff --check`。
- 因此本报告中所有"符合"仅指静态证据成立，不代表运行期行为已验证。
