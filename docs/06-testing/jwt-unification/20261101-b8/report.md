# 批次 B8 测试报告：租户管理员自助管理

- 批次：方案 [unified-auth-tenant-platform-remediation-plan.md](../../../08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md) 批次 B8
- 执行日期：2026-11-01
- 环境：macOS、JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`；双库演练容器 MySQL 9.0 / PostgreSQL 15
- 状态：**实施完成，门禁结果见 §4**；下一批 B9（租户化缓存键助手）待用户验收后启动

## 1. 已交付变更

### V80 租户自助管理菜单种子（MySQL / PostgreSQL 成对）

- 菜单种子 ID 7200–7202（空闲段，`ON DUPLICATE KEY UPDATE` / `ON CONFLICT DO UPDATE` 幂等）：
  - `tenant-user` 用户管理（`tenant:user:manage`）
  - `tenant-role` 角色管理（`tenant:role:manage`）
  - `tenant-dept` 部门管理（`tenant:dept:manage`）
- 三码授予平台 ADMIN 角色；租户内 `tenant_admin` 角色由 B4 开通时按套餐授权建立
- **种子自洽**：迁移尾部重算 `platform-full` 的 `menu_ids`/`permission_codes`，保证"全量套餐"语义随菜单演进不漂移

### 平台/租户分界（第二道边界）

- `TenantGuard.assertPlatformTenant()`：断言当前租户为 `default`，否则 FORBIDDEN——平台租户治理（目录/套餐/平台配置）**除权限码之外**再加租户维度校验
- `PlatformTenantHandler` 的 `create` / `changeStatus` / `updateQuota` 三个写入口接入该守卫（拆分 `doXxx` 私有方法保持方法体清晰）

### 权限码分界设计（承接 B6 盘点定论）

| 场景 | 权限码 | 说明 |
| --- | --- | --- |
| 平台超管管理全部用户 | `staff:manage` | 既有码，`default` 租户 ADMIN 持有 |
| 租户管理员管理本租户用户 | `tenant:user:manage` | 新码，`tenant_admin` 角色持有；数据范围由租户拦截器自动收敛 |
| 平台租户目录/套餐/配置 | `tenant:read` / `tenant:package:*` / `tenant:config:*` | 仅 `default` 租户 ADMIN + `assertPlatformTenant` 双保险 |

## 2. 测试

| 测试 | 结果 |
| --- | --- |
| 双库演练（含 V80 菜单种子 + 种子重算断言） | **2/2 通过**（63.98s） |
| 六模块（auth/auth-service/system/gateway/file-service/office-service）及上游 `test` | **BUILD SUCCESS** |

## 3. 过程缺陷与修复记录

1. **V80 新增菜单后 V78 的 platform-full 种子差值为 3 不为 0**（既有断言失败）→ 在 V80 尾部重算 `platform-full` 种子（数据驱动聚合），使"全量套餐"语义始终自洽——这是**种子自洽性设计的必要修正**，否则每次加菜单都要改 V78（Flyway 禁改历史迁移）。

## 4. 结果

- 双库演练：**2/2 通过**。
- 六模块及上游 `test`：**BUILD SUCCESS**。
- `spotless:apply` → 全仓 `spotless:check`：**通过**；`git diff --check`：**干净**；doc-links 本批 **0 断裂**。
- 全量 `clean verify` 纳入 B9 批次门禁。

## 5. 未验证项与边界

- **租户管理员自助管理 E2E**：需 Compose 环境开通租户后用 `tenant_admin` 登录验证菜单可见与数据隔离（拦截器机制已由 `TenantLineE2eTest` 覆盖，端到端待部署验证）。
- **`DeptHandler`/`AdminUserManagementHandler` 仍是 `staff:manage`/`system:dept:*`**：租户管理员使用新码 `tenant:*:manage` 的 handler 挂点未新增（复用现有管理页面的权限码显隐由前端按权限码控制，后端接口保持既有码）；如需租户管理员直接复用同一批接口，需在 `@RequirePermission` 支持"任一满足"语义后补——列为 B9/后续。
- **菜单父节点 3000**：假定为既有系统管理目录；若实际树形不同需按前端菜单树调整（幂等迁移可重放修正）。
