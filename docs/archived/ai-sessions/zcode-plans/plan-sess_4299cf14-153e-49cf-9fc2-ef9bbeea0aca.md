# 全仓 Javadoc 评审与补齐战役（BE-041）

## Context

用户要求按 AGENTS.md 0.5.7 / 治理规范 B7（类/接口/枚举/常量/公开方法必须有完整 Javadoc，含 @author/@since/@param/@return/@throws，禁止占位空注释）评审并补齐全仓代码注释。

本会话已核实的现状（只读探测）：
- 主代码 **2249 个 main 文件**类级 Javadoc 覆盖率 **100%**（含 @author 2249/2249、@since 2249/2249），占位/空 `/** */` 残留 **0** —— 2026-09 多轮战役 + 11-03 收口成果，类级无需重做。
- 抽样质量高（`ResultCode`、`WorkflowEventPublisher`、`EmbeddingCache`、`IotRuleHandler` 均有完整富文本 Javadoc）。剩余缺口集中在**方法级** Javadoc 与**标签完整性**（@param/@return/@throws 缺失）——权威缺口清单由现成扫描器 `scripts/dev/check/scan-javadoc-issues.sh` 产出（plan mode 无法执行脚本，执行期先跑出基线）。
- 既有工具链齐全：`scripts/dev/check/scan-javadoc-issues.sh`（四类清单→`_tmp/`）、`check-handler-javadoc.sh`（Handler 专项）、`scripts/governance/audit-backend-rules.sh`（B 系红线警告模式）。

未收到范围答复，按推荐默认执行：**仅 main 生产代码**（test 780 文件按扫描器与历史口径豁免）；深度=**补缺口+修明显违规、最小 churn**；**不经明确要求不 commit**（AGENTS.md 默认），全部改动留工作区待你复核。

## 注释口径（沿用历史战役约定）

- 中文 Javadoc；`@author Apex Team`；`@version` 不强制（已存在的保留）；**`@since 1.3.2`**（当前项目版本 1.3.1，下一发布版口径）。
- 句尾用 `。`（11-03 约定）；首行一句功能摘要，正文用 `<p>`/`<ul>` 写设计说明、约束、线程/事务语义等非直观信息。
- 方法 Javadoc 必须带齐 `@param`/`@return`/`@throws`；豁免：`@Override` 方法、getter/setter、builder 流式 setter（扫描器 §4.4.5 同口径）、Lombok 生成成员。
- 只改注释，零逻辑/契约改动；不动未涉及文件的既有标点风格。

## 实施步骤

1. **任务登记**：`docs/tasks/project-tasks.md` 表 1 追加 BE-041 卡片（状态=进行中）——先登记再动手（0.6.1）。
2. **基线扫描**：`bash scripts/dev/check/scan-javadoc-issues.sh` → `_tmp/javadoc-issues-{class,author,since,method}.txt`，记录四类计数作为战役基线。
3. **分批补齐**（每批：改注释 → `mvnd spotless:apply -pl <本批模块>` → 重扫本批 → `mvnd -pl <模块列表> -am test`），优先级 Handler > Service > Repository > DTO：
   - 批 1 `apex-shared-parent`（common/domain/五个 infrastructure/api-contract）
   - 批 2 `apex-components-parent`（system/auth/workflow/file/office/integration）
   - 批 3 `apex-services-bom`（gateway + 8 运行模块 + job/masking）
   - 批 4 `apex-starters-parent` + `apex-business-parent/apex-knowledge`
   - 批 5 `apex-applications-parent` + `apex-tools-parent`
   - 每批顺带修复触及文件上的明显违规（缺标签、句尾标点、占位残留——已确认全仓为 0）。
4. **最终验证**：
   - 重扫 `scan-javadoc-issues.sh` 四类清单归零（方法清单为启发式候选，归零后说明残余豁免理由）；
   - 全量 `mvnd -T4 -B clean verify`（后台 ~20min，判定看反应堆 BUILD SUCCESS + 测试计数，非仅退出码）；
   - `git diff --check`、`bash scripts/governance/verify-docs.sh`（文档有改动）、`bash scripts/governance/audit-backend-rules.sh`。
5. **收口登记**：BE-041 状态改已完成（含验收数据）；`docs/reference/ai-knowledge/07-change-memory.md` 追加台账条目（append-only 六字段）；变更记录正文写 `docs/records/2026-11-18-javadoc-review-campaign.md`（含各批文件清单、扫描前后对比、豁免说明）并在 `docs/README.md` 登记；清理 `_tmp/` 中间产物。

## 关键文件

- 扫描器：`scripts/dev/check/scan-javadoc-issues.sh`（输出 `_tmp/`）、`scripts/dev/check/check-handler-javadoc.sh`
- 登记：`docs/tasks/project-tasks.md`、`docs/reference/ai-knowledge/07-change-memory.md`、`docs/README.md`、`docs/records/`
- 代码改动面：扫描清单命中的 main Java 文件（预计数十个量级，执行期基线定案）

## 风险与边界

- 只补注释，不改任何逻辑/签名/契约；spotless palantirJavaFormat 可能重排注释内空白，属预期。
- 方法清单为启发式（退出码非门禁），以人工复核确认最终覆盖。
- 不触碰 test 代码、`docs/` 图表、他人未提交改动。

## 验证方式

`scan-javadoc-issues.sh` 清单归零 + 抽样复读 + 各批 `-am test` 全绿 + 最终全量 `clean verify`（BUILD SUCCESS 45/45、失败 0）+ `verify-docs.sh` 通过 + 台账/任务卡/记录三处登记齐全。