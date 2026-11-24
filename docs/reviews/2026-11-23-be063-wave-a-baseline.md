# BE-063 Wave A：后端静态基线

基线：`dev` `90a2f783e2c251a55f53b0fc1b845b11d537e951`。范围为 reactor 全部项目的模块登记盘点，以及关键 DDD、共享韧性和发布链路的代表性源码抽查，不等同于逐文件评审。本文不使用历史测试结果证明当前运行行为。

## 优先处理项

| 优先级 | 静态证据与影响 | 修复准入与验证 |
| --- | --- | --- |
| P1 发布隔离 | `k8s/deployment.yaml:90` 稳定版 Deployment selector 与 `:167` 稳定 Service selector 均只有 `app: apex-gateway`；`k8s/canary-deployment.yaml:43` 金丝雀 Pod 具有该标签。稳定 Service 的 selector 因此会匹配金丝雀 Pod，Ingress 权重不再是唯一分流入口。`k8s/pod-disruption-budget.yaml:18` 要求 `track: stable`，稳定 Pod 模板未设置，PDB 不选中稳定 Pod。 | 先出独立部署规格，区分可变的 Service selector 和不可变的 Deployment selector；验证现网无中断过渡、稳定/金丝雀 EndpointSlice 互斥、PDB 选中稳定副本以及 0%/放量/切回。不直接更改现网 Deployment selector。 |
| P1 发布门禁 | `apex-tools-parent/apex-tools-build/bin/apex.sh:832`、`:839` 仍等待 `deployment/apex-scaffold`，清单部署的是 `apex-gateway` 等八个服务。`k8s/README.md` 的提升命令及金丝雀 Ingress 名称也沿用旧名；`k8s/canary-deployment.yaml:143` 初始权重为 10，与手册“部署后不接收流量”不符。 | 按清单校验等待目标，失败必须阻止放量；先验证新版本就绪和可达，再显式放量；补静态合约和命令失败路径测试，并在隔离集群演练。 |
| P1 无 Nacos 出站 | `k8s/deployment.yaml` 的共享配置关闭 Nacos discovery，稳定网关的显式路由覆盖只含 integration/aggregation 及两个可选服务，金丝雀网关无同类覆盖；`apex-gateway` 的 `application-prod.yml:37` 起，多条路由仍回落 `lb://`。这是模板接线缺口，不代表外部注入了配置的部署必然失败。 | 同一拓扑下对照两版本的有效路由和必需依赖；两种服务发现模式分别冒烟。Office HTTP/WS 不能仅靠两个副本视为实时高可用：票据与连接注册表为实例本地，须单独验证一致路由、断线补偿和排空。 |
| P2 多级缓存（关联 BE-051） | `MultiLevelCacheService#getWithLoader` 已有同键合并、回源速率保护和负缓存；`tryReadStaleCache` 在首次未命中及数据库错误后，用相同 Redis 键再读一次，不读取可用期限元数据、不校验配置的最大 stale 年龄。在普通原生 TTL 到期时，二次读取不会把已经过期的原值恢复出来。 | 不挪用 BE-051。先确定真实配置与允许读取旧值的数据域；只对有明确陈旧上限的数据设计保留窗口，覆盖过期边界、主动失效、跨实例回填和 DB/Redis 组合故障。 |
| P2 DDD 边界 | workflow 的 `WorkflowQueryService`、`WorkflowStatisticsService` 直读 Mapper，属已记录的查询侧例外；`DeviceAdminHandler#shadow`、`#commands` 直接协调域仓储查询，属于可评估的协议层编排，未发现这两处从 Handler 依赖具体 MyBatis 适配器。 | 优先核定用例应归属的位置，保留查询约束及租户口径；如需抽取应用查询服务，先固定响应和分页基线，不因统一包名搬迁现有组件。 |

缓存补充：`MultiLevelCacheService.java:185` 回填 L1 使用固定五分钟，未参考 L2 的剩余 TTL；实际窗口需按部署配置和回填时刻测量，继续关联 BE-051。

## 模块与扩展边界

由根 `pom.xml` 沿 `<modules>` 递归解析，计入根共 **45 个项目，8 个 POM 与 37 个 JAR**。下表省略 `apex-` 前缀；目录聚合不等同于继承或运行依赖。

| 分组 | 叶子清单 | 核验重点 |
| --- | --- | --- |
| shared-parent (9) | common、domain、api-contract、infrastructure-persistence、infrastructure-cache、infrastructure-messaging、infrastructure-remote、infrastructure-integration、infrastructure | `common` 提供通用上下文与阻塞 I/O 调度；共享 domain/contract 作为端口与 DTO，设施实现分模块；旧 infrastructure 坐标仅为兼容。 |
| components-parent (6) | system、auth、workflow、file、office、integration | system 保留自有四层；auth/workflow/file/office HTTP 协议装配在运行模块；integration 以 connector/device 自含子域承载协议，已有 IntegrationService 边界测试。 |
| starters-parent (4) | storage-spring-boot-starter、license-spring-boot-starter、job-spring-boot-starter、seata-spring-boot-starter | 存储插件 SPI 与基础增强由 starter 提供；不在业务域复制框架；默认关闭的能力不能算作所有运行档位已启用。 |
| services-bom (11) | gateway、system-service、auth-service、workflow-service、file-service、office-service、integration-service、aggregation-service、job-service、masking-service、knowledge-service | 八个标准部署、三个可选；聚合服务以 workflow/office 两源做只读查询，不持有业务数据库；网关只管入口策略。 |
| applications-parent (2) | example、boot-all | 示例与组合装配；变动不能只验证企业版而忽略 basic 档和单进程装配。 |
| tools-parent (4) | license-tools、generator、tools-deployment、tools-build | 保持离线工具隔离；`BuildManifestSyncTest` 约束构建注册表，`TestReportSummary` 聚合测试 XML，不替代 fresh verify。 |
| business-parent (1) | knowledge | 知识域的解析、向量化、索引和会话端口与适配器分离；运行装配在 knowledge-service。旧联调所需模型和凭据仍留 BE-043/FE-029。 |

`ContextBoundaryArchTest` 只扫描指定组件源码的 import，不覆盖所有依赖形式或所有运行模块；`BuildManifestSyncTest` 校验模块登记和产物映射，不验证 DDD 边界。后续逐域复核应分开记录 Maven 聚合、parent 继承、直接生产依赖和守护测试。

## 韧性与容量取证

| 能力 | 已定位的实现或配置 | 未验证及后续证据 |
| --- | --- | --- |
| 缓存 | `MultiLevelCacheConfig` 装配异步 Caffeine，`MultiLevelCacheService` 使用 Redis 与本地缓存，`InFlightRequestMerge` 为进程内合并。 | 查两实例缓存命中率、失效传播时间、Redis 降级流量、短期热点与高基数时的容量。单进程合并不等于跨实例合并。 |
| 熔断与超时 | gateway 的 route `CircuitBreaker` 配合命名 Resilience4j 配置，auth 有 10 秒 TimeLimiter；system-service 配置 Resilience4j 与 Sentinel Nacos 数据源；`RemoteApiClient` 根据配置应用熔断和重试；`UnifiedTodoAggregationService`、`OverviewFacadeService` 有各源超时及并发隔离。 | 分别核定全链路预算、各层最大尝试数、写幂等与超时后的资源释放；不能把静态阈值直接外推成生产 SLO。 |
| 限流与背压 | `RequestRateLimitFilter`、`TokenBucketRateLimitFilter` 及 system 自有守卫；`BlockingIoSchedulers` 隔离阻塞工作；Outbox 配有批量、并行参数和积压指标。 | 测网关与业务守卫叠加、Redis 故障模式、调度器饱和、MQ 积压及恢复；观测拒绝率、队列深度、连接池和重试放大。 |
| 健康与发布 | Kubernetes 清单提供探针、滚动更新和网关 HPA/PDB 资产；`GrayRouteFilter` 与 `GrayTagServiceInstanceListSupplier` 提供发现侧标签过滤，与 Ingress 金丝雀是独立机制。当前 `k8s/` 无独立蓝绿切换资产。 | 先确定发布控制层与指标归因；在隔离集群验证实流量分布、长连接/摘流、HPA 扩容响应及回滚时间。蓝绿必须说明活跃/预览入口、版本兼容与切回门禁，不能以滚动更新替代。 |

覆盖差异：gateway 的 `application.yml:113` 基础认证路由挂了 CircuitBreaker，但 `application-prod.yml:36` 的对应路由没有；`GatewayApplicationTest` 在 dev 档校验认证的 10 秒配置，不能证明 prod 路由实际使用该限制。Sentinel 的 Nacos 数据源也受配置条件约束；后续按运行档和覆盖来源检查最终绑定，不直接复制 dev 阈值到 prod。

### 源码与测试定位

以下是静态取证位置；列出的既有应用测试均未在本波执行。

| 对象 | 实现或配置 | 已有守护测试 |
| --- | --- | --- |
| 无 Nacos 路由 | `k8s/deployment.yaml:59` 发现开关、`:122` 稳定路由覆盖；`k8s/canary-deployment.yaml:57` 金丝雀环境；`apex-services-bom/apex-gateway/src/main/resources/application-prod.yml:37` 默认路由 URI | `apex-services-bom/apex-gateway/src/test/java/com/apex/scaffold/gateway/GatewayRouteProfileConfigTest.java:52` 核对路由档位，不验证 Kubernetes 注入。 |
| DDD 查询边界 | `apex-components-parent/apex-workflow/src/main/java/com/apex/scaffold/workflow/application/query/WorkflowQueryService.java:13`；`apex-components-parent/apex-integration/src/main/java/com/apex/scaffold/integration/device/interfaces/DeviceAdminHandler.java:337` 与 `:450` | `apex-components-parent/apex-system/src/test/java/com/apex/scaffold/arch/ContextBoundaryArchTest.java:35`；`apex-services-bom/apex-integration-service/src/test/java/com/apex/scaffold/integrationservice/arch/IntegrationServiceBoundaryTest.java:156` 核对装配资源。 |
| 缓存 | `apex-shared-parent/apex-infrastructure-cache/src/main/java/com/apex/scaffold/infrastructure/cache/config/MultiLevelCacheConfig.java:79`；同模块 `cache/service/MultiLevelCacheService.java:276` 与 `:324`；`cache/support/InFlightRequestMerge.java:42` | `apex-shared-parent/apex-infrastructure-cache/src/test/java/com/apex/scaffold/infrastructure/cache/service/MultiLevelCacheServiceStampedeTest.java:39`。 |
| 熔断与隔离 | `apex-services-bom/apex-gateway/src/main/resources/application.yml:113` 与 `:328`；`apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/config/SentinelNacosDataSourceConfig.java:60`；`apex-shared-parent/apex-infrastructure-remote/src/main/java/com/apex/scaffold/infrastructure/remote/service/RemoteApiClient.java:888`；`apex-services-bom/apex-aggregation-service/src/main/java/com/apex/scaffold/aggregationservice/application/UnifiedTodoAggregationService.java:143`；`apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/OverviewFacadeService.java:169` | `apex-services-bom/apex-gateway/src/test/java/com/apex/scaffold/gateway/CircuitBreakerRouteIntegrationTest.java:44` 与 `GatewayApplicationTest.java:33`，不替代真实产线路由验证。 |
| 限流、调度与积压 | `apex-services-bom/apex-gateway/src/main/java/com/apex/scaffold/gateway/filter/RequestRateLimitFilter.java:55`、同模块 `ratelimit/TokenBucketRateLimitFilter.java:125`；`apex-shared-parent/apex-common/src/main/java/com/apex/scaffold/common/scheduler/BlockingIoSchedulers.java:94`；`apex-shared-parent/apex-infrastructure-messaging/src/main/java/com/apex/scaffold/infrastructure/event/OutboxMetricsReporter.java:74` | `apex-shared-parent/apex-infrastructure-messaging/src/test/java/com/apex/scaffold/infrastructure/event/OutboxMetricsReporterTest.java:19`。 |
| 健康与发布 | `k8s/deployment.yaml:132` 探针与 `:813` HPA；`apex-services-bom/apex-gateway/src/main/java/com/apex/scaffold/gateway/filter/GrayRouteFilter.java:45`、同模块 `lb/GrayTagServiceInstanceListSupplier.java:39` | `apex-services-bom/apex-gateway/src/test/java/com/apex/scaffold/gateway/lb/GrayTagServiceInstanceListSupplierTest.java:38` 只验证发现侧标签选择。 |

建议测量先固定 SHA、镜像摘要、资源规格、副本、数据规模、运行档及流量分布；采集稳态与阶梯加压的吞吐、P95/P99、错误和拒绝率，以及依赖故障期、恢复期与回滚时间。本波未执行 JVM/前端构建、应用测试、压测或真实集群切流；不生成测试报告、不设定虚构阈值。本波只新增静态证据文档，可按文件或本波新增段撤回；不暂存、提交或部署。
