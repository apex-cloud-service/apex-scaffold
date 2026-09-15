# 前端 API 更新指南

## 概述

前端项目 `/Users/work/apex-admin-web/src/api/office.ts` 已存在，但需要根据当前后端实际实现进行调整。

## 当前后端已实现的接口

### 公开接口（无需认证）

1. **GET /api/v1/office/status** - 查询平台状态
   - 返回：`string[]` 平台列表（如 `["FEISHU", "DINGTALK", "WECOM"]`）
   - 用途：登录页判断可用的办公平台登录方式

2. **GET /api/v1/office/login** - 生成授权地址
   - 查询参数：`platform` (OfficePlatform)
   - 返回：授权 URL 字符串
   - 用途：获取平台授权地址，前端跳转

3. **GET /api/v1/office/callback** - OAuth 回调处理
   - 查询参数：`platform`, `code`, `state`
   - 返回：`LoginResult`（包含 accessToken 和 refreshToken）
   - 用途：平台授权后回调，换取 Apex 登录态

### 管理接口（需要管理员权限）

#### 应用配置管理

1. **GET /admin-api/v1/office/apps** - 查询应用列表
   - 返回：`List<OfficeAppResponse>`
   - 权限：`office:app:list`

2. **GET /admin-api/v1/office/apps/{platform}** - 查询应用详情
   - 路径参数：`platform`
   - 返回：`OfficeAppResponse`
   - 权限：`office:app:get`

3. **POST /admin-api/v1/office/apps/{platform}** - 创建应用配置
   - 路径参数：`platform`
   - 请求体：`OfficeAppRequest`
   - 权限：`office:app:create`
   - 审计：是，幂等：是

4. **PUT /admin-api/v1/office/apps/{platform}** - 更新应用配置
   - 路径参数：`platform`
   - 查询参数：`version` (乐观锁版本号)
   - 请求体：`OfficeAppRequest`
   - 权限：`office:app:update`
   - 审计：是，幂等：是

5. **DELETE /admin-api/v1/office/apps/{platform}** - 删除应用配置
   - 路径参数：`platform`
   - 查询参数：`version` (乐观锁版本号)
   - 权限：`office:app:delete`
   - 审计：是，幂等：是

#### 绑定审批管理

6. **GET /admin-api/v1/office/bindings** - 查询绑定列表
   - 查询参数：`platform`, `page`, `size`
   - 返回：`List<OfficeBindingResponse>`
   - 权限：`office:binding:list`

7. **POST /admin-api/v1/office/bindings/{id}/review** - 审批绑定
   - 路径参数：`id`
   - 请求体：`OfficeBindingReviewRequest` (包含 approved 和 version)
   - 权限：`office:binding:review`
   - 审计：是，幂等：是

8. **POST /admin-api/v1/office/bindings/{id}/unbind** - 解绑身份
   - 路径参数：`id`
   - 查询参数：`version` (乐观锁版本号)
   - 权限：`office:binding:unbind`
   - 审计：是，幂等：是

## 前端需要调整的内容

### 1. 类型定义调整

#### OfficeAppResponse (后端实际返回)

```typescript
/** 办公平台应用配置响应 */
export interface OfficeAppResponse {
  /** 配置 ID */
  id: number
  /** 平台类型 */
  platform: OfficePlatform
  /** 应用 ID */
  appId: string
  /** 回调地址 */
  redirectUri: string
  /** 是否启用 */
  enabled: boolean
  /** 乐观锁版本号 */
  version: number
}
```

#### OfficeAppRequest (后端接受的请求)

```typescript
/** 办公平台应用配置请求 */
export interface OfficeAppRequest {
  /** 应用 ID（必填） */
  appId: string
  /** 应用密钥环境变量引用（必填，如 FEISHU_SECRET） */
  secretReference: string
  /** 回调地址（必填） */
  redirectUri: string
  /** 签名密钥环境变量引用（可选） */
  signingSecretReference?: string
  /** 是否启用（必填） */
  enabled: boolean
}
```

#### OfficeBindingResponse (后端实际返回)

```typescript
/** 办公平台绑定响应 */
export interface OfficeBindingResponse {
  /** 绑定 ID */
  id: number
  /** 用户 ID */
  userId: number
  /** 平台类型 */
  platform: OfficePlatform
  /** 外部用户标识 */
  externalUserId: string
  /** 审批状态：PENDING, APPROVED, REJECTED, REVOKED */
  status: string
  /** 是否启用 */
  enabled: boolean
  /** 乐观锁版本号 */
  version: number
}
```

#### OfficeBindingReviewRequest (后端接受的请求)

```typescript
/** 绑定审批请求 */
export interface OfficeBindingReviewRequest {
  /** 是否批准（true=批准，false=拒绝） */
  approved: boolean
  /** 乐观锁版本号 */
  version: number
}
```

### 2. API 方法调整

#### 平台状态查询

```typescript
/**
 * 查询全部办公平台启用状态。
 * 公开接口，无需登录态。
 * @returns 平台类型字符串数组
 */
export function getPlatformStatus(): Promise<OfficePlatform[]> {
  return get<OfficePlatform[]>('/api/v1/office/status')
}
```

#### 发起办公平台登录

```typescript
/**
 * 发起办公平台登录（获取平台授权 URL）。
 * 公开接口，登录页调用。
 * @param platform 平台类型
 * @returns 授权 URL
 */
export function startOfficeLogin(platform: OfficePlatform): Promise<string> {
  return get<string>(`/api/v1/office/login`, { platform })
}
```

#### 办公平台授权回调

```typescript
/**
 * 办公平台授权回调处理。
 * 公开接口，无需登录态。
 * @param platform 平台类型
 * @param code 授权码
 * @param state 防 CSRF 状态码
 * @returns 登录结果（包含 accessToken 和 refreshToken）
 */
export function officeCallback(
  platform: OfficePlatform,
  code: string,
  state: string,
): Promise<LoginResult> {
  return get<LoginResult>(`/api/v1/office/callback`, { platform, code, state })
}
```

#### 查询应用列表

```typescript
/**
 * 查询当前租户的所有办公平台应用配置列表。
 * @returns 应用配置列表
 */
export function listApps(): Promise<OfficeAppResponse[]> {
  return get<OfficeAppResponse[]>('/admin-api/v1/office/apps')
}
```

#### 查询应用详情

```typescript
/**
 * 查询指定平台的应用配置。
 * @param platform 平台类型
 * @returns 应用配置
 */
export function getApp(platform: OfficePlatform): Promise<OfficeAppResponse> {
  return get<OfficeAppResponse>(`/admin-api/v1/office/apps/${platform}`)
}
```

#### 创建应用配置

```typescript
/**
 * 创建办公平台应用配置。
 * @param platform 平台类型
 * @param request 应用配置请求
 * @returns 创建的应用配置
 */
export function createApp(
  platform: OfficePlatform,
  request: OfficeAppRequest,
): Promise<OfficeAppResponse> {
  return post<OfficeAppResponse>(`/admin-api/v1/office/apps/${platform}`, request)
}
```

#### 更新应用配置

```typescript
/**
 * 更新办公平台应用配置。
 * @param platform 平台类型
 * @param version 乐观锁版本号
 * @param request 应用配置请求
 * @returns 更新后的应用配置
 */
export function updateApp(
  platform: OfficePlatform,
  version: number,
  request: OfficeAppRequest,
): Promise<OfficeAppResponse> {
  return put<OfficeAppResponse>(`/admin-api/v1/office/apps/${platform}?version=${version}`, request)
}
```

#### 删除应用配置

```typescript
/**
 * 删除办公平台应用配置。
 * @param platform 平台类型
 * @param version 乐观锁版本号
 * @returns 是否删除成功
 */
export function deleteApp(platform: OfficePlatform, version: number): Promise<boolean> {
  return del<boolean>(`/admin-api/v1/office/apps/${platform}?version=${version}`)
}
```

#### 查询绑定列表

```typescript
/**
 * 分页查询账号绑定列表。
 * @param platform 平台筛选
 * @param page 页码（从 1 开始）
 * @param size 每页大小
 * @returns 绑定列表
 */
export function listBindings(
  platform: OfficePlatform,
  page: number,
  size: number,
): Promise<OfficeBindingResponse[]> {
  return get<OfficeBindingResponse[]>('/admin-api/v1/office/bindings', { platform, page, size })
}
```

#### 审批绑定

```typescript
/**
 * 审批绑定申请（通过或拒绝）。
 * @param id 绑定记录 ID
 * @param request 审批请求（包含 approved 和 version）
 * @returns 是否审批成功
 */
export function reviewBinding(id: number, request: OfficeBindingReviewRequest): Promise<boolean> {
  return post<boolean>(`/admin-api/v1/office/bindings/${id}/review`, request)
}
```

#### 解绑身份

```typescript
/**
 * 解除绑定。
 * @param id 绑定记录 ID
 * @param version 乐观锁版本号
 * @returns 是否解绑成功
 */
export function unbindBinding(id: number, version: number): Promise<boolean> {
  return post<boolean>(`/admin-api/v1/office/bindings/${id}/unbind?version=${version}`)
}
```

## 尚未实现的接口（前端可先保留，后续实现）

以下接口前端 API 文件中已定义，但后端尚未实现，前端可以先保留这些定义：

- 组织通讯录同步相关接口
- 消息发送接口
- 审批列表查询接口
- 日历日程查询接口
- 文件列表查询接口

这些接口将在后续迭代中实现。

## 前端更新步骤

1. 备份当前 `/Users/work/apex-admin-web/src/api/office.ts`
2. 根据本文档更新类型定义和 API 方法
3. 暂时注释掉未实现的接口调用
4. 更新相关 Vue 组件中的 API 调用
5. 运行 `npm run build` 验证编译通过
