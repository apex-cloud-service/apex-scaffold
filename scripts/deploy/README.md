# `scripts/deploy/`

部署编排：Podman Compose 一键启停、单服务启动、监控栈与镜像拉取。

## 目录清单

| 脚本 | 用途 | 用法 |
| --- | --- | --- |
| `scripts/deploy/apex-scaffold.service` | Apex Scaffold - systemd 服务单元模板（微服务 java -jar 部署） | （systemd 单元，安装到 `/etc/systemd/system/`） |
| `scripts/deploy/app.sh` | 兼容原部署入口，统一使用微服务启动与 Agent 配置。 | `bash scripts/deploy/app.sh` |
| `scripts/deploy/assemble-topology.sh` | 组合部署拓扑装配（方案 §13 脚本交付物 / C9）。 | `bash scripts/deploy/assemble-topology.sh` |
| `scripts/deploy/deploy-monitoring.sh` | 兼容部署分类入口，实际监控部署逻辑集中在 podman/ 分类目录。 | `bash scripts/deploy/deploy-monitoring.sh` |
| `scripts/deploy/podman/deploy-monitoring.sh` | 验证并启动独立监控栈；macOS 需先 podman machine start。 | `bash scripts/deploy/podman/deploy-monitoring.sh` |
| `scripts/deploy/podman/pull-infra-images.sh` | 拉取基础设施镜像，网络失败最多重试四次。 | `bash scripts/deploy/podman/pull-infra-images.sh` |
| `scripts/deploy/pull-infra-images.sh` | 兼容部署分类入口，实际镜像拉取逻辑集中在 podman/ 分类目录。 | `bash scripts/deploy/pull-infra-images.sh` |
| `scripts/deploy/service/app.sh` | Apex Scaffold - 微服务 java -jar 部署守护脚本 | `bash scripts/deploy/service/app.sh` |
| `scripts/deploy/service/start-local.sh` | 启动脚本 - 使用 java -jar 方式在本地运行单个微服务 | `bash scripts/deploy/service/start-local.sh` |
| `scripts/deploy/service/start.sh` | 保留旧路径，规范目录维护实际实现。 | `bash scripts/deploy/service/start.sh` |
| `scripts/deploy/service/stop.sh` | 保留旧路径，规范目录维护实际实现。 | `bash scripts/deploy/service/stop.sh` |
| `scripts/deploy/start.sh` | Apex Scaffold - Podman Compose 一键启动 | `bash scripts/deploy/start.sh` |
| `scripts/deploy/stop.sh` | Apex Scaffold - Podman Compose 停止 | `bash scripts/deploy/stop.sh` |

## 约定

- 「用法」列的 `{p}` 指同一行「脚本」列的仓库相对路径；`bash {p}` 可直接执行。
- 脚本从仓库根解析路径，不依赖开发机绝对路径；需要环境变量的脚本在头注释中列明。
- 退出码：`0` 成功，非 `0` 失败并在 stderr 说明原因；CI 脚本以退出码阻断合并。
- 新增脚本须先在 `docs/tasks/project-tasks.md` 立卡，再在本 README 登记用途，保持单一职责。
