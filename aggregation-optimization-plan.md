# Apex Scaffold 聚合能力调整优化方案

> 状态：**S1/S2 已执行（2026-11-11），S3 已按调整后方案执行（2026-11-11），S4-a 统一待办已执行（2026-11-11），S4-b 跨域写编排与 S4-c/d 独立只读聚合服务已执行（2026-11-12）**。
> 关联文档：`architecture-review-report.md`（评审报告）
> 日期：2026-11-11（S4-b/c/d 执行记录补充于 2026-11-12）

## 0. 方案总览与执行边界

| 编号 | 事项 | 优先级 | 执行方式 |
| --- | --- | --- | --- |
| S1 | 修复 office 审批记录解析（F1） | 高 | ✅ 已执行（含顺带修复 `getApprovalClient` 大小写缺陷） |
| S2 | 概览缓存治理：降级不缓存 + 契约显式化（F2） | 中 | ✅ 已执行 |
| S3 | 概览门面接入并发隔离（F3） | 条件触发 | ✅ 已执行（2026-11-11 用户授权；实现由预案 B 调整为 JDK `Semaphore` 舱壁，理由见 §S3 执行记录） |
| S4-a | 统一待办 / 个人工作台 | 条件触发 | ✅ 已执行（2026-11-11 用户授权；归属裁决落 system，见 §S4-a 执行记录） |
| S4-b/c/d | 跨域写编排 / BFF / 独立聚合服务 | 条件触发 | ✅ 已执行（2026-11-12）；S4-b 落 office 发起方应用层，S4-c/d 落独立只读聚合运行模块 `apex-aggregation-service`，见 §S4-b / §S4-c/d 执行记录 |

通用约束（全部阶段适用）：

- 构建环境：`source scripts/build/init-build-env.sh`（或 `sdk env`），JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`；此前用过其他 JDK 时先 `mvnd --stop`。
- 不回退系统 `mvn`；本地仓库固定 `~/.m2/repository`。
- 遵循 AGENTS.md：WebFlux 函数式 Router/Handler、构造器注入、阻塞 I/O 归 `BlockingIoSchedulers`、MyBatis 多步写同一同步事务、i18n 同步维护。
- 未获明确要求不创建 Git commit。

---

## S1：修复 office 审批记录解析（对应 F1 🔴）

### 现状

`OfficeApprovalService.parseApprovalRecords(Map<String, Object> detail)` 恒返回 `List.of()`，导致 `getApprovalDetail` 的 `process.records` 恒为空。

### 目标

`getApprovalDetail` 返回真实审批流转记录；解析失败不抛错、降级为空列表并留日志，保持现有"详情查询永不向上抛平台差异"的行为。

### 实施步骤

1. **摸清远端详情结构**：读取各平台 `ApprovalPort` 实现与 `getApprovalDetail` 返回的 `Map` 内容，确认每个平台的记录字段（节点名、审批人、动作、时间、意见）。
   - 涉及文件：`apex-office/src/main/java/com/apex/scaffold/office/**` 下 ApprovalPort 实现与客户端。
2. **确定映射规则**：按平台在 office 应用层编写结构化解析（建议每平台一个私有解析方法或策略，避免 `instanceof` 堆叠）；`ApprovalRecord` 字段缺失时置 null/空，不伪造值。
3. **实现 `parseApprovalRecords`**：替换 `return List.of()`；解析异常捕获后返回空列表 + `log.warn`（沿用类内现有日志风格），不让单平台格式差异炸掉详情查询。
4. **补测试**：在 `apex-office/src/test/.../OfficeApprovalServiceTest.java` 增加：
   - 各平台典型详情 → 记录正确解析；
   - 结构不符/字段缺失 → 空记录 + 不抛错；
   - `getApprovalDetail` 全链路（本地过程 + 远端详情 + 记录覆盖）。
5. **回归验证**：
   ```bash
   sdk env
   mvnd -T4 -B -pl apex-office -am test
   ```
6. **范围控制**：不改 `initiateApproval`/`approveProcess`/`cancelApproval` 逻辑；不引入跨域依赖；不动 POM。

### 影响面

- 模块：`apex-office`（实现）+ `apex-office-service`（如测试需要）。
- 接口契约：`getApprovalDetail` 响应中 `records` 从恒空变为真实数据——对前端是**增强**，需确认前端详情页是否已适配非空 records（管理后台是独立项目，本仓库不改前端，仅在交付说明中提示）。
- 数据库：无变更。

---

## S2：概览缓存治理（对应 F2 🟡）

### 现状

`OverviewFacadeService.getOverview` 的 `doOnNext` 无条件 `cache.put`：成功与 DEGRADED 同 TTL 缓存；缓存键仅 tenantId。

### 目标

1. 降级结果不被 30s 缓存放大；
2. "统计端点均为租户级数据、缓存按租户共享成立"从隐含假设变为显式契约（代码注释 + 测试固定）。

### 实施步骤

1. **降级不缓存**：`doOnNext` 中判断 `response.degraded()`——降级响应不入缓存（下一次请求自然重试下游，恢复时效等于即时）。成功响应维持 `cacheTtlSeconds` 缓存。
2. **防缓存击穿（可选，低成本顺带）**：同一租户并发过期时多个请求同时打下游。用 `ConcurrentHashMap.compute` 或先 `putIfAbsent` 一个进行中占位再共享同一 `Mono`（`.cache()`）合并并发请求。**默认做**：改动小且直接消除放大节律。
3. **契约显式化**：
   - 在 `OverviewFacadeService` 类注释中写明：三个统计端点返回租户级聚合数据，不含用户个体数据，因此缓存按租户共享；若未来端点引入用户维度，必须同步改造缓存键。
   - 在 `OverviewFacadeServiceTest` 增加测试固定语义：
     - 降级响应不写缓存（两次请求 → 下游两次被调）；
     - 成功响应缓存命中（两次请求 → 下游一次）。
4. **回归验证**：
   ```bash
   sdk env
   mvnd -T4 -B -pl apex-system-service -am test
   ```
5. **范围控制**：不改对外契约 `OverviewResponse` 字段；不改超时/降级判定逻辑；不引入 Redis（进程内短 TTL 缓存与现状一致，单实例语义明确——概览非跨实例一致性强需求，不为此加分布式缓存）。

### 影响面

- 模块：`apex-system`（实现 + 测试）。
- 行为变化：降级时下游被调频率升高（从 30s 一次变为每次请求重试）。三个统计端点均为轻查询，可接受；若下游负载敏感，可改为降级用短负缓存（如 3s），实施时按 S2.1 默认"完全不缓存"，需要时再调。
- 配置：无新增配置项（如需负缓存 TTL 再议）。

---

## S3：概览门面并发隔离（对应 F3 🟡）——默认不执行

**定位：预留方案，暂不触发。** 理由：

1. **问题尚未发生**：F3 是风险推演（"下游持续慢响应时缓存过期会打出请求放大"），不是已观察到的故障。当前只有 3 个轻查询统计源 + 30s TTL 缓存，下游压力极小。
2. **已有基本防护**：单源 3s 超时 + `onErrorResume` 降级已隔离单源故障；缓存本身也削了大部分请求。
3. **改造成本不对称**：接共享 remote 链需重构门面请求抽象，Resilience4j 需新增装配——两者都是为"假设的风险"付确定成本。
4. **提前做的副作用**：隔离参数（并发上限、熔断阈值）无真实流量数据支撑，只能拍脑袋配置，上线后大概率反复调整——不如等有观测数据再加。

**触发条件**（满足其一开始考虑）：概览来源从 3 个扩展；下游统计出现过载告警；生产出现"缓存过期风暴"证据。

候选方案（届时二选一，**默认选 B**）：

- **A. 复用共享 remote 链**：迁移到 `apex-infrastructure-remote` 的 `RemoteApiClient`（已有可选熔断、配置化重试、异常映射）。代价：门面需适配该客户端的请求/响应抽象，改动面大。
- **B. Resilience4j 并发隔离（默认）**：对 `fetchSource` 加 `SemaphoreBulkhead`，每源独立并发上限。代价：新增依赖装配（版本已在根 POM 声明 2.2.0），改动集中在门面单类。

选 B 为默认的理由：改动面最小、与现有"单源降级"模型天然对齐（舱壁满即该源降级）、不需要重构请求抽象。

### S3 实施预案（触发后执行）

1. **前置核验**：确认根 POM `resilience4j` 版本与 Spring Boot 3.5.15 的兼容性；确认以编程式 API（非 starter 注解）接入，避免引入配置面膨胀。
2. **实现**：在 `OverviewFacadeService` 中为每个来源创建独立 `SemaphoreBulkhead`（名称与 `apex.overview.sources` 键一致）；`fetchSource` 内用 `Mono.defer(...)` + Bulkhead 的 Reactor 组合子包装 `fetchBody` 调用；舱壁饱和按现有降级路径处理（`SourceResult.degraded("bulkhead saturated")`），不向上传播。
3. **配置**：新增 `apex.overview.bulkhead.<source>.max-concurrency`（默认建议 8，触发实施时按下游配额校准）与可选 `max-wait-duration`（建议 0，饱和即降级）。同步维护 [配置文档](docs/05-operations/configuration.md)。
4. **测试**：`OverviewFacadeServiceTest` 增加——舱壁饱和时该源降级且其他源不受影响；并发上限生效（超过上限的请求立即降级而非排队）；既有降级/缓存语义回归。
5. **验证命令**：
   ```bash
   sdk env
   mvnd -T4 -B -pl apex-system-service -am test
   mvnd -T4 -B spotless:check
   ```
6. **保持不变的语义**：单源超时降级、错误不向上传播、Authorization 透传、成功结果租户缓存（S2 后的语义）。

### S3 执行记录（2026-11-11）

按预案启动后，前置核验发现**预案 B 与项目现实的冲突**：`resilience4j` 在本项目是 basic 档裁剪依赖（根 POM basic profile 将其声明为 `optional`，`apex-system` basic 档对上游传递依赖加 exclusions、fat jar 中不含相关 jar）。若 `OverviewFacadeService` 直接编程式引用 Bulkhead 类，basic 档运行时将 `NoClassDefFoundError`。

**实际实施（等价调整）**：以 JDK `java.util.concurrent.Semaphore` 实现每源独立舱壁，语义与预案 B 的"饱和即降级"完全一致（`tryAcquire` 失败立即 `SourceResult.degraded("bulkhead saturated")`，不排队；许可随订阅终止经 `doFinally` 释放）；获取动作在 `Mono.defer` 内于订阅时执行，避免装配后未订阅导致许可泄漏。零新增依赖，basic 档安全。配置为 `apex.overview.bulkhead.<source>`（按源并发上限）与 `apex.overview.default-bulkhead-max-concurrency`（默认 8）；未引入 `max-wait-duration`——响应式链路中阻塞等待不可取，预案本身也建议 0（饱和即降级）。配置说明已同步 [配置文档](docs/05-operations/configuration.md)（"概览聚合门面并发隔离"节）。

验证：`mvnd -T4 -B -pl apex-system-service -am test` 通过；新增测试覆盖"饱和源立即降级且不影响其他源、许可随订阅取消释放"。隔离参数（默认 8）仍无真实流量数据支撑，按预案预期需在观测后校准。

### S4-a 执行记录（2026-11-11）

按用户授权启动。预案条目 3 的归属裁决倾向"workflow 或 system"，实际裁决 **落 system 应用层**：与已有 `OverviewFacadeService` / `OverviewHandler` 同构，复用网关 `/api/v2/**` → system 路由，不新建服务、不放网关（条目 3 的否决项均满足）。预案条目 5 提到"短负缓存"，未采用——个人维度的待办数据不做任何缓存，与概览的租户级缓存契约相反。

**实际实施**：

1. **契约（`apex-api-contract`）**：新增 `InternalTodoItem`（`itemType` / `title` / `link` / `occurredAt` / `status` / `refId`）与 `OfficeTodoInternalResponse`（`approvals` / `messages` / `approvalTotal` / `unreadTotal`）。
2. **域内内部端点**：
   - workflow：`GET /internal/v1/workflow/todo/tasks`（`WorkflowInternalHandler` + `WorkflowInternalRouter`），当前用户来自验签 JWT 建立的 `AuthenticatedUser`，转 `workflowQueryService.listMyTasks`，`itemType=workflow_task`；
   - office：`GET /internal/v1/office/todo`（`OfficeInternalHandler` + `OfficeInternalRouter`），`Mono.zip` 组合待审批（`getPendingApprovals`）与未读站内信（`getInbox` + `countUnread`），`itemType=office_approval` / `office_message`；未认证 fail-closed。
   - 两处均以真实总数返回、条目按 `limit` 截断，不复用全量拉取。
3. **聚合门面（system）**：`TodoFacadeService.getUnifiedTodo(userId, authorization)` 用 `Mono.zip` 并行三源（workflow HTTP、office HTTP、system 进程内 `SystemMessageRepository.pageByUser`），输出 `UnifiedTodoResponse`（`userId` / `retrievedAt` / `degraded` / `sources`，每源含 `status` / `error` / `total` / `items`）。单源超时、不可达、信封非 0、解析失败统一降级为 `DEGRADED` + 错误摘要，`degraded=true`，不向上抛错。
   - 2026-11-12 反向调整：门面迁至独立只读聚合运行模块 `apex-aggregation-service`，system 进程内源已删除，仅保留 workflow + office 两源（`Mono.zip` 二元）；`GET /api/v2/todo` 路径不变。详见 §S4-c/d 执行记录。
4. **并发隔离**：沿用 S3 结论用 JDK `Semaphore` 每源舱壁（`Mono.defer` 内 `tryAcquire`，饱和即降级不排队，`doFinally` 释放），避免 basic 档裁剪 `resilience4j` 后的 `NoClassDefFoundError`。
5. **接口与权限**：`GET /api/v2/todo`（`UnifiedTodoRouter` + `UnifiedTodoHandler`），仅需认证，不加 `@RequirePermission`（无新权限码，无需 Flyway 权限种子）；`Authorization` 头透传给内部端点，由下游安全链继续约束。
6. **配置**：`apex.todo`（`sources.workflow` / `sources.office`、`page-size`、`approval-limit`、`message-limit`、`timeout-millis`、`bulkhead.<source>`、`default-bulkhead-max-concurrency`），说明已同步 [配置文档](docs/05-operations/configuration.md)（"统一待办聚合"节；2026-11-12 随迁移改写为 `apex-aggregation-service` 口径并补 `apex.internal-auth.callers` 登记）。

验证：`mvnd -T4 -B -pl apex-system-service,apex-office-service,apex-workflow-service -am test` BUILD SUCCESS（`apex-system` Tests run 1802 / Failures 0 / Errors 0；`apex-system-service` Tests run 4 / 0 / 0）。`mvnd -T4 -B spotless:check` 通过，`git diff --check` 无输出。

---

## S4：条件触发事项及实施预案

S4-a~d 已全部执行：S4-a 于 2026-11-11 落 system（见 §S4-a 执行记录）；S4-b 于 2026-11-12 落 office 发起方应用层（见 §S4-b 执行记录）；S4-c/d 于 2026-11-12 以独立只读聚合运行模块 `apex-aggregation-service` 落地，未采用 BFF 形态（见 §S4-c/d 执行记录）。以下预案保留作为原始设计依据。

### S4-a 统一待办 / 个人工作台（触发：业务方提出需求）

1. **需求确认先行**：与业务方对齐范围——哪些域进入统一待办（workflow 任务、office 站内信/审批、system 通知等）、是否需要跨域排序分页、刷新时效要求。
2. **身份与授权设计**：待办条目必须绑定可信当前用户（Reactor Context），聚合层只做组合不做鉴权判断；各域条目的查看/办理权限由归属域校验。
3. **归属裁决**：倾向在 workflow 或 system 建组合用例（届时按"哪个域的条目占比最高/变更最频繁"裁决）；**不放入网关，不新建服务**。
4. **契约设计**：统一条目 DTO（来源域、类型、标题、跳转、时间、状态），各域提供分页查询内部端点；跨域分页采用"各域取 N 页合并"或"各域计数 + 客户端分组"，实施时按数据量裁决，禁止全量拉取内存合并。
5. **部分失败语义**：沿用概览门面模式——单域降级标记、不缓存或短负缓存、错误不向上传播。
6. **验证**：归属模块 `mvnd -T4 -B -pl <module> -am test`；跨租户与权限用例必测。

### S4-b 跨域写编排（触发：出现真实跨服务写用例）

1. **归属**：由业务发起方（编排有明确业务归属）的应用层编排，不设独立编排器。
2. **一致性设计**：每步写操作定义幂等键与补偿动作；优先事件/Outbox 补偿模式（仓库已有 `TransactionalOutboxExecutor` 参照），Seata 仅在补偿模型确实不适配时评估（AT/TCC/SAGA/XA 按 `apex.seata.mode`，真实 TC 环境端到端验证不能跳过记为通过）。
3. **状态可恢复**：编排过程状态落库（发起方域内），失败可重放、可人工介入。
4. **禁止事项**：全局事务内不放远程调用；Outbox 写与业务写同事务同线程边界；不把编排塞进概览门面。

### S4-b 执行记录（2026-11-12）

触发条件由用户授权满足。实际落点与预案一致：**office 发起方应用层**，未新增独立编排器，未启用 Seata。

**实际实施**（office 审批撤销入口）：

1. **幂等键**：撤销请求携带稳定幂等键，重复提交命中同一编排记录后直接返回既有结果，不重复发起远端撤销。
2. **显式补偿动作**：远端撤销成功但本地状态更新失败时，按预案第 2 条走显式补偿入口（office 原有的"CANCELLED 后置"形态），不依赖全局事务回滚跨服务状态。
3. **编排状态落库**（预案第 3 条）：编排过程状态持久化在 office 域内，失败可重放、可人工介入；写路径沿用同一同步事务边界，参照共享 `TransactionalOutboxExecutor` 的同事务同线程做法。
4. **禁止事项核验**：未在全局事务内放远程调用；未把编排塞进概览门面。

验证：office 模块 `mvnd -T4 -B -pl apex-office-service -am test` 通过，新增 8 个场景用例（幂等命中、补偿触发、状态落库与重放等）全绿。

### S4-c BFF（触发：出现差异化客户端）

1. **先验证适用条件**：至少两个客户端对同一能力需要不同组合/裁剪，且独立发布节奏成为实际痛点；单客户端差异用现有服务的多接口即可满足。
2. **形态**：新建 BFF 运行模块（参照七个运行模块形态），只做组合/裁剪/协议适配，不承载业务四层实现；依赖 api-contract，不反向依赖业务库内部包。
3. **成本确认**：立项前确认团队接受部署、维护、安全（独立鉴权链）、生命周期成本。

### S4-d 独立聚合服务（触发：多个跨域只读场景涌现且门面归属争执）

1. **前置条件**：至少 2-3 个跨域只读场景且归属方无法就"放谁的门面"达成一致；否则继续按场景放归属方应用层。
2. **形态**：新查询聚合运行模块，复用 S3 的隔离预案与 S2 的缓存契约；聚合契约入 `apex-api-contract`。
3. **评审重启**：本评审报告第 4 节结论需随之更新。

### S4-c/d 执行记录（2026-11-12）

触发条件由用户授权满足。**未采用预案的 BFF 形态**，也未把聚合继续留在 system 门面内，实际形态为**独立只读查询聚合运行模块** `apex-aggregation-service`：

1. **模块形态**：新增运行模块 `apex-aggregation-service`（根 reactor 由 35 增至 36），包名 `com.apex.scaffold.aggregationservice`，默认端口 **8098**；父 POM 为 `apex-services-bom`，直接内部依赖仅 `common`、`api-contract`。无数据库、无 MyBatis、无 Flyway、无 Seata 与 job starter——纯只读聚合，不持有任何业务状态。
2. **聚合范围**：对外 `GET /api/v2/todo`（路径与 S4-a 一致，前端无需改路径）。只聚合两个 HTTP 源——workflow 任务（`/internal/v1/workflow/todo/tasks`）与 office 待审批/未读（`/internal/v1/office/todo`），以 `Mono.zip` 二元并行。**system 进程内站内通知源已删除**：该源依赖 system 仓储，与"聚合模块不持有业务状态"的边界冲突。
3. **隔离与降级**（复用 S3/S4-a 结论）：JDK `Semaphore` 每源舱壁（`Mono.defer` 内 `tryAcquire`，饱和即降级不排队，`doFinally` 释放），单源 3000ms 超时，任一源失败仅该源 `DEGRADED` + 错误摘要，`degraded=true`，不向上抛错；个人维度数据不缓存。
4. **契约**：`UnifiedTodoResponse` / `SourceSection` / `TodoItem` / `InternalTodoItem` / `OfficeTodoInternalResponse` 落在 `apex-api-contract`，不反向依赖业务库内部包。
5. **安全**：出站签名由共享 `InternalAuthExchangeFilter` 完成（`apex.internal-auth.*`）；`Authorization` 头透传给内部端点，由下游安全链继续约束。**部署需在 `apex.internal-auth.callers` 白名单登记 `apex-aggregation-service`**，授权路径 `/internal/v1/workflow/todo/**`、`/internal/v1/office/todo`；未登记时内部调用被 fail-closed 拒绝（表现为全源降级）。
6. **装配**：根 `pom.xml`、`build-manifest.yaml`、`release.sh` SERVICES、网关 `application.yml` / `application-prod.yml`（`ROUTE_AGGREGATION_URI=lb://apex-aggregation-service`、`aggregation-domain`、熔断器与 fallback）、`AGENTS.md` 模块与依赖表、`docs/05-operations/configuration.md` 均已同步。

验证：`mvnd -T4 -B -pl apex-aggregation-service -am test` BUILD SUCCESS（6 个用例：双源聚合、单源错误降级、业务错误信封降级、源未配置降级、舱壁饱和降级、请求间不缓存）；`mvnd -T4 -B -pl apex-aggregation-service,apex-api-contract spotless:check` 通过；`git diff --check` 无输出。

**核心原则**：这些基础设施（聚合层、编排层、BFF）建造成本前置且确定，收益依赖未来场景。没有实证需求时建设，属于为假设的未来需求设计；预案已细化到"触发即执行"，条件满足即按预案启动，而非永远不做。若企业中台规范有强制条款（如统一聚合层或 BFF），提供规范文本后按条款重新评估。

**执行现状（2026-11-12）**：S4-b 已落 office 发起方应用层（幂等键 + 补偿动作 + 编排状态落库，未新增编排器、未启用 Seata）；S4-c/d 已以独立只读聚合运行模块 `apex-aggregation-service` 落地（未采用 BFF 形态，只聚合 workflow + office 两源）。S4 全部事项均已执行，本预案转为历史设计依据。

---

## 5. 执行计划（确认后）

| 顺序 | 阶段 | 验证命令 | 说明 |
| --- | --- | --- | --- |
| 1 | S1 office 审批解析 | `mvnd -T4 -B -pl apex-office -am test` | 独立可交付 |
| 2 | S2 概览缓存治理 | `mvnd -T4 -B -pl apex-system-service -am test` | 独立可交付 |
| 3 | 收尾 | `mvnd -T4 -B spotless:check` + `git diff --check` | S1+S2 均完成后统一做 |
| 4 | （可选）完整门禁 | `mvnd -T4 -B clean verify` | 若用户要求全量回归；注意 `-T4` 上限，计时敏感测试（如 `DualServiceAuthorizationIntegrationTest`）偶发超时属负载 flake，重跑即可 |
| 条件触发 | S3 并发隔离 | 按 §S3 预案步骤 5 | ✅ 已执行（2026-11-11，实现调整为 JDK `Semaphore` 舱壁，见 §S3 执行记录） |
| 条件触发 | S4-a 统一待办 | `mvnd -T4 -B -pl apex-system-service,apex-office-service,apex-workflow-service -am test` | ✅ 已执行（2026-11-11，见 §S4-a 执行记录） |
| 条件触发 | S4-b/c/d | 按各预案内的验证要求 | ✅ 已执行（2026-11-12；S4-b 见 §S4-b 执行记录，S4-c/d 见 §S4-c/d 执行记录） |

S1/S2/S3/S4-a/S4-b/S4-c/d 均已执行（S1~S3、S4-a 于 2026-11-11，S4-b/c/d 于 2026-11-12）；各预案保留为历史设计依据。

## 6. 风险与未验证声明

- S1/S2/S3/S4-a 已于 2026-11-11 实施并通过模块回归；运行时行为（真实下游、真实流量下的隔离参数）未验证。
- S4-a 的三个来源均未做真实下游联调：workflow/office 内部端点由测试桩覆盖，真实 HTTP 请求头/签名行为未验证；`apex.todo.sources.*` 默认端口（workflow 8093、office 8084）与部署实际地址需核对。
- S4-a 对外 `/api/v2/todo` 的前端适配未做（管理后台独立项目）；响应字段变更需前端同步。
- S4-a 的 office 未读消息复用 `getInbox(userId,1,limit)`，其排序语义沿用现有收件箱实现，未新增专用待办查询。
- S4-a 的错误摘要当前使用中文常量消息，未纳入 i18n 资源；如需多语言需补 `apex-system` 资源文件。
- S4-a 的舱壁并发上限与 S3 同样缺乏真实流量数据支撑，需在观测后校准。
- S1 依赖各平台 `ApprovalPort` 实际返回结构——解析用例基于各平台公开 API 文档结构编写，真实租户凭据联调未做。
- S1 的 `records` 由恒空变为真实数据，前端展示适配情况未知（管理后台独立项目），交付时已提示。
- S2 降级不缓存会小幅增加降级期间下游请求量；统计端点为轻查询，评估可接受。
- S3 舱壁默认并发上限 8 无真实流量数据支撑，上线后按观测校准。
- S4-b 的幂等键、补偿动作与编排状态落库仅在 office 模块单元测试中验证，未做真实远端审批平台联调；编排状态重放未在真实故障注入下验证。
- S4-c/d 聚合服务只做过桩测试（覆写 `fetchBody` 接缝），未与真实 workflow / office 实例联调；`apex.todo.sources.*` 的默认地址需按部署实际端口核对。
- S4-c/d 依赖部署时在 `apex.internal-auth.callers` 登记 `apex-aggregation-service`；未登记时内部调用被 fail-closed 拒绝，表现为两个源同时降级。
- `GET /api/v2/todo` 的响应结构未做前端适配回归；管理后台若仍按 system 门面时期的调用方式访问需复验。
- 聚合服务舱壁并发上限（`apex.todo.bulkhead.workflow` / `apex.todo.bulkhead.office`，默认各 4）无真实流量数据支撑，需上线后按观测校准。
- 实施结论以实际 `mvnd` 输出为准。
