# 项目任务大盘 · apex-scaffold

> **唯一可信源**。仅主控智能体（默认 Trae）可编辑；WorkBuddy / ZCode / Qoder / Codex 只读，完成后回报主控更新状态。
> 优先级：`P0` 线上阻断 · `P1` 当期必做 · `P2` 后续排期 · `P3` 技术债/优化
> 状态：`待规划｜执行中｜待复核｜已完成｜搁置`
> 记录日期：2026-11-16（治理基线建立；基线：`docs/` 1272 文件 = 715 md + 217 svg + 68 mmd + 139 json + 68 mmd…）

## 1. 活跃任务

| ID | 优先级 | 任务 | 状态 | 负责 | 验收标准 | 关联 |
| --- | --- | --- | --- | --- | --- | --- |
| BE-002 | P2 | 文档孤岛盘点：715 篇 md 中未被 `docs/README.md` 或任何目录索引引用的文件清单化（只盘点、不移动、不删除） | 待规划 | 待定 | 输出「孤岛清单 + 建议归属」，不产生任何文件移动 | `docs/README.md` |
| BE-004 | P3 | 存量注解式异常处理例外评估：`apex-system` 的 `GlobalExceptionHandler`、`SentinelExceptionHandler`（`@RestControllerAdvice`）是否下沉为 `ErrorWebExceptionHandler` | 待规划 | 待定 | 评估错误响应契约兼容性；**评估通过前禁止改动**，避免影响线上错误码/报文 | `apex-components-parent/apex-system` |
| BE-005 | P3 | 周期报告（日报/周报/月报）流水件治理：`docs/08-project-management/periodic-reports/` 只保留索引与近期，历史按月归档 | 待规划 | 待定 | 索引完整、无散落根目录日报 | `docs/08-project-management/periodic-reports/` |
| BE-006 | P3 | `scripts/` 与 `docs/` 的脚本说明对齐：`scripts/` 各子目录是否都有 README 与用途说明 | 待规划 | 待定 | 每个脚本目录有 README，命令可复现 | `scripts/` |
| BE-007 | P3 | AI 私有目录指针桩清理：确认 `.trae/documents` 的 `MIGRATED.md` 指针以外无残留正文、全仓无指向私有目录的链接后，移除桩文件 | 待规划 | 待定 | `grep -r ".trae/documents"` 无命中 | 本表 BE-012 |

## 2. 本期已完成（治理基线）

| ID | 优先级 | 任务 | 状态 | 完成日期 | 产出 |
| --- | --- | --- | --- | --- | --- |
| BE-010 | P1 | AGENTS.md 增补第 0 章治理规范（附录 A 原文逐字保留，SHA-256 已比对） | 已完成 | 2026-11-16 | `AGENTS.md` |
| BE-011 | P1 | 建立 `docs/{tasks,specs,plans,records,reviews,notes}` 标准目录与索引（既有编号目录不动） | 已完成 | 2026-11-16 | `docs/README.md`、各目录 README |
| BE-012 | P1 | 迁移 `.trae/documents` 4 份设计文档 → `docs/plans/archived-ai-sessions/`（逐字节一致后移除原件，留指针） | 已完成 | 2026-11-16 | `docs/records/2026-11-16-docs-governance.md` |
| BE-013 | P1 | 治理自检脚本与 pre-commit 钩子（警告模式，不阻断） | 已完成 | 2026-11-16 | `scripts/governance/`、`.githooks/` |
| BE-014 | P1 | 迁移 `.workbuddy/knowledge` 7 份知识库文档 → `docs/reference/ai-knowledge`（逐字节一致，仅修相对链接） | 已完成 | 2026-11-16 | `docs/reference/ai-knowledge/PROVENANCE.md` |
| BE-015 | P1 | 迁移 `.zcode/plans` 13 份会话计划 → `docs/archived/ai-sessions/zcode-plans`（逐字节一致） | 已完成 | 2026-11-16 | `docs/archived/ai-sessions/zcode-plans/README.md` |
| BE-016 | P1 | 迁移 `.workbuddy/reports` 3 份自动周期报告 → `docs/08-project-management/periodic-reports/{daily,weekly,monthly}`（内容唯一，避免丢失） | 已完成 | 2026-11-16 | `docs/records/2026-11-16-docs-governance.md` |
| BE-018 | P3 | 规范正文入仓并补工具目录/忽略规则条款 | 已完成 | 2026-11-16 | `docs/specs/project-governance.md`、`AGENTS.md` 0.1/0.4/0.7、`scripts/governance/README.md` |
| BE-019 | P2 | 他人未提交的 3 项（记忆卡基线、daily 索引、11-16 日报）按规范复核后纳入版本库 | 已完成 | 2026-11-16 | `MEMORY-CARD.md`（指针改指 `docs/`）、`daily/README.md`、`2026-11-16-all-projects-daily-report.md`（补后段补记） |
| BE-020 | P1 | 治理评审整改（RV-01~RV-14，批次 1–6） | 已完成 | 主控 | 2026-11-16 | `verify-docs.sh` 通过（仅 BE-001 存量告警）、`check-doc-refs.sh` 0 项、图表 217/68 不变 | `docs/records/2026-11-16-governance-alignment-remediation.md` |
| BE-001 | P2 | 图表资产补导出：30 个 `.mmd` 补同名 `.svg` | 已完成 | 主控 | 2026-11-16 | 68/68 配对、SVG 217→247、`verify-docs.sh` 第 6 项 0 告警 | `docs/diagrams/README.md`（补导出记录）、`scripts/utils/render-mermaid-svg.sh` |
| BE-017 | P3 | `apex-common` 的 `VirtualThreadBoundedExecutor` 手写 `LoggerFactory` 改 `@Slf4j` | 已完成 | 主控 | 2026-11-16 | `mvnd -pl :apex-common -am verify` BUILD SUCCESS（测试/覆盖率/failsafe 全绿）、日志变量名仍为 `log` | `apex-shared-parent/apex-common/.../scheduler/VirtualThreadBoundedExecutor.java` |
| BE-021 | P3 | 恢复 5 张序列图的激活盒：`78/84/90/93/94` 源文件激活盒嵌套不合法，已归一化为无语义损失的默认生命线；如需恢复需人工重排 `activate/deactivate` | 已完成（登记） | 待定 | 三选一：①接受现状 ②人工重排 ③改写为 `opt/alt` 片段替代激活盒 | 见 `docs/diagrams/README.md`「2026-11-16 批量补导出记录」 |
| BE-003 | P2 | 根目录散落文档归位：`aggregation-optimization-plan.md` → `docs/plans/`、`architecture-review-report.md` → `docs/reviews/`（根目录留 3 行指针，SHA-256 迁移前后核对） | 已完成 | 主控 | 2026-11-16 |

## 3. 阻塞与风险登记

| 风险 | 影响 | 处置 |
| --- | --- | --- |
| 图表被批量移动会导致报告/README 断链 | 文档可信度下降 | 第 0.3 节禁止批量移动；迁移必须附引用扫描 |
| `.workbuddy/memory`、`.trae/documents` 长期未入库 | 本地知识可能丢失 | 设计文档已迁入 `docs/`；`memory` 属工具私有记忆，按规范保留 |
| 工作区存在他人未提交改动（`MEMORY-CARD.md`、daily 报告） | 可能被误覆盖 | 首轮未触碰、按路径分批 add；经用户授权后于 BE-019 按规范复核（只改指针与索引登记，正文保留）并单独提交 |

## 4. 维护规则

1. 新任务先入表再动手；ID 采用 `BE-nnn` 连续编号，不复用。
2. 状态变更只由主控智能体执行；执行智能体提交「完成说明 + 验证命令输出」。
3. 任务关闭必须在 `docs/records/` 留变更记录（做了什么 / 验证方式 / 影响面）。
4. 每月复核：连续两期未动的 `P2/P3` 降级或标 `搁置` 并写原因。
