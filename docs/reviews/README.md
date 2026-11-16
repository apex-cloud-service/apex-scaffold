# 评审记录（docs/reviews）

存放方案评审、代码评审、上线检查的结论与清单结果。

建议格式：
1. 评审对象与版本（commit / 分支 / 模块）
2. 检查清单（勾选项 + 不通过原因）
3. 结论（通过 / 有条件通过 / 驳回）与责任人
4. 后续动作与任务 ID

既有评审材料保留在 `docs/08-project-management/reports/` 与 `docs/02-architecture/`，不搬迁。

## 存量评审（2026-11-16 由根目录迁入）

| 对象 | 文件 |
| --- | --- |
| 后端模块架构评审（分层/依赖/响应式链路） | [architecture-review-report.md](./architecture-review-report.md) |

## 已归档评审

| 日期 | 对象 | 结论 | 文件 |
| --- | --- | --- | --- |
| 2026-11-16 | 前后端两仓治理规范对齐（基线 前端 `1455fbc` / 后端 `14f6bc2a`） | 有条件通过（P1 5 项待整改） | [2026-11-16-governance-alignment-review.md](./2026-11-16-governance-alignment-review.md) |
| 2026-11-16 | 两仓 `dev` 分支治理一致性（基线 后端 `d4311202` / 前端 `d957e6f`） | 有条件通过（P0 1 / P1 6 / P2 2 / P3 1） | [2026-11-16-dev-branch-governance-audit.md](./2026-11-16-dev-branch-governance-audit.md) |
| 2026-11-16 | 异常处理机制（`@RestControllerAdvice` vs `ErrorWebExceptionHandler`） | 维持现状，不迁移（契约零变化） | [2026-11-16-exception-handler-evaluation.md](./2026-11-16-exception-handler-evaluation.md) |
