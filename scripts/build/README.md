# `scripts/build/`

构建环境初始化与构建入口：固定 JDK 21 与 mvnd，供本地与 CI 复用。

## 目录清单

| 脚本 | 用途 | 用法 |
| --- | --- | --- |
| `scripts/build/build.sh` | Apex Scaffold 一键构建脚本 | `bash scripts/build/build.sh` |
| `scripts/build/init-build-env.sh` | 构建环境初始化检查 | `bash scripts/build/init-build-env.sh` |

## 约定

- 「用法」列的 `{p}` 指同一行「脚本」列的仓库相对路径；`bash {p}` 可直接执行。
- 脚本从仓库根解析路径，不依赖开发机绝对路径；需要环境变量的脚本在头注释中列明。
- 退出码：`0` 成功，非 `0` 失败并在 stderr 说明原因；CI 脚本以退出码阻断合并。
- 新增脚本须先在 `docs/tasks/project-tasks.md` 立卡，再在本 README 登记用途，保持单一职责。
