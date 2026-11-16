# `scripts/data/`

本地联调数据：测试数据 SQL 与「拉起 MySQL 并执行 SQL」的一键脚本。

## 目录清单

| 脚本 | 用途 | 用法 |
| --- | --- | --- |
| `scripts/data/backfill_auth_user_account_id.sql` | auth_user.account_id 统一账户映射回填模板（人工执行，非 Flyway） | `mysql -u<user> -p < scripts/data/backfill_auth_user_account_id.sql` |
| `scripts/data/init_workflow_test_data.sql` | 工作流测试数据初始化脚本 | `mysql -u<user> -p < scripts/data/init_workflow_test_data.sql` |
| `scripts/data/init_workflow_test_data_v2.sql` | 工作流测试数据初始化脚本（修复版） | `mysql -u<user> -p < scripts/data/init_workflow_test_data_v2.sql` |
| `scripts/data/start-mysql-and-run-sql.sh` | Podman MySQL 启动与权限 SQL 执行脚本 | `bash scripts/data/start-mysql-and-run-sql.sh` |

## 约定

- 「用法」列的 `{p}` 指同一行「脚本」列的仓库相对路径；`bash {p}` 可直接执行。
- 脚本从仓库根解析路径，不依赖开发机绝对路径；需要环境变量的脚本在头注释中列明。
- 退出码：`0` 成功，非 `0` 失败并在 stderr 说明原因；CI 脚本以退出码阻断合并。
- 新增脚本须先在 `docs/tasks/project-tasks.md` 立卡，再在本 README 登记用途，保持单一职责。
