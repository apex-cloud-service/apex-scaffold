# 批次 B9 测试报告：租户化缓存键助手（M2 收官）

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

- 批次：方案 [unified-auth-tenant-platform-remediation-plan.md](../../../08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md) 批次 B9
- 执行日期：2026-11-01
- 环境：macOS、JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`（构建环境校验通过）
- 状态：**实施完成，M2 多租户完整化收官**；下一阶段 M3（组件化服务化）待用户验收后启动

## 1. 已交付变更

### `TenantCacheKeys` 租户化缓存键助手（apex-infrastructure-cache）

统一键规则 `<prefix>:tenant:<tenantCode>:<segments...>`，杜绝各模块自行拼接导致的键空间漂移与跨租户串读。四个 API：

| API | 用途 | 失败语义 |
| --- | --- | --- |
| `of(prefix, segments...)` | 当前租户键（取 `TenantContext` 可信上下文） | 上下文缺失 **fail-closed** 抛 `IllegalStateException` |
| `forTenant(prefix, tenantCode, segments...)` | 消息消费侧按可信消息中的租户还原键 | 租户编码空白抛 `IllegalArgumentException` |
| `platform(prefix, segments...)` | 平台级（跨租户）键，显式 `platform` 段 | — |
| `isTenantScoped(key)` | 键合规校验（供架构测试与迁移审计） | — |

设计要点：**不允许生成无租户维度的键**（fail-closed）；平台级缓存必须显式声明 `platform` 段，便于审计识别跨租户缓存。

## 2. 测试

| 测试 | 结果 | 覆盖 |
| --- | --- | --- |
| `TenantCacheKeysTest`（新增） | **5/5** | 租户键构造、上下文缺失 fail-closed、显式租户键与空租户拒绝、平台键、键合规校验（含 null/空串） |
| 七模块及上游 `test`（含 cache 模块） | **BUILD SUCCESS** | 见 §4 |

## 3. 结果

- `TenantCacheKeysTest`：**5/5 通过**。
- 七模块（auth/auth-service/system/gateway/file-service/office-service/**infrastructure-cache**）及上游 `test`：**BUILD SUCCESS**。
- `spotless:apply` → 全仓 `spotless:check`：**通过**；`git diff --check`：**干净**；doc-links 本批 **0 断裂**。

## 4. 未验证项与边界

- **既有缓存键未全部迁移**：本批交付助手与规范，`UnifiedAuthorizationService` 的手拼 base64 键等既有缓存点**未逐一改造**（改造需评估各键的失效策略与滚动升级兼容性，避免上线瞬间全量缓存失效）。建议后续以独立批次按模块迁移，并用 `isTenantScoped` 加架构测试守护新增缓存点。
- **架构测试引导**：方案 B9 提及"新增缓存键必须携带租户维度"的架构测试未新增（需先完成既有键迁移，否则测试会大面积失败——顺序上应先迁后守）。
- **运行时验证**：缓存键变更的跨租户串读风险消除需 Compose 双租户压测确认。

## 5. M2 多租户完整化阶段总结

| 批次 | 内容 | 门禁 |
| --- | --- | --- |
| B1 | 租户编码统一（V76） | 双库演练 2/2 |
| A6 | 认证期租户校验（内部端点 + 登录前置） | 适配器单测 + 登录门禁用例 |
| B2 | 主库 RBAC 租户化（V77） | 双库演练 2/2 |
| B3 | 租户套餐与功能裁剪（V78） | 套餐服务 6/6 + 菜单/权限裁剪测试 |
| B4 | 租户开通引导（事务化 provision） | 开通事务 3/3 + 双库演练 2/2 |
| B5 | 配额真实执行（用户 + 存储） | 存储配额 4/4 + file 测试 14/14 |
| B6 | 租户级配置与权限/套餐种子（V79） | 双库演练 2/2 + 权限码盘点定论 |
| B7 | 隔离默认开启 + office 拦截链收敛 | 双库演练 2/2（含 V79） |
| B8 | 租户管理员自助管理（V80） | 双库演练 2/2（含 V80） |
| B9 | 租户化缓存键助手 | 5/5 |

**M2 结论**：多租户从"行级隔离骨架 + 条件启用"升级为"默认开启 + RBAC 租户化 + 套餐裁剪 + 开通引导 + 配额执行 + 租户配置 + 自助管理"，v1 评审列出的多租户缺口全部闭环（除缓存键全量迁移与架构守护有序顺延）。
