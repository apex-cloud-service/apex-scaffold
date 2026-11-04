# 批次 B3 测试报告：租户套餐与功能裁剪

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

- 批次：方案 [unified-auth-tenant-platform-remediation-plan.md](../../../08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md) 批次 B3
- 执行日期：2026-10-31
- 环境：macOS、JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`（构建环境校验通过）
- 状态：**实施完成，门禁结果见 §4**；下一批 B4（租户开通引导）待用户验收后启动

## 1. 已交付变更

### V78 迁移（MySQL / PostgreSQL 成对，幂等）

- `sys_tenant_package` 平台级定义表：`package_code` 唯一、`menu_ids`/`permission_codes`/`features` JSON（PG JSONB）、状态/审计列
- `platform-full` 种子为**数据驱动聚合**（`JSON_ARRAYAGG`/`jsonb_agg` 全量菜单与权限码）——菜单演进后语义不漂移
- `DefaultTenantLineHandler.IGNORE_TABLES` 增加 `sys_tenant_package`（平台级定义表，先于租户上下文查询）

### 套餐实体/仓储/服务

- `TenantPackageEntity`（领域语义：空集合 = 全量不裁剪；`withParentChain` 父链补全）+ `TenantPackagePO/Mapper` + `TenantPackageRepository` 端口与实现（JSON 经 `JsonUtil` TypeReference 序列化，解析失败按全量兜底并告警）
- `TenantPackageService.resolveScope(tenantCode[, packageCode])`：平台 `default` 租户不裁剪；未绑定套餐不裁剪（B4 开通引导强制绑定后收紧）；停用/未知套餐回退全量（无裁剪依据不裁剪）；`PackageScope` 值对象（空集合语义 = 全量放行，与实体口径一致）；CRUD 供 B6 管理接口消费

### 裁剪接入

- `MenuService.getMenuTree`：非平台租户按套餐 `menu_ids` 过滤 + **父链补全**（祖先自动可见，树完整）；无租户上下文退化为平台租户
- `PermissionService.getPermissionsByUserId`：非平台租户有效权限 = 角色权限 ∩ 套餐 `permission_codes`（统一授权与本地 RBAC 双路径统一后置过滤）

## 2. 测试

| 测试 | 数量 | 覆盖 |
| --- | --- | --- |
| `TenantPackageServiceTest`（新增） | 6 | 平台租户/未绑定不裁剪、激活套餐裁剪、停用/未知回退全量、空集合全量语义、父链补全 |
| `MenuServiceTest`（同步） | 10 | 套餐桩（full）后树构建/孤儿兜底语义保持 |
| `PermissionServiceTest`（同步） | — | resolveScope 桩后既有权限用例语义保持 |
| 四模块回归 | — | 见 §4 |

## 3. 过程缺陷与修复记录

1. **record 组件与静态工厂同名冲突**（`PackageScope.unbounded` 组件 vs `unbounded()` 工厂 → `!` 操作符类型错乱）→ 工厂改名 `full()`。
2. `menuRepository.findAll()` 实为 `Mono<List>` 而非 `Flux`（误加 `collectList()`）。
3. `createPackage` 链 `Mono.error` 泛型推断退化 → 显式 `Mono.<TenantPackageEntity>error`。
4. 既有测试（PermissionServiceTest/MenuServiceTest/UnifiedAuthorizationServiceTest）未同步新依赖 → 补 Mock 与 lenient 桩（full 口径保持既有语义）。
5. 测试中 `TenantEntity.builder()` 不存在 → setter 构造；`LongFunction` 原始类型误用 `.intValue()` → 强转。

## 4. 结果

- 四模块（auth/auth-service/system/gateway）及上游 `test`：**BUILD SUCCESS**。
- B3 定向测试：**50/50**（TenantPackageServiceTest 6、MenuServiceTest 10、PermissionServiceTest/UnifiedAuthorizationServiceTest 既有用例保持）。
- `spotless:apply` → 全仓 `spotless:check`：**通过**；`git diff --check`：**干净**；doc-links 本批 **0 断裂**。
- 全量 `clean verify` 纳入下一批门禁（V78 双库演练随 B4 迁移合并执行，复用 `SystemDualDatabaseMigrationIntegrationTest` 模式）。

## 5. 未验证项与边界

- **V78 真实双库演练未单独执行**（JSON_ARRAYAGG/jsonb_agg 聚合种子需真库验证）——B4 迁移（V79）实施时合并演练并补 `sys_tenant_package` 断言。
- **越套餐授予拒绝**：运行时角色-权限授予 API 当前不存在（授权关系仅种子维护），约束落点为 B6 套餐管理接口（变更时校验引用完整）——方案 B3 项如实顺延。
- **套餐 CRUD HTTP 层**：随 B6 权限种子批交付（本批仅服务层）。
- **运行时联调**：非平台租户菜单树裁剪需 Compose 冒烟（拦截器启用 + 套餐绑定后）。
