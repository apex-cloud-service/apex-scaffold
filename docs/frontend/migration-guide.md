# 前端页面功能整合迁移到 apex-admin-web

## 摘要

将 `apex-scaffold` 后端工程内嵌的静态前端（`apex-interfaces/src/main/resources/static/admin/`，原生 HTML/JS/CSS 实现的管理控制台）整合到独立的前端工程 `apex-admin-web`（Vue 3 + Vite + TS + Pinia + Element Plus），迁移完成后删除后端工程内的静态前端内容，并清理后端为托管该静态前端而存在的重定向路由，确保所有原功能在新前端工程中可正常使用。

## 现状分析

### 后端内嵌静态前端（待删除源）

位置：`/Users/work/apex-scaffold/apex-interfaces/src/main/resources/static/admin/`

| 文件 | 职责 |
|------|------|
| `index.html` | 管理控制台入口 HTML，含登录视图 + 主应用 shell + 9 个视图占位 + 通用弹窗/Toast |
| `app.js` | 原生 ES Module 应用逻辑：会话/权限、统一 `api()` 请求封装、登录、9 个视图渲染（仪表盘/用户/菜单/字典/参数/定时任务/登录日志/审计日志/API调用日志） |
| `styles.css` | 控制台全部样式（登录页、侧边栏、表格、表单、弹窗、徽标、分页等） |

导航 9 项：dashboard、users、menus、dicts、configs、jobs、login-logs、audit-logs、**api-call-logs**。

### 前端工程 apex-admin-web（迁移目标）

位置：`/Users/work/apex-scaffold/../apex-admin-web/`（即 `/Users/work/apex-admin-web`）

技术栈：Vue 3.5 + Vite 6 + TypeScript + Pinia + Element Plus + ECharts；与后端通过 `/api/v1`（业务）、`/admin-api/v1`（管理）两组 REST 接口对接；动态路由由后端菜单树驱动。

README 与 `.workbuddy/memory/2026-08-28.md` 已记录：登录 → 工作台 → 系统管理（用户/角色/菜单/部门/字典/参数/定时任务）→ 监控中心（监控大盘/登录日志/操作审计/在线用户）全链路冒烟通过。

### 视图覆盖比对（静态前端 9 项 vs apex-admin-web 已有视图）

| 静态前端视图 | apex-admin-web 对应视图 | 状态 |
|--------------|--------------------------|------|
| dashboard | `views/dashboard/index.vue` | ✅ 已实现 |
| users | `views/system/user/index.vue` | ✅ 已实现 |
| menus | `views/system/menu/index.vue` | ✅ 已实现 |
| dicts | `views/system/dict/index.vue` | ✅ 已实现 |
| configs | `views/system/config/index.vue` | ✅ 已实现 |
| jobs | `views/system/job/index.vue` | ✅ 已实现 |
| login-logs | `views/monitor/loginlog/index.vue` | ✅ 已实现 |
| audit-logs | `views/monitor/audit/index.vue` | ✅ 已实现 |
| **api-call-logs** | **无** | ❌ **唯一缺失** |

**结论**：apex-admin-web 已覆盖 8/9 视图且经过端到端验证，**唯一缺口为「API 调用日志」页面**。迁移工作聚焦于补齐该视图，随后删除后端静态前端并清理托管路由。

### 后端 API 调用日志接口契约（已就绪，无需改动）

- 路由：`GET /admin-api/v1/api-call-logs`（[ApiRouter.java#L184](file:///Users/work/apex-scaffold/apex-interfaces/src/main/java/com/apex/scaffold/interfaces/router/ApiRouter.java#L184)）
- Handler：[ApiCallLogHandler.java](file:///Users/work/apex-scaffold/apex-interfaces/src/main/java/com/apex/scaffold/interfaces/handler/ApiCallLogHandler.java)（`@RequirePermission("system:api-call-log:list")`）
- 查询参数：`page`、`size`、`serviceName`（精确）、`status`（精确，0/1/2/3）、`traceId`（模糊）、`path`（模糊）
- 响应：`PageResponse<ApiCallLogDTO>`
- DTO 字段（[ApiCallLogDTO.java](file:///Users/work/apex-scaffold/apex-application/src/main/java/com/apex/scaffold/application/dto/admin/ApiCallLogDTO.java)）：
  `id:Long, serviceName:String, method:String, path:String, url:String, responseStatus:String, executionTime:Long, status:Integer(0-成功/1-客户端错误/2-服务端错误/3-网络异常), errorMsg:String, traceId:String, requestId:String, callTime:LocalDateTime, createTime:LocalDateTime`

### 后端菜单与权限（已就绪，无需改动）

Flyway `V13__seed_api_call_log_permissions.sql` 已写入：
- 权限码 `system:api-call-log:list`（id 2051）、`system:api-call-log:query`（id 2052），已授予 ADMIN 角色
- 菜单项 id=1102，归入「监控中心」目录（parent_id=1100），`path='api-call-log'`，`component='monitor/api-call-log/index'`，`perms='system:api-call-log:list'`，`visible=1 status=1`

→ apex-admin-web 动态路由会自动拉取该菜单，仅需在 `VIEW_REGISTRY` 注册 `monitor/api-call-log/index` 组件加载器即可让侧边栏出现该入口。

### 后端静态前端托管路由（迁移后需清理）

[ApiRouter.java#L79-L87](file:///Users/work/apex-scaffold/apex-interfaces/src/main/java/com/apex/scaffold/interfaces/router/ApiRouter.java#L79-L87) 的 `adminFrontendRedirectRoutes()` Bean：把 `/admin`、`/admin/` 重定向到 `/admin/index.html`。删除静态前端后该重定向会 404，需一并移除该 Bean。

## 变更方案

### 阶段 A：在 apex-admin-web 补齐「API 调用日志」视图

> 所有路径基于 `/Users/work/apex-admin-web/`。文件夹名 `api-call-log`（带连字符）严格对齐后端菜单 `component` 字段 `monitor/api-call-log/index`，避免新增后端迁移。Vite/TS 支持连字符目录名。

#### A1. 新增类型定义

文件：`src/types/index.ts`

在 `AuditLog` 接口之后追加：

```typescript
/** API 调用日志（/admin-api/v1/api-call-logs） */
export interface ApiCallLog {
  id: number
  serviceName?: string
  method?: string
  path?: string
  url?: string
  responseStatus?: string
  executionTime?: number
  /** 0=成功 1=客户端错误 2=服务端错误 3=网络异常 */
  status: number
  errorMsg?: string
  traceId?: string
  requestId?: string
  callTime?: string
  createTime?: string
}
```

#### A2. 新增 API 函数

文件：`src/api/monitor.ts`

在文件末尾追加（沿用现有 `get` 封装与 `PageQuery`/`PageResponse` 类型）：

```typescript
/** API 调用日志查询参数（serviceName/status 精确，traceId/path 模糊） */
export interface ApiCallLogQuery extends PageQuery {
  serviceName?: string
  status?: number
  traceId?: string
  path?: string
}

/**
 * 分页查询 API 调用日志（对外 HTTP 调用回溯）。
 * @param params 分页与筛选参数
 */
export function listApiCallLogs(
  params: ApiCallLogQuery
): Promise<PageResponse<ApiCallLog>> {
  return get<PageResponse<ApiCallLog>>(
    '/admin-api/v1/api-call-logs',
    params as unknown as Record<string, unknown>
  )
}
```

并在文件顶部 import 块追加 `ApiCallLog` 类型引入。

#### A3. 新增视图组件

新文件：`src/views/monitor/api-call-log/index.vue`

结构参考 [views/monitor/audit/index.vue](file:///Users/work/apex-admin-web/src/views/monitor/audit/index.vue)（同为「分页 + 多字段筛选 + el-table」形态），覆盖静态前端 `renderApiCallLogs()` 的全部能力：

- **查询区**（el-form inline）：serviceName（目标服务）、status（el-select：成功/客户端错误/服务端错误/网络异常，对应 0/1/2/3）、traceId、path，查询/重置按钮
- **表格列**：ID、服务(serviceName)、方法(method)、路径(path, show-overflow-tooltip)、响应码(responseStatus)、状态(用 el-tag 按 0/1/2/3 着色：success/info/warning/danger)、耗时(`${executionTime}ms`)、错误(errorMsg, slice 摘要 + tooltip 全量)、TraceId、调用时间(callTime ?? createTime)
- **分页**：复用 `<Pagination :total @change />` 组件，page/size 与查询参数联动
- **脚本**：`<script setup lang="ts">`，`onMounted(fetchList)`，`search()` 重置 page=1，`reset()` 清空筛选后 search

状态映射常量（脚本内）：

```typescript
const STATUS_OPTIONS = [
  { value: 0, label: '成功', tagType: 'success' },
  { value: 1, label: '客户端错误', tagType: 'warning' },
  { value: 2, label: '服务端错误', tagType: 'danger' },
  { value: 3, label: '网络异常', tagType: 'info' },
] as const
```

#### A4. 注册动态路由组件

文件：`src/router/dynamic.ts`

在 `VIEW_REGISTRY` 中追加一行（保持字母序）：

```typescript
'monitor/api-call-log/index': () => import('@/views/monitor/api-call-log/index.vue'),
```

位置：放在 `'monitor/audit/index'` 与 `'monitor/dashboard/index'` 之间。

### 阶段 B：删除后端内嵌静态前端并清理托管路由

> 所有路径基于 `/Users/work/apex-scaffold/`。

#### B1. 删除静态前端目录

删除目录及其全部内容：`apex-interfaces/src/main/resources/static/admin/`（含 `index.html`、`app.js`、`styles.css`）。

#### B2. 清理后端重定向路由

文件：`apex-interfaces/src/main/java/com/apex/scaffold/interfaces/router/ApiRouter.java`

删除 `adminFrontendRedirectRoutes()` Bean 方法（约 L79-L87，含方法上方的 Javadoc 注释块），该方法把 `/admin`、`/admin/` 重定向到已删除的 `/admin/index.html`。删除后访问 `/admin` 将返回 404，符合「前端独立部署、后端不再托管静态前端」的预期。

> 注：`/admin-api/v1/**` 业务接口路由不受影响，无需改动。

#### B3. 验证后端编译

执行后端构建确保移除路由 Bean 后无编译错误：
```
mvn -pl apex-interfaces -am compile -DskipTests
```

## 假设与决策

1. **目标前端工程确认**：`/Users/work/apex-admin-web` 是用户所指「前端项目」。依据：其 `package.json` description 与 README 均明确为「Apex Scaffold 管理后台前端（前后端分离独立工程）」，且已实现 8/9 视图并经端到端冒烟验证。
2. **迁移范围**：仅「API 调用日志」视图缺失，其余 8 项已存在且功能完整（README + memory 日志 + audit 视图代码交叉验证）。无需重写已有视图。
3. **文件夹命名**：新视图目录用 `api-call-log`（带连字符），严格对齐后端 V13 菜单 `component='monitor/api-call-log/index'`，避免新增后端 Flyway 迁移。Vite/TS 完整支持连字符目录名。
4. **后端接口/菜单/权限**：均已就绪（V13 迁移 + ApiCallLogHandler），阶段 A 不改后端任何代码。
5. **静态前端删除后访问方式**：管理后台改由 apex-admin-web 独立部署（dev: `npm run dev` → localhost:5173；prod: `npm run build` → dist/ 交 Nginx 或放入后端静态目录），后端 `/admin` 短入口不再提供重定向。
6. **不触碰**：`apex-generator` 模块下的 `frontend*.ftl` 模板（代码生成器产物，非运行时前端，不在本迁移范围）。

## 验证步骤

### 阶段 A 验证（前端工程）

1. 类型检查 + 构建：
   ```
   cd /Users/work/apex-admin-web && npm run build
   ```
   预期 `vue-tsc --noEmit` 与 `vite build` 均通过，无类型错误。
2. 启动 dev server（需后端 8080 在运行）：
   ```
   npm run dev
   ```
3. 浏览器登录后，侧边栏「监控中心」下应出现「API 调用日志」入口；点击进入，验证：
   - 列表加载（无数据时显示空态，有数据时表格渲染）
   - 各筛选条件（serviceName/status/traceId/path）查询生效
   - 分页翻页生效
   - 状态列按 0/1/2/3 着色正确
   - 错误信息列摘要 + tooltip 完整

### 阶段 B 验证（后端工程）

1. 删除静态前端 + 移除重定向 Bean 后，后端编译通过：
   ```
   cd /Users/work/apex-scaffold && mvn -pl apex-interfaces -am compile -DskipTests
   ```
2. 启动后端，确认：
   - `GET /admin` 返回 404（不再重定向）
   - `GET /admin-api/v1/api-call-logs?page=1&size=20` 仍正常返回数据（业务接口未受影响）
3. 全仓 grep 确认 `static/admin/` 引用已清理：
   ```
   grep -r "static/admin" apex-interfaces/src --include="*.java" --include="*.yml"
   ```
   预期零命中。
