# BE-063 Wave D1 Kubernetes 灰度隔离实现

## 范围

按已获独立 R5 `APPROVE_WITH_NITS` 的 D1 规格，隔离 Kubernetes 网关稳定/金丝雀服务端点并为发布 CLI 增加 fail-closed 门禁。基线 `dev` SHA：`90a2f783e2c251a55f53b0fc1b845b11d537e951`。不操作真实集群，不自动放量，不声称蓝绿。

## 改动清单

- 稳定 Deployment 保留不可变的 `app: apex-gateway` selector，只给 Pod template 添加 `track: stable`；稳定 Service selector 和既有 PDB 与之对齐。
- 金丝雀 Deployment 文件不再内含 Ingress；新增独立 `canary-ingress.yaml`，缺省权重 0。
- `apex.sh deploy k8s apply` 识别缺失资源/部分迁移/旧 selector；旧 Service 或 Deployment 拓扑未完成迁移时 fail closed。修正等待目标至 `apex-gateway`，等待 rollout 后检查 Ready EndpointSlice、target Pod UID、ReplicaSet 和 Deployment track 归属。
- `apply --canary` 只操作金丝雀预览：重验稳定基线、稳定/金丝雀 rollout、两组端点非空且互斥、稳定入口 host/path/class 一致和仅允许权重导流规则后，才创建 0% Ingress。`check-canary` 重做资源与入口验证；人工探测、容量评估及放量仍由操作者负责。
- 更新 Kubernetes 迁移/回滚手册、构建工具指南、运维入口与项目索引；新增工具模块静态契约测试。

## 验证证据

- `source scripts/build/init-build-env.sh && mvnd -B -pl :apex-tools-build -am -Dtest=KubernetesReleaseContractTest -Dsurefire.failIfNoSpecifiedTests=false test spotless:check`：`BUILD SUCCESS`；2 tests，0 failures/errors/skips；Spotless 通过。第一次断言定位错误，修正后复跑通过。
- `bash scripts/test/generate-test-report.sh`：成功生成忽略目录下 `target/reports/summary/test-report.md` 与 `test-summary.json`；递归 reactor 45 模块，报告 XML 快照汇总 Surefire 6278 + Failsafe 405 = 6683 tests，0 failures/errors、33 skipped，4 条重复 testcase 告警。该脚本只汇总现存各模块 XML，不能证明全量 `verify` 或所有 XML 对应同一代码 SHA；报告不纳入版本控制。
- `zsh -n apex-tools-parent/apex-tools-build/bin/apex.sh`：通过。
- `bash scripts/test/test-k8s-gateway-release.sh`：模拟旧/部分 stable selector fail closed，验证 EndpointSlice target UID 不属于预期 workload、stable/canary UID 重叠、Ingress class 缺失/错误、host 错配、header 导流和非零入场权重均被拒绝；全通过，不访问集群。
- Ruby Psych 解析 `deployment.yaml`、`canary-deployment.yaml`、`canary-ingress.yaml`、`pod-disruption-budget.yaml`：均通过。
- `bash scripts/governance/audit-backend-rules.sh --strict`：后端红线无阻断项（含已登记 Advice 例外与非阻断 POJO 抽查告警）；`git diff --check`：通过。
- `bash scripts/governance/verify-docs.sh --strict` 首次发现本记录创建前的三个待登记链接，修复后复跑；旧归档文档中的断链仅做基线记录，不在本任务改写归档。
- 没有可验证集群上下文；未执行部署、容量压测、外部入口探测、流量切换或蓝绿回滚。

## 影响面

变更局限于 K8s 网关清单、K8s 编排 CLI/测试和相应文档。新集群基础 apply 使用稳定标签；旧拓扑必须先人工逐阶段迁移。CLI 对主入口业务版本/连续可用性与独立承载容量不作判断。当前未暂存、提交、推送或部署；全仓构建与新的全量测试报告尚未完成。

## 回滚方式

先删除金丝雀 Ingress 或设为 0，并确认稳定 Service EndpointSlice 和主入口正常，再缩容/删除金丝雀资源。不要先恢复旧 selector；只有金丝雀入口关闭且所有金丝雀 Pod/EndpointSlice 均已摘除后才可回退 Service selector。变更按独立文件/单项 revert；保留 BE-043/BE-049/FE-029 既有状态及任何用户工作区差异。

## 独立复核

D1 规格由 Pauli 增量复核 `APPROVE_WITH_NITS`。实现首轮 R5 `REJECT` 要求验证真实 IngressClass/controller、补行为测试；已添加显式 Nginx class/controller fail-closed 校验、可复用 jq 策略文件和模拟 kubectl/端点/Ingress 行为测试，增量复核 `APPROVE_WITH_NITS`。R5 建议集成测试覆盖 CLI controller 查询分支；随后已添加缺失/错误 controller 的 CLI mock 用例并通过。本地建议项已处理；容量、排空和主入口探测仍是现场验收条件，没有运行现场操作。

## 追记：Wave E 全仓门禁结果

- `source scripts/build/init-build-env.sh && mvnd -T4 -B verify`：失败。`apex-auth-service` PMD 报告 `Unsupported class file major version 65`（PMD 6/ASM 不能解析 Java 21 编译产物），发生于静态分析阶段；未放宽 PMD、未跳过门禁。由同一并行 reactor 触发的 `ExampleDataSourceTimeoutTest.shouldBoundUnresponsiveDatabaseHandshakeUsingTestPoolConfiguration` 测得 12.667 秒，超过其 12 秒阈值并报 1 个失败；这是运行报告的时限失败，不是本波触碰的功能代码。
- 随后运行 `bash scripts/test/generate-test-report.sh` 成功，输出仍位于 git 忽略的 `target/reports/summary/`。45 模块 XML 汇总 6683 项、1 失败、0 errors、33 skipped，4 条重复 testcase 告警。工具聚合各模块既有 XML，不保证来自同一次构建或同一 SHA；因此本汇总不能替代失败的全量 reactor 验收。
- 构建/质量门禁阻塞留待单独任务处理；本波不修改 PMD 版本、质量门槛或 unrelated 的时延断言。此前 D1 定向 Maven 2/2、Spotless、模拟发布行为与严格治理证据仍有效。
