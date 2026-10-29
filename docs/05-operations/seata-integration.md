# Seata 接入与运行限制（AT / TCC / SAGA / XA 多模式）

## 交付范围

`apex-seata-spring-boot-starter` 使用真实 Apache 客户端 `org.apache.seata:seata-all:2.2.0`，由本模块管理 TM/RM 生命周期、数据源代理、同步全局事务和受保护 XID 传播。坐标可在 [Maven Central](https://repo.maven.apache.org/maven2/org/apache/seata/seata-all/2.2.0/) 核查；参考 [Seata 文档](https://seata.apache.org/docs/)。模块在 Java 21（Temurin `21.0.13`）、Spring Boot 3.5.15 管理的 Spring 6 依赖下通过编译和单元测试；这不代表生产 TC、数据库及故障恢复已验证。排除客户端传递的 spring-webmvc，不引入上游注解事务扫描器。

starter 支持通过 `apex.seata.mode` 选择 **AT（默认）/ TCC / SAGA / XA** 四种事务模式，模式决定数据源代理、必需的表结构和事务边界写法，详见下节。默认值 AT 与既有接入方式兼容：只配置 `apex.seata.enabled=true` 时行为与历史版本一致。

五个业务服务添加依赖并默认关闭。已有业务方法不会自动加入全局事务；接入需明确改为下文对应模式的执行器边界。本模块不支持 R2DBC、H2、文件存储、消息发送或第三方平台写操作的 AT 回滚。

根 reactor 已注册 `apex-seata-spring-boot-starter`，Seata 2.2.0 及内部 starter 版本已收敛到根依赖管理。Compose 提供可选 `seata` profile、外部配置挂载和持久日志卷；这不代表 TC 数据库、可达性或真实事务已验证。

## 事务模式选择

通过 `apex.seata.mode` 选择模式，取值 `AT`（默认）、`TCC`、`SAGA`、`XA`，大小写不敏感。四种模式共用同一套 XID 传播过滤（`SeataXidPropagation`）、入站校验与 Outbox 防护（`SeataOutboxGuard`），差异集中在数据源处理、必需表结构、生命周期所有者与事务边界写法。

| 维度 | AT（默认） | TCC | SAGA | XA |
| --- | --- | --- | --- | --- |
| 补偿方式 | 自动生成前镜像，自动回滚 | 业务自实现 Try/Confirm/Cancel | 状态机编排，正向/补偿节点 | 数据库 XA，两阶段提交 |
| 数据源代理 | `DataSourceProxy`（`wrapDataSource`） | 不包装，扫描器代理 `@LocalTCC` 动作 | 不包装，引擎直接持有原始数据源 | `DataSourceProxyXA` |
| 必需表结构 | 各业务库 `undo_log` | 无额外表（业务自建 Try/Cancel 幂等表） | SAGA 存储三表 | 无额外表（依赖数据库 XA 驱动） |
| 生命周期所有者 | 本模块 `ClientLifecycle` | `GlobalTransactionScanner`（同名 bean `seataTccScanner`） | 本模块 `ClientLifecycle` | 本模块 `ClientLifecycle` + 显式注册 `ResourceManagerXA` |
| 事务边界入口 | `SeataTransactionExecutor.execute/branch` | `@LocalTCC` + `@TwoPhaseBusinessAction` 方法 | `SagaExecutor.start/forward/compensate` | `SeataTransactionExecutor.execute/branch` |
| 隔离级别 | 读未提交（默认），依赖全局锁 | 业务保证 | 最终一致 | 强隔离（数据库级） |
| 单 JDBC 约束 | 要求 | 无强制（由业务自行组织） | 要求（引擎持久化编排状态） | 要求 |

### 各模式前置条件

- **AT**：业务库执行所属 Flyway 迁移生成 `undo_log`（见下节部署顺序第 2 条）；数据源必须为单 JDBC，动态路由数据源会被 [SeataAutoConfiguration.java](../../apex-seata-spring-boot-starter/src/main/java/com/apex/scaffold/seata/SeataAutoConfiguration.java) 直接拒绝；AT 代理要求真实 MySQL/PostgreSQL，H2 等测试库不在 Seata AT dbtype 支持列表，会报 `AT mode don't support the dbtype: h2`。
- **TCC**：业务类实现 Try/Confirm/Cancel 三段，用 `@LocalTCC` 标注接口、`@TwoPhaseBusinessAction` 标注 Try 方法；Confirm/Cancel 必须幂等、可重入。TCC 模式不包装数据源，分支注册由动作代理完成。
- **SAGA**：先执行 SAGA 存储三表建表脚本，再提供状态机 JSON 定义。建表脚本随 starter 发布在 classpath：
  - MySQL：`sql/saga/mysql/seata_state_machine.sql`
  - PostgreSQL：`sql/saga/postgresql/seata_state_machine.sql`

  脚本源自 Apache Seata 2.2.0 官方 DDL，表名前缀为 `seata_`（对应 `apex.seata.saga.table-prefix` 默认值）。这些表由使用方按需手工执行，不纳入业务 Flyway 版本链；`table-prefix` 改动时须与脚本中的表名一致。状态机定义 JSON 通过 `apex.seata.saga.state-machine-resources` 配置 classpath 资源模式，留空只装配引擎、定义后续经 API 注册。
- **XA**：数据库驱动须支持 XA（MySQL Connector/J、PostgreSQL JDBC 均支持）；无需 `undo_log`，无需额外建表。starter 在 `ClientLifecycle.start()` 中显式注册 `ResourceManagerXA`，因为 seata-all 手动初始化模式下 XA 分支资源管理器不随 `RMClient.init()` 自动注册。

### 数据源代理适用矩阵

`undo_log` 仅 AT 模式需要，TCC/SAGA/XA 均不需要：

| 模式 | 是否包装数据源 | 是否需 `undo_log` |
| --- | --- | --- |
| AT | 是（`DataSourceProxy`） | 是 |
| TCC | 否 | 否 |
| SAGA | 否 | 否 |
| XA | 是（`DataSourceProxyXA`） | 否 |

### 模式配置示例

```yaml
apex:
  seata:
    enabled: true
    mode: SAGA                       # AT / TCC / SAGA / XA，缺省 AT
    application-id: apex-system-service
    transaction-service-group: apex_tx_group
    saga:                            # 仅 SAGA 模式读取，其他模式忽略
      table-prefix: seata_
      state-machine-resources:
        - classpath:saga/*.json
```

## 部署顺序

1. 部署与客户端版本匹配的 Seata 2.2.0 TC，配置持久化存储及官方 TC 数据库表、独立数据库账号、注册中心或静态地址。TC 表与业务库 undo_log 不同，不能互相替代。TC RPC 8091 和管理端口仅允许内网访问，不能直接暴露公网。生产配置 TC 高可用、备份、认证和告警。
2. 业务库先执行所属 Flyway 迁移：system V61、auth V57、workflow V2、file V3、office V3。每个库均有 MySQL/PostgreSQL 成对 undo_log；MySQL 使用 InnoDB，PostgreSQL rollback_info 使用 BYTEA。undo_log 是 Seata 内部表，不加租户字段或 MyBatis 业务拦截规则。**仅 AT 模式需要 undo_log**；TCC/XA 无需该表，SAGA 另需状态机存储三表（见「事务模式选择」）。开启 AT 前确认该库的迁移确实已执行。
3. 按所选模式准备数据源：**AT / SAGA / XA** 要求单 JDBC 数据源，系统 dev/prod 等已有动态数据源配置须切换到专用单数据源配置集，并排除 dynamic-datasource 自动配置。仅设置 dynamic.seata=false 不够：当前 starter 检测动态数据源会直接拒绝启动。禁止同时启用上游数据源自动代理、手工代理及本 starter，避免双代理。**TCC** 不强制单数据源，由业务自行组织本地事务。
4. 向 Seata 客户端提供官方 `registry.conf`、`file.conf`，或等价官方配置中心配置。配置文件放在外部 classpath；本 starter 使用 seata-all，不自动把 Spring `seata.*` 属性转换为 Seata 官方配置。文件模式示例见下节。配置中心的凭据与 TC 认证按官方版本文档设置，禁止打印密钥。
5. 配置内部 HTTPS、网络访问控制和传播密钥；网关不向公网注册 `/internal/seata/` 路由，清除外部请求的 `X-Apex-Seata-Xid` 与 `X-Apex-Seata-Key`。独立随机密钥至少 32 字符，不能复用登录令牌或数据库密码。现有 Spring Security 的业务权限仍须保留，传播凭据不替代业务授权。
6. 先在隔离环境执行跨库提交、回滚和 TC 故障恢复测试，再逐服务显式启用。关闭时先停止新事务并等待已有全局事务结束；不要在仍有待回滚事务时移除代理或删除 undo_log。

## 配置

业务服务现有主配置均包含 `apex.seata.enabled=${APEX_SEATA_ENABLED:false}`。关闭时不初始化 TM/RM、不代理数据源、不启用传播或 Outbox 切面。

启用示例（以受保护配置中心覆盖主配置）：

```yaml
apex:
  seata:
    enabled: true
    mode: AT                         # AT / TCC / SAGA / XA，缺省 AT
    application-id: apex-system-service
    transaction-service-group: apex_tx_group
    timeout-millis: 30000
    inbound-path-prefix: /internal/seata/
    propagation-secret: ${APEX_SEATA_PROPAGATION_SECRET}
    allowed-origins:
      - https://workflow.internal:8443
```

`mode` 大小写不敏感，缺省 `AT` 保持既有行为；非法取值在绑定阶段抛 `BindException` 拒绝启动。`saga.table-prefix` 与 `saga.state-machine-resources` 仅 SAGA 模式读取。

`application-id` 每个服务不同，事务组必须与 TC 映射一致；出站 origin 精确匹配 scheme、host、port，无通配符。默认空列表不会向任何目标发送 XID。凭据必须通过秘密管理注入，各参与方使用同一组受信传播凭据。内部调用必须启用 TLS 并验证服务证书；禁止绕过证书校验。

外部 `registry.conf` 文件模式最小结构：

```hocon
registry { type = "file" }
config { type = "file" file { name = "file.conf" } }
```

外部 `file.conf`（示例地址须替换）：

```hocon
service {
  vgroupMapping.apex_tx_group = "default"
  default.grouplist = "tc.internal:8091"
  disableGlobalTransaction = false
}
```

测试事务组为 `apex_seata_it_group`，需另配 `vgroupMapping.apex_seata_it_group = "default"`。Nacos 等注册/配置中心方案遵循 Apache Seata 对应版本的官方配置；不能假定项目 Spring Nacos 配置会自动配置 Seata。

## 接入同步事务边界

本节的执行器写法适用于 **AT / XA** 模式（XA 使用相同 `SeataTransactionExecutor`，差别仅在数据源代理类型和数据库 XA 能力）。TCC 与 SAGA 的边界写法见本节末尾。

注入 `SeataTransactionExecutor`。入口应用用例返回 Mono，内部业务回调完全同步；执行器在 `BlockingIoSchedulers.jdbc()` 运行 begin、回调、commit/rollback，并 finally 清理 RootContext。有受信入站 XID 时仅参与事务，不拥有全局提交。

```java
public Mono<Integer> updateLocally() {
    return seata.execute("update-system", () ->
        transactionTemplate.execute(status -> mapper.updateById(entity)));
}
```

多个 JDBC 写步骤必须包在同一个同步 `TransactionTemplate` 回调中；返回 Mono 的方法不能直接标注 `@GlobalTransactional` 或 JDBC `@Transactional`。回调返回 Publisher 会被拒绝并触发回滚。失败应向上游传播，禁止把失败恢复为无依据的成功。

下游 Handler 在已有入站 XID 下执行完整本地分支：

```java
return seata.branch(() -> transactionTemplate.execute(status -> mapper.updateById(entity)));
```

`branch` 无 XID 时是普通同步调用，仍需本地事务；不能假定所有现有仓储内部调度任务都会自动携带 XID。应将完整本地事务放在回调内，避免回调再切换线程。

受管 WebClient 显式附加过滤器：

```java
WebClient internalClient = ManagedWebClientFactory.create("https://workflow.internal:8443")
    .mutate().filter(propagation.outbound()).build();
```

本 starter 不修改静态受管工厂，也不自动给任意 WebClient 传播事务。过滤器清除手工提供的传播头，仅向 allowed-origins 中的目标发送当前受信 XID。对响应式分支调用，由 Reactor Context 提供 XID；同步事务回调发起并同步等待内部 HTTP 时，从 RootContext 读取 XID。同步 HTTP 等待只允许在此 JDBC 编排边界发生，必须设置有限超时，不能在 Handler/Netty 线程 block。远程调用不能放在持有本地数据库事务锁的回调内：先结束本地分支，再在全局边界内调用下游，缩短锁占用。

入站有 XID 却不满足内部路径、独立密钥或格式约束时返回 403；无 XID 时不改变现有请求行为。若全局请求跨异步链路，继续通过 Reactor Context 传递，不自行在 Netty 线程 bind RootContext。

### TCC 模式边界

TCC 模式不经过 `SeataTransactionExecutor`；由 `GlobalTransactionScanner` 代理 `@LocalTCC` 接口的 `@TwoPhaseBusinessAction` 方法，Seata 在全局事务上下文中调用 Try，提交时调用 Confirm、回滚时调用 Cancel：

```java
@LocalTCC
public interface AccountTccAction {
    @TwoPhaseBusinessAction(name = "prepareAccount", commitMethod = "confirm", rollbackMethod = "cancel")
    boolean prepare(BusinessActionContext context, @BusinessActionContextParameter(paramName = "accountId") String accountId);

    boolean confirm(BusinessActionContext context);

    boolean cancel(BusinessActionContext context);
}
```

Confirm/Cancel 必须幂等且可重入：TC 可能重试。TCC 模式启用后，TM/RM 生命周期由扫描器（bean `seataTccScanner`）在上下文刷新时完成初始化，不再创建本模块 `ClientLifecycle`，避免双重初始化。

### SAGA 模式边界

SAGA 模式注入 `SagaExecutor`，以 Mono 门面暴露状态机启动、推进与补偿，阻塞引擎调用由 `BlockingIoSchedulers.jdbc()` 隔离：

```java
public Mono<String> submitOrder(String businessKey, Map<String, Object> payload) {
    return saga.start("orderSaga", businessKey, payload);   // 返回状态机实例 id
}

public Mono<Map<String, Object>> inspect(String instanceId) {
    return saga.getInstance(instanceId);                    // 导出状态快照，不含入参原文
}
```

`start` 启动新实例，`forward` 推进、`compensate` 触发补偿。状态机定义 JSON 通过 `apex.seata.saga.state-machine-resources` 指定 classpath 资源；留空时仅装配引擎，定义后续经 API 注册。SAGA 存储三表须先行建好，见「事务模式选择」章节。

## Outbox 与外部副作用

现有 TransactionalOutboxExecutor、DomainEventOutboxStore 及 MessageOutboxServiceImpl 只保证本地事务。AT / XA 分支本地提交后，PENDING 事件即对 dispatcher 可见，全局事务仍可能回滚。当前实现没有解决该窗口。

启用 starter 后，切面在全局事务中拒绝上述 Outbox 写入口（适用全部模式）；响应式 append 同时检查订阅时的 Reactor XID。不要通过自调用、直接 Mapper/JDBC、主动清空上下文或切换线程绕过保护。直接调用不受 Spring 代理的对象不在保护范围，代码接入审查必须确认使用受管入口。

可操作替代方案：不要在 AT 回调中写当前 Outbox。对于要求业务与消息可靠一致的流程，优先采用本地事务 + 当前 Outbox + 幂等消费者 + 显式补偿，取消该流程的 AT。若确需 AT，则另行设计持久化事务意图/状态协调表，消费者只消费明确全局提交后的状态，配套恢复扫描、幂等键、TC 最终状态核查和人工恢复；在该协议实现与故障测试完成前，禁止宣称 AT 与当前 Outbox 已原子一致。简单在 execute 成功后写 Outbox 会有进程崩溃丢消息窗口，只适用于允许丢失的通知，不能作为可靠替代。

## 验证

所有构建先激活固定环境：

```bash
source scripts/build/init-build-env.sh
mvnd -T4 -B -f apex-seata-spring-boot-starter/pom.xml test
```

独立模块 `clean verify` 已通过：21 个单测成功，Spotless、PMD/p3c 和 JaCoCo 门禁通过；依赖树确认没有 spring-webmvc 或 jakarta.servlet-api。单测覆盖提交/失败/参与已有事务、begin/commit 失败、线程隔离、响应式回调拒绝、关闭默认、启用配置校验、安全入站与目标限制、Outbox 的同步和订阅保护，以及**模式绑定与按模式装配**（缺省 AT、大小写不敏感、非法值拒绝、AT/XA 代理决策、TCC 扫描器装配、SAGA 引擎在单数据源上的装配）。模式相关测试为无网络测试：不触发 TM/RM netty 初始化，SAGA 仅验证引擎与存储配置绑定，不调用 `afterPropertiesSet`（其内部会初始化 TM 并连接 TC）。它们使用 mock 事务对象与装配断言验证边界，不证明数据库 AT 回滚或四种模式的真实端到端提交/回滚。

### 真实环境集成测试

四个模式的端到端验证都是 `apex-seata-spring-boot-starter` 模块内的标准 JUnit 集成测试（`*IntegrationTest`，由 Failsafe 在 `verify` 阶段执行），不通过 `.sh` 脚本或 Python/Ruby 载体编排：

| 测试类 | 模式 | 主要验证 |
| --- | --- | --- |
| `SeataAtIntegrationTest` | AT | 真实 TC + 两个独立 MySQL 库 + 两个同 JVM 的本机 HTTP 分支；全局提交后两库余额变更，强制异常后由真实 AT 回滚恢复 |
| `SeataTccIntegrationTest` | TCC | 两个 `@LocalTCC` 动作经扫描器代理并向真实 TC 注册；TC 回调 Confirm / Cancel，动作名与 target bean 不串用 |
| `SeataSagaIntegrationTest` | SAGA | 状态机引擎装配、businessKey 幂等、本地失败补偿、外层失败走 TC 二阶段回滚 |
| `SeataXaIntegrationTest` | XA | 二阶段前 `XA RECOVER` 恰好 2 条 prepared 分支，提交与回滚后均无残留，两库余额分别符合预期 |

环境由 JUnit 扩展 `SeataRealCluster` 在测试进程内编排：校验 MySQL 容器健康与宿主端口映射、确认 TC 镜像本地存在（`--pull=never`，不隐式拉取）、启动唯一命名的 TC 容器并按日志完成就绪判定、创建 `apex_seata_it_<suffix>_a/b` 双库与专用账号、写入 file 注册/配置及事务组属性；成功时删除全部资源，失败时保留并输出资源名以便排查。各集成类通过 Failsafe `reuseForks=false` + `forkCount=1` 在独立 JVM 中运行，隔离 Seata 静态 TM/RM。

前置条件：podman 中已存在运行且健康的 MySQL 容器（默认名 `apex-infra-mysql`，宿主 `127.0.0.1:3306`），本地已准备官方 TC 镜像：

```bash
# 镜像不存在时先显式准备；测试本身不会自动拉取。
podman pull apache/seata-server:2.2.0
source scripts/build/init-build-env.sh
APEX_SEATA_IT=true mvnd -B -f apex-seata-spring-boot-starter/pom.xml verify
```

- `APEX_SEATA_IT=true` 是显式开关；未设置时四类集成测试跳过，此时模块通过不构成四模式端到端验证。
- 可用 `-Dapex.seata.it.image`、`-Dapex.seata.it.mysql-container` 覆盖 TC 镜像与 MySQL 容器名（系统属性）。
- 测试会建表并重置 `apex_seata_it_account` 等测试表，且会创建/删除专用库与账号；不要指向生产环境或共享实例。
- 判定只采用本次运行生成的 Failsafe XML：测试数正确且 `failures`、`errors`、`skipped` 全为 0。

2026-10-26 已实际执行上述命令：JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`、Seata Server `2.2.0`、MySQL `9.0`（容器 `apex-infra-mysql`，服务端 `9.0.1`），`BUILD SUCCESS`。AT 1 项、TCC 4 项、SAGA 1 项、XA 2 项全部通过且未跳过（共 8 项），单元测试 21 项通过，Spotless、PMD/p3c、JaCoCo 门禁通过。详细用例、证据与边界见 [Seata 真实四模式集成测试报告](../06-testing/seata-real-modes-test-report.md)。

依次运行四个模式时还需注意两点真实环境约束：podman 的端口转发在容器启动瞬间即接受宿主连接，因此 TC 就绪必须以容器日志出现 `seata server started` 为准；MySQL 中非 `SUPER` 账号执行 `XA RECOVER` 需显式持有 `XA_RECOVER_ADMIN`，且其结果是服务器级视图、不按库过滤。

此次仅验证 MySQL（InnoDB）、TC file 存储、单机 TC 和测试进程内的分支服务；MySQL/PostgreSQL Flyway SQL 的实际数据库执行、五个业务服务启用启动、生产 HTTPS 传播、TC 持久数据库与故障恢复仍需部署环境验证。不得将这些边界记为已通过。

## 运维与后续统一事项

监控全局/分支超时、回滚失败、TC 可用性与连接数。AT 模式额外关注 undo_log 堆积，SAGA 模式关注状态机实例表（`seata_state_machine_inst`、`seata_state_inst`）堆积与补偿失败，XA 模式关注数据库 XA 事务残留与两阶段提交挂起。全局回滚可能异步重试，失败响应不代表数据库已经恢复；保留 TC 和业务 RM，分析脏数据冲突后按官方恢复流程处理，不能直接删除日志。限制全局事务时长，避免大批量写、DDL、无主键表、复杂 SQL 或无法被对应模式支持的语句（AT 需数据库解析器支持）。

统一集成已完成根模块/依赖版本注册、四模式装配（`apex.seata.mode`）、SAGA 存储建表脚本发布、Compose 可选 TC 与外部配置挂载入口，网关已剥离传播头。四种模式均已在真实 TC（file 存储）+ 两个真实 MySQL 库下通过端到端验证，见上文「验证」章节与 [测试报告](../06-testing/seata-real-modes-test-report.md)；仍需在部署环境验证 TC 持久数据库、内部 TLS 与网络策略。现有业务流程未自动加入全局事务。
