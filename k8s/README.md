# Apex Scaffold Kubernetes 部署资产说明

本目录提供完整的 K8s 部署与发布能力资产，与 [docs/DEPLOYMENT.md](../docs/DEPLOYMENT.md) §5/§12 配套。

## 文件清单

| 文件 | 说明 |
|------|------|
| `deployment.yaml` | 基础部署：Namespace / Secret / Deployment（稳定版滚动更新）/ Service / Ingress / HPA（CPU 70% + 内存 80% 自动伸缩 2~10 副本） |
| `canary-deployment.yaml` | 金丝雀（灰度）发布：金丝雀 Deployment + Service + Nginx Ingress 按权重/请求头导流 |
| `pod-disruption-budget.yaml` | 稳定版 PodDisruptionBudget，节点维护时至少保留一个稳定副本 |

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
#    修改 canary-deployment.yaml 中的 image: apex-scaffold:v-next 为实际新版本镜像
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

- Deployment labels `version` 与镜像 tag 保持一致（当前 v1.2.0）
- 灰度期间金丝雀使用 `track: canary` 标签，与稳定版按 `track` 维度隔离，便于日志/指标对比
