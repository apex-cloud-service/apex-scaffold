# `scripts/keycloak/`

Keycloak 身份域初始化：创建 realm 与 OIDC 客户端配置。

## 目录清单

| 脚本 | 用途 | 用法 |
| --- | --- | --- |
| `scripts/keycloak/init-realm.sh` | init-realm.sh - 初始化 Keycloak realm 与客户端配置 | `bash scripts/keycloak/init-realm.sh` |

## 约定

- 「用法」列的 `{p}` 指同一行「脚本」列的仓库相对路径；`bash {p}` 可直接执行。
- 脚本从仓库根解析路径，不依赖开发机绝对路径；需要环境变量的脚本在头注释中列明。
- 退出码：`0` 成功，非 `0` 失败并在 stderr 说明原因；CI 脚本以退出码阻断合并。
- 新增脚本须先在 `docs/tasks/project-tasks.md` 立卡，再在本 README 登记用途，保持单一职责。
