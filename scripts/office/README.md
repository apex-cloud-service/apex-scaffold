# `scripts/office/`

办公/站内信模块工具：配置校验、冒烟测试、测试报告入口。

## 目录清单

| 脚本 | 用途 | 用法 |
| --- | --- | --- |
| `scripts/office/generate-message-infrastructure.sh` | generate-message-infrastructure.sh - 历史一次性站内信基础设施代码生成工具归档转发入口 | `bash scripts/office/generate-message-infrastructure.sh` |
| `scripts/office/generate-test-report.sh` | 保留旧入口；真实统计由统一报告工具维护。 | `bash scripts/office/generate-test-report.sh` |
| `scripts/office/smoke-test.sh` | 办公平台冒烟测试脚本 | `bash scripts/office/smoke-test.sh` |
| `scripts/office/validate-office-config.sh` | 办公平台配置验证脚本 | `bash scripts/office/validate-office-config.sh` |

## 约定

- 「用法」列的 `{p}` 指同一行「脚本」列的仓库相对路径；`bash {p}` 可直接执行。
- 脚本从仓库根解析路径，不依赖开发机绝对路径；需要环境变量的脚本在头注释中列明。
- 退出码：`0` 成功，非 `0` 失败并在 stderr 说明原因；CI 脚本以退出码阻断合并。
- 新增脚本须先在 `docs/tasks/project-tasks.md` 立卡，再在本 README 登记用途，保持单一职责。
