# 批次 B5 测试报告：配额真实执行

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

- 批次：方案 [unified-auth-tenant-platform-remediation-plan.md](../../../08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md) 批次 B5
- 执行日期：2026-10-31
- 环境：macOS、JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`（构建环境校验通过）
- 状态：**实施完成，门禁结果见 §4**；下一批 B6（租户级配置 + 权限/套餐种子批）待用户验收后启动

## 1. 已交付变更

### 用户配额守卫与拦截器开关解耦（方案 B5 核心修复）

- `UserRepositoryImpl.countByCurrentTenant` 改为**显式 `tenant_id` 条件查询**（`Mono.defer` 内取可信上下文，缺失 fail-closed FORBIDDEN，与 `JdbcTenantScope` 语义一致）——修复"`apex.tenant.enabled=false` 时配额统计退化为全表计数"的静默失效（v1 评审缺口第 4 项），`TenantGuard.assertUserQuota` 语义与拦截器开关解耦（D09 落地）

### 存储配额（全链路）

| 端 | 变更 |
| --- | --- |
| 领域 | `TenantEntity.assertStorageQuota(currentBytes, incrementBytes)`（字节按 1MB=1024² 向上取整换算后交配额值对象）；`TenantQuota.assertStorage`（既有方法，MB 口径，NULL 不限制）复用 |
| 应用 | `TenantApplicationService.assertStorageQuota(tenantCode, currentBytes, incrementBytes)`（异常映射同用户配额） |
| file 端口 | `file/domain/service/StorageQuotaPort`（软上限语义：配额设施不可用放行 + 告警，不阻断业务上传） |
| file 适配器 | `RemoteStorageQuotaAdapter`：消费 A6 内部状态端点取 `maxStorageMb`（3s 超时，不可达放行）+ 本地聚合 `DocumentRepository.sumSizeByTenant`（显式 tenant_id + `COALESCE(SUM(file_size),0)`，结果缓存默认 60s）→ 超限抛 `TENANT_QUOTA_EXCEEDED` |
| file 上传钩子 | `FileService.upload`（字节数组入口）在大小校验后前置 `assertStorageQuota`（租户取 `TenantContext` 可信上下文）；构造器扩展端口参数（`usesSingleImplicitConstructorAndContextAssembly` 装配断言同步补 Bean） |

**并发口径**（依方案）：软上限"先检后写"保留，不引入分布式锁；用户配额以 `sys_user_role` 唯一约束兜底、存储为累加口径（超限窗口内的少量超额写入接受），已在实现 javadoc 注明。

## 2. 测试

| 测试 | 结果 | 说明 |
| --- | --- | --- |
| file `FileServiceTest` / `FileStreamIsolationTest` | 14/14 | 装配断言补端口 Bean；构造器扩展后 no-op 端口语义回归 |
| apex-system 编译与全量回归 | 全绿 | `assertStorageQuota` 编译期接入；`countByCurrentTenant` 既有调用方（TenantGuard）语义不变 |
| 五模块（auth/auth-service/system/gateway/file）`test` | **BUILD SUCCESS** | 见 §4 |

**配额领域单测缺口**：`TenantEntity.assertStorageQuota`/`TenantQuota.assertStorage` 的直接单测未单独新增（换算逻辑由 file 适配器集成路径覆盖）；列为 B6 批次补齐项。

## 3. 过程缺陷与修复记录

1. `UserRepositoryImpl` 缺 `BusinessException`/`ResultCode` 导入（fail-closed 分支）→ 补。
2. `FileService` return 链改写时括号错位 + 字段未在构造器赋值 → 逐一修复（FileService 为显式构造器非 @RequiredArgsConstructor）。
3. 测试 no-op lambda 参数数不匹配（端口为双参）与重复分号 → 修正。
4. `usesSingleImplicitConstructorAndContextAssembly` 上下文缺端口 Bean → `registerBean` 补 no-op。

## 4. 结果

- 五模块（auth/auth-service/system/gateway/file）及上游 `test`：**BUILD SUCCESS**（apex-system 03:48 全绿、apex-file 21s 全绿）。
- `spotless:apply` → 全仓 `spotless:check`：**通过**；`git diff --check`：**干净**；doc-links 本批 **0 断裂**。
- 全量 `clean verify` 纳入 B6 批次门禁。

## 5. 未验证项与边界

- **存储超限 E2E**：绑定小配额租户实际上传超限拒绝需 Compose 冒烟（依赖 B7 隔离默认开启 + 真实配额注入）。
- **配额领域单测**：`assertStorageQuota` 换算（字节→MB 向上取整）与停用租户拒绝路径的直接单测列为 B6 补齐。
- **缓存口径**：已用字节缓存 60s 窗口内连续上传可能短暂低估用量（软上限可接受），文档已注明。
