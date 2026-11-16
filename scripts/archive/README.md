# `scripts/archive/`

历史一次性工具的归档区：保留可追溯的执行记录，**不再新增、不再修改**；需要重跑请先在 `docs/plans/` 立卡并说明原因。

## 目录清单

| 脚本 | 用途 | 用法 |
| --- | --- | --- |
| `scripts/archive/frontend/fix-typescript-errors.sh` | 历史一次性工具：用于旧前端 TypeScript 错误修复，执行前请核对原始路径与内容。 | `bash scripts/archive/frontend/fix-typescript-errors.sh` |
| `scripts/archive/frontend/frontend-add-comments.sh` | 历史一次性工具：用于旧前端项目注释补充，执行前请核对原始路径与内容。 | `bash scripts/archive/frontend/frontend-add-comments.sh` |
| `scripts/archive/frontend/update-frontend-version.sh` | 历史一次性工具：用于旧前端 v1.1.4 到 v1.2.0 的版本更新，执行前请核对原始路径与内容。 | `bash scripts/archive/frontend/update-frontend-version.sh` |
| `scripts/archive/frontend/vue-components-add-comments.sh` | 历史一次性工具：用于旧前端 Vue 组件注释补充，执行前请核对原始路径与内容。 | `bash scripts/archive/frontend/vue-components-add-comments.sh` |
| `scripts/archive/microservices/reconcile-off-star-data-guard.sh` | reconcile-off-star-data-guard.sh - off_* 存量对账守护（Phase 3 双写过渡） | `bash scripts/archive/microservices/reconcile-off-star-data-guard.sh` |
| `scripts/archive/microservices/reconcile-off-star-data.sh` | reconcile-off-star-data.sh - off_* 存量数据对账（Phase 2 验收） | `bash scripts/archive/microservices/reconcile-off-star-data.sh` |
| `scripts/archive/microservices/scaffold-phase5-6.sh` | scaffold-phase5-6.sh - 微服务化改造 Phase 5/6 非破坏性模块骨架生成器 | `bash scripts/archive/microservices/scaffold-phase5-6.sh` |
| `scripts/archive/microservices/sync-off-star-data.sh` | sync-off-star-data.sh - off_* 存量数据全量同步（Phase 2） | `bash scripts/archive/microservices/sync-off-star-data.sh` |
| `scripts/archive/office/generate-message-infrastructure.sh` | 历史一次性工具：用于旧办公平台消息基础设施代码生成，执行前请核对原始路径与内容。 | `bash scripts/archive/office/generate-message-infrastructure.sh` |
| `scripts/archive/office/generate-phase2-code.sh` | 办公平台集成项目 - 阶段二和阶段三代码生成脚本 | `bash scripts/archive/office/generate-phase2-code.sh` |
| `scripts/archive/svg/batch-upgrade-icons.sh` | SVG图表云原生图标批量升级脚本 | `bash scripts/archive/svg/batch-upgrade-icons.sh` |
| `scripts/archive/svg/cleanup_old_svgs.sh` | SVG图表清理脚本 - 保留2026-09-10重新生成的29张图表 | `bash scripts/archive/svg/cleanup_old_svgs.sh` |
| `scripts/archive/svg/rollback-icons.sh` | SVG图表云原生图标升级回滚脚本 | `bash scripts/archive/svg/rollback-icons.sh` |
| `scripts/archive/svg/verify_svg.sh` | SVG 图表验证脚本 | `bash scripts/archive/svg/verify_svg.sh` |

## 约定

- 「用法」列的 `{p}` 指同一行「脚本」列的仓库相对路径；`bash {p}` 可直接执行。
- 脚本从仓库根解析路径，不依赖开发机绝对路径；需要环境变量的脚本在头注释中列明。
- 退出码：`0` 成功，非 `0` 失败并在 stderr 说明原因；CI 脚本以退出码阻断合并。
- 新增脚本须先在 `docs/tasks/project-tasks.md` 立卡，再在本 README 登记用途，保持单一职责。
