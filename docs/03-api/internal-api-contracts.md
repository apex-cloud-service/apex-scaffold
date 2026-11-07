# 内部 API 契约清单（/internal/v1/**）

本清单登记跨服务内部 HTTP 契约的提供方、消费方与契约载体，作为防漂移基线（2026-11-07 模块架构评审 F3 引入）。

## 1. 通用口径

- 内部端点统一挂 `/internal/v1/**` 前缀，**不经网关**（网关对该前缀强制 403），仅集群内服务直连；各服务安全链对该前缀按内部认证放行。
- 响应统一为 `Result<T>` 封装（`code`/`msg`/`data`/`traceId`，`ResultCode.SUCCESS` 为 0）；调用方经 `apex-common` 的 `InternalAuthExchangeFilter` 注入内部认证与签名（`InternalAuthProperties` / `InternalServiceSigner`）。
- "资源不存在"类查询以数据化结果返回（HTTP 200，如 `exists=false`），便于消费方缓存负结果；基础设施故障走 fail-closed，由消费方按业务语义降级。
- 跨服务请求/响应 DTO 优先放入 `apex-api-contract`（`com.apex.scaffold.contract.dto.internal`）；消费方自定义 ACL DTO 属合法防腐层形态，但必须在本清单登记，变更提供方字段时先核对消费方。
- **新增或变更内部端点必须同步本清单**；契约 DTO 变更需两端编译与契约测试通过。

## 2. 端点清单

| 提供方 | 端点 | 消费方 | 契约载体 |
| --- | --- | --- | --- |
| system | `GET /internal/v1/tenants/{code}/status` | auth-service（`RemoteTenantStatusAdapter` 登录前置租户校验） | `apex-api-contract` 的 `TenantStatusSnapshotDTO`（2026-11-07 契约化） |
| system | `GET /internal/v1/tenants/{code}/capabilities` | 网关开放接口能力门禁（D4） | 提供方内部类型 `TenantPackageService.CapabilitySnapshot` |
| system | `POST /internal/v1/org/users/resolve` | office-service（`SystemOrgUserQueryAdapter` 组织/用户查询） | 消费端 ACL DTO（`OfficeOrgUserQueryPort` / `OfficeOrgUserSnapshot`） |
| system | `GET /internal/v1/overview` | 平台总览聚合（`InternalOverviewHandler` 并行拉取各域 stats 后聚合；`PlatformOverviewHandler` 共用同一门面） | 提供方内部类型 `OverviewResponse`；各域 stats 响应字段名以本清单约束 |
| system | `GET /internal/v1/open-apps/{clientId}/rate-limit-tier` | 网关开放接口按应用分档限流（D2） | 提供方内部类型 |
| system | `/internal/v1/interests/**` | 仓内未见跨服务消费方（system 用户画像兴趣路径，安全链特判） | system 自有路径 |
| auth | `POST /internal/v1/tokens/validate` | 仓内未见调用方（预留内部契约；system 本地 JWT 验签，不经此端点） | `apex-api-contract` 的 `TokenValidationRequest` / `TokenValidationDTO` |
| auth | `POST /internal/v1/tokens/revocations` | 仓内未见调用方（预留内部契约） | 提供方契约接口 |
| auth | `GET /internal/v1/users/{accountId}/permissions` | 仓内未见调用方（预留内部契约；system 统一授权实走 `/auth/v1/authorization/users/{id}` 公开契约，见 `AuthCenterPermissionClient`） | 提供方契约接口 |
| auth | `POST /internal/v1/open-apps`、`/open-apps/import`、`/open-apps/{clientId}/status`、`/open-apps/{clientId}/reset-secret` | system（`AuthOpenAppRegistryRemoteAdapter` 开放应用注册中心管理） | `apex-api-contract` 的 `OpenAppRegistrationRequest/Response`、`OpenAppImportRequest`、`OpenAppStatusRequest` |
| office | `POST /internal/v1/notify` | 仓内未定位具体调用方（按提供方 javadoc：集群内服务统一消息门面按模板投递） | 提供方契约接口 |
| office | `GET /internal/v1/office/messages/stats` | system（`/internal/v1/overview` 聚合） | 只读统计，字段名勿改 |
| file | `GET /internal/v1/file/stats` | system（`/internal/v1/overview` 聚合） | 只读统计，字段名勿改（`FileInternalHandler`） |
| workflow | `GET /internal/v1/workflow/stats` | system（`/internal/v1/overview` 聚合） | 只读统计，字段名勿改（`WorkflowInternalHandler`） |
| integration | `GET /internal/v1/iot/devices/stats` | system（`/internal/v1/overview` 聚合） | 只读统计 |
| integration | `POST /internal/v1/iot/devices/{key}/commands` | 集群内服务（设备命令下发代理） | 提供方契约接口 |
| integration | `POST /internal/v1/iot/mqtt/auth`、`POST /internal/v1/iot/mqtt/acl` | 外部 EMQX（MQTT 接入认证/ACL 钩子回调） | EMQX HTTP 钩子载荷 |

## 3. 事件契约（apex-api-contract，MQ 通道）

| 事件 | 发布方 | 消费方现状（2026-11-07 核实） |
| --- | --- | --- |
| `UserChangedEvent` | system（用户主数据变更） | system 内 `UserMessageListener` 消费用户 Topic（消费端自有载荷解析，未引用契约类型） |
| `TenantChangedEvent` | system（租户生命周期/配额变更） | auth-service `TenantChangedEventListener` 订阅失效租户判定缓存（消费端 JSON 解析，未引用契约类型） |
| `WorkflowInstanceStartedEvent` / `TaskAssignedEvent` / `TaskCompletedEvent` | workflow-service（Outbox + MQ 类型化发布） | 仓内暂无消费方接入；后续消费必须经同一契约类型，禁止同步回调 |

说明：契约事件当前为"发布方类型化、消费方自解析"状态。新增消费方时应直接引用契约事件类型；存量消费方如改造为类型化消费，随对应批次实施并在本表更新。
