# API 路由清单

> 本文档记录项目当前实际注册的函数式 WebFlux API 路由。
>
> 路由以 `RouterFunction`、`RouterFunctions.route()`、`RouterFunctions.nest()` 和 `SpringdocRouteBuilder` 的可执行注册代码为准。`RouterOpenApiConfig` 中仅用于 SpringDoc 展示的占位路由不视为独立业务实现。
>
> 项目按微服务拆分，路由分属多个 Spring Boot 运行边界：system（`apex-system-service`）、认证中心（`apex-auth-service`）、办公（`apex-office-service`）、文件（`apex-file-service`）、工作流（`apex-workflow-service`）、集成（`apex-integration-service`）、任务代理（`apex-job-service`）、脱敏（`apex-masking-service`）与知识库（`apex-knowledge-service`）。各服务的路由、Handler、应用服务及数据源应分别理解和部署；下文分节按提供方 Router 登记，"system 域"指原 `/api/v1`、`/admin-api/v1` 前缀族的路由集合，不代表全部由 system-service 注册。

## 1. 路径与认证约定

| 路径前缀 | 运行边界 | 典型用途 | 认证约定 |
| --- | --- | --- | --- |
| `/api/v1/**` | system / office / file / workflow 多服务 | 用户业务、办公集成、消息和工作流接口（网关按前缀分流到各服务，未命中前缀由 system-service 兜底） | 默认需要认证；公开端点以安全配置和路由说明为准 |
| `/admin-api/v1/**` | system / office / file / workflow 多服务 | 管理后台和运维接口 | 需要认证；启用 RBAC 时还需命中 `securityProperties.adminRoles` 中的任一角色，具体权限以 Handler 注解为准 |
| `/api/office/**` | office（`apex-office-service`） | 办公平台能力接口 | 默认需要认证 |
| `/auth/sso/**` | system 域 | SSO 登录、回调和登出 | 全部需要认证；当前公开白名单未放行这些路径 |
| `/oauth2/**`、`/.well-known/**` | 认证中心 | OAuth2 授权服务器标准端点 | 网关把该前缀路由到 `apex-auth-service`；仅 `token`、`revoke`、`introspect` 与 `/.well-known/**` 在 Web 安全层公开，其余已注册端点需要认证。system 库仍保留一份历史 `OAuth2RouterConfig`，但网关不路由到它，不能按该实现推断线上契约 |
| `/api/v1/auth/**` | system 域 | 认证、授权及 Keycloak/OIDC 登录接口 | 默认需要认证；Keycloak 的三个登录/回调端点公开且仅在 `keycloak.enabled=true` 时装配 |
| `/api/mfa/**`、`/api/ip-access-control/**` | 认证中心 | MFA 管理和 IP 访问控制 | 需要 Bearer ACCESS 访问令牌 |
| `/api/v1/mfa/**`、`/auth/v1/**` | 认证中心 | MFA、登录、令牌、角色、权限和会话 | 仅 `/auth/v1/login`、`/auth/v1/refresh`、`/auth/v1/mfa/verify`、`/auth/v2/password-recovery/**` 与 `/.well-known/**` 公开，其余需要 Bearer ACCESS 访问令牌 |

 system 域认证约定以 `SecurityConfig` 和 `SystemConstants.PUBLIC_PATHS` 为准。本文业务路由中的公开端点为：`GET /`、`GET /api/v1/health`；`POST /api/v1/auth/login`、`register`、`wechat/bind-phone`、`refresh`、`sms/code`、`cl/sms/code`、`cl/validate-mobile`（后六项均拼接 `/api/v1/auth/` 前缀）；`GET /api/v1/auth/wechat/mp/authorize`（公众号网页授权跳转入口）；`GET /api/v1/auth/captcha`、`POST /api/v1/auth/captcha/verify`；`GET /api/v1/office/login`、`GET /api/v1/office/callback`；`POST /oauth2/token`、`POST /oauth2/revoke`、`POST /oauth2/introspect`；以及 Keycloak 的三个登录/回调端点。`/api/v1/info`、`/api/v1/office/status`、`/api/v1/auth/wx/phone`、`/api/v1/auth/logout` 均需要认证。白名单还包含框架或静态资源路径，不能据此推断存在业务 Router。

管理端在 `rbacEnabled=true` 时按配置角色授权，关闭时 Web 安全层仅要求认证。`securityProperties.adminPaths` 命中的其他管理路径在启用 RBAC 时要求配置的 `adminRole` 或 `SUPER_ADMIN`；`/api/v1/auth/roles/my` 和 `/api/v1/auth/permissions/my` 优先按普通已认证用户放行。Handler 的权限注解仍需独立核对。

认证中心仅匿名放行登录、刷新、MFA 二步交换（`/auth/v1/mfa/verify`）、三条自助密码找回路径与 `/.well-known/**`，其余本文端点均要求 `Authorization: Bearer <access-token>`，且令牌类型必须为 `ACCESS`。两模块各有同名 `AuthConfigRouter` / `AuthConfigHandler`： system 域注册 `/admin-api/v1/auth/config/**`，认证中心注册 `/api/v1/auth/configs/**`，不能合并。

统一响应、错误码和异常出口不由路由表决定。普通业务异常进入统一 WebFlux 异常处理边界，认证失败和权限不足分别由认证入口点与拒绝处理器返回，流控/降级异常由 Sentinel 异常处理器处理。

## 2. system 域路由

### 2.1 用户、办公登录和 Keycloak

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/user/info` | `PublicApiRouter` | `UserHandler.getCurrentUserInfo` | 获取当前用户信息 |
| POST | `/api/v1/user/me/deletion-code` | `PublicApiRouter` | `UserHandler.sendAccountDeletionCode` | 发送账户注销验证码 |
| DELETE | `/api/v1/user/me` | `PublicApiRouter` | `UserHandler.deleteCurrentUser` | 注销当前用户账户 |
| GET | `/api/v1/user/{userId}` | `PublicApiRouter` | `UserHandler.getUserById` | 根据用户 ID 查询用户信息 |
| POST | `/api/v1/user/list` | `PublicApiRouter` | `UserHandler.listUsers` | 分页查询用户列表 |
| GET | `/api/v1/office/status` | `OfficeRouter` | `OfficeHandler.status` | 查询办公平台认证状态 |
| GET | `/api/v1/office/login` | `OfficeRouter` | `OfficeHandler.start` | 发起办公平台登录 |
| GET | `/api/v1/office/callback` | `OfficeRouter` | `OfficeHandler.callback` | 处理办公平台登录回调 |
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
| GET | `/admin-api/v1/staff-roles` | `UserAdminRouter` | `AdminUserManagementHandler.roles` | 查询员工角色 |
| GET | `/admin-api/v1/users` | `UserAdminRouter` | `AdminUserManagementHandler.list` | 查询后台用户 |
| POST | `/admin-api/v1/users` | `UserAdminRouter` | `AdminUserManagementHandler.create` | 创建后台用户 |
| GET | `/admin-api/v1/users/{userId}` | `UserAdminRouter` | `AdminUserManagementHandler.detail` | 查询后台用户详情 |
| PUT | `/admin-api/v1/users/{userId}` | `UserAdminRouter` | `AdminUserManagementHandler.update` | 更新后台用户 |
| POST | `/admin-api/v1/users/{userId}/sessions/revoke` | `UserAdminRouter` | `AdminUserManagementHandler.revokeSessions` | 撤销用户会话 |
| PUT | `/admin-api/v1/users/{userId}/status` | `UserAdminRouter` | `AdminUserManagementHandler.updateStatus` | 更新用户状态 |
| PUT | `/admin-api/v1/users/{userId}/roles` | `UserAdminRouter` | `AdminUserManagementHandler.replaceRoles` | 替换用户角色 |
| GET | `/admin-api/v1/menus` | `RbacRouter` | `MenuHandler.tree` | 查询菜单树 |
| GET | `/admin-api/v1/menus/{menuId}` | `RbacRouter` | `MenuHandler.detail` | 查询菜单详情 |
| POST | `/admin-api/v1/menus` | `RbacRouter` | `MenuHandler.create` | 创建菜单 |
| PUT | `/admin-api/v1/menus/{menuId}` | `RbacRouter` | `MenuHandler.update` | 更新菜单 |
| DELETE | `/admin-api/v1/menus/{menuId}` | `RbacRouter` | `MenuHandler.delete` | 删除菜单 |
| PUT | `/admin-api/v1/menus/{menuId}/status` | `RbacRouter` | `MenuHandler.updateStatus` | 更新菜单状态 |
| GET | `/admin-api/v1/dict/types` | `ConfigRouter` | `DictHandler.listTypes` | 查询字典类型 |
| GET | `/admin-api/v1/dict/types/{id}` | `ConfigRouter` | `DictHandler.getType` | 查询字典类型详情 |
| POST | `/admin-api/v1/dict/types` | `ConfigRouter` | `DictHandler.createType` | 创建字典类型 |
| PUT | `/admin-api/v1/dict/types/{id}` | `ConfigRouter` | `DictHandler.updateType` | 更新字典类型 |
| DELETE | `/admin-api/v1/dict/types/{id}` | `ConfigRouter` | `DictHandler.deleteType` | 删除字典类型 |
| PUT | `/admin-api/v1/dict/types/{id}/status` | `ConfigRouter` | `DictHandler.updateTypeStatus` | 更新字典类型状态 |
| GET | `/admin-api/v1/dict/data` | `ConfigRouter` | `DictHandler.listData` | 查询字典数据 |
| GET | `/admin-api/v1/dict/data/{id}` | `ConfigRouter` | `DictHandler.getData` | 查询字典数据详情 |
| POST | `/admin-api/v1/dict/data` | `ConfigRouter` | `DictHandler.createData` | 创建字典数据 |
| PUT | `/admin-api/v1/dict/data/{id}` | `ConfigRouter` | `DictHandler.updateData` | 更新字典数据 |
| DELETE | `/admin-api/v1/dict/data/{id}` | `ConfigRouter` | `DictHandler.deleteData` | 删除字典数据 |
| PUT | `/admin-api/v1/dict/data/{id}/status` | `ConfigRouter` | `DictHandler.updateDataStatus` | 更新字典数据状态 |
| GET | `/admin-api/v1/depts` | `RbacRouter` | `DeptHandler.listTree` | 查询部门树 |
| GET | `/admin-api/v1/depts/{id}` | `RbacRouter` | `DeptHandler.detail` | 查询部门详情 |
| POST | `/admin-api/v1/depts` | `RbacRouter` | `DeptHandler.create` | 创建部门 |
| PUT | `/admin-api/v1/depts/{id}` | `RbacRouter` | `DeptHandler.update` | 更新部门 |
| DELETE | `/admin-api/v1/depts/{id}` | `RbacRouter` | `DeptHandler.delete` | 删除部门 |
| GET | `/admin-api/v1/export/users` | `ExportRouter` | `ExcelExportHandler.exportUsers` | 导出用户 |

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
| GET | `/admin-api/v1/login-logs` | `LogRouter` | `LoginLogHandler.list` | 查询登录日志 |
| GET | `/admin-api/v1/audit-logs` | `LogRouter` | `AuditLogHandler.list` | 查询审计日志 |
| GET | `/admin-api/v1/api-call-logs` | `LogRouter` | `ApiCallLogHandler.list` | 查询 API 调用日志 |
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
| POST | `/api/office/approvals` | `OfficeApprovalRouter` | `OfficeApprovalHandler.initiateApproval` | 发起审批流程 |
| GET | `/api/office/approvals/{processInstanceId}` | `OfficeApprovalRouter` | `OfficeApprovalHandler.getApprovalDetail` | 查询审批详情 |
| POST | `/api/office/approvals/{processInstanceId}/approve` | `OfficeApprovalRouter` | `OfficeApprovalHandler.approveProcess` | 执行审批操作 |
| DELETE | `/api/office/approvals/{processInstanceId}` | `OfficeApprovalRouter` | `OfficeApprovalHandler.cancelApproval` | 撤销审批（幂等编排：重复请求重放既有撤销单，下游失败走补偿重放） |
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

### 2.8.1 工作流定义生命周期（`/api/v2`）

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/api/v2/workflow-definitions` | `WorkflowDefinitionRouter` | `WorkflowDefinitionHandler.listDefinitions` | 查询工作流定义列表 |
| POST | `/api/v2/workflow-definitions` | `WorkflowDefinitionRouter` | `WorkflowDefinitionHandler.createDraft` | 创建工作流定义草稿 |
| GET | `/api/v2/workflow-definitions/{definitionId}` | `WorkflowDefinitionRouter` | `WorkflowDefinitionHandler.getDefinition` | 查询工作流定义详情 |
| PUT | `/api/v2/workflow-definitions/{definitionId}` | `WorkflowDefinitionRouter` | `WorkflowDefinitionHandler.updateDraft` | 更新工作流定义草稿 |
| POST | `/api/v2/workflow-definitions/{definitionId}/validate` | `WorkflowDefinitionRouter` | `WorkflowDefinitionHandler.validateDefinition` | 校验工作流定义 |
| POST | `/api/v2/workflow-definitions/{definitionId}/publish` | `WorkflowDefinitionRouter` | `WorkflowDefinitionHandler.publishDefinition` | 发布工作流定义 |
| POST | `/api/v2/workflow-definitions/{definitionId}/enable` | `WorkflowDefinitionRouter` | `WorkflowDefinitionHandler.enableDefinition` | 启用工作流定义 |
| POST | `/api/v2/workflow-definitions/{definitionId}/disable` | `WorkflowDefinitionRouter` | `WorkflowDefinitionHandler.disableDefinition` | 停用工作流定义 |
| GET | `/api/v2/workflow-definitions/{definitionId}/versions` | `WorkflowDefinitionRouter` | `WorkflowDefinitionHandler.listVersions` | 查询工作流定义版本列表 |

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
| GET | `/admin-api/v1/generator/templates` | `GeneratorAdminRouter` | `generatorHandler.templates` | 查询生成模板 |
| POST | `/admin-api/v1/generator/preflight` | `GeneratorAdminRouter` | `generatorHandler.preflight` | 生成前预检 |
| POST | `/admin-api/v1/generator/preview` | `GeneratorAdminRouter` | `generatorHandler.preview` | 生成代码预览 |
| GET | `/admin-api/v1/generator/artifacts/{id}/download` | `GeneratorAdminRouter` | `generatorHandler.download` | 按产物 ID 下载 |
| POST | `/admin-api/v1/generator/tasks` | `GeneratorAdminRouter` | `generatorHandler.submit` | 提交异步生成任务 |
| GET | `/admin-api/v1/generator/tasks/{id}` | `GeneratorAdminRouter` | `generatorHandler.taskProgress` | 查询任务进度 |
| GET | `/admin-api/v1/generator/tasks/{id}/artifacts` | `GeneratorAdminRouter` | `generatorHandler.taskArtifacts` | 查询任务产物清单 |
| POST | `/admin-api/v1/generator/tasks/{id}/cancel` | `GeneratorAdminRouter` | `generatorHandler.cancelTask` | 取消生成任务 |

### 2.10 OAuth2

> **运行边界**：`/oauth2/**` 与 `/.well-known/**` 在网关路由到 `apex-auth-service`（见 §1），
> 当前线上契约以 `OauthTokenRouter`、`OauthEndpointRouter`、`OauthMetadataRouter` 为准。
> 下表是 system 库中历史 `OAuth2RouterConfig` 的注册清单，仅在直连 system-service 且该配置被装配时生效，
> 不能作为网关后的实际接口清单。

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
| GET | `/api/v1/auth/captcha` | `CaptchaRouter` | `CaptchaHandler.generate` | 生成图形验证码 |
| POST | `/api/v1/auth/captcha/verify` | `CaptchaRouter` | `CaptchaHandler.verify` | 验证图形验证码 |
| POST | `/api/v1/auth/register` | `PublicApiRouter` | `AuthHandler.register` | 完成用户注册 |
| POST | `/api/v1/auth/wechat/bind-phone` | `PublicApiRouter` | `AuthHandler.bindWechatPhone` | 注册前验证并绑定微信手机号 |
| POST | `/api/v1/auth/login` | `PublicApiRouter` | `AuthHandler.login` | 用户登录 |
| POST | `/api/v1/auth/refresh` | `PublicApiRouter` | `AuthHandler.refresh` | 刷新令牌 |
| POST | `/api/v1/auth/logout` | `PublicApiRouter` | `AuthHandler.logout` | 退出登录 |
| POST | `/api/v1/auth/sms/code` | `PublicApiRouter` | `AuthHandler.sendSmsCode` | 发送短信验证码 |
| POST | `/api/v1/auth/cl/sms/code` | `PublicApiRouter` | `AuthHandler.sendClSmsCode` | 发送创蓝短信验证码 |
| POST | `/api/v1/auth/cl/validate-mobile` | `PublicApiRouter` | `AuthHandler.validateMobile` | 校验本机手机号 |
| POST | `/api/v1/auth/wx/phone` | `PublicApiRouter` | `AuthHandler.bindPhone` | 绑定微信手机号 |
| GET | `/api/v1/auth/wechat/mp/authorize` | `PublicApiRouter` | `AuthHandler.mpAuthorize` | 公众号网页授权跳转（签发一次性 OAuth state 并 302 跳转微信授权页；公开端点，`wechat.mp` 凭据缺失时返回 `wx.channel_not_configured`） |
| GET | `/api/v1/auth/roles/my` | `PublicApiRouter` | `AuthorizationHandler.getMyRoles` | 查询本人角色 |
| GET | `/api/v1/auth/permissions/my` | `PublicApiRouter` | `AuthorizationHandler.getMyPermissions` | 查询本人权限 |
| GET | `/api/v1/auth/roles` | `PublicApiRouter` | `AuthorizationHandler.getAllRoles` | 查询全部角色 |
| GET | `/api/v1/auth/permissions` | `PublicApiRouter` | `AuthorizationHandler.getAllPermissions` | 查询全部权限 |
| POST | `/api/v1/auth/roles` | `PublicApiRouter` | `AuthorizationHandler.createRole` | 创建角色 |
| POST | `/api/v1/auth/roles/update` | `PublicApiRouter` | `AuthorizationHandler.updateRole` | 更新角色 |
| DELETE | `/api/v1/auth/roles/{id}` | `PublicApiRouter` | `AuthorizationHandler.deleteRole` | 删除角色 |
| POST | `/api/v1/auth/permissions` | `PublicApiRouter` | `AuthorizationHandler.createPermission` | 创建权限 |
| POST | `/api/v1/auth/permissions/update` | `PublicApiRouter` | `AuthorizationHandler.updatePermission` | 更新权限 |
| DELETE | `/api/v1/auth/permissions/{id}` | `PublicApiRouter` | `AuthorizationHandler.deletePermission` | 删除权限 |
| POST | `/api/v1/auth/users/{userId}/roles/{roleId}` | `PublicApiRouter` | `AuthorizationHandler.assignRoleToUser` | 为用户分配角色 |
| DELETE | `/api/v1/auth/users/{userId}/roles/{roleId}` | `PublicApiRouter` | `AuthorizationHandler.removeRoleFromUser` | 移除用户角色 |

### 2.12 文件、健康检查和通知

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/notifications/stream` | `NotificationRouter` | `NotificationSseHandler.stream` | 订阅通知 SSE 流 |
| GET | `/api/v1/notifications/inbox` | `SystemMessageRouter` | `SystemMessageHandler.inbox` | 分页查询系统通知收件箱 |
| GET | `/api/v1/notifications/unread-count` | `SystemMessageRouter` | `SystemMessageHandler.unreadCount` | 查询系统通知未读数 |
| POST | `/api/v1/notifications/{id}/read` | `SystemMessageRouter` | `SystemMessageHandler.markRead` | 标记单条系统通知已读 |
| POST | `/api/v1/notifications/read-all` | `SystemMessageRouter` | `SystemMessageHandler.markAllRead` | 系统通知全部标记已读 |
| GET | `/` | `PublicApiRouter` | `HealthHandler.root` | 根路径探活 |
| GET | `/api/v1/health` | `PublicApiRouter` | `HealthHandler.health` | 查询应用健康状态 |
| GET | `/api/v1/info` | `PublicApiRouter` | `HealthHandler.info` | 查询应用信息 |
| POST | `/api/v1/files/generic` | `FileRouter` | `FileHandler.uploadGenericFile` | 上传通用文件（multipart/form-data） |
| POST | `/api/v1/file/upload` | `FileRouter` | `FileHandler.upload` | 上传文件 |
| GET | `/api/v1/file/content` | `FileRouter` | `FileHandler.content` | 读取文件内容 |
| GET | `/api/v1/file/url` | `FileRouter` | `FileHandler.getUrl` | 获取文件 URL |
| DELETE | `/api/v1/file/delete` | `FileRouter` | `FileHandler.delete` | 删除资源 |
| GET | `/api/v1/file/exists` | `FileRouter` | `FileHandler.exists` | 检查文件是否存在 |

### 2.13 系统配置、搜索和日志

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/admin-api/v1/configs` | `ConfigRouter` | `ConfigHandler.list` | 查询系统配置 |
| GET | `/admin-api/v1/configs/{id}` | `ConfigRouter` | `ConfigHandler.detail` | 查询系统配置详情 |
| POST | `/admin-api/v1/configs` | `ConfigRouter` | `ConfigHandler.create` | 创建系统配置 |
| PUT | `/admin-api/v1/configs/{id}` | `ConfigRouter` | `ConfigHandler.update` | 更新系统配置 |
| DELETE | `/admin-api/v1/configs/{id}` | `ConfigRouter` | `ConfigHandler.delete` | 删除系统配置 |
| PUT | `/admin-api/v1/configs/{id}/status` | `ConfigRouter` | `ConfigHandler.updateStatus` | 更新系统配置状态 |
| GET | `/admin-api/v1/search` | `SearchRouter` | `UnifiedSearchHandler.search` | 统一搜索 |
| POST | `/admin-api/v1/search/rebuild` | `SearchRouter` | `UnifiedSearchHandler.rebuildIndex` | 重建搜索索引 |
| GET | `/admin-api/v1/logs/runtime` | `LogRouter` | `RuntimeLogHandler.queryLogs` | 查询日志 |
| GET | `/admin-api/v1/logs/runtime/count` | `LogRouter` | `RuntimeLogHandler.countLogs` | 统计日志数量 |
| GET | `/admin-api/v1/logs/business` | `LogRouter` | `UnifiedBusinessLogHandler.queryLogs` | 查询日志 |
| GET | `/admin-api/v1/logs/business/count` | `LogRouter` | `UnifiedBusinessLogHandler.countLogs` | 统计日志数量 |

### 2.14 在线用户和监控

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/admin-api/v1/online-users` | `MonitorRouter` | `OnlineUserHandler.list` | 查询在线用户 |
| GET | `/admin-api/v1/monitor/overview` | `MonitorRouter` | `MonitorDashboardHandler.overview` | 查询监控概览 |
| GET | `/admin-api/v1/monitor/system` | `MonitorRouter` | `MonitorDashboardHandler.system` | 查询系统监控 |
| GET | `/admin-api/v1/monitor/middleware` | `MonitorRouter` | `MonitorDashboardHandler.middleware` | 查询中间件监控 |
| GET | `/admin-api/v1/monitor/governance` | `MonitorRouter` | `MonitorDashboardHandler.governance` | 查询治理监控 |
| GET | `/admin-api/v1/monitor/business` | `MonitorRouter` | `MonitorDashboardHandler.business` | 查询业务监控 |

### 2.15 任务和数据库配置

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/admin-api/v1/jobs` | `JobRouter` | `JobHandler.list` | 查询任务列表 |
| GET | `/admin-api/v1/jobs/page` | `JobRouter` | `JobHandler.page` | 分页查询任务 |
| GET | `/admin-api/v1/jobs/{id}` | `JobRouter` | `JobHandler.detail` | 查询任务详情 |
| POST | `/admin-api/v1/jobs` | `JobRouter` | `JobHandler.create` | 创建任务 |
| PUT | `/admin-api/v1/jobs/{id}` | `JobRouter` | `JobHandler.update` | 更新任务 |
| DELETE | `/admin-api/v1/jobs/{id}` | `JobRouter` | `JobHandler.delete` | 删除任务 |
| PUT | `/admin-api/v1/jobs/{id}/status` | `JobRouter` | `JobHandler.updateStatus` | 更新任务状态 |
| POST | `/admin-api/v1/jobs/{id}/run` | `JobRouter` | `JobHandler.runOnce` | 立即执行任务 |
| GET | `/admin-api/v1/jobs/{id}/logs` | `JobRouter` | `JobHandler.logs` | 查询任务日志 |
| POST | `/admin-api/v1/database/test` | `DatabaseConfigRouter` | `DatabaseConfigHandler.testConnection` | 测试数据库连接 |
| POST | `/admin-api/v1/database/generate-config` | `DatabaseConfigRouter` | `DatabaseConfigHandler.generateConfig` | 生成数据库配置 |
| GET | `/admin-api/v1/database/current` | `DatabaseConfigRouter` | `DatabaseConfigHandler.getCurrentInfo` | 查询当前数据库信息 |
| GET | `/admin-api/v1/database/datasources` | `DatabaseConfigRouter` | `DatabaseConfigHandler.listDataSources` | 查询数据源 |
| POST | `/admin-api/v1/database/datasources` | `DatabaseConfigRouter` | `DatabaseConfigHandler.createDataSource` | 创建数据源 |
| PUT | `/admin-api/v1/database/datasources/{id}` | `DatabaseConfigRouter` | `DatabaseConfigHandler.updateDataSource` | 更新数据源 |
| DELETE | `/admin-api/v1/database/datasources/{id}` | `DatabaseConfigRouter` | `DatabaseConfigHandler.deleteDataSource` | 删除数据源 |
| GET | `/admin-api/v1/database/datasources/{id}/metrics` | `DatabaseConfigRouter` | `DatabaseConfigHandler.getDataSourceMetrics` | 查询数据源指标 |

### 2.16 能力、平台治理与开放应用管理（system，`/api/v2` 与 `/open-api`）

> 运行边界：`apex-system-service`（Router 位于 `apex-system` 的 `interfaces/router`）。网关已为
> `/api/v2/operations|platform|tenant-packages|capabilities|capability-usage|overview/**` 显式放行。

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/api/v2/capabilities` | `CapabilityRouter` | `CapabilityHandler.list` | 能力目录查询 |
| GET | `/api/v2/capabilities/subscribed` | `CapabilityRouter` | `CapabilityHandler.subscribed` | 当前租户已订阅能力（网关能力快照同源） |
| GET | `/api/v2/capability-usage` | `CapabilityUsageRouter` | `CapabilityUsageHandler.queryUsage` | 能力用量查询（`capability:usage:read` 门控） |
| GET | `/api/v2/operations/health-report` | `OperationsRouter` | `OperationsHandler` | 运维健康报告 |
| POST/GET | `/api/v2/operations/health-snapshots` | `OperationsRouter` | `OperationsHandler` | 健康快照创建与分页查询 |
| GET | `/api/v2/operations/health-snapshots/export` | `OperationsRouter` | `OperationsHandler` | 快照导出查询 |
| POST/GET | `/api/v2/operations/health-snapshots/export-tasks` | `OperationsRouter` | `OperationsHandler` | 导出任务创建与查询 |
| GET | `/api/v2/operations/health-snapshots/export-tasks/{id}` | `OperationsRouter` | `OperationsHandler` | 导出任务详情 |
| GET | `/api/v2/operations/health-snapshots/export-tasks/{id}/download` | `OperationsRouter` | `OperationsHandler` | 导出结果下载 |
| GET | `/api/v2/operations/health-trend` | `OperationsRouter` | `OperationsHandler` | 健康趋势查询 |
| GET | `/api/v2/operations/tenant-health-summary` | `OperationsRouter` | `OperationsHandler` | 租户健康汇总 |
| GET/POST | `/api/v2/platform/tenants` | `PlatformTenantRouter` | `PlatformTenantHandler.page/create` | 平台租户分页与创建 |
| GET | `/api/v2/platform/tenants/{id}` | `PlatformTenantRouter` | `PlatformTenantHandler.detail` | 平台租户详情 |
| POST | `/api/v2/platform/tenants/{id}/actions` | `PlatformTenantRouter` | `PlatformTenantHandler.changeStatus` | 平台租户状态变更 |
| PUT | `/api/v2/platform/tenants/{id}/quota` | `PlatformTenantRouter` | `PlatformTenantHandler.updateQuota` | 平台租户配额调整 |
| GET/POST/PUT | `/api/v2/tenant-packages`（含 `/{packageCode}`） | `TenantPackageRouter` | `TenantPackageHandler` | 租户套餐查询/创建/更新 |
| GET | `/api/v2/overview` | `PlatformOverviewRouter` | `PlatformOverviewHandler.overview` | 平台总览聚合 |
| GET/POST | `/api/v1/open-apps` | `OpenAppAdminRouter` | `OpenAppAdminHandler` | 开放应用列表与注册 |
| GET | `/api/v1/open-apps/scopes` | `OpenAppAdminRouter` | `OpenAppAdminHandler.scopes` | 开放 scope 目录 |
| GET/PUT | `/api/v1/open-apps/{clientId}` | `OpenAppAdminRouter` | `OpenAppAdminHandler` | 开放应用详情与更新 |
| POST | `/api/v1/open-apps/{clientId}/reset-secret` | `OpenAppAdminRouter` | `OpenAppAdminHandler.resetSecret` | 重置应用密钥 |
| GET | `/admin-api/v1/open-apps/migration/export` | `OpenAppMigrationRouter` | `OpenAppMigrationHandler.export` | 开放应用迁移导出 |
| POST | `/admin-api/v1/open-apps/migration/import` | `OpenAppMigrationRouter` | `OpenAppMigrationHandler.import` | 开放应用迁移导入 |
| GET | `/open-api/users` | `OpenUserRouter` | `OpenUserHandler` | 开放 API 用户信息端点 |

### 2.17 办公与文件服务补充端点（office / file 运行模块）

> 运行边界：`apex-office-service`、`apex-file-service`（Router 位于各自 service 运行模块）。

| 方法 | 路径 | Router | 说明 |
| --- | --- | --- | --- |
| POST/GET | `/api/v2/office/meeting-rooms` | `OfficeMeetingRoomRouter` | 会议室创建与列表 |
| GET | `/api/v2/office/meeting-rooms/enabled`、`/available`、`/{roomId}` | `OfficeMeetingRoomRouter` | 会议室可用/详情查询 |
| PUT/DELETE | `/api/v2/office/meeting-rooms/{roomId}` | `OfficeMeetingRoomRouter` | 会议室更新与删除 |
| POST/DELETE | `/api/v2/office/meeting-rooms/{roomId}/bookings`（取消含 `/{eventId}`） | `OfficeMeetingRoomRouter` | 会议室预订与取消 |
| POST | `/api/v2/office/sync/preflight`、`/preview`、`/tasks`、`/tasks/{taskId}/retry` | `OfficeSyncWizardRouter` | 办公平台组织同步向导（前缀 `/api/v2/office/sync`） |
| GET | `/api/v2/office/sync/tasks`、`/tasks/{taskId}` | `OfficeSyncWizardRouter` | 同步任务查询 |
| POST/GET | `/api/v2/office/notify/templates` | `OfficeNotifyRouter` | 通知模板创建与列表 |
| GET | `/api/v2/office/notify/requests` | `OfficeNotifyRouter` | 通知记录查询 |
| GET/PUT/DELETE | `/api/v2/office/notify/templates/{templateId}` | `OfficeNotifyRouter` | 通知模板详情/更新/删除 |
| GET | `/api/v1/messages/inbox/cursor` | `MessageRouter` | 站内信游标分页收件箱 |
| GET | `/api/v1/messages/{messageId}/deliveries` | `MessageRouter` | 站内信投递明细 |
| GET | `/admin-api/v1/office/bindings/page` | `OfficeAdminRouter` | 办公绑定分页查询 |
| POST | `/api/v1/file/chunk/init` | `ChunkRouter` | 初始化分片上传 |
| POST | `/api/v1/file/chunk/upload` | `ChunkRouter` | 上传分片 |
| POST | `/api/v1/file/chunk/merge` | `ChunkRouter` | 合并分片 |
| GET | `/api/v1/file/chunk/status/{fileId}` | `ChunkRouter` | 查询分片状态 |
| POST | `/api/v1/file/chunk/cleanup/{fileId}/retry` | `ChunkRouter` | 清理分片重试 |
| GET | `/api/v1/file/preview` | `FileRouter` | 文件预览 |
| POST | `/api/v1/file/orphan/{fileId}/retry` | `FileRouter` | 孤儿文件重试 |
| GET | `/api/v1/files`、`/api/v1/files/generic` | `FileRouter` | 文件列表与通用查询 |
| POST/GET | `/api/v2/files/{fileId}/shares` | `FileShareRouter` | 创建与查询文件分享 |
| DELETE | `/api/v2/file-shares/{shareId}` | `FileShareRouter` | 撤销文件分享 |
| POST | `/api/v2/file-shares/exchange`、`/api/v2/file-shares/download` | `FileShareRouter` | 公开兑换码换票与下载（`SystemConstants.PUBLIC_PATHS` 放行） |

### 2.18 查询聚合服务端点（`apex-aggregation-service` 运行模块）

> 运行边界：`apex-aggregation-service`（Router 位于运行模块）。网关 `aggregation-domain` 路由（`ROUTE_AGGREGATION_URI`，默认 `lb://apex-aggregation-service`）承载 `/api/v2/todo`；服务自验访问 JWT，无本地数据库。workflow/office 供其调用的 `/internal/v1/*/todo` 内部端点不在本清单登记，见 `internal-api-contracts.md`。

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/api/v2/todo` | `UnifiedTodoRouter` | `UnifiedTodoHandler.todo` | 当前用户跨域统一待办聚合（workflow 任务 + office 待审批/未读站内信）；单源降级部分可用，个人维度不缓存 |

### 2.19 知识库服务端点（`apex-knowledge-service` 运行模块）

> 运行边界：`apex-knowledge-service`（可选运行模块）。网关 `knowledge-domain` 路由（`ROUTE_KNOWLEDGE_URI`，默认 `http://127.0.0.1:8100`，固定地址口径与 job/masking 一致）承载 `/api/v1/kb/**` 与 `/admin-api/v1/kb/**`；服务独立验签访问 JWT，独立 `apex_knowledge` 库。细粒度权限由本地 `KnowledgeSecurityAspect` 按权限码（`kb:*`）经授权中心快照校验。检索/问答依赖 Meilisearch 索引与模型供应商，未启用时按能力级降级（检索空结果、问答显式报错或模板兜底）。

| 方法 | 路径 | Router | Handler 方法 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/admin-api/v1/kb/spaces` | `KnowledgeSpaceRouter` | `KnowledgeSpaceHandler.page` | 空间分页（`kb:space:read`） |
| GET | `/admin-api/v1/kb/spaces/{spaceId}` | `KnowledgeSpaceRouter` | `KnowledgeSpaceHandler.detail` | 空间详情（`kb:space:read`） |
| POST | `/admin-api/v1/kb/spaces` | `KnowledgeSpaceRouter` | `KnowledgeSpaceHandler.create` | 创建空间（`kb:space:create`） |
| PUT | `/admin-api/v1/kb/spaces/{spaceId}` | `KnowledgeSpaceRouter` | `KnowledgeSpaceHandler.update` | 更新空间（`kb:space:update`） |
| DELETE | `/admin-api/v1/kb/spaces/{spaceId}` | `KnowledgeSpaceRouter` | `KnowledgeSpaceHandler.delete` | 删除空间（`kb:space:delete`；空间下仍有文档时拒绝） |
| POST | `/admin-api/v1/kb/spaces/{spaceId}/documents` | `KnowledgeDocumentRouter` | `KnowledgeDocumentHandler.upload` | multipart 上传文档（`kb:doc:upload`），异步解析摄取 |
| POST | `/admin-api/v1/kb/manual-documents` | `KnowledgeDocumentRouter` | `KnowledgeDocumentHandler.registerManual` | 在线录入纯文本文档（`kb:doc:upload`） |
| GET | `/admin-api/v1/kb/documents` | `KnowledgeDocumentRouter` | `KnowledgeDocumentHandler.page` | 文档分页（`kb:doc:read`） |
| GET | `/admin-api/v1/kb/documents/{docId}` | `KnowledgeDocumentRouter` | `KnowledgeDocumentHandler.detail` | 文档详情含分块预览（`kb:doc:read`） |
| DELETE | `/admin-api/v1/kb/documents/{docId}` | `KnowledgeDocumentRouter` | `KnowledgeDocumentHandler.delete` | 接受删除意图并隐藏文档，后台清理对象、分块与索引（`kb:doc:delete`；布尔成功不等于全部资源已清理） |
| POST | `/admin-api/v1/kb/documents/{docId}/reindex` | `KnowledgeDocumentRouter` | `KnowledgeDocumentHandler.reindex` | 手动重建索引（`kb:doc:reindex`） |
| GET | `/admin-api/v1/kb/document-cleanups` | `KnowledgeDocumentRouter` | `KnowledgeDocumentHandler.cleanupPage` | 当前租户删除清理记录分页，page/size（`kb:doc:read`） |
| GET | `/admin-api/v1/kb/document-cleanups/{docId}` | `KnowledgeDocumentRouter` | `KnowledgeDocumentHandler.cleanupDetail` | 删除清理状态与安全错误摘要（`kb:doc:read`） |
| POST | `/admin-api/v1/kb/document-cleanups/{docId}/retry` | `KnowledgeDocumentRouter` | `KnowledgeDocumentHandler.retryCleanup` | 接受清理重试，布尔结果不表示完成（`kb:doc:delete`） |
| GET | `/api/v1/kb/spaces` | `KnowledgeSearchRouter` | `KnowledgeSearchHandler.spaces` | 可用空间列表（登录即可） |
| GET | `/api/v1/kb/search` | `KnowledgeSearchRouter` | `KnowledgeSearchHandler.search` | 混合检索（`kb:search:query`；hybrid 优先、词法兜底） |
| POST | `/api/v1/kb/chat/ask` | `KnowledgeChatRouter` | `KnowledgeChatHandler.ask` | SSE 流式问答（`kb:chat:ask`；事件 citations/delta/done/error） |
| GET | `/api/v1/kb/chat/conversations` | `KnowledgeChatRouter` | `KnowledgeChatHandler.conversations` | 当前用户会话分页（登录即可） |
| GET | `/api/v1/kb/chat/conversations/{conversationId}/messages` | `KnowledgeChatRouter` | `KnowledgeChatHandler.messages` | 会话消息（归属用户校验） |
| DELETE | `/api/v1/kb/chat/conversations/{conversationId}` | `KnowledgeChatRouter` | `KnowledgeChatHandler.deleteConversation` | 删除会话（归属用户校验） |

问答流的错误事件统一为 `event: error` 与 JSON data `{code: string, message: string}`，业务消息按已有业务异常口径展示，技术异常使用安全中文兜底，不外泄内部原因。身份/请求校验/会话建立等进入 Handler 错误恢复的失败也采用该结构，HTTP 仍为 200 SSE；安全链或权限切面在进入 Handler 前拒绝时仍返回 401/403，不转换为成功流。

## 3. 独立认证中心路由

### 3.1 登录、注销和令牌

| 方法 | 路径 | Router | Handler 方法 | 认证说明 |
| --- | --- | --- | --- | --- |
| POST | `/auth/v1/login` | `AuthenticationRouter` | `AuthenticationHandler.login` | 无需认证；用户名密码登录 |
| POST | `/auth/v1/logout/{userId}` | `AuthenticationRouter` | `AuthenticationHandler.logout` | 需要 Bearer 访问令牌；路径用户必须与认证主体一致 |
| POST | `/auth/v1/mfa/verify` | `AuthenticationRouter` | `AuthenticationHandler.verifyMfa` | 无需访问令牌；以登录返回的挑战凭据 + TOTP 交换正式令牌，挑战一次性消费 |
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
请求体中的 `userId` / `tenantId` 仅用于与认证主体比对；不一致返回 403，实际执行始终使用令牌中的身份。

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

### 3.7 密码找回（`PasswordRecoveryRouter`）

| 方法 | 路径 | Handler 方法 | 认证说明 |
| --- | --- | --- | --- |
| POST | `/auth/v2/password-recovery/requests` | `PasswordRecoveryHandler.initiate` | 公开；发起找回请求 |
| POST | `/auth/v2/password-recovery/verifications` | `PasswordRecoveryHandler.verify` | 公开；校验验证凭据 |
| POST | `/auth/v2/password-recovery/confirmations` | `PasswordRecoveryHandler.confirm` | 公开；确认并重置密码 |

### 3.8 auth 内部接口（`InternalAuthRouter`）

| 方法 | 路径 | Handler 方法 | 说明 |
| --- | --- | --- | --- |
| POST | `/internal/v1/tokens/validate` | `validateToken` | 校验存量 Token 并返回用户语义快照 |
| GET | `/internal/v1/tokens/revocations` | `tokenRevocations` | 撤销列表增量拉取（网关本地缓存） |
| GET | `/internal/v1/users/{accountId}/permissions` | `getUserPermissions` | 查询用户权限码集合 |
| POST/GET | `/internal/v1/open-apps` | `registry.create/list` | 开放应用注册与列表 |
| GET/PUT/DELETE | `/internal/v1/open-apps/{clientId}` | `registry.get/update/delete` | 开放应用详情、更新与删除 |
| PUT | `/internal/v1/open-apps/{clientId}/status` | `registry.status` | 开放应用状态变更 |
| POST | `/internal/v1/open-apps/{clientId}/reset-secret` | `registry.rotateSecret` | 重置应用密钥 |
| POST | `/internal/v1/open-apps/import` | `registry.importApp` | 开放应用批量导入 |

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
4. 认证中心路由和 system 域路由分别维护，不能因为路径相似而合并。
5. 路由行为发生变化后，应运行 `scripts/ci/check-doc-consistency.sh`，另做方法、完整路径、运行边界及 Handler 映射的集合比对；该脚本只检查本文件存在性，不能证明路由完整。涉及代码时根据需要运行 Spotless 和项目测试。

### 4.4 核对范围

当前清单登记 410 条方法与路径注册：§2（`/api`、`/admin-api`、`/open-api` 前缀族，含 office/file/workflow/aggregation 等运行模块注册）330 条，§3 独立认证中心（`apex-auth-service`）80 条，来自本文列出的各 Router 类。Keycloak 的 6 条条件装配路由包含在 §2 计数中；405 fallback、OpenAPI 占位、示例和框架端点不计入。统计按运行边界区分相同类名，并展开全部嵌套前缀和路径常量。

> 计数口径：仅为本文表格行数（2026-11-11 按方法列重算，2026-11-13 增补 §2.18 聚合服务，合并单元格如 `POST/GET` 计 1 条），用于快速核对文档自身完整性；新增或删除端点时应同步更新本表，路由完整性以各 Router 源码为准。

## 5. 主要源码索引

system 域（`apex-system-service` 装配，业务实现在 `apex-system`）：

- 路由包：`apex-system/src/main/java/com/apex/scaffold/interfaces/router/`
- 用户与认证路由：`PublicApiRouter.java`、`CaptchaRouter.java`、`AuthConfigRouter.java`
- 后台用户与 RBAC 路由：`UserAdminRouter.java`、`RbacRouter.java`
- 配置、字典、搜索、日志路由：`ConfigRouter.java`、`SearchRouter.java`、`LogRouter.java`
- 任务与数据库配置路由：`JobRouter.java`、`DatabaseConfigRouter.java`
- 监控、在线用户与导出：`MonitorRouter.java`、`ExportRouter.java`
- OAuth2 与 Keycloak 路由配置：`apex-system/src/main/java/com/apex/scaffold/interfaces/config/OAuth2RouterConfig.java`、`KeycloakRouterConfig.java`
- 安全规则：`apex-system/src/main/java/com/apex/scaffold/interfaces/config/SecurityConfig.java`、`apex-common/src/main/java/com/apex/scaffold/common/constant/SystemConstants.java`

认证中心（`apex-auth-service`）：

- 认证、MFA 与 OAuth2 端点：`apex-auth-service/src/main/java/com/apex/scaffold/authservice/interfaces/router/`
- MFA 与 IP 访问控制路由装配：`apex-auth-service/src/main/java/com/apex/scaffold/authservice/config/AuthCenterRouterConfig.java`
- 配置及审计：同目录下 `AuthConfigRouter.java`、`AuditLogRouter.java`
- 安全规则：`apex-auth-service/src/main/java/com/apex/scaffold/authservice/config/AuthCenterConfig.java`

其他业务服务：

- 工作流路由：`apex-workflow-service/src/main/java/com/apex/scaffold/workflowservice/interfaces/router/`（`WorkflowRouter.java`、`WorkflowDefinitionRouter.java`）
- 文件路由：`apex-file-service/src/main/java/com/apex/scaffold/fileservice/interfaces/router/`
- 办公路由：`apex-office-service/src/main/java/com/apex/scaffold/officeservice/interfaces/router/`（含 `office/` 子包）
- 集成路由：`apex-integration/src/main/java/com/apex/scaffold/integration/`
