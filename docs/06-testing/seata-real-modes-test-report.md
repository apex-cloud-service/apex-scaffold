# Seata 真实四模式集成测试报告（AT / TCC / SAGA / XA）

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

> 报告日期：2026-10-26 ｜ 执行环境：macOS 15.8.2（x86_64，JDK `21.0.13.0+9-tem` + Maven Daemon `2.0.0-rc-3`，容器运行时 podman 5.8.9 / crun 5.8.7）

## 一、测试概览

| 项目 | 结果 |
| --- | --- |
| 模块 | `apex-seata-spring-boot-starter` |
| 执行命令 | `APEX_SEATA_IT=true mvnd -B -f apex-seata-spring-boot-starter/pom.xml clean verify` |
| 反应堆结果 | `BUILD SUCCESS`（exit=0，耗时 03:13 min，`Finished at: 2026-10-26T23:48:36+08:00`） |
| 集成测试（Failsafe） | 8 项通过，`failures=0 / errors=0 / skipped=0` |
| 单元测试（Surefire） | 21 项通过（`SeataModeTest` 13、`SeataSafetyTest` 8） |
| 质量门禁 | Spotless、PMD/p3c、JaCoCo 全部通过 |
| 真实依赖 | Seata Server `2.2.0` 容器 1 个 + 本机 MySQL 容器内两个独立库 + 专用最小权限账号 |

本次运行不再使用任何 `.sh` 脚本或 Python/Ruby 载体：TC 与双库的生命周期由 JUnit 扩展 `SeataRealCluster` 在测试进程内直接编排，`podman` 仅作为外部命令被 Java 调用。

## 二、环境编排方式

`SeataRealCluster` 是 `BeforeAllCallback` + `ExtensionContext.Store.CloseableResource` 实现的 JUnit 扩展，通过 `@RegisterExtension static final` 字段为每个集成测试类提供一次独立环境：

| 阶段 | 动作 |
| --- | --- |
| 前置校验 | 确认 MySQL 容器运行且健康、宿主端口映射可读；确认 `apache/seata-server:2.2.0` 镜像本地存在（`--pull=never`，不隐式拉取） |
| TC 启动 | `podman run -d --name apex-seata-it-<suffix> -p 127.0.0.1::18091 -e STORE_MODE=file`，由 podman 动态分配宿主端口后经 `podman port` 读回 |
| TC 就绪 | 轮询 `podman logs` 直到出现 `seata server started`（约 6~7 秒），再做端口探测 |
| 数据准备 | 创建 `apex_seata_it_<suffix>_a` / `_b` 两个空库，创建随机密码专用账号并授予两库权限与 `XA_RECOVER_ADMIN` |
| 属性注入 | 写入 `registry.type=file`、`config.type=file`、`seata.config.name=seata-test`、`service.vgroupMapping.apex_seata_it_group=default`、`service.default.grouplist=127.0.0.1:<tcHostPort>`，SAGA 额外开启 `client.rm.sagaBranchRegisterEnable` |
| 清理 | 成功时删除容器、两库与账号；失败时保留并输出资源名，避免丢失回滚证据 |

`pom.xml` 为 Failsafe 设置 `reuseForks=false` + `forkCount=1`，四个模式的集成类各跑在独立 JVM 中，隔离 Seata 静态 TM/RM 单例。

## 三、分模式结果

### 3.1 AT（`SeataAtIntegrationTest`）：1/1 通过

真实 TC + 两个独立 MySQL 库 + 两个同 JVM 的本机 HTTP 分支服务，无 mock 数据源或事务管理器。

| 用例 | 状态 | 验证内容 |
| --- | --- | --- |
| `commitsAndRollsBackAcrossTwoHttpServicesAndDatabases` | ✅ | 全局提交后两库余额 100→90；第二次扣款后主动抛错，等待真实 AT 回滚使两库余额恢复 90 |

### 3.2 TCC（`SeataTccIntegrationTest`）：4/4 通过

两个 `@LocalTCC` 动作 Bean 由真实 `GlobalTransactionScanner` 代理并向真实 TC 注册。

| 用例 | 状态 | 验证内容 |
| --- | --- | --- |
| `usesScannerProxyAndDistinctActionNames` | ✅ | 两个动作均为 AOP 代理；动作名 `accountATccAction` / `accountBTccAction` 均已注册到 `TCCResourceManager`，且 target bean 不互相串用 |
| `commitsBothBranchesAndTcInvokesConfirm` | ✅ | 两个分支 Try 成功后由 TC 回调 Confirm（每次恰好 1 次、`fromRm=true`）；余额扣减生效 |
| `callbackFailureRollsBackBothBranchesAndTcInvokesCancel` | ✅ | 业务回调抛错后 TC 对两个分支均回调 Cancel；两库余额不变 |
| `failedTryLeavesNoReservationAndTcCancelsEarlierBranch` | ✅ | 第二个分支 Try 失败时不产生预留，TC 对先前分支执行 Cancel |

### 3.3 SAGA（`SeataSagaIntegrationTest`）：1/1 通过

单个用例内按顺序断言四段语义：

| 断言段 | 验证内容 |
| --- | --- |
| `assertRealAssembly` | 引擎上下文恰好 1 个 `DataSource` 且装配 `StateMachineEngine`；第二库上下文不装配 Seata 属性与引擎 |
| `assertSuccessfulSagaAndBusinessKeyIdempotence` | 两库状态机状态随真实 TC 注册推进；同一 businessKey 重复启动不重复执行 |
| `assertLocalFailureCompensatesWithoutPretendingToBeTcRollback` | 本地失败触发补偿链路，且不伪装成 TC 二阶段回滚 |
| `assertOuterFailureUsesTcPhaseTwoRollback` | 外层异常使全局事务回滚，两库状态与补偿审计记录对应真实 TC 二阶段 |

### 3.4 XA（`SeataXaIntegrationTest`）：2/2 通过

每个用例前重置两库余额并断言 `XA RECOVER` 为空，保证基线干净。

| 用例 | 状态 | 验证内容 |
| --- | --- | --- |
| `globalCommitPreparesThenClearsBothXaBranches` | ✅ | 全局二阶段之前 `XA RECOVER` 恰好返回 2 条 prepared 分支（formatId=9752、gtrid=本次 XID、bqual 以 `-` 开头、branchId 互不相同）；提交后两库余额各减 10，且无残留 prepared |
| `globalRollbackPreparesThenClearsBothXaBranches` | ✅ | 同样先出现 2 条 prepared 分支；强制异常回滚后两库余额恢复原值，且无残留 prepared |

## 四、证据（本次运行的 Failsafe / Surefire XML）

`apex-seata-spring-boot-starter/target/failsafe-reports/`：

| 报告文件 | tests | failures | errors | skipped |
| --- | --- | --- | --- | --- |
| `TEST-com.apex.scaffold.seata.SeataAtIntegrationTest.xml` | 1 | 0 | 0 | 0 |
| `TEST-com.apex.scaffold.seata.SeataTccIntegrationTest.xml` | 4 | 0 | 0 | 0 |
| `TEST-com.apex.scaffold.seata.SeataSagaIntegrationTest.xml` | 1 | 0 | 0 | 0 |
| `TEST-com.apex.scaffold.seata.SeataXaIntegrationTest.xml` | 2 | 0 | 0 | 0 |

`apex-seata-spring-boot-starter/target/surefire-reports/`：`SeataModeTest` 13/0/0/0、`SeataSafetyTest` 8/0/0/0。

各集成类耗时（同一次 `verify` 生成的 Failsafe XML）：AT 51.43s、TCC 41.17s、SAGA 42.60s、XA 35.94s。

通过判定只采用本次运行产生的报告：模拟 TC/RM、条件跳过、旧报告残留以及手工 Confirm/Cancel/补偿均不计为通过。

## 五、本轮定位的两个真实环境问题

1. **TC 就绪误判**：podman 的端口转发在容器启动瞬间即接受宿主 TCP 连接，仅做端口探测会把「Seata 尚未初始化完成」当作就绪，客户端随后在 TM/RM 注册时收到 `StacklessClosedChannelException`。已改为以容器日志 `seata server started` 为就绪判据，再做端口探测。
2. **`XA RECOVER` 权限与作用域**：
   - 非 `SUPER` 账号必须显式持有 `XA_RECOVER_ADMIN`，否则返回 `XAE03 / 1401 XAER_RMERR`（本机 MySQL 9.0 容器 `apex-infra-mysql`，服务端 9.0.1 复现）。`SeataRealCluster` 建号时已补充该授权。
   - `XA RECOVER` 是服务器级视图，一次返回实例内全部 prepared 分支，不存在按库过滤。因此断言改为「恰好 2 条分支且 XID 归属本次全局事务」，两库归属由二阶段后两库余额分别变化来证明。

## 六、边界与未覆盖项

- 仅验证 MySQL（InnoDB）、TC `file` 存储、单机 TC；未覆盖 TC 持久数据库（DB 存储）、多 TC 集群与故障切换。
- AT/TCC 的分支服务与 SAGA 的引擎上下文均在测试 JVM 内，不是五个完整业务应用的启动验证；未覆盖生产 HTTPS 传播过滤器与网关链路。
- MySQL/PostgreSQL Flyway 脚本在真实库上的执行、Seata 模式在业务服务中的实际启用，仍需部署环境验证。
- 默认（未设置 `APEX_SEATA_IT=true`）时四类集成测试跳过，此时该模块的通过不构成四模式端到端验证。
