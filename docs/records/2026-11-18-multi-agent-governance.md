# 2026-11-18 多智能体并行协作治理（BE-042）

> 状态：**R5 首轮 REJECT，返工待复审**。本记录由执行会话供 R0 验收，不替代 R5 结论或最终集成。

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
6. **独立复核**：R5 必须与实际执行者为不同智能体实例或会话；代码任务中 R2 与 R5 也不得相同；并行任务不得合并 R0、执行者和复核者。R5 未放行不得集成，R0 未集成与留痕不得关闭。
7. **逐个集成与异常接管**：多个 R0 共享 `dev` 时按依赖/BE 顺序持有全局集成租约，一次只合一个任务；冲突由写入范围最小 owner 处理、持有租约的 R0 决定语义；禁止强推/硬重置。P0 先冻结并行、指定单一写者，恢复后补卡补证。

## 4. 验证证据

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| 补丁卫生 | `git diff --check` | PASS（无输出） |
| 附录 A 完整性 | `tail -n 306 AGENTS.md \| shasum -a 256` | `aa0c25fcc3d4b32a79981efb4e01534ae68449fb320d37037263d4668d8960ed`，与保护值一致 |
| 预检 | `bash scripts/dev/apex-preflight.sh --fast --strict` | PASS：`OK=14 WARN=4 FAIL=0`，退出码 0 |
| 文档治理 | `bash scripts/governance/verify-docs.sh --strict` | PASS：0 项问题，退出码 0；ZCode 私有计划正文已迁入 `docs/` 并保留短指针，未修改或放宽脚本 |
| 文档引用 | `bash scripts/governance/check-doc-refs.sh --strict` | PASS：0 问题 |
| 后端红线 | `bash scripts/governance/audit-backend-rules.sh --strict` | PASS：无阻断项，退出码 0 |
| 记忆台账 | 暂存 `AGENTS.md` 与 `docs/` 后执行 `bash scripts/governance/check-memory-sync.sh --strict` | PASS：已登记 `07-change-memory.md`，0 项（暂存内容保持，用于本次提交） |

## 5. 影响面

- 仅治理文档与任务/记录/记忆索引，不改 Java、POM、构建脚本、接口契约、数据库或运行拓扑。
- 后续智能体开工门禁与并行方式发生变化：必须先获取 R0 租约；并行写任务必须隔离 worktree；R5 必须独立于实际执行者，代码任务中 R2/R5 也必须独立。
- 既有五状态、P0–P3、`dev` 工作分支、文档收口和后端 B1–B10 红线不变。

## 6. 回滚方式

由 R0 以最终集成 commit SHA 回滚：`git revert <integration-sha>`。回滚后同步恢复任务盘、记录索引与记忆台账指针，不覆盖其他智能体未提交成果。

## 7. 复核状态

- R5：首轮已由独立子代理 Chandrasekhar（`01a1ed5c-98d3-72f2-9091-ac26530b2d12`）复核并给出 **REJECT**；MF-01–MF-05 返工后待同一 R5 增量复审，详见 §8–§9。
- 用户授权：2026-11-18 用户明确要求提交、合并至 `master`/`test` 并推送所有分支；这是对默认复核顺序的一次性覆盖，不视为 R5 通过。
- 内容提交：`61a7499f7cf87d52930b0dd3b2de7e69d91fe6d5`。
- 集成：中间集成 `90c13b74` 依据用户明确授权，将 `dev` 以 `--ff-only` 同步至 `master`/`test`；该动作发生在 R5 首轮评审前，且首轮结论为 REJECT，不能据此关闭 BE-042。
- 关闭：**不满足**；须待 R5 结论、逐任务集成、治理复验和台账可定位后，由 R0 更新为 `已完成`。

## 8. R5 首轮驳回、返工与中间集成事实（2026-11-18）

- 独立 R5：Chandrasekhar（子代理 `01a1ed5c-98d3-72f2-9091-ac26530b2d12`，与执行者不同实例/会话）。首轮评审对象为 `90c13b744be84b0bbd9dbab96c9d47fc55cbef22`，结论为 **REJECT**，报告见 `docs/reviews/2026-11-18-be-042-r5-review.md`。
- 首轮必须改：MF-01 统一共享 checkout 串行单写者与并行 worktree 口径；MF-02 将“R2 与 R5 独立”扩展为“R5 与实际执行者独立，代码任务中 R2/R5 也不相同”；MF-03 补齐任务租约、真实 R5 与全局集成租约；MF-04 回填实际中间集成事实且不得把 `90c13b74` 当作最终关闭 SHA；MF-05 以追加条目更正记忆台账 #53 的 append-only 违规。
- 历史事实：`61a7499f7cf87d52930b0dd3b2de7e69d91fe6d5` 为正文章节内容提交；`90c13b744be84b0bbd9dbab96c9d47fc55cbef22` 为证据回填和中间集成提交。`90c13b74` 时 `dev`/`master`/`test` 与同名远端均为该 SHA，推送范围为 `5d17cac2..90c13b74`；该 SHA **不是最终关闭 SHA**。
- append-only 纠正：`61a7499f` 首次登记 #53 时提交字段为“本轮 dev 提交（SHA 待回填）”；`90c13b74` 原地改写 #53 填入内容提交 SHA，违反历史条目只追加的硬规则。本轮不再次修改 #53，改在台账追加 #54 说明原状态、违规事实、当前可定位提交与防再犯措施。
- ZCode 外部阻断处置：用户于本轮明确授权处置 ZCode 私有计划目录。`plan-sess_4299cf14-153e-49cf-9fc2-ef9bbeea0aca.md` 共 51 行、4844 bytes，原文 SHA-256 为 `9e83bc0321bc126f14bdcee640a2a7b0276e560cf15c39b9ee86638519bd6de1`；已逐字节迁入 `docs/archived/ai-sessions/zcode-plans/`，归档副本哈希相同，原位置仅保留 5 行短指针。未修改或放宽 `verify-docs.sh`。
- 当前尚未最终集成：返工提交需先经同一独立 R5 增量复审，R5 `APPROVE` 或 `APPROVE_WITH_NITS` 且必须修项清零后，才能产生最终关闭提交并同步 `dev`/`master`/`test`。返工验证结果见任务盘执行租约与台账 #54。

## 9. 返工验证与复审结果

- 返工验证：`bash scripts/governance/verify-docs.sh --strict` PASS（0 项问题）；`bash scripts/governance/check-doc-refs.sh --strict` PASS（0 问题）；`bash scripts/governance/audit-backend-rules.sh --strict` PASS（无阻断项）。
- 预检：`bash scripts/dev/apex-preflight.sh --fast --strict` PASS（`OK=14 WARN=4 FAIL=0`）；`git diff --check` PASS；附录 A 哈希保持 `aa0c25fcc3d4b32a79981efb4e01534ae68449fb320d37037263d4668d8960ed`。
- R5 复审：待同一独立 R5 对返工提交增量复核。
- 最终集成：尚未产生最终关闭 SHA。
