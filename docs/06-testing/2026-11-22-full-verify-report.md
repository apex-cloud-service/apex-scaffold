# 2026-11-22 全量验证报告（BE-045~BE-048 收尾复验）

> 执行环境：macOS（Temurin JDK `21.0.13.0+9-tem` + mvnd `2.0.0-rc-3`，`scripts/build/init-build-env.sh` 固定；`PATH` 前置 podman/orbstack 供 Testcontainers）。
> 命令：`mvnd -T4 -B clean verify`（仓库根，分支 `dev`，基线 `8d8dc31c` + 本轮改动）。
> 原始日志（会话本机证据，不入库）：`/tmp/be045-full-verify.log`。

## 1. 结论

| 项 | 结果 |
| --- | --- |
| 反应堆 | **45/45 项目 BUILD SUCCESS**（`Reactor Summary` 无 FAILURE；`Apex Scaffold 1.3.1` 总根起） |
| 总耗时 | 12:40 min (Wall Clock)，完成于 2026-11-22T10:20:27+08:00 |
| 测试汇总（控制台 `Results:` 块聚合，surefire+failsafe） | **Tests run 6716 / Failures 0 / Errors 0 / Skipped 33** |
| 退出码 | 0（已按纪律另行核对反应堆清单与计数，非仅退出码） |

> 计数口径说明：以控制台 `Results:` 汇总块聚合（48 块）。`apex-system` 存在双 surefire 执行块的已知口径差异（溯源见 [模块架构评审报告](../08-project-management/reports/module-architecture-review-report.md) 2026-11-08 溯源段），XML 口径计数可能与控制台略有出入；失败/错误为 0 不受该口径影响。

## 2. 环境依赖跳过明细（33 项，非缺陷）

| 模块 | Tests | Skipped | 原因 |
| --- | --- | --- | --- |
| apex-seata-spring-boot-starter | 8 | 8 | 需真实 TC/双库环境的四模式端到端用例 |
| apex-infrastructure-messaging | 139 + 5 | 10 + 3 | 真实 RocketMQ 环境用例 |
| apex-integration | 135 | 5 | 外部环境依赖用例 |
| apex-system | 19 | 5 | 环境/TC 依赖用例 |
| apex-office-service | 9 | 1 | 环境依赖用例 |
| apex-auth-service | 198 | 1 | 环境/计时敏感用例 |

按治理要求：跳过仅代表报告中明确跳过的测试，不能推断未执行用例数量，不记为通过。

## 3. 本轮改动与门禁关系

- 本轮生产代码改动仅 1 处纯注释（`FileAuditFieldFillHandler` 类 Javadoc），无逻辑/契约/迁移变更；全量 verify 为收尾复验门禁。
- 配套门禁：`spotless:check`（触及模块）通过；Javadoc 扫描四类 0 问题；治理脚本结果见[战役记录 §5](../records/2026-11-22-alignment-campaign.md)。
- 上一口径参考：当日早间 BE-044 全量 `clean verify` 45/45 SUCCESS（台账 #78）；本轮为叠加战役改动后的复验。
