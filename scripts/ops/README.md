# `scripts/ops/`

运维周期性任务（周/月/季清理与归档）与监控栈运维脚本。

## 目录清单

| 脚本 | 用途 | 用法 |
| --- | --- | --- |
| `scripts/ops/maintenance/monthly-cleanup.sh` | monthly-cleanup.sh - 月度清理：备份文件与临时产物检查清理，日志写 logs/maintenance-YYYYMM.log | `bash scripts/ops/maintenance/monthly-cleanup.sh` |
| `scripts/ops/maintenance/quarterly-archive.sh` | quarterly-archive.sh - 季度归档：将 docs/reports 过时报告移入 docs/_archive/reports | `bash scripts/ops/maintenance/quarterly-archive.sh` |
| `scripts/ops/maintenance/weekly-link-check.sh` | weekly-link-check.sh - 周度文档链接检查并生成 Markdown 报告 | `bash scripts/ops/maintenance/weekly-link-check.sh` |
| `scripts/ops/monitoring/grafana/check-environment.sh` | Grafana 和 Prometheus 环境检查脚本 | `bash scripts/ops/monitoring/grafana/check-environment.sh` |
| `scripts/ops/monitoring/grafana/configure-datasource.sh` | Grafana Prometheus 数据源自动配置脚本 | `bash scripts/ops/monitoring/grafana/configure-datasource.sh` |
| `scripts/ops/monitoring/grafana/import-dashboard.sh` | Grafana Dashboard 自动导入脚本 | `bash scripts/ops/monitoring/grafana/import-dashboard.sh` |
| `scripts/ops/monitoring/grafana/quick-check.sh` | 简化版环境检查脚本（无需认证） | `bash scripts/ops/monitoring/grafana/quick-check.sh` |
| `scripts/ops/monitoring/start-monitoring.sh` | start-monitoring.sh - 启动独立监控栈 | `bash scripts/ops/monitoring/start-monitoring.sh` |
| `scripts/ops/monitoring/verify-reactor-metrics.sh` | verify-reactor-metrics.sh - 验证应用的 Reactor Netty 指标暴露情况 | `bash scripts/ops/monitoring/verify-reactor-metrics.sh` |

## 约定

- 「用法」列的 `{p}` 指同一行「脚本」列的仓库相对路径；`bash {p}` 可直接执行。
- 脚本从仓库根解析路径，不依赖开发机绝对路径；需要环境变量的脚本在头注释中列明。
- 退出码：`0` 成功，非 `0` 失败并在 stderr 说明原因；CI 脚本以退出码阻断合并。
- 新增脚本须先在 `docs/tasks/project-tasks.md` 立卡，再在本 README 登记用途，保持单一职责。
