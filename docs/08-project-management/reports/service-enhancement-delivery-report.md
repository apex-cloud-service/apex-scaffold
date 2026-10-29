# 服务增强统一集成交付报告

日期：2026-10-14；项目版本：1.3.1。配套 [实施计划](../plans/service-enhancement-implementation-plan.md)。本报告将代码集成、默认质量门禁与真实部署验收分别记录。多模式扩展后第三轮完整门禁已停止：此前 SQL 注释与 PMD 问题已修复，剩余 apex-example 集成测试因本机 MySQL 连接拒绝持续重试。本轮未取得全 reactor BUILD SUCCESS，完整门禁不计通过，不能据此认定上线验收完成。

## 交付范围与需求追踪

| 需求 | 结果 | 证据与限制 |
| --- | --- | --- |
| R1 四模块注册 | 已实现 | 根 reactor 24 模块 |
| R2 统一依赖 | 已实现 | 根管理 XXL 3.1.1、Seata 2.2.0、内部 starter；services BOM 管理 SpringDoc |
| R3 任务代理与调度 | 已实现 | 固定 system 代理、九路由、安全头过滤；仅一个 system 任务和 workflow 超时扫描可接管 |
| R4 数据脱敏 | 已实现 | common 工具/Jackson/JSON 与8096受保护服务；配置式版本，无CRUD/热刷新 |
| R5 Seata 接入（多模式） | 已实现基础能力 | 默认关闭，`apex.seata.mode` 选择 AT/TCC/SAGA/XA，缺省 AT；AT/SAGA/XA 同步单JDBC；真实TC四模式端到端验收未执行 |
| R6 部署入口 | 配置检查通过 | 新端口、可选中心、只读外部配置、日志卷；未启动完整应用栈 |
| R7 文档 | 已同步 | 架构、配置、三运维文档、计划与本报告；最终链接检查见验证记录 |

## 架构、接口与兼容性

任务路径经 gateway → job-service → system。代理直接内部依赖 infrastructure，以共享受管 WebClient 调用固定上游；实体、迁移、权限和日志仍归 system，没有数据库所有权迁移。五业务 service 均引入 job/seata starter，两个 starter 和 masking-service 直接内部依赖 common。

任务接口为 GET `/admin-api/v1/jobs`、`/page`、`/{id}`、`/{id}/logs`，POST 根路径及 `/{id}/run`，PUT `/{id}`、`/{id}/status`，DELETE `/{id}`。代理仅检查 Bearer 格式和ID，system验证签名、租户及 `system:job:*` 权限；仅转发 Authorization、Content-Type，保留上游状态、内容类型和字节响应，连接失败502。测试的 WebClient transport 替身不证明真实上游权限链。

XXL `apexSystemJob` 只执行配置租户的一个任务，方法须有 `@ManagedJob`；`apexWorkflowTimeout` 是固定扫描入口。完整 Publisher 等待、末尾错误和扫描互斥有测试。中心cron/状态人工同步，多实例跨进程互斥未实现，workflow NOTIFY仅记录日志，管理员转办固定ID=1；搜索任务原凌晨扫描仍存在，应避免重叠。

脱敏 POST `/api/v1/masking/batch` 需 `masking:apply`，GET `/api/v1/masking/rules` 需 `masking:rules`。JWT验证签名/issuer/audience/时间与字符串tenant_id，客户端租户头不能覆盖。请求1–100项、256KB，未知顶层字段拒绝。JSON深拷贝递归按精确字段名处理，深度上限64；未配置字段保留。Unicode按码点而非字素簇处理。非法/短输入全遮盖，旧展示星号格式可能变化。规则配置重启生效，无数据库、CRUD或热刷新；脱敏不是加密或完全匿名化。

Seata管理TM/RM、数据源代理、同步执行器和受保护XID，支持通过 `apex.seata.mode` 选择 **AT（缺省）/ TCC / SAGA / XA** 四种模式，大小写不敏感，非法值绑定阶段失败。四种模式共用同一XID传播过滤、入站校验与Outbox防护；差异集中在数据源代理与必需表结构：AT用 `DataSourceProxy` 并需各业务库 `undo_log`，XA用 `DataSourceProxyXA` 且依赖数据库XA能力，TCC不包装数据源、由扫描器代理 `@LocalTCC` 动作，SAGA不包装数据源、引擎持有原始数据源并需状态机存储三表（建表脚本随starter发布在 `sql/saga/{mysql,postgresql}/seata_state_machine.sql`）。AT/SAGA/XA要求单JDBC数据源，拒绝动态数据源与Publisher回调；全局事务内拒绝现有Outbox写入。默认关闭时不初始化或代理，缺省AT与既有接入行为兼容。已有业务方法不会自动进入全局事务；R2DBC、H2、对象存储、消息和第三方写入不在支持范围。网关清除外部传播头，内部origin精确白名单、独立至少32字符传播密钥和生产HTTPS仍须配置。Outbox本地提交的可见性窗口未解决，应使用既有本地事务+Outbox+幂等补偿或另行设计持久全局状态协调协议。

## 迁移与配置

| 域 | MySQL/PostgreSQL 新版本 | 所属模块 |
| --- | --- | --- |
| system | V61__create_seata_undo_log.sql | apex-system |
| auth | V57__create_seata_undo_log.sql | apex-auth-service |
| workflow | V2__create_seata_undo_log.sql | apex-workflow-service |
| file | V3__create_seata_undo_log.sql | apex-file-service |
| office | V3__create_seata_undo_log.sql | apex-office-service |

每项位于 `src/main/resources/db/migration/{mysql,postgresql}/`，新增两套SQL，不改历史版本。业务 undo_log 与TC官方持久表不同；生产启用前逐库执行迁移并验证。MySQL使用InnoDB，PostgreSQL使用BYTEA保存回滚镜像；不得加入业务租户拦截。

| 参数/端口 | 用途与启用要求 |
| --- | --- |
| APEX_JOB_PORT=8095、APEX_JOB_SYSTEM_URL | 固定代理上游；本机Java运行时应改为宿主可达system地址 |
| ROUTE_JOB_URI、ROUTE_MASKING_URI | 网关下游；本机默认示例8095/8096 |
| MASKING_PORT=8096 | masking可选profile |
| MASKING_JWT_ISSUER、MASKING_JWT_JWK_SET_URI、MASKING_JWT_AUDIENCE、MASKING_ALLOWED_TENANT | 必填身份与租户信任配置，模板为空 |
| XXL_ADMIN_CONFIG_FILE、XXL_ADMIN_PORT=8097 | 官方3.1.1中心外部properties只读挂载，文件须预先存在 |
| apex.job.executor.* | HTTPS中心、至少32字符令牌、独立执行器端口；默认禁用 |
| apex.job.system.tenant-id/job-id、apex.job.workflow.enabled | 单任务及workflow接管开关 |
| SEATA_SERVER_CONFIG_FILE、SEATA_RPC_PORT=8091、SEATA_CONSOLE_PORT=7091 | 官方2.2.0 TC外部YAML只读挂载，文件须预先存在 |
| APEX_SEATA_ENABLED=false、APEX_SEATA_MODE=AT、APEX_SEATA_TX_GROUP、APEX_SEATA_PROPAGATION_SECRET | Java/配置中心业务开关；`APEX_SEATA_MODE` 取 AT/TCC/SAGA/XA，缺省 AT；Compose未统一透传，.env不自动注入Java |

workflow应用默认8083与system管理8083冲突，本机应改8093，Compose已有8093约定。app.sh新增8095/8096；job探针访问任务根路径期待401，只证明路由存活，不验证上游；masking使用health200。允许通过APEX_READINESS_URL/APEX_READINESS_STATUS覆盖探针。容器job探针的403来自安全链，同样不能证明真实业务就绪。

## 部署、启停与切换示例

先准备外部 `apex-observability` 网络、可用镜像、数据库/Redis凭据、稳定加密密钥、生产CORS、身份中心和中心官方表，再部署。安全模板不能直接用于生产，配置文件不可提交真实秘密。

```bash
# 仅渲染配置不等于运行验收；配置文件必须预先存在
podman-compose -f podman-compose.yml --profile xxl-job up -d xxl-job-admin
podman-compose -f podman-compose.yml --profile seata up -d seata-tc
podman-compose -f podman-compose.yml up -d apex-job-service
podman-compose -f podman-compose.yml --profile masking up -d apex-masking-service

# 使用已构建jar和可信环境文件；Java不会自动读取.env
APEX_ENV_FILE="$PWD/.env" APEX_SERVICE_MODULE=apex-job-service \
  bash scripts/deploy/app.sh start dev standard
APEX_SERVICE_MODULE=apex-job-service bash scripts/deploy/app.sh status
APEX_SERVICE_MODULE=apex-job-service bash scripts/deploy/app.sh stop
```

中心profile仅启动中心，不开启五业务执行器或全局事务。XXL启用前确认可信租户、任务白名单、人工cron、中心与执行器网络和HTTPS；关闭时先停中心任务再恢复本地调度，避免双执行。Seata按所选模式准备数据源与表结构：AT需undo_log、SAGA需状态机存储三表、XA依赖XA驱动，三者均需单JDBC数据源；TCC不包装数据源。启用前验证外部registry.conf/file.conf事务组映射、TC和TLS，再逐服务启用；关闭前停止新事务并等待已有事务终结，AT下不删除undo_log、不停止待恢复RM。

```bash
# 示例只引用环境中的有效令牌，不在文档放置真实凭据
curl -H "Authorization: Bearer ${MASKING_ACCESS_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"version":"v1","documents":[{"phone":"13800138000"}]}' \
  http://127.0.0.1:8096/api/v1/masking/batch
```

成功业务Result.code为0，示例手机号138****8000；401/403沿用安全协议。请求原文、响应原文和完整令牌不得由网关/APM/日志采集。

## 实际验证记录

固定环境：Java17.0.12-oracle、mvnd2.0.0-rc-3。每轮先source初始化脚本，所有构建使用mvnd；未回退mvn、未降低覆盖率或业务SLA。格式化范围为 job starter、job-service 与 apex-system；格式化结束后重新执行完整门禁，避免运行中修改源码。

| 检查 | 本轮实况 |
| --- | --- |
| 第一轮 `mvnd -T4 -B clean verify` | 失败：job starter PMD七项，五类@author及两常量问题；已修复 |
| 第二轮完整verify | 失败：job starter行24%、分支23%，门槛60%/50%；补充启用配置合法/拒绝路径测试 |
| 第三轮完整verify | file/office迁移表集合各1失败，job-service被依赖失败跳过；修正生产undo_log契约后停止剩余运行 |
| 第四轮完整verify（此前阶段） | 未完成；当时完成聚合`test`（见下行），不作为完整门禁通过证据 |
| 多模式扩展后完整verify第一轮 | 失败：job-service 四项 PMD 违规，system SQL 首行注释契约失败；已提取认证前缀/ID正则常量、拆分URI校验，并为八个新增迁移补齐注释 |
| 多模式扩展后完整verify第二轮 | 失败：system 的 JobInvoker 括号字符串与 JobScheduler 复合条件共三项 PMD 违规；已提取括号常量与外部调度接管判断 |
| 修复后定向格式与PMD复验 | `mvnd -T4 -B -pl apex-system,apex-job-service spotless:apply` 与对应 `pmd:check` 均 **BUILD SUCCESS**；随后重新运行全仓 `clean verify` |
| 多模式扩展后完整verify第三轮（最终本轮） | 已停止，未取得全 reactor BUILD SUCCESS：apex-example 的 ExampleCapabilityIntegrationTest 启动时连接 `127.0.0.1:3306/apex_example_test` 被拒绝，Druid 持续重试；需准备测试MySQL后重新执行完整门禁。system 1532项单测0失败0错误、3项Keycloak测试跳过；auth-service 双服务授权集成测试5项通过，格式/PMD/覆盖率与Failsafe verify已执行；SeataAtIntegrationTest因条件缺失跳过1项。阶段性通过不替代全仓门禁 |
| 聚合 `mvnd -T4 -B -pl apex-job-service,apex-masking-service,apex-job-spring-boot-starter,apex-seata-spring-boot-starter -am test` | **BUILD SUCCESS**，Failures: 0 / Errors: 0；含 common/domain/infrastructure(489项)/四新模块 |
| job-service 修复复验 | `/actuator/health` 先报401（安全链默认拒绝）→放行探针；再报503（共享设施传递Redis/邮件健康指示器DOWN）→启动类排除无状态代理未用的自动配置；最终7项测试、`spotless:check`通过 |
| job starter第三轮 | 8项测试0失败0错误，已通过JaCoCo检查 |
| seata starter 多模式单元测试 | `SeataModeTest`（9项，模式绑定与按模式装配）+ `SeataTransactionExecutorTest`（7项）+ `SeataSafetyTest`（8项），合计24项，Failures: 0 / Errors: 0；离线装配，不触发TM/RM netty初始化 |
| Compose全可选profile渲染 | 最初失败：xxl-job-logs未声明；补齐xxl-job-logs/seata-logs后exit0 |
| shell与diff | `bash -n scripts/deploy/service/app.sh`、`git diff --check` 通过，报告回填后复查通过 |
| 文档链接 | 报告回填后全仓扫描651个Markdown文件、2459个链接，断裂链接0 |

Compose用进程环境中的非机密占位值验证所有必需安全变量插值，捕获渲染输出而不打印展开配置；该测试不验证密钥格式或服务启动。当前Podman可连接，podman-compose1.6.0、Podman5.8.5；不能以历史Docker daemon不可连接解释本轮未部署。仓库默认 `config/xxl-job`、`config/seata` 下未找到外部配置，未创建真实凭据或官方中心数据库。

## 未验证项与运行限制

- 未运行真实XXL中心通信、真实system JWT/权限链和workflow数据库任务。
- 未启动完整Compose应用栈，未执行镜像构建、生产TLS/JWKS轮换、APM原文禁采、限流或并发容量验收。
- 真实Seata AT测试需 `APEX_SEATA_IT=true`、DB1/DB2不同MySQL库、USER/PASSWORD与官方配置；条件缺失跳过不能算通过。测试为同JVM两个HTTP分支，不代表五完整应用和生产HTTPS已验收。
- TCC `@LocalTCC` 动作代理的真实Try/Confirm/Cancel执行、SAGA状态机的真实定义加载与正向/补偿节点执行、XA两阶段提交均未在真实TC下验证；单测仅断言按模式装配的数据源代理类型、扫描器与引擎/存储配置绑定，不证明四种模式端到端提交与回滚。
- 双数据库SQL实际执行、五服务按各模式启用Seata启动、TC故障恢复/脏数据恢复未验证。
- p3c曾输出内部maximum Iterations exceeded，检查目标通过不能解释为分析穷尽全部路径。

## 完整变更文件清单

下列为本次增强相关源码、配置、测试和文档；排除target生成物与机密文件。新模块所有源文件逐项列出，已有变更保留。

### 聚合与部署

- pom.xml
- apex-services-bom/pom.xml
- .env.example
- Containerfile
- podman-compose.yml
- scripts/deploy/service/app.sh
- AGENTS.md
- README.md
- docs/05-operations/configuration.md
- docs/05-operations/xxl-job-integration.md
- docs/05-operations/data-masking.md
- docs/05-operations/seata-integration.md
- docs/08-project-management/plans/service-enhancement-implementation-plan.md
- docs/08-project-management/reports/service-enhancement-delivery-report.md

### 五服务与网关

五个模块 `apex-{auth,system,workflow,file,office}-service` 的 `pom.xml` 与 `src/main/resources/application.yml`，合计10文件，分别引入两starter并配置默认关闭AT。

- apex-gateway/src/main/resources/application.yml
- apex-gateway/src/main/java/com/apex/scaffold/gateway/filter/JwtSignatureFilter.java
- apex-gateway/src/test/java/com/apex/scaffold/gateway/filter/JwtSignatureFilterTest.java

### common与业务库

以下包名均位于各模块 `src/main/java/com/apex/scaffold/`；测试位于对应 `src/test/java/com/apex/scaffold/`。

- apex-common：common/annotation/DataMask.java；common/util/DataMaskUtil.java、SensitiveDataUtil.java；common/masking/DataMaskSerializer.java、JsonFieldMasker.java。
- apex-common测试：common/util/DataMaskJacksonTest.java、DataMaskSafetyTest.java、JsonFieldMaskerTest.java。
- apex-system：application/job/JobInvoker.java、JobScheduler.java、SearchIndexReconciliationJob.java、ManagedJob.java；application/service/JobService.java。
- apex-system测试：application/job/JobInvokerTenantTest.java、JobSchedulerTenantTest.java、JobInvokerCompletionTest.java、ManagedJobBehaviorTest.java；application/service/JobServiceTest.java。
- apex-workflow：workflow/application/scheduler/WorkflowTimeoutHandler.java；对应测试WorkflowTimeoutHandlerTest.java。
- 十个迁移SQL：上一节五域各MySQL/PostgreSQL对应版本和完整相对目录。
- apex-file-service/src/test/java/com/apex/scaffold/fileservice/db/migration/FileFlywayMigrationTest.java。
- apex-office-service/src/test/java/com/apex/scaffold/officeservice/db/migration/OfficeFlywayMigrationTest.java：两测试将生产undo_log及七列纳入双方言契约，H2仍只要求业务表。

### 新模块

所有新模块包含pom.xml；源码包路径为 `src/main/java/com/apex/scaffold/{包}/`，测试相同包位于src/test/java。

| 模块/包 | 生产源码 | 测试 |
| --- | --- | --- |
| apex-job-spring-boot-starter/job | JobCompletion.java、JobExecutorAutoConfiguration.java、JobExecutorProperties.java、SystemJobBridge.java、WorkflowJobBridge.java | JobExecutorTest.java、SystemJobBridgeTest.java、WorkflowJobBridgeTest.java |
| apex-job-service/jobservice | JobServiceApplication.java、JobProxy.java | JobProxyTest.java、JobServiceStartupTest.java |
| apex-masking-service/maskingservice | MaskingServiceApplication.java；config/MaskingProperties.java、MaskingSecurityConfig.java；interfaces/MaskingHandler.java、MaskingRequest.java、MaskingRouter.java | MaskingApiTest.java、MaskingJwtTest.java |
| apex-seata-spring-boot-starter/seata | SeataAutoConfiguration.java、SeataProperties.java、SeataTransactionExecutor.java、SeataXidPropagation.java、SeataOutboxGuard.java、SagaExecutor.java | SeataTransactionExecutorTest.java、SeataSafetyTest.java、SeataModeTest.java、SeataAtIntegrationTest.java；src/test/resources/seata-test.conf |

`apex-seata-spring-boot-starter` 另含 SAGA 存储建表脚本 `src/main/resources/sql/saga/mysql/seata_state_machine.sql` 与 `.../postgresql/seata_state_machine.sql`（源自 Apache Seata 2.2.0 官方 DDL，表名前缀 `seata_`）。

两个starter分别包含 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`；job-service、masking-service分别包含 `src/main/resources/application.yml`。

## 交付结论

统一代码、依赖及部署入口已集成，Seata 四模式配置与离线装配测试已完成。完整门禁本轮因 apex-example 测试数据库连接拒绝而停止，尚未全仓通过；准备可用的 apex_example_test MySQL 环境后须重新执行 `source scripts/build/init-build-env.sh && mvnd -T4 -B clean verify`。真实 XXL/Seata 中心、双数据库、TLS和全栈部署仍是独立验收条件，当前不宣称上线或完整运行交付。未创建Git提交或推送。
