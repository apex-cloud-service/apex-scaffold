# 开放平台接入契约（M4 / D2 + D6）

本文件描述第三方与前台应用接入中台能力中心的完整流程。实现基线：认证中心 `OpenAppScopes` scope 注册表、`/admin-api/v1/open-apps` 管理端点、认证中心 OAuth2 授权服务器。

## 1. 接入流程

```mermaid
sequenceDiagram
    participant App as 第三方应用
    participant Admin as 平台管理员
    participant Auth as 认证中心
    participant GW as 网关 /open-api

    Admin->>Auth: 注册开放应用（clientId + scopes）
    Admin->>App: 交付 clientId / clientSecret
    App->>Auth: 授权码 + PKCE 或 client_credentials
    Auth-->>App: access_token（含 scope）
    App->>GW: 携带 Bearer 调用 /open-api/**
    GW->>GW: 验签 + scope 断言 + client 限流
    GW-->>App: 业务响应
```

## 2. 应用注册

- 应用复用认证中心 `OAUTH2_CLIENT` 注册表（`auth_config`，租户级），凭据签发/轮换沿用既有管理端点
- 注册时声明：`appName`、`description`、`scopes`、`rateLimitTier`、`redirectUris`
- 声明 scope 必须来自平台目录（`GET /admin-api/v1/open-apps/scopes`），非法 scope 在详情接口的 `invalidScopes` 中回报

## 3. scope 目录与权限映射

| scope | 服务侧权限码 | 能力 |
| --- | --- | --- |
| `open:file:read` | `file:read` | 文件读取 |
| `open:file:write` | `file:write` | 文件写入 |
| `open:workflow:read` | `workflow:read` | 工作流读取 |
| `open:workflow:write` | `workflow:write` | 工作流发起 |
| `open:user:read` | `user:read` | 用户只读 |
| `open:message:send` | `office:message:send` | 站内信发送 |

- scope 收窄遵循 RFC 6749 §3.3：请求 scope 必须是应用已授权 scope 的子集（`OpenAppScopes.covers`）
- 新增能力 = 在 `OpenAppScopes` 目录登记 + 在能力目录（`CapabilityCatalog`）登记对应能力编码

## 4. 调用约定

- 网关开放路由组：`/open-api/**`（**不接受用户个人令牌**，仅接受开放应用令牌）
- 令牌类型：`client_credentials`（服务间调用）或授权码 + PKCE（代用户访问）
- 限流：按 `clientId` 分档（`rateLimitTier`），与租户维度限流叠加
- 审计：开放访问写入访问日志（含 client 维度）
- 错误：统一 `Result<T>` 包装；scope 不足返回 403，令牌无效/过期返回 401

## 5. 事件订阅（可选）

- 工作流事件契约见 `apex-api-contract` 的 `contract/event/**`（`WorkflowInstanceStartedEvent`、`TaskAssignedEvent`、`TaskCompletedEvent`）
- 订阅方式：由部署侧配置 MQ 消费方，事件经 Outbox 可靠投递

## 6. 与套餐/能力订阅的关系

- 应用声明的 scope 属于**技术授权范围**；租户实际可用的能力由套餐 `features` 决定（`GET /api/v2/capabilities/subscribed`）
- 两者同时生效：scope 允许 + 租户已订阅能力，调用才成功
- 套餐未订阅某能力时，即使应用持有对应 scope 也应被拒绝（网关按路由→能力映射校验）

## 7. 未实现项（如实声明）

- `/open-api/**` 网关路由组、scope 断言过滤器与 client 限流档：**尚未实现**（本文件为契约基线，网关侧实现列入后续批次）
- 自定义 HMAC 请求签名：明确不做（Bearer 令牌已满足 v1）
- 开放平台计费：不做
