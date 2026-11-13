# 架构设计说明

> 当前运行架构为「API 网关 + 6 个业务微服务（system/auth/workflow/file/office/integration）+ 只读查询聚合服务（apex-aggregation-service，8098）+ 共享 SDK/契约」，另有任务代理（8095）与脱敏服务（8096）。Maven 管理由总根直接聚合七组，再递归聚合现有模块；这不改变服务拓扑、业务调用或生产依赖。

## 当前 Maven 分组（2026-11-13）

根 POM 是总父与总聚合入口，直接声明 shared/components/starters/services/applications/tools/business 七组。第一阶段递归 42 个模块，含根 reactor 43 个项目（8 个 POM、35 个既有 JAR）；六个新 parent 没有业务源码、启动类、端口或可执行 JAR。完整清单见 [README 分组表](../../README.md#maven-分组与父链)，图见[聚合分组](../diagrams/128_maven_module_groups.svg)与[parent 继承](../diagrams/129_maven_parent_inheritance.svg)。

| 聚合组 | 当前范围 | 第一阶段直接 parent |
| --- | --- | --- |
| shared（`apex-shared-parent`） | 9 个共享基础/领域/契约/设施 JAR | 8 个继承 shared；api-contract 保留 services-bom |
| components（`apex-components-parent`） | 6 个脚手架能力库 | 5 个继承 components；integration 保留 services-bom |
| starters（`apex-starters-parent`） | 4 个存储/授权/任务/事务 starter | starters |
| services（`apex-services-bom`） | 10 个运行模块，含八个标准发布服务及 job/masking | 保持 services-bom |
| applications（`apex-applications-parent`） | example、boot-all | example 继承 applications；boot-all 保留 services-bom |
| tools（`apex-tools-parent`） | license-tools、generator、tools-deployment、tools-build | tools |
| business（`apex-business-parent`） | 第一阶段为空，无项目业务子模块 | 后续项目业务 JAR 继承 business |

22 个既有叶子变更直接 parent，原有 13 个 services-bom 子继承保留。七组均直接继承根 POM，统一版本、构建档位和质量门禁仍由根治理，services-bom 保留原有依赖管理；聚合边与继承边不同，不能把 api-contract / integration / boot-all 三个例外误画为已迁移。

**物理与运行边界不变**：所有既有叶子仍在仓库根级，分组通过 `../<叶子>` 聚合，不存在 parent 目录下嵌套源码。35 个既有 JAR 的坐标与打包形态、服务接口、数据库所有权、资源和端口不因分组而变。parent 管理不是 compile 依赖，也不会产生新服务、日志目录或监控目标；本阶段不承诺构建性能提升，验证结果以实际执行记录为准。

business 只承载未来项目专属模型、规则、用例、业务配置和领域适配；通用框架实现由其余支撑模块提供，不复制第二套安全、数据源、缓存、消息或日志框架。业务仓储、Mapper / PO 仍留在本域 infrastructure。独立运行模块应聚合并继承 services-bom，不在 business 重复登记、不自动进入八服务发布集合；后续数量据实际新增模块更新。

根 POM 沿 `<modules>` 递归的后代集合（含空 parent、不含总根）与 `apex-tools-build` 扁平注册表一致；不沿 `<parent>` 回溯。创建步骤见[开发规范](../04-development/development-standards.md#23-maven-分组与模块创建)。构建继续从总根加载固定环境后 `mvnd -pl <叶子列表> -am`。

## 当前物理边界（2026-10-12）

system 的接口、应用、业务领域与专属持久化已迁入 `apex-system`，Java 包名保持不变；启动由 `apex-system-service` 装配。原 `apex-interfaces`、`apex-application` 已退出 reactor。共享 domain 保留 BaseEntity、Token/SMS/Event/缓存/远程调用契约；共享 infrastructure 保留通用配置、缓存、MQ、Outbox、审计、加密、邮件及 HTTP 客户端，不反向依赖 system。HTTP 观测数据通过 ApiCallObservation/ApiCallLogSink 桥接，数据库业务实体留在 system。auth-service 的 system 依赖仅用于 test-scope parity 测试，example 编译使用 system 库。

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

> 注：`apex-common` 在微服务改造后作为 **共享 SDK** 独立复用，其内容受 [SDK 内容准入规则](../../apex-common/README.md#sdk-内容准入规则强制)约束（结果/异常/上下文/注解/纯工具可进，Redis/MQ/Storage 实现与业务实体禁入）。它直接继承 shared parent，版本治理最终来自根 POM；服务仍经 services-bom 获得已有依赖管理。

## 微服务共享层

| 模块 | 定位 | 依赖 |
|---|---|---|
| `apex-services-bom` | 运行模块聚合与父 POM，保留原有服务依赖管理，不是纯 import BOM，也不是全仓唯一版本源 | 继承根；聚合 10 个运行模块，保留它们及三个例外的 13 项直接子继承 |
| `apex-api-contract` | 跨服务内部 API 与 DTO，不放具体业务实体 | 生产依赖 `apex-common`；聚合在 shared，parent 仍为 services-bom |
| `apex-gateway` | Spring Cloud Gateway（WebFlux）：路由 / JWT(JWKS) 验签 / 用户上下文注入 / 全局限流 / TraceId / CORS / 灰度路由 / 访问日志 | `apex-common` + gateway-webflux + nacos + Resilience4j + redis-reactive |

```
客户端 → apex-gateway ──(lb://apex-auth-service)──→ 认证/各业务微服务
共享代码：apex-common（SDK）与 apex-api-contract（内部 API/DTO）按实际 POM 依赖复用
```

以上只描述运行调用和代码复用，不表示 Maven parent 关系；继承使用独立图表达。gateway 聚合在 services、契约聚合在 shared，二者仍继承 services-bom，common 聚合并继承 shared；全部从总根递归纳入 reactor。六个业务服务（auth/system/workflow/office/file/integration）与只读查询聚合服务（aggregation）保留现有装配。服务间同步调用一律使用 `WebClient`，项目不引入 Feign。

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
- 火山 TOS (对象存储)
- 阿里云 OSS (对象存储)
- 七牛云 Kodo (对象存储)

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

1. 先明确是扩展既有脚手架能力库还是新增项目业务 JAR；前者留在所属 components 库，后者聚合并继承 `apex-business-parent`。具体模块另行登记，空 business parent 不等于已有业务实现。
2. 在本业务库的 domain 定义模型、聚合与仓储端口，在本域 infrastructure 实现仓储/Mapper/PO，在 application 编排用例。只把真正跨域稳定契约放共享 domain；不把业务实现放回旧兼容 `apex-infrastructure`。
3. 通用持久化、缓存、消息、远程客户端、安全和日志能力按需复用支撑模块，不在 business 重写；业务专属适配仍属于业务库。
4. 若需要新独立 HTTP 服务，在根级创建运行模块，聚合并继承 services-bom，定向装配业务库，以 Router + Handler 适配用例；不在 business 重复聚合，不自动加入八服务发布集合。只装配到现有应用时不必新增 service。system 与 integration 已有协议层归属不变。
5. 按[模块创建规则](../04-development/development-standards.md#23-maven-分组与模块创建)同步注册表、容器 POM 清单、权限/i18n、迁移与有效文档；从总根加载固定环境后使用 `-pl <叶子> -am` 验证实际依赖与业务边界。

### 添加新的基础设施组件

1. 先选择 shared 组中对应的 `apex-infrastructure-persistence/cache/messaging/remote/integration` 能力模块；`apex-infrastructure` 仍是普通兼容 JAR，不新增实现源码，也不改成 parent。
2. 定义稳定契约和适配实现，不向共享层引入具体业务库的反向依赖。
3. 按现有自动装配模式暴露能力；确需新 Maven 模块时登记到所属支撑分组，不放到 business 孵化第二套框架。

### 自定义配置

所有配置项都在 `application.yml` 中，支持通过环境变量覆盖。
