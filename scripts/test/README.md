# `scripts/test/`

测试与验证入口：集成测试、迁移完整性、混沌演练、测试报告生成。

## 目录清单

| 脚本 | 用途 | 用法 |
| --- | --- | --- |
| `scripts/test/chaos-fault-injection.sh` | 混沌故障注入演练脚本（Phase 7 / 强化 §7.1） | `bash scripts/test/chaos-fault-injection.sh` |
| `scripts/test/check-migration-integrity.sh` | Flyway 迁移脚本完整性检查 | `bash scripts/test/check-migration-integrity.sh` |
| `scripts/test/generate-comprehensive-test-report.sh` | 综合入口复用同一 XML 汇总实现，避免两个脚本统计口径漂移。 | `bash scripts/test/generate-comprehensive-test-report.sh` |
| `scripts/test/generate-test-report.sh` | 生成测试报告脚本 | `bash scripts/test/generate-test-report.sh` |
| `scripts/test/integration-test.sh` | 前后端对接测试脚本 | `bash scripts/test/integration-test.sh` |
| `scripts/test/jvm-diagnostics-test.sh` | jvm-diagnostics-test.sh - jvm-diagnostics.sh 的自动化测试 | `bash scripts/test/jvm-diagnostics-test.sh` |
| `scripts/test/new-batch-report.sh` | 批次测试报告骨架生成（方案 §13 脚本交付物）。 | `bash scripts/test/new-batch-report.sh` |
| `scripts/test/run-dual-database-migration.sh` | 双库迁移演练（方案 §13 脚本交付物）。 | `bash scripts/test/run-dual-database-migration.sh` |
| `scripts/test/run-mqtt-integration.sh` | 通过专用 `podman-compose.mqtt.yml` 项目执行 MQTT 真实 broker 联调；只停止本次脚本创建的项目。 | `bash scripts/test/run-mqtt-integration.sh` |
| `scripts/test/test-check-javadoc-comments.sh` | TMP_DIR="$(mktemp -d) | `bash scripts/test/test-check-javadoc-comments.sh` |
| `scripts/test/test-sentinel-hot-reload.sh` | Sentinel 规则热更新测试脚本 | `bash scripts/test/test-sentinel-hot-reload.sh` |
| `scripts/test/test-workflow-api.sh` | echo "工作流接口联调测试 | `bash scripts/test/test-workflow-api.sh` |
| `scripts/test/verify-infrastructure-websocket.sh` | 基础设施拆分与 office 站内信的固定环境验证入口。 | `bash scripts/test/verify-infrastructure-websocket.sh` |
| `scripts/test/verify-middle-platform.sh` | 中台架构整改的固定环境验证入口。 | `bash scripts/test/verify-middle-platform.sh` |
| `scripts/test/verify-monitoring-config.sh` | Apex Scaffold - 监控配置静态验证脚本 | `bash scripts/test/verify-monitoring-config.sh` |

## 约定

- 「用法」列的 `{p}` 指同一行「脚本」列的仓库相对路径；`bash {p}` 可直接执行。
- 脚本从仓库根解析路径，不依赖开发机绝对路径；需要环境变量的脚本在头注释中列明。
- 退出码：`0` 成功，非 `0` 失败并在 stderr 说明原因；CI 脚本以退出码阻断合并。
- 新增脚本须先在 `docs/tasks/project-tasks.md` 立卡，再在本 README 登记用途，保持单一职责。
