# 基础设施能力拆分与 office 站内信交付报告

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

## 状态

本文记录当前工作区的交付范围。最终全仓 `source scripts/build/init-build-env.sh && mvnd -T4 -B clean verify` 已成功，日志为 `/Users/eric/apex-final-verify6.log`，于 2026-10-16 08:45:51（UTC+08:00）结束，耗时 20:08；31 个反应堆项目（根项目加 30 个模块）全部 `SUCCESS`，最终为 `BUILD SUCCESS`。当前 reactor 测试 XML 汇总为 Surefire 4689 项、Failsafe 297 项，Failures/Errors 均为 0；合计 4986 项，实际通过 4985 项、跳过 1 项。当前绑定的 Spotless、PMD/p3c、ArchUnit 和 JaCoCo 门禁通过。新增真实 H2 Mapper、事务与 Gateway 网络测试已覆盖本地消息闭环；生产数据库、多实例与 HTTPS/WSS 代理仍待验证。历史失败及主动停止的验证保留为过程记录，不能记为通过。

## 已交付清单

- 根 reactor 按实际 POM 聚合为 30 个模块；新增 persistence、cache、messaging、remote、integration 五个共享能力 JAR。
- `apex-infrastructure` 保留为旧坐标兼容聚合，不再承载实现源码；原 Java 包名、配置前缀、本地调用语义和业务数据所有权保持不变。
- 直接依赖边界已按能力更新：messaging 依赖 persistence，integration 依赖 remote，cache 仅在 integration 测试中使用；system、auth、auth-service、job-service、example 的消费者边界按当前 POM 记录。
- office 站内信包含 HTTP 收件箱/未读数/消息操作与 WebSocket 在线提示；访问 JWT 独立验签，浏览器先获取 30 秒一次性票据，再建立 WebSocket。
- 网关消息 HTTP 与 WebSocket 使用 `ROUTE_OFFICE_MESSAGE_URI`、`ROUTE_OFFICE_WS_URI`，两者必须指向同一 office 实例。
- 票据、连接注册表和推送队列为单实例内存状态；断线通过 HTTP 收件箱和未读数补偿，未实现跨实例广播、持久提示、确认或重放。
- 收件人必须属于当前租户、未删除且为 ACTIVE 的 `off_employee.user_id` 绑定；消息主体与收件人使用同一 JDBC 本地事务，提交后才发本实例提示。
- 新增固定环境验证入口 [`scripts/test/verify-infrastructure-websocket.sh`](../../../scripts/test/verify-infrastructure-websocket.sh)，支持 `--infrastructure`、`--websocket`、`--full`，只调用指定 JDK 环境中的 mvnd。
- workflow 修复 `contextJson/formData`、`currentNodeKey/currentNodeCode` 持久化映射，新增时安装可信租户、初始化时间与版本并回填主键；新增真实 H2/HTTP 测试验证身份与数据库结果。
- office 修复握手映射和 WebSocket 适配器优先级、消息与收件记录默认持久化字段、收件箱最新优先排序，以及入站结束和出站关闭的竞争处理；新增真实 Mapper、事务、生命周期与 Gateway 网络联合测试。

## 边界与不拆项

本次不创建远程基础设施服务，不迁移业务数据库所有权，不改变各业务域 Flyway 目录，不拆分业务领域模块，不把单实例内存提示描述为可靠消息投递，也不声称迁移文件均已补齐 Javadoc。办公站内信与 system 历史消息/SSE 是两套能力。

## 回滚

代码回滚应依据实施计划的逐文件迁移清单反向移动文件，恢复消费者到旧坐标，依据迁移前备份恢复 `apex-infrastructure/pom.xml` 的第三方依赖、插件及构建档位配置并移除其五能力依赖，再移除五个新增 reactor/module 管理项；操作前保留当前工作区备份，不使用 `git reset` 或 `git restore` 覆盖未提交改动。数据库不需要回滚迁移。发布回滚时五个能力 JAR 与兼容 JAR 使用同版本；旧消费者继续使用兼容坐标。

## 静态检查与注释审计

最新静态检查记录：`bash scripts/ci/check-doc-links.sh` 扫描 659 个 Markdown 文件、2498 个链接，断裂链接为 0，退出码为 0；脚本 `bash -n` 与 `git diff --check` 均退出 0。这些检查已在本轮文档更新后复跑；语法检查覆盖两个验证入口、构建初始化及链接检查脚本。检查范围内已无迁移前 `apex-infrastructure/src/...` 路径引起的剩余断链，保留已有链接修复。检查器不验证外部 URL，也不校验代码块、行内代码中的普通路径文本；历史文档中的旧路径文本不能据此视为已全部更新。此前记录的 658 个文件、119 个断链及 2495 个链接均不代表最新检查结果。

本轮五能力模块共有 152 个生产 Java 文件。Tree-sitter AST 统计得到 203 个具名类型、85 个显式构造器、770 个显式方法，共 1058 个声明，均有 Javadoc，且无解析错误。本轮在 10 个文件中补齐了 17 个私有方法的 Javadoc，并修正数据权限 fail-closed 语义、`MailService` 异常说明及 `RemoteRequest` 标签。AST 统计只证明上述声明的 Javadoc 存在性，不表示 1058 份正文均已逐项审校，也不构成 `javadoc` 编译通过。152 个文件仅指五能力模块生产 Java；此前 223 个迁移文件包含其他迁移文件，统计范围不同，不能混用。

以下保留此前逐项只读复核原缺失清单的记录：表内列出的 31 个方法及嵌套类型声明当时均已有显式 Javadoc。行号为此前复核时的声明起始行，本轮补注释后可能变化；这只确认原清单当时的补齐现状，不代表 223 个迁移文件的全部字段、私有方法、测试代码或标签质量均已审查，也不构成 Javadoc 编译或运行验证。

| 文件（相对仓库根目录） | 此前核实具备显式 Javadoc 的声明及当时行号 |
| --- | --- |
| `apex-infrastructure-integration/src/main/java/com/apex/scaffold/infrastructure/audit/DefaultAuditLogService.java` | `log(String,String,String,String,String,Map<String,Object>)` 37；`logAuthEvent(String,String,String,String,boolean,String)` 69；`logPermissionCheck(String,String,String,String,boolean)` 95 |
| `apex-infrastructure-integration/src/main/java/com/apex/scaffold/infrastructure/security/DefaultEncryptionService.java` | `encrypt(String)` 71；`decrypt(String)` 109；`hash(String)` 146 |
| `apex-infrastructure-integration/src/main/java/com/apex/scaffold/infrastructure/metrics/BusinessMetricsService.java` | `recordCreditChange(String,long)` 43 |
| `apex-infrastructure-messaging/src/main/java/com/apex/scaffold/infrastructure/outbox/service/impl/MessageOutboxServiceImpl.java` | `append(MessageOutboxEntity)` 65；`fetchPendingMessages(int)` 107；`lockMessage(String,String)` 129；`markAsDelivered(String)` 144；`markAsFailed(String,String)` 170；`releaseExpiredLocks()` 214 |
| `apex-infrastructure-persistence/src/main/java/com/apex/scaffold/infrastructure/persistence/datapermission/DefaultTenantLineHandler.java` | `getTenantId()` 75；`ignoreTable(String)` 90；`getTenantIdColumn()` 107 |
| `apex-infrastructure-cache/src/main/java/com/apex/scaffold/infrastructure/cache/wrapper/LogicalExpiryWrapper.java` | `isExpired()` 94；`of(T,long)` 108 |
| `apex-infrastructure-remote/src/main/java/com/apex/scaffold/infrastructure/remote/config/AuthCenterPermissionProperties.java` | 嵌套 `Mapping` record 65 |
| `apex-infrastructure-messaging/src/main/java/com/apex/scaffold/infrastructure/outbox/entity/MessageOutboxEntity.java` | 嵌套类型 `MessageType` 109；`Status` 118 |
| `apex-office/src/main/java/com/apex/scaffold/office/off/infrastructure/persistence/repository/OffMessageRepositoryImpl.java` | `findById(Long)` 47；`findByMessageCode(String)` 60；`findBySenderId(Long,Integer,Integer)` 112；`insert(OffMessageEntity)` 136；`update(OffMessageEntity)` 153；`deleteById(Long)` 168；`countByRecipientId(Long)` 181；`countUnreadByRecipientId(Long)` 194；`findPage(Integer,Integer,String,String)` 214 |
| `apex-gateway/src/main/java/com/apex/scaffold/gateway/filter/JwtSignatureFilter.java` | `getOrder()` 194 |

此前也已逐项复核原匿名回调清单（以下行号为当时记录）：`apex-infrastructure-messaging/src/main/java/com/apex/scaffold/infrastructure/mq/service/MqProducerService.java` 的 `onSuccess(SendResult)` 314、`onException(Throwable)` 324，以及 `apex-infrastructure-integration/src/main/java/com/apex/scaffold/infrastructure/wechat/service/WeChatAppAdvancedService.java` 的 `newThread(Runnable)` 92，均已有独立显式 Javadoc。此前对 office 新增 `MessageWebSocketConfig`、`MessageTransactionConfig`、`MessageTenantConfig` 和 `MessageSocketRegistry` 公开方法已有说明的记录予以保留，本次未扩大到这些文件重新审计。

## 实测记录（2026-10-15—2026-10-16）

验证环境通过 `source scripts/build/init-build-env.sh` 加载 JDK `17.0.12-oracle` 和 mvnd `2.0.0-rc-3`。

- 此前一轮全仓 `mvnd -T4 -B clean verify` **失败**：网关 `JwtSignatureFilter.filter` 命中一条 PMD `AvoidComplexConditionRule`。不是测试失败，不能记为全仓门禁通过。
- 该轮五能力 JAR、兼容聚合、office、office-service、job-service 等模块均为 `SUCCESS`；example、system-service、auth-service 被跳过。这些状态仅属于该轮。
- 已将网关握手路径与 Upgrade 头的联合判断提取为具名布尔变量，保持原鉴权语义。随后针对网关和上述跳过模块及其依赖的补充 `verify` 未完成：example 连接 `127.0.0.1:3306/apex_example_test` 被拒，Druid 持续重试，因此主动停止。该次验证不能宣称通过。
- 当时启动本机容器并查询确认 `apex_example_test` 数据库存在。example 的 `application-test.yml` 已显式覆盖 slave 与 master，二者使用同一组 `DB_MASTER_*` 参数，避免 slave 继续继承开发库配置；该测试配置不验证主从复制。后续曾因 OrbStack 停止导致数据库再次不可用；环境随后恢复，当前容器为 `apex-infra-mysql`，状态为 healthy，且测试库存在。
- 第三轮全仓 `clean verify` 已结束（17:22:38—17:47:06），日志为 `/Users/eric/apex-final-verify3.log`。除 example 外反应堆模块全部 SUCCESS；example Failsafe 共 20 项，Failures 0、Errors 17、Skipped 3。17 项错误源于 `mqDrainParticipant` 强依赖最终未装配的 `MqConsumerService`；条件报告显示 participant 条件先匹配，而 consumer 随后因缺少 `RocketMQTemplate` 被排除，最终表现为 JaCoCo 覆盖不足。3 项 workflow 跳过为原有禁用，不能计为通过。未降低覆盖率门槛或跳过失败测试。
- MQ 治理装配修复已改为在 `ApplicationReadyEvent` 后查询最终可用依赖并注册排空适配器，保留已有参与者 Bean 的注册行为，重复就绪事件不重复创建。新增 `GovernanceLifecycleConfigurationTest`：修复前 4 项中 1 项失败（预期的参与者未注册），修复后 4 项全部通过，Failures 0、Errors 0、Skipped 0；定向 `test` 构建 SUCCESS，日志分别为 `/Users/eric/:apex-mq-registration-red.log`、`/Users/eric/:apex-mq-registration-green.log`。该定向结果不替代 example 真实上下文验证。随后执行的 `mvnd -T4 -B -pl :apex-example -am verify`（日志 `/Users/eric/:apex-example-final-verify.log`）因 OrbStack 停止、数据库连接被拒而主动停止，该轮未通过。
- 环境恢复后，最终全仓 `source scripts/build/init-build-env.sh && mvnd -T4 -B clean verify` 于 21:14:41（UTC+08:00）成功结束，耗时 28:47，日志 `/Users/eric/apex-final-verify4.log`。31 个反应堆项目全部 SUCCESS；example 本轮 Failsafe 为 20/0/0/3（Tests/Failures/Errors/Skipped），17 项非跳过测试通过。
- 最终报告编辑后已复跑静态检查：文档链接扫描 659 个 Markdown、2496 个链接、0 个断链；脚本 `bash -n` 与 `git diff --check` 均退出 0。未跟踪报告另行检查行尾空白和结尾换行，不以 `git diff --check` 代替其内容检查。
- 基础、prod 与 Nacos 三份路由均显式声明站内信 WS/HTTP 路由。新增配置契约测试检查路由存在、顺序、协议与熔断回退；此项不等于真实经过网关的 WebSocket 转发验收。
- `bash -n scripts/test/verify-infrastructure-websocket.sh` 退出码 0；没有将语法检查描述为该脚本所有模式的实跑结果。

- 2026-10-16 `/Users/eric/apex-final-verify5.log` 全仓构建自然结束，耗时 15:11，结果 FAILURE：office `OfficeServiceContextLoadTest` 4 个 Errors。gateway `test-support` 传递引入 `GatewayRedisAutoConfiguration`，非 Web 上下文关闭 Gateway 后仍尝试装配缺少 `ConfigurationService` 的 `redisRateLimiter`。仅在该测试增加 `spring.autoconfigure.exclude=org.springframework.cloud.gateway.config.GatewayRedisAutoConfiguration`；未改生产配置、未移除 gateway 联合测试。
- 待上一轮结束后串行执行 `/Users/eric/apex-final-verify6.log`，2026-10-16 08:45:51（UTC+08:00）结束，耗时 20:08，31 个反应堆项目全部 SUCCESS。office 上下文 4 项、Keycloak 5 项、workflow 数据库 HTTP 3 项与新增 office 专项均通过，未降低门禁或跳过失败测试。

### 最终测试统计与门禁

以下统计读取当前根 POM 的 30 个模块各自 `target/surefire-reports/TEST-*.xml` 与 `target/failsafe-reports/TEST-*.xml`，与最终成功轮次对应；排除已退役 `apex-interfaces` 等非当前 reactor 的历史报告。每格依次为 Tests/Failures/Errors/Skipped，Tests 包含跳过项；`0/0/0/0` 表示该模块该类报告无测试计数，不代表新增验证覆盖。根项目与 30 个模块的构建状态均为 SUCCESS。

| 模块 | Surefire（T/F/E/S） | Failsafe（T/F/E/S） |
| --- | ---: | ---: |
| apex-scaffold（根） | 0/0/0/0 | 0/0/0/0 |
| apex-common | 538/0/0/0 | 0/0/0/0 |
| apex-storage-spring-boot-starter | 11/0/0/0 | 0/0/0/0 |
| apex-license-spring-boot-starter | 19/0/0/0 | 0/0/0/0 |
| apex-license-tools | 0/0/0/0 | 0/0/0/0 |
| apex-domain | 9/0/0/0 | 0/0/0/0 |
| apex-infrastructure-persistence | 83/0/0/0 | 0/0/0/0 |
| apex-infrastructure-cache | 130/0/0/0 | 6/0/0/0 |
| apex-infrastructure-messaging | 77/0/0/0 | 2/0/0/0 |
| apex-infrastructure-remote | 84/0/0/0 | 0/0/0/0 |
| apex-infrastructure-integration | 124/0/0/0 | 0/0/0/0 |
| apex-infrastructure | 0/0/0/0 | 0/0/0/0 |
| apex-workflow | 40/0/0/0 | 0/0/0/0 |
| apex-system | 1478/0/0/0 | 12/0/0/0 |
| apex-example | 0/0/0/0 | 17/0/0/0 |
| apex-generator | 19/0/0/0 | 0/0/0/0 |
| apex-services-bom | 0/0/0/0 | 0/0/0/0 |
| apex-api-contract | 30/0/0/0 | 0/0/0/0 |
| apex-gateway | 100/0/0/0 | 2/0/0/0 |
| apex-workflow-service | 36/0/0/0 | 27/0/0/0 |
| apex-file | 59/0/0/0 | 0/0/0/0 |
| apex-file-service | 25/0/0/0 | 50/0/0/0 |
| apex-office | 69/0/0/0 | 0/0/0/0 |
| apex-office-service | 343/0/0/0 | 1/0/0/0 |
| apex-system-service | 4/0/0/0 | 0/0/0/0 |
| apex-auth | 1260/0/0/0 | 0/0/0/0 |
| apex-auth-service | 103/0/0/0 | 179/0/0/0 |
| apex-job-spring-boot-starter | 11/0/0/0 | 0/0/0/0 |
| apex-job-service | 7/0/0/0 | 0/0/0/0 |
| apex-masking-service | 6/0/0/0 | 0/0/0/0 |
| apex-seata-spring-boot-starter | 24/0/0/0 | 1/0/0/1 |
| **合计** | **4689/0/0/0** | **297/0/0/1** |

Surefire 实际通过 4689 项，Failsafe 实际通过 296 项；合计 4986 项，实际通过 4985 项，跳过 1 项。唯一跳过为 `SeataAtIntegrationTest`：普通门禁未启用 `APEX_SEATA_IT=true`，按环境条件跳过；此跳过不覆盖下述独立实测证据。

旧 system `KeycloakConfigTest` 的 3 项禁用已由 5 项离线测试替代，本轮 5/0/0/0：属性绑定、配置 Bean、真实 Admin Client 创建、禁用不装配及缺少必需属性失败；不调用远端认证 API，不证明真实 Keycloak 登录链路。旧 example 的 3 项禁用 workflow 测试已迁移为 workflow-service 的 `WorkflowDatabaseHttpIntegrationTest`，本轮 3/0/0/0，真实随机 HTTP 端口、H2 Mapper 验证可信身份启动、当前用户待办与拒绝未认证写入；其身份输入模拟网关中继，不属于真实 Gateway 联合测试。example 当前 Failsafe 17 项全部通过。

独立 Seata AT 验证已于 2026-10-15 在真实 TC 与 MySQL 双库运行：AT 集成测试 1 项通过、0 跳过，两个数据库分支的提交和失败回滚均有 TC 状态与余额证据，临时 TC、双库及账号已清理。当时由 `scripts/test/verify-seata-at.sh` 编排；该脚本已删除，改为模块内 JUnit 扩展 `SeataRealCluster` 编排，2026-10-26 已用 `APEX_SEATA_IT=true mvnd -B -f apex-seata-spring-boot-starter/pom.xml verify` 一次跑通 AT/TCC/SAGA/XA 四模式。详见 [Seata 集成指南](../../05-operations/seata-integration.md) 与 [四模式测试报告](../../06-testing/seata-real-modes-test-report.md)。这些结果仍不覆盖五业务服务完整部署或生产 HTTPS/TC 恢复验收。

最终成功轮次包含当前 POM 绑定范围内的 Spotless、PMD/p3c、ArchUnit 测试及 JaCoCo 覆盖率检查，均已通过；未降低覆盖率门槛或通过跳过失败测试取得成功。ArchUnit 仅证明现有规则声明的包与目录范围，JaCoCo 仅证明当前有效配置及排除项下的门槛，不扩展为所有业务路径或生产条件已覆盖。此门禁不替代前述 Javadoc 正文审校，也不构成独立 `javadoc` 编译通过。

### office/gateway 专项证据

本轮 `apex-office-service` 与 `apex-gateway` 的 Surefire/Failsafe 报告确认以下测试均为 Failures 0、Errors 0、Skipped 0（合计 31 项；其中真实 gateway 联合测试 1 项由 Failsafe 执行）：

| 测试类 | Tests run | 主要验证 |
| --- | ---: | --- |
| MessageWebSocketTest | 1 | 本机 Netty 直连握手、Origin 拒绝、票据重放拒绝及定向提示 |
| MessageSocketRegistryTest | 5 | 一次性票据及并发消费、队列/连接容量与释放、租户与用户隔离 |
| MessageSocketLifecycleTest | 3 | 真实网络帧边界、心跳/寿命关闭、队列溢出及连接释放 |
| MessageDatabaseWebSocketTest | 1 | 真实 H2 Mapper、事务回滚/提交提示、已读/未读及离线收件箱补偿 |
| MessageGatewayDatabaseIntegrationTest | 1 | 独立 Gateway/office Netty 网络 HTTP/WS、身份头清洗及数据库闭环 |
| MessageDeliveryTransactionTest | 3 | H2 JDBC 事务回滚、员工绑定与租户校验 |
| MessageOwnershipTest | 3 | 消息归属、可信租户上下文及异步边界 |
| IdentityRelayAuthenticationFilterTest（office） | 4 | JWT 身份优先、刷新/过期令牌拒绝及非 GET 握手拒绝 |
| OfficeServiceContextLoadTest | 4 | basic/H2 上下文与事务、握手组件装配 |
| GatewayRouteProfileConfigTest | 3 | 基础、prod、Nacos 配置路由声明契约 |
| GatewayApplicationTest | 3 | dev 上下文、过滤器顺序与路由绑定 |

新增数据库证据：`MessageDatabaseWebSocketTest` 使用真实 H2（MySQL mode）、MyBatis Mapper、租户插件与事务管理器，通过实际 HTTP 路由发送消息；第二个收件人的真实 CHECK 约束失败时，消息与全部收件记录回滚且无提交提示。成功时收到 WebSocket 提示前两表已提交，并验证已读时间、未读数、断线后发送与 HTTP 收件箱补偿、其他用户隔离。旧 `MessageDeliveryTransactionTest` 的 Mapper 替身测试仍保留，不能将其单独描述为真实 MyBatis 验证。

新增网络证据：`MessageGatewayDatabaseIntegrationTest` 启动独立 gateway 与 office Netty 端口，导入真实 Gateway 自动配置与生产 JWT/身份中继过滤器；HTTP 和 WS 路由指向同一 office，经网关验证未认证拒绝、伪造身份及 Seata 头清洗、Origin 拒绝、票据重放拒绝，以及 HTTP 发送→H2 提交→事件→WebSocket 提示→HTTP 收件箱/未读数的本地闭环。其独立装配不等于完整业务服务及外部部署启动验收。

生命周期证据：`MessageSocketLifecycleTest` 验证 UTF-8 1024 字节帧可用、1025 字节以 1009 关闭，无 pong 时策略关闭、持续应答时寿命到期正常关闭、64 条通知队列溢出关闭及连接释放。该测试注入较短心跳、超时和寿命参数，未等待生产 20 秒/60 秒/一小时周期。注册表另验证 1024 连接容量及释放槽位、并发单次票据消费与租户/用户隔离。

以上已覆盖真实 H2 Mapper、事务与 Gateway 网络本地闭环；H2 MySQL mode 不证明生产 MySQL/PostgreSQL 迁移语义，单实例内存状态不证明跨实例广播，环回 HTTP/WS 不证明 HTTPS/WSS 反向代理。不能把专项表全部项目称为端到端测试。

## 待生产验证

- 在目标 MySQL/PostgreSQL、Redis、MQ、第三方集成和真实密钥配置下启动相关服务，核对五能力自动装配与 Flyway 归属。
- 核对 gateway 的 HTTP/WS 两个目标在单实例、反向代理 HTTPS/WSS、Origin 白名单和 Upgrade 头条件下保持一致。
- 验证多实例部署的粘性路由或后续共享票据/跨实例事件方案；当前实现不能作为跨实例实时必达方案。
- 进行真实 office 消息发送、收件箱补偿、未读数、断线重连、租户隔离和敏感查询参数脱敏验收。
