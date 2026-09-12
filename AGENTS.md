# AGENTS.md — Apex Scaffold

本文件定义本仓库的编码、验证和提交约定。修改前先检查实际代码和配置，遵循现有模式；不要为了满足规则进行无关重构。

## 项目结构

Apex Scaffold 是 Java 17、Spring Boot 3.5.15、Spring WebFlux 的 Maven 多模块项目，根项目版本与依赖版本以 [pom.xml](pom.xml) 为准，JDK 和 Maven Daemon 版本以 `.sdkmanrc` 为准。

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

接口层使用函数式 WebFlux 路由，路由位于 `apex-interfaces` 的 router 包，业务处理位于 handler 包。新增接口必须完成请求校验、路由注册和统一响应转换。

JDBC、MyBatis-Plus、同步 Redis、文件 I/O 和同步 HTTP 客户端都是阻塞操作，必须使用项目已有的 `BlockingIoSchedulers` 或专用调度器隔离。请求处理链中不得直接调用 `.block()`；消息监听器只有在现有同步消费边界明确允许时才能使用阻塞桥接。

优先使用 `Mono`、`Flux`、`Mono.defer`、`Mono.fromCallable` 和 `Mono.zip`。远程调用应设置超时，并按业务语义处理重试、降级和取消。

### DDD、数据库与租户

新增业务按以下顺序实现：

1. domain：聚合、值对象、领域服务、事件和 Repository 接口。
2. infrastructure：PO、Mapper、MapStruct Converter 和 RepositoryImpl。
3. application：用例编排、事务和 DTO/实体转换。
4. interfaces：Handler、Router、安全和响应转换。
5. 数据库变更：在 `apex-interfaces/src/main/resources/db/migration/` 追加 Flyway 脚本，禁止修改已执行脚本。

租户字段、数据权限、乐观锁、逻辑删除和拦截器顺序必须与现有实现一致。消费者、缓存键和异步任务的业务标识必须包含租户作用域，不能让外部请求覆盖服务端租户上下文。

### 响应、异常与安全

接口保持 `Result<T>` 的 `code`、`msg`、`data`、`traceId` 契约。错误码集中维护在 `apex-common` 的 `ResultCode`，消息使用 i18n key；优先使用已有 `BusinessException` 和错误码，禁止散落魔法数字和裸 `RuntimeException`。

管理端点必须具备权限校验，并在 Flyway 菜单/权限种子中登记。写操作应检查幂等、防重放、审计、数据权限和租户上下文。修改 JWT、OAuth2、Keycloak、权限、验证码、限流或安全响应头时，先查找现有 security、filter、aspect 实现和测试。

### 缓存、消息与限流

缓存遵循现有 L1 Caffeine、L2 Redis、数据源回源顺序。回源需要考虑 Single-Flight、空值标记、过期数据降级、分布式锁和阻塞线程隔离。

RocketMQ 消费必须保留租户上下文、幂等、失败状态和重试语义。只有确认消费框架能正确重试时，才能把限流拒绝接入消息失败路径。

令牌桶用于受控突发和缓存回源保护，漏桶用于平滑消费，滑动窗口用于进程内计数和降级保护。Redis、Sentinel 等分布式能力优先用于多实例场景；本地算法只承担单 JVM 保护或降级。新增限流行为要覆盖边界、并发、恢复和时间窗口测试。

## 配置与代码风格

配置优先放入实际会被加载的 `application.yml`、`application-{profile}.yml` 或通过 `spring.config.import` 明确导入的文件。当前资源中存在 `basic`、`dev`、`prod`、`standard`、`enterprise`、`performance` 等 profile；新增 profile 或独立配置文件前必须确认加载方式。

配置类优先使用 `@ConfigurationProperties` 和构造器注入，避免同一参数同时由硬编码、`@Value` 和配置类管理。密码、Token、证书、私钥和真实连接信息只能来自环境变量或本地未跟踪文件。

- 遵循 Spotless；修改后运行 `mvnd spotless:check`。
- Service、Component 和配置类优先使用 Lombok `@RequiredArgsConstructor`。
- DTO、Request、Response、VO 和领域实体遵循现有 Lombok 模式，不手写可由 Lombok 生成的样板代码。
- 禁止使用 `@SneakyThrows`、`val`、`var` 和 `@Synchronized`。
- public/protected 类、方法和字段补充符合项目规范的中文 JavaDoc；不要添加无关注释。
- 保持包名：主模块使用 `com.apex.scaffold.*`，example 使用现有 `com.apex.example`。
- 引入依赖前先检查根 POM和目标模块是否已有可复用能力。

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

- `scripts/build/`：构建环境和构建脚本。
- `scripts/test/`：测试和 JVM 诊断。
- `scripts/codegen/`：DDD 代码生成。
- `scripts/check_javadoc.sh`、`scripts/scan-javadoc-issues.sh`：JavaDoc 检查。
- `scripts/backup/`、`scripts/deploy/`、`scripts/diagnostics/`：备份、部署和诊断。
- `scripts/ci/`：文档一致性检查。

修改响应式、缓存、消息、安全、租户或架构代码时，先跑受影响模块测试，再根据变更范围运行全量测试。外部服务测试使用对应 profile，并先确认 MySQL、Redis、RocketMQ 等依赖已准备好。

测试重点包括参数边界、异常、并发、幂等、租户隔离、Reactor 链路、阻塞线程隔离、缓存回源与降级、消息重试以及限流时间窗口。

## 文档与前端

项目文档位于 `docs/`，应引用真实代码、配置和脚本路径，避免写入未经验证的版本、数量、耗时和完成状态。Mermaid 文件位于 `docs/diagrams/`，操作前阅读该目录 README 和相关验证脚本。

管理后台是独立工程 `/Users/work/apex-admin-web`。新增菜单时，Flyway 的 `component` 必须与前端 `VIEW_REGISTRY` 完全一致；接口 DTO 保持 camelCase，不破坏 `Result` 解包契约。

## Git 提交

不要擅自提交、回滚或删除用户已有改动。提交前按功能分组，认证、业务代码、架构图、报告和配置等不同主题分别提交。禁止提交 `.env`、密钥、证书、私钥、真实配置和构建产物。

提交前执行：

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

完成后只报告实际执行过的命令、测试范围、结果和已知限制。
