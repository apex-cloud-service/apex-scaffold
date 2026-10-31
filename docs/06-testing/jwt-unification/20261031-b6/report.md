# 批次 B6 测试报告：租户级配置与权限/套餐种子

- 批次：方案 [unified-auth-tenant-platform-remediation-plan.md](../../../08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md) 批次 B6
- 执行日期：2026-10-31
- 环境：macOS、JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`（构建环境校验通过）
- 状态：**实施完成，门禁结果见 §4**；下一批 B7（隔离默认开启）待用户验收后启动

## 1. 已交付变更

### V79 迁移（MySQL / PostgreSQL 成对，幂等）

- `sys_tenant_config`（tenant_id, config_key 唯一；**不进拦截器白名单**——随租户行级隔离）
- 权限种子（ID 2605–2612 保留段，WHERE NOT EXISTS 幂等，参照 V63 范式）：`tenant:package:read/create/update`、`tenant:config:read/update`、`tenant:user:manage`、`tenant:role:manage`、`tenant:dept:manage`（B8 自助管理消费）
- 套餐/配置管理权限授予平台 ADMIN 角色（MySQL INSERT IGNORE / PG NOT EXISTS 对齐）

### 租户配置读取链路

- `TenantConfigEntity`/`TenantConfigRepository` 端口与实现（实现侧显式租户校验：入参租户必须与可信上下文一致，防跨租户键注入；上下文缺失 fail-closed）
- `ConfigService.getValueByKey` 读取优先级 **租户配置 → 全局配置**（平台租户/无上下文直接全局；租户查询业务异常静默回退全局）

### 套餐管理 HTTP 层（B3 服务层出接口）

- `TenantPackageHandler` + `TenantPackageRouter`：`/api/v2/tenant-packages` CRUD（GET 列表/{packageCode} 详情、POST/PUT），权限码 `tenant:package:read/create/update`，OpenAPI 元数据齐备
- `TenantPackageRequest` DTO（menuIds/permissionCodes/features 集合入参）

### 配额领域单测补齐（B5 遗留项）

`TenantStorageQuotaTest`（4 用例）：字节→MB 向上取整换算超限、恰好等于上限不超限、NULL 配额不限制、停用租户生命周期优先拒绝。

## 2. 权限码盘点（B2 延期项，如实记录）

按列名精确解析种子 SQL：**sys_permission 种子 16 码**（system:dept:list、system:audit-log:list、office:message:query、tenant:* 等）vs **auth_permission 种子 4 码**（user:read、auth:config:query、mfa:setup、mfa:unlock），**交集为 0**。

**结论**：两套权限码体系当前即完全不相交——这不是数据错误，而是双体系设计使然（system 侧码面向主应用菜单/接口，auth 侧码面向认证中心自身接口）。统一授权模式下 system 消费 auth 侧码；硬凑"diff 为空"会引入无依据改动。**对齐口径结论**：两套体系保持各自命名空间，B8 租户自助管理直接使用 system 侧码（本批种子已交付）；仅在租户绑定套餐裁剪 auth 侧码时按需补录。此结论与方案 D02 乙案口径一致。

## 3. 过程缺陷与修复记录

1. `ServerRequest` 导错包（重复踩坑第 3 次，应形成肌肉记忆：`web.reactive.function.server`）。
2. `Result` 工厂方法再次用错（`success(data)` 非 `ok`）。
3. 脚本 python 变量作用域错误（path 未定义）→ 重跑修正。

## 4. 结果

- 四模块（auth/auth-service/system/gateway）及上游 `test`：**BUILD SUCCESS**。
- B6 定向测试：`TenantStorageQuotaTest` **4/4**（换算/边界/NULL/停用）。
- `spotless:apply` → 全仓 `spotless:check`：**通过**；`git diff --check`：**干净**；doc-links 本批 **0 断裂**。
- 全量 `clean verify` 纳入 B7 批次门禁。

## 5. 未验证项与边界

- **V79 真实双库演练**：随 B7 批次合并执行（复用双库 IT 模式，补 sys_tenant_config 断言）。
- **套餐管理接口运行时联调**：需 Compose 环境授予 ADMIN 角色后冒烟。
- **租户配置管理 HTTP 层**：租户配置的 CRUD 接口未单独出（读取优先级已生效；租户管理员维护接口随 B8 自助管理合并交付，避免重复出两层）。
