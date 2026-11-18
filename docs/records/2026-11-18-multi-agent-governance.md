# 2026-11-18 多智能体并行协作治理（BE-042）

> 状态：**待 R5 独立复核**。本记录由执行会话供 R0 验收，不替代 R5 结论或最终集成。

## 1. 范围

- 任务：BE-042【P1】治理-落地多智能体并行协作与交接规则。
- 基线：`dev` · `5d17cac276c8fcb4b9855671a7240b14a669d455`。
- 写入范围：`AGENTS.md`、`docs/README.md`、`docs/specs/README.md`、`docs/specs/project-governance.md`、`docs/specs/ai-agents/**`、`docs/tasks/README.md`、`docs/tasks/project-tasks.md`、`docs/records/**`、`docs/reference/ai-knowledge/07-change-memory.md`。
- 禁止触碰：Java/POM、构建与治理脚本、数据库迁移、接口契约、图表资产、其他智能体私有目录。

## 2. 改动清单

1. `AGENTS.md` 第 0 章：修正图表基线为 247 SVG / 68 MMD、配对缺口 0；角色卡扩展到 R0–R5；新增 0.9 多智能体并行协作入口。
2. `docs/specs/project-governance.md`：v1.2 新增每任务唯一 R0、任务卡租约字段、全局集成租约、状态开工门禁、职责分离、并行准入矩阵、worktree 隔离、标准交接包、逐任务集成和异常接管，并统一 R1 任务盘只读、`dev` 分支口径与 `BlockingIoSchedulers` 口径。
3. `docs/specs/ai-agents/`：新增 R0 主控角色卡；同步 R1–R5 的租约确认、写入权限、构建命令、红线编号、真实证据和独立复核要求。
4. `docs/tasks/README.md`、`docs/README.md`、`docs/specs/README.md`、`docs/records/README.md`：登记 R0–R5 主链路、治理版本、交接规则和本记录。
5. `docs/tasks/project-tasks.md`：登记 BE-042、R0、基线、写入范围、验收命令与待复核状态。
6. `docs/reference/ai-knowledge/07-change-memory.md`：追加 #53 治理变更事实。

## 3. 关键规则

1. **唯一 R0**：每个任务只有一个 R0；R0 独占该任务的任务盘行、租约、状态、记忆台账、记录索引和最终 `dev` 集成权。不同任务可由不同 R0 负责；未指定 R0 不得并行写入。多个 R0 共享 `dev` 时必须取得全局集成租约，逐个 rebase/cherry-pick、合并、复验和推送。
2. **任务租约**：进入 `执行中` 前必须写全基线 SHA、执行者、独立复核者、写入/禁止范围、并行边界、执行环境、验收命令、交接物和租约时间；同一任务只能有一个活跃租约。
3. **并行准入**：依赖完成、写入互斥、验收独立、资源可隔离、复核独立、可分别回滚，六项全部满足才可并行；共享构建文件、契约、迁移、任务盘、总索引、记录索引、记忆台账和同图资产默认串行。
4. **写隔离**：共享 checkout 单写者；并行写任务使用独立 worktree + `codex/BE-xxx` 短分支，一个 worktree 只承载一个任务；禁止覆盖他人未提交改动。
5. **标准交接**：执行者提交范围、基线、原始命令/结果、测试变化、未验证项、commit、回滚和下一步；不得以“已完成”或历史转述代替证据。
6. **独立复核**：R2 实现与 R5 复核不得是同一智能体实例或会话；R5 未放行不得集成，R0 未集成与留痕不得关闭。
7. **逐个集成与异常接管**：多个 R0 共享 `dev` 时按依赖/BE 顺序持有全局集成租约，一次只合一个任务；冲突由写入范围最小 owner 处理、持有租约的 R0 决定语义；禁止强推/硬重置。P0 先冻结并行、指定单一写者，恢复后补卡补证。

## 4. 验证证据

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| 补丁卫生 | `git diff --check` | PASS（无输出） |
| 附录 A 完整性 | `tail -n 306 AGENTS.md \| shasum -a 256` | `aa0c25fcc3d4b32a79981efb4e01534ae68449fb320d37037263d4668d8960ed`，与保护值一致 |
| 预检 | `bash scripts/dev/apex-preflight.sh --fast --strict` | PASS：`OK=14 WARN=4 FAIL=0`，退出码 0 |
| 文档治理 | `bash scripts/governance/verify-docs.sh --strict` | **仅 1 项告警并退出 1**：他人 `.zcode` 私有计划目录中的既有文件 疑似私有目录业务正文；该文件属于他人私有目录，本任务不修改、不清理，也不放宽脚本 |
| 文档引用 | `bash scripts/governance/check-doc-refs.sh --strict` | PASS：0 问题 |
| 后端红线 | `bash scripts/governance/audit-backend-rules.sh --strict` | PASS：无阻断项，退出码 0 |
| 记忆台账 | 暂存 `AGENTS.md` 与 `docs/` 后执行 `bash scripts/governance/check-memory-sync.sh --strict` | PASS：已登记 `07-change-memory.md`，0 项，复验后撤销暂存 |

## 5. 影响面

- 仅治理文档与任务/记录/记忆索引，不改 Java、POM、构建脚本、接口契约、数据库或运行拓扑。
- 后续智能体开工门禁与并行方式发生变化：必须先获取 R0 租约；并行写任务必须隔离 worktree；R2/R5 必须独立。
- 既有五状态、P0–P3、`dev` 工作分支、文档收口和后端 B1–B10 红线不变。

## 6. 回滚方式

由 R0 以最终集成 commit SHA 回滚：`git revert <integration-sha>`。回滚后同步恢复任务盘、记录索引与记忆台账指针，不覆盖其他智能体未提交成果。

## 7. 复核状态

- R5：**待独立复核**。
- 用户授权：2026-11-18 用户明确要求提交、合并至 `master`/`test` 并推送所有分支；这是对默认复核顺序的一次性覆盖，不视为 R5 通过。
- 集成：待记录 `dev` 提交 SHA 后执行并回填。
- 关闭：**不满足**；须待 R5 结论、逐任务集成、治理复验和台账可定位后，由 R0 更新为 `已完成`。
