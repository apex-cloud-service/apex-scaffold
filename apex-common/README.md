# Apex Common（共享 SDK）

> 依据：`docs/08-project-management/plans/microservice-migration-plan.md` §4.1、`.trae/documents/microservice-migration-execution-plan.md` §1.1.3
> 版本：1.3.1（微服务改造 Phase 1）
> 维护：Apex Team

## 模块定位

`apex-common` 是微服务改造后的**共享 SDK**，被网关、各业务微服务、契约模块共同依赖。它承载跨服务复用的**抽象能力**（结果模型、异常体系、上下文、注解、工具类），不承载任何**实现型基础设施**或**业务实体**。

> 说明：方案 §4.1 允许保留 `artifactId = apex-common`（不改名 `apex-common-sdk`），本期维持现状，仅通过内容准入规则约束其边界。

## SDK 内容准入规则（强制）

为保证 SDK 可被所有服务安全复用、且不引入循环依赖或环境耦合，**只有以下类别允许进入本模块**：

| 允许进入（✅） | 说明 |
|---|---|
| 结果模型 | `result/Result`、`result/ResultCode`、分页 `dto/PageRequest`、`dto/PageResponse` |
| 异常体系 | `exception/BaseException`、`BusinessException`、`ForbiddenException`、`error/ErrorCode` |
| 上下文 | `context/TenantContext`、`DataSourceContext`、`DataScopeContext` 等 ThreadLocal / Reactor Context 载体 |
| 常量 | `constant/SystemConstants`、`CacheConstants` |
| 注解 | `annotation/**`（`@RequirePermission`、`@IdempotentApi`、`@RateLimiter` 等声明式注解） |
| 纯工具 | `util/**`（无外部 IO 副作用的编解码/时间/脱敏/ID 工具，如 `JsonUtil`、`DateUtil`、`TraceIdUtil`、`SnowflakeIdGenerator`） |
| 领域事件基类 | `event/AbstractDomainEvent`、`event/BaseDomainEvent`（仅抽象，不含具体业务事件） |
| SPI 契约 | `spi/StorageProvider` 等**接口**（实现必须留在 `apex-infrastructure` 或对应服务） |
| 线程调度约定 | `scheduler/BlockingIoSchedulers`（阻塞 IO 桥接线程池约定） |
| 枚举/DTO | 无状态、无持久化映射的通用枚举与传输对象 |

## 禁止进入（❌）

| 禁止进入（❌） | 原因 |
|---|---|
| Redis / Redisson / 缓存实现 | 引入运行时中间件耦合，应由 `apex-infrastructure` 或服务本地实现 |
| MQ / RocketMQ 生产者消费者实现 | 同上，且易造成依赖收敛困难 |
| 对象存储实现（Local/MinIO/TOS） | 实现下沉到 `apex-infrastructure`/文件服务，SDK 只保留 `StorageProvider` 接口 |
| 数据库访问（Mapper / MyBatis-Plus 实体 / SQL） | 破坏服务独立库边界，禁止跨服务共享持久化模型 |
| 业务实体 / 聚合根 | 领域模型应归属各自服务，禁止在 SDK 中重复定义 |
| 具体业务 Service / Router / Controller | 属服务内部实现，SDK 不承载业务逻辑 |
| 第三方 SDK 直连（微信/短信/办公平台等） | 属基础设施适配，下沉到对应服务或 `apex-infrastructure` |

## 发版与依赖约定

- 本模块版本由根 `pom.xml` 统一管理（`${project.version}`）。
- 本模块直接继承 `apex-shared-parent`，经它继承根 POM 的版本与构建配置；目录与 JAR 坐标不变，见 [当前分组说明](../README.md#maven-分组与父链)。
- 网关、契约、各业务微服务仍通过 `apex-services-bom` 的现有依赖管理引用本模块，**禁止**在服务内自行指定 SDK 版本。契约聚合在 shared，但其直接 parent 保持 services-bom；聚合不等于继承或生产依赖。
- 安装或未来发布 Maven 构件须同时提供根与所需分组 parent POM，不可仅发布子 JAR/POM 后留下缺失继承链。此约定不授权远程发布。
- 任何向本模块新增内容，必须先对照上表评估；触及「禁止进入」项应改放对应服务或 `apex-infrastructure`。

## 包结构

```
com.apex.scaffold.common
├── annotation     声明式注解
├── audit          审计抽象
├── config         通用配置属性（JwtProperties、RedissonProperties 等）
├── constant       常量（SystemConstants、CacheConstants）
├── context        租户/数据源/数据权限上下文
├── dto            通用传输对象（分页等）
├── enums          通用枚举
├── error          错误码定义
├── event          领域事件基类
├── exception      异常体系
├── governance     优雅下线契约（DrainParticipant）
├── id             ID 生成约定
├── persistence    PO 基类与查询工具
├── result         统一返回结果
├── scheduler      阻塞 IO 调度约定
├── security       加密/脱敏抽象
├── spi            可插拔 SPI 接口
├── state          状态机抽象
└── util           纯工具类
```

## 相关文档

- 架构总览：`docs/02-architecture/architecture-overview.md`
- 迁移方案：`docs/08-project-management/plans/microservice-migration-plan.md`
- 开发规范：`docs/04-development/development-standards.md`
