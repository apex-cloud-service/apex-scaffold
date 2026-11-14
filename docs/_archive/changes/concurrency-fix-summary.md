# 并发问题修复变更摘要

- **提交**: `b12b010` (`fix: 修复并发上下文竞态与幂等释放问题`)

- **日期**: 2026-09-02

- **涉及模块**: apex-common / apex-domian / apex-infrastructure / apex-application / apex-interfaces（DDD 五层）

- **变更规模**: 19 个文件，+1285 / −343

- **验证**: `mvn clean verify -DskipTests=false -DskipITs=true -T 1C` → BUILD SUCCESS（10 模块，含单测、Spotless、PMD、JaCoCo）；`mvn spotless:check` → 0 violations

***

## 一、P0 高优修复

### C1 HTTP 幂等键释放逻辑（IdempotentApiAspect）

**问题**: 切面在业务执行异常时无条件删除幂等键。若数据库写入成功、但后续响应阶段抛出异常，幂等键被提前释放，导致同一幂等请求被重复处理（重复下单/重复扣款）。

**修复**:

- 仅当异常为客户端可重试的 **4xx 业务异常**（`BusinessException`）时才释放幂等键；

- **5xx / 瞬态异常保留幂等键至 TTL 过期**，由分布式锁的原子性保证并发重试只被处理一次。

**文件**: [IdempotentApiAspect.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/aspect/IdempotentApiAspect.java)、[IdempotentApiAspectTest.java](../../../apex-components-parent/apex-system/src/test/java/com/apex/scaffold/interfaces/aspect/IdempotentApiAspectTest.java)

### C2 EventListener 去阻塞与租户上下文治理（3 个监听器 + 事件模型）

**问题**:

- 监听器在 Reactor 线程调用 `.block()`，极端情况下阻塞事件总线线程；

- 异步线程切换后 `ThreadLocal` 中的租户上下文丢失或串扰。

**修复**:

- 3 个监听器（[UserRegisteredEventListener.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/listener/UserRegisteredEventListener.java)、[SubscriptionEventListener.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/listener/SubscriptionEventListener.java)、[CreditEventListener.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/listener/CreditEventListener.java)）全部去除 `.block()`，改为纯 Mono 链；

- [BaseDomainEvent.java](../../../apex-shared-parent/apex-common/src/main/java/com/apex/scaffold/common/event/BaseDomainEvent.java) 新增 `tenantId` 字段并随事件 DTO 传递；

- [EnhancedDomainEventPublisherImpl.java](../../../apex-shared-parent/apex-infrastructure-messaging/src/main/java/com/apex/scaffold/infrastructure/event/EnhancedDomainEventPublisherImpl.java) 发布前将当前租户注入事件；

- 监听器用 `Mono.usingWhen` 安装/恢复 `TenantContext`。

### C3 fire-subscribe 传参化与受控提交（OperationLog / ApiCallLog）

**问题**: 日志/审计写入在请求响应阶段裸 `subscribe()`，且依赖调用线程的 `ThreadLocal` 上下文——WebClient 切换到 NIO 线程后租户上下文丢失，导致日志多租户隔离失效。

**修复**:

- [OperationLogAspect.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/aspect/OperationLogAspect.java) 与 [ApiCallLogFilter.java](../../../apex-shared-parent/apex-infrastructure-remote/src/main/java/com/apex/scaffold/infrastructure/remote/filter/ApiCallLogFilter.java) 在**入口捕获** **`tenantId`** **并显式传入**日志服务；

- 统一在专用阻塞 Scheduler（`BlockingIoSchedulers.jdbc()`）上受控提交；

- [OperationLogService.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/OperationLogService.java) 新增 `saveAsync(entity, tenantId)` 重载。

***

## 二、P1 修复

### H1 LocalCacheService 读写顺序（[LocalCacheService.java](../../../apex-shared-parent/apex-infrastructure-cache/src/main/java/com/apex/scaffold/infrastructure/cache/service/LocalCacheService.java)）

**问题**: `ttlTracker` 与 `cache` 并发读写时存在竞态，可能读到已过期但仍在 cache 的数据，或写入 TTL 后缓存未落位。

**修复**:

- **写入**: 先写 `ttlTracker` 再写 `cache`；

- **删除**: 先 `evict(cache)` 再删 `ttlTracker`。

### H2 MQ 幂等状态三态化（[MqConsumerService.java](../../../apex-shared-parent/apex-infrastructure-messaging/src/main/java/com/apex/scaffold/infrastructure/mq/service/MqConsumerService.java)）

**问题**: 消费异常时直接释放幂等键（CAS 为 `null`），瞬态异常导致的失败会被当作"从未消费"重复处理。

**修复**:

- 引入三态 `PROCESSING / SUCCESS / FAILED`；

- 业务异常时 CAS 为 **`FAILED`**（拒绝重入，避免重复消费）；

- 仅存在可重试语义时才重新入队。

**测试**: [MqConsumerServiceTest.java](../../../apex-shared-parent/apex-infrastructure-messaging/src/test/java/com/apex/scaffold/infrastructure/mq/service/MqConsumerServiceTest.java)

### H3 跨线程上下文桥接（DataScope / DataSource / Tenant）

**问题**: `ThreadLocal` 上下文（`DataScopeContext`、`DataSourceContext`、`TenantContext`）无法在 Reactor 调度切换（NIO/EventLoop → 工作线程）后自动传播。

**修复**: 三个上下文类统一实现：

- **Scheduler Hook**：任务提交到调度器时快照上下文，执行前安装、执行后恢复；

- **Operator Hook**：包装 onSubscribe/onNext/onRequest/onCancel，在回调边界同步上下文（含 Fuseable QueueSubscription 场景）；

- **null 短路**：当 Reactor Context 中无显式上下文快照时不参与安装/恢复，**避免覆盖** **`onComplete`/`doFinally`** **中业务的** **`pop()`/`clear()`**（这是 `DataSourceSwitchAspectTest` 失败的根因）。

**文件**: [DataScopeContext.java](../../../apex-shared-parent/apex-common/src/main/java/com/apex/scaffold/common/context/DataScopeContext.java)、[DataSourceContext.java](../../../apex-shared-parent/apex-common/src/main/java/com/apex/scaffold/common/context/DataSourceContext.java)、[TenantContext.java](../../../apex-shared-parent/apex-common/src/main/java/com/apex/scaffold/common/context/TenantContext.java)

### H4 AntiReplayAspect 响应式返回值处理（[AntiReplayAspect.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/aspect/AntiReplayAspect.java)）

**问题**: 切面用 `fromCallable` 包装方法返回值，当返回值为 `Publisher` 时被二次包装，破坏响应式语义。

**修复**: 区分 Publisher 与同步返回分支——响应式返回值直接透传，仅同步返回值走 `fromCallable`。

***

## 三、调试中发现的额外关键问题

### 3.1 `Mono.usingWhen` 空资源陷阱

`TenantContext.setTenantId()` 返回的 previous 为 `null` 时，`Mono.usingWhen` 将 null 资源视为"空"直接 `onComplete`，**跳过 resourceClosure**，导致核心业务逻辑静默不执行（`ApiCallLogFilter`、`OperationLogService` 及 3 个监听器均受影响，Mockito verify 失败暴露）。

**修复**: 改用非空 `String[1]` holder 保存 previous，保证 resource 对象永不为 null，三个 cleanup 回调（正常/错误/取消）全部执行。`ApiCallLogFilter.writeLog` 则简化为同步 try/finally 结构。

### 3.2 Operator Hook 覆盖业务清理

当 Reactor Context 无显式快照时，Hook 的 finally-restore 会把 `onComplete`/`doFinally` 中业务代码的 `pop()`/`clear()` 效果覆盖（`DataSourceSwitchAspectTest.shouldSwitchDuringReactiveExecutionAndRestoreOnTermination` 断言 `current().isEmpty()` 失败）。

**修复**: 三处 Hook 均增加"快照为 null 时直接穿透执行"的短路分支。

### 3.3 异步测试时序断言

[OperationLogServiceTest.java](../../../apex-components-parent/apex-system/src/test/java/com/apex/scaffold/application/service/OperationLogServiceTest.java) 改为通过 `saveInternal` 返回的 Mono 手动 `block()` 后再 verify，消除"subscribe 后立即断言"的竞态。

***

## 四、变更文件清单

| 模块                  | 文件                                            | 变更                                    |
| ------------------- | --------------------------------------------- | ------------------------------------- |
| apex-interfaces     | `aspect/IdempotentApiAspect.java`             | 幂等键释放策略（4xx 才释放）                      |
| apex-interfaces     | `aspect/IdempotentApiAspectTest.java`         | 用例补充（5xx 保留键）                         |
| apex-interfaces     | `aspect/AntiReplayAspect.java`                | Publisher/同步返回分支                      |
| apex-interfaces     | `aspect/OperationLogAspect.java`              | tenantId 入口捕获 + 传参                    |
| apex-application    | `listener/UserRegisteredEventListener.java`   | 去 .block() + usingWhen 租户管理           |
| apex-application    | `listener/SubscriptionEventListener.java`     | 同上                                    |
| apex-application    | `listener/CreditEventListener.java`           | 同上                                    |
| apex-application    | `service/OperationLogService.java`            | saveAsync(tenantId) 重载 + holder 修复    |
| apex-application    | `service/OperationLogServiceTest.java`        | 异步时序断言修复                              |
| apex-common         | `context/TenantContext.java`                  | setTenantId/restoreTenantId + null 短路 |
| apex-common         | `context/DataScopeContext.java`               | Scheduler/Operator Hook 桥接            |
| apex-common         | `context/DataSourceContext.java`              | Scheduler/Operator Hook 桥接            |
| apex-common         | `event/BaseDomainEvent.java`                  | 新增 tenantId 字段                        |
| apex-infrastructure | `event/EnhancedDomainEventPublisherImpl.java` | 发布前注入 tenantId                        |
| apex-infrastructure | `cache/service/LocalCacheService.java`        | ttl/cache 读写顺序                        |
| apex-infrastructure | `mq/service/MqConsumerService.java`           | 三态幂等                                  |
| apex-infrastructure | `mq/service/MqConsumerServiceTest.java`       | FAILED 断言更新                           |
| apex-infrastructure | `remote/filter/ApiCallLogFilter.java`         | tenantId 传参 + try/finally             |
| apex-infrastructure | `remote/logging/ApiCallLogService.java`       | tenantId 透传                           |

***

## 五、验证结果

| 检查项                           | 命令                                                                                                                            | 结果              |
| ----------------------------- | ----------------------------------------------------------------------------------------------------------------------------- | --------------- |
| 全量构建（含单测/Spotless/PMD/JaCoCo） | `mvn clean verify -DskipTests=false -DskipITs=true -T 1C`                                                                     | ✅ BUILD SUCCESS |
| 格式检查                          | `mvn spotless:check`                                                                                                          | ✅ 0 violations  |
| 并发相关单测                        | IdempotentApiAspectTest / MqConsumerServiceTest / ApiCallLogFilterTest / DataSourceSwitchAspectTest / OperationLogServiceTest | ✅ 全部通过          |

## 六、影响面与注意事项

- **行为变更**：HTTP 幂等键在 5xx 异常后将保留至 TTL（默认 30s），期间相同幂等请求会收到重复请求提示；若需缩短等待窗口需调整幂等键 TTL 配置。

- **兼容性**：`BaseDomainEvent` 新增 `tenantId` 为向后兼容字段（可空），旧事件无需改造即可反序列化。

- **上下文语义**：H3 桥接仅在显式写入 Reactor Context 时生效，未使用响应式上下文传播的业务（纯同步路径）行为不变。

- **部署建议**：由于涉及多租户隔离与幂等语义，建议在灰度环境先行验证事件消费与日志写入的多租户表现。

