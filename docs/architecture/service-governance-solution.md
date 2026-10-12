# 单体阶段服务治理能力落地方案

> 文档状态: 方案评审稿
>
> 适用项目: apex-scaffold
>
> 当前架构: 单体应用 + DDD 分层, Spring Boot WebFlux
>
> 目标部署演进: `java -jar` → Docker Compose → Kubernetes

## 1. 目标与结论

### 1.1 目标

在尚未拆分为微服务之前,为当前单体应用建立可逐步演进的运行治理能力:

1. 应用实例可以在停止、发布、重启时完成优雅关机,尽量避免新请求、异步任务和消息消费被强制中断。
2. 应用可以根据健康状态、依赖状态、错误率和响应时间实施自动降级,并通过注册中心或流量入口完成实例上下线。
3. 应用可以根据运行指标进行扩缩容,且方案能够从普通 `java -jar` 部署平滑演进到 Docker Compose 和 Kubernetes。

### 1.2 总体结论

| 能力 | 单体阶段是否可实现 | `java -jar` | Docker Compose | Kubernetes | 结论 |
|---|---|---|---|---|---|
| 优雅关机 | 可以 | 可以 | 可以 | 可以 | 当前项目已有基础实现,需要补齐关停顺序、真实排空确认和发布联动 |
| 自动降级 | 可以 | 可以 | 可以 | 可以 | 属于进程内治理,不要求先拆微服务;当前已有 Sentinel、Resilience4j 基础 |
| 自动上下线 | 有条件可以 | 依赖负载均衡或 Nacos | 依赖 Nacos/代理或 Compose 编排 | 由 Service、Probe、Deployment 协同完成 | 单体实例也可以注册为一个服务实例,但必须明确流量入口和状态语义 |
| 自动扩缩容 | 单实例不能自动扩容 | 需要外部编排/脚本/负载均衡 | Compose 原生能力有限,需要额外控制器 | 原生支持 HPA | 真正可靠的自动扩缩容应以 Kubernetes 为最终落点 |

核心判断是:微服务化不是实现这三项能力的前置条件。单体应用可以先完成实例级治理,但自动扩缩容和跨实例上下线始终需要外部运行平台、流量入口和可观测指标配合。

## 2. 当前项目现状

### 2.1 应用与依赖

- 根项目使用 Spring Boot `3.5.15`、Spring Cloud `2025.0.0`、Spring Cloud Alibaba `2025.0.0.0`。
- 项目采用单体应用 + DDD 分层结构,接口、应用、领域和基础设施能力集中在同一应用进程中。
- 应用使用 Spring WebFlux,因此关停期间既要处理 Web 服务停止接收请求,也要关注 Reactor 链路、异步执行器和消息消费者。
- 依赖和配置中已经存在 Nacos、Sentinel、Resilience4j、Actuator、Prometheus、RocketMQ 等治理基础设施。

### 2.2 已有优雅关机能力

`apex-interfaces/src/main/resources/application.yml` 已配置 `server.shutdown: graceful`。

`apex-interfaces/src/main/java/com/apex/scaffold/interfaces/config/GracefulShutdownManager.java` 已实现 `SmartLifecycle`:

- 参与 Spring 容器生命周期回调。
- RocketMQ 消费者停止后固定等待 15 秒。
- 记录 `@Async` 执行器状态。
- 线程池本身依赖 Bean 生命周期关闭。

现状可以作为第一阶段基础,但固定等待不是“已排空”的证明,且当前实现没有把 readiness 置为不可接流、未对关键业务任务做明确计数,也没有在应用层显式编排 Nacos 下线动作。Nacos 的注册生命周期目前主要由 Spring Cloud 组件处理。

### 2.3 已有降级、限流和熔断能力

`apex-interfaces/src/main/java/com/apex/scaffold/interfaces/config/SentinelConfig.java` 已配置:

- 约 43 条流控规则,覆盖认证、文件、用户、媒体、排行等资源。
- 约 30 条降级规则,包含异常比例和慢调用比例。
- 系统保护规则,包含 CPU 和入口 QPS 等约束。
- Nacos 动态规则数据源配置能力。

因此当前项目具备“资源级限流/熔断/降级”的基础。后续重点不是重新引入框架,而是统一降级返回语义、补齐依赖故障策略、定义自动触发与恢复边界,并区分进程内降级和实例级上下线。

> **口径一致性说明（2026-10-12，网关韧性落地）**：上游 `apex-gateway` 已新增独立的网关层韧性能力
> （非替换本节的 Sentinel 资源级限流，二者并行）：
> - **熔断**：路由级 Resilience4j `CircuitBreaker`，按路由覆盖阈值，OPEN 返回 `503` + 统一 `Result`
>   （`com.apex.scaffold.gateway.fallback`）。
> - **限流**：网关层全局限流，实现已由“固定窗口计数”统一为 **令牌桶（token-bucket）**，
>   `apex.gateway.rate-limit.algorithm` 默认 `token-bucket`（可切换 `fixed-window`），超限 `429`，
>   Redis 异常时降级放行并记录 WARN 与指标。
> - **负载均衡**：`apex.gateway` 按 `X-Gray-Tag` 匹配 `metadata[gray-tag]` 实例（`com.apex.scaffold.gateway.lb`）。
> 上述实现细节、装配类与指标（`apex.gateway.*`）以源码与 `.trae/documents/gateway-resilience-enhancement-plan.md` 为准。

### 2.4 已有健康检查与注册能力

`application.yml` 已配置 Actuator liveness/readiness probes,readiness 包含数据库和 Redis 检查;同时存在 Nacos discovery 开关、注册开关和实例权重配置。

这说明项目可以实现以下状态分层:

- `UP`: 可以正常接收流量。
- `DEGRADED`: 进程仍然可服务,部分非核心能力返回兜底结果。
- `OUT_OF_SERVICE`: 实例不再接收新流量,但允许存量请求和后台任务排空。
- `DOWN`: 进程或关键依赖不可用,由平台重启或人工介入。

需要避免把所有业务依赖故障都直接映射为 liveness 失败。liveness 失败会触发重启,而单个非核心依赖异常通常应该先降级或只影响 readiness。

### 2.5 已有扩缩容能力

`k8s/deployment.yaml` 已存在:

- Deployment 资源请求和限制。
- liveness/readiness HTTP 探针。
- HPA `minReplicas: 2`、`maxReplicas: 10`。
- CPU 目标利用率 70%、内存目标利用率 80%。

因此 Kubernetes 方向已有初始实现。当前缺口主要是单体多副本运行的共享状态、会话/任务/消息消费一致性、扩缩容指标质量、发布期间下线协作以及 HPA 参数和容量基线验证。

## 3. 总体架构方案

### 3.1 治理边界

单体阶段将“服务”定义为一个可独立启动、停止、注册、探活和扩缩容的应用实例,而不是一个已经拆分的业务微服务。应用内部仍保持单体边界,治理能力位于接口层和运行平台层。

分层职责如下:

- 应用进程: 处理流量接纳、请求排空、业务降级、执行器关闭和状态发布。
- Actuator/指标: 暴露 liveness、readiness、业务指标和资源指标。
- Nacos 或流量入口: 维护实例可接流量状态和权重。
- Docker Compose: 提供容器生命周期和健康检查,不负责完整的生产级自动扩容。
- Kubernetes: 负责副本数、滚动发布、探针、Service 路由和 HPA。
- Prometheus/Grafana/Loki: 提供指标、日志和告警,不直接替代扩容控制器。

### 3.2 状态机

建议统一采用以下实例状态:

```text
STARTING -> READY -> DEGRADED
    |         |          |
    v         v          v
  FAILED <- DRAINING <- OUT_OF_SERVICE
                 |
                 v
              STOPPED
```

状态含义:

- `STARTING`: 应用已启动但未完成关键依赖检查,不得接收业务流量。
- `READY`: 核心依赖正常,可正常提供服务。
- `DEGRADED`: 核心链路仍可用,非核心能力或部分依赖采用兜底策略。
- `OUT_OF_SERVICE`: 主动下线或 readiness 失败,不再接收新流量。
- `DRAINING`: 等待存量 HTTP 请求、消息和后台任务结束。
- `FAILED`: 进程不可用或关键故障,由平台重启/替换。
- `STOPPED`: 生命周期结束。

不建议把 `DEGRADED` 直接注册为完全不可用。是否继续接收流量应由业务影响决定,例如核心交易不可用时可以 readiness 失败,而搜索、推荐、统计不可用时保留核心接口并进行局部降级。

### 3.3 设计模式与可替换架构

为保证部署方式从 `java -jar` 平滑迁移到 Docker Compose 和 Kubernetes,应用治理能力采用“稳定抽象 + 平台适配器”的结构,业务代码不得直接调用 Nacos、Docker API 或 Kubernetes API。

采用的设计模式如下:

- 策略模式:以 `DeploymentGovernanceStrategy` 定义上线、下线、开始排空、恢复接流和查询能力等统一行为,三种部署方式提供独立策略实现。
- 工厂/注册表模式:由 `DeploymentGovernanceStrategyRegistry` 根据最终解析出的运行模式返回唯一策略,调用方不使用 `if/else` 判断平台。
- 适配器模式:将 Spring Availability、Nacos 注册、外部代理以及 Kubernetes readiness 语义适配为统一接口,隔离第三方 SDK 和平台差异。
- 模板方法模式:由 `GracefulShutdownCoordinator` 固定“标记不可接流 → 等待传播 → 停止新工作 → 排空 → 关闭”的骨架,各策略只实现平台相关步骤。
- 状态模式:由 `ServiceLifecycleStateMachine` 约束 `STARTING`、`READY`、`DEGRADED`、`OUT_OF_SERVICE`、`DRAINING`、`FAILED`、`STOPPED` 的合法迁移,拒绝重复下线和非法恢复。
- 观察者模式:利用 Spring 事件和 Micrometer 指标发布状态变化,监控、审计与告警订阅事件,避免治理主流程反向依赖监控实现。
- 熔断器模式:Sentinel/Resilience4j 负责资源与依赖故障隔离,不与部署策略混合。

建议接口职责:

```java
public interface DeploymentGovernanceStrategy {
    DeploymentMode mode();
    void prepareForTraffic();
    void withdrawFromTraffic(WithdrawalReason reason);
    void restoreTraffic();
    GovernanceCapabilities capabilities();
}
```

接口方法表达业务语义而不是平台动作。例如调用方只请求 `withdrawFromTraffic`,由 standalone 策略发布 readiness 并提示外部摘流,compose 策略同时配合健康检查/Nacos,kubernetes 策略只发布 readiness,让 Service 与 EndpointSlice 完成摘流。实现时所有可能阻塞的 SDK 调用必须避开 WebFlux 事件循环,并设置超时和幂等保护。

### 3.4 部署模式配置与自动选择

建议新增统一配置:

```yaml
apex:
  governance:
    deployment-mode: auto
    auto-detection:
      enabled: true
      fail-on-ambiguous-environment: true
    registration:
      provider: auto
      fail-mode: auto
```

`deployment-mode` 允许以下值:

- `auto`:启动时自动识别,推荐作为默认值。
- `standalone`:普通 `java -jar`、systemd 或无容器编排环境。
- `compose`:Docker Compose 环境。
- `kubernetes`:Kubernetes Pod 环境。

选择优先级必须固定:

1. 显式配置 `APEX_GOVERNANCE_DEPLOYMENT_MODE` 或配置文件值最高优先级。
2. `auto` 模式下检测 Kubernetes 官方服务账号文件和 `KUBERNETES_SERVICE_HOST`/`KUBERNETES_SERVICE_PORT`。
3. 检测项目约定的 `APEX_DEPLOYMENT_PLATFORM=compose` 标记;不能只依赖 `/.dockerenv`,因为 Kubernetes 容器也可能存在该文件。
4. 均未匹配时选择 `standalone`。

自动识别只在 Spring 容器启动期间执行一次,选定后整个进程生命周期保持不变。禁止因环境变量或探针短暂变化在运行中切换策略;部署平台切换应通过新实例启动和旧实例优雅下线完成。

冲突处理:

- 显式配置与检测结果不一致时以显式配置为准,记录一次告警但不输出敏感环境变量值。
- 同时检测到多个平台标记且未显式配置时,生产默认启动失败;开发环境可配置为选择优先级最高的平台并告警。
- 配置为 `kubernetes` 但关键环境标记缺失时启动失败,防止误用不存在的平台能力。
- 配置为 `compose` 但未启用 Nacos/代理时允许启动,但能力声明中 `supportsTrafficWithdrawal=false`,监控持续告警。
- `standalone` 不允许应用自行创建或销毁进程,只负责状态发布、优雅关机和与已配置注册中心协作。

`registration.provider` 独立于部署模式,可取 `none`、`nacos`、`platform`、`auto`。建议映射为:

| 部署模式 | 默认注册方式 | 上下线动作 | 扩缩容责任方 |
|---|---|---|---|
| standalone | Nacos 已启用则 `nacos`,否则 `none` | Nacos 权重/注销或外部 LB 脚本 | systemd/运维控制器,首阶段仅半自动 |
| compose | Nacos 已启用则 `nacos`,否则外部代理健康检查 | readiness + Nacos/代理 | 外部控制器或人工 `--scale` |
| kubernetes | `platform` | Spring readiness + Service 路由 | HPA/Deployment |

### 3.5 Spring 装配方案

遵循项目已有 `@ConfigurationProperties` 与 `@ConditionalOnProperty` 风格:

- `GovernanceProperties` 负责配置绑定和启动校验。
- `DeploymentModeResolver` 只负责按固定优先级解析运行模式,无上下线副作用。
- 每个平台策略作为独立 Bean,通过条件装配或统一注册表管理。
- `DeploymentGovernanceStrategyRegistry` 在启动时校验最终只能选中一个策略。
- `GracefulShutdownCoordinator`、健康状态控制器和后续扩容指标发布器只依赖统一接口。
- 通过 `GovernanceCapabilities` 明确当前策略是否支持主动摘流、权重调整、自动扩缩容和渐进恢复,不对不支持的功能静默假成功。

推荐包边界位于接口层运行配置附近,但避免继续扩大单个 `GracefulShutdownManager` 的职责:

```text
com.apex.scaffold.interfaces.governance
├── config
├── lifecycle
├── platform
│   ├── standalone
│   ├── compose
│   └── kubernetes
└── observability
```

平台策略只处理实例运行治理,不能包含业务降级规则。Sentinel/Resilience4j 仍按资源和依赖配置,从而保证部署模式变化不会改变业务降级语义。

## 4. 方案一:优雅关机

### 4.1 目标行为

收到 SIGTERM、容器停止或 Kubernetes 滚动更新信号后,应用按以下顺序执行:

1. 进入 `DRAINING` 并立即让 readiness 变为失败或从注册中心摘除。
2. 停止接收新 HTTP 请求,保留已建立请求在限定时间内完成。
3. 停止拉取新消息,等待正在处理的消息完成或按重试策略重新投递。
4. 停止定时任务和后台任务接收新工作。
5. 等待 `@Async` 与自定义线程池任务结束。
6. 完成资源关闭,退出进程。
7. 超过总超时时间后强制退出,确保发布不会无限阻塞。

推荐默认预算:

- readiness 摘除后传播等待: 5-10 秒。
- HTTP 优雅关闭: 20-30 秒。
- RocketMQ 消费排空: 15-30 秒,以实际未完成数为准,不能只固定 sleep。
- 总关停预算: 45-60 秒。

具体值应以最长业务请求、消息可见性/重试时间和平台 termination grace period 取最大安全值。

### 4.2 代码方案

在现有 `GracefulShutdownManager` 上增量改造,不新建独立治理服务:

- 增加可观测的生命周期状态,至少能区分运行、排空和停止。
- 增加 HTTP 在途请求计数或基于 WebFilter 的请求跟踪,仅统计需要排空的业务请求。
- 增加消息在途计数,消费者停止后轮询计数归零,达到超时则记录未完成数量。
- 增加后台任务计数或统一使用现有执行器的 shutdown/awaitTermination 语义。
- 将超时时间配置化,提供环境变量覆盖,并校验超时为正数。
- 将“开始排空”动作放在 readiness 变更和容器停止流程之前,保证流量先迁移再关闭。
- 保持 Spring Boot `server.shutdown: graceful`,让 Web Server 负责连接级优雅停止。

建议配置项:

```yaml
apex:
  lifecycle:
    graceful-shutdown-enabled: true
    readiness-drain-delay: 8s
    http-drain-timeout: 30s
    mq-drain-timeout: 20s
    task-drain-timeout: 20s
    total-shutdown-timeout: 60s
```

以上配置名称为方案建议,实施时应按项目现有 `apex` 配置命名规范落地。

### 4.3 各部署方式

`java -jar`:

- 使用 systemd、Supervisor 或发布脚本发送 SIGTERM。
- 发布脚本必须先从负载均衡器摘除实例,再发送 SIGTERM。
- 若无负载均衡器,无法保证请求迁移,只能做到进程内部尽量优雅。

Docker Compose:

- 使用 `stop_grace_period` 覆盖总关停预算。
- 配置容器 healthcheck 指向 readiness,不要只探测端口。
- Compose 单机多副本需要外部代理或服务发现,否则副本增加后不一定有流量进入。

Kubernetes:

- 使用 readinessProbe 控制流量摘除,preStop 可用于触发排空,terminationGracePeriodSeconds 覆盖总预算。
- preStop 不应承担长时间业务排空的全部逻辑,应用自身必须能处理 SIGTERM。
- Deployment 滚动更新使用 `maxUnavailable: 0` 和合理的 `maxSurge`,避免单体副本全部同时退出。

### 4.4 故障与边界

- 请求超过关停预算:记录请求摘要和耗时,由上游重试或返回超时,不得无限等待。
- 消息处理超过预算:优先保证消息可重试和幂等,不得为了“清零计数”无限阻塞进程退出。
- 外部依赖已故障:停止阶段不再发起新的非必要远程调用。
- SIGKILL 或节点宕机:无法保证优雅关机,必须依赖消息重试、数据库事务和幂等设计兜底。
- 多实例同时关停:平台应设置最小可用副本,应用内部不能解决容量不足问题。

## 5. 方案二:自动降级与上下线

### 5.1 降级层次

按影响范围划分为四层:

1. 资源级限流:超过 QPS/并发阈值时快速拒绝,保护进程。
2. 接口级降级:非核心接口返回缓存、空结果或明确的业务兜底响应。
3. 依赖级熔断:数据库、Redis、搜索、远程 HTTP、消息等依赖连续失败或慢调用时短路。
4. 实例级上下线:实例无法提供核心服务、正在发布或资源耗尽时从流量入口摘除。

Sentinel 负责资源级流控、异常比例和慢调用规则;Resilience4j 适合补充代码级 CircuitBreaker、TimeLimiter、Retry 和 Bulkhead。两套机制必须明确边界,同一接口不要无理由重复套用两套熔断器,避免阈值叠加和状态难以解释。

### 5.2 核心与非核心能力划分

实施前必须建立接口目录,至少标记:

- 核心接口:不可用时实例应 readiness 失败或触发人工告警。
- 可降级接口:允许缓存、空结果、默认配置或异步化。
- 可拒绝接口:高峰期直接限流,不影响核心链路。
- 管理接口:仅内部网络可访问,不能参与公网流量判断。

降级返回必须统一:

- HTTP 状态码和业务错误码有明确约定。
- 返回体不泄露下游异常、堆栈和敏感配置。
- 日志记录资源名、触发类型、实例标识和采样信息,不记录密钥和完整用户敏感数据。
- 通过指标区分限流、熔断、超时、缓存兜底和主动下线。

### 5.3 自动触发策略

建议以滑动窗口和连续状态判断,避免单点抖动:

- 错误率达到阈值并持续多个窗口:触发接口熔断或降级。
- P95/P99 延迟超过阈值并伴随并发升高:触发慢调用降级。
- Redis、搜索等非核心依赖连续失败:启用缓存或空结果策略。
- 数据库连接池耗尽、JVM 内存压力过高、线程池队列持续堆积:停止接收非核心流量,必要时实例下线。
- liveness 只判断进程自身是否还活着;readiness 判断当前是否适合接收流量。

自动恢复必须带半开探测、最小开路时间、最大恢复速率和冷却时间。恢复过程中先恢复非核心流量,观察稳定后再恢复核心或高并发流量。

### 5.4 自动上下线流程

主动下线:

1. 识别触发原因并写入结构化事件。
2. 将 readiness 置为不可接流。
3. 等待路由层传播摘除。
4. 关闭或降低 Nacos 实例权重,必要时注销实例。
5. 排空存量请求、消息和任务。
6. 若可恢复,先恢复 readiness 并以低权重试流;否则交给平台重启。

自动上线:

1. 进程启动并完成基础配置加载。
2. 核心依赖检查通过。
3. 暖缓存、初始化连接池和关键资源。
4. readiness 置为可接流。
5. 在 Nacos/Service 中以低权重或逐步流量加入。
6. 稳定观察后恢复正常权重。

Nacos 的 `enabled`、`register-enabled` 和 `weight` 应作为明确的部署开关管理。注册中心不可用时,应根据部署模式选择 fail-open 或 fail-closed,不能由框架默认行为决定:

- 核心生产流量:建议 fail-closed,无法确认实例状态时不接入新流量。
- 单机开发环境:可 fail-open,保证本地启动不依赖 Nacos。

### 5.5 防抖与人工保护

- 自动下线使用连续失败次数或持续时间,单次探测失败不触发。
- 自动上线使用冷却时间,避免反复上下线。
- 提供全局降级开关和按资源关闭自动恢复的保护开关。
- 所有自动决策要可审计,记录规则版本、触发指标和恢复时间。
- 规则通过 Nacos 动态发布时要有版本、回滚和变更权限控制。

## 6. 方案三:自动扩缩容

### 6.1 重要限制

单个 `java -jar` 进程不能自己复制出第二个进程并安全接收流量,因此“应用根据指标自动扩缩容”必须由外部控制器执行。应用能做的是暴露可靠指标、声明健康状态和保持多实例运行所需的无状态约束。

### 6.2 `java -jar` 阶段

建议定位为半自动扩容:

- 应用暴露 Prometheus 指标:请求率、P95/P99、错误率、活动请求数、线程池队列、连接池使用率、JVM 内存、GC、消息堆积。
- 发布脚本或运维控制器根据阈值启动/停止 JAR 实例。
- 使用 Nginx、HAProxy、云负载均衡或 Nacos 客户端将新实例加入/摘除。
- 启动后必须先通过 readiness,再加入流量。
- 缩容前必须先摘流量并等待优雅关机。

如果只有一台机器,此阶段只能做进程重启或资源调节,不能获得真正的横向高可用。多机扩容还需要统一配置、日志、锁、会话和任务调度策略。

### 6.3 Docker Compose 阶段

Compose 适合验证容器化和固定副本,不适合作为完整的生产级自动扩容控制平面:

- `docker compose up --scale` 可以手动调整副本数。
- 需要外部脚本读取 Prometheus 指标并调用 Docker API/Compose 命令,才能实现自动调整。
- 需要独立的反向代理或 Nacos 服务发现将流量分发到副本。
- 需要限制扩缩容频率、设置最小/最大副本数、冷却时间和失败回滚。
- 单机 Docker Compose 无法解决主机容量不足和节点故障。

该阶段建议只实现“可扩容运行”和“半自动扩容”,把控制逻辑保留在部署运维层,不要将 Docker API 权限放进业务容器。

### 6.4 Kubernetes 阶段

Kubernetes 是最终推荐方案:

- Deployment 管理单体实例副本。
- Service 负责稳定访问地址和就绪实例路由。
- readinessProbe 负责流量准入,livenessProbe 负责异常重启。
- HPA 根据 CPU、内存和自定义指标修改副本数。
- Prometheus Adapter 可将 RPS、P95、活动请求数或消息堆积转换为 HPA 指标。
- PodDisruptionBudget 保证维护或扩容期间仍保留最小可用副本。
- RollingUpdate 配合优雅关机完成逐步发布。

现有 HPA 的 CPU 70%、内存 80%可以作为初始值,但不能直接视为经过容量验证的最终参数。推荐扩展指标优先级:

1. 活动请求数或每 Pod 请求率。
2. P95/P99 延迟和错误率作为告警与发布门禁。
3. RocketMQ 消息堆积作为消费者场景的扩容指标。
4. CPU、内存作为资源保护指标。

缩容必须设置稳定窗口和最大缩容速率,避免短时流量波动导致频繁创建/销毁 Pod。单体应用若包含定时任务、分布式锁或本地缓存,扩容前必须先完成外置化或明确每个副本的唯一职责。

## 7. 单体多副本前置条件

自动上下线和扩缩容只有在以下条件满足后才可进入生产:

- 登录态、会话和验证码不依赖单机内存;使用 Redis 或无状态 Token。
- 文件、导出物和临时结果不只保存在本地磁盘;使用共享对象存储或明确单实例亲和策略。
- 定时任务有分布式锁或集中调度,不会因副本增加而重复执行。
- 消息消费具备幂等键、重试和死信处理,消费者扩容不会造成业务重复。
- 数据库连接池按副本总数计算,扩容上限不能突破数据库连接和 QPS 容量。
- 本地缓存有容量、过期和失效策略,不能作为一致性数据源。
- 所有实例使用统一配置、时钟、日志字段和实例 ID。
- 管理接口、探针接口和业务流量隔离,避免探针触发业务副作用。

## 8. 分阶段实施路线

### 阶段 A: `java -jar` 可治理

目标是先把应用做成可被外部平台正确停止和观测的单体实例:

- 完善 `GracefulShutdownManager`,实现 readiness 先摘除、真实排空计数、分阶段超时和结构化指标。
- 梳理核心/非核心接口,统一 Sentinel/Resilience4j 规则和降级响应。
- 完善 Actuator probes、Prometheus 指标和日志字段。
- 通过 systemd/发布脚本实现摘流量、SIGTERM、失败回滚。
- 验证单实例发布、依赖故障降级和超时强制退出。

阶段验收:停止期间无新流量进入,存量请求在预算内完成;非核心依赖故障不拖垮核心接口;应用可被监控发现并人工安全上下线。

### 阶段 B: Docker Compose 多副本

目标是验证容器化和多实例行为:

- 增加容器 healthcheck、stop grace period 和资源限制。
- 通过反向代理或 Nacos 完成副本发现和路由。
- 手动执行 `--scale` 验证启动、就绪、摘流和缩容。
- 用外部脚本实现受控半自动扩缩容,不在业务容器内操作 Docker。
- 验证 Redis、数据库、消息和本地文件相关的多副本风险。

阶段验收:至少两个副本可以同时提供流量,任一副本停止不导致核心请求整体不可用;扩缩容后连接池和消息处理仍在容量边界内。

### 阶段 C: Kubernetes 生产化

目标是由 Kubernetes 接管副本编排和自动扩缩容:

- 完善 Deployment、Service、HPA、PDB、ConfigMap/Secret 和滚动发布参数。
- 以 readiness 控制流量,以 liveness 控制重启,以 SIGTERM 触发应用排空。
- 先使用 CPU/内存 HPA,完成压测后引入 RPS、延迟或消息堆积等自定义指标。
- 配置 HPA 稳定窗口、扩缩容策略、最小/最大副本和告警。
- 建立发布前容量检查、故障演练和回滚流程。

阶段验收:高峰压测时副本数按指标扩展,流量恢复后稳定缩容;滚动发布没有整体不可用;实例异常能被重启,依赖局部故障能降级而非级联故障。

## 9. 建议修改文件范围

代码实施阶段建议优先在以下既有文件和相邻测试目录内增量修改:

- `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/config/GracefulShutdownManager.java`:保留为生命周期入口,逐步委托给新的关停协调器。
- `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/governance/config/GovernanceProperties.java`:绑定部署模式、自动检测、注册方式和失败策略配置。
- `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/governance/platform/DeploymentModeResolver.java`:按显式配置、Kubernetes、Compose、standalone 的固定优先级解析一次运行模式。
- `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/governance/platform/DeploymentGovernanceStrategy.java`:声明平台无关的流量准备、摘除、恢复和能力查询契约。
- `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/governance/platform/DeploymentGovernanceStrategyRegistry.java`:注册策略并保证每个进程唯一选择。
- `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/governance/platform/standalone/StandaloneGovernanceStrategy.java`:处理普通 JAR 环境的 readiness 与可选 Nacos 联动。
- `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/governance/platform/compose/ComposeGovernanceStrategy.java`:处理 Compose 健康检查、Nacos/代理能力声明,不直接操作 Docker API。
- `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/governance/platform/kubernetes/KubernetesGovernanceStrategy.java`:通过 Spring Availability/readiness 与 Kubernetes Service 协作,不在应用内操作 Deployment/HPA。
- `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/governance/lifecycle/GracefulShutdownCoordinator.java`:以模板方法固定摘流、传播等待、停止新工作、排空和退出顺序。
- `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/governance/lifecycle/ServiceLifecycleStateMachine.java`:维护合法生命周期迁移和幂等操作。
- `apex-interfaces/src/main/resources/application.yml`:增加 `apex.governance` 配置并保留生命周期、探针和指标参数。
- `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/config/SentinelConfig.java`:规则边界、动态配置和统一降级指标。
- `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/config/SentinelNacosDataSourceConfig.java`:动态规则加载、版本和失败策略,以实际文件存在情况为准。
- `k8s/deployment.yaml`:探针、滚动更新、termination grace period、PDB/HPA 配套配置。
- `docker-compose.yml`:healthcheck、stop grace period、资源限制和多副本验证所需配置。
- 现有执行器、RocketMQ 监听器、WebFilter 和异常处理类:仅在排空计数和降级语义确有归属时修改。
- 对应模块现有测试目录:补充生命周期、降级触发、探针状态和配置绑定测试。

不建议在本阶段拆分 Maven 模块或按业务域拆分服务。目标是建立可迁移的治理接口,将微服务拆分留到后续独立项目计划。

## 10. 测试与验收矩阵

### 10.1 部署模式选择与策略装配

- 未设置显式配置且无平台标记时解析为 `standalone`。
- 设置 `APEX_DEPLOYMENT_PLATFORM=compose` 时解析为 `compose`。
- Kubernetes 服务账号文件及官方环境变量同时存在时解析为 `kubernetes`。
- 显式 `deployment-mode` 覆盖自动检测结果,并记录不含敏感值的告警。
- 多个平台标记冲突时按 `fail-on-ambiguous-environment` 启动失败或告警降级。
- 每种模式只装配并选择一个 `DeploymentGovernanceStrategy`,不存在零个或多个活动策略。
- 不支持主动摘流或自动扩容的平台通过 `GovernanceCapabilities` 返回明确的 `false`,不会静默成功。
- 应用运行期间改变环境标记不会切换策略,重启后才重新解析。
- standalone → compose → kubernetes 切换集成测试中,业务调用方代码和接口契约保持不变。

### 10.2 优雅关机

- 启动实例后发送 SIGTERM,确认 readiness 先变为不可用。
- 关停期间新请求被上游摘除,已开始请求能在预算内完成。
- RocketMQ 停止拉取新消息,在途消息完成、重试或进入可追踪失败路径。
- `@Async` 和自定义线程池任务不被静默丢弃。
- 超过排空预算时进程仍能退出,日志包含各类未完成数量。
- Docker stop 和 Kubernetes Pod 删除行为与直接 SIGTERM 一致。

### 10.3 降级与上下线

- 错误比例触发熔断,恢复窗口后进入半开并可恢复。
- 慢调用触发降级,线程池和连接池不会无限堆积。
- Redis/搜索等非核心依赖故障时返回约定兜底,核心接口保持可用。
- 核心依赖故障时 readiness 失败,流量入口不再转发新请求。
- Nacos 不可用时符合预先定义的 fail-open/fail-closed 策略。
- 规则动态更新失败时保留上一份有效规则,不加载空规则覆盖生产保护。

### 10.4 自动扩缩容

- K8s HPA 在 CPU、内存或自定义指标超过阈值时扩容,低于阈值并经过稳定窗口后缩容。
- 扩容出的实例必须先通过 readiness 才能接收流量。
- 缩容实例必须先摘流量再优雅退出。
- 单体多副本下登录、文件、定时任务、消息消费和缓存行为符合一致性要求。
- 数据库连接数、Redis连接数和消息吞吐不超过依赖容量上限。
- HPA、探针、滚动发布和 PDB 同时生效时不会造成整体不可用。

## 11. 监控、告警与运行手册

必须建立以下指标和告警:

- 实例状态、readiness/liveness 状态、注册状态和当前权重。
- 在途 HTTP 请求数、排空耗时、关停超时次数。
- 消息在途数、消费延迟、重试数和死信数。
- Sentinel 限流数、熔断数、降级数、恢复数。
- 请求率、错误率、P95/P99、线程池队列、连接池使用率。
- JVM 堆、GC、CPU、容器内存和重启次数。
- HPA 当前副本、期望副本、扩缩容事件和指标采集失败。

告警应区分:

- 需要立即摘流量的核心故障。
- 可自动降级且需要观察的非核心故障。
- 容量接近上限的扩容预警。
- 自动扩缩容控制器、注册中心或指标系统自身故障。

运行手册至少覆盖启动、发布、手动下线、故障摘除、规则回滚、HPA 暂停/恢复、消息积压处理和强制终止后的数据核查。

## 12. 风险与决策记录

| 风险 | 影响 | 控制措施 |
|---|---|---|
| 固定 sleep 代替真实排空 | 发布时丢请求/消息或等待不足 | 使用在途计数和分阶段超时 |
| readiness 与 liveness 混用 | 依赖短暂故障导致频繁重启 | liveness 只判断进程,readiness 判断接流资格 |
| Sentinel 与 Resilience4j 规则重复 | 阈值叠加、故障定位困难 | 按资源和依赖划分唯一责任边界 |
| Compose 误认为具备生产自动扩容 | 扩容不触发或流量不均 | 采用外部控制器,最终迁移 K8s |
| 单体扩成多副本后状态不一致 | 登录、任务、文件、消息异常 | 扩容前完成无状态化和幂等检查 |
| HPA 只看 CPU/内存 | 延迟和消息堆积已恶化但不扩容 | 压测后引入业务自定义指标 |
| 注册中心故障 | 实例状态不可知、流量错误 | 明确 fail-open/fail-closed 并告警 |
| 自动识别误判部署平台 | 装配错误策略、上下线失效 | 显式配置优先、强平台证据、冲突启动失败 |
| 运行期动态切换策略 | 关停与注册状态不一致 | 模式只在启动期解析,迁移通过滚动替换实例 |
| 应用持有 Docker/Kubernetes 管理权限 | 业务容器权限过高 | 扩缩容始终由外部控制平面负责 |

## 13. 需要本方案确认的事项

代码实施前需要确认以下决策:

1. 默认采用 `apex.governance.deployment-mode=auto`,并允许生产通过环境变量显式覆盖。
2. 部署模式只在启动期选择,不支持运行中热切换;平台迁移通过滚动创建新实例完成。
3. 第一阶段只实现单实例优雅关机、进程内降级和可观测能力,不承诺 `java -jar` 自动横向扩容。
4. 生产最终以 Kubernetes 为自动扩缩容平台,Compose 作为多副本过渡和验证环境。
5. 核心接口、非核心接口及其降级返回语义由业务方确认。
6. Nacos 在各环境是否启用,以及注册中心不可用时采用 fail-open 还是 fail-closed。
7. RocketMQ 消费、定时任务、文件存储和会话是否允许多副本,对应的幂等、锁和共享存储方案需确认。
8. HPA 的最终指标、最小/最大副本和容量基线需由压测结果确认,现有 2-10 副本与 CPU 70%/内存 80%仅作为初始配置。

## 14. 最终建议

建议按“先治理单实例,再验证容器多副本,最后交给 Kubernetes 编排”的顺序实施。第一阶段不需要等待微服务拆分,也不应因引入治理能力而提前拆分业务边界。优雅关机和进程内降级可以立即落地;上下线需要 Nacos、负载均衡或 Kubernetes 配合;可靠的自动扩缩容应在 Kubernetes 阶段完成。

本文件仅为方案和实施边界确认稿。未经方案确认,不执行代码、配置和部署清单修改。
