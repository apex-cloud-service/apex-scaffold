# 批次 B7 测试报告：隔离默认开启

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

- 批次：方案 [unified-auth-tenant-platform-remediation-plan.md](../../../08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md) 批次 B7
- 执行日期：2026-10-31
- 环境：macOS、JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`；双库演练容器 MySQL 9.0 / PostgreSQL 15
- 状态：**实施完成，门禁结果见 §4**；下一批 B8（租户自助管理）待用户验收后启动

## 1. 已交付变更

### 隔离默认开启（yml 翻转）

- `apex-system/application.yml`：新增 `apex.tenant.enabled: true`（原仅 enterprise 档 true，现在全档默认开启）
- `apex-file-service/application.yml`：同上
- `apex-office-service/application.yml`：同上
- `application-basic.yml`（三服务）：显式 `apex.tenant.enabled: false`（D09 basic 档离线最小档维持关闭）
- `workflow` / `auth`：均为 JdbcTemplate 持久化（无 MyBatis-Plus），维持手写 `WorkflowTenantAccess` / `JdbcTenantScope` fail-closed 例外口径，**本批不改动**

### office 拦截链收敛

- **移除** `MessageTenantConfig.java`（仅隔离 `off_message`/`off_message_recipient` 两表的专用注册）
- 统一由共享 `MybatisPlusConfig`（`@AutoConfiguration`，apex-infrastructure-persistence）的 `DefaultTenantLineHandler` 接管：全表隔离（不进白名单即参与），与 system/file 口径一致
- 办公 2 个测试文件（`MessageDatabaseWebSocketTest`/`MessageGatewayDatabaseIntegrationTest`）从引用 `MessageTenantConfig` 改为内联构造等效 `TenantLineInnerInterceptor`（避免跨模块依赖 `DefaultTenantLineHandler`，因 office 不直接依赖 apex-infrastructure-persistence 的类路径）
- 全部 `off_*` 表 `tenant_id` 覆盖核实通过（8 表均有，无缺列）

### AGENTS.md 例外口径更新

- "持久化迁移与租户隔离"节：新增"多租户行级隔离拦截器默认开启（B7）"条款；标注 workflow/auth 例外口径

### 双库演练扩展

- `SystemDualDatabaseMigrationIntegrationTest` 补 V79 断言：`sys_tenant_config` 表存在且含 `tenant_id` 列；8 个新权限码全部入库

## 2. 过程缺陷与修复记录

1. **V79 权限 ID 2605–2608 与 V64 冲突**（V64 已用 2605–2608 注册工作流定义权限码）→ 改用空闲段 **2700–2707**；MySQL 侧加 `INSERT IGNORE` 防重放冲突。
2. **PG V79 `sys_role_permission` 缺 `id` 列**（PG 不像 MySQL 可省略主键列由应用层生成）→ 补 `id` 列 `7900 + ROW_NUMBER() OVER ()`。
3. **office 测试跨模块依赖**：`DefaultTenantLineHandler` 在 apex-infrastructure-persistence 中，office 不直接依赖 → 测试改为内联构造等效 `TenantLineInnerInterceptor`。
4. **文档断链**（删除 `MessageTenantConfig.java` 后两份历史文档引用失效）→ 行内代码替换。

## 3. 事实澄清

- `MybatisPlusConfig` 为 `@AutoConfiguration`（apex-infrastructure-persistence 的 AutoConfiguration.imports），自动装配到所有依赖该模块的服务——office 的 `MessageTenantConfig` 之前与之并存产生两个 `MybatisPlusInterceptor` Bean，收敛后只留一个。
- 办公 `off_*` 全表 `tenant_id` 覆盖核实：8 张表均有列，无缺列需补。

## 4. 结果

- 双库演练：**2/2 通过**（62.94s，含 V79 断言）。
- 六模块（auth/auth-service/system/gateway/file-service/office-service）及上游 `test`：**BUILD SUCCESS**。
- `spotless:apply` → 全仓 `spotless:check`：**通过**；`git diff --check`：**干净**；doc-links 本批 **0 断裂**。
- 全量 `clean verify` 纳入 B8 批次门禁。

## 5. 未验证项与边界

- **双租户 E2E 反例**：需 Compose 环境两个租户各建角色/用户后交叉访问（B7 后拦截器已生效，由 `TenantIsolationArchTest` + `TenantLineE2eTest` 保证 SQL 改写机制正确；端到端反例待部署验证）。
- **AGENTS.md 原文残留**：原"企业档默认 true"描述仍存在于 enterprise yml 注释中（不影响运行时，B8 清理）。
- **file-service 拦截器生效路径**：file 的 `IdentityRelayAuthenticationFilter` 桥接 TenantContext 已验证，但 file 的 MyBatis-Plus 查询此前全部使用显式条件，拦截器叠加后的冗余过滤（安全无害但需确认性能）待运行时观察。
