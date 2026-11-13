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

在仓库根目录运行 `bash scripts/test/generate-test-report.sh` 或 `bash scripts/test/generate-comprehensive-test-report.sh`；两入口复用同一实现，要求 Python 3，仅解析现存 XML，不执行构建。模块从根 POM 动态读取；Surefire/Failsafe 分列统计，`skipped` 仅代表报告中明确跳过的测试。无报告标记为“未执行或无可用报告”，JSON 计数为 null，不能推断未执行用例数量或记为通过。JaCoCo 读取 XML 的 LINE/BRANCH counter，单元/集成报告不相加。

默认输出 `target/reports/summary/test-report.md` 和 `test-summary.json`。如需避免覆盖最终报告，设置 `TEST_REPORT_OUTPUT_DIR` 为隔离目录。报告保留 XML 路径和修改时间，但不证明同一次构建、最新代码或完整 verify 门禁通过；损坏 XML 导致脚本失败。真实云桶测试、TC/双库等因环境缺失而跳过时必须单独披露，不得列作通过。存储配置与验证命令见[对象存储指南](../05-operations/object-storage-integration.md)。

- [测试执行指南](./TEST_EXECUTION_GUIDE.md)
- [集成测试指南](./integration-test-guide.md)
- [测试报告](./TEST_REPORT.md)
- [认证中心 JWT/JWKS 测试报告](./auth-center-jwt-jwks-test-report.md)
- [网关模块测试报告](./gateway-module-test-report.md)
- [契约模块测试报告](./contract-module-test-report.md)
