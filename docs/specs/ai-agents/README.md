# 项目级 AI 智能体角色定义（apex-scaffold 后端）

本目录定义**跨工具通用**的智能体角色规范：同一份角色卡可直接粘贴给 Trae / WorkBuddy / Qoder / Codex / ZCode / Claude 等任意工具，作为 system prompt 或角色说明使用。

## 为什么放这里

| 位置 | 用途 | 是否入库 |
| --- | --- | --- |
| `docs/specs/ai-agents/`（本目录） | **角色规范正文**，跨工具唯一权威副本 | ✅ 入库 |
| `.trae/` `.workbuddy/` `.zcode/` `.qoder/` `.codex/` | 各工具的会话缓存、索引、规则副本 | ❌ gitignore |

工具私有目录只放“读取路径/指针”，正文一律在本目录，避免多工具各自一份导致口径分裂（见 `AGENTS.md` 0.4/0.9）。

## 角色清单

| 角色 | 文件 | 适用场景 |
| --- | --- | --- |
| R0 主控编排 | `r0-orchestrator.md` | 建卡、租约、并行准入、分派、交接验收、逐个集成与关闭 |
| R1 需求拆解与任务卡 | `r1-task-planner.md` | 用户提需求 → 输出 P0–P3 优先级 + 任务卡 + 验收标准 |
| R2 后端实现（WebFlux） | `r2-backend-implementer.md` | 按红线写 Handler/Router/Service/Repository |
| R3 接口契约守门 | `r3-contract-guard.md` | 审 OpenAPI/Handler 签名/DTO/错误码/版本兼容 |
| R4 测试与回归 | `r4-test-runner.md` | 执行/定位测试，按任务授权修改指定测试文件，跑 `mvnd` |
| R5 评审与红线扫描 | `r5-reviewer.md` | 提交前评审、跑治理脚本、给结论 |

主链路：`R0 建卡/授权 → R1 拆解 → R2 实现 → R3/R4 独立取证 → R5 放行 → R0 集成/关闭`。小任务可合并规划与实现，但 R5 必须与实际执行者为不同智能体实例或会话；代码任务中 R2 与 R5 也不得相同；并行任务不得合并 R0、执行者和复核者。

## 使用方式（三选一）

1. **直接粘贴**：把角色卡全文作为工具的系统提示 / 自定义指令。
2. **文件引用**：在工具的项目规则里写 `见 docs/specs/ai-agents/r2-backend-implementer.md`。
3. **任务卡内联**：在 `docs/tasks/project-tasks.md` 的任务卡"执行说明"里引用角色文件名。

## 通用铁律（所有角色都适用）

1. 改代码前先读 `AGENTS.md` 第 0 章 + 目标文件全文，不凭猜测改。
2. 先确认任务 R0、活跃租约和写入范围；缺少任一项时停止写入并回报 R0。
3. 一个任务只解决一个目标；跨模块改动必须拆卡。执行者不得直接改任务盘、记录索引或记忆台账。
4. 并行写任务使用独立 worktree；共享 checkout 同一时刻只允许一个写者，禁止清理他人未提交改动。
5. 不删既有文档与图表；移动必须先建新版再留指针。
6. 提交前必跑：`bash scripts/governance/verify-docs.sh --strict`、`bash scripts/governance/check-doc-refs.sh --strict`、`bash scripts/governance/audit-backend-rules.sh --strict` 与 `bash scripts/governance/check-memory-sync.sh --strict`。
7. R5 必须与实际执行者为不同智能体实例或会话；代码任务中 R2 与 R5 也不得相同；不得自审自放；任何“顺手重构”一律拒绝并登记独立任务卡。
