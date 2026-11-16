# `scripts/dev/`

开发期工具：开工预检、注解/Javadoc 扫描、格式化批处理、OpenAPI 归档。

## 目录清单

| 脚本 | 用途 | 用法 |
| --- | --- | --- |
| `scripts/dev/apex-preflight.sh` | apex 一键开工/提交前预检（前后端双仓通用） | `bash scripts/dev/apex-preflight.sh` |
| `scripts/dev/check/QUICKREF-springdoc-check.md` | SpringDoc 注解检查工具 - 快速参考 | （说明文档） |
| `scripts/dev/check/README-check-springdoc.md` | SpringDoc/Swagger 注解完整性检查脚本 | （说明文档） |
| `scripts/dev/check/check-flyway-timestamp-usage.sh` | check-flyway-timestamp-usage.sh - 检查 Flyway 迁移脚本中禁用的 TIMESTAMP 类型用法 | `bash scripts/dev/check/check-flyway-timestamp-usage.sh` |
| `scripts/dev/check/check-handler-javadoc.sh` | Handler JavaDoc 检查脚本 | `bash scripts/dev/check/check-handler-javadoc.sh` |
| `scripts/dev/check/check-port-conflicts.sh` | 用法: check-port-conflicts.sh [compose.yml ...]，默认检查主服务栈。 | `bash scripts/dev/check/check-port-conflicts.sh` |
| `scripts/dev/check/check-springdoc-annotations-v2.sh` | check-springdoc-annotations-v2.sh - SpringDoc注解检查脚本（使用公共库重构版） | `bash scripts/dev/check/check-springdoc-annotations-v2.sh` |
| `scripts/dev/check/check-springdoc-annotations.sh` | check-springdoc-annotations.sh - 兼容入口：转发到 check-springdoc-annotations-v2.sh | `bash scripts/dev/check/check-springdoc-annotations.sh` |
| `scripts/dev/check/check_javadoc.sh` | JavaDoc 质量检查脚本 | `bash scripts/dev/check/check_javadoc.sh` |
| `scripts/dev/check/owasp-check.sh` | owasp-check.sh - 运行 OWASP Dependency Check 依赖漏洞扫描 | `bash scripts/dev/check/owasp-check.sh` |
| `scripts/dev/check/scan-javadoc-issues.sh` | 扫描项目中 JavaDoc 注释缺失或不完整的文件 | `bash scripts/dev/check/scan-javadoc-issues.sh` |
| `scripts/dev/check/scan-lombok-issues.sh` | 扫描项目中 Lombok 使用不规范的文件 | `bash scripts/dev/check/scan-lombok-issues.sh` |
| `scripts/dev/format/batch-add-javadoc.sh` | 批量为类添加基础 JavaDoc 框架 | `bash scripts/dev/format/batch-add-javadoc.sh` |
| `scripts/dev/format/batch-add-lombok.sh` | 批量为 DTO/PO 类添加 Lombok 注解 | `bash scripts/dev/format/batch-add-lombok.sh` |
| `scripts/dev/openapi/archive-openapi.sh` | OpenAPI 文档版本归档脚本 | `bash scripts/dev/openapi/archive-openapi.sh` |
| `scripts/dev/optimization-plan.sh` | optimization-plan.sh - 按顺序执行完成报告中的后续优化建议 | `bash scripts/dev/optimization-plan.sh` |
| `scripts/dev/quick-start.sh` | 基于脚本位置解析项目目录，避免依赖开发机绝对路径 | `bash scripts/dev/quick-start.sh` |

## 约定

- 「用法」列的 `{p}` 指同一行「脚本」列的仓库相对路径；`bash {p}` 可直接执行。
- 脚本从仓库根解析路径，不依赖开发机绝对路径；需要环境变量的脚本在头注释中列明。
- 退出码：`0` 成功，非 `0` 失败并在 stderr 说明原因；CI 脚本以退出码阻断合并。
- 新增脚本须先在 `docs/tasks/project-tasks.md` 立卡，再在本 README 登记用途，保持单一职责。
