# `scripts/ci/`

CI 校验脚本：文档一致性、文档链接、SpringDoc 覆盖率；由 `.github/workflows/` 调用，失败即阻断合并。

## 目录清单

| 脚本 | 用途 | 用法 |
| --- | --- | --- |
| `scripts/ci/check-doc-consistency.sh` | 文档一致性检查脚本 | `bash scripts/ci/check-doc-consistency.sh` |
| `scripts/ci/check-doc-links.sh` | check-doc-links.sh - 扫描全仓 Markdown/HTML 本地路径及标题/HTML 锚点的有效性 | `bash scripts/ci/check-doc-links.sh` |
| `scripts/ci/check-springdoc-coverage.sh` | SpringDoc 注解覆盖率检查脚本（用于 CI/CD） | `bash scripts/ci/check-springdoc-coverage.sh` |

## 约定

- 「用法」列的 `{p}` 指同一行「脚本」列的仓库相对路径；`bash {p}` 可直接执行。
- 脚本从仓库根解析路径，不依赖开发机绝对路径；需要环境变量的脚本在头注释中列明。
- 退出码：`0` 成功，非 `0` 失败并在 stderr 说明原因；CI 脚本以退出码阻断合并。
- 新增脚本须先在 `docs/tasks/project-tasks.md` 立卡，再在本 README 登记用途，保持单一职责。
