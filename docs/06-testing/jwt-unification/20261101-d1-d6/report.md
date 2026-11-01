# 批次 D1 + D2 + D4 + D6 测试报告：中台能力层（消息门面 / 开放平台 / 能力目录）

- 批次：方案 [unified-auth-tenant-platform-remediation-plan.md](../../../08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md) 批次 D1/D2/D4/D6（M4 中台）
- 执行日期：2026-11-01
- 环境：macOS、JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`（构建环境校验通过）
- 状态：**实施完成，门禁结果见 §4**；D5（轻量数据服务层）与 M5（集成与 IoT）待后续

## 1. 已交付变更

### D1 统一消息门面（apex-office）

| 组件 | 说明 |
| --- | --- |
| `NotifyChannelPort` | 通道扩展点：站内信/IM/短信/邮件统一投递契约（新增通道 = 实现接口 + 条件装配） |
| `NotifyTemplateRenderer` | 占位符渲染 `${key}`，**缺失参数原样保留**（不静默输出空串），无表达式执行、无注入面 |
| `NotifyApplicationService` | 门面：模板渲染 → 通道路由 → 失败收敛为结果；支持单通道与多通道扇出（逐通道独立成败） |
| `InboxNotifyChannel` | 站内信通道（复用既有 `OffMessageService` 落库路径，避免双写） |
| `ImNotifyChannel` + `NotifyChannelConfiguration` | FEISHU/DINGTALK/WECOM 三通道，对接既有 `OfficePlatformMessageClient` SPI；客户端未装配时返回失败（不静默成功） |

**边界说明**：office 不直接依赖 `apex-infrastructure-integration`（保持既有 POM 边界），短信/邮件通道由扩展模块实现 `NotifyChannelPort` 接入——已在类注释与报告注明。

### D2 开放平台（apex-auth / apex-auth-service）

| 组件 | 说明 |
| --- | --- |
| `OpenAppScopes` | 平台 scope 注册表：6 个开放 scope（file/workflow/user/message 的读写）+ scope→服务侧权限码映射 + `covers` 收窄校验（RFC 6749 §3.3） |
| `OpenAppHandler` + `OpenAppRouter` | `/admin-api/v1/open-apps`：应用列表、详情（含 `invalidScopes` 合法性回报）、平台 scope 目录 |

**复用设计**：开放应用复用认证中心 `OAUTH2_CLIENT` 注册表（`auth_config`，租户级），凭据签发/轮换沿用既有端点，不新建第二套客户端体系。

### D4 能力目录与订阅（apex-system）

| 组件 | 说明 |
| --- | --- |
| `CapabilityCatalog` | 8 项能力登记（认证授权/用户组织/工作流/文件/消息/租户治理/集成连接器/IoT），含归属中心、依赖组件、开放 scope、说明——**中台能力运营的代码侧单一数据源** |
| `CapabilityHandler` + `CapabilityRouter` | `/api/v2/capabilities`（目录）与 `/api/v2/capabilities/subscribed`（当前租户已订阅） |
| `PackageScope.features` + `allowsCapability` | 订阅判定复用套餐 `features`（B3）：空 = 全量订阅，否则精确匹配 |

**修正记录**：初版 `subscribedByPackage` 恒返回 true（占位实现），已改为真实读取套餐 features——避免"看起来能用但实际不裁剪"的伪实现。

### D6 接入契约文档

新增 [开放平台接入契约](../../03-api/open-platform-integration.md)：接入流程图、应用注册、scope 目录与权限映射表、调用约定、事件订阅、与套餐订阅的关系，并**如实声明** `/open-api/**` 网关路由与 scope 断言尚未实现。

## 2. 测试

| 测试 | 结果 | 覆盖 |
| --- | --- | --- |
| `NotifyFacadeTest`（新增） | **5/5** | 模板渲染（替换/缺失保留/参数提取）、单通道投递、未知通道与空目标拒绝、通道失败收敛、多通道扇出独立成败 |
| `CapabilityCatalogTest`（新增） | **5/5** | 目录完整性（8 项、编码唯一、七中心齐备）、开放/内部能力 scope 区分、未登记编码识别、订阅语义（空=全量）、`PackageScope.allowsCapability` 一致性 |
| `TenantPackageServiceTest`（同步） | 6/6 | `PackageScope` 新增 features 参数后既有用例语义保持 |
| 五模块及上游 `test` | **BUILD SUCCESS** | auth/auth-service/system/office/gateway |

## 3. 过程缺陷与修复记录

1. `MarkdownMessageContent` 无全参构造 → 改 builder（`markdown`/`title`）。
2. `findEnabledByType(configType, tenantId)` 参数顺序写反 → 修正。
3. `PackageScope` 扩展 features 后既有测试构造失败 → 补第 4 参。
4. D4 占位实现（`subscribedByPackage` 恒 true）→ 改为真实 features 判定。

## 4. 未验证项与边界

- **`/open-api/**` 网关路由组、scope 断言过滤器、client 限流档未实现**（D2 只交付注册表、管理接口与契约文档）——这是开放平台从"可注册"到"可调用"的关键缺口，需在网关侧补路由与过滤器。
- **D5 轻量数据服务层未实施**（主数据事件分发 + 跨域只读聚合门面）。
- **短信/邮件通知通道未实现**（需扩展模块实现 `NotifyChannelPort`，避免 office 反向依赖 integration）。
- **通知模板/请求持久化表未建**：复用既有 `off_message_template`/`off_message_delivery`，但门面自身未新增 `off_notify_template`/`off_notify_request`（方案原设）；当前模板由调用方直接传入正文，模板库管理待补。
- **消息门面未接 Outbox 异步投递**：当前为同步调用通道适配器，失败即返回结果；方案要求的 Outbox 重试与 DLQ 未接入。
- **能力计量未实现**（方案 D4 的按租户×能力用量统计）。
