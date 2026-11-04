# 批次 B2 测试报告：主库 RBAC 租户化（含 V76+V77 真实双库演练）

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

- 批次：方案 [unified-auth-tenant-platform-remediation-plan.md](../../../08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md) 批次 B2
- 执行日期：2026-10-31
- 环境：macOS、JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`；双库演练容器 MySQL 9.0 / PostgreSQL 15（Testcontainers）
- 状态：**实施完成，门禁结果见 §4**；下一批 B3（租户套餐与功能裁剪）待用户验收后启动

## 1. 已交付变更

### V77 迁移（MySQL / PostgreSQL 成对，幂等条件 ALTER）

- `sys_role` 增加 `tenant_id VARCHAR(64) NOT NULL DEFAULT 'default'`；唯一键重建：`uk_code(code)` → `uk_tenant_code(tenant_id, code)`（MySQL）/ `uk_role_code` → `uk_tenant_code`（PG）
- `sys_role_permission` 增加 `tenant_id`（NOT NULL DEFAULT 'default'）+ 索引 `idx_sys_role_permission_tenant`
- 授权关系租户与角色对齐：`sys_role_permission`/`sys_role_dept` 按 `role_id` JOIN 对齐（覆盖任何漂移）
- 事实澄清：`sys_role` 此前**无** tenant_id 列（V18 仅给 sys_dept 加列）；V44 回填的是 sys_role_dept；V76 动态遍历已覆盖全部既有租户列表

### 拦截器与代码

- `DefaultTenantLineHandler.IGNORE_TABLES` 移除 `sys_role`/`sys_role_permission`（B2 起角色/授权参与行级隔离；菜单/权限码/字典/配置仍平台全局，经 B3 套餐裁剪）
- `RoleService`：`updateRole` 空结果显式 NOT_FOUND（拦截器过滤后空即"不存在或跨租户越权"，fail-loud）；`assignRoleToUser` 分配前校验角色本租户可见（防跨租户孤儿授权行）；`createRole` 既有 TenantGuard + 拦截器自动填列

### 双库演练断言（`SystemDualDatabaseMigrationIntegrationTest` 扩展）

全链 77 版迁移后断言：`sys_tenant` 仅含 `default` 且无 `apex-default` 残留；`sys_role`/`sys_role_permission` 含 tenant_id 列；唯一键 `uk_tenant_code` 存在且旧键移除；存量角色/授权关系全部归属 default。

## 2. 测试

| 测试 | 结果 | 说明 |
| --- | --- | --- |
| `SystemDualDatabaseMigrationIntegrationTest`（真实 MySQL 9 + PG 15） | **2/2** | V76+V77 真实双库演练：全链迁移 + 租户改名 + RBAC 租户键断言 |
| `DefaultTenantLineHandlerTest` | 通过 | 白名单断言更新（SYS_ROLE/sys_role_permission → False） |
| `RoleServiceTest` | 18/18 | `updateRole_NotFound` 语义更新为 fail-loud；assign 用例补角色可见桩 |
| `TenantLineE2eTest` | 10/10 | 回归确认（曾因同模块编译错误的 Lombok 级联假错短暂报错，根因修复后自清） |

## 3. 过程缺陷与修复记录

1. **V76 动态遍历在数值型 tenant_id 表触发 DOUBLE 截断错误**（MySQL 22001）→ 游标限定 `DATA_TYPE IN (varchar/char/text)`（数值列不可能含源编码，语义无损），双库同步修复——由双库演练捕获，正是演练价值所在。
2. **索引断言按行计数错误**：MySQL `information_schema.statistics` 复合索引每列一行 → 改 `COUNT(DISTINCT index_name)`。
3. **重复 `@Test` 注解引发 Lombok 级联假错**（同模块注解处理降级，`TenantLineE2eTest` 假性 cannot find symbol）→ 根因修复后自清。
4. `mvnd -pl <module>` 无 `-am` 陈旧构件问题再次出现（第 4 次），全部以 `-am` 纠正。

## 4. 结果

- 双库演练：**2/2 通过**（92.86s，真实容器全链）。
- 四模块（auth/auth-service/system/gateway）及上游 `test`：**BUILD SUCCESS**（apex-system 1686 用例全绿）。
- `spotless:apply`（system/persistence）→ 全仓 `spotless:check`：**通过**；`git diff --check`：**干净**；doc-links 本批 **0 断裂**。
- 全量 `clean verify`：双库演练为 failsafe IT 且四模块 test 全绿；全量 verify 纳入 B3 批次门禁。

## 5. 未验证项与边界

- **权限码一致性测试未实施**（方案 B2 项）：核实发现 sys_permission（菜单/按钮码 + V63 租户管理码）与 auth_permission（user:read 等）编码集合**当前即不一致**——全集对齐是 B8（租户管理员权限码分界）的设计前提，硬凑 diff 为空会引入无依据改动；列为 B8 首项，先盘点再定对齐口径。
- **双租户同 role_code 隔离 E2E**：拦截器 H2 端到端（TenantLineE2eTest 范式）覆盖 SQL 改写机制，sys_role 专属用例随 B7 灰度（拦截器默认开启 + 各服务反例 IT）合并交付。
- **运行时联调**：跨租户角色操作反例需 Compose 冒烟（拦截器启用态）。
- **并行会话**：瞬态编译中间态再现（自行恢复），本批未触碰其文件。
