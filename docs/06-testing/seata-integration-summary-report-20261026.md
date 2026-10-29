# Seata 集成测试总结报告（2026-10-26）

> 执行时间：2026-10-26 23:48 前后 ｜ 模块：`apex-seata-spring-boot-starter` ｜ 结论：**四模式真实环境全部通过**
>
> 详细证据与逐模式说明见 [Seata 真实四模式集成测试报告](seata-real-modes-test-report.md)。

## 一、结论摘要

| 项目 | 结果 |
| --- | --- |
| 一键命令 | `APEX_SEATA_IT=true mvnd -B -f apex-seata-spring-boot-starter/pom.xml verify` |
| 构建结果 | `BUILD SUCCESS`（exit=0） |
| 集成测试 | **8 项通过**，`failures=0 / errors=0 / skipped=0` |
| 单元测试 | **21 项通过**（`SeataModeTest` 13、`SeataSafetyTest` 8） |
| 质量门禁 | Spotless、PMD/p3c、JaCoCo 全部通过 |
| 编排方式 | 纯 Java JUnit 扩展 `SeataRealCluster`，无 `.sh`、无 Python/Ruby 载体 |
| 判定口径 | 只认本次运行生成的 Failsafe XML；模拟 TC/RM、条件跳过、旧报告、手工 Confirm/Cancel/补偿均不计通过 |

四个模式在同一命令中按 AT → TCC → SAGA → XA 顺序执行，每个模式独立 JVM，互不影响。

## 二、环境

### 2.1 构建与运行时

| 项 | 值 |
| --- | --- |
| 操作系统 | macOS 15.8.2（x86_64） |
| JDK | `21.0.13.0+9-tem`（由 `.sdkmanrc` 固定，未使用 `java/current`） |
| 构建工具 | Apache Maven Daemon `2.0.0-rc-3`（`MVND_CLIENT=jvm`，无 `mvn` 回退） |
| 容器运行时 | podman `5.8.9`（crun `5.8.7`，非 VM 替代） |

### 2.2 被测依赖

| 组件 | 版本 / 实例 | 用途 |
| --- | --- | --- |
| Seata Server（TC） | `apache/seata-server:2.2.0`，`STORE_MODE=file` | 每次运行由测试新建，单实例、唯一命名 |
| TC RPC 端口 | 容器内 `18091`，宿主端口由 podman 动态分配后经 `podman port` 读回 | 仅映射到 `127.0.0.1` |
| 事务组 | `apex_seata_it_group → default` | 经系统属性注入 file 注册/配置 |
| MySQL | `docker.io/library/mysql:9.0`（服务端 `9.0.1`），容器 `apex-infra-mysql`（healthy，`127.0.0.1:3306`） | 复用既有测试容器 |
| 测试库 | `apex_seata_it_<suffix>_a`、`apex_seata_it_<suffix>_b` | 每次运行新建的两个独立库 |
| 测试账号 | `seata_it_<suffix>`，随机 24 字节密码，仅授两库权限 + `XA_RECOVER_ADMIN` | 密码不落日志 |

### 2.3 环境编排（`SeataRealCluster`）

`BeforeAllCallback` + `ExtensionContext.Store.CloseableResource` 扩展，`@RegisterExtension static final` 注入：

1. 校验 MySQL 容器 healthy、`3306/tcp` 映射到 `127.0.0.1`，并确认 TC 镜像本地存在（`--pull=never`，不隐式拉取）。
2. 启动 TC 容器；**就绪判据为容器日志出现 `seata server started`**，之后再探测端口。
3. 建双库、建专用账号（含 `XA_RECOVER_ADMIN`）、写入 file 注册/配置与事务组属性；SAGA 额外开启 `client.rm.sagaBranchRegisterEnable`。
4. 成功时清理容器、双库与账号；失败时保留并输出资源名，避免丢失回滚证据。

Failsafe 配置 `reuseForks=false` + `forkCount=1`，隔离 Seata 静态 TM/RM 单例。

## 三、用例与结果

### 3.1 AT — `SeataAtIntegrationTest`：1/1

| 用例 | 结果 | 验证要点 |
| --- | --- | --- |
| `commitsAndRollsBackAcrossTwoHttpServicesAndDatabases` | 通过 | 真实 TC + 两独立库 + 两个同 JVM 本机 HTTP 分支；全局提交后两库余额 100→90；第二次扣款后抛错，等真实 AT 回滚恢复 90 |

### 3.2 TCC — `SeataTccIntegrationTest`：4/4

| 用例 | 结果 | 验证要点 |
| --- | --- | --- |
| `usesScannerProxyAndDistinctActionNames` | 通过 | 两个 `@LocalTCC` 动作均为 AOP 代理，动作名 `accountATccAction`/`accountBTccAction` 注册进 `TCCResourceManager`，target bean 不串用 |
| `commitsBothBranchesAndTcInvokesConfirm` | 通过 | 两分支 Try 后由 TC 回调 Confirm，每次恰好 1 次且 `fromRm=true`；余额扣减 |
| `callbackFailureRollsBackBothBranchesAndTcInvokesCancel` | 通过 | 回调抛错后 TC 对两分支 Cancel，余额不变 |
| `failedTryLeavesNoReservationAndTcCancelsEarlierBranch` | 通过 | 第二分支 Try 失败不产生预留，TC 取消先前分支 |

### 3.3 SAGA — `SeataSagaIntegrationTest`：1/1

| 用例 | 结果 | 验证要点 |
| --- | --- | --- |
| `realTcRegistersBothDatabasesAndRollsBackASuccessfulSagaInAnOuterTransaction` | 通过 | 依次断言：引擎装配（单 `DataSource` + `StateMachineEngine`，第二库不装配）；businessKey 幂等；本地失败补偿不伪装成 TC 回滚；外层失败走 TC 二阶段回滚；上下文清理 |

### 3.4 XA — `SeataXaIntegrationTest`：2/2

| 用例 | 结果 | 验证要点 |
| --- | --- | --- |
| `globalCommitPreparesThenClearsBothXaBranches` | 通过 | 二阶段前 `XA RECOVER` 恰好 2 条 prepared（formatId=9752、gtrid=XID、bqual 以 `-` 开头、branchId 不重复）；提交后两库各减 10 且无残留 |
| `globalRollbackPreparesThenClearsBothXaBranches` | 通过 | 同样先有 2 条 prepared；强制异常后余额恢复原值且无残留 |

`@BeforeEach` 基线断言：两库余额复位且 `XA RECOVER` 为空，避免上一轮残留污染结论。

### 3.5 单元测试与门禁

| 项 | 结果 |
| --- | --- |
| `SeataModeTest` | 13/0/0/0 |
| `SeataSafetyTest` | 8/0/0/0 |
| Spotless / PMD(p3c) / JaCoCo | 通过 |

### 3.6 结果汇总

| 模式 | tests | failures | errors | skipped | 耗时（同次 verify） |
| --- | --- | --- | --- | --- | --- |
| AT | 1 | 0 | 0 | 0 | 51.43s |
| TCC | 4 | 0 | 0 | 0 | 41.17s |
| SAGA | 1 | 0 | 0 | 0 | 42.60s |
| XA | 2 | 0 | 0 | 0 | 35.94s |
| **合计** | **8** | **0** | **0** | **0** | — |

证据文件：`apex-seata-spring-boot-starter/target/failsafe-reports/TEST-com.apex.scaffold.seata.Seata{At,Tcc,Saga,Xa}IntegrationTest.xml`，Surefire 报告在同目录上级 `surefire-reports/`。

## 四、本轮解决的问题

| 问题 | 表现 | 处置 |
| --- | --- | --- |
| TC 就绪误判 | podman 端口转发在容器启动瞬间即接受宿主连接，TCP 探测通过但 Seata 未初始化完成，TM/RM 注册报 `StacklessClosedChannelException` | 就绪判据改为轮询 `podman logs` 出现 `seata server started`，再做端口探测 |
| `XA RECOVER` 权限不足 | 非 `SUPER` 账号执行报 `XAE03 / 1401 XAER_RMERR` | 建号时授予 `XA_RECOVER_ADMIN`（MySQL 9.0 容器 `apex-infra-mysql` 实测复现） |
| `XA RECOVER` 作用域误判 | 曾断言「每库各一条」，实际一次返回 2 条导致 `Expected size: 1 but was: 2` | `XA RECOVER` 为服务器级视图，断言改为「恰好 2 条且 XID 归属本次全局事务」，两库归属由二阶段后余额变化证明 |
| 脚本载体不合规 | 原由 `.sh` + Ruby 编排真实 TC | 删除 `verify-seata.sh`、`verify-seata-at.sh`、`seata-verification-test.rb` 及 4 个 Java 替身工具类，改为 JUnit 扩展 |

## 五、待验证项（本报告不宣称已通过）

| 类别 | 未覆盖内容 |
| --- | --- |
| TC 形态 | 仅单机 TC + `file` 存储；TC 持久数据库（DB 存储）、多 TC 集群、TC 故障切换与全局事务恢复未验证 |
| 服务集成 | 未启动五个业务服务；AT/TCC 分支服务与 SAGA 引擎上下文均在测试 JVM 内，非完整应用链路 |
| 传输与安全 | 生产 HTTPS 传播过滤器、网关链路、TC 内部 TLS 与网络策略未覆盖 |
| 数据库矩阵 | 仅 MySQL（InnoDB）；PostgreSQL 的 XA/TCC/SAGA 路径未在本轮执行，MySQL/PostgreSQL Flyway 脚本的真实库执行未验证 |
| 业务语义 | 现有业务流程未自动加入全局事务；Outbox 与全局事务原子性仍按既有结论「未解决」 |
| 默认门禁 | 未设置 `APEX_SEATA_IT=true` 时四类集成测试跳过，此时模块通过不构成四模式端到端验证 |

## 六、复现方式

```bash
cd /Users/work/apex-scaffold
export MVND_CLIENT=jvm
source scripts/build/init-build-env.sh

# 首次准备（镜像不存在时；测试本身不会拉取）
podman pull apache/seata-server:2.2.0

# 前置：podman 中 MySQL 容器 healthy 且 3306 映射到 127.0.0.1
APEX_SEATA_IT=true mvnd -B -f apex-seata-spring-boot-starter/pom.xml verify
```

可选系统属性：`-Dapex.seata.it.image=<TC 镜像>`、`-Dapex.seata.it.mysql-container=<MySQL 容器名>`。测试会建表、建/删专用库与账号，不得指向生产环境或共享实例。
