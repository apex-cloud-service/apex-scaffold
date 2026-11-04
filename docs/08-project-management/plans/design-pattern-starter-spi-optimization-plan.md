# 设计模式、Spring Boot Starter 与 SPI 优化方案

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

> 文档性质：**评审结论与待实施优化方案**。本轮只做代码评审与文档整理，未修改任何生产代码、POM、配置或测试。
> 评审基线：`main` 分支，根 POM 聚合 31 个模块，Java 21 / Spring Boot 3.5.15 / Spring Cloud 2025.0.0。
> 评审范围：根 `pom.xml` 全部模块的 `src/main/java` 生产源码，以及 `scripts/ci/check-doc-links.sh` 所约束的文档事实。

## 一、结论摘要

仓库已经在**对象存储、XXL 任务、Seata、License** 四处采用标准 Spring Boot Starter 形态（`@AutoConfiguration` + `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`），并在 **office 平台客户端、登录策略、限流算法、数据权限、脱敏、排空** 等处使用端口/策略/工厂等模式。这些是本轮优化的**正例，应当保留为参照物**。

真正的改进空间不在“再引入几个设计模式名词”，而在三类具体问题：

1. **跨运行模块的横切能力被复制粘贴，而不是抽取为 starter。** 三个服务的 `IdentityRelayAuthenticationFilter`、四个服务的 `RequestHandlerUtil`、`AuthenticatedUser`、`@RequirePermission` 切面各自维护一套；其中已有可观测的行为分叉。
2. **已声明的横切契约在部分运行模块中没有任何实现，注解静默失效。** 最明确的一例是 `@IdempotentApi` 在 `apex-office-service` 的 10 个写接口上被使用，但唯一实现 `IdempotentApiAspect` 位于 `apex-system`，office-service 既不依赖也不扫描该包。
3. **“可选/可插拔”的扩展点在代码里靠字符串与 `switch` 硬编码，没有形成 SPI。** office 的文档/日历/审批三个客户端用 Bean 名称字符串拼接查找（而接口早已声明 `getPlatformType()` 却未使用）；SSO 协议工厂把四个实现写死成 `switch`；job starter 的 system 任务桥接条件只检查 `job-id`、方法体却强依赖 `tenant-id`。

下表按“收益 / 风险”给出优先级。P0 指**已经在生产语义上产生偏差**，P1 指重复度高且改造机械，P2 指结构性优化。

| 优先级 | 项 | 类型 | 影响面 |
| --- | --- | --- | --- |
| P0 | `@IdempotentApi` 在 office-service 失效 | 缺陷确认 | 10 个写接口 |
| P0 | 三个 JWT 认证过滤器语义不一致 | 缺陷确认 | 3 个服务 |
| P1 | 安全链 / 认证过滤器 / 请求工具 / `AuthenticatedUser` starter 化 | Starter | 4–5 个模块 |
| P1 | `@RequirePermission` 切面三套实现 | Starter + SPI | 3 个服务 |
| P1 | Outbox / 领域事件装配重复手写 | Starter | auth、workflow |
| P2 | office 平台客户端 Bean 名拼接 → SPI 注册表 | SPI | office |
| P2 | SSO 协议工厂 `switch` → SPI | SPI | system |
| P2 | `SystemJobPort` 条件只查 `job-id`、方法体却强依赖 `tenant-id` | 装配缺陷 | job starter |
| P2 | 启动类扫描配置、分页 DTO 分叉 | 结构收敛 | 5 个启动类 |

## 二、先明确：哪些现状不要动

评审中确认以下实现已经符合目标形态，改造容易破坏既有语义，**不建议在本次方案中调整**：

- **`apex-storage-spring-boot-starter`**：`StorageAutoConfiguration` 用 `@ConditionalOnMissingBean`、`@ConditionalOnClass`、`@ConditionalOnProperty` 三件套，`StorageRegistry` 对重复类型、未知默认类型**显式失败**而非静默降级。这比“再套一层工厂”更可靠。
- **`apex-seata-spring-boot-starter`**：用 `Condition` 子类按 `apex.seata.mode` 分派，而不是在单一 Bean 里堆 `if`；`proxyClassFor` 与 `wrapDataSource` 保持包可见以支持无网络单测。这是本仓库最佳的模式范例。
- **`apex-license-spring-boot-starter`**：`@ConditionalOnLicenseFeature` 用自定义 `Condition` + 静态指纹缓存，解决了“`Condition` 早于 Bean 实例化”的时序问题，且 `enabled=false` 解释为“更严格”而非绕过。语义正确。
- **`OfficeMessageService`**：以 `List<MessagePort>` 注入 + `getPlatformType()` 自注册到 `EnumMap`，重复平台显式抛错。这是**同一个仓库里正确的 SPI 写法**，可作为 office 其余三个客户端的改造模板。
- **`StateTransitionMachine`、`RateLimitAlgorithmFactory`、`CachePrewarmStrategy`、`DataScopeProvider`、`DrainParticipant`、`MqLocalTransactionHandler`**：契约与实现分离清晰，`DataScopeProvider` 还有 `@ConditionalOnMissingBean` 兜底。保留。
- **`OperationAuditAutoConfiguration` + `ReactiveOperationLogAspect`**：**这是本仓库最值得复制的横切装配范例**。切面放在 `apex-common`（不是某个业务模块），通过 `META-INF/spring/...AutoConfiguration.imports` 注册，切点显式枚举 `authservice`、`fileservice`、`gateway`、`maskingservice` 四个包，`@ConditionalOnMissingBean` 允许覆盖。office 与 workflow 各自保留本地审计消费者（因为它们的审计语义不同）。这证明“横切下沉 common + 自动配置装配”在本仓库已经跑通。
- **`api-contract` 的 `FeignTenantTraceConfiguration`**：只在上下文存在时注入、不覆盖调用方已显式设置的头。语义正确。

## 三、P0：已在语义上失效或分叉的问题

### 3.1 `@IdempotentApi` 在 office-service 中无实现

**证据链**

- 注解定义：`apex-common/src/main/java/com/apex/scaffold/common/annotation/IdempotentApi.java`，其 JavaDoc 明确“配合 `com.apex.scaffold.interfaces.aspect.IdempotentApiAspect`”。
- 唯一实现：`apex-system/src/main/java/com/apex/scaffold/interfaces/aspect/IdempotentApiAspect.java`（`@Component`，`@Around` 切点只匹配注解本身）。
- 使用方：`apex-office-service/src/main/java/com/apex/scaffold/officeservice/interfaces/handler/OfficeAdminHandler.java`（5 处）与 `office/OfficeMessageHandler.java`（5 处）。
- 装配事实：`OfficeServiceApplication` 的 `scanBasePackages` 为 `officeservice`、`office`、`common`；`apex-office-service/pom.xml` 与 `apex-office/pom.xml` 均**不依赖** `apex-system`。

**结论**：这 10 个写接口的 `@IdempotentApi` 当前只是元数据，不会执行任何 SETNX 去重。注解的 `required()` 默认值为 `true`，即按注解契约，**缺少 `Idempotency-Key` 头的请求应当被直接拒绝**；实际这些接口全部放行。这不只是“少了去重”，而是“本该拒绝的请求被接受”，属于安全与幂等语义双重偏离。

**同类风险（需逐个确认，不要在方案中直接断言）**：

- `@RequireAdminRole`、`@RequireResourcePermission` 由 `apex-auth-service` 使用，切面位于 `apex-auth`，而 auth-service 的扫描范围包含 `com.apex.scaffold.auth`——**按静态分析可用**，但仍应做上下文加载验证。
- `@TenantAware` 由 `apex-workflow-service` 使用 3 处，唯一实现 `TenantAspect` 位于 `apex-system`，而 workflow-service 不依赖 system——**切面不会装配**。不过需注意：workflow 的租户上下文实际由 `IdentityRelayAuthenticationFilter` 直接写入 Reactor Context，因此缺 `TenantAspect` 影响的是“请求头 `X-Tenant-Id` 与认证主体的交叉校验”，**不是租户上下文完全缺失**。这个区别决定了修复优先级，不能在文档里混为一谈。

**建议方案**

**先例**：`apex-common` 已经用 `OperationAuditAutoConfiguration` + `ReactiveOperationLogAspect` 解决了同类问题（切面在 common、通过 `.imports` 装配、切点显式枚举目标包）。`@IdempotentApi` 与 `@TenantAware` 可以照此办理，**不必先建新 starter**。

1. **最小可行修复**：把 `IdempotentApiAspect`、`TenantAspect` 从 `apex-system` 移到 `apex-common` 的 `common.autoconfigure` 同款包，新增 `WebCrossCuttingAutoConfiguration` 用 `.imports` 注册，`@ConditionalOnMissingBean` 允许服务覆盖；切点显式覆盖 `officeservice`、`workflowservice`、`systemservice` 等实际使用方。
2. **目标形态**：随 `apex-web-spring-boot-starter` 一起迁移（见 §4.2），把 `@RequirePermission` 等更重的切面也纳入。
3. 切面依赖的 Redis 模板、`AuditLogService`、`PermissionService` 一律用 `ObjectProvider` 可选注入，缺失时**显式启动失败或明确降级**（二选一，需业务确认，见 §8 决策点 D2），不允许“注解放着不生效”。
4. 增加一条架构测试：`common` 中声明的每个 `@interface`，必须能在一个运行模块的 `AutoConfiguration.imports` 链路里找到对应 `@Around`，否则测试失败。这比人工 grep 更能防回归。

### 3.2 三个 `IdentityRelayAuthenticationFilter` 语义已经分叉

三个文件位于 `apex-file-service`、`apex-office-service`、`apex-workflow-service` 的 `interfaces/security` 包，长度 138 / 153 / 141 行。逐行 diff 后确认的**非表面差异**：

| 维度 | workflow | file | office |
| --- | --- | --- | --- |
| HMAC 兜底 | 仅当 `jwt-hmac-enabled=true` 且未配置 RSA 公钥时启用 | **只要配置了 secret 就启用**，无开关 | **只要配置了 secret 就启用**，无开关 |
| `clientType` 声明 | 读取并写入 `authentication.setDetails(...)` | 不读取 | 不读取 |
| Reactor Context | 只写 `TenantContext` | 额外写 `FileAccessContext.KEY` | 额外写 `MessageSocketRegistry` 票据分支 |
| 配置前缀 | `workflow.security.*` | `file.security.*` | `office.security.*` |
| RSA 算法约束 | 仅接受 `RS256` | 仅接受 `RS256`（写法不同） | 仅接受 `RS256` |
| 其他依赖 | 无 | `FileAccessContext` | 一次性票据、Origin 白名单 |

**风险点**：workflow 是三者中**唯一**要求显式 `jwt-hmac-enabled=true` 才启用 HMAC 的；file 与 office 只要配置了 secret 就会持有 HMAC 密钥，即使同时配置了 RSA 公钥。若某环境误留 `jwt-secret`，file / office 会接受 HMAC 签名的令牌，而 workflow 不会。这是“同一安全基线在不同服务上不等价”的典型问题，属于应当收敛的信号。

**建议方案**

- 在 `apex-web-spring-boot-starter` 中提供 `IdentityRelayAuthenticationFilter` 的**单一实现**，用 `IdentityRelayProperties`（`apex.security.identity.*`）统一配置前缀，保留 `hmac-enabled` 显式开关。
- 各服务通过 `@Bean` 覆盖或实现 `IdentityClaimsCustomizer` SPI 来注入服务特有行为（如 file 的 `FileAccessContext`），而不是复制整个 `filter` 方法。
- **迁移期**允许旧前缀继续生效（`@ConfigurationProperties` 支持多前缀映射需手写，见 §7），并加一条测试断言“任意服务在同时配置 RSA 与 HMAC 时的行为一致”。

## 四、P1：抽取 starter 的候选与边界

### 4.0 `@RequirePermission` 的三套实现（先解决重复，再谈 starter）

同一个注解 `com.apex.scaffold.common.annotation.RequirePermission` 在本仓库有三套互不相关的实现：

| 位置 | 权限来源 | 覆盖范围 | 备注 |
| --- | --- | --- | --- |
| `apex-system/.../interfaces/aspect/RequirePermissionAspect` | 本地 `PermissionService`（system 域） | 全部 `@annotation` | 支持 `Mono`/`Flux`，依赖 system 应用服务 |
| `apex-workflow-service/.../WorkflowSecurityAspect` | 远程 `WorkflowPermissionClient`（同租户 reader 凭据） | 仅 `workflowservice.interfaces..*` | 与 `WorkflowOperationLogAspect` 分离，避免重复审计 |
| `apex-office-service/.../OfficeSecurityAspect` | 远程 `OfficePermissionClient` | `@RequirePermission` **或** `@OperationLog` | 把授权与审计合并在一个切面 |

而 `WorkflowPermissionClient` 与 `OfficePermissionClient` 除类名/包名/配置前缀外**逐行相同**（各 121 行），`WorkflowPermissionProperties` 与 `OfficePermissionProperties` 同样只差前缀（各 40 行）。

**为什么不能简单合并成一个切面**：三者的权限数据源不同（本地服务 vs 远程快照），且 office 把审计与授权耦合在一个切面里、workflow 刻意拆开。合并的正确粒度是**抽出“权限判定 SPI”**，而不是合并切面：

```java
// 放在 apex-web-spring-boot-starter（或先放 common）
public interface PermissionEvaluator {
    /** 判断当前可信身份是否满足注解要求；实现方决定数据来源。 */
    Mono<Void> check(RequirePermission requirement);
}
```

- system 提供基于本地 `PermissionService` 的实现。
- workflow / office 提供基于远程快照的实现——此时两个重复的 `*PermissionClient` 收敛为一个通用的 `RemotePermissionSnapshotClient` + 各自的 `@ConfigurationProperties`。
- 切面本身只保留一份（`ReactivePermissionAspect`），从 `PermissionEvaluator` 取判定结果。
- office 的“授权+审计合一”需要拆开，否则 starter 里的切面要么耦合审计、要么与 office 本地实现重复。

**风险**：这条改造会触碰三个服务的授权路径，是**安全相关**变更。必须做到：每个服务迁移前后各跑一遍“有权限/无权限/身份缺失/远程失败”四类用例，并保留 `WorkflowSecurityAspect` 中“远程失败一律拒绝、不降级放行”的语义。

### 4.1 候选清单与量化证据

以下为 `src/main/java` 生产代码实测行数（不含测试、注释按行计）：

| 重复资产 | 出现位置 | 行数 | 差异性质 |
| --- | --- | --- | --- |
| `RequestHandlerUtil` | system 258 / office 225 / auth-service 133 / file-service 124 / workflow-service 124 | 合计 864 | 方法集合不同，非纯复制 |
| `IdentityRelayAuthenticationFilter` | office 153 / workflow 141 / file 138 | 合计 432 | 安全语义分叉（见 §3.2） |
| `AuthenticatedUser` | file 57 / workflow 57 / system 52 / office 49 | 合计 215 | 字段一致，包名不同 |
| `WorkflowPermissionClient` / `OfficePermissionClient` | 各 121 | 合计 242 | **除类名、包名、配置前缀外完全一致** |
| `WorkflowPermissionProperties` / `OfficePermissionProperties` | 各 40 | 80 | 同上 |
| `MessageUtils` | system 142 / office 142 | 合计 284 | 除包名与 JavaDoc 引用外一致 |
| `PageResponse` | common 111（含 `hasNext`、`records` 访问器）/ system 100（无）/ file 100（无） | 合计 311 | **契约不同，不可盲目合并**（AGENTS 已明示） |
| `PageRequest` | common 78 / system 74 / file 74 | 合计 226 | 同上 |
| 安全链 `SecurityWebFilterChain` | system 182 / auth 268 / office 75 / file 76 / workflow 57 / masking 63 | 合计 721 | 放行清单与认证方式不同 |

### 4.2 建议新增的 starter 及其职责边界

按“一个 starter 只解决一类能力”的原则，建议新增**两个**，而不是一个“大而全”的 `apex-web-starter`：

#### `apex-web-spring-boot-starter`

职责：WebFlux 接口层的**协议与横切**，不包含任何业务实体、Mapper 或具体权限数据源。

```
com.apex.scaffold.web
├── autoconfigure/
│   ├── WebSecurityAutoConfiguration     # 统一 SecurityWebFilterChain 基线
│   ├── IdentityRelayAutoConfiguration   # JWT 认证过滤器（可被服务 Bean 覆盖）
│   └── WebAspectAutoConfiguration       # @RequirePermission/@IdempotentApi/@OperationLog/@TenantAware
├── security/
│   ├── AuthenticatedUser                # 唯一 principal 类型
│   ├── IdentityRelayProperties
│   └── IdentityClaimsCustomizer         # SPI：服务注入特有声明（如 FileAccessContext）
├── web/
│   ├── RequestHandlerUtil               # 统一响应/校验工具（收敛方法并集）
│   └── ApiPathPolicy                    # 放行路径策略（见下）
└── META-INF/spring/...AutoConfiguration.imports
```

关键设计约束：

- **不要**把 `SystemConstants.PUBLIC_PATHS` 直接烧进 starter。改为 `ApiPathPolicy` 接口 + 默认实现，服务可通过 `@Bean` 覆盖。现有各服务的放行清单差异（`/internal/v1/**`、匿名分享路径、OAuth2 端点）必须在迁移时逐条对照，不能取并集。
- `RequestHandlerUtil` 的五份实现方法集合不同：auth-service 有 `validateAndMap(T)`（无 `ServerRequest` 重载），office 有 `getTenantId`/`safeParseLong`/`safeParseInt`/`ok(Result)`，system 有 `extractToken`/`extractQueryParam`/`extractPathParam`/`extractClientIp`。合并时以**并集**为基础，但需逐个确认语义（例如 `getTenantId` 在 office 读的是请求头还是安全上下文——这直接关系租户隔离，不能想当然）。
- `AuthenticatedUser` 合并前必须确认 `openId`、`deptId` 在四个服务中是否都被消费；若某服务恒为 `null`，应收窄为可选或在 starter 中保留全字段并注明。

#### `apex-events-spring-boot-starter`

职责：领域事件与 Outbox 的**装配**，不复制 `apex-infrastructure-messaging` 的实现类。

现状问题：`AuthCenterEventConfig` 手工 new 出 `DomainEventOutboxStore`、`DomainEventPublisherImpl`、`DomainEventOutboxDispatcher`，注释直言“因为 scanBasePackages 不含 `com.apex.scaffold.infrastructure`”。workflow 的 `WorkflowEventConfig` 是同样的“按 `apex.events.outbox.enabled` 二选一”模式（`MqWorkflowEventPublisher` / `LocalWorkflowEventPublisher`）。这是**同一套装配逻辑的第二次手写**，第三、第四个服务复制时会继续分叉。

starter 内容：

- `OutboxAutoConfiguration`：`@ConditionalOnProperty("apex.events.outbox.enabled")` 装配 Store / Publisher / Dispatcher，全部带 `@ConditionalOnMissingBean`。
- `EventPublisherAutoConfiguration`：按“有 `RocketMQTemplate` → MQ 版；否则 → Local 版”选择，**复用一套决策树**，而不是每个服务写一份 `@ConditionalOnBean` + `@ConditionalOnMissingBean` 组合。
- 必须保留 `@Primary` 语义与 `.imports` 顺序，避免与既有 `AuthCenterEventConfig` 同时存在时出现两个 `DomainEventPublisher`。

**风险**：`DomainEventOutboxStore` 使用 MySQL 方言（`ON DUPLICATE KEY UPDATE ... IF()`），H2 的 MySQL 模式不支持 `IF()`——这是 `AuthCenterEventConfig` 注释里已经记录的实测结论。starter 化时不能因为“看起来更通用”就默认在所有环境启用，必须保留 `enabled=false` 的降级路径，并在文档中写明。

### 4.3 不建议 starter 化的模块

- **`apex-infrastructure-*` 五个能力 JAR**：这些已经是“能力 JAR”，且内部大量类用 `@Component`/`@Service`（实测 persistence 3、cache 30、messaging 25、remote 8、integration 22 个类），依赖宿主的 `scanBasePackages`。实际依赖面也很窄：`dependency:tree` 显示只有 `apex-auth-service` 直接引用 cache / messaging / integration，`apex-job-service` 引用 remote；gateway、system-service、workflow-service、file-service、office-service、masking-service 均无直接依赖。若要真正 starter 化，需要先把这些 `@Component` 改成 `@Bean` + `@ConditionalOnMissingBean`，工作量远大于收益，且会与“能力 JAR”定位冲突。**建议维持现状，只在文档中明确“能力 JAR 不是 starter，需要被扫描”这一事实**（当前 `architecture-overview.md` 未说明，容易误判）。
- **`MybatisPlusConfig` / `MultiLevelCacheConfig` 的边界问题（需单独确认，不是 starter 化收益）**：这两个类已用 `@AutoConfiguration` 注册，但 `mybatisPlusInterceptor()` 与 `caffeineCacheManager()` 两个 `@Bean` **没有** `@ConditionalOnMissingBean`。目前没有冲突，因为 workflow-service 与 office-service 都不依赖 `apex-infrastructure-persistence`（`dependency:tree` 已确认），它们各自声明的 `MybatisPlusInterceptor` 与共享配置不会同时出现在同一容器。**但这是一个潜在的装配陷阱**：一旦某服务同时引入 persistence 能力 JAR 并声明自己的拦截器链，启动会因同类型多 Bean 失败。建议只补 `@ConditionalOnMissingBean`，不改变模块归属。
- **`apex-system`**：742 个生产类、10 万行，承载 system 域全部四层。它不符合 starter“无业务实体”的准入规则，强行拆分只会制造新的边界问题。
- **`apex-domain`、`apex-api-contract`**：已是纯契约模块，保持。

## 五、P2：SPI 化候选

### 5.1 office 平台客户端：用自注册替换 Bean 名拼接

**现状证据**

- `OfficeDocumentService`：`documentClients.get(platform + "DocumentClient")`；`OfficeCalendarService`、`OfficeApprovalService` 同构。
- 客户端类名与 Bean 名靠约定维持：`@Component("wecomApprovalClient")`、`@Component("dingtalkApprovalClient")`、`@Component("feishuApprovalClient")`。
- `ApprovalPort`、`CalendarPort`、`DocumentPort` 已经各自声明了 `String getPlatformType()`，但**没有被使用**。

**问题**：平台标识大小写、拼写、新增平台时忘记改 Bean 名，都会在运行时变成“不支持的办公平台”异常；编译器与 Spring 容器都无法提前发现。

**改造模板**（直接照搬同仓库 `OfficeMessageService` 的写法）：

```java
public OfficeDocumentService(
        DocumentInfoRepository repository,
        List<DocumentPort> ports) {
    this.documentClients = ports.stream()
            .collect(Collectors.toUnmodifiableMap(
                    port -> normalize(port.getPlatformType()),
                    Function.identity(),
                    (left, right) -> {
                        throw new IllegalArgumentException("重复的文档平台实现: " + left.getPlatformType());
                    }));
}
```

同时把 `OfficeMessageService` 的 `normalize` 逻辑抽成共享小工具（`OfficePlatform.of(String)`），让四个端口用同一套大小写/别名规则。**这一步不需要新模块**，属于 office 内部重构。

### 5.2 SSO 协议适配器：`switch` 工厂 → SPI 注册表

**现状**：`SsoProtocolAdapterFactory` 构造器注入 `SamlProtocolAdapter`、`CasProtocolAdapter`、`LdapProtocolAdapter`、`OidcProtocolAdapter` 四个具体类，再在 `getAdapter` 里 `switch`。新增协议必须改工厂源码，违反开闭原则；`isSupported` 还要再维护一份分支。

**改造**：让 `SsoProtocolAdapter` 增加 `String protocol()` 默认方法（或复用现有常量），工厂改为 `List<SsoProtocolAdapter>` 注入 + `Map` 注册。这样 `switch` 与 `isSupported` 的分支同时消失。

**注意**：这是 `apex-system` 内部重构，**不是**给第三方插件留扩展点。若要支持外部 jar 动态注册，才需要引入 `ServiceLoader`；当前仓库唯一的 `META-INF/services` 是 Sentinel 的 `HeartbeatSender`，没有自建 SPI 加载器实践。**建议先用 Spring 的 `List` 注入**，不要为“SPI 而 SPI”引入 `ServiceLoader` 与类加载器隔离问题。

### 5.3 `SystemJobPort` / `WorkflowJobPort`

两者都是“业务运行模块适配、starter 只定义端口”的正确结构：

- `WorkflowJobPort`：`WorkflowJobPortConfiguration` 把 `WorkflowTimeoutHandler::scanTimeoutTasks` 适配为端口。
- `SystemJobPort`：`SystemJobPortConfiguration`（位于 `apex-system-service`，该类只有 27 行）把 `JobService` 适配为端口。

**真正的问题在装配条件，不在端口设计**：`JobExecutorAutoConfiguration.systemJobBridge` 上的 `@ConditionalOnProperty(prefix = "apex.job.system", name = "job-id")` **只检查 `job-id`**，而方法体用 `@Value("${apex.job.system.tenant-id}")` 读取租户。也就是说：**只配置 `job-id` 而未配置 `tenant-id` 时，条件成立、Bean 定义被注册，但占位符解析失败导致启动报错**，且错误信息不会直接指出缺少的是 `tenant-id`。建议改为 `@ConfigurationProperties` 绑定 + 同时校验两个属性，让“启用执行器但不接管 system 任务”成为明确的合法组合。

### 5.4 `common/spi` 包为空

`apex-common/src/main/java/com/apex/scaffold/common/spi/` **目录存在但无任何文件**；而 `apex-common/README.md` 的包结构列表写着 `spi 可插拔 SPI 接口`，并声称允许 `spi/StorageProvider` 等接口。这是**文档与代码不一致**。

处理建议（二选一，需确认，见 §8 决策点 D3）：

- **方案 A（推荐）**：删除空目录，并从 README 包结构中移除 `spi` 行，把 SPI 契约按归属放到对应模块（如 `apex-web-spring-boot-starter` 的 `IdentityClaimsCustomizer`、office 的 `office.infrastructure.spi`）。
- **方案 B**：把真正跨服务的 SPI 契约（`DataScopeProvider`、`DrainParticipant` 已在 `context`/`governance` 包，不必迁移）集中到 `common/spi`，但那会造成包移动与兼容性成本，收益有限。

## 六、启动类与配置收敛（结构性）

### 6.1 五个启动类的扫描配置

现状（实测）：

| 启动类 | `scanBasePackages` | 额外注解 |
| --- | --- | --- |
| `SystemServiceApplication` | systemservice、interfaces、application、domain、infrastructure、common | `@ConfigurationPropertiesScan("com.apex.scaffold")`、`@MapperScan` 两个包 |
| `AuthServiceApplication` | authservice、auth、common | `@MapperScan`、`@EnableR2dbcRepositories` |
| `WorkflowServiceApplication` | workflowservice、workflow、common | `@MapperScan` |
| `FileServiceApplication` | fileservice、file、common | `@MapperScan` |
| `OfficeServiceApplication` | officeservice、office、common | `@ConfigurationPropertiesScan` 指定单类、`@MapperScan` 两个包 |

问题不在“重复几行注解”，而在于**扫描范围是隐式契约**：新增一个子包或把类移动到别的模块时，没有任何机制提示“它不会被扫描到”。这也是 §3.1 那类缺陷的根源。

**建议**：不追求把五个启动类合并成一个基类（`@SpringBootApplication` 不能被继承复用），而是：

1. 每个运行模块增加一个 `@ConfigurationPropertiesScan` 基包常量，并在架构测试中断言“所有 `@ConfigurationProperties` 类都位于已扫描包内”。
2. 增加一条测试：对每个运行模块，断言其依赖的业务库中所有 `@Component`/`@Service` 类的包都在 `scanBasePackages` 覆盖范围内。这条测试可以直接捕获 §3.1 的 office 幂等切面问题。

### 6.2 分页 DTO 的三份实现

`PageResponse` 存在于 common（含 `hasNext`、`records` 访问器）、system、file 三处；`PageRequest` 同样三处。AGENTS.md 已明确“不假定全仓字段统一”，**因此不建议合并**。但建议：

- 在 `apex-common/README.md` 或架构文档中列一张“分页类型归属表”，说明哪份用于哪个对外契约，避免新代码随手引用。
- 若未来要做统一，必须先确认序列化结果（`hasNext` 是否出现在对外 JSON）与契约测试，而不是简单的类替换。

## 七、实施顺序与验证要求

按“先修语义、再抽公共、最后做 SPI”推进，每一步都可独立回滚。

### 阶段 0：事实确认（不改代码）

1. 对 §3.1 与 §3.2 的每一项做**运行期验证**：在 office-service 启动日志中确认 `IdempotentApiAspect` 不存在；用一个重复 `Idempotency-Key` 请求确认未去重。**不要仅凭静态扫描就写“已失效”**——虽然静态证据链已经很完整，但运行期确认能排除“通过其他 `@Import` 意外装配”的可能。
2. 确认 `@TenantAware` 在 workflow-service 的实际效果：`TenantAspect` 在 system 包内，静态分析判定未装配；但因租户上下文已由过滤器写入，需验证“请求头与认证主体不一致时是否仍被放行”，以此判断严重程度。

### 阶段 1：修复失效的横切（P0）

1. 若确认 `IdempotentApiAspect` 未生效，**首选最小修复**：参照 `OperationAuditAutoConfiguration` 的既有先例，把切面下沉到 `apex-common` 并由 `.imports` 装配，而不是等新 starter。理由是这条路径已被 `@OperationLog` 验证过，改动面小、可快速验证。
2. 补一条“注解-切面可达性”架构测试（见 §3.1）。
3. 验证：`mvnd -T4 -B -pl apex-office-service -am test`；新增一个集成测试，断言重复幂等键返回 `REPEAT_SUBMIT`，且缺少幂等键时按 `required=true` 被拒绝。

### 阶段 2：`apex-web-spring-boot-starter`（P1）

1. 按 §4.2 建模块，先在 **file-service** 试点（它的安全链最简单、无 OAuth2），保留旧类并在测试中并行验证。
2. file 通过后迁移 workflow-service（注意 `clientType` 与 `TenantAware` 语义），再迁移 office-service（注意匿名分享路径与一次性票据）。
3. 每个服务迁移后必须验证：未认证 401、匿名放行路径、内部路径、租户上下文、`@RequirePermission`。
4. **不要**一次性改五个服务；auth-service 与 system 的安全链包含 OAuth2/OIDC/Keycloak，必须单独评估，建议最后做或不做。

### 阶段 3：`apex-events-spring-boot-starter`（P1）

1. 先只收敛 auth-service 的 `AuthCenterEventConfig`（它有最完整的注释与已知 H2 限制）。
2. 验证 `apex.events.outbox.enabled=true/false` 两条路径，以及 `@Primary` 是否仍唯一。
3. 再评估 workflow 的 `WorkflowEventConfig` 是否迁移——它的 `WorkflowEventPublisher` 是服务私有接口，与 `DomainEventPublisher` 不是同一契约，**不要为了统一而强行合并两个接口**。

### 阶段 4：SPI 化（P2）

按 §5.1（office 端口，收益最高、风险最低）→ §5.2（SSO 工厂）→ §5.3（job 端口条件）顺序推进。每一项都单独提交、单独验证。

### 阶段 5：清理与文档

1. 删除 `common/spi` 空目录或按决策点 D3 处理。
2. 更新 `apex-common/README.md` 包结构，使其与实际一致。
3. 在 `docs/02-architecture/architecture-overview.md` 补一句“能力 JAR 需要被宿主扫描，不是 starter”，避免后来者重复误判。

### 验证门禁

每阶段结束执行（在已 `source scripts/build/init-build-env.sh` 的 shell）：

```bash
mvnd -T4 -B -pl <受影响模块> -am test
mvnd -T4 -B spotless:check
git diff --check
```

合并前执行完整门禁：

```bash
mvnd -T4 -B clean verify
```

完整通过的判据是反应堆全部模块 `BUILD SUCCESS`、`Failures: 0 / Errors: 0`，而不是“编译通过”。本方案涉及的 spring 装配变更**必须**有至少一个 `@SpringBootTest` 级别的上下文加载测试，纯 Mockito 单测无法证明自动配置生效。

## 八、需要先行确认的决策点

以下问题无法从代码单方面判定，需业务/架构确认后才能进入实施：

| 编号 | 决策点 | 影响 |
| --- | --- | --- |
| D1 | `@IdempotentApi` 在 office 失效是“已知未启用”还是“遗漏”？ | 决定是补装配还是删除注解与文档承诺 |
| D2 | starter 中可选依赖缺失时的策略：启动失败 vs 明确降级日志 | 影响所有自动配置的 `@ConditionalOnMissingBean` 语义 |
| D3 | `common/spi` 空包：删除还是填充 | 影响 `apex-common/README.md` 与包结构稳定性 |
| D4 | file 的 HMAC 兜底条件是否应与 workflow 对齐（仅在无 RSA 时启用） | 安全基线一致性；对齐可能导致既有环境无法启动 |
| D5 | `apex-web-spring-boot-starter` 是否包含 `@RequirePermission` 切面 | system 的切面依赖 `PermissionService`（system 域），下沉会把 system 概念带进 starter |
| D6 | 三个分页 `PageResponse` 是否长期共存 | 影响对外契约与前端序列化 |

## 九、风险与反向意见

1. **starter 化会削弱“显式装配”的可读性。** 当前仓库的 `AuthCenterEventConfig` 虽然重复，但把“为什么需要手工装配”写在了注释里。抽成 starter 后，这类知识会散落到自动配置的 `@ConditionalOn*` 组合中。**缓解**：starter 内部保留同等密度的注释，并在各服务 `application.yml` 旁注明“由哪个 starter 装配”。
2. **统一安全链可能改变既有放行面。** 三个服务的放行路径不同（file 有匿名分享、auth 有 OAuth2 端点），取并集会扩大攻击面，取交集会导致功能不可用。**这是本方案风险最高的部分**，必须逐服务评审，不能批量替换。
3. **`@IdempotentApi` 的修复可能暴露真实重复请求。** 若该注解长期未生效，前端可能已经依赖“重复提交能成功”的行为。修复前应与前端确认 `Idempotency-Key` 的实际发送情况。
4. **SPI 化不等于动态插件。** 本方案所有 SPI 都基于 Spring 容器内的 `List`/`Map` 注入，**不引入** `ServiceLoader`、类加载器隔离或运行时热插拔。理由：当前没有任何跨 jar 动态发现的真实需求，而 `ServiceLoader` 会带来类加载、版本冲突与调试困难。这一点需要在评审时明确，避免被误解为“支持第三方插件”。
5. **量化收益要谨慎表述。** 上表中的行数只能说明“重复规模”，不能直接换算为“减少多少维护成本”。真正的收益是“行为一致性可被测试断言”，这一点比行数更重要。

## 十、附：本次评审的证据索引

| 结论 | 证据文件 |
| --- | --- |
| 已有 4 个标准 starter | `apex-*/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| `@IdempotentApi` 使用与实现分离 | `apex-office-service/.../OfficeAdminHandler.java`、`OfficeMessageHandler.java`、`apex-system/.../IdempotentApiAspect.java` |
| 三个 JWT 过滤器分叉 | `apex-{file,office,workflow}-service/.../interfaces/security/IdentityRelayAuthenticationFilter.java` |
| 权限客户端完全重复 | `apex-{workflow,office}-service/.../{Workflow,Office}PermissionClient.java`（121 行，除命名外一致） |
| office Bean 名拼接 | `apex-office/.../application/OfficeDocumentService.java:384` 等 |
| office 正确写法（对照） | `apex-office/.../application/OfficeMessageService.java:52` |
| SSO 工厂 `switch` | `apex-system/.../application/support/sso/SsoProtocolAdapterFactory.java` |
| `common/spi` 为空但 README 有描述 | `apex-common/src/main/java/com/apex/scaffold/common/spi/`、`apex-common/README.md` |
| Outbox 手工装配 | `apex-auth-service/.../config/AuthCenterEventConfig.java` |
| 能力 JAR 依赖扫描而非自动配置 | `apex-infrastructure-*/src/main/java` 中 `@Component`/`@Service` 类统计 |

> 说明：本文所有行号与行数来自 2026-10-28 的 `main` 工作区实测（`find` + `wc -l`），未运行构建或测试。任何“可用/不可用”的判断在实施前都应按 §7 阶段 0 做运行期确认。
