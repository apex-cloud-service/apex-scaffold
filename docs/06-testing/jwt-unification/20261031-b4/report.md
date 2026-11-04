# 批次 B4 测试报告：租户开通引导

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

- 批次：方案 [unified-auth-tenant-platform-remediation-plan.md](../../../08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md) 批次 B4
- 执行日期：2026-10-31
- 环境：macOS、JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`；双库演练容器 MySQL 9.0 / PostgreSQL 15
- 状态：**实施完成，门禁结果见 §4**；下一批 B5（配额真实执行）待用户验收后启动

## 1. 已交付变更

### 开通事务（领域端口范式）

- 领域端口 `TenantProvisioningGateway`（`domain/gateway`，与 `RoleDeletionGateway` 同范式：多表写入收敛到基础设施事务边界）
- 基础设施实现 `TenantProvisioningTransaction`（`TransactionTemplate` 单事务，jdbc 池执行，租户上下文以新租户编码桥接使拦截器补齐 `tenant_id`）：默认部门 → 租户管理员账号（BCrypt）→ 内置角色 `tenant_admin` → 按套餐 `permissionCodes` 建立角色授权（无套餐/全量套餐 = 全部平台权限码）→ 管理员角色绑定 → 租户登记与管理员回填；任一步失败整体回滚
- `TenantCreateRequest` 增量扩展 `adminUsername`/`adminPassword`：提供时走开通引导（密码缺失 → PARAM_ERROR）；未提供保持既有单行创建行为（adminUserId 引用既有账号），既有测试与调用方零破坏
- `createTenant` 接入开通链路（编码冲突 `DuplicateKeyException` 统一映射 `TENANT_CODE_EXISTS` 保持）
- 初始版本曾直接让 application 依赖 infrastructure 事务类，**被 `LayeringArchTest` 拦截**（application 不依赖 interfaces/infrastructure 规则）→ 重构为领域端口范式（架构门禁按设计生效的直接证据）

### V78 双库演练断言（合并演练承诺兑现）

`SystemDualDatabaseMigrationIntegrationTest` 增加：`platform-full` 种子存在且启用；种子 `menu_ids` 覆盖全量 `sys_menu`（差值为 0，MySQL `JSON_LENGTH` / PG `jsonb_array_length` 双方言）。演练 **2/2 通过**。

## 2. 测试

| 测试 | 数量 | 覆盖 |
| --- | --- | --- |
| `TenantProvisioningTransactionTest`（新增） | 3 | 开通步骤序列与管理员回填、套餐权限码过滤授权（role_permission 仅含套餐内码）、事务内失败回滚传播（末步租户登记不落库） |
| 既有回归 | — | TenantApplicationServiceTest 18/18（未提供管理员账号的既有语义零破坏）、TenantPackageServiceTest 6/6 |

## 3. 过程缺陷与修复记录

1. **LayeringArchTest 违规**（application→infrastructure 直依赖）→ 领域端口重构（架构门禁按设计生效）。
2. 重构后公共/私有方法重名（`provision`）→ 私有改名 `provisionInternal`。
3. 测试未同步端口方法（`execute` → `provision`）导致 testCompile 失败 → 同步。
4. 首次补 V78 断言两次锚点未命中（spotless 重排）→ 尾部定位插入。

## 4. 结果

- 双库演练：**2/2 通过**（68s，含 V78 套餐种子覆盖断言）。
- 四模块（auth/auth-service/system/gateway）及上游 `test`：**BUILD SUCCESS**。
- `spotless:apply`（system）→ 全仓 `spotless:check`：**通过**；`git diff --check`：**干净**；doc-links 本批 **0 断裂**。
- 全量 `clean verify`：双库演练为 failsafe IT 且四模块 test 全绿；全量 verify 纳入 B5 批次门禁。

## 5. 未验证项与边界

- **开通后管理员登录 E2E**：真实链路（开通 → 管理员登录 → 仅见本租户数据）需 Compose 冒烟（依赖 B7 隔离默认开启）。
- **并发开通同编码冲突**：由 `sys_tenant` 唯一键 + `TENANT_CODE_EXISTS` 映射兜底（数据库约束兜底语义保留），高并发压测未执行。
- **初始密码策略**：当前明文入参 BCrypt 存储；首登强制改密（沿用户体系既有机制）未在本批强制——与 §12 前端开通向导联调时对齐。
