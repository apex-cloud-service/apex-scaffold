# 补齐报告：C8/C9/D1/D3 与 §13 脚本交付物

- 范围：上轮核查发现的 5 项未完全实现条目 + 偏差项
- 执行日期：2026-11-02
- 状态：**5 项全部补齐，4 份脚本交付**

## 1. C8 事件通道自适应（此前「只有定义、零消费」）

### 问题

核查发现 `EventChannelMode`（AUTO/LOCAL/REMOTE）与 `EventChannelProperties` 只有定义，
**全仓无任何引用**——各服务仍靠 `@ConditionalOnBean(RocketMQTemplate)` 各自决定通道，
抽象的"自适应"语义完全没有落地。

### 补齐

| 变更 | 内容 |
| --- | --- |
| 位置迁移 | `EventChannelMode`/`EventChannelProperties` 从 `infrastructure-messaging` 迁至 **`apex-common`**（纯枚举无依赖）——使不依赖 messaging 的服务（如 workflow-service）也能消费 |
| 真实消费 | `WorkflowEventConfig` 重写：按 `EventChannelMode.resolve(remoteCapable)` 选择发布器；`AUTO` 依据「Outbox 启用 + 存在 `RocketMQTemplate`」判定，`LOCAL`/`REMOTE` 可强制 |
| 空洞配置守卫 | 新增 `workflowRemoteChannelGuard`：`channel=remote` 但缺 MQ 或 Outbox 关闭时**启动即失败**，避免静默退化为本地派发让运维误以为消息已跨服务投递 |

### 验证

`WorkflowEventChannelTest` **8/8 通过**：

| 场景 | 期望 | 结果 |
| --- | --- | --- |
| AUTO + 有 MQ + Outbox 开 | MQ 实现 | ✅ |
| AUTO + 无 MQ | 本地实现（组合拓扑退化） | ✅ |
| AUTO + 有 MQ 但 Outbox 关 | 本地实现 | ✅ |
| LOCAL 强制（有 MQ） | 本地实现 | ✅ |
| REMOTE 强制（有 MQ） | MQ 实现 | ✅ |
| REMOTE 缺 MQ | 启动失败 | ✅ |
| REMOTE + Outbox 关 | 启动失败 | ✅ |
| REMOTE 条件齐备 | 守卫通过 | ✅ |

## 2. D1 SMS / EMAIL 通知通道（此前「只声明未实现」）

### 补齐

- `SmsNotifyChannel` / `EmailNotifyChannel`：以**"发送函数"解耦服务商**——office 不硬依赖 `apex-infrastructure-integration`（保持模块边界），消费方注册 `BiFunction` 或 `MailSender` Bean 即启用
- 含手机号（前 3 后 4）与邮箱（首字符 + 域名）**脱敏日志**、空目标拒绝
- `NotifyChannelConfiguration` 按 `apex.notify.sms.enabled` / `apex.notify.email.enabled` 装配；开启但缺发送函数时**启动失败**（不静默）

## 3. D1 通知门面 Outbox 异步投递（此前「同步直调，无重试」）

`NotifyApplicationService.sendAsync`：

- 先落 `off_notify_request` 留痕（PENDING）并返回留痕 ID，再投递
- 投递失败**不抛出**（交由 Outbox 派发器重试与状态回写），与 `send` 的同步失败即返回形成两种语义
- 开关 `apex.notify.async-outbox`（默认 `false`），关闭时退化为同步投递——**保持既有调用方行为不变**

`NotifyChannelExtensionTest` **5/5 通过**（含短信/邮件投递、门面路由、异步双态）。

## 4. C9 部署拓扑矩阵（此前只有 all-in-one）

`DeploymentTopologyMatrixTest` **4/4 通过**：

| 拓扑 | 验证内容 |
| --- | --- |
| ① 五+服务拆分 | 六个组件根包各自可独立扫描出 Spring Bean |
| ② all-in-one | 全组件合并扫描无 Bean 名冲突 |
| ③ 任意子集 | 抽样 `system+file`、`auth+office`、`workflow+integration` 三组合同工厂装配 |
| ④ 组件入口独立 | 五个组件入口类均无带参构造器（无状态、互不持有） |

## 5. D3 文档修订

| 文档 | 修订 |
| --- | --- |
| `docs/02-architecture/architecture-evolution.md` | 第 1.2 节「不拆，保持模块化单体」→ 加**状态更新块**标注结论已过时，标题改为"历史决策留档"；第四节结论改为"已完成拆分（网关 + 六业务服务）并保留 `apex-boot-all` 组合部署" |
| `DefaultTenantLineHandler` | 注释移出已租户化的 `sys_role`/`sys_role_permission`；开关描述由"仅 Enterprise 默认开启"改为"B7 起四服务默认开启，basic 关闭" |

## 6. §13 脚本交付物（4 份，均通过 `bash -n`）

| 脚本 | 用途 |
| --- | --- |
| `scripts/test/run-dual-database-migration.sh` | 双库迁移演练（system V1–V84 / integration V1–V3，真实 MySQL 9 + PG 15 容器；失败即非零退出） |
| `scripts/deploy/assemble-topology.sh` | 三拓扑装配：`split` / `all-in-one` / `subset`（`APEX_COMPONENTS=system,file`），**未选组件显式 `enabled=false`** |
| `scripts/test/run-mqtt-integration.sh` | EMQX 联调：`plain`（基线与冒烟）/ `acl`（HTTP 授权钩子）；自动拉起与清理容器 |
| `scripts/test/new-batch-report.sh` | 批次报告骨架生成（预填章节，**不预填任何未验证结论**）；已试用生成后清理 |

全部脚本复用 `scripts/lib/common.sh`（`get_project_root`/`log_*`），并 `source scripts/build/init-build-env.sh` 校验构建环境，失败即退出。

## 7. 门禁结果

| 项 | 结果 |
| --- | --- |
| `WorkflowEventChannelTest` | **8/8** |
| `NotifyChannelExtensionTest` + `NotifyFacadeTest` | **10/10** |
| `DeploymentTopologyMatrixTest` + `BootAllAssemblyTest` | **8/8** |
| 五模块及上游 `test` | **BUILD SUCCESS** |
| 脚本 `bash -n` | 4/4 通过 |
| `git diff --check` / doc-links | 干净 / 0 断裂 |

## 8. 未验证项（如实保留）

- **C8 的 REMOTE 路径未做真实 MQ 端到端**：仅验证了 Bean 选择逻辑与守卫；真实 RocketMQ 投递未跑（需 broker）。
- **SMS/EMAIL 通道未对接真实服务商**：发送函数为注入点，测试用桩；真实创蓝/邮件服务器联调未做。
- **`sendAsync` 的 Outbox 派发未做端到端**：留痕与开关逻辑已验证，真实 Outbox 派发重试链路需部署环境。
- **拓扑脚本未做真实启动验证**：`assemble-topology.sh` 的 split 拓扑需外部 MySQL/Redis；all-in-one/subset 的启动冒烟未跑（仅业务层装配测试）。
- **前端页面**：按用户要求排除。
