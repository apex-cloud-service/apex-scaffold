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
