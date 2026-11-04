# 批次 C3 + C8 测试报告：存储 SPI 扩展点与事件通道自适应

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

- 批次：方案 [unified-auth-tenant-platform-remediation-plan.md](../../../08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md) 批次 C3/C8（M3 组件化服务化）
- 执行日期：2026-11-01
- 环境：macOS、JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`（构建环境校验通过）
- 状态：**实施完成，门禁结果见 §4**；M3 剩余 C9（apex-boot-all 组合拓扑）/C4（interfaces 迁移）

## 1. 已交付变更

### C3 存储 SPI 扩展点

- 新增 `StorageProviderLoader`（`apex-storage-spring-boot-starter`）：以 `ServiceLoader` 发现第三方 `ObjectStorage` 实现——第三方以独立 JAR 提供实现并在 `META-INF/services/com.apex.scaffold.storage.ObjectStorage` 声明类名即可接入（华为 OBS、Azure Blob 等），**无需修改 starter**
- `StorageAutoConfiguration.storageRegistry` 合并 SPI 后端与内置后端（同 type 以内置优先）
- 受控口径：SPI 加载失败逐条跳过不阻断内置后端；重复 `type()` 仍由 `StorageRegistry` 严格拒绝（不隐式覆盖）；SPI 后端同样受 `storage.default-type` 约束
- 内置五后端仍走 Spring 条件装配（SDK `optional`），SPI 仅用于免改库接入新后端

### C8 事件通道自适应

- 新增 `EventChannelMode`（`AUTO`/`LOCAL`/`REMOTE`）与 `EventChannelProperties`（`apex.events.channel`，默认 `auto`）
- `AUTO` 语义：具备跨服务投递能力（MQ 模板 + Outbox 启用）→ `REMOTE`；否则 → `LOCAL`。**组合部署（C9）自动退化 LOCAL，拆分部署自动 REMOTE，业务代码零感知**
- `LOCAL`/`REMOTE` 支持显式强制覆盖（单进程拓扑、离线验收、故障降级）
- 现状核实：workflow 既有 `MqWorkflowEventPublisher`（`@ConditionalOnBean(RocketMQTemplate)`）+ `LocalWorkflowEventPublisher`（`@ConditionalOnMissingBean`）双实现**本就是 AUTO 语义**；本批将其决策逻辑抽象为共享枚举/配置，供后续各上下文统一采用
- 非法配置值回退 `AUTO`（不因拼写错误阻断启动）

## 2. 测试

| 测试 | 结果 | 覆盖 |
| --- | --- | --- |
| `StorageProviderLoaderTest`（新增） | **4/4** | SPI 与内置后端合并注册、重复 type 严格拒绝、未知类型访问失败、无 SPI 声明时返回空 |
| `EventChannelModeTest`（新增） | **4/4** | 配置解析（大小写/空/非法回退）、AUTO 按部署形态解析、LOCAL/REMOTE 强制语义、属性解析 |
| 四模块及上游 `test`（storage starter / messaging / file-service / workflow-service） | **BUILD SUCCESS** | 见 §3 |

## 3. 结果

- `StorageProviderLoaderTest` 4/4、`EventChannelModeTest` 4/4。
- 四模块（storage-starter / infrastructure-messaging / file-service / workflow-service）及上游 `test`：**BUILD SUCCESS**。
- `spotless:apply` → 全仓 `spotless:check`：**通过**；`git diff --check`：**干净**；doc-links **0 断裂**。

## 4. 未验证项与边界

- **SPI 真实第三方 JAR 接入未实证**：单测覆盖了注册表语义与加载器空结果，但"第三方 JAR + `META-INF/services` 声明 → 被装配"的端到端未跑（需额外构建一个测试 JAR）。列为 C9 拓扑验证项。
- **事件通道未接入各上下文的发布器**：本批交付共享枚举/配置与 AUTO 语义定义，workflow 既有双实现**未改造**为消费 `EventChannelMode`（其 `@ConditionalOnBean/@ConditionalOnMissingBean` 已是等价行为，改造属纯形式统一，收益低风险高）。其它上下文（auth 的 Outbox 发布器、system 的 Outbox）保持既有装配。
- **local/remote 投递语义一致性契约测试未编写**（方案 C8 要求：顺序/至少一次/幂等）——需真实 MQ 环境，列为 C9 或部署验证项。
- **`apex.events.channel` 配置尚未写入各服务 yml**：默认 `auto` 由代码兜底生效，显式配置待 C9 拓扑矩阵时按拓扑注入。
