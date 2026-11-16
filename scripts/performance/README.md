# `scripts/performance/`

负载测试：JMeter 计划、执行入口与结果目录。

## 目录清单

| 脚本 | 用途 | 用法 |
| --- | --- | --- |
| `scripts/performance/jmeter/api-load-test.jmx` | <jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3"> | `jmeter -n -t scripts/performance/jmeter/api-load-test.jmx -l result.jtl` |
| `scripts/performance/results/20261113/gen_hmac_jwt.py` | 生成 HMAC HS256 ACCESS JWT（userId/tenantId/tokenType/exp），用于本地压测认证。 | `python3 scripts/performance/results/20261113/gen_hmac_jwt.py` |
| `scripts/performance/results/20261113/start-minimal-stack.sh` | 最小压测栈（复现用：需先完成 mvnd package；Redis 由 podman run -p 6379:6379 redis:7-alpine 提供）：workflow/office（H2 basic，HMAC J | `bash scripts/performance/results/20261113/start-minimal-stack.sh` |
| `scripts/performance/run-load-test.sh` | run-load-test.sh - 运行 JMeter 负载测试并生成 HTML 报告 | `bash scripts/performance/run-load-test.sh` |

## 约定

- 「用法」列的 `{p}` 指同一行「脚本」列的仓库相对路径；`bash {p}` 可直接执行。
- 脚本从仓库根解析路径，不依赖开发机绝对路径；需要环境变量的脚本在头注释中列明。
- 退出码：`0` 成功，非 `0` 失败并在 stderr 说明原因；CI 脚本以退出码阻断合并。
- 新增脚本须先在 `docs/tasks/project-tasks.md` 立卡，再在本 README 登记用途，保持单一职责。
