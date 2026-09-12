# AGENTS.md — Apex Scaffold 后端

本文件是本仓库的编码、验证和提交约定。修改代码前先确认模块职责、依赖方向和现有实现；遵循现有代码风格，不为满足本文件而引入无关重构。

## 项目与模块

Apex Scaffold 是 Java 17、Spring Boot、Spring WebFlux 和 Maven 多模块项目，采用 DDD 四层架构。根项目版本和依赖版本以 [pom.xml](pom.xml) 为准，JDK 与构建工具版本以 `.sdkmanrc` 为准。

主要模块：

- `apex-common`：公共结果、异常、注解、常量、上下文、工具和调度器，不放业务实现。
- `apex-domain`：聚合、值对象、领域服务、领域事件和仓储接口，不依赖应用层、接口层或基础设施技术。
- `apex-application`：用例编排、事务边界、DTO 转换和应用服务，依赖 domain。
- `apex-infrastructure`：仓储实现、MyBatis-Plus、缓存、消息、存储和外部服务适配器，依赖 domain 与 common。
- `apex-interfaces`：WebFlux 路由与处理器、安全、过滤器、配置、OpenAPI、国际化和 Flyway，包含应用启动入口。
- `apex-example`：示例业务和集成测试。
- `apex-generator`：独立的 DDD 代码生成器。
- `apex-license-spring-boot-starter`、`apex-license-tools`：授权相关模块，改动前确认商业发行要求。

依赖方向必须保持为：

```text
interfaces → application → domain ← infrastructure
                  common  ← 各模块按需依赖
```

禁止 domain 依赖 application、interfaces 或 infrastructure。涉及分层的改动应运行现有 ArchUnit 测试，重点关注 `apex-infrastructure/src/test` 下的架构测试。

## 开发规则

### WebFlux 与阻塞操作

接口层使用函数式 WebFlux 端点。路由集中在 `apex-interfaces` 的 router 包，业务处理放在 handler 包；新增端点必须同时注册路由、校验请求参数并返回统一响应。

JDBC、MyBatis-Plus、同步 Redis、文件系统和同步 HTTP 客户端都属于阻塞操作。不得在 event-loop 线程直接调用，必须使用项目现有的 `BlockingIoSchedulers` 或对应的专用调度器。先读取调度器实现和调用方上下文，再决定 `subscribeOn` 或 `publishOn` 的位置。不要用 `.block()` 处理 WebFlux 请求；消息监听器只有在现有同步消费边界明确允许时才能使用阻塞桥接。

优先使用 `Mono`、`Flux`、`Mono.defer`、`Mono.fromCallable` 和 `Mono.zip` 表达异步流程。对远程调用设置超时，并根据业务语义处理重试、降级和取消。

### DDD 与数据访问

新增业务按以下顺序实现：

1. 在 domain 定义聚合、值对象、领域服务、事件和 Repository 接口。
2. 在 infrastructure 实现 PO、Mapper、Converter 和 RepositoryImpl，并隔离阻塞 I/O。
3. 在 application 编排用例、事务和 DTO/实体转换。
4. 在 interfaces 编写 Handler、Router、安全和响应转换。
5. 数据库结构只通过 `apex-interfaces/src/main/resources/db/migration/` 新增 Flyway 脚本修改；已执行脚本不得改写。

MyBatis-Plus 的 PO、Mapper 和 MapStruct Converter 遵循已有模块样例。租户字段、数据权限、乐观锁和逻辑删除必须与现有拦截器顺序保持一致。

### 统一响应、异常与安全

接口返回统一 `Result<T>`，保持 `code`、`msg`、`data` 和 `traceId` 契约。错误码集中维护在 `apex-common` 的 `ResultCode`，消息使用 i18n key；禁止散落数字和裸 `RuntimeException`。新增错误码前先复用已有错误码，确有必要时按现有分类追加。

管理端点必须同时具备权限校验和菜单/权限种子。涉及写操作时检查幂等、防重放、审计、数据权限和租户上下文。消费者、缓存键和异步任务中的业务标识必须包含租户作用域，不能信任外部请求传入的 tenantId 覆盖服务端上下文。

认证授权相关实现集中在 interfaces 的 security、filter 和 aspect 代码中。修改 JWT、权限、验证码、限流或安全响应头时，先查找对应测试和配置，避免绕过现有过滤器链。

### 缓存、消息和限流

缓存遵循 L1 Caffeine、L2 Redis 和数据源回源的既有顺序；回源需要考虑 Single-Flight、空值标记、过期数据降级和阻塞线程隔离。Redis 分布式锁、幂等状态和缓存键不得跨租户复用。

RocketMQ 消费必须保留幂等、租户上下文、重试和失败状态语义。限流拒绝只能在确认监听器和消费框架会产生正确重试行为后接入。

限流算法的职责按现有实现划分：令牌桶用于受控突发和回源保护，漏桶用于平滑消费，滑动窗口用于进程内窗口计数和降级保护。分布式场景优先使用 Redis/Sentinel 等现有能力；本地算法只能作为单 JVM 保护或降级机制。新增算法必须补充边界、并发和时间窗口测试。

### 配置与外部依赖

配置优先放在现有 `application.yml` 或已有 profile 文件中。新增 `application-*.yml` 前必须确认它通过 profile、`spring.config.import` 或其他明确机制被加载，不能仅创建一个不会被读取的配置文件。密钥、密码、Token 和真实连接信息不得写入仓库；使用环境变量和 `.env.example`。

配置类使用已有的 `@ConfigurationProperties` 模式，避免同一参数同时存在硬编码、`@Value` 和配置类三种来源。修改配置时同步检查 basic、standard、enterprise 和 performance profile 的行为。

## 代码风格

- 遵循现有 Spotless 格式，改动后运行 `mvnd spotless:check`；需要修复格式时运行 `mvnd spotless:apply`。
- Service、Component 和配置类优先使用构造器注入与 Lombok `@RequiredArgsConstructor`。
- DTO、Request、Response 和 VO 使用现有 Lombok 模式；领域实体保持封装，通过领域方法修改状态。
- 不手写 Lombok 已覆盖的 getter、setter、构造器、`toString`、`equals` 和 `hashCode`。
- 禁止使用 `@SneakyThrows`、Lombok `val`、`var` 和 `@Synchronized`。
- public/protected 类、方法和字段按照项目 JavaDoc 规范补充中文说明；新增代码不要添加无关注释。
- 常量、枚举、错误码和配置键集中管理，避免魔法字符串。
- 保持包名为 `com.apex.scaffold.*`；example 模块遵循现有 `com.apex.example` 约定。
- 不引入新依赖，除非先确认根 POM 或目标模块没有可复用实现，并说明依赖必要性。

## 构建与验证

先加载项目环境：

```bash
cd /Users/work/apex-scaffold
sdk env
```

常用命令：

```bash
mvnd -pl apex-interfaces -am compile -DskipTests
mvnd clean test -P basic
mvnd -pl apex-common,apex-infrastructure -am test
mvnd spotless:check
mvnd pmd:check
mvnd clean package -DskipTests
```

需要外部服务的测试使用对应 profile，并先确认 MySQL、Redis、RocketMQ 或其他依赖已准备好。修改响应式、缓存、消息、租户、安全或架构代码时，优先运行受影响模块测试，再运行全量测试。

测试应覆盖实际行为，重点包括：

- 参数边界、异常和空值；
- 并发与幂等；
- Reactor 链路和阻塞线程隔离；
- 租户数据隔离；
- 缓存命中、回源、降级和 Single-Flight；
- 消费失败、重试和重复消息；
- 限流算法的拒绝、恢复和时间窗口边界。

## 文档与前端联动

架构和功能文档位于 `docs/`，新增或修改文档应链接到真实代码、配置或脚本路径，避免复制已经失效的版本和统计信息。Mermaid 图放在 `docs/diagrams/`，遵循该目录 README 的命名和验证约定。

管理后台是独立的 `/Users/work/apex-admin-web` 工程。新增菜单时，Flyway 中的 `component` 必须与前端 `VIEW_REGISTRY` 完全一致；接口 DTO 保持 camelCase，不能破坏 `Result` 解包契约。

## Git 与提交检查

不要擅自提交、回滚或删除用户已有改动。提交前按功能分组，避免把认证、业务代码、架构图和无关报告混在同一个提交中。不得提交 `.env`、密钥、证书、私钥、真实配置或构建产物。

提交前检查：

```bash
git status --short
git diff --check
mvnd spotless:check
mvnd -pl 受影响模块 -am test
git diff --cached --check
git status --short
```

提交信息使用 Conventional Commits，例如：

```text
feat(auth): add token refresh flow
docs: update architecture diagrams
fix(cache): handle stale data fallback
```

完成改动后报告实际运行的命令、测试范围、通过/失败结果和已知限制，不要把未执行的检查写成已通过。
