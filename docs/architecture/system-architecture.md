# Apex Scaffold 系统架构设计文档

> 文档版本: v1.0\
> 创建时间: 2026-09-03\
> 适用版本: apex-scaffold v1.2.0+\
> 架构模式: DDD (领域驱动设计)

> **配套架构图**:
>
> - [i18n 消息处理链路](../diagrams/69_i18n_message_flow.svg)
>
> - [DDD 四层逻辑架构图](../diagrams/01_logical_architecture.svg)
>
> - [网络物理部署架构图](../diagrams/02_physical_architecture.svg)
>
> - [全栈技术选型全景图](../diagrams/03_technical_architecture.svg)
>
> - [部署架构拓扑图](../diagrams/45_deployment_flow.svg)
>
> - [网络架构图](../diagrams/02_physical_architecture.svg)
>
> - [数据流转架构图](../diagrams/62_data_flow_consistency.svg)
>
> - [RocketMQ消息队列架构图](../diagrams/61_message_queue_reliability.svg)
>
> - [监控体系架构图](../diagrams/48_monitoring_configuration.svg)
>
> - [微服务治理架构图](../diagrams/43_spi_plugin_architecture.svg)
>
> - [后端技术栈架构图](../diagrams/29_technology_stack_overview.svg)
>
> - [基础设施组件架构图](../diagrams/46_configuration_hierarchy.svg)
>
> - [技术生态全景图](../diagrams/29_technology_stack_overview.svg)
>
> - [前端技术栈架构图](../diagrams/29_technology_stack_overview.svg)
>
> - [前后端交互架构图](../diagrams/58_api_error_response_flow.svg)
>
> - **[Grafana监控面板架构图](../diagrams/48_monitoring_configuration.svg)** ⭐ NEW
>
> - **[Prometheus监控体系架构图](../diagrams/24_prometheus_grafana_monitoring.svg)** ⭐ NEW

***

## 一、架构概览

Apex Scaffold 采用**领域驱动设计(DDD)思想,结合**响应式编程(Reactive)**和**微服务治理模式,构建高性能、可扩展的企业级应用平台。

### 1.1 核心设计原则

| 原则        | 说明          | 实现方式                             |
| --------- | ----------- | -------------------------------- |
| **分层解耦**  | 清晰的四层架构边界   | 接口层 → 应用层 → 领域层 → 基础设施层          |
| **依赖倒置**  | 高层模块不依赖低层模块 | 领域层定义接口,基础设施层实现                  |
| **响应式优先** | 非阻塞异步编程     | Spring WebFlux + Project Reactor |
| **可观测性**  | 全链路监控追踪     | Prometheus + Grafana + Zipkin    |
| **弹性伸缩**  | 水平扩展能力      | 无状态服务 + Redis共享会话                |
| **安全合规**  | 多租户数据隔离     | tenant\_id + RBAC权限              |

***

## 二、逻辑架构 - DDD四层模型

### 2.1 架构分层

```
┌─────────────────────────────────────────────────────────────┐
│  接口层 (Interfaces Layer)                                   │
│  - REST API (RouterFunction)                                │
│  - Scheduler (定时任务)                                      │
│  - MQ Listener (消息监听)                                    │
│  - CLI (命令行)                                             │
├─────────────────────────────────────────────────────────────┤
│  应用层 (Application Layer)                                  │
│  - 认证授权 (JWT/RBAC/数据权限/租户隔离)                      │
│  - 系统管理 (用户/角色/菜单/部门/字典/配置/定时任务)           │
│  - 监控审计 (登录日志/操作日志/API调用日志)                   │
│  - 工作流引擎 (流程定义/流程实例/任务审批)                     │
├─────────────────────────────────────────────────────────────┤
│  领域层 (Domain Layer)                                       │
│  - 领域模型 (Entity/ValueObject/Aggregate)                  │
│  - 领域服务 (业务规则/领域事件/规格模式)                      │
│  - 仓储接口 (Repository/聚合根操作/查询规约)                  │
│  - 工厂与策略 (Factory/Strategy/Policy)                     │
├─────────────────────────────────────────────────────────────┤
│  基础设施层 (Infrastructure Layer)                           │
│  - 数据持久化 (MyBatis-Plus)                                │
│  - 缓存 (Redis/Caffeine)                                    │
│  - 消息队列 (RocketMQ)                                      │
│  - 文件存储 (MinIO/TOS)                                     │
│  - 第三方服务 (微信/短信)                                    │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 模块职责

#### 2.2.1 接口层 (Interfaces Layer)

**职责**: 对外暴露服务能力,接收请求并路由到应用层

**核心组件**:

| 组件          | 技术实现                              | 端口   | 说明          |
| ----------- | --------------------------------- | ---- | ----------- |
| REST API    | Spring WebFlux RouterFunction     | 8080 | 函数式端点,响应式处理 |
| Scheduler   | Spring @Scheduled + Quartz        | -    | 定时任务调度      |
| MQ Listener | RocketMQ @RocketMQMessageListener | -    | 消息消费监听      |
| CLI         | Spring Shell (可选)                 | -    | 命令行接口       |

**设计要点**:

- ✅ 薄接口层,仅负责参数验证、格式转换、异常处理

- ✅ 统一响应格式 `Result<T>`,包含code/message/data

- ✅ 全局异常处理器 `GlobalExceptionHandler`

- ✅ 请求日志拦截器,记录API调用轨迹

#### 2.2.2 应用层 (Application Layer)

**职责**: 编排业务流程,协调领域服务完成用例

**核心模块**:

**1. 认证授权模块**

- JWT Token生成与验证

- RBAC权限校验 (用户-角色-权限)

- 数据权限拦截器 (部门级/租户级隔离)

- 多租户上下文管理 `TenantContext`

**2. 系统管理模块**

- 用户管理: CRUD、角色分配、部门分配

- 角色管理: CRUD、权限分配、数据范围

- 菜单管理: 树形结构、动态路由、按钮权限

- 部门管理: 树形结构、租户隔离

- 字典管理: 类型-数据两级管理

- 参数配置: 系统参数热更新

- 定时任务: Quartz任务CRUD、执行日志

**3. 监控审计模块**

- 登录日志: 记录登录成功/失败、IP地理位置

- 操作日志: `@OperationLog`注解,AOP切面记录

- API调用日志: WebFilter拦截,记录请求/响应

**4. 工作流引擎模块**

- 流程定义: BPMN建模、流程版本管理

- 流程实例: 启动流程、挂起/激活、终止

- 任务审批: 待办任务、已办任务、审批操作

**设计要点**:

- ✅ 应用服务负责事务边界管理 `@Transactional`

- ✅ DTO与领域模型转换 (使用MapStruct)

- ✅ 防腐层模式,隔离外部API依赖

- ✅ 领域事件发布与订阅

#### 2.2.3 领域层 (Domain Layer)

**职责**: 封装核心业务逻辑,保持领域模型纯粹性

**核心概念**:

**1. 聚合根 (Aggregate Root)**

- `User` 聚合: 用户基本信息、认证信息、组织信息

- `Role` 聚合: 角色信息、权限列表、数据范围

- `Dept` 聚合: 部门信息、上下级关系、租户归属

- `Workflow` 聚合: 流程定义、流程实例、任务节点

**2. 值对象 (Value Object)**

- `LoginCredential`: 登录凭据(用户名/密码)

- `GeoLocation`: 地理位置(国家/省份/城市)

- `AuditInfo`: 审计信息(创建人/创建时间/修改人/修改时间)

**3. 领域服务 (Domain Service)**

- `PermissionChecker`: 权限校验服务

- `DataPermissionFilter`: 数据权限过滤服务

- `TenantIsolationService`: 租户隔离服务

- `WorkflowEngine`: 工作流引擎服务

**4. 仓储接口 (Repository Interface)**

```java
public interface UserRepository {
    Mono<User> findById(Long id);
    Mono<User> findByUsername(String username);
    Flux<User> findByDeptId(Long deptId);
    Mono<User> save(User user);
    Mono<Void> deleteById(Long id);
}
```

**设计要点**:

- ✅ 领域模型充血模型,封装行为方法

- ✅ 仓储接口定义在领域层,实现在基础设施层

- ✅ 领域事件驱动异步解耦

- ✅ 规格模式实现复杂查询条件组合

#### 2.2.4 基础设施层 (Infrastructure Layer)

**职责**: 提供技术支撑,实现领域层定义的接口

**核心组件**:

**1. 数据持久化**

- MyBatis-Plus: ORM框架,Lambda条件构造器

- Druid: 连接池,SQL监控

- Flyway: 数据库版本化迁移

- Dynamic-Datasource: 主从读写分离

**2. 缓存体系**

- L1缓存: Caffeine本地缓存,JVM内存

- L2缓存: Redis分布式缓存,集群模式

- 缓存策略: Cache-Aside模式,先查缓存后查DB

**3. 消息队列**

- RocketMQ: 可靠消息投递,事务消息

- 应用场景: 异步解耦、削峰填谷、事件通知

**4. 文件存储**

- MinIO: 私有化对象存储

- 火山云TOS: 公有云对象存储

- 统一抽象接口 `FileStorageService`

**5. 第三方服务**

- 微信登录: OAuth2授权,OpenID/UnionID

- 短信服务: 创蓝短信API,验证码/通知

- 统一客户端封装 `WebClient` 响应式调用

**设计要点**:

- ✅ 实现领域层定义的Repository接口

- ✅ PO与DO分离,PO仅用于持久化

- ✅ 技术组件可替换,通过配置切换实现

- ✅ 异常转换,将技术异常转换为领域异常

***

## 三、物理架构 - 部署拓扑

### 3.1 部署架构图

```
┌──────────────────────────────────────────────────────────────┐
│  客户端层                                                      │
│  ┌────────────────────┐    ┌────────────────────┐           │
│  │  Web前端(Vue 3)    │    │  移动端(微信小程序) │           │
│  │  apex-admin-web    │    │  WeChat MiniProgram│           │
│  └────────────────────┘    └────────────────────┘           │
└───────────────┬────────────────────┬─────────────────────────┘
                │                    │
┌───────────────┴────────────────────┴─────────────────────────┐
│  网关与负载均衡层                                              │
│  ┌──────────────┐         ┌─────────────────────┐           │
│  │  Nginx/ALB   │   →    │ Spring Cloud Gateway │           │
│  │  负载均衡    │         │  API网关 (可选)      │           │
│  └──────────────┘         └─────────────────────┘           │
└───────────────┬────────────────────────────────────────────┘
                │
┌───────────────┴────────────────────────────────────────────┐
│  应用服务层 (无状态,可水平扩展)                              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                │
│  │ Apex     │  │ Apex     │  │ Apex     │  ...           │
│  │ Instance1│  │ Instance2│  │ InstanceN│                │
│  │ :8080    │  │ :8080    │  │ :8080    │                │
│  └──────────┘  └──────────┘  └──────────┘                │
└───────┬───────────────┬───────────────────────────────────┘
        │               │
        ├───────────────┼───────────────────────────────────┐
        │               │                                   │
┌───────┴───────┐  ┌────┴────────┐  ┌────────────────────┴─┐
│ 数据存储层     │  │ 微服务治理层 │  │ 可观测性平台         │
│ ┌──────────┐ │  │ ┌─────────┐ │  │ ┌─────────────────┐ │
│ │ MySQL 8  │ │  │ │ Nacos   │ │  │ │ Prometheus       │ │
│ │ 主从复制 │ │  │ │ :8848   │ │  │ │ :9090            │ │
│ └──────────┘ │  │ └─────────┘ │  │ └─────────────────┘ │
│ ┌──────────┐ │  │ ┌─────────┐ │  │ ┌─────────────────┐ │
│ │ Redis    │ │  │ │ Sentinel│ │  │ │ Grafana          │ │
│ │ Cluster  │ │  │ │ :8858   │ │  │ │ :3000            │ │
│ └──────────┘ │  │ └─────────┘ │  │ └─────────────────┘ │
│ ┌──────────┐ │  │ ┌─────────┐ │  │ ┌─────────────────┐ │
│ │ MinIO    │ │  │ │RocketMQ │ │  │ │ Zipkin           │ │
│ │ TOS      │ │  │ │ :9876   │ │  │ │ :9411            │ │
│ └──────────┘ │  │ └─────────┘ │  │ └─────────────────┘ │
└───────────────┘  └─────────────┘  └──────────────────────┘
```

### 3.2 组件职责与端口

| 组件             | 用途                     | 端口                  | 部署方式               |
| -------------- | ---------------------- | ------------------- | ------------------ |
| **应用服务**       | apex-scaffold业务应用      | 8080(业务) / 8081(管理) | Docker / K8s       |
| **MySQL**      | 关系型数据库,主从复制            | 3306                | 独立部署 / RDS         |
| **Redis**      | 缓存与会话,Cluster/Sentinel | 6379                | 独立部署 / ElastiCache |
| **Nacos**      | 配置中心与服务发现              | 8848                | Docker / K8s       |
| **Sentinel**   | 流控熔断Dashboard          | 8858                | Docker             |
| **RocketMQ**   | 消息队列                   | 9876(NameServer)    | Docker / K8s       |
| **Prometheus** | 指标采集                   | 9090                | Docker             |
| **Grafana**    | 监控大盘                   | 3000                | Docker             |
| **Zipkin**     | 链路追踪                   | 9411                | Docker             |

### 3.3 网络拓扑

**生产环境网络规划**:

| 网络分区     | 组件                        | 访问策略           |
| -------- | ------------------------- | -------------- |
| **DMZ区** | Nginx/ALB                 | 公网暴露,HTTPS 443 |
| **应用区**  | apex-scaffold实例           | 仅DMZ可访问        |
| **数据区**  | MySQL/Redis               | 仅应用区可访问        |
| **中间件区** | Nacos/Sentinel/RocketMQ   | 仅应用区可访问        |
| **监控区**  | Prometheus/Grafana/Zipkin | 管理员VPN访问       |

**安全策略**:

- ✅ 数据库禁止公网访问,仅内网IP白名单

- ✅ Redis设置密码认证

- ✅ 应用服务间通信使用HTTPS

- ✅ 敏感配置存储在Nacos加密配置

***

## 四、技术架构 - 技术栈全景

### 4.1 前端技术栈

| 分类          | 技术           | 版本     | 说明                  |
| ----------- | ------------ | ------ | ------------------- |
| **核心框架**    | Vue          | 3.5.13 | Composition API,响应式 |
| **构建工具**    | Vite         | 6.0.7  | 极速冷启动,HMR           |
| **类型系统**    | TypeScript   | 5.x    | 类型安全,智能提示           |
| **UI组件库**   | Element Plus | 2.x    | 企业级UI组件             |
| **状态管理**    | Pinia        | 2.x    | Vue官方推荐             |
| **HTTP客户端** | Axios        | 1.x    | Promise based       |
| **路由**      | Vue Router   | 4.x    | 动态路由,守卫             |

### 4.2 后端技术栈

#### 4.2.1 核心框架

| 技术                  | 版本             | 说明         |
| ------------------- | -------------- | ---------- |
| **Java**            | 17             | LTS长期支持版本  |
| **Spring Boot**     | 3.5.15         | 最新稳定版      |
| **Spring WebFlux**  | Reactor 3.6.13 | 响应式编程      |
| **Spring Security** | 6.5.0          | JWT+RBAC权限 |
| **Netty**           | 4.x            | 底层IO运行时    |

#### 4.2.2 数据访问

| 技术               | 版本       | 说明                      |
| ---------------- | -------- | ----------------------- |
| **MyBatis-Plus** | 3.5.10.1 | ORM框架,代码生成              |
| **Druid**        | 1.2.25   | 连接池+SQL监控               |
| **Flyway**       | 10.15.0  | 数据库版本化迁移(V1-V22, 22个脚本) |
| **MySQL**        | 8.4.0    | 关系型数据库(Connector 8.4.0) |
| **Redis**        | 7.x      | KV缓存,Redisson 3.36.0    |
| **Caffeine**     | 3.1.8    | 本地缓存                    |
| **Meilisearch**  | 1.10     | 轻量级全文搜索引擎               |

#### 4.2.3 微服务治理

| 技术               | 版本    | 说明        |
| ---------------- | ----- | --------- |
| **Nacos**        | 2.4.3 | 配置中心+服务发现 |
| **Sentinel**     | 1.8.8 | 流控熔断降级    |
| **Resilience4j** | 2.2.0 | 熔断器模式     |
| **RocketMQ**     | 5.3.1 | 消息队列,事务消息 |
| **Seata**        | 2.2.0 | 分布式事务(可选) |

#### 4.2.4 可观测性

| 技术                | 版本     | 说明                               |
| ----------------- | ------ | -------------------------------- |
| **Micrometer**    | 1.14.2 | 指标门面,统一抽象                        |
| **Prometheus**    | 2.54.1 | 时序数据库,指标采集                       |
| **Grafana**       | 11.2.0 | 监控大盘,可视化                         |
| **Grafana Loki**  | 3.0.0  | 日志聚合系统,轻量级Elasticsearch替代        |
| **Grafana Alloy** | 1.0.0  | OpenTelemetry Collector,统一遥测数据收集 |
| **Zipkin**        | 2.27.4 | 链路追踪,Span收集                      |
| **Logback**       | 1.5.12 | 日志框架,异步Appender                  |

#### 4.2.5 对象存储

| 技术                 | 版本     | 说明             |
| ------------------ | ------ | -------------- |
| **MinIO**          | 8.5.15 | 自托管对象存储,S3兼容   |
| **Volcengine TOS** | 2.6.1  | 火山引擎对象存储,云原生方案 |

#### 4.2.6 安全与认证

| 技术         | 说明          |
| ---------- | ----------- |
| **JWT**    | Token认证,无状态 |
| **BCrypt** | 密码加密,防彩虹表   |
| **RBAC**   | 角色权限模型      |
| **数据权限**   | 部门级/租户级隔离   |

#### 4.2.7 API文档

| 技术            | 版本    | 说明            |
| ------------- | ----- | ------------- |
| **SpringDoc** | 2.7.0 | OpenAPI 3.0规范 |
| **Knife4j**   | 4.5.0 | Swagger增强UI   |

#### 4.2.8 测试框架

| 技术                 | 版本     | 说明           |
| ------------------ | ------ | ------------ |
| **JUnit**          | 5.11.3 | 单元测试框架       |
| **Mockito**        | 5.14.2 | Mock框架       |
| **TestContainers** | 1.20.4 | 容器化集成测试      |
| **WebTestClient**  | -      | WebFlux响应式测试 |

#### 4.2.9 构建与部署

| 技术                 | 版本    | 说明        |
| ------------------ | ----- | --------- |
| **Maven**          | 3.9.x | 构建工具,依赖管理 |
| **Docker**         | 24.x  | 容器化       |
| **Docker Compose** | 2.x   | 本地编排      |
| **Kubernetes**     | 1.28+ | 生产编排(可选)  |
| **GitHub Actions** | -     | CI/CD流水线  |

### 4.3 技术选型理由

| 技术决策                      | 理由                                         |
| ------------------------- | ------------------------------------------ |
| **WebFlux而非WebMVC**       | 高并发场景下非阻塞性能更优,适合IO密集型应用                    |
| **MyBatis-Plus而非JPA**     | 更灵活的SQL控制,Lambda条件构造器易用性高                  |
| **Sentinel+Resilience4j** | Sentinel提供Dashboard可视化,Resilience4j提供熔断器模式 |
| **Nacos而非Config Server**  | 同时提供配置中心和服务发现,运维成本低                        |
| **RocketMQ而非Kafka**       | 事务消息支持,更适合业务消息场景                           |
| **Prometheus+Grafana**    | 云原生监控标准,社区生态丰富                             |

***

## 五、架构演进路线

### 5.1 当前架构 (v2.5.0)

- ✅ 单体应用 + DDD分层

- ✅ 响应式编程 (WebFlux)

- ✅ 微服务治理组件集成 (Nacos/Sentinel)

- ✅ 完善的监控体系 (66个Grafana面板)

### 5.2 近期优化 (v2.6.0 - v3.0.0)

**性能优化**:

- [ ] 引入Caffeine二级缓存,减少Redis访问

- [ ] SQL慢查询优化,索引调优

- [ ] JVM参数调优,GC优化

**功能增强**:

- [ ] 集成Elasticsearch全文检索

- [ ] 工作流引擎BPMN 2.0支持

- [ ] 多语言国际化完善

**运维增强**:

- [ ] Kubernetes部署清单

- [ ] Helm Chart打包

- [ ] 金丝雀发布支持

### 5.3 长期规划 (v3.0.0+)

**微服务拆分** (按需):

- 用户中心服务

- 权限管理服务

- 工作流服务

- 文件服务

**技术升级**:

- Java 21 (虚拟线程)

- Spring Boot 4.x

- GraalVM Native Image

**云原生演进**:

- Service Mesh (Istio)

- Serverless部署 (Knative)

- 多云适配 (AWS/阿里云/腾讯云)

***

## 六、架构决策记录 (ADR)

正式编号以[逻辑架构 ADR-001](01-logical-architecture-decision.md)、[部署 ADR-002–008](02-physical-architecture-decision.md)及[技术 ADR-009–015](03-technical-architecture-decision.md)为准。下列旧决策摘要不再复用冲突编号，历史性能结果未在本轮重新验证。

### ADR-AUTH-CENTER-001：独立认证中心的远程授权集成

**状态**：方案已批准；当前代码具备渐进授权接入，部署与集成验收待执行。

**决策**：主应用与 `AuthCenterApplication` 保持独立 Spring ApplicationContext，主应用扫描排除 authcenter 包。共享基础类型，不直接调用中心内部仓储或 Mapper。主应用身份认证继续使用原有 JWT 链，授权数据通过领域端口 `UnifiedAuthorizationGateway` 与 HTTPS 适配器接入中心。

**实现边界**：`apex.features.auth-center-permission` 默认关闭。开启后以可信主租户/用户解析服务端唯一映射，使用专用读取账号 ACCESS Token 查询 `/auth/v1/authorization/users/{userId}`；中心租户来自 Token，中心检查读取账号和目标账号的状态。客户端验证响应身份并转换回主应用身份；映射 SELF、ALL、DEPT、DEPT_AND_CHILD 数据范围，CUSTOM、空值和未知范围拒绝。

**取舍**：3 秒请求超时与默认熔断器限制远程故障影响；错误拒绝授权。缓存默认关闭，可配置 0–30 秒，存在 TTL 撤权延迟；没有过期缓存降级或本地权限回退。专用凭据由部署侧轮换，适配器不自动刷新。跨实例主动失效、生产 sys_* 数据迁移和整体认证迁移仍需独立推进，不能登记为已完成。

**迁移资源**：认证中心当前激活 `classpath:db/migration/mysql`；未激活的 `db/migration/auth/*/V51` 重复脚本已从当前工作区移除，不表示数据库迁移已执行。

**验证依据**：[配置说明](../05-operations/configuration.md#认证中心统一授权)、[登录与授权时序](business-flows.md)、[验证矩阵](../../apex-auth-center/TESTING.md)。本 ADR 的状态不代表测试通过或已部署。

### 历史决策摘要：采用 Spring WebFlux（正式编号 ADR-009）

**决策日期**: 2024-Q2\
**状态**: 已采纳 ✅

**背景**:\
传统Servlet容器(Tomcat)基于线程池模型,每个请求占用一个线程,在高并发IO密集场景下线程资源受限。

**决策**:\
采用Spring WebFlux + Netty响应式框架,非阻塞异步处理请求。

**结果**:

- ✅ 单实例并发能力从1000 QPS提升到5000 QPS

- ✅ 平均响应时间从200ms降低到80ms

- ⚠️ 学习曲线陡峭,需要团队培训

***

### 历史决策摘要：采用 DDD（正式编号 ADR-001）

**决策日期**: 2024-Q3\
**状态**: 已采纳 ✅

**背景**:\
传统MVC三层架构在业务复杂度增长后,Service层过于臃肿,业务逻辑分散。

**决策**:\
采用DDD四层架构,明确接口层、应用层、领域层、基础设施层职责。

**结果**:

- ✅ 业务逻辑内聚在领域层,可测试性提升

- ✅ 领域模型充血,封装行为而非贫血模型

- ✅ 仓储模式实现存储技术解耦

- ⚠️ 初期开发效率略有下降,但长期可维护性增强

***

### 历史决策摘要：多租户 tenant_id 字段隔离

**决策日期**: 2024-Q4\
**状态**: 已采纳 ✅

**背景**:\
SaaS场景需要支持多租户,保证数据隔离与安全。

**决策**:\
采用共享数据库、共享Schema、tenant\_id字段隔离方案。

**方案对比**:

| 方案            | 优点          | 缺点       | 选择 |
| ------------- | ----------- | -------- | -- |
| 独立数据库         | 隔离性最强,可独立备份 | 成本高,运维复杂 | ❌  |
| 独立Schema      | 隔离性强,扩展灵活   | 连接池管理复杂  | ❌  |
| 共享表tenant\_id | 成本低,易扩展     | 需严格SQL审查 | ✅  |

**结果**:

- ✅ 单库支持1000+租户

- ✅ 运维成本低,仅需一套数据库

- ✅ 通过MyBatis-Plus拦截器自动注入tenant\_id

- ⚠️ 需要代码review保证tenant\_id不遗漏

***

## 七、非功能性需求

### 7.1 性能指标

| 指标        | 目标值         | 实际值      |
| --------- | ----------- | -------- |
| **QPS**   | 5000 QPS/实例 | 5200 QPS |
| **P95延迟** | < 200ms     | 180ms    |
| **P99延迟** | < 500ms     | 450ms    |
| **可用性**   | 99.9%       | 99.92%   |

### 7.2 扩展性

- ✅ 应用服务无状态,支持水平扩展

- ✅ 数据库读写分离,主从复制

- ✅ Redis Cluster模式,支持水平扩展

- ✅ RocketMQ分布式消息队列

### 7.3 安全性

- ✅ HTTPS全链路加密

- ✅ JWT Token认证,无状态

- ✅ 密码BCrypt加密,防彩虹表

- ✅ SQL注入防护 (MyBatis预编译)

- ✅ XSS防护 (前端转义)

- ✅ CSRF防护 (Token验证)

### 7.4 可维护性

- ✅ 代码分层清晰,职责单一

- ✅ 单元测试覆盖率 > 70%

- ✅ API文档自动生成 (OpenAPI 3)

- ✅ 日志结构化,便于检索

- ✅ 监控告警完善 (66个Grafana面板)

***

## 八、参考资料

### 8.1 内部文档

- [前后端API路由对比清单](../_archive/api/route-mapping.md)

- [配置文件完整性检查报告](../_archive/operations/configuration.md)

- [Grafana监控面板增强方案](../_archive/operations/monitoring.md)

### 8.2 外部参考

- [Spring Boot官方文档](https://docs.spring.io/spring-boot/docs/current/reference/html/)

- [Spring WebFlux官方文档](https://docs.spring.io/spring-framework/reference/web/webflux.html)

- [DDD领域驱动设计](https://domain-driven-design.org/)

- [12-Factor App](https://12factor.net/)

- [云原生应用架构实践](https://www.cncf.io/)

***

**文档维护者**: 架构团队\
**审核状态**: 待人工审核\
**下次更新**: 架构演进后同步更新
