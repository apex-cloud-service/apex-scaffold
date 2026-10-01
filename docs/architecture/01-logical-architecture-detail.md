# Apex Scaffold DDD 四层逻辑架构详解

> 图表版本：2.0\
> 项目基线：根 POM `1.2.0`，Java 17，Spring Boot 3.5.15\
> 更新日期：2026-09-04

![Apex Scaffold DDD 四层逻辑架构图](../diagrams/01_logical_architecture.svg)

## 1. 文档目的

本文解释 Apex Scaffold 的逻辑分层、模块职责、依赖方向和典型调用链。内容以当前仓库代码、Maven 配置和架构约束测试为依据。图中能力分为三种状态：

- **已实现**：仓库存在对应接口、服务或适配器。

- **可选能力**：依赖与实现存在，但是否启用由 Maven/Spring Profile 或配置项决定。

- **架构扩展点**：图中用于表达可演进方向，不代表当前存在完整生产实现。

## 2. 总体结构

系统采用 DDD 四层架构，并结合 Ports and Adapters 模式隔离业务规则与技术实现。

| 层次    | Maven 模块              | 核心职责                        | 允许的主要依赖             |
| ----- | --------------------- | --------------------------- | ------------------- |
| 接口层   | `apex-interfaces`     | 协议适配、请求校验、安全入口、响应转换、OpenAPI | 应用层、领域层；启动装配时接入基础设施 |
| 应用层   | `apex-application`    | 用例编排、事务边界协调、DTO 转换、跨聚合协作    | 领域层、公共模块            |
| 领域层   | `apex-domain`         | 聚合、实体、值对象、领域服务、端口接口         | 公共模块及必要的基础类型        |
| 基础设施层 | `apex-infrastructure` | 数据库、缓存、消息、存储、搜索、第三方服务的适配器   | 领域层、公共模块            |

源码依赖规则由 `LayeringArchTest` 固化：领域层不得依赖接口层或基础设施层，应用层不得导入接口层或基础设施实现，基础设施层不得依赖接口层，领域层不得依赖 MyBatis-Plus。测试还禁止在返回 `Mono` 或 `Flux` 的方法上直接使用普通 JDBC `@Transactional`。

当前 `apex-application/pom.xml` 仍声明了 `apex-infrastructure` Maven 依赖，而源码规则禁止应用代码导入基础设施包。两者含义不同：前者属于构建类路径，后者约束源码依赖方向。后续演进应继续评估是否可移除该构建依赖，使模块描述与源码规则完全一致。

## 3. 接口层

接口层负责把外部协议转换为应用用例输入，不承载核心业务规则。

### 3.1 已实现组件

- `ApiRouter`：以 Spring WebFlux 函数式路由注册认证、用户、部门、文件、日志和监控等 HTTP 端点。

- `WorkflowRouter`：提供工作流相关路由。

- Handler：包括 `AuthHandler`、`UserHandler`、`DeptHandler`、`FileHandler`、`HealthHandler`、`MonitorDashboardHandler` 等。

- `RouterOpenApiConfig`：为函数式路由维护 OpenAPI 描述和唯一 `operationId`。

- 安全与上下文：`SecurityConfig`、`JwtConfig`、权限切面、租户切面和相关过滤器。

- 可观测性入口：`MetricsConfig`、`TraceConfig`、`LoggingFilter`、`TraceFilter`。

### 3.2 边界规则

1. Router 只定义协议路径和 Handler 映射。
2. Handler 负责解析、校验、鉴权调用和响应转换。
3. 业务流程交由应用服务完成。
4. 阻塞数据库、文件系统或同步 SDK 调用不得直接运行在 Netty 事件循环上。
5. OpenAPI 定义应与实际函数式路由同步维护。

图中的 WebSocket、GraphQL 和 gRPC 表示协议扩展方向；是否具备完整实现应以对应模块、Bean 和运行配置为准。

## 4. 应用层

应用层表达系统可执行的业务用例，组织领域对象与领域端口，但不实现 SQL、缓存客户端或第三方 SDK 细节。

### 4.1 主要应用服务

| 业务域   | 代表服务                                                                               | 作用               |
| ----- | ---------------------------------------------------------------------------------- | ---------------- |
| 认证与用户 | `AuthService`、`AuthRegistrationService`、`UserService`、`AdminUserManagementService` | 登录、注册、用户查询与管理编排  |
| 权限与组织 | `PermissionService`、`RoleService`、`DeptService`、`MenuService`                      | RBAC、角色、部门树和菜单用例 |
| 工作流   | `WorkflowService`、`WorkflowQueryService`、`WorkflowStatisticsService`               | 流程定义、实例、任务与统计编排  |
| 文件与存储 | `FileService`                                                                      | 文件元数据与存储端口协作     |
| 搜索    | `SearchService`、`IndexSyncService`                                                 | 统一搜索与索引同步        |
| 运维管理  | `JobService`、`RuntimeLogService`、`AuditLogQueryService`                            | 调度、运行日志和审计查询     |
| 租户与合规 | `TenantApplicationService`、`AccountDeletionService`、`ModerationService`            | 租户、账号注销和内容审核流程   |

### 4.2 应用层不负责的内容

- 不直接编写 Mapper SQL。

- 不选择 Redis、Caffeine、RocketMQ、MinIO 或 TOS 客户端。

- 不依赖 HTTP 请求/响应对象表达业务规则。

- 不把跨请求状态保存在应用服务实例中。

## 5. 领域层

领域层是业务语义中心。它定义上层可使用的业务模型，也定义下层必须实现的技术端口。

### 5.1 领域模型

仓库中包含用户、角色、部门、租户、文件、订阅、积分、合规、工作流、审核、日志和搜索等领域模型。工作流领域包含流程定义、流程实例、任务和审批记录等概念；统一搜索通过 `SearchResultEntity` 等领域对象屏蔽具体搜索引擎。

### 5.2 领域端口

典型端口包括：

- 仓储：`UserRepository`、`RoleRepository`、`DeptRepository`、`TenantRepository`、`FileRepository`。

- 工作流仓储：`WorkflowDefinitionRepository`、`ProcessInstanceRepository`、`TaskRepository`、`ApprovalRecordRepository`。

- 技术能力：`CacheService`、`StorageService`、`TokenService`、`TokenBlacklistService`、`SmsCodeService`。

- 集成能力：`DomainEventPublisher`、`SearchRepository` 和日志查询端口。

端口放在领域层，使应用层依赖业务契约，基础设施层实现契约。更换数据库、缓存、对象存储或搜索引擎时，上层用例不应感知具体 SDK。

## 6. 基础设施层

基础设施层将领域端口映射到可运行的技术实现。

| 能力    | 当前实现或技术                                                              | 状态说明                                  |
| ----- | -------------------------------------------------------------------- | ------------------------------------- |
| 持久化   | MyBatis-Plus、MyBatis、Druid、Dynamic Datasource、MySQL、Flyway           | 已引入并存在 RepositoryImpl、Mapper、PO 和迁移脚本 |
| 缓存    | Caffeine、Redis Reactive、Redisson                                     | 已引入；标准版默认启用缓存                         |
| 消息    | RocketMQ、领域事件总线、Outbox                                               | 依赖与实现存在；标准版默认关闭消息队列，企业版启用             |
| 对象存储  | Local、MinIO、火山云 TOS                                                  | 多实现可选，实际 Provider 由配置决定               |
| 搜索    | Meilisearch 适配能力                                                     | 依赖和搜索端口存在，运行可用性取决于配置与外部服务             |
| 外部服务  | 微信、短信、受管远程 API 客户端                                                   | 按业务模块和配置启用                            |
| 治理与监控 | Actuator、Micrometer、Prometheus、Sentinel、Resilience4j、Zipkin Reporter | 监控在标准版默认启用；链路追踪在企业版默认启用               |

基础设施层中的 `UserRepositoryImpl`、`RoleRepositoryImpl`、`DeptRepositoryImpl`、工作流 RepositoryImpl 等实现领域仓储接口；`CacheServiceImpl`、`LocalStorageServiceImpl`、`MinioServiceImpl`、`VolcEngineStorageServiceImpl` 等实现可替换技术能力。

## 7. 典型请求链路

以“查询部门树”为例：

1. 客户端请求 `GET /admin-api/v1/depts`。
2. `ApiRouter` 将请求交给 `DeptHandler.listTree`。
3. Handler 执行权限要求并调用 `DeptService.listTree`。
4. 应用服务依赖领域端口 `DeptRepository` 获取部门数据并组装树。
5. 基础设施实现 `DeptRepositoryImpl` 通过持久化组件访问数据库。
6. 结果沿相反方向转换为领域对象、应用结果和 HTTP 响应。

关键点是控制流从接口层向内推进，但基础设施实现对领域端口的依赖方向指向领域层，这就是图中的依赖倒置。

## 8. 响应式与阻塞 I/O 边界

Spring WebFlux 和 Reactor 管理 HTTP 流程不意味着底层 JDBC、MyBatis、对象存储 SDK 都是非阻塞的。项目必须显式隔离阻塞调用：

- Handler 和应用服务可使用 `Mono`/`Flux` 表达异步流程。

- MyBatis/JDBC、同步文件操作和同步第三方 SDK 应调度到专用阻塞线程池。

- 普通 JDBC `@Transactional` 不能直接包裹返回 `Mono`/`Flux` 的方法；需要使用适合阻塞事务的端口与基础设施实现完成原子操作。

- 监控应覆盖事件循环、阻塞线程池、连接池和下游延迟。

## 9. 构建档位与能力状态

根 POM 定义三个构建档位：

| Profile        | JWT | RBAC | 缓存 | 消息队列 | 监控 | 链路追踪 |
| -------------- | --: | ---: | -: | ---: | -: | ---: |
| `basic`        |   开 |    关 |  关 |    关 |  关 |    关 |
| `standard`（默认） |   开 |    开 |  开 |    关 |  开 |    关 |
| `enterprise`   |   开 |    开 |  开 |    开 |  开 |    开 |

因此架构图展示的是系统能力全集，不等同于任意部署都启用全部组件。发布说明和部署图必须注明实际 Profile 与运行配置。

## 10. 架构约束与验收

- 领域层不得依赖 Web、Mapper、PO 或具体中间件类型。

- 应用层生产代码不得导入接口层或基础设施包。

- 基础设施适配器实现领域端口，并通过 Spring 装配提供给上层。

- 新增端点时同步维护 Router、Handler、OpenAPI 和权限声明。

- 新增数据库结构时只通过 Flyway 增量迁移。

- 新增同步 I/O 时明确线程调度、超时、重试和可观测性。

- 架构变更应通过 ArchUnit 测试及模块测试验证。

## 11. 事实来源与限制

本文依据根 `pom.xml`、四个核心 Maven 模块、`LayeringArchTest`、路由与服务代码、领域端口及基础设施实现编写。本文不声明未经压测验证的 QPS、延迟、可用性或成本收益；运行拓扑、外部组件版本和启用状态以目标环境配置为准。

## 12. 相关文档

- [快速参考](01-logical-architecture-quick-ref.md)

- [架构决策记录](01-logical-architecture-decision.md)

- [商业价值说明](01-logical-architecture-business-value.md)

- [系统架构说明](system-architecture.md)

- [架构图索引](../02-architecture/architecture-index.md)

