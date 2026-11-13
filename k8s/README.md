# Apex Scaffold Kubernetes 部署资产说明

本目录提供完整的 K8s 部署与发布能力资产，与 [docs/05-operations/deployment.md](../docs/05-operations/deployment.md) §5/§12 配套。

## 文件清单

| 文件 | 说明 |
|------|------|
| `deployment.yaml` | 基础部署：Namespace / Secret / **8 个服务各自的 Deployment + Service**（gateway、auth、system、workflow、office、file、integration、aggregation）/ Ingress / 网关 HPA（CPU 70% + 内存 80% 自动伸缩 2~10 副本） |
| `canary-deployment.yaml` | 网关金丝雀（灰度）发布：金丝雀 Deployment + Service + Nginx Ingress 按权重/请求头导流 |
| `pod-disruption-budget.yaml` | 网关稳定版 PodDisruptionBudget，节点维护时至少保留一个稳定副本 |

## 服务与端口（与 `build-manifest.yaml` / `podman-compose.yml` 同源）

| 服务 | 镜像 | 业务端口 | 管理端口 | 数据库 |
|------|------|---------:|---------:|--------|
| `apex-gateway` | `apex-gateway:latest` | 8080 | — | — |
| `apex-auth-service` | `apex-auth-service:latest` | 8081 | — | `apex_auth` |
| `apex-system-service` | `apex-system-service:latest` | 8082 | 8083 | `apex_system` |
| `apex-workflow-service` | `apex-workflow-service:latest` | 8093 | — | `apex_workflow` |
| `apex-office-service` | `apex-office-service:latest` | 8084 | — | `apex_office` |
| `apex-file-service` | `apex-file-service:latest` | 8085 | — | `apex_file` |
| `apex-integration-service` | `apex-integration-service:latest` | 8094 | 8097 | `apex_integration` |
| `apex-aggregation-service` | `apex-aggregation-service:latest` | 8098 | — | —（只读跨源聚合，无库） |

镜像构建示例：`podman build -f Containerfile --build-arg SERVICE_MODULE=apex-aggregation-service --build-arg SERVICE_PORT=8098 --build-arg MANAGEMENT_PORT=8098 -t apex-aggregation-service:latest .`
（`MANAGEMENT_PORT` 对无管理端口的服务取业务端口，与 Containerfile `HEALTHCHECK_PORT` 一致。）

**探针端口**：system 与 integration 使用管理端口（8083 / 8097），其余使用业务端口。
**服务发现**：默认关闭 Nacos（Secret 中 `NACOS_DISCOVERY_ENABLED=false`），网关经 `ROUTE_*_URI`
直连各 Service DNS；接入 Nacos 时改为 true 并注入 `NACOS_SERVER_ADDR`。

## 基础部署

```bash
kubectl apply -f k8s/deployment.yaml
```

> 使用前按环境修改 Secret（`DB_MASTER_PASSWORD` / `DB_SLAVE_PASSWORD` / `REDIS_PASSWORD` / `JWT_SECRET` 必改）与 Ingress 域名。
>
> `startupProbe` 应用启动阶段使用管理端口 `/actuator/health/readiness`，启动成功后才启用 liveness/readiness；`readinessProbe` 负责摘流，`livenessProbe` 负责异常重启；HPA 负责副本数，应用不直接操作 Kubernetes 控制面。稳定版使用 `track: stable`，金丝雀使用 `track: canary`，避免 Service selector 交叉选 Pod。`terminationGracePeriodSeconds` 为 120 秒，覆盖应用 90 秒总关停预算和摘流传播缓冲；请通过压测校准副本数与关停时间。

## 灰度发布演练（10% 流量 → 全量）

```bash
# 1. 确认稳定版运行正常（2 副本、Ready）
kubectl -n apex-scaffold get deploy,svc,ing

# 2. 部署金丝雀（v-next 镜像），此时不接收流量
#    修改 canary-deployment.yaml 中的 image: apex-gateway:v-next 为实际新版本网关镜像
kubectl apply -f k8s/canary-deployment.yaml

# 3. 观察灰度流量（约 10% 请求进入金丝雀）
#    通过日志/指标确认: kubectl -n apex-scaffold logs -l track=canary --tail=50
#    Grafana 按 version 标签对比 apex_api_* 指标的错误率/P95

# 4a. 验证通过 → 逐步放量: 改 canary-by-weight 为 50 → 100
kubectl -n apex-scaffold edit ingress apex-scaffold-canary-ingress   # 改 canary-by-weight: "100"

# 4b. 全量切换: 将主 Deployment 镜像更新为新版本后，删除金丝雀
kubectl -n apex-scaffold set image deploy/apex-scaffold apex-scaffold=apex-scaffold:v-next
kubectl -n apex-scaffold rollout status deploy/apex-scaffold
kubectl delete -f k8s/canary-deployment.yaml

# 5. 验证失败 → 即时回滚（流量 100% 回稳定版，金丝雀保留供排查）
kubectl -n apex-scaffold delete ingress apex-scaffold-canary-ingress
# 或保留金丝雀但停流: kubectl -n apex-scaffold annotate ingress apex-scaffold-canary-ingress \
#   nginx.ingress.kubernetes.io/canary-by-weight=0
```

## 进阶：Argo Rollouts 渐进式交付

方式二适合需要自动化的团队（自动分析指标 → 自动放量/回滚）：

```bash
# 安装（选用）
kubectl create namespace argo-rollouts
kubectl apply -n argo-rollouts -f https://github.com/argoproj/argo-rollouts/releases/latest/download/install.yaml

# 将 Deployment 迁移为 Rollout（滚动更新策略替换为 canary 策略，
# 定义 steps: setWeight 10 → pause → setWeight 50 → pause → setWeight 100）
```

> Rollout 模板与 AnalysisTemplate 示例将在 v1.2 提供；当前以 Nginx Ingress 权重方式为准。

## 版本标签约定

- Deployment labels `version` 与镜像 tag 保持一致（当前 v1.3.1）
- 灰度期间金丝雀使用 `track: canary` 标签，与稳定版按 `track` 维度隔离，便于日志/指标对比
