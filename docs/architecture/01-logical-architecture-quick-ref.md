# Apex Scaffold DDD 四层逻辑架构快速参考

> 适用基线：Apex Scaffold `1.3.1`，Java 21，Spring Boot 3.5.15

![Apex Scaffold DDD 四层逻辑架构图](../diagrams/01_logical_architecture.svg)

## 核心原则

1. 领域层保存业务语义，不依赖接口层、基础设施层或 MyBatis-Plus。
2. 应用层编排用例，面向领域模型和领域端口编程。
3. 接口层只做协议适配、校验、鉴权和响应转换。
4. 基础设施层实现数据库、缓存、消息、存储、搜索和外部服务端口。
5. WebFlux 流程中的 JDBC、文件和同步 SDK 调用必须隔离到阻塞线程池。

## 模块定位

| 模块                    | 放置内容                                 | 不应放置                           |
| --------------------- | ------------------------------------ | ------------------------------ |
| `apex-system`     | Router、Handler、安全配置、过滤器、OpenAPI      | 核心业务规则、SQL                     |
| `apex-system`    | 应用服务、用例编排、DTO/Assembler              | Mapper、PO、HTTP 协议细节            |
| `apex-domain`         | 聚合、实体、值对象、领域服务、Repository/Port       | Spring Web、MyBatis-Plus、具体 SDK |
| `apex-infrastructure` | RepositoryImpl、Mapper、缓存、MQ、存储、远程适配器 | HTTP Handler、业务流程编排            |
| `apex-common`         | 跨模块通用类型与基础能力                         | 特定业务域规则                        |

## 典型代码定位

| 需求           | 首选位置                                              | 示例                                             |
| ------------ | ------------------------------------------------- | ---------------------------------------------- |
| 新增 HTTP 端点   | `apex-system`                                 | `ApiRouter`、对应 Handler、`RouterOpenApiConfig`   |
| 新增业务用例       | `apex-system`                                | `UserService`、`DeptService`、`WorkflowService`  |
| 新增业务不变量      | `apex-domain`                                     | 聚合、实体、值对象或领域服务                                 |
| 新增数据访问契约     | `apex-domain`                                     | `*Repository` 或技术 Port                         |
| 新增数据库实现      | `apex-infrastructure`                             | `*RepositoryImpl`、Mapper、PO、Converter          |
| 修改数据库结构      | `apex-system/src/main/resources/db/migration` | 新增 Flyway 迁移，不修改已发布迁移                          |
| 新增缓存/存储/消息实现 | `apex-infrastructure`                             | `CacheServiceImpl`、Storage Provider、MQ Adapter |

## 请求调用链

```text
Client
  -> Router
  -> Handler
  -> Application Service
  -> Domain Model / Domain Port
  -> Infrastructure Adapter
  -> Database / Cache / MQ / Storage / External Service
```

依赖方向不是简单地随调用方向向下。基础设施适配器实现领域端口，因此源码依赖指向领域层：

```text
Interfaces -> Application -> Domain <- Infrastructure
```

## 主要技术

| 范围  | 技术                                                         |
| --- | ---------------------------------------------------------- |
| 运行时 | Java 21、Spring Boot 3.5.15、Spring WebFlux、Reactor、Netty    |
| 安全  | Spring Security、JWT、RBAC、租户上下文                             |
| 数据  | MyBatis-Plus、MyBatis、Druid、Dynamic Datasource、MySQL、Flyway |
| 缓存  | Caffeine、Redis Reactive、Redisson                           |
| 集成  | RocketMQ、MinIO、火山云 TOS、Meilisearch、短信/微信适配器                |
| 治理  | Sentinel（system 流控）、Resilience4j（网关熔断）、本地限流算法                             |
| 观测  | Actuator、Micrometer、Prometheus、Zipkin Reporter、结构化日志       |

## Profile 能力矩阵

| Profile      | 默认 | RBAC | 缓存 | 消息 | 监控 | 追踪 |
| ------------ | -: | ---: | -: | -: | -: | -: |
| `basic`      |  否 |    关 |  关 |  关 |  关 |  关 |
| `standard`   |  是 |    开 |  开 |  关 |  开 |  关 |
| `enterprise` |  否 |    开 |  开 |  开 |  开 |  开 |

依赖存在不代表运行时已启用。排障时先确认 Maven Profile、Spring Profile、条件 Bean 和外部服务配置。

## 开发检查清单

- 业务规则是否位于领域层，而不是 Handler 或 RepositoryImpl。

- 应用服务是否只依赖领域端口。

- 基础设施实现是否隐藏了具体 SDK 和持久化模型。

- Router、Handler、权限声明和 OpenAPI 是否同步。

- 阻塞 I/O 是否离开 Netty 事件循环。

- 响应式方法是否错误使用普通 JDBC `@Transactional`。

- Schema 变化是否新增 Flyway 迁移。

- 新能力是否有超时、错误处理、指标和日志。

- 是否通过 ArchUnit 和受影响模块测试。

## 常见误区

| 误区                    | 正确做法                     |
| --------------------- | ------------------------ |
| WebFlux 下所有调用天然非阻塞    | 识别 JDBC/SDK 阻塞点并切换专用调度器  |
| Repository 接口放在基础设施层  | 业务仓储契约放领域层，实现放基础设施层      |
| Handler 直接组合多个 Mapper | Handler 调用应用服务，由应用服务编排用例 |
| 图中组件全部默认启用            | 根据 Profile 和运行配置确认实际能力   |
| Maven 可依赖就代表架构允许导入    | 以 ArchUnit 源码依赖规则和模块边界为准 |

## 相关文档

- [详细说明](01-logical-architecture-detail.md)

- [架构决策记录](01-logical-architecture-decision.md)

- [商业价值说明](01-logical-architecture-business-value.md)

- [原始 SVG](../diagrams/01_logical_architecture.svg)

