# BE-063 Wave E 全仓门禁与报告

## 范围

固定环境执行不带 `clean` 的全仓 `mvnd -T4 -B verify`，保留已有 `target/` 产物；随后运行测试报告汇总脚本。基线 `dev` SHA `90a2f783e2c251a55f53b0fc1b845b11d537e951`。Wave E 不修改生产代码、测试、依赖、质量规则或旧任务。

## 改动清单

- 仅登记全仓门禁失败、测试报告快照及后续处理边界；测试报告输出在 git 忽略的 `target/reports/summary/`，没有引入跟踪报告文件。
- 保留全部工作区既有差异；未暂存、提交、推送或部署。

## 验证证据

- `source scripts/build/init-build-env.sh && mvnd -T4 -B verify`：`BUILD FAILURE`，退出码 1。`apex-auth-service` PMD/PMD 6.55 / ASM 在解析 Java 21 class 时抛 `Unsupported class file major version 65`；未跳过或放宽 PMD。输出报告位于 `apex-services-bom/apex-auth-service/target/pmd.xml`。
- 此并行 reactor 同时记录 `ExampleDataSourceTimeoutTest.shouldBoundUnresponsiveDatabaseHandshakeUsingTestPoolConfiguration` 实测 12.667 秒，高于测试断言 12 秒，1 次失败。XML 位于 `apex-applications-parent/apex-example/target/surefire-reports/`；未改动该测试或断言。该时延失败可能受本机全量并行负载影响，需单独复测，不能简单忽略。
- `bash scripts/test/generate-test-report.sh`：命令成功；45 模块现存 XML 快照计数 Surefire 6278、Failsafe 405、总计 6683，失败 1、错误 0、跳过 33，另有 4 项重复 testcase 告警。输出 `target/reports/summary/test-report.md`、`test-summary.json`。脚本只是汇总现存报告，不能证明各 XML 同一构建/代码 SHA，也不是全量 verify 成功证据。
- Wave D1 专项测试 `mvnd -B -pl :apex-tools-build -am -Dtest=KubernetesReleaseContractTest -Dsurefire.failIfNoSpecifiedTests=false test spotless:check` 通过 2/2，Spotless 通过；`bash scripts/test/test-k8s-gateway-release.sh` 通过；严格文档、链接、后端红线和 `git diff --check` 门禁通过。
- 未运行 clean，以避免移除既有目标产物。未进行生产集群、容量/延迟或蓝绿演练。

## 影响面

Wave D1 实现已独立 R5 `APPROVE_WITH_NITS` 并释放本地实现租约；BE-063 Wave E 未达到全仓验收。需另立受控任务确认 PMD 6 与 JDK 21 字节码兼容（不能以降级/跳过质量门禁规避）并复测 12 秒数据库超时用例的稳定性，再运行全量验证。PE/FE 任务状态和旧任务待办不因本记录改变。

## 回滚方式

本波仅生成 git 忽略的 `target/reports/summary/` 汇总产物和文档记录，没有源代码或依赖变更；保留它们作为失败门禁和 XML 快照证据。不得执行 `clean` 删除目标目录或覆盖其他用户产物。
## 2026-11-23 后续复验

- 固定环境下再次执行 `mvnd -T4 -B verify`，仍失败且未生成可宣称为全仓通过的新报告：`apex-api-contract` 的 2 条 DTO 包装类型规则违规与 `apex-tools-deployment` 的 74 条规则违规首先阻断 reactor；其余模块因并行构建结果部分完成/跳过，不能以该次命令宣称全仓测试结论。
- BE-065 最新独立 `pmd:pmd` 扫描刷新 37 份 XML：252 条违规、18 个模块、0 个 XML `<error>`；P3C 仍有 `maximum Iterations exceeded` 诊断。未放宽门禁、未扩大排除清单。
- 本波仍保持 `执行中` / `WAITING`，BE-064/BE-065 待独立 R5，BE-063 的真实集群灰度、蓝绿切流和容量基线仍未验证。

## 2026-11-23 后续 PMD 修复波次

- BE-065 已修复此前首要阻断的 `apex-api-contract` 2 条与 `apex-tools-deployment` 74 条违规；两个模块的定向编译/测试、Spotless、PMD 均通过。
- 全仓 PMD 重新扫描结果为 37 个模块报告、176 条剩余违规、0 个 XML `<error>`；因此 Wave E 仍未达到全仓 verify 验收，不生成新的全仓通过报告。
- 剩余违规位于业务模块，真实集群灰度、蓝绿切流、负载/容量基线和独立 R5 仍未验证；旧任务未覆盖或关闭。
- 后续远程基础设施新增两处类 Javadoc 整改，定向 PMD/Spotless 通过；刷新后全仓 PMD 为 174 条违规、0 个 XML `<error>`。全仓 verify 仍需从修复后的工作树重跑，未生成新全仓报告。

## 2026-11-24 隔离全仓验证

- 从 `dev` `90a2f783e2c251a55f53b0fc1b845b11d537e951` 上含本轮未提交改动的工作树导出到 `/private/tmp/apex-wave-e-20261124.lwjlzF`；未复制 `.git`、忽略内容和旧 `target/`，原仓产物保持原状。固定 JDK 21.0.13.0+9-tem / mvnd 2.0.0-rc-3 运行 `mvnd -T4 -B clean verify`（`verify.log`）。
- 首轮 44 成功、1 失败、0 跳过模块。唯一失败是 `apex-auth-service` 中 `RemoteTenantStatusAdapter` 构造调用的 Spotless 换行；不是用例断言或 PMD Java 21 解析失败。`ExampleDataSourceTimeoutTest` 1/1 通过（12.25 秒，无固定 12 秒断言）；file-service、integration-service、knowledge-service、boot-all 全部成功。未从首轮失败结果生成报告。
- 仅修正 `RemoteTenantStatusAdapter` 换行后，从同一隔离目录执行 `mvnd -T4 -B verify -rf :apex-auth-service`（`verify-resume.log`）。续验与首轮覆盖互补；如成功，汇总结果应称为同一干净副本的分段验证，不称为单次 `clean verify` 全绿。
- 续验退出 0，19/19 模块成功，包含 auth-service 的 Spotless 和 PMD。两段合计覆盖全部 45 个模块；抽查范围内 3515 份 Java、POM、资源及工具链配置与原仓逐字节一致。37 份 PMD XML 含 0 条违规、0 个 XML error；PMD 6 数据流分析诊断仍在，详见 [BE-065 记录](./2026-11-23-be065-pmd-rule-repair.md)。
- 在副本执行 `bash scripts/test/generate-test-report.sh` 退出 0，产物为 `/private/tmp/apex-wave-e-20261124.lwjlzF/target/reports/summary/test-report.md` 与 `test-summary.json`。按 XML `tests` 属性汇总：Surefire 6277（跳过 15），Failsafe 405（跳过 18），合计 6682、通过 6649、失败及错误均为 0；system 四份重复 testcase 告警未改变计数。跳过项涉及 MQTT、真实消息代理、MySQL TLS、office 双库、Outbox 双库和 Seata 真 TC，不计为通过。
- 不覆盖原仓历史报告。该结果不是单次全仓 `clean verify` 成功，也不代表真实集群、容量、切流或完整数据流规则覆盖；最终代码独立 R5 结论及集成提交仍待处理。

## 2026-11-24 单次隔离全仓中间快照

- 完成 BE-065 MySQL 工具与仓储注释整改后，在 `/private/tmp/apex-wave-e-final-20261124.u4XOBS` 固定工具链执行单次 `mvnd -T4 -B clean verify`，45 模块通过，完成于 12:45:45（Asia/Shanghai）。
- 同一副本随后运行 `bash scripts/test/generate-test-report.sh` 退出 0。汇总 Surefire 6280、Failsafe 405，共 6685 项，跳过 33、失败和错误均为 0，另有 4 条重复 testcase 告警；原始日志为该副本的 `verify.log` 与 `report.log`。该构建早于 BE-064 握手属性断言，不作为最终结果。

## 2026-11-24 最终单次全仓验证

- 从 `dev` `90a2f783e2c251a55f53b0fc1b845b11d537e951` 加当前未提交改动导出独立快照 `/private/tmp/apex-wave-e-accepted-20261124.65m9GF`，包含 BE-064 最终握手属性断言及 BE-065 最终整改。未复制 `.git` 或旧 `target/`；验证后逐字节比较 3800 份工作树非文档输入，差异 0。
- 固定 JDK 21.0.13.0+9-tem、mvnd 2.0.0-rc-3，执行 `source scripts/build/init-build-env.sh && mvnd -T4 -B clean verify`，单次 45/45 模块 `BUILD SUCCESS`，耗时 16:48，结束于 13:04:24（Asia/Shanghai）。握手超时测试 1/1 通过；没有降低门禁或跳过测试阶段。
- 同一命令链随后执行 `bash scripts/test/generate-test-report.sh`，退出 0。Surefire 6280 项、跳过 15；Failsafe 405 项、跳过 18；合计 6685 项，6652 通过、33 跳过、0 失败或错误，另有 4 条重复 testcase 告警（按 XML `tests` 属性计数）。报告与 JSON 均在该副本的 `target/reports/summary/`，原始日志为 `verify.log` 和 `report.log`；未覆盖原仓产物。
- 37 份 PMD XML 含 0 条违规、0 个 XML error，未再出现 Java 21 字节码解析失败。日志仍报告 PMD 6 数据流分析达到最大迭代数，不能据此推断完整规则覆盖。跳过项不视为通过；本地门禁也不替代最终整包终审、集成提交、真实集群切流或容量/SLO 验收。
- 独立 R5 Curie 只读核对构建结束状态、最终测试文件摘要、同轮报告和 PMD XML，确认可作为 BE-064/065 本地门禁证据；结论不覆盖整包差异、构件兼容或生产放行。
