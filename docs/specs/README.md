# 需求与技术方案（docs/specs）

存放需要评审后再实施的设计类文档：接口契约、模块与坐标调整、运行拓扑、数据库迁移、网关路由、跨服务改造。

命名：`YYYY-MM-DD-主题.md`；必须包含「背景 / 目标 / 影响面（模块+接口+数据）/ 兼容性 / 回滚方案 / 验收标准」六节。

> 规则：涉及模块坐标、parent 继承、运行模块清单、数据库迁移、网关路由的改动，必须先在此出方案，评审通过后再动代码。
>
> 历史方案仍在既有目录：`docs/08-project-management/plans/`（迁移方案、整改方案）与 `docs/02-architecture/`，不搬迁。

## 联调修复方案

- [2026-11-17 办公日历端口扩展与组织者身份换算](2026-11-17-calendar-port-extension.md) — BE-038，端口契约变更与映射换算，待独立复核。
- [2026-11-17 根治 office/auth 字段自动填充失效](2026-11-17-meta-field-fill.md) — BE-040，模块内装配审计字段填充器，待独立复核。
- [2026-11-17 前后端联调后端修复方案](2026-11-17-integration-contract-repair.md) — BE-036，既有改动补记及后续回归边界，待独立复核；非事前审批。
- [2026-11-18 前后端全量联调与 Podman 基线方案](2026-11-18-fullstack-integration.md) — FE-029 / BE-043，待独立方案评审。

## AI 智能体角色规范（跨工具通用）

`docs/specs/ai-agents/` 存放角色卡正文，可直接粘贴给 Trae / WorkBuddy / Qoder / Codex / ZCode 等工具：

| 角色 | 文件 |
| --- | --- |
| 主控编排与最终集成 | `ai-agents/r0-orchestrator.md` |
| 需求拆解与任务卡 | `ai-agents/r1-task-planner.md` |
| 后端实现（WebFlux） | `ai-agents/r2-backend-implementer.md` |
| 接口契约守门 | `ai-agents/r3-contract-guard.md` |
| 测试与回归 | `ai-agents/r4-test-runner.md` |
| 评审与红线扫描 | `ai-agents/r5-reviewer.md` |

## 规范正文登记

| 文档 | 内容 | 状态 |
| --- | --- | --- |
| [project-governance.md](project-governance.md) | 项目任务管理规范完整正文（任务租约、并行准入、worktree 隔离、标准交接、独立复核、任务体系、文档收口、后端 10 条红线、验收清单） | v1.2 · 2026-11-18 生效 |
