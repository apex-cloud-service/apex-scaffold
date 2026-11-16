# Apex Scaffold 聚合能力架构评审报告

> 评审日期：2026-11-11
> 评审方式：只读源码核验（未执行构建、测试或服务启动）
> 关联文档：`aggregation-optimization-plan.md`（调整优化方案；S1/S2/S3/S4-a~d 已按方案执行，见该文档执行记录）

## 1. 评审背景与范围

业务方提出的问题：对照中台架构规范，当前实现是否需要：

- 接口 / API 查询聚合；
- 功能聚合、跨域业务编排；
- 独立聚合服务或 BFF。

评审基于源码事实而非模块名称或架构图。本次未获得具体企业中台标准文本，因此不引用任何"合规必选"条款，只区分**架构必需**与**按场景优化**。

### 1.1 核验范围

| 对象 | 结论 | 证据强度 |
| --- | --- | --- |
| 概览聚合门面（system 应用层） | 已存在，形态正确 | 直接读源码 |
| 三个下游统计 Handler（file/workflow/integration） | 契约已核验 | 直接读源码 |
| 内部签名出/入站过滤器（common） | 服务身份与租户绑定语义已核验 | 直接读源码 |
| workflow 安全链 | 内部统计仍受用户认证约束 | 直接读源码 |
| office 审批详情解析 | 存在未完成实现 | 直接读源码 |
| 概览单元测试 | 已读，未执行 | 静态阅读 |
| `IdentityRelayAuthenticationFilter` 实现 | 未核验 | 未读 |
| 权限切面实际执行、权限种子 | 未核验 | 未读 |
| 运行行为（JWT/签名/持久化租户端到端一致性） | 未验证 | 未运行 |

### 1.2 概念澄清

评审过程中明确区分了以下不能互相替代的概念：

| 概念 | 项目中的对应物 | 是否构成"API 聚合" |
| --- | --- | --- |
| Maven 聚合 | 根 reactor 36 个 modules | 否 |
| 部署装配 | `apex-boot-all` 单进程装配 | 否 |
| DDD 聚合根 | 各业务域内部一致性边界 | 否 |
| API 查询聚合 | `OverviewFacadeService`（概览）与 `UnifiedTodoAggregationService`（统一待办，独立聚合模块） | 是 |
| BFF | 无（统一待办以只读聚合运行模块实现，非 BFF 形态） | — |
| 跨域写编排 | office 审批撤销幂等 + 补偿编排（S4-b） | — |

## 2. 已有聚合能力核验

### 2.1 跨域只读概览聚合（已存在）

调用链（静态核验）：

```
前端 → 网关 /api/v2/overview → PlatformOverviewHandler
     → OverviewFacadeService.getOverview(tenantId, authorization)
       → Mono.zip 并行调用：
         file:        GET /internal/v1/file/stats?tenantId=
         workflow:    GET /internal/v1/workflow/stats
         integration: GET /internal/v1/iot/devices/stats?tenantId=
```

已核验的设计要点：

1. **聚合位置正确**：门面放在 system 应用层，不在网关内——符合项目"网关不承载业务四层实现"约束，也符合 Gateway Aggregation 模式"聚合可位于网关之后"的适用条件。
2. **单源隔离**：任一源超时（3s）/不可达/业务错误时降级为 `DEGRADED`（`data=null` + 错误摘要），不向上传播错误，不影响其他源。
3. **三层安全语义分离**：
   - 服务身份：出站由 `InternalAuthExchangeFilter` 签名（绑定调用服务、方法、目标、租户、正文）；
   - 用户认证：门面显式透传调用方 `Authorization`，workflow 安全链仍要求请求已认证；
   - 租户作用域：入站过滤器校验 `X-Tenant-Id` 与 query `tenantId` 与签名租户一致；Handler 从可信 Reactor Context 取租户。
4. **响应式合规**：原生异步 WebClient，不占用阻塞调度器；Handler 链无 `.block()`。
5. **降级语义明确**：`ResultCode.SUCCESS` 为 0；降级源 `data=null`，不会把缺失数据误读为零值。

### 2.2 域内查询组合（非跨域聚合）

- workflow `WorkflowQueryService.getInstanceDetail`：本域内组合实例/节点/定义/任务。
- workflow `WorkflowStatisticsService`：本域组合发起数、运行数、待办数、超时数、平均时长。
- office `getPendingApprovals` 等列表：office 本域数据，不构成 office+workflow 统一待办。

这些归本域应用/查询层，**不是**跨服务聚合，也不应被搬到聚合层。

## 3. Findings

采用 Symptom → Source → Consequence → Remedy 结构。

### F1 🔴 office 审批记录解析是未完成实现

- **Symptom**：远端平台审批详情被查询，但审批记录永远为空。
- **Source**：`OfficeApprovalService.parseApprovalRecords` 直接 `return List.of()`（`apex-components-parent/apex-office/src/main/java/com/apex/scaffold/office/application/OfficeApprovalService.java` L329–333）。
- **Consequence**：`getApprovalDetail` 返回的 `process.records` 恒为空列表，审批详情页无法展示流转记录。这是能力缺口，不是代码风格问题。
- **Remedy**：在 office 本域内实现真实解析（按各平台 ApprovalPort 返回结构映射 `ApprovalRecord`），并补齐测试覆盖。归属：office 应用层，无需跨域方案。

### F2 🟡 概览缓存未区分降级与成功，且授权等价性未证明

- **Symptom**：成功响应与降级响应写入同一个租户缓存，TTL 相同（默认 30s）；缓存键只有 tenantId，而下游请求携带调用者 Authorization。
- **Source**：`OverviewFacadeService.getOverview` 的 `doOnNext` 无条件 `cache.put`（L100–111）。
- **Consequence**：单个调用者鉴权失败产生的 DEGRADED 结果会在 30s 内被同租户所有调用者复用；"同租户不同权限用户的统计结果等价"是隐含假设，未写成显式契约。
- **Remedy**：降级结果不缓存（或用显著缩短的负缓存）；将"三个统计端点均为租户级数据"写成显式契约并在测试中固定。注意：**不能仅凭租户共享缓存断言存在已证实的越权漏洞**——file/integration 的 query 租户优先行为受入站签名一致性校验约束。

### F3 🟡 概览门面未接入熔断/并发隔离

- **Symptom**：门面直接用 WebClient，仅有单源 timeout + `onErrorResume`；共享 `RemoteApiClient` 链的熔断、配置化重试能力未复用。
- **Consequence**：下游持续慢响应时，每次缓存过期会打出 3 个 3s 并行请求，无舱壁隔离，缓存反而形成请求放大节律。
- **Remedy**：当前三个纯读源可接受现状；若来源扩展或下游出现过载，迁移到共享 remote 链或引入 Resilience4j（根 POM 声明 2.2.0）做并发隔离。按场景优化，非强制。

### F4 ⚪ 跨域写编排尚无用例，不应预建

- 未观察到任何跨服务写编排场景。Seata starter 存在不代表用例存在。
- 未来出现真实跨域写用例时的归属方案：由**业务归属方的应用层编排** + 显式幂等/补偿设计（必要时采用 Saga 模式，参考 AWS Saga Orchestration 的适用条件）。
- （评审后回填）2026-11-12 已在 office 发起方应用层落地首个跨域写编排：审批撤销幂等键 + 补偿动作 + 编排状态落库（参照 `TransactionalOutboxExecutor`），未启用 Seata，也未新增独立编排器。

## 4. 结论

| 问题 | 结论 |
| --- | --- |
| 是否需要 API 查询聚合？ | **已有**（概览门面），且实现质量总体良好 |
| 是否需要新增聚合能力？ | **需要，且已落地**；F1/F2 已治理；统一待办聚合门面于 2026-11-11 落 system 应用层，后于 2026-11-12 迁移至独立只读聚合运行模块 `apex-aggregation-service`（`GET /api/v2/todo`）；其余场景仍无实证需求 |
| 聚合应放在哪里？ | 只读跨域查询：独立只读聚合运行模块 `apex-aggregation-service`（统一待办）；只读概览类：业务归属方应用层（现模式正确）；域内组合：本域查询层 |
| 是否需要独立聚合服务？ | **已建**——2026-11-12 立项 `apex-aggregation-service`（只读查询聚合，无 DB/MyBatis/Flyway/Seata/job starter，默认业务端口 8098），仅聚合 workflow 与 office 两个 HTTP 源 |
| 是否需要 BFF？ | **否**——当前只有单一客户端形态（管理后台），不满足 BFF 适用条件（多客户端差异化、独立发布/扩缩容）；统一待办以只读聚合运行模块实现，不是 BFF 形态 |
| 是否需要跨域写编排？ | **已有首个用例**——2026-11-12 在 office 发起方应用层落地审批撤销幂等 + 补偿编排；未启用 Seata，也未新增独立编排器；新用例出现时仍按业务归属方编排 + 幂等/补偿设计 |

网关、共享 domain/infrastructure、`apex-boot-all` 均不应成为业务聚合的"垃圾桶"；现有边界干净，应维持。

### 4.1 条件触发决策的依据（S3 与 S4-a~d 已按触发条件执行）

这是基于证据强度的判断，不是"不重要"，而是**当前没有足够证据证明收益大于成本**。

**S3（熔断/并发隔离）——已于 2026-11-11 执行（JDK `Semaphore` 舱壁）**：

以下为评审时点"暂不触发"的判断依据，后经用户授权按预留方案启动：

1. 问题尚未发生：F3 是风险推演，不是已观察到的故障。当前只有 3 个轻查询统计源 + 30s TTL 缓存，下游压力极小。
2. 已有基本防护：单源 3s 超时 + `onErrorResume` 降级已隔离单源故障；缓存本身也削了大部分请求。
3. 改造成本不对称：接共享 remote 链需重构门面请求抽象，Resilience4j 需新增装配——两者都是为"假设的风险"付确定成本。
4. 提前做的副作用：隔离参数（并发上限、熔断阈值）无真实流量数据支撑，只能拍脑袋配置，上线后大概率反复调整——不如等有观测数据再加。

以上是评审时点的判断。S3 已于 2026-11-11 按"触发即执行"预案启动：因 `resilience4j` 在 basic 档被裁剪（直接引用会 `NoClassDefFoundError`），实现由预案 B 的 Resilience4j 舱壁调整为等价的 JDK `Semaphore` 每源舱壁（饱和即降级、不排队，许可随订阅终止释放），配置为 `apex.overview.bulkhead.<source>` + `default-bulkhead-max-concurrency`（默认 8，仍待观测校准）；细节见优化方案 §S3 执行记录。

**S4（BFF/独立聚合服务/跨域编排/统一待办）——S4-a~d 已执行（S4-b 落 office 发起方应用层，S4-c/d 以独立只读聚合模块落地，未采用 BFF 形态）**：

| 事项 | 评审时判断 | 2026-11-12 实际执行 |
| --- | --- | --- |
| 独立聚合服务 | 唯一确认的跨域场景（概览）已被现有门面覆盖；新增服务带来部署、维护、安全、生命周期成本，却没有对应收益 | 已立项只读查询聚合运行模块 `apex-aggregation-service`（默认业务端口 8098，无 DB/MyBatis/Flyway/Seata/job starter），仅聚合 workflow 与 office 两个 HTTP 源 |
| BFF | BFF 适用前提是**多客户端差异化**。当前只有管理后台一个客户端形态——单一客户端不需要 BFF，这是模式文档的明确适用条件 | 仍未采用 BFF；统一待办以独立只读聚合模块实现，不引入多客户端差异化能力 |
| 跨域写编排 | 全仓没有观察到任何跨服务写用例。Seata starter 存在不代表用例存在；编排需要幂等、补偿、恢复设计，无场景时建了就是死代码 + 运维负担 | 已落首个用例：office 发起方应用层审批撤销幂等键 + 补偿动作 + 编排状态落库（参照 `TransactionalOutboxExecutor`），未启用 Seata，未新增独立编排器 |
| 统一待办/个人工作台 | 已于 2026-11-11 落地 system 应用层门面 `GET /api/v2/todo`：并行聚合 workflow 任务、office 待审批与未读站内信、system 站内通知，身份取验签 JWT 当前用户，单源失败降级不向上抛错，个人维度数据不缓存。其余场景（跨域排序分页、个人工作台深聚合）仍无实证需求 | 门面迁至 `apex-aggregation-service`，路径 `GET /api/v2/todo` 不变；system 站内通知源已删除，仅保留 workflow + office 两源（`Mono.zip` 二元）；降级不缓存、每源 JDK `Semaphore` 舱壁语义不变 |

**核心原则**：这类基础设施（隔离层、聚合层、编排层）的共同特征是——建造成本前置且确定，收益依赖未来场景。在没有实证需求时建设，属于为假设的未来需求设计。**S3 与 S4-a 已于 2026-11-11 按预留预案启动（S3 以 JDK `Semaphore` 舱壁取代原定 Resilience4j，S4-a 以 system 应用层门面实现）；S4-b/c/d 于 2026-11-12 按条件触发执行（S4-b 落 office 发起方应用层编排，S4-c/d 以独立只读聚合运行模块 `apex-aggregation-service` 落地，未采用 BFF 形态），执行细节见优化方案文档。**

> 注：以上判断基于架构模式适用条件与源码事实。若企业中台规范有强制要求（如统一聚合层或 BFF），提供规范文本后可按条款重新评估。

## 5. 证据边界声明

- 本报告全部结论来自**静态源码阅读**，未运行 `mvnd test`、未启动服务、未做真实 HTTP 验证。结论反映评审时点（2026-11-11）；S3、S4-a~d 为评审后实施，已通过 `mvnd -T4 -B -pl apex-system-service,apex-office-service,apex-workflow-service,apex-aggregation-service -am test` 与 `mvnd -T4 -B -pl apex-aggregation-service,apex-api-contract spotless:check`，不属于本报告的静态评审范围。
- `IdentityRelayAuthenticationFilter` 完整实现、权限切面实际执行、权限种子、JWT/签名/持久化租户端到端一致性均未核验。
- 根 POM 声明版本 1.3.1；`OverviewFacadeService` 注释提及 `@since 1.3.2`，注释不能替代实际版本声明（仅记录，不构成 Finding）。
- 单元测试（`OverviewFacadeServiceTest`）已读未执行；其桩覆写了真实 HTTP 边界，因此测试通过不能证明真实请求头/签名行为。
- 评审时未修改任何项目文件；评审后已按优化方案执行 S1/S2/S3/S4-a~d，§4、§4.1 结论已按实际执行事实回填（2026-11-12）。
