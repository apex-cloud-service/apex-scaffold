# Python → Java/Shell 工具迁移总结报告

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

> 日期：2026-10-21 ｜ 范围：scripts/ 与 apex-system 资源目录下的 Python 工具清理与能力迁移
> 约束：历史验收证据 JSON 与历史迁移 SQL 不可变；构建固定 Oracle JDK 17.0.12 + mvnd 2.0.0-rc-3

## 1. 任务目标

删除仓库中的独立 Python 代码与脚本；对仍需保留的能力（部署、测试、采集、校验、数据库恢复）先迁移到 Java 或 Shell 并补齐测试，验证通过后再删除对应 Python；保留全部既有验收记录与证据哈希。

## 2. 迁移范围与处置结果

### 2.1 已迁移并删除（本批次）

| 原 Python 工具 | Java/Shell 替代 | 测试 |
| --- | --- | --- |
| `apex-system/src/main/resources/db/upgrade/mysql/token_v42_recover.py`（218 行，deployment.md 引用的活跃运维工具） | [TokenV42Recover.java](../../apex-tools-parent/apex-tools-deployment/src/main/java/com/apex/scaffold/tools/deployment/TokenV42Recover.java) + [token-v42-recover.sh](../../scripts/utils/token-v42-recover.sh)（apex-tools-deployment 模块 Shade JAR） | TokenV42RecoverTest 9 项 |
| `scripts/test/test_token_v42_recovery.py` | [TokenV42RecoverTest.java](../../apex-tools-parent/apex-tools-deployment/src/test/java/com/apex/scaffold/tools/deployment/TokenV42RecoverTest.java) | 同上（8 组断言逐条移植） |
| `scripts/test/test_consent_v40_upgrade.py` 资源断言部分 | [ConsentV40UpgradeTest.java](../../apex-tools-parent/apex-tools-deployment/src/test/java/com/apex/scaffold/tools/deployment/ConsentV40UpgradeTest.java) | 2 项 |
| `scripts/ci/collect_route_dispositions.py`、`collect_table_authority.py`、`review_table_authority_candidates.py` 及对应测试（前段批次） | 临时工具工程 `scripts/ci/java` 下 RouteDispositions / TableAuthority / TableAuthorityReview（DispositionToolsTest 11 项；该工程未入库，已移除，能力入口见 §4 第 3 条） | 见 §3 |
| `scripts/ci/collect_gateway_config.py`、`collect_p0_dependency_disposition.py` 及对应测试（前段批次） | 同一临时工具工程的 GatewayConfig / P0DependencyDisposition（5 + 3 项；已随该工程移除） | 5 + 3 项 |

### 2.2 冻结探针集群（归档后删除，共 17 个）

以下文件属于一次性 P0 动态验收探针，证据已冻结、无 CI pytest 运行器、且 `collect_table_facts.py` 依赖的 `collect_migration_tables` 已在更早批次删除导致整组不可运行，故不再迁移，按 `scripts/ci/source-archive/<原相对路径>.source` 模式字节级归档后删除：

- `scripts/ci/`：`collect_database_schema.py`、`collect_table_facts.py`、`migration_one_time_acceptance.py`、`mysql_v19_v42_negative_probe.py`、`pg_p0_dynamic_probe.py`、`auth_v45_dynamic_probe_20261018.py`
- `scripts/test/`：`test_collect_database_schema.py`、`test_collect_table_facts.py`、`test_pg_p0_dynamic_probe.py`、`test_auth_v45_dynamic_probe_20261018.py`、`test_mysql_v19_v42_negative_probe.py`、`test_migration_one_time_acceptance.py`、`verify_auth_postgresql_production.py`、`verify_consent_v40_upgrade.py`、`verify_token_v42_recovery.py`

归档完整性由 ArchiveIntegrityTest 递归校验（28 项归档，逐文件字节比对）。`token_v42_recover.py`（含 `target/classes` 构建产物副本）同样已归档后删除。全部 18 个归档经 `cmp` 逐一核验字节一致。

### 2.3 移植过程中的缺陷修复（TDD 捕获）

- `P0DependencyDisposition`：XML 中 version 元素与注释之间存在换行时 DOM TEXT_NODE 占位导致 origin 丢失——收集子节点时过滤 TEXT_NODE。
- `TokenV42Recover`：mysql 客户端命令提前退出时 Java 写 stdin 抛 `Stream closed`（Python subprocess 忽略 BrokenPipe）——补 try/catch 保持语义一致；修复后 stub 命令正确输出 `preflight rejected: only MySQL 8.0 is supported` 并以 exit 1 结束。
- RouteDispositions / TableAuthorityReview：字符串非法转义 `\v`、缺右括号、`classify` 返回类型错误三处编译错误修复。
- `TokenV42Recover`：容器重验冒烟捕获——`DEFAULT` 正则丢失闭合引号（`'[^']*` 而非 `'[^']*'`），导致字符串字面量默认值（如 `rotation_reason DEFAULT 'NORMAL_REFRESH'`）解析残留前引号，与 information_schema 实际值不符，工具自建表在 postflight 被自身拒绝——对齐 Python 原版正则并补 `rotation_reason` 契约断言（真实 MySQL 8.0 容器复现确认）。

## 3. 验证结果

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| canonical CI 工具全量门禁 | `mvnd -B -f scripts/ci/java/pom.xml verify`（该临时工程已移除） | 当时记录为 **51 tests, Failures: 0, Errors: 0, BUILD SUCCESS**（含 ArchiveIntegrityTest 28 归档、DispositionToolsTest 11、GatewayConfigTest 5、P0DependencyDispositionTest 3 等）；工程未入库，该结果不可复现 |
| 部署/运维模块全量门禁 | `mvnd -B -pl :apex-tools-deployment -am verify` | **38 tests, Failures: 0, Errors: 0, BUILD SUCCESS**（TokenV42RecoverTest 9、ConsentV40UpgradeTest 2、LocalDeploymentTest 8、InitBuildEnvTest 13、ConfigPreflightTest 6） |
| V42 恢复工具端到端冒烟 | `bash scripts/utils/token-v42-recover.sh ...` | 非法库名 exit 2、`--force=true` exit 2 且未创建 Client、stub 非 8.0 exit 1，与原 Python 行为一致 |
| V42 恢复工具真实 MySQL 8.0 容器重验 | `bash` 容器装置 + `token-v42-recover.sh` | **31/31 场景通过**（3 恢复正向 + 28 拒绝），MySQL 8.0.46、`--network none`、`/var/lib/mysql` tmpfs，详见 §3.1 |
| 文档链接 | `scripts/ci/check-doc-links.sh` | 663 份 Markdown、3047 条链接、**断链 0**（修复前 36 个指向已删 Python 的断链，已全部重定向至归档副本或 Java 替代实现） |
| 空白错误 | `git diff --check` | 通过 |
| CI workflow | grep `.github/workflows/` | 无 pytest / python3 运行器引用 |

`docs/05-operations/deployment.md` 的 V42 恢复工具引用与命令已同步更新为 `token-v42-recover.sh`。

### 3.1 V42 恢复工具真实容器重验（Java 实现）

用真实 MySQL 8.0.46 容器对 Java 版 [TokenV42Recover.java](../../apex-tools-parent/apex-tools-deployment/src/main/java/com/apex/scaffold/tools/deployment/TokenV42Recover.java) 重跑原 Python 验收的全部 31 个场景，逐条复刻 [verify_token_v42_recovery.py.source](../../scripts/ci/source-archive/scripts/test/verify_token_v42_recovery.py.source) 的断言。

- **容器隔离**：`podman create --network none --pull=never --tmpfs /var/lib/mysql:rw -e MYSQL_ALLOW_EMPTY_PASSWORD=yes localhost/mysql:8.0`；证据记录 `isolation=none`、tmpfs 挂载与 `SELECT VERSION()` 实际返回值。
- **调用路径**：经 `scripts/utils/token-v42-recover.sh` → deployment Shade JAR → `TokenV42Recover`，即生产运维入口，而非直接跑测试桩。
- **分批执行**：单容器串行跑完全部场景会因 podman exec 往返劣化超时，故分 5 批、每批独立容器；每个场景独立 `fixture()` 重建 `probe` 库，场景间无残留。分批只影响执行组织，不裁剪任何断言。

覆盖与结果：

| 分组 | 场景数 | 断言要点 | 结果 |
| --- | --- | --- | --- |
| 恢复正向 | 3 | `actual_v42_failure`（原 V42 首跑 1064、14 列 5 索引、重跑 1060）、`clean_predecessor`、`token_absent` | 通过 |
| 冲突拒绝 | 23 | 缺表/缺列依赖、列类型/默认值/生成列/字符集、触发器、CHECK/外键、错误引擎、索引（错列/前缀/唯一/降序/不可见/复合）、历史表与密钥表冲突、视图错误与表替视图 | 通过 |
| 后续破坏拒绝 | 5 | 恢复成功后篡改默认值、索引、额外列、视图标识符含空格、`SQL SECURITY INVOKER` | 通过 |

每个正向场景除 plan/apply 外还验证：dry-run 不改状态、业务数据（`access_token_value`、`token_value`）保留、重复执行 statements 为空且快照哈希不变、`active=0` 的旧密钥未被重新激活。每个拒绝场景验证错误前缀 `preflight rejected: ` 且前后快照哈希一致（拒绝前未改状态）。

证据文件（只新增，未覆盖任何历史证据）：

| 证据 | 场景数 | 说明 |
| --- | --- | --- |
| `token-v42-recovery-java-dfa7e28abb9e-evidence.json` | 3 | 恢复正向 |
| `token-v42-recovery-java-6abcb2bc7e32-evidence.json` | 12 | 冲突拒绝（第一批） |
| `token-v42-recovery-java-a8c428b04532-evidence.json` | 11 | 冲突拒绝（第二批） |
| `token-v42-recovery-java-b696496dc02d-evidence.json` | 5 | 后续破坏拒绝 |
| `token-v42-recovery-java-27ad777aaed3-evidence.json` | 1 | `token_absent` 单独复跑 |

另有两次早期运行留档，均如实保留：`token-v42-recovery-java-58a30b698b94-evidence.json`（装置初始化，0 场景，`status=passed`）与 `token-v42-recovery-java-f7821b03d900-evidence.json`（`token_absent` 在批次内 apply 报 `recovery stopped`，`status=failed`，容器清理未完成）。后者经同场景独立复跑与 `token-v42-recover.sh` 单跑均返回 0 且 31/31 齐全，判定为该批次 podman exec 往返劣化导致的装置级偶发失败，非工具缺陷；原始失败证据按「不覆盖历史证据」原则保留，不做修改。

## 4. 遗留项说明

1. **早期 10 个 Python 文件无字节归档**：本任务前段删除的 gateway-config / p0-disposition / route-dispositions / table-authority 五组工具+测试当时均为 Git 未跟踪文件，删除前未做 `.source` 归档，已不可逆；对应 Java 替代实现均曾在临时工具工程内有测试，该工程现已移除，相关能力入口见 §4 第 3 条。
2. **PostgreSQL 探针路径未重验**：V42 恢复的 31 场景已用真实 MySQL 8.0.46 容器对 Java 实现重跑完毕（见 §3.1）；但 `pg_p0_dynamic_probe.py`、`verify_auth_postgresql_production.py` 等 PostgreSQL 动态探针为冻结的一次性 P0 验收，本次未用 Java 探针在真实 PostgreSQL 上重跑，其真实库路径仍只有原 Python 证据。MySQL V42 场景与 PG 场景的库内语义不同（如 `information_schema` 字段、索引可见性表达），MySQL 通过不构成 PG 已验的证明。
3. **维护性 Shell 脚本曾一度迁移至 Java，现已回退为仓库内的 Bash/Python 实现**：`check-doc-links.sh`、`weekly-link-check.sh`、`generate-test-report.sh`、`start-doc-search.sh`、`fix-converter-javadoc.sh` 等入口曾改为调用临时工具工程 `scripts/ci/java` 的 `DocLinks`/`TestReport`/`ScriptSupport`（`verify-seata-at.sh` 已于 2026-10-26 删除，其编排职责改由 `apex-seata-spring-boot-starter` 的 JUnit 扩展 `SeataRealCluster` 承担，见 [Seata 四模式测试报告](seata-real-modes-test-report.md)）。该工程从未纳入版本管理、位于 reactor 之外且无 Spotless/PMD/JaCoCo 门禁，使已提交脚本在干净克隆下依赖未入库产物；因此本轮已按仓库规范移除该工程，上述脚本恢复为仓库内的 Python/Bash 实现，不再要求构建 JAR。相关脚本已通过 `bash -n` 与 `git diff --check`，文档链接入口实际执行通过。真实 Seata TC 双库场景未在本批次执行，不能以静态检查代替其验收。
4. **两个独立数据库迁移脚本已删除**：`scripts/test/test-mysql-migration.sh`、`scripts/test/test-postgresql-migration.sh` 与 JUnit 集成测试 `SystemDualDatabaseMigrationIntegrationTest`（Testcontainers 双库、逐版本 `validate()` 与 schema 断言）职责重复，且断言强度明显低于后者；已按“测试代码用 Java”的要求删除，双库迁移回归统一由 `mvnd -T4 -B clean verify` 的 Failsafe 阶段执行。`scripts/test/check-migration-integrity.sh` 的第 7 项检查已改为校验该 Java 测试存在。
5. **vendored 依赖未动**：`docs/_archive/ppt/架构图表新人培训/node_modules/katex/` 下 5 个上游构建用 `.py` 属已归档 PPT 的第三方依赖，与项目工具无关，保留原样。
6. **历史文档中的命令记录不改写**：P0 报告与实施方案中记录的 `python3 -B ...` 历史执行命令保留原文（证据属性），仅失效的 Markdown 链接按 §3 重定向。

### 4.1 Podman 环境恢复与门禁续验（2026-10-21）

已确认镜像仓库直连超时，而经本机现有代理可访问；为 Podman VM 的 systemd 服务设置临时代理后，成功拉取 `testcontainers/ryuk:0.8.1`、`mysql:8.0`、`postgres:15`。新建本轮专用容器 `apex-verify-mysql-20261021`、`apex-verify-pg-20261021`，仅绑定宿主机 `127.0.0.1`，分别通过 `mysqladmin ping`、`pg_isready` 就绪检查；凭据随机生成，未写入文档或日志。通过 `APEX_TEST_MYSQL_CONTAINER`、`APEX_TEST_POSTGRES_CONTAINER` 指定新容器，不改历史容器默认值及历史验收证据。恢复后的首次定向复验已执行：`FlywayMigrationStartupTest` 与 `AccountDeletionUserMapperIntegrationTest` 通过，Ryuk 正常启动，MySQL 完整迁移链通过；`SystemDualDatabaseMigrationIntegrationTest` 的 PostgreSQL 分支连接失败，根因异常为 `UnknownHostException: 127.0.0.1`。该轮共 9 项集成测试、Failures 0、Errors 1，日志为 `/tmp/apex-container-focused-20261021-113001.log`。PostgreSQL 容器就绪检查与宿主机端口连接检查通过；独立双库复验仍复现相同异常，日志为 `/tmp/apex-dualdb-rerun-20261021-114045.log`。

进一步在固定 JDK 下用 PostgreSQL 驱动 `PGStream` 建立最小连接：当前 JVM 的 `socksNonProxyHosts` 含 `127.0.0.0/8` 时复现 `UnknownHostException`，仅将本轮 JVM 绕过规则改为 `localhost|127.*|[::1]` 后立即连接成功。未修改业务代码、测试断言或系统永久代理。采用 `JDK_JAVA_OPTIONS='-DsocksNonProxyHosts=localhost|127.*|[::1]'` 后，通过 `initialize failsafe:integration-test failsafe:verify` 定向执行上述三个测试，**9 tests，Failures 0，Errors 0，Skipped 0，BUILD SUCCESS**；日志 `/tmp/apex-focused-proxyfix-20261021-115115.log`。该命令只验证目标集成测试，不等价于完整生命周期门禁。

随后在固定 Oracle JDK 17.0.12、mvnd 2.0.0-rc-3 环境下，保留上述本轮 JVM 绕过规则、Podman socket 与专用双库容器配置，执行全仓 `mvnd -T4 -B clean verify`，于 **2026-10-21 12:16:05（Asia/Shanghai）退出 0、BUILD SUCCESS**。Reactor 共 31 个项目（根项目及 30 个模块）全部 SUCCESS；测试汇总无非零 Failures/Errors，生命周期内 Spotless、PMD/p3c、ArchUnit 与适用模块的 JaCoCo 检查通过。日志为 `/tmp/apex-full-verify-recovered-20261021-115449.log`。

该轮日志仍记录 **13 项条件测试跳过**：`SeataAtIntegrationTest` 1 项、`MessageOutboxDialectTest` 4 项、`DomainEventOutboxDialectTest` 6 项、`OfficeWorkflowBooleanIntegrationTest` 1 项、`AuthMySqlTlsContractIntegrationTest` 1 项。默认全仓门禁通过不构成这些跳过场景或真实 Seata TC 四模式端到端场景通过的证据。专用双库容器及 Podman VM 临时代理保留供后续运行验收使用。

本轮再次执行文档链接入口与 `git diff --check`：**665 份 Markdown、3053 条链接、断链 0**，空白检查通过。

## 5. 历史失败与未执行事项

- 未创建 Git commit、未推送；工作区其他历史修改保持原样。
- 以下保留环境恢复前两轮全仓 `mvnd -T4 -B clean verify` 的**历史失败**；最新全仓门禁已通过，见 §4.1。首轮在 `:apex-system` 的容器集成测试失败，Testcontainers 报连接无响应。验证 Podman Unix socket `/_ping` 返回 OK 后，显式设置 Podman 连接、停止 mvnd daemon 重跑：Testcontainers 已识别 Podman socket，但拉取 `testcontainers/ryuk:0.8.1` 返回 HTTP 500 `connection refused`；双库集成测试引用的两个既有 Podman 容器不存在。重跑另有 `JobServiceStartupTest.startsWithoutExternalDependencies` 的 5 秒请求超时；随后以 `mvnd -T1 -B -pl :apex-job-service -am -Dtest=JobServiceStartupTest -Dsurefire.failIfNoSpecifiedTests=false test` 独立复跑，**3 tests，Failures 0，Errors 0，BUILD SUCCESS**，仅说明该超时未在独立复跑中复现。确认环境阻塞后停止该轮，未跳过容器测试或放宽超时；不能记为全仓门禁通过。两轮日志分别位于 `/tmp/:apex-full-verify-20261021-1040.log`、`/tmp/:apex-full-verify-podman-20261021.log`（临时诊断日志，不是冻结验收证据）。
- 九个运维脚本曾迁移至临时工具工程 `scripts/ci/java`，该工程记录过 `mvnd -B -f scripts/ci/java/pom.xml verify` 为 **69 tests，Failures 0，Errors 0，BUILD SUCCESS**；该工程从未纳入版本管理，已移除，结果不可复现。真实 Seata TC 双库及两个独立数据库迁移脚本的运行验收仍未执行。
