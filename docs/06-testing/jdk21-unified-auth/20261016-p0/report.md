# Java 21、统一认证与多租户升级 P0 阶段报告

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

日期：2026-10-16；最新整合：2026-10-19。V55八项、V15 184项、V16 24项、V59 16项及V43 24项已取得限定MySQL合成fixture运行证据；V40真实轮次因非空主键改造触发1062，45项不计分；当前有效去重覆盖为**411/586，剩175**，V19/V42本轮诊断新增0项，不等同于全局语义验收完成。V15旧失败记录保留；修正探针后46组138次分支、46个缺表检查和23表目录/数据重放通过，临时容器清理成功；两份成功重跑证据只计一次184项。V59真实12/12分支及完整文件回填、重放在明确兼容collation的合成夹具通过，首次1267失败原样保留，不证明生产collation安全；重复重跑不重复计分。探针33项、三组采集器63项合计96项通过；V43此前真实MySQL验证21组63次分支、21个缺表和46个逐列缺失no-op；最新隔离venv全集188项通过，0失败/0错误/0跳过，540.834s，限执行当时17模块；此前cryptography缺失的3错误已在该范围收口，历史失败保留，后续新增文件未包含。本轮仅持久化摘要并复核环境，未重跑全集。两条Outbox方言及消息dispatcher修复已记录，此前Java 17全仓clean verify日志明确BUILD SUCCESS和EXIT=0；本轮未修改Java或重跑Java门禁。表权威4 reviewed/67 partial/19 unknown仍为既有静态口径，生产时钟、过期PROCESSING恢复、真实RocketMQ、双库差异、历史升级链和生产配置/启动仍待收口。历史章节及旧hash保留，详情见末节。方案版本：v1；**P0整体仍未准出，Java 21未切换**。

> 2026-10-19 V51最新执行：真实MySQL 8.0.46两组守卫6/6分支与完整文件重放通过，容器及匿名卷清理成功，新增8项。当前独立复算覆盖为 **411/586，剩175**，取代上段历史覆盖数；V40原生失败和consent修复不混计。详见末节，P0仍未准出。

> 2026-10-19 本轮P0记录收口：实际调用 `verified_union()` 并独立复核得到 **489/586，剩97**。相对V51时点411新增78项为MySQL V37（15）、V44（32）及PostgreSQL V14/V15/V16（31）；V42部分提交诊断新增0项。前文411/403和历史检查结果均保留为原时点记录，当前覆盖以本条和末节为准。P0仍未准出、Java 21未切换；本轮Java未跑。

## 2026-10-19 consent V40 前置修复复验

本轮已交付 [MySQL 前置修复](../../../../apex-system/src/main/resources/db/upgrade/mysql/consent_before_v40.sql)、[PostgreSQL 前置修复](../../../../apex-system/src/main/resources/db/upgrade/postgresql/consent_before_v40.sql)及 [PostgreSQL 原 V40 事务包装器](../../../../apex-system/src/main/resources/db/upgrade/postgresql/run_v40_with_consent_backup.sql)，操作入口与限制见[部署指南](../../../05-operations/deployment.md)。资源位于 `db/upgrade`，不进入 Flyway 自动迁移扫描；本轮未修改历史 V40、基线或旧失败证据。

前置修复仅为缺失或空 ID 生成 `legacy:` 加 SHA-256，输入采用 UTF-8 长度与十六进制编码，区分分隔符拼接歧义。已有非空 ID、权限与已有时间保持不变，业务复合唯一性在切换主键前保护。MySQL 支持部分加列及无主键结构恢复；PostgreSQL 使用事务与排他表锁。PostgreSQL 原 V40 无存在性守卫，不能在补列后直接重跑；包装器在同一事务暂存 consent、执行原 V40、恢复数据，失败整体回滚。

| 实际验证 | 结果与范围 |
| --- | --- |
| 先 RED 后 GREEN | 首轮资源缺失 RED 保留；本轮新增原文件执行及 id 类型守卫测试先出现 1 failure / 2 errors（缺少守卫及执行函数），实现后与原探针单测合计 37 项通过 |
| 真实隔离双库 | MySQL 8.0.46、PostgreSQL 17.11；每库覆盖旧多行、部分加列、无主键、已有 ID 四种结构，共 8 场景通过 |
| 数据与约束 | 同 client 不同 principal、不同 client 同 principal、中文、`a/b:c` 与 `a:b/c`；权限数据、已有 ID/时间、重入快照不变，重复业务键和 ID 写入被拒 |
| 原始 V40 | 原 MySQL 1062 与 PostgreSQL 非空列失败再次真实复现；前置后 MySQL 完整原 V40 及重放通过；PostgreSQL 包装执行完整原 V40 及包装器重放通过 |
| PostgreSQL 原文件包含 | `podman cp` 按 `db/upgrade` / `db/migration` 相对结构复制三份原文件并逐一校验容器内 SHA-256；`psql -f` 实际执行原包装器 `\ir` 及重放，数据快照保持一致 |
| PostgreSQL 故障恢复 | 提交前注入除零，数据快照与原字段均回滚保留；该负例单独展开 V40 全文后经 stdin 执行，只证明事务回滚，不作为原文件包含证据 |
| PostgreSQL 已有 id 类型 | `VARCHAR(50)`、`VARCHAR(101)`、`TEXT`、`BIGINT` 四类真实负例均在回填前明确拒绝；数据、原主键与无 created_at 状态保持，事务回滚通过 |
| Java 定向验证 | 加载 Oracle 17.0.12 / mvnd 2.0.0-rc-3 后执行 `mvnd -T4 -B -pl apex-system -am -Dtest=OAuth2AuthorizationConsentRepositoryImplTest -Dsurefire.failIfNoSpecifiedTests=false test`，相关 reactor 成功，仓储 5 项测试无失败、错误或跳过；未运行全仓 verify |
| 隔离清理 | 唯一容器、`network none`、tmpfs；finally 删除容器及匿名卷，证据中两库清理均为 true |

本轮原文件包含独立[新证据](consent-v40-repair-cd38593bac32-evidence.json) SHA-256：`71fc8aa09dcda8efd968db7f20817b0b40a159c6b71d1105830b1460cf26acff`，状态为 `passed_with_postgresql_upgrade_limitation`（限制是 Flyway 历史与原 V40 非幂等，不再是未测相对包含）。实际执行：`python3 -B scripts/test/verify_consent_v40_upgrade.py`；包装器命令：`podman exec apex-consent-v40-d3e2edf3106e psql -X -U postgres -d postgres -v ON_ERROR_STOP=1 -At -f /tmp/consent-upgrade/upgrade/postgresql/run_v40_with_consent_backup.sql`。Python 定向命令为 `python3 -B -m unittest scripts/test/test_consent_v40_upgrade.py scripts/test/test_auth_v45_dynamic_probe_20261018.py`，37 项通过；上表 Java 定向命令本轮再次执行成功，5 项无失败/错误/跳过。

此前独立[修复证据](consent-v40-repair-e147f94739dc-evidence.json) SHA-256：`6dfe2ccce91a0d68d79a8f301c28c72fa8d40fc418dd670ec57dcbe121e865fc`。原[失败证据](dynamic-system-v40-20261019-client-evidence.json)保持原样，SHA-256：`e4d4a36271dee34ac07d8e9d2f9c8279cfc70051704dfb03adab2f7392a6a57d`。此前两份独立修复运行证据也保留，不覆盖。

限制：仅验证审核过的合成历史结构，未执行生产升级、Flyway 启动或完整历史升级链。此前 e147f94739dc 轮次展开了 `\ir`；本轮已补齐容器内原文件相对包含与包装器重放验证，只有除零注入负例仍单独展开全文。包装器重放按原字段是否仍存在选择跳过，不代表原 PostgreSQL V40 自身幂等；扩展列、外键、触发器、非默认主键名及其他表的半迁移状态须 DBA 审核。运行前停写并备份，Flyway failed history 与手工执行历史须人工协调；不自动 repair、修改 checksum 或标记迁移成功。

上述结果只证明显式前置路径，不能算作原 V40 原生缺失分支闭合；原 45 条不计分，**verified_union 仍为 403/586，剩 183，P0 未准出**。本轮 `git diff --check` 通过；报告更新后文档链接扫描覆盖 663 个 Markdown、2920 个链接，断链为 0。

## 授权与范围

用户已明确批准按[主方案](../../../02-architecture/jdk21-unified-auth-multitenancy-implementation-plan.md)执行，并要求同步交付配套资产。当前已完成构建检查修复、资产采集工具及测试，并于 2026-10-17 分别在隔离 MySQL、PostgreSQL 新空库执行五业务域原始迁移、采集真实结构与历史（历史观测：两库均四域成功；system 分别在 MySQL V21、PostgreSQL V1 失败）。后续 system 29 份历史 SQL 修复后的双库新空库定向回归已通过，最终 12 项测试无失败、错误或跳过；详见本报告末尾补充及[修复证据](system-migration-repair-evidence.json)。旧失败 schema 快照保持原样。未切换 Java 21、实施统一认证业务改造、存量或生产数据库升级、部署、提交或推送。后续已完成 system 差异静态归因，并执行 SQL 修改后的完整 clean verify：自然退出 1、BUILD FAILURE；详见末尾补充及[独立门禁证据](post-repair-verify-evidence.json)。该失败轮次保留为历史；随后三项修复及最终完整门禁结果见末尾补充。V62 的隔离测试负例在恢复测试值后执行过 repair，不代表生产或存量库 repair 授权；本轮未对现有业务库执行 repair。P0 仍未准出，D01–D07 独立决策仍须遵守阶段前置条件。

基准 HEAD 为 `0c506a7ee0f94a07b359edda8ebbab4aac60dab3`。工作区存在大量已有改动，不能以 HEAD 单独重现现场；[基线清单](baseline.json)记录采集时的 Git HEAD、变更路径及选定资产的字节数、SHA-256。该清单不是整个工作区的完整快照，报告及后续编辑不自动纳入其采集时间点。

## 已交付变更

| 资产 | 行为及限制 |
| --- | --- |
| [构建初始化脚本](../../../../scripts/build/init-build-env.sh) | SDKMAN 初始化、项目环境加载、Java/mvnd 命令失败均非零退出；固定 Java 路径与版本，严格解析完整 mvnd 版本 token；全部通过后才输出成功 |
| [构建检查测试](../../../../apex-tools-deployment/src/test/java/com/apex/scaffold/tools/deployment/InitBuildEnvTest.java) | 临时 HOME 和模拟命令，覆盖直接执行、source、条件 source；包含真实格式 banner 与 JLine 警告，不是 Java 集成验证 |
| [采集工具](../../../../scripts/ci/source-archive/scripts/ci/collect_upgrade_baseline.py.source) | stdlib，从根 POM 模块扫描 main/test Java、所属域双库版本迁移及显式配置白名单；排除构建目录与符号链接，只记录元数据，不输出配置正文 |
| [采集工具测试](../../../../scripts/ci/source-archive/scripts/test/test_collect_upgrade_baseline.py.source) | 构建目录、符号链接、零字节、hash 稳定、双库版本差异保留、配置正文不泄露、Git 重命名与输出失败反例 |
| [方案](../../../02-architecture/jdk21-unified-auth-multitenancy-implementation-plan.md) | 更新为已批准执行、P0 未准出，保留评审阶段历史口径 |

未为尚未开始的 Java 21/业务改造生成虚构配置、迁移或空测试报告；现有生产配置及构建规则保持 Java 17。

## 实际执行证据

### 当前轮次

| 命令或步骤 | 结果 | 解释 |
| --- | --- | --- |
| 新增采集测试，工具尚不存在时运行 | 退出 1，6 项失败 | RED；缺失工具导致失败 |
| `python3 -B -m unittest scripts/test/test_collect_upgrade_baseline.py scripts/test/test_init_build_env.py` | 退出 0，19 项通过，12.725s | 主代理复验；不等于 Java 回归 |
| `zsh -n scripts/build/init-build-env.sh` | 退出 0 | shell 语法 |
| `git diff --check` | 退出 0 | 检查 tracked diff，不覆盖全部未跟踪文件 |
| `bash scripts/ci/check-doc-links.sh` | 退出 0；661 个 Markdown、2602 个链接、断链 0 | 报告与索引写入后的当前轮次检查 |
| `python3 -B scripts/ci/collect_upgrade_baseline.py --output docs/06-testing/jdk21-unified-auth/20261016-p0/baseline.json` | 退出 0 | 当前工作区文件清单，未做 AST 或数据库运行验证 |

### 前轮已观察结果与无法补证部分

前轮曾观察到 Oracle Java 17.0.12、mvnd 2.0.0-rc-3、内嵌 Maven 4.0.0-rc-3。初次 daemon 连接超时；加载固定 SDKMAN 环境并 `mvnd --stop` 后，`mvnd --version` 成功。启动过 `source scripts/build/init-build-env.sh && mvnd -T4 -B clean verify`，但当前原始执行日志与任务最终状态已不可取得。

现存 auth-service/system Failsafe XML 有通过结果，system-service summary 为零测试；这些文件不能可靠归属于指定轮次，不能合计为本轮通过数，也不能据此判定整个 reactor 成败。**该次完整 verify 的最终退出码、BUILD 结论、总计数与结束时间记为未知，不再称仍在运行，也不宣称完整门禁通过。**

前轮文档检查曾输出 660 个 Markdown、2592 个链接、断链 0；不继承为本报告新增后的检查结果。

## 资产清单与口径修正

| 类别 | 当前采集数量 |
| --- | ---: |
| 根 POM reactor 模块 | 30 |
| main Java | 1646 |
| test Java | 557 |
| Java 合计 | 2203 |
| 零字节 Java | 0 |
| 显式白名单配置 | 80 |
| 双库版本迁移 | 170 |

前次内容检索计数 1813 未构成完整冻结清单，现以逐文件采集的 2203 为本次快照口径；差异未经逐路径对账，不能断言全部由忽略规则或代码新增造成。文件数不是 AST 声明数、手写认定、Javadoc 覆盖率或测试用例数。

| 业务域 | MySQL | PostgreSQL | 静态版本范围 |
| --- | ---: | ---: | --- |
| system | 61 | 61 | V1–V61 |
| auth | 14 | 14 | V44–V57 |
| workflow | 2 | 2 | V1–V2 |
| file | 5 | 5 | V1–V5 |
| office | 3 | 3 | V1–V3 |

版本集合匹配不代表 SQL 语义一致。采集器保留每条版本与 hash，不执行 SQL 或查询数据库 Flyway 历史。配置白名单尚不覆盖所有部署入口、脚本和变量注入；Git 仅记录路径，没有记录内容差异及暂存状态，不能作为完整恢复包。

## 后续执行补充（2026-10-16）

- 采集器已按 TDD 扩展部署入口白名单，新增 workflows 两种 YAML 扩展名、Compose、构建/部署脚本及 systemd 模板、环境示例和 Nacos 配置；不采集真实 `.env` 正文。新[扩展清单](baseline-expanded.json)配置为 **100 项**，较原清单新增 20、删除 0；原清单保留。新增按业务域和 scope 的双库版本差异字段，五域 main 集合差异为空，仍不证明 SQL 等价。
- 新增采集反例后 RED 为 8 项中 2 项失败、退出 1；实现后采集测试 8 项通过。主代理复验 `python3 -B -m unittest scripts/test/test_collect_upgrade_baseline.py scripts/test/test_init_build_env.py`：**21 项通过，14.233s，退出 0**；zsh 语法和 `git diff --check` 均退出 0。
- [接口与表资产调查](api-table-assessment.md)已交付，包含五域真实入口及代表性租户、安全、迁移证据。它不是完整 AST 路由清单或实际数据库 schema，不替代运行安全负例。
- 新启动固定 Java 17/mvnd 环境的 `clean verify`，本轮命令标识、版本、已观测模块和插件汇总见[构建证据快照](verify-evidence.json)。报告补充时直接查询仍为 Running，最终退出码和 BUILD 结论未知；不得把已完成插件计数当全仓验收。日志出现 example 集成场景 MySQL 连接拒绝，不能在终态前据此认定 reactor 失败。
- Oracle 21 发行版路线已确认。官方检索命中 [21.0.12 发布说明](https://www.oracle.com/java/technologies/javase/21-0-12-relnotes.html)、[下载页](https://www.oracle.com/java/technologies/downloads/)及[许可 FAQ](https://www.oracle.com/java/technologies/javase/jdk-faqs.html)。发布说明提示 2026 年 9 月之后的 Oracle 21 更新切换至 OTN，下载检索摘要又出现 21.0.12.1；**不能沿用旧候选即认定是当前最新、固定安装包或有生产许可**。当前仅为检索线索，未完成具体发行物许可与 checksum 核对，未下载安装或切换。组织实际生产使用授权需由许可责任人确认，不以本报告代替法律判断。

## 门禁收尾补充（2026-10-17）

- 完整回归命令 `job-dff35a402a084126bad0ba6d0c986b6b` 已由主代理通过 StopCommand 主动停止，工具确认停止。example 集成测试使用真实 MySQL，连接被拒绝后连接池持续重试，日志观察已超过 237 万行。没有取得 Maven 自然退出码或最终 BUILD 结论，因此[回归证据](verify-evidence.json)记为 `interrupted`，不判为通过，也不伪造最终 BUILD FAILURE；此前 Running 描述保留为历史观察。
- 重跑前需提供可连接、允许 Flyway 建表迁移及测试数据读写的隔离 MySQL 测试库。配置变量为 `DB_MASTER_URL`、`DB_MASTER_USERNAME`、`DB_MASTER_PASSWORD`，test profile 主从共用。当前测试没有数据库可用性自动跳过逻辑；不得以跳过该测试替代完整门禁。报告不保存连接凭据。
- 有效依赖命令 `source scripts/build/init-build-env.sh && mvnd -T4 -B -l /tmp/apex-p0-dependency-tree.log dependency:tree -DoutputType=text` 已退出 **0**，日志为 **BUILD SUCCESS，31 个 reactor 项目全部 SUCCESS**。精简结果与原日志行号见[依赖解析证据](dependency-evidence.json)。执行环境仍为 Oracle Java 17.0.12、mvnd 2.0.0-rc-3。
- 实际依赖存在 JUnit 混合版本：28 个模块中的 `junit-jupiter` 聚合包为 **5.12.2**，API、Engine、Params 为 **5.10.2**，Platform 为 **1.10.2**。不能仅按声明属性认定最终版本；该差异纳入后续兼容性核验，本轮未改依赖。依赖树成功不证明测试、构建插件、annotation processor 或 Java 21 兼容性通过。

本次证据更新后执行 `bash scripts/ci/check-doc-links.sh`：662 个 Markdown、2674 个链接、断链 0，退出 0；`git diff --check` 及两份门禁证据的 JSON 语法检查均退出 0。上述检查不替代 Java 回归。

## 本轮 AST 采集与回归重启（2026-10-17）

- 已读取本轮真实采集产物 [declarations.json](declarations.json) 摘要：**2203 个 Java 文件、26601 条声明**，模式为 `javac-parse-only`，JDK 为 `17.0.12-oracle`；`semantic_validation=false`、`javadoc_coverage_acceptance=false`。声明分类为 CLASS 2565、INTERFACE 377、ENUM 102、ANNOTATION_TYPE 30、RECORD 127、CONSTRUCTOR 586、METHOD 13007、FIELD 8678、ENUM_CONSTANT 537、RECORD_COMPONENT 592。文件数与声明数分别计量，不能据此认定 API 完整或 Javadoc 达标。
- 已核对 [Python 采集入口](../../../../scripts/ci/source-archive/scripts/ci/collect_java_declarations.py.source)、当时临时工具工程中的 javac Tree/DocTrees 实现（`scripts/ci/java/src/main/java/JavaDeclarationCollector.java`，该工程未入库、现已移除）及[新增测试](../../../../scripts/ci/source-archive/scripts/test/test_collect_java_declarations.py.source)。工具只执行 parse，不调用 `analyze()`，不解析 classpath、不运行 annotation processor；记录源码类型拼写的签名、位置、可见性、Javadoc 文本与块标签名称。标签完整性、引用与继承、文档语义仍未验收；模块 exports 与语义可访问性未解析。只枚举显式顶层/成员声明及 package-info，局部类、匿名类和方法体局部声明不在采集范围；Lombok/processor 生成成员、默认构造器、record 隐式成员及组件与 `@param` 的文档关联仍未闭环。
- 测试结果来源为**本轮测试子代理报告**：新增声明采集测试 **4 项通过**，既有[文件采集测试](../../../../scripts/ci/source-archive/scripts/test/test_collect_upgrade_baseline.py.source) **8 项通过**。本次报告编辑未重跑这些测试；该转述不等于主代理复验或 Java 完整回归通过。新增测试覆盖声明/文档/位置/可见性、注释与字符串伪声明排除、record/enum/interface/package-info、解析失败不替换旧输出及稳定性等场景。
- 主代理随后加载固定构建环境，执行 `python3 -B -m unittest scripts/test/test_collect_java_declarations.py scripts/test/test_collect_upgrade_baseline.py scripts/test/test_init_build_env.py`：**25 项通过，145.798s，退出 0**。此结果补充上述子代理测试记录，不代表完整 Java 门禁。
- 环境事实来源为本轮主代理操作记录：默认 Docker OrbStack 不可达，但 **Podman VM 运行中**。主代理新建独立临时 MySQL 容器 `apex-p0-gate-mysql-20261017`，映射 `127.0.0.1:13317`；随机密码未输出。已通过 `SELECT VERSION()` 与 schema 查询确认 **MySQL 8.0.46**、数据库 **apex_example_test**。旧容器未启动或修改；数据库连通与版本/schema 确认不代表迁移和测试验收通过。
- 主代理已在当前 shell `source scripts/build/init-build-env.sh` 后重新启动 `mvnd -T4 -B clean verify`；新任务标识为 `job-8c519b9d54df41bb9e61de7c3b696cef`，日志路径为 `/tmp/apex-p0-20261017-verify.log`，`DB_MASTER_*` 仅向本次构建进程注入，报告不保存凭据。**本条记录时任务运行中，最终退出码、reactor BUILD 结论与最终测试汇总未知，不可称通过。**旧任务 `job-dff35a402a084126bad0ba6d0c986b6b` 的主动中断及 [verify-evidence.json](verify-evidence.json) 仍为独立历史证据，不与新任务合并或覆盖。

## 完整回归最终结果（2026-10-17）

本轮 `job-8c519b9d54df41bb9e61de7c3b696cef` 已自然结束，退出码 **0**。日志最终 Reactor Summary 中 **31 个项目全部 SUCCESS**，最终结论为 **BUILD SUCCESS**，结束时间为 `2026-10-17T07:18:25+08:00`。以上述终态替代前文关于新轮次运行中的历史观察。

[回归证据](verify-evidence.json)顶层现记录本轮结果、逐模块终态、插件级测试汇总与日志行号；旧中断轮次完整保留在 `previous_attempt`，未改写其结论。

| 验证 | 实际结果与范围 |
| --- | --- |
| 固定环境完整 `clean verify` | Oracle Java 17.0.12、mvnd 2.0.0-rc-3；31 个 reactor 项目全部成功 |
| Surefire | 4753 项，失败 0、错误 0、跳过 0 |
| Failsafe | 297 项，失败 0、错误 0、跳过 1 |
| 测试合计 | 5050 项，失败 0、错误 0、跳过 1；仅累计 36 条插件级汇总，不重复累计类明细 |
| Spotless | 31 个模块出现检查目标；28 个有明确文件清洁汇总，需修改文件数均为 0 |
| PMD/p3c | 已记录目标执行范围，verify 成功；日志未给出统一的逐模块源码扫描数量 |
| JaCoCo | 27 个模块明确通过覆盖率检查，4 个模块因缺少执行数据跳过；不将跳过认定为覆盖率达标 |
| 架构、边界与 Flyway | 证据列出 12 个通过测试类及日志行号；检查范围仍受各测试实现限制 |

Seata 真实 TC 集成测试跳过 1 项，不构成真实 TC 四模式端到端通过。日志未明确具体跳过原因，源码环境门控与日志观察在证据中分别记录。本轮使用隔离 MySQL 的成功结果不证明生产服务启动、全业务双库迁移、上线或回退演练通过，也不证明 Oracle Java 21 兼容性通过。

## P0 API 路由与迁移表清单（2026-10-17）

在声明清单基础上补充两份静态资产清单，均为 parse-only 或词法级采集，不是完整 API 契约或真实数据库 schema。

### 路由清单

[路由清单](routes.json) 由当时临时工具工程中的 `JavaRouteCollector.java`（`scripts/ci/java`，未入库、现已移除）与 [collect_java_routes.py](../../../../scripts/ci/source-archive/scripts/ci/collect_java_routes.py.source) 采集，模式 `javac-parse-only`、JDK `17.0.12-oracle`、`semantic_validation=false`。

| 指标 | 数量 |
| --- | ---: |
| 扫描 main Java 文件 | 1646 |
| 命中候选文件 | 56 |
| 已解析路由 | 250 |
| 未解析候选 | 148 |

未解析项按原因分类：`receiver_not_proven_webflux` 59、`unknown_enclosing_prefix` 40、`dynamic_path` 26、`annotation_mapping_unsupported` 7、`gateway_dsl_unsupported` 7、`dynamic_or_unsupported_nest` 4、`builder_helper_call_unsupported` 3、`predicate_unsupported` 1、`url_handler_mapping_unsupported` 1。

限制：路由为源码注册候选，非运行期注册、可达性、权限或 OpenAPI 覆盖证明；仅解析字面量路径、`route/andRoute` 断言与内联 `path/nest` 回调；常量、计算路径、构建器别名/重赋值、跨方法前缀未解析；网关 YAML/配置、运行期/生成路由与 WebSocket URL 映射不在范围内。未解析项不记为已解析路由。

### 迁移表事件清单

[迁移表清单](tables.json) 由 [collect_migration_tables.py](../../../../scripts/ci/source-archive/scripts/ci/collect_migration_tables.py.source) 采集，`kind=static_migration_table_events`，仅做词法级静态事件识别，不执行 SQL。

| 指标 | 数量 |
| --- | ---: |
| 迁移脚本 | 170 |
| 静态表事件合计 | 767 |
| `create_table` | 258 |
| `alter_table` | 125 |
| `create_index` | 382 |
| `drop_table` | 2 |
| 去重后规范化表名 | 115 |
| 逐版本双库表事件比较记录 | 85（system 61、auth 14、file 5、office 3、workflow 2） |
| 至少一侧事件差集非空的记录 | 51（system 38、auth 9、file 1、office 2、workflow 1），涉及 5 域 |
| 两侧事件差集均为空的记录 | 34 |

限制：仅为历史脚本静态事件，不是当前真实表或最终 schema；保留版本与两库事件差异，不证明 MySQL/PostgreSQL 语义等价或迁移可执行；条件执行注释、动态 SQL、过程体及无法静态确定的索引显式记为未解析，不展开执行。版本集合匹配、迁移版本差异为空（五域 main 集合 `mysql_only`/`postgresql_only` 均为空）不代表 SQL 语义一致。

### 复验

主代理在本轮以固定 Java 17 环境执行：

```bash
python3 -B -m unittest \
  scripts/test/test_collect_java_routes.py \
  scripts/test/test_collect_migration_tables.py \
  scripts/test/test_collect_upgrade_baseline.py \
  scripts/test/test_init_build_env.py
```

结果：**36 项通过，62.674s，退出 0**。此为采集脚本单元验证，不替代 Java 回归，也不改变 P0 未准出结论。

## 2026-10-17 五域 MySQL 空库实测

[真实 schema/history 证据](mysql-schema-evidence.json)采集时间为 **09:15:53–09:16:14 +08:00**，执行命令 `job-8feba02f79e946b7b45dc27509632f0f` 退出 0（代表完成五域采集，**不代表五域迁移全部成功**）。使用既有隔离容器 `apex-p0-gate-mysql-20261017`、`127.0.0.1:13317`，SQL 实查 MySQL **8.0.46**；Oracle Java **17.0.12+8-LTS-286**、mvnd **2.0.0-rc-3**、Flyway **10.15.0**、Connector/J **8.4.0**。

每域新建唯一数据库，统一前缀 `apex_p0_20261017_091553_7a3b9f07_`，后缀为域名。数据库均保留，不修改旧测试库。使用原始 filesystem SQL，`baselineOnMigrate=false`、`cleanDisabled=true`、`validateMigrationNaming=true`，无 repair、失败重试或历史 SQL 修改。凭据由 Java 子进程从容器环境读取到内存管道，不进入命令参数、证据或临时配置文件。只查询 `information_schema` 与 Flyway history，不保存业务种子行。

| 域 | 源脚本 | 实际 history | 结果 | 表数（含 history） | 列数 | 索引列元数据行数 |
| --- | --- | --- | --- | ---: | ---: | ---: |
| system | 61，V1–V61 | V1–V20 成功；V21 失败 | **失败**，V22–V61 未执行 | 43 | 566 | 224 |
| auth | 14，V44–V57 | 14 条成功 | 成功 | 16 | 213 | 85 |
| workflow | 2，V1–V2 | 2 条成功 | 成功 | 6 | 73 | 39 |
| file | 5，V1–V5 | 5 条成功 | 成功 | 6 | 90 | 31 |
| office | 3，V1–V3 | 3 条成功 | 成功 | 16 | 250 | 105 |

system 的真实失败为 `V21__enhance_workflow_tables.sql:84`：SQLState `42S02`、MySQL 错误 `1146`，`wf_definition` 不存在。失败前的 MySQL DDL 已部分提交，证据反映失败后的结构，不能当作 V61 最终 schema。现有 `AbstractMySqlContainerBase` 会容忍该已知 V21 缺陷，`FlywayMigrationStartupTest` 只要求至少 20 条成功，因此此前完整 Java 17 verify 成功不能证明 system 全迁移链成功。

临时 harness、依赖 POM、classpath 和核验脚本位于 `/tmp/apex-p0-mysql-evidence-J52CXYXF/`，没有新增生产代码。JSON 保存 85 份 SQL 的 SHA-256、运行 JAR 与 harness 的 SHA-256、各域 history version/checksum/success、表、列、索引及约束元数据、失败原因与运行时间。索引行数是组合索引的逐列行数，不是独立索引数量。

实际执行的核心命令（均在仓库根目录）：

```bash
source scripts/build/init-build-env.sh
java -version
mvnd --version
podman version --format '{{.Client.Version}}'
mvnd -B -f /tmp/apex-p0-mysql-evidence-J52CXYXF/pom.xml \
  org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath \
  -Dmdep.outputFile=/tmp/apex-p0-mysql-evidence-J52CXYXF/classpath.txt
# 再次在当前 shell 加载环境后编译并执行临时 Java harness
source scripts/build/init-build-env.sh
python3 /tmp/apex-p0-mysql-evidence-J52CXYXF/run.py
python3 /tmp/apex-p0-mysql-evidence-J52CXYXF/check.py
python3 /tmp/apex-p0-mysql-evidence-J52CXYXF/check_artifacts.py
git diff --check
```

`run.py` 调用固定 `JAVA_HOME/bin/javac -proc:none` 和 `JAVA_HOME/bin/java`，classpath 由上述依赖解析产生。核验脚本已通过 JSON 解析、五个库名唯一性、85 份源文件 hash、history 来源及成功/失败数量、列所属表引用检查。此次未重跑 Java 完整回归、未启动五业务服务，未验证 PostgreSQL、存量升级、生产升级或回退；system 失败和双库对账仍阻塞 P0 准出。

## 2026-10-17 五域 PostgreSQL 空库实测与双库对照

[PostgreSQL 真实 schema/history 证据](postgresql-schema-evidence.json)采集时间 **09:22:57–09:23:13 +08:00**，命令 `job-171c1a22e43442849e9c7d48659a171b` 退出 **0**，含义为五域证据采集完成，不是迁移全部成功。复用既有 MySQL 临时 harness 结构，仅新增临时 PostgreSQL Java/Python/POM，不重复 MySQL 迁移。

使用本地已有 `localhost/postgres:17-alpine` 镜像（ID `79bd7c99e923138f136f8009d6bffa66e21e9d4fda5c0c561b00fc9c90cfe537`，`--pull=never`），新隔离容器 **`apex-p0-pg-b1ddd1785ccf`**，动态映射 **`127.0.0.1:41187`**。SQL 实查 **PostgreSQL 17.11**，UTF8、`en_US.utf8`、时区 `Asia/Shanghai`；Oracle Java **17.0.12+8-LTS-286**、mvnd **2.0.0-rc-3**、Flyway **10.15.0**、PostgreSQL JDBC **42.7.4**、Podman **5.8.5**。每域以 `template0` 新建独立数据库，前缀 **`apex_p0pg_20261017_092257_ce28d8a0_`**，后缀为域名。

随机密码只保留于 Python/Java 内存和 stdin 管道，不写临时配置、命令参数、容器环境、证据或日志；启动期间 host 首条规则拒绝连接，设置随机密码后切换为 SCRAM，local socket 仅用于容器内管理。数据库数据目录使用 tmpfs（服务端保存 SCRAM verifier 而非明文密码）；容器与数据库暂时保留，**停止/移除容器会丢失数据，不能作为持久备份**。实际扫描证据及容器日志未发现随机密码。

| 域 | 源脚本 | 实际 history | 结果 | 表数（含 history） | 列数 | 独立索引数 | 约束数 |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: |
| system | 61，V1–V61 | history 表存在，0 行 | **V1 失败并回滚**，V2–V61 未执行 | 1 | 10 | 2 | 1 |
| auth | 14，V44–V57 | 14 条成功 | 成功 | 16 | 213 | 62 | 29 |
| workflow | 2，V1–V2 | 2 条成功 | 成功 | 6 | 73 | 29 | 8 |
| file | 5，V1–V5 | 5 条成功 | 成功 | 6 | 90 | 27 | 6 |
| office | 3，V1–V3 | 3 条成功 | 成功 | 16 | 250 | 63 | 16 |

system 的失败为 `V1__init_schema.sql`，SQLState **`42P07`**、vendor code **0**：`relation "uk_user_id" already exists`。Flyway 报告失败语句起始行为 **416**；源码第 **216、427、515** 行分别为不同表重复命名 `CONSTRAINT uk_user_id UNIQUE (user_id)`。PostgreSQL 唯一约束创建同名 schema 级索引，第二处冲突；MySQL 索引名的表级作用域不触发同一错误。PostgreSQL 本次 V1 事务已回滚，history 无失败行；**空 history 不代表成功或未尝试**，须结合异常链。未 repair、跳过版本、重试业务迁移或修改历史 SQL。

### 与既有 MySQL 实测的差异和边界

- MySQL system 已完成 V1–V20，在 V21 缺少 `wf_definition` 失败，遗留部分已提交 DDL 与 `success=false` 历史行；PostgreSQL system 在 V1 即失败，只有空 history 表。两者停点不同，不能把 43 对 1 的表数差异解释为最终 schema 缺表，也不能称已完成 system 双库对账。
- auth/workflow/file/office 两库均完成各自源迁移链；逐域查询表名集合相同，列数量分别同为 213/73/90/250。这只验证集合与计数，不证明列类型、默认值、索引、约束或业务语义等价。PostgreSQL JSON 的索引是独立索引定义，MySQL JSON 为索引逐列行，**不得直接比较 62 对 85 等计数**。两库原始结构已保留，完整归一化语义对账仍未完成。
- 重新解析 `tables.json.table_event_differences`：**85 条是逐版本比较记录**；按 `mysql_only` 或 `postgresql_only` 至少一个非空过滤后为 **51 条**，两者均空为 **34 条**，涉及五域（system 38、auth 9、file 1、office 2、workflow 1）。此前“触发差异的域 5（system 61…）”混淆版本记录数，现已修正。该事件签名只比较操作和表名（含 rename 目标），不是 SQL 全文/最终 schema 差异，也不是 51 个已确认业务缺陷。
- 五域 PostgreSQL 源文件共 **85 份 SHA-256**、字节数、运行 JAR 与 harness hash、history 的 version/checksum/success、列、独立索引完整定义、约束定义与失败异常链均已写入 JSON；不采集业务种子数据。

### 实际命令、采集器预检失败与验证

```bash
source scripts/build/init-build-env.sh
mvnd -B -f /tmp/apex-p0-mysql-evidence-J52CXYXF/postgresql-pom.xml \
  org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath \
  -Dmdep.outputFile=/tmp/apex-p0-mysql-evidence-J52CXYXF/postgresql-classpath.txt
source scripts/build/init-build-env.sh
python3 -B /tmp/apex-p0-mysql-evidence-J52CXYXF/run_postgresql.py
python3 -B /tmp/apex-p0-mysql-evidence-J52CXYXF/check_postgresql.py
bash scripts/ci/check-doc-links.sh
git diff --check
```

首次采集命令 `job-6b336fdbf1614ce583b831ffd1c5ee56` 的依赖解析 **BUILD SUCCESS**，但环境查询 `current_setting('lc_collate')` 在 PostgreSQL 17 不可用，退出 **1**，发生在业务库创建/迁移之前。临时采集器改为查询 `pg_database.datcollate` 后，在**另一个新容器、新数据库**执行上述正式轮次；首个容器 `apex-p0-pg-da68d9647a30`（`127.0.0.1:39189`，tmpfs）仅为未执行迁移的预检现场，保留未动。不是重试失败迁移或掩盖 SQL 失败。

核验脚本已通过 JSON 解析、5 库唯一性、85 份 PostgreSQL 源 hash 和字节数、既有 85 份 MySQL 源 hash、运行 JAR/harness hash、history 与源脚本关联、四域执行数量、system 空 history 与失败异常、列/索引/约束所属表引用检查。最终执行 `git diff --check` 退出 0；新 JSON/报告的末尾换行、逐行空白与本地链接目标检查通过；全仓文档链接检查 **662 个 Markdown、2691 个链接、断链 0，退出 0**。PostgreSQL JSON 的 SHA-256 为 `96f1d91cf09ae92ff081e12f56dd3efb87b8c80fd784223fd7509beb4b3275fe`。此次未重跑完整 Java 回归、未启动五业务服务，未验证存量升级、生产升级或回退；**两库 system 原始链均失败，P0 继续阻塞、不得准出**。

## 2026-10-17 system 双库迁移修复与最终定向回归

本节追加后续修复结果，前文均保留为当时历史观测。[修复证据](system-migration-repair-evidence.json)保存三份日志原始 `/tmp` 位置、SHA-256、必要行号、三份实际 Surefire XML 的摘要/hash、最终库名及 29 份历史 SQL 的 HEAD/当前文件 SHA-256。此次证据编辑未修改代码或 SQL，未复制大段日志或 XML 属性。保留的片段信息经审阅不含连接密码、令牌或密钥；原始日志进程退出码不能仅凭日志文件还原，以下只报告可核实的 BUILD 终态。

### RED 与最终结果

- `/tmp/system-dual-red.log`：`2026-10-17T10:51:36+08:00` 结束，**BUILD FAILURE**，双库测试 2 项、Errors 2、Failures 0、Skipped 0。第 719–734 行分别重现 MySQL V21（`wf_definition` 不存在，42S02/1146）与 PostgreSQL V1（`uk_user_id` 重名，42P07/0）；对应之前失败快照。
- `/tmp/system-dual-final.log`：`2026-10-17T12:28:45+08:00` 结束，**BUILD SUCCESS**，本次选定 reactor 10 项均 SUCCESS。日志第 757、1347、1349、1353 行与 `apex-system/target/surefire-reports/` 下三份 `TEST-*.xml` 一致：**12 项 = FlywayMigrationStartupTest 6 + SystemDualDatabaseMigrationIntegrationTest 2 + FlywayMigrationVersionTest 4；Failures 0、Errors 0、Skipped 0**。不合并旧 Failsafe XML 或先前 5050 项。
- XML 的 Java 版本为 **17.0.12**，`java.home=/Users/eric/.sdkman/candidates/java/17.0.12-oracle`。这是 Java 17 定向验证，不是 Java 21 验证。
- `/tmp/system-dual-spotless-final.log`：`2026-10-17T12:28:47+08:00` 结束，**BUILD SUCCESS**，选定 10 项 reactor 的 `spotless:check` 成功；system 第 129 行显示 971 个 Java 文件、0 需修改，均由缓存判为已清洁。此为该日志实测范围，不称全仓格式或 SQL 语义验收。

### 两个最终新空库

库名从最终日志第 759、1139 行提取；测试源码创建唯一新库，设置 `baselineOnMigrate=false`、`cleanDisabled=true`、`validateMigrationNaming=true`，从 classpath 加载对应方言迁移。

| 数据库 | 最终库名 | 首次迁移 | validate | pending | 二次迁移 | applied |
| --- | --- | --- | --- | --- | --- | --- |
| MySQL，127.0.0.1:13317 | `apex_reg_a119e67144b444eda161e2bb227f89e9` | 61 条，V1–V61 | 通过 | 0 | 0 条 | 61 条有序且状态 applied |
| PostgreSQL 17.11，127.0.0.1:41187，public | `apex_reg_4547f75e60664165892a25c64c5db0c8` | 61 条，V1–V61 | 通过 | 0 | 0 条 | 61 条有序且状态 applied |

日志第 1134–1138、1216–1223 行直接记录迁移、validate 与第二次无需迁移；pending=0、第二次执行数=0、有序 applied 则由[双库测试](../../../../apex-system/src/test/java/com/apex/scaffold/interfaces/test/SystemDualDatabaseMigrationIntegrationTest.java)第 62–69 行断言及实际通过 XML 共同支持。测试还检查选定配置、权限与角色关联、菜单、工作流/OAuth2 字段、种子微信社交绑定回填；空库种子数据上的回填断言不能作为存量真实业务升级验收。

本轮未重新导出最终完整 schema 或逐条 Flyway history/checksum，不填造最终表数、约束数或 checksum。原 [MySQL 快照](mysql-schema-evidence.json)及 [PostgreSQL 快照](postgresql-schema-evidence.json)仍是失败时现场，不能拿其结构替代新库 V61；其 hash 已固定到新证据中。现有静态清单也仍为原采集时间点，不能自动当成修复后重新采集。

### 29 份历史 SQL 与存量 checksum 风险

以 `git diff --name-only -- apex-system/src/main/resources/db/migration/mysql apex-system/src/main/resources/db/migration/postgresql` 限定范围实查，共 **29 份**：

| 方言 | 数量 | 改动版本 |
| --- | ---: | --- |
| MySQL | 14 | V21、V23、V24、V25、V28、V30、V33、V36、V42、V48、V51、V52、V58、V59 |
| PostgreSQL | 15 | V1、V21、V23、V25、V27、V28、V30、V33、V36、V40、V41、V42、V47、V50、V59 |

证据逐文件给出完整名称、`git show HEAD:<path>` 的旧字节 SHA-256 与工作区当前字节 SHA-256，基准 HEAD 为 `0c506a7ee0f94a07b359edda8ebbab4aac60dab3`。29 份文件 hash 均变化；**SHA-256 不是 Flyway checksum**，本轮未实测这些文件在存量库中的新旧 Flyway checksum 数值，不能推算或冒充它们。

可核实的代表修复包括 MySQL V21 改为真实 `wf_workflow_definition` 表、沿用 V20 已有 version 字段并对齐 `sys_config` 字段；PostgreSQL V1 为各表唯一约束使用不同名称；PostgreSQL V27 全文索引改用内置 **`simple`，不提供中文分词**；PostgreSQL V40 避免重复新增 title。新空库执行成功并不证明所有双库类型、默认值、索引、权限与业务语义完全等价。日志另提示 Flyway 对 PostgreSQL 17.11 的支持尚未测试（该版本宣称最新支持 PostgreSQL 16），本次成功不扩展为通用兼容性结论。

修改已执行的版本化迁移可能导致既有库 `validate` checksum 不匹配；MySQL 失败现场还可能存在部分已提交 DDL。**本轮未执行存量升级或 repair，不能将空库通过表述为存量可直接升级。** repair 只调整历史元数据，不会执行被修改的 DDL 或自动修复结构/数据差异；后续须对照实际存量 history/schema、备份及变更策略处理，不能直接对失败库套用 repair。

**先前 5050 项完整 `clean verify` 于 07:18:25 完成，早于本次 SQL 修改，只代表修改前工作区。此次只核对最终定向测试和 Spotless 日志，未执行修改后的 `clean verify`、存量升级、repair、生产升级或回退演练。P0 仍未准出。**

### 本次证据编辑验证

- Python JSON/证据一致性检查退出 **0**：报告目录 **10 份 JSON** 均可解析；29 组 HEAD/当前 SQL SHA-256 与限定 Git diff 路径集合一致；三份原始日志 hash、三份 Surefire XML hash 与 testcase 数量一致，合计 `[tests=12, failures=0, errors=0, skipped=0]`；两份旧失败 schema JSON 的 SHA-256 未变。核验命令标识为 `job-ac32aa12d4e94994b08dc12bb33f4241`。
- `bash scripts/ci/check-doc-links.sh` 退出 **0**：**662 个 Markdown、2696 个链接、断链 0**，命令标识 `job-e797a9ce4a37461eb5b2561b5fa8b89d`。
- `git diff --check` 退出 **0**，命令标识 `job-d1b045f9439c44f0a2161dc15afe6203`；因该目录为未跟踪资产，另对本次报告与新证据检查末尾换行、逐行空白。以上为证据/文档检查，不是重跑 Java 测试或完整门禁。

## 2026-10-17 网关配置补采、最终 schema 只读重采与工具固化

本节追加本轮结果，不覆盖前文失败、重跑及当时“未采集最终 schema”的历史记录。用户明确授权修订 system 的 **29 份历史 SQL，仅修复初始化缺陷**；这是本次受控例外，不构成任意修改生产已执行 history/checksum、执行 repair、存量升级或生产操作的授权。

### 网关与部署配置静态证据

[gateway-config-evidence.json](gateway-config-evidence.json)由当时临时工具工程中的网关配置工具（`scripts/ci/java/src/main/java/GatewayConfig.java`，未入库、现已移除）采集，记录 **25 个来源文件、23 条网关路由声明、624 条配置引用**。其中网关作用域去重环境变量 **41 个**，**29 个没有显式 gateway Compose 候选声明**。624 条包含所选来源中的环境变量及属性引用，不是 624 个独立环境变量；23 条是不同配置来源/profile 的声明，不是运行时有效路由数量，也不与 Java 静态路由直接相加。

工具范围仅为 **gateway 及所选部署入口**：gateway application/bootstrap、Nacos 网关文件、所选 Compose、镜像构建文件、部署脚本/systemd 与环境示例；不是全业务配置全量。候选保留 service、来源行号、hash、profile 与顺序；未求解 Spring/Nacos 优先级、profile 激活、路由列表替换、Shell 控制流或镜像阶段，未读取真实 `.env`、env_file、EnvironmentFile、宿主环境或远端 Nacos。环境示例、Shell 引用与其他服务声明不能当作 gateway 注入证据；29 项无候选也不等于已证实运行缺失。运行路由、实际注入及可达性尚未验证。

### 十个成功测试库的最终结构与 history

[final-schema-evidence.json](final-schema-evidence.json)于 `2026-10-17T04:47:06.965634+00:00` 开始，对 **10 个既有成功完成空库迁移的隔离测试库**重新只读查询 catalog 与 `flyway_schema_history`，PostgreSQL 限定 public。system 使用上节两个最终 `apex_reg_*` 库，其余四域复用此前成功库，均重新查询而非拼接旧 schema 快照。本轮不执行迁移、建库、业务数据查询、repair、clean、升级或写入。原始采集由 **`/tmp/p0_schema_compare.py` 临时工具**执行，路径与 SHA-256 记录于证据；两份旧失败快照及其 hash 保留。

两库合计 **170 条 history 均 success**，各自与对应方言当前源文件的 Flyway 逐行 CRC32 checksum 一致，源脚本集合均有 history 对应。此处是测试库与当前源的校验，不是 MySQL 与 PostgreSQL checksum 互相相等，也不是生产存量 checksum 风险已消除。

[归一化对照](schema-comparison.json)的数量口径如下，含 history；表对象包括视图，不全部是业务基表：

| 域 | MySQL/PostgreSQL 表对象 | MySQL/PostgreSQL 列 | MySQL/PostgreSQL 独立索引 | MySQL 索引逐列元数据行 | 每库成功 history |
| --- | --- | --- | --- | ---: | ---: |
| system | 96 / 81 | 1347 / 1156 | 447 / 372 | 577 | 61 |
| auth | 16 / 16 | 213 / 213 | 60 / 62 | 85 | 14 |
| workflow | 6 / 6 | 73 / 73 | 29 / 29 | 39 | 2 |
| file | 6 / 6 | 90 / 90 | 27 / 27 | 31 | 5 |
| office | 16 / 16 | 250 / 250 | 63 / 63 | 105 | 3 |

原始采集 JSON 的 MySQL `counts.indexes` 保留了 **577/85/39/31/105** 的逐列元数据行数；固化工具按 `(table_name,index_name)` 去重重算对照，独立索引数为 **447/60/29/27/63**。原始证据未为纠正标签而改写，独立索引以对照中的重算值为准。

system 有 **15 个表对象、193 个列存在性差异**；193 是列集合的对称差，不是总列数相减的 191。对象差异含 `v_token_health` 视图等，尚未完成逐项来源和业务归属归因，不能称 schema 等价。auth/workflow/file/office 的表名和列名集合一致，也不能据此认定类型、默认值、生成行为、索引、约束或业务语义等价。

对照仅折叠有限类型别名，保留长度、精度、unsigned、时区等；不将 tinyint 自动等同 boolean、datetime 等同 timestamp 或 JSON 等同 JSONB。默认值以 catalog 原文比较，索引签名与约束类别计数只是有限结构对照；原始定义仍保留。方言表示差异及跨库 history checksum 差异是观察项，不自动定性为缺陷；独立 routine/trigger/sequence、授权、种子/业务数据、执行计划、应用启动与生产升级均不在验收范围。

### 可复用工具与测试执行来源

已核对[数据库结构工具](../../../../scripts/ci/source-archive/scripts/ci/collect_database_schema.py.source)及[15 项测试](../../../../scripts/ci/source-archive/scripts/test/test_collect_database_schema.py.source)、当时临时工具工程中的网关工具 6 项测试（`scripts/ci/java/src/test/java/GatewayConfigTest.java`，该工程未入库、现已移除）。数据库工具提供两种入口：`--collect` 仅面向显式 manifest 白名单中的既有测试库，以只读事务查询元数据/history；`--compare` 仅离线重算对照。**本轮固化数据库 CLI 只实际执行了离线重算，尚未用其 `--collect` 对真实数据库执行采集**，不能将临时工具的真实查询冒称固化 CLI 已实跑。采集相关测试以 fixture/Mock 验证边界，不替代真实采集验收。

可复用的离线命令（仓库根目录；已有输出须显式允许替换）为：

```bash
python3 -B scripts/ci/collect_database_schema.py \
  --compare docs/06-testing/jdk21-unified-auth/20261016-p0/final-schema-evidence.json \
  --output docs/06-testing/jdk21-unified-auth/20261016-p0/schema-comparison.json \
  --replace-comparison
```

本轮测试子代理报告新增脚本测试 **15 + 6 项通过**。主代理随后加载固定 Oracle Java 17.0.12 / mvnd 2.0.0-rc-3 环境，执行 `python3 -B -m unittest discover -s scripts/test -p 'test_collect_*.py'`，**49 项通过，57.980s**；再执行 `python3 -B -m unittest scripts/test/test_init_build_env.py`，**13 项通过，1.073s**，合计 **62 项通过**。组合命令 `job-f4b4b90ad1b74cbab0899b1fd7a4b955` 退出 0，随后 `git diff --check` 通过。这是采集及环境脚本验证，不计作修改后 Java 完整 verify。本次未运行 Java 回归。文档更新后 `bash scripts/ci/check-doc-links.sh` 扫描 662 个 Markdown、2710 个链接，断链 0；随后 `git diff --check` 通过，组合命令 `job-d4a8105fc1a24ca796c60673e9334389` 退出 0。

## 2026-10-17 system 差异归因与修改后完整门禁

本节为后续终态补充，前文成功、中断、运行中及“尚未执行”的叙述均保留为各轮历史，不继承为当前通过结论。专项见[system 差异归因报告](system-schema-attribution.md)及[逐项归因 JSON](system-schema-attribution.json)；完整回归使用独立的[修改后门禁证据](post-repair-verify-evidence.json)，未覆盖旧证据。本次仅更新文档，不修改 SQL、生产代码或执行源码格式化。

### system 结构差异归因

- **15 个对象均为 MySQL 有、PostgreSQL 无：14 张基础表、1 个视图 `v_token_health`**。193 项列存在性差异为 **167 项缺对象衍生列（含视图 8 列）+26 项共有表列漂移**；共有表为 `oauth2_authorization` MySQL 多 16 列、`sys_user` MySQL 多 9 列、`off_sync_history` PostgreSQL 多 `update_time` 1 列。MySQL 独有 192 列、PostgreSQL 独有 1 列，净差 191，不与对称差 193 混淆。
- 对象遗漏对应 PostgreSQL 占位脚本或同版本不同功能迁移；专项已给出逐对象、逐列源文件及行号，并核对 system 两库共 122 份迁移源 hash。不能用版本集合相同或占位注释证明功能覆盖。auth 中四个安全对象有直接模型/仓储引用，但属于独立业务数据库，不证明 system-service 调用缺表；其余未命中仅为字面扫描结果，不能排除动态 SQL 或外部客户端。
- **4 项可空性漂移**均来自 MySQL V40 `CHANGE COLUMN` 未保留 `NOT NULL`，而 PostgreSQL rename 保留原约束：`office_message.send_status`、`office_message.target_user_ids`、`wf_workflow_definition.enabled`、`wf_workflow_definition.node_definition`。
- 另有真实默认值漂移：`wf_workflow_definition.enabled` 为 MySQL **ACTIVE**、PostgreSQL **DRAFT**；长度漂移：`sys_user.keycloak_sub` 为 MySQL **varchar(255)**、PostgreSQL **varchar(100)**。本轮仅归因，未修复这些 SQL 差异。
- 类型差异 277 项、默认值差异 208 项已分类，不能把外观分类当作等价证明；索引签名差异涉及 27 个对象（14 个缺表衍生、13 个共有对象）。off_* 索引部分差异源于 PostgreSQL V42 已建表、MySQL V57 才建表，使 MySQL V43 条件调用跳过；其他部分重复/额外索引历史来源仍未知。约束、生成行为、时间/JSON/全文检索语义、查询计划及真实业务读写均未完成验收。

### SQL 修改后完整 clean verify 终态

固定 Oracle Java `17.0.12-oracle` / mvnd `2.0.0-rc-3` 环境执行 `source scripts/build/init-build-env.sh && mvnd -T4 -B clean verify`，任务 `job-758ba6ca98d14b22a2ce1e0fae088932`，开始 `2026-10-17T16:51:01+08:00`、结束 `2026-10-17T17:04:20+08:00`。命令**自然结束，退出 1，BUILD FAILURE**；退出码来自后台命令最终状态，初始化脚本 `set -e` 使 shell 尾部退出码标记未打印。原始日志 `/Users/eric/.cache/apex-p0-post-repair-verify.log` 的 hash、行号及逐模块结果保存在独立证据中。

| 项目 | 本轮结果与限制 |
| --- | --- |
| Reactor | 31 项：26 SUCCESS、2 FAILURE、3 SKIPPED |
| Surefire | 4648 项；Failures 0、Errors 0、Skipped 0 |
| Failsafe | 104 项；Failures 0、Errors 7、Skipped 1 |
| 合计 | 4752 项；Failures 0、Errors 7、Skipped 1，实际通过 4744；仅累计插件级汇总，不重复累计类明细 |
| system | `failsafe:verify` 失败：拉取 `testcontainers/ryuk:0.8.1` 返回 HTTP 500 connection refused，重试后 `localhost:2375 failed to respond`；`AbstractMySqlContainerBase` 静态初始化失败，导致 `FlywayMigrationStartupTest` 6 个错误、`AccountDeletionUserMapperIntegrationTest` 1 个错误。未观察到进入业务断言，不能归因为 SQL 回归或记为通过 |
| office-service | `spotless:check` 因 7 个文件格式失败；保留既有改动，未自动格式化 |
| 跳过模块 | `apex-example`、`apex-system-service`、`apex-auth-service` 因依赖失败未执行后续门禁，不是人为 `skipTests` |
| Spotless | 28 项出现 check 标题；office-service 失败，其余已执行项目未报格式失败；3 个跳过项目未验证 |
| PMD/p3c | 27 项出现 check 标题且无 PMD 门禁失败；office-service 在 Spotless 后未执行 PMD，3 个跳过项目未验证 |
| JaCoCo | 27 项出现 check 标题；有数据的已执行检查报告通过。根项目、services-bom、license-tools、兼容 infrastructure 因缺数据跳过；office-service 未到此阶段，3 个跳过项目未验证，不能称全仓覆盖率门禁通过 |
| ArchUnit | 包含在已执行测试中，不重复累计；不扩大为全部架构、租户或 SQL 语义通过 |

office-service 的 7 个格式文件为其 `src/test/java/com/apex/scaffold/officeservice/interfaces/router/OfficeOpenApiContractTest.java`，以及生产 `interfaces/handler/office/` 下 `OfficeDocumentHandler.java`、`OfficeCalendarHandler.java`、`OfficeOrganizationHandler.java` 和 `interfaces/router/office/` 下 `OfficeCalendarRouter.java`、`OfficeDocumentRouter.java`、`OfficeOrganizationRouter.java`（生产路径前缀为 `src/main/java/com/apex/scaffold/officeservice/`）。

本轮 `SystemDualDatabaseMigrationIntegrationTest` **2 项通过，无失败、错误或跳过**：双库新隔离空库执行 V1–V61，并校验 validate、无 pending、二次 migrate 为 0、有序 applied 及部分种子契约；不与 system 上述 7 个环境错误混淆。`SeataAtIntegrationTest` 跳过 1 项，因 `APEX_SEATA_IT=true` 环境门控未满足，真实 TC、双 MySQL 及 HTTP 分支未验收。可选脚本 `scripts/test/mvnd-only-build-tool-check.sh` 不存在，调用失败不能记为通过；核心回归使用指定初始化脚本及 mvnd。

### 剩余修复与准出限制

需恢复 Testcontainers/Ryuk 镜像与容器 API 可达性，审查并修复 office-service 上列 7 个格式文件，再执行完整门禁并补齐跳过模块和未执行的质量检查；本次文档编辑未实施这些修复。另须按业务归属决定 14 表/1 视图、26 共有表列及默认值、长度、NOT NULL 漂移的处理方案，继续核实未知索引来源和双库语义；不能从静态归因直接推导删除历史对象或任意改 SQL。存量 history/checksum、部分提交 DDL 风险、存量升级、repair、生产升级及回退均未验证。本轮不是 Java 21 验收，未运行 remote-it/stress 额外场景。**完整命令已执行但失败，P0 仍未准出。**

### 本次文档更新检查

本次只读解析 P0 目录 15 份 JSON，核对专项归因对象/列集合与 `schema-comparison.json` 一致，类型、默认值、可空性和索引分类计数一致，Reactor 逐项状态及插件级测试汇总一致；两份编辑文档均有末尾换行且无行尾空白。`bash scripts/ci/check-doc-links.sh` 扫描 663 个 Markdown、2763 个链接，断链 0。本次未重跑 Java 构建或数据库迁移，以上检查不改变完整门禁失败及 P0 未准出的结论。

## 2026-10-17 三项修复后的最终完整门禁

本轮在当前 shell 加载固定 Oracle Java `17.0.12-oracle` / mvnd `2.0.0-rc-3`，复用既有 MySQL 8.0.46（13317，example 库 `apex_example_test`）与 PostgreSQL 17.11（41187）。root 密码仅从容器环境读入内存，未输出。未跳过测试、未禁用 Ryuk、未削弱 SLA、未删除现有库。完整命令 `mvnd -T4 -B clean verify` 于 `2026-10-17T21:35:03+08:00` 启动，Maven 于 `21:53:35+08:00` 自然结束，shell 于 `21:53:36+08:00` 确认**退出 1，BUILD FAILURE**；没有主动停止或重复重跑。

独立[最终门禁证据](final-repair-verify-evidence.json)记录31项逐模块reactor、每条插件汇总及行号、环境与限制，旧证据均保留。原始日志为 `/Users/eric/.cache/apex-p0-final-repair-verify-20261017-213503-14271.log`，SHA-256 为 `62282f85c5c7159c060f83df10516e31fcb8d50b9399afe499f5e135567f7bbd`。

| 项目 | 最终结果 |
| --- | --- |
| Reactor | 31项：27 SUCCESS、1 FAILURE、3 SKIPPED |
| Surefire | 4649项，Failures 1、Errors 0、Skipped 0 |
| Failsafe | 103项，Failures 0、Errors 0、Skipped 1 |
| 合计 | 4752项，失败1、错误0、跳过1、通过4750；只累计插件级汇总，不重复累计类明细或failsafe:verify |
| system | SUCCESS；Surefire1542、Failsafe14，均无失败、错误或跳过；Ryuk正常启动，MySQL空库62迁移和双库V61→V62通过 |
| 唯一失败 | office-service的`OfficeOpenApiContractTest.generatedOfficeAdminAndLoginContractsDescribeBodiesDataAndPaginationDefaults`在232→331行断言失败：`GET /admin-api/v1/office/apps`的200响应`application/json` schema `$ref`期望`#/components/schemas/OfficeAppListResult`，实际为空；operationId断言已通过 |
| 失败定位边界 | Router 78–84行和Handler 95–103行已有响应implementation声明，不能简单归因为缺注解。生成文档未满足响应契约已证实，合并/生成机制的更深根因尚未证明；超出本轮V62计数、格式、连接修复范围，未改业务或削弱断言 |
| 未执行范围 | example、system-service、auth-service被reactor跳过；office-service在Surefire失败，未到Failsafe、Spotless、PMD、JaCoCo |
| 质量检查 | Spotless、PMD/p3c各27个模块，无对应门禁失败；JaCoCo为27模块38次check，有数据检查通过，根项目/services-bom/license-tools/兼容infrastructure缺数据跳过，不能称全仓覆盖率门禁通过；ArchUnit已含在测试计数中 |

三项修复的最新状态如下，来源分别为[容器与格式证据](container-format-repair-evidence.json)、[V62修复证据](schema-alignment-repair-evidence.json)：

- office七文件此前已精确格式化且95个Java文件check通过；本轮先发生Surefire契约失败，不能声称本轮office格式检查已执行。
- Podman VM systemd失效代理已在当前runtime清空，Ryuk真实定向启动/清理证据保留；本轮日志17065行再次正常启动。VM重启或同步后代理可能恢复；本轮未另做退出后容器清理断言。
- 配对V62补齐PostgreSQL14表1视图、OAuth16列、用户安全9列与keycloak_sub长度255；MySQL补update_time、恢复四项业务NOT NULL并在持久DDL前拒绝存量NULL；workflow默认统一DRAFT且保留存量ACTIVE，补有效遗漏索引。既有定向8项通过，覆盖双库V61→V62、validate、无pending、重复migrate为0、数据保留及NULL负例。测试负例恢复值后repair仅属于隔离测试流程。
- 固化CLI已真实collect/compare，见[最终catalog](schema-alignment-final-catalog-evidence.json)和[最终对照](schema-alignment-final-comparison.json)：两库各96对象（含视图和history）、1348列、62条成功history，匹配各自当前源checksum；对象、列存在性及业务列可空性差异均为0。索引451/456、约束152/141；仍有337类型、1348 generation、242 raw default、98列顺序、14索引签名multiset差异，不能统称语义等价。两项可空性差异仅为视图COUNT元数据。

`SeataAtIntegrationTest`仍因环境门控跳过1项；真实TC及全业务运行条件未验证。Flyway仍提示PostgreSQL17.11超出其最新已测试版本16。Java21、全业务语义、存量checksum/部分DDL风险、生产升级与回退演练、remote-it/stress均未验收。**完整门禁本轮失败，P0仍未准出，整个升级计划未完成。**

本轮收尾检查：P0目录24份JSON均可解析；独立重算日志SHA-256、31项reactor及31条插件汇总，与新证据逐项一致，测试总数与质量检查模块数一致。三份交付文件均有末尾换行且无行尾空白。`bash scripts/ci/check-doc-links.sh`扫描663个Markdown、2770个链接，断链0；`git diff --check`通过。

## 2026-10-17 OpenAPI 修复后的局部验证与后续完整命令

本节追加最新轮次，保留上述所有失败与成功历史。局部日志 `/Users/eric/.cache/office-openapi-related-tests-20261017-17735.log` 的 SHA-256 为 `53f3612f56a8761c1384545929558f277038dda99400b59efe754243f3766e75`。原始日志956、1101、1153、1246、1268、1548、2149、4218行的八个模块Surefire汇总依次为538、9、11、30、24、69、100、345，合计 **1126项，Failures 0、Errors 0、Skipped 0**；其中4214行 `OfficeOpenApiContractTest` 两项通过。4232–4248行给出所选10个reactor项目全成功、`BUILD SUCCESS`，结束于 `22:22:57+08:00`。这是相关单测范围，不能替代完整verify或Office后续质量门禁。

随后[完整命令证据](post-openapi-verify-evidence.json)记录 `source scripts/build/init-build-env.sh && mvnd -T4 -B clean verify` 于 `23:01:06+08:00` 启动，shell于 `23:19:27+08:00` **自然退出1**。已复核原始日志 `/Users/eric/.cache/apex-p0-post-openapi-verify-20261017-XXXXXX.log`（`XXXXXX`为实际文件名的一部分），SHA-256与证据一致：`a3ebf99c9db87742403e082473dc111f43087a4aaa7cb57002262a9240348d0a`。

| 项目 | 本轮已证实结果与边界 |
| --- | --- |
| 命令终态 | 14706行 `DaemonException$StaleAddressException: Could not receive a message from the daemon.`；14920行收消息3000ms超时。无最终Reactor Summary、BUILD结论或最终失败goal，不写成Maven BUILD FAILURE，也不推算31项最终状态 |
| workflow HTTP | 8348–8358行 `WorkflowDatabaseHttpIntegrationTest.pendingTasksUseAuthenticatedAssignee` 在源码111行WebTestClient.exchange等待响应5秒超时；9398行Failsafe汇总27项、Errors 1。尚不能认定业务缺陷、数据库阻塞或负载flake；不能降低超时/断言掩盖失败 |
| 部分测试统计 | 只累计完整插件汇总：Surefire **4279**，失败/错误/跳过均0；Failsafe **38**，Failures 0、**Errors 1、Skipped 1**。部分合计4317、通过4315，不是全仓最终数；system Surefire1542通过，system Failsafe无插件终态，类级通过不混入 |
| Office | office-service于9571行被调度器跳过，**本轮未执行Office OpenAPI测试**；既不推翻上一轮局部通过，也不能算本轮验证成功。office业务库69项单测与office-service执行范围分开 |
| 其他未验收范围 | infrastructure、file-service于9576/9581行被调度器跳过；example、system-service、auth-service未见启动与终态，不能自行归入Reactor SKIPPED |
| 质量与环境 | 仅观察到Spotless/PMD各24模块、JaCoCo25模块35次check，非全仓完成；system未完成后续verify。Seata因缺APEX_SEATA_IT跳过1项，真实TC不计通过。Ryuk本轮有正常启动记录，VM代理修复仍仅是runtime状态 |

客户端失联与workflow超时是两条独立待调查事实。daemon ID为 `5ea5fca6`，保留其registry日志及输出；错误提示中的“may have crashed”不证明崩溃或OOM。现有23:22状态证据未列出该daemon，不能把其他Busy daemon视为本轮仍运行。此轮完整门禁未通过，**P0仍不准出**。

<a id="p0-可执行剩余资产与归属核查"></a>

### P0 可执行剩余资产与归属核查

下表保留2026-10-18配置及09:22资产补采时点的剩余清单；其中路由技术处置和当前依赖采集状态已由文末“本轮证据整合与当前P0剩余”更新，以文末当前清单为准。历史轮次保留。Java17完整门禁已有成功证据，不再列为未执行。静态资产补齐不等于语义或运行时验收。

| 剩余资产 | 已有证据及应补齐的结果 |
| --- | --- |
| 当前源码、声明与Javadoc分母 | 20261018新快照已完成：2221文件、26301显式声明，逐文件模块/服务候选及hash已关联。本轮采集口径为parse-only显式顶层/成员声明，局部/匿名类、隐式及Lombok生成成员不计，record组件按parse阶段树计；标签只采原文、语义验收在P6。剩余为确认该统计口径及适用范围，不再重列全量清单未做 |
| 五域表与运行使用映射 | 含配对V62的172迁移、836表事件、136域/表组合与628去重前文本引用候选已关联。剩余逐域/表确认候选是否为真实Mapper/R2DBC/任务访问，登记tenant/唯一键/历史NULL及业务权威；589条SQL未解析项按源行解释，不能将临时表/已删除表当最终schema。system已有337类型、1348 generation、242 raw default、98列序和14索引multiset差异仍须语义分类；其他四域catalog条件须登记 |
| API与可信边界 | 新时点258静态路由与139未解析候选均有模块、服务候选、声明索引和精确位置。P0剩余按下文九类逐项登记处置/验证前置，并把397项与网关、权限、DTO/OpenAPI契约关联；无需虚构解析成功。运行时office子路径、auth internal调用者/tenant、workflow身份头直连及跨租户矩阵留所属阶段执行并在P0登记 |
| 全业务配置与部署资产 | 已完成本地静态扩围：138来源、1872配置定义、4652引用、1661部署声明、622显式注入候选；新快照见下文。仍须核对profile/优先级、跨文件变量传播、动态服务选择及真实注入。474个作用域/变量组合中342个无同名Compose候选，不等于运行缺配置 |
| 可追溯依赖与外部条件 | 当前31 POM已与旧基线关联：27同hash、4变化；旧dependency:tree原日志缺失，35精选坐标保留为历史证据，当前完整树未认证。补可复核完整树、有效配置/插件、scope/档位及收敛分析。记录DB/Redis/Nacos/MQ/存储/Provider/TC及容器网络的实际可用条件；未具备者记未执行，不用Mock替代 |
| 完整基线与故障证据 | 已有第十五轮31项目成功、Surefire4790/Failsafe300且真实TC跳过1的完整门禁证据，本轮不重复构建。仍须将资产与工作区/POM/hash及执行轮次关联；历史workflow超时和mvnd失联根因未证明，保留为调查事项，不把未复现历史故障写成当前构建失败 |
| 后续阶段：存量升级与回退证据 | P0登记真实旧库、checksum/部分DDL风险、所需环境与责任；实际冲突/NULL处理、备份恢复、升级/生产repair及上线回退演练属于P7/T11/T13相应前置条件，不要求在P0完成生产实施，也不能以新空库测试替代 |

归属未决须按[历史归因](system-schema-attribution.md)和[API/表评估](api-table-assessment.md)逐对象登记。V62已补齐存在性，不能继续写成缺14表1视图；但auth独立库实际使用的 `mfa_config`、`biometric_auth`、`device_trust`、`ip_access_control` 与system历史同名对象的保留、迁移和权威关系仍待确认。其余11个历史对象 `encryption_key`、`encryption_key_version`、`jwt_key_version`、`login_history`、`oauth2_client_secret_history`、`password_history`、`security_audit_log`、`token_rotation_history`、`token_security_event`、`trusted_device`、`v_token_health` 未见直接生产映射，不等于无人使用或可删除。system历史OAuth16列、sys_user九安全列的当前消费者与未来身份权威，以及office使用 `off_sync_history.update_time` 与system历史表的关系仍需闭环；V62中的默认补时不是历史最后更新时间。workflow业务键租户范围、file历史NULL归属及账号合并冲突不能猜测回填。上述需要所有者/业务确认，索引命名等实现细节不必交给用户决策。

### D01 与 P1 前可独立推进的核验

**Oracle 21路线已选定，无须重复选择发行版。** D01剩余是冻结精确补丁/build、各目标OS/架构安装包或镜像及checksum/digest、SDKMAN标识、获取渠道、对应许可版本/日期，并结合组织生产/开发/分发用途与现有授权确认许可适用性；`21.0.12+7`仍是历史候选，不是已核准发行物。组织用途和授权事实应由实际使用方/许可责任人提供，不能由技术代理推定。

P0未准出前可独立进行只读资产补全、依赖/BOM覆盖与插件规则审查、Oracle发行物/许可材料核对、容器/CI/部署JDK矩阵及Javadoc口径设计；本轮未把这些记为完成。需要运行的mvnd内嵌Maven/JDK21、编译器与Lombok/MapStruct、JaCoCo、PMD/p3c等价规则、doclint正反例及运行栈兼容验证，可先形成隔离验证方案，待允许运行及环境具备后采证；不切换仓库构建基线，不提前记P1通过。D02–D05及D07按计划在P2相应契约/数据模型实施前确认，D06在P3/P4前确认，不把所有后续业务决策混作当前只读核查前置条件。

### 本次仅文档核查结果

已核对上述两份原始日志的关键行、局部单测八条汇总及最新完整命令的27条完整插件汇总，并独立计算两份日志SHA-256；历史轮次与JSON未覆盖。文档链接检查扫描663个Markdown、2775个链接，断链0；`git diff --check`无诊断。两份目标文档原已为未跟踪文件，另分别执行对 `/dev/null` 的 `git diff --no-index --check`，均无空白诊断（退出1表示存在内容差异），直接行尾空白检查亦无命中。已复阅编辑后的状态和决策条目。本轮只改这两份既有文档，未运行Java构建/测试、数据库迁移或Java21兼容验证。

## 2026-10-17 至 18 日 Java 17 基线重试

本轮获准执行故障调查和构建，追加[独立重试证据](baseline-retry-evidence.json)，保留前述仅文档轮次及所有失败历史。两次构建均先在当前shell加载固定Oracle 17.0.12-oracle、mvnd 2.0.0-rc-3环境，使用 `-T1`；未修改生产/测试源码、超时、断言或跳过条件，未切换Java21。

定向 `WorkflowDatabaseHttpIntegrationTest` 于23:43:22收到构建请求，23:45:53自然退出1、`BUILD FAILURE`：所选9项reactor中8成功、1失败，Failsafe **3项，Failures 0、Errors 1、Skipped 0**，未执行Surefire。相同 `pendingTasksUseAuthenticatedAssignee:111` 仍在WebTestClient.exchange等待5秒处超时；H2已连接、随机端口已监听，Spring启动69.707秒。Handler使用可信身份，查询已惰性执行于专用jdbc调度器；没有连接耗尽、SQL异常或分段耗时证据，不能推定SQL慢、业务缺陷或负载flake。日志为 `/Users/eric/.cache/apex-p0-workflow-retry-20261017.log`，SHA-256 `ddf44128cf7eaf91ef8276324274caf41aa6e1db74a4206ce59f28f7ec6b2d7e`。

旧失联daemon `5ea5fca6` 最后日志24523行为23:19:24.022发送KeepAlive，对应客户端23:19:27接收超时；daemon输出没有OOM或自然BUILD终态。指定范围内未找到Java崩溃报告不能证明无崩溃，底层失联原因仍未闭合。此次定向和完整命令均正常获得Maven终态，不能据此宣称旧通信问题已修复。

| 项目 | 本轮完整命令事实 |
| --- | --- |
| 命令与终态 | `mvnd -T1 -B clean verify`，23:48:06至次日00:08:06 +08:00；自然退出1、`BUILD FAILURE`，daemon `039e5a07`，没有复现客户端失联 |
| Reactor | 日志8863–8895行明确31项：**13 SUCCESS、1 FAILURE、17 SKIPPED** |
| 测试 | 仅计15条完整插件汇总：Surefire **2694**、Failsafe **22**，合计 **2716项，Failures/Errors/Skipped均0**；这是失败前执行范围，不是全仓全部测试通过 |
| 唯一失败goal | 8902行system `spotless:2.43.0:check`，7文件格式违规：RouterOpenApiCoverageTest、OpenApiDocumentationCustomizerTest、OAuth2AuthorizationHandler、SocialLoginHandler、OAuth2RouterConfig、KeycloakRouterConfig、OpenApiDocumentationCustomizer；准确路径见JSON |
| system | Surefire1549、Failsafe14通过，含真实迁移；历史1542保留为历史，不覆盖。本轮后续Spotless失败，PMD及最终JaCoCo未到达 |
| 未执行 | workflow-service、office-service等17项被Reactor跳过；workflow HTTP、Office OpenAPI本轮均未执行，不能以workflow业务库40项单测替代HTTP验证 |
| 质量范围 | Spotless到达14模块，13通过、system失败；PMD/p3c到达13模块。JaCoCo14模块22次check，20次有数据检查输出通过，根项目/services-bom两次缺数据跳过；共享target并发限制使其不能扩大为可重复的全仓覆盖率验收 |
| 环境边界 | Ryuk于7767行正常启动，未另行断言退出清理。Seata starter本轮Reactor跳过，不能把历史环境门控跳过1项移入本轮计数；真实TC仍未验收 |

完整日志 `/Users/eric/.cache/apex-p0-baseline-retry-20261017.log` 的SHA-256为 `5d1a081891ffc06353a155183b82121e273bf936df4950a809035d90da1483b9`。本轮MySQL密码仅从既有容器读入内存并在退出前unset，沿用apex_example_test和既有双库环境，未删库。完整命令结束后未再次构建或格式修复。

**隔离限制：**独立daemon `4ff79feb` 在同工作区23:43:01–23:47:20执行system `-T4`定向单测，与workflow定向重叠；23:54:22–23:56:15再次执行同类命令，与完整门禁重叠；23:56:35还收到指定10个文件模式的 `spotless:apply` 请求。它不是本次启动，未擅自终止。开始前的无残留进程检查只代表当时瞬间，不能声称本轮全过程隔离或源码冻结；不能认定该格式操作造成此次格式失败，也不使用共享target报告补计测试。观察到系统load约160–241、swap使用约7–8GB及独立LLVM编译，支持降低本次Maven并行度，但没有请求分段/JVM停顿证据证明其为超时或失联根因。

下一项可执行P0工作是先协调同工作区构建/格式操作并冻结快照，核对system七文件格式差异；为workflow HTTP链采集调度、连接获取、SQL及响应阶段耗时，形成一次有判别力的定向验证。保留当前失败，停止盲重试；其他声明、表/API、配置、依赖及存量升级回退欠账继续保留。**完整门禁本轮失败，P0未准出。**

收尾已独立解析新证据JSON，并从原始日志逐项重算15条插件汇总、Reactor与质量检查次数，核对两份日志SHA-256。全仓文档链接检查通过（663份Markdown、2777条链接、0断链）；`git diff --check`及三份交付文件的直接行尾空白/末尾换行检查通过，后者覆盖未跟踪文件。这些检查不替代失败的Java门禁。

## 2026-10-18 P0 门禁修复续验

本轮沿用固定 Oracle 17.0.12-oracle / mvnd 2.0.0-rc-3，每次构建前加载初始化脚本。发现旧 apex-example Surefire 已在 Druid/Flyway 获取连接阶段等待超过四小时，正确加载环境后执行 `mvnd --stop`，确认旧 daemon 和测试进程退出；这不能证明 workflow 历史超时根因。未改动 SystemDualDatabaseMigrationIntegrationTest、FlywayMigrationStartupTest 或 OfficeAdminRouter。

system 七文件定向 `spotless:apply spotless:check` 于 06:25:25 成功，格式器报告七文件原已干净、零修改。首次 workflow 诊断在编译阶段发现七个写方法重复不可重复的 `@OperationLog`；删除重复声明、保留原中文操作声明后，Lombok getter/log/构造器的连带编译错误消失。WorkflowHandler 已定向格式化并通过检查。该编译修复不能解释先前已经运行到 HTTP 的超时。

原 `WorkflowDatabaseHttpIntegrationTest` 三项测试均通过，Failures/Errors/Skipped 均为零，命令自然退出 0。仍使用默认 5 秒超时，未削弱断言或增加跳过。首个待办请求 06:30:07.313 收到，08.507 在 apex.jdbc-2 请求连接，08.521 准备 count SQL，08.831 返回，08.861 准备分页 SQL，08.944 返回，08.982 响应对象就绪，09.358 完成 HTTP 200；服务端合计 2045ms。第二次待办请求合计 27ms。请求到连接获取前的 1194ms 包含路由、安全、初始化和调度，不能单独归因于排队；本次没有慢连接或 SQL 错误，历史超时未复现，根因仍未闭环。

新[修复证据](p0-gate-repair-evidence-20261018.json)保留失败及成功日志 SHA-256。首次全仓 `mvnd -T2 -B clean verify` 于 06:35:56 自然退出1、BUILD FAILURE，31项为17成功、1失败、13跳过；system 六个 Handler 中14处重复 OperationLog 导致编译失败，尚未执行 system 测试。日志 `/Users/eric/.cache/apex-p0-full-verify-20261018.log` 的 SHA-256 为 `889de057e0062eac03fe623bb6e3a8c42753a07fe6883e0d462a4f300848abcd`。移除重复单行声明，保留禁止请求/响应捕获的声明及 SSO 回调 LOGIN 分类，六文件定向 Spotless 通过。第二轮于06:49:59自然退出1、BUILD FAILURE，日志 `/Users/eric/.cache/apex-p0-full-verify-repaired-20261018.log` 的 SHA-256 为 `2553d27a9aa2dd0bda2b1bb3f1dc6dcf8a4504c54c5616d12ce5e01845e1e941`。system恢复编译，971文件Spotless及保护的迁移测试均通过；唯一失败为 `WorkflowOperationLogAspectTest` 要求 HTTP 方法/状态元数据而 completeTask 注解关闭相应开关。核对本地切面仅读取方法与状态、不读正文后，仅开启该方法的两个元数据开关，原审计测试6项及格式检查于06:51:54通过，未改断言。第三轮于06:53:59自然退出1、BUILD FAILURE，common DateUtil的import后多余空行触发Spotless；日志 `/Users/eric/.cache/apex-p0-full-verify-audit-repaired-20261018.log` 的SHA-256为 `de48be0aef3971daf809e8b6b9cba62d478395c8cf3912e8b00587bc03b50d04`。仅对DateUtil定向格式化，一文件改变，06:54:55检查成功。上一轮common已通过，不能声称共享工作区源码冻结。第四轮于07:06:36自然退出1，25 SUCCESS、1 FAILURE、5 SKIPPED；按完整插件汇总，Surefire 4647项无失败/错误/跳过，Failsafe 53项无失败/错误、跳过1项（真实Seata TC），workflow-service单测42/集成27及system单测1549/集成14均通过。唯一失败为MonitorMetricsCollector的import顺序，定向格式化并检查于07:08:05通过。第四轮日志 `/Users/eric/.cache/apex-p0-full-verify-date-repaired-20261018.log` 的SHA-256为 `ea80a2349dcd6591645c17ce82e8d598a3d884cba47e4600c7c3558e4e4b86be`；第五轮于07:16:36自然失败，17 SUCCESS、1 FAILURE、13 SKIPPED，system完整通过，但office ApprovalStatus末尾空行触发Spotless；读取时该空行已消失，本代理未修改该文件。第五轮日志 `/Users/eric/.cache/apex-p0-full-verify-monitor-repaired-20261018.log` 的SHA-256为 `4a4ecbab4bda81a02b6ab55924f69e82e2f59539bba08fb4fdd9004a4ed4959f`。随后全仓Spotless预检通过，日志hash为 `d00d6c380151601b5548523ae2f1b01c2c25cf10f5464b3ed7ed7af1c884770b`；第六轮于07:25:44自然失败，24 SUCCESS、1 FAILURE、6 SKIPPED；system、workflow-service、office-service均成功。LicenseGenerator import被格式器判未使用，随后读取源码已有对应注解、定向检查零修改通过，不能归为本代理修复；日志 `/Users/eric/.cache/apex-p0-full-verify-preflight-20261018.log` SHA-256为 `be43edd1b62fd0754e67baea9cbfc26db290bfe6099671039bcead5e928bc1e5`。后续格式预检先后暴露LeakyBucket、ApexBlockingIoProperties、LicenseVersion、ApiCallLogProperties末尾空行，均只定向格式化；全仓格式预检最终通过，全部日志/hash见JSON。第七轮于07:28:38自然失败，9 SUCCESS、1 FAILURE、21 SKIPPED，TokenBlacklistServiceImpl重复构造器阻断编译；读取时显式构造器已不存在，本代理未改该文件，当前源码定向编译于07:30:20成功。第七轮日志 `/Users/eric/.cache/apex-p0-full-verify-format-stable-20261018.log` SHA-256为 `d6d920276cb80925aefa2b06996bd6d0a604b84416661bd2dd703e27999e4ebe`。第八轮于07:33:03自然失败，3 SUCCESS、1 FAILURE、27 SKIPPED，common TokenBucket、SlidingWindow、BasePO新增空行触发Spotless，仅定向格式化并检查通过，保留既有Lombok重构；该轮日志SHA-256为 `23352d22652584cc359b25b2b7618ef8ae974510f9df2fde1ef9e447f626c764`。第九轮于07:35:01自然失败，5 SUCCESS、1 FAILURE、25 SKIPPED，domain RemoteApiException末尾空行触发Spotless，定向格式化通过；日志SHA-256为 `d19d8a12f7b5172d8260749a5365ee68bc523fc52774299265d3339b8cd600b0`。随后预检发现LicenseValidator与ChuanglanProperties/ChuanglanApiException格式问题，均定向格式化通过；全仓格式预检最终成功。第十轮于07:37:57自然失败，7 SUCCESS、1 FAILURE、23 SKIPPED，remote的RemoteRequest.Builder及ApiCallLogFilter构造器编译错误；读取时当前源码已有Lombok构造器注解，未修改源码的定向编译于07:39:11成功。该轮日志SHA-256为 `8ed0398746e83984233dcc1cd2f3eaeaf8f63cdf809a093456bfe7b7e67ea5f8`。第十一轮于07:52:20自然退出1，30 SUCCESS、1 FAILURE、0 SKIPPED；Surefire4790项无失败/错误/跳过，Failsafe300项失败1、错误0、真实TC跳过1。唯一失败为example幂等演示接口返回409，正文明确为异步切面链的注解JoinPointMatch绑定丢失，非重复幂等键。日志 `/Users/eric/.cache/apex-p0-full-verify-remote-confirmed-20261018.log` SHA-256为 `5aa9daab0a381b4d07677e69ae4a6ece2786c5bd3bcda6f3c9fd51e2b852f8a0`。OperationLogAspect改为从最具体目标方法读取注解，避免依赖异步继续调用时的线程绑定；原ExampleCapabilityIntegrationTest七项定向通过，断言未改，日志hash为 `297d646501b853e2aed61d15956a480fc10169157b23eb105f9f5dcc90506f91`。前次定向因BlacklistStatus重复构造器失败，读取时显式构造器已消失，本代理未改实体，历史日志保留。第十二轮于08:10:44自然退出1，18 SUCCESS、2 FAILURE、11 SKIPPED；system原审计单测六项通过，但file两文件与system八文件新增空行触发Spotless。十文件已定向格式化、检查通过，保留用户Lombok重构；该轮日志SHA-256为 `364fcf25d9fed6f3ca9788a5c388e5f44d7d7d2480a1914533fcc04bc09a7bc3`。第十三轮08:15:40自然失败，15 SUCCESS、2 FAILURE、14 SKIPPED：SubscriptionEntity重复无参构造器及office六实体空行格式违规。读取时显式构造器已消失；定向格式通过后编译又遇SubscriptionStatus重复构造器，下一次读取同样已消失，未做实体语义修改。当前源码的system及依赖compile已成功，相关失败和成功日志/hash见JSON；第十三轮日志SHA-256为 `be5df8ae2d333a68310de67765bcecc4d7d76a127fd517b7333356b9f6ff8974`。第十四轮08:27:26自然退出1，19 SUCCESS、2 FAILURE、10 SKIPPED；完整插件汇总Surefire4236项失败/错误/跳过均0，Failsafe25项失败/错误0、真实TC跳过1。auth四文件及system两文件空行格式违规已定向格式化检查通过，保留既有Lombok修改；该轮日志SHA-256为 `c9ce1177106da1ce89897b86b4722d04800110e5099f4b291d4a7c65b9a86a28`。第十五轮 `mvnd -T2 -B clean verify` 于08:51:31 +08:00自然退出0、BUILD SUCCESS，用时19:15，31个Reactor项目全部SUCCESS。完整插件汇总Surefire4790项失败/错误/跳过均0，Failsafe300项失败/错误0、跳过1（缺真实Seata TC环境）；workflow-service单测42/集成27、system单测1549/集成14、office-service单测355/集成1、auth-service单测114/集成179、file-service单测25/集成51均通过。Spotless与PMD各覆盖31项目；JaCoCo覆盖31项目、42次check，38次通过，根项目、services-bom、license-tools及兼容infrastructure因缺执行数据跳过，不能视为这些项目的覆盖率验收。最终日志 `/Users/eric/.cache/apex-p0-full-verify-auth-formatted-20261018.log` SHA-256为 `d8e02918875b7465b44d679550a6dcb9eaf4e7c3296e1120c99ed99cda79ada3`。本次完整构建门禁通过；共享工作区期间存在源码变化，结果对应此次执行而非冻结commit，P0其他验收仍未完成。MySQL 沿用 13317 测试库，密码仅读入内存环境并在退出时 unset，Ryuk 未禁用。

## 验收与未完成项

下表保留门禁修复及09:22实采时点状态；路由分类和依赖采集的最新完成范围与剩余项以文末本轮整合为准。

| 目标 | 当前状态 |
| --- | --- |
| R08/T12.2 配套交付 | 部分完成：工具、测试、清单、方案状态和阶段报告已交付；尚缺完整资产映射 |
| R01/A01/T01 Java 21 | 未执行：Oracle 21路线已选；精确补丁/build、各平台发行物及许可适用性未闭环，未安装或切换 |
| P0 AST/表/API/配置完整清单 | 当前实采2221文件、26301显式声明、258静态路由及139未解析候选、含V62的172迁移/836表事件，五域已刷新；397路由候选与136域/表组合已关联模块/服务候选及源码。旧快照保留为历史。固化数据库CLI已对V62最终system双库真实collect/compare：各96对象、1348列、62条成功history，匹配各自源checksum；对象、列存在性及业务列可空性差异为0。全业务配置138来源已补采；仍缺声明口径确认、业务权威及契约关联、未解析项处置、配置生效/注入前置及双库语义对账，详见下文验收表 |
| P0 有效依赖与外部环境 | 本轮固定Java17/mvnd，system七文件格式通过；原workflow HTTP三项及审计六项定向通过，默认5秒超时和断言未改。前三轮 `-T2 clean verify` 分别因system重复注解、workflow审计元数据及common新增格式违规自然失败，对应问题均已定向处理；第二轮system完整模块通过。第四轮因MonitorMetricsCollector import顺序自然失败，Surefire4647项通过，Failsafe53项失败/错误0、真实TC跳过1项；实际格式问题均已定向处理，第五至十轮失败及源码变化详见本节续验与证据JSON；第十一轮因example异步审计注解绑定失败，定向修复后原七项能力集成通过；第十二轮因file/system十文件空行格式违规自然失败，已定向处理；第十三轮构造器/office格式及第十四轮auth/system格式失败后定向检查通过，第十五轮08:51:31自然退出0、BUILD SUCCESS，31项目全部成功；Surefire4790项零失败/错误/跳过，Failsafe300项零失败/错误、真实TC跳过1，Spotless/PMD全仓检查通过，JaCoCo缺数据范围见续验记录；历史workflow超时和旧mvnd失联根因未证明，共享工作区不能视为冻结快照。Seata真实TC与全业务条件仍未验收，旧失败历史全部保留 |
| R02–R07 业务及隔离 | 未开始实现，不以既有代码或 Mock 认定达标 |
| R09/T12.3 全仓 Javadoc | 已采集显式声明的文档文本与标签名称；覆盖口径、标签完整性与语义验收未完成 |
| R10/T11/T13 迁移与回退 | system 双库新空库修复回归通过，十库 history 与各自源 checksum 一致；用户仅明确授权 29 份历史 SQL 的初始化缺陷修订。system配对V62已修复对象/列存在性及四项业务NOT NULL等漂移，双库测试与真实CLI采集已完成；全部语义验收及存量checksum/部分DDL风险仍未闭环。仅隔离测试负例执行过恢复值后的repair，未执行生产存量升级/repair及上线回退演练 |

本轮用户已明确选择 D01 的 **Oracle 21** 发行版路线；此选择不代表商用许可适用性、精确补丁或兼容性已确认。下一步沿该路线核验发行物与许可前置条件，并补齐 P0 的表/API/声明与部署配置清单，以及可关联执行轮次的依赖和构建证据。D07 在 P2 前确认 Provider 精确版本、身份权威、部署、许可和故障边界；不在现有 WebFlux 服务内擅自混入 Servlet。任何缺证据项均不记为通过，P0 未准出前不进入依赖阶段。

<a id="2026-10-18-全业务配置与部署变量静态补采"></a>

## 2026-10-18 全业务配置与部署变量静态补采

扩展既有 `scripts/ci/collect_gateway_config.py` 至 schema 2，沿用安全路径、节点级YAML、hash/行号、独占创建输出及原网关路由采集。新[时点快照](business-config-evidence-20261018.json)采集于 **2026-10-18 09:08:35 +08:00**（JSON使用UTC），SHA-256为 `03c0eff107b0f538ca1d36388a39316bc19a5024f6fd5236f3fe4bbd6252fd67`；旧 `gateway-config-evidence.json` 保留。本轮未修改生产Java/SQL，未运行mvnd、迁移、安装或Java21验证。

范围由根POM的30个模块发现main application/bootstrap配置（不含test），加本地Nacos YAML、仓库Compose、Containerfile/Dockerfile、`.env.example`及scripts内shell/systemd入口，包含scripts/build。实际有配置的作用域为auth-service、example、file-service、gateway、job-service、masking-service、office-service、system、system-service、workflow-service及共享Nacos。不是30个模块都有配置文件，也不读取远端Nacos、真实.env、env_file、systemd EnvironmentFile或宿主环境。

| 静态资产 | 精确数量与口径 |
| --- | --- |
| 来源 | 138文件，均记录SHA-256与大小，采集后逐一重算匹配 |
| 配置定义 | 1872个标量叶节点；仅名称/位置/profile/默认表达式与字面量存在标志，不输出值 |
| 引用 | 4652次词法引用，环境名去重720；包含脚本裸变量引用，不等于720个外部必填配置 |
| 部署声明 | 1661条：模板235、image ARG13/ENV20、Compose映射530/列表7、shell赋值791/export59、systemd6 |
| 显式注入关系 | 622条：Compose537、image ENV20、shell export59、systemd6；输入变量到目标变量与service/来源，不证明分支执行或子进程实际继承 |
| 模块配置环境映射 | 474个scope/name组合，132有同名service Compose候选，342未解析作用域或注入；system库到system-service、别名和动态服务选择不猜定 |
| 网关路由 | 23条声明，保留独立profile和顺序，不合并有效路由 |
| 未解析记录 | 3703条：shell展开/控制流3239、多行/动态语法403、source35、镜像续行25、systemd EnvironmentFile1；是逐行静态限制记录，非3703个运行故障 |

默认值只记录是否存在fallback；`:?`必填报错不算默认值。变量清单不包含默认值、实际值或敏感内容；既有路由端点allowlist单独保留。shell采用词法候选口径，单引号、heredoc、函数、set -a、命令内赋值、续行与跨文件传播未求值。`.env.example`仅模板，Compose环境声明才是显式容器注入候选。profile激活、配置优先级、relaxed binding、远端配置与运行时路由均未验收。

验证：新增两项业务来源/注入与shell/image元信息回归，先运行8项得到预期2项失败，修复后8项通过；补充快照时点、collector hash及scripts/build覆盖断言后，配置/基础清单/迁移表/数据库CLI四套Python回归共 **38项通过**。一次扩大到全部采集器的发现运行共51项，38项通过、13项Java声明/路由工具测试因固定JDK未激活被前置检查拒绝，未执行其Java解析；未为此重新构建或宣称全套通过。新JSON可解析，138个来源hash匹配；收尾文档链接检查扫描663份Markdown、2782条链接，断链0；`git diff --check`无诊断。对本轮五份交付文件另直接检查行尾空白和末尾换行，覆盖未跟踪文件，全部通过；采集器hash及变量记录无值字段检查通过。

截至配置补采轮次，P0剩余准出项为刷新并绑定当前源码/声明、V62五域迁移和API候选清单；其后本节末的09:22实采已完成文件、候选及模块/服务关联。当前剩余为确认业务权威/可信边界、逐未解析项处置，以及当前依赖树/档位/插件与外部条件、配置优先级/传播/注入前置登记，并冻结可审阅基线范围。已有Java17全仓门禁不重复要求。完整Javadoc语义交付属于P6，存量升级/生产repair/上线回退实施属于P7；P0需明确清单、风险与阶段依赖，不能要求这些后期工作已实施。D01精确发行物/许可仍是Java21切换前置，D02–D05/D07按P2相应前置点、D06按P3/P4确认。P0尚未准出；可继续只读核对BOM/插件、Oracle21发行物与许可材料、CI/容器/部署JDK矩阵及Javadoc口径。

<a id="2026-10-18-当前源码声明路由与五域迁移实采关联"></a>

## 2026-10-18 当前源码声明路由与五域迁移实采关联

本轮先source固定构建环境，运行四个既有采集器；没有重跑Java17全仓门禁，没有修改生产Java或SQL。新时点独立保存为[baseline-20261018.json](baseline-20261018.json)、[declarations-20261018.json](declarations-20261018.json)、[routes-20261018.json](routes-20261018.json)、[tables-20261018.json](tables-20261018.json)，旧快照不覆盖。[关联资产](asset-links-20261018.json)记录09:22:09 +08采集完成时点、六个工具及四份输出hash、当前POM与历史依赖证据hash。HEAD仍为0c506a7ee0f94a07b359edda8ebbab4aac60dab3，baseline记录1139条工作区差异路径；这是工作区时点，不能宣称冻结commit。

| 实采资产 | 数量与可核验关联 |
| --- | --- |
| Java | main1654/test567，共2221文件，26301显式声明；逐文件模块、scope、hash、服务候选；声明通过文件内索引/position回溯 |
| 路由 | 扫描1654个main源、56候选文件，258静态路由/139未解析；全部397项关联最小包围声明，含行、列、UTF-16 offset/end_offset、源hash |
| 五域迁移 | system124、auth28、workflow4、file10、office6，共172；system V1–62、auth V44–57、workflow V1–2、file V1–5、office V1–3，双库版本集合差0 |
| 表事件 | 836：create_table273、alter_table120、create_index441、drop_table2；136域/表组合、112规范化表名，628文本引用候选；每个事件回指迁移文件/行/方言/版本 |
| SQL限制 | 589未解析：动态/过程536、unsupported_ddl27、unsupported_statement26；86个域/版本事件对照中52有差集、34无差集，不是52个语义缺陷 |
| POM/配置 | 根加30模块共31 POM；baseline配置白名单100项，独立业务配置快照仍为138来源，不混淆两者分母 |

服务候选从当前POM直接生产依赖图向下可达得到，排除test/provided，不计算profile条件、可选依赖传播或运行时装配；共享模块可有多个候选，未落入运行服务的工具/示例不虚构唯一权威。表引用扫描生产Java/XML/非迁移SQL，仅记录文本命中位置和hash，可能含注释/字符串，不等于已验证Mapper/SQL调用。迁移归属不等于身份数据最终权威；例如auth与system历史同名表保持分域记录。`v62_null_preflight`是迁移辅助对象，事件清单不能当最终schema；视图及动态DDL继续依赖既有catalog和逐行核查。

未解析精确位置逐项见routes新快照的`unresolved[]`及关联资产的`route_links`（collection=unresolved，index回指原项）；每项均保留源文件、声明、行列、offset范围、技术原因和hash，不猜测运行结果。

| 技术原因 | 数量 | 可验收处置 |
| --- | --- | --- |
| receiver_not_proven_webflux | 51 | 逐项核对receiver类型/同名普通调用，记录排除证据或真实路由补采 |
| unknown_enclosing_prefix | 40 | 追踪helper调用者及所有前缀，不能只把相对路径当完整路径 |
| dynamic_path | 26 | 追踪常量/表达式来源及profile条件，记录候选展开或待运行验证条件 |
| annotation_mapping_unsupported | 7 | 单列注解映射清单及运行模块归属，不算函数式解析结果 |
| gateway_dsl_unsupported | 6 | 与既有配置网关声明按profile/顺序关联，不能直接合并成生效路由 |
| dynamic_or_unsupported_nest | 4 | 核对nest谓词及上下文，并保留无法静态展开的原因 |
| builder_helper_call_unsupported | 3 | 追踪builder辅助调用及前缀传递，列所有调用位置 |
| predicate_unsupported | 1 | 展开组合谓词并保持方法/路径布尔语义 |
| url_handler_mapping_unsupported | 1 | 单列WebSocket握手映射及认证边界，登记运行验证前置 |

当前根POM仍声明Java17、compiler3.13.0、Lombok1.18.36/MapStruct1.5.5.Final/binding0.2.0处理器路径，Surefire/Failsafe3.2.5、JaCoCo0.8.12、Spotless2.43.0、PMD3.19.0/p3c2.1.1。Spotless/PMD/JaCoCo检查绑定verify；Javadoc3.6.3仅pluginManagement且doclint=none/failOnError=false，Enforcer3.4.1同属pluginManagement，只声明Java/Maven版本规则，没有dependencyConvergence/requireUpperBoundDeps门禁。插件声明XML已逐POM保存并记录来源路径，包含局部覆盖声明；未单列profile ID及build/pluginManagement位置上下文，不是effective-pom或实际插件执行证明。generator局部java.version仍17。basic/standard/enterprise保持构建档位独立语义，未运行三个档位的新依赖解析。

31份POM中27份与旧baseline-expanded同hash，变化4份为job starter、masking-service、storage starter、system。旧dependency-evidence.json当前SHA-256为`e1c0b12e949b6e047dbdaa1b890bffaf78aff978cbdc73808e5701247e572ead`，其2026-10-17的31项目成功和35精选坐标/scope作为历史记录保留；原`/tmp/apex-p0-dependency-tree.log`现不可用，不能重新核实日志hash。旧基线POM与dependency:tree也不是原子采集，因此27份同hash只证明声明未变，4份差异证明相关声明证据过期；**全部当前有效树仍标not-revalidated**，尤其JUnit聚合5.12.2与API/engine5.10.2不能概括为统一版本。

| 09:22时点P0剩余（最新状态见文末） | 当时登记的完成验收条件 |
| --- | --- |
| 139路由候选与397项契约关联 | 每项有技术处置/排除证据或精确验证前置，并关联Handler、网关、权限及DTO/OpenAPI；无需在P0编造运行成功 |
| 136域/表权威与访问语义 | 逐域/表确认628文本候选的真实访问路径、tenant/唯一键/历史NULL规则；保留无命中和迁移辅助对象分类；auth四安全表、system历史11对象及OAuth16/sys_user9列、office同步列逐对象确认 |
| 589 SQL未解析与双库差异 | 逐源行分类动态/视图/索引等；system catalog的337类型/1348 generation/242默认值/98列序/14索引multiset给出等价、需改或阶段待验结论；其他四域列出catalog条件 |
| 当前有效依赖与外部条件 | 补可复核完整依赖树/有效POM及hash、档位、scope、覆盖/收敛结论；逐项登记DB/Redis/Nacos/MQ/存储/Provider/TC和容器网络条件，不以35精选坐标替代 |
| 配置与可审阅基线 | 对既有342未解析scope/name和3703词法限制登记传播/profile/优先级/注入前置，确认声明排除口径及基线冻结范围；业务所有者及许可适用性不得由工具猜定 |

完整Javadoc语义及严格doclint实施属于P6；真实旧库升级、生产repair、恢复与上线回退实施属于P7。Java21发行物/build/checksum与组织许可确认是D01切换前置。上述阶段实施不挪入P0；P0仍未准出。

本轮验证：当前shell加载固定Oracle17.0.12-oracle/mvnd2.0.0-rc-3后，四采集器依次成功，`python3 -m unittest discover -s scripts/test -p 'test_collect_*.py'`共51项全部通过（304.839s），包含前轮因环境未激活未执行的13项AST工具测试；新增关联逻辑两项测试先红后绿。五份新JSON均可解析，重新计算关联结果与已保存manifest的资产/工具hash、声明/路由/表关联、POM、插件及依赖证据逐项相同；2493个唯一源hash及397项路由关联复核通过。全仓文档链接检查扫描663份Markdown、2789条链接、断链0；`git diff --check`无诊断，并对本轮九份交付直接检查行尾空白及末尾换行，覆盖未跟踪文件，全部通过。没有重新构建Java、运行服务/迁移或Java21兼容验证。

<a id="2026-10-18-本轮证据整合与当前p0剩余"></a>

## 2026-10-18 本轮证据整合与当前P0剩余

> 本节保留10:08证据整合时点；下方最新“表、配置与依赖处置整合”更新其剩余状态，其中配置与依赖登记已有后续成果。

本轮仅整合[三档有效依赖证据](dependency-effective-evidence-20261018.json)（09:47:04 +08）和[路由处置证据](route-disposition-evidence-20261018-1008.json)（10:08:40 +08），未重新执行Java构建。两份JSON的SHA-256分别为 `ceec550421d6225132633f61576ed31520f0d02d8e9d801886d00be6b815d623`、`39a33cee44e98b789b0e7b5f718d083e75ec8105c23b9d969cf9e1743750392f`。前文未采集/未处置的表述保留为其时点事实，不再代表当前状态；P0仍未准出。

### 路由技术分类已完成

原139项索引唯一且完整，逐项匹配原始候选及处置分类：**139 = 116静态端点 + 23其他分类**。116由常量端点36、helper端点29、SpringDoc关联端点51组成；其他23为客户端契约7、常量嵌套结构2、委托匹配探针1、DSL根5、helper/嵌套结构5、内部路径fallback1、OpenAPI占位1、WebSocket映射1。剩余未知候选为0，不能再列“139项技术分类未完成”。

[新路由快照](routes-20261018-disposition-1018.json)仍扫描1654源/56候选文件，374静态路由 = 原258 + 新116；44条原始未解析项全部在 `fresh_unresolved_reconciliation` 中关联原候选索引及分类，其中helper独立扫描保持未解析不等于调用点未展开。118个上下文端点与116个原候选解析端点分母不同，结构项的文件级关联也不是精确调用图。上述均为parse-only静态证据，不证明运行可达性、鉴权、租户隔离、网关生效或OpenAPI验收；P0继续登记这些契约和运行前置，具体运行验收由所属阶段执行。

### 三档有效模型与依赖采集已完成

固定Oracle17.0.12-oracle、mvnd2.0.0-rc-3下，basic/standard/enterprise各执行effective-pom与verbose dependency:tree，共六条命令自然退出0、BUILD SUCCESS；内嵌Maven为4.0.0-rc-3，属性 `maven.version=3.9.16` 不是运行版本。31份POM采集前后相同且本轮重新计算仍匹配。11份原始文件（环境、执行manifest、三档日志和XML）hash均匹配。

原始日志需区分聚合目标与逐模块执行：三个dependency:tree各31 SUCCESS；三个effective-pom目标各1 SUCCESS、30 SKIPPED，其聚合输出均含31个项目模型。因此不能把证据中的每档 `reactor_success_count=31` 扩写为“六条命令各31 SUCCESS”。三档模型/树已采集完成，不再沿用全部not-revalidated；这不是compile/test/verify成功或依赖收敛通过。

| 档位 | 模型项目数 | 依赖树SUCCESS | 冲突仲裁组/出现次数 |
| --- | --- | --- | --- |
| basic | 31 | 31 | 40 / 126 |
| standard | 31 | 31 | 40 / 129 |
| enterprise | 31 | 31 | 40 / 129 |

standard与enterprise选中坐标及scope集合相同；basic主要将Nacos/Sentinel/Druid相关依赖置为provided，档位差异不代表自动启用运行配置。三档均有JUnit Jupiter聚合包5.12.2与API/Engine5.10.2、Platform Engine1.10.2混用。Enforcer3.4.1仅pluginManagement且只有Maven/JDK规则，未配置dependencyConvergence或requireUpperBoundDeps。40组仲裁是Maven选择记录，不自动判为bug；P0需要逐组登记受影响模块/scope、选中/被覆盖版本、BOM或显式覆盖来源、保留理由/兼容验证前置/后续处置责任，JUnit版本族需单列。是否改依赖及如何建立收敛门禁由证据分析决定，本轮未改POM。

### 来源与工具时点

本轮核验路由5份输入、3份工具、38个源文件，以及依赖11份原始文件、前后各31 POM和7条命令日志，共126次hash比较均匹配（含重复路径）。原139索引/原候选、分类计数、374路由及44关联守恒通过。旧关联资产的六工具中，只有以下两项hash已不代表当前工具：

| 工具 | 09:22旧hash | 当前/10:08证据hash |
| --- | --- | --- |
| collect_java_routes.py | da35cb63c04f49bb38759255608d97c8fae72eb12fe1a7e1b2c303c5da749d5c | 4a0d681d2762b63feafe07e6590561e2de7b99424b4825436813ab77c378799a |
| JavaRouteCollector.java | e72d92ce5deabacb7c6496a380a4efc7684f8c3f34fdb660c392c08698eb189f | a05008721daa41c4e234cf34ab758a8eb608110c3ddc48cbfa2c6a945f858458 |

新增collect_route_dispositions.py的hash为 `47848108cb22ccdf118f14d5ba7f61b01c44ac694172e8d1694c37201498db82`。其余四个旧工具hash仍匹配；原routes、declarations及asset-links文件hash也匹配新处置证据的输入。保留旧工具hash及258/139快照用于追溯当时采集，不覆盖旧证据，也不宣称旧工具hash代表当前实现。共享工作区和HEAD不构成冻结commit。

### 精确剩余及阶段边界

| P0剩余 | 应形成的可审阅结果 |
| --- | --- |
| 表权威与真实消费者 | 对136域/表及628文本引用确认真实Mapper/R2DBC/任务访问或无命中/辅助/历史分类；明确auth四安全表与system历史同名对象、11历史对象、OAuth16/sys_user9列和office同步列的归属及保留关系 |
| tenant、唯一键与历史NULL规则 | 单列workflow业务键租户范围、file历史NULL归属、账号合并冲突等规则及确认责任；不得凭默认值猜测历史归属，不要求P0实施P7回填 |
| 双库语义分类 | 589条SQL未解析逐源行处置；system337类型、1348 generation、242默认值、98列序、14索引multiset差异分类为等价/需改/阶段待验，其他四域登记catalog和真实执行条件，不能把差集数量直接当缺陷 |
| 配置传播、profile、优先级与注入 | 对342个未解析scope/name、3703词法限制登记跨文件source/export、动态服务选择、profile激活、Spring/Nacos/Compose优先级及实际注入前置；同名候选不代表有效注入，静态未关联不代表运行缺配置 |
| 当前依赖具体处置登记 | 三档模型/树采集已完成；逐40组仲裁、JUnit混用、basic scope差异及插件门禁缺口形成模块/来源/理由/验证前置/责任登记，不以仲裁数量自动要求改版本 |
| 契约、分母与可追溯基线 | 原139技术分类完成；继续确认Handler、网关、权限、tenant及DTO/OpenAPI关联和所属阶段运行前置，确认显式声明排除口径与工作区审阅范围；登记DB/Redis/Nacos/MQ/存储/Provider/TC及网络可用条件 |

08:51:31结束的Java17第十五轮31项目成功、Surefire4790/Failsafe300（真实TC跳过1）仅证明当时执行，不能覆盖本轮工具变动后的状态，本轮不重复Java构建。D01精确Oracle21发行物/build/checksum及组织许可是切换前置，D02–D07按原方案相应阶段确认；完整Javadoc/doclint语义交付属P6，真实存量升级、生产repair、备份恢复及上线回退实施属P7。P0登记风险、责任及前置，不将P6/P7最终实施验收扩大为P0门槛。

本轮文档验证：目录内36份JSON全部可解析；来源hash、路由关联及三档XML/日志计数复算通过。全仓链接检查扫描663份Markdown、2795条链接、0断链；`git diff --check`无诊断。两份目标文档原为未跟踪文件，另以 `/dev/null` 为基准执行 `git diff --no-index --check`，并直接检查行尾空白及末尾换行。本轮仅编辑这两份既有文档，未修改生产源码、SQL或POM，未构建Java、提交或推送。

<a id="2026-10-18-表配置与依赖处置整合"></a>

## 2026-10-18 表、配置与依赖处置整合

本节接续10:08记录，整合工作区已有的[表来源复核快照](table-source-facts-20261018-reviewed.json)、[表验证记录](table-source-facts-20261018-validation.json)、[配置传播证据](config-propagation-evidence-20261018-1208.json)和[依赖处置证据](dependency-disposition-evidence-20261018-1208.json)。文件名1208是产物标识，不作为采集时刻；配置、依赖JSON内时间分别为02:45:06、02:47:33 UTC（10:45:06、10:47:33 +08），表快照为10:48:49 +08。前文历史数字及检查结果保持原时点，当前状态以本节为准。**P0仍未准出**。

### 核验后的成果与边界

表资产为**136域/表组合、112规范化表名**；103有源码实体映射、33无映射，合计136；另34项无通用持久化绑定，属于不同口径，不能用34替代33。859物理域关联对应628去重表/源行候选（610去重源行）；872词法调用关联不是生产调用图，143次SQL字符串关联仍待调用路径复核，264次注释/非SQL分类不是无消费证明。33无映射项不能据此删除。权威、条件Bean、显式SQL、外部消费者和实际tenant保护仍需审阅。

五域双库差异复算为**3283 = 2117局部方言表示 + 1162需业务/运行确认 + 4缺陷候选**。局部表示分类不证明整列、整表或业务语义等价。4项均为office可空性差异：`office_calendar_event.is_all_day/is_recurring`、`office_document_info.is_archived/is_folder`，需所有者确认预期NULL契约；workflow定义与实例两个业务唯一键不包含tenant_id，需确认跨租户同键规则。这些静态事实不能直接判为漏洞。**589条SQL未解析、139项差异无语句级精确关联**仍逐项保留，规则分类不替代源SQL语义确认。system原始差异数量和NULL/default值没有被归一化覆盖。

配置已登记**474个scope/name组合 = 472有默认 + 1宿主继承候选 + 1静态未知**。后两项分别为apex-system的REDIS_PASSWORD、shared_nacos的DB_HOST_PROD；所有组合均未证明运行注入。342未解析scope/name和3703词法限制为旧快照口径，不与474直接相减，也不能继续把它们整体写成尚未登记。默认值可能仅覆盖部分声明，仍需核验必填引用、激活profile及生产值策略。`app.sh`在有效APEX_ENV_FILE下source并启用自动export，且在后台start（216行）和前台console（309行）**显式传递SPRING_PROFILES_ACTIVE**，不是只靠宿主继承。跨脚本、动态服务选择、Spring/Nacos/Compose优先级及最终部署满足仍待验证。

三档依赖各**40组**已有选中/覆盖版本、模块/scope、有效管理来源（无管理项保持null）、历史选中记录、风险/后续处置、责任角色和兼容验证前置登记；basic126次、standard/enterprise各129次。登记处置为后续依赖收敛评审，具体人员仍待指定；不应再写“40组尚未登记”，也不等于逐组已批准保留、兼容已通过或依赖收敛通过。JUnit版本族混用、basic provided差异及Enforcer缺口保持单列；本轮未改POM或运行Maven。

### 哈希与工具代表性

以下是本轮实际计算的产物SHA-256：

| 文件 | SHA-256 |
| --- | --- |
| table-source-facts-20261018-reviewed.json | 60d321f7be81b078027f2dea60cf5451487bf1cf1557430da01371171f5ed17a |
| table-source-facts-20261018-validation.json | 2ec8634a7e15a5b2e67ff51f94d135fb82da34db84617eef9d4945407f916e43 |
| config-propagation-evidence-20261018-1208.json | 023617838875cf17c389bcdea1de95ceb444473b7d4af387bab2faedf8c0ac95 |
| dependency-disposition-evidence-20261018-1208.json | 607aac7ce816929309a7bf58ad1cadf28145ce06086ac380d2cb58e6b857ac6f |

编辑文档前重新计算表快照1896份来源均匹配；其1203条历史输入比较记录均为匹配。配置138份来源及collector、依赖11原始文件/31 POM/输入证据/collector均匹配。表快照把report与主方案也纳入来源，因此本次文档更新后这两项必然漂移；这是后续整合产生的新时点，不能继续宣称1896份与最终工作区全部相同，也不回写旧快照hash制造闭环。

本轮使用的工具为[表事实采集器](../../../../scripts/ci/source-archive/scripts/ci/collect_table_facts.py.source)（SHA-256 `d78f61c19de3fdcdf35a118bf9bbee2f60256db2737bf753e6f8d09c72229c05`）、配置采集器（`scripts/ci/java/src/main/java/GatewayConfig.java`，SHA-256 `b68e0398fc97bad07443c36b5d0fe175c29b70f696f95a086d51693f174de5a3`）和依赖处置采集器（`scripts/ci/java/src/main/java/P0DependencyDisposition.java`，SHA-256 `734930af7f2cd9da680330d05a157f8292413fa003ec462ebc603b9a03dcf335`）；后两者属临时工具工程 `scripts/ci/java`，从未入库、现已移除，上述哈希仅作历史记录，源码已不可取回。旧asset-links只记录六工具，其中路由两工具已变，其他四项仍匹配；其工具清单不覆盖后来新增的表/依赖处置及配置传播能力，不能作为当前全部工具的冻结证明。首轮followup、reviewed、validation及所有历史工具hash保留原样，共享工作区与HEAD仍不构成冻结快照。

### 当前P0未验收逐项工作

| 工作 | 可审阅的P0结果及责任前置 |
| --- | --- |
| 表权威与消费链 | 各域维护人确认136组合的真实消费者、33无映射与34无通用绑定项；重点确认auth四安全表/system历史同名对象、11历史对象、OAuth16/sys_user9列和office同步列的归属、保留与迁移责任 |
| SQL与差异闭环 | 数据库及各域维护人逐项处置589未解析SQL、139无精确关联差异；审阅1162需确认项及office4候选的NULL契约，补源行/理由/责任/后续验收阶段，不能只给总数 |
| tenant与历史规则 | 业务和安全责任人确认workflow两个唯一键范围、file历史NULL所有权、账号合并冲突；核对实际Bean/数据源/SQL保护边界，登记P2/P4/P7前置，不在P0实施回填 |
| 配置有效性前置 | 部署及模块维护人审阅474组合的必填引用、profile、动态选择、source/export及优先级；重点处理REDIS_PASSWORD继承与DB_HOST_PROD未知，登记实际注入验证环境和责任 |
| 依赖登记验收 | 依赖治理及模块维护人审阅每档40组已有处置、指定具体责任人，确认JUnit/scope/插件缺口的理由与后续验证阶段；升级与收敛门禁实施须独立评审 |
| 契约与基线 | 139路由技术分类已完成，继续确认Handler/网关/权限/tenant/DTO/OpenAPI关联、声明排除分母及运行前置；审阅工作区范围并登记DB/Redis/Nacos/MQ/存储/Provider/TC和网络条件 |
| D决策台账 | 为D01–D07明确责任人、确认记录及阶段阻断点；未确认项保持待决，不用整体方案批准替代独立决策 |

最优先可执行项：从office4项NULL候选、workflow两个唯一键、REDIS_PASSWORD/DB_HOST_PROD两项开始，由相应所有者给出源行、预期规则、责任人及后续验证入口；随后按589/139/1162清单逐项分派，避免再次重复采集已完成的分母与依赖登记。

D01精确Oracle21补丁/build、目标OS/架构、checksum/digest、SDKMAN渠道及组织用途许可须在P1切换前确认；D02权威/个人租户在P2数据模型前，D03会话/撤权在P2实现前，D04合并/历史归属在回填合并前，D05状态/宽限在P2签发契约前并于P4/P5全链验证，D06档位/许可在P3/P4前，D07精确Provider版本、可行性、运行栈、权威/部署/许可/故障边界在P2协议实施前。P0登记待决事项与前置，不将全部后期运行验收前移；P6完整Javadoc、P7存量迁移/生产repair/上线回退仍属其原阶段。

### 本次验证范围

本次实际重跑两组纯Python回归：`python3 -m unittest scripts/test/test_collect_table_facts.py scripts/test/test_collect_database_schema.py scripts/test/test_collect_migration_tables.py`为29项通过；`python3 -m unittest scripts/test/test_collect_gateway_config.py scripts/test/test_collect_p0_dependency_disposition.py`为11项通过。前者与validation中的历史29项记录独立，后者不是validation声明的测试。未执行Java构建、数据库查询、服务启动或生产修改，未提交或推送。文档更新后41份JSON全部可解析；全仓链接检查663份Markdown、2807条链接、0断链。`git diff --check`及两份未跟踪文档相对`/dev/null`的`git diff --no-index --check`通过，已检查行尾空白及末尾换行。表来源最终漂移恰为本报告与主方案两项，其他1894项保持匹配。

<a id="2026-10-18-officeworkflow与配置已实施成果整合"></a>

## 2026-10-18 office、workflow与配置已实施成果整合

本节接续17:00的[office/workflow修复证据](office-workflow-repair-evidence-20261018-1700.json)与16:41:20的[配置前置验证证据](config-preflight-verification-20261018-1641.json)，更新前节的待确认状态。旧章节、历史hash及其执行时点保留，最新状态以本节为准。**P0仍未准出，Java 21尚未切换**。本次只整合文档并静态复核，未重跑Java构建或配置回归，以下测试结果属于两份证据记录的原执行。

### 已实施决策及静态复核

office四项NULL契约已落实为false：`office_calendar_event.is_all_day/is_recurring`与`office_document_info.is_folder/is_archived`。两库各追加`V4__normalize_office_boolean_flags.sql`，只回填这四列的NULL并设置`NOT NULL DEFAULT 0`；保留数值布尔类型、原true/false，不增加0/1 CHECK或清洗非0/1历史值。包含未跟踪文件扫描后，office两库均V1–V4、workflow两库均V1–V2，无重复版本；本轮未改历史迁移或workflow索引。V4未增加或重命名`update_time/updated_at`列，也未显式回填审计时间；不将此表述为真实旧库触发器行为已经验收。

两个converter的四标志均在实体→PO、PO→实体双向将null归false；创建日历时全天省略归false、重复标志为false，创建文件夹持久化`is_folder=true`、归档为false。更新链已核对DTO可选Boolean、Handler传参、新八参数服务重载及原七参数兼容重载：`isAllDay=null`保留，true/false显式更新。持久化仍沿用converter、`updateById`及PO的`update_time`填充声明。Handler实际返回`Result<Boolean>`，OpenAPI使用`CalendarBooleanResult`，与服务布尔结果一致。对应测试覆盖转换null/false/true、创建默认、文件夹标志及更新传参。未发现需最小修正的明显错误，因此本次未改源码、SQL、POM或测试。外部`CalendarPort`没有全天/重复参数，不能据本地更新宣称外部办公平台全天同步通过。

workflow保留`(workflow_code, version, deleted)`与`(business_key, deleted)`全局唯一，COMPLETED/REJECTED/TERMINATED终态不释放业务键。本次静态复核仍见只有分页拦截器、定义插入未赋tenant、按ID及业务键访问未完整过滤tenant、身份中继允许缺tenant；部分实例写入带tenant不能证明闭环。当前兼容规则已有决策及约束验证，不再列为本轮待决定是否放宽的键；tenant闭环及未来键范围变更仍需所属阶段独立实施和验收，**全局唯一测试不等于租户隔离安全通过**。

### 原执行证据及本次哈希核验

| 原执行 | 结果与适用范围 |
| --- | --- |
| office定向测试 | 43项，Failures 0 / Errors 0 / Skipped 0：converter 6、日历服务7、文档服务2、Handler含嵌套21、迁移静态5、真实双库参数化2 |
| 真实MySQL/PostgreSQL | V3→V4恰执行一项、validate成功、再次migrate为0；MySQL验证NULL回填，PostgreSQL V3本已非空，以原0验证兼容；验证保留true、默认0、拒绝NULL、1→0，以及workflow跨tenant同键和终态仍拒绝复用；临时库/角色finally清理并复查 |
| Spotless | 两个office模块`spotless:check`退出0；不等于全仓格式检查 |
| 配置定向测试 | 10项，40.266秒，Failures 0 / Errors 0 / Skipped 0，退出0；含真实`podman-compose config`四场景及模拟值不泄漏断言，不执行up/build/run |

43项命令显式启用`-Dapex.office.real-db=true`并跳过JaCoCo；未覆盖全仓clean verify、PMD、完整Failsafe、JaCoCo、完整服务启动、生产升级或外部平台端到端。默认测试不启用真实双库项，不能将跳过计为通过。此前08:51:31完整门禁仍只代表其历史时点，不能覆盖这些后续改动。

本次重新计算office证据8项及配置证据5项来源，**13/13与当前源码/SQL/测试/配置匹配**，未观察到这13项的格式化后漂移；配置保留基线hash亦匹配（`8fdfcc68c2cfd29aa3040078a9575719b1a2232b80fdc70047733670723b8c6b`）。这不是所有测试、工具或共享工作区的冻结证明。旧表快照仍包含本报告与主方案，后续文档时点与旧hash不同属已登记漂移，本次不重写旧证据。

| 本次核验产物 | SHA-256 |
| --- | --- |
| office-workflow-repair-evidence-20261018-1700.json | a66a41d9ca105549c35c9362d8afa5e69b15370c70084cb53664dd2270190389 |
| config-preflight-verification-20261018-1641.json | 9a028b9de542d396398b5789592bca8706bcfb385867b56ff6ea5ff1c8bbe1dc |

### 配置交付与具体剩余

REDIS_PASSWORD已有拒绝空白、模板回退及未解析占位符的离线前置；完整Compose检查只对声明该变量的渲染环境校验，不误要求仅含密钥的`security-key-check`提供Redis。DB_HOST_PROD仍是Nacos模板五处无默认引用，DB_HOST不作别名；独立target只证明执行进程变量具备，未证明模板发布或服务消费。两项已交付失败即停能力，不再写成工具尚未具备；实际责任人、使用状态和运行注入仍未验。凭据由部署方通过受控环境或批准的文件渠道注入，无需通过聊天提供secret。

在仓库根目录、相应进程环境已安全注入变量后，可直接使用：

```bash
python3 -B scripts/utils/local_deployment.py preflight --target compose
python3 -B scripts/utils/local_deployment.py preflight --target nacos-datasource-prod
```

完整渲染检查使用`python3 -B scripts/utils/local_deployment.py compose --env-file <approved-env-file>`，将占位路径替换为批准的环境文件；宿主环境可覆盖文件值，检查不自动加载工作目录`.env`。Nacos维护人先登记模板使用或遗留状态；如使用，明确namespace/group/Data ID、发布入口、消费绑定及注入端，并把preflight成功接入实际发布流程。这些命令不联网验证Redis认证、主机可达性或最终Spring属性来源。

P0具体剩余沿用已有清单，不新增泛化任务：136域/表的权威与真实消费链（33无映射、另34无通用绑定不能删除）；589未解析SQL、139无精确关联差异及1162需确认项逐项审阅；workflow tenant保护、file历史NULL归属及账号冲突的责任和阶段前置；474配置组合的有效性、两变量实际使用/责任及运行注入；三档各40组依赖已有处置的具体责任人与审阅（含JUnit/scope/Enforcer）；Handler/网关/权限/tenant/DTO/OpenAPI关联、声明分母、可审阅工作区及外部环境台账。旧3283差异快照保留，4项office候选已有本轮修复证据，不能再整体列为待NULL决策，也不据此重写旧catalog计数。D01–D07仍按原阶段确认，P6完整Javadoc、P7存量升级/生产repair/恢复与回退不前移为本轮实施验收。

本次检查：证据目录46份JSON全部可解析；13项来源及配置基线hash匹配，两指定证据文件hash保持上表值。全仓文档链接检查扫描663份Markdown、2812条链接、0断链；`git diff --check`退出0。两份目标文档仍为未跟踪文件，分别相对`/dev/null`执行`git diff --no-index --check`无空白诊断（退出1表示文件内容存在差异，不记作退出0）；直接检查行尾空白为0、末尾换行均存在。仅定向证据整合，不是全仓验收。未提交、推送或恢复已删临时脚本。

<a id="2026-10-18-最新完整门禁与p0收口状态"></a>

## 2026-10-18 最新完整门禁与P0收口状态

本节接续前节，按证据内部时间更新当前状态，旧失败和局部成功记录全部保留。**Java 17 全仓完整门禁当前通过，P0整体仍未准出，Java 21未切换**。本轮仅核验保存证据并更新本报告与主方案，未重跑Java、修改生产代码或脚本、恢复用户删除的临时文件、提交或推送。

### 三轮门禁的实际时间与终态

下列时刻均为2026-10-18、+08:00，按运行起止时间排序，文件名不作为先后依据。

| 证据 | 实际运行时间；记录时间 | 结果及边界 |
| --- | --- | --- |
| [1810证据](gate-evidence-20261018-1810.json) | 17:41:27–17:46:19；17:49:49 | `mvnd -T2 -B clean verify`客户端自然退出1，daemon失联；无最终Reactor Summary或Maven BUILD终态，不能写成Maven BUILD FAILURE |
| [1758证据](gate-evidence-20261018-1758.json) | 17:58:04–17:58:10；17:59 | noDaemon同JVM初始化因缺少`mvnd.java.home`失败，退出100；未进入reactor、未执行clean，本轮测试及质量门禁未执行 |
| [1803证据](gate-evidence-20261018-1803.json) | 18:02:15–18:21:52；18:22:53 | 固定Oracle JDK `17.0.12-oracle`、mvnd `2.0.0-rc-3`，native客户端及新daemon串行完整构建；自然退出0、BUILD SUCCESS，31/31 reactor项目成功 |

成功命令在当前shell加载`source scripts/build/init-build-env.sh`，检查/停止旧daemon并核对版本后执行`mvnd -Dmvnd.java.home="$JAVA_HOME" -T1 -B clean verify`，没有测试或质量检查跳过参数。成功不能倒推此前daemon失联的底层根因；1810轮次的`StaleAddressException / No message received within 3000ms`仍只证明通信失联，OOM等原因未确认。1758初始化失败是另一轮独立故障。

### 当前通过范围与保留限制

| 项目 | 最新完整运行结果 |
| --- | --- |
| Surefire | 4749 tests，Failures 0 / Errors 0 / Skipped 0 |
| Failsafe | 301 tests，Failures 0 / Errors 0 / Skipped 2 |
| 合计 | 5050 tests，Failures 0 / Errors 0 / Skipped 2；实际执行5048项 |
| 报告归属 | 本轮时间窗口内1157份XML；排除2份更早的退役`apex-interfaces`报告，不混入旧运行 |
| Spotless / PMD / Failsafe verify | 各31次check或verify；PMD内部分析告警261次，不能写成无异常或完整分析无遗漏 |
| JaCoCo | report/check各42次，38次check通过；根项目、services-bom、license-tools及兼容infrastructure四项目缺执行数据跳过，不是全模块均有覆盖率数据 |
| 架构/边界测试 | 证据按文件名选取的46项，失败/错误/跳过均0；不扩大为全仓所有架构断言覆盖 |

两项跳过分别为`OfficeWorkflowBooleanIntegrationTest.validatesOfficeUpgradeAndGlobalWorkflowKeys(String)`（缺少系统属性`apex.office.real-db`）及`SeataAtIntegrationTest.commitsAndRollsBackAcrossTwoHttpServicesAndDatabases`（缺少环境变量`APEX_SEATA_IT`）。这两项真实环境场景未在本次默认完整门禁执行，不能记为通过。前节43项定向测试显式启用office真实双库，属于此前另一个时点，不能合并成此次完整门禁的双库通过或抹掉本次skip。

PMD告警原文为`aktStatus is NULL: maximum Iterations exceeded, abort 0`，出现261次；插件check及reactor成功与内部分析限制并列保留。本次完整生命周期通过，不据此宣称Java 21兼容、统一认证/租户改造或全部外部环境验收完成。

### SQL保存产物核实及当前优先剩余

已先查找table/SQL相关证据与脚本，再检查Git状态和定向diff。`collect_table_facts.py`、`collect_migration_tables.py`及对应测试均为未跟踪文件，因此空diff不能证明没有实现变化。当前工具已有细分SQL处置逻辑，但没有找到可核验的新保存产物，不能从工具变化或上轮任务未返回推断任务完成。

[reviewed快照](table-source-facts-20261018-reviewed.json)内部采集时刻仍为10:48:49 +08:00，589条SQL处置全部是`unknown_requires_statement_semantic_review`；[validation](table-source-facts-20261018-validation.json)引用的reviewed hash匹配。**589未解析SQL、139无语句级精确关联差异、1162需确认项均仍是旧时点数据，不是本轮重新采集的当前计数，也未证明减少或清零**。旧3283差异及其4项office候选保留历史，office四标志已有后续修复证据，workflow全局键已有兼容决策，不再列为尚无规则。

P0最优先完成已有表/SQL清单的逐项审阅、处置保存与关联复核，并明确136域/表权威、真实消费者及责任人；静态目标识别不能替代语义验证。其余沿用既有范围：tenant/历史NULL/账号冲突的责任与阶段前置、474配置组合及两变量使用/注入前置、三档各40组依赖已有处置的责任与审阅、契约关联/声明分母/工作区与外部环境台账。全仓完整门禁已改为当前通过，不再列为当前失败阻塞；D01–D07仍按所属阶段确认，P6完整Javadoc及P7存量升级/生产repair/上线回退实施不扩为P0门槛。

### 本轮证据复核

本轮已确认P0目录50份JSON均可解析；成功轮次原始日志SHA-256匹配，1157份本轮XML逐份hash匹配。2537个选定路径的before/after清单相同，canonical JSON manifest SHA-256均为`7b5444ec57c328c8884a001c83856fe21663c78b7f959a8be7b8e54baf8cbed1`；本轮逐文件复核当前内容0漂移。该结论只覆盖选定2537路径，不表示全仓冻结，也不覆盖随后本次文档编辑；旧表快照中的两份文档hash继续保留为历史。

| 本轮核验文件 | SHA-256 |
| --- | --- |
| gate-evidence-20261018-1803.json | 6c61e8af41185a53be99dec71603f56100f3339120064ce7be1fd9041d4555e0 |
| gate-evidence-20261018-1758.json | d769f58469c9397aeaf48941e3d0ea8b3f76fefbdd2e25dd18b04510913fb577 |
| gate-evidence-20261018-1810.json | a740916e7e7e6beb78796547d9e1c107dbd559feb51758d9add894641545fc80 |
| 成功轮次verify.log | 160a207ee43b10d8b0de5932a7034e37c65dec5fcdabf8c98ac9e849d4874c74 |
| table-source-facts-20261018-reviewed.json | 60d321f7be81b078027f2dea60cf5451487bf1cf1557430da01371171f5ed17a |
| table-source-facts-20261018-validation.json | 2ec8634a7e15a5b2e67ff51f94d135fb82da34db84617eef9d4945407f916e43 |

当前`collect_table_facts.py`的hash为`7d19931097c43afcf91999932ee0244acf11412ca02f48a48341971931152124`，仅记录已读实现的时点，不作为新SQL证据已生成的证明。以上为保存产物复核，不是本轮重新执行Java门禁。

本轮文档校验：`bash scripts/ci/check-doc-links.sh`退出0，扫描663份Markdown、2822条链接、0断链（仅本地路径及锚点，不验证外部URL）。`git diff --check`退出0；两份目标文档为未跟踪文件，另逐份执行`git diff --no-index --check /dev/null <文档>`，均无空白诊断，退出1表示文件存在差异，未误记为退出0。审阅本轮编辑范围仅为两份文档的页首状态与最新补充，历史证据及失败记录未被覆盖。

<a id="2026-10-18-sql逐项来源与表权威最新证据整合"></a>

## 2026-10-18 SQL逐项来源与表权威最新证据整合

本节接续并更新前节“未找到新保存产物”的历史状态。现已读取[逐项来源证据](table-source-facts-20261018-itemized-1905.json)及其[validation](table-source-facts-20261018-itemized-1905-validation.json)、[权威静态登记](table-authority-20261018-1935.json)及其[validation](table-authority-20261018-1935-validation.json)。内部采集时刻分别为19:04:30、19:11:45 +08:00，1935等文件名不代替内部时间。**P0仍未准出**；本次仅核验并整合文档，未执行Java构建、Python测试、数据库查询或SQL，未改生产代码/脚本、恢复已删cache或临时文件、提交或推送。

### 旧分母保留与当前逐项进展

| 口径 | 已保存且本次复核的事实 | 尚未证明的内容 |
| --- | --- | --- |
| 旧589项SQL | 121 dynamic_sql + 465过程/预处理控制 + 3静态DDL目标；589个稳定ID与源位置、语句引用保留，类型未知0 | 586项仍需运行/语义审阅；另3项仅识别静态DDL目标，全部589项semantic_verified=false，类型未知0不表示语义已验 |
| 3项未绑定控制 | system MySQL V14第47–49行，old-sql-0142/0143/0144：PREPARE stmt FROM @sql、EXECUTE stmt、DEALLOCATE PREPARE stmt | 属于上述586项，需追溯赋值、条件及执行范围，不能因语句引用可解析而当作已绑定 |
| 旧139项差异 | 64项来源关联 = 47表列token/条件字符串/过程体候选 + 17迁移版本语句关联（包含DML）；另75项为Flyway自管对象、无仓库DDL，未解释来源缺口0 | 不是139项均有精确DDL绑定，也不是64项语义通过；全部139项semantic_verified=false |
| 当前语句2120 | 843静态DDL目标 + 465过程控制 + 358注释DDL + 333数据DML + 121动态SQL | 语句分类不证明执行或双库等价；office V4新增12项（8 DML、4 DDL目标、parser issues 0）单列，不混入旧589/139 |
| 旧双库差异3283 | category/object/mysql/postgresql原值逐项保留，复用历史测试库快照，本次未查询数据库 | 2117局部方言表示、1162需确认、4旧office候选仍是历史分类；office四标志已有后续V4修复，workflow全局键已有兼容规则，不再泛化为未决策 |

### 136域/表的权威静态登记及边界

登记仍为136域/表组合、112规范化表名；33无实体映射与34无通用持久化绑定是不同统计口径，不能相减或据此判定废表。跨域同名为21个表名、45个域/表组合。

| 登记结论 | 数量 | 证据边界或后续动作 |
| --- | ---: | --- |
| 静态服务边界 | 18 | 依据已审阅装配规则分配，不能写成18项实体上游调用均经逐项人工审阅 |
| 显式SQL消费者 | 6 | 仅证明代码定义/直接调用；Outbox两个物理域仍需核验有效条件装配、本地数据源与实际执行 |
| 框架本地条件 | 5 | 需按目标profile验证框架启用、连接及本地生命周期 |
| 迁移生命周期 | 2 | 不作为现行运行消费者证据 |
| 历史复制保留待定 | 19 | 逐项确认保留/迁移策略、所有者及历史数据处置前置 |
| 消费者与迁移域错位 | 2 | system.jwt_keys、system.mfa_config的消费者在auth；需核验实际数据源和归属迁移。oauth2_registered_client及oauth2_authorization_consent映射在system，不误归auth |
| 候选待验证 | 84 | 由绑定source追溯仓储上游调用、Bean、数据源及tenant条件 |

18+6+5+2+19+2+84=136是静态登记分区，**不是136项或18+6项实际运行通过**。租户条件、唯一键、历史NULL与账号冲突语义仍需各域所有者审阅，不能用登记覆盖率代替验收。

### Hash复核、原执行记录与时点

| 保存产物 | 本次重算SHA-256 |
| --- | --- |
| table-source-facts-20261018-itemized-1905.json | 8f1402d62caf444d2d7343aa1b8f69ee6031e788dcf9c6d827eaf80d33eed254 |
| table-source-facts-20261018-itemized-1905-validation.json | 1e21ab26b36ca39788b2baaaf7ef9f58cd873860c6d18460eb186510d87bc644 |
| table-authority-20261018-1935.json | 87dc85adcbfe87c44c47442b0df3a684788e2cc387660f15d415bdf7a3c8b378 |
| table-authority-20261018-1935-validation.json | 639b0844f6eb4ac8be1ea2ac1a76fa36a17d88d659d5d9154b421450b3a34963 |

编辑前重算逐项证据1901份当前来源hash无漂移、2120条语句offset切片和SHA-256一致；权威登记1569处引用范围、313个唯一来源的文件及范围hash匹配，六份输入hash匹配。旧1896来源对当前复核仍有24项漂移；逐项证据另有1203次历史输入比较，其中UserEntity/MenuEntity两项不匹配。二者比较集合不同，不能合并或声称旧来源全部无漂移。1901无漂移仅为当前采集局部；其中包含report与主方案，本次文档编辑后这两项将形成后续漂移，旧hash不回写。

38项及30项Python测试通过分别来自两份validation的**原执行记录**，本次没有复跑，不能相加包装为本轮68项测试。Java最新已执行时点仍为18:21:52完整门禁31/31成功（18:22:53记录），5050 tests、0失败/错误、2跳过及PMD261次内部告警全部保留；本次文档整合不刷新其运行时点或扩大环境覆盖。

### 当前精确优先剩余

1. 优先逐项处置system.jwt_keys/mfa_config两项错位、19项历史复制及84项候选，记录具体责任人、调用入口、目标profile、实际数据源和验证阶段；复核6项SQL尤其两个Outbox物理域的条件装配，不重复采集136/112分母。
2. 先追溯V14三项未绑定控制，再按已有ID审阅586项动态/过程控制的条件与执行语义；3项静态DDL仅目标识别。复核47候选、17迁移语句和75 Flyway自管来源归类的适用性，不再列“589类型全未知/139全部无来源”，也不将来源关联当语义验收。
3. 在旧3283快照上逐项审阅1162需确认项及tenant/唯一键/历史NULL/账号冲突前置，关联office V4已有修复、workflow全局键兼容决策；新库、存量升级与生产执行证据分别登记，不重写旧差异。
4. 继续补474配置组合有效性与REDIS_PASSWORD/DB_HOST_PROD实际使用、注入及责任前置，三档各40组依赖处置的具体责任与审阅，路由/权限/tenant/DTO/OpenAPI契约关联、声明分母、工作区和外部环境台账。完整Java门禁不再列失败阻塞；D01–D07按既定阶段确认，P6完整Javadoc和P7生产repair/上线回退实施不前移为P0门槛。

本次文档核验：P0目录54份JSON均可解析，上述统计、语句切片及hash断言通过。编辑后1901来源与权威六份输入的漂移均精确限于本报告和主方案两份文档，符合后续编辑时点，其他来源未见新增漂移。`bash scripts/ci/check-doc-links.sh`退出0，扫描663份Markdown、2831条链接、0断链（限本地路径及锚点）。`git diff --check`退出0；两份目标文档为未跟踪文件，各自`git diff --no-index --check /dev/null <文档>`无空白诊断、退出1仅表示存在文件差异。编辑范围仅为两份既有文档的页首与新增章节，历史内容、JSON、生产代码和脚本未改。

<a id="2026-10-18-消费者边界与mfa-v58后续收口"></a>

## 2026-10-18 消费者边界与MFA V58后续收口

本节接续[消费者边界followup](table-consumer-boundary-20261018-followup.json)、[静态validation](table-consumer-boundary-20261018-followup-validation.json)和[20:03 MFA修复证据](auth-mfa-migration-20261018-2003-evidence.json)，更新上节的优先剩余，不回写历史分类或旧hash。边界采集为19:44:29 +08:00，早于MFA修复；其“auth无mfa_config迁移”断言仅适用于当时扫描，不能作为当前缺表结论。

### 21项边界的当前解释

| 项数 | 当前事实与剩余责任 |
| --- | --- |
| 18 | workflow/office业务库未进入system生产依赖闭包，组件和Mapper扫描也排除相应业务包；关闭“system需补这些实体/仓库”的静态误报。system历史数据保留、迁移、归档或清理仍需责任人与对账/回退方案。 |
| 1 | system OAuth2历史表未发现实际SQL消费者；auth的显式JDBC消费者和独立迁移不能证明system副本仍在运行。 |
| 1 | jwt_keys仓库虽在auth扫描范围，未证实活跃上游；签发材料链使用Redis轮换服务，不能据此宣称当前签发链缺SQL表。闲置仓库用途与system存量责任待定。 |
| 1 | MFA Router→Handler→Service→R2DBC仓储活跃链成立；auth双库新增V58已修复初始化缺口，不再列为未修复缺表。 |

这21项是旧2错位+19历史复制的后续解释，不构成21项运行验收通过；136/112旧登记仍是旧分母，未重新采集为含V58的新全量清单。84候选、6项SQL（尤其两个Outbox物理域）的条件装配、目标数据源及责任仍待验证。

### V58实现、测试与生产边界

已实际阅读[MySQL V58](../../../../apex-auth-service/src/main/resources/db/migration/mysql/V58__create_reactive_mfa_config.sql)、[PostgreSQL V58](../../../../apex-auth-service/src/main/resources/db/migration/postgresql/V58__create_reactive_mfa_config.sql)、[MfaConfig实体](../../../../apex-auth/src/main/java/com/apex/scaffold/auth/domain/model/MfaConfig.java)、[仓储](../../../../apex-auth/src/main/java/com/apex/scaffold/auth/infrastructure/repository/MfaConfigRepository.java)与[迁移测试](../../../../apex-auth-service/src/test/java/com/apex/scaffold/authservice/AuthMfaMigrationIntegrationTest.java)。12列与驼峰/下划线映射一致；AUTO_INCREMENT/BIGSERIAL支持无ID插入；DATETIME/TIMESTAMP对应LocalDateTime；两个枚举均可容纳于VARCHAR(20)，secret/backup_codes保持nullable TEXT字符串契约。MySQL唯一键(tenant_id,user_id,type)为(64+64+20)×4=592字节，低于767及3072字节索引键上限，未见该长度导致的明显兼容缺陷。user_id单列索引与tenant前缀索引契合既有查询。

原执行GREEN为**25项=AuthMfaMigrationIntegrationTest 4项真实双库用例（MySQL/PostgreSQL各空库升级与兼容手工表）+MfaServiceImplTest 21项服务单测**，0失败/错误/跳过。本轮未复跑。测试显式指定auth filesystem迁移，V44–V57的14次迁移后升级V58至15次，验证validate、pending=0、再次迁移0、脚本重放、默认值、跨租户同用户/type及真实R2dbcRepositoryFactory的生成ID、字符串/枚举读写与删除。未覆盖同租户重复键拒绝、最大长度值或所有生产配置，因此不扩大这些断言。

CREATE TABLE IF NOT EXISTS只保留兼容手工表，不能修复未知列、identity或旧UNIQUE(user_id,type)，也不自动复制system数据。fixture的MySQL手工表采用TIMESTAMP而正式V58为DATETIME，支持该fixture保留与读取，不证明所有手工时间类型等价。opaque测试字符串只证明持久化，不证明真实TOTP加解密、HTTP流程、短信/邮件或生产密钥配置。既有无tenant仓储方法未由本修复审计。

[auth POM](../../../../apex-auth/pom.xml)生产R2DBC驱动有MySQL/H2；[auth-service POM](../../../../apex-auth-service/pom.xml)的PostgreSQL JDBC驱动不能替代R2DBC驱动。PostgreSQL R2DBC经apex-system的test依赖进入本测试classpath，不能进入生产装配结论；须落实生产驱动与目标profile后另行验证真实启动。

**旧18:21:52的31/31 clean verify发生在V58证据之前**；保留5050 tests、2 skips及PMD261次告警，但不承接新增迁移。V58仅有上述原执行定向结果及单文件Spotless记录，无新的全仓clean verify/PMD结论。P0仍未准出，Java21未切换。

### 动态SQL落盘状态与hash

本轮Glob列举P0目录并读取最新产物，结合Git差异/状态及collect_table_facts.py、test_collect_table_facts.py的实际文件核查，未发现比itemized-1905更新的动态SQL专项保存证据。两个工具文件为未跟踪文件，普通git diff无内容不能证明未改动；工具扩展或上轮agent返回缺失也不能替代执行结果。因此仍保留**586项待运行/语义审阅**，包括V14的old-sql-0142/0143/0144；其余3项仅静态目标识别，全部589项仍未语义验收。不伪造新统计或测试来源。

| 新保存证据 | 本轮重算SHA-256 |
| --- | --- |
| auth-mfa-migration-20261018-2003-evidence.json | ad16a122b4aa1ad4e45942567a6a9ad43fd65d377b59f44e0c73dbf758498d25 |
| table-consumer-boundary-20261018-followup.json | a33d0758f7bf67ff3cd3222ec3e4e6c167af4b3e2bbd7c8577eafc50b6bdf908 |
| table-consumer-boundary-20261018-followup-validation.json | e935d8d982cb7c1bf76d0e909d8c9a14ce2b0b88cc76221818d09dbe03bf651a |

MFA六项实现来源hash全部匹配；边界376处文件/范围hash、1719项扫描来源文件hash全部匹配，validation指向的证据hash匹配。旧itemized/authority及其validation hash保留上节记录，旧证据不回写；本报告与主方案的后续编辑形成预期文档漂移，新增V58使旧迁移清单不再等于当前全量。

本轮文档核验：P0目录57份JSON均可解析，新三份证据与旧itemized/authority四份证据文件的SHA-256均已重算核对。`bash scripts/ci/check-doc-links.sh`退出0，扫描663份Markdown、2851条链接、0断链（限本地路径及锚点）。`git diff --check`退出0；两份未跟踪目标文档各自`git diff --no-index --check /dev/null <文档>`无空白诊断，退出1仅表示存在文件差异。动态SQL测试代码已静态阅读，本轮未执行Python单测，不能为工具扩展补记执行通过。

当前最高优先是明确system历史数据处置责任，落实PG R2DBC生产驱动/配置与启动验证，再推进84候选、6项SQL及586项语义审阅；tenant/唯一键/历史NULL/账号冲突、474配置组合与两变量、依赖责任、契约关联等仍按既有清单推进。D01–D07与后续阶段边界不变。本轮只改两份既有文档，不执行Java构建、数据库操作、生产代码修改、cache恢复、提交或推送。

<a id="2026-10-18-pg生产驱动与v45最新证据整合"></a>

## 2026-10-18 PG生产驱动与V45最新证据整合

本节接续并更新上节的当前结论，依据[PG运行证据](auth-postgresql-runtime-20261018-2033-evidence.json)（20:46记录）及[V45客户端证据](dynamic-auth-v45-20261018-client-evidence.json)（20:31:33更新）。上节“生产PG驱动未落实”“无新全仓门禁”“586项未减少”属于较早时点；旧失败、旧hash和历史章节原样保留，不回写原JSON。

### PG驱动、生产包与探针范围

已核对auth POM：新增`org.postgresql:r2dbc-postgresql`为runtime scope、无显式版本，Spring Boot 3.5.15 BOM解析为1.0.9.RELEASE。basic、standard、enterprise三档生产jar均含PG R2DBC驱动且不含apex-system；三次`package -DskipTests`成功只证明打包。此前测试classpath经system取得驱动的问题已有生产依赖修复，不再列未修复缺口。

enterprise生产jar在真实隔离的`localhost/postgres:17-alpine`上完成auth V44–V58共**15次迁移**及MFA save/read/update/tenant-query/delete探针，日志`/tmp/auth-pg-production-probe-20261018.log`有`AUTH_POSTGRESQL_PRODUCTION_DATABASE_PROBE_PASS migrations=15 mfa=save,read,update,tenant-query,delete`。这里15是迁移次数，不是PostgreSQL版本。探针使用生产jar classpath和最小非Web Spring上下文，不含test classes或system jar；不构成完整auth服务启动、HTTP/MFA/TOTP加解密流程、生产TLS、Redis、Nacos优先级或真实存量数据验收。容器已清理。本轮只复核证据，不重跑探针。

PG切换仍须以下四项一致指向同一目标数据库，并正确提供JDBC/R2DBC凭据：

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://host:port/database
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
SPRING_R2DBC_URL=r2dbc:postgresql://host:port/database
SPRING_FLYWAY_LOCATIONS=classpath:db/migration/postgresql
```

仅改DB_HOST/PORT/NAME不能切换数据库vendor。base/prod默认仍为MySQL，Spring basic运行配置为H2且关闭Flyway；Maven basic构建档位不能等同于Spring basic profile，更不能称默认已切PG。

### 定向结果与最新完整门禁

原GREEN日志`/tmp/auth-pg-r2dbc-green-20261018.log`记录**28项通过**：R2dbcDriverAvailabilityTest 3、MfaServiceImplTest 21、AuthMfaMigrationIntegrationTest 4，失败/错误/跳过均0。

独立按`/tmp/auth-pg-final-clean-verify-20261018.log`插件段中的模块汇总重算，未累加嵌套类明细或陈旧XML：20:45:19完成，耗时15:06，31/31 reactor SUCCESS。Surefire的27条模块汇总为**4816/0/0/0**，Failsafe的10条为**305/0/0/2**（tests/failures/errors/skipped）。合计**5121项包含2项跳过，实际执行5119项**。跳过分别缺少`APEX_SEATA_IT`及`apex.office.real-db`，不得计为相关外部场景通过。AuthMfaMigrationIntegrationTest 4及DualServiceAuthorizationIntegrationTest 5均通过，最新门禁已承接V58与生产驱动修复。Spotless check 31次、PMD check 31次、JaCoCo check 42次/达标消息38条是插件调用统计，不代表每模块统一覆盖；PMD仍出现`aktStatus is NULL`诊断，不宣称日志无诊断。本轮未执行Java构建。

### V45严格旧ID口径

已核对MySQL V45源码SHA-256 `ac857930413474e5f1db46b67831738abc8f28a39156cc58356ff1c6da0f94e6`，以及96条语句offset切片hash，全部匹配。baseline指向itemized-1905，hash为`8f1402d62caf444d2d7343aa1b8f69ee6031e788dcf9c6d827eaf80d33eed254`。

**24组SET/PREPARE/EXECUTE/DEALLOCATE四元组对应96个唯一旧ID（old-sql-0000–0095）**；每组absent、present、replay均成功，共**72次分支/重放检查**，不是72项Java测试。按“绑定四元组且双分支与重放全部通过才关闭旧ID”口径，586项中96项取得该范围证据，**剩余490项**。全文件成功、catalog相等及NULL回填/非NULL保留仅补充观察，不额外计旧ID；旧尝试80项/剩506/63次检查因socket就绪错误被TCP就绪重跑替代，不叠加。

MySQL 8.0.46合成夹具不是完整历史升级链或生产数据；不覆盖PostgreSQL、其他490项、缺表/权限拒绝/并发/回滚。已有同名但不兼容定义被故意保留，不能解释为列定义等价或自动修复。旧589项中的3项仍仅静态识别目标，不能把96项夹具证据扩大为全部589项语义准出。

用户交接记录为“39项Python通过”；两份最新JSON未提供该39项的命令、测试清单或原始日志，本轮未找到可独立复算来源且未执行Python单测。因此仅保留为交接结果，不作为本轮复验通过或新的P0准出依据；72次数据库检查也不能补作Python测试数。

### 本轮hash核验与下一批精确范围

| 最新证据 | 本轮重算SHA-256 |
| --- | --- |
| auth-postgresql-runtime-20261018-2033-evidence.json | 02fc2ba1b946eccd90ce2dfc4dfd812ba54a3fc9f00b9ccf91e10e82ae2e7dd9 |
| dynamic-auth-v45-20261018-client-evidence.json | 938a8f8edcf353510859df1650a0d2db677350b14421c891d5a310a8fd7681b4 |

PG证据sha256字典19项在编辑前全部匹配；编辑后仅本报告与方案两项发生预期文档漂移，余17项源码/配置/脚本/日志保持匹配。旧文档hash分别为`ddcde6a2dd71ece28e9ee19087177efd5d8ce1e1bf79d9a76437263b4d3906cd`、`106634a505fce87c5d4871fd4f420711655223597766a543c55b219304d17ece`，不回写旧证据。enterprise探针jar历史hash `8a047c5c71899abe571b6e9cd1763495314d1fdf2c0b502134737bfddb0b9c70`仅对应探针时点；后续clean verify重建target，不宣称当前jar仍同hash。

P0仍未准出，下一批按以下精确范围推进：

- 剩余490项动态/过程控制，优先system MySQL V14的old-sql-0142/0143/0144三项未绑定控制；另3项静态目标继续补语义结论，96项保留其夹具适用限制。
- 139项旧差异的47候选、17版本语句、75 Flyway自管分类适用性；3283双库差异中1162项需确认，已有office四标志修复和workflow全局键兼容决策不重复列未修复。
- system历史数据保留/迁移/归档/清理责任与对账回退；system OAuth2 SQL实际消费者、jwt_keys活跃上游；84候选及6项SQL（含两个Outbox物理域）的条件装配、目标数据源及责任。21边界仍为18静态排除+1 OAuth2未证实+1 JWT未证实+1 MFA已修复，不等于21项运行验收。
- 历史tenant/唯一键/NULL/账号冲突；474配置组合及两变量实际使用、注入与责任；三档各40组依赖责任/审阅，契约关联、声明分母、工作区和外部环境台账。136域/表、112表名仍是旧登记分母。
- PG完整auth服务启动及部署注入、TLS/Nacos/Redis、完整HTTP/TOTP流程等所属场景；生产驱动缺口已修复，不重复列待修。D01–D07及P6文档语义、P7真实旧库升级/repair/恢复回退仍按原阶段推进。

本轮文档核验：P0目录**59份JSON全部可解析**；两份最新JSON的hash与上表一致，PG来源编辑后17/19匹配，仅两目标文档发生预期漂移。V45源码及96条语句切片hash匹配、24组72个结果均true。`bash scripts/ci/check-doc-links.sh`退出0，扫描**663份Markdown、2856条链接、0断链**（仅本地路径与锚点）。`git diff --check`退出0；两份未跟踪目标文档另行`git diff --no-index --check /dev/null <文档>`均无空白诊断，退出1仅表示文件存在差异。本轮仅修改报告与方案，未执行Java构建、数据库、生产修改、删除脚本恢复、commit或push；未将历史Java/数据库执行结果表述为本轮重跑。

## 2026-10-18 V14绑定修复与合成夹具范围

本节追加[采集证据](table-source-facts-20261018-v14-control-binding.json)（21:26 +08）和[V14客户端证据](dynamic-system-v14-20261018-client-evidence.json)（21:35 +08）的核验结果，保留上节及旧JSON。采集器已将old-sql-0142/0143/0144从未绑定修为`prepared_control_lexically_bound_in_procedure_requires_runtime_review`，三项均关联V14第46行赋值，offset切片/hash一致，`semantic_verified=false`。客户端items仍来自旧基线，保留旧未绑定分类及`semantic_verified=false`；`verified_in_this_probe=true`仅表示以下夹具范围，不将两份JSON的不同状态混为全局验收。

MySQL 8.0.46、隔离临时容器中的**15个唯一旧ID old-sql-0140–0154**绑定原过程生命周期及两个CALL。operation_log/system_message缺列时新增可空、默认NULL的VARCHAR(64)及非唯一索引；present分支故意保留VARCHAR(7)且不补索引；过程重放保留catalog及含NULL、64字符、重复tenant值的数据，缺表调用为no-op。absent/present/replay共**3次分支检查**，不是15次测试。四表合成fixture的整文件首次执行与过程删除成功；整文件重放预期报**ERROR 1060重复tenant_id列**，失败后结构和数据不变，`counts_as_branch_proof=false`。已有列不修复索引或不兼容定义；过程重放不能扩大为整文件幂等。容器及匿名卷已按证据清理。本轮未重跑数据库；不覆盖Flyway解析/history/checksum、PostgreSQL全链、真实历史升级、生产回填/应用隔离、并发/拒权/DDL回滚。

| Python测试口径 | 记录及复核范围 |
| --- | --- |
| 52项采集相关测试 | 按交接结果单列；两份新JSON不含该组精确命令、清单或日志，本轮未独立复算此52项，仍需补来源 |
| 46项探针相关测试 | 本轮分别执行`python3 -m unittest discover -s scripts/test -p test_collect_table_facts.py`（39项）及`python3 -m unittest discover -s scripts/test -p test_auth_v45_dynamic_probe_20261018.py`（7项），均OK，无失败/错误 |

两组未建立跨组去重清单，不能相加为98项或并入Java门禁统计。全局**490项暂保留**；采集JSON的586是静态审阅分母，客户端`remaining_of_586=571`只扣其局部15项且明确`no global additive credit`，本节不将490改为475，不新增累计语义验收。

本轮重算SHA-256：采集JSON为`64af7cedf7b0926d9408c13cac9f8b6caf6c7bee68ff76d1b7c80c4d0085e932`，客户端JSON为`bf978a17c42edab2d00ffa055bf34de3cd1695e3d8c69a8c7a27f2db856634ff`。V14源码hash及客户端基线hash匹配；采集JSON的1903项当前来源hash全部匹配，其1203项旧输入hash检查仍保留UserEntity/MenuEntity两项历史漂移，不能表述为旧输入全匹配。P0目录65份JSON可解析。文档链接及空白检查见本轮交付结果；未执行Java构建或完整回归。

P0仍未准出：上节490项语义审阅、另3项静态目标、139旧差异（47/17/75）与3283双库差异中1162项、历史数据归属/冲突/对账回退、OAuth2与jwt_keys实际消费者、84候选及6项SQL的条件装配/目标数据源/责任、474配置组合及两变量、三档各40组依赖责任、契约/声明/工作区与外部环境台账、PG完整服务/HTTP/TOTP/TLS/Nacos/Redis场景均继续核验。三项V14未绑定缺口已修复，不重复列待修，也不据此关闭语义工作。

D01–D07精确决策仍按[方案第16节](../../../02-architecture/jdk21-unified-auth-multitenancy-implementation-plan.md#16-风险假设与集中决策)阻断对应后续：D01 Oracle精确补丁/build、OS/架构包或镜像、checksum/digest、SDKMAN/渠道、用途授权事实及兼容核验（P1前）；D02身份/成员授权权威及个人租户创建（P2建模前）；D03端并发、5m access、7d闲置/30d绝对refresh、≤5s撤权与敏感写在线规则的取舍（P2实现前）；D04双方验证、冲突审核、历史归属、不并集权限与不自动默认租户（回填合并前）；D05过期/停用/卸载、只读宽限期限及白名单、待办冻结/数据保留（P2契约前，P4/P5全链验证）；D06全档位强制隔离、商业权益与SaaS许可（P3/P4前）；D07精确Provider版本、身份/会话兼容、WebFlux边界或Servlet例外、外部部署/许可/故障边界及可行性证据（P2协议前）。整体授权不代替决策；P6语义文档和P7真实旧库升级/repair/恢复回退仍未完成，不能宣称全部任务完成。


<a id="2026-10-18-v52与90项表权威最新整合"></a>

## 2026-10-18 V52与90项表权威最新整合

本节依据[V52客户端证据](dynamic-auth-v52-client-evidence.json)、[V52审计](dynamic-auth-v52-audit.json)、[表权威复核](table-authority-candidates-20261018-review.json)及[复核验证记录](table-authority-candidates-20261018-validation.json)，接续上一节。证据时间分别为北京时间21:20:42、21:17:20；旧96/586、剩490以及39项Python缺原始日志的记录保留为历史，不回写原证据。

### V52增量覆盖与测试来源

已独立核对V52源码SHA-256 `d29af51d282faea9f8ebeb5dc73c37d2e994ae793d44d9b43b4c03faaeba6bd5`、36条语句offset切片hash及baseline `8f1402d62caf444d2d7343aa1b8f69ee6031e788dcf9c6d827eaf80d33eed254`，全部匹配。9组SET/PREPARE/EXECUTE/DEALLOCATE绑定的旧ID集合恰为**old-sql-0096–0131共36项**，与V45的old-sql-0000–0095共96项无交集；每组absent/present/replay均通过，共**27次分支检查**。累计**132/586，剩454**；27是数据库分支检查次数，不是Python或Java测试数。

完整V52文件的初次执行与重放均成功、catalog相等且数据保持，作为单列补充观察，`counts_as_branch_proof=false`，不额外增加覆盖ID。present分支故意预置同名不兼容`VARCHAR(7)`列，条件按名称存在性选择`SELECT 1`并保留原定义，只证明跳过，不能证明类型/默认值等价或自动修复。absent/replay对列类型、NULL/default/extra和夹具数据的校验也仅适用于MySQL 8.0.46合成夹具，不代表生产数据、完整历史升级链、PostgreSQL、缺表、权限拒绝、并发或回滚验收。

V52 GREEN日志 `dynamic-auth-v52-green.log` 记录`python3 -B -m unittest scripts/test/test_auth_v45_dynamic_probe_20261018.py scripts/test/test_collect_table_facts.py`原执行41项、2.113秒、OK，审计记录退出0，日志SHA-256为`fb3b65052afe54c2214f35ddd4c8c6785db1509c25025c86789a8eebf57cae80`。RED为6项、2 failures、退出1，属于此前缺少`plan_next`及`expected_v52_catalog`的失败记录。本轮核对保存日志及hash，未重跑单测；41项有独立来源，不能倒补上一节39项缺失的原始日志。

### 90项表权威复核及Outbox边界

分母严格为旧**84 candidate_requires_validation + 6 static_sql_consumer_resolved = 90**；排除旧19 historical copies及2 mismatches。本轮19 unknown与被排除的旧19历史副本不是同一集合。独立按items计数为**4 reviewed + 67 partial + 19 unknown**，静态消费者闭环4项、静态证明未完成86项、运行收口0、历史tenant/NULL/唯一键语义收口0。

| 唯一静态闭环项 | 已证明的消费者链与限制 |
| --- | --- |
| file.file_document、file.file_chart | POST Router → Handler.create → ServiceImpl.create → repository.save → Mapper.insert/updateById → TableName，file启动扫描及生产依赖覆盖；未证明实际profile/DataSource、租户及历史数据运行行为 |
| auth.sys_domain_event_outbox | EnableScheduling → 条件Dispatcher.dispatch → Store.claim → 当前域JDBC DataSource；AuthCenterEventConfig在enabled=true或缺省时显式装配 |
| system.sys_domain_event_outbox | system扫描infrastructure事件包；Store为无条件Repository，Dispatcher在enabled=true或缺省时启用；不能把关闭Dispatcher等同于全部Store调用消失 |

默认apex_auth/apex_system等仅是迁移域预期，未解析实际Nacos/profile/DataSource绑定；auth对apex-system仅test scope，不能据此推断生产消费者。词法命中不是调用图，未命中映射不能推断表可删除。复核中的181处文件/行范围证据、173个唯一来源，本轮全文件及范围hash均匹配；这表示来源未漂移，不增加运行证明。

两个域Outbox共用`DomainEventOutboxStore`：append/appendSync含MySQL `ON DUPLICATE KEY`、`IF()`，claim含`UPDATE ... ORDER BY ... LIMIT`。**存在PostgreSQL方言风险，尚未证明PG兼容**；PG迁移存在、MFA CRUD最小探针及此前Java门禁均不能关闭此项。后续应针对append/appendSync/claim及超时恢复、失败重试、清理SQL确认方言策略，再按auth/system各自实际enabled/profile/DataSource验证事务、幂等和领取行为。

表复核GREEN日志 `table-authority-candidates-20261018-green.log` 记录4项、9.247秒、OK，验证记录退出0、失败/错误/跳过0；日志SHA-256 `06b695a1fae54bdd4e721333b57b7b0992670782712f26aecdaf8f55687e4009`。覆盖词法命中不能关闭调用链、非法证据范围失败、排除旧历史/错域及保留unknown/条件Outbox。RED为4项、1 failure、退出1。这里是采集/复核工具测试，不能计作90项业务运行验收。

### 本轮hash与后续精确范围

| 证据 | 本轮重算SHA-256 |
| --- | --- |
| dynamic-auth-v52-client-evidence.json | 3e83c2f2354500ffadea9fca60bf37bb613026347d2d0464f040d9bd97ddf373 |
| dynamic-auth-v52-audit.json | 079e759a53eca3467f938331494c39035ac486ff391a7db5b28b38c05f8cade1 |
| table-authority-candidates-20261018-review.json | 5d1a0aa02d37f64d1ec06c474e3291df602aeff5f7f68e893556bf120d23f2a3 |
| table-authority-candidates-20261018-validation.json | 43c4a423cdc8da4a6f1a4153500d64e1c16b5ea4a6f3719e18fcd052b0d43af9 |

V52审计7个hash引用、表复核验证5个hash引用全部匹配。表复核5个输入编辑前全部匹配；本轮仅两文档编辑产生预期漂移，旧报告hash `e9ba9ce5348c74b78f58f1448d5965faf2782d51b69ac17c237576d1ef9a13cc`、旧方案hash `670afab325d2a78a6e6f3e59437385528f4e16cc2767ba951865bef4f3c3856d`保留，不修改JSON。

**P0仍未准出**。下一批按以下范围推进，不把静态结果扩展为完整服务或所有历史数据验收：

1. 454项动态/过程控制中优先system MySQL V14的old-sql-0142/0143/0144三项未绑定控制；另3项仅静态目标继续补语义。132项保留各自夹具限制，V52全文件执行不重复计数。
2. 两域Outbox先确认上述SQL的PG方言策略与实际装配/DataSource；67 partial逐项沿review JSON的`next_source_entry`补生产调用、条件装配及数据源责任，不按词法命中关闭。
3. 19 unknown逐项核对迁移生命周期、设计用途、生产消费者和数据处置责任，入口以review JSON的`next_source_entry`为准：auth.auth_protocol_config（auth V44）；system.wf_task_history、wf_rollback_history（V21）；system.oauth2_scope_permission、oauth2_audit_log（V25）；system.sys_user_online（V39）；system.token_rotation_history、token_security_event、encryption_key_version（MySQL V42/PG V62）；system.password_history、security_audit_log、jwt_key_version、oauth2_client_secret_history、login_history、trusted_device、encryption_key（MySQL V48/PG V62）；system.doc_document、doc_chart（MySQL V54/PG V27）；system.sys_gateway_access_log（V60）。后面省略域前缀的名称均属system；缺少映射不构成删除依据。
4. 承接旧明确台账：139项的47候选/17版本语句/75 Flyway自管适用性、3283差异中1162待确认、system历史数据处置与对账回退、OAuth2消费者及JWT活跃上游、tenant/唯一键/NULL/账号冲突、474配置组合和两变量注入、三档各40组依赖责任及契约/声明分母。D01–D07、P6/P7沿原阶段边界推进，不把已修MFA或生产PG驱动重复列为未修。

此前生产驱动修复后的Java 17全仓31/31 SUCCESS（20:45:19，5121项含2跳过、实际5119）仍是旧时点结果；本次脚本/证据整合不要求重新运行Java。PG最小MFA探针不是完整服务启动或Outbox验收。本轮仅执行JSON/hash、文档链接及diff核验，未运行Java、Python单测、数据库或生产修改，未恢复已删除cache，未commit/push。

本轮实际文档检查：P0目录65份JSON全部可解析，items独立计数与上述90项分类一致。`git diff --check`退出0；报告的`git diff --no-index --check /dev/null`退出1且无空白诊断。全仓链接检查扫描663份Markdown、2870条链接，退出1、1个断链：方案第19节新增V14链接指向报告尚不存在的`2026-10-18-v14绑定修复与合成夹具范围`锚点。方案no-index检查另报文件末尾新增空行，退出3。这两处为当时未编辑的并行V14章节中的历史发现；本次文档链接修复已核对两份V14证据JSON存在、报告暂无对应V14章节，保留方案第19节与既有运行事实，将失效链接改为阶段报告文件入口，并移除方案末尾额外空行。原失败结果保留，不追溯改写为通过。

本次文档链接修复后：`bash scripts/ci/check-doc-links.sh`退出0，扫描663份Markdown、2870条链接、0断链；`git diff --check`退出0。两份目标文档均为未跟踪文件，分别执行`git diff --no-index --check /dev/null <文档>`，均退出1且无空白诊断（1表示文件存在差异），方案末尾新增空行诊断已消失。本次仅修改上述两份既有文档中的链接、末尾空行及本检查说明；未运行Java、Python单测或数据库，未改生产代码、证据JSON及其历史hash，未commit/push。共享工作区仍有大量既有/并行改动，以上通过仅代表检查时点，不代表工作区冻结或P0准出。

### V14、V52与V45去重及当前测试核验

本轮读取方案3.12、3.13、第19节及三份实际客户端JSON，统一为**MySQL 8.0.46合成fixture证据覆盖**口径。三份JSON引用同一baseline SHA-256 `8f1402d62caf444d2d7343aa1b8f69ee6031e788dcf9c6d827eaf80d33eed254`；按V45/V52的groups.old_ids及coverage.verified_old_ids、V14的items中verified_in_this_probe及coverage.verified_old_ids核对集合：

| 证据 | 唯一旧ID集合 | 项数 |
| --- | --- | --- |
| [V45](dynamic-auth-v45-20261018-client-evidence.json) | old-sql-0000–0095 | 96 |
| [V52](dynamic-auth-v52-client-evidence.json) | old-sql-0096–0131 | 36 |
| [V14](dynamic-system-v14-20261018-client-evidence.json) | old-sql-0140–0154 | 15 |

各组内部无重复，V45∩V52、V45∩V14、V52∩V14均为空。因此限定fixture覆盖的去重并集为**147/586（96+36+15），尚无这三批fixture证据439项（586−147）**。旧96/490、132/454与V14局部15/571保留为历史时点或单批统计，旧“全局490暂保留”不再作为当前fixture余额。V14三项词法绑定已修复，历史“未绑定”不作为当前缺口。

此并集只合并各自限定fixture的覆盖ID，不表示运行了统一数据库全链。V14 JSON的`no global additive credit`及各旧ID的`semantic_verified=false`仍保留；V45/V52组级`semantic_verified=true`也仅在其前提下成立。**147不是全局语义完成数，439不是全局语义待办数**；不据此推导总语义完成数或关闭586项的逐项全局语义审阅，另3项仅静态目标也未因此验收。已有列不修复不兼容定义、V14整文件重放预期1060等限制继续适用；未覆盖PG全链、真实旧库/生产数据、Flyway history/checksum、权限拒绝、并发与回滚。P0与D01–D07、P6/P7仍按前述边界阻断。

本轮为补足旧“52项采集相关测试”交接缺少可复现命令及执行记录，直接运行：

```bash
python3 -B -m unittest scripts.test.test_collect_table_facts scripts.test.test_collect_migration_tables scripts.test.test_auth_v45_dynamic_probe_20261018
```

真实终端结果为`Ran 53 tests in 24.709s`、`OK`，退出0，无失败/错误/跳过。当前测试加载清单为表事实39项、迁移表采集7项、探针7项，共53项。该记录闭合当前复验缺口，不强行凑旧52项、不反推旧52项的历史清单；此前46项（39+7）、V52 GREEN 41项等保留为历史，不跨组相加或并入Java门禁。

此前V14文档检查（663份Markdown、2874条链接、0断链；diff检查通过）保留为旧时点结果。本轮仅修改两份既有文档并运行上述Python测试、JSON集合核对及空白检查；未改代码或证据JSON，未重跑Java或数据库，未commit/push。本轮`git diff --check`退出0；因两目标文档仍为未跟踪文件，另分别执行`git diff --no-index --check /dev/null <文档>`，均退出1且无空白诊断，1仅表示文件存在差异。

<a id="2026-10-18-outbox修复与目录回归最新整合"></a>

## 2026-10-18 Outbox修复与目录回归最新整合

本节为本次文档整合口径；以上章节及旧hash均作为历史保留。本次仅核对证据并局部编辑，未重跑Java、Python单测、数据库或完整服务。

### 共享领域事件Outbox修复与验证边界

[Outbox证据](outbox-dialect-20261018-evidence.json)确认共享`DomainEventOutboxStore`在auth/system装配路径可达，方言由实际JDBC连接的`DatabaseMetaData`识别。PostgreSQL已修复append/appendSync写入、claim、超时恢复和retention cleanup：分别使用冲突处理、锁定有界子查询、interval及有界删除；MySQL DEAD重复事件复活修复赋值顺序，避免先改status使后续重置判断失效。auth双库V56用于隔离测试建表；system双库V1仅静态核对，没有重放system全迁移链。

- 定向验证日志 `outbox-dialect-20261018-verified.log`：真实MySQL/PostgreSQL每库6项共12项，加4项H2事务及3项metrics，共19项，失败/错误/跳过均0；定向运行关闭JaCoCo。质量日志 `outbox-dialect-20261018-quality-final.log` 中的messaging Spotless/PMD通过。这是证据记录的执行结果，本次未重跑。
- MySQL默认隔离下重叠claim曾观察SQLState `40001`；测试仅允许该状态一次重试，模拟下一调度轮次。生产重试和隔离策略没有变化，不证明无死锁或并发生产SLA。过期夹具使用数据库CURRENT_TIMESTAMP，生产retry仍沿用Instant.now，时钟/时区尚未验收。
- 另一条`MessageOutboxMapper` / `sys_message_outbox`仍有DATE_SUB/NOW(6)路径，需message-queue、dispatcher开关及RocketMQTemplate装配，未修复或验证PG兼容。Store定向通过不等于全部Outbox或完整服务通过，也不自动改写旧90项表权威静态台账的运行收口数。
- **本轮生产代码修改后没有新的全仓verify**。此前20:45:19的Java 17全仓31/31 SUCCESS仅为旧时点；19项、Spotless和PMD不能替代当前工作区的全仓门禁。

### 动态SQL全局唯一覆盖与V14限制

[V52目录回归审计](dynamic-auth-v52-catalog-regression-audit.json)与[运行证据](dynamic-auth-v52-catalog-regression-evidence.json)核对9组27次分支、COLUMN_TYPE/IS_NULLABLE/COLUMN_DEFAULT/EXTRA、完整文件目录及数据保持。该批覆盖old-sql-0096–0131已在既有V52证据中，**此次新增唯一覆盖0**；present不兼容VARCHAR(7)保持原样，只证明按名称跳过，不证明结构等价。

独立按三份客户端JSON的verified_old_ids复算：V45为96、V52为36、V14为15，组内无重复、两两无交集，同一baseline下并集**147/586，剩439**。这是限定MySQL合成fixture证据覆盖，不是全局语义完成数。探针仍以V45的96项初始化，局部132/454及new_unique_passed_count=36不能作为当前全局账目；V52重跑不得再加36。

[V14真实证据](dynamic-system-v14-20261018-client-evidence.json)的absent/present/过程重放通过，缺表no-op；完整文件首次执行通过，**完整文件重放预期ERROR 1060 (42S21)，Duplicate column name tenant_id**，失败后schema/data不变。过程重放与全文件重放必须分开，不能称整个V14幂等；此次仅复核原证据，没有重跑V14。三项词法绑定已修不等于语义验收，旧JSON不改写。

46项Python日志 `dynamic-catalog-regression-python.log` 记录`python3 -B -m unittest scripts/test/test_auth_v45_dynamic_probe_20261018.py scripts/test/test_collect_table_facts.py`退出0、46项无失败/错误；其SHA-256为`546cb9deafad8f248b733ae1320abb91a17dc0ca9bd292179bcbef3843bea355`。这是目录回归审计绑定的历史执行，与上一节53项不同命令记录独立保留，不相加、不反推彼此清单。

### 本次hash核验与具体剩余

本次解析P0目录68份JSON全部成功；对Outbox证据的13项hash及目录回归审计的15项源码/工具/基线/证据/日志hash，共28项全部匹配。关键证据重算如下：

| 文件 | SHA-256 |
| --- | --- |
| outbox-dialect-20261018-evidence.json | 1085f3a4e9db6d7884387c8f287240b153a9a203e05f185627b2a1e229ada2f9 |
| dynamic-auth-v52-catalog-regression-audit.json | e179c22651e592b10ccda06b4786c6c28438530cb168dbe57e36ccec15e7a66f |
| dynamic-auth-v52-catalog-regression-evidence.json | 286fd56716171a547cd65a78e5912e61f548a777ac37a57c9589628e65c4d4d1 |
| dynamic-system-v14-20261018-client-evidence.json | bf978a17c42edab2d00ffa055bf34de3cd1695e3d8c69a8c7a27f2db856634ff |

编辑前报告hash为`9113d586e1a1c7219dc242d38d61d1f2e02e0d866658430ded66c79d24a20826`，方案为`0854006bce4880d06e4ba1dbe68efce2c01f84efcb539b0966296a92b3c8e60f`。旧JSON引用的两文档source hash已随历次文档整合发生预期漂移，本次编辑继续产生预期漂移；这是文档版本变化，不能回写旧JSON或替换旧hash。生产源码和本次选定日志hash匹配不消除其他历史来源的时点限制。

**P0不准出**。下一批V55为old-sql-0132–0139共8项，状态selected_not_executed；需列/索引guard的absent/present/replay及精确目录/数据检查，索引guard无缺表检查须单列，现有列专用runner需先以测试驱动最小扩展。本次未执行这些ID。其余431项加该8项共439项无上述限定fixture证据，按审计remaining_by_source继续；已有147项的限制和全局语义审阅、另3静态目标均保留。还需生产修改后的完整verify、sys_message_outbox方言与实际装配/DataSource、auth/system完整PG服务及历史升级/时钟边界验证；67 partial/19 unknown、139项旧差异、1162项双库差异、历史数据对账回退、474配置组合、三档依赖责任与D01–D07、P6/P7均沿已有台账推进。没有因本节修复而自动验收。

## 2026-10-19 V55核验与sys_message_outbox双库方言修复

### V55动态SQL证据核验

[V55证据](dynamic-auth-v55-client-evidence.json)已复核：源文件`apex-auth-service/src/main/resources/db/migration/mysql/V55__auth_user_account_binding.sql`的SHA-256为`2f0368581aa58f7d763c4342710cde06d13bf1c81575fcbeb8ed0dc1467910fd`，与证据`source_sha256`字段完全一致。baseline仍为`8f1402d62caf444d2d7343aa1b8f69ee6031e788dcf9c6d827eaf80d33eed254`。

V55覆盖`old-sql-0132`–`old-sql-0139`共**8**项，与既有V45(96)/V52(36)/V14(15)无交集。因此限定fixture覆盖并集更新为**155/586（96+36+8+15），剩余431**。V55的`scenario_status`为`branches_passed_with_missing_table_failures`：6次分支检查通过，`missing_table`为预期失败（索引guard无缺表保护，单列记录）。offset切片为字符偏移（非字节），切片hash全部匹配。

该8项仍受既有V45/V52/V14相同限制约束：MySQL 8.0合成fixture、不覆盖PG全链、不覆盖真实旧库/生产数据、不覆盖Flyway history/checksum、权限拒绝、并发与回滚。155不是全局语义完成数，431不是全局语义待办数；不据此关闭586项逐项全局语义审阅。

### sys_message_outbox PostgreSQL方言修复

此前报告记录`MessageOutboxMapper`/`sys_message_outbox`仍有`DATE_SUB(NOW(6), INTERVAL ...)`与`NOW(6)`路径，未修复PG兼容。本轮修复如下：

**1. `MessageOutboxMapper.releaseExpiredLocks`**：改为`<script>`动态SQL，按`_databaseId`分支：
- PostgreSQL（`_databaseId == 'postgresql'`）：`locked_at < CURRENT_TIMESTAMP(6) - (lock_timeout_seconds * INTERVAL '1 second')`
- 默认（MySQL及未识别方言）：保持原`DATE_SUB(NOW(6), INTERVAL lock_timeout_seconds SECOND)`不变

**2. `MessageOutboxMapper.lockMessage`**：`NOW(6)`替换为`CURRENT_TIMESTAMP(6)`，两库通用且语义等价。额外补充`status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP(6)`条件，与待投递查询条件保持一致，避免锁定未到重试时间或已终态的消息。

**3. `MybatisPlusConfig`**（`apex-infrastructure-persistence`）：新增`VendorDatabaseIdProvider` Bean，映射`MySQL→mysql`、`PostgreSQL→postgresql`，供MyBatis-Plus自动装配的`SqlSessionFactory`使用，驱动Mapper内`_databaseId`方言分支。未识别数据库返回null，`_databaseId`为null时落入默认MySQL分支，既有Mapper行为不变。

**4. `MessageOutboxDispatcher`**：修复前序遗留的`Flux<Void>.block()`编译错误为`.blockLast()`（`dispatch()`需等待投递管线完成再返回）。

**验证**：
- 新增常驻单测`MessageOutboxMapperDialectSqlTest`（4项，无DB）：断言`releaseExpiredLocks`在`databaseId`为null/mysql时走MySQL分支、为postgresql时走PG分支且可被JSqlParser解析；`lockMessage`在各方言下均使用`CURRENT_TIMESTAMP(6)`。
- 既有双库回归测试`MessageOutboxDialectTest`（8项，`OUTBOX_DUAL_DB=true`时运行）：在真实MySQL 8.0与PostgreSQL 17容器上，经`MessageOutboxServiceImpl`验证lockMessage状态/时序约束、releaseExpiredLocks仅清过期锁、markAsFailed重试/死信、fetchPendingMessages仅返回到期PENDING。3次重复运行均通过。
- `apex-infrastructure-messaging`与`apex-infrastructure-persistence`模块测试：94项通过，0失败，0错误。

**边界**：本次修复覆盖`sys_message_outbox`的Mapper方言与装配层，不自动消除旧台账中`sys_message_outbox`相关的双库差异或消费者核验项；不证明完整Outbox调度在PG生产环境的并发SLA、时钟边界或与RocketMQ的端到端投递。

### 全仓回归状态

`sys_message_outbox`相关生产代码修改后，已完成Java 17全仓`mvnd -T4 -B clean verify`（全新daemon）。反应堆30个模块全部`BUILD SUCCESS`，测试统计`run=10276, failures=0, errors=0, skipped=24`。Spotless、PMD(p3c)、ArchUnit、JaCoCo门禁随verify一并通过。

本次文档检查：`bash scripts/ci/check-doc-links.sh`退出0，扫描663份Markdown、2894条链接，0断链；`git diff --check`退出0。检查范围为本报告新增章节及全部生产代码变更的文档链接与差异空白；未改证据JSON、未commit/push。全仓`clean verify`已通过（30模块SUCCESS、10276测试0失败0错误），但P0整体仍不准出——剩余431项动态SQL、19项未知表消费者、历史升级链等价性、生产配置注入与启动链路等P0待办沿已有台账继续推进。

## 2026-10-19 V15索引校验修复与独立重跑

本节追加记录，保留此前章节、证据与hash。编辑前报告SHA-256为`b9040333ccf7ae1b0e2abd03e393d60cb288082c9441f135c9d23f663d2d4821`，实施计划为`4f334f8ed9b2119b1f373f26b9bb4838a83d03305e6074bbf16e68d0fdb36b57`。

### 修复及历史失败口径

[旧V15失败证据](dynamic-system-v15-20261019-client-evidence.json)保持原样，SHA-256为`a42a9a42d29973c6c10ec91ab6b8a38acb4d7e6a8cf8a2023c3611e7b9ffbe37`。其46组、138次分支虽通过，整文件索引检查包含PRIMARY，23张表返回46行而非预期23行，终态为failed_or_environment_unavailable。该轮184项不加入有效累计，重跑前仍为155/586、剩431。

共用探针现按每个guard的目标索引名查询，逐行核对表名、索引名、NON_UNIQUE=1、SEQ_IN_INDEX=1、tenant_id和BTREE，严格保留23行断言。缺表检查实际断言selected_sql=`SELECT 1`并确认表仍不存在；replay的fixture_sql记录为复用absent结果，未虚构重建；整文件重放比较全部23表数据及列/索引目录。run返回值由清理后的真实终态决定，旧ID计分同时要求整轮passed和容器/匿名卷清理成功，避免提前return或finally失败形成假成功。

### 独立重跑结果

[新V15运行证据](dynamic-system-v15-20261019-rerun-evidence.json)SHA-256为`37e4821b20ce16058ff0836d2f88d465ff474bc851df5f027a7d460b895fb303`。同一轮真实MySQL 8.0.46探针退出0、终态passed；46组138/138分支通过，46个缺表检查均选择`SELECT 1`且表保持不存在；整文件创建23列/23个目标索引，全23表数据及目录重放保持一致，删除11张表后整文件再次执行仍no-op并保留12个tenant列。容器network none、无发布端口、数据库tmpfs；清理remove退出0、exists退出1，容器与匿名卷均已移除。

该轮old-sql-0155–0338共184项与旧有效155项无交集。source/baseline SHA-256、旧ID绑定、分支闭合及计分flags均复核；`verified_union()`独立复算为**339/586，剩247**，失败证据未进入有效证据hash集合。此处339仅表示限定fixture证据的去重覆盖，仍不构成P0准出。

### 绑定与验证边界

V15源文件SHA-256为`003383a4ac436588bd27f89b6aef852e8916eeb25a77e5bdd96a4e7f072af78f`，baseline为`8f1402d62caf444d2d7343aa1b8f69ee6031e788dcf9c6d827eaf80d33eed254`。old-sql-0155–0338共184项已逐项核对字符偏移切片hash及statement ID，均匹配源文件与baseline。目标索引定义、错误唯一性/列名/列序号、缺表no-op及未创建表、重放数据、V14/V55/V15清理后失败不计分等14项探针测试通过；采集器41项测试通过，合计55项。

Python全集在加载项目固定JDK环境后运行160项，耗时518.938秒，Failures=0、Errors=3；三个错误均为既有`test_local_deployment`用例因缺少cryptography依赖而抛出ValueError，未修改该无关功能或安装其依赖。因此全集未通过，不能以55项定向通过替代全集结果。此前未加载固定JDK环境的首轮亦未通过（159项，17失败、3错误），仅作为环境排查记录，不作通过证据。

该验证仅限MySQL 8合成fixture。未证明PostgreSQL、Flyway解析与history/checksum、完整历史升级链、生产数据及租户回填、权限拒绝、并发、DDL回滚或真实服务隔离；同名列/索引分支仍允许不兼容定义，DDL列位置仅在合成表核对。限定fixture旧ID覆盖数不等于全局语义完成数。

### 消息Outbox及Java门禁复核

本次静态核对现有源码，`sys_message_outbox`的VendorDatabaseIdProvider映射与Mapper的_databaseId双库分支、PENDING/到期条件CAS、dispatcher的blockLast、失败统一markAsFailed及同步发送remote池均已存在。过期PROCESSING仅清锁不恢复PENDING、markAsFailed依赖锁持有者单写、生产应用/数据库时钟边界和真实RocketMQ端到端投递仍未收口，本轮未新增Java修改或重跑其测试。

沿用已经明确输出BUILD SUCCESS的全仓门禁：`source scripts/build/init-build-env.sh && mvnd -T4 -B clean verify`，JDK 17.0.12-oracle、mvnd 2.0.0-rc-3，2026-10-19T09:33:11+08:00结束，耗时16:42，任务标识`job-a2ac737d39a04bbc849c226a115bcbae`。不将管道退出0单独作为Maven成功证据，本次不重新统计Java测试数量。

追加后全仓文档链接检查退出0：663份Markdown、2899条链接、0断链；`git diff --check`退出0。五个目标文件另以`git diff --no-index --check /dev/null <文件>`检查，均无空白诊断（退出1仅表示文件差异）；P0目录73份JSON解析通过，六份历史探针证据hash均保持不变。报告与方案历史正文前缀hash与编辑前记录匹配；未commit/push。

**P0仍不准出**：19项未知表消费者、双库差异、历史升级等价性、生产配置与启动链路及既有阶段台账继续推进。

## V16任务表租户DDL限定验证（2026-10-19）

[真实运行证据](dynamic-system-v16-20261019-client-evidence.json)SHA-256：`ee97ae2bb94a4d82a8de8e139cde76b37a1a6c1711431d401dc42bc2f7db0fad`。source SHA-256：`00460e1037bc2871686b3633d5eb7eb00977b53509f58440f99297d35ec9c21e`；baseline仍为`8f1402d62caf444d2d7343aa1b8f69ee6031e788dcf9c6d827eaf80d33eed254`。

先运行新增回归确认RED，再实现V16规划和专用verifier。探针18项、采集器41项共59项通过。真实MySQL 8.0.46终态passed，6组18/18 absent/present/replay通过，6组缺表均选择SELECT 1且未创建表；完整文件检查两列nullable/default NULL、AFTER deleted、四个目标索引的准确列顺序，包括(tenant_id,status)与(tenant_id,job_id,start_time)。两表非空tenant-a数据及完整目录在重放前后保持一致，双表均缺失时完整文件保持no-op。隔离容器remove退出0、exists退出1，容器及匿名卷成功删除后计分。

新增old-sql-0339–0362共24项，去重有效并集**363/586，剩223**。present夹具的同名VARCHAR(7)或UNIQUE(id)被接受，故不证明对象定义等价。本轮仅修改Python探针、测试与台账；未修改历史SQL或Java，未重跑Java门禁或Python全集。此前全集缺cryptography导致的3个错误仍未收口。本次合成fixture不证明PostgreSQL、Flyway/history/checksum、完整历史升级、生产租户数据、并发、权限拒绝或回滚；**P0仍不准出**。

## V59多端账号回填与collation边界（2026-10-19）

[最终运行证据](dynamic-system-v59-20261019-client-final-evidence.json)SHA-256已实算为`ba135b0fdfaa02aad4a554f68d0b0a4c662f0b58d040942eaa0b1132944c3e66`。source SHA-256为`7f61aee0c9ad3a2dae03d1869800936e317806a35ab19b20ee18ace0a7699b82`，baseline为`8f1402d62caf444d2d7343aa1b8f69ee6031e788dcf9c6d827eaf80d33eed254`。真实MySQL 8.0.46的4组absent/present/replay共12/12分支通过；清理remove退出0、exists退出1，容器与匿名卷删除成功，数据库仅存在于已移除的tmpfs容器。

V59新增old-sql-0513–0528共16项，实际调用`verified_union()`复算有效去重并集**379/586，剩207**。该计算核对baseline/source hash、整轮passed、清理成功、分支闭合及逐ID计分标记；首次失败证据未进入有效hash集合。[兼容夹具证据](dynamic-system-v59-20261019-client-compatible-evidence.json)已覆盖相同16项，最终重跑记录`new_unique_passed_count=0`，并非再次增加16项。静态CREATE TABLE/INSERT及完整文件成功不增加分支旧ID计分。

完整文件回填核对结果：

- `PHONE_123`、NULL open_id与deleted用户不生成绑定；`PHONEXvalid`正常生成，验证PHONE_字面前缀排除范围。
- 新增id为1、2、6、7，沿用用户id；tenant_id及union_id（均含NULL样例）、user_id、WECHAT_APP渠道与status=1逐项匹配。bound_at沿用create_time，NULL时取固定会话时钟`2026-01-02 03:04:05`，不是生产时钟验证。
- 已有id=90的跨tenant WECHAT_APP绑定保留原tenant、user_id、union_id、status=0、bound_at、version=7及deleted=1，不被源用户覆盖；已有WECHAT_MINI同open_id绑定保留且不阻断WECHAT_APP新增。
- sys_user新增register_channel为UNKNOWN，sys_login_log新增client_type/platform均NULL；三表数据及全部列/索引目录在完整文件重放前后相同，证据`backfill_verified`、`replay`、`catalog_equal`均true。

[首次失败证据](dynamic-system-v59-20261019-client-evidence.json)原样保留，SHA-256已实算为`ef52d2c1a060871de76838833aa11559817e6739c0ca467bda99ebd1b7f03828`。该轮12/12分支虽通过，默认utf8mb4_0900_ai_ci用户夹具与utf8mb4_unicode_ci社交表比较触发真实1267（Illegal mix of collations），整轮`failed_or_environment_unavailable`、计分0。最终仅将合成用户夹具明确为兼容的utf8mb4_unicode_ci后通过，未修改历史SQL；不能据此推断生产collation安全。

三个列guard缺表时SELECT 1且表仍不存在，属于no-op；索引guard及完整文件缺少sys_user时均实际返回1146，并记录`status=expected_failure`、`passed=false`。整轮passed仅表示限定分支与回填校验完成，不把这两个预期失败场景记为通过。同名不兼容列/索引被guard接受，不表示定义等价。

全局`(channel,open_id)`唯一键及回填NOT EXISTS均无tenant作用域；已存在的其他tenant、解绑或逻辑删除绑定仍可阻止相同渠道/open_id回填，其他channel则不阻断。此处记录身份模型、唯一性与数据迁移边界，尚未取得真实请求越权链路，不能直接定性为可利用漏洞。

本轮范围仅Python与docs，定向探针23项、采集器41项合计64项通过；本次文档更新只做证据读取、hash与并集复算，未重跑Java或Python全集，此前全集cryptography缺失的3个错误仍未收口。未验证PostgreSQL、Flyway解析/history/checksum、完整历史升级链、生产数据及collation、权限拒绝、并发或回滚。**P0仍不准出，Java 21未切换**。

## V43条件索引过程与重放验证（2026-10-19）

[运行证据](dynamic-system-v43-20261019-client-evidence.json)SHA-256实算为`4c76fefb305913982111e346ee4c258805b0b5f7101fc14f5f18675521514918`。24个旧ID（old-sql-0449–0472）与基线、原始source和statement偏移哈希绑定，包含21个CALL及3个过程生命周期条目。真实MySQL 8.0.46完成21组63次absent/present/replay分支，21个缺表及46个逐列缺失场景均为no-op；同名不兼容UNIQUE(id)保留，说明名称guard接受已有定义，不表示定义等价。

精确核对目标索引NON_UNIQUE、SEQ_IN_INDEX、COLUMN_NAME与BTREE；非空数据和列目录保持不变。原始完整文件初次执行和重放后全表目录/数据一致，过程均已删除。生命周期组另有3个闭合标志，证据branch_runs=66并非66个CALL分支。运行使用禁网、tmpfs、独有容器；remove退出0、exists退出1，容器及匿名卷清理成功后计分。主代理独立复算`verified_union()`：**379 → 403/586，新增24，剩183**。

实际运行命令为加载固定构建环境后执行`python3 scripts/ci/auth_v45_dynamic_probe_20261018.py --run --source apex-system/src/main/resources/db/migration/mysql/V43__performance_optimization_indexes.sql --output docs/06-testing/jdk21-unified-auth/20261016-p0/dynamic-system-v43-20261019-client-evidence.json`。定向测试命令为`python3 -m unittest scripts.test.test_auth_v45_dynamic_probe_20261018 scripts.test.test_collect_table_facts scripts.test.test_collect_migration_tables scripts.test.test_collect_database_schema`，探针29项、三组采集器63项合计92项通过；最终修改后再次运行29项探针测试通过。保留既有证据，禁止覆盖已有输出。

本轮仅修改Python、证据与两份既有文档，未修改历史SQL或Java，未重跑Java门禁或Python全集；此前cryptography缺失3个错误仍未收口。该验证不覆盖PostgreSQL、Java/Flyway解析/history/checksum、完整历史升级链、生产性能、并发、权限拒绝或回滚。**P0仍未准出，Java 21未切换**。


## V40动态列guard失败轮次（2026-10-19）

[运行证据](dynamic-system-v40-20261019-client-evidence.json) SHA-256为`e4d4a36271dee34ac07d8e9d2f9c8279cfc70051704dfb03adab2f7392a6a57d`。V40严格绑定45个old ID、9组guard和54条语句；真实MySQL 8.0.46隔离容器禁网、tmpfs运行，remove退出0、exists退出1，清理成功。9组absent/present/replay执行25/27次，首组oauth2_authorization_consent对两条非空记录新增NOT NULL主键时触发1062，整轮partial_failure，45项不计分；缺表、缺源列及完整文件失败证据保留。present不兼容字段仅证明名称guard no-op，不证明定义等价。

本轮新增4项V40测试；定向探针与三组采集器合计96项通过。verified_union仍为**403/586，剩183**，V40新增0项。未修改历史SQL或Java，未运行Java回归或Python全集；cryptography缺失的3个错误仍未解决。限定证据不覆盖PostgreSQL、Flyway/history/checksum、完整升级链、生产数据、权限拒绝、并发或回滚；**P0仍未准出，Java 21未切换**。

## V51邮箱列与索引守卫收口（2026-10-19）

[本轮唯一证据](dynamic-system-v51-20261019-7b2ec947-evidence.json) SHA-256为`5be00f8da636d88e0dfad1b715326e691cc444a901142c3368200d59af7468f0`；source SHA-256为`5bcce0eec1a75873b724e3c3c5bacbada38c4e90c391aca11018a2e5bd416e38`；baseline SHA-256保持`8f1402d62caf444d2d7343aa1b8f69ee6031e788dcf9c6d827eaf80d33eed254`。新增planner/verifier/CLI与union绑定检查，要求source、statement偏移/hash、baseline条目一一对应；先观察缺实现RED，再完成GREEN，并拒绝目录、数据及证据绑定篡改。

真实MySQL 8.0.46完成email及idx_email两组absent/present/replay共6/6分支；列类型、nullable、默认、位次和索引唯一性、序号、列及BTREE均精确核对。同名不兼容VARCHAR(7) NOT NULL DEFAULT与UNIQUE(id)原样保留，只证明名称guard no-op。非空中文、legacy-sub与后续填入email的数据保留；完整原文件执行及重放的列/索引目录、数据、表注释一致，完整文件成功不额外计分。真实缺表1146、缺phone1054、索引缺email1072，以及完整文件缺keycloak_sub1054均记录expected_failure、passed=false。

唯一容器采用network none与tmpfs，finally执行rm -f --volumes，remove退出0、exists退出1，清理成功。整轮passed且6/6分支闭合后仅计old-sql-0505–0512共8项；verified_union与另行独立扫描均为**411/586，剩175**。V40原生失败45项和consent修复证据仍不混入。定向探针38项、三组采集器63项、consent资源4项合计105项通过。未修改历史迁移或原baseline，V42本轮未开展，未运行Java回归或Python全集；既有cryptography缺失3错误仍未收口。本证据不证明PostgreSQL、Flyway/history/checksum、完整历史升级、生产数据、权限拒绝、并发或回滚；**P0仍未准出，Java 21未切换**。

## 2026-10-19 Python隔离全集与V19/V42诊断证据收口

本节更新当前状态，前文Python全集因缺少cryptography出现3个错误的记录、V51时点“未运行全集”和所有历史hash均保留。[持久环境/运行摘要](python-full-20261019-run-summary.json)记录隔离环境 `/tmp/apex-p0-python-20261019` 的既有全集结果：**188项，0失败、0错误、0跳过，540.834s，OK**。日志中可识别17个测试模块；该结果仅对应执行当时范围，不包含后续新增文件或其他代理的后续源码修改，也不代表最终工作区全集通过。未记录运行时源码快照hash，不能补造最终覆盖声明。

原日志为 `/tmp/apex-p0-python-full-20261019.log`，实际复算SHA-256为 `9dc11042b7c02cd72d226e28ed32ed1bc1d8cac167641dc6a18bd087425eabe7`。未复制整份临时日志；`/tmp` 日志和venv可能消失，持久摘要及hash不等于保存了原始日志，原日志丢失后无法从摘要重建完整输出。日志未记录原调用命令，摘要明确标为未知，不编造命令。Python `3.13.12`、cryptography `46.0.7`、PyYAML `6.0.3`、cffi `2.1.1`、pycparser `3.11` 均已在既有venv复核；本次实际执行 `/tmp/apex-p0-python-20261019/bin/python -m pip check`，退出0，输出 `No broken requirements found.`。requirements未改；本次未重新运行188项或Java门禁。

已读取以下真实JSON并逐一复算hash；均来自MySQL 8.0.46合成fixture诊断，不是生产旧库或Flyway完整升级：

| 证据 | SHA-256 | 实际结果与计分边界 |
| --- | --- | --- |
| [V19诊断](dynamic-system-v19-20261019-negative-evidence.json) | `e66cdc70955faf0db7bb164c02f454b37c1d5a131f6914f27245f1e94f6320a4` | 首次整文件成功，租户复合唯一键/FK及NOT NULL实际建立；同租户引用成功，跨租户FK以1452拒绝，NULL租户以1048拒绝；整文件直接重放以1061重复索引名失败。14场景均不计分。 |
| [V42首次采集失败](dynamic-system-v42-20261019-negative-evidence.json) | `a7826ad7aeff847e4d5832a3be5ace534d096397c4e608a82f7310b1527f014d` | `diagnostic_failed`，UTF-8解码错误，第三场景未得到完整结果；保留原证据，不能认定SQL验证通过。 |
| [V42独立重跑](dynamic-system-v42-20261019-negative-rerun-evidence.json) | `c2441533121c83c9c487a2505299c314eb9b2976918565ab3441285933580898` | token表存在时，列/索引原生分支与整文件首次执行实际报1064；`ADD COLUMN IF NOT EXISTS`、`CREATE INDEX IF NOT EXISTS`及一次PREPARE多条合法DDL的控制场景均1064，单条合法prepared DDL成功。token表不存在的no-op与首次整文件成功不证明存在分支；该整文件直接重放报1060重复列。13场景均不计分。 |

V19与V42重跑状态均为 `diagnostic_expected_failures_confirmed`；三份JSON均记录容器及匿名卷清理成功。两批有效 `verified_old_ids=[]`、新增 **0项**，`counts_as_branch_proof=false`，当前覆盖保持 **411/586，剩175**。V42的1064是被真实执行证实的MySQL语法问题，应进入后续处置；V19重放1061及V42重放1060只表明绕过Flyway history直接重放的行为，版本迁移由history管理一次执行，不能仅凭天然非幂等就定性为迁移缺陷。源SQL、测试、baseline及历史证据本次均未修改。

## 2026-10-19 P0准出边界与仍需外部确认

P0准出要求冻结已知风险、处置责任、证据缺口及各后续阶段前置条件，当前仍未准出。应将缺口逐项关联实际责任人与确认记录，不能用方案整体获批代替业务或许可决策。P6完整Javadoc/doclint和P7真实旧库升级、生产repair、恢复、上线回退实施按原阶段验证；不把P7全部生产演练提前作为P0门槛。P0须记录这些实施所需的样本、责任、计划与阻断点，未完成演练不得写为已通过。

仍需外部确认的事实和决策如下；具体人员、组织用途与商业规则尚未提供，均不得由技术代理代填：

| 项目 | 待确认内容与阶段前置 |
| --- | --- |
| D01 | Oracle路线已选；仅待精确Oracle 21发行物、补丁/build、平台、checksum/digest、获取渠道及组织实际用途/许可适用性，在P1切换前冻结，无须重问发行版路线。 |
| D02 | 身份权威系统、个人租户创建时点，在P2模型冻结前确认。 |
| D03 | 会话有效期、并发策略和撤权语义，在P2会话实现前确认。 |
| D04 | 账号合并规则、历史回填归属及冲突处理，在回填/合并前确认。 |
| D05 | 冻结、宽限期与状态签发契约，在P2契约前确认，并在P4/P5验证。 |
| D06 | 权益与许可规则及适用边界，在P3/P4相应实施前确认。 |
| D07 | 先研究精确Provider与既有运行栈的可行性；仅在证实确需例外或外部选择时再提请确认，不预设例外已获批准。 |
| 历史升级输入 | 提供真实旧制品、对应版本与脱敏 `schema_history` 数据，才能验证真实checksum/升级链；P0记录提供责任与后续验证前置，不冒充已有样本。 |
| 责任与保留期 | 确认各风险、部署、许可与证据的具体负责人，以及证据和临时日志的保留期限/保存责任；当前未指定人员或期限。 |
| Nacos配置 | 确认 `shared_nacos/DB_HOST_PROD` 的实际消费链、部署注入与配置责任；静态未知不等于运行不消费，也不证明注入已满足。 |

## 2026-10-19 本轮覆盖独立核验与V42部分提交诊断收口

本节仅追加当前结果，保留历史411/403、原失败与历史hash。已读取并实际调用 `scripts/ci/auth_v45_dynamic_probe_20261018.py` 的 `verified_union()`，得到489个有效旧ID、分母586、剩97；另行扫描磁盘证据，核对passed状态、基线和源文件SHA-256、逐ID计分标记、absent/present/replay闭合、清理及旧ID的statement偏移/hash绑定，独立并集与函数返回逐ID一致。新增证据还核对group内statement与原文件一致；PostgreSQL要求额外existing_column/existing_index/existing_both三分支，V37/V44要求原文件首次执行和重放。未采信证据内可能过时的cumulative/remaining摘要；重复证据只按旧ID并集计一次。

| 有效覆盖来源 | 旧ID数 |
| --- | ---: |
| auth MySQL V45 / V52 / V55 | 96 / 36 / 8 |
| system MySQL V14 / V15 / V16 | 15 / 184 / 24 |
| system MySQL V37 / V43 / V44 / V51 / V59 | 15 / 24 / 32 / 8 / 16 |
| system PostgreSQL V14 / V15 / V16 | 4 / 23 / 4 |
| 合计 | 489 |

MySQL458 + PostgreSQL31 = **489/586，剩97**。分母586沿用冻结动态SQL及过程控制旧ID，另3项静态DDL目标不进入本分母。相对文档旧411，新增78有以下有效来源，而V42新增为0：

| 新增证据 | 项数与有效范围 | 文件SHA-256实算 |
| --- | --- | --- |
| [MySQL V37](dynamic-system-v37-20261019-2010-evidence.json) | 15；旧ID0379–0393，3组9次分支、原文件首次/重放，非空数据保留 | `3da6c2c9a77034ee30f6dc5dc67d36a381cc8cd3f1dc09b1ab67c42e9f1691c8` |
| [MySQL V44](dynamic-system-v44-20261019-2012-evidence.json) | 32；旧ID0473–0504，两条租户初始化路径、原文件首次/重放、8个拒绝guard | `fbdc60915de3979fa0154f3355ef9e55b1d48f879fa3cec99fe9f0cebde62cba` |
| [PostgreSQL V14/V15/V16](dynamic-pg-p0-d5b8c9f273a848d191ca4b7165dd8135-evidence.json) | 31；旧ID0530–0560，PostgreSQL17.11，31组186次六分支 | `ec4d78c3818ba28771410abab0a797b8e497c283dafbefcac2c70d64f251db11` |

三份均passed，源文件/基线绑定有效且容器与匿名卷清理成功。V37/V44使用MySQL8.0.46；同名不兼容列/索引被名称guard接受不证明结构等价。新增集合78项从当前489中扣除恰为旧411，未将consent前置修复或V40原生失败45项混计。

剩余97按源文件反集分解为MySQL V19 16、V40 45、V42 9，共70；PostgreSQL V18 1、V19 2、V20 1、V24 5、V26 2、V27 4、V31 2、V32 2、V34 2、V35 2、V41 1、V44 1、V45 2，共27。以上是未取得本口径有效闭合证据的项数，不把所有剩余项一概判为SQL缺陷。

### V42两轮证据与部分提交事实

| 轮次 | 实际状态与计分 | 文件SHA-256实算 |
| --- | --- | --- |
| [首次采集排序失败5d8549a1](dynamic-system-v42-20261019-partial-commit-5d8549a1-evidence.json) | `diagnostic_failed`，14个已采集场景；错误保存实际观测字典，未完成最终闭合；新增0 | `be2c97c2b23b98496ca55cd757681928520d804f3bb403b1b970a9351691e821` |
| [成功诊断82e3c165](dynamic-system-v42-20261019-partial-commit-82e3c165-evidence.json) | `diagnostic_expected_failures_confirmed`，15场景；新增0 | `3d23cbfc10eab19fabc50e1d2e13021a131f67bad6c88c9105209dfd511cc4f7` |

15场景为列guard absent/replay/present、索引guard absent/replay/present共6个，单条合法prepared DDL、ADD COLUMN IF NOT EXISTS、CREATE INDEX IF NOT EXISTS、多语句PREPARE控制共4个，整文件token不存在首次/重放、token存在首次共3个，最后增加隐式提交失败与部分状态重试2个。预期1064/1060被确认属于诊断成功，所有场景 `counts_as_branch_proof=false`，两轮 `verified_old_ids=[]`。

成功诊断使用禁网、tmpfs独立MySQL8.0.46容器及合成非空fixture。`SET autocommit=0; START TRANSACTION; INSERT INTO commit_marker VALUES(99);` 后执行未修改原始V42，后续PREPARE在原文件58行报1064；新连接仍观察到oauth2_authorization新增10列（access_token_hash/encrypted/iv、refresh_token_hash/encrypted/iv、token_family、previous_jti、revoked_at、rotation_count）、4个非主键索引及原主键。原行结果 `1\t0`、token行7和事务标记99均保留，后续token_rotation_history/token_security_event/encryption_key_version/v_token_health未创建。从该部分状态重试原文件，14行报1060重复列access_token_hash，after快照完全相同。即使显式事务已开始，前置DDL及其前的事务标记仍可因DDL隐式提交保留，不能将后续PREPARE失败当作全文件回滚。

首次采集的错误字典也包含相同列、索引及标记99，但证据状态仍为失败；保留排序/观测断言失败，不追认整轮通过。两轮cleanup均attempted=true、remove_exit_code=0、exists_exit_code=1、container_and_anonymous_volumes_removed=true。

V42诊断新增0，不证明Flyway启动/history/checksum或完整历史升级链，不证明PostgreSQL V42、生产数据升级或生产回退。上文31项PostgreSQL有效证据属于另一探针，仅覆盖其冻结ID及合成fixture。V19直接重放1061、V42直接重放1060也不能单独将Flyway一次执行迁移的天然非幂等定性为缺陷。

### 本次收口实际检查

- `python3 -B -m unittest scripts.test.test_auth_v45_dynamic_probe_20261018 scripts.test.test_mysql_v19_v42_negative_probe`：53项、314.212s、OK；输出中的拒绝覆盖旧证据提示属于预期负向测试。
- `python3 -B -m unittest scripts.test.test_mysql_v19_v42_negative_probe`：最终重跑6项、1.761s、OK。
- `python3 -B -m unittest scripts.test.test_pg_p0_dynamic_probe`：11项、4.734s、OK。
- `bash scripts/ci/check-doc-links.sh`：扫描663份Markdown、2942个链接，断裂链接0，退出0；`git diff --check`退出0。两份目标文档当前未跟踪，另行检查全文尾随空白为0，避免仅依赖Git diff遗漏。
- 本轮Java未跑，未重跑188项历史全集，未重建数据库容器；上述数据库行为来源于已读取和复算hash的真实JSON证据。本次只补充本报告与实施方案，保留其他用户修改，未提交。

P0整体仍未准出，Java21未切换；前节列出的风险、责任和阶段前置继续保留。

## 2026-10-19 V42失败后的显式离线结构恢复

新增 [恢复入口](../../../../scripts/ci/source-archive/apex-system/src/main/resources/db/upgrade/mysql/token_v42_recover.py.source)、[真实隔离验证器](../../../../scripts/ci/source-archive/scripts/test/verify_token_v42_recovery.py.source)及[契约单测](../../../../scripts/ci/source-archive/scripts/test/test_token_v42_recovery.py.source)，操作命令见[部署指南](../../../05-operations/deployment.md)。先创建验证器并实际运行，因工具不存在得到 RED；临时证据 `/tmp/v42-recovery-red.json` SHA-256 为 `0345c8c47a4b0aa47c086167988a598b79d9b14ee2877cb208a570bea5d4804f`。随后实现工具。开发轮正则错误已修复，未将该轮失败记为通过；最终 13 项定向测试通过（2.363s），包含恢复契约、既有 consent 及 V19/V42 诊断测试。Java未构建，未宣称全仓回归通过。

最终[唯一新证据](token-v42-recovery-0d66ed866802-evidence.json)状态 `passed`，SHA-256 实算 `e9bf228428585bbf777144e59aceac4f3eedd974d92f99d2ee65882f2313f4eb`。真实 MySQL 8.0.46 共22场景：3类正例（实际原V42失败残留、干净前置结构、token缺失）均验证只读计划不改变状态、结构补齐、非空明文/token保留、已有密文及inactive密钥重入不变；19类负例覆盖缺oauth2/依赖列、列类型/默认值/生成列、字符集/触发器、错误/前缀/唯一索引、后三表不兼容、错误view及对象类型替换，并在存在待补列时拒绝后续表默认值/索引/额外列冲突，所有拒绝前后完整快照一致。实际残留场景先执行原文件确认1064、已持久化10列4索引，重试1060，再恢复。证据保留正例结构快照、计划、数据保留断言及负例拒绝结果；全部是合成数据。

容器 `apex-v42-recovery-b4092c83eb43` 使用 `--network none --pull=never --tmpfs /var/lib/mysql:rw`，inspect确认network none及tmpfs；finally清理 `remove_exit_code=0`、`exists_exit_code=1`，容器及匿名卷移除成功。证据绑定原V42 hash `df9a7c3a20b21a3be1c80ab8d2c11be37c155fee9ca5001eb1c9ceb02b75299e`、恢复工具 hash `b37ca95a985c005743fa1a7cc623d020a6d7469c76cd52810868d716e1bb7a7b`、验证器 hash `f183a05730463a2c1602e66ede6c0dc9e85f7867e44a3cdb6807768ac0e588fc`，均与当前文件实算一致。开发临时轮记录留在 `/tmp`，未覆盖旧证据。最终 `bash scripts/ci/check-doc-links.sh` 扫描663份Markdown、2956个链接、断裂0，`git diff --check`通过；新增脚本与目标文档全文尾随空白另查为0，覆盖未跟踪文件。保留用户既有改动，未提交或推送。

入口仅支持 MySQL 8.0，要求操作员确认全部写入和并发 DDL 已停止且备份已验证。默认只输出计划，显式 `--offline --apply` 才执行；完整只读预检完成前无新增 DDL。核对已存在列的类型/空值/默认值/生成属性、完整索引定义、后三表列索引集合与 view 定义；缺 oauth2 表及视图依赖列明确拒绝，token 可缺。非 InnoDB/utf8mb4、触发器、CHECK/外键等要求人工审核。仅补结构，不删除或覆盖已有非空 token/加密数据，不执行明文加密迁移；初始密钥仅不存在时插入，既有 inactive 状态不更新。

MySQL DDL 隐式提交，基础设施错误仍可能产生部分完成，须保持离线、核对后重入，不保证事务原子性或在线并发安全。历史 V42、baseline及旧证据未被本轮改写；不运行 Flyway repair/mark success/checksum 修改。补齐结构不表示原 V42 重试成功，其重复列 1060 与 token PREPARE 1064 的历史事实保留，恢复后 Flyway 失败记录及后续上线仍需 DBA 单独对账。仅验证隔离合成库，不证明真实旧制品升级链或生产恢复。实际复算 `verified_union()` 仍为 **489/586，剩97**，本次恢复新增原生覆盖为0；P0仍未准出，Java21未切换。

## 2026-10-19 PostgreSQL增量与V42恢复最终证据收口

本节为上述489/586、恢复22场景及旧工具hash之后的追加记录，历史内容与失败不回写。本轮实际调用 `verified_union()`，核对冻结基线和有效证据资格，得到 **500/586，剩86**，覆盖MySQL458、PostgreSQL42；剩MySQL70、PostgreSQL16。恢复新增原生覆盖0，另3项静态DDL不进入586分母，P0仍未准出、Java21未切换。

### PostgreSQL两批增量与剩余分布

| 保存证据 | 资格与增量 | SHA-256实算 |
| --- | --- | --- |
| [第一批](dynamic-pg-p0-448d208aed8d4db585be12204edfedac-evidence.json) | postgresql_p0_remaining，partial_diagnostic；old-sql-0561及0564–0569共7项：V18 1、V20 1、V24 5 | aeedf6bbad091a9c3300ab36c535772f486c9c90e81a862c21173787a9143631 |
| [第二批](dynamic-pg-p0-4beadc1f27904043824ff937b400e3cb-evidence.json) | postgresql_p0_final_four，passed；old-sql-0562/0563/0584/0585共4项：V19 2、V41 1、V44 1 | d9bde84430f3a20e58e6a98490d5c3fa23471278957a9bb36b820a41716ab270 |

第一批共23个旧ID，仅7项通过专用validator进入并集；另外16项函数/触发器已执行诊断，直接重放duplicate不计分，不据此判定Flyway一次执行迁移缺陷。第二批四组分别8、4、5、13场景，合计30，不是30个旧ID或Python测试。两批均为PostgreSQL17.11、network none、tmpfs隔离已核验，容器及匿名卷清理成功。本轮读取并复算保存证据，没有重跑数据库。两批新增ID互不重叠，489+7+4=500，重复证据不累加。

| 尚无合格原生覆盖的迁移 | 项数 |
| --- | ---: |
| MySQL V19 / V40 / V42 | 16 / 45 / 9，共70 |
| PostgreSQL V26 / V27 / V31 / V32 / V34 / V35 / V45 | 2 / 4 / 2 / 2 / 2 / 2 / 2，共16 |

500只表示冻结语句的限定合成fixture覆盖，不是全局语义完成数。DO分支、非空最小fixture及允许审计时间刷新的业务状态比较，不证明完整历史迁移链、生产数据、Flyway history/checksum、权限或并发安全。

### V42最终工具、31场景及历史失败

已只读复核当前恢复入口：postflight返回非空计划即拒绝完成；补齐后实际执行 `SELECT * FROM v_token_health LIMIT 0`，查询失败不能输出成功；view规范化区分带空格引用标识符；CLI在创建客户端前拒绝 `--force=true`、`-vf`等force形式及空 `--mysql-command`。恢复单文件当前实际9个测试方法，覆盖上述契约；前节“13项”是当时恢复、consent和V19/V42的组合记录，保留历史但不能解释为恢复单文件有13方法。

| 恢复证据 | 结果 | SHA-256实算 |
| --- | --- | --- |
| [最终31场景](token-v42-recovery-143e4f2373bf-evidence.json) | passed，baseline_credit=false；3恢复、28拒绝 | 9ede559419e79355c2b1c60b3e96de2560b942574f0ab1a1a0c3b3fb4ac02ca9 |
| [首轮外键fixture失败](token-v42-recovery-63e78ddff9c9-evidence.json) | failed；ERROR 1452，token_parent外键引用oauth2_authorization失败；不追认通过 | 09cdd7b06874f2d4eaed4a41d038c5d806034bf414478031ee366bc422a96b11 |

最终真实MySQL8.0.46使用禁网tmpfs隔离，清理remove退出0、exists退出1、容器及匿名卷删除成功。3正例为原V42失败残留、干净前置结构、token缺失；均passed。28负例均rejected。验证器在恢复后设置v1为inactive并加入active v2，再重入比较完整数据/schema快照不变，保留非空明文/token、密文及密钥状态。原V42执行1064、重复执行1060仍作为独立历史事实；恢复不自动加密、不修改原迁移、不执行Flyway repair/mark success或history/checksum协调，不能证明生产旧库恢复或DDL原子性。

当前恢复工具hash `4e4184ebb7cd41ccc739822395ab13b66118330387ffaf97fc10d1d2ccbe9c9b`、真实验证器hash `fe3cb12ef8997c246c4529f9f8299a797e4ea42f9ecc123a40ca93e588eeb470`，本轮实算与最终证据匹配。原V42 hash仍为 `df9a7c3a20b21a3be1c80ab8d2c11be37c155fee9ca5001eb1c9ceb02b75299e`；前节旧工具/验证器hash“当前一致”仅属当时记录，不再代表本轮文件，旧值不改写。

### 本轮实际测试范围

执行一次 `python3 -B -m unittest scripts.test.test_pg_p0_dynamic_probe scripts.test.test_auth_v45_dynamic_probe_20261018 scripts.test.test_token_v42_recovery`：**78项、348.625s、OK、退出0**。通过loader独立统计PG22、共享探针47、恢复9，合计78；不再额外重跑恢复9项并累加，数据库31/30场景也不混入Python分母。输出中拒绝覆盖旧证据的usage错误属于预期负向测试。未执行Java构建、Python全集、真实数据库重跑或生产操作。

### P0真实剩余与可独立推进项

| 仓库内可继续完成的工作 | 需要外部事实才能最终关闭的部分 |
| --- | --- |
| 按冻结ID推进86项证据；为PG函数/触发器按一次迁移语义设计分支、依赖和诊断，不强求任意直接重放幂等；保留MySQL V40/V42原失败与恢复边界 | 生产存量、历史制品及实际升级路径的验收条件；P7执行仍归P7 |
| 沿既有next_source_entry补67 partial/19 unknown调用链、条件装配；验证两域Outbox append/appendSync/claim及恢复/重试/清理的PG方言与事务行为 | 实际profile/DataSource、启用状态、数据所有者与生产消费责任；4 reviewed仅静态闭环 |
| 复核139旧差异的47候选/17版本语句/75 Flyway自管分类，以及1162待确认项；登记tenant/唯一键/NULL/账号冲突的代码事实与处置选项 | system历史副本保留/迁移/归档/清理、真实历史归属、冲突与对账规则；缺映射不能直接删除 |
| 补474配置组合有效性、两变量失败即停接入说明、三档各40组依赖责任/审阅及Handler/网关/权限/tenant/DTO/OpenAPI关联和声明分母 | REDIS_PASSWORD/DB_HOST_PROD实际使用、Nacos发布消费与注入责任，真实TLS/Redis/完整HTTP/TOTP环境；秘密不通过聊天提供 |

可先保存代码追溯、测试设计、证据入口及缺口台账，无需等待全部外部输入或另设新的批准门槛。责任人的真实归属与业务决策不能由静态工具代填。office四标志、workflow全局唯一兼容规则、MFA V58及PG生产驱动已有成果，不再整体列未修；workflow tenant闭环仍需独立验证。历史Java完整门禁成功不因本轮未重跑而改记失败，也不覆盖后续未验证环境。D01–D07按方案第16节原阶段前置确认；P6完整Javadoc/doclint与P7生产升级/repair/恢复/回退不整体前移到P0，本轮只维护风险、责任、证据与前置条件。

本轮文档检查：`bash scripts/ci/check-doc-links.sh`退出0，663份Markdown、2966条链接、0断链，仅检查本地路径和锚点；`git diff --check`退出0。三份目标文档全文尾随空白均为0；两份未跟踪方案/report末尾换行存在。首次全文脚本把deployment也要求末尾换行而退出1，实际为该文件既有末尾无换行，保留其原状，不记作全部换行检查通过。仅追加既有方案、报告及deployment说明，未改代码、历史迁移、证据JSON或旧hash，未提交或推送。

## 2026-10-19 shared_nacos/DB_HOST_PROD分段静态链核验

新增[唯一审计JSON](config-shared-nacos-chain-20261019-a67e92c4.json)，记录来源SHA-256、检索范围、分段关系、Nacos开关声明和运行事实缺口；两份旧配置JSON保持原状。当前474组合分母与该项“静态未知；仅模板实际消费时必填”的处置不变。

| 链路段 | 仓库可证明的事实 | 尚不能证明 |
| --- | --- | --- |
| 引用 | prod共享模板22/30/38/46/54行五URL引用无默认DB_HOST_PROD，前缀为五个服务名.datasource | 这些属性已被DataSource消费 |
| 发布与加载 | README说明模板不参与启动；发布章节是人工控制台/curl示例，curl例子仅common/gateway；主Compose Nacos服务只见数据卷。external-configs仅在模板注释出现，未搜到共享Data ID实际挂载 | 远端是否发布、revision、namespace/group、实际加载入口 |
| 属性绑定 | system-service声明spring.datasource，auth prod同时有spring.datasource/r2dbc，system库prod另有动态master和DB_MASTER_URL；未搜到服务名前缀的显式映射 | 按服务截取、转换、覆盖或实际绑定，DB_HOST/DB_MASTER_URL不是已证实别名 |
| Compose | 五业务service显式声明SPRING_DATASOURCE_*，共享锚点注入Nacos三开关；无DB_HOST_PROD环境键 | 外部override或真实容器环境是否补充它 |
| app.sh | 宿主export可继承；有效APEX_ENV_FILE以set -a/source加载，普通.env不自动加载 | 实际部署文件路径和Java/Spring消费关系 |
| preflight | 既有nacos-datasource-prod目标仅检查当前进程DB_HOST_PROD有效非空，拒绝DB_HOST伪别名 | 发布、渲染、DNS/数据库可达以及实际流水线已接入 |

Nacos开关不能按单个文件外推：五业务service bootstrap的config/discovery/register默认均false；system-service application及system业务库bootstrap/application默认均true，存在同名classpath资源；gateway prod三项是字面true。Compose三项默认true、namespace默认public，部分prod声明默认prod；实际属性源、profile及最终有效值未运行求值。三个开关各自独立，Compose传入false不足以单独证明Spring最终全关闭。此处记录声明差异，没有将其直接认定为生产缺陷。

本轮执行 `/tmp/apex-p0-python-20261019/bin/python -B -m unittest scripts.test.test_config_preflight scripts.test.test_collect_gateway_config scripts.test.test_local_deployment`：**25项、68.295s、0失败、0错误、0跳过、退出0**。复用真实app.sh加假Java四场景和临时合成值的真实podman-compose config渲染；Compose测试的DB_HOST_PROD不注入断言只针对system，其他服务来自静态声明核对。不读取真实.env或凭据，不启动容器、不连接Nacos/生产，不运行Java构建或Spring启动。未确认需要RED/修复的代码错误，生产配置与工具未改。

最终外部缺口限定为：模板实际采用与配置权威入口；不含秘密的Data ID/namespace/group/revision、发布/转换与加载引用；服务名前缀到DataSource/ConnectionFactory的绑定；运行profile、三个开关最终有效值；变量供给环节（发布前、宿主、APEX_ENV_FILE、容器或远端）、preflight接入与真实责任人。只需入口与责任事实，不需秘密值。静态未命中不证明部署不消费，模拟传播也不证明注入已满足；P0仍未准出，未提交或推送。

本轮审计JSON SHA-256为 `5c776266ae134d831d242f6f73d4b0ed364c55be51b46f87ac422ec140d07851`。实际解析JSON并核对20个当前来源与2份旧证据hash，22/22匹配；三份目标文件全文尾随空白与末尾换行检查通过，覆盖未跟踪文件。`bash scripts/ci/check-doc-links.sh`扫描663份Markdown、2968条链接、断链0、退出0；`git diff --check`退出0。检查只涉及本地路径/锚点与文件一致性，不代替运行配置验证。

## 2026-10-19 剩余迁移诊断与网关开关契约最终记录

本节追加于既有章节之后，旧hash、旧失败与旧时点声明均保留。已读取[剩余处置审计](migration-remaining-closure-audit-20261019-c8f4d7a2.json)，SHA-256实算与交付一致：`2cb79a786bf728a399f26f016500ea0a3079ca2da88d4763d8a65064905916b5`。状态为 `diagnostic_inventory_closed_acceptance_open`，不是新增分支通过证据。审计复算覆盖仍为 **500/586，剩86**，MySQL已覆盖458、PG已覆盖42，新增计分0；全部剩余ID已绑定既有诊断，来源hash匹配冻结基线。

| 剩余项 | 数量 | 诊断与验收限制 |
| --- | ---: | --- |
| MySQL V19 | 16 | 首次原文件执行及约束行为已有诊断；直接重放1061不单独构成一次性迁移缺陷，诊断不满足现行分支计分 |
| MySQL V40 | 45 | 原轮次partial_failure，consent非空主键改造1062保留；不拆出其余40项追认，也不将前置修复算原生闭合 |
| MySQL V42 | 9 | token存在时PREPARE 1064、失败前DDL隐式提交与重试1060已有诊断；离线恢复不改变原生资格 |
| PG V26/V27/V31/V32/V34/V35/V45 | 16（2/4/2/2/2/2/2） | 8组函数/触发器配对已有创建、依赖错误与重放诊断，CREATE TRIGGER重放重复不计分；不据此直接判Flyway一次迁移缺陷 |

迁移代理本轮报告 **28项通过、16.852s**，没有重跑数据库、没有新增容器或卷。此为代理交付结果，本次文档收口未重跑该测试，也不将28项工具测试当作86项验收。诊断清单闭合与验收关闭分开记录；不修改计分脚本、历史SQL、旧JSON或旧hash，不擅自改变验收口径。

### 网关真实YAML修复及局部验证

已核对[网关prod源码](../../../../apex-gateway/src/main/resources/application-prod.yml)：config.enabled、discovery.enabled、discovery.register-enabled分别使用 `${NACOS_CONFIG_ENABLED:true}`、`${NACOS_DISCOVERY_ENABLED:true}`、`${NACOS_REGISTER_ENABLED:true}`。方案第32节及本报告前节“字面true”是修复前时点记录；当前默认仍true，但可分别由环境占位符供值。

[GatewayNacosConfigContractTest](../../../../apex-gateway/src/test/java/com/apex/scaffold/gateway/GatewayNacosConfigContractTest.java)使用YamlPropertySourceLoader加载真实application-prod/application/bootstrap，移除StandardEnvironment宿主systemProperties和systemEnvironment，再注入合成环境源；八种独立布尔组合加无变量默认共9项。配置代理记录 **RED 7失败 → GREEN 9通过**，加既有GatewayRouteProfileConfigTest 3项，共 **12通过**，相关reactor Spotless通过，固定Oracle17.0.12/mvnd2.0.0-rc-3正确，未执行clean verify。

本次只读核对现存Surefire：Nacos契约9项，0失败/错误/跳过，0.213s；路由3项，0失败/错误/跳过，0.726s。XML记载java.home为 `/Users/eric/.sdkman/candidates/java/17.0.12-oracle`、java.version为17.0.12。RED、mvnd版本和Spotless结论按配置代理交付记录，本次未重跑；局部属性求值不代表Spring启动、真实Nacos客户端/远端覆盖或生产部署验证。

### auth双连接TLS静态差异与待核验项

已核对[主Compose](../../../../podman-compose.yml)、[auth prod](../../../../apex-auth-service/src/main/resources/application-prod.yml)和[JwtKeyRepositoryImpl](../../../../apex-auth/src/main/java/com/apex/scaffold/auth/infrastructure/repository/JwtKeyRepositoryImpl.java)。Compose auth的 `SPRING_DATASOURCE_URL` 含 `useSSL=false`，覆盖prod JDBC URL的 `useSSL=true&requireSSL=true`；prod R2DBC按DB_HOST/DB_PORT/DB_NAME拼接URL并设 `sslMode=REQUIRED`，主Compose无 `SPRING_R2DBC_*`。JwtKeyRepositoryImpl实际注入DatabaseClient并读写jwt_keys，R2DBC存在消费链。

该差异已静态确认，未证实生产故障；本轮未修改TLS策略。剩余核验是实际运行profile、Compose override和远端/环境/本地属性源、最终DataSource/ConnectionFactory及双连接目标、TLS握手和业务访问。已同步[配置文档](../../../05-operations/configuration.md)的独立Nacos开关、默认值分层和TLS说明。没有启动Spring、Nacos或数据库，没有执行Java或完整门禁；P0仍未准出、Java21未切换，生产升级/Flyway历史协调仍未验收。

本次文档检查实际执行 `bash scripts/ci/check-doc-links.sh`：退出0，663份Markdown、2987条链接、0断链；仅覆盖本地路径及锚点。`git diff --check`退出0；方案与report全文尾随空白检查无命中，配置文档既有空白保留，未新增同类问题。本次仅编辑既有方案、report与configuration三份文档，未改代码、旧JSON或旧hash，未提交或推送。

## 2026-10-19 PG触发器72场景与auth绑定最终收口

本节接续前节，保留旧计数、旧失败及历史hash。本次只编辑既有方案、report、configuration；PG数据库结果来自保存证据，Java结果来自现存Surefire及交付记录，没有重跑数据库或Java门禁。

### PG完整迁移行为证据与计分边界

[pg_p0_dynamic_probe.py](../../../../scripts/ci/source-archive/scripts/ci/pg_p0_dynamic_probe.py.source)新增 `--trigger-behavior`；[对应测试](../../../../scripts/ci/source-archive/scripts/test/test_pg_p0_dynamic_probe.py.source)新增3项，分别约束完整来源/old-ID绑定但不计旧覆盖、错误时间戳/哨兵行/catalog拒绝、仅布尔标志无观察不能通过。本次实际执行 `python3 -B -m unittest scripts.test.test_pg_p0_dynamic_probe`：**25项、14.981s、OK、退出0**。

[72场景保存证据](dynamic-pg-p0-b04da714fb6a403994cbc858ffd79299-evidence.json) SHA-256实算为 `50e03a27f273a7631b4e9bfd115fc1e9f4109d87bf312f6e9e171f56d8b3ea6c`。真实服务版本PostgreSQL17.11，容器镜像localhost/postgres:17-alpine，network none、无发布端口、tmpfs，保存的isolation_verified=true；清理remove退出0、exists退出1，容器及匿名卷移除成功。7份完整原始迁移V26/V27/V31/V32/V34/V35/V45均核对当前来源SHA-256一致；8组配对绑定16个old-ID，V27含文档与图表两组。

| 每组场景 | 已核验的行为 |
| --- | --- |
| first_execution | 完整原始文件在新schema事务提交，核对完整catalog、来源索引及有效性、触发器函数绑定/启用/BEFORE UPDATE逐行类型 |
| insert | 新增行保留显式2001年旧时间，UPDATE触发器不因INSERT执行 |
| update / explicit_timestamp | 普通更新及显式1999年旧时间更新均改为事务CURRENT_TIMESTAMP，非目标行保持 |
| zero_update / delete | 零匹配UPDATE不改快照；DELETE只删除指定行 |
| data_rollback | UPDATE混合CREATE TABLE后故意除零，数据及DDL均回滚至完整先前快照 |
| ddl_rollback | 完整原始迁移后故意除零，完整DDL回滚且预置哨兵表/数据保留 |
| duplicate_replay | 原函数/触发器配对重放产生预期already exists错误，事务回滚后快照不变；不是完整文件重放成功 |

8组各9场景，共72，保存结果均true且有观察。本次在内存中重跑 `finalize_trigger_behavior()` 的逐观察断言，仍为passed、validated_old_ids 16、scenario_count 72，不改写JSON。绑定ID为old-sql-0570–0583及0586–0587，不将72场景当作72个旧ID或Python测试。

独立调用原 `verified_union()` 并按冻结ID重算：**500/586，剩86**，22份合格证据；已覆盖MySQL458、PostgreSQL42，剩MySQL70、PostgreSQL16。上述16个行为ID全部仍在remaining，`coverage.verified_old_ids=[]`、new_verified_count=0。PG剩16项已有更强行为证据，但独立行为模式不属于旧absent/present/replay三分支validator，不改计分规则、不追认旧失败、不据一次性CREATE TRIGGER重放duplicate认定Flyway迁移缺陷。完整升级链、并发/权限拒绝、真实生产数据及Flyway history/checksum未验证；P0仍未准出。

### auth真实Compose锚点、环境源与Binder契约

已核对[AuthDataSourceConfigContractTest](../../../../apex-auth-service/src/test/java/com/apex/scaffold/authservice/AuthDataSourceConfigContractTest.java)：SnakeYAML读取真实主Compose并解析YAML合并锚点，移除宿主系统属性和环境源，再注入隔离 `SystemEnvironmentPropertySource`；真实prod/application/bootstrap由YamlPropertySourceLoader加载，通过Spring Binder绑定DataSourceProperties与R2dbcProperties，不创建数据库连接。

现存Surefire为 **7项、0失败/错误/跳过、0.797s**；[驱动可用性测试](../../../../apex-auth/src/test/java/com/apex/scaffold/auth/infrastructure/persistence/R2dbcDriverAvailabilityTest.java)为 **3项、0失败/错误/跳过、0.284s**，共10项通过。XML确认java.home为 `/Users/eric/.sdkman/candidates/java/17.0.12-oracle`、java.version为17.0.12；mvnd2.0.0-rc-3正确及相关reactor Spotless通过按本轮交付记录，本次未重跑。没有连接DB，不将驱动发现/属性绑定解释为应用启动。

当前[Compose](../../../../podman-compose.yml)公共DB_HOST=mysql、DB_PORT=3306、DB_USERNAME/DB_PASSWORD已由锚点注入，auth另设DB_NAME=apex_auth。双客户端目标均为 `mysql:3306/apex_auth`，用户与凭据一致；前节“没有SPRING_R2DBC_*”只描述显式键缺省，不能延伸为当前目标错误。JDBC `useSSL=false` 与R2DBC `sslMode=REQUIRED` 的静态差异仍真实存在，本轮没有配置改动。

七项契约还覆盖：直接prod DB变量时两客户端一致且TLS安全默认；缺DB定位变量时默认localhost:3306/apex_auth；缺凭据时Binder保留 `${DB_USERNAME}` / `${DB_PASSWORD}`，strict required属性解析抛IllegalArgumentException；仅JDBC变量、移除公共DB变量的负向输入会令R2DBC回到localhost，但不是当前Compose；显式SPRING_R2DBC_URL/USERNAME/PASSWORD可覆盖DB变量派生值。缺凭据的绑定行为不证明完整启动结果。实际握手、远端配置覆盖、运行profile及Flyway历史尚未验证，详见[配置补充](../../../05-operations/configuration.md)。

### 本次实际收口检查

PG定向25项测试及覆盖独立复算已完成。实际执行 `bash scripts/ci/check-doc-links.sh`：退出0，663份Markdown、3003条链接、0断链，仅覆盖本地路径与锚点；`git diff --check`退出0。本次未修改代码、配置、原迁移、原计分脚本或旧证据，未提交或推送。

## 2026-10-19 V19失败残留与auth真实TLS核验

### V19部分提交、事务及重放边界

[失败残留证据](mysql-v19-failure-residue-20261019-1850-evidence.json) SHA-256为 `15f680eebf3710b1e7c50a558271953b906c147f7203583f0ad5908c350ed031`。隔离MySQL8.0.46的22个场景全部通过；[探针](../../../../scripts/ci/source-archive/scripts/ci/mysql_v19_v42_negative_probe.py.source)及[对应单测](../../../../scripts/ci/source-archive/scripts/test/test_mysql_v19_v42_negative_probe.py.source)补充残留状态断言，8项Python单测通过，新增测试曾先失败再通过。

完整V19末尾复合外键因跨租户数据报1452时，先前种子部门清理、管理员解绑、NOT NULL与复合唯一索引已持久化，外键未建立，原跨租户数据保留；autocommit与显式事务场景均保存这些观察。原文件重放报1061且快照不变，不能将失败后的数据库视为未迁移状态。另在先清理种子再遇残余NULL的1644 SIGNAL路径中，autocommit保留清理，显式事务在失败客户端断连后回滚；后者不证明SIGNAL自身自动回滚。复合唯一索引由catalog名称、唯一属性及列序证明，PRIMARY(id)冲突1062不作为该索引生效证据。

证据绑定原V19 SHA-256 `f245b680ef5b4c84ade1e5384fe2b3fdb43681ebe3b23cb59ec1305789d7f701` 和baseline SHA-256 `8f1402d62caf444d2d7343aa1b8f69ee6031e788dcf9c6d827eaf80d33eed254`。隔离容器及匿名卷清理成功，remove退出0、exists退出1。本轮使用合成fixture和mysql CLI，不证明真实旧库的完整Flyway升级，不修改历史SQL或追认原三分支失败。

### auth双驱动真实TLS矩阵

[真实TLS证据](auth-mysql-tls-contract-20261019-2336-evidence.json) SHA-256为 `4e0a64b92786e2676e9e3b8f3f95afed8f91b05a458662745015d962a298a0ab`；[集成测试](../../../../apex-auth-service/src/test/java/com/apex/scaffold/authservice/AuthMySqlTlsContractIntegrationTest.java)最终源码SHA-256为 `1196a05634b7db3a91d3616e857e834fd383d86ed3efcb33e3b8dbf0fd65ec23`。使用Oracle17.0.12、mvnd2.0.0-rc-3、MySQL8.0.46、Connector/J8.4.0及r2dbc-mysql1.4.2。最终格式化后重新编译执行的命令如下。

```bash
source scripts/build/init-build-env.sh &&
APEX_AUTH_MYSQL_TLS_IT=true mvnd -T4 -B -pl apex-auth-service -am \
  -Dtest=AuthDataSourceConfigContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test=AuthMySqlTlsContractIntegrationTest \
  -Dfailsafe.failIfNoSpecifiedTests=false \
  test-compile surefire:test failsafe:integration-test failsafe:verify
```

结果BUILD SUCCESS：7项配置契约（0.717s）与3项TLS集成场景（48.01s），失败、错误、跳过均0。auth-service Spotless检查通过，59 files clean。

| MySQL服务端 | Compose JDBC useSSL=false | prod JDBC要求SSL | prod R2DBC REQUIRED | 禁TLS R2DBC对照 |
| --- | --- | --- | --- | --- |
| TLS可用 | 明文成功 | 加密成功 | 加密成功 | 明文成功 |
| TLS关闭 | 明文成功 | SSL错误 | SSL错误 | 明文成功 |
| 强制安全传输 | insecure transport拒绝 | 加密成功 | 加密成功 | insecure transport拒绝 |

所有成功连接均在同会话验证DATABASE()为隔离库，并检查Ssl_cipher及Ssl_version两状态的空/非空；未保存具体cipher/version字符串。属性来自真实Compose及prod YAML，仅替换隔离数据库目标。REQUIRED及useSSL/requireSSL证明加密要求，不证明CA或主机名身份验证。

首轮因指定镜像标签缺失而3项IT失败，保留失败记录；远端pull被本机代理拒绝后，为已有同一镜像临时补标签并重跑成功。最初Spotless差异仅在新IT，精确格式化后复查及重新执行通过。各场景UUID容器、网络及tmpfs均清理，镜像原ID和localhost/mysql:8.0标签恢复，未删除其他资源。

### 准出边界

原有效覆盖仍为**500/586，剩86**，诊断证据不纳入原absent/present/replay资格。真实旧制品、脱敏schema_history、完整Flyway升级及checksum协调、实际profile/Compose override/Nacos远端覆盖仍待核验。未执行全仓clean verify、完整Spring/Compose启动或CA/主机名身份验证；P0仍未准出，Java21未切换。未修改生产TLS策略、原迁移或旧证据，未提交或推送。

本轮文档检查：`bash scripts/ci/check-doc-links.sh`退出0，663份Markdown、3014条链接、0断链，只验证本地路径及锚点。首次`git diff --check`发现配置说明末尾多余空行，已移除；最终复查退出0。

## 2026-10-19 覆盖复算与多文件Compose覆盖契约

[本轮证据](compose-override-coverage-audit-20261019-evidence.json)记录只读复算、配置来源hash、26项测试和验证限制。原verified_union与独立并集复算一致：22份合格证据、845次覆盖、去重500项、分母586、剩86（MySQL70、PG16），来源与冻结baseline一致。既有PG partial_diagnostic中的7项constraint已计分、16项trigger仍未计分；新V19与PG行为证据不增加计分。未发现可直接认定为真实旧版本的制品或替代完整升级验收的history。

新增[多文件渲染测试](../../../../apex-tools-deployment/src/test/java/com/apex/scaffold/tools/deployment/ConfigPreflightTest.java)含4子场景，单跑1项通过23.495s。随后实际执行：

```bash
/tmp/apex-p0-python-20261019/bin/python -B -m unittest -v \
  scripts.test.test_config_preflight scripts.test.test_local_deployment \
  scripts.test.test_collect_gateway_config
```

26项通过、83.773s、无跳过；日志SHA-256为 `675f4800ca2237f961c7f99a9af912b64e66aa484e545921b9fec1b9251af16b`。podman-compose1.6.0真实主文件加临时override，仅覆盖auth DB_HOST/DB_NAME仍保留原显式JDBC URL，形成与DB变量派生目标分叉；同时显式覆盖双URL后渲染目标对齐。公共凭据、安全变量及Redis/Nacos锚点保留，system目标不变；四个被测DB变量的非空宿主值优先于env文件。合成URL无TLS参数，仅证明目标合并，不得作为部署模板。测试只运行config，不启动容器、不读取真实.env。现有compose_check CLI仍只支持单主文件。

collector实测138来源、23路由、474配置disposition；shared_nacos/DB_HOST_PROD仍静态未知，有5个无默认引用。旧config-shared-nacos-chain证据20个源hash中19个一致，gateway prod已改为环境占位符，旧字面true描述不适用；app.sh加载环境文件当前在62–66行，旧行号39–43不准确。保留旧证据，通过本节纠正时点与引用。当前TLS测试源码hash仍为1196a05634b7db3a91d3616e857e834fd383d86ed3efcb33e3b8dbf0fd65ec23，匹配上轮证据。

本轮未修改生产配置或历史SQL，未运行Java构建、数据库探针及完整服务启动。远端Nacos Data ID、namespace/group/revision、实际profile和覆盖优先级，以及完整Flyway升级仍待外部运行证据；P0未准出，Java21未切换。

最终执行本地文档链接及空白检查：663份Markdown、3019条链接、0断链，`git diff --check`退出0。新增未跟踪测试另按全文检查，无空白诊断。未提交或推送。

## 2026-10-20 V42部分提交复验与Binder覆盖契约

[迁移新证据](mysql-v19-v42-20261020-observation-8f78ab31e34f-evidence.json) SHA-256为 `88387ee1b2684558376848cef0bd7c51194098917e6e243c4de1985f98fa96d5`。实际执行负向探针与恢复单测共17项通过；V19为22个诊断场景、V42为15个。V42 PREPARE报1064后事务标记99、oauth新增10列/4索引仍在，原token行7保留，后续三表和视图未创建；重放1060且状态不变。源V42 SHA-256为 `df9a7c3a20b21a3be1c80ab8d2c11be37c155fee9ca5001eb1c9ceb02b75299e`，历史SQL未改。

恢复回归仅actual_v42_failure完成dry-run、apply和重入；clean_predecessor的apply在90秒超时，余29项未执行。39个场景条目中完整完成38个，整体partial_failure。保留首轮汇总超时及就绪检查失败记录，不能以本轮覆盖前轮31场景成功，也不追认为本轮完整恢复成功。隔离MySQL8.0.46容器全部清理、卷集合不变；只用合成fixture，样例保留不证明token加密转换。命令和逐观察结果见JSON，未修复Flyway history。

[Binder新证据](auth-compose-binder-override-20261020-evidence.json) SHA-256为 `7659543289388464aaa9bc57fcd952f3741cb2c5c188d4a17c37fbed99f64e64`。[AuthDataSourceConfigContractTest](../../../../apex-auth-service/src/test/java/com/apex/scaffold/authservice/AuthDataSourceConfigContractTest.java)保留原7项新增2项，最终9 tests、0 failures/errors/skipped、2.818s。Oracle17.0.12、mvnd2.0.0-rc-3，先清理漂移daemon，目标格式调整后Spotless和定向测试均通过。

```bash
source scripts/build/init-build-env.sh &&
mvnd -T4 -B -pl apex-auth-service spotless:check &&
mvnd -T4 -B -pl apex-auth-service -am \
  -Dtest=AuthDataSourceConfigContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

真实Compose解析后叠加合成覆盖Map、真实prod/application/bootstrap经Spring Binder：仅公共DB目标覆盖使JDBC原目标与R2DBC新目标分叉，原TLS参数及凭据保留；显式覆盖双URL后目标一致，JDBC useSSL=true/requireSSL=true、R2DBC sslMode=REQUIRED明确断言。测试源码SHA-256为 `3becf4617a0d98255482afcb27f73daf75dcb34202072fe0e330e3aecde6514c`。本轮首次新增契约即通过，不声称RED/GREEN；不启动应用、不连接DB、不访问Nacos。

原覆盖沿用500/586、剩86，本轮诊断新增计分0；未重跑全仓verify或真实TLS，未改生产策略、历史SQL及旧证据，P0未准出、Java21未切换。

配置说明同步后执行 `bash scripts/ci/check-doc-links.sh`，退出0：扫描663份Markdown、3026个链接、0个断链；`git diff --check` 退出0。该链接检查覆盖本地路径与Markdown锚点，不验证外部URL或运行配置。

## 2026-10-20 V42恢复超时根因与31场景重跑

上一节记录的 `clean_predecessor` apply 90秒超时已完成根因判定与验证器装置修正，并取得一次完整恢复回归结果。本节不修改上一节任何结论、hash 或 partial_failure 记录。

### 超时根因与装置修正

[根因与重跑证据](mysql-v42-timeout-rootcause-and-suite-rerun-20261020-evidence.json) SHA-256 为 `a88e4f7466c161ce6f2f444e3cfe9b4977c99863c1696fcfa47ed68007bb9978`。判定为**验证器装置超时预算过紧**，不是恢复逻辑缺陷或数据库锁：恢复工具 hash `4e4184ebb7cd41ccc739822395ab13b66118330387ffaf97fc10d1d2ccbe9c9b` 与 2026-10-19 31场景全通过证据完全一致，历史迁移 SQL hash 仍为 `df9a7c3a20b21a3be1c80ab8d2c11be37c155fee9ca5001eb1c9ceb02b75299e`，本时间窗内无代码回归。

单次恢复调用内部按条串行执行数十次 `podman exec`。本轮 900 次空载往返采样的百次窗口均值为 220.8–302.9ms、最差 1412.9ms；整次调用耗时对往返波动高度敏感，90s 预算过于贴近实际分布。需要明确：本次采样**未重现**早前会话中往返延迟随调用次数单调上升的现象，因此不把“累积劣化”作为可复现结论，只记录往返方差与宿主负载敏感性。

[验证器](../../../../scripts/ci/source-archive/scripts/test/verify_token_v42_recovery.py.source)仅调整超时装置：`command()` 增加 `timeout` 参数并保留 90s 默认；`recover()` 单次调用独立放宽到 300s；`finally` 中 `podman rm` 用 300s、`container exists` 用 120s，并捕获 `TimeoutExpired` 记录 `remove_timed_out`/`exists_timed_out`，避免清理超时中断证据落盘。断言与 31 场景集合零改动。修改后 hash 为 `70d38665df5103ccf070c343d3dd0fb69dc27d1333c3c8f12778cc1be5138f9e`。

### 完整31场景重跑结果

命令 `python3 -B scripts/test/verify_token_v42_recovery.py --output docs/06-testing/jdk21-unified-auth/20261016-p0/token-v42-recovery-suite-20261020-evidence.json`，退出0。

[新证据](token-v42-recovery-suite-20261020-evidence.json) SHA-256 为 `2708b858c1b5f41a7a973d4f4e58113072880c1b45d6ba1a100189f1e6fd0bad`，状态 `passed`、`baseline_credit=false`、31场景（含 `view_invoker`）：3正例 `actual_v42_failure`、`clean_predecessor`、`token_absent` 全部 passed，28负例全部 rejected。开始 `2026-10-20T02:21:14Z`、结束 `2026-10-20T02:32:38Z`，684.59s，42次恢复调用平均 16.3s。隔离容器 `--network none`、`--tmpfs /var/lib/mysql`、MySQL 8.0.46，清理 `remove_exit_code=0`、`exists_exit_code=1`、容器及匿名卷移除成功。

定向单测 `python3 -B -m unittest scripts.test.test_token_v42_recovery scripts.test.test_mysql_v19_v42_negative_probe -v`：**17项、2.850s、OK、退出0**。

### 边界

该证据是恢复回归通过，不是历史迁移原生验收：不改变 V42 的 1064/1060 历史事实，不给 absent/present/replay 三分支计分，不验证 Flyway history/checksum 协调、token 明文加密迁移、PostgreSQL V42 或生产升级/回退。覆盖仍为 **500/586，剩86**，本轮新增计分0。早前 90秒超时记录与 `token-v42-recovery-8548f6b5ee86` 的 partial_failure 结果保持原状，不追认为通过。P0 仍未准出，Java21 未切换；未执行 Java 构建、全仓 clean verify 或服务启动。

独立调用 `verified_union()` 复算：500项、分母586、剩86，与本节前后一致。`bash scripts/ci/check-doc-links.sh` 退出0，663份Markdown、3031条链接、0断链；`git diff --check` 退出0；两份新JSON、验证器与两份目标文档全文无尾随空白且以换行结尾。临时诊断脚本仅用于本轮定位，未加入仓库，运行后已从 `/tmp` 删除；隔离容器与匿名卷无残留。

## 2026-10-20 独立一次性迁移验收落地

用户批准后新增[独立验收账本](migration-one-time-acceptance-20261020.json)、[复算工具](../../../../scripts/ci/source-archive/scripts/ci/migration_one_time_acceptance.py.source)及[拒绝性测试](../../../../scripts/ci/source-archive/scripts/test/test_migration_one_time_acceptance.py.source)，同步主方案第39节。账本 SHA-256：`e7069952d1faac8ba30455f579658e332f2bed17a39154cfd448d000c3d3ae63`。冻结审计 SHA-256 仍为 `2cb79a786bf728a399f26f016500ea0a3079ca2da88d4763d8a65064905916b5`，baseline 仍为 `8f1402d62caf444d2d7343aa1b8f69ee6031e788dcf9c6d827eaf80d33eed254`。

全部86个ID分别绑定冻结来源/hash、原位置和statement IDs、诊断、首次执行、失败残留、显式修复/恢复。证据引用包含文件hash、JSON pointer和观察内容hash；32项在观察到的首次执行路径无需修复，明确记录该局部边界。账本没有整项accepted，状态为 `local_evidence_bound_full_upgrade_open`。

| 局部证据分类 | 数量 | 说明 |
| --- | ---: | --- |
| 首次执行已验证 | 32 | MySQL V19 16项、PG触发器16项；不把预期重复DDL错误认作Flyway首次执行失败 |
| 前置修复后已验证 | 45 | MySQL V40；consent原1062观察的前后结构/数据相同，其余40项局部成功不能追认整轮成功；显式修复后未改完整文件执行/重放通过 |
| 结构恢复后已验证 | 9 | MySQL V42；31场景恢复证据，原1064、隐式提交与重试1060保持原结论 |
| 完整旧制品Flyway升级链未验证 | 86 | 与以上局部分类并存，`full_upgrade=unverified` |

实际执行：先运行5项测试得到缺实现的RED；实现工具并 `--write` 后，`python3 -B -m unittest scripts.test.test_migration_one_time_acceptance -v` 5项通过、57.547s、退出0。随后追加观察pointer/证据hash替换拒绝测试，单项定向运行通过、2.579s、退出0。测试覆盖缺失/重复/混入已覆盖ID、来源/证据/观察hash漂移、缺文件/观察、移除修复证据及无据提升状态；54个修复/恢复ID逐一检查缺引用拒绝。

`python3 -B scripts/ci/migration_one_time_acceptance.py` 退出0，复算86项精确集合；只读调用原 `verified_union()`，结果 **500/586，剩86，新增计分0**。工具还校验保存执行记录中的consent前置SQL及V42恢复工具/验证器hash。没有修改 `verified_union()`、`remaining_closed`、历史SQL或旧证据，账本文件名不进入原证据扫描。

本轮 `bash scripts/ci/check-doc-links.sh` 退出0（追加本节后最终扫描663份Markdown、3038条链接、0断链）；`git diff --check` 退出0。新文件与文档还单独检查尾随空白和末尾换行，避免未跟踪文件被git diff遗漏。

确切剩余阻塞：实际旧制品、前驱数据库及完整Flyway升级链未执行；V40前置修复和V42恢复仍需独立协调Flyway history/checksum；历史token明文加密、生产数据核对与真实升级/回退未验证。本轮仅复用保存证据，不重复数据库失败，不接生产、不读凭据、不运行Java构建或全仓verify；P0未准出、Java21未切换，无commit/push。

## 2026-10-20 合成前驱库双库Flyway定向验收

本次新执行窗口为 `2026-10-20T11:30:55+0800` 至 `2026-10-20T11:34:23+0800`。使用当前源码 V1–V61 构造合成前驱库，再升级至 V62；没有实际旧发布制品或可恢复前驱快照，因此不将结果记为真实历史完整升级验收通过。第39节及上节“未运行Java”的描述仍对应其原轮次。

[本轮独占证据](synthetic-predecessor-upgrade-20261020-a13f7c9e-evidence.json) SHA-256 为 `b2fb58d9a686ac8e0b07d0c5b966c4ecd1ca3151ffd1239c70993791580e0039`，含完整命令、镜像ID、实际版本、源码manifest、断言边界、日志hash及清理记录。原始执行日志 `synthetic-predecessor-upgrade-20261020-a13f7c9e-targeted-failsafe.log` SHA-256 为 `781fb9a823176a42f3719922c97adc88d0d674e9c237a2a71c991df48c6e44c9`；[Failsafe XML](synthetic-predecessor-upgrade-20261020-a13f7c9e-failsafe.xml) SHA-256 为 `9ec6a5316ab02ed111eaaa42d0f3de208d5d4b70d5e86286142c38be5e22bbb0`，报告mtime位于本次执行窗口，stdout中的随机数据库名与本轮一致。13份保存产物hash及129个源码/账本hash均已复核。

实际构建命令（同一shell先激活固定环境）：

```bash
source scripts/build/init-build-env.sh && mvnd -T4 -B -pl apex-system -am test-compile failsafe:integration-test failsafe:verify spotless:check -Dskip.unit.tests=true -Djacoco.skip=true -Dit.test=SystemDualDatabaseMigrationIntegrationTest -Dfailsafe.failIfNoSpecifiedTests=false
```

工具链为 Oracle JDK `17.0.12-oracle`、mvnd `2.0.0-rc-3`、Flyway `10.15.0`，先以正确环境执行 `mvnd --stop`。10个选中reactor项目均BUILD SUCCESS，wall time 02:55 min；test-compile实际执行，上游生产/测试类为up to date，system重新编译237个测试源码（release 17），不宣称clean rebuild。10个项目的Spotless check成功。

| 数据库实际版本 | 定向测试 | Failures / Errors / Skipped | case耗时 |
| --- | ---: | --- | ---: |
| MySQL 8.0.46 | 1 | 0 / 0 / 0 | 56.767s |
| PostgreSQL 17.11 | 1 | 0 / 0 / 0 | 76.849s |

套件共2项、133.696s。两库均完成61项前驱迁移及1项V62升级，validate、pending为空、重复migrate零执行、62个连续版本均applied；断言还包括合成工作流ACTIVE与node_definition保留、15项索引前缀、14表及1视图、安全列、keycloak_sub长度255、四项NOT NULL及DRAFT默认值、workflow.version单行及值、七项权限角色授权、workflow/OAuth列、微信绑定回填及两个菜单。MySQL预期NULL负例验证V62拒绝标记、NULL保留且off_sync_history.update_time未创建，再恢复ACTIVE并沿用既有fixture内Flyway repair后重试；repair只作用于本轮合成库，未repair真实history。

只读列出已有镜像后按实际ID运行，未拉取；两个唯一命名容器均 `--pull never --network none`、数据目录tmpfs、无端口发布，仅使用本轮合成密码。测试最小适配为可选 `APEX_TEST_MYSQL_PORT` / `APEX_TEST_POSTGRES_PORT`，JDBC使用宿主127.0.0.1回环代理经podman exec进入容器，未改断言。结束后两个容器精确 `podman rm -f -v` 均退出0，代理监听关闭；[清理前](synthetic-predecessor-upgrade-20261020-a13f7c9e-resources-before.json)与[清理后](synthetic-predecessor-upgrade-20261020-a13f7c9e-resources-after.json)的容器ID及卷名称集合相同（两文件SHA-256均为 `19509ee3d71405b57e0537e2ed23d377e5cd4f2d89fdf67e1da3fbb6458d5855`），没有删除其他资源。

只读复算 `python3 -B scripts/ci/migration_one_time_acceptance.py` 退出0：**500/586，剩86，新增计分0**；32首次执行、45前置修复、9结构恢复分类不变，全部86项 `full_upgrade=unverified`。独立账本SHA-256仍为 `e7069952d1faac8ba30455f579658e332f2bed17a39154cfd448d000c3d3ae63`。历史SQL、原计分规则、旧证据及独立账本未修改。

真实旧制品/前驱快照完整升级、V40/V42真实history/checksum协调、历史token明文加密、生产升级/回退、服务启动及Auth MFA仍未验证；未执行单测、ArchUnit、PMD、JaCoCo或全仓clean verify，不计为通过。P0未准出、Java21未切换，无commit/push。

本轮文档链接检查 `bash scripts/ci/check-doc-links.sh` 退出0：663份Markdown、3045条链接、0断链；`git diff --check` 退出0。两份目标文档、本轮新证据JSON及适配测试另行检查尾随空白和末尾换行，避免未跟踪文件被git diff遗漏；原始日志保持原字节，不进行格式整理。

## 2026-10-20 按项目目录约定定位历史升级材料

用户授权根据项目实际情况选择目录。本轮选择仓库根目录作为有界查找范围，并根据 `scripts/backup/db-backup.sh` 第34行与 `scripts/release/release.sh` 第39行，确认数据库备份约定为根目录 `backups/`，发行产物约定为根目录 `dist/`。两目录经直接检查均不存在；候选 `release/` 也不存在。目录选择已完成，未为制造材料齐备的表象新建空目录。

只读检索包含被Git忽略的文件，覆盖 SQL gzip、dump、backup、tar、tgz、zip、其他压缩后缀及大小写变体，并补查 manifest、checksum、provenance、SHA256SUMS 与 Flyway history 文件名。未找到数据库快照或独立原始 `flyway_schema_history` 导出。命中的六份归档按路径/文件名分别属于 Karabiner 配置备份、系统诊断日志、三份HTML分享包及 k8s 图标资源；未展开其内容。日志压缩包、源码迁移及 target 中迁移副本均不作为快照。

JAR 候选位于各模块 `target/` 和 `target/project-local-repo/`，版本均为1.3.1；system构建属性为standard/1.3.1。它们只能作为工作区构建候选，未发现旧发行来源与数据库部署关联证明，也未宣称其与当前源码逐字节一致。OpenAPI清单存在1.2.0-7fa3ebc归档，但只有API文档，未找到配套发行包、脱敏快照或原始history。已有schema alignment清单明确为test_only，不能补足真实历史材料。

本次结论仅覆盖本仓库文件检索，不涉及外部存储、远端Release或未展开归档内部。未读取.env、密钥及业务数据，未连接数据库、运行备份/恢复、执行repair或重复合成测试。真实历史升级仍缺：可确认来源的旧发布制品、可恢复且确认脱敏的前驱快照、同库原始Flyway history及三者关联信息；因此未执行真实恢复与升级，不能记录通过。原覆盖 **500/586、剩86、新增计分0** 及86项 `full_upgrade=unverified` 保持不变，P0未准出、Java21未切换。本轮仅追加本节调查记录。

## 2026-10-20 用户确认未部署后的适用范围更正

用户确认项目从未部署、当前仅为新开发。此前以存量部署为前提要求旧发行制品、脱敏前驱快照、原始生产Flyway history及历史token转换的阻塞判断不再适用于当前交付；这些场景登记为当前不适用，不记为测试通过。最新准出适用范围见[方案第41节](../../../02-architecture/jdk21-unified-auth-multitenancy-implementation-plan.md#41-未部署新开发项目的验收范围更正2026-10-20)。此前查找结果和原始证据保留，不补造历史材料。

后续按全新库首次部署核验完整迁移、种子与权限、配置及服务启动，按阶段验证身份/租户隔离、Java21兼容和质量门禁；上线前仍需使用新系统测试数据验证备份恢复及失败处置。已有双库合成迁移证据只按原执行范围复用。原生统计500/586、剩86与独立账本hash不变；其中full_upgrade未验证字段不提升为通过，当前历史部署场景不再构成准出必需项。P0尚未重新完成适用项审核，不自动准出，Java21未切换。本轮仅调整文档，未执行数据库或Java测试。

<a id="2026-10-21-o-base首批安全前置修复与定向回归"></a>

## 2026-10-21 O-BASE首批安全前置修复与定向回归

本批落实方案42.7的四类局部修复：SSE租户/账户双维隔离及可信主体取值；workflow我的任务缺Reactor租户时拒绝；office日历静态路径优先；监控同步系统采集隔离到general阻塞池。没有新增业务API、迁移或前端，不声称O-BASE整体完成。

实际使用Oracle JDK17.0.12-oracle与mvnd2.0.0-rc-3，每条构建命令先在当前shell加载初始化脚本。最终格式化后回归命令为：

```bash
source scripts/build/init-build-env.sh && mvnd -T4 -B -pl apex-system,apex-office-service,apex-workflow-service -am -Dtest=NotificationServiceIsolationTest,NotificationBroadcastListenerTest,NotificationSseHandlerTest,MonitorDashboardHandlerTest,NoBoundedElasticArchGuardTest,OfficeCalendarRouterTest,OfficeRouterRegressionTest,OfficeCalendarHandlerTest,WorkflowQueryServiceTest,WorkflowServiceTest,WorkflowDatabaseHttpIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true test
```

最终执行日志位于本机临时路径 `/tmp/apex-o-base-final-tests-20261021.log`，SHA-256为 `7b97c17938659b03d9ee7cb880d036ab879546abfc23d3f1e95fbf1629174b16`；2026-10-21T17:20:09+08:00结束，BUILD SUCCESS。临时日志未作为持久证据入库，清理后需重新执行，不把hash当作日志内容替代物。

| 模块 | 实际测试数 | Failures / Errors / Skipped | 范围 |
| --- | ---: | --- | --- |
| infrastructure-messaging | 9 | 0 / 0 / 0 | 通知隔离4、广播消费者5 |
| workflow | 32 | 0 / 0 / 0 | 查询13、服务19（嵌套套件去重计数） |
| office-service | 35 | 0 / 0 / 0 | 日历Handler21、真实Router4、路由回归10 |
| workflow-service | 3 | 0 / 0 / 0 | WorkflowDatabaseHttpIntegrationTest，H2/HTTP定向集成 |
| system | 8 | 0 / 0 / 0 | SSE Handler4、监控3、NoBoundedElastic架构检查1 |
| 合计 | 87 | 0 / 0 / 0 | 不与此前代理运行数量相加 |

WorkflowDatabaseHttpIntegrationTest本次由Surefire显式选中，不代表完整Failsafe通过；没有实际执行记录的WorkflowHandlerTest不计入。SSE Handler测试主要验证响应创建和拒绝路径，不是实际HTTP长连接或JWT端到端验收。

定向格式检查首先因spotlessFiles误用glob而失败（`Dangling meta character '*'`）；改正为正则后暴露实际排版差异，再仅对12个相关文件执行apply/check：

```bash
source scripts/build/init-build-env.sh && mvnd -T4 -B -pl apex-infrastructure-messaging,apex-system,apex-office-service,apex-workflow '-DspotlessFiles=.*(NotificationService|NotificationBroadcastListener|NotificationServiceIsolationTest|NotificationBroadcastListenerTest|NotificationSseHandler|NotificationSseHandlerTest|MonitorDashboardHandler|MonitorDashboardHandlerTest|OfficeCalendarRouter|OfficeCalendarRouterTest|WorkflowQueryService|WorkflowQueryServiceTest)\.java' spotless:apply spotless:check
```

四模块共12文件检查成功，9文件发生格式整理。日志 `/tmp/apex-o-base-format-green-20261021.log`，SHA-256为 `659e3c1af9359392326aeb60f3abfb7af835d7138254a59146f779562da2ed63`，结束于2026-10-21T17:17:53+08:00。没有全量格式化其他用户修改；源码完成后git diff --check退出0。

剩余安全边界：workflow身份中继仍须验真X-User-Id/X-Tenant-Id，按ID访问和办理人/PENDING校验尚不完备；日历路由回归不证明输入userId可信或权限完整；通知只验证本地流与Mock消费者，不证明真实RocketMQ、多实例、提交后投递及断线补偿。监控只覆盖system采集，不证明其他指标口径或生成器边界。

本批未执行全仓clean verify、PMD、完整Failsafe/JaCoCo、真实双库workflow隔离、五域首次部署、真实JWT/MQ/平台链路；缺条件的场景保持not-run。P0仍需表权威/消费者、配置/依赖责任及适用项审核，D01–D07按原阶段确认，不能用“继续执行”替代许可或产品契约决策。历史500/586统计及原证据hash不变，P0未准出、Java21未切换，无commit/push。


<a id="2026-10-21-workflow任务操作与独立jwt验签增量回归"></a>

## 2026-10-21 workflow任务操作与独立JWT验签增量回归

本记录对应[实施计划42.8](../../../02-architecture/jdk21-unified-auth-multitenancy-implementation-plan.md#428-workflow任务操作与独立jwt验签增量记录2026-10-21)，追加更新42.7之后的workflow进度；上述首批记录及历史证据保持原样。本批仍使用Oracle JDK17.0.12-oracle与mvnd2.0.0-rc-3，未切换Java21。

任务操作complete、reject、rollback、transfer、delegate已统一验证任务存在、PENDING状态、非空办理人与当前操作者匹配，拒绝路径不继续办理；reject实例ID使用错误已修复。WorkflowServiceTest RED执行69项，Failures 37、Errors 0、Skipped 0；GREEN执行69项，Failures/Errors/Skipped均0。该证据证明本批操作前置条件，不证明逐ID租户隔离、多步写入事务原子性或并发安全已完成。

workflow已独立验证RS256 JWT，要求exp、ACCESS类型、正userId及非空tenant，认证身份仅取签名内容，忽略X-User-Id/X-Tenant-Id身份头。HMAC兼容必须显式开启，RSA模式不降级到HMAC；密钥注入已同步application配置、.env模板、Compose及configuration文档。IdentityRelayAuthenticationFilterTest RED执行10项、9失败；途中遇到共享类缺失，经定向clean test后，GREEN为10项全部通过。共享类缺失属于中途构建问题，不计作JWT业务断言失败或额外通过项。

最终组合定向回归实际命令为：

```bash
source scripts/build/init-build-env.sh && mvnd -T4 -B -pl apex-workflow-service -am -Dtest=WorkflowServiceTest,WorkflowQueryServiceTest,IdentityRelayAuthenticationFilterTest,WorkflowDatabaseHttpIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true test
```

组合首跑共95项、2失败：旧WorkflowDatabaseHttpIntegrationTest使用身份头登录，两个预期200的请求实际返回401。将该测试改为真实RS256签名后，最终组合BUILD SUCCESS，结果如下；不与RED、单类GREEN或此前87项重复相加。

| 模块 / 测试 | 测试数 | Failures / Errors / Skipped | 范围 |
| --- | ---: | --- | --- |
| workflow / WorkflowServiceTest | 69 | 0 / 0 / 0 | 五类任务操作及既有服务回归，嵌套套件去重计数 |
| workflow / WorkflowQueryServiceTest | 13 | 0 / 0 / 0 | 查询及缺租户拒绝路径 |
| workflow-service / IdentityRelayAuthenticationFilterTest | 10 | 0 / 0 / 0 | 签名、声明约束、身份头拒绝及安全链 |
| workflow-service / WorkflowDatabaseHttpIntegrationTest | 3 | 0 / 0 / 0 | H2数据库与真实RS256签名HTTP链路 |
| 合计 | 95 | 0 / 0 / 0 | workflow 82 + workflow-service 13 |

以下日志已读取核对，SHA-256由本机shasum计算。所有路径均为/tmp临时文件，尚未持久归档；文件清理后需重跑，hash不能替代日志内容。

| 阶段 | 临时日志 | SHA-256 |
| --- | --- | --- |
| 任务RED：69项/37失败 | `/tmp/apex-workflow-task-red-20261021.log` | `7a20e8944d304b9f0b29c463c25e0ebc84ac9b37058c1bb7180a55c5350a3b2b` |
| 任务GREEN：69项全通过 | `/tmp/apex-workflow-task-green-20261021.log` | `36d0bb7e07dbc87b9f06cec85646861e2e5dd3917a7d5b6d9175d54afb11b496` |
| JWT RED：10项/9失败 | `/tmp/apex-workflow-jwt-red-20261021.log` | `08c598cfa5477b106064bad4f1bbca05a775a50d358d6f395d5640355a39e346` |
| JWT GREEN：10项全通过 | `/tmp/apex-workflow-jwt-green-20261021.log` | `e7955d60f2a334899b5041c59af18a859862234ad52819377964c96891673470` |
| 组合首跑：95项/2失败 | `/tmp/apex-workflow-combined-20261021.log` | `367424f7ac47a9a4c91f0e19337a3e4dc2fe5fbec86103ded050a2ed5bd73fd0` |
| 组合GREEN：95项全通过 | `/tmp/apex-workflow-combined-green-20261021.log` | `201b2d33fa98943ef2b2b451b26fb8cd137c5437783b507eb40556d97ae9ad92` |

本批所改5个Java文件（WorkflowService、WorkflowServiceTest、IdentityRelayAuthenticationFilter、IdentityRelayAuthenticationFilterTest、WorkflowDatabaseHttpIntegrationTest）定向Spotless检查均通过，源码git diff --check通过。本次文档追加后执行`bash scripts/ci/check-doc-links.sh`，退出0，扫描3094个链接、断裂链接0（日志`/tmp/apex-workflow-doc-links-20261021.log`）；git diff --check退出0。两份目标文档当前为未跟踪文件，git diff --check不覆盖其内容，新增内容另经人工核对。

剩余限制：逐ID租户隔离仍未完成，`/internal/v1/**`仍为permitAll；多步事务与并发保护、自动JWKS发现/轮换及撤权未完成。H2/HTTP证据不能证明MySQL/PostgreSQL双库隔离、真实认证中心运行或完整平台链路。本次HTTP集成类由Surefire显式执行，不代表完整Failsafe通过；未运行全量clean verify、PMD、完整Failsafe或JaCoCo门禁，命令显式跳过JaCoCo。

P0未准出，O-BASE/P4尚未整体完成，Java21未切换；D01–D07决策未代定，历史500/586统计及既有hash不变。本次仅更新两份既有文档，无commit/push。

<a id="2026-10-21-workflow查询隔离事件组合链与内部路径收紧回归"></a>

## 2026-10-21 workflow查询隔离、事件组合链与内部路径收紧回归

本记录对应[实施计划42.9](../../../02-architecture/jdk21-unified-auth-multitenancy-implementation-plan.md#429-workflow查询租户隔离事件组合链与内部路径收紧2026-10-21)，保留42.8及上述历史条目原文。源码和日志经本轮核对；测试为主代理及各实施代理实际执行的既有结果，本次文档维护未重跑Java测试。构建仍为Oracle JDK17.0.12-oracle与mvnd2.0.0-rc-3，Java21未切换。

### 源码核实与分批RED/GREEN

- WorkflowQueryService全部查询入口（含重载）要求订阅时存在非空白Reactor tenant，缺失/空白时不访问Mapper，不依赖或回退ThreadLocal；定义、实例、任务以及实例关联定义、详情关联任务查询均显式包含tenant_id SQL条件。查询RED为26项/17失败（所在模块合计95项/17失败）；GREEN组合109项全部通过，即26查询+69服务+4 H2/HTTP+10 JWT。
- WorkflowHandler将事件旁路的手动subscribe改为组合链；完成、回退、转办、委派及启动事件保留原请求Reactor context。回查或发布旁路失败记录告警后，主操作仍成功；主操作错误传播，不被旁路恢复逻辑吞掉。响应现在等待发布链结束。Handler RED为48项/8失败，GREEN为48项全部通过；真实MQ投递和消费未验证。
- WorkflowSecurityConfig移除`/internal/v1/**`的permitAll，其余非公开路径统一要求认证。核对workflow生产Router及仓内调用后，未发现该前缀的workflow业务路由或对应仓内生产调用；本次未引入服务凭证。缺凭证、伪造身份头或无效凭证返回401；有效用户ACCESS通过认证后返回404，不能据此认定服务间认证已完成。安全链RED为15项/3失败，GREEN为15项全部通过。

源码依据为WorkflowQueryService、WorkflowHandler、WorkflowSecurityConfig及对应查询、Handler、安全链、H2/HTTP测试；仓储与扫描边界另核对TaskRepositoryImpl、ProcessInstanceRepositoryImpl、WorkflowDefinitionRepositoryImpl及WorkflowTimeoutHandler。上述结论限定本次改动范围。

### 最终组合命令与结果

主代理实际执行命令：

```bash
source scripts/build/init-build-env.sh && mvnd -T4 -B -pl apex-workflow-service -am -Dtest=WorkflowQueryServiceTest,WorkflowServiceTest,WorkflowDatabaseHttpIntegrationTest,IdentityRelayAuthenticationFilterTest,WorkflowHandlerIntegrationTest,WorkflowRouterRegressionTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true test
```

日志`/tmp/apex-workflow-isolation-combined-20261021.log`为BUILD SUCCESS，166项Failures/Errors/Skipped均0；嵌套服务套件去重，不与分批GREEN或历史95项重复相加。

| 模块 / 测试 | 测试数 | Failures / Errors / Skipped |
| --- | ---: | --- |
| workflow / WorkflowQueryServiceTest | 26 | 0 / 0 / 0 |
| workflow / WorkflowServiceTest | 69 | 0 / 0 / 0 |
| workflow-service / WorkflowDatabaseHttpIntegrationTest | 4 | 0 / 0 / 0 |
| workflow-service / WorkflowHandlerIntegrationTest | 48 | 0 / 0 / 0 |
| workflow-service / IdentityRelayAuthenticationFilterTest | 15 | 0 / 0 / 0 |
| workflow-service / WorkflowRouterRegressionTest | 4 | 0 / 0 / 0 |
| 合计：workflow 95 + workflow-service 71 | 166 | 0 / 0 / 0 |

各实施代理已对7个改动Java文件定向Spotless检查通过：WorkflowQueryService、WorkflowQueryServiceTest、WorkflowDatabaseHttpIntegrationTest、WorkflowHandler、WorkflowHandlerIntegrationTest、WorkflowSecurityConfig、IdentityRelayAuthenticationFilterTest。本次文档维护未重复执行Spotless。

### 日志SHA-256与保存边界

以下七份日志已核对测试汇总及构建终态，SHA-256通过本机`shasum -a 256`重新计算。日志均位于本机/tmp临时目录，非持久归档；hash不能替代原始日志，清理后需重新收集证据。

| 阶段 | 临时日志 | SHA-256 |
| --- | --- | --- |
| 查询RED：26项/17失败 | `/tmp/apex-workflow-query-red-20261021.log` | `eeaeff27230ec0455792874769bf01c2be965fb4accfc5624f3cbd8c55556501` |
| 查询GREEN组合：109项全通过 | `/tmp/apex-workflow-query-green-20261021.log` | `50021ed0dd7223c0575a46563f425b9aacf85232d2132fbdd9fcb95e35e29b15` |
| 事件RED：48项/8失败 | `/tmp/apex-workflow-events-red-20261021.log` | `2a3115013f0403b5ac9cf654923ca55d2541b90871dc7f522b24568c6462953b` |
| 事件GREEN：48项全通过 | `/tmp/apex-workflow-events-green-20261021.log` | `e1460f60eb2f5c60ba6311dc56ca69a69a0073affd1c1ac25291067060bf1ee6` |
| 内部路径RED：15项/3失败 | `/tmp/apex-workflow-internal-red-20261021.log` | `f254a524d1a0428fcc424f27e972727d487e35842427896a03a621dec896913e` |
| 内部路径GREEN：15项全通过 | `/tmp/apex-workflow-internal-green-20261021.log` | `2d38eb0cb6d44995f57a57042fcd0ed1377cbb8079ed357e2017cda9205317ab` |
| 最终组合：166项全通过 | `/tmp/apex-workflow-isolation-combined-20261021.log` | `852e69d265d189cfa0817c8cad59882e2e6a35b6991d9d10636681e0501cc259` |

### 未完成范围与准出限制

本次仅补WorkflowQueryService查询面，仓储读写及超时扫描仍无统一租户保护，不等于逐ID办理或全链路隔离完成。TaskPO、WorkflowDefinitionPO及ApprovalRecordPO的tenantId仍为Long，而MySQL/PostgreSQL及basic schema使用VARCHAR(64)，非数字租户存在映射风险；流程定义唯一索引(workflow_code, version, deleted)与实例唯一索引(business_key, deleted)仍未带tenant。多步事务原子性与并发保护未完成。

跨租户实例详情沿用HTTP 200空body，未改契约。H2/HTTP不能证明MySQL/PostgreSQL双库隔离；本批未执行全量clean verify、PMD、完整Failsafe、JaCoCo或真实MQ验收。HTTP集成类通过Surefire显式选中执行，组合命令显式跳过JaCoCo，不能视作完整门禁。O-BASE/P4未整体完成，P0未准出，Java21未切换；D01–D07决策、历史500/586统计及既有hash不变。

### 本次文档检查

文档追加后执行`bash scripts/ci/check-doc-links.sh > /tmp/apex-workflow-isolation-doc-links-20261021.log`，退出0：扫描667个Markdown文件、3096个链接，断裂链接0；`git diff --check`退出0。两份目标文档当前为未跟踪文件，git diff --check不覆盖其内容；新增条目已另行核对内容及行尾空白，链接与锚点由上述全仓扫描覆盖。本次仅追加两份既有文档，无commit/push。

<a id="2026-10-22-workflow仓储隔离与超时扫描回归"></a>

## 2026-10-22 workflow仓储隔离与超时扫描回归

本轮承接计划42.9并追加42.10，保留工作区全部已有改动。开始时四类仓储的WorkflowTenantAccess、tenant SQL、String tenant和专用TimeoutTaskScan已经存在；本轮新增3项真实H2 Mapper/调度/服务测试，合计该HTTP集成类10项。没有修改历史SQL、索引或事务结构，没有提交或推送。

真实RED暴露3项失败：专用扫描误返回未来任务；Task部分更新把update_time写成null；完成任务的completedTime未映射到PO completeTime。分别修复TimeoutTaskScanAdapter的到期SQL过滤、TaskRepositoryImpl更新审计时间及TaskPoConverter双向完成时间映射，断言保持不变。

现有H2测试覆盖四类仓储缺失/空白Reactor tenant读写拒绝（即使ThreadLocal存在）；跨租户ID/关联读取和更新删除零影响；Task非数字tenant新增覆盖伪造归属并合法更新；相同assignee不能完成/转办另一租户任务；合法完成持久化complete_time；后台仅扫描已到期待办，并在无关外层tenant存在时仍按每项持久tenant完成tenant-A/tenant-B任务。

所有构建先在当前shell执行`source scripts/build/init-build-env.sh`，实际为Oracle JDK17.0.12-oracle和mvnd2.0.0-rc-3。日志名按用户指定保留20261021，记录日期为2026-10-22。

| 实际命令（初始化后） | 结果 | 临时日志 |
| --- | --- | --- |
| `mvnd -T4 -B -pl apex-workflow-service -am -Dtest=WorkflowDatabaseHttpIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true test` | RED：10项，3失败、0错误、0跳过；退出1 | `/tmp/apex-workflow-repository-red-20261021.log` |
| 同上命令，最小修复后 | GREEN：10项，失败/错误/跳过均0；BUILD SUCCESS，退出0 | `/tmp/apex-workflow-repository-green-20261021.log` |
| `mvnd -T4 -B -pl apex-workflow-service -am test` | 773项，失败/错误/跳过均0；BUILD SUCCESS，退出0 | `/tmp/apex-workflow-repository-alltest-20261021.log` |
| `mvnd -T4 -B -pl apex-workflow,apex-workflow-service '-DspotlessFiles=.*(TaskRepositoryImpl\|TaskPoConverter\|TimeoutTaskScanAdapter\|WorkflowDatabaseHttpIntegrationTest)\.java' spotless:apply spotless:check` | 限定4个Java文件，2个格式调整、2个已干净；检查通过，退出0 | `/tmp/apex-workflow-repository-spotless-green-20261021.log` |

上表Spotless代码内的竖线为Markdown表格转义，实际shell参数用正则竖线。首次使用`**/文件名`被插件按正则解析而失败，未通过检查；更正后成功，原失败保存在`/tmp/apex-workflow-repository-spotless-20261021.log`。

773项按日志模块汇总独立求和：common539、domain9、api-contract30、job starter11、seata starter24、workflow107、workflow-service53，不与定向10项重复累计。普通test排除该HTTP IntegrationTest，故单独显式选中执行；完整test未显式跳过JaCoCo，workflow自身绑定test的覆盖率检查通过，不能外推为全仓覆盖率或完整verify门禁。日志仅存在本机/tmp，不是持久归档。

未解决范围：多步事务回滚、并发状态/更新行数保护、扫描后竞争、多实例互斥仍未实现；定义和实例唯一索引未带tenant；关联ID写入完整性以及定义/审批等仓储全部合法新增更新组合尚未完整验证，非数字tenant的真实合法写证据重点为Task。回退/委派等合法多步写仍有独立字段和原子性边界，不能把Mockito服务单测当作真实数据库写入通过。H2不证明MySQL/PostgreSQL双库行为，真实MQ、完整Failsafe、PMD及全仓clean verify本轮未执行。P0未准出，Java21未切换，历史证据及hash不变。

格式后再次执行上述H2定向命令：10项全部通过，失败/错误/跳过均0，BUILD SUCCESS，退出0，日志`/tmp/apex-workflow-repository-postformat-20261021.log`。文档追加后执行`bash scripts/ci/check-doc-links.sh > /tmp/apex-workflow-repository-doc-links-20261021.log 2>&1`，退出0，扫描667个Markdown、3097个链接、断裂链接0；`git diff --check`退出0。两份文档及HTTP测试等当前为未跟踪文件，git diff --check不覆盖全部未跟踪内容，新增段落另行核对，链接与锚点由扫描覆盖。

<a id="2026-10-22-workflow任务并发状态与更新行数保护回归"></a>

## 2026-10-22 workflow任务并发状态与更新行数保护回归

本轮对应计划42.11，保留全部已有改动及42.10仓储租户保护。复核五项办理语义：完成到COMPLETED、驳回到REJECTED、回退到ROLLED_BACK并创建PENDING任务、转办保持PENDING修改办理人、委派保持原任务PENDING并创建DELEGATED任务。Task增加既有version字段映射，TaskRepositoryImpl按可信tenant/id/PENDING/读取version条件更新并递增version；缺版本或旧快照返回0。WorkflowService五操作统一要求更新恰为1行，失败不继续修改实例或创建任务；委派先竞争原任务版本。保留TimeoutTaskScan与持久tenant上下文，超时经相同服务边界保护。

新增真实H2测试4项：旧快照竞争和跨租户更新；五操作分别注入状态变化与办理人/version变化的10个确定性场景，断言错误、赢家不被覆盖、实例保持RUNNING且无新增任务；超时扫描旧结果遇到用户已完成时拒绝；合法回退与委派。合法路径另暴露task_name必填缺失，已补齐原任务名称。Mockito仅控制读取后注入真实SQL竞争，条件更新与结果断言均走真实Mapper/H2，非mock行数证明。

所有构建均先在当前shell执行`source scripts/build/init-build-env.sh`，固定Oracle JDK17.0.12-oracle及mvnd2.0.0-rc-3。

| 实际命令（初始化后） | 结果 | 本机日志 |
| --- | --- | --- |
| `mvnd -T4 -B -pl apex-workflow-service -am test -Dtest=WorkflowDatabaseHttpIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` | RED：13项、2失败、0错误、0跳过；旧快照影响行数实际1及竞争后假成功，退出1 | `/tmp/apex-workflow-concurrency-red-20261021.log` |
| 同上，修复并增加合法路径后 | GREEN：14项，失败/错误/跳过均0，BUILD SUCCESS，退出0 | `/tmp/apex-workflow-concurrency-green-20261021.log` |
| `mvnd -T4 -B -pl apex-workflow-service -am test` | 773项，失败/错误/跳过均0，BUILD SUCCESS，退出0 | `/tmp/apex-workflow-concurrency-alltest-20261021.log` |
| `mvnd -T4 -B -pl apex-workflow,apex-workflow-service '-DspotlessFiles=.*(Task\|TaskRepository\|TaskRepositoryImpl\|WorkflowService\|WorkflowServiceTest\|WorkflowDatabaseHttpIntegrationTest)\.java' spotless:apply spotless:check` | 仅6个本批Java文件，2个格式调整，检查通过，退出0 | `/tmp/apex-workflow-concurrency-spotless-20261021.log` |

表格中竖线是Markdown转义，shell中使用正则竖线。773项模块汇总为common539、domain9、api-contract30、job starter11、seata starter24、workflow107、workflow-service53；与定向14项不重复累计。普通test未包含该IntegrationTest，故单独选中；完整test未跳过JaCoCo，但不代表全仓verify通过。日志在本机/tmp，非持久归档。

事务评估：WorkflowTenantAccess在各仓储订阅时独立切换JDBC池，当前服务链无法直接共享线程绑定同步事务。完整原子性需要调整同步执行边界，本批仅实现任务条件更新及0行截断；任务CAS成功后第二步失败仍不能回滚任务，未添加返回Mono的JDBC事务注解。多实例扫描互斥、定义/实例tenant唯一索引及关联ID写入完整性未解决。H2不证明MySQL/PostgreSQL，真实MQ、完整Failsafe、PMD及clean verify未执行；P0未准出，Java21未切换，未commit/push。

格式后再次运行同一H2定向命令，14项全部通过、失败/错误/跳过均0、BUILD SUCCESS，退出0，日志`/tmp/apex-workflow-concurrency-postformat-20261021.log`。执行`bash scripts/ci/check-doc-links.sh`退出0，扫描667个Markdown文件、3098个链接、断裂链接0，日志`/tmp/apex-workflow-concurrency-doc-links-20261021.log`；`git diff --check`退出0。两份文档及H2测试等既有文件目前未跟踪，git diff --check不覆盖这些文件，新增内容另行核对，文档路径和锚点由全仓检查覆盖。

<a id="2026-10-22-workflow多步事务原子性故障注入"></a>

## 2026-10-22 workflow多步事务原子性故障注入

本轮对应计划42.12，仅新增[WorkflowAtomicityIntegrationTest](../../../../apex-workflow-service/src/test/java/com/apex/scaffold/workflowservice/interfaces/handler/WorkflowAtomicityIntegrationTest.java)及维护现有计划/报告，保留既有工作区改动，不改生产代码。测试使用独立H2 MySQL mode数据库、真实仓储及Mapper，每例重建种子，事后移除CHECK约束，无测试事务、sleep或disabled。断言要求业务Mono报错且任务、实例、审批记录的完整行及数量与操作前一致；使用assertAll同时报告错误信号与数据库残留，不将半成功写成合法期望。

所有构建先在当前shell执行`source scripts/build/init-build-env.sh`，使用Oracle JDK17.0.12-oracle、mvnd2.0.0-rc-3。执行日期为2026-10-22，以下日志文件名按本轮要求保留20261021。

| 实际命令（初始化后） | 结果 | 本机日志 |
| --- | --- | --- |
| `mvnd -T4 -B -pl apex-workflow-service -am -Dtest=WorkflowDatabaseHttpIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true test` | 基线14项通过，失败/错误/跳过均0，BUILD SUCCESS，退出0 | `/tmp/apex-workflow-atomicity-baseline-20261021.log` |
| `mvnd -T4 -B -pl apex-workflow-service '-DspotlessFiles=.*WorkflowAtomicityIntegrationTest\.java' spotless:apply spotless:check` | 仅新测试1文件格式化，检查通过，BUILD SUCCESS，退出0 | 终端输出 |
| `mvnd -T4 -B -pl apex-workflow-service -am -Dtest=WorkflowDatabaseHttpIntegrationTest,WorkflowAtomicityIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true test` | 共20项：既有14项通过，新增6项全部失败；错误0、跳过0，BUILD FAILURE，退出1 | `/tmp/apex-workflow-atomicity-tests-20261021.log` |

以下每项独立运行，原任务为PENDING、version=0、complete_time=null、固定update_time；原实例RUNNING。三项约束故障均为真实H2 SQLState 23513，通过DataIntegrityViolationException传播。日志中`ATOMICITY`行保存操作前后完整快照。

| 故障场景 | 实际业务信号 | 实际持久化残留（正确期望均为报错且整体回滚） |
| --- | --- | --- |
| reject：CHECK禁止实例REJECTED | onError，约束ATOMICITY_FAULT_INSTANCE | 任务已REJECTED，version=1，complete_time非空、update_time变化；实例仍RUNNING、end_time为空 |
| rollback：CHECK禁止previous节点任务 | onError，约束ATOMICITY_FAULT_TASK | 原任务已ROLLED_BACK，version=1，complete_time非空、update_time变化；无子任务 |
| delegate：CHECK禁止DELEGATED任务 | onError，约束ATOMICITY_FAULT_TASK | 原任务仍PENDING，但version=1、update_time变化；complete_time仍空，无委派任务 |
| reject：后置实例find为空 | onComplete，静默成功 | 预置任务关联不存在实例9999后，任务仍被写成REJECTED/version=1，完成和更新时间残留 |
| rollback：后置实例find为空 | onComplete，静默成功 | 同样预置关联9999，任务仍被写成ROLLED_BACK/version=1，完成和更新时间残留，无子任务 |
| reject：后置实例update真实影响0行 | onComplete，静默成功；真实Mapper count=0断言通过 | 任务已REJECTED/version=1，完成和更新时间残留；原实例3001未变 |

所有场景任务数维持1、实例数维持1、审批记录数维持0；完整快照确认实例及审批记录未变，任务快照断言失败。最后一项仅对后续实例仓储使用受控spy，将更新目标改成不存在ID9999并调用真实仓储/Mapper，未mock前一步持久化或返回行数；finally独立断言真实结果0。其余三项后续SQL故障由数据库CHECK确定性触发。start经代码核对只有一次实例插入，不创建初始任务，因此未虚构多写回滚用例。

根因：WorkflowTenantAccess在每次仓储调用时独立调度至JDBC池，没有覆盖多步写的同步事务；已提交的任务CAS不随后续异常回滚。reject和rollback后置find的empty经flatMap/then变成完成；reject忽略后置实例update的0行结果。后续需统一同步JDBC事务执行边界（例如同步事务端口或TransactionTemplate，在同一事务/线程内完成所有仓储写），并将后置关联缺失、更新非1行转为业务错误以触发回滚，不能仅给返回Mono的方法添加JDBC事务注解。本轮保留全部6项失败，原子性整改未完成，P0未准出。

本轮未运行全仓clean verify、完整Failsafe、PMD或JaCoCo门禁；H2不能证明MySQL/PostgreSQL双库行为，真实MQ及多实例调度仍未验收，Java21未切换。两份日志仅在本机/tmp，非持久归档；未commit/push。

本轮文档检查：`bash scripts/ci/check-doc-links.sh`退出0，扫描667个Markdown文件、3101个链接、断裂链接0，日志`/tmp/apex-workflow-atomicity-doc-links-20261021.log`。`git diff --check`退出0；测试及两份文档为未跟踪文件，另外分别用`git diff --no-index --check /dev/null <文件>`检查完整内容（无空白诊断时退出1表示存在文件差异）。

<a id="2026-10-22-workflow多步同步事务原子性修复"></a>

## 2026-10-22 workflow多步同步事务原子性修复

本轮对应计划42.13，承接并保留42.12六项失败证据。新增领域端口[WorkflowTaskTransaction](../../../../apex-workflow/src/main/java/com/apex/scaffold/workflow/domain/repository/WorkflowTaskTransaction.java)，以同步Operations描述任务及实例读取/写入；[JdbcWorkflowTaskTransaction](../../../../apex-workflow/src/main/java/com/apex/scaffold/workflow/infrastructure/persistence/repository/JdbcWorkflowTaskTransaction.java)使用同一DataSource的JdbcTransactionManager和TransactionTemplate实现真实同步事务，复用TaskRepositoryImpl及ProcessInstanceRepositoryImpl抽取的同步SQL方法。没有新增依赖或system耦合，领域不引用具体仓储/MyBatis。

WorkflowService的rejectTask、rollbackToNode、delegateTask在Mono.deferContextual订阅边界获取非空可信tenant，显式传入事务；Mono.fromRunnable包裹整个事务并subscribeOn(BlockingIoSchedulers.jdbc())，同步回调内不切线程。查询、tenant/id/PENDING/version任务CAS、关联实例校验及后续写入在同一事务完成。关联缺失、任务CAS或关键实例更新/子任务插入非1行均抛错，先前写入整体回滚；delegate原任务保持PENDING，仅竞争并递增版本。没有给Mono方法添加JDBC事务注解，没有引入no-op响应式事务或生产请求链block。Handler既有then/defer事件链继续仅在业务成功后执行。

六项原子性故障仍比较任务、实例、审批记录完整快照，无测试事务。CHECK约束继续由真实数据库触发；实例更新0行场景改为spy同步边界，仅改变后置目标ID并调用真实Mapper，独立断言实际count=0，未伪造先前CAS或后置影响行数。新增合法reject、rollback、delegate提交测试，确认最终任务/实例状态、版本及子任务租户与办理信息。既有数据库HTTP竞争场景适配新事务入口，赢家使用REQUIRES_NEW真实独立提交，保留原竞争断言。单元测试新增三操作各自的关联缺失、CAS0行、后续写0行共九项，另加一项验证订阅前不执行、缺tenant拒绝、可信tenant传递及apex.jdbc线程执行。

所有构建在当前shell先执行`source scripts/build/init-build-env.sh`，固定Oracle JDK17.0.12-oracle及mvnd2.0.0-rc-3。执行日期2026-10-22，日志文件名按要求保留20261021。

| 实际命令（初始化后） | 结果 | 本机日志 |
| --- | --- | --- |
| `mvnd -T4 -B -pl apex-workflow-service -am test -Dtest=WorkflowAtomicityIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` | 修复前RED：6项、6失败、0错误/跳过，BUILD FAILURE | `/tmp/apex-workflow-transaction-red-20261021.log` |
| `mvnd -T4 -B -pl apex-workflow-service -am test -Dtest=WorkflowAtomicityIntegrationTest,WorkflowDatabaseHttpIntegrationTest,WorkflowServiceTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` | 最终GREEN：原子性9项、数据库HTTP14项、服务单元79项，失败/错误/跳过均0，BUILD SUCCESS | `/tmp/apex-workflow-transaction-green-20261021.log` |
| `mvnd -T4 -B -pl apex-workflow-service -am test` | 783项全部通过，失败/错误/跳过均0，BUILD SUCCESS | `/tmp/apex-workflow-transaction-regression-20261021.log` |
| `mvnd -T4 -B -pl apex-workflow-service -am test -Dtest=WorkflowHandlerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` | Handler集成48项通过，含业务失败原样传播且查询/事件发布器无交互的参数化断言，BUILD SUCCESS | `/tmp/apex-workflow-transaction-handler-20261021.log` |
| `mvnd -T4 -B -pl apex-workflow,apex-workflow-service spotless:apply spotless:check '-DspotlessFiles=.*(WorkflowService\|WorkflowTaskTransaction\|JdbcWorkflowTaskTransaction\|TaskRepositoryImpl\|ProcessInstanceRepositoryImpl\|WorkflowServiceTest\|WorkflowAtomicityIntegrationTest\|WorkflowDatabaseHttpIntegrationTest)\.java'` | 仅8个本批Java文件，定向格式及检查通过，BUILD SUCCESS | `/tmp/apex-workflow-transaction-spotless-20261021.log` |

表格竖线为Markdown转义，shell中使用正则竖线。普通test模块汇总：common539、domain9、api-contract30、job starter11、seata starter24、workflow117、workflow-service53，总783；与定向测试不重复累计。普通test未包含上述IntegrationTest，故显式通过Surefire选中；普通test未跳过JaCoCo，定向测试显式跳过JaCoCo，不代表完整Failsafe/PMD/JaCoCo或全仓verify通过。日志仅在本机/tmp，非持久归档。

本批完成三项任务操作的多步同步事务原子性及失败截断。多实例扫描互斥、定义/实例tenant唯一索引、其他关联写入完整性、MySQL/PostgreSQL双库、真实MQ及全仓clean verify仍未完成。H2 MySQL mode不证明双库生产行为；Handler mock事件断言不构成真实MQ交付证明。P0未准出，Java21未切换，未commit/push。

文档检查`bash scripts/ci/check-doc-links.sh`退出0，扫描667个Markdown文件、3104个链接、断裂链接0，日志`/tmp/apex-workflow-transaction-doc-links-20261021.log`。`git diff --check`通过；对两个新事务Java文件、两份未跟踪集成测试及原计划/报告另行执行`git diff --no-index --check /dev/null <文件>`，无空白诊断（存在文件差异时退出1）。

<a id="2026-10-22-workflow双库与扫描租约生命周期增量"></a>

## 2026-10-22 workflow双库与扫描租约生命周期增量

对应计划42.14。保留此前原子性与竞争赢家断言，补齐配对V3 tenant唯一键/version、V4扫描租约表和basic schema。统计SQL使用显式tenant条件；DatabaseIdProvider根据真实数据库元数据选择MySQL TIMESTAMPDIFF或PostgreSQL TRUNC/EXTRACT，避免将H2兼容模式作为PostgreSQL证明。

已复核前序定向日志`/tmp/apex-workflow-dual-verify.log`：WorkflowDualDatabaseIntegrationTest 8项、WorkflowFlywayMigrationTest 8项全部通过，Failures/Errors/Skipped均0，BUILD SUCCESS。这是前序定向执行证据，不重复计为本次全仓结果。测试使用独立UUID数据库及PostgreSQL角色，覆盖空库、存量升级和统计租户隔离；不证明全部业务原子性场景已在生产双库执行。

租约生命周期改为Mono.usingWhen；每轮生成独立owner，缓存单次acquire结果，取消后仍等待晚到结果并清理；成功、错误、取消均等待release终止后复位本地running。补充确定性Sinks测试覆盖release挂起禁止重入、晚到acquire成功/未抢到/失败、释放失败保留扫描结果、不同轮次owner；真实H2补取消释放和旧owner无法释放新轮租约。释放失败记录告警并由TTL兜底，不能称为即时释放成功。没有自动续租或fencing；超TTL和同步JDBC取消尾部仍可能跨实例重叠。

本轮构建均先source scripts/build/init-build-env.sh，固定Oracle JDK17.0.12-oracle及mvnd2.0.0-rc-3。双库容器覆盖为APEX_TEST_MYSQL_CONTAINER=apex-gate-fix-mysql-20261021-2132、APEX_TEST_POSTGRES_CONTAINER=apex-gate-fix-pg-20261021-2132，不删除原有数据。

| 实际执行 | 结果与边界 | 日志 |
| --- | --- | --- |
| 全仓`mvnd -T4 -B clean verify`，附loopback SOCKS例外 | 失败：common产物路径异常、gateway缺SnowflakeIdGenerator、file测试格式；未证明共同根因 | `/tmp/apex-full-verify-current.log` |
| 同环境全仓clean verify | 失败于workflow测试Spotless，后续模块SKIPPED，不能据此判定原缺类问题已修复 | `/tmp/apex-full-verify-lease-current.log` |
| 租约相关Java定向spotless:apply spotless:check | 两模块BUILD SUCCESS，仅格式化目标文件；git diff --check通过 | 终端输出，2026-10-22 12:31 |
| 同环境全仓clean verify，附loopback SOCKS例外 | 12:37结束并失败：apex-auth的clean无法删除target；gateway、file已成功，system及workflow-service等后续模块SKIPPED，不能记为全仓通过 | `/tmp/apex-full-verify-lease-formatted.log` |
| 租约相关Java定向spotless:apply spotless:check及Handler单测 | 两模块格式检查成功；WorkflowTimeoutHandlerTest实际执行19项，Failures/Errors/Skipped均0，BUILD SUCCESS；新增生命周期用例已真实编译运行 | `/tmp/apex-workflow-lease-focused-20261022.log`，格式检查见12:49终端输出 |

| Failsafe定向数据库租约及真实双库 | WorkflowScanLeaseIntegrationTest 8项、WorkflowDualDatabaseIntegrationTest 8项，Failures/Errors/Skipped均0，BUILD SUCCESS | `/tmp/apex-workflow-lease-dual-20261022.log` |
| 同环境全仓clean verify，附loopback SOCKS例外 | 13:18:34结束，退出码0，31个反应堆项目全部SUCCESS，默认质量门禁通过 | `/tmp/apex-full-verify-lease-confirmed-20261022.log` |

全仓中WorkflowTimeoutHandlerTest 19项、WorkflowScanLeaseIntegrationTest 8项、WorkflowDualDatabaseIntegrationTest 8项、SystemDualDatabaseMigrationIntegrationTest 2项均实际执行，Failures/Errors/Skipped均0。该次全仓条件跳过MessageOutboxDialectTest 4个方法、DomainEventOutboxDialectTest 6个方法、SeataAtIntegrationTest 1项、OfficeWorkflowBooleanIntegrationTest 1项及AuthMySqlTlsContractIntegrationTest 1项；这些跳过项不计为该轮外部环境验收成功。本次auth清理失败未复现，历史根因仍未证明。

随后按本次提供的执行结果补录真实Outbox双库定向补验：使用OUTBOX_DUAL_DB及独立随机库，连接已有真实MySQL（38645）和PostgreSQL（43997）；MessageOutboxDialectTest 8项、DomainEventOutboxDialectTest 12项，Failures/Errors/Skipped均0。此前全仓跳过的10个方法按双库展开为20项，本次补验通过，不追记为此前全仓实际执行。日志路径为`/tmp/apex_outbox_20261022_133854_18ed5498-direct.log`；本轮文档更新时该日志在当前环境不可读取，未重新核验原始日志。该结果仅补齐Outbox双库方言验证，不等于真实MQ投递消费或完整可靠消息闭环通过；Seata、office及auth上述条件跳过项仍未计为通过。

新增Handler测试经Git diff、Spotless实际格式化和JUnit执行数量交叉确认，闭合此前编辑器内容与终端磁盘不一致导致只运行旧10项的证据缺口。auth/target清理失败尚未证明根因；目录属主及权限初查正常，lsof未返回有效占用证据，不据此声称不存在并发或权限问题。

全仓命令附`'-DsocksNonProxyHosts=localhost|127.*|[::1]'`。system旧重跑日志实际停在构造器单测，旧Failsafe容器退出125报告不可作为新执行结果；后续全仓完整门禁已于13:18:34结束，退出码0，31个反应堆项目全部SUCCESS，以`/tmp/apex-full-verify-lease-confirmed-20261022.log`所记后续结果为准，保留此前失败记录及未证明根因。真实Outbox双库补验只证明数据库方言行为；后续新增真实MQ基础设施验收，结果见下文。system业务listener、故障恢复、跨实例通知及JWT链路仍待验收，不以DELIVERED状态替代消费者业务成功证据。P0未准出，Java21未切换；未commit/push。

### 真实MQ基础设施补验（2026-10-22 15:48）

新增[MqConsumerBrokerIntegrationTest](../../../../apex-infrastructure-messaging/src/test/java/com/apex/scaffold/infrastructure/mq/service/MqConsumerBrokerIntegrationTest.java)，真实producer经RocketMQ broker投递，消费回调调用生产MqConsumerService，Redis保存幂等状态，PostgreSQL记录JDBC副作用。每轮独立topic、consumer group、tenant和schema；副作用表不设tenant/message唯一约束，避免数据库约束掩盖handler重复执行。三项分别验证正常持久化、同租户同messageId第二次消费成功但只保留首次副作用、跨租户相同messageId分别落库。

执行命令（仓库根目录，数据库为隔离验收库）：

```bash
source scripts/build/init-build-env.sh &&
export APEX_REAL_MQ_INFRA_IT=true \
  APEX_MQ_NAMESRV=127.0.0.1:41976 \
  APEX_MQ_REDIS=redis://127.0.0.1:41979 \
  APEX_MQ_JDBC_URL=jdbc:postgresql://127.0.0.1:41932/mq_acceptance \
  APEX_MQ_DB_USER=postgres &&
mvnd -T4 -B -pl apex-infrastructure-messaging -am \
  test-compile failsafe:integration-test failsafe:verify \
  -Dit.test=MqConsumerBrokerIntegrationTest \
  -Dfailsafe.failIfNoSpecifiedTests=false \
  '-DsocksNonProxyHosts=localhost|127.*|[::1]'
```

实际结果：3项，Failures/Errors/Skipped均0，BUILD SUCCESS，结束于15:48:48。主代理复读独立日志229–258行确认；日志为`/var/folders/0x/mpfcvmh560q4rbbvkd43ks280000gn/T/trae-agent-toolhost-501/jobs/job-a9db6db9761e4df3bff381ed5de407f0/output.log`。保留schema为`mq_infra_it_9f706db0c1e14ae39487cea0791ba363`。后续其他构建已覆盖标准Failsafe报告为3项条件跳过，故以本次独立成功日志为准，不混用报告时间。

本测试用隔离回调设置租户上下文，未验证可信租户入口、Spring装配、system业务listener、Flyway、Outbox、重试/崩溃恢复、DLQ或跨实例广播。该结果只闭合基础设施正常链路及两项幂等隔离断言；本次定向Failsafe也不等于新增测试后的完整clean verify。新增测试定向spotless:check于15:58:14返回BUILD SUCCESS，git diff --check通过。

<a id="system真实用户消息消费补验2026-10-22"></a>

### system真实用户消息消费补验（2026-10-22）

本轮恢复隔离NameServer：旧命令`mqnamesrv -p 41976`未提供可用监听，备用`-c /dev/stdin`因Illegal seek失败；改用容器内普通配置文件，未恢复被删除的宿主配置，未删除旧容器。确认Broker注册为AcceptanceCluster / acceptance-broker / BID 0 / 127.0.0.1:41911 / V5_3_1 / ACTIVATED=true。数据库为隔离PostgreSQL库mq_acceptance，Redis为41979端口。

[UserMessageBrokerIntegrationTest](../../../../apex-system/src/test/java/com/apex/scaffold/infrastructure/mq/listener/UserMessageBrokerIntegrationTest.java)使用生产UserMessageListener、MyBatis仓储、租户插件及填充组件。修复测试夹具中的NameServer属性源、消费容器名称与同名Spring singleton注册，以满足ORDERLY回调；未修改生产代码。每轮独立topic/group/tenant/schema，通过消费成功Hook、Redis SUCCESS和欢迎消息/注册审计行数断言验证实际副作用。

| 测试类 | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| MqConsumerBrokerIntegrationTest | 3 | 0 | 0 | 0 |
| UserMessageBrokerIntegrationTest | 3 | 0 | 0 | 0 |

system三项分别覆盖正常注册落欢迎消息和审计、同租户重复消息不重复副作用、不同租户相同messageId独立生效。主代理复读两份本轮Failsafe XML确认上述计数；消费Hook是客户端成功证据，不据此推断故障下Broker确认与多表事务的原子性。

实际命令（先在当前shell加载固定JDK17.0.12-oracle与mvnd2.0.0-rc-3；凭据由容器环境传入、不打印）：

```bash
source scripts/build/init-build-env.sh &&
export APEX_REAL_MQ_INFRA_IT=true APEX_REAL_MQ_IT=true \
  APEX_MQ_NAMESRV=127.0.0.1:41976 \
  APEX_MQ_REDIS=redis://127.0.0.1:41979 &&
export APEX_MQ_DB_USER="$(podman exec apex-mq-db-20261022 sh -c 'printf %s "${POSTGRES_USER:-postgres}"')" \
  APEX_MQ_DB_PASSWORD="$(podman exec apex-mq-db-20261022 sh -c 'printf %s "${POSTGRES_PASSWORD:-}"')" &&
export APEX_MQ_JDBC_URL="jdbc:postgresql://127.0.0.1:41932/$(podman exec apex-mq-db-20261022 sh -c 'printf %s "$POSTGRES_DB"')" &&
mvnd -T4 -B -pl apex-infrastructure-messaging,apex-system -am \
  test-compile failsafe:integration-test failsafe:verify \
  -Dskip.unit.tests=true -Djacoco.skip=true \
  -Dit.test=MqConsumerBrokerIntegrationTest,UserMessageBrokerIntegrationTest \
  -Dfailsafe.failIfNoSpecifiedTests=false \
  '-DsocksNonProxyHosts=localhost|127.*|[::1]' \
  -Dapex.log.dir=/tmp/apex-mq-app-20261022 \
  '-DargLine=-Drocketmq.client.logRoot=/tmp/apex-mq-client-20261022 -Dlogging.file.path=/tmp/apex-mq-app-20261022' \
  > /tmp/apex-mq-targeted-exclusive-20261022.log 2>&1
```

最终退出码0，BUILD SUCCESS。此前编译阶段出现.class缺失，失败日志`/tmp/apex-mq-targeted-verified-20261022.log`保留；其他构建退出后重跑成功，不据此宣称已证明产物缺失根因。定向spotless:check通过，日志`/tmp/apex-mq-spotless-exclusive-20261022.log`；git diff --check通过。

本轮XML分别为`apex-infrastructure-messaging/target/failsafe-reports/TEST-com.apex.scaffold.infrastructure.mq.service.MqConsumerBrokerIntegrationTest.xml`和`apex-system/target/failsafe-reports/TEST-com.apex.scaffold.infrastructure.mq.listener.UserMessageBrokerIntegrationTest.xml`。后续构建可能覆盖target报告，/tmp日志亦非持久归档。补录后执行git diff --check与scripts/ci/check-doc-links.sh均退出0；文档检查扫描667份Markdown、3108个链接，断裂链接0。

边界：这是生产listener正常链路和两项租户幂等场景的真实Broker验收，不是完整应用装配、可信生产租户入口或可靠消息闭环。手工建表不证明Flyway；未验证Outbox生产者、跨表事务原子性、重试/崩溃恢复、DLQ或跨实例广播。本轮跳过单元测试及JaCoCo，未运行新增测试后的全仓clean verify。consumer维度幂等和既有Seata/office/auth条件项等仍未关闭；P0未准出，Java21未切换，D01–D07不代定。未commit/push。

<a id="用户注册消费双表事务补验2026-10-22"></a>

### 用户注册消费双表事务补验（2026-10-22）

故障注入先证明原缺陷：在真实PostgreSQL的sys_operation_log增加仅拒绝“用户注册: 故障注入”的约束，向真实RocketMQ发送注册消息。原实现先提交欢迎消息，再在审计写入失败，Redis状态为FAILED，但欢迎消息残留1条；RED日志为`/tmp/apex-mq-registration-red.log`，测试1项、Failures=1、Errors=0、Skipped=0。

修复新增[UserRegistrationTransaction](../../../../apex-system/src/main/java/com/apex/scaffold/infrastructure/persistence/repository/UserRegistrationTransaction.java)，由同一TransactionTemplate在BlockingIoSchedulers.jdbc()中执行两个同步Mapper插入；UserMessageListener的注册分支调用该事务，保留租户上下文恢复和Redis幂等状态语义。修复后真实Broker测试4项、UserMessageListenerTest 6项、LayeringArchTest与NoBoundedElasticArchGuardTest合计7项均通过，失败/错误/跳过均0。绿色日志分别为`/tmp/apex-mq-registration-all-green.log`、`/tmp/apex-mq-registration-green.log`、`/tmp/apex-user-registration-architecture.log`；定向Spotless日志为`/tmp/apex-user-registration-spotless.log`。

实际验证命令均先执行`source scripts/build/init-build-env.sh`，使用固定JDK17.0.12-oracle和mvnd，并为loopback连接设置`-DsocksNonProxyHosts=localhost|127.*|[::1]`；真实Broker验证使用隔离RocketMQ、Redis 41979与PostgreSQL 41932。新增代码后的全仓clean verify已执行：首轮日志`/tmp/apex-full-verify-registration-20261022.log`因UserRegistrationTransaction缺少@author触发ClassMustHaveAuthorRule，按邻近类惯例补齐Apex Team后独占完整重跑。最终日志`/tmp/apex-full-verify-registration-retry-20261022.log`在2026-10-22 21:55:22记录BUILD SUCCESS，31个反应堆项目全部SUCCESS；本轮真实MQ基础设施3项、system消费4项的Failsafe XML均为Failures=0、Errors=0、Skipped=0。Seata AT、office双库布尔迁移与auth MySQL TLS各1项条件跳过，另行补验，不能算该轮通过。全目录XML扫描发现未纳入本轮reactor的apex-interfaces目录中的旧失败报告，时间早于本轮且不在reactor，不能混入本轮统计。未验证注册事务的MySQL行为、完整应用启动、Flyway、Redis与数据库跨资源原子性、崩溃恢复、DLQ或可靠消息闭环；未commit/push。

<a id="auth-tlsoffice双库与seata-at条件项补验2026-10-22"></a>

### Auth TLS、Office双库与Seata AT条件项补验（2026-10-22）

Auth：启用`APEX_AUTH_MYSQL_TLS_IT=true`，使用固定构建环境执行`mvnd -T4 -B -pl apex-auth-service -am -Dit.test=AuthMySqlTlsContractIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false verify`。真实MySQL的tls、no-tls、tls-required三场景实际执行3项，Failures/Errors/Skipped均0，2026-10-22 22:04:44 BUILD SUCCESS；日志`/tmp/apex-auth-tls-followup-20261022.log`。验证生产配置绑定及真实JDBC/R2DBC驱动TLS策略，不证明生产证书身份校验或完整Auth应用启动。

Office首轮真实MySQL/PostgreSQL运行2项均失败，日志`/tmp/apex-office-real-db-followup-20261022.log`。失败位于跨租户workflow_code插入应抛异常的旧断言；核对两种数据库V3迁移及WorkflowDualDatabaseIntegrationTest，当前契约明确允许跨租户同键。仅修复OfficeWorkflowBooleanIntegrationTest并将方法命名改为TenantWorkflowKeys：显式断言跨租户定义/实例同键成功，同租户重复必须触发SQLState 23完整性错误，COMPLETED/REJECTED/TERMINATED且未删除的实例仍占用同租户业务键。未修改历史迁移或生产逻辑。修复后首轮2项均通过，但verify因测试文件格式失败，日志`/tmp/apex-office-real-db-retry-20261022.log`；定向Spotless apply命令未实际匹配文件；按Spotless差异手工补齐格式，第二次verify仍有一处换行不符（日志`/tmp/apex-office-real-db-final-20261022.log`，双库2项仍通过），继续按精确差异修正。最终定向verify于2026-10-22 22:33:44 BUILD SUCCESS，日志`/tmp/apex-office-real-db-confirmed-20261022.log`；最新Failsafe XML确认MySQL/PostgreSQL共2项，Failures/Errors/Skipped均0，耗时7.766秒，Spotless/PMD/JaCoCo等该次verify门禁通过。命令为`mvnd -T4 -B -pl apex-office-service -am -Dit.test=OfficeWorkflowBooleanIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false -Dapex.office.real-db=true -Dapex.office.mysql.container=apex-contract-mysql-20261022 -Dapex.office.postgresql.container=apex-mq-db-20261022 verify`，先加载固定构建环境。验证四布尔列V3→V4规范化、默认0、拒绝NULL、0/1更新及当前workflow租户唯一键；不是生产存量升级或完整Office服务启动。

Seata AT：Office verify成功后独占执行`APEX_SEATA_IT_MYSQL_CONTAINER=apex-contract-mysql-20261022 APEX_SEATA_IT_TC_IMAGE=docker.io/apache/seata-server:2.2.0 bash scripts/test/verify-seata-at.sh`（该脚本已于后续改造中删除，现由 `SeataRealCluster` 扩展在测试进程内编排同一环境，入口为 `APEX_SEATA_IT=true mvnd -B -f apex-seata-spring-boot-starter/pom.xml verify`）。脚本以真实TC 2.2.0、两个隔离MySQL库和两个HTTP分支验证全局提交与故障回滚；2026-10-22 22:34:34 BUILD SUCCESS，日志`/tmp/apex-seata-at-followup-20261022.log`。最新SeataAtIntegrationTest XML为1项、Failures/Errors/Skipped均0、耗时3.381秒；starter单元测试24项均通过。脚本报告本次隔离TC、双测试库及账号已清理，串联命令自然退出0；共享测试MySQL及既有MQ基础设施不删除。日志仍包含ConfigurationProvider加载诊断及PMD阶段aktStatus诊断，不将BUILD SUCCESS描述为日志无ERROR；未抑制门禁。本结果仅证明该AT测试场景，不替代TCC/SAGA/XA、生产部署或完整跨服务业务链验收。

本批补齐此前全仓门禁中三个条件项的真实执行证据，不改写当轮跳过记录；Office测试契约修复发生在前轮全仓clean verify之后，本批仅执行上述受影响模块及依赖的定向verify，不宣称重新完成全仓门禁。P0仍未准出，Java21未切换，D01–D07不代定，可靠消息闭环等既有剩余事项保持未完成。

本批文档检查于2026-10-22 22:40:49完成：`bash scripts/ci/check-doc-links.sh`自然退出0，扫描667份Markdown、3111条链接、断链0。`git diff --check`无诊断；对本批三个未跟踪目标文件另执行相对`/dev/null`的`git diff --no-index --check`均无空白错误输出（存在文件差异时退出1，不记为退出0）。两份文档行尾空白扫描无命中，Office修复块及最新Failsafe XML已复读；未commit/push。

<a id="consumer维度幂等隔离2026-10-23"></a>

### consumer维度幂等隔离（2026-10-23）

范围：完成tenant+consumer+eventId的消费幂等作用域，不改变Redis与业务数据库的资源边界。MqConsumerService强制传入实际消费组，三维分别UTF-8 Base64URL无填充编码，两个生产listener使用与RocketMQ注解一致的消费组常量，所有受影响调用与测试同步迁移。空consumer及空白tenant在Redis访问前拒绝；原TTL、CAS及FAILED重试策略保持不变。

RED：固定Oracle17.0.12-oracle/mvnd2.0.0-rc-3，每次先`source scripts/build/init-build-env.sh`，执行`mvnd -T4 -B -pl apex-infrastructure-messaging -am '-Dtest=MqConsumerServiceTest#shouldRejectBlankTenantBeforeRedisAccess' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true test`，1项断言失败、0错误、0跳过，日志`/tmp/apex-mq-scope-red.log`；再以`shouldNotSuppressAnotherConsumerOfSameEvent`替换方法选择器复现跨consumer抑制，1项断言失败、0错误、0跳过，日志`/tmp/apex-mq-scope-core-red.log`。修复后GREEN三类测试共34项通过，日志`/tmp/apex-mq-scope-green.log`。

定向verify：启用`APEX_REAL_MQ_INFRA_IT=true`与`APEX_REAL_MQ_IT=true`，使用既有真实RocketMQ、Redis、PostgreSQL测试设施及本地非敏感连接配置；执行`mvnd -T4 -B -pl apex-system -am -Dtest=MqConsumerServiceTest,NotificationBroadcastListenerTest,UserMessageListenerTest -Dit.test=MqConsumerBrokerIntegrationTest,UserMessageBrokerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false -Djacoco.skip=true '-DspotlessFiles=.*(MqConsumerService|NotificationBroadcastListener|UserMessageListener|MqConsumerServiceTest|NotificationBroadcastListenerTest|UserMessageListenerTest|MqConsumerBrokerIntegrationTest|UserMessageBrokerIntegrationTest)\.java' verify`。2026-10-23 06:51:05完成，10个reactor项目BUILD SUCCESS；messaging单测28项、system单测6项，真实基础设施IT3项及用户消费IT4项，Failures/Errors/Skipped均0。日志`/tmp/apex-mq-scope-verify.log`。真实测试覆盖两个consumer对同事件各产生一次JDBC副作用、同consumer重复抑制、租户隔离及原有用户注册失败回滚。限定范围Spotless及PMD check通过，PMD仍有maximum Iterations exceeded诊断；未描述为无ERROR日志。

边界：显式跳过JaCoCo，未运行全仓clean verify或完整应用部署E2E。新旧幂等键不互认，未验证生产滚动升级、历史幂等状态迁移或安全混部，发布前必须明确停消费、在途排空与重复事件处置。Redis/DB一致性、崩溃恢复、租约续期/fencing、DLQ租户归属及Outbox可靠消息闭环不据此关闭。未commit/push，未删除既有容器。

文档检查于2026-10-23 06:54:30完成：`git diff --check`无诊断；`bash scripts/ci/check-doc-links.sh`退出0，扫描667份Markdown、3112条链接、断链0。该差异检查仅覆盖Git已跟踪内容，不将其解释为未跟踪文件已通过完整检查。

<a id="dlq租户上下文边界2026-10-23"></a>

### DLQ租户上下文边界（2026-10-23）

范围：公共DeadLetterQueueProcessor在业务回调前校验消息tenantId、安装租户并在finally恢复旧值；缺失或空白租户返回RECONSUME_LATER且不调用业务处理器。UserMessageDlqHandler取消default兜底，保留消息租户、载荷和既有重试参数，Outbox错误继续传播；日志去除完整body。

RED：DeadLetterQueueProcessorTest为14项、3失败、0错误、0跳过，复现旧租户未被消息租户覆盖、空上下文没有安装租户及非法租户仍进入业务处理器。UserMessageDlqHandlerTest补齐append Mock后为3项、1失败、0错误、0跳过，证明缺租户仍写入Outbox。首次GREEN尝试中空白属性被RocketMQ的putUserProperty提前拒绝，改用Mock模拟非法消息属性，保留全部非法输入断言。

GREEN：每次先`source scripts/build/init-build-env.sh`，使用Oracle17.0.12-oracle和mvnd2.0.0-rc-3，执行`mvnd -T4 -B -pl apex-infrastructure-messaging -am -Dtest=DeadLetterQueueProcessorTest,UserMessageDlqHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test`，17项全部通过、无跳过。该次旧JaCoCo执行数据存在类不匹配告警，未作为干净覆盖率结论。

干净门禁：执行`mvnd -T4 -B -pl apex-infrastructure-messaging -am clean verify`；首次因本批两处排版被Spotless阻断，仅对四个本批文件执行spotless:apply后重跑相同命令，5个reactor项目全部BUILD SUCCESS。common单测539项、domain单测9项、persistence单测83项，均Failures/Errors/Skipped=0；messaging单测115项、跳过10项（MessageOutboxDialectTest四项及DomainEventOutboxDialectTest六项），Failsafe五项、跳过三项（MqConsumerBrokerIntegrationTest），Failures/Errors均0。条件跳过不计通过。本轮未传jacoco.skip，覆盖率检查及Spotless通过；PMD check通过，但仍输出maximum Iterations exceeded诊断，不宣称扫描无错误日志。本轮是五项目范围门禁，不是全仓回归。

边界：未执行真实Broker重试转DLQ再入持久化Outbox的验收，未证明tenantId及原Topic属性在Broker转投过程中保留。可信生产者身份、头体租户一致性、重复重放及可靠消息闭环不据此关闭；无Handler时ACK丢弃策略保持既有契约。P0未准出，Java21未切换，D01–D07不代定；未commit/push，未删除既有容器。

### 全仓真实基线重新验证（2026-10-24）

背景：此前多轮补验均为定向范围（单模块或数个受影响模块），report.md反复记录"本轮非全仓回归""未运行全仓clean verify"。工作区同时存在795个文件改动未提交（HEAD仍为`0c506a7`），此前声称的大量构建/测试证据主要依赖非持久化`/tmp/*.log`与临时podman容器，未脚本化复核。为建立可信基线，本轮执行真正的全仓反应堆验证，不限定模块范围。

执行前核查：`source scripts/build/init-build-env.sh`确认JDK 17.0.12-oracle、mvnd 2.0.0-rc-3加载正常，未漂移。核查此前报告中提及的podman基础设施容器（`apex-mq-namesrv-20261022`、`apex-mq-redis-20261022`、`apex-mq-db-20261022`、`apex-mq-broker-20261024`、`apex-mq-ns-20261024`）均为`Up`状态，端口映射正常，数据卷未受影响。

命令：`mvnd -T4 -B clean verify`，完整日志`/tmp/apex-reverify-20261024-run2.log`（28971行）。首次尝试（日志`/tmp/apex-reverify-20261024.log`）在`apex-infrastructure-cache`的`InFlightRequestMergeTest.shouldRemoveEntryAfterCompletion`出现1项断言失败（`expected: 0, but was: 1`）；该测试与对应源码`InFlightRequestMerge.java`均为未跟踪新文件（`git status`显示`??`，无历史可比对）。单独重跑同一测试5次（`mvnd -pl apex-infrastructure-cache test -Dtest=InFlightRequestMergeTest`）全部4/4通过，判定为`-T4`并行构建下4模块同时编译测试的CPU争用导致`delayElement(10ms)`计时敏感断言偶发flake，非真实回归，与项目规则记录的计时敏感测试flake模式一致；未修改任何源码。

结果：2026-10-24 16:29:07 BUILD SUCCESS，31个reactor模块全部SUCCESS（apex-system 9分20秒、apex-auth-service 5分46秒、apex-workflow-service 5分6秒为耗时最长的三个模块），总耗时17:54 min。全局统计（按每行"Tests run/Failures/Errors/Skipped"文本累加，含测试类级与模块汇总级重复计数）：run=10778，fail=0，err=0，skip=42；按模块汇总行去重后真实跳过21项，明细与此前各轮定向补验记录完全吻合：
- `MessageOutboxDialectTest`（4项）+`DomainEventOutboxDialectTest`（6项）：方言矩阵条件跳过
- `MqConsumerBrokerIntegrationTest`（3项）、`UserMessageBrokerIntegrationTest`（5项）：未设置`APEX_REAL_MQ_IT=true`等真实Broker环境变量，按条件跳过
- `OfficeWorkflowBooleanIntegrationTest`（1项）：未设置`apex.office.real-db=true`
- `AuthMySqlTlsContractIntegrationTest`（1项）：未设置`APEX_AUTH_MYSQL_TLS_IT=true`
- `SeataAtIntegrationTest`（1项）：未设置真实TC及双库环境变量

以上7类条件跳过项此前均已通过定向补验获得真实GREEN证据（分别见上文"用户注册消费双表事务补验""Auth TLS、Office双库与Seata AT条件项补验""consumer维度幂等隔离"章节），本轮全仓默认verify不启用对应环境变量属于预期行为，不是回归，不是隐藏失败。

六项架构检查（`TenantIsolationArchTest`、`GatewayArchTest`、`PersistenceContractTest`、`NoBoundedElasticArchGuardTest`、`FlywayMigrationVersionTest`、`LayeringArchTest`）在本轮全仓verify中均执行且全部通过，与此前零散验证记录的检查范围一致。全部31个模块的Spotless check均执行完成，日志中未出现任何Spotless违规。PMD/p3c阶段仅出现已知的`aktStatus is NULL: maximum Iterations exceeded`及XPath规则废弃属性警告噪音，未出现任何`[ERROR]`级真实违规。JaCoCo覆盖率检查42次全部为"All coverage checks have been met"，无一处不达标。

边界：本轮验证证明当前795个未提交改动在默认环境变量下可完整通过全仓构建、单元测试、默认Failsafe集成测试、Spotless、PMD、六项架构检查与JaCoCo覆盖率门槛，是迄今唯一一次脚本化、单命令、可复现的全仓结论，取代此前依赖`/tmp`日志与手工累加的verified_union覆盖率叙事（403/586→500/586等）作为新基线。本轮不包含需要显式环境变量启用的7类条件集成测试（已有独立定向补验证据，未在本轮内重新执行）、TCC/SAGA/XA三种Seata模式、真实生产部署与完整应用启动。Java21迁移未开始，D01–D07决策点不代定，P0仍未准出。未commit/push，未删除既有容器，`InFlightRequestMerge`相关两个新文件保持未跟踪状态待后续按P0任务清单逐项核查。

### 两条Outbox线死信机制边界澄清（2026-10-24）

范围：核查42.19节完成的DLQ租户上下文边界修复后，是否需要为`sys_domain_event_outbox`（领域事件Outbox）补一个类似`UserMessageDlqHandler`的死信处理器。完整阅读`DeadLetterQueueProcessor.java`、`DomainEventOutboxDispatcher.java`、`DomainEventOutboxStore.java`、`DomainEventPublisherImpl.java`、`OutboxMetricsReporter.java`全文后确认，仓库内存在两条互相独立、机制不同的Outbox实现：

- 业务消息Outbox（`sys_message_outbox`）：面向RocketMQ真实发送；消费失败重试耗尽后由Broker按`%DLQ%+消费组名`原生死信Topic转发，`DeadLetterQueueProcessor`监听该Topic并路由给已注册的`UserMessageDlqHandler`，后者按租户写回Outbox重试。此线的"死信"是RocketMQ协议层概念。
- 领域事件Outbox（`sys_domain_event_outbox`）：`DomainEventPublisherImpl.publish()`只负责持久化到Outbox；`DomainEventOutboxDispatcher.deliver()`通过本地Spring`ApplicationEventPublisher.publishEvent()`投递，完全不经过RocketMQ，不产生任何MQ消息，因此不可能触发RocketMQ的死信转发机制。失败按指数退避重试，达到`apex.events.outbox.max-attempts`（默认8）后由`DomainEventOutboxStore.markFailed`落`DEAD`，这是该线JDBC状态机自身的终态，不是"MQ死信未被捡回"的缺口。

结论：之前拟定的"为领域事件Outbox线接入`DeadLetterQueueProcessor`/实现类似`UserMessageDlqHandler`的处理器"方案在架构上不成立，两者不是同类机制，强行对接属于概念混淆，不予实施。核查`OutboxMetricsReporter.java`确认该线的DEAD状态已有可观测出口：每`apex.events.outbox.metrics-interval-ms`（默认10s）统计`sys_domain_event_outbox`各状态数，刷新为Micrometer Gauge `apex.outbox.records{status=...}`供Prometheus抓取，查询异常时显式置NaN并联动`apex.outbox.metrics.up=0`避免误报陈旧快照；该指标已见于`docs/architecture/advanced-features/auto-retry-strategy.md`及P0阶段既有`declarations*.json`记录，非本轮新增。当前架构下DEAD状态的处置路径是"监控可见+人工/运维介入"，不是"自动重新入队"，与业务消息线的设计差异是故意的，非实现缺口。

边界：本次是纯代码阅读与方案审查，未修改任何源码。未重新评估`MessageOutboxServiceImpl.markAsFailed`查询+更新缺乏乐观锁保护的并发竞态风险（此前会话已识别，未展开处理）。未执行真实Broker端到端验证（42.19节已记录的边界：未证明tenantId及原Topic属性在Broker转投过程中保留，可信生产者身份、头体租户一致性、重复重放及可靠消息闭环不据此关闭）。P0未准出，Java21未切换，D01–D07不代定。

### 真实Broker端到端验证：消费耗尽重试→原生死信转发→DLQ处理器写回Outbox（2026-10-24）

范围：补齐42.19及43节反复记录的"未执行真实Broker端到端验证"边界，实际执行`UserMessageBrokerIntegrationTest`（[测试源码](../../../../apex-system/src/test/java/com/apex/scaffold/infrastructure/mq/listener/UserMessageBrokerIntegrationTest.java)）全部5项真实Broker用例，核心目标是验证"消费失败耗尽重试→RocketMQ Broker真实原生转发死信→`DeadLetterQueueProcessor`+`UserMessageDlqHandler`写回`sys_message_outbox`"完整闭环，并确认`tenantId`、原始`topic`在Broker转投过程中原样保留。

基础设施恢复：`podman ps`核查发现此前记录为`Up`的5个容器（`apex-mq-namesrv-20261022`、`apex-mq-redis-20261022`、`apex-mq-db-20261022`、`apex-mq-broker-20261024`、`apex-mq-ns-20261024`）在本轮启动时均为`Exited`。核对容器创建参数后确认`apex-mq-ns-20261024`+`apex-mq-broker-20261024`为同批配套（容器名`namesrv`在自定义podman网络`apex-mq-net`内可被broker解析），执行`podman start`恢复四个所需容器（namesrv、broker、redis、postgres）为`Up`。`apex-mq-broker-20261024`容器Entrypoint为占位`sleep 3600`，内部RocketMQ broker进程未运行，需手动以`sh bin/mqbroker -n namesrv:9876 -c conf/broker.conf`启动。

网络问题与修复：首次手动启动broker后，从宿主机执行测试在`sendOrderly`阶段全部5项均报`RemotingSendRequestException: send request to </10.89.4.3:10911> failed`。核查`podman port`/`podman inspect`确认根因：broker容器的`10911`/`10912`（RocketMQ客户端服务端口）未映射到宿主机，仅`47911`/`47912`两个与RocketMQ无关的预留端口有`127.0.0.1`映射；broker以默认配置启动时向namesrv注册的是容器内部网络IP（`10.89.4.3:10911`），namesrv将该地址原样下发给宿主机客户端，宿主机进程无法路由到podman内部网络IP。修复方式：停止broker进程，在`broker.conf`追加`brokerIP1 = 127.0.0.1`、`listenPort = 47911`、`haListenPort = 47912`，让broker复用已映射到宿主机的端口并上报宿主机可达地址，重启后日志确认`The broker[broker-a, 127.0.0.1:47911] boot success`。

执行：`source scripts/build/init-build-env.sh`加载JDK 17.0.12-oracle、mvnd 2.0.0-rc-3后，设置`APEX_REAL_MQ_IT=true`、`APEX_MQ_NAMESRV=127.0.0.1:41981`、`APEX_MQ_REDIS=redis://127.0.0.1:41979`、`APEX_MQ_JDBC_URL=jdbc:postgresql://127.0.0.1:41932/mq_acceptance`、`APEX_MQ_DB_USER=postgres`、`APEX_MQ_DB_PASSWORD=''`，执行`mvnd -T1 -B -pl apex-system -am -Dtest=UserMessageBrokerIntegrationTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`，完整日志保存于`/tmp/apex-real-mq-it-20261024-v2.log`。结果：8个依赖模块（common、license-starter、domain、infrastructure-persistence/cache/messaging/remote/integration）全部SUCCESS，`apex-system`模块`Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`，耗时37.33s，reactor整体`BUILD SUCCESS`。

关键用例`exhaustedReconsumeProducesNaturalDeadLetterPersistedToOutbox`真实日志证据：消息`240882140243A64011101015AEECF79137F8251A69D77A2F4A990002`（租户`f-21563dcd80684b0b855604b0eb8dfdba`）故障注入触发消费失败后，经真实Broker顺序重试4轮（`reconsumeTimes:0→1→2→3`）耗尽，被Broker原生转发至`%DLQ%apex_user_it_group_21563dcd80684b0b855604b0eb8dfdba`；`DeadLetterQueueProcessor`收到后日志确认"[DLQ] 收到死信消息...reconsumeTimes: 4"，交由`UserMessageDlqHandler`处理，日志确认"死信消息已重新写入Outbox"及"[DLQ] 死信消息处理成功"。另一独立故障注入场景（租户`e-21563dcd80684b0b855604b0eb8dfdba`）同样触发`DataIntegrityViolationException`（check constraint `registration_failure_injection`）并进入消费失败路径，验证了测试用例内多租户故障注入场景互不干扰。5项测试断言（含tenantId、原topic在Broker转投后保留的断言）均无失败。

边界：本轮只验证`UserMessageBrokerIntegrationTest`覆盖的业务消息Outbox线（`sys_message_outbox`），不涉及领域事件Outbox线（43节已澄清两者机制不同，不适用同一验证路径）。未执行`MqConsumerBrokerIntegrationTest`（需`APEX_REAL_MQ_INFRA_IT=true`，更底层、不含业务listener/DLQ）。网络修复是对测试容器`broker.conf`的手动运行时调整，未修改任何生产配置或源码，容器重建后该调整会丢失，不构成持久化基础设施变更。本轮未执行全仓`clean verify`，未验证TCC/SAGA/XA、真实TC环境下的Seata四模式。P0未准出，Java21未切换，D01–D07不代定；未commit/push，未删除既有容器。

### 将broker网络修复固化至容器编排定义（2026-10-24）

范围：上一节记录的`brokerIP1`/端口复用修复是对手动临时容器`broker.conf`的运行时调整，容器重建即丢失。本轮将该修复固化进[podman-compose.yml](../../../../podman-compose.yml)与[podman-compose.infra.yml](../../../../podman-compose.infra.yml)两份声明式编排定义的`rocketmq-broker` service，使其在标准`podman-compose up`重建流程下自动生效，不再依赖手工`podman exec`调整。

实现：核查RocketMQ`mqbroker`脚本源码（容器内`bin/mqbroker`）确认其`BrokerStartup`仅接受`-c <配置文件>`整份加载配置，不支持`-D`/`-p`等单项命令行属性覆盖（`-p`的实际语义是"打印配置后退出"，首次尝试误用已自行发现并纠正，未落盘）。改为在`command`中启动前用`printf`生成临时配置文件`/home/rocketmq/broker-external.conf`（含`brokerClusterName`、`brokerName`、`brokerId`及`brokerIP1=${ROCKETMQ_BROKER_EXTERNAL_IP:-127.0.0.1}`），再以`-c`加载后`exec`启动，同机场景默认复用`127.0.0.1`，跨主机场景可通过环境变量覆盖。两份文件同步补齐此前缺失的`haListenPort`（10912）端口映射。

验证：为避免与现有手动测试容器（`apex-mq-ns-20261024`/`apex-mq-broker-20261024`，端口`41981`/`47911`/`47912`）冲突，核查`podman port`确认二者互不占用后，执行`podman-compose -f podman-compose.infra.yml up -d rocketmq-namesrv rocketmq-broker`从零创建全新容器`apex-infra-rocketmq-namesrv`/`apex-infra-rocketmq-broker`。日志确认`The broker[broker-a, 127.0.0.1:10911] boot success. serializeType=JSON and name server is rocketmq-namesrv:9876`；`podman exec`确认容器内自动生成的`/home/rocketmq/broker-external.conf`内容为`brokerIP1=127.0.0.1`等四行，且`ps aux`确认broker进程命令行为`sh mqbroker -n rocketmq-namesrv:9876 -c /home/rocketmq/broker-external.conf`，证明该修复在compose机制创建的全新容器中无需任何手动干预即自动生效，不止是此前手动容器内复现同款shell逻辑。验证后执行`podman-compose -f podman-compose.infra.yml stop`+`podman rm`清理该隔离验证容器，未保留，未影响既有手动测试容器与其他服务。

边界：本轮只验证了broker启动阶段的网络注册行为（日志确认`boot success`及配置文件内容），未在该新建隔离容器上重跑`UserMessageBrokerIntegrationTest`端到端消息测试（该测试已在42.19/44节用手动容器验证通过，机制相同，未重复执行）。`podman-compose.yml`中的同款修改仅验证过逻辑等价（与`infra.yml`共享同一段`command`文本），未独立对该文件单独执行一次`up`重建；`infra.yml`已完成独立重建验证。未执行全仓`clean verify`，未提交git（两份compose文件为工作区未提交改动）。P0未准出，Java21未切换，D01–D07不代定；未commit/push，未删除既有容器。

### 补验：podman-compose.yml独立重建+端到端消息闭环复核（2026-10-24）

范围：补齐上一节记录的两处边界——`podman-compose.yml`未独立重建验证、未在固化后的新建容器上重跑端到端消息闭环。

执行：`podman-compose -f podman-compose.yml --profile rocketmq up -d rocketmq-namesrv rocketmq-broker`解析整份compose文件时因顶层`APEX_DATA_ENCRYPTION_KEY`等五个YAML锚点必填密钥变量未设置而报`required variable ... is missing a value`；该校验是既有安全机制（防止业务服务用默认值启动），与本次改动无关，不应绕过设计本身，故在shell临时`export`五个仅用于满足解析期校验的占位值（`rocketmq-namesrv`/`rocketmq-broker`两个service的`environment`块未引用这些变量，不影响其实际配置）后重新执行，创建全新容器`apex-scaffold_rocketmq-namesrv_1`、`apex-scaffold_rocketmq-broker_1`。日志确认`The broker[broker-a, 127.0.0.1:10911] boot success`，`podman exec`确认自动生成的配置文件含`brokerIP1=127.0.0.1`，证明`podman-compose.yml`文件本身（非仅`infra.yml`）在真实重建流程下同样生效。

随后以该新建容器为Broker端点，`source scripts/build/init-build-env.sh`后另行显式导出`JAVA_HOME=/Users/eric/.sdkman/candidates/java/17.0.12-oracle`（核查发现`~/.sdkman/candidates/java/current`符号链接当前漂移至GraalVM 21，`sdk env`回显声明版本但未真正切换shell的`JAVA_HOME`/`PATH`，与AGENTS.md"禁止依赖current"的记录一致，显式指定绝对路径后`java -version`/`mvnd --version`确认均为17.0.12-oracle），设置`APEX_REAL_MQ_IT=true`、`APEX_MQ_NAMESRV=127.0.0.1:9876`、`APEX_MQ_REDIS=redis://127.0.0.1:41979`、`APEX_MQ_JDBC_URL=jdbc:postgresql://127.0.0.1:41932/mq_acceptance`等复用既有手动Redis/PostgreSQL测试容器，执行`mvnd -T1 -B -pl apex-system -am -Dtest=UserMessageBrokerIntegrationTest test`，日志`/tmp/apex-real-mq-it-20261024-compose-verify.log`。结果：`Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`；日志中出现的`dlq_failure_injection`/`registration_failure_injection`相关`ERROR`行是测试用例故障注入断言的预期产物，非真实失败。证明固化进`podman-compose.yml`的网络修复在全新创建、未经任何手动干预的容器上，完整支撑了消费失败耗尽重试→Broker真实原生死信转发→`UserMessageDlqHandler`写回Outbox的端到端闭环，不止是broker启动成功这一项。

验证后执行`podman stop`+`podman rm`清理`apex-scaffold_rocketmq-namesrv_1`/`apex-scaffold_rocketmq-broker_1`两个隔离验证容器，确认清理完整（`podman ps -a`无残留）。清理过程中发现既有手动容器`apex-mq-broker-20261024`已于验证窗口期内变为`Exited`——核查确认该容器`Entrypoint`固定为占位`sleep 3600`（内部真正运行的RocketMQ broker进程是此前另行手动以`sh bin/mqbroker`启动，依附于该容器生命周期），启动于17:14:57，整一小时后于19:10:18自然到期退出，与本轮`podman stop`/`rm`命令（仅针对`apex-scaffold_rocketmq-*`命名的容器）无关，纯属时间窗口重叠；该容器内此前手动启动的broker进程随容器退出已同步终止，未恢复，留待用户确认是否需要重新拉起。

边界：本轮已补齐"podman-compose.yml独立验证"与"固化后端到端消息闭环复核"两项此前遗留边界。未验证`podman-compose.yml`中其余业务service（网关、五个业务服务等）在真实部署下的行为，本轮仅针对`rocketmq-namesrv`/`rocketmq-broker`两个service。未执行全仓`clean verify`，未提交git。`apex-mq-broker-20261024`手动容器已到期退出且未重新拉起，不在本轮固化范围内（该容器从未被compose管理，属于此前会话的临时验证资产）。P0未准出，Java21未切换，D01–D07不代定；未commit/push，未删除既有容器（本轮新建的隔离容器已清理，非既有容器）。

### 需求评审收口：前端对齐、O08b 静态缺口修复与到期门禁（2026-10-31）

对应[方案](../../../02-architecture/jdk21-unified-auth-multitenancy-implementation-plan.md)42.33。评审确认 42.20–42.32 后端实现与文档声称一致后，本轮修复 42.31 记录的生成器异步链缺口 a–h（i 为 V71 历史迁移注释，按约定不改已执行迁移）：RUNNING 过期候选补 attempts 上限；`updateProgress`/`markCompleted`/`markFailed` 全部限定 `locked_by` 且进度回写顺延 `locked_at` 充当心跳；调度器认领后按主键重读实体；`renewLease` 以 `LeaseGuard` 接入执行链（任务入口、每单元写盘前、历史落库前复检，取消在首次写盘前感知、接管后旧执行者不写历史与产物）；执行期重算输入摘要比对 `previewToken` 并以实际可读表重算 `totalCount`；字段映射 JSON 损坏快速失败；保留期清理改「先查主键、后按主键物理删除」两步可移植实现。另修复方案未记录的调度器租户桥接缺陷：生成器、体检导出、体检保留期三条链路原仅 `contextWrite`，企业档开启租户拦截器时 JDBC 线程缺 ThreadLocal 租户会被 `DefaultTenantLineHandler` 拒绝，且 `gen_task` 跨租户 `DISTINCT tenant_id` 扫描不可执行；改为读取白名单表 `sys_tenant` 逐租户执行并以 `TenantContext.withTenant(Mono)` 桥接。新增 `GET /admin-api/v1/generator/tasks/{id}/artifacts` 补齐异步下载闭环；`TenantEntity.canCreateBusinessData()` 纳入 `expireTime` 断言；`WorkflowQueryService.convertToNodeDTO` 单节点 position 类型漂移仅降级该字段不再吞整图；Nacos `workflow-domain` 路由补 `/api/v2/workflow-definitions/**` 并扩展 `GatewayRouteProfileConfigTest` 断言；新增 `PasswordRecoveryHandlerIntegrationTest` 9 项补齐 O01 handler 级集成测试缺口。新增测试：`GenTaskMapperSqlContractTest` 4 项（反射锁定注解 SQL 契约）、`GeneratorWizardServiceTest` 异步链用例、`GeneratorTaskServiceTest`/`GeneratorTaskSchedulerTest`/`TenantEntityTest`（ExpireTests 4 项）扩展。前端（apex-admin-web 独立仓库）：生成器向导页签、运维租户摘要页签、站内信投递记录抽屉、匿名页 `/share` 与 `/password-recovery`、租户页按钮权限绑定；vue-tsc 通过、Vitest 44/44、`npm run build` 成功。

| 实际执行 | 结果与边界 |
| --- | --- |
| 定向单测/集成：GeneratorTaskServiceTest 13、GeneratorWizardServiceTest 18、GeneratorTaskSchedulerTest 9、GeneratorServicesTest 4、GenTaskMapperSqlContractTest 4、TenantEntityTest 20、PasswordRecoveryHandlerIntegrationTest 9、GatewayRouteProfileConfigTest 3、OfficeFlywayMigrationTest 5 | 全部 Failures 0 / Errors 0，BUILD SUCCESS；JDK 21.0.13.0+9-tem + mvnd 2.0.0-rc-3 固定环境 |
| apex-workflow 全模块 test | 164 项，Failures 0 / Errors 0 |
| `mvnd -T4 -B -pl apex-gateway,apex-system,apex-workflow,apex-office-service,apex-auth-service -am verify`，2026-10-31 12:30:37 +08:00 自然退出 0 | 20 个反应堆项目全部 SUCCESS；Surefire 汇总 4944 项（失败/错误 0，跳过 10 为 messaging 真实 MQ 门控）；Failsafe 汇总 227 项（失败/错误 0，跳过 10：system 5 含真实 Seata TC 门控、messaging 3 真实 MQ、office-service 1、auth-service 1 TLS 门控）；Spotless、PMD/p3c、JaCoCo 通过；`git diff --check` 无空白错误 |
| 前端 vue-tsc --noEmit / vitest run / npm run build | 类型检查通过；44/44 通过（含新增 generator.test.ts 7 项、message.test.ts 扩展 3 项）；生产构建成功 |

边界：受影响反应堆 verify 不等于全仓 clean verify（未含 file/example/generator/tools 等无关模块）；surefire/failsafe 汇总数为各模块 TEST-*.xml 直接求和，未与插件逐行输出二次核对；调度器租户桥接修复未在真实企业档 MySQL/PostgreSQL 环境联调；前端新页面与真实后端联调未执行；V71 注释前缀保留不改。P0未准出；未commit/push。
