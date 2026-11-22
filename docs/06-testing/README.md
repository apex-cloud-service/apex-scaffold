# 测试文档

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

本目录收录项目的测试文档，按主题维护当前有效文档。

## Java 21 与统一认证多租户升级

- [P0 阶段执行报告](jdk21-unified-auth/20261016-p0/report.md)：构建检查修复、资产采集与实际验证；P0 尚未准出，不代表完整回归或 Java 21 验收通过。
- [P0 文件哈希清单](jdk21-unified-auth/20261016-p0/baseline.json)：模块、Java、迁移与选定配置元数据，不是 AST/Javadoc 覆盖率或完整恢复包。
- [P0 声明清单](jdk21-unified-auth/20261016-p0/declarations.json)：2203 个 Java 文件、26601 条显式声明的 javac parse-only 清单，不是语义验证或 Javadoc 覆盖率。
- [P0 路由清单](jdk21-unified-auth/20261016-p0/routes.json)：250 条已解析路由与 148 条未解析候选，非运行期注册或 OpenAPI 覆盖证明。
- [P0 迁移表清单](jdk21-unified-auth/20261016-p0/tables.json)：170 个脚本的 767 条静态表事件，不是真实 schema 或 SQL 语义等价。

## 查询聚合与统一待办验证

- [查询聚合与统一待办测试报告](aggregation-todo-20261113/report.md)：覆盖 `apex-aggregation-service`（8098）统一待办门面、office 审批撤销幂等编排（V9/V10）、system 概览聚合舱壁的单元/集成/压力/真实压测四类结果，以及本轮发现并修复的 9 项缺陷。报告含本地最小栈压测的真实数值与未覆盖边界（网关层、MySQL/PG 真实库执行）。

## 中台架构整改验证

- [中台架构整改完成测试报告](middle-platform/20261110-completion/report.md)：记录 G1–G8 代码/静态契约关闭、定向 `mvnd` 测试、完整 `clean verify`、双库迁移测试结果及真实环境未验证项。该报告不构成完整中台生产验收结论。

## 基础设施与站内信验证

新增入口 `bash scripts/test/verify-infrastructure-websocket.sh --infrastructure`（五能力和直接消费者及依赖）、`--websocket`（office/gateway 及依赖）、`--full`（完整 reactor）。脚本在 zsh 子 shell 中加载固定构建环境，再执行 mvnd `clean verify`，不跳过 Surefire、Failsafe 或现有质量门禁；针对性验证不能代替完整 reactor 验证。脚本本次仅创建和静态检查，尚未执行。最终结果由主代理在[交付报告](../08-project-management/reports/infrastructure-websocket-delivery-report.md)填入真实记录。

## 阻塞 I/O 虚拟线程压测

- [虚拟线程压测报告](virtual-thread-bench/20261027/report.md)：`/api/v1/health` 在 `event`、`cache` 池上平台线程与虚拟线程对比。第一轮 `-c200` 5 轮全部出现 wrk socket timeout，数据不可用；重测 `-c32` 1 轮 + `-c64` 5 个有效样本后，逐轮配对中位吞吐 +18.2%（中位值汇总 +12.9%）、中位 p99 -18.2%，属方向性正收益但幅度接近噪声，不支撑调整默认配置。虚拟线程开关已由 JFR `jdk.VirtualThreadStart` 证实生效；同批次的 `mvnd -T4 -B clean verify` 已 `BUILD SUCCESS`（31 个聚合模块，Surefire 5057 / Failsafe 389）。

## 当前报告汇总口径

在仓库根目录运行 `bash scripts/test/generate-test-report.sh` 或 `bash scripts/test/generate-comprehensive-test-report.sh`；两入口复用同一实现（`apex-tools-deployment` 的 `TestReportSummary`，需先完成该模块打包），仅解析现存 XML，不执行构建。模块沿根 POM `<modules>` 递归发现（不再使用硬编码清单或单层目录 glob）；Surefire/Failsafe 分列统计，`skipped` 仅代表报告中明确跳过的测试。无报告模块标注“单元/集成”缺失，不能推断未执行用例数量或记为通过。XML 解析禁用 DOCTYPE 与外部实体。`tests` 属性为权威计数：按 `classname#name` 去重后的 `testcase` 数与 `tests` 不符即判定报告被截断并**非零退出**；仅有重复 `testcase`（Surefire 重复执行/嵌套类的合法输出）时计入告警、不阻断。非法属性或畸形 XML 同样非零退出。报告不校验整次 `verify` 是否通过。

固定输出 `target/reports/summary/test-report.md` 和 `test-summary.json`（未实现 `TEST_REPORT_OUTPUT_DIR` 覆盖）；两文件不保留逐文件 XML 路径与修改时间，也不证明同一次构建、最新代码或完整 verify 门禁通过；损坏 XML 导致脚本失败。真实云桶测试、TC/双库等因环境缺失而跳过时必须单独披露，不得列作通过。存储配置与验证命令见[对象存储指南](../05-operations/object-storage-integration.md)。

## 最近一次全量验证（2026-11-22，BE-049）

- [2026-11-22 BE-049 全量验证报告](./2026-11-22-be049-full-verify-report.md)：最终门禁 `mvnd -T1 -B clean verify` 45/45 SUCCESS、控制台 6740 测试 0 失败/33 跳过（23:37）、XML 汇总 6676（0 失败）；含七次执行的失败与处置记录。

## 全仓对齐战役全量验证（2026-11-22，BE-045~BE-048）

- [2026-11-22 全量验证报告](./2026-11-22-full-verify-report.md)：`mvnd -T4 -B clean verify` 全反应堆结果、Surefire/Failsafe 计数与环境依赖跳过项披露。当日早间 BE-044 已有一次全量 `BUILD SUCCESS`（45/45 项目，耗时 13:24，证据见[变更记录](../records/2026-11-22-code-quality-pattern-spi.md)与台账 #78）；本报告为全仓对齐战役（BE-045~BE-048）收尾复验的实测记录。

## 历史报告导航（均为带日期历史快照，正文命令与数据不作为现状依据）

- [测试执行指南](./TEST_EXECUTION_GUIDE.md)（历史快照，2026-11-11 复核：正文 `mvn`/Java 17 命令不可直接执行）
- [集成测试指南](./integration-test-guide.md)（历史快照，工作流接口联调口径）
- [测试报告](./TEST_REPORT.md)（历史快照，2026-10-05~10-26 段；含微服务改造 Phase 1–4 段落）
- [认证中心 JWT/JWKS 测试报告](./auth-center-jwt-jwks-test-report.md)（2026-10-08，Java 17 环境快照）
- [网关模块测试报告](./gateway-module-test-report.md)（2026-10-08，Java 17 环境快照）
- [契约模块测试报告](./contract-module-test-report.md)（2026-10-08，Java 17 环境快照）
