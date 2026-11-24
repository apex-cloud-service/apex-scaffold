# Apex Scaffold Kubernetes 部署与网关灰度

## 清单

| 文件 | 用途 |
| --- | --- |
| `deployment.yaml` | Namespace、Secret、八个服务 Deployment/Service、稳定 Ingress 与 HPA。网关稳定 Pod 和 Service 使用 `track: stable`。 |
| `canary-deployment.yaml` | 只部署金丝雀 Deployment 与隔离 Service，不创建任何流量入口。 |
| `canary-ingress.yaml` | 与稳定 Ingress 同 host/path/class 的 Nginx Canary 入口，初始权重固定为 0。 |
| `pod-disruption-budget.yaml` | 稳定网关副本 PDB，仅在显式 `--pdb` 时应用。 |

全部操作从仓库根目录执行。生产先更换 `deployment.yaml` 中模板 Secret 和镜像引用。`apex.sh` 基础 `apply` 对已存在集群做 fail-closed 检查：如果发现旧 `app=apex-gateway` selector 拓扑，会停止而不是直接应用 selector 更新。新环境可直接运行：

```bash
./apex-tools-parent/apex-tools-build/bin/apex.sh deploy k8s precheck
./apex-tools-parent/apex-tools-build/bin/apex.sh deploy k8s apply --pdb
```

## 旧稳定拓扑的分阶段迁移

Kubernetes Deployment selector 不可变；**不要**改动稳定 Deployment 的 `app: apex-gateway` selector。旧 Service 按 `app` 匹配时也会选中带相同 app 标签的金丝雀 Pod，因此仅把 Ingress 权重设为 0 并不能隔离端点。迁移前冻结其他发布，确认稳定版本、Ready 副本数及容量足以独立承载当前峰值流量；任何容量或身份无法确认时停止。

1. 查看稳定与金丝雀 Deployment、Ingress、所有 EndpointSlice：`kubectl -n apex-scaffold get deploy,pods,svc,ingress,endpointslices -o wide`。旧 Ingress 若有请求头/Cookie 等额外 canary 规则，先删除金丝雀 Ingress；再缩容旧金丝雀 Deployment 至 0，等待其 Pod 终止。不得在旧 Service selector 仍生效时保留金丝雀 Pod。
2. 确认 `apex-gateway` Service 的**所有** EndpointSlice 均仅列出稳定 Pod，Endpoint `targetRef.uid` 与 `track=stable` Pod UID 对应；通过实际主 host/path 的外部探测确认只返回已知稳定版本。EndpointSlice 非空且 stable Ready 容量不低于迁移前批准门槛，方可继续。
3. 仅给稳定 Deployment template 增加 `track: stable` 标签（不要改 selector），应用并等待：

   ```bash
   kubectl -n apex-scaffold patch deployment apex-gateway --type=merge \
     -p '{"spec":{"template":{"metadata":{"labels":{"track":"stable"}}}}}'
   kubectl -n apex-scaffold rollout status deployment/apex-gateway --timeout=300s
   ```

4. 确认所有目标稳定 Ready Pod 都带 `track=stable` 且稳定 Service EndpointSlice 仍全部指向这些 Pod，再收紧可变的 Service selector：

   ```bash
   kubectl -n apex-scaffold patch service apex-gateway --type=merge \
     -p '{"spec":{"selector":{"app":"apex-gateway","track":"stable"}}}'
   ```

   若启用 PDB，应用 `kubectl apply -f k8s/pod-disruption-budget.yaml`。确认 PDB 实际选中稳定副本，EndpointSlice 收敛为非空稳定端点，且主入口持续探测通过。未通过则保持金丝雀关闭，不执行后续步骤。
5. 确认目标 `IngressClass` 存在且 `spec.controller` 为配置的 Nginx Controller；将稳定 Ingress `spec.ingressClassName` 更新到该 class（默认 `nginx`），并确认主入口仍正常。已有集群若采用不同 class，可同时设置 CLI 环境变量 `APEX_K8S_INGRESS_CLASS` 和 `APEX_K8S_INGRESS_CONTROLLER`。
6. 再运行 `apex.sh deploy k8s apply --canary`。CLI 先核实稳定标签与稳定 Service selector、稳定 rollout 和 EndpointSlice 身份；应用金丝雀后等待 `apex-gateway-canary` rollout，就绪后核对两个 Service 的 EndpointSlice 均非空、Ready、互斥且归属正确，再创建 0% Ingress。重跑依据当前实际资源重新判断；部分状态会 fail closed，不会自动摘流或放量。

实际迁移期间需持续主入口探测，并记录版本、稳定容量、EndpointSlice/Pod UID、Ingress class 与请求探测结果。CLI 不负责自动证明容量足够，也不会自动执行主入口业务探测或确认流量版本。

## 金丝雀放量和回滚

```bash
./apex-tools-parent/apex-tools-build/bin/apex.sh deploy k8s check-canary
```

两个 Ingress 默认显式使用 `ingressClassName: nginx`。CLI 要求集群存在同名 IngressClass，且 `spec.controller` 精确匹配 `k8s.io/ingress-nginx`；集群采用不同名称/控制器时需在两个 Ingress 清单中使用其已验证的 Nginx class，并设置 `APEX_K8S_INGRESS_CLASS` 与 `APEX_K8S_INGRESS_CONTROLLER`。放量前再次人工核验 EndpointSlice/Pod UID 与版本、主入口外部探测只到稳定版本、Ingress class/host/path 对齐。然后只调整 `canary-ingress.yaml` 中 `nginx.ingress.kubernetes.io/canary-by-weight`（0–100），通过显式 `kubectl apply -f k8s/canary-ingress.yaml` 分级调整；每级观测错误率、延迟和稳定容量。该步骤不会由 CLI 自动放量，也不是蓝绿 promotion。

发布安全策略的本地行为回归可运行 `bash scripts/test/test-k8s-gateway-release.sh`；需要 `zsh` 与 `jq`，使用模拟 kubectl 和内存 JSON fixture，不访问集群。

回滚时先删除 `canary-ingress.yaml` 对应 Ingress 或将权重设为 0，再确认主入口和稳定 EndpointSlice 正常，之后才可缩容/删除金丝雀资源。只有已验证 Service selector 为 `app + track=stable` 后才允许金丝雀 Pod 与稳定服务并存。若回退到旧 selector，必须先停用金丝雀入口、摘除并排空金丝雀 Pod，再验证所有端点与主入口后才能恢复旧 selector。

真实集群迁移、连续可用性、流量隔离及逐级放量须在隔离集群演练；单测与静态清单检查不代表生产验证。真正蓝绿部署需要独立的双环境、预览入口、切流和回退设计，不属于本清单能力。
