# BE-064 构建门禁修复

## 范围

修复 BE-063 Wave E 暴露的 PMD/Java 21 字节码解析兼容性和数据库握手超时测试稳定性。基线 `dev` `90a2f783e2c251a55f53b0fc1b845b11d537e951`；不处理 BE-043、BE-049、FE-029 的未完成事项。

## 改动清单

- Maven PMD 插件显式使用 ASM 9.6，保持 PMD 6.55、P3C 2.1.1、Java 20 分析级别及 `failOnViolation=true` 不变。
- 数据源握手测试验证绑定的 10 秒 `socketTimeout`，并要求实际失败链以 `SocketTimeoutException` 结束；删除受机器调度影响的固定 12 秒墙钟断言，保留 20 秒 watchdog 仅作为配置失效时的防卡死兜底。

## 验证证据

`source scripts/build/init-build-env.sh && mvnd -B -pl :apex-example -Dtest=ExampleDataSourceTimeoutTest -Dsurefire.failIfNoSpecifiedTests=false test` BUILD SUCCESS，1/1、0 failures/errors/skips（11.319 秒）；此测试校验配置值及根因类型，不以系统调度耗时判断通过。

`source scripts/build/init-build-env.sh && mvnd -B -pl :apex-auth-service pmd:pmd` BUILD SUCCESS，auth-service 新报告不再包含 `Unsupported class file major version 65`，仍有 3 条规则违规。尝试 `-pl :apex-auth-service -am verify` 时 ASM 异常已消除，但先在 `apex-common` 的 PMD check 失败（32 条现存规则违规），未到 auth-service verify；未豁免这些违规，也未宣称全仓通过。此前 Wave E 记录仍有效，新的失败与待办另行登记。

## 影响面

仅构建期 PMD 插件依赖与 `apex-example` 测试代码；无生产运行行为变化。全量门禁仍被 `apex-common` 的 32 条 PMD 违规及 auth-service 的 3 条违规阻断，需单独评估、修复后才能重新完成全量验证。

## 回滚方式

仅回滚本任务明确列出的文件；不执行全仓恢复或清理其他任务的工作区差异。

## 追加：全仓门禁复核

`-pl :apex-auth-service -am verify` 已不再遇到 ASM Java 21 字节码异常，但在 `apex-common` 的 32 项现存 PMD 违规处停止；独立 auth PMD 报告还有 3 项规则违规。没有修改 PMD 规则、exclude 文件或旧源代码来绕开门禁，继续修复已拆为 BE-065。BE-064 仍保持执行中，直到 BE-065 完成、全仓 verify 和同一轮测试报告达标，再提交独立复核。

2026-11-24 [Wave E 隔离全仓验证](./2026-11-23-be063-wave-e-full-verify.md#2026-11-24-隔离全仓验证)中，Example 超时回归通过，auth-service 续验通过 Spotless 与 PMD，未再报 Java 21 字节码异常。分段构建覆盖 45 模块并产生报告；不是单次全仓 clean verify 成功，BE-064 仍开放，待独立复核。

## 2026-11-24 握手属性复核整改

- Curie 首轮 `REJECT`：ASM 插件隔离及 watchdog EOF 不误判均符合要求，但池属性 `socket-timeout` 不能替代 Connector/J 握手的 `connection-properties.socketTimeout` 校验。
- 明确在惰性数据源初始化前独立校验 Druid 传给物理连接创建路径的 `socketTimeout=10000`，再触发真实无响应握手，要求 `SocketTimeoutException` 根因。保留 20 秒兜底，不以墙钟时长判定通过，不改 YAML 或生产代码。
- 固定工具链执行 `mvnd -T4 -B -pl :apex-example -am -Dtest=ExampleDataSourceTimeoutTest -Dsurefire.failIfNoSpecifiedTests=false test spotless:check pmd:check`，17 模块均成功，目标用例 1/1 通过、无跳过（13.08 秒）。原始日志 `/private/tmp/apex-be064-focused-20261124.I7gs6s/focused.log`；已交独立增量复核。
- Curie 对最终测试改动的静态增量复核为 `APPROVE`，无必改项；确认两个隔离快照中的测试文件与被审版本一致。复核时未使用未完成的全仓结果作为放行证据；同轮构建结果独立见 Wave E。
- [最终单次全仓验证](./2026-11-23-be063-wave-e-full-verify.md#2026-11-24-最终单次全仓验证) 45/45 模块通过，握手用例再次 1/1，通过同轮报告汇总；此前 32/3 条所致构建阻断已消除。未集成、未关闭。

## 2026-11-24 集成状态核对

- 当前 `dev` HEAD `4a0d6782` 的汇总发布提交包含 BE-064 的 PMD 插件兼容调整及握手测试改动；该提交同时包含其他任务内容，并非按治理 §4.10 逐任务执行的 BE-064 专属集成提交。
- Curie 的 `APPROVE` 仅覆盖握手测试的最终整改增量；Wave E 的独立复核确认最终隔离全仓构建与同轮报告可作为本地门禁证据，不等同于对 BE-064 整个集成 diff 的最终 R5 终审。
- 因此 BE-064 保持 **待复核**。关闭前仍需独立 R5 绑定最终任务范围、任务专属集成与治理/台账证据；不改变已记录的 45/45 构建及 6685 项报告结果。

## 2026-11-24 独立 R5 复核（整包 BE-064 diff）

复核范围仅为 BE-064 的两个文件：根 `pom.xml`（`pmd.asm.version` 9.6）与 `ExampleDataSourceTimeoutTest.java`。结论 **APPROVE_WITH_NITS**，无必改项：

- POM 变更仅为 `maven-pmd-plugin` 自身 `<dependencies>` 内的 ASM 版本 pin（`pom.xml:128`、`pom.xml:1047-1051`），不进入项目 `dependencyManagement` 或生产 classpath；`failOnViolation=true`、10 个 p3c 规则集、`excludeFromFailureFile`、`targetJdk` 20、`pmd.skip=false` 在 `90a2f783..4a0d6782` 间零改动；13 个重复声明该插件的模块 POM 均无自有 `<dependencies>`，不会丢 pin。
- 测试断言被**加强**而非削弱：`application-test.yml` 绑定校验 `socketTimeout=10000`（`:31`）、`DruidDataSource.getConnectProperties()` 校验物理连接属性（`:55`，已核对 Druid 1.2.25 MySQL 分支原样透传）、根因断言由 `Exception` 收紧为 `SocketTimeoutException`（`:61`）、`createDataSource` 移出异常断言块（`:54`），20 秒 watchdog 仅防死锁。
- 证据可复核：聚焦运行 17 模块 SUCCESS、目标用例 1/1（13.08 秒）；最终快照 45/45 SUCCESS、6685 项/0 失败/33 跳过；37 份 PMD XML 零违规零 error；`Unsupported class file major version` 计数为 0；快照内两文件与当前 HEAD 逐字节一致。
- 非阻断建议：N1 汇总提交不满足治理 §4.10 逐任务集成（故仍待复核）；N2 若未来引入 `asm-tree`/`asm-analysis` 需与该 pin 同步；N3 原 12 秒墙钟上限已移除，仅验证机制不验证时延量级；N4 证据位于 `/private/tmp` 非持久。

本结论不覆盖：`4a0d6782` 中其余 203 个文件、PMD 数据流最大迭代诊断（BE-065 未关闭项）、Java ABI/构件兼容与生产发布放行。

## 2026-11-24 BE-060 复核连带整改

BE-060 的独立 R5 在复核 SpringDoc 门禁时发现该脚本本身有两类缺陷（与 BE-064 的 PMD 门禁修复同属"门禁可信度"范畴，故在此登记关联）：

- 通过条件漏掉写操作 `@RequestBody` 维度，导致该维度可退化到 0% 而 CI 仍绿。
- 缺注解分支存在 macOS bash 3.2 多字节变量名崩溃（`$module_root` 后接全角括号），失败时丢失诊断。

修复与实测见 [BE-060 记录](./2026-11-22-handler-annotation-completion.md#2026-11-24-独立-r5-复核reject与门禁修复)。本次仅收紧门禁、未放宽任何阈值或豁免。
