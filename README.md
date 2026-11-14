# Apex Scaffold

![Apex Scaffold](https://img.shields.io/badge/Apex_Scaffold-v1.3.1-blue)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.15-brightgreen)
![Java](https://img.shields.io/badge/Java-21.0.13-orange)

基于 Spring Boot、Spring WebFlux 和 DDD 的 Java 后端脚手架，提供认证、系统管理、工作流、办公集成、文件与对外集成/物联网设备接入服务。当前运行架构为 **API 网关 + 六个业务服务 + 只读查询聚合服务 + 任务代理及可选脱敏服务**，业务代码以库模块复用，各服务通过独立启动模块装配。

[模块结构](#模块结构) · [快速开始](#快速开始) · [配置与部署](#配置与部署) · [验证与开发](#验证与开发) · [文档入口](#文档入口) · [许可证](#许可证)

## 项目能力

- 认证与访问控制：JWT、OAuth2、MFA、会话管理、RBAC、数据权限和租户隔离。
- 系统管理：用户、角色、菜单、配置、字典、审计及相关管理接口。
- 工作流：工作流定义与执行，以及独立服务接口。
- 办公集成：企业微信、钉钉、飞书等平台适配与消息集成；office 还提供基于 HTTP 收件箱和 WebSocket 在线提示的站内信。
- 文件服务：上传、文档管理、预览，以及 Local、MinIO、火山 TOS、阿里云 OSS、七牛五种存储适配。默认 Local，无故障降级；详见[对象存储接入指南](docs/05-operations/object-storage-integration.md)。
- 共享设施：统一响应、异常处理、国际化、缓存、事件 Outbox、加密和受管 HTTP 客户端。
- 服务治理与运维：网关路由、Nacos、限流与熔断、健康检查、指标和日志采集。
- 服务增强：[XXL-JOB](docs/05-operations/xxl-job-integration.md)、[脱敏](docs/05-operations/data-masking.md)、[Seata 多模式](docs/05-operations/seata-integration.md)。执行器与分布式事务默认关闭；事务模式经 `apex.seata.mode` 选择 AT/TCC/SAGA/XA（缺省 AT）；任务代理 8095、可选脱敏服务 8096。

能力是否启用取决于版本档位、服务配置、许可证及外部组件。仓库不包含管理后台前端源码；Compose 中的前端是独立镜像。

## 技术栈

版本以根 [pom.xml](pom.xml) 和 [.sdkmanrc](.sdkmanrc) 为准。

| 项目 | 当前版本或实现 |
| --- | --- |
| 项目版本 | 1.3.1 |
| JDK | SDKMAN `21.0.13.0+9-tem` |
| 构建工具 | Maven Daemon `2.0.0-rc-3` |
| Spring Boot / Spring Cloud | 3.5.15 / 2025.0.0 |
| Spring Cloud Alibaba | 2025.0.0.0 |
| Web 层 | Spring WebFlux、Reactor、函数式 Router + Handler |
| 持久化 | MyBatis-Plus 3.5.10.1；部分服务使用 R2DBC |
| 数据库迁移 | Flyway 11.20.3；MySQL / PostgreSQL 迁移资源 |
| 对象转换 | MapStruct 1.5.5.Final、Lombok 1.18.36 |
| API 文档 | SpringDoc 2.8.1 |
| 质量检查 | Surefire、Failsafe、ArchUnit、Spotless、PMD/p3c、JaCoCo |

WebFlux 不会使 JDBC、同步 Redis 或文件操作自动变为非阻塞。项目通过 `BlockingIoSchedulers` 的专用线程池隔离这些调用。

<a id="模块结构"></a>

## 模块结构

根 POM 直接聚合七个管理组，递归聚合 42 个模块；含根 POM 的全量 reactor 为 43 个项目，其中 8 个为 `packaging=pom`、35 个为既有 JAR 模块。第一阶段只增加六个分组 parent，复用 `apex-services-bom`，不增加业务服务或项目业务库。后续新增模块按实际递归清单计数，不永久锁死 42 / 43。

### Maven 分组与父链

[聚合分组图](docs/diagrams/128_maven_module_groups.svg) · [parent 继承图](docs/diagrams/129_maven_parent_inheritance.svg)

| 根级管理组 | 聚合 JAR 数 | 聚合模块与职责 |
| --- | ---: | --- |
| `apex-shared-parent` | 9 | `apex-common`、`apex-domain`、`apex-api-contract`、五个 `apex-infrastructure-*` 能力 JAR 及旧坐标兼容 JAR `apex-infrastructure` |
| `apex-components-parent` | 6 | 六个脚手架能力库：`apex-system`、`apex-auth`、`apex-workflow`、`apex-file`、`apex-office`、`apex-integration` |
| `apex-starters-parent` | 4 | `apex-storage-spring-boot-starter`、`apex-license-spring-boot-starter`、`apex-job-spring-boot-starter`、`apex-seata-spring-boot-starter` |
| `apex-services-bom` | 10 | 网关、六个业务 service、只读查询聚合 service，以及 job / masking 两个可选运行模块 |
| `apex-applications-parent` | 2 | `apex-example` 示例应用和 `apex-boot-all` 组合装配 |
| `apex-tools-parent` | 4 | `apex-license-tools`、`apex-generator`、`apex-tools-deployment`、`apex-tools-build` |
| `apex-business-parent` | 1 | 项目专属业务库聚合；当前仅含知识库 `apex-knowledge` |

**聚合与继承不是同一关系**：七组均直接继承根 POM；22 个既有叶子的直接 parent 改为所属新分组。原有 13 个 `apex-services-bom` 子项目仍继承它，其中 `apex-api-contract` 聚合在 shared、`apex-integration` 聚合在 components、`apex-boot-all` 聚合在 applications，另外 11 个运行模块的聚合和继承归属均为 services。根 POM 仍保留统一版本、构建档位和质量门禁；分组 parent 不自动引入运行依赖或激活叶子打包插件。

所有既有叶子目录仍位于仓库根级，分组通过 `../apex-xxx` 聚合同级目录，不存在 `apex-components-parent/apex-system/` 这样的嵌套源码路径。模块坐标、35 个原 JAR 的打包形态、业务接口、服务拓扑和端口不因分组而改变；分组不承诺构建提速或产物缩小。

`apex-business-parent` 只管理未来项目业务 JAR 的模型、规则、用例、领域专属适配和业务配置；通用框架实现由 shared / components / starters 等支撑模块提供，不在 business 复制。业务仓储、Mapper / PO 仍留在本域 infrastructure 层。若另建独立运行模块，聚合并继承 `apex-services-bom`，不在 business 重复登记；2026-11-14 起知识库以 `apex-knowledge`（business）+ `apex-knowledge-service`（services，可选模块）落地，也不进入八服务标准发布集合。

以下按能力列出既有模块职责；分组不改变其生产依赖边界。

| 分类 | 模块 | 职责 |
| --- | --- | --- |
| 共享基础 | `apex-common` | 响应、异常、上下文、工具、通用持久化辅助与阻塞调度器 |
| 共享领域 | `apex-domain` | 基础实体、通用事件、端口与 Gateway 契约 |
| 共享设施 | `apex-infrastructure-persistence`、`apex-infrastructure-cache`、`apex-infrastructure-messaging`、`apex-infrastructure-remote`、`apex-infrastructure-integration` | 按持久化、缓存、消息、远程调用和第三方集成拆分发布；`apex-infrastructure` 保留为旧坐标兼容聚合，不再承载实现源码 |
| 跨服务契约 | `apex-api-contract` | 内部 API 接口和共享 DTO |
| 服务父 POM | `apex-services-bom` | 聚合 11 个运行模块，保留现有依赖管理；仍为这 11 个模块及 api-contract / integration / boot-all 三个继承例外的 parent |
| 业务库 | `apex-system`、`apex-auth`、`apex-workflow`、`apex-office`、`apex-file`、`apex-integration`、`apex-knowledge` | 各业务域的领域、应用与专属基础设施实现；`apex-integration` 含连接器与物联网设备两个子域；`apex-knowledge` 提供知识空间/文档摄取/混合检索/问答编排 |
| 运行服务 | `apex-gateway`、`apex-system-service`、`apex-auth-service`、`apex-workflow-service`、`apex-office-service`、`apex-file-service`、`apex-integration-service`、`apex-aggregation-service`、`apex-knowledge-service` | 网关、业务服务或只读查询聚合服务的启动、配置与装配；知识库服务为可选运行模块（不进标准发布包） |
| 组合部署 | `apex-boot-all` | basic/示例/演示档单进程装配全部业务组件，仅装配不含业务实现 |
| 服务增强 | `apex-job-spring-boot-starter`、`apex-seata-spring-boot-starter`、`apex-job-service`、`apex-masking-service` | XXL 执行器、多模式分布式事务（AT/TCC/SAGA/XA）、固定任务代理与脱敏服务 |
| 对象存储 | `apex-storage-spring-boot-starter` | 仅内部依赖 common，提供五后端策略和自动装配 |
| 授权 | `apex-license-spring-boot-starter`、`apex-license-tools` | 运行时许可证校验与离线授权工具 |
| 示例与工具 | `apex-example`、`apex-generator` | 示例应用与代码生成器 |
| 运维工具 | `apex-tools-deployment` | 离线部署密钥/配置校验、TOTP 敏感字段迁移包生成与回滚、MySQL V42 结构恢复；只由 `scripts/utils/`、`scripts/ci/check-doc-links.sh` 脚本与 `podman-compose.yml` 的 `security-key-check` 调用，不参与服务运行期装配 |
| 构建管理 | `apex-tools-build` | 模块注册表与构建/发布/部署编排入口；注册表对齐根 POM 的递归聚合集合，见[使用说明](docs/04-development/build-tools-usage.md) |

`apex-system` 已接收原接口层、应用层及 system 专属领域和基础设施代码，保留 `com.apex.scaffold.interfaces/application/domain/infrastructure` Java 包名。原 `apex-interfaces`、`apex-application` 和 `apex-auth-center` 不再作为当前运行入口。

```text
请求 → apex-gateway
        ├─ apex-auth-service     → apex-auth
        ├─ apex-system-service   → apex-system
        ├─ apex-workflow-service → apex-workflow
        ├─ apex-office-service   → apex-office
        ├─ apex-file-service     → apex-file → apex-storage-spring-boot-starter → apex-common
        ├─ apex-integration-service → apex-integration（连接器 + 物联网设备）
        ├─ apex-aggregation-service → api-contract（只读跨源聚合：统一待办）
        ├─ apex-job-service      → system 任务管理接口
        ├─ apex-masking-service  → common 脱敏工具（可选）
        └─ apex-knowledge-service → apex-knowledge（知识库摄取/检索/问答，可选）

共享依赖：persistence/cache → domain、common；messaging → persistence、domain、common；remote → domain、common；infrastructure-integration → remote、domain、common；兼容聚合 infrastructure → 五个能力 JAR
跨服务契约：api-contract → common
```

各业务库按需依赖共享模块，并非所有业务库都依赖 `apex-infrastructure`。具体依赖以各模块 POM 为准；开发边界见 [AGENTS.md](AGENTS.md)。

### 服务端口与数据边界

| 启动模块 | 启动类 | 默认业务端口 | 默认数据库 |
| --- | --- | ---: | --- |
| `apex-gateway` | `GatewayApplication` | 8080 | 可选访问日志库 `apex_gateway` |
| `apex-auth-service` | `AuthServiceApplication` | 8081 | `apex_auth`；dev 配置使用 `apex_auth_dev` |
| `apex-system-service` | `SystemServiceApplication` | 8082 | `apex_system` |
| `apex-workflow-service` | `WorkflowServiceApplication` | 8093 | `apex_workflow` |
| `apex-office-service` | `OfficeServiceApplication` | 8084 | `apex_office` |
| `apex-file-service` | `FileServiceApplication` | 8085 | `apex_file` |
| `apex-integration-service` | `IntegrationServiceApplication` | 8094 | `apex_integration` |
| `apex-aggregation-service` | `AggregationServiceApplication` | 8098 | 无数据库（只读跨源聚合） |
| `apex-job-service` | `JobServiceApplication` | 8095 | 无独立数据库，固定代理 system |
| `apex-masking-service` | `MaskingServiceApplication` | 8096 | 无持久化 |
| `apex-knowledge-service` | `KnowledgeServiceApplication` | 8100 | `apex_knowledge`（可选模块） |
| `apex-example` | `ExampleApplication` | 8080 | `apex_example` |

system 的默认管理端口为 8083；workflow 自 2026-11-07 起默认业务端口为 8093，两者不再冲突，同机直跑无须调整端口。集成服务管理端口为 8097（避开任务代理 8095 与脱敏服务 8096）。查询聚合服务默认业务端口 8098，无独立管理端口；XXL 控制台默认宿主端口为 8099（避开 8097 与 8098）。example 与 gateway 也不能同时占用默认 8080。

数据库由各服务分别管理。system 的迁移资源位于 `apex-system/src/main/resources/db/migration/`，由 system-service 从依赖 classpath 加载；integration 的迁移资源位于 `apex-integration/src/main/resources/db/migration/`；其他四个业务服务与知识库的迁移资源位于各自 service 模块（知识库为 `apex-knowledge-service/src/main/resources/db/migration/`）。迁移包含 MySQL、PostgreSQL 两套目录，实际选择由服务数据源及 Flyway 配置决定。

<a id="快速开始"></a>

## 快速开始

以下命令在仓库根目录执行。先准备构建环境，再准备目标服务需要的数据库、缓存和安全配置。

### 1. 激活固定构建环境

安装 SDKMAN 及 [.sdkmanrc](.sdkmanrc) 指定的 JDK、mvnd 后，在当前 shell 中加载项目环境：

```bash
source scripts/build/init-build-env.sh
java -version
mvnd --version
```

也可在已加载 SDKMAN 的 shell 中执行 `sdk env`。JDK 必须为 `21.0.13.0+9-tem`，不能只满足“21+”，也不能依赖会漂移的 `java/current` 链接。没有 mvnd 时应安装指定版本，不要回退到 `mvn`。

如此前用其他 JDK 启动过 mvnd daemon，加载正确环境后先执行 `mvnd --stop` 再构建。

### 2. 构建目标服务

例如构建 system-service 及其依赖：

```bash
mvnd -T4 -B -pl apex-system-service -am clean package -Pbasic -DskipTests
```

这是便于本地运行的打包命令，跳过测试，不代表通过质量门禁。默认产物为 `apex-system-service/target/apex-system-service-1.3.1.jar`；其他服务也使用各自模块名与项目版本，不附加版本档位后缀。

分组后仍从总根使用 `-pl <叶子列表> -am`，叶子路径不变。`-pl apex-services-bom -am` 不表示构建该组全部服务；整组构建应明确列出叶子，不能用 parent 选择或 `-f 分组/pom.xml` 假定跨组依赖均在 reactor 中。

### 3. 准备运行配置

system 的 basic 档位仍需要 MySQL 和 Redis。公共配置启用 Flyway，但依赖 system 库的 dev 配置会关闭它；首次使用空库时，在运行配置中设置 `SPRING_FLYWAY_ENABLED=true`，或先完成数据库迁移。启动前应创建目标数据库，配置数据库连接、Redis，以及所用能力要求的密钥。参照 [.env.example](.env.example) 和目标模块的 `src/main/resources/application*.yml`，不要将模板默认值直接用于生产。

本地不使用 Nacos 时，显式设置 `NACOS_CONFIG_ENABLED=false`、`NACOS_DISCOVERY_ENABLED=false`、`NACOS_REGISTER_ENABLED=false`。网关的默认路由为 `lb://` 服务名；关闭服务发现后还需要设置 HTTP 路由地址，见下文。

可将本地配置保存在未提交的 `.env` 文件中，通过 `APEX_ENV_FILE` 交给部署脚本加载。该文件按 shell 语法执行，值需要正确引用，并且只应加载可信配置。

### 4. 启动单个服务

```bash
APEX_ENV_FILE="$PWD/.env" \
APEX_SERVICE_MODULE=apex-system-service \
  bash scripts/deploy/app.sh console dev basic
```

脚本默认运行 gateway；`APEX_SERVICE_MODULE` 用于选择服务。`console` 为前台运行，`start` 为后台运行，另支持 `stop`、`restart`、`status`、`logs`，实际实现见 [scripts/deploy/service/app.sh](scripts/deploy/service/app.sh)。脚本不自动启动基础设施，也不自动激活 SDKMAN。

也可在已导出运行环境变量的 shell 中直接运行 JAR：

```bash
java -jar apex-system-service/target/apex-system-service-1.3.1.jar \
  --spring.profiles.active=dev,basic
```

Java 进程不会自动读取根目录 `.env`。只启动 system-service 是单服务开发环境；完整登录与业务联调还需要 auth-service、网关及相关业务服务。初始化账号与权限以实际迁移种子及环境配置为准。

<a id="配置与部署"></a>

## 配置与部署

### 三种 profile 的作用

| 类型 | 示例 | 作用 |
| --- | --- | --- |
| Maven profile | `-Pbasic`、`-Pstandard`、`-Penterprise` | 构建属性与依赖选择；默认 standard |
| Spring profile | `dev,basic`、`prod,standard` | 服务运行时配置 |
| Compose profile | `--profile rocketmq`、`--profile keycloak` | 选择可选容器 |

Maven profile 不等于 JAR 启动时的 Spring profile。auth、workflow、office、file 的 basic 配置有 H2 初始化方案并关闭 Flyway，但不能由此推断所有服务都不需要 Redis或安全密钥。

### 密钥与环境变量

| 变量 | 配置要求 |
| --- | --- |
| `APEX_DATA_ENCRYPTION_KEY` | Base64 编码，解码后恰好 32 字节 |
| `SECURITY_ENCRYPTION_KEY` | Base64 编码，解码后恰好 32 字节 |
| `APEX_CONFIG_ENCRYPTION_KEY` | Base64 编码，解码后恰好 32 字节 |
| `CONFIG_ENCRYPTION_KEY` | 非空白，UTF-8 编码恰好 32 字节 |
| `JWT_SECRET` | 至少 32 字节，不能使用被安全校验拒绝的模板值 |

`.env.example` 中这些安全变量需要自行填写。用于持久数据的加密密钥应稳定保存，轮换必须配合迁移方案，不能每次启动重新生成。数据库密码、Redis 密码、证书、令牌和真实环境文件不得提交。

### 不使用服务发现的本地网关路由

在下列下游服务已经运行且端口按约定配置后，为网关导出：

```bash
export ROUTE_AUTH_URI=http://127.0.0.1:8081
export ROUTE_SYSTEM_URI=http://127.0.0.1:8082
export ROUTE_WORKFLOW_URI=http://127.0.0.1:8093
export ROUTE_OFFICE_URI=http://127.0.0.1:8084
export ROUTE_OFFICE_MESSAGE_URI=http://127.0.0.1:8084
export ROUTE_OFFICE_WS_URI=ws://127.0.0.1:8084
export ROUTE_FILE_URI=http://127.0.0.1:8085
export ROUTE_JOB_URI=http://127.0.0.1:8095
export ROUTE_MASKING_URI=http://127.0.0.1:8096
```

这些是网关下游地址；workflow 使用其默认业务端口 8093。路由前缀以 [网关配置](apex-gateway/src/main/resources/application.yml) 和各业务 Router 为准，包含 `/auth/v1/`、`/api/v1/`、`/admin-api/v1/`、OAuth2 及 well-known 路径。

### Podman Compose

仓库提供 [应用栈](podman-compose.yml) 和 [基础设施栈](podman-compose.infra.yml)。运行前先检查目标栈的端口、网络、镜像及变量要求：

- 应用栈依赖预先存在的外部网络 `apex-observability`；前端容器需要可用的独立前端镜像。
- Compose `.env` 用于变量插值，只有 `environment` 或 `env_file` 等明确配置的值才会传入容器。
- 应用栈的 `DB_PASSWORD` 应与所用 MySQL 凭据一致；使用 root 时应与 `MYSQL_ROOT_PASSWORD` 一致。
- 生产网关必须配置允许的 CORS 源。当前应用栈未直接透传 `CORS_ALLOWED_ORIGINS`，需通过 Nacos 或补充部署配置提供。
- auth 的 JDBC 与 R2DBC 配置需分别检查；prod R2DBC 的 TLS 要求必须与 MySQL 和证书配置匹配。
- 基础设施栈 Redis 默认无密码，Nacos 宿主端口为 18848/19848；应用栈 Redis 有密码，Nacos 使用 8848/9848。选择栈时同步调整客户端配置。
- 两套栈存在 MySQL、Redis 等宿主端口重叠，不应直接完整同时启动。

部署细节见 [部署指南](docs/05-operations/deployment.md) 和 [微服务加固指南](docs/05-operations/microservice-hardening-guide.md)。

<a id="验证与开发"></a>

## 验证与开发

所有构建、测试、打包和依赖解析前，先加载项目构建环境。

```bash
source scripts/build/init-build-env.sh
mvnd -T4 -B clean verify
```

这是完整回归入口，包括编译、单元测试、默认 Failsafe 集成测试、架构测试、Spotless、PMD/p3c 和 JaCoCo。通过标准为反应堆全部模块 `BUILD SUCCESS`，测试无失败或错误；外部依赖需求以测试配置为准。

推荐 `-T4`，避免过高并行度导致真实 Redis/HTTPS 等计时敏感测试出现负载超时。`remote-it` 和 `stress-tests` 等额外 profile 不等于默认门禁，需要按各自场景准备环境。

受影响模块的快速验证示例：

```bash
mvnd -T4 -B -pl apex-system-service -am test
```

本次基础设施拆分与 office 站内信的固定环境验证入口为 [`scripts/test/verify-infrastructure-websocket.sh`](scripts/test/verify-infrastructure-websocket.sh)，支持 `--infrastructure`、`--websocket` 和 `--full`；中台主干（网关、六个业务库与运行模块）的验证入口为 [`scripts/test/verify-middle-platform.sh`](scripts/test/verify-middle-platform.sh)，默认执行定向 `test`，使用 `--full` 执行完整反应堆 `clean verify`。两个脚本都只调用 mvnd，不降低现有 verify 门禁。office 站内信的协议、Origin 白名单、HTTP/WS 成对路由和单实例内存状态见[站内信说明](docs/05-operations/in-app-messaging.md)。

`test` 不会执行完整 Failsafe/verify 门禁。开发规范、事务与调度器约束见 [AGENTS.md](AGENTS.md)。业务 JSON 响应使用 `Result<T>`，成功业务码为 `0`，与 HTTP 状态码 200 区分。

改动文档后执行 `bash scripts/ci/check-doc-links.sh`：它校验本地路径存在、大小写精确匹配、目标未被 `.gitignore` 排除，以及标题与 HTML 锚点，适用于 Linux 与 GitHub 渲染。

<a id="文档入口"></a>

## 文档入口

| 文档 | 内容 |
| --- | --- |
| [文档索引](docs/README.md) | 文档目录与阅读入口 |
| [快速开始](docs/00-getting-started/quickstart.md) | 环境准备和使用步骤 |
| [开发规范](docs/04-development/development-standards.md) | 开发约定 |
| [配置管理](docs/05-operations/configuration.md) | 运行配置与环境变量 |
| [部署指南](docs/05-operations/deployment.md) | 部署流程 |
| [集成测试指南](docs/06-testing/integration-test-guide.md) | 集成测试环境与操作 |
| [系统架构](docs/architecture/system-architecture.md) | 架构说明 |
| [能力目录](docs/02-architecture/capability-catalog.md) | 八项中台能力、治理字段、计量维度与开放 scope 登记 |
| [中台架构评审报告](docs/08-project-management/reports/middle-platform-architecture-review.md) | 八维度评审结论、已证实差距与整改关闭状态 |
| [业务流程](docs/architecture/business-flows.md) | 业务流程说明 |
| [项目交付文档集](docs/08-project-management/delivery/README.md) | 交付范围、交付物、需求追踪、质量、风险、变更、验收与交接 |
| [微服务验收清单](docs/08-project-management/checklists/microservice-acceptance-checklist.md) | 迁移与验收记录 |
| [服务增强实施计划](docs/08-project-management/plans/service-enhancement-implementation-plan.md) | 增强范围、依赖边界与验收要求 |
| [服务增强交付报告](docs/08-project-management/reports/service-enhancement-delivery-report.md) | 文件清单、真实检查结果与运行限制 |
| [周期报告归档](docs/08-project-management/periodic-reports/README.md) | 日报、周报、月报的归档位置与命名口径 |

历史文档可能保留迁移阶段名称；当前模块、入口、依赖和配置以根 POM、服务代码及脚本为准。

<a id="许可证"></a>

## 许可证

基础版源码适用 [MIT 许可证](LICENSE)。标准版、企业版适用 [商业授权协议](LICENSE-COMMERCIAL.md)，不适用 MIT；使用时应遵守对应版本的授权范围及许可证校验要求。
