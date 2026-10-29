# 服务治理跨部署模式代码实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不进行微服务拆分的前提下，补齐单体应用的优雅关机、运行期降级与上下线协同，并让同一套代码根据配置或启动环境选择 `standalone`、Podman Compose、Kubernetes 治理实现，为 `java -jar` 到 Kubernetes 的迁移提供兼容路径。

**Architecture:** 在 `apex-system` 建立平台无关的治理 SPI、配置属性、启动期部署模式解析、生命周期状态机、统一 deadline 和关停协调器；通过策略模式隔离 standalone、Compose、Kubernetes，通过适配器连接 Spring Availability、Nacos 和平台探针。MQ、线程池等资源以 drain participant 接入协调器，HPA/Deployment 仍由 Kubernetes 控制面负责，业务代码不直接调用平台 API。

**Tech Stack:** Java 21、Spring Boot 3.5.15、Spring WebFlux、Spring Availability、Spring Cloud Alibaba Nacos/Sentinel、RocketMQ Spring、Micrometer、JUnit 5、Mockito、AssertJ、Podman Compose、Kubernetes YAML、Bash。

---

## 1. 当前状态与实现结论

- [GracefulShutdownManager.java](../../apex-system/src/main/java/com/apex/scaffold/interfaces/config/GracefulShutdownManager.java) 已实现 `SmartLifecycle`，但使用固定 15 秒 `Thread.sleep`，未主动切换 readiness、未统一管理 MQ/线程池排空。
- [application.yml](../../apex-system/src/main/resources/application.yml) 已启用 `server.shutdown=graceful`，管理端口默认 `8081`，readiness 包含 `readinessState,db,redis`，但关停时没有主动设置拒绝流量。
- [SentinelConfig.java](../../apex-system/src/main/java/com/apex/scaffold/interfaces/config/SentinelConfig.java) 已有流控、熔断和系统保护规则；本次不改变业务资源规则，仅使其与治理状态解耦。
- [SentinelNacosDataSourceConfig.java](../../apex-system/src/main/java/com/apex/scaffold/interfaces/config/SentinelNacosDataSourceConfig.java) 已接入动态规则；本次仅在必要处补充生命周期和失败策略配置，不把 Nacos 故障误判为应用存活失败。
- [MqConsumerService.java](../../apex-infrastructure-messaging/src/main/java/com/apex/scaffold/infrastructure/mq/service/MqConsumerService.java)、[UserMessageListener.java](../../apex-system/src/main/java/com/apex/scaffold/infrastructure/mq/listener/UserMessageListener.java)、[DeadLetterQueueProcessor.java](../../apex-infrastructure-messaging/src/main/java/com/apex/scaffold/infrastructure/mq/listener/DeadLetterQueueProcessor.java) 需要提供停止 intake、in-flight 计数和 deadline drain 能力。
- [ThreadPoolConfig.java](../../apex-system/src/main/java/com/apex/scaffold/interfaces/config/ThreadPoolConfig.java) 与 [AsyncConfig.java](../../apex-system/src/main/java/com/apex/scaffold/interfaces/config/AsyncConfig.java) 各自管理关闭等待，需接入统一资源排空协议，避免等待时间叠加。
- [k8s/deployment.yaml](../../k8s/deployment.yaml) 已存在 probes 与 HPA；[podman-compose.yml](../../podman-compose.yml) 已有基础服务，但应用缺少 readiness healthcheck 和 stop grace 配置。
- [scripts/deploy/app.sh](../../scripts/deploy/app.sh)、[scripts/deploy/start.sh](../../scripts/deploy/start.sh)、[scripts/deploy/stop.sh](../../scripts/deploy/stop.sh) 需要统一检查管理端口 readiness 和退出码。
- 未找到 `AGENTS.md` 和 `docs/guide/代码开发规范.md`，实现遵循已加载 Java/Spring 规范与仓库现有 Javadoc、配置注释、测试命名风格。

## 2. 明确的设计决策

1. `apex.governance.deployment-mode` 默认 `auto`，可选 `auto|standalone|compose|kubernetes`。
2. 解析优先级固定为：显式配置/`APEX_GOVERNANCE_DEPLOYMENT_MODE` > Kubernetes 官方标记 > `APEX_DEPLOYMENT_PLATFORM=compose` > standalone。
3. 模式只在启动期解析一次，运行期不热切换；部署方式迁移采用新实例启动、readiness 就绪、旧实例优雅下线。
4. 显式配置与环境探测冲突时以显式配置为准并告警；自动探测出现多个平台证据时，生产默认启动失败。
5. `standalone` 和 `compose` 不在应用内创建/销毁进程或容器；Kubernetes 扩缩容仅由 HPA/Deployment 完成。
6. 业务层只依赖 `DeploymentGovernanceStrategy`、`DrainParticipant` 和状态查询，不直接依赖 Nacos、Podman 或 Kubernetes API。
7. 优雅关机总流程为：进入 `DRAINING` → readiness 立即拒绝流量 → 平台摘流/注销 → 停止 HTTP/MQ/后台 intake → 等待 participant → 关闭资源 → deadline 到期强制停止并记录诊断。
8. 所有新增生产类、具名方法和构造器添加中文 Javadoc；类型 Javadoc 包含 `@author`、`@version`、`@since`。配置文件、Shell、Kubernetes、Compose 和测试代码添加与仓库一致的中文说明注释。不得记录密钥、Token 或完整环境变量。

## 3. 文件结构

### 新增生产代码

- `apex-system/src/main/java/com/apex/scaffold/interfaces/governance/config/GovernanceProperties.java`：治理配置绑定、默认值和校验。
- `apex-system/src/main/java/com/apex/scaffold/interfaces/governance/platform/DeploymentMode.java`：部署模式枚举。
- `apex-system/src/main/java/com/apex/scaffold/interfaces/governance/platform/DeploymentModeResolver.java`：无副作用的启动期模式解析。
- `apex-system/src/main/java/com/apex/scaffold/interfaces/governance/platform/DeploymentGovernanceStrategy.java`：跨平台策略接口。
- `apex-system/src/main/java/com/apex/scaffold/interfaces/governance/platform/DeploymentGovernanceStrategyRegistry.java`：策略注册、唯一选择和能力查询。
- `apex-system/src/main/java/com/apex/scaffold/interfaces/governance/platform/GovernanceCapabilities.java`：平台能力值对象。
- `apex-system/src/main/java/com/apex/scaffold/interfaces/governance/platform/standalone/StandaloneGovernanceStrategy.java`：JAR/systemd 模式策略。
- `apex-system/src/main/java/com/apex/scaffold/interfaces/governance/platform/compose/ComposeGovernanceStrategy.java`：Compose 健康检查/可选 Nacos 策略。
- `apex-system/src/main/java/com/apex/scaffold/interfaces/governance/platform/kubernetes/KubernetesGovernanceStrategy.java`：Kubernetes readiness 协作策略。
- `apex-system/src/main/java/com/apex/scaffold/interfaces/governance/lifecycle/ServiceLifecycleState.java`：生命周期状态枚举。
- `apex-system/src/main/java/com/apex/scaffold/interfaces/governance/lifecycle/ServiceLifecycleStateMachine.java`：原子状态迁移和幂等校验。
- `apex-system/src/main/java/com/apex/scaffold/interfaces/governance/lifecycle/DrainParticipant.java`：资源停止接收、排空和强制停止契约。
- `apex-system/src/main/java/com/apex/scaffold/interfaces/governance/lifecycle/DrainResult.java`：排空结果和诊断信息。
- `apex-system/src/main/java/com/apex/scaffold/interfaces/governance/lifecycle/ResourceDrainRegistry.java`：participant 注册和 deadline 协调。
- `apex-system/src/main/java/com/apex/scaffold/interfaces/governance/lifecycle/ReadinessStateController.java`：Spring `ApplicationAvailability` 适配。
- `apex-system/src/main/java/com/apex/scaffold/interfaces/governance/lifecycle/GracefulShutdownCoordinator.java`：统一关停编排。
- `apex-system/src/main/java/com/apex/scaffold/interfaces/governance/lifecycle/GovernanceLifecycleConfiguration.java`：配置属性、策略、协调器和 participant 的 Spring 装配。
- `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/mq/governance/MqDrainParticipant.java`：MQ 消费排空适配。
- `apex-system/src/main/java/com/apex/scaffold/interfaces/governance/lifecycle/ExecutorDrainParticipant.java`：线程池排空适配。

### 修改生产代码

- `apex-system/src/main/java/com/apex/scaffold/interfaces/config/GracefulShutdownManager.java`：保留兼容 Bean 和构造器入口，移除固定 sleep，委托 `GracefulShutdownCoordinator`。
- `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/mq/service/MqConsumerService.java`：增加 intake 状态、in-flight 计数和 participant 所需方法。
- `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/mq/listener/UserMessageListener.java`：在消息处理前后接入计数和拒绝新 intake。
- `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/mq/listener/DeadLetterQueueProcessor.java`：纳入 participant 管理，明确关闭和失败语义。
- `apex-system/src/main/java/com/apex/scaffold/interfaces/config/ThreadPoolConfig.java`、`AsyncConfig.java`：避免独立等待超过总 deadline，改由 participant 或统一关闭钩子管理。
- `apex-system/src/main/resources/application.yml` 及 `application-{dev,basic,standard,enterprise,prod,performance}.yml`：增加治理配置和逐环境能力声明。

### 修改部署、脚本和文档

- `podman-compose.yml`：应用 readiness healthcheck、`stop_grace_period`、依赖条件和中文注释。
- `k8s/deployment.yaml`、`k8s/canary-deployment.yaml`：管理地址、startupProbe、readinessProbe、preStop、termination grace、稳定/金丝雀 selector。
- `k8s/pod-disruption-budget.yaml`：新增稳定版本 PDB。
- `scripts/deploy/app.sh`、`start.sh`、`stop.sh`：统一 readiness、超时、强杀和退出码。
- `scripts/deploy/apex-scaffold.service`：与应用总关停预算一致。
- `scripts/dev/quick-start.sh`：修复硬编码路径和管理端口健康检查。
- `docs/architecture/system-architecture.md` 或现有部署文档：补充治理模式、状态机、部署迁移和故障策略。
- `k8s/README.md`：补充探针、HPA、PDB 和 selector 约定。
- `k8s/validate-manifests.sh`：若仓库无现成 manifest 校验工具则新增，用于本地/CI 静态校验。

### 新增测试代码

- `apex-system/src/test/java/com/apex/scaffold/interfaces/governance/platform/DeploymentModeResolverTest.java`
- `apex-system/src/test/java/com/apex/scaffold/interfaces/governance/lifecycle/ServiceLifecycleStateMachineTest.java`
- `apex-system/src/test/java/com/apex/scaffold/interfaces/governance/lifecycle/ResourceDrainRegistryTest.java`
- `apex-system/src/test/java/com/apex/scaffold/interfaces/governance/lifecycle/GracefulShutdownCoordinatorTest.java`
- `apex-system/src/test/java/com/apex/scaffold/interfaces/governance/lifecycle/ReadinessStateControllerTest.java`
- `apex-infrastructure/src/test/java/com/apex/scaffold/infrastructure/mq/governance/MqDrainParticipantTest.java`
- `apex-system/src/test/java/com/apex/scaffold/interfaces/governance/ReadinessEndpointIntegrationTest.java`
- `apex-system/src/test/java/com/apex/scaffold/interfaces/governance/GovernanceConfigurationContextTest.java`
- `apex-system/src/test/java/com/apex/scaffold/interfaces/governance/GracefulShutdownE2ETest.java`，仅在仓库现有 E2E 基础设施可复用时启用。

## 4. 实施任务

### Task 1：建立配置和部署模式解析

**Files:**
- Create: `apex-system/src/main/java/com/apex/scaffold/interfaces/governance/config/GovernanceProperties.java`
- Create: `apex-system/src/main/java/com/apex/scaffold/interfaces/governance/platform/DeploymentMode.java`
- Create: `apex-system/src/main/java/com/apex/scaffold/interfaces/governance/platform/DeploymentModeResolver.java`
- Create: `apex-system/src/test/java/com/apex/scaffold/interfaces/governance/platform/DeploymentModeResolverTest.java`
- Modify: `apex-system/src/main/resources/application.yml`

- [ ] 定义 `DeploymentMode`、`RegistrationProvider`、`FailMode`，枚举值仅覆盖 `AUTO/STANDALONE/COMPOSE/KUBERNETES`、`NONE/NACOS/PLATFORM/AUTO` 和 `OPEN/CLOSED/AUTO`。
- [ ] 绑定 `apex.governance`，至少包含：`enabled`、`deployment-mode`、`auto-detection.enabled`、`auto-detection.fail-on-ambiguous-environment`、`registration.provider`、`registration.fail-mode`、`shutdown.total-timeout`、`shutdown.readiness-withdraw-timeout`、`shutdown.force-shutdown-after-timeout`、`mq.drain-timeout`。
- [ ] 解析器提供无副作用方法 `DeploymentMode resolve(GovernanceProperties properties, Map<String,String> environment)`；显式值优先，Kubernetes 检测同时检查官方环境变量和服务账号文件，Compose 只接受约定环境变量，未命中回退 standalone。
- [ ] 对非法显式模式、Kubernetes/Compose 证据冲突和显式模式缺少必要平台证据实现配置异常或告警行为；日志只打印模式名和证据类型，不打印密钥及完整环境变量。
- [ ] 为显式模式、自动模式、默认 standalone、Compose、Kubernetes、冲突、禁用自动检测和非法配置编写单元测试；测试不得等待真实关停时间。
- [ ] 在 `application.yml` 增加完整中文注释，默认总关停预算采用 `90s`，readiness withdrawal 采用 `5s`；profile 文件只覆盖必要差异。

### Task 2：建立状态机、readiness 控制和排空契约

**Files:**
- Create: `ServiceLifecycleState.java`
- Create: `ServiceLifecycleStateMachine.java`
- Create: `DrainParticipant.java`
- Create: `DrainResult.java`
- Create: `ReadinessStateController.java`
- Create: 对应 `ServiceLifecycleStateMachineTest.java`、`ReadinessStateControllerTest.java`

- [ ] 状态至少包含 `STARTING`、`READY`、`DEGRADED`、`OUT_OF_SERVICE`、`DRAINING`、`FAILED`、`STOPPED`。
- [ ] 只允许 `STARTING→READY/FAILED`、`READY→DEGRADED/OUT_OF_SERVICE/DRAINING`、`DEGRADED→READY/OUT_OF_SERVICE/DRAINING`、`DRAINING→STOPPED/FAILED`、`OUT_OF_SERVICE→READY/DRAINING`；非法迁移返回 false 或抛出明确异常，重复迁移幂等。
- [ ] `ReadinessStateController` 使用 Spring `ApplicationAvailability` 发布 `ReadinessState.ACCEPTING_TRAFFIC` 和 `REFUSING_TRAFFIC`，关停入口必须在任何阻塞 drain 前调用拒绝流量。
- [ ] `DrainParticipant` 定义 `name()`、`stopAccepting()`、`CompletionStage<DrainResult> drain(Instant deadline)`、`forceStop()`；接口 Javadoc 说明必须幂等、不得阻塞 WebFlux 事件循环。
- [ ] 测试状态迁移、重复 shutdown、readiness 立即切换和失败状态禁止误恢复。

### Task 3：实现策略模式、注册表和 Spring 装配

**Files:**
- Create: `DeploymentGovernanceStrategy.java`
- Create: `GovernanceCapabilities.java`
- Create: `DeploymentGovernanceStrategyRegistry.java`
- Create: `standalone/StandaloneGovernanceStrategy.java`
- Create: `compose/ComposeGovernanceStrategy.java`
- Create: `kubernetes/KubernetesGovernanceStrategy.java`
- Create: `GovernanceLifecycleConfiguration.java`
- Create: `GovernanceConfigurationContextTest.java`

- [ ] 定义统一接口：`mode()`、`prepareForTraffic()`、`withdrawFromTraffic(WithdrawalReason reason)`、`restoreTraffic()`、`capabilities()`；声明方法必须可重复调用。
- [ ] registry 接收所有策略和解析结果，只选择一个活动策略；零个或多个匹配策略启动失败，输出能力矩阵。
- [ ] standalone 策略只负责本地 readiness 和可选 Nacos 联动；Compose 策略依赖 healthcheck/Nacos/外部代理，不调用容器管理 API；Kubernetes 策略通过 Spring readiness 与 Service/EndpointSlice 协作，不调用 Kubernetes API。
- [ ] 生产环境采用构造器注入和条件装配；不使用字段注入。所有新类型和方法添加中文 Javadoc。
- [ ] 上下文测试验证四种 mode 选择、唯一策略、Nacos/MQ 能力开关，以及不支持能力返回 false 而非静默成功。

### Task 4：实现统一 deadline 和优雅关停协调器

**Files:**
- Create: `ResourceDrainRegistry.java`
- Create: `ExecutorDrainParticipant.java`
- Create: `GracefulShutdownCoordinator.java`
- Modify: `GracefulShutdownManager.java`
- Modify: `GracefulShutdownManagerTest.java`
- Create: `GracefulShutdownCoordinatorTest.java`
- Create: `ResourceDrainRegistryTest.java`

- [ ] `ResourceDrainRegistry` 维护有序 participant 列表，`stopAccepting` 按注册顺序执行，drain 在同一 deadline 内并行等待，单个 participant 失败不得阻断其他 participant。
- [ ] 协调器使用单调 deadline，不为每个资源重新计算完整 timeout；流程固定为状态切换、readiness 拒绝、策略摘流、participant 停止 intake、排空、关闭、超时 force stop。
- [ ] 删除 `GracefulShutdownManager` 的固定 15 秒 `Thread.sleep`；保留兼容类作为 `SmartLifecycle` 入口，委托协调器并保持重复 stop 幂等。
- [ ] `ExecutorDrainParticipant` 对 `ThreadPoolTaskExecutor` 和 `ExecutorService` 统一执行 `shutdown`、剩余时间等待、deadline 后 `shutdownNow`，记录活动任务数和队列数。
- [ ] 所有等待必须可被中断并恢复中断标记；阻塞关闭不在 Reactor/Netty 事件循环执行。
- [ ] 测试空资源、任务完成、participant 异常、deadline 超时、force stop、重复 stop 和 shutdown 顺序；测试使用极短可控 timeout，禁止固定 15 秒等待。

### Task 5：接入 RocketMQ 消费排空

**Files:**
- Create: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/mq/governance/MqDrainParticipant.java`
- Modify: `MqConsumerService.java`
- Modify: `UserMessageListener.java`
- Modify: `DeadLetterQueueProcessor.java`
- Create: `apex-infrastructure/src/test/java/com/apex/scaffold/infrastructure/mq/governance/MqDrainParticipantTest.java`

- [ ] 增加 `accepting` 原子标记和 in-flight 计数；消息进入处理前检查 accepting，处理完成或异常时在 finally 中递减计数。
- [ ] `stopAccepting` 必须先于 RocketMQ container 关闭执行；`drain` 等待 in-flight 归零；超时返回当前计数和有限诊断信息，不记录完整消息内容或敏感字段。
- [ ] `forceStop` 停止 listener/container 和 DLQ consumer；对不存在或已停止资源保持幂等。
- [ ] 明确处理失败消息的 retry/DLQ 语义，不在 shutdown 中静默确认未完成消息；补充关闭期间新消息拒绝和当前消息完成测试。
- [ ] 如果 MQ feature flag 关闭，不装配活动 participant；配置和测试说明 basic/standard/enterprise 的行为差异。

### Task 6：配置、脚本和本地部署闭环

**Files:**
- Modify: `application.yml` 及所有相关 profile YAML
- Modify: `podman-compose.yml`
- Modify: `scripts/deploy/app.sh`
- Modify: `scripts/deploy/start.sh`
- Modify: `scripts/deploy/stop.sh`
- Modify: `scripts/deploy/apex-scaffold.service`
- Modify: `scripts/dev/quick-start.sh`
- Create if absent: `k8s/validate-manifests.sh`

- [ ] YAML 为每个治理参数增加中文注释，环境变量只作为注入入口；统一 `MANAGEMENT_SERVER_ADDRESS`、management port、readiness URL 和总关停预算。
- [ ] Compose 增加 `healthcheck` 检查 `http://127.0.0.1:8081/actuator/health/readiness`、`stop_grace_period`，依赖方从 `service_started` 改为 `service_healthy`；修正 Redis 健康检查认证参数。
- [ ] `app.sh` 使用 `set -Eeuo pipefail`，启动轮询 readiness，停止时使用 SIGTERM、等待统一 timeout、超时执行 `kill -9` 并返回非零码；不得在强杀后输出成功。
- [ ] `start.sh` 和 `stop.sh` 传递 deployment mode 和 timeout，不改变用户传入的 Compose 参数；所有输出不包含敏感配置。
- [ ] systemd `TimeoutStopSec` 大于应用 drain deadline 并预留传播缓冲；`scripts/dev/quick-start.sh` 去掉机器相关绝对路径，改检查管理端口 readiness。
- [ ] manifest 校验脚本使用仓库已有工具；若无工具，至少校验 YAML 可解析、探针路径、selector 标签、termination grace 和 HPA 引用存在，失败返回非零码。

### Task 7：Kubernetes 迁移配置

**Files:**
- Modify: `k8s/deployment.yaml`
- Modify: `k8s/canary-deployment.yaml`
- Create: `k8s/pod-disruption-budget.yaml`
- Modify: `k8s/README.md`

- [ ] Deployment 显式设置 `MANAGEMENT_SERVER_ADDRESS=0.0.0.0`，增加 startupProbe，readinessProbe 周期调整为 2 秒级并与启动数据校准。
- [ ] 增加 `preStop` 与应用关停入口一致，设置 `terminationGracePeriodSeconds` 至少覆盖 90 秒应用预算加传播缓冲；不在 preStop 中实现第二套关停逻辑。
- [ ] 稳定 Deployment/Service 使用 `track: stable`，金丝雀使用 `track: canary`，避免 selector 交叉选 Pod；保留 HPA 的 CPU 70%、内存 80%、2-10 副本初始值并在文档标注需压测校准。
- [ ] 新增 PDB，保证节点维护时至少保留一个稳定副本；补充 `kubectl apply --dry-run=client` 或仓库已有 lint 工具验证方式。
- [ ] 文档说明 readiness 负责摘流、liveness 负责重启、HPA 负责副本数，应用不直接操作 Kubernetes 控制面。

### Task 8：文档与可观测性补齐

**Files:**
- Modify: `docs/architecture/system-architecture.md` 或现有部署文档
- Modify: `k8s/README.md`
- Modify: `README.md`（仅补充必要运行说明，不重写项目介绍）
- Modify: `SentinelNacosDataSourceConfig.java`（仅在测试证明需要时）

- [ ] 增加治理状态图、模式选择优先级、生命周期时序、配置示例、故障处理、迁移步骤和回滚步骤。
- [ ] 明确 Sentinel/Resilience4j 负责业务资源降级，readiness/策略负责实例流量治理，二者不得混用。
- [ ] 增加指标命名和告警建议：当前生命周期状态、readiness refusal、drain duration、drain timeout、in-flight count、force shutdown count、模式选择冲突。
- [ ] 说明 Nacos 注册中心故障的 `fail-open/fail-closed` 配置边界，不记录敏感连接信息。
- [ ] 文档内容与实际 YAML、脚本和测试命令一致。

### Task 9：测试门禁与质量验证

**Files:**
- Create/modify tests listed above
- Modify only if needed: `.github/workflows/ci-cd.yml`

- [ ] 单元测试覆盖 mode resolver、状态机、readiness、registry、deadline、线程池和 MQ participant。
- [ ] Spring context test 覆盖 basic/standard/enterprise 组合、Bean 条件和唯一策略装配。
- [ ] `WebTestClient` 随机端口测试访问 `/actuator/health/liveness` 和 `/actuator/health/readiness`，并验证 shutdown 后 readiness 变为拒绝流量。
- [ ] 如可复用现有测试基础设施，增加进程级 SIGTERM 测试；否则保留为可选 profile，不能让普通单元测试依赖 Podman、Nacos 或 RocketMQ。
- [ ] 增加 Compose 配置校验和 K8s manifest 校验，不把 PMD、Spotless、Javadoc 失败用 `|| true` 吞掉。
- [ ] 按项目规范执行：
  ```bash
  source ./scripts/build/init-build-env.sh
  mvnd --version
  mvnd -pl apex-system,apex-infrastructure -am test
  scripts/test/test-javadoc.sh
  git diff --check
  git status --short
  ```
- [ ] 若仓库没有 `scripts/test/test-javadoc.sh`，先确认现有 Javadoc 校验脚本后使用实际存在的脚本，不自行假设命令。

## 5. 验收标准

1. `java -jar` 未设置模式时解析为 standalone；设置 `APEX_GOVERNANCE_DEPLOYMENT_MODE=kubernetes` 时使用 Kubernetes 策略；Compose 通过约定标记自动识别；显式配置覆盖探测结果。
2. 模式选择只发生一次，运行期修改环境变量不会切换策略；日志能显示最终模式、能力矩阵和冲突告警，但不泄露敏感值。
3. 收到 SIGTERM 或 Spring context stop 后，readiness 在阻塞排空前变为 `REFUSING_TRAFFIC`，新 HTTP/MQ intake 被拒绝，已开始工作在 deadline 内完成或进入 force stop。
4. 重复执行 shutdown 不重复摘流、不重复关闭资源、不产生错误日志风暴。
5. MQ、Async executor、ThreadPoolConfig 共享一个总 deadline，不再固定等待 15 秒或独立叠加 30/60 秒。
6. Sentinel/Resilience4j 现有规则测试和业务降级语义不改变；Nacos 动态规则可选故障不会把非关键控制面直接变成 liveness 失败。
7. Compose readiness healthcheck、stop grace、依赖条件正确；Kubernetes startup/readiness/liveness、selector、PDB、HPA 引用通过静态校验。
8. basic/standard/enterprise 三种 profile 均能启动并装配预期治理能力；不支持的能力明确返回 false。
9. 新增 Java 类型/方法、YAML、Shell、Kubernetes、Compose 和测试均有符合仓库规范的中文注释/Javadoc，且不包含敏感信息。
10. 目标模块测试、Javadoc、格式/静态校验、`git diff --check` 均通过；不提交任何 Git commit。

## 6. 发布与回滚

- 第一批仅上线治理抽象、启动期模式解析、readiness 主动摘流和可观测日志；默认 `standalone` 行为必须与现有 JAR 兼容。
- 第二批启用 Compose healthcheck 和统一脚本，验证单机重启、依赖故障和强杀告警。
- 第三批启用 Kubernetes startupProbe、preStop、PDB 和 stable/canary selector，先固定副本运行，再开启 HPA。
- 每批发布前观察 readiness refusal、drain timeout、force shutdown、HTTP 5xx、MQ 重复消费和 P99 延迟。
- 若新协调器异常，使用配置开关关闭治理协调扩展并回退到 Spring Boot 原生 graceful shutdown；不得通过修改业务代码回滚。
- 回滚 Kubernetes 时先恢复旧 selector/Deployment 版本，再恢复旧脚本和探针；确认旧实例已停止接收流量后再删除新实例。
