# Apex Scaffold API 规范

**版本**: v1.0  
**适用版本**: Apex Scaffold v1.2.0  
**定位**: 对接方开发规范；所有约定均已在代码中实现并有契约测试保障（RouterOpenApiCoverageTest 等）。

---

## 1. 接口分组

| 分组 | 前缀 | 鉴权 | 用途 |
|------|------|------|------|
| 业务 API | `/api/v1/**` | Bearer Token（部分公开） | 前台业务接口 |
| 管理 API | `/admin-api/v1/**` | Bearer Token + `@RequirePermission` | 管理后台接口 |
| 健康检查 | `/api/v1/health`、`/actuator/health/*` | 无 | 探针/监控 |

## 2. 统一响应结构

所有 JSON 接口返回统一 `Result` 结构（HTTP 状态码 + 业务码双层语义）：

```json
{
  "code": 0,
  "msg": "成功",
  "data": { },
  "traceId": "a1b2c3..."
}
```

| 字段 | 说明 |
|------|------|
| `code` | 业务错误码：`0` = 成功；`4001` 用户不存在、`2001` 限流、`2006` 请求超时等 |
| `msg` | 提示信息（i18n，支持 zh/en/zh-TW/th） |
| `data` | 业务数据（成功时） |
| `traceId` | 链路追踪 ID，随日志贯穿，排查问题必需 |

> 错误响应：HTTP 语义 + 业务码并存。例：`USER_NOT_FOUND` → HTTP 404 + code 4001；
> 参数校验失败 → HTTP 400 + 具体业务码；限流 → HTTP 429 + 2001；熔断 → HTTP 503 + 2002/2003。

## 3. 鉴权约定

- 请求头：`Authorization: Bearer <accessToken>`
- Token 生命周期：Access Token 默认 2h（可配）+ Refresh Token 默认 7d（可配）
- 无感续期：剩余有效期低于阈值（默认 30min）且携带有效 Refresh Token 时，响应头自动签发新 Token
- 会话撤销：登出/管理端强退即时失效（Redis 黑名单）

## 4. 分页约定

统一分页模型（列表接口均适用）：

```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "total": 100,
    "list": [ ],
    "page": 1,
    "size": 20
  }
}
```

- 查询参数：`page`（从 1 开始）、`size`（默认 20，上限 100）
- 后端执行 COUNT + LIMIT 两次查询，保证 total 精确

## 5. 幂等约定

- 写接口（创建/扣减等副作用操作）建议携带 `Idempotency-Key` 请求头（客户端生成的 UUID）
- 相同键在 TTL 窗口（默认 600s，可注解级配置）内重复请求返回 `REPEAT_SUBMIT`，业务不重复执行
- 声明方式：`@IdempotentApi(header = "Idempotency-Key", expireTime = 60)`

## 6. 参数校验约定

- 基于 Jakarta Validation 注解声明（`@NotBlank`/`@Size`/`@Pattern` 等）
- 校验失败返回 HTTP 400 + 统一业务码 + i18n 消息
- 请求体为空返回 `PARAM_ERROR`

## 7. 接口版本管理

- 采用 URL 路径版本：`/api/v1/**`；新增不兼容变更时升级路径段（`/api/v2/**`）
- 迁移期间 v1 与 v2 并存；文档见 docs/API_VERSION_MANAGEMENT.md

## 8. 错误码段位

| 段位 | 含义 | 示例 |
|------|------|------|
| 0 | 成功 | 0 |
| 1000-1999 | 通用/参数 | 1001 参数无效 |
| 2000-2999 | 流量治理 | 2001 限流、2002 熔断、2006 请求超时 |
| 3000-3999 | 系统/依赖 | 3001 系统内部错误 |
| 4000-4999 | 业务 | 4001 用户不存在、4002 数据冲突 |

## 9. 在线文档

- 开发环境：Knife4j 增强 UI `/doc.html`（OpenAPI 3.0 自动生成，含示例与分组）
- 分组：admin-system / admin-user / 业务模块组；`@RouterOperation` 声明与路由一一对应（契约测试保障不漂移）

## 10. 兼容性承诺

- 已发布 v1 前缀接口不破坏性变更；变更需新增版本段
- OpenAPI 契约由 `RouterOpenApiCoverageTest` 端到端守护（路由 ↔ 文档不漂移）
