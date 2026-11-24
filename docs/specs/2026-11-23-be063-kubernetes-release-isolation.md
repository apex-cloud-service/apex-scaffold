# BE-063 Wave D1：Kubernetes 网关灰度隔离规格

## 背景与基线证据

- `k8s/deployment.yaml` 中稳定 Deployment 的 selector 与 Pod template 目前只有 `app: apex-gateway`；同文件稳定 Service 也只按该标签选择 Pod。
- `k8s/canary-deployment.yaml` 的金丝雀 Pod 同样带 `app: apex-gateway`，另有 `track: canary`；金丝雀 Service 已按 `app + track=canary` 隔离。
- `k8s/pod-disruption-budget.yaml` 按 `app + track=stable` 选择 Pod，但稳定 Pod template 当前没有 `track: stable`，因此 PDB 可能没有目标 Pod。
- 稳定 Deployment selector 是不可变字段，不能通过将 `track: stable` 添加到 selector 来修复。`k8s/README.md` 当前称稳定版已使用该标签，与清单不符。
- `apex-tools-parent/apex-tools-build/bin/apex.sh` 的 K8s apply/health 分支等待 `deployment/apex-scaffold`，实际网关 Deployment 名为 `apex-gateway`；status 只列出资源。金丝雀清单的 Ingress 权重缺省为 10，和手册“先部署、不接收流量”的描述不符。

## 目标与边界

Wave D1 修复网关稳定/金丝雀 Service Endpoint 隔离、稳定 PDB 选择、部署等待目标与安全的初始灰度权重；使应用脚本在资源就绪验证前不允许用户放量。仅针对 Nginx Ingress canary 路径，不宣称实现蓝绿部署或自动化指标分析。

不改变稳定 Deployment selector、服务 API、数据库迁移、业务代码或其他服务路由。真正蓝绿需另行设计双环境资源、预览入口、流量切换与回退合同，不以权重 100% 的 canary 冒充蓝绿。

## 影响面

- **模块/文件**：计划涉及 `k8s/deployment.yaml`、`k8s/canary-deployment.yaml`、`k8s/pod-disruption-budget.yaml`、`k8s/README.md`、`apex-tools-parent/apex-tools-build/bin/apex.sh` 及其契约测试；当前规格评审租约只修改本规格与索引。
- **接口/数据**：不改 HTTP/API、数据库、消息契约或持久化数据；只调整 Kubernetes Pod labels、Service selectors、Ingress 流量门禁及部署 CLI 行为。
- **运行拓扑**：网关稳定/金丝雀 EndpointSlice 分离；实际迁移需要可用 Kubernetes 集群和 Ingress Controller，不在本规格阶段操作集群。

## 安全迁移顺序

现网稳定 Deployment selector 不可变，必须保留 `app: apex-gateway`。为避免 selector 切换期间稳定 Service 无 endpoint，配置与编排需支持分阶段迁移：

1. **旧拓扑预检与摘流**：冻结其他发布操作，确认稳定副本的实际版本、健康和容量足以独立承载流量；无法确认则停止。若已有金丝雀，先停用其 Ingress（包括权重以外的导流规则），再将旧金丝雀后端摘流并排空。旧 selector 下仅改 Ingress 不足以隔离；必须等稳定 Service 的全部 EndpointSlice 均不再包含金丝雀 Pod，且主入口探测确认只命中健康稳定后端，才继续。
2. **滚动更新稳定标签**：仅给稳定 Pod template 增加 `track: stable`，不顺带更换稳定镜像；保留稳定 Deployment selector 和当前 Service selector。等待 `deployment/apex-gateway` rollout 完成，核对 Ready Pod 的归属与标签；如已启用 PDB，核对它确实选中稳定副本。期间不得重新启用金丝雀后端。
3. **收紧稳定 Service**：只有新标签的健康稳定后端已达到预检容量门槛，才将稳定 Service selector 收紧为 `app=apex-gateway, track=stable`。等待 EndpointSlice 收敛，验证稳定后端非空、版本正确且主入口探测通过。
4. **恢复金丝雀预览**：只有确认步骤 3 生效后，才部署或恢复金丝雀后端，初始对外权重为 0，且不启用按请求头或 Cookie 等额外导流规则。新版本 rollout 就绪、两组健康 EndpointSlice 按 Pod 身份互斥且主入口确认仍只返回稳定版本后，才允许通过独立的显式操作放量。
5. **逐阶段放量**：每次调整前重新确认两组后端健康、互斥且版本匹配；任一检查失败即非零退出，不继续放量，按下述阶段执行回滚。编排重跑必须从实际 selector、EndpointSlice 和 Ingress 状态重新判断阶段，不依赖上次命令已成功的假设。

若现有编排无法把 Deployment template rollout、Service selector 更新和每阶段验证拆开，必须先拆分清单或增加显式迁移子命令；禁止对现网直接一条 `kubectl apply -f deployment.yaml` 假设顺序安全。

## 验收标准

- 单测/静态契约：解析 K8s YAML 并验证 stable/canary label、Service selector、PDB selector、实际 Deployment 名称、canary 初始 0%、apply/rollout 等待顺序与失败非零退出。
- `apex.sh deploy k8s apply --canary` 必须等待正确 Deployment rollout，检查两组 endpoint 存在且不交叉，并在未健康/配置错误时保持 0% 流量并退出非零。
- 静态与编排回归需覆盖旧清单已有 Ready 金丝雀、selector 切换失败、执行中断后重跑和可选 PDB 未部署。校验未通过必须非零退出，且不执行后续放量或恢复金丝雀动作。
- 真实集群验收：以已有 Ready 金丝雀的旧清单演练迁移；在滚动更新和 selector 切换期间持续探测主入口，记录请求可用性，并用后端版本识别验证完成摘流后没有串流；同时核对 EndpointSlice、PDB、0%/小流量/逐级放量/回到 0%、切换失败及重跑。记录集群版本、Ingress Controller 版本、镜像摘要与命令输出；rollout 或 PDB 状态不能代替流量验证。
- 未提供可用集群时，只能报告单测/静态契约通过；不能声称 Kubernetes 灰度或无中断迁移已经实测。

## 回滚与兼容

- **收紧前失败（或状态未确认）**：保持金丝雀入口关闭及后端摘除，不启动金丝雀；核对稳定 EndpointSlice 和主入口后才恢复或重试稳定发布。权重为 0 不证明旧 selector 已隔离金丝雀。
- **收紧后只回退灰度流量**：撤销所有金丝雀导流规则或停用其 Ingress，保留稳定 Service 的已验证 selector；核对主入口只命中稳定版本后再排空、清理金丝雀资源。
- **确需恢复旧 selector**：必须先停用金丝雀入口并摘除金丝雀后端，验证不存在会被旧 selector 纳入的金丝雀 Pod/端点；再恢复旧 selector、确认稳定端点和入口健康。旧 selector 生效期间不得恢复金丝雀。
- `track: stable` 仅添加到稳定 Pod template，不修改不可变 Deployment selector。
- 迁移脚本必须分阶段、幂等并在任一等待/校验失败时停止后续步骤；禁止自动把权重设为 100 或以流量切换替代镜像 promotion。
- 本规格获独立 R5 通过后方可另立实现租约；实现须通过编排回归和隔离集群迁移验证后才能用于现网。
