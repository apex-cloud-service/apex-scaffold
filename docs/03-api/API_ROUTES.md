# API 路由清单

> 本文档记录项目当前实际注册的函数式 WebFlux API 路由。
>
> 路由以 `RouterFunction`、`RouterFunctions.route()`、`RouterFunctions.nest()` 和 `SpringdocRouteBuilder` 的可执行注册代码为准。`RouterOpenApiConfig` 中仅用于 SpringDoc 展示的占位路由不视为独立业务实现。
>
> 项目包含两个独立的 Spring Boot 运行边界：主应用和认证中心。两者的路由、Handler、应用服务及数据源应分别理解和部署。

## 1. 路径与认证约定

| 路径前缀 | 运行边界 | 典型用途 | 认证约定 |
| --- | --- | --- | --- |
| `/api/v1/**` | 主应用 | 用户业务、办公集成、消息和工作流接口 | 默认需要认证；公开端点以安全配置和路由说明为准 |
| `/admin-api/v1/**` | 主应用 | 管理后台和运维接口 | 需要认证；启用 RBAC 时还需命中 `securityProperties.adminRoles` 中的任一角色，具体权限以 Handler 注解为准 |
| `/api/office/**` | 主应用 | 办公平台能力接口 | 默认需要认证 |
| `/auth/sso/**` | 主应用 | SSO 登录、回调和登出 | 全部需要认证；当前公开白名单未放行这些路径 |
| `/oauth2/**`、`/.well-known/**` | 主应用 | OAuth2 授权服务器标准端点 | 仅 `token`、`revoke`、`introspect` 在 Web 安全层公开，其余已注册端点需要认证 |
| `/api/v1/auth/**` | 主应用 | 认证、授权及 Keycloak/OIDC 登录接口 | 默认需要认证；Keycloak 的三个登录/回调端点公开且仅在 `keycloak.enabled=true` 时装配 |
| `/api/mfa/**`、`/api/ip-access-control/**` | 认证中心 | MFA 管理和 IP 访问控制 | 需要 Bearer ACCESS 访问令牌 |
| `/api/v1/mfa/**`、`/auth/v1/**` | 认证中心 | MFA、登录、令牌、角色、权限和会话 | 仅 `/auth/v1/login`、`/auth/v1/refresh` 公开，其余需要 Bearer ACCESS 访问令牌 |

主应用认证约定以 `SecurityConfig` 和 `SystemConstants.PUBLIC_PATHS` 为准。本文业务路由中的公开端点为：`GET /`、`GET /api/v1/health`；`POST /api/v1/auth/login`、`register`、`wechat/bind-phone`、`refresh`、`sms/code`、`cl/sms/code`、`cl/validate-mobile`（后六项均拼接 `/api/v1/auth/` 前缀）；`GET /api/v1/auth/wechat/mp/authorize`（公众号网页授权跳转入口）；`GET /api/v1/auth/captcha`、`POST /api/v1/auth/captcha/verify`；`GET /api/v1/office/login`、`GET /api/v1/office/callback`；`POST /oauth2/token`、`POST /oauth2/revoke`、`POST /oauth2/introspect`；以及 Keycloak 的三个登录/回调端点。`/api/v1/info`、`/api/v1/office/status`、`/api/v1/auth/wx/phone`、`/api/v1/auth/logout` 均需要认证。白名单还包含框架或静态资源路径，不能据此推断存在业务 Router。

管理端在 `rbacEnabled=true` 时按配置角色授权，关闭时 Web 安全层仅要求认证。`securityProperties.adminPaths` 命中的其他管理路径在启用 RBAC 时要求配置的 `adminRole` 或 `SUPER_ADMIN`；`/api/v1/auth/roles/my` 和 `/api/v1/auth/permissions/my` 优先按普通已认证用户放行。Handler 的权限注解仍需独立核对。

认证中心仅放行登录和刷新，其余本文端点均要求 `Authorization: Bearer <access-token>`，且令牌类型必须为 `ACCESS`。两模块各有同名 `AuthConfigRouter` / `AuthConfigHandler`：主应用注册 `/admin-api/v1/auth/config/**`，认证中心注册 `/api/v1/auth/configs/**`，不能合并。

统一响应、错误码和异常出口不由路由表决定。普通业务异常进入统一 WebFlux 异常处理边界，认证失败和权限不足分别由认证入口点与拒绝处理器返回，流控/降级异常由 Sentinel 异常处理器处理。

## 2. 主应用路由

### 2.1 用户、办公登录和 Keycloak

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/user/info` | `ApiRouter` | `UserHandler.getCurrentUserInfo` | 获取当前用户信息 |
| POST | `/api/v1/user/me/deletion-code` | `ApiRouter` | `UserHandler.sendAccountDeletionCode` | 发送账户注销验证码 |
| DELETE | `/api/v1/user/me` | `ApiRouter` | `UserHandler.deleteCurrentUser` | 注销当前用户账户 |
| GET | `/api/v1/user/{userId}` | `ApiRouter` | `UserHandler.getUserById` | 根据用户 ID 查询用户信息 |
| POST | `/api/v1/user/list` | `ApiRouter` | `UserHandler.listUsers` | 分页查询用户列表 |
| GET | `/api/v1/office/status` | `ApiRouter` | `OfficeHandler.status` | 查询办公平台认证状态 |
| GET | `/api/v1/office/login` | `ApiRouter` | `OfficeHandler.start` | 发起办公平台登录 |
| GET | `/api/v1/office/callback` | `ApiRouter` | `OfficeHandler.callback` | 处理办公平台登录回调 |
| GET | `/auth/sso/initiate` | `SsoAuthRouter` | `SsoAuthHandler.initiate` | 发起 SSO 认证，需要认证 |
| POST | `/auth/sso/callback` | `SsoAuthRouter` | `SsoAuthHandler.callback` | 处理 SSO 回调，需要认证 |
| POST | `/auth/sso/logout` | `SsoAuthRouter` | `SsoAuthHandler.logout` | SSO 登出，需要认证 |
| GET | `/api/v1/auth/oidc/login` | `KeycloakRouterConfig` | `SocialLoginHandler.initiateOidcLogin` | 发起 Keycloak OIDC 登录；可选装配 |
| GET | `/api/v1/auth/social/{provider}/login` | `KeycloakRouterConfig` | `SocialLoginHandler.initiateSocialLogin` | 发起第三方社交登录；可选装配 |
| GET | `/api/v1/auth/oidc/callback` | `KeycloakRouterConfig` | `SocialLoginHandler.handleOidcCallback` | 处理 OIDC 回调；可选装配 |
| POST | `/admin-api/v1/keycloak/sync/all` | `KeycloakRouterConfig` | `KeycloakAdminHandler.syncAllUsers` | 全量同步 Keycloak 用户；可选装配 |
| POST | `/admin-api/v1/keycloak/sync/user/{keycloakSub}` | `KeycloakRouterConfig` | `KeycloakAdminHandler.syncUser` | 同步指定 Keycloak 用户；可选装配 |
| GET | `/admin-api/v1/keycloak/status` | `KeycloakRouterConfig` | `KeycloakAdminHandler.getKeycloakStatus` | 查询 Keycloak 集成状态；可选装配 |

### 2.2 管理后台基础资源

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/admin-api/v1/staff-roles` | `ApiRouter` | `AdminUserManagementHandler.roles` | 查询员工角色 |
| GET | `/admin-api/v1/users` | `ApiRouter` | `AdminUserManagementHandler.list` | 查询后台用户 |
| POST | `/admin-api/v1/users` | `ApiRouter` | `AdminUserManagementHandler.create` | 创建后台用户 |
| GET | `/admin-api/v1/users/{userId}` | `ApiRouter` | `AdminUserManagementHandler.detail` | 查询后台用户详情 |
| PUT | `/admin-api/v1/users/{userId}` | `ApiRouter` | `AdminUserManagementHandler.update` | 更新后台用户 |
| POST | `/admin-api/v1/users/{userId}/sessions/revoke` | `ApiRouter` | `AdminUserManagementHandler.revokeSessions` | 撤销用户会话 |
| PUT | `/admin-api/v1/users/{userId}/status` | `ApiRouter` | `AdminUserManagementHandler.updateStatus` | 更新用户状态 |
| PUT | `/admin-api/v1/users/{userId}/roles` | `ApiRouter` | `AdminUserManagementHandler.replaceRoles` | 替换用户角色 |
| GET | `/admin-api/v1/menus` | `ApiRouter` | `MenuHandler.tree` | 查询菜单树 |
| GET | `/admin-api/v1/menus/{menuId}` | `ApiRouter` | `MenuHandler.detail` | 查询菜单详情 |
| POST | `/admin-api/v1/menus` | `ApiRouter` | `MenuHandler.create` | 创建菜单 |
| PUT | `/admin-api/v1/menus/{menuId}` | `ApiRouter` | `MenuHandler.update` | 更新菜单 |
| DELETE | `/admin-api/v1/menus/{menuId}` | `ApiRouter` | `MenuHandler.delete` | 删除菜单 |
| PUT | `/admin-api/v1/menus/{menuId}/status` | `ApiRouter` | `MenuHandler.updateStatus` | 更新菜单状态 |
| GET | `/admin-api/v1/dict/types` | `ApiRouter` | `DictHandler.listTypes` | 查询字典类型 |
| GET | `/admin-api/v1/dict/types/{id}` | `ApiRouter` | `DictHandler.getType` | 查询字典类型详情 |
| POST | `/admin-api/v1/dict/types` | `ApiRouter` | `DictHandler.createType` | 创建字典类型 |
| PUT | `/admin-api/v1/dict/types/{id}` | `ApiRouter` | `DictHandler.updateType` | 更新字典类型 |
| DELETE | `/admin-api/v1/dict/types/{id}` | `ApiRouter` | `DictHandler.deleteType` | 删除字典类型 |
| PUT | `/admin-api/v1/dict/types/{id}/status` | `ApiRouter` | `DictHandler.updateTypeStatus` | 更新字典类型状态 |
| GET | `/admin-api/v1/dict/data` | `ApiRouter` | `DictHandler.listData` | 查询字典数据 |
| GET | `/admin-api/v1/dict/data/{id}` | `ApiRouter` | `DictHandler.getData` | 查询字典数据详情 |
| POST | `/admin-api/v1/dict/data` | `ApiRouter` | `DictHandler.createData` | 创建字典数据 |
| PUT | `/admin-api/v1/dict/data/{id}` | `ApiRouter` | `DictHandler.updateData` | 更新字典数据 |
| DELETE | `/admin-api/v1/dict/data/{id}` | `ApiRouter` | `DictHandler.deleteData` | 删除字典数据 |
| PUT | `/admin-api/v1/dict/data/{id}/status` | `ApiRouter` | `DictHandler.updateDataStatus` | 更新字典数据状态 |
| GET | `/admin-api/v1/depts` | `ApiRouter` | `DeptHandler.listTree` | 查询部门树 |
| GET | `/admin-api/v1/depts/{id}` | `ApiRouter` | `DeptHandler.detail` | 查询部门详情 |
| POST | `/admin-api/v1/depts` | `ApiRouter` | `DeptHandler.create` | 创建部门 |
| PUT | `/admin-api/v1/depts/{id}` | `ApiRouter` | `DeptHandler.update` | 更新部门 |
| DELETE | `/admin-api/v1/depts/{id}` | `ApiRouter` | `DeptHandler.delete` | 删除部门 |
| GET | `/admin-api/v1/export/users` | `ApiRouter` | `ExcelExportHandler.exportUsers` | 导出用户 |

### 2.3 认证配置、日志和图表

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/admin-api/v1/auth/config/jwt` | `AuthConfigRouter` | `AuthConfigHandler.getJwtConfig` | 查询 JWT 配置 |
| PUT | `/admin-api/v1/auth/config/jwt` | `AuthConfigRouter` | `AuthConfigHandler.updateJwtConfig` | 更新 JWT 配置 |
| GET | `/admin-api/v1/auth/config/session` | `AuthConfigRouter` | `AuthConfigHandler.getSessionConfig` | 查询会话配置 |
| PUT | `/admin-api/v1/auth/config/session` | `AuthConfigRouter` | `AuthConfigHandler.updateSessionConfig` | 更新会话配置 |
| GET | `/admin-api/v1/auth/config/security` | `AuthConfigRouter` | `AuthConfigHandler.getSecurityPolicy` | 查询安全策略 |
| PUT | `/admin-api/v1/auth/config/security` | `AuthConfigRouter` | `AuthConfigHandler.updateSecurityPolicy` | 更新安全策略 |
| POST | `/admin-api/v1/auth/config/permissions/sync` | `AuthConfigRouter` | `AuthConfigHandler.syncPermissions` | 同步权限 |
| GET | `/admin-api/v1/auth/config/oauth2/clients` | `AuthConfigRouter` | `AuthConfigHandler.listOAuth2Clients` | 查询 OAuth2 客户端 |
| GET | `/admin-api/v1/auth/config/sessions` | `AuthConfigRouter` | `AuthConfigHandler.listOnlineSessions` | 查询在线会话 |
| POST | `/admin-api/v1/auth/config/sessions/kickout` | `AuthConfigRouter` | `AuthConfigHandler.kickoutSession` | 强制下线会话 |
| GET | `/admin-api/v1/auth/config/login-logs` | `AuthConfigRouter` | `AuthConfigHandler.listLoginLogs` | 查询登录日志 |
| GET | `/admin-api/v1/auth/config/permissions` | `AuthConfigRouter` | `AuthConfigHandler.listPermissions` | 查询权限 |
| POST | `/admin-api/v1/auth/config/jwt/test` | `AuthConfigRouter` | `AuthConfigHandler.testJwtConfig` | 测试 JWT 配置 |
| GET | `/admin-api/v1/login-logs` | `ApiRouter` | `LoginLogHandler.list` | 查询登录日志 |
| GET | `/admin-api/v1/audit-logs` | `ApiRouter` | `AuditLogHandler.list` | 查询审计日志 |
| GET | `/admin-api/v1/api-call-logs` | `ApiRouter` | `ApiCallLogHandler.list` | 查询 API 调用日志 |
| GET | `/admin-api/v1/charts` | `ChartRouter` | `ChartHandler.query` | 查询图表 |
| POST | `/admin-api/v1/charts` | `ChartRouter` | `ChartHandler.create` | 创建图表 |
| GET | `/admin-api/v1/charts/{id}` | `ChartRouter` | `ChartHandler.getById` | 查询图表详情 |
| GET | `/admin-api/v1/charts/{id}/data` | `ChartRouter` | `ChartHandler.getChartData` | 查询图表数据 |
| PUT | `/admin-api/v1/charts/{id}` | `ChartRouter` | `ChartHandler.update` | 更新图表 |
| DELETE | `/admin-api/v1/charts/{id}` | `ChartRouter` | `ChartHandler.delete` | 删除图表 |
| PUT | `/admin-api/v1/charts/{id}/status` | `ChartRouter` | `ChartHandler.updateStatus` | 更新图表状态 |

### 2.4 组织同步和办公平台管理

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/admin-api/v1/org/departments` | `OrgSyncRouter` | `OrgSyncHandler.getDepartmentList` | 查询组织部门 |
| GET | `/admin-api/v1/org/departments/{id}` | `OrgSyncRouter` | `OrgSyncHandler.getDepartmentDetail` | 查询部门详情 |
| POST | `/admin-api/v1/org/departments` | `OrgSyncRouter` | `OrgSyncHandler.createDepartment` | 创建或同步部门 |
| PUT | `/admin-api/v1/org/departments/{id}` | `OrgSyncRouter` | `OrgSyncHandler.updateDepartment` | 更新部门 |
| DELETE | `/admin-api/v1/org/departments/{id}` | `OrgSyncRouter` | `OrgSyncHandler.deleteDepartment` | 删除部门 |
| GET | `/admin-api/v1/org/employees` | `OrgSyncRouter` | `OrgSyncHandler.getEmployeeList` | 查询组织员工 |
| GET | `/admin-api/v1/org/employees/{id}` | `OrgSyncRouter` | `OrgSyncHandler.getEmployeeDetail` | 查询员工详情 |
| GET | `/admin-api/v1/org/sync-history` | `OrgSyncRouter` | `OrgSyncHandler.getSyncHistory` | 查询同步历史 |
| GET | `/admin-api/v1/office/apps` | `OfficeAdminRouter` | `OfficeAdminHandler.listApps` | 查询办公平台应用 |
| GET | `/admin-api/v1/office/apps/{platform}` | `OfficeAdminRouter` | `OfficeAdminHandler.getApp` | 查询平台应用详情 |
| POST | `/admin-api/v1/office/apps/{platform}` | `OfficeAdminRouter` | `OfficeAdminHandler.createApp` | 创建平台应用配置 |
| PUT | `/admin-api/v1/office/apps/{platform}` | `OfficeAdminRouter` | `OfficeAdminHandler.updateApp` | 更新平台应用配置 |
| DELETE | `/admin-api/v1/office/apps/{platform}` | `OfficeAdminRouter` | `OfficeAdminHandler.deleteApp` | 删除平台应用配置 |
| GET | `/admin-api/v1/office/bindings` | `OfficeAdminRouter` | `OfficeAdminHandler.listBindings` | 查询办公账号绑定 |
| POST | `/admin-api/v1/office/bindings/{id}/review` | `OfficeAdminRouter` | `OfficeAdminHandler.reviewBinding` | 审核办公账号绑定 |
| DELETE | `/admin-api/v1/office/bindings/{id}/unbind` | `OfficeAdminRouter` | `OfficeAdminHandler.unbind` | 解除办公账号绑定 |

### 2.5 办公集成、审批和日历

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/office/approvals` | `OfficeIntegrationRouter` | `OfficeIntegrationHandler.getApprovalList` | 查询审批列表 |
| POST | `/api/v1/office/approvals` | `OfficeIntegrationRouter` | `OfficeIntegrationHandler.createApproval` | 创建审批 |
| GET | `/api/v1/office/approvals/pending` | `OfficeIntegrationRouter` | `OfficeIntegrationHandler.getPendingApprovals` | 查询待审批 |
| GET | `/api/v1/office/approvals/{id}` | `OfficeIntegrationRouter` | `OfficeIntegrationHandler.getApprovalDetail` | 查询审批详情 |
| PUT | `/api/v1/office/approvals/{id}` | `OfficeIntegrationRouter` | `OfficeIntegrationHandler.updateApproval` | 更新审批 |
| DELETE | `/api/v1/office/approvals/{id}` | `OfficeIntegrationRouter` | `OfficeIntegrationHandler.deleteApproval` | 删除审批 |
| GET | `/api/v1/office/calendar-events` | `OfficeIntegrationRouter` | `OfficeIntegrationHandler.getCalendarEventList` | 查询日历事件 |
| POST | `/api/v1/office/calendar-events` | `OfficeIntegrationRouter` | `OfficeIntegrationHandler.createCalendarEvent` | 创建日历事件 |
| GET | `/api/v1/office/calendar-events/upcoming` | `OfficeIntegrationRouter` | `OfficeIntegrationHandler.getUpcomingEvents` | 查询即将到来的事件 |
| GET | `/api/v1/office/calendar-events/meeting-room-availability` | `OfficeIntegrationRouter` | `OfficeIntegrationHandler.getMeetingRoomAvailability` | 查询会议室可用性 |
| GET | `/api/v1/office/calendar-events/{id}` | `OfficeIntegrationRouter` | `OfficeIntegrationHandler.getCalendarEventDetail` | 查询日历事件详情 |
| PUT | `/api/v1/office/calendar-events/{id}` | `OfficeIntegrationRouter` | `OfficeIntegrationHandler.updateCalendarEvent` | 更新日历事件 |
| DELETE | `/api/v1/office/calendar-events/{id}` | `OfficeIntegrationRouter` | `OfficeIntegrationHandler.deleteCalendarEvent` | 删除日历事件 |
| POST | `/api/office/approvals` | `OfficeApprovalRouter` | `OfficeApprovalHandler.initiateApproval` | 发起审批流程 |
| GET | `/api/office/approvals/{processInstanceId}` | `OfficeApprovalRouter` | `OfficeApprovalHandler.getApprovalDetail` | 查询审批详情 |
| POST | `/api/office/approvals/{processInstanceId}/approve` | `OfficeApprovalRouter` | `OfficeApprovalHandler.approveProcess` | 执行审批操作 |
| DELETE | `/api/office/approvals/{processInstanceId}` | `OfficeApprovalRouter` | `OfficeApprovalHandler.cancelApproval` | 撤销审批 |
| GET | `/api/office/approvals/pending` | `OfficeApprovalRouter` | `OfficeApprovalHandler.getPendingApprovals` | 查询待审批列表 |
| GET | `/api/office/approvals/completed` | `OfficeApprovalRouter` | `OfficeApprovalHandler.getCompletedApprovals` | 查询当前用户已同意或拒绝的审批 |
| GET | `/api/office/approvals/initiated` | `OfficeApprovalRouter` | `OfficeApprovalHandler.getMyInitiatedApprovals` | 查询我发起的审批 |
| POST | `/api/office/calendar/events` | `OfficeCalendarRouter` | `OfficeCalendarHandler.createCalendarEvent` | 创建日历事件 |
| GET | `/api/office/calendar/events/{eventId}` | `OfficeCalendarRouter` | `OfficeCalendarHandler.getCalendarEventDetail` | 查询事件详情 |
| PUT | `/api/office/calendar/events/{eventId}` | `OfficeCalendarRouter` | `OfficeCalendarHandler.updateCalendarEvent` | 更新日历事件 |
| DELETE | `/api/office/calendar/events/{eventId}` | `OfficeCalendarRouter` | `OfficeCalendarHandler.deleteCalendarEvent` | 删除日历事件 |
| GET | `/api/office/calendar/events/range` | `OfficeCalendarRouter` | `OfficeCalendarHandler.getCalendarEventsByTimeRange` | 查询时间范围事件 |
| GET | `/api/office/calendar/events/my` | `OfficeCalendarRouter` | `OfficeCalendarHandler.getMyCalendarEvents` | 查询我的日历事件 |
| GET | `/api/office/calendar/events/upcoming` | `OfficeCalendarRouter` | `OfficeCalendarHandler.getUpcomingEvents` | 查询即将到来的事件 |

### 2.6 办公消息、组织和文档

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| POST | `/api/office/messages` | `OfficeMessageRouter` | `OfficeMessageHandler.sendMessage` | 发送办公平台消息 |
| GET | `/api/office/messages/{messageId}` | `OfficeMessageRouter` | `OfficeMessageHandler.getMessageStatus` | 查询消息状态 |
| POST | `/api/office/messages/{messageId}/resend` | `OfficeMessageRouter` | `OfficeMessageHandler.resendMessage` | 重新发送消息 |
| DELETE | `/api/office/messages/{messageId}` | `OfficeMessageRouter` | `OfficeMessageHandler.recallMessage` | 撤回消息 |
| POST | `/api/v1/office/organization/sync` | `OfficeOrganizationRouter` | `OfficeOrganizationHandler.syncOrganization` | 同步办公平台组织 |
| GET | `/api/v1/office/organization/mappings/department/{platform}/{externalOrgId}` | `OfficeOrganizationRouter` | `OfficeOrganizationHandler.getDepartmentMapping` | 查询部门映射 |
| GET | `/api/v1/office/organization/mappings/member/{platform}/{externalUserId}` | `OfficeOrganizationRouter` | `OfficeOrganizationHandler.getMemberMapping` | 查询成员映射 |
| GET | `/api/v1/office/organization/mappings` | `OfficeOrganizationRouter` | `OfficeOrganizationHandler.listOrganizationMappings` | 查询组织映射 |
| GET | `/api/v1/office/organization/sync/tasks` | `OfficeOrganizationRouter` | `OfficeOrganizationHandler.listSyncTasks` | 查询同步任务 |
| POST | `/api/office/documents` | `OfficeDocumentRouter` | `OfficeDocumentHandler.createDocument` | 创建办公文档 |
| POST | `/api/office/documents/upload` | `OfficeDocumentRouter` | `OfficeDocumentHandler.uploadFile` | 上传办公文档文件 |
| GET | `/api/office/documents/{documentId}` | `OfficeDocumentRouter` | `OfficeDocumentHandler.getDocumentDetail` | 查询办公文档详情 |
| GET | `/api/office/documents/folder` | `OfficeDocumentRouter` | `OfficeDocumentHandler.getDocumentsInFolder` | 查询文件夹文档 |
| GET | `/api/office/documents/my` | `OfficeDocumentRouter` | `OfficeDocumentHandler.getMyDocuments` | 查询我的文档 |
| GET | `/api/office/documents/{documentId}/download` | `OfficeDocumentRouter` | `OfficeDocumentHandler.downloadDocument` | 下载办公文档 |
| DELETE | `/api/office/documents/{documentId}` | `OfficeDocumentRouter` | `OfficeDocumentHandler.deleteDocument` | 删除办公文档 |
| PUT | `/api/office/documents/{documentId}/move` | `OfficeDocumentRouter` | `OfficeDocumentHandler.moveDocument` | 移动办公文档 |
| POST | `/api/office/documents/{documentId}/copy` | `OfficeDocumentRouter` | `OfficeDocumentHandler.copyDocument` | 复制办公文档 |

### 2.7 站内消息、模板和文档管理

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/messages` | `MessageRouter` | `MessageHandler.sendMessage` | 发送站内消息 |
| GET | `/api/v1/messages/inbox` | `MessageRouter` | `MessageHandler.getInbox` | 查询收件箱 |
| GET | `/api/v1/messages/sent` | `MessageRouter` | `MessageHandler.getSentMessages` | 查询发件箱 |
| GET | `/api/v1/messages/{messageId}` | `MessageRouter` | `MessageHandler.getMessageDetail` | 查询消息详情 |
| PUT | `/api/v1/messages/{messageId}/read` | `MessageRouter` | `MessageHandler.markAsRead` | 标记消息已读 |
| PUT | `/api/v1/messages/read-all` | `MessageRouter` | `MessageHandler.markAllAsRead` | 全部标记已读 |
| GET | `/api/v1/messages/unread/count` | `MessageRouter` | `MessageHandler.getUnreadCount` | 查询未读消息数 |
| GET | `/admin-api/v1/message-templates/{templateCode}` | `MessageRouter` | `MessageTemplateHandler.getTemplateByCode` | 按编码查询模板 |
| GET | `/admin-api/v1/message-templates` | `MessageRouter` | `MessageTemplateHandler.getEnabledTemplates` | 查询启用模板 |
| POST | `/admin-api/v1/message-templates` | `MessageRouter` | `MessageTemplateHandler.createTemplate` | 创建消息模板 |
| PUT | `/admin-api/v1/message-templates/{templateId}` | `MessageRouter` | `MessageTemplateHandler.updateTemplate` | 更新消息模板 |
| DELETE | `/admin-api/v1/message-templates/{templateId}` | `MessageRouter` | `MessageTemplateHandler.deleteTemplate` | 删除消息模板 |
| GET | `/admin-api/v1/documents` | `DocumentRouter` | `DocumentHandler.query` | 分页查询文档 |
| POST | `/admin-api/v1/documents` | `DocumentRouter` | `DocumentHandler.create` | 创建文档 |
| GET | `/admin-api/v1/documents/{id}` | `DocumentRouter` | `DocumentHandler.getById` | 查询文档详情 |
| PUT | `/admin-api/v1/documents/{id}` | `DocumentRouter` | `DocumentHandler.update` | 更新文档 |
| DELETE | `/admin-api/v1/documents/{id}` | `DocumentRouter` | `DocumentHandler.delete` | 删除文档 |
| PUT | `/admin-api/v1/documents/{id}/status` | `DocumentRouter` | `DocumentHandler.updateStatus` | 更新文档状态 |
| GET | `/admin-api/v1/documents/{id}/download` | `DocumentRouter` | `DocumentHandler.download` | 获取文档下载信息 |

### 2.8 工作流

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/admin-api/v1/workflows/instances` | `WorkflowRouter` | `WorkflowHandler.listInstances` | 查询流程实例 |
| GET | `/admin-api/v1/workflows/instances/{instanceId}` | `WorkflowRouter` | `WorkflowHandler.getInstanceDetail` | 查询流程实例详情 |
| GET | `/admin-api/v1/workflows/statistics` | `WorkflowRouter` | `WorkflowHandler.getStatistics` | 查询流程统计 |
| GET | `/admin-api/v1/workflows/statistics/trend` | `WorkflowRouter` | `WorkflowHandler.getTrend` | 查询流程趋势 |
| POST | `/api/v1/workflows/{workflowCode}/instances` | `WorkflowRouter` | `WorkflowHandler.startProcessInstance` | 发起流程实例 |
| GET | `/api/v1/workflows/tasks/pending` | `WorkflowRouter` | `WorkflowHandler.listPendingTasks` | 查询待办任务 |
| POST | `/api/v1/workflows/tasks/{taskId}/complete` | `WorkflowRouter` | `WorkflowHandler.completeTask` | 完成任务 |
| POST | `/api/workflow/tasks/{taskId}/rollback` | `WorkflowRouter` | `WorkflowHandler.rollbackTask` | 回退任务 |
| POST | `/api/workflow/tasks/{taskId}/transfer` | `WorkflowRouter` | `WorkflowHandler.transferTask` | 转办任务 |
| POST | `/api/workflow/tasks/{taskId}/delegate` | `WorkflowRouter` | `WorkflowHandler.delegateTask` | 委派任务 |
| POST | `/api/workflow/instances/{instanceId}/suspend` | `WorkflowRouter` | `WorkflowHandler.suspendInstance` | 挂起流程实例 |
| POST | `/api/workflow/instances/{instanceId}/resume` | `WorkflowRouter` | `WorkflowHandler.resumeInstance` | 恢复流程实例 |

### 2.9 SSO 管理和代码生成器

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/admin-api/v1/sso/providers` | `SsoAdminRouter` | `SsoProviderHandler.list` | 查询 SSO 提供商 |
| POST | `/admin-api/v1/sso/providers` | `SsoAdminRouter` | `SsoProviderHandler.create` | 创建 SSO 提供商 |
| PUT | `/admin-api/v1/sso/providers/{id}` | `SsoAdminRouter` | `SsoProviderHandler.update` | 更新 SSO 提供商 |
| DELETE | `/admin-api/v1/sso/providers/{id}` | `SsoAdminRouter` | `SsoProviderHandler.delete` | 删除 SSO 提供商 |
| GET | `/admin-api/v1/sso/providers/{id}` | `SsoAdminRouter` | `SsoProviderHandler.get` | 查询 SSO 提供商详情 |
| POST | `/admin-api/v1/sso/providers/{id}/test` | `SsoAdminRouter` | `SsoProviderHandler.testConnection` | 测试 SSO 连接 |
| GET | `/admin-api/v1/sso/providers/{id}/metadata` | `SsoAdminRouter` | `SsoProviderHandler.getMetadata` | 获取 SSO 元数据 |
| GET | `/admin-api/v1/sso/sessions` | `SsoAdminRouter` | `SsoSessionHandler.list` | 查询 SSO 会话 |
| POST | `/admin-api/v1/sso/sessions/{id}/revoke` | `SsoAdminRouter` | `SsoSessionHandler.revoke` | 撤销 SSO 会话 |
| POST | `/admin-api/v1/sso/sessions/users/{userId}/revoke` | `SsoAdminRouter` | `SsoSessionHandler.revokeUserSessions` | 撤销用户 SSO 会话 |
| GET | `/admin-api/v1/sso/sessions/statistics` | `SsoAdminRouter` | `SsoSessionHandler.getStatistics` | 查询 SSO 会话统计 |
| GET | `/admin-api/v1/sso/audit-logs` | `SsoAdminRouter` | `SsoAuditLogHandler.list` | 查询 SSO 审计日志 |
| GET | `/admin-api/v1/sso/audit-logs/statistics` | `SsoAdminRouter` | `SsoAuditLogHandler.getStatistics` | 查询 SSO 审计统计 |
| GET | `/admin-api/v1/generator/datasources` | `GeneratorAdminRouter` | `GenDatasourceHandler.list` | 查询代码生成数据源 |
| POST | `/admin-api/v1/generator/datasources` | `GeneratorAdminRouter` | `GenDatasourceHandler.create` | 创建数据源 |
| PUT | `/admin-api/v1/generator/datasources/{id}` | `GeneratorAdminRouter` | `GenDatasourceHandler.update` | 更新数据源 |
| DELETE | `/admin-api/v1/generator/datasources/{id}` | `GeneratorAdminRouter` | `GenDatasourceHandler.delete` | 删除数据源 |
| POST | `/admin-api/v1/generator/datasources/{id}/test` | `GeneratorAdminRouter` | `GenDatasourceHandler.testConnection` | 测试数据源连接 |
| GET | `/admin-api/v1/generator/configs` | `GeneratorAdminRouter` | `GenConfigHandler.list` | 查询生成配置 |
| POST | `/admin-api/v1/generator/configs` | `GeneratorAdminRouter` | `GenConfigHandler.create` | 创建生成配置 |
| PUT | `/admin-api/v1/generator/configs/{id}` | `GeneratorAdminRouter` | `GenConfigHandler.update` | 更新生成配置 |
| DELETE | `/admin-api/v1/generator/configs/{id}` | `GeneratorAdminRouter` | `GenConfigHandler.delete` | 删除生成配置 |
| GET | `/admin-api/v1/generator/datasources/{id}/tables` | `GeneratorAdminRouter` | `GenConfigHandler.getTables` | 查询数据源表结构 |
| GET | `/admin-api/v1/generator/datasources/{id}/tables/{tableName}/columns` | `GeneratorAdminRouter` | `GenConfigHandler.getColumns` | 查询表字段 |
| GET | `/admin-api/v1/generator/configs/{id}/history` | `GeneratorAdminRouter` | `GenConfigHandler.listHistory` | 查询生成历史 |
| POST | `/admin-api/v1/generator/configs/{id}/generate` | `GeneratorAdminRouter` | `CodeGeneratorHandler.generate` | 执行代码生成 |

### 2.10 OAuth2

当前安全配置要求 discovery、JWKS、authorize、consent 和 consent/revoke 使用已认证登录态。Web 安全层公开的 token 端点仍按 grant 类型向应用服务传递客户端凭据并校验授权；revoke Handler 要求 `token` 和 `client_id`，introspect Handler 要求 `token`，二者没有在 Handler 中执行客户端密钥认证。不能将协议预期或 OpenAPI 标注替代为实际认证行为。

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/.well-known/oauth-authorization-server` | `OAuth2RouterConfig` | `OAuth2DiscoveryHandler.discovery` | OAuth2 授权服务器发现文档 |
| GET | `/oauth2/authorize` | `OAuth2RouterConfig` | `OAuth2AuthorizationHandler.authorize` | OAuth2 授权端点 |
| POST | `/oauth2/authorize` | `OAuth2RouterConfig` | `OAuth2AuthorizationHandler.authorize` | OAuth2 授权端点 |
| POST | `/oauth2/token` | `OAuth2RouterConfig` | `OAuth2AuthorizationHandler.token` | OAuth2 Token 端点 |
| POST | `/oauth2/revoke` | `OAuth2RouterConfig` | `OAuth2AuthorizationHandler.revoke` | 撤销 Token |
| POST | `/oauth2/introspect` | `OAuth2RouterConfig` | `OAuth2AuthorizationHandler.introspect` | Token 自省 |
| POST | `/oauth2/consent` | `OAuth2RouterConfig` | `OAuth2AuthorizationHandler.consent` | 提交用户同意 |
| POST | `/oauth2/consent/revoke` | `OAuth2RouterConfig` | `OAuth2AuthorizationHandler.revokeConsent` | 撤销用户同意 |
| GET | `/oauth2/jwks` | `OAuth2RouterConfig` | `OAuth2JwksHandler.jwks` | 获取 JWKS 公钥 |
| POST | `/admin-api/v1/oauth2/clients` | `OAuth2RouterConfig` | `OAuth2ClientManagementHandler.createClient` | 创建 OAuth2 客户端 |
| PUT | `/admin-api/v1/oauth2/clients/{clientId}` | `OAuth2RouterConfig` | `OAuth2ClientManagementHandler.updateClient` | 更新 OAuth2 客户端 |
| DELETE | `/admin-api/v1/oauth2/clients/{clientId}` | `OAuth2RouterConfig` | `OAuth2ClientManagementHandler.deleteClient` | 删除 OAuth2 客户端 |
| GET | `/admin-api/v1/oauth2/clients/{clientId}` | `OAuth2RouterConfig` | `OAuth2ClientManagementHandler.getClient` | 查询 OAuth2 客户端 |
| GET | `/admin-api/v1/oauth2/clients` | `OAuth2RouterConfig` | `OAuth2ClientManagementHandler.listClients` | 查询 OAuth2 客户端列表 |
| POST | `/admin-api/v1/oauth2/clients/{clientId}/reset-secret` | `OAuth2RouterConfig` | `OAuth2ClientManagementHandler.resetClientSecret` | 重置客户端密钥 |

### 2.11 认证、授权和验证码

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/auth/captcha` | `ApiRouter` | `CaptchaHandler.generate` | 生成图形验证码 |
| POST | `/api/v1/auth/captcha/verify` | `ApiRouter` | `CaptchaHandler.verify` | 验证图形验证码 |
| POST | `/api/v1/auth/register` | `ApiRouter` | `AuthHandler.register` | 完成用户注册 |
| POST | `/api/v1/auth/wechat/bind-phone` | `ApiRouter` | `AuthHandler.bindWechatPhone` | 注册前验证并绑定微信手机号 |
| POST | `/api/v1/auth/login` | `ApiRouter` | `AuthHandler.login` | 用户登录 |
| POST | `/api/v1/auth/refresh` | `ApiRouter` | `AuthHandler.refresh` | 刷新令牌 |
| POST | `/api/v1/auth/logout` | `ApiRouter` | `AuthHandler.logout` | 退出登录 |
| POST | `/api/v1/auth/sms/code` | `ApiRouter` | `AuthHandler.sendSmsCode` | 发送短信验证码 |
| POST | `/api/v1/auth/cl/sms/code` | `ApiRouter` | `AuthHandler.sendClSmsCode` | 发送创蓝短信验证码 |
| POST | `/api/v1/auth/cl/validate-mobile` | `ApiRouter` | `AuthHandler.validateMobile` | 校验本机手机号 |
| POST | `/api/v1/auth/wx/phone` | `ApiRouter` | `AuthHandler.bindPhone` | 绑定微信手机号 |
| GET | `/api/v1/auth/wechat/mp/authorize` | `ApiRouter` | `AuthHandler.mpAuthorize` | 公众号网页授权跳转（签发一次性 OAuth state 并 302 跳转微信授权页；公开端点，`wechat.mp` 凭据缺失时返回 `wx.channel_not_configured`） |
| GET | `/api/v1/auth/roles/my` | `ApiRouter` | `AuthorizationHandler.getMyRoles` | 查询本人角色 |
| GET | `/api/v1/auth/permissions/my` | `ApiRouter` | `AuthorizationHandler.getMyPermissions` | 查询本人权限 |
| GET | `/api/v1/auth/roles` | `ApiRouter` | `AuthorizationHandler.getAllRoles` | 查询全部角色 |
| GET | `/api/v1/auth/permissions` | `ApiRouter` | `AuthorizationHandler.getAllPermissions` | 查询全部权限 |
| POST | `/api/v1/auth/roles` | `ApiRouter` | `AuthorizationHandler.createRole` | 创建角色 |
| POST | `/api/v1/auth/roles/update` | `ApiRouter` | `AuthorizationHandler.updateRole` | 更新角色 |
| DELETE | `/api/v1/auth/roles/{id}` | `ApiRouter` | `AuthorizationHandler.deleteRole` | 删除角色 |
| POST | `/api/v1/auth/permissions` | `ApiRouter` | `AuthorizationHandler.createPermission` | 创建权限 |
| POST | `/api/v1/auth/permissions/update` | `ApiRouter` | `AuthorizationHandler.updatePermission` | 更新权限 |
| DELETE | `/api/v1/auth/permissions/{id}` | `ApiRouter` | `AuthorizationHandler.deletePermission` | 删除权限 |
| POST | `/api/v1/auth/users/{userId}/roles/{roleId}` | `ApiRouter` | `AuthorizationHandler.assignRoleToUser` | 为用户分配角色 |
| DELETE | `/api/v1/auth/users/{userId}/roles/{roleId}` | `ApiRouter` | `AuthorizationHandler.removeRoleFromUser` | 移除用户角色 |

### 2.12 文件、健康检查和通知

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/notifications/stream` | `ApiRouter` | `NotificationSseHandler.stream` | 订阅通知 SSE 流 |
| GET | `/` | `ApiRouter` | `HealthHandler.root` | 根路径探活 |
| GET | `/api/v1/health` | `ApiRouter` | `HealthHandler.health` | 查询应用健康状态 |
| GET | `/api/v1/info` | `ApiRouter` | `HealthHandler.info` | 查询应用信息 |
| POST | `/api/v1/files/generic` | `ApiRouter` | `FileHandler.uploadGenericFile` | 上传通用文件（multipart/form-data） |
| POST | `/api/v1/file/upload` | `ApiRouter` | `FileHandler.upload` | 上传文件 |
| GET | `/api/v1/file/content` | `ApiRouter` | `FileHandler.content` | 读取文件内容 |
| GET | `/api/v1/file/url` | `ApiRouter` | `FileHandler.getUrl` | 获取文件 URL |
| DELETE | `/api/v1/file/delete` | `ApiRouter` | `FileHandler.delete` | 删除资源 |
| GET | `/api/v1/file/exists` | `ApiRouter` | `FileHandler.exists` | 检查文件是否存在 |

### 2.13 系统配置、搜索和日志

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/admin-api/v1/configs` | `ApiRouter` | `ConfigHandler.list` | 查询系统配置 |
| GET | `/admin-api/v1/configs/{id}` | `ApiRouter` | `ConfigHandler.detail` | 查询系统配置详情 |
| POST | `/admin-api/v1/configs` | `ApiRouter` | `ConfigHandler.create` | 创建系统配置 |
| PUT | `/admin-api/v1/configs/{id}` | `ApiRouter` | `ConfigHandler.update` | 更新系统配置 |
| DELETE | `/admin-api/v1/configs/{id}` | `ApiRouter` | `ConfigHandler.delete` | 删除系统配置 |
| PUT | `/admin-api/v1/configs/{id}/status` | `ApiRouter` | `ConfigHandler.updateStatus` | 更新系统配置状态 |
| GET | `/admin-api/v1/search` | `ApiRouter` | `UnifiedSearchHandler.search` | 统一搜索 |
| POST | `/admin-api/v1/search/rebuild` | `ApiRouter` | `UnifiedSearchHandler.rebuildIndex` | 重建搜索索引 |
| GET | `/admin-api/v1/logs/runtime` | `ApiRouter` | `RuntimeLogHandler.queryLogs` | 查询日志 |
| GET | `/admin-api/v1/logs/runtime/count` | `ApiRouter` | `RuntimeLogHandler.countLogs` | 统计日志数量 |
| GET | `/admin-api/v1/logs/business` | `ApiRouter` | `UnifiedBusinessLogHandler.queryLogs` | 查询日志 |
| GET | `/admin-api/v1/logs/business/count` | `ApiRouter` | `UnifiedBusinessLogHandler.countLogs` | 统计日志数量 |

### 2.14 在线用户和监控

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/admin-api/v1/online-users` | `ApiRouter` | `OnlineUserHandler.list` | 查询在线用户 |
| GET | `/admin-api/v1/monitor/overview` | `ApiRouter` | `MonitorDashboardHandler.overview` | 查询监控概览 |
| GET | `/admin-api/v1/monitor/system` | `ApiRouter` | `MonitorDashboardHandler.system` | 查询系统监控 |
| GET | `/admin-api/v1/monitor/middleware` | `ApiRouter` | `MonitorDashboardHandler.middleware` | 查询中间件监控 |
| GET | `/admin-api/v1/monitor/governance` | `ApiRouter` | `MonitorDashboardHandler.governance` | 查询治理监控 |
| GET | `/admin-api/v1/monitor/business` | `ApiRouter` | `MonitorDashboardHandler.business` | 查询业务监控 |

### 2.15 任务和数据库配置

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/admin-api/v1/jobs` | `ApiRouter` | `JobHandler.list` | 查询任务列表 |
| GET | `/admin-api/v1/jobs/page` | `ApiRouter` | `JobHandler.page` | 分页查询任务 |
| GET | `/admin-api/v1/jobs/{id}` | `ApiRouter` | `JobHandler.detail` | 查询任务详情 |
| POST | `/admin-api/v1/jobs` | `ApiRouter` | `JobHandler.create` | 创建任务 |
| PUT | `/admin-api/v1/jobs/{id}` | `ApiRouter` | `JobHandler.update` | 更新任务 |
| DELETE | `/admin-api/v1/jobs/{id}` | `ApiRouter` | `JobHandler.delete` | 删除任务 |
| PUT | `/admin-api/v1/jobs/{id}/status` | `ApiRouter` | `JobHandler.updateStatus` | 更新任务状态 |
| POST | `/admin-api/v1/jobs/{id}/run` | `ApiRouter` | `JobHandler.runOnce` | 立即执行任务 |
| GET | `/admin-api/v1/jobs/{id}/logs` | `ApiRouter` | `JobHandler.logs` | 查询任务日志 |
| POST | `/admin-api/v1/database/test` | `ApiRouter` | `DatabaseConfigHandler.testConnection` | 测试数据库连接 |
| POST | `/admin-api/v1/database/generate-config` | `ApiRouter` | `DatabaseConfigHandler.generateConfig` | 生成数据库配置 |
| GET | `/admin-api/v1/database/current` | `ApiRouter` | `DatabaseConfigHandler.getCurrentInfo` | 查询当前数据库信息 |
| GET | `/admin-api/v1/database/datasources` | `ApiRouter` | `DatabaseConfigHandler.listDataSources` | 查询数据源 |
| POST | `/admin-api/v1/database/datasources` | `ApiRouter` | `DatabaseConfigHandler.createDataSource` | 创建数据源 |
| PUT | `/admin-api/v1/database/datasources/{id}` | `ApiRouter` | `DatabaseConfigHandler.updateDataSource` | 更新数据源 |
| DELETE | `/admin-api/v1/database/datasources/{id}` | `ApiRouter` | `DatabaseConfigHandler.deleteDataSource` | 删除数据源 |
| GET | `/admin-api/v1/database/datasources/{id}/metrics` | `ApiRouter` | `DatabaseConfigHandler.getDataSourceMetrics` | 查询数据源指标 |

## 3. 独立认证中心路由

### 3.1 登录、注销和令牌

| 方法 | 路径 | Router | Handler 方法 | 认证说明 |
| --- | --- | --- | --- | --- |
| POST | `/auth/v1/login` | `AuthenticationRouter` | `AuthenticationHandler.login` | 无需认证；用户名密码登录 |
| POST | `/auth/v1/logout/{userId}` | `AuthenticationRouter` | `AuthenticationHandler.logout` | 需要 Bearer 访问令牌；路径用户必须与认证主体一致 |
| POST | `/auth/v1/refresh` | `AuthenticationRouter` | `AuthenticationHandler.refreshToken` | 无需访问令牌；刷新令牌一次性轮换并防重放 |

### 3.2 角色、权限和会话

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| POST | `/auth/v1/roles` | `RoleRouter` | `RoleHandler.createRole` | 创建角色 |
| GET | `/auth/v1/roles` | `RoleRouter` | `RoleHandler.listRoles` | 查询角色 |
| POST | `/auth/v1/roles/assign-permissions` | `RoleRouter` | `RoleHandler.assignPermissions` | 为角色分配权限 |
| PUT | `/auth/v1/roles/{id}` | `RoleRouter` | `RoleHandler.updateRole` | 更新角色 |
| DELETE | `/auth/v1/roles/{id}` | `RoleRouter` | `RoleHandler.deleteRole` | 删除角色 |
| GET | `/auth/v1/roles/{id}` | `RoleRouter` | `RoleHandler.getRoleById` | 查询角色详情 |
| GET | `/auth/v1/roles/{id}/permissions` | `RoleRouter` | `RoleHandler.getRolePermissions` | 查询角色权限 |
| POST | `/auth/v1/permissions` | `PermissionRouter` | `PermissionHandler.createPermission` | 创建权限 |
| GET | `/auth/v1/permissions` | `PermissionRouter` | `PermissionHandler.listPermissions` | 查询权限 |
| GET | `/auth/v1/permissions/resource/{resource}` | `PermissionRouter` | `PermissionHandler.getPermissionsByResource` | 按资源查询权限 |
| PUT | `/auth/v1/permissions/{id}` | `PermissionRouter` | `PermissionHandler.updatePermission` | 更新权限 |
| DELETE | `/auth/v1/permissions/{id}` | `PermissionRouter` | `PermissionHandler.deletePermission` | 删除权限 |
| GET | `/auth/v1/permissions/{id}` | `PermissionRouter` | `PermissionHandler.getPermissionById` | 查询权限详情 |
| GET | `/auth/v1/sessions/user/{userId}` | `SessionRouter` | `SessionHandler.getUserSessions` | 查询用户会话 |
| DELETE | `/auth/v1/sessions/user/{userId}` | `SessionRouter` | `SessionHandler.terminateUserSessions` | 终止用户会话 |
| GET | `/auth/v1/sessions/online` | `SessionRouter` | `SessionHandler.getOnlineUsers` | 查询在线用户 |
| GET | `/auth/v1/sessions/statistics` | `SessionRouter` | `SessionHandler.getOnlineStatistics` | 查询在线统计 |
| DELETE | `/auth/v1/sessions/{sessionId}` | `SessionRouter` | `SessionHandler.terminateSession` | 终止指定会话 |
| GET | `/auth/v1/sessions/{sessionId}` | `SessionRouter` | `SessionHandler.getSessionById` | 查询会话详情 |

### 3.3 MFA 接口

`MfaRouter` 注册的是 `/api/v1/mfa/**`，面向已认证用户的 MFA 设置、验证和状态查询。

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/mfa/setup` | `MfaRouter` | `MfaHandler.setupMfa` | 设置 TOTP MFA |
| POST | `/api/v1/mfa/verify` | `MfaRouter` | `MfaHandler.verifyAndEnableMfa` | 验证 TOTP 并启用 MFA |
| POST | `/api/v1/mfa/verify-code` | `MfaRouter` | `MfaHandler.verifyMfaCode` | 验证 TOTP 验证码 |
| POST | `/api/v1/mfa/verify-backup` | `MfaRouter` | `MfaHandler.verifyBackupCode` | 验证备份码 |
| POST | `/api/v1/mfa/disable` | `MfaRouter` | `MfaHandler.disableMfa` | 禁用 MFA |
| GET | `/api/v1/mfa/status` | `MfaRouter` | `MfaHandler.getMfaStatus` | 查询 MFA 状态 |

`AuthCenterRouterConfig` 另外注册 `/api/mfa/**` 管理接口。这组路由与 `/api/v1/mfa/**` 相互独立，不能视为同一路由的别名。

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| POST | `/api/mfa/totp/enable` | `AuthCenterRouterConfig` | `MfaManagementHandler.enableTotp` | 启用 TOTP |
| POST | `/api/mfa/totp/activate` | `AuthCenterRouterConfig` | `MfaManagementHandler.activateTotp` | 激活 TOTP |
| POST | `/api/mfa/sms/enable` | `AuthCenterRouterConfig` | `MfaManagementHandler.enableSms` | 启用短信 MFA |
| POST | `/api/mfa/email/enable` | `AuthCenterRouterConfig` | `MfaManagementHandler.enableEmail` | 启用邮箱 MFA |
| DELETE | `/api/mfa/{type}` | `AuthCenterRouterConfig` | `MfaManagementHandler.disableMfa` | 禁用指定类型 MFA |
| POST | `/api/mfa/verify` | `AuthCenterRouterConfig` | `MfaManagementHandler.verifyMfaCode` | 验证 MFA 验证码 |
| POST | `/api/mfa/send-code` | `AuthCenterRouterConfig` | `MfaManagementHandler.sendMfaCode` | 发送 MFA 验证码 |
| GET | `/api/mfa/configs` | `AuthCenterRouterConfig` | `MfaManagementHandler.getUserMfaConfigs` | 查询用户 MFA 配置 |
| GET | `/api/mfa/status` | `AuthCenterRouterConfig` | `MfaManagementHandler.checkMfaStatus` | 检查 MFA 状态 |
| POST | `/api/mfa/backup-codes/regenerate` | `AuthCenterRouterConfig` | `MfaManagementHandler.regenerateBackupCodes` | 重新生成备份码 |
| POST | `/api/mfa/backup-codes/verify` | `AuthCenterRouterConfig` | `MfaManagementHandler.verifyBackupCode` | 验证备份码 |
| POST | `/api/mfa/unlock` | `AuthCenterRouterConfig` | `MfaManagementHandler.unlockMfa` | 解锁 MFA |

### 3.4 IP 访问控制

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| POST | `/api/ip-access-control/whitelist` | `AuthCenterRouterConfig` | `IpAccessControlHandler.addToWhitelist` | 添加白名单规则 |
| POST | `/api/ip-access-control/blacklist` | `AuthCenterRouterConfig` | `IpAccessControlHandler.addToBlacklist` | 添加黑名单规则 |
| DELETE | `/api/ip-access-control/whitelist` | `AuthCenterRouterConfig` | `IpAccessControlHandler.removeFromWhitelist` | 移除白名单规则 |
| DELETE | `/api/ip-access-control/blacklist` | `AuthCenterRouterConfig` | `IpAccessControlHandler.removeFromBlacklist` | 移除黑名单规则 |
| GET | `/api/ip-access-control/whitelist` | `AuthCenterRouterConfig` | `IpAccessControlHandler.getWhitelist` | 查询白名单 |
| GET | `/api/ip-access-control/blacklist` | `AuthCenterRouterConfig` | `IpAccessControlHandler.getBlacklist` | 查询黑名单 |
| PUT | `/api/ip-access-control/{ruleId}/enable` | `AuthCenterRouterConfig` | `IpAccessControlHandler.enableRule` | 启用访问控制规则 |
| PUT | `/api/ip-access-control/{ruleId}/disable` | `AuthCenterRouterConfig` | `IpAccessControlHandler.disableRule` | 禁用访问控制规则 |
| DELETE | `/api/ip-access-control/{ruleId}` | `AuthCenterRouterConfig` | `IpAccessControlHandler.deleteRule` | 删除访问控制规则 |
| POST | `/api/ip-access-control/cleanup` | `AuthCenterRouterConfig` | `IpAccessControlHandler.cleanupExpiredRules` | 清理过期规则 |
| POST | `/api/ip-access-control/check` | `AuthCenterRouterConfig` | `IpAccessControlHandler.checkAccess` | 检查 IP 访问权限 |

### 3.5 认证配置

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/auth/configs` | `AuthConfigRouter` | `AuthConfigHandler.createConfig` | 创建认证配置 |
| PUT | `/api/v1/auth/configs/{id}` | `AuthConfigRouter` | `AuthConfigHandler.updateConfig` | 更新认证配置 |
| DELETE | `/api/v1/auth/configs/{id}` | `AuthConfigRouter` | `AuthConfigHandler.deleteConfig` | 删除认证配置 |
| GET | `/api/v1/auth/configs/effective` | `AuthConfigRouter` | `AuthConfigHandler.getEffectiveConfig` | 查询有效认证配置 |
| GET | `/api/v1/auth/configs/type/{type}` | `AuthConfigRouter` | `AuthConfigHandler.getConfigsByType` | 按类型查询认证配置 |
| GET | `/api/v1/auth/configs` | `AuthConfigRouter` | `AuthConfigHandler.listConfigs` | 查询认证配置列表 |
| GET | `/api/v1/auth/configs/{id}` | `AuthConfigRouter` | `AuthConfigHandler.getConfig` | 查询认证配置详情 |

### 3.6 认证审计日志

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/auth/audit-logs/failed` | `AuditLogRouter` | `AuditLogHandler.listFailedLogs` | 查询失败审计日志 |
| GET | `/api/v1/auth/audit-logs/high-risk` | `AuditLogRouter` | `AuditLogHandler.listHighRiskLogs` | 查询高风险审计日志 |
| GET | `/api/v1/auth/audit-logs/user/{userId}` | `AuditLogRouter` | `AuditLogHandler.listUserLogs` | 查询用户审计日志 |
| GET | `/api/v1/auth/audit-logs/event-type/{eventType}` | `AuditLogRouter` | `AuditLogHandler.listLogsByEventType` | 按事件类型查询审计日志 |
| GET | `/api/v1/auth/audit-logs/ip/{ipAddress}` | `AuditLogRouter` | `AuditLogHandler.listLogsByIpAddress` | 按 IP 查询审计日志 |
| GET | `/api/v1/auth/audit-logs/user/{userId}/login-failures` | `AuditLogRouter` | `AuditLogHandler.countLoginFailures` | 统计用户登录失败次数 |
| POST | `/api/v1/auth/audit-logs/time-range` | `AuditLogRouter` | `AuditLogHandler.listLogsByTimeRange` | 按时间范围查询审计日志 |
| POST | `/api/v1/auth/audit-logs/count` | `AuditLogRouter` | `AuditLogHandler.countLogs` | 统计日志数量 |
| DELETE | `/api/v1/auth/audit-logs/cleanup` | `AuditLogRouter` | `AuditLogHandler.cleanupExpiredLogs` | 清理过期审计日志 |
| GET | `/api/v1/auth/audit-logs/{id}` | `AuditLogRouter` | `AuditLogHandler.getLog` | 查询审计日志详情 |

## 4. 非业务路由和文档说明

### 4.1 方法不允许处理

`MethodNotAllowedRouter` 实现 `RouterFunction<ServerResponse>` 和 `Ordered`，用于处理已匹配资源但 HTTP 方法不支持的请求，返回 405 类错误响应。它不是业务 API，不在业务路由表中重复列出。

### 4.2 OpenAPI 展示配置

`RouterOpenApiConfig` 中存在用于 SpringDoc 元数据展示的路由定义和占位路由。其中 21 个 `/__openapi_*_placeholder` GET 占位端点实际返回 404，用于补充 OpenAPI 文档，不应与实际业务 `Handler` 注册重复计数。新增业务端点时，应优先修改真实 Router，再同步对应的 `@RouterOperation` 或 SpringDoc 元数据。

示例模块 `apex-example` 的 `ExampleRouterConfig` 不纳入生产业务清单。Actuator、静态资源和 Spring Security 自身提供的端点也不属于本文业务 Router 清单。

本清单按可执行注册记录；WebFlux 按注册顺序匹配。Office 审批、日历和文档 Router 中，部分单段固定路径注册在 `/{id}` 类路径之后，可能先命中详情 Handler，表格列出注册事实并不代表不存在路径遮蔽。

### 4.3 路由维护规则

1. 新增、删除或修改端点时，同时更新本文件中的方法、完整路径、Router、Handler 方法和认证说明。
2. 路由路径以源码最终拼接结果记录，不保留无法直接调用的 `BASE_PATH + "/..."` 表达式。
3. 具体权限码应以 Handler 上实际存在的 `@RequirePermission` 等注解为准；仅凭 `/admin-api` 前缀不得推断权限码。
4. 认证中心路由和主应用路由分别维护，不能因为路径相似而合并。
5. 路由行为发生变化后，应运行 `scripts/ci/check-doc-consistency.sh`，另做方法、完整路径、运行边界及 Handler 映射的集合比对；该脚本只检查本文件存在性，不能证明路由完整。涉及代码时根据需要运行 Spotless 和项目测试。

### 4.4 核对范围

当前生产业务清单包含 340 条方法与路径注册：主应用 272 条，独立认证中心 68 条，来自 27 个生产业务路由定义文件。Keycloak 的 6 条条件装配路由包含在主应用计数中；405 fallback、OpenAPI 占位、示例和框架端点不计入。统计按运行边界区分相同类名，并展开全部嵌套前缀和路径常量。

## 5. 主要源码索引

- 主应用核心路由：`apex-interfaces/src/main/java/com/apex/scaffold/interfaces/router/ApiRouter.java`
- 工作流路由：`apex-interfaces/src/main/java/com/apex/scaffold/interfaces/router/WorkflowRouter.java`
- OAuth2 路由：`apex-interfaces/src/main/java/com/apex/scaffold/interfaces/config/OAuth2RouterConfig.java`
- Keycloak 路由：`apex-interfaces/src/main/java/com/apex/scaffold/interfaces/config/KeycloakRouterConfig.java`
- 主应用 Office 子路由：`apex-interfaces/src/main/java/com/apex/scaffold/interfaces/router/office/`
- 认证中心认证路由：`apex-auth-center/src/main/java/com/apex/scaffold/authcenter/interfaces/router/AuthenticationRouter.java`
- 认证中心 MFA/IP 路由：`apex-auth-center/src/main/java/com/apex/scaffold/authcenter/config/AuthCenterRouterConfig.java`
- 认证中心角色、权限和会话路由：`apex-auth-center/src/main/java/com/apex/scaffold/authcenter/interfaces/router/`
- 主应用认证配置：`apex-interfaces/src/main/java/com/apex/scaffold/interfaces/router/AuthConfigRouter.java`
- 认证中心配置及审计：`apex-auth-center/src/main/java/com/apex/scaffold/authcenter/interfaces/router/AuthConfigRouter.java`、`AuditLogRouter.java`
- 主应用安全规则：`apex-interfaces/src/main/java/com/apex/scaffold/interfaces/config/SecurityConfig.java`、`apex-common/src/main/java/com/apex/scaffold/common/constant/SystemConstants.java`
- 认证中心安全规则：`apex-auth-center/src/main/java/com/apex/scaffold/authcenter/config/AuthCenterConfig.java`
