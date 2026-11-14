# AGENTS.md — Apex Scaffold

本文件定义仓库的开发、验证和协作约束。项目使用 Java 21、Spring Boot、Spring WebFlux 和 DDD，当前运行架构为网关加六个业务服务（system/auth/workflow/file/office/integration）、只读查询聚合服务，以及任务代理、可选脱敏服务与可选知识库服务（system/auth/workflow/file/office/integration 七个业务库，2026-11-14 新增知识库）。修改前阅读目标模块的代码、POM、配置和测试，沿用现有模式；只完成任务需要的变更。

项目介绍与运行指引见 [README.md](README.md)。版本与聚合模块以根 [pom.xml](pom.xml) 为准，构建环境以 [.sdkmanrc](.sdkmanrc) 为准。历史文档、注释中的迁移阶段名称不能替代当前实现。本文件是仓库协作约束的唯一权威来源；`.trae/rules/project_rules.md` 只是部分编辑器的本地镜像，不入版本库，也不得与本文件冲突。

## 构建环境

- JDK 必须为 SDKMAN `21.0.13.0+9-tem`，mvnd 必须为 `2.0.0-rc-3`。
- 所有构建、测试、打包、依赖解析和发布前，在当前 shell 执行 `sdk env`，或加载 [构建初始化脚本](scripts/build/init-build-env.sh)。
- 不得依赖 `~/.sdkman/candidates/java/current`，该链接可能漂移到不兼容的 JDK。如需显式设置 `JAVA_HOME`，使用固定版本安装路径 `$HOME/.sdkman/candidates/java/21.0.13.0+9-tem`，或直接用 `sdk env` 后的 `JAVA_HOME`。
- 所有构建命令使用 `mvnd`。缺少 mvnd 时直接失败并提示安装，不得回退到系统 `mvn`。
- 此前使用过其他 JDK 的 daemon 时，先加载正确环境，再执行 `mvnd --stop` 清理，随后重新构建。
- 本地仓库固定为 `~/.m2/repository`。不得使用 `-Dmaven.repo.local` 指向项目内目录（尤其 `target/`）：`clean` 会连同产物一起删除，且非标准分层的目录（如 `com.apex.scaffold` 写成单级目录）无法被 Maven 解析，只会留下整份 JAR 副本。
- 构建产物只保留在各模块 `target/`，并由 `.gitignore`、`.containerignore`、`.zcodeignore` 统一忽略。`consumer-*.pom`、`resume.properties`、`failsafe-reports/failsafe-summary.xml` 是构建中断留下的临时残渣，确认后可清除，不入库、不进发布包。

```bash
source scripts/build/init-build-env.sh
java -version
mvnd --version
mvnd -T4 -B clean verify
```

初始化脚本必须在当前 shell 中 `source`，仅启动子进程执行它不会切换父 shell 环境。

## 模块与物理边界

根 POM 直接聚合七个管理组；递归聚合 44 个模块，含根 POM 的全量 reactor 为 45 个项目（8 个 POM、37 个 JAR）。2026-11-14 完成第二阶段物理归位：37 个叶子模块目录已从仓库根级移入各自分组父目录之下（如 `apex-components-parent/apex-system`），分组 POM 以 `<module>apex-xxx</module>` 聚合同目录叶子，不再使用 `../apex-xxx` 跨目录聚合；模块坐标、实际生产依赖、业务接口、服务与端口不变。后续新增项目放入所属分组父目录并登记，以实际递归清单计数，不把 44 / 45 固定为永久门槛。

### Maven 分组与继承

| 根直接聚合的管理组 | 聚合叶子数 | 职责 |
| --- | ---: | --- |
| `apex-shared-parent` | 9 | common、共享 domain、api-contract、五个 infrastructure 能力 JAR 和旧坐标兼容 JAR |
| `apex-components-parent` | 6 | system/auth/workflow/file/office/integration 六个脚手架能力库 |
| `apex-starters-parent` | 4 | storage/license/job/seata starter |
| `apex-services-bom` | 11 | 八个标准发布运行模块及 job/masking/knowledge 三个可选运行模块 |
| `apex-applications-parent` | 2 | example 与 boot-all 装配 |
| `apex-tools-parent` | 4 | license-tools、generator、tools-deployment、tools-build |
| `apex-business-parent` | 1 | 项目专属业务 JAR 库聚合；当前含知识库 `apex-knowledge`，无独立运行模块在此登记 |

- 聚合、parent 继承、物理目录、生产依赖和运行部署是不同关系，完整模块映射见 [README 分组说明](README.md#maven-分组与父链)。
- 第一阶段 22 个既有叶子的直接 parent 改为各自新分组；13 个 `apex-services-bom` 子项目保持继承。其中 api-contract 聚合在 shared、integration 聚合在 components、boot-all 聚合在 applications，10 个运行模块的聚合与继承均在 services；不为外观一致而消除三个例外或扩大服务 BOM 管理范围。
- 第二阶段（2026-11-14）37 个叶子目录已物理移入所属分组父目录之下；三个例外叶子按聚合归属落位，`relativePath` 跨两级指向 `../../apex-services-bom/pom.xml`，其余叶子使用默认 `../pom.xml`。
- 根保留统一版本、构建档位与质量门禁；分组 parent 只管理配置与聚合，不自动注入整套设施依赖或激活 Boot repackage / Shade。叶子依赖、scope、optional、exclusions 和打包行为仍按各 POM 保留。
- business 只实现项目专属模型、规则、用例、领域端口、业务适配及配置；通用框架由 shared / components / starters 等支撑模块提供，不能复制安全、数据源、缓存、消息、日志或调度框架。领域专属仓储、Mapper / PO 留在业务库 infrastructure 层。
- 新业务库直接继承并聚合到 business；若新建独立运行模块，其唯一 parent 与聚合归属为 services，不在 business 重复登记。不自动装配全部项目业务或扩大八服务发布清单，具体模块与运行形态须另行确认。
- 新增模块只在所属分组 `<modules>` 登记一次，根只登记顶层分组；同步注册表和容器 POM 收集清单、相关测试与有效文档。注册表对齐从根沿 `<modules>` 递归得到的全部后代项目（含空 parent、不含总根），不是根直接七项或 `<parent>` 继承树。
- 构建仍从总根加载固定环境后使用 `mvnd -pl :<artifactId 列表> -am`（冒号按坐标匹配，与两级物理目录解耦）；选择 parent 加 `-am` 不等于构建其全部聚合叶子。构件安装或未来发布须包含根与所需 parent POM；分组不授权远程发布，也不承诺性能提升。

以下按既有能力列出模块职责，不改变生产依赖边界。

| 分类 | 模块及职责 |
| --- | --- |
| 共享基础 | `apex-common`：响应、异常、上下文、工具、调度器及通用持久化辅助 |
| 共享领域 | `apex-domain`：基础实体、通用事件与服务端口/Gateway 契约 |
| 共享设施 | `apex-infrastructure-persistence`、`apex-infrastructure-cache`、`apex-infrastructure-messaging`、`apex-infrastructure-remote`、`apex-infrastructure-integration`：分别提供持久化、缓存、消息、远程调用和第三方集成能力；`apex-infrastructure` 仅保留旧坐标兼容，不再承载实现源码 |
| 共享契约 | `apex-api-contract`：跨服务内部 API 与 DTO，无具体业务实体 |
| 服务父 POM | `apex-services-bom`：聚合十个运行模块，保留其及 api-contract/integration/boot-all 三个例外的直接 parent 继承与原依赖管理 |
| 业务库 | `apex-system`、`apex-auth`、`apex-workflow`、`apex-file`、`apex-office`、`apex-integration`：`apex-integration` 含 connector（连接器/租户凭据/出站调用/入站 webhook）与 device（设备注册/影子/遥测/命令/规则）两个包结构隔离的子域；`apex-knowledge`：知识空间/文档摄取（Tika 解析/分块/向量化）/Meilisearch hybrid 检索/RAG 问答编排，能力级降级（索引/向量化/问答各自独立开关） |
| 运行模块 | `apex-gateway`、`apex-system-service`、`apex-auth-service`、`apex-workflow-service`、`apex-file-service`、`apex-office-service`、`apex-integration-service`、`apex-aggregation-service`（只读查询聚合，无 DB/迁移/事务，仅通过 WebClient 聚合 workflow 与 office 的内部接口）、`apex-knowledge-service`（知识库可选运行模块，独立 apex_knowledge 库，端口 8100） |
| 组合部署 | `apex-boot-all`：basic/示例/演示档单进程装配全部业务组件，仅装配不含业务实现 |
| 服务增强 | `apex-job-spring-boot-starter`：默认禁用的 XXL 执行器；`apex-seata-spring-boot-starter`：默认禁用的多模式分布式事务（`apex.seata.mode` 选择 AT/TCC/SAGA/XA，缺省 AT）；`apex-job-service`：固定 system 代理；`apex-masking-service`：无持久化的脱敏接口 |
| 对象存储 | `apex-storage-spring-boot-starter`：无业务域依赖的五后端存储自动装配 |
| 授权模块 | `apex-license-spring-boot-starter`、`apex-license-tools` |
| 示例与工具 | `apex-example`、`apex-generator` |
| 运维工具 | `apex-tools-deployment`：离线部署密钥/配置校验、TOTP 敏感字段迁移包生成与回滚、MySQL V42 结构恢复、文档链接校验与本地文档静态服务；仅由 `scripts/utils/`、`scripts/ci/check-doc-links.sh` 脚本与 Compose `security-key-check` 以 fat jar 调用，不参与服务运行期装配 |
| 构建管理 | `apex-tools-build`：全部模块注册表 `build-manifest.yaml`（单一事实来源，`BuildManifestSyncTest` 校验其与根 POM 递归 modules 集合（不含总根）、各模块打包插件/mainClass、release.sh SERVICES 一致）与编译/构建/打包/发布/镜像/部署统一编排入口 `bin/apex.sh`（只做编排，委托既有脚本与 mvnd 执行；部署覆盖 java -jar、Podman Compose、Docker Compose、K8s 四种运行时，K8s 为 fail-closed 编排）；不参与服务运行期装配，不进交付发布包 |

### 当前依赖

下面列出直接内部生产依赖；不表示传递依赖或第三方依赖。

| 模块 | 直接内部依赖 |
| --- | --- |
| `apex-common` | 无 |
| `apex-domain` | common |
| `apex-infrastructure-persistence` | common、domain |
| `apex-infrastructure-cache` | common、domain |
| `apex-infrastructure-messaging` | common、domain、infrastructure-persistence |
| `apex-infrastructure-remote` | common、domain |
| `apex-infrastructure-integration` | common、domain、infrastructure-remote；缓存仅 test scope |
| `apex-infrastructure` | 五个能力 JAR，仅兼容旧坐标 |
| `apex-api-contract` | common |
| `apex-system` | common、domain、api-contract、五个 infrastructure 能力 JAR、license-spring-boot-starter |
| `apex-auth` | common、domain、infrastructure-integration、api-contract |
| `apex-workflow`、`apex-office` | common、domain |
| `apex-file` | common、domain、storage-spring-boot-starter |
| `apex-integration` | common、domain、infrastructure-persistence、infrastructure-remote；MQTT 客户端 hivemq-mqtt-client 为 optional |
| `apex-knowledge` | common、infrastructure-persistence、storage-spring-boot-starter；Tika（文本解析）与 meilisearch-java（索引写入）为第三方依赖 |
| `apex-storage-spring-boot-starter` | common |
| `apex-system-service` | common、domain、api-contract、system、job-spring-boot-starter、seata-spring-boot-starter（能力由 system 装配） |
| `apex-auth-service` | common、domain、api-contract、auth、infrastructure-cache、infrastructure-messaging、infrastructure-integration、job-spring-boot-starter、seata-spring-boot-starter |
| `apex-workflow-service` | common、domain、api-contract、workflow、job-spring-boot-starter、seata-spring-boot-starter |
| `apex-file-service` | common、domain、file、job-spring-boot-starter、seata-spring-boot-starter |
| `apex-office-service` | common、domain、api-contract、office、job-spring-boot-starter、seata-spring-boot-starter |
| `apex-integration-service` | common、domain、integration、job-spring-boot-starter、seata-spring-boot-starter |
| `apex-aggregation-service` | common、api-contract |
| `apex-knowledge-service` | common、knowledge |
| `apex-job-service` | infrastructure-remote |
| `apex-masking-service`、两个增强 starter | common |
| `apex-gateway` | common |
| `apex-boot-all` | common、domain、api-contract、system、auth、workflow、file、office、integration |
| `apex-example` | common、domain、infrastructure-cache、infrastructure-integration、system |
| `apex-tools-deployment` | 无内部依赖；仅 Jackson、SnakeYAML、CommonMark（本地文档 Markdown 渲染）与 JUnit |
| `apex-tools-build` | 无内部依赖；仅测试 scope 使用 SnakeYAML（解析注册表）与 JUnit |

表内省略统一的 `apex-` 前缀。auth-service 对 system 的依赖仅为 test scope，不得据此引入生产耦合。新增依赖前检查目标 POM，不要机械套用“所有业务库依赖 infrastructure”的链条。

### 服务增强边界

- 六个业务 service（system/auth/workflow/file/office/integration）均直接依赖 job 与 seata starter；job-service 仅直接内部依赖 infrastructure-remote，masking-service 与两个 starter 仅直接内部依赖 common；knowledge-service 仅直接内部依赖 common 与 knowledge（可选模块不接 job/seata）。
- 集成服务与 `apex-integration` 的对外集成、设备接入一律使用 `WebClient`（HTTP），不引入 Feign；MQTT 客户端依赖 optional，classpath 无该依赖时零 Bean，生产由外部 EMQX 提供接入。
- job-service 不迁移 system 数据所有权；只代理九个任务接口。XXL 仅接管配置的一个 system 任务及 workflow 超时扫描，中心 cron 人工同步。
- 脱敏策略为启动配置，无 CRUD 或热刷新；租户来自已验证 JWT。
- Seata 默认关闭，通过 `apex.seata.mode` 选择 AT（缺省）/TCC/SAGA/XA 四种模式；AT/SAGA/XA 要求同步单 JDBC 数据源，拒绝动态数据源与 Publisher 回调；全局事务内拒绝当前 Outbox 写入。AT 需各业务库 undo_log，SAGA 需存储三表（建表脚本随 starter 发布），XA 依赖数据库 XA 能力；TCC 由扫描器代理 `@LocalTCC` 动作。缺省 AT 与既有接入行为兼容。真实 TC 环境下四模式端到端测试缺少 TC/双库时跳过，不能记为通过。

### system 归属

`apex-system` 已承接原 interfaces/application 源码、资源和测试，以及 system 专属 domain/infrastructure。Java 包名保持不变：

```text
apex-components-parent/apex-system/src/main/java/com/apex/scaffold/
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
- 协议层（Router/Handler）归属按域区分：auth/file/office/workflow 位于各自 service 运行模块；system 位于业务库 `interfaces/router`、`interfaces/handler` 包（D07 裁决维持现状）；integration 为 C7 自含组件试点，协议层位于业务库 `connector/interfaces`、`device/interfaces`，经 `apex.component.integration.interfaces-enabled` 开关守护，integration-service 只承载安全与装配。新增服务默认采用运行模块形态；需要"引依赖即含 HTTP 能力"的自含组件时参照 integration。
- Router 注册路径与方法，Handler 验证请求并调用应用服务，不在 Handler 中堆积持久化或核心业务规则。
- 沿用目标模块现有验证工具和 Jakarta Validation。`@NotBlank` 只校验非空白，不会自动修剪或修改输入值。
- Router 的 `@RouterOperation` / `@RouterOperations`、DTO 的 `@Schema` 等 SpringDoc 元数据与实际路径、方法、参数和响应一致。
- 管理权限与 `@RequirePermission`、数据权限规则、Flyway 权限种子保持一致。写操作沿用 `@OperationLog`；幂等、防重放、限流等按业务语义配置。
- 路径以实际 Router 与网关配置为准，不能只按 `/api/v1/` 等前缀推断接口归属。
- office 站内信的 HTTP 消息路由与 WebSocket 握手路由分别使用 `ROUTE_OFFICE_MESSAGE_URI`、`ROUTE_OFFICE_WS_URI`；两者缺省与 office 主路由同为 `lb://apex-office-service`，关闭 Nacos 的直连部署须显式注入且指向同一 office 实例。票据和连接注册表是单实例内存状态，不提供跨实例实时广播，断线须通过收件箱与未读数 HTTP 补偿。

### 响应、异常与国际化

- 常规业务 JSON 响应沿用 `Result<T>`：`code`、`msg`、`data`、`traceId`。`ResultCode.SUCCESS` 为 **0**，不是 HTTP 200。
- OAuth2、JWKS、文件流、健康检查等已有协议响应保持原契约，不强行改为业务包装。
- 业务异常使用现有 `BusinessException` / `ResultCode`，技术异常交给已有 WebFlux 异常边界；认证与授权异常沿用安全处理器。不要吞掉错误或把失败转换成无依据的成功。
- 新增面向用户的消息同步维护所属模块的 i18n 资源。system 资源位于 `apex-components-parent/apex-system/src/main/resources/i18n/`，不得使用旧 interfaces 模块路径。
- DTO 字段和序列化方式保持接口兼容。分页存在多份 `PageResponse`：system/file 版本已新增派生 `hasNext`，字段集与 common 一致；common 还额外提供 `records` 访问器（`list` 的序列化别名），system/file 不引入。修改前检查实际使用类型、序列化结果和契约测试，不假定全仓字段统一。
- 日期/时间格式遵循目标 DTO 与序列化配置，不擅自切换字符串、毫秒时间戳或时区语义。

## 阻塞 I/O 与事务

JDBC、MyBatis-Plus、同步 Redis/Redisson、文件 I/O、同步存储 SDK 和同步 HTTP 客户端必须隔离到 [BlockingIoSchedulers](apex-shared-parent/apex-common/src/main/java/com/apex/scaffold/common/scheduler/BlockingIoSchedulers.java)。

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
- 各池可通过 `apex.blocking-io.<pool>.virtual-threads` 单独启用虚拟线程执行器，默认关闭。启用后 `threads` 语义变为默认并发上限，`max-concurrency` 是真正的并发闸门，必须与数据库连接池、下游配额等稀缺资源对齐，不能按 CPU 核数放大；`jdbc` 池还要确认驱动未用 `synchronized` 包裹阻塞 I/O，否则会钉住载体线程。开关语义与灰度建议见 [配置文档](docs/05-operations/configuration.md)。
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

<a id="持久化迁移与租户隔离"></a>

## 持久化、迁移与租户隔离

### Flyway 资源归属

| 业务域 | 生产迁移目录 |
| --- | --- |
| system | `apex-components-parent/apex-system/src/main/resources/db/migration/{mysql,postgresql}/` |
| auth | `apex-services-bom/apex-auth-service/src/main/resources/db/migration/{mysql,postgresql}/` |
| workflow | `apex-services-bom/apex-workflow-service/src/main/resources/db/migration/{mysql,postgresql}/` |
| file | `apex-services-bom/apex-file-service/src/main/resources/db/migration/{mysql,postgresql}/` |
| office | `apex-services-bom/apex-office-service/src/main/resources/db/migration/{mysql,postgresql}/` |
| integration | `apex-components-parent/apex-integration/src/main/resources/db/migration/{mysql,postgresql}/` |

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
- 多租户行级隔离拦截器（`DefaultTenantLineHandler`）默认开启（`apex.tenant.enabled:true`，basic 档除外）；
  workflow 与 auth 为 JdbcTemplate 持久化，维持手写 `WorkflowTenantAccess` / `JdbcTenantScope` fail-closed 例外口径。
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
- system 默认业务端口 8082、管理端口 8083；workflow 默认业务端口 8093（2026-11-07 由 8083 调整，消除与 system 管理端口冲突）；集成服务默认业务端口 8094、管理端口 8097；只读查询聚合服务默认业务端口 8098；知识库服务默认业务端口 8100（XXL 控制台占用宿主 8099，故跳过）。example 与 gateway 默认均为 8080。
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
mvnd -T4 -B -pl :apex-system-service -am test

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
- `*DualDatabaseMigrationIntegrationTest`（system / integration / office / knowledge 四域）以 Testcontainers 拉起真实 MySQL 9 + PostgreSQL 15，属于默认 Failsafe 门禁。`-T4` 下多个模块同时拉容器可能因资源竞争出现 `ContainerLaunchException`/连接超时；此类失败属环境问题，单独重跑该模块（`mvnd -T1 -pl <module> integration-test -Dit.test='*DualDatabaseMigrationIntegrationTest'`）或整体降为 `-T1` 复核，不得据单次并发失败判定迁移脚本缺陷。

### 已有架构检查的实际范围

| 检查 | 主要覆盖 |
| --- | --- |
| system `LayeringArchTest` | domain/application/infrastructure 分层；部分 QueryService/StatisticsService/LogService 有例外；方法级事务返回类型限制 |
| infrastructure-persistence `TenantIsolationArchTest` | persistence 范围的 `@InterceptorIgnore` 禁用 |
| infrastructure-persistence `TimeFieldArchTest` | 指定命名时间字段的类型 |
| gateway `GatewayArchTest` | 网关包边界、业务层与 Servlet 依赖限制 |
| auth `PersistenceContractTest` | auth domain 与持久化边界及相关契约 |
| system `NoBoundedElasticArchGuardTest` | 指定共享/system 源码目录的直接 boundedElastic 使用检查 |
| system-service `SystemServiceBoundaryTest` | 扫描声明与类可加载性，不是实际服务启动 |
| integration-service `IntegrationServiceBoundaryTest` | 扫描声明与类可加载性，不是实际服务启动 |
| system `FlywayMigrationVersionTest` | MySQL/PostgreSQL 版本集合一致与 system 版本连续性 |

这些检查只覆盖其声明的范围，不证明全仓调度器、所有租户缓存键、SQL 语义或运行服务均正确。业务变更还需验证实际调用链、失败路径、事务回滚与权限行为。

## 文档与协作

- 只修改任务相关内容，保留用户已有改动，不擅自回滚、删除或进行无关重构。
- 不主动创建 README、报告或其他文档；需要维护现有文档时引用真实路径，不写未经验证的性能、数量、默认账号和完成状态。
- 当前架构与命令不要引用已退役模块。API、配置和模块边界变化同步更新相关文档；图表以实际实现为准。
- 管理后台是独立项目，后端不生成或修改不存在的前端目录。菜单组件字段和 API 契约仍需与前端保持一致。
- `scripts/ci/check-doc-links.sh` 离线扫描全仓 Markdown 与 HTML（含根 README/AGENTS 和模块文档），校验本地路径存在、大小写精确匹配、目标未被 `.gitignore` 排除，以及 Markdown 标题/HTML 显式锚点；排除构建产物、工具配置与依赖目录，不验证外部 URL、JavaScript 动态生成的导航或其他文件格式的内部位置。文档引用未入库的日志、证据或本地配置时用行内代码写文件名，不要写成链接。
- 未获用户明确要求，不创建 Git commit 或推送。提交前检查实际 diff 与暂存范围，不混入其他主题。
- 不提交 `.env`、真实配置、密钥、证书、私钥或构建产物。需要提交时使用 Conventional Commits，按变更目的选择 type/scope。
- 基础版与标准版/企业版的许可范围分别见 [LICENSE](LICENSE)、[LICENSE-COMMERCIAL.md](LICENSE-COMMERCIAL.md)，不要将整个仓库描述为统一 MIT 或 Apache 授权。
- 完成时说明改动、实际检查结果及未验证的运行条件，不能将静态核对写成启动或完整门禁通过。
