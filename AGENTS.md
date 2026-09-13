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

### WebFlux 与阻塞 I/O

接口层必须使用函数式 WebFlux Router + Handler 模式，禁止使用 `@Controller` 或 `@RestController` 注解式编程。路由位于 `apex-interfaces` 的 router 包，业务处理位于 handler 包。

**接口实现规范**：
- 所有 HTTP 接口必须使用 Handler 方式实现，通过 `RouterFunction<ServerResponse>` 定义路由。
- 禁止使用 `@Controller`、`@RestController`、`@RequestMapping`、`@GetMapping`、`@PostMapping` 等注解式接口。
- 新增接口必须完成请求校验、路由注册和统一响应转换。
- 接口及相关代码（Router、Handler、Request、Response）必须添加完整的 SpringDoc（OpenAPI 3.0）注解，确保在线 Swagger 文档清晰完整。

**SpringDoc/Swagger 注解规范**：
- Router 方法使用 `@RouterOperation` 或 `@RouterOperations` 定义接口元数据。
- Handler 方法添加详细的中文 JavaDoc，说明接口功能、业务逻辑、参数和返回值。
- Request DTO 使用 `@Schema` 注解标注类和字段说明，必填字段使用 `required = true`。
- Response DTO 使用 `@Schema` 注解标注类和字段说明，包含示例值。
- 每个接口参数使用 `@Parameter` 注解说明参数用途、类型和约束。
- 使用 `@Operation` 注解说明接口摘要、描述、标签和响应状态码。

**阻塞 I/O 隔离**：
- JDBC、MyBatis-Plus、同步 Redis、文件 I/O 和同步 HTTP 客户端都是阻塞操作，必须使用项目已有的 `BlockingIoSchedulers` 或专用调度器隔离。
- 请求处理链中不得直接调用 `.block()`；消息监听器只有在现有同步消费边界明确允许时才能使用阻塞桥接。

**响应式编程最佳实践**：
- 优先使用 `Mono`、`Flux`、`Mono.defer`、`Mono.fromCallable` 和 `Mono.zip`。
- 远程调用应设置超时，并按业务语义处理重试、降级和取消。

### DDD、数据库与租户

新增业务按以下顺序实现：

1. domain：聚合、值对象、领域服务、事件和 Repository 接口。
2. infrastructure：PO、Mapper、MapStruct Converter 和 RepositoryImpl。
3. application：用例编排、事务和 DTO/实体转换（使用 MapStruct Assembler）。
4. interfaces：Handler、Router、安全和响应转换。
5. 数据库变更：在 `apex-interfaces/src/main/resources/db/migration/` 按数据库类型（mysql/postgresql）追加 Flyway 脚本，禁止修改已执行脚本。

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

### 响应、异常与安全

接口保持 `Result<T>` 的 `code`、`msg`、`data`、`traceId` 契约。错误码集中维护在 `apex-common` 的 `ResultCode`，消息使用 i18n key；优先使用已有 `BusinessException` 和错误码，禁止散落魔法数字和裸 `RuntimeException`。

**异常处理规范**：
- 业务异常统一使用 `BusinessException`，传入 `ResultCode` 枚举
- 技术异常在 `GlobalExceptionHandler` 统一处理，转换为标准 `Result` 响应
- 异常信息使用 i18n 国际化 key，支持多语言
- 日志记录异常时必须包含堆栈信息和上下文参数

**安全防护规范**：
- 管理端点必须具备权限校验，并在 Flyway 菜单/权限种子中登记
- 写操作应检查幂等（`@IdempotentApi`）、防重放、审计（`@OperationLog`）、数据权限和租户上下文
- 修改 JWT、OAuth2、Keycloak、权限、验证码、限流或安全响应头时，先查找现有 security、filter、aspect 实现和测试
- 敏感操作使用 Sentinel 限流保护（`@SentinelResource`）
- 密码、Token、证书、私钥等敏感信息禁止记录到日志或响应体中

**参数校验规范**：
- Request DTO 必须使用 JSR-303/Jakarta Validation 注解（`@NotNull`、`@NotBlank`、`@Size`、`@Pattern` 等）
- Handler 方法使用 `RequestHandlerUtil.validateAndMap` 进行统一校验和转换
- 校验失败自动返回 400 错误，错误信息支持国际化

### 缓存、消息与限流

**缓存策略**：
- 缓存遵循现有 L1 Caffeine（本地）、L2 Redis（分布式）、数据源回源的三层架构
- 回源需要考虑 Single-Flight（防击穿）、空值标记（防穿透）、过期数据降级、分布式锁和阻塞线程隔离
- 缓存 key 必须包含租户标识，确保租户隔离
- 使用 `MultiLevelCacheService` 实现多级缓存，统一缓存失效策略
- 热点数据优先放入 L1 缓存，减少 Redis 访问压力

**消息队列规范**：
- RocketMQ 消费必须保留租户上下文、幂等、失败状态和重试语义
- 只有确认消费框架能正确重试时，才能把限流拒绝接入消息失败路径
- 消息体必须包含租户 ID、业务 ID、幂等 key 等关键标识
- 消费者异常必须记录详细日志，支持问题排查和重试

**限流策略**：
- 令牌桶用于受控突发和缓存回源保护
- 漏桶用于平滑消费
- 滑动窗口用于进程内计数和降级保护
- Redis、Sentinel 等分布式能力优先用于多实例场景；本地算法只承担单 JVM 保护或降级
- 新增限流行为要覆盖边界、并发、恢复和时间窗口测试
- 使用 Bucket4j 实现限流，支持多种限流算法

## 配置与代码风格

配置优先放入实际会被加载的 `application.yml`、`application-{profile}.yml` 或通过 `spring.config.import` 明确导入的文件。当前资源中存在 `basic`、`dev`、`prod`、`standard`、`enterprise`、`performance` 等 profile；新增 profile 或独立配置文件前必须确认加载方式。

配置类优先使用 `@ConfigurationProperties` 和构造器注入，避免同一参数同时由硬编码、`@Value` 和配置类管理。密码、Token、证书、私钥和真实连接信息只能来自环境变量或本地未跟踪文件。

### 代码风格与开发规范

- **代码格式化**：遵循 Spotless；修改后运行 `mvnd spotless:check`。
- **对象转换**：所有涉及对象转换的场景（实体与 PO、DTO 与实体、VO 与 DTO 等）必须使用 MapStruct 实现，禁止手写转换代码。Converter 和 Assembler 使用 `@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)`。
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

## 常用脚本与验证

加载项目环境：

```bash
cd /Users/work/apex-scaffold
sdk env
```

常用构建和质量检查：

```bash
mvnd -pl apex-interfaces -am compile -DskipTests
mvnd clean test -P basic
mvnd -pl apex-common,apex-infrastructure -am test
mvnd spotless:check
mvnd pmd:check
mvnd clean package -DskipTests
```

常用脚本位于 `scripts/`：

- `scripts/build/`：构建环境和构建脚本
  - `init-build-env.sh`：初始化构建环境
  - `build.sh`：完整构建脚本
- `scripts/test/`：测试和 JVM 诊断
  - `jvm-diagnostics-test.sh`：JVM 诊断测试
- `scripts/codegen/`：DDD 代码生成
  - `generate.sh`：代码生成脚本
- `scripts/check_javadoc.sh`、`scripts/scan-javadoc-issues.sh`：JavaDoc 检查
- `scripts/scan-lombok-issues.sh`：Lombok 使用问题扫描
- `scripts/batch-add-javadoc.sh`、`scripts/batch-add-lombok.sh`：批量添加 JavaDoc 和 Lombok 注解
- `scripts/backup/`：数据库备份和恢复
  - `db-backup.sh`：数据库备份
  - `db-restore.sh`：数据库恢复
- `scripts/deploy/`：部署相关脚本
  - `start.sh`、`stop.sh`、`app.sh`：应用启动、停止和管理
  - `pull-infra-images.sh`：拉取基础设施 Docker 镜像
- `scripts/diagnostics/`：诊断工具
  - `jvm-diagnostics.sh`：JVM 诊断
- `scripts/ci/`：持续集成和文档检查
  - `check-doc-consistency.sh`：文档一致性检查
  - `check-doc-links.sh`：文档链接检查
- `scripts/bench/`：性能基准测试
  - `bench.sh`：性能测试脚本
- `scripts/license/`：授权相关
  - `generate-license.sh`：生成授权文件

**测试策略**：
- 修改响应式、缓存、消息、安全、租户或架构代码时，先跑受影响模块测试，再根据变更范围运行全量测试
- 外部服务测试使用对应 profile（basic/dev/prod/standard/enterprise/performance），并先确认 MySQL、Redis、RocketMQ 等依赖已准备好
- 测试重点包括参数边界、异常、并发、幂等、租户隔离、Reactor 链路、阻塞线程隔离、缓存回源与降级、消息重试以及限流时间窗口
- 使用 ArchUnit 进行架构测试，确保分层依赖、租户隔离和阻塞调度器隔离规则
- 集成测试使用 `@SpringBootTest`，单元测试使用 Mockito 模拟依赖

## 文档与前端

项目文档位于 `docs/`，应引用真实代码、配置和脚本路径，避免写入未经验证的版本、数量、耗时和完成状态。Mermaid 文件位于 `docs/diagrams/`，操作前阅读该目录 README 和相关验证脚本。

管理后台是独立工程 `/Users/work/apex-admin-web`。新增菜单时，Flyway 的 `component` 必须与前端 `VIEW_REGISTRY` 完全一致；接口 DTO 保持 camelCase，不破坏 `Result` 解包契约。

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
- [ ] **构造器注入**：Service/Component 使用 `@RequiredArgsConstructor`，无 `@Autowired` 字段注入
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
- [ ] **阻塞 I/O 隔离**：JDBC/MyBatis/同步 Redis/文件 I/O 使用 `BlockingIoSchedulers` 隔离，无 `.block()` 调用
- [ ] **安全防护**：
  - 写操作已添加 `@IdempotentApi` 和 `@OperationLog` 注解
  - 敏感操作已添加 `@SentinelResource` 限流保护
  - 管理端点已在 Flyway 种子数据中登记权限
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
