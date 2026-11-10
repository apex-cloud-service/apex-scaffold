# 中台架构整改方案（2026-11-09 制定，2026-11-10 实施）

本方案针对 [中台架构评审报告](../reports/middle-platform-architecture-review.md) 中编号为 G1–G8 的已证实差距，给出具体措施、实施顺序与验收条件。

> **2026-11-10 实施完成说明**：G1–G8 已按本方案进入代码与静态契约关闭；定向回归与完整 `clean verify` 均已执行并通过（6,251 个测试、0 失败、0 错误、33 跳过）。真实 Nacos/MySQL/Redis、RocketMQ/EMQX、Seata TC、告警触达、备份恢复与第三方消费方验收仍属未验证事项，详见[中台整改测试报告](../../06-testing/middle-platform/20261110-completion/report.md)。本页后续各节保留原始方案文本，作为实施依据与变更记录。

## 1. 适用范围与前提

- 只处理报告 5 节列出的 G1–G8。符合项 C1–C9 保持不动，合理取舍 D07、C7、D21–D25 及 `apex-infrastructure` 兼容壳、`apex-tools-*` 不进运行期等结构决定不重开讨论。
- 不重复 F1–F12 已处置项（见 [模块架构评审报告](../reports/module-architecture-review-report.md)）。
- 报告 7 节“证据不足”九项不在本方案范围内：未取得证据的问题先补证，不预置改造动作。
- 每项改造都要能在现有门禁（`mvnd -T4 -B clean verify`）下验证；不能验证的运行期行为（MQ、TC、告警、备份）在验收条件中明确标注为“需真实环境另行确认”，不得计入本方案通过项。

## 2. 分级与实施顺序

| 阶段 | 范围 | 目标 | 依赖 |
| --- | --- | --- | --- |
| P1 | G1、G6 | 消除默认配置下的门禁缺口，明确“空订阅”语义 | 无 |
| P2 | G2、G3 | 清理 auth 域分层倒置与演示残留 | P1 完成后进行，避免与门禁开关变更交织 |
| P3 | G4、G5、G7、G8 | 补齐治理字段、收敛重复实现、扩大守卫覆盖、修复依赖表漂移 | G1/G2 定稿后同步开展 |

P1 优先的原因：G1 使默认部署的纵深防御只剩一层，G6 使配置漏填静默放大权限，两者都不依赖其他改造即可独立修复，收益最直接。

## 3. P1 项

### G1 服务侧能力二次校验默认关闭

**现状证据**：`OpenApiGuardProperties.enabled = false`（`apex-common`），`TenantCapabilityProperties.enabled = true`（`apex-gateway`）；服务侧 javadoc 声明“外部伪造值由网关剥离，业务服务据此做能力订阅二次校验”，与默认关闭自相矛盾。报告 3.5 节。

**目标**：默认部署下，绕过网关直连暴露开放能力的服务同样受能力订阅门禁约束。

**实施步骤**：
1. 将 `OpenApiGuardProperties.enabled` 默认值改为 `true`。
2. 逐个核对承载 `/open-api/**` 的服务运行模块（当前为 file、workflow、system、office）的 `application*.yml`，确认没有残留的 `apex.open-api.guard.enabled: false`；确需关闭的实例必须在配置注释中写明原因与补偿措施。
3. 检查守卫在无开放流量时的行为：`pathPrefix` 之外的请求不得因开关打开而产生额外远程调用。
4. 同步服务配置文档中的默认值说明。

**验收条件**：
- 单元/集成测试覆盖“开关为默认值时，携带 marker 头且租户未订阅 → 403”与“非开放路径 → 不触发能力查询”。
- 四个服务的配置文件中不存在隐式关闭。
- `mvnd -T4 -B -pl apex-common,apex-file-service,apex-workflow-service,apex-system-service,apex-office-service -am test` 通过。

**影响面**：默认行为变化，依赖“服务侧不校验”的部署需显式关闭；升级说明中必须列出该变更。

### G6 “空 features”与“全量订阅”语义合并

**现状证据**：`CapabilityGate.subscribed(TenantFeatures, String)` 中 `codes.isEmpty() || codes.contains(code)`，`exists=true` 且 `features` 为空即视为订阅全部；`unbounded=true` 同样全放行。报告 3.6 节。

**目标**：把“不限量租户”“未配置订阅”“显式订阅集合”三种状态区分开，配置漏填不再静默变成全量授权。

**实施步骤**：
1. 明确语义归属：不限量只由 `unbounded=true` 表达；`unbounded=false` 且 `features` 为空视为“未订阅任何能力”。
2. 修改 `CapabilityGate` 判定，并在两侧客户端的解析处对“`unbounded=false` 且 `features` 为空”记录一次可观测的日志（含租户编码，不含敏感数据）。
3. 核对 system 侧能力快照端点对 `features` 为空的返回是否为有意状态；若是数据缺陷，需在数据侧修正而非放宽判定。
4. 更新涉及该语义的既有测试与文档表述。

**验收条件**：
- 新增单测覆盖 `unbounded=true`、`unbounded=false + 空 features`、`unbounded=false + 非空 features` 三种组合的判定结果。
- 网关与服务侧对同一快照得出相同判定（同一测试用两侧客户端分别断言）。
- 配置漏填场景在日志中可识别。

**影响面**：可能使部分原本“隐式全量”的租户被拒；上线前须以生产租户快照做一次离线比对，比对结果作为发布前置条件。

## 4. P2 项

### G2 auth 域 Domain→Application 反向依赖

**现状证据**：`apex-auth` 的 `domain/service/ResourcePermissionService` 依赖 application 层 `UnifiedAuthorizationQueryService`，并在 domain 内持有 `ReactiveRedisTemplate`；`authorizationQueryService == null` 返回 `true`，`checkOrganizationPermission()` 一律 `false`。现有 `PersistenceContractTest` 未禁止这两类依赖。报告 3.3 节。

**目标**：领域层不依赖 application 与外框（Redis）；不再存在无标记的 fail-open 分支。

**实施步骤**：
1. 先确认该类的真实调用方：若生产代码零调用，直接按 G3 一并删除；若有调用，按下述步骤改造。
2. 将 `UnifiedAuthorizationQueryService` 的调用下沉为 domain 端口（如 `AuthorizationQueryPort`），由 infrastructure 实现，domain 只依赖端口。
3. 移除 domain 内的 `ReactiveRedisTemplate`；缓存职责上移到 infrastructure 适配器。
4. `authorizationQueryService == null` 分支改为显式失败（拒绝或抛业务异常），不再静默放行。
5. `checkOrganizationPermission()` 按报告 7 节第 9 项要求，由业务确认后二选一：实现，或显式标注未实现并拒绝。

**验收条件**：
- domain 包内无 application 包引用、无 Spring Data Redis 引用（由 G7 的新守卫强制）。
- 无 fail-open 分支；对应分支有单元测试证明返回拒绝。
- `mvnd -T4 -B -pl apex-auth -am test` 通过。

**影响面**：权限判定行为可能收紧，需与业务确认是否会误拒现有合法请求。此项依赖业务确认，因此排在 P1 之后。

### G3 application 层演示残留 `ResourcePermissionService`

**现状证据**：`apex-auth` 的 `application/service/ResourcePermissionService` 硬编码示例权限、CRUD 为空、缓存键 `resource:permission:{userId}:{type}:{id}:{action}` 不含租户维度、生产代码零调用（仅测试实例化）。报告 3.3 节。

**目标**：消除会引入跨租户权限缓存串用的死代码。

**实施步骤**：
1. 复核全仓引用，确认无生产调用方。
2. 删除该实现及其专属测试；如存在真实需求，改为按 `tenantId` 参与缓存键的正式实现，并在能力目录登记（与 G4 联动）。
3. 删除后检查是否有配置项、文档或迁移脚本单独指向该服务。

**验收条件**：
- 全仓检索确认无残留引用。
- `mvnd -T4 -B -pl apex-auth -am test` 通过。
- 文档中不再出现该类作为“当前实现”的描述。

**影响面**：仅删除未被生产使用的代码；若复核发现调用方，改为“先修缓存键再保留”。

## 5. P3 项

### G4 能力目录治理字段缺失

**现状证据**：`CapabilityCatalog` 为八项能力的纯 `record`，无 owner、版本、消费者清单、SLO、生命周期状态；后三项能力 `openScopes` 为空却无字段表达“未对外暴露”。报告 3.1、3.8 节。

**目标**：能力目录能回答“谁负责、谁在用、什么版本、什么时候下线”。

**实施步骤**：
1. 为目录条目增加 `owner`、`version`、`consumers`、`slo`、`lifecycle` 字段；`consumers` 先以仓内可核对的引用登记，仓外消费方标注“未知”。
2. 对后三项能力显式标注生命周期为“未开放”，而不是依赖空 `openScopes` 隐含表达。
3. 增加校验测试：已开放 scope 的能力必须有 owner 与版本；生命周期为“已下线”的能力不得出现在 `openScopes` 中。

**验收条件**：
- 八项能力均有 owner、版本、生命周期取值。
- 校验测试在字段缺失时失败。
- 能力目录文档与代码字段一致。

**影响面**：新增字段需要确定的 owner 归属，须由各能力负责人提供，属于组织协作项而非纯技术改动。

### G5 两端能力客户端重复实现

**现状证据**：`TenantCapabilityVerifier`（`apex-common`）与 `TenantCapabilityClient`（`apex-gateway`）各自实现 `Snapshot`、`parse()`、`fresh()`、`fetch()`，只有 `CapabilityGate` 共享。报告 3.4 节。

**目标**：快照解析与缓存只有一份实现，响应结构变更不会单边漂移。

**实施步骤**：
1. 在 `apex-common` 收敛 `Snapshot` 解析与缓存逻辑为一个可复用组件，供两侧注入使用；保留各侧差异（网关的启用开关、服务侧无开关——该差异由 G1 后仍需保留，因为服务侧默认开启不等于强制）。
2. 网关侧改为委托共享实现，删除本地重复代码。
3. 为共享组件补齐“缺 `data` 节点”“`features` 非数组”“空 `features`”三类解析测试（与 G6 的语义变更一并覆盖）。

**验收条件**：
- 全仓仅存在一份 `Snapshot.parse` 实现。
- 两侧客户端在同一输入下返回一致结果，由共用测试断言。
- `mvnd -T4 -B -pl apex-common,apex-gateway -am test` 通过。

**影响面**：重构网关与 common 的类结构，需保证配置键与外部行为不变。

### G7 架构守卫覆盖不足

**现状证据**：`PersistenceContractTest` 不禁止 domain→application 与 Spring Redis；`ContextBoundaryArchTest` 的 `CONTEXTS` 仅 auth/workflow/file/office，缺目录静默 `continue`；`SystemServiceBoundaryTest`、`IntegrationServiceBoundaryTest` 只校验声明与可加载性，不启动服务。报告 3.7 节。

**目标**：门禁覆盖范围与命名一致；缺失目录不再静默通过。

**实施步骤**：
1. `ContextBoundaryArchTest` 补入 system 与 integration；目录缺失时改为失败而非 `continue`。
2. `PersistenceContractTest` 增加“domain 不得依赖 application 包”“domain 不得依赖 Spring Redis”两条规则（G2 的强制手段）。
3. 边界测试的命名或 javadoc 明确其只验证扫描声明与可加载性，避免被误读为启动验证；如需启动验证，另行设计独立的启动冒烟测试，不在本项内扩大范围。

**验收条件**：
- 人为引入一条 domain→application 依赖或一处缺失目录时，对应守卫测试失败。
- 扩大后的守卫在现有代码上全绿（依赖 G2 先完成）。
- `mvnd -T4 -B clean verify` 通过。

**影响面**：守卫变严可能暴露既有违规，需与 G2、G3 一并处置。

### G8 `apex-example` 依赖与 AGENTS.md 漂移

**现状证据**：`apex-example/pom.xml` 直接依赖 common/domain/infrastructure-cache/infrastructure-integration/system，无 workflow（test scope 亦无）；`AGENTS.md` 依赖表写作“workflow 仅 test scope”。报告 3.2 节。

**目标**：文档与 POM 一致，依赖关系可被机器核对。

**实施步骤**：
1. 先判定事实上应该以哪一侧为准：检查 `apex-example` 测试是否实际使用 workflow。
2. 若测试使用 → 补 test scope 依赖；若未使用 → 修改 `AGENTS.md` 依赖表，删除该表述。
3. 在 `apex-tools-build` 或现有校验测试中增加一条“AGENTS.md 依赖表与各模块 POM 直接依赖一致”的核对（可先只覆盖示例模块，避免一次性引入大范围解析）。

**验收条件**：
- 文档表述与 POM 一致。
- 新增核对测试在人为改动依赖时失败。
- `mvnd -T4 -B -pl apex-example -am test` 通过。

**影响面**：最小；只涉及示例模块与文档。

## 6. 实施顺序与依赖

1. **P1（G1 → G6）**：先开默认门禁，再收紧空订阅语义。顺序不可颠倒——若先收紧语义，默认关闭的服务侧不会暴露问题，等 G1 打开后才会集中爆发。
2. **P2（G3 → G2）**：先删演示残留，再改造仍被使用的权限服务，减少改造面。
3. **P3（G5 → G7 → G4 → G8）**：先收敛重复实现，再扩大守卫（守卫需要覆盖收敛后的结构），然后补治理字段，最后修文档漂移。
4. 每阶段结束运行 `mvnd -T4 -B clean verify`，并记录实际执行范围；不得以 `-DskipTests` 打包替代。

## 7. 实施结果与边界声明

- **已进入实施并按结果关闭**：G1–G8 的代码整改、静态契约测试、迁移回填与文档同步已完成；关闭口径为“代码/静态契约可核对”，不等于生产验收通过。
- 已实际执行定向 `mvnd` 测试、静态检查和完整 `mvnd -T4 -B clean verify`；完整门禁 35 个聚合模块 `BUILD SUCCESS`，6,251 个测试、0 失败、0 错误、33 跳过。具体命令、用例统计和跳过/未执行项以[中台整改测试报告](../../06-testing/middle-platform/20261110-completion/report.md)为准；未执行项不得由本方案推定通过。
- 真实 Nacos/MySQL/Redis、RocketMQ/EMQX、Seata TC 联调、告警触达、备份恢复、真实第三方消费方，以及能力 owner/SLO/生命周期运营台账和组织层级权限业务确认，仍需在部署与运营环境另行验收。报告 7 节“证据不足”九项不因本方案实施而自动成立。
