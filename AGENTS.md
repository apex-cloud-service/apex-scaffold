# AGENTS.md — Apex Scaffold

本文件定义仓库的开发、验证和协作约束。项目使用 Java 17、Spring Boot、Spring WebFlux 和 DDD，当前运行架构为网关加五个业务服务。修改前阅读目标模块的代码、POM、配置和测试，沿用现有模式；只完成任务需要的变更。

项目介绍与运行指引见 [README.md](README.md)。版本与聚合模块以根 [pom.xml](pom.xml) 为准，构建环境以 [.sdkmanrc](.sdkmanrc) 为准。历史文档、注释中的迁移阶段名称不能替代当前实现。本文件与 `.trae/rules/project_rules.md` 冲突时，以本文件为准。

## 构建环境

- JDK 必须为 SDKMAN `17.0.12-oracle`，mvnd 必须为 `2.0.0-rc-3`。
- 所有构建、测试、打包、依赖解析和发布前，在当前 shell 执行 `sdk env`，或加载 [构建初始化脚本](scripts/build/init-build-env.sh)。
- 不得依赖 `~/.sdkman/candidates/java/current`，该链接可能漂移到不兼容的 JDK。当前工作机如需显式 `JAVA_HOME`，使用 `/Users/eric/.sdkman/candidates/java/17.0.12-oracle`；其他机器使用本机对应的固定版本安装路径。
- 所有构建命令使用 `mvnd`。缺少 mvnd 时直接失败并提示安装，不得回退到系统 `mvn`。
- 此前使用过其他 JDK 的 daemon 时，先加载正确环境，再执行 `mvnd --stop` 清理，随后重新构建。

```bash
source scripts/build/init-build-env.sh
java -version
mvnd --version
mvnd -T4 -B clean verify
```

初始化脚本必须在当前 shell 中 `source`，仅启动子进程执行它不会切换父 shell 环境。

## 模块与物理边界

根 POM 聚合以下 20 个模块：

| 分类 | 模块及职责 |
| --- | --- |
| 共享基础 | `apex-common`：响应、异常、上下文、工具、调度器及通用持久化辅助 |
| 共享领域 | `apex-domain`：基础实体、通用事件与服务端口/Gateway 契约 |
| 共享设施 | `apex-infrastructure`：缓存、消息、Outbox、审计、加密、邮件、通用持久化配置和受管 HTTP 客户端 |
| 共享契约 | `apex-api-contract`：跨服务内部 API 与 DTO，无具体业务实体 |
| 服务父 POM | `apex-services-bom`：网关和五个业务服务的 parent |
| 业务库 | `apex-system`、`apex-auth`、`apex-workflow`、`apex-file`、`apex-office` |
| 运行模块 | `apex-gateway`、`apex-system-service`、`apex-auth-service`、`apex-workflow-service`、`apex-file-service`、`apex-office-service` |
| 授权模块 | `apex-license-spring-boot-starter`、`apex-license-tools` |
| 示例与工具 | `apex-example`、`apex-generator` |

### 当前依赖

下面列出直接内部生产依赖；不表示传递依赖或第三方依赖。

| 模块 | 直接内部依赖 |
| --- | --- |
| `apex-common` | 无 |
| `apex-domain` | common |
| `apex-infrastructure` | domain、common |
| `apex-api-contract` | common |
| `apex-system` | common、domain、infrastructure、license-spring-boot-starter |
| `apex-auth` | common、domain、infrastructure、api-contract |
| `apex-workflow`、`apex-file`、`apex-office` | common、domain |
| `apex-system-service` | common、domain、infrastructure、api-contract、system |
| `apex-auth-service` | common、domain、api-contract、auth |
| `apex-workflow-service` | common、domain、api-contract、workflow |
| `apex-file-service` | common、domain、file |
| `apex-office-service` | common、domain、api-contract、office |
| `apex-gateway` | common |
| `apex-example` | common、domain、infrastructure、system |

表内省略统一的 `apex-` 前缀。auth-service 对 system、example 对 workflow 的依赖仅为 test scope，不得据此引入生产耦合。新增依赖前检查目标 POM，不要机械套用“所有业务库依赖 infrastructure”的链条。

### system 归属

`apex-system` 已承接原 interfaces/application 源码、资源和测试，以及 system 专属 domain/infrastructure。Java 包名保持不变：

```text
apex-system/src/main/java/com/apex/scaffold/
├── interfaces/      Router、Handler、安全配置与接口适配
├── application/     用例、应用服务、DTO、Assembler
├── domain/          system 领域模型、服务与仓储端口
└── infrastructure/  system Mapper/PO、仓储与适配器
```

- `apex-interfaces`、`apex-application` 已退出 reactor；原 `apex-auth-center` 的当前入口是 auth 业务库及 auth-service。
- `apex-system-service` 负责启动与装配 system 库，不应重新复制其业务实现或迁移资源。
- `SystemServiceApplication` 显式扫描 systemservice、interfaces、application、domain、infrastructure、common，配置属性扫描根包，Mapper 扫描限定 `@Mapper`。调整扫描需检查类是否重复装配或遗漏。
- 共享 domain 只容纳可跨域复用的契约；system 专属实体、仓储、Mapper/PO 和适配器留在 system。
- common、共享 domain/infrastructure、api-contract 不得反向依赖 system 或其他具体业务库。
- HTTP 调用日志沿用共享 `ApiCallObservation` / `ApiCallLogSink` 契约，system 的持久化实现负责映射业务实体。

## 分层与实现方式

- 先在所属业务域定义领域模型与端口，再实现持久化/外部适配、应用用例和接口。不能把新增 system 业务默认放回共享 domain/infrastructure。
- Domain 不依赖 interfaces、具体 infrastructure 或 MyBatis-Plus；仓储接口与实现分离。
- Application 编排用例并调用领域端口，Interfaces 处理协议、输入验证与响应映射。沿用既有查询服务例外时控制范围，不将其推广成任意跨层依赖。
- Infrastructure 实现端口，不依赖 interfaces。网关不承载业务四层实现，不引入 Servlet 栈。
- 使用构造器注入，可沿用 `@RequiredArgsConstructor`；禁止字段或 setter 注入。
- DTO/实体/PO 转换优先沿用目标模块现有 MapStruct Assembler/Converter，Lombok 用法保持局部一致。
- 注释解释非直观业务规则、协议与约束，避免为简单代码添加样板注释。

### Router 与 Handler

- 新增业务 HTTP 接口采用 WebFlux 函数式 Router + Handler，不新增 `@Controller`、`@RestController` 或注解式请求映射。
- system Router/Handler 位于 system 的 `interfaces/router`、`interfaces/handler` 包；其他服务使用各自 service 模块的接口层。
- Router 注册路径与方法，Handler 验证请求并调用应用服务，不在 Handler 中堆积持久化或核心业务规则。
- 沿用目标模块现有验证工具和 Jakarta Validation。`@NotBlank` 只校验非空白，不会自动修剪或修改输入值。
- Router 的 `@RouterOperation` / `@RouterOperations`、DTO 的 `@Schema` 等 SpringDoc 元数据与实际路径、方法、参数和响应一致。
- 管理权限与 `@RequirePermission`、数据权限规则、Flyway 权限种子保持一致。写操作沿用 `@OperationLog`；幂等、防重放、限流等按业务语义配置。
- 路径以实际 Router 与网关配置为准，不能只按 `/api/v1/` 等前缀推断接口归属。

### 响应、异常与国际化

- 常规业务 JSON 响应沿用 `Result<T>`：`code`、`msg`、`data`、`traceId`。`ResultCode.SUCCESS` 为 **0**，不是 HTTP 200。
- OAuth2、JWKS、文件流、健康检查等已有协议响应保持原契约，不强行改为业务包装。
- 业务异常使用现有 `BusinessException` / `ResultCode`，技术异常交给已有 WebFlux 异常边界；认证与授权异常沿用安全处理器。不要吞掉错误或把失败转换成无依据的成功。
- 新增面向用户的消息同步维护所属模块的 i18n 资源。system 资源位于 `apex-system/src/main/resources/i18n/`，不得使用旧 interfaces 模块路径。
- DTO 字段和序列化方式保持接口兼容。分页存在多份 `PageResponse`：common 版本有 `hasNext`，system/file 版本没有；common 还有 `records` 访问器。修改前检查实际使用类型、序列化结果和契约测试，不假定全仓字段统一。
- 日期/时间格式遵循目标 DTO 与序列化配置，不擅自切换字符串、毫秒时间戳或时区语义。

## 阻塞 I/O 与事务

JDBC、MyBatis-Plus、同步 Redis/Redisson、文件 I/O、同步存储 SDK 和同步 HTTP 客户端必须隔离到 [BlockingIoSchedulers](apex-common/src/main/java/com/apex/scaffold/common/scheduler/BlockingIoSchedulers.java)。

| 调度器 | 用途 |
| --- | --- |
| `jdbc()` | JDBC / MyBatis 数据库访问 |
| `cache()` | 同步缓存与分布式锁 |
| `storage()` | 文件与对象存储 |
| `event()` | 独立事件、Outbox 或日志持久化任务 |
| `remote()` | 同步远程 SDK / HTTP 调用 |
| `general()` | 无专属分类的阻塞后台任务 |

- 将同步调用延迟到订阅时执行，并在调用边界使用 `subscribeOn`；仅在下游 `publishOn` 不能隔离此前已经执行的阻塞调用。
- 不直接使用 `Schedulers.boundedElastic()` 绕过专用池。原生异步 WebClient 等无须为了形式统一切到阻塞池。
- Handler 请求链不使用 `.block()` 或手动 `.subscribe()`；同步消息消费等桥接边界按既有实现处理。
- 超时、重试、降级和取消需要匹配业务语义，写操作重试需考虑幂等性。

典型同步访问边界：

```java
Mono.fromCallable(() -> repository.findById(id))
    .subscribeOn(BlockingIoSchedulers.jdbc());
```

### 事务边界

- MyBatis/JDBC 的多步写操作使用同一同步事务，例如 `TransactionTemplate` 回调，或独立 Spring Bean 上的同步 `@Transactional` 方法；在 jdbc 池执行整个同步事务。
- 不给返回 `Mono` / `Flux` 的方法直接添加 JDBC `@Transactional` 并假定异步操作受保护；避免自调用绕过事务代理。
- `TransactionalOperator` 只在真实响应式事务管理器与对应数据访问匹配时提供事务语义。no-op 管理器不提供原子性，不能保护 JDBC 写操作。
- 与业务写同事务的 Outbox 写入必须保留在同一事务与线程边界，不能拆到 event 池。可参考共享 `TransactionalOutboxExecutor` 和 system 的同步事务实现。
- 事务中不包含远程调用或无关长耗时操作。多服务一致性采用已有事件/补偿模式，不假定跨库本地事务。

## 持久化、迁移与租户隔离

### Flyway 资源归属

| 业务域 | 生产迁移目录 |
| --- | --- |
| system | `apex-system/src/main/resources/db/migration/{mysql,postgresql}/` |
| auth | `apex-auth-service/src/main/resources/db/migration/{mysql,postgresql}/` |
| workflow | `apex-workflow-service/src/main/resources/db/migration/{mysql,postgresql}/` |
| file | `apex-file-service/src/main/resources/db/migration/{mysql,postgresql}/` |
| office | `apex-office-service/src/main/resources/db/migration/{mysql,postgresql}/` |

- system-service 通过 system 依赖加载生产迁移；各域的数据库和版本链独立，不能按全仓最大版本分配新版本。
- 命名使用 `V{版本号}__{描述}.sql`。先读取所属域当前脚本，再追加版本，不修改、删除、重排已经执行的历史迁移。
- system 历史 SQL 中仍可能包含已拆服务的表，不能为了整理模块而改写历史。
- MySQL/PostgreSQL 版本需配对维护，并核对语义与语法。版本集合一致测试不能证明两种 SQL 语义相同。
- Flyway 版本迁移由历史表管理一次性执行；不要将“所有 DDL 必须可无限重复执行”作为统一要求。需要重入、数据回填或安全重试时明确设计。
- 权限与菜单变更同步维护两套种子数据，权限编码与接口要求一致；前端组件字段遵循已有契约。
- MySQL 业务时间字段使用 `DATETIME`，PostgreSQL 使用适当的 `TIMESTAMP` 类型，Java 持久化时间字段沿用 `LocalDateTime`。禁止用 32 位秒时间戳或硬编码远期日期表示永久有效。
- 乐观锁、逻辑删除、租户字段与拦截器顺序遵循现有持久化配置。

### 租户与安全

- 租户作用域来自受信任认证上下文，不允许客户端任意覆盖。跨异步链路沿用项目的 Reactor Context 与上下文桥接方式。
- 租户相关缓存键、消息与后台任务携带租户作用域；更新、删除和批量访问同样验证租户及数据权限。
- 不通过 `@InterceptorIgnore` 绕过租户隔离；确有跨租户管理需求时使用已有受控入口与审计机制。
- 网关和服务端各自遵守当前鉴权职责，内部接口不得因有网关而无条件公开。
- JWT、加密密钥、外部凭据通过配置注入，保留现有长度、格式、模板值拒绝及生产校验；不得绕过 License 验证。
- 日志包含必要 traceId 与业务上下文，不输出密码、完整令牌、密钥或敏感个人数据。审计与普通应用日志分别沿用已有实现。

## 配置、运行与脚本

- Maven `basic/standard/enterprise` 是构建档位，默认 standard；Spring `dev/it/prod` 与档位组合是运行配置；Compose profile 选择容器。不得混淆或假定自动联动。
- system basic 仍使用 MySQL、Redis，数据库使用 Flyway 迁移。公共配置启用 Flyway，但 system 库的 dev 配置关闭自动迁移；首次空库启动需明确启用或先完成迁移。其他服务的 H2 basic 方案不代表全栈离线。
- 配置变更核对对应服务的 `application*.yml`、`bootstrap.yml`、`.env.example` 与部署注入；必要时同步 [配置文档](docs/05-operations/configuration.md)。
- Java 不自动加载 `.env`；部署 app.sh 只在 `APEX_ENV_FILE` 指定有效文件时 source；Compose `.env` 插值不等于全部变量注入容器。
- 关闭 Nacos 时核对 config/discovery/register 开关；网关 `lb://` 路由同时改为明确 HTTP 地址。
- system 默认业务端口 8082、管理端口 8083；workflow 同机开发可用 8093 避开冲突。example 与 gateway 默认均为 8080。
- Compose 部署核对外部 `apex-observability` 网络、前端镜像、数据库/Redis 凭据、生产 CORS 和 auth JDBC/R2DBC TLS 配置，不把模板复制描述为可直接上线。
- 已用于持久数据的加密密钥需要稳定保存，轮换配合迁移，不能每次启动随机重建。
- 新增 Bash 脚本复用 [公共函数库](scripts/lib/common.sh)，使用其根目录、日志与安全查找函数，不重复手写排除规则。修改旧脚本只处理当前任务所需内容。
- 构建脚本使用统一环境初始化并明确失败，不静默回退工具或忽略错误。路径正确引用，敏感值不写日志。

## 验证门禁

按变更选择有意义的验证，报告实际执行的范围。只改文档时检查事实、相对链接、锚点、命令与 diff；不宣称已运行 Java 回归。

### 常用命令

以下命令在根目录、已激活指定构建环境的 shell 中执行：

```bash
# 受影响模块及其依赖的单元/架构测试
mvnd -T4 -B -pl apex-system-service -am test

# 格式检查；应用格式化前检查差异范围
mvnd -T4 -B spotless:check

# 完整回归门禁
mvnd -T4 -B clean verify

# 所有变更都检查空白错误
git diff --check
```

- `test` 是快速验证，不包含完整 Failsafe 门禁；`-DskipTests` 打包不构成测试结果。
- 默认 Failsafe 包含 `*IntegrationTest`、`*E2ETest` 和 `FlywayMigrationStartupTest`，绑定 integration-test/verify，无需虚构 `integration-test` Maven profile。
- Spotless、PMD/p3c、JaCoCo 检查绑定 verify。父 POM 默认 JaCoCo BUNDLE 门槛为行覆盖 60%、分支覆盖 50%，实际生效范围、排除项及模块覆盖以有效配置与报告为准。
- `remote-it`、`stress-tests` 等额外场景按对应配置准备依赖，不能用其结果替代默认完整门禁。
- 完整通过标准为反应堆全部模块 `BUILD SUCCESS`，测试 `Failures: 0 / Errors: 0`。
- 推荐 `-T4`，不要使用过高并行度如 `-T1C`。真实 Redis/HTTPS 的计时敏感测试可能因负载超时；出现此类失败应分析日志，必要时重跑或降低并行度，不改弱业务 SLA 来掩盖问题。

### 已有架构检查的实际范围

| 检查 | 主要覆盖 |
| --- | --- |
| system `LayeringArchTest` | domain/application/infrastructure 分层；部分 QueryService/StatisticsService/LogService 有例外；方法级事务返回类型限制 |
| infrastructure `TenantIsolationArchTest` | persistence 范围的 `@InterceptorIgnore` 禁用 |
| infrastructure `TimeFieldArchTest` | 指定命名时间字段的类型 |
| gateway `GatewayArchTest` | 网关包边界、业务层与 Servlet 依赖限制 |
| auth `PersistenceContractTest` | auth domain 与持久化边界及相关契约 |
| system `NoBoundedElasticArchGuardTest` | 指定共享/system 源码目录的直接 boundedElastic 使用检查 |
| system-service `SystemServiceBoundaryTest` | 扫描声明与类可加载性，不是实际服务启动 |
| system `FlywayMigrationVersionTest` | MySQL/PostgreSQL 版本集合一致与 system 版本连续性 |

这些检查只覆盖其声明的范围，不证明全仓调度器、所有租户缓存键、SQL 语义或运行服务均正确。业务变更还需验证实际调用链、失败路径、事务回滚与权限行为。

## 文档与协作

- 只修改任务相关内容，保留用户已有改动，不擅自回滚、删除或进行无关重构。
- 不主动创建 README、报告或其他文档；需要维护现有文档时引用真实路径，不写未经验证的性能、数量、默认账号和完成状态。
- 当前架构与命令不要引用已退役模块。API、配置和模块边界变化同步更新相关文档；图表以实际实现为准。
- 管理后台是独立项目，后端不生成或修改不存在的前端目录。菜单组件字段和 API 契约仍需与前端保持一致。
- `scripts/ci/check-doc-links.sh` 离线扫描全仓 Markdown（含根 README/AGENTS 和模块文档），校验本地路径及 Markdown 标题/HTML 锚点；排除构建产物、工具配置与依赖目录，不验证外部 URL 或非 Markdown 文件内部位置。
- 未获用户明确要求，不创建 Git commit 或推送。提交前检查实际 diff 与暂存范围，不混入其他主题。
- 不提交 `.env`、真实配置、密钥、证书、私钥或构建产物。需要提交时使用 Conventional Commits，按变更目的选择 type/scope。
- 基础版与标准版/企业版的许可范围分别见 [LICENSE](LICENSE)、[LICENSE-COMMERCIAL.md](LICENSE-COMMERCIAL.md)，不要将整个仓库描述为统一 MIT 或 Apache 授权。
- 完成时说明改动、实际检查结果及未验证的运行条件，不能将静态核对写成启动或完整门禁通过。
