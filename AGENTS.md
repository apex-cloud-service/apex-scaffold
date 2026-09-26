# AGENTS.md — Apex Scaffold

本文件定义本仓库的编码、验证和提交约定。修改前先检查实际代码和配置，遵循现有模式；不要为了满足规则进行无关重构。

## 项目结构

Apex Scaffold 是 Java 17、Spring Boot 3.5.15、Spring WebFlux 的多模块项目。

- **Java 版本**：由 `.sdkmanrc` 指定（当前 `17.0.12-oracle`），使用 SDKMAN 管理，禁止手动修改 `JAVA_HOME` 或使用其他 JDK 版本。
- **构建工具**：使用 Maven Daemon（mvnd）而非 Maven（mvn），版本由 `.sdkmanrc` 指定（当前 `2.0.0-rc-3`），所有构建命令必须使用 `mvnd`。
- **项目版本与依赖**：以根 [pom.xml](pom.xml) 为准。

根 POM 当前包含：

- `apex-common`：公共结果、异常、注解、常量、上下文、工具和调度器。
- `apex-domain`：领域模型、领域服务、领域事件和仓储接口。
- `apex-application`：用例编排、应用服务、事务边界和 DTO 转换。
- `apex-infrastructure`：仓储、MyBatis-Plus、缓存、消息、存储和外部服务适配器。
- `apex-interfaces`：WebFlux 路由、Handler、安全、过滤器、配置、OpenAPI、i18n、Flyway 和启动入口。
- `apex-example`：示例应用和测试。
- `apex-generator`：独立的 DDD 代码生成器。
- `apex-license-spring-boot-starter`、`apex-license-tools`：授权相关模块，涉及发行时先确认商业代码隔离要求。

依赖方向保持为：

```text
interfaces → application → domain ← infrastructure
                  common  ← 各模块按需依赖
```

`domain` 不得依赖 application、interfaces 或 infrastructure。修改跨层代码时运行已有架构测试。

## 实现约定

### WebFlux 函数式路由与 Handler

**核心原则**：前后端严格分离，后端只提供 RESTful API，不涉及前端路由、视图或组件配置。

**接口实现规范**：
- 所有 HTTP 接口必须使用函数式 WebFlux Router + Handler 模式，禁止使用 `@Controller`、`@RestController`、`@RequestMapping`、`@GetMapping`、`@PostMapping` 等注解式编程。
- Router 类位于 `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/router/` 包，负责定义路由规则。
- Handler 类位于 `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/handler/` 包，负责处理业务逻辑。
- 新增接口必须完成：
  1. 创建 Request/Response DTO（位于 `interfaces/dto/` 包）
  2. 在 Handler 中实现业务逻辑（调用 Application 层 Service）
  3. 在 Router 中注册路由（使用 `RouterFunctions.route()`）
  4. 添加完整的 SpringDoc 注解（确保 Swagger 文档完整）
  5. 使用 `RequestHandlerUtil.validateAndMap()` 进行参数校验

**Router 实现模板**：
```java
@Configuration
public class XxxRouter {
    @Bean
    @RouterOperations({
        @RouterOperation(
            path = "/api/v1/xxx/{id}",
            method = RequestMethod.GET,
            beanClass = XxxHandler.class,
            beanMethod = "getById",
            operation = @Operation(
                operationId = "getById",
                summary = "根据 ID 查询",
                description = "根据 ID 查询详细信息",
                tags = {"业务模块"},
                parameters = {
                    @Parameter(name = "id", description = "记录 ID", 
                               required = true, in = ParameterIn.PATH,
                               schema = @Schema(type = "string"))
                },
                responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功",
                                 content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                }
            )
        )
    })
    public RouterFunction<ServerResponse> xxxRoutes(XxxHandler handler) {
        return RouterFunctions.route()
            .path("/api/v1/xxx", builder -> builder
                .GET("/{id}", handler::getById)
                .POST("", accept(MediaType.APPLICATION_JSON), handler::create))
            .build();
    }
}
```

**Handler 实现规范**：
- Handler 方法必须添加详细的中文 JavaDoc，说明接口功能、业务逻辑、参数和返回值。
- 使用构造器注入依赖（配合 `@RequiredArgsConstructor`），禁止使用 `@Autowired` 字段注入。
- 所有写操作必须添加 `@OperationLog` 注解记录操作日志。
- 敏感操作必须添加权限校验注解（`@RequirePermission`）和数据权限注解（`@DataScope`）。
- 返回值统一使用 `Result<T>` 封装，通过 `buildSuccess()` 或 `buildError()` 构建响应。

**SpringDoc/Swagger 注解规范**：
- Router 方法必须使用 `@RouterOperation` 或 `@RouterOperations` 定义接口元数据。
- Request DTO 使用 `@Schema` 注解标注类和字段说明，必填字段使用 `required = true`。
- Response DTO 使用 `@Schema` 注解标注类和字段说明，包含示例值（`example = "..."`）。
- 接口参数使用 `@Parameter` 注解说明参数用途、类型、约束和位置（PATH/QUERY/HEADER）。
- 使用 `@Operation` 注解说明接口摘要（`summary`）、描述（`description`）、标签（`tags`）和响应状态码。

**阻塞 I/O 隔离**：
- JDBC、MyBatis-Plus、同步 Redis、文件 I/O 和同步 HTTP 客户端都是阻塞操作，必须使用项目已有的 `BlockingIoSchedulers` 隔离。
- **严禁直接使用 `Schedulers.boundedElastic()`**，必须使用 `BlockingIoSchedulers` 提供的语义明确的专用调度器：
  - `BlockingIoSchedulers.jdbc()`：JDBC、MyBatis-Plus 数据库操作
  - `BlockingIoSchedulers.cache()`：Redisson 同步 API、分布式锁
  - `BlockingIoSchedulers.storage()`：MinIO、TOS 对象存储、本地文件 I/O
  - `BlockingIoSchedulers.event()`：Outbox 写入、API 调用日志入库
  - `BlockingIoSchedulers.remote()`：外部 SDK、第三方 HTTP 同步调用（LDAP、钉钉、飞书、企业微信等）
  - `BlockingIoSchedulers.general()`：启动预热、后台批量任务、运维脚本
- Handler 层的请求处理链中不得直接调用 `.block()`。
- 消息监听器只有在现有同步消费边界明确允许时才能使用阻塞桥接。

**响应式编程最佳实践**：
- 优先使用 `Mono`、`Flux`、`Mono.defer`、`Mono.fromCallable` 和 `Mono.zip`。
- 远程调用应设置超时，并按业务语义处理重试、降级和取消。
- 使用 `subscribeOn()` 指定阻塞操作的调度器，使用 `publishOn()` 切换后续操作的线程。
- 避免在响应式链中使用阻塞代码，必须通过 `Mono.fromCallable()` 或 `Mono.defer()` 包装并调度到专用线程池。

### DDD 分层架构与开发顺序

**核心原则**：严格遵循 DDD 分层依赖关系，确保领域层纯粹性和业务逻辑内聚。

**新增业务实现顺序**：

1. **Domain 层**（`apex-domain`）：
   - 定义聚合根（Aggregate Root）、实体（Entity）、值对象（Value Object）
   - 编写领域服务（Domain Service）处理核心业务逻辑
   - 定义领域事件（Domain Event）用于跨聚合通信
   - 定义仓储接口（Repository Interface），但不实现
   - **禁止依赖**：不得引入 Spring、MyBatis、Redis 等基础设施依赖

2. **Infrastructure 层**（`apex-infrastructure`）：
   - 创建持久化对象（PO）对应数据库表结构
   - 编写 MyBatis-Plus Mapper 接口（继承 `BaseMapper<PO>`）
   - 实现 Repository 接口（使用 MapStruct Converter 进行 PO ↔ 实体转换）
   - 实现外部服务适配器（如第三方 API 调用、对象存储、消息队列）
   - 阻塞操作必须使用 `BlockingIoSchedulers` 隔离

3. **Application 层**（`apex-application`）：
   - 编写应用服务（Application Service）编排用例流程
   - 定义事务边界（使用 `TransactionalOperator` 或 `@Transactional`）
   - 创建 DTO（Data Transfer Object）用于跨层数据传输
   - 使用 MapStruct Assembler 实现 DTO ↔ 领域实体转换
   - 处理领域事件监听和发布

4. **Interfaces 层**（`apex-interfaces`）：
   - 创建 Request/Response DTO（API 契约层）
   - 编写 Handler 调用 Application 层服务
   - 在 Router 中注册路由并添加 SpringDoc 注解
   - 添加安全校验（`@RequirePermission`、`@DataScope`）
   - 配置统一异常处理和响应转换

5. **数据库迁移**（Flyway）：
   - 在 `apex-interfaces/src/main/resources/db/migration/` 按数据库类型（`mysql`/`postgresql`）追加脚本
   - 禁止修改已执行的迁移脚本（版本号不可回退或重复）

**数据库迁移规范**：
- Flyway 脚本命名：`V{版本号}__{描述}.sql`（如 `V28__add_job_query_menu_and_permissions.sql`）
- 版本号必须递增，不得重复或回退已执行的版本号
- 脚本必须幂等，支持重复执行不报错
- MySQL 和 PostgreSQL 脚本需分别维护，确保语法兼容性
- 菜单、权限变更必须同步更新 Flyway 种子数据

**租户隔离规范**：
- 租户字段、数据权限、乐观锁、逻辑删除和拦截器顺序必须与现有实现一致
- 消费者、缓存键和异步任务的业务标识必须包含租户作用域（使用 `TenantContext.currentTenantId()`）
- 不能让外部请求覆盖服务端租户上下文
- 更新和删除操作必须校验租户权限，防止跨租户数据访问

**事务管理规范**：
- 事务边界应位于 Application 层，Service 方法使用 `@Transactional` 注解
- WebFlux 环境需使用 `TransactionalOperator` 而非声明式 `@Transactional`
- 避免事务方法中包含远程调用、长时间阻塞操作
- 乐观锁冲突使用 `@OptimisticLockRetry` 注解自动重试

**时间字段使用规范**：
- MySQL 数据库必须使用 `DATETIME` 类型，**严禁使用 `TIMESTAMP` 类型**（后者范围限制为 1970-2038，存在 Y2038 问题）
- PostgreSQL 数据库使用 `TIMESTAMP` 类型（默认 64 位，范围到 294276 AD，无 2038 年限制）
- Java 实体类统一使用 `LocalDateTime` 类型，**严禁使用 `Integer` 或 32 位 `int` 存储秒级时间戳**
- 时间字段默认值统一使用 `CURRENT_TIMESTAMP`，支持自动更新使用 `ON UPDATE CURRENT_TIMESTAMP`（MySQL）
- **严禁使用硬编码未来日期**（如 `'2099-12-31'`、`'9999-12-31'`）表示永久有效，应使用 `NULL` 或布尔标志字段
- 标准时间字段命名：`create_time`、`update_time`、`deleted_at`（软删除）、`expire_at`（过期时间）
- MyBatis-Plus 使用 `@TableField` 的 `fill` 属性实现时间字段自动填充：
  - `@TableField(value = "create_time", fill = FieldFill.INSERT)`
  - `@TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)`

### 统一响应与异常处理

**响应契约**：
- 所有接口统一使用 `Result<T>` 封装响应，包含以下字段：
  - `code`：业务状态码（整数，200 表示成功）
  - `msg`：提示消息（支持国际化）
  - `data`：业务数据（泛型 T）
  - `traceId`：链路追踪 ID（用于问题排查）
- Handler 中使用 `buildSuccess(data)` 或 `buildError(ResultCode, traceId)` 构建响应
- 禁止直接返回裸对象或自定义响应格式，破坏前后端契约

**错误码管理**：
- 错误码集中维护在 `apex-common` 模块的 `ResultCode` 枚举类
- 每个错误码包含：
  - 唯一的整数编码（如 `40001`）
  - 国际化消息 key（如 `error.user.not.found`）
  - 中文默认消息（作为降级方案）
- 新增错误码时必须同步更新 `i18n` 资源文件（支持 zh_CN、en_US、zh_TW、th_TH）
- 禁止在代码中散落魔法数字或硬编码错误消息

**异常处理规范**：
- 业务异常统一使用 `BusinessException`，构造时传入 `ResultCode` 枚举
- 技术异常（如 `DataAccessException`、`TimeoutException`）在 `GlobalExceptionHandler` 统一捕获，转换为标准 `Result` 响应
- 参数校验异常（`MethodArgumentNotValidException`）自动转换为 400 错误，错误信息支持国际化
- WebFlux 响应式异常使用 `onErrorResume()` 或 `onErrorReturn()` 处理，避免异常传播到最外层
- 日志记录异常时必须包含完整堆栈信息、TraceId 和业务上下文参数

**参数校验规范**：
- Request DTO 必须使用 JSR-303/Jakarta Validation 注解：
  - `@NotNull`：非空校验（适用于所有类型）
  - `@NotBlank`：非空字符串校验（自动去除首尾空格）
  - `@Size(min, max)`：字符串长度或集合大小校验
  - `@Pattern(regexp)`：正则表达式校验
  - `@Min` / `@Max`：数值范围校验
  - `@Email`：邮箱格式校验
- Handler 方法统一使用 `RequestHandlerUtil.validateAndMap()` 进行参数校验和 DTO 转换
- 校验失败自动返回 400 错误，错误信息从 Validation 注解的 `message` 属性获取（支持国际化 key）

### 安全防护与权限控制

**核心原则**：深度防御、最小权限、默认拒绝。

**权限校验机制**：
- 管理端点必须具备权限校验，使用 `@RequirePermission` 注解标记 Handler 方法
- 权限编码（如 `"user:read"`、`"role:update"`）必须在 Flyway 菜单/权限种子中预先登记
- 数据权限使用 `@DataScope` 注解，自动注入租户、部门或用户级别的数据隔离条件
- 权限不足时返回 403 错误，错误信息明确说明缺少的权限

**写操作防护**：
- 所有写操作（POST/PUT/DELETE）必须添加以下防护措施：
  - `@OperationLog`：记录操作日志（操作人、操作类型、请求参数、响应结果、IP 地址）
  - `@IdempotentApi`：防止重复提交（客户端需携带 `Idempotency-Key` 请求头）
  - `@AntiReplay`：防重放攻击（客户端需携带 `X-Timestamp` 和 `X-Nonce` 请求头）
  - `@DataScope`：确保只能操作有权限的数据
  - 租户上下文校验：确保跨租户数据访问被正确拦截

**敏感操作限流**：
- 使用 `@SentinelResource` 注解标记需要限流保护的方法
- 配置限流规则（QPS、并发数、熔断阈值）
- 限流拒绝时返回 429 错误，并记录日志
- 敏感接口（登录、注册、密码重置）使用 IP 级别限流（`IpRateLimitWebFilter`）

**安全配置管理**：
- 修改 JWT、OAuth2、Keycloak、权限、验证码、限流或安全响应头时，先查找现有 `security`、`filter`、`aspect` 包中的实现和测试
- JWT 密钥、数据库密码、第三方 API 密钥等敏感配置必须通过环境变量注入
- 生产环境启动时自动校验敏感配置是否存在（使用 `@Profile("prod")` 的校验器）
- 密码、Token、证书、私钥等敏感信息禁止记录到日志或响应体中（使用脱敏工具类）

**安全响应头**：
- 统一配置在 `SecurityHeaderWebFilter` 中：
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: DENY`
  - `X-XSS-Protection: 1; mode=block`
  - `Strict-Transport-Security: max-age=31536000; includeSubDomains`
  - `Content-Security-Policy`：根据业务需求配置

### 缓存架构与策略

**多级缓存架构**：
- **L1 缓存**（Caffeine）：进程内本地缓存，适合热点数据和高频读取场景
  - 优势：无网络开销，纳秒级响应
  - 配置：最大条目数、过期时间、淘汰策略（LRU/LFU）
  - 使用场景：字典数据、配置项、用户基本信息
- **L2 缓存**（Redis）：分布式缓存，支持跨实例共享
  - 优势：支持集群、持久化、复杂数据结构
  - 配置：TTL、序列化方式（JSON/Protobuf）
  - 使用场景：会话状态、业务数据、分布式锁
- **数据源回源**：缓存未命中时从数据库查询，并回填缓存

**缓存防护策略**：
- **防击穿**（Single-Flight/Singleflight）：同一 key 的并发请求只触发一次数据库查询
- **防穿透**：空值标记，对不存在的数据也进行缓存（TTL 较短）
- **防雪崩**：缓存 TTL 添加随机抖动（如 300±30 秒），避免大量 key 同时过期
- **降级策略**：数据源查询失败时返回过期缓存数据（stale-while-revalidate）
- **线程隔离**：缓存回源的数据库查询必须使用 `BlockingIoSchedulers.jdbc()` 隔离

**缓存使用规范**：
- 缓存 key 必须包含租户标识，格式：`{prefix}:{tenantId}:{bizKey}`（如 `user:1001:profile:123456`）
- 使用 `MultiLevelCacheService` 统一管理多级缓存，自动处理 L1/L2 一致性
- 缓存失效策略：
  - 更新数据库后主动删除缓存（Cache-Aside 模式）
  - 使用 Redis Pub/Sub 或 RocketMQ 广播缓存失效消息，清理所有实例的 L1 缓存
- 热点数据识别：通过 Caffeine 统计命中率，自动预热到 L1 缓存

### 消息队列与事件驱动

**RocketMQ 使用规范**：
- **消息生产**：
  - 使用 `MqProducerService` 统一发送消息
  - 消息体必须包含：租户 ID、业务 ID、幂等 key、时间戳、事件类型
  - 重要消息使用事务消息（Transactional Message）确保最终一致性
  - 发送失败时记录到 Outbox 表，通过定时任务补偿
- **消息消费**：
  - 消费者方法必须保留租户上下文（从消息体中提取 `tenantId` 并设置到 `TenantContext`）
  - 实现幂等消费（根据幂等 key 判断是否已处理）
  - 消费异常时返回 `RECONSUME_LATER`，触发 RocketMQ 自动重试（最多 16 次）
  - 重试仍失败的消息进入死信队列（DLQ），需要人工介入处理
  - 消费者异常必须记录详细日志（包含消息 ID、业务 ID、异常堆栈）
- **阻塞操作隔离**：消费者中的数据库操作必须使用 `BlockingIoSchedulers.jdbc()` 或 `.event()`

**领域事件机制**：
- 领域事件在 Domain 层定义，使用 `DomainEvent` 接口标记
- Application 层通过 `DomainEventPublisher` 发布事件（同步或异步）
- 使用 Outbox 模式保证事件发布的可靠性（事件先写入 Outbox 表，异步发送到 MQ）
- 事件消费者实现 `DomainEventListener` 接口，按事件类型路由

**限流与熔断策略**：
- **限流算法**：
  - 令牌桶（Token Bucket）：允许短时突发流量，适合 API 限流和缓存回源保护
  - 漏桶（Leaky Bucket）：平滑流量输出，适合消息消费限速
  - 滑动窗口（Sliding Window）：精确统计时间窗口内的请求数，适合防刷和降级保护
- **限流实现**：
  - 接口级限流：使用 `@SentinelResource` 注解，配合 Sentinel 控制台动态调整规则
  - IP 级限流：使用 `IpRateLimitWebFilter`，基于 Redis + Lua 脚本实现分布式限流
  - 用户级限流：使用 `@UserRateLimit` 注解，防止单个用户过度调用
  - 使用 Bucket4j 实现本地限流（单 JVM 保护），Redis + Bucket4j 实现分布式限流
- **熔断降级**：
  - 使用 Resilience4j CircuitBreaker 实现熔断保护
  - 配置慢调用比例（如 50%）、错误率阈值（如 60%）、半开状态恢复策略
  - 熔断开启时返回降级数据或友好错误提示
- **测试要求**：新增限流行为必须覆盖边界测试、并发测试、时间窗口测试、恢复测试

## 配置管理与环境隔离

### Profile 配置策略

**环境隔离原则**：不同环境使用不同的 Spring Profile，配置文件位于 `apex-interfaces/src/main/resources/`。

**Profile 定义**：
- **`basic`**：本地开发环境（默认）
  - 特点：内存队列、单机缓存、H2/嵌入式 Redis，无外部依赖
  - 适用场景：单元测试、快速原型验证、离线开发
  - 启动方式：`mvnd spring-boot:run -P basic` 或不指定 Profile
- **`dev`**：开发环境
  - 特点：连接开发环境的 MySQL、Redis、RocketMQ、Minio
  - 适用场景：联调测试、集成测试、功能验证
  - 启动方式：`mvnd spring-boot:run -Dspring.profiles.active=dev`
- **`prod`**：生产环境
  - 特点：完整基础设施、启用生产安全校验、性能优化配置
  - 适用场景：正式上线环境
  - 启动方式：`java -jar app.jar --spring.profiles.active=prod`
  - **强制要求**：敏感配置必须通过环境变量注入，启动时自动校验
- **`standard`**：标准版（中型企业）
  - 特点：MySQL + Redis 基础配置，不包含高级特性（如 Keycloak、Sentinel）
  - 适用场景：中小型企业部署
- **`enterprise`**：企业版（大型企业）
  - 特点：完整功能，包含 SSO、高级审计、多租户增强、Office 集成等
  - 适用场景：大型企业、SaaS 平台
- **`performance`**：性能测试环境
  - 特点：针对性能优化的配置（连接池加大、缓存 TTL 延长、日志级别降低）
  - 适用场景：压力测试、性能基准测试

### 配置文件组织

**配置加载顺序**：
1. `application.yml`：通用配置（所有环境共享）
2. `application-{profile}.yml`：特定环境配置（覆盖通用配置）
3. `spring.config.import`：外部配置文件导入（如 `optional:file:./config/local.yml`）
4. 环境变量：最高优先级（生产环境敏感配置）

**配置组织原则**：
- 通用配置（如 server.port、logging.pattern）放在 `application.yml`
- 环境差异配置（如数据源、Redis 地址）放在 `application-{profile}.yml`
- 敏感配置（如密码、密钥）通过环境变量或 Vault 管理，**禁止提交到 Git**
- 新增 Profile 或独立配置文件前必须确认加载方式（通过 `spring.config.import` 或 `@PropertySource`）

### 配置类规范

**使用 `@ConfigurationProperties` 管理配置**：
```java
@Data
@ConfigurationProperties(prefix = "apex.feature")
public class FeatureProperties {
    private boolean keycloakEnabled = false;
    private boolean sentinelEnabled = true;
    private boolean officeIntegrationEnabled = false;
}
```

**配置类规范**：
- 优先使用 `@ConfigurationProperties` + 构造器注入，避免使用 `@Value` 散落在代码中
- 配置类必须提供合理的默认值，避免强制要求所有环境都配置
- 使用 `@Validated` + JSR-303 注解校验配置合法性（如 `@NotBlank`、`@Min`、`@Max`）
- 敏感配置字段使用 `@Sensitive` 注解标记，防止日志泄露

**条件装配**：
- 使用 `@ConditionalOnProperty` 控制功能模块的启用/禁用：
  ```java
  @Configuration
  @ConditionalOnProperty(name = "apex.feature.keycloak-enabled", havingValue = "true")
  public class KeycloakAutoConfiguration { ... }
  ```
- 使用 `@ConditionalOnClass` 根据 classpath 中是否存在特定类来决定是否装配
- 使用 `@Profile` 注解限制 Bean 只在特定环境加载

### 敏感配置管理

**生产环境强制规范**：
- JWT 密钥、数据库密码、Redis 密码、第三方 API 密钥等敏感配置**必须**通过环境变量注入
- 环境变量命名规范：`APEX_DATABASE_PASSWORD`、`APEX_JWT_SECRET`、`APEX_REDIS_PASSWORD`
- 启动时使用 `@Profile("prod")` 的校验器自动检查敏感配置是否存在，缺失则拒绝启动
- **禁止**将真实密码、Token、证书、私钥提交到 Git 仓库

**本地开发配置**：
- 开发环境可以使用配置文件存储测试密码
- 使用 `.env` 文件或 `application-local.yml`（加入 `.gitignore`）存储个人配置
- 提供 `application-local.yml.example` 作为模板，开发者复制后修改

**配置变更流程**：
- 配置变更需同步更新相关文档（`docs/05-operations/configuration.md`）
- 新增配置项需在 `application.yml` 中添加注释说明
- 修改默认值需评估对现有环境的影响

### 代码风格与开发规范

- **代码格式化**：遵循 Spotless；修改后运行 `mvnd spotless:check`。
- **对象转换**：所有涉及对象转换的场景（实体与 PO、DTO 与实体、VO 与 DTO 等）必须使用 MapStruct 实现，禁止手写转换代码。Converter 和 Assembler 使用 `@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)`。
- **依赖注入规范**：
  - **严禁使用 `@Autowired` 注解**进行字段注入或 setter 注入。
  - Service、Component、Handler 和配置类必须使用构造器注入，配合 Lombok 的 `@RequiredArgsConstructor` 注解实现。
  - 构造器注入的优势：编译时检查依赖完整性、支持 final 字段、便于单元测试、避免循环依赖。
- **Lombok 使用规范**：
  - Service、Component 和配置类优先使用 `@RequiredArgsConstructor` 实现构造器注入。
  - DTO、Request、Response、VO 和领域实体必须使用 Lombok 注解（`@Data`、`@Builder`、`@AllArgsConstructor`、`@NoArgsConstructor` 等）。
  - 禁止手写 getter、setter、equals、hashCode、toString、构造器等可由 Lombok 生成的样板代码。
  - 禁止使用 `@SneakyThrows`、`val`、`var` 和 `@Synchronized`。
- **代码注释规范**：
  - 所有 public/protected 类必须添加类级别的中文 JavaDoc，说明类的职责、主要功能和使用场景。
  - 所有 public/protected 方法必须添加方法级别的中文 JavaDoc，说明方法功能、参数含义、返回值和可能抛出的异常。
  - 重写的方法（`@Override`）如果父类/接口已有清晰注释可省略，但如果有特殊实现逻辑或业务含义必须补充说明。
  - private 方法如果逻辑复杂或不直观，建议添加简要注释说明意图。
  - 不要添加无意义的样板注释或与代码重复的废话注释。
  - 使用 `scripts/scan-javadoc-issues.sh` 扫描 JavaDoc 问题，`scripts/batch-add-javadoc.sh` 批量添加缺失的 JavaDoc。
- **包名约定**：主模块使用 `com.apex.scaffold.*`，example 使用现有 `com.apex.example`。
- **依赖管理**：引入依赖前先检查根 POM 和目标模块是否已有可复用能力。

## 脚本开发规范

项目提供了统一的脚本公共函数库 `scripts/lib/common.sh`，用于简化脚本编写、提高代码质量和可维护性。

### 公共函数库使用

**强制要求**：
- 所有新编写的 Bash 脚本**必须**使用公共函数库
- 修改现有脚本时，**优先考虑**迁移到公共函数库
- **严禁**在脚本中直接使用原始 `find` 命令（必须使用 `safe_find`）
- **严禁**手写目录排除规则（由公共库统一管理）

**基本用法**：

```bash
#!/bin/bash

# 1. 加载公共函数库（根据脚本位置调整路径）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/common.sh"              # scripts/ 目录下
# source "${SCRIPT_DIR}/../lib/common.sh"         # scripts/xxx/ 目录下
# source "${SCRIPT_DIR}/../../lib/common.sh"      # scripts/xxx/yyy/ 目录下

# 2. 获取项目根目录
PROJECT_ROOT=$(get_project_root) || exit 1
cd "$PROJECT_ROOT"

# 3. 使用公共函数
log_info "开始执行检查..."
java_files=$(safe_find "apex-*/src/main/java" "*.java")
count=$(safe_count "@author" "$file")
if safe_gt "$count" 0; then
    log_success "检查通过"
fi
```

### 核心函数说明

#### 1. 文件查找函数

**safe_find** - 安全的文件查找（自动排除 .trae、target、.git 等8类目录）

```bash
# 查找所有 Java 文件
java_files=$(safe_find "apex-*/src/main/java" "*.java")

# 查找 Router 类
router_files=$(safe_find "apex-interfaces/src/main/java" "*Router.java")
```

**safe_find_custom** - 自定义查找（保留自动排除功能）

```bash
# 查找特定文件
result=$(safe_find_custom "apex-common/src" -name "FeatureGate.java")
```

#### 2. 日志输出函数

**统一的日志输出**（支持颜色和级别控制）：

```bash
log_debug "调试信息"      # 仅在 DEBUG 级别显示
log_info "普通信息"       # 默认显示
log_warn "警告信息"       # 黄色显示
log_error "错误信息"      # 红色显示
log_success "成功信息"    # 绿色 ✓ 标记
log_failure "失败信息"    # 红色 ✗ 标记
```

**设置日志级别**：

```bash
export LOG_LEVEL=0  # DEBUG
export LOG_LEVEL=1  # INFO（默认）
export LOG_LEVEL=2  # WARN
export LOG_LEVEL=3  # ERROR
```

#### 3. 计数和数字处理

**safe_count** - 安全的计数（自动处理数字格式）

```bash
# 统计注解数量
count=$(safe_count "@author" "$file")
operation_count=$(safe_count "@Operation" "$handler_file")
```

**safe_gt / safe_lt / safe_eq** - 安全的数字比较

```bash
if safe_gt "$count" 10; then
    echo "数量超过10"
fi

if safe_lt "$count" 5; then
    echo "数量少于5"
fi

if safe_eq "$count" 0; then
    echo "数量为0"
fi
```

#### 4. 错误处理函数

**统一的错误检查**：

```bash
# 检查必需命令
require_command "mvnd" "Maven Daemon" || exit 1

# 检查目录存在
require_directory "$src_dir" "源代码目录" || exit 1

# 检查文件存在
require_file "$config_file" "配置文件" || exit 1

# 设置错误陷阱（自动捕获错误并显示行号）
setup_error_trap
```

#### 5. 路径处理函数

**自动路径检测**：

```bash
# 获取脚本所在目录
SCRIPT_DIR=$(get_script_dir)

# 获取项目根目录（自动查找 pom.xml）
PROJECT_ROOT=$(get_project_root) || exit 1
```

#### 6. 格式化输出函数

**美化终端输出**：

```bash
# 打印大标题
print_header "开始执行检查"

# 打印小节标题
print_section "检查 Router 类"

# 打印分隔线
print_separator "=" 40
```

#### 7. 统计和汇总函数

**生成统计报告**：

```bash
# 计算百分比
percentage=$(calculate_percentage 13 18)  # 返回 72

# 打印统计表格行
print_stats "Router 类" 18 13  # 输出：Router 类  |  18 |  13 | 72%
```

### 脚本编写规范

#### 统一的脚本结构

```bash
#!/bin/bash
###############################################################################
# 脚本名称 - 脚本功能描述
#
# 用法：./script-name.sh [选项]
#
# 作者：Apex Scaffold Team
# 日期：YYYY-MM-DD
###############################################################################

# 1. 加载公共函数库
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/common.sh"

# 2. 设置错误处理（可选，推荐）
setup_error_trap

# 3. 获取项目根目录
PROJECT_ROOT=$(get_project_root) || exit 1
cd "$PROJECT_ROOT"

# 4. 参数解析
parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --verbose|-v)
                VERBOSE=1
                shift
                ;;
            --help|-h)
                show_usage
                exit 0
                ;;
            *)
                log_error "未知参数: $1"
                exit 1
                ;;
        esac
    done
}

# 5. 主函数
main() {
    parse_arguments "$@"
    
    print_header "脚本标题"
    
    # 检查依赖
    require_command "mvnd" || exit 1
    
    # 执行任务
    log_info "开始执行..."
    
    # ... 具体逻辑 ...
    
    log_success "执行完成"
}

# 6. 运行主函数
main "$@"
```

#### 错误处理模式

```bash
# 模式1：检查并退出
require_directory "$dir" || exit 1

# 模式2：检查并继续
if ! require_file "$file"; then
    log_warn "文件不存在，使用默认值"
    use_default_value
fi

# 模式3：设置错误陷阱
setup_error_trap  # 之后任何错误都会自动捕获并显示行号
```

#### 日志使用规范

```bash
# 调试信息（开发时使用）
log_debug "变量值: $var"

# 普通信息（进度提示）
log_info "正在处理..."

# 警告信息（潜在问题）
log_warn "文件可能需要更新"

# 错误信息（确定的错误）
log_error "无法读取文件"

# 成功/失败（最终结果）
log_success "所有测试通过"
log_failure "发现3个问题"
```

### 禁止事项

以下写法**严格禁止**：

```bash
# ❌ 禁止：直接使用 find 命令
find apex-*/src/main/java -name "*.java" -type f

# ✅ 正确：使用 safe_find
java_files=$(safe_find "apex-*/src/main/java" "*.java")

# ❌ 禁止：手写目录排除规则
find ... -not -path "*/.trae/*" -not -path "*/target/*" ...

# ✅ 正确：使用公共库自动处理
java_files=$(safe_find "apex-*/src/main/java" "*.java")

# ❌ 禁止：直接使用 grep -c
count=$(grep -c "@author" "$file")

# ✅ 正确：使用 safe_count
count=$(safe_count "@author" "$file")

# ❌ 禁止：不安全的数字比较
if [ "$count" -gt 0 ]; then

# ✅ 正确：使用 safe_gt
if safe_gt "$count" 0; then

# ❌ 禁止：不统一的日志输出
echo "✓ Success"
echo "✗ Failed"

# ✅ 正确：使用统一日志函数
log_success "Success"
log_failure "Failed"
```

### 脚本迁移指南

迁移现有脚本到公共函数库：

**步骤1**：备份原脚本

```bash
cp script.sh script.sh.backup
```

**步骤2**：添加库引用

```bash
# 在脚本开头添加
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/common.sh"
```

**步骤3**：替换函数调用

| 原代码模式 | 替换为 |
|-----------|--------|
| `find ... -not -path ...` | `safe_find` |
| `grep -c ... \| tr -d '\n'` | `safe_count` |
| `[ "$a" -gt "$b" ]` | `safe_gt "$a" "$b"` |
| `echo "✓ ..."` | `log_success` |
| `echo "✗ ..."` | `log_failure` |
| 硬编码项目路径 | `get_project_root` |

**步骤4**：测试验证

```bash
./script.sh  # 运行测试，确保功能正常
```

### 相关文档

- **公共函数库完整文档**：`scripts/lib/README.md`（15,000+ 字详细说明）
- **测试脚本**：`scripts/lib/test-common-lib.sh`（包含10个测试用例）
- **重构示例**：`scripts/dev/check/check-springdoc-annotations-v2.sh`（使用公共库重构的完整示例）

### 最佳实践

1. **优先使用公共函数**：开发新脚本时，先查看公共库是否有可用函数
2. **避免重复造轮子**：不要在脚本中重复实现已有功能
3. **保持代码简洁**：使用公共函数可以大幅减少代码量（通常减少30-50%）
4. **统一错误处理**：使用 `require_*` 函数进行依赖检查
5. **规范日志输出**：使用 `log_*` 函数保持输出风格统一
6. **性能优化**：公共函数已经过性能优化，开销极小（< 2%）

## 测试策略与质量保证

### 测试分层与职责

**单元测试**（Unit Tests）：
- **目标**：测试单个类或方法的逻辑正确性，隔离外部依赖
- **工具**：JUnit 5 + Mockito + AssertJ
- **覆盖范围**：
  - Domain 层：聚合根、值对象、领域服务的业务规则
  - Application 层：应用服务的用例编排逻辑
  - Infrastructure 层：Repository、Converter 的转换逻辑
- **最佳实践**：
  - 使用 Mockito 模拟外部依赖（数据库、Redis、MQ、HTTP 客户端）
  - 测试边界条件（空值、空集合、极限值）
  - 测试异常分支（业务异常、技术异常）
  - 每个测试方法只验证一个行为点

**集成测试**（Integration Tests）：
- **目标**：测试多个组件协作时的正确性，验证与真实基础设施的交互
- **工具**：`@SpringBootTest` + Testcontainers + WebTestClient
- **覆盖范围**：
  - Handler → Application → Infrastructure → Database 完整链路
  - 缓存一致性（L1/L2 缓存同步）
  - 消息生产与消费（RocketMQ）
  - 事务边界和回滚机制
- **环境准备**：
  - 使用 `@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)`
  - 使用 Testcontainers 启动真实的 MySQL、Redis、RocketMQ 容器
  - Profile 设置为 `test`，加载测试专用配置
- **数据隔离**：
  - 每个测试类使用独立的测试数据
  - 使用 `@Transactional` + `@Rollback` 自动回滚数据变更
  - 或使用 `@DirtiesContext` 重建 Spring 上下文

**架构测试**（ArchUnit Tests）：
- **目标**：验证代码架构规则，防止依赖关系破坏
- **工具**：ArchUnit
- **验证规则**：
  - Domain 层不得依赖 Application、Infrastructure、Interfaces 层
  - Domain 层不得依赖 Spring、MyBatis、Redis 等基础设施框架
  - Repository 实现类必须位于 Infrastructure 层
  - 所有阻塞操作必须使用 `BlockingIoSchedulers` 隔离
  - 租户隔离注解使用规范（缓存 key、SQL 拦截器）

**性能测试**（Performance Tests）：
- **目标**：验证接口响应时间、吞吐量、并发能力
- **工具**：JMeter / Gatling / K6
- **测试场景**：
  - 单接口压测：QPS、P95/P99 响应时间
  - 混合场景压测：模拟真实流量比例
  - 缓存预热后的性能表现
  - 数据库连接池、线程池的资源使用情况
- **性能基准**：
  - 查询接口 P95 < 200ms
  - 写操作接口 P95 < 500ms
  - QPS 目标根据业务场景设定

### 测试执行与验证

**常用测试命令**：
```bash
# 运行所有单元测试（使用 basic profile）
mvnd clean test -P basic

# 运行指定模块的测试
mvnd -pl apex-common,apex-infrastructure -am test

# 运行集成测试（需要 Docker 环境）
mvnd verify -P integration-test

# 运行架构测试
mvnd test -Dtest=ArchitectureTest

# 生成测试覆盖率报告（JaCoCo）
mvnd clean test jacoco:report
```

**测试策略**：
- 修改响应式、缓存、消息、安全、租户或架构代码时，**先跑受影响模块测试**，再根据变更范围运行全量测试
- 外部服务测试使用对应 Profile（basic/dev/prod），并先确认依赖的基础设施已准备好
- 新增功能必须补充单元测试和集成测试，测试覆盖率不低于 80%
- 重要业务逻辑（如支付、权限、审计）测试覆盖率要求 90% 以上

**测试重点**：
- **参数边界**：空值、空字符串、空集合、最大/最小值、特殊字符
- **异常分支**：业务异常、技术异常、超时、网络中断
- **并发场景**：多线程竞争、乐观锁冲突、分布式锁争抢
- **幂等性**：重复提交、重复消费、重试机制
- **租户隔离**：跨租户数据访问、缓存 key 租户前缀、SQL 拦截器
- **响应式链路**：Reactor 操作符使用、背压处理、错误传播
- **阻塞线程隔离**：JDBC、Redis、文件 I/O 使用正确的 Scheduler
- **缓存回源与降级**：缓存未命中、缓存失效、数据源异常时的降级策略
- **消息重试**：消息消费失败、重试次数、死信队列处理
- **限流时间窗口**：限流触发、恢复、并发请求、时间窗口边界

### 代码质量检查

**Spotless 代码格式化**：
```bash
# 检查代码格式
mvnd spotless:check

# 自动修复格式问题
mvnd spotless:apply
```

**PMD 静态代码分析**：
```bash
# 运行 PMD 检查
mvnd pmd:check

# 生成 PMD 报告
mvnd pmd:pmd
```

**JaCoCo 测试覆盖率**：
```bash
# 生成覆盖率报告
mvnd clean test jacoco:report

# 查看报告
open target/site/jacoco/index.html
```

**提交前质量检查清单**：
- [ ] `mvnd spotless:check` 通过（代码格式规范）
- [ ] `mvnd pmd:check` 通过（静态代码分析无警告）
- [ ] `mvnd test` 通过（单元测试全部通过）
- [ ] 测试覆盖率达标（新增代码覆盖率 > 80%）
- [ ] ArchUnit 测试通过（架构规则验证）
- [ ] 集成测试通过（如果修改了核心模块）

## 常用开发工具与脚本

### 环境准备与加载

**SDKMAN 环境管理**：
```bash
# 进入项目目录
cd /Users/work/apex-scaffold

# 加载项目环境（自动使用 .sdkmanrc 配置的 Java 和 Maven Daemon 版本）
sdk env

# 验证环境
java -version    # 应显示 Java 17.0.12-oracle
mvnd --version   # 应显示 Maven Daemon 2.0.0-rc-3
```

**项目环境配置文件**（`.sdkmanrc`）：
```properties
# SDKMAN 环境配置
java=17.0.12-oracle
maven=2.0.0-rc-3
```

### 常用构建命令

**编译与打包**：
```bash
# 编译单个模块（不运行测试）
mvnd -pl apex-interfaces -am compile -DskipTests

# 编译所有模块
mvnd clean compile -DskipTests

# 打包（生成可执行 JAR）
mvnd clean package -DskipTests

# 安装到本地仓库
mvnd clean install -DskipTests
```

**测试相关**：
```bash
# 运行所有单元测试（使用 basic profile）
mvnd clean test -P basic

# 运行指定模块的测试
mvnd -pl apex-common,apex-infrastructure -am test

# 运行集成测试
mvnd verify -P integration-test

# 跳过测试
mvnd clean package -DskipTests
```

**代码质量检查**：
```bash
# 代码格式检查
mvnd spotless:check

# 自动修复格式问题
mvnd spotless:apply

# PMD 静态代码分析
mvnd pmd:check

# 生成测试覆盖率报告
mvnd clean test jacoco:report
```

### 项目脚本工具

项目提供了丰富的脚本工具，位于 `scripts/` 目录，按功能分类：

**开发辅助脚本**（`scripts/dev/`）：
- **代码生成**：
  - `codegen/generate.sh`：DDD 代码生成器，根据配置自动生成 Entity、Repository、Service、Handler
  - 使用方式：配置 `apex-generator` 模块的生成模板，运行脚本自动生成代码框架
  
- **代码格式化**：
  - `format/batch-add-javadoc.sh`：批量为缺失 JavaDoc 的类和方法添加标准注释模板
  - `format/batch-add-lombok.sh`：批量为 DTO/PO/VO 类添加 Lombok 注解（`@Data`、`@Builder` 等）
  
- **代码检查**：
  - `check/scan-javadoc-issues.sh`：扫描项目中缺失或不规范的 JavaDoc 注释
  - `check/scan-lombok-issues.sh`：检查 Lombok 使用问题（如手写 getter/setter、缺少注解）
  - `check/check-springdoc-annotations.sh`：检查 Router 和 Handler 的 SpringDoc 注解完整性
  - `check/check-port-conflicts.sh`：检查配置文件中的端口冲突

**测试与诊断脚本**（`scripts/test/`）：
- `jvm-diagnostics-test.sh`：JVM 诊断测试，检测内存泄漏、线程死锁、CPU 高负载
- `generate-test-report.sh`：生成测试报告（HTML 格式），汇总单元测试和集成测试结果

**部署相关脚本**（`scripts/deploy/`）：
- **应用服务管理**：
  - `service/start.sh`：启动应用（支持指定 Profile 和 JVM 参数）
  - `service/stop.sh`：停止应用（优雅关闭，等待请求处理完成）
  - `service/app.sh`：应用管理脚本（start/stop/restart/status）
  
- **Docker 部署**：
  - `docker/pull-infra-images.sh`：拉取基础设施 Docker 镜像（MySQL、Redis、RocketMQ、Grafana）
  - `docker/deploy-monitoring.sh`：部署监控服务（Prometheus、Grafana、Loki、Alloy）

**运维相关脚本**（`scripts/ops/`）：
- **数据库备份**：
  - `backup/db-backup.sh`：数据库备份脚本（支持 MySQL 和 PostgreSQL）
  - `backup/db-restore.sh`：数据库恢复脚本
  
- **监控服务**：
  - `monitoring/start-monitoring.sh`：启动监控服务（Grafana + Prometheus + Loki）
  - `monitoring/grafana/`：Grafana Dashboard 配置脚本
  
- **JVM 诊断**：
  - `diagnostics/jvm-diagnostics.sh`：JVM 运行时诊断（线程 dump、堆 dump、GC 日志分析）

**CI/CD 脚本**（`scripts/ci/`）：
- `check-doc-consistency.sh`：检查文档一致性（README、架构图、API 文档）
- `check-doc-links.sh`：检查文档中的链接有效性

**工具脚本**（`scripts/utils/`）：
- `export-api-docs.sh`：导出 Swagger API 文档为 JSON/YAML 格式
- `extract-env-vars.sh`：从配置文件提取环境变量列表
- `batch-verify-svg.sh`：批量验证 SVG 文件格式和有效性

**性能测试脚本**（`scripts/bench/`）：
- `bench.sh`：性能基准测试脚本（使用 JMeter 或 K6）

**授权管理脚本**（`scripts/license/`）：
- `generate-license.sh`：生成授权文件（用于商业版本授权管理）

### 脚本使用最佳实践

**脚本开发规范**：
- 所有新脚本必须使用公共函数库 `scripts/lib/common.sh`
- 严禁直接使用 `find` 命令，必须使用 `safe_find`
- 严禁手写目录排除规则，由公共库统一管理
- 使用统一的日志输出函数（`log_info`、`log_error`、`log_success`）
- 添加错误处理和参数校验

**脚本执行权限**：
```bash
# 为脚本添加执行权限
chmod +x scripts/dev/codegen/generate.sh

# 执行脚本
./scripts/dev/codegen/generate.sh
```

**查看脚本帮助**：
```bash
# 大多数脚本支持 --help 参数
./scripts/dev/codegen/generate.sh --help
```

**测试策略**：
- 修改响应式、缓存、消息、安全、租户或架构代码时，先跑受影响模块测试，再根据变更范围运行全量测试
- 外部服务测试使用对应 profile（basic/dev/prod/standard/enterprise/performance），并先确认 MySQL、Redis、RocketMQ 等依赖已准备好
- 测试重点包括参数边界、异常、并发、幂等、租户隔离、Reactor 链路、阻塞线程隔离、缓存回源与降级、消息重试以及限流时间窗口
- 使用 ArchUnit 进行架构测试，确保分层依赖、租户隔离和阻塞调度器隔离规则
- 集成测试使用 `@SpringBootTest`，单元测试使用 Mockito 模拟依赖

**性能与监控规范**：
- 接口响应时间 P95 应控制在合理范围（查询 < 200ms，写操作 < 500ms）
- 数据库查询必须添加合适的索引，避免全表扫描
- 热点数据使用多级缓存（Caffeine L1 + Redis L2），缓存 key 必须包含租户标识
- 使用 Micrometer 暴露业务指标，关键操作添加 `@Timed` 注解
- Sentinel 限流规则应覆盖所有写操作和敏感接口
- 使用 Grafana 监控 JVM、数据库连接池、缓存命中率、限流/熔断指标
- 日志必须包含 TraceId，支持分布式链路追踪

## 文档与前端

项目文档位于 `docs/`，应引用真实代码、配置和脚本路径，避免写入未经验证的版本、数量、耗时和完成状态。Mermaid 文件位于 `docs/diagrams/`，操作前阅读该目录 README 和相关验证脚本。

**文档维护规范**：
- 架构图必须与代码实现保持一致，修改代码时同步更新相关架构图
- API 文档通过 SpringDoc 注解自动生成，访问 `/swagger-ui.html` 查看
- 重大功能变更需更新 `docs/` 下的相关技术文档和用户手册
- 配置变更需同步更新 `docs/05-operations/configuration.md`

**国际化（i18n）规范**：
- 所有面向用户的消息必须使用 i18n key，不得硬编码中文或英文
- i18n 资源文件位于 `apex-interfaces/src/main/resources/i18n/`，支持 zh_CN、en_US、zh_TW、th_TH 四种语言
- 新增错误码或提示消息时，必须在所有语言文件中添加对应的翻译
- 使用 `MessageUtils.getMessage(key)` 获取国际化消息，会根据请求的 `Accept-Language` 自动选择语言

### 前后端分离与接口契约

**核心原则**：前后端完全分离，后端只负责提供 RESTful API，不涉及前端实现细节。

**前端项目**：
- 管理后台是独立项目（不在本仓库中），使用独立的技术栈和构建流程。
- 前端路由、视图组件、页面布局由前端团队维护，后端开发者不需要关心。

**菜单权限管理**：
- 新增功能模块时，菜单和权限数据通过 Flyway 种子数据维护（位于 `db/migration/mysql` 和 `db/migration/postgresql`）。
- 菜单表中的 `component` 字段由前端定义，后端只负责存储和查询。
- 权限编码（`permission_code`）必须与 `@RequirePermission` 注解中的值一致。

**接口契约规范**：
- 所有接口统一使用 `Result<T>` 封装响应，包含 `code`、`msg`、`data`、`traceId` 字段。
- Request/Response DTO 字段命名统一使用 **camelCase**（驼峰命名），不使用下划线。
- 分页响应使用 `PageResponse<T>`，包含 `total`、`page`、`pageSize`、`list` 字段。
- 时间字段统一使用 ISO 8601 格式字符串（`yyyy-MM-dd'T'HH:mm:ss`）或时间戳（Long 类型毫秒数）。
- 枚举类型在接口层传输时使用字符串或数值，DTO 中添加清晰的注释说明可选值。

**API 版本管理**：
- 管理后台接口路径前缀：`/admin-api/v1/`
- 业务 API 接口路径前缀：`/api/v1/`
- OAuth2/OIDC 标准接口路径前缀：`/oauth2/`、`/.well-known/`

**跨域与安全**：
- CORS 配置在 `SecurityConfig` 中统一管理，不在接口层单独配置。
- 所有接口默认要求 JWT 认证，公开接口需显式配置白名单。

## Git 提交

不要擅自提交、回滚或删除用户已有改动。提交前按功能分组，认证、业务代码、架构图、报告和配置等不同主题分别提交。禁止提交 `.env`、密钥、证书、私钥、真实配置和构建产物。

### 提交前规范检查

提交前必须执行以下检查，确保代码符合开发规范：

```bash
# 1. 检查工作区状态
git status --short

# 2. 检查空白字符错误
git diff --check

# 3. 代码格式化检查
mvnd spotless:check

# 4. 代码质量检查
mvnd pmd:check

# 5. 运行受影响模块的测试
mvnd -pl 受影响模块 -am test

# 6. 检查暂存区空白字符错误
git diff --cached --check

# 7. 最终确认暂存内容
git status --short
```

### 开发规范检查清单

提交前必须确认以下规范已遵守：

- [ ] **接口实现方式**：所有 HTTP 接口使用 Handler 方式，无 `@Controller`/`@RestController` 注解式接口
- [ ] **SpringDoc/Swagger 注解**：
  - Router 方法已添加 `@RouterOperation` 或 `@RouterOperations` 注解
  - Handler 方法已添加详细的中文 JavaDoc
  - Request/Response DTO 已添加 `@Schema` 注解说明类和字段
  - 接口参数已添加 `@Parameter` 注解
  - 接口已添加 `@Operation` 注解说明摘要、描述、标签和响应状态码
- [ ] **对象转换**：所有对象转换使用 MapStruct，无手写转换代码
- [ ] **Lombok 注解**：DTO/PO/VO/Entity 使用 Lombok 注解，无手写 getter/setter/equals/hashCode/toString
- [ ] **依赖注入**：Service/Component/Handler 使用 `@RequiredArgsConstructor` 构造器注入，**严禁使用 `@Autowired` 注解**进行字段注入或 setter 注入
- [ ] **构建工具**：所有命令使用 `mvnd`，无 `mvn` 命令
- [ ] **代码注释完整性**：
  - 所有 public/protected 类已添加类级别中文 JavaDoc
  - 所有 public/protected 方法已添加方法级别中文 JavaDoc（含参数、返回值、异常说明）
  - 重写方法（`@Override`）如有特殊逻辑已补充说明
  - 无样板注释或废话注释
- [ ] **参数校验**：Request DTO 已添加 JSR-303/Jakarta Validation 注解（`@NotNull`、`@NotBlank`、`@Size` 等）
- [ ] **异常处理**：业务异常使用 `BusinessException` + `ResultCode`，无裸 `RuntimeException` 或魔法数字
- [ ] **租户隔离**：
  - 缓存 key、消息体、异步任务包含租户标识
  - 更新/删除操作已校验租户权限
  - 使用 `TenantContext.currentTenantId()` 获取租户 ID
- [ ] **事务管理**：事务边界在 Application 层，WebFlux 使用 `TransactionalOperator`
- [ ] **阻塞 I/O 隔离**：JDBC/MyBatis/同步 Redis/文件 I/O 使用 `BlockingIoSchedulers` 隔离，无 `.block()` 调用，**严禁使用 `Schedulers.boundedElastic()`**
- [ ] **安全防护**：
  - 写操作已添加 `@IdempotentApi` 和 `@OperationLog` 注解
  - 敏感操作已添加 `@SentinelResource` 限流保护
  - 管理端点已在 Flyway 种子数据中登记权限
  - 权限校验使用 `@RequirePermission` 注解
  - 数据权限使用 `@DataScope` 注解
  - 防重放攻击使用 `@AntiReplay` 注解
- [ ] **数据库迁移**：Flyway 脚本命名规范（`V{版本号}__{描述}.sql`），版本号递增，脚本幂等
- [ ] **代码格式**：`mvnd spotless:check` 通过
- [ ] **代码质量**：`mvnd pmd:check` 通过
- [ ] **架构测试**：ArchUnit 测试通过（分层依赖、租户隔离、阻塞调度器隔离）
- [ ] **测试覆盖**：受影响模块测试通过，集成测试和单元测试覆盖核心逻辑

如发现不符合规范的代码，必须先调整再提交。

### 提交信息格式

提交信息使用 Conventional Commits，例如：

```text
feat(auth): add token refresh flow
docs: update architecture diagrams
fix(cache): handle stale data fallback
refactor(user): migrate manual converter to MapStruct
style: apply Lombok annotations to DTOs
```

完成后只报告实际执行过的命令、测试范围、结果和已知限制。

## 日志规范与可观测性

### 日志级别使用规范

**日志级别定义**：
- **`ERROR`**：系统错误、未捕获异常、需要立即处理的问题
  - 使用场景：数据库连接失败、外部服务调用超时、业务关键流程异常
  - 示例：`log.error("用户支付失败: userId={}, orderId={}, amount={}", userId, orderId, amount, exception)`
  - **必须**包含完整异常堆栈信息
  
- **`WARN`**：潜在问题、降级处理、需要关注但不影响主流程的情况
  - 使用场景：缓存未命中、限流拒绝、降级策略触发、配置缺失使用默认值
  - 示例：`log.warn("缓存未命中，回源数据库: cacheKey={}, tenantId={}", cacheKey, tenantId)`
  - 记录可能需要优化或排查的异常情况
  
- **`INFO`**：关键业务流程、外部调用、状态变更、定时任务执行
  - 使用场景：用户登录、订单创建、支付完成、定时任务启动/结束
  - 示例：`log.info("用户登录成功: userId={}, loginType={}, ip={}", userId, loginType, ip)`
  - 生产环境默认日志级别
  
- **`DEBUG`**：详细的调试信息、方法入参出参、中间状态
  - 使用场景：开发环境调试、问题排查、性能分析
  - 示例：`log.debug("查询用户信息: userId={}, result={}", userId, result)`
  - 仅在开发和测试环境启用，生产环境应关闭
  
- **`TRACE`**：最详细的跟踪信息、第三方库调用细节
  - 使用场景：框架底层调试、复杂问题深度排查
  - 极少使用，性能开销大

### 日志内容规范

**日志格式要求**：
- 使用占位符而非字符串拼接：`log.info("用户登录: userId={}", userId)`（✅正确）
- 禁止字符串拼接：`log.info("用户登录: userId=" + userId)`（❌错误，性能差且无法优化）
- 日志消息应简洁清晰，描述"什么操作"、"操作对象"、"操作结果"
- 包含关键业务标识（userId、orderId、tenantId、traceId）便于问题排查

**TraceId 链路追踪**：
- 所有日志自动包含 TraceId，通过 MDC（Mapped Diagnostic Context）注入
- TraceId 在请求入口（WebFilter）生成，跨服务调用时通过 HTTP Header 传递
- 日志格式示例：`[2026-09-24 10:30:15.123] [INFO] [traceId:abc123] [userId:1001] 用户登录成功`
- 便于在 Grafana Loki 中按 TraceId 查询完整请求链路

**异常日志规范**：
- 异常日志必须记录完整堆栈信息：
  ```java
  try {
      // 业务逻辑
  } catch (Exception e) {
      log.error("操作失败: userId={}, operation={}", userId, operation, e);  // ✅ 正确
      // log.error("操作失败: " + e.getMessage());  // ❌ 错误，缺少堆栈
  }
  ```
- 业务异常（已知异常）使用 WARN 级别，技术异常（未预期异常）使用 ERROR 级别
- 异常信息应包含业务上下文参数，便于快速定位问题

**敏感信息脱敏**：
- **严禁记录**：密码、Token、证书、私钥、完整银行卡号、完整身份证号
- **需要脱敏**：手机号（`138****5678`）、邮箱（`u***@example.com`）、姓名（`张*`）
- 使用脱敏工具类：`DesensitizationUtil.maskPhone(phone)`、`DesensitizationUtil.maskIdCard(idCard)`
- 示例：
  ```java
  log.info("用户注册: phone={}, email={}", 
           DesensitizationUtil.maskPhone(phone), 
           DesensitizationUtil.maskEmail(email));
  ```

### 业务审计日志

**操作日志（`@OperationLog`）**：
- 所有写操作（POST/PUT/DELETE）必须添加 `@OperationLog` 注解
- 自动记录到 `sys_operation_log` 表，包含以下字段：
  - `operator_id`：操作人 ID
  - `operator_name`：操作人姓名
  - `operation_type`：操作类型（CREATE/UPDATE/DELETE/QUERY/EXPORT）
  - `module_name`：模块名称（如"用户管理"）
  - `business_type`：业务类型（如"新增用户"）
  - `request_method`：HTTP 方法（GET/POST/PUT/DELETE）
  - `request_uri`：请求 URI
  - `request_params`：请求参数（JSON 格式，敏感字段已脱敏）
  - `response_result`：响应结果（JSON 格式）
  - `ip_address`：客户端 IP
  - `location`：IP 归属地
  - `user_agent`：浏览器信息
  - `execute_time`：执行耗时（毫秒）
  - `status`：执行状态（SUCCESS/FAIL）
  - `error_msg`：错误信息（失败时）
  - `create_time`：操作时间
  - `tenant_id`：租户 ID

**登录日志（自动记录）**：
- 记录到 `sys_login_log` 表，包含登录成功/失败信息
- 字段包括：用户名、登录类型、IP 地址、归属地、浏览器、操作系统、登录时间、登录状态

**API 调用日志（`ApiCallLogService`）**：
- 记录外部 API 调用（第三方服务、微服务间调用）
- 字段包括：API 名称、请求 URL、请求参数、响应结果、耗时、状态码

### 日志配置与采集

**Logback 配置**：
- 配置文件：`apex-interfaces/src/main/resources/logback-spring.xml`
- 日志格式：结构化 JSON 格式（便于 ELK/Loki 采集和查询）
- 日志输出：
  - 控制台：开发环境启用，生产环境可选
  - 文件：滚动日志文件（按日期和大小切分）
  - Loki：通过 Grafana Alloy 采集（生产环境推荐）

**日志文件管理**：
- 日志路径：`logs/apex-scaffold.log`
- 滚动策略：每天生成新文件，单文件最大 100MB
- 归档文件：`logs/apex-scaffold-%d{yyyy-MM-dd}.%i.log.gz`
- 保留策略：本地保留 7 天，自动删除过期文件

**Grafana Loki 集成**：
- 日志采集：使用 Grafana Alloy（推荐）或 Promtail
- 日志推送：通过 HTTP API 实时推送到 Loki
- 日志查询：
  - 按 TraceId 查询完整请求链路
  - 按时间范围、日志级别、关键字过滤
  - 按租户 ID、用户 ID、业务模块分组统计
- 日志保留：Loki 保留 30 天（可配置）

**日志查询示例**：
```logql
# 查询指定 TraceId 的所有日志
{app="apex-scaffold"} |= "traceId:abc123"

# 查询 ERROR 级别日志
{app="apex-scaffold"} | json | level="ERROR"

# 查询指定用户的操作日志
{app="apex-scaffold"} | json | userId="1001" | line_format "{{.timestamp}} {{.level}} {{.message}}"

# 统计接口错误率
sum(rate({app="apex-scaffold"} | json | level="ERROR" [5m])) by (requestUri)
```

### 性能与监控指标

**日志性能优化**：
- 异步日志：使用 AsyncAppender 避免日志 I/O 阻塞业务线程
- 日志采样：高频日志（如 DEBUG 级别）可按比例采样
- 条件日志：使用 `log.isDebugEnabled()` 避免不必要的字符串拼接

**监控指标暴露**：
- 使用 Micrometer 暴露业务指标：
  - 接口 QPS、响应时间（P50/P95/P99）
  - 缓存命中率、缓存穿透次数
  - 限流拒绝次数、熔断次数
  - 数据库连接池状态、线程池状态
- 关键操作添加 `@Timed` 注解：
  ```java
  @Timed(value = "user.login", description = "用户登录耗时")
  public Mono<ServerResponse> login(ServerRequest request) { ... }
  ```

**告警配置**：
- ERROR 日志告警：5 分钟内 ERROR 日志超过 10 条触发告警
- 接口超时告警：P95 响应时间超过 1000ms 触发告警
- 限流告警：限流拒绝率超过 10% 触发告警
- 缓存告警：缓存命中率低于 80% 触发告警

## 编译故障排查与诊断

### 常见编译问题分类

**1. 注解处理器问题**（Lombok、MapStruct）
- 症状："cannot find symbol" 错误，涉及 getter/setter/builder 等 Lombok 生成的方法
- 根本原因：注解处理器未正确执行或源代码有前置编译错误
- 排查优先级：⭐⭐⭐⭐⭐

**2. 依赖冲突问题**
- 症状：`NoClassDefFoundError`、`ClassNotFoundException`、版本冲突警告
- 根本原因：依赖传递冲突、版本不兼容、缺少必需依赖
- 排查优先级：⭐⭐⭐⭐

**3. 方法签名不匹配**
- 症状：调用方法时参数类型、数量、返回值类型不匹配
- 根本原因：Service 层方法签名变更后，Handler 层未同步更新
- 排查优先级：⭐⭐⭐

**4. 导入错误**
- 症状：找不到类、包名错误、类名拼写错误
- 根本原因：导入语句错误、类路径不正确、包结构变更
- 排查优先级：⭐⭐⭐⭐⭐

**5. 模块依赖问题**
- 症状：跨模块引用找不到类、Maven 模块依赖未声明
- 根本原因：POM 文件中缺少模块依赖、依赖顺序错误
- 排查优先级：⭐⭐⭐⭐

### 编译问题诊断流程

**第一步：快速定位错误类型**

```bash
# 1. 清理并重新编译（排除缓存干扰）
mvnd clean compile -DskipTests 2>&1 | tee compile-error.log

# 2. 查看错误摘要（关注第一个错误）
grep -A 5 "COMPILATION ERROR" compile-error.log | head -20

# 3. 统计错误类型
grep "error:" compile-error.log | sort | uniq -c | sort -rn
```

**第二步：按优先级排查**

**优先级 1：检查导入和基础语法**
```bash
# 检查是否有导入错误
grep "package .* does not exist" compile-error.log

# 检查是否有类名拼写错误
grep "cannot find symbol.*class" compile-error.log
```

常见问题：
- 错误的包名：`import com.apex.common.*` 写成 `import com.apex.common.*`（包名正确示例）
- 类名拼写错误：`BlockingIoSchedulers` 写成 `BlockingIOSchedulers`
- 静态导入问题：缺少 `import static` 或导入了错误的静态方法

修复方法：
1. 使用 IDE 的自动导入功能（IntelliJ IDEA：Alt+Enter）
2. 检查源文件包声明与文件路径是否一致
3. 对比正常编译的模块的导入语句

**优先级 2：检查注解处理器配置**

```bash
# 使用 DEBUG 模式查看详细编译过程
mvnd clean compile -X -DskipTests 2>&1 | tee debug-compile.log

# 查看注解处理器路径
grep "processorpath" debug-compile.log

# 确认 Lombok 和 MapStruct 是否正确加载
grep -E "lombok|mapstruct" debug-compile.log | head -20
```

预期输出应包含：
```
-processorpath .../lombok-1.18.36.jar:.../mapstruct-processor-1.5.5.Final.jar:...
```

如果缺少，检查：
1. 根 POM 的 `maven-compiler-plugin` 配置
2. 子模块是否正确继承父 POM 配置
3. IDEA 是否启用了注解处理（Settings → Build → Compiler → Annotation Processors）

**优先级 3：检查模块依赖**

```bash
# 查看模块依赖树
mvnd dependency:tree -pl apex-interfaces

# 检查是否有依赖冲突
mvnd dependency:tree -pl apex-interfaces | grep "omitted for conflict"

# 查看有效 POM（合并后的配置）
mvnd help:effective-pom -pl apex-interfaces > effective-pom.xml
```

常见问题：
- 子模块未声明对其他模块的依赖（如 apex-interfaces 依赖 apex-application）
- 依赖版本冲突（多个模块引入不同版本的同一依赖）
- 依赖作用域错误（test 依赖无法在 main 代码中使用）

修复方法：
1. 在子模块 POM 中添加缺失的模块依赖
2. 使用 `<dependencyManagement>` 统一管理依赖版本
3. 使用 `mvnd dependency:analyze` 分析未使用和未声明的依赖

**优先级 4：检查方法签名匹配**

使用 IDE 查找方法调用：
1. 在 Service 方法上右键 → Find Usages
2. 检查所有调用点的参数类型、数量、返回值类型
3. 特别注意响应式类型转换：`Mono` vs `Flux`

常见问题示例：
```java
// Service 方法签名
public Mono<Void> revokeSession(Long userId, String sessionId) { ... }

// Handler 错误调用（参数顺序错误）
ssoService.revokeSession(sessionId, userId)  // ❌ 错误

// Handler 正确调用
ssoService.revokeSession(userId, sessionId)  // ✅ 正确
```

### Lombok 特定问题排查

**问题：Lombok 生成的方法找不到**

排查步骤：
```bash
# 1. 确认 Lombok 版本
grep "<lombok.version>" pom.xml

# 2. 检查 Lombok 注解是否正确
grep -r "@Data\|@Builder\|@Getter\|@Setter" apex-*/src/main/java

# 3. 查看生成的字节码（确认 Lombok 是否生效）
javap -c target/classes/com/apex/scaffold/interfaces/dto/XxxRequest.class | grep -E "get|set"
```

常见 Lombok 错误：
- ❌ 在接口或抽象类上使用 `@Data`（无效）
- ❌ 同时手写 getter 和使用 `@Data`（冲突）
- ❌ Lombok 注解和 `final` 字段冲突（`@Data` 会为 final 字段生成 getter 但不生成 setter）
- ✅ 使用 `@RequiredArgsConstructor` 替代 `@AllArgsConstructor` 实现构造器注入

**问题：MapStruct 生成的实现类找不到**

排查步骤：
```bash
# 1. 检查 Mapper 接口注解
grep -r "@Mapper" apex-*/src/main/java

# 2. 确认 MapStruct 生成的实现类
find target/generated-sources/annotations -name "*Impl.java"

# 3. 查看生成的实现类内容
cat target/generated-sources/annotations/com/apex/scaffold/application/assembler/UserAssemblerImpl.java
```

常见 MapStruct 错误：
- ❌ `@Mapper` 缺少 `componentModel = "spring"`（无法注入）
- ❌ 字段名不匹配且未使用 `@Mapping` 注解指定映射关系
- ❌ Lombok 和 MapStruct 执行顺序错误（需要 lombok-mapstruct-binding）

### 编译器配置最佳实践

**父 POM 统一配置**：
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <source>${maven.compiler.source}</source>
                <target>${maven.compiler.source}</target>
                <encoding>${project.build.sourceEncoding}</encoding>
                <parameters>true</parameters>
                <annotationProcessorPaths>
                    <!-- Lombok 必须放在 MapStruct 之前 -->
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <version>${lombok.version}</version>
                    </path>
                    <path>
                        <groupId>org.mapstruct</groupId>
                        <artifactId>mapstruct-processor</artifactId>
                        <version>${mapstruct.version}</version>
                    </path>
                    <!-- Lombok 和 MapStruct 协同工作的桥接器 -->
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok-mapstruct-binding</artifactId>
                        <version>0.2.0</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

**关键配置说明**：
- `<parameters>true</parameters>`：保留方法参数名，便于 Spring 等框架使用
- Lombok 必须放在 MapStruct 之前：确保 MapStruct 能识别 Lombok 生成的 getter/setter
- `lombok-mapstruct-binding`：解决 Lombok 和 MapStruct 协同问题

**子模块配置原则**：
- ✅ 优先继承父 POM 配置，避免重复定义
- ✅ 只在需要特殊配置时才显式添加 `maven-compiler-plugin`
- ❌ 避免在子模块中只定义 `<configuration>` 而不引用插件（会导致配置不生效）
- ❌ 避免子模块覆盖父 POM 的 `annotationProcessorPaths`（除非有充分理由）

### 依赖冲突解决

**查看依赖冲突**：
```bash
# 查看完整依赖树
mvnd dependency:tree > dependency-tree.txt

# 查找冲突的依赖
grep "omitted for conflict" dependency-tree.txt

# 查找重复引入的依赖
mvnd dependency:tree | grep -E "spring-boot-starter-web" | sort | uniq -c
```

**常见依赖冲突场景**：

**场景 1：Spring Boot 版本冲突**
```xml
<!-- ❌ 错误：子模块引入了不同版本的 Spring Boot -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <version>3.3.0</version>  <!-- 与父 POM 的 3.5.15 冲突 -->
</dependency>

<!-- ✅ 正确：不指定版本，继承父 POM -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

**场景 2：Jackson 版本冲突**
```bash
# 查找 Jackson 版本冲突
mvnd dependency:tree | grep jackson-databind

# 解决方法：在父 POM 的 <dependencyManagement> 中统一版本
```

**场景 3：日志框架冲突**（Logback vs Log4j）
```xml
<!-- 排除冲突的日志依赖 -->
<dependency>
    <groupId>com.example</groupId>
    <artifactId>some-library</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.apache.logging.log4j</groupId>
            <artifactId>log4j-core</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

**依赖分析工具**：
```bash
# 分析未使用的依赖
mvnd dependency:analyze

# 查看依赖的来源
mvnd dependency:tree -Dverbose -Dincludes=com.example:some-artifact
```

### IDE 配置检查

**IntelliJ IDEA 配置**：

1. **启用注解处理**：
   - Settings → Build, Execution, Deployment → Compiler → Annotation Processors
   - ✅ Enable annotation processing
   - ✅ Obtain processors from project classpath

2. **配置 Maven**：
   - Settings → Build, Execution, Deployment → Build Tools → Maven
   - Maven home path：使用 `.sdkmanrc` 中配置的 Maven Daemon（mvnd）
   - User settings file：使用项目的 `settings.xml`（如果有）
   - JDK for importer：Java 17

3. **刷新 Maven 项目**：
   - 右键项目 → Maven → Reload Project
   - 或点击 Maven 工具窗口的刷新按钮

4. **清理 IDEA 缓存**（最后手段）：
   - File → Invalidate Caches / Restart
   - 选择 Invalidate and Restart

**常见 IDEA 问题**：
- 问题：IDEA 显示错误但 Maven 编译成功
  - 原因：IDEA 缓存过期或配置不同步
  - 解决：Reload Maven Project + Invalidate Caches

- 问题：Lombok 在 IDEA 中不生效
  - 原因：未安装 Lombok 插件或未启用注解处理
  - 解决：安装 Lombok 插件（Settings → Plugins → Lombok）

- 问题：MapStruct 生成的实现类标红
  - 原因：IDEA 未识别 `target/generated-sources/annotations` 为源代码目录
  - 解决：右键该目录 → Mark Directory as → Generated Sources Root

### 增量编译与缓存管理

**清理策略**：
```bash
# 清理编译缓存
mvnd clean

# 清理特定模块
mvnd clean -pl apex-interfaces

# 强制重新下载依赖（慎用）
mvnd clean install -U

# 清理本地仓库的 SNAPSHOT 依赖
mvnd dependency:purge-local-repository -DmanualIncludes=com.apex.scaffold
```

**增量编译**：
```bash
# 只编译变更的模块及其依赖
mvnd compile -pl apex-interfaces -am

# 跳过测试，加快编译速度
mvnd compile -DskipTests

# 并行编译（利用多核 CPU）
mvnd -T 4 compile  # 使用 4 个线程
```

**Maven Daemon 优势**：
- 预热 JVM，避免每次编译都启动新 JVM
- 智能缓存依赖，加快后续编译
- 并行编译支持，充分利用多核 CPU

### 故障排查检查清单

**编译失败时的系统化检查**：

- [ ] **环境检查**
  - [ ] Java 版本：`java -version` 确认为 Java 17
  - [ ] Maven Daemon 版本：`mvnd --version` 确认版本正确
  - [ ] 项目环境已加载：已执行 `sdk env`

- [ ] **代码检查**
  - [ ] 导入语句正确，无拼写错误
  - [ ] 包声明与文件路径一致
  - [ ] 方法签名匹配（参数类型、数量、返回值）
  - [ ] Lombok 注解使用正确（`@Data`、`@Builder`、`@RequiredArgsConstructor`）

- [ ] **配置检查**
  - [ ] 父 POM 的 `maven-compiler-plugin` 配置正确
  - [ ] 子模块正确继承父 POM 配置
  - [ ] `annotationProcessorPaths` 包含 Lombok 和 MapStruct
  - [ ] Lombok 在 MapStruct 之前

- [ ] **依赖检查**
  - [ ] 模块依赖已声明（`dependency:tree`）
  - [ ] 无依赖版本冲突（`dependency:analyze`）
  - [ ] 依赖作用域正确（compile/test/provided）

- [ ] **IDE 检查**
  - [ ] IDEA 注解处理已启用
  - [ ] Lombok 插件已安装
  - [ ] Maven 项目已刷新
  - [ ] 生成源代码目录已标记

- [ ] **清理与重试**
  - [ ] 已执行 `mvnd clean`
  - [ ] 已重新编译 `mvnd compile -DskipTests`
  - [ ] 已查看完整编译日志

### 典型案例分析

**案例 1：Lombok getter 找不到**

错误信息：
```
cannot find symbol: method getUserId()
```

排查过程：
1. 检查类是否有 `@Data` 或 `@Getter` 注解 → 有
2. 查看 DEBUG 日志确认 Lombok 是否执行 → 未执行
3. 向上查找第一个编译错误 → 发现导入错误：`import com.apex.common.*` 包名不正确
4. 修复导入错误后，Lombok 正常执行，getter 方法生成成功

**案例 2：MapStruct 实现类找不到**

错误信息：
```
cannot find symbol: class UserAssemblerImpl
```

排查过程：
1. 检查 `UserAssembler` 接口是否有 `@Mapper(componentModel = "spring")` → 有
2. 查看 `target/generated-sources/annotations/` 目录 → 实现类已生成
3. 检查 IDEA 是否识别生成源代码目录 → 未识别
4. 右键目录 → Mark Directory as → Generated Sources Root → 问题解决

**案例 3：方法签名不匹配**

错误信息：
```
method revokeSession in class SsoService cannot be applied to given types
```

排查过程：
1. 使用 Find Usages 查找所有调用点
2. 发现 Handler 传递参数顺序错误：`revokeSession(sessionId, userId)` 应为 `revokeSession(userId, sessionId)`
3. 修复参数顺序 → 编译成功
- 参数顺序不一致(如 `(providerId, userId)` vs `(userId, providerId)`)
- 返回类型不匹配(如 Service 返回 `Flux` 但 Handler 期望 `Mono`)
- 方法已重命名但调用方未更新

**自动化检查**:
```bash
# 编译时会自动检查方法签名
mvnd clean compile -pl apex-interfaces -am -DskipTests

# 检查特定 Handler 的编译错误
mvnd clean compile -pl apex-interfaces 2>&1 | grep "cannot find symbol\|cannot be applied"
```

### 依赖注入和包导入检查

**Spring 依赖注入**:
- WebFlux 中 `org.springframework.web.bind.annotation.RequestMethod` 用于注解
- `org.springframework.web.reactive.function.server` 包中没有 `RequestMethod` 类
- 使用 IDE 的自动导入时要仔细检查导入的包是否正确

**阻塞 I/O 调度器**:
- `BlockingIoSchedulers` 提供静态方法: `jdbc()`, `cache()`, `storage()`, `remote()`, `general()`
- 没有 `getScheduler(String)` 方法
- 选择合适的调度器: LDAP/HTTP 远程调用使用 `remote()`,数据库使用 `jdbc()`

### 增量编译验证

**开发流程**:
1. 修改代码后立即编译单个模块: `mvnd compile -pl <module>`
2. 编译成功后再编译依赖模块: `mvnd compile -pl <module> -am`
3. 最后编译整个项目: `mvnd clean compile`
4. 避免积累大量修改后才编译,这会增加问题定位难度

**编译检查点**:
- 修改 Service 方法签名后 → 立即编译检查调用方
- 添加新依赖后 → 立即编译检查是否引入冲突
- 修改导入语句后 → 立即编译检查是否有错误
- 重构代码后 → 立即运行相关测试

### 未实现功能处理

对于尚未实现的 Service 方法,Handler 层应采取以下策略:

**临时方案**:
```java
public Mono<ServerResponse> someMethod(ServerRequest request) {
    // TODO: 实现 someService.someMethod() 方法
    return buildError(ResultCode.ERROR, TraceIdUtil.getTraceId());
    
    /* 原实现代码注释保留,便于后续实现
    return someService.someMethod(params)
            .flatMap(this::buildSuccess);
    */
}
```

**注意事项**:
- 添加清晰的 TODO 注释说明缺失的功能
- 返回合适的错误码(如 `ResultCode.ERROR`)
- 保留原有代码注释,便于后续实现
- 在代码审查和测试时明确标注这是临时实现
- 在项目管理系统中创建对应的任务跟踪

### 调试工具和命令

**Maven DEBUG 模式**:
```bash
# 生成完整的编译日志
mvnd clean compile -X 2>&1 | tee debug-build.log

# 查看注解处理器路径
grep "processorpath" debug-build.log

# 查看第一个编译错误
grep -A 3 "ERROR" debug-build.log | head -20
```

**有效 POM 检查**:
```bash
# 查看子模块的实际有效配置
mvnd help:effective-pom -pl apex-interfaces > effective-pom.xml

# 查看编译器插件配置
grep -A 30 "maven-compiler-plugin" effective-pom.xml
```

**模块依赖分析**:
```bash
# 查看模块依赖树
mvnd dependency:tree -pl apex-interfaces

# 查看编译顺序
mvnd compile -pl apex-interfaces -am -DskipTests
```

### 经验教训

1. **系统性思维**: 不要被表面现象迷惑,要深入理解底层机制
2. **工具的价值**: Maven DEBUG 日志、IDE 错误提示、编译日志都是重要线索
3. **基础的关键性**: 一个简单的导入错误可以导致整个编译流程失败
4. **持续验证**: 修复后必须运行测试验证,确保问题真正解决
5. **文档记录**: 复杂问题的排查过程和解决方案应该记录下来,供团队参考
