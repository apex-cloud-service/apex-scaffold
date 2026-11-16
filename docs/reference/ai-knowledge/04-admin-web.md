# 04 · apex-admin-web（管理后台前端）

> 采集：2026-10-31。**权威原文为该仓 `AGENTS.md`（核对日期 2026-10-30）**，本文只做要点与风险提炼。
> **细节已下沉**：页面清单、契约对照、排障表见 `apex-admin-web/MEMORY-CARD.md` 与 `apex-admin-web/docs/reference/ai-knowledge/`（2026-11-01 建立）。本页只保留跨仓视角的摘要，避免两处维护。

## 1. 基本盘

| 项 | 值 |
| --- | --- |
| 路径 / 版本 | `/Users/work/apex-admin-web` · `1.2.0` |
| 技术栈 | Vue 3.5、Vite 6、TypeScript ~5.7、Vue Router 4、Pinia 2、Element Plus 2、Axios、ECharts 5、CropperJS 1 |
| 规模 | `src/views` 下 53 个 `.vue`，9 个测试文件；10 月 11 次提交 |
| 工作树 | 干净 |
| 构建 | `npm run build` → `dist/`；Node `>=18` |

## 2. 与后端的契约面

| 前缀 | 用途 |
| --- | --- |
| `/api/v1` | 用户侧业务 |
| `/api/v2` | 业务增强（租户台账 `/api/v2/platform/tenants`、运维 `/api/v2/operations/*`、工作流定义 `/api/v2/workflow-definitions`） |
| `/admin-api/v1` | 管理后台（需管理角色 + 权限码） |
| `/api/workflow` | 工作流高级操作 |
| `/oauth2/*` | 授权码登录（PKCE） |

生产需反代 `/oauth2`、`/api`、`/admin-api`，并为 `/api/v1/messages/ws` **单独配置 WebSocket 转发**。

## 3. 工程约定（易错点）

| 主题 | 约定 |
| --- | --- |
| 接口封装 | 统一走 `src/api/*` + `src/utils/request.ts`（`request/get/post/put/del`），**页面不新建 Axios 实例** |
| 会话 | 只经 `src/utils/auth.ts` 读写，不自行引 `access_token` 等键 |
| 动态菜单 | 新页面必须同步 `src/router/dynamic.ts` 的 `VIEW_REGISTRY` 与后端菜单 `component`；**文件存在 ≠ 路由已注册** |
| 菜单 path | 目录用绝对路径（`/platform`），子菜单用相对段（`tenant`），由 `resolvePath` 拼接 |
| 分页 | 三种口径并存：普通列表 `page/size` + `list/total`；工作流实例 `pageNum/pageSize` + `records/total`；待办任务 `pageNum/pageSize` 但响应 `PageResponse` 读 `list/total`。**以具体 API 类型为准，不能全局替换** |
| 按钮权限 | `v-permission`：空列表放行、多码任一满足、不满足移除 DOM。前端隐藏**不能**替代后端鉴权 |
| 租户 | 作用域由后端从令牌解析；前端仅在登录响应带 `tenantId` 时记 `apex.admin.tenant-id` 并附 `X-Tenant-Id`。**业务侧无租户切换器**，登录态绑单租户 |
| 站内信 | `utils/message-socket.ts`：ticket/pong/1h 重连；未读角标 = 60s 轮询 + WS 触发刷新 |
| 脱敏 | 规则**只读**（启动配置），仅批量试算 |
| 上传 | Element Plus/XHR 路径**不经过** Axios 拦截器 |
| 注释 | 该仓 AGENTS.md 要求：除明确要求外不新增注释、不建额外文档、不自动提交 |

## 4. 页面模块

`dashboard` / `system`（用户、角色、菜单、部门、字典、参数、定时任务、租户台账、MFA、IP 访问控制、脱敏、认证与 SSO）/ `monitor`（登录日志、操作审计、API 调用日志、在线用户、运维健康五页签）/ `workflow`（实例监控、待办、定义、发起）/ `office`（站内信、模板、绑定审批、组织同步、会议室）/ `data`（文档、图表）/ `oauth2` / `public`（匿名分享兑换、密码找回）/ `profile` / `login` / `demo` / `error`。

## 5. 近期变动

- **10-26**：补齐配置与数据维护能力，对齐后端契约。
- **10-31（4 次提交）**：对齐后端 v2 契约并补齐审批/分享/会议室/安全/运维管理页；新增生成向导、游标收件箱、密码找回、分享兑换公开页；全量校准文档与实现一致性；注释整改。

## 6. 风险与待办

| 编号 | 事项 |
| --- | --- |
| W1 | 后端批次 A3（撤销传播）/A4（会话 + MFA 接线）落地后，前端需同步"在线会话"与"MFA 二步"页面，属方案 §12 同批交付纪律 |
| W2 | 分页三口径并存，新增页面易抄错；改任何列表先看该域 `api/*.ts` 的返回类型 |
| W3 | 运维健康页部分端点（根路径、导出任务列表）是否真实存在**需以后端契约为准**，前端已封装不等于后端已提供 |
| W4 | 生产部署依赖 Nginx 反代与 WS 转发，`dist/` 直开无效 |
