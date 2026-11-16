# 来源说明（PROVENANCE）

本目录内容迁自 AI 工具私有目录 `.workbuddy/knowledge/`（被 `.gitignore` 忽略，长期未入库，存在丢失风险）。

| 项 | 值 |
| --- | --- |
| 迁移日期 | 2026-11-16 |
| 迁移执行 | 项目治理改造（任务 BE-014） |
| 迁移方式 | 逐文件 `cp` + SHA-256 逐一比对，全部一致后移除原件；原目录保留 `MIGRATED.md` 指针 |
| 内容改动 | **仅修正相对链接**：`../rag/README.md`、`../ops/README.md` → `../../../.workbuddy/rag|ops/README.md`（因目录层级由 `.workbuddy/knowledge` 变为 `docs/reference/ai-knowledge`）。`rag/`、`ops/` 属工具基础设施（检索脚本、漂移检测脚本），按规范继续保留在私有目录，故链接指回私有目录并在文中标注。正文其余内容零删改 |

## 文件清单

| 文件 | 内容 |
| --- | --- |
| `README.md` | 工作区知识库入口：阅读路径、检索/维护入口、三仓索引 |
| `01-project-map.md` | 项目地图（三仓关系与边界） |
| `02-apex-scaffold-current-state.md` | apex 后端现状（2026-11-16 基线） |
| `03-conventions-and-pitfalls.md` | 约定与陷阱（排障表） |
| `04-admin-web.md` | apex-admin-web 前端要点 |
| `05-cold-chain.md` | cold-chain 冷链客户项目要点 |
| `06-delivery-status-and-next.md` | 交付状态与下一步（2026-11-05） |

## 使用约定

1. 本目录是**唯一副本**，`.workbuddy/knowledge/` 不再存放正文。
2. 内容为**手工快照**，不随代码自动更新；开工前先按 `README.md` 的维护入口跑 `python3 .workbuddy/ops/maintain.py status` 校验口径漂移。
3. 更新内容直接改本目录文件，并在 `docs/records/` 留痕。
4. 涉及架构结论的变更，升级为 `docs/specs/` 方案或 `docs/02-architecture/` 正式说明。
