# `scripts/datasource/`

动态数据源演示环境：初始化主从库与路由探针，验证多数据源切换。

## 目录清单

| 脚本 | 用途 | 用法 |
| --- | --- | --- |
| `scripts/datasource/init-slave.sql` | 初始化本地动态数据源演示库，并在主从库创建可识别的路由探针。 | `mysql -u<user> -p < scripts/datasource/init-slave.sql` |
| `scripts/datasource/setup-slave.sh` | Apex Scaffold 数据源动态切换 - 从库一键初始化脚本 | `bash scripts/datasource/setup-slave.sh` |

## 约定

- 「用法」列的 `{p}` 指同一行「脚本」列的仓库相对路径；`bash {p}` 可直接执行。
- 脚本从仓库根解析路径，不依赖开发机绝对路径；需要环境变量的脚本在头注释中列明。
- 退出码：`0` 成功，非 `0` 失败并在 stderr 说明原因；CI 脚本以退出码阻断合并。
- 新增脚本须先在 `docs/tasks/project-tasks.md` 立卡，再在本 README 登记用途，保持单一职责。
