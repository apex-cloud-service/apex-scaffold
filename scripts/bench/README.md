# `scripts/bench/`

性能基准脚本（虚拟线程与平台线程吞吐对比等），仅用于本地/压测环境，不参与 CI。

## 目录清单

| 脚本 | 用途 | 用法 |
| --- | --- | --- |
| `scripts/bench/bench-vt.sh` | Apex Scaffold 虚拟线程压测脚本（阻塞 I/O 池 A/B 对比） | `bash scripts/bench/bench-vt.sh` |
| `scripts/bench/bench.sh` | Apex Scaffold 性能压测脚本（wrk） | `bash scripts/bench/bench.sh` |

## 约定

- 「用法」列的 `{p}` 指同一行「脚本」列的仓库相对路径；`bash {p}` 可直接执行。
- 脚本从仓库根解析路径，不依赖开发机绝对路径；需要环境变量的脚本在头注释中列明。
- 退出码：`0` 成功，非 `0` 失败并在 stderr 说明原因；CI 脚本以退出码阻断合并。
- 新增脚本须先在 `docs/tasks/project-tasks.md` 立卡，再在本 README 登记用途，保持单一职责。
