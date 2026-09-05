# 前后端API路由对比清单

> 生成时间: 2026-09-03
> 目的: 验证前端所有API调用是否都有后端路由支持,排查404问题

## 一、认证授权模块 (`/api/v1/auth`)

| 前端API            | HTTP方法 | 路径                            | 后端路由                            | 状态 |
| ---------------- | ------ | ----------------------------- | ------------------------------- | -- |
| getCaptcha       | GET    | `/api/v1/auth/captcha`        | ✅ captchaRoutes:269             | 正常 |
| login            | POST   | `/api/v1/auth/login`          | ✅ apiRoutes→buildAuthRoutes:403 | 正常 |
| logout           | POST   | `/api/v1/auth/logout`         | ✅ apiRoutes→buildAuthRoutes:405 | 正常 |
| getMyRoles       | GET    | `/api/v1/auth/roles/my`       | ✅ apiRoutes→buildAuthRoutes:410 | 正常 |
| getMyPermissions | GET    | `/api/v1/auth/permissions/my` | ✅ apiRoutes→buildAuthRoutes:411 | 正常 |

**结论**: 认证授权模块所有5个前端API调用均有后端路由支持 ✅

***

## 二、用户管理模块

### 2.1 当前用户 (`/api/v1/user`)

| 前端API              | HTTP方法 | 路径                  | 后端路由                            | 状态 |
| ------------------ | ------ | ------------------- | ------------------------------- | -- |
| getCurrentUserInfo | GET    | `/api/v1/user/info` | ✅ apiRoutes→buildUserRoutes:435 | 正常 |

### 2.2 后台用户管理 (`/admin-api/v1/users`)

| 前端API              | HTTP方法 | 路径                                             | 后端路由                           | 状态       |
| ------------------ | ------ | ---------------------------------------------- | ------------------------------ | -------- |
| listUsers          | GET    | `/admin-api/v1/users`                          | ✅ adminUserManagementRoutes:60 | 正常       |
| getUserDetail      | GET    | `/admin-api/v1/users/{userId}`                 | ✅ adminUserManagementRoutes:62 | 正常       |
| createUser         | POST   | `/admin-api/v1/users`                          | ✅ adminUserManagementRoutes:61 | 正常       |
| updateUser         | PUT    | `/admin-api/v1/users/{userId}`                 | ✅ adminUserManagementRoutes:63 | 正常       |
| updateUserStatus   | PUT    | `/admin-api/v1/users/{userId}/status`          | ✅ adminUserManagementRoutes:65 | 正常       |
| revokeUserSessions | POST   | `/admin-api/v1/users/{userId}/sessions/revoke` | ✅ adminUserManagementRoutes:64 | 正常       |
| listStaffRoles     | GET    | `/admin-api/v1/staff-roles`                    | ✅ adminUserManagementRoutes:59 | 正常       |
| assignUserRoles    | PUT    | `/admin-api/v1/users/{userId}/roles`           | ✅ adminUserManagementRoutes:66 | 正常       |
| deleteUser         | DELETE | `/admin-api/v1/users/{userId}`                 | ⚠️ 未实现                         | **预留接口** |

**结论**: 用户管理模块9个API,8个正常,1个为预留接口(前端已标注) ⚠️

***

## 三、系统管理模块 (`/admin-api/v1`)

### 3.1 菜单管理 (`/menus`)

| 前端API            | HTTP方法 | 路径                                | 后端路由                 | 状态 |
| ---------------- | ------ | --------------------------------- | -------------------- | -- |
| listMenuTree     | GET    | `/admin-api/v1/menus`             | ✅ adminMenuRoutes:82 | 正常 |
| getMenuDetail    | GET    | `/admin-api/v1/menus/{id}`        | ✅ adminMenuRoutes:83 | 正常 |
| createMenu       | POST   | `/admin-api/v1/menus`             | ✅ adminMenuRoutes:84 | 正常 |
| updateMenu       | PUT    | `/admin-api/v1/menus/{id}`        | ✅ adminMenuRoutes:85 | 正常 |
| deleteMenu       | DELETE | `/admin-api/v1/menus/{id}`        | ✅ adminMenuRoutes:86 | 正常 |
| updateMenuStatus | PUT    | `/admin-api/v1/menus/{id}/status` | ✅ adminMenuRoutes:87 | 正常 |

### 3.2 部门管理 (`/depts`)

| 前端API        | HTTP方法 | 路径                         | 后端路由                  | 状态 |
| ------------ | ------ | -------------------------- | --------------------- | -- |
| listDeptTree | GET    | `/admin-api/v1/depts`      | ✅ adminDeptRoutes:181 | 正常 |
| createDept   | POST   | `/admin-api/v1/depts`      | ✅ adminDeptRoutes:183 | 正常 |
| updateDept   | PUT    | `/admin-api/v1/depts/{id}` | ✅ adminDeptRoutes:184 | 正常 |
| deleteDept   | DELETE | `/admin-api/v1/depts/{id}` | ✅ adminDeptRoutes:185 | 正常 |

**注意**: 前端未调用部门详情接口,但后端有 GET `/admin-api/v1/depts/{id}` (line 182)

### 3.3 字典管理 (`/dict`)

| 前端API                | HTTP方法 | 路径                                     | 后端路由                  | 状态 |
| -------------------- | ------ | -------------------------------------- | --------------------- | -- |
| listDictTypes        | GET    | `/admin-api/v1/dict/types`             | ✅ adminDictRoutes:103 | 正常 |
| createDictType       | POST   | `/admin-api/v1/dict/types`             | ✅ adminDictRoutes:105 | 正常 |
| updateDictType       | PUT    | `/admin-api/v1/dict/types/{id}`        | ✅ adminDictRoutes:106 | 正常 |
| deleteDictType       | DELETE | `/admin-api/v1/dict/types/{id}`        | ✅ adminDictRoutes:107 | 正常 |
| updateDictTypeStatus | PUT    | `/admin-api/v1/dict/types/{id}/status` | ✅ adminDictRoutes:108 | 正常 |
| listDictData         | GET    | `/admin-api/v1/dict/data`              | ✅ adminDictRoutes:109 | 正常 |
| createDictData       | POST   | `/admin-api/v1/dict/data`              | ✅ adminDictRoutes:111 | 正常 |
| updateDictData       | PUT    | `/admin-api/v1/dict/data/{id}`         | ✅ adminDictRoutes:112 | 正常 |
| deleteDictData       | DELETE | `/admin-api/v1/dict/data/{id}`         | ✅ adminDictRoutes:113 | 正常 |
| updateDictDataStatus | PUT    | `/admin-api/v1/dict/data/{id}/status`  | ✅ adminDictRoutes:114 | 正常 |

**注意**: 前端未调用字典详情接口,但后端有 GET `/admin-api/v1/dict/types/{id}` (line 104) 和 GET `/admin-api/v1/dict/data/{id}` (line 110)

### 3.4 参数配置 (`/configs`)

| 前端API              | HTTP方法 | 路径                                  | 后端路由                    | 状态 |
| ------------------ | ------ | ----------------------------------- | ----------------------- | -- |
| listConfigs        | GET    | `/admin-api/v1/configs`             | ✅ adminConfigRoutes:310 | 正常 |
| createConfig       | POST   | `/admin-api/v1/configs`             | ✅ adminConfigRoutes:312 | 正常 |
| updateConfig       | PUT    | `/admin-api/v1/configs/{id}`        | ✅ adminConfigRoutes:313 | 正常 |
| deleteConfig       | DELETE | `/admin-api/v1/configs/{id}`        | ✅ adminConfigRoutes:314 | 正常 |
| updateConfigStatus | PUT    | `/admin-api/v1/configs/{id}/status` | ✅ adminConfigRoutes:315 | 正常 |

**注意**: 前端未调用配置详情接口,但后端有 GET `/admin-api/v1/configs/{id}` (line 311)

### 3.5 定时任务 (`/jobs`)

| 前端API           | HTTP方法 | 路径                               | 后端路由                 | 状态 |
| --------------- | ------ | -------------------------------- | -------------------- | -- |
| listJobs        | GET    | `/admin-api/v1/jobs`             | ✅ adminJobRoutes:287 | 正常 |
| createJob       | POST   | `/admin-api/v1/jobs`             | ✅ adminJobRoutes:289 | 正常 |
| updateJob       | PUT    | `/admin-api/v1/jobs/{id}`        | ✅ adminJobRoutes:290 | 正常 |
| deleteJob       | DELETE | `/admin-api/v1/jobs/{id}`        | ✅ adminJobRoutes:291 | 正常 |
| updateJobStatus | PUT    | `/admin-api/v1/jobs/{id}/status` | ✅ adminJobRoutes:292 | 正常 |
| runJobOnce      | POST   | `/admin-api/v1/jobs/{id}/run`    | ✅ adminJobRoutes:293 | 正常 |
| listJobLogs     | GET    | `/admin-api/v1/jobs/{id}/logs`   | ✅ adminJobRoutes:294 | 正常 |

**注意**: 前端未调用任务详情接口,但后端有 GET `/admin-api/v1/jobs/{id}` (line 288)

**结论**: 系统管理模块所有33个前端API调用均有后端路由支持 ✅

***

## 四、监控审计模块 (`/admin-api/v1`)

### 4.1 日志查询

| 前端API           | HTTP方法 | 路径                            | 后端路由                        | 状态 |
| --------------- | ------ | ----------------------------- | --------------------------- | -- |
| listLoginLogs   | GET    | `/admin-api/v1/login-logs`    | ✅ adminLoginLogRoutes:130   | 正常 |
| listAuditLogs   | GET    | `/admin-api/v1/audit-logs`    | ✅ adminAuditLogRoutes:147   | 正常 |
| listApiCallLogs | GET    | `/admin-api/v1/api-call-logs` | ✅ adminApiCallLogRoutes:165 | 正常 |

### 4.2 在线用户

| 前端API           | HTTP方法 | 路径                           | 后端路由                        | 状态 |
| --------------- | ------ | ---------------------------- | --------------------------- | -- |
| listOnlineUsers | GET    | `/admin-api/v1/online-users` | ✅ adminOnlineUserRoutes:233 | 正常 |

### 4.3 监控大盘

| 前端API                | HTTP方法 | 路径                                 | 后端路由                     | 状态 |
| -------------------- | ------ | ---------------------------------- | ------------------------ | -- |
| getMonitorOverview   | GET    | `/admin-api/v1/monitor/overview`   | ✅ adminMonitorRoutes:250 | 正常 |
| getMonitorSystem     | GET    | `/admin-api/v1/monitor/system`     | ✅ adminMonitorRoutes:251 | 正常 |
| getMonitorMiddleware | GET    | `/admin-api/v1/monitor/middleware` | ✅ adminMonitorRoutes:252 | 正常 |
| getMonitorGovernance | GET    | `/admin-api/v1/monitor/governance` | ✅ adminMonitorRoutes:253 | 正常 |
| getMonitorBusiness   | GET    | `/admin-api/v1/monitor/business`   | ✅ adminMonitorRoutes:254 | 正常 |

**结论**: 监控审计模块所有9个前端API调用均有后端路由支持 ✅

***

## 五、总结

### 5.1 整体统计

- **前端API总数**: 56个

- **后端已实现**: 55个 ✅

- **预留接口(未实现)**: 1个 ⚠️

- **潜在404风险**: 0个 ✅

### 5.2 预留接口清单

| 前端API      | 路径                                    | 说明                                |
| ---------- | ------------------------------------- | --------------------------------- |
| deleteUser | DELETE `/admin-api/v1/users/{userId}` | 前端已标注"预留接口,后端当前未暴露DELETE,保留供后续扩展" |

### 5.3 后端已实现但前端未调用的接口

以下后端接口已实现但前端暂未调用,可能是功能待扩展:

1. GET `/admin-api/v1/depts/{id}` - 部门详情
2. GET `/admin-api/v1/dict/types/{id}` - 字典类型详情
3. GET `/admin-api/v1/dict/data/{id}` - 字典数据详情
4. GET `/admin-api/v1/configs/{id}` - 参数配置详情
5. GET `/admin-api/v1/jobs/{id}` - 定时任务详情
6. `/admin-api/v1/workflows/*` - 工作流引擎模块(前端无对应API文件)

### 5.4 最终结论

✅ **前后端路由对接完整,不存在会导致功能不可用的404问题**

唯一的 `deleteUser` 接口已在前端代码中明确标注为预留接口,不影响当前系统使用。

***

## 六、建议

### 6.1 立即处理

- 无需立即处理的404问题

### 6.2 后续优化

1. 考虑补充前端对部门/字典/配置/任务详情接口的调用(如需要详情页功能)
2. 评估是否需要实现用户删除功能并补充后端DELETE路由
3. 评估工作流模块是否需要前端页面支持

### 6.3 文档建议

- 建议在API文档中明确标注哪些接口为预留接口

- 建议补充工作流模块的前端实现规划

