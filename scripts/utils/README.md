# `scripts/utils/`

杂项工具：SVG 校验与渲染、OpenAPI 导出、环境变量提取、离线运维启动器。

## 目录清单

| 脚本 | 用途 | 用法 |
| --- | --- | --- |
| `scripts/utils/ConfigEncryptionTool.java` | 配置加密命令行工具（GCM 加密/解密 application 配置项） | `javac scripts/utils/ConfigEncryptionTool.java` 后按类名运行 |
| `scripts/utils/batch-verify-svg.sh` | SVG图表批量验证脚本 | `bash scripts/utils/batch-verify-svg.sh` |
| `scripts/utils/check-commercialization-readiness.sh` | Apex Scaffold 商业化就绪度检查脚本 | `bash scripts/utils/check-commercialization-readiness.sh` |
| `scripts/utils/export-api-docs.sh` | export-api-docs.sh - 从运行中的应用导出 OpenAPI 文档（JSON/YAML） | `bash scripts/utils/export-api-docs.sh` |
| `scripts/utils/extract-env-vars.sh` | 提取所有 application*.yml 中的环境变量引用 | `bash scripts/utils/extract-env-vars.sh` |
| `scripts/utils/local-deployment.sh` | local-deployment.sh - 本地部署离线运维工具启动器，运行 apex-tools-deployment 模块构建的 local-deployment.jar | `bash scripts/utils/local-deployment.sh` |
| `scripts/utils/render-mermaid-svg.sh` | 渲染 Mermaid 源文件为同名 SVG（BE-001 补导出的可复用脚本） | `bash scripts/utils/render-mermaid-svg.sh` |
| `scripts/utils/start-doc-search.sh` | 文档搜索工具启动脚本 | `bash scripts/utils/start-doc-search.sh` |
| `scripts/utils/token-v42-recover.sh` | token-v42-recover.sh - 令牌 v4.2 恢复工具启动器，运行 local-deployment.jar 中的 TokenV42Recover 主类 | `bash scripts/utils/token-v42-recover.sh` |
| `scripts/utils/verify-svg-quality.sh` | SVG 图表质量验证脚本 | `bash scripts/utils/verify-svg-quality.sh` |

## 约定

- 「用法」列的 `{p}` 指同一行「脚本」列的仓库相对路径；`bash {p}` 可直接执行。
- 脚本从仓库根解析路径，不依赖开发机绝对路径；需要环境变量的脚本在头注释中列明。
- 退出码：`0` 成功，非 `0` 失败并在 stderr 说明原因；CI 脚本以退出码阻断合并。
- 新增脚本须先在 `docs/tasks/project-tasks.md` 立卡，再在本 README 登记用途，保持单一职责。
