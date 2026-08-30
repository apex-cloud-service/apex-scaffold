# 配置、重试与消息可靠性增强实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**目标：** 完成配置键审计与补齐、注解驱动的同步/响应式自动重试，以及 RocketMQ 生产消费幂等和确认语义增强，并同步测试、脚本和文档。

**架构：** 保留现有 `@OptimisticLockRetry` 的乐观锁兼容语义，新增通用方法级重试注解与 AOP；同步方法使用受控退避，`Mono`/`Flux` 使用 Reactor 重试，不阻塞 WebFlux 线程。生产可靠投递继续复用事务 Outbox，消费者使用 Redis 状态机区分处理中与成功，并在监听器中以异常传播触发 RocketMQ 重试。

**技术栈：** Java 17、Spring Boot 3.5、Spring WebFlux、Spring AOP、Reactor、RocketMQ Spring、Redisson、JUnit 5、Mockito、Maven Wrapper Daemon。

---

### 任务 1：建立配置审计基线

**文件：**
- 检查：`apex-*/src/main/java/**/**Properties.java`
- 检查：`apex-*/src/main/java/**/*.java` 中 `@Value`
- 修改：`apex-interfaces/src/main/resources/application.yml`
- 修改：`apex-interfaces/src/main/resources/application-{basic,standard,dev,enterprise,performance,prod}.yml`
- 修改：`apex-interfaces/src/test/resources/application-it.yml`
- 检查：`apex-example/src/main/resources/application.yml`

- [ ] 生成字段到配置键的审计清单，覆盖 31 个 `@ConfigurationProperties` 声明和全部生产 `@Value` 键。
- [ ] 以 Java 字段实际名称修正 `apex.circuit-breaker.default` 与 `defaultConfig` 的绑定风险，统一 JWT 最小密钥长度键，并处理登录保护中源码未消费的 `lock-duration`。
- [ ] 在公共配置补齐通用默认值，在各 profile 明确声明环境开关、MQ、Outbox、重试、Redis 幂等 TTL、安全和存储相关键；凭据只保留环境变量占位，不写入真实密钥。
- [ ] 为缺失的 `@Value` 键补充可覆盖默认值，生产敏感项使用空值或环境变量，确保启动校验与配置文档一致。
- [ ] 添加配置绑定测试，验证关键 profile 能加载并验证修正后的键名。

### 任务 2：新增注解驱动的自动重试

**文件：**
- 新增：`apex-common/src/main/java/com/apex/scaffold/common/annotation/AutoRetry.java`
- 修改：`apex-interfaces/src/main/java/com/apex/scaffold/interfaces/aspect/OptimisticLockRetryAspect.java`
- 新增：`apex-interfaces/src/main/java/com/apex/scaffold/interfaces/aspect/AutoRetryAspect.java`
- 修改：需要自动重试的外部调用服务方法，优先 `RemoteApiClient` 与审核适配器
- 新增：`apex-interfaces/src/test/java/com/apex/scaffold/interfaces/aspect/AutoRetryAspectTest.java`
- 修改：`apex-interfaces/src/test/java/com/apex/scaffold/interfaces/aspect/OptimisticLockRetryAspectTest.java`

- [ ] 先写失败测试，覆盖同步方法按 `maxAttempts` 重试、异常类型过滤、指数退避上限和耗尽后原异常传播。
- [ ] 写失败测试，覆盖 `Mono` 与 `Flux` 的按注解参数重试，确认延迟通过 Reactor 调度器实现且不调用 `Thread.sleep`。
- [ ] 实现 `@AutoRetry` 参数：最大尝试次数、初始延迟、最大延迟、退避倍率、可重试异常类型和耗尽策略；默认只重试明确允许的瞬态异常。
- [ ] 实现 AOP：同步返回值采用受控循环，响应式返回值采用 `retryWhen`，保留原异常链并记录 Micrometer 计数器。
- [ ] 保持原 `@OptimisticLockRetry` 的公开 API 和乐观锁业务异常语义；移除其 WebFlux 阻塞风险，必要时让响应式方法由通用注解承担。
- [ ] 在实际需要重试的接口/服务方法上添加注解，避免对非幂等写操作盲目重试。

### 任务 3：强化 RocketMQ 生产者可靠发送

**文件：**
- 修改：`apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/mq/service/MqProducerService.java`
- 修改：`apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/event/EnhancedDomainEventPublisherImpl.java`
- 检查/修改：`apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/event/DomainEventPublisherImpl.java`
- 修改：公共及 profile MQ 配置
- 修改：`apex-infrastructure/src/test/java/com/apex/scaffold/infrastructure/mq/service/MqProducerServiceTest.java`
- 新增/修改：领域事件 Outbox 相关测试

- [ ] 先补失败测试，验证每条消息生成稳定业务消息 ID，并写入 RocketMQ `KEYS` 以及必要的业务头。
- [ ] 统一同步、异步、延迟和带 Header 发送的消息构造，校验 topic/tag/message 非法输入，避免日志输出敏感 payload。
- [ ] 同步发送必须以 `SendResult` 成功状态作为成功；异步发送将回调结果映射到 `CompletableFuture`，失败可观察并交由调用方/Outbox 处理。
- [ ] 领域事件默认走 Outbox；增强发布路径不得在异步发送提交后提前完成 `Mono`，需要明确发送成功或失败语义。
- [ ] 复用 RocketMQ starter 的发送重试配置，避免在服务层重复叠加不可控重试。

### 任务 4：强化消费者幂等、确认与重复消费防护

**文件：**
- 修改：`apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/mq/service/MqConsumerService.java`
- 修改：`apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/mq/listener/UserMessageListener.java`
- 修改：`apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/mq/listener/UserMessagePayload.java`
- 修改：MQ 常量与配置文件
- 新增/修改：`apex-infrastructure/src/test/java/com/apex/scaffold/infrastructure/mq/service/MqConsumerServiceTest.java`
- 修改：`apex-infrastructure/src/test/java/com/apex/scaffold/infrastructure/mq/listener/UserMessagePayloadTest.java`
- 修改：`apex-infrastructure/src/test/java/com/apex/scaffold/infrastructure/mq/listener/UserMessageListenerTest.java`

- [ ] 先写失败测试，覆盖首次处理、成功重复、并发处理中、处理失败和处理锁超时恢复的状态语义。
- [ ] 将 Redis 幂等值改为带状态和 token 的记录，使用原子占用；发现 `PROCESSING` 时不得直接返回成功确认，需抛出可重试异常或按明确策略等待。
- [ ] 业务成功后原子转换为 `SUCCESS` 并保留足够长 TTL；业务失败只释放当前 token，避免删除其他实例的状态。
- [ ] 监听器检查处理结果，失败抛出异常让 RocketMQ 执行重试，成功和已成功重复消费才正常返回。
- [ ] 消除欢迎消息、操作日志等关键后置操作的 fire-and-forget 确认漏洞；若后置操作非消费成功必要条件，明确其独立失败处理，不阻塞错误确认。
- [ ] payload 数字字段兼容 `Number` 和合法字符串，并拒绝缺少稳定 messageId 的消息进入幂等流程。

### 任务 5：同步更新文档、脚本和部署配置

**文件：**
- 修改：`README.md`
- 修改：`docs/CONFIGURATION.md`
- 修改：`docs/DEPLOYMENT.md`
- 修改：`docs/BEST_PRACTICES.md`
- 修改：`docs/ADVANCED_FEATURES.md`
- 修改：`.env.example`
- 检查/修改：`docker-compose.yml`
- 检查/修改：`docker-compose.infra.yml`
- 检查/修改：`scripts/build/build.sh`
- 检查/修改：`scripts/deploy/app.sh`
- 检查/修改：`scripts/deploy/start.sh`

- [ ] 更新配置键、profile 覆盖规则、MQ 环境变量和敏感配置说明。
- [ ] 记录 `@AutoRetry` 使用约束、幂等方法要求、同步/响应式行为和重试异常白名单。
- [ ] 记录 RocketMQ producer 发送确认、Outbox 投递状态、consumer 重复消费和失败重试语义。
- [ ] 让脚本检查必要的 MQ/Redis 环境变量并保持现有 `mvnd` 构建方式，不引入交互式或破坏性命令。

### 任务 6：验证与回归

- [ ] 运行新增测试的定向模块测试并确保先观察到预期失败，再实现至通过。
- [ ] 运行 `source ./scripts/build/init-build-env.sh && mvnd --version`。
- [ ] 运行 `mvnd -pl apex-interfaces -am test`，必要时补充 `apex-infrastructure` 定向测试。
- [ ] 运行 `scripts/test/test-javadoc.sh`。
- [ ] 运行 `git diff --check`。
- [ ] 运行 `git status --short`，确认只包含本任务相关文件，且不提交变更。
