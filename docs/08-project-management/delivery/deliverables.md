# 交付物清单

> **版本**: v1.0
> **核对日期**: 2026-10-30
> **基线提交**: `b8e5733`
> **维护者**: Apex Team

本文档列出一次交付应包含的产物、生成方式与校验方法。**产物必须能从仓库基线重新生成**，只存在于本地的文件不计入交付物。

---

## 1. 可执行产物

### 1.1 服务 JAR

| 产物 | 模块 | 生成命令 | 校验 |
| --- | --- | --- | --- |
| 网关 | `apex-gateway` | `mvnd -pl apex-gateway -am package -P<档位>` | `java -jar` 可启动，8080 返回健康检查 |
| 系统服务 | `apex-system-service` | 同上替换模块名 | 8082 业务端口、8083 管理端口 |
| 认证服务 | `apex-auth-service` | 同上 | 8081 |
| 工作流服务 | `apex-workflow-service` | 同上 | 8093 |
| 办公服务 | `apex-office-service` | 同上 | 8084 |
| 文件服务 | `apex-file-service` | 同上 | 8085 |
| 任务代理（可选） | `apex-job-service` | 同上 | 8095 |
| 脱敏服务（可选） | `apex-masking-service` | 同上 | 8096 |

档位写入产物内的 `target/classes/apex-build.properties`，可用 `grep build.profile.id` 核对是否与构建命令一致。

> 三个档位的依赖裁剪规则由根 POM 的 `basic`/`standard`/`enterprise` profile 定义；`basic` 档以 `apex-example` 作为聚合装配模块。档位与运行时环境 profile（`dev`/`it`/`prod`）是两套配置，不能互相替代。

### 1.2 发布压缩包

由 [release.sh](../../../scripts/release/release.sh) 生成：

```bash
source scripts/build/init-build-env.sh
# 版本号可省略（读取根 POM）；显式传入时必须与 POM 版本一致
./scripts/release/release.sh <basic|standard|enterprise> [版本号]
```

输出 `dist/apex-scaffold-<版本>-<档位>.zip` 及同名 `.sha256`，脚本内组装内容为：

| 内容 | 来源 | 说明 |
| --- | --- | --- |
| 八个服务 JAR | 各服务 `target/` | 网关 + 六个业务服务 + 查询聚合服务 |
| `LICENSE` / `LICENSE-COMMERCIAL.md` | 仓库根 | 双轨授权 |
| `README.md` / `CHANGELOG.md` / `CODE_OF_CONDUCT.md` / `CONTRIBUTING.md` | 仓库根 | |
| `docs/` | `docs/` 全目录 | 保留子目录结构，仅复制 Markdown 与图片（排除测试证据 json/log/jfr 等） |
| `config/` | `config/`（排除 `private.key`） | 配置模板、监控、Nacos、Sentinel |
| `podman-compose.yml` / `podman-compose.infra.yml` / `Containerfile` | 仓库根 | |
| `k8s/` | 仓库根 | 基础部署、金丝雀、PDB |
| `scripts/build` / `scripts/deploy` / `scripts/lib` | `scripts/` | 构建与部署脚本及其公共函数库 |
| `tools/local-deployment.jar` | `apex-tools-deployment/target/` | Compose `security-key-check` 离线校验工具 |

**不含**：`apex-license-tools`、`scripts/license/`、`config/license/private.key`、测试源码、构建中间产物。

---

## 2. 部署与运维资产

| 资产 | 路径 | 用途 |
| --- | --- | --- |
| 容器镜像定义 | [Containerfile](../../../Containerfile) | 单服务镜像，`SERVICE_MODULE` 选择目标 |
| 快速镜像 | [Containerfile.quick](../../../Containerfile.quick) | 本地快速构建 |
| 全栈编排 | [podman-compose.yml](../../../podman-compose.yml) | 网关、五服务、可选服务与基础设施 |
| 基础设施编排 | [podman-compose.infra.yml](../../../podman-compose.infra.yml) | MySQL、Redis、Nacos、RocketMQ、MinIO 等 |
| Kubernetes 基础 | [k8s/deployment.yaml](../../../k8s/deployment.yaml) | Namespace、Secret、Deployment、Service、Ingress、HPA |
| Kubernetes 金丝雀 | [k8s/canary-deployment.yaml](../../../k8s/canary-deployment.yaml) | 按权重/请求头导流 |
| Pod 中断预算 | [k8s/pod-disruption-budget.yaml](../../../k8s/pod-disruption-budget.yaml) | 节点维护保护 |
| 环境变量模板 | [.env.example](../../../.env.example) | 变量名与默认值参考 |
| 监控资产 | [config/monitoring/](../../../config/monitoring/) | Prometheus 抓取、13 条告警规则、Grafana 大盘 |
| Nacos 配置 | [config/nacos/](../../../config/nacos/) | 公共、数据源、网关配置 |
| Sentinel 规则 | [config/sentinel/](../../../config/sentinel/) | 流控规则 |

---

## 3. 数据库迁移

迁移脚本是交付物的一部分，位置与版本见 [交付范围与基线](./delivery-scope.md) §4。交付时必须同时提供 MySQL 与 PostgreSQL 两套目录，且版本集合一致。

> 迁移脚本一经在目标环境执行即不可修改。交付后如需修正，只能追加新版本（详见 [变更与配置控制](./change-control.md)）。

---

## 4. 文档交付范围

| 类别 | 文档 | 交付必要性 |
| --- | --- | --- |
| 入口 | [README.md](../../../README.md)、[文档中心](../../README.md) | 必需 |
| 快速开始 | [quickstart.md](../../00-getting-started/quickstart.md) | 必需 |
| 部署 | [deployment.md](../../05-operations/deployment.md) | 必需 |
| 配置 | [configuration.md](../../05-operations/configuration.md) | 必需 |
| 开发规范 | [development-standards.md](../../04-development/development-standards.md) | 必需 |
| 接口清单 | [API_ROUTES.md](../../03-api/API_ROUTES.md) | 必需 |
| 运维 | [oncall-runbook.md](../../05-operations/oncall-runbook.md)、[microservice-hardening-guide.md](../../05-operations/microservice-hardening-guide.md) | 必需 |
| 安全 | [security-protections.md](../../05-operations/security-protections.md) | 必需 |
| 授权 | [license-management.md](../../07-commercial/license-management.md) | 商业版必需 |
| 交付管理 | 本目录文档集 | 必需（内部与客户交接共用） |
| 内部过程文档 | `docs/reports/`、`docs/06-testing/`、`docs/08-project-management/`、`docs/_archive/`、需求与计划 | **不作为客户交付文档**；`release.sh` 仅按名称排除 `COMMERCIAL_*`/`REFACTORING_*`/`*GAP_ANALYSIS*`，其余 Markdown 随包以保持跨目录链接可解析 |

> `release.sh` 已改为按目录结构复制 docs（排除 `_archive`、`06-testing`、`08-project-management`），上述客户文档会随包交付；发布流水线另有内容校验（见 [风险登记册](./risk-register.md) R-02）。

---

## 5. 交付物完整性校验

交付前逐项核对，全部通过方可放行：

| # | 校验项 | 方法 | 判定 |
| --- | --- | --- | --- |
| 1 | 八个服务 JAR 存在且唯一 | 每个服务 `target/` 下 `*.jar` 计数为 1 | 通过 / 不通过 |
| 2 | 档位标识正确 | `grep build.profile.id <module>/target/classes/apex-build.properties` | 与命令一致 |
| 3 | 私钥未打入 | `unzip -l <交付包> \| grep private.key` 无输出 | 通过 / 不通过 |
| 4 | 授权工具未打入 | 包内无 `apex-license-tools`、无 `scripts/license/` | 通过 / 不通过 |
| 5 | 公钥存在 | 包内 `config/license/public.key` 或 jar 内 `license/public.key` 存在 | 通过 / 不通过 |
| 6 | 迁移双库齐全 | 每个域 `mysql` 与 `postgresql` 版本集合一致 | 通过 / 不通过 |
| 7 | 文档可读 | 包内文档链接可解析、无断链 | 通过 / 不通过 |
| 8 | 校验和 | 生成 SHA-256 并随交付渠道提供 | 已提供 / 未提供 |
| 9 | 无构建残渣 | 无 `consumer-*.pom`、`resume.properties`、`failsafe-summary.xml` | 通过 / 不通过 |
| 10 | 无真实凭据 | 无 `.env`、私钥、证书、真实密码 | 通过 / 不通过 |

> 校验 5 存在基线问题：`config/license/public.key` 与 `apex-common/src/main/resources/license/public.key` 均被 `.gitignore` 的 `*.key` 规则排除，**未纳入版本控制**。从干净克隆构建会缺失公钥，License 校验进入降级模式。处理方式见 [风险登记册](./risk-register.md) R-03。

---

## 6. 交付包命名与版本

| 项 | 约定 |
| --- | --- |
| 包名 | `apex-scaffold-<版本号>-<档位>.zip` |
| 版本号 | 与根 `pom.xml` 的 `<version>` 一致 |
| 档位 | `basic` / `standard` / `enterprise` |
| 校验文件 | `apex-scaffold-<版本号>-<档位>.zip.sha256` |

> [release.sh](../../../scripts/release/release.sh) 默认读取根 POM 版本；显式传入的版本必须与 POM 一致，否则脚本直接失败（见 [风险登记册](./risk-register.md) R-04）。校验文件 `.zip.sha256` 由 release.sh 打包阶段自动生成，随包分发。

---

**维护者**: Apex Team
**下次核对**: 发布脚本或交付物结构变更时
