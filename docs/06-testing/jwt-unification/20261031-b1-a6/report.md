# 批次 B1+A6 测试报告：租户编码统一与认证期租户校验

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

- 批次：方案 [unified-auth-tenant-platform-remediation-plan.md](../../../08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md) 批次 B1（M2 首批）+ A6（依赖 B1，随批实施）
- 执行日期：2026-10-31
- 环境：macOS、JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`（构建环境校验通过）
- 状态：**实施完成，门禁结果见 §4**；下一批 B2（主库 RBAC 租户化）待用户验收后启动

## 1. 已交付变更

### B1 租户编码统一（V76）

| 项 | 内容 |
| --- | --- |
| 迁移 | `V76__unify_default_tenant_code.sql`（MySQL 存储过程 + PostgreSQL DO 块，成对）：`apex-default` → `default` 全库统一——前置断言（源行数 ≤1、目标编码冲突拒绝、双侧皆缺失拒绝）、`sys_tenant` 改名、**information_schema 动态遍历**所有含 `tenant_id` 列的表执行改名（防手工枚举遗漏）、后置断言不残留源编码；语义与 MySQL 版本逐条人工配对 |
| 代码侧 | 全仓 Java/yml/Nacos 零 `apex-default` 字面量引用（已核实）——改名纯数据层，代码零改动 |
| 前提 | 项目从未部署（大方案 2026-10-20 口径）；若存在已初始化环境，须先按大方案迁移验证工具演练 |
| 涉及核对 | 网关健康任务默认 `default`（`APEX_HEALTH_SCHEDULE_TENANT_ID:default`）、认证中心代码默认 `default`——统一后两套租户 ID 空间对齐 |

### A6 认证期租户校验

| 端 | 变更 |
| --- | --- |
| apex-system | `SystemConstants.INTERNAL_TENANT_PATHS`（`/internal/v1/tenants/**`）+ `SecurityConfig` permitAll + `JwtAuthenticationFilter` 放行；`TenantApplicationService.getTenantStatusByCode`（存在性/状态机/过期/配额摘要，未知租户 `exists=false` 数据化返回）；新增 `InternalTenantHandler` + `InternalTenantRouter`（`GET /internal/v1/tenants/{code}/status`，含 OpenAPI 元数据） |
| apex-auth | 领域端口 `TenantStatusPort`；`AuthenticationApplicationService.login` 在用户查询前前置校验（fail-closed） |
| apex-auth-service | `RemoteTenantStatusAdapter`：WebClient 消费 system 内部端点，判定缓存（正/负结果均缓存，默认 60s）；**fail-closed**——租户中心不可达时拒绝，仅平台 `default` 租户白名单放行（平台自愈通道）；`base-url` 空 = 校验跳过（basic/dev 档） |
| 配置 | auth `application.yml` `apex.tenant-check.*` + `.env.example` `TENANT_CHECK_BASE_URL/CACHE_SECONDS/TIMEOUT_MILLIS` |
| A6-3（file 适配器） | **保留降级实现**（`ConfiguredTenantStatusAdapter` + `file.share.suspended-tenants`）：内部端点契约已就绪，file 侧切换为后续项（避免本批新增 file→system 跨服务耦合）；适配器自述"显式降级、不替代租户中心实时状态"口径不变 |

## 2. 新增/变更测试

| 测试 | 数量 | 覆盖 |
| --- | --- | --- |
| auth-service `RemoteTenantStatusAdapterTest`（新增） | 4 | ACTIVE 放行、未知/冻结/过期拒绝、不可达 fail-closed + default 白名单、判定缓存（第二次不拉取） |
| apex-auth `AuthenticationMfaFlowTest`（扩展） | 6 | 新增"租户门拒绝时登录在用户查询前终止"（verifyNoInteractions(userRepository, tokenService)） |
| apex-auth `AuthenticationApplicationServiceTest`（同步） | 17 | 补第 8 依赖 Mock + lenient 桩 |
| V76 | `FlywayMigrationVersionTest` 自动纳入版本集合一致性与连续性校验；动态遍历逻辑由前后置断言守护 |

## 3. 过程缺陷与修复记录

1. **auth-service yml 顶层 `apex:` 键重复**（tenant-check 块与既有 L1 块冲突 → 32 个 Spring 上下文测试全崩）→ 合并入既有块；**教训同 B 批次前车**：新增 yml 段前先查顶层键。
2. `InternalTenantHandler` 三处笔误（`ServerRequest` 导错包、`Result.ok` 不存在、`@ApiResponses` 嵌套错位）→ 逐一修正。
3. 并行会话两次瞬态编译中间态（`UserProfileEntity`/`EducationLevel`，自行恢复）→ 未触碰其文件。

## 4. 结果

- 受影响 4 模块及上游 `test`：**BUILD SUCCESS**（apex-system 03:46 / apex-gateway / apex-auth 01:01 / apex-auth-service 修复 yml 后全绿；新增 10 用例全过）。
- `spotless:apply`（5 模块）→ 全仓 `spotless:check`：**通过**；`git diff --check`：**干净**；`check-doc-links.sh` 本批 **0 断裂**。
- 全量 `clean verify` 未重跑（增量集中于四模块且模块级 test 全绿；全量纳入下一批门禁）。

## 5. 未验证项与边界

- **V76 真实双库演练未执行**：动态 SQL 与断言逻辑经静态核对与版本集合测试，真实 MySQL 9/PostgreSQL 17 容器演练按大方案迁移验证工具口径列为 B 批次实施项（建议 B2 迁移前合并演练）。
- **运行时联调未执行**：frozen 租户登录拒绝、租户中心不可达 fail-closed 的端到端时延（含 60s 缓存窗口）需 Compose 冒烟。
- **A6-3 file 切换未实施**（保留降级，见 §1）。
- **并行会话**：执行期间出现两次瞬态编译中间态（UserProfileEntity/EducationLevel、AdminRoleAspect 等），本批未触碰其文件；auth-service 测试曾因并行改动窗口出现 2394s 拖长运行。
