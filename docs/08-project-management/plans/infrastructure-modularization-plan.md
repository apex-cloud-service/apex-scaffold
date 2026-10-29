# 基础设施能力模块设计与迁移清单

## 设计与实施状态

从原 25 个 reactor 模块增加五个共享 JAR，总计 30 个。旧 apex-infrastructure 已保留为仅承载五个能力 JAR 的兼容聚合 JAR，不再承载实现源码。五个实际能力模块为 `apex-infrastructure-persistence`、`apex-infrastructure-cache`、`apex-infrastructure-messaging`、`apex-infrastructure-remote` 和 `apex-infrastructure-integration`；五个模块的 POM 已按能力声明直接依赖，原包名、配置前缀和本地调用语义保持兼容。

本次实施不创建远程基础设施服务，不迁移业务数据库所有权，不改变 Flyway 资源归属，不拆分业务领域模块。本计划记录已落地结构和可复现验证入口；测试结果、质量门禁结果和生产启动结果以[交付报告](../reports/infrastructure-websocket-delivery-report.md)的实测记录为准，结构迁移完成不代表完整门禁或生产验收通过。

## 能力依赖矩阵

| 模块 | 直接内部依赖（省略 apex- 前缀） | 责任 |
| --- | --- | --- |
| infrastructure-persistence | common、domain | MyBatis、租户 SQL、数据权限、数据库驱动、Flyway、Druid、P6Spy |
| infrastructure-cache | common、domain | Redis、Redisson、Caffeine、令牌、黑名单、限流、bulkhead；预热仅依赖 spring-jdbc API |
| infrastructure-messaging | common、domain、infrastructure-persistence | MQ、Outbox、本地事务、领域事件、SSE 通知；直接声明 Redisson/Caffeine |
| infrastructure-remote | common、domain | 受管 WebClient、日志 SPI、熔断、混沌、日志工具；无 Redis/JDBC/MQ starter |
| infrastructure-integration | common、domain、infrastructure-remote | 微信、创蓝、邮件、Excel、搜索、Loki、加密、审计与业务指标；缓存仅 test scope |
| infrastructure | 五个能力 JAR | 兼容旧依赖坐标 |
| system | 五个能力 JAR、common、domain、license starter | 继续持有 system 专属仓储和迁移 |
| auth | integration、common、domain、api-contract | 邮件与加密；其自身数据库/Redis依赖保持 |
| auth-service | cache、messaging、integration、auth及原服务依赖 | 认证装配与本地事件事务 |
| example | cache、integration、system及原依赖 | 示例用例；system仍传递完整能力 |
| system-service | system及原服务依赖 | 通过system装配完整能力 |
| job-service | remote | 仅受管HTTP任务代理，移除无关数据库/Redis/MQ/微信/邮件依赖 |

MQ producer 与 Outbox dispatcher 双向调用，合并消息能力避免循环；领域事件及通知保持同进程。布隆预热使用 JdbcTemplate，cache 直接依赖 API 而不反向依赖 persistence。TokenBlacklistServiceImpl 和 TokenServiceImpl 按 Redis 实质迁移至 cache，保留原包名。Druid/Redisson 指标分别随对应能力。微信、创蓝依赖远程日志与熔断，integration 单向依赖 remote。

## 原25模块范围与不拆理由

- `apex-common`：保留既有职责和依赖；不属于共享基础设施实现，无任务相关拆分理由。
- `apex-domain`：保留既有职责和依赖；不属于共享基础设施实现，无任务相关拆分理由。
- `apex-infrastructure`：本次拆分实现并保留旧坐标兼容。
- `apex-api-contract`：保留既有职责和依赖；不属于共享基础设施实现，无任务相关拆分理由。
- `apex-services-bom`：保留既有职责和依赖；不属于共享基础设施实现，无任务相关拆分理由。
- `apex-system`：更新直接能力依赖或源码守护路径，业务实现不迁移。
- `apex-auth`：更新直接能力依赖或源码守护路径，业务实现不迁移。
- `apex-workflow`：保留既有职责和依赖；不属于共享基础设施实现，无任务相关拆分理由。
- `apex-file`：保留既有职责和依赖；不属于共享基础设施实现，无任务相关拆分理由。
- `apex-office`：保留既有职责和依赖；不属于共享基础设施实现，无任务相关拆分理由。
- `apex-gateway`：保留既有职责和依赖；不属于共享基础设施实现，无任务相关拆分理由。
- `apex-system-service`：更新直接能力依赖或源码守护路径，业务实现不迁移。
- `apex-auth-service`：更新直接能力依赖或源码守护路径，业务实现不迁移。
- `apex-workflow-service`：保留既有职责和依赖；不属于共享基础设施实现，无任务相关拆分理由。
- `apex-file-service`：保留既有职责和依赖；不属于共享基础设施实现，无任务相关拆分理由。
- `apex-office-service`：保留既有职责和依赖；不属于共享基础设施实现，无任务相关拆分理由。
- `apex-job-spring-boot-starter`：保留既有职责和依赖；不属于共享基础设施实现，无任务相关拆分理由。
- `apex-seata-spring-boot-starter`：保留既有职责和依赖；不属于共享基础设施实现，无任务相关拆分理由。
- `apex-job-service`：更新直接能力依赖或源码守护路径，业务实现不迁移。
- `apex-masking-service`：保留既有职责和依赖；不属于共享基础设施实现，无任务相关拆分理由。
- `apex-storage-spring-boot-starter`：保留既有职责和依赖；不属于共享基础设施实现，无任务相关拆分理由。
- `apex-license-spring-boot-starter`：保留既有职责和依赖；不属于共享基础设施实现，无任务相关拆分理由。
- `apex-license-tools`：保留既有职责和依赖；不属于共享基础设施实现，无任务相关拆分理由。
- `apex-example`：更新直接能力依赖或源码守护路径，业务实现不迁移。
- `apex-generator`：保留既有职责和依赖；不属于共享基础设施实现，无任务相关拆分理由。

## 兼容性与质量检查

API/SPI 保持二进制包名，包括 ApiCallObservation/ApiCallLogSink、领域Gateway、Repository及服务端口。三条自动装配 imports 分别迁入 persistence/cache/remote，内容保持原类名；spy.properties 保留classpath根路径，随persistence发布。原模块无测试资源，全部测试源码按能力迁移，跨缓存/创蓝短信契约放integration并以test依赖cache。中文现有Javadoc随源码完整保留，不伪造作者。

原PMD配置、JaCoCo排除项、包分组阈值和test阶段执行绑定随每个能力POM保留；无匹配类的历史FaceAnalysis规则继续保留，不降低门槛。原basic高级库exclusions复制至system全部直接能力路径；root basic/standard/enterprise与许可依赖不改。services-bom继承root能力版本管理。

NoBoundedElasticArchGuardTest扫描五个新能力目录，并向上定位repo root，避免子模块cwd导致静默跳过。TenantIsolationArchTest、TimeFieldArchTest迁入persistence，保持既有包扫描范围；覆盖仍受测试classpath约束，不宣称覆盖全仓。有效格式/扫描脚本与商业结构脚本模块数组同步，build/build.sh未硬编码旧源码位置。历史生成器保留历史模板，避免无关行为修改。

## 本地事务与运行边界

TransactionalOutboxExecutor仍与业务写入共享同一JDBC事务和线程，不使用HTTP替代本地事务。租户SQL拦截器顺序、Redis键、阻塞调度器、Flyway迁移目录及业务数据所有权不变。运行配置与启动扫描仍按原包名加载，不额外自动启用第三方平台。

## 验证与回滚

使用固定 JDK 17.0.12-oracle、mvnd 2.0.0-rc-3，每次命令先 source 构建初始化脚本；验证脚本入口为 [`scripts/test/verify-infrastructure-websocket.sh`](../../../scripts/test/verify-infrastructure-websocket.sh)，支持五能力针对性验证、office/gateway 针对性验证和完整 `clean verify`。本子任务不执行构建；以下 2026-10-14 记录属于迁移过程中的历史记录，不代表当前工作区或本次最终 `clean verify` 结果，最终结果以交付报告补写的主代理实测记录为准：

- `mvnd -T4 -B -pl apex-infrastructure,apex-system-service,apex-auth-service,apex-job-service,apex-example -am test-compile -DskipTests`：BUILD SUCCESS，生产与测试源码编译通过，不代表执行测试。
- `mvnd -T4 -B -pl apex-infrastructure-persistence,apex-infrastructure-cache,apex-infrastructure-messaging,apex-infrastructure-remote,apex-infrastructure-integration -am test`：BUILD SUCCESS。五能力依次执行 83、126、77、84、124 项测试，全部 Failures/Errors/Skipped 为 0；common 538 项、domain 9 项同样通过，原 test 阶段 JaCoCo 检查通过。
- 五能力及 system 的 `spotless:check`：BUILD SUCCESS；修改过的八个扫描/格式脚本 `bash -n` 和 `git diff --check` 通过。
- 迁移阶段曾执行 NoBoundedElasticArchGuardTest 并发现直接 boundedElastic 调用；该条仅为历史诊断记录，当前调用数量、修复状态和最终门禁结果须由主代理按当前代码重新验证，不得将其作为本次通过或失败结论。
- 首轮编译暴露 cache 原先通过其他 SDK 间接获取 Guava；现已直接声明 Guava 并统一管理版本，不再依赖 MQ/微信的偶然传递关系。

迁移阶段的 test 不包含 Failsafe、PMD 或最终服务启动验证；完整门禁必须由主代理在当前工作区执行 clean 后验证。basic/enterprise 档位是否通过也以真实执行记录为准。

回滚以本次迁移清单反向移动当前文件、恢复消费者依赖为旧聚合坐标，并依据迁移前备份恢复 `apex-infrastructure/pom.xml` 的第三方依赖、插件及构建档位配置，移除其五能力依赖，再移除五个 reactor/module 管理条目及新增 POM。必须基于当前工作区备份操作，不能git reset/restore覆盖用户未提交改动；不涉及数据库迁移回滚。发布时五能力JAR与兼容JAR同版本发布，旧消费者可继续使用旧坐标，新消费者选直接能力。

## 逐文件迁移清单

以下路径相对旧apex-infrastructure，目标保留相同src路径。自动装配清单三条拆分已在上述章节列出。

| 原文件 | 目标模块 |
| --- | --- |
| `src/main/java/com/apex/scaffold/infrastructure/audit/DefaultAuditLogService.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/cache/annotation/CacheLock.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/cache/aspect/CacheLockAspect.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/cache/config/BloomFilterNamespaceProperties.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/cache/config/CacheProperties.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/cache/config/CaffeineCacheMetricsBinder.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/cache/config/MultiLevelCacheConfig.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/cache/config/RedisDataCleanupRunner.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/cache/config/RedissonConfig.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/cache/filter/BloomFilterService.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/cache/loader/BloomFilterCacheWarmer.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/cache/metrics/BloomFilterMetrics.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/cache/metrics/BloomFilterRebuildMetrics.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/cache/prewarm/CachePrewarmStrategy.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/cache/service/BloomFilterService.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/cache/service/CacheConsistencyService.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/cache/service/CachePrewarmService.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/cache/service/CacheServiceImpl.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/cache/service/LocalCacheService.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/cache/service/MultiLevelCacheService.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/cache/service/SmsCodeServiceImpl.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/cache/support/InFlightRequestMerge.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/cache/task/BloomFilterRebuildTask.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/cache/wrapper/LogicalExpiryWrapper.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/chaos/ChaosInjectionProperties.java` | `apex-infrastructure-remote` |
| `src/main/java/com/apex/scaffold/infrastructure/chaos/ChaosInjectionService.java` | `apex-infrastructure-remote` |
| `src/main/java/com/apex/scaffold/infrastructure/chuanglan/config/ChuanglanProperties.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/chuanglan/config/ChuanglanWebClientConfig.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/chuanglan/exception/ChuanglanApiException.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/chuanglan/gateway/ChuanglanGatewayImpl.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/chuanglan/service/ChuanglanApiResponse.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/chuanglan/service/ChuanglanApiService.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/chuanglan/service/ChuanglanFlashClient.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/chuanglan/service/ChuanglanRequestSigner.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/chuanglan/service/ChuanglanSmsClient.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/chuanglan/service/ChuanglanSmsCodeServiceImpl.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/chuanglan/strategy/AesPhoneDecryptStrategy.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/chuanglan/strategy/PhoneDecryptStrategy.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/circuitbreaker/CircuitBreakerProperties.java` | `apex-infrastructure-remote` |
| `src/main/java/com/apex/scaffold/infrastructure/circuitbreaker/CircuitBreakerService.java` | `apex-infrastructure-remote` |
| `src/main/java/com/apex/scaffold/infrastructure/config/LokiConfig.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/config/MeilisearchConfig.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/encryption/EncryptionKeyProvider.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/event/DomainEventOutboxDispatcher.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/event/DomainEventOutboxStore.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/event/DomainEventPublisherImpl.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/event/EnhancedDomainEventPublisherImpl.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/event/InMemoryDomainEventBus.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/event/OutboxEventRecord.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/event/OutboxMetricsReporter.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/excel/ExcelExportService.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/logging/LogSampler.java` | `apex-infrastructure-remote` |
| `src/main/java/com/apex/scaffold/infrastructure/logging/SensitiveDataMasker.java` | `apex-infrastructure-remote` |
| `src/main/java/com/apex/scaffold/infrastructure/mail/MailConfigurationProperties.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/mail/MailService.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/metrics/BusinessMetricsService.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/metrics/DruidDataSourceMetricsBinder.java` | `apex-infrastructure-persistence` |
| `src/main/java/com/apex/scaffold/infrastructure/metrics/RedissonMetricsBinder.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/mq/MqDrainParticipant.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/mq/alert/AlertNotificationSender.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/mq/alert/MqAlertManager.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/mq/annotation/Idempotent.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/mq/aspect/IdempotentAspect.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/mq/aspect/MqPublishMetricsAspect.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/mq/cache/LocalIdempotentCache.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/mq/config/RocketMqConsumerConfig.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/mq/config/RocketMqProducerConfig.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/mq/constant/MqConstant.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/mq/handler/UserMessageDlqHandler.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/mq/listener/DeadLetterQueueProcessor.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/mq/listener/NotificationBroadcastListener.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/mq/listener/UserMessagePayload.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/mq/metrics/MqConsumerMetrics.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/mq/metrics/MqProducerMetrics.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/mq/monitor/MqLagMonitor.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/mq/protocol/ServiceMessage.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/mq/service/DefaultMqLocalTransactionHandler.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/mq/service/MqConsumerService.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/mq/service/MqLocalTransactionHandler.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/mq/service/MqProducerService.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/mq/service/MqTransactionListener.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/notification/NotificationService.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/outbox/TransactionalOutboxExecutor.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/outbox/dispatcher/MessageOutboxDispatcher.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/outbox/entity/MessageOutboxEntity.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/outbox/mapper/MessageOutboxMapper.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/outbox/service/MessageOutboxService.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/outbox/service/impl/MessageOutboxServiceImpl.java` | `apex-infrastructure-messaging` |
| `src/main/java/com/apex/scaffold/infrastructure/persistence/aspect/OptimisticLockUpdateAspect.java` | `apex-infrastructure-persistence` |
| `src/main/java/com/apex/scaffold/infrastructure/persistence/config/DatabaseProperties.java` | `apex-infrastructure-persistence` |
| `src/main/java/com/apex/scaffold/infrastructure/persistence/config/MyMetaObjectHandler.java` | `apex-infrastructure-persistence` |
| `src/main/java/com/apex/scaffold/infrastructure/persistence/config/MybatisPlusConfig.java` | `apex-infrastructure-persistence` |
| `src/main/java/com/apex/scaffold/infrastructure/persistence/config/PageHelperConfig.java` | `apex-infrastructure-persistence` |
| `src/main/java/com/apex/scaffold/infrastructure/persistence/config/SlowSqlMetricsInterceptor.java` | `apex-infrastructure-persistence` |
| `src/main/java/com/apex/scaffold/infrastructure/persistence/config/SlowSqlProperties.java` | `apex-infrastructure-persistence` |
| `src/main/java/com/apex/scaffold/infrastructure/persistence/datapermission/DataScopeConditionBuilder.java` | `apex-infrastructure-persistence` |
| `src/main/java/com/apex/scaffold/infrastructure/persistence/datapermission/DataScopeInnerInterceptor.java` | `apex-infrastructure-persistence` |
| `src/main/java/com/apex/scaffold/infrastructure/persistence/datapermission/DataScopeSqlRewriter.java` | `apex-infrastructure-persistence` |
| `src/main/java/com/apex/scaffold/infrastructure/persistence/datapermission/DefaultTenantLineHandler.java` | `apex-infrastructure-persistence` |
| `src/main/java/com/apex/scaffold/infrastructure/persistence/datapermission/MdcDataScopeProvider.java` | `apex-infrastructure-persistence` |
| `src/main/java/com/apex/scaffold/infrastructure/persistence/repository/BaseRepositoryImpl.java` | `apex-infrastructure-persistence` |
| `src/main/java/com/apex/scaffold/infrastructure/persistence/repository/TokenBlacklistServiceImpl.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/persistence/util/PageUtil.java` | `apex-infrastructure-persistence` |
| `src/main/java/com/apex/scaffold/infrastructure/ratelimit/RateLimitProperties.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/ratelimit/adaptive/AdaptiveRateLimiter.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/ratelimit/distributed/DistributedRateLimitMetrics.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/ratelimit/distributed/DistributedRateLimitProperties.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/ratelimit/distributed/DistributedRateLimitService.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/ratelimit/distributed/DistributedRateLimitWarmer.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/ratelimit/distributed/DistributedTokenBucketRateLimiter.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/remote/config/AuthCenterPermissionProperties.java` | `apex-infrastructure-remote` |
| `src/main/java/com/apex/scaffold/infrastructure/remote/config/ExternalHttpClientPoolProperties.java` | `apex-infrastructure-remote` |
| `src/main/java/com/apex/scaffold/infrastructure/remote/config/ManagedWebClientFactory.java` | `apex-infrastructure-remote` |
| `src/main/java/com/apex/scaffold/infrastructure/remote/config/ManagedWebClientInitializer.java` | `apex-infrastructure-remote` |
| `src/main/java/com/apex/scaffold/infrastructure/remote/config/ManagedWebClientLogConfig.java` | `apex-infrastructure-remote` |
| `src/main/java/com/apex/scaffold/infrastructure/remote/config/RemoteApiClientConfig.java` | `apex-infrastructure-remote` |
| `src/main/java/com/apex/scaffold/infrastructure/remote/config/RemoteApiConnectionConfig.java` | `apex-infrastructure-remote` |
| `src/main/java/com/apex/scaffold/infrastructure/remote/config/RemoteApiProperties.java` | `apex-infrastructure-remote` |
| `src/main/java/com/apex/scaffold/infrastructure/remote/filter/ApiCallLogFilter.java` | `apex-infrastructure-remote` |
| `src/main/java/com/apex/scaffold/infrastructure/remote/logging/ApiCallLogProperties.java` | `apex-infrastructure-remote` |
| `src/main/java/com/apex/scaffold/infrastructure/remote/logging/ApiCallLogService.java` | `apex-infrastructure-remote` |
| `src/main/java/com/apex/scaffold/infrastructure/remote/logging/ApiCallLogSink.java` | `apex-infrastructure-remote` |
| `src/main/java/com/apex/scaffold/infrastructure/remote/logging/ApiCallObservation.java` | `apex-infrastructure-remote` |
| `src/main/java/com/apex/scaffold/infrastructure/remote/service/RemoteApiClient.java` | `apex-infrastructure-remote` |
| `src/main/java/com/apex/scaffold/infrastructure/remote/service/RemoteCallPolicy.java` | `apex-infrastructure-remote` |
| `src/main/java/com/apex/scaffold/infrastructure/remote/service/RemoteRequest.java` | `apex-infrastructure-remote` |
| `src/main/java/com/apex/scaffold/infrastructure/resilience/config/BulkheadProperties.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/resilience/service/DistributedBulkheadService.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/security/DefaultEncryptionService.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/security/service/TokenServiceImpl.java` | `apex-infrastructure-cache` |
| `src/main/java/com/apex/scaffold/infrastructure/wechat/config/WeChatAppProperties.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/wechat/config/WeChatMiniAppProperties.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/wechat/config/WeChatMpProperties.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/wechat/config/WeChatWebClientConfig.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/wechat/config/WeWorkProperties.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/wechat/dto/SubscribeMessage.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/wechat/dto/TemplateMessage.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/wechat/dto/WeChatAccessToken.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/wechat/dto/WeChatUserInfo.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/wechat/exception/WeChatApiException.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/wechat/gateway/WeChatGatewayImpl.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/wechat/service/WeChatApiResponse.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/wechat/service/WeChatAppAdvancedService.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/wechat/service/WeChatAppService.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/wechat/service/WeChatJsonCallTemplate.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/wechat/service/WeChatMaterialService.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/wechat/service/WeChatMiniProgramService.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/wechat/service/WeChatMpService.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/wechat/service/WeChatOAuthService.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/wechat/service/WeChatStatisticsService.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/wechat/service/WeChatTagService.java` | `apex-infrastructure-integration` |
| `src/main/java/com/apex/scaffold/infrastructure/wechat/service/WeWorkService.java` | `apex-infrastructure-integration` |
| `src/main/resources/spy.properties` | `apex-infrastructure-persistence` |
| `src/test/java/com/apex/scaffold/arch/TenantIsolationArchTest.java` | `apex-infrastructure-persistence` |
| `src/test/java/com/apex/scaffold/arch/TimeFieldArchTest.java` | `apex-infrastructure-persistence` |
| `src/test/java/com/apex/scaffold/domain/service/SmsCodeServiceContractTest.java` | `apex-infrastructure-integration` |
| `src/test/java/com/apex/scaffold/infrastructure/SmsCodeServiceImplementationsContractTest.java` | `apex-infrastructure-integration` |
| `src/test/java/com/apex/scaffold/infrastructure/cache/aspect/CacheLockAspectTest.java` | `apex-infrastructure-cache` |
| `src/test/java/com/apex/scaffold/infrastructure/cache/benchmark/RedisBenchmark.java` | `apex-infrastructure-cache` |
| `src/test/java/com/apex/scaffold/infrastructure/cache/config/CaffeineCacheMetricsBinderTest.java` | `apex-infrastructure-cache` |
| `src/test/java/com/apex/scaffold/infrastructure/cache/filter/BloomFilterServiceTest.java` | `apex-infrastructure-cache` |
| `src/test/java/com/apex/scaffold/infrastructure/cache/service/CacheServiceImplTest.java` | `apex-infrastructure-cache` |
| `src/test/java/com/apex/scaffold/infrastructure/cache/service/MultiLevelCacheServiceBloomFilterIntegrationTest.java` | `apex-infrastructure-cache` |
| `src/test/java/com/apex/scaffold/infrastructure/cache/service/MultiLevelCacheServiceIntegrationTest.java` | `apex-infrastructure-cache` |
| `src/test/java/com/apex/scaffold/infrastructure/cache/service/MultiLevelCacheServiceStampedeTest.java` | `apex-infrastructure-cache` |
| `src/test/java/com/apex/scaffold/infrastructure/cache/service/SmsCodeServiceImplTest.java` | `apex-infrastructure-cache` |
| `src/test/java/com/apex/scaffold/infrastructure/cache/support/InFlightRequestMergeTest.java` | `apex-infrastructure-cache` |
| `src/test/java/com/apex/scaffold/infrastructure/cache/task/BloomFilterRebuildTaskTest.java` | `apex-infrastructure-cache` |
| `src/test/java/com/apex/scaffold/infrastructure/cache/wrapper/LogicalExpiryWrapperTest.java` | `apex-infrastructure-cache` |
| `src/test/java/com/apex/scaffold/infrastructure/chaos/ChaosInjectionServiceTest.java` | `apex-infrastructure-remote` |
| `src/test/java/com/apex/scaffold/infrastructure/chuanglan/config/ChuanglanPropertiesTest.java` | `apex-infrastructure-integration` |
| `src/test/java/com/apex/scaffold/infrastructure/chuanglan/exception/ChuanglanApiExceptionTest.java` | `apex-infrastructure-integration` |
| `src/test/java/com/apex/scaffold/infrastructure/chuanglan/gateway/ChuanglanGatewayImplTest.java` | `apex-infrastructure-integration` |
| `src/test/java/com/apex/scaffold/infrastructure/chuanglan/service/ChuanglanApiServiceTest.java` | `apex-infrastructure-integration` |
| `src/test/java/com/apex/scaffold/infrastructure/chuanglan/service/ChuanglanRequestSignerTest.java` | `apex-infrastructure-integration` |
| `src/test/java/com/apex/scaffold/infrastructure/chuanglan/service/ChuanglanSmsCodeServiceImplTest.java` | `apex-infrastructure-integration` |
| `src/test/java/com/apex/scaffold/infrastructure/circuitbreaker/CircuitBreakerPropertiesTest.java` | `apex-infrastructure-remote` |
| `src/test/java/com/apex/scaffold/infrastructure/event/DomainEventPublisherImplTest.java` | `apex-infrastructure-messaging` |
| `src/test/java/com/apex/scaffold/infrastructure/event/OutboxMetricsReporterTest.java` | `apex-infrastructure-messaging` |
| `src/test/java/com/apex/scaffold/infrastructure/metrics/DruidDataSourceMetricsBinderTest.java` | `apex-infrastructure-persistence` |
| `src/test/java/com/apex/scaffold/infrastructure/metrics/RedissonMetricsBinderTest.java` | `apex-infrastructure-cache` |
| `src/test/java/com/apex/scaffold/infrastructure/mq/MqDrainParticipantTest.java` | `apex-infrastructure-messaging` |
| `src/test/java/com/apex/scaffold/infrastructure/mq/OutboxIntegrationTest.java` | `apex-infrastructure-messaging` |
| `src/test/java/com/apex/scaffold/infrastructure/mq/aspect/IdempotentAspectTest.java` | `apex-infrastructure-messaging` |
| `src/test/java/com/apex/scaffold/infrastructure/mq/aspect/MqPublishMetricsAspectTest.java` | `apex-infrastructure-messaging` |
| `src/test/java/com/apex/scaffold/infrastructure/mq/listener/DeadLetterQueueProcessorTest.java` | `apex-infrastructure-messaging` |
| `src/test/java/com/apex/scaffold/infrastructure/mq/listener/NotificationBroadcastListenerTest.java` | `apex-infrastructure-messaging` |
| `src/test/java/com/apex/scaffold/infrastructure/mq/listener/UserMessagePayloadTest.java` | `apex-infrastructure-messaging` |
| `src/test/java/com/apex/scaffold/infrastructure/mq/monitor/MqLagMonitorTest.java` | `apex-infrastructure-messaging` |
| `src/test/java/com/apex/scaffold/infrastructure/mq/service/DefaultMqLocalTransactionHandlerTest.java` | `apex-infrastructure-messaging` |
| `src/test/java/com/apex/scaffold/infrastructure/mq/service/MqConsumerServiceTest.java` | `apex-infrastructure-messaging` |
| `src/test/java/com/apex/scaffold/infrastructure/mq/service/MqProducerServiceTest.java` | `apex-infrastructure-messaging` |
| `src/test/java/com/apex/scaffold/infrastructure/mq/service/MqTransactionListenerTest.java` | `apex-infrastructure-messaging` |
| `src/test/java/com/apex/scaffold/infrastructure/outbox/TransactionalOutboxExecutorTest.java` | `apex-infrastructure-messaging` |
| `src/test/java/com/apex/scaffold/infrastructure/persistence/config/DatabasePropertiesTest.java` | `apex-infrastructure-persistence` |
| `src/test/java/com/apex/scaffold/infrastructure/persistence/config/MybatisPlusConfigTest.java` | `apex-infrastructure-persistence` |
| `src/test/java/com/apex/scaffold/infrastructure/persistence/config/SlowSqlMetricsInterceptorTest.java` | `apex-infrastructure-persistence` |
| `src/test/java/com/apex/scaffold/infrastructure/persistence/datapermission/DataScopeConditionBuilderTest.java` | `apex-infrastructure-persistence` |
| `src/test/java/com/apex/scaffold/infrastructure/persistence/datapermission/DataScopeE2eTest.java` | `apex-infrastructure-persistence` |
| `src/test/java/com/apex/scaffold/infrastructure/persistence/datapermission/DataScopeInnerInterceptorTest.java` | `apex-infrastructure-persistence` |
| `src/test/java/com/apex/scaffold/infrastructure/persistence/datapermission/DataScopeSqlRewriterTest.java` | `apex-infrastructure-persistence` |
| `src/test/java/com/apex/scaffold/infrastructure/persistence/datapermission/DefaultTenantLineHandlerTest.java` | `apex-infrastructure-persistence` |
| `src/test/java/com/apex/scaffold/infrastructure/persistence/datapermission/MdcDataScopeProviderTest.java` | `apex-infrastructure-persistence` |
| `src/test/java/com/apex/scaffold/infrastructure/persistence/datasource/DataSourceSwitchE2eTest.java` | `apex-infrastructure-persistence` |
| `src/test/java/com/apex/scaffold/infrastructure/persistence/repository/TokenBlacklistServiceImplTest.java` | `apex-infrastructure-cache` |
| `src/test/java/com/apex/scaffold/infrastructure/persistence/util/PageUtilTest.java` | `apex-infrastructure-persistence` |
| `src/test/java/com/apex/scaffold/infrastructure/ratelimit/adaptive/AdaptiveRateLimiterStressTest.java` | `apex-infrastructure-cache` |
| `src/test/java/com/apex/scaffold/infrastructure/ratelimit/distributed/DistributedRateLimitServiceTest.java` | `apex-infrastructure-cache` |
| `src/test/java/com/apex/scaffold/infrastructure/ratelimit/distributed/DistributedTokenBucketRateLimiterTest.java` | `apex-infrastructure-cache` |
| `src/test/java/com/apex/scaffold/infrastructure/remote/ManagedWebClientLifecycleTest.java` | `apex-infrastructure-remote` |
| `src/test/java/com/apex/scaffold/infrastructure/remote/exception/RemoteApiExceptionTest.java` | `apex-infrastructure-remote` |
| `src/test/java/com/apex/scaffold/infrastructure/remote/filter/ApiCallLogFilterTest.java` | `apex-infrastructure-remote` |
| `src/test/java/com/apex/scaffold/infrastructure/remote/logging/ApiCallLogServiceTest.java` | `apex-infrastructure-remote` |
| `src/test/java/com/apex/scaffold/infrastructure/remote/service/RemoteApiClientTest.java` | `apex-infrastructure-remote` |
| `src/test/java/com/apex/scaffold/infrastructure/remote/service/RemoteRequestTest.java` | `apex-infrastructure-remote` |
| `src/test/java/com/apex/scaffold/infrastructure/security/DefaultEncryptionServiceTest.java` | `apex-infrastructure-integration` |
| `src/test/java/com/apex/scaffold/infrastructure/security/TokenServiceImplTest.java` | `apex-infrastructure-cache` |
| `src/test/java/com/apex/scaffold/infrastructure/wechat/config/WeChatWebClientConfigTest.java` | `apex-infrastructure-integration` |
| `src/test/java/com/apex/scaffold/infrastructure/wechat/gateway/WeChatGatewayImplTest.java` | `apex-infrastructure-integration` |
| `src/test/java/com/apex/scaffold/infrastructure/wechat/service/WeChatApiResponseTest.java` | `apex-infrastructure-integration` |
| `src/test/java/com/apex/scaffold/infrastructure/wechat/service/WeChatAppServiceTest.java` | `apex-infrastructure-integration` |
| `src/test/java/com/apex/scaffold/infrastructure/wechat/service/WeChatMiniProgramServiceTest.java` | `apex-infrastructure-integration` |
| `src/test/java/com/apex/scaffold/infrastructure/wechat/service/WeChatMpServiceTest.java` | `apex-infrastructure-integration` |
