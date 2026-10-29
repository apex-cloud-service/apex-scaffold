# 架构设计说明

> 依据持续演进：本仓库正按 `docs/08-project-management/plans/microservice-migration-plan.md` 从「多模块 DDD 单体」改造为「API 网关 + 5 个业务微服务 + 共享 SDK/契约」。截至微服务改造 Phase 1，已新增 `apex-gateway`、`apex-api-contract`、`apex-services-bom` 三个模块（详见下文「微服务共享层」与 `architecture-evolution.md`）。

## 当前物理边界（2026-10-12）

system 的接口、应用、业务领域与专属持久化已迁入 `apex-system`，Java 包名保持不变；启动由 `apex-system-service` 装配。原 `apex-system`、`apex-system` 已退休。共享 domain 保留 BaseEntity、Token/SMS/Event/缓存/远程调用契约；共享 infrastructure 保留通用配置、缓存、MQ、Outbox、审计、加密、邮件及 HTTP 客户端，不反向依赖 system。HTTP 观测数据通过 ApiCallObservation/ApiCallLogSink 桥接，数据库业务实体留在 system。auth-service 的 system 依赖仅用于 test-scope parity 测试，example 编译使用 system 库。

本批 application/domain/infrastructure 共 683 个文件迁入 system（281/180/222；main 560、test 123，含资源）；共享层及兄弟服务 801 个生产 Java 文件无 system 类型的直接导入。构建与测试结果以验收清单的本批验证记录为准。

下文五层模块图为迁移前背景；当前分层存在于 system 库内的原 Java 包中。实际构建模块以根 POM 为准。

## 📐 DDD五层架构（迁移前背景）

Apex Scaffold 采用**领域驱动设计(DDD)**的五层架构模式,确保代码结构清晰、职责分明、易于维护和扩展。

```
┌─────────────────────────────────────────┐
│         apex-system (接口层)         │
│  HTTP端点 | 安全配置 | 过滤器 | 切面      │
└──────────────┬──────────────────────────┘
               │ 调用
┌──────────────▼──────────────────────────┐
│      apex-system (应用层)           │
│  用例编排 | 事务管理 | DTO转换            │
└──────────────┬──────────────────────────┘
               │ 调用
┌──────────────▼──────────────────────────┐
│        apex-domain (领域层)              │
│  聚合根 | 值对象 | 领域服务 | 仓储接口     │
└──────────────┬──────────────────────────┘
               │ 实现
┌──────────────▼──────────────────────────┐
│   apex-infrastructure (基础设施层)        │
│  持久化 | 缓存 | 消息队列 | 外部服务       │
└──────────────┬──────────────────────────┘
               │ 依赖
┌──────────────▼──────────────────────────┐
│        apex-common (共享 SDK)            │
│  工具类 | 异常 | 统一响应 | 上下文 | 常量  │
└─────────────────────────────────────────┘
```

> 注：`apex-common` 在微服务改造后作为 **共享 SDK** 独立复用，其内容受 `apex-common/README.md` 的「SDK 内容准入规则」约束（结果/异常/上下文/注解/纯工具可进，Redis/MQ/Storage 实现与业务实体禁入）。版本由 `apex-services-bom` 统一管理。

## 微服务共享层（Phase 1 新增）

| 模块 | 定位 | 依赖 |
|---|---|---|
| `apex-services-bom` | 微服务统一版本清单（parent 唯一来源）：锁定 `apex-common`/`apex-api-contract`/`apex-license-starter` 及 MyBatis-Plus/Redisson/RocketMQ/Sentinel/Resilience4j 等 | 继承根 `pom.xml`，供网关/契约/各业务服务 parent 引用 |
| `apex-api-contract` | 跨服务内部 API 契约（`/internal/v1/**`）+ Feign 基础设施（`FeignTenantTraceConfiguration`、粉丝回调）+ 阻塞 IO 桥接约定（`BlockingIoSchedulers.remote()`） | `apex-common` + OpenFeign |
| `apex-gateway` | Spring Cloud Gateway（WebFlux）：路由 / JWT(JWKS) 验签 / 用户上下文注入 / 全局限流 / TraceId / CORS / 灰度路由 / 访问日志 | `apex-common` + gateway-webflux + nacos + Resilience4j + redis-reactive |

```
客户端 → apex-gateway ──(lb://apex-auth-service)──→ 认证/各业务微服务
              │ 依赖 + 统一契约
              ▼
   apex-api-contract（Feign 契约）+ apex-common（SDK）
              ▲
              │ parent 统一版本
        apex-services-bom（唯一版本源）
```

三个模块均已在根 `pom.xml` `<modules>` 注册，是微服务改造 Phase 1 的基座，后续 5 个业务服务（auth/system/workflow/office/file）均以此为模板。

### 分层职责

#### apex-common (公共模块)
- **无业务依赖**，可被其他模块自由引用
- 提供通用工具类、异常体系、统一响应格式
- 定义系统常量和枚举

#### apex-domain (领域层)
- **核心业务逻辑**，不依赖任何上层模块
- 定义聚合根、值对象、领域服务
- 定义仓储接口（由基础设施层实现）

#### apex-system (应用层)
- **用例编排**，协调领域对象完成业务用例
- 处理事务边界
- DTO 与领域对象的转换

#### apex-infrastructure (基础设施层)
- **技术实现**，实现领域层定义的接口
- 数据持久化（MyBatis-Plus）
- 缓存、消息队列、外部服务集成
- 提供通用的基础设施组件（缓存、限流、熔断等）

#### apex-system (接口层)
- **协议适配**，将 HTTP 请求转换为应用层调用
- 安全配置（JWT、CORS、Spring Security）
- WebFlux 过滤器链
- AOP 切面（日志、限流、防重放等）
- 全局异常处理

## 核心组件

### 1. 统一响应格式 (Result<T>)

所有 API 接口返回统一的响应格式：

```json
{
  "code": 0,
  "msg": "成功",
  "data": {...},
  "traceId": "abc123"
}
```

### 2. 全局异常处理

通过 `GlobalExceptionHandler` 统一捕获和处理异常，返回标准化的错误响应。

### 3. 多级缓存

- **L1**: Caffeine 本地缓存（进程内）
- **L2**: Redis 分布式缓存
- 支持缓存穿透防护、雪崩防护、布隆过滤器

### 4. 限流熔断

- **Sentinel**: 入口限流、热点参数限流
- **Resilience4j**: 熔断器、重试策略
- 三级容错保障系统稳定性

### 5. 消息队列

- RocketMQ 生产者/消费者封装
- 幂等消费保证
- 发件箱模式可靠投递

### 6. 文件存储抽象

策略模式支持多种存储后端：
- Local (本地文件系统)
- MinIO (对象存储)
- S3 兼容存储

### 7. 监控告警

- **Prometheus**: 采集应用指标
- **Grafana**: 可视化看板
- **Alertmanager**: 告警规则配置

## 安全机制

- JWT Access Token + Refresh Token
- RBAC 基于角色的权限控制
- CORS 跨域配置
- SQL 注入防护（参数化查询）
- XSS 防护（输入验证）
- 防重放攻击（@AntiReplay 注解）
- 并发控制（@ConcurrencyControl 注解）

## 扩展指南

### 添加新的业务模块

1. 在 `apex-domain` 中定义聚合根和仓储接口
2. 在 `apex-infrastructure` 中实现仓储（Mapper + PO + Converter）
3. 在 `apex-system` 中编写应用服务
4. 在 `apex-system` 中创建 Handler 和路由

### 添加新的基础设施组件

1. 在 `apex-infrastructure` 中创建新包
2. 定义接口和实现
3. 通过 Spring 自动配置暴露给应用层使用

### 自定义配置

所有配置项都在 `application.yml` 中，支持通过环境变量覆盖。
