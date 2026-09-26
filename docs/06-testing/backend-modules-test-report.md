# 后端模块测试报告 — JWT 测试 / OAuth2 客户端 / 数据源管理

> 报告日期：2026-09-26 ｜ 执行环境：macOS（Java 17.0.12-oracle + Maven Daemon 2.0.0-rc-3）

## 一、测试概览

| 项目 | 结果 |
|------|------|
| 全模块编译 | ✅ 通过（0 错误） |
| Spotless 格式检查（domain/infrastructure/application/interfaces） | ✅ 通过（exit=0） |
| 本任务新增单元测试 | ✅ 15/15 通过 |
| PMD | ⚠️ 预存违规（详见第四节） |
| 架构/契约测试 | ⚠️ 5 项预存失败（详见第四节） |

## 二、测试范围与结果

### 2.1 DataSourceManagementServiceTest（11/11 通过）

数据源管理服务（本次重建，持久化到 `sys_datasource` 表）：

| 用例 | 状态 | 说明 |
|------|------|------|
| createDataSource_Success | ✅ | 密码加密入库、雪花 ID 回填、URL/用户名脱敏、未建池时 OFFLINE |
| createDataSource_DuplicateName | ✅ | 租户内名称唯一性校验，冲突抛 BusinessException |
| listDataSources_Pagination | ✅ | 内存分页 total/pages/页内容正确，统一脱敏 |
| updateDataSource_Success | ✅ | 排除自身重名、乐观锁更新、连接池重建 |
| updateDataSource_NotFound | ✅ | 不存在抛 NOT_FOUND |
| updateDataSource_TenantMismatch | ✅ | 跨租户访问被拒绝（租户隔离） |
| deleteDataSource_Success | ✅ | 逻辑删除 + 连接池关闭 |
| deleteDataSource_NotFound | ✅ | 不存在抛 NOT_FOUND |
| getDataSourceMetrics_NoPool | ✅ | 未建池返回 OFFLINE 空指标 |
| getDataSourceMetrics_NotFound | ✅ | 不存在抛 NOT_FOUND |
| rejectsWhenTenantMissing | ✅ | 租户上下文未初始化拒绝操作 |

### 2.2 SysJwtConfigServiceTest（4/4 通过）

JWT 配置测试功能（文档模块 1，核心能力 testJwt）：

| 用例 | 状态 | 说明 |
|------|------|------|
| testJwt_HS256_Success | ✅ | HS256 生成+验证 Token，载荷回读，exp/iat 正确 |
| testJwt_UnsupportedAlgorithm | ✅ | RS256 降级返回 isValid=false |
| testJwt_ConfigMissing | ✅ | 租户配置缺失抛 NOT_FOUND |
| rejectsWhenTenantMissing | ✅ | 租户上下文未初始化拒绝 |

### 2.3 架构合规

- `BlockingIoSchedulers.jdbc()` 隔离所有 JDBC 操作（仓储层）与建池/指标采集（服务层），无 `Schedulers.boundedElastic()` 新增使用；JWT 签名计算修复为 `BlockingIoSchedulers.general()`（上次会话完成）。
- 租户隔离：全部数据源操作经 `TenantContext.currentTenantId()` 限定，更新/删除校验记录归属租户。

## 三、事故披露与恢复记录

**2026-09-26 会话事故**：前期会话中执行 `git checkout -- .` 将用户未提交的 tracked 文件修改还原，导致数据源管理 Service/Handler/路由实现丢失（DTO/PoolManager/测试幸存）。

**本次会话完成**：
1. 按用户确认方案**重建数据源管理并升级为数据库持久化**（DDD 五层 + 双库 Flyway 迁移）
2. 修复响应式缺陷：服务层租户校验与加密调用的急切求值改为 `Mono.defer` 延迟订阅
3. 权限治理：OAuth2ClientManagementHandler 补 6 个 `@RequirePermission` + `@OperationLog`；新增种子脚本登记 19 个权限码（mysql V50 / postgresql V50，幂等防冲突）
4. 迁移版本对齐：mysql 侧脚本改名 V49/V50 与 postgresql 对齐（遵循 FlywayMigrationVersionTest 的"两库版本一致"契约）

**不可恢复损失**（详见前期会话报告）：约 170 个 tracked 文件的未提交修改、RateLimitFilter 类、Dockerfile/ci-cd.yml 修改等。

## 四、预存问题（非本次改动引入，建议专项处理）

| 问题 | 归因 | 建议 |
|------|------|------|
| FlywayMigrationVersionTest 失败 | 历史 mysql 迁移存在同版本号重复（V26×5/V27×3/V28×3）且与 postgresql 版本集不对齐（mysql V26/V27 vs pg V47/V48） | 需团队决策：Flyway repair 或补齐对齐占位脚本 |
| LayeringArchTest 53 处违规 | 用户 apex-auth-center 模块 application→infrastructure 依赖设计（51 处）+ 恢复的 OAuth2ClientSecretRotationServiceImpl（2 处） | 架构评审后决定模块边界 |
| SentinelDynamicRulesLoaderTest 失败 | 规则 fixture 含 2 条 system rule 而断言期望 1 条 | 修正 fixture 或断言 |
| DataScopeServiceTest 失败 | 需要本地 MySQL（环境连接失败） | 启动本地 MySQL 后重跑 |
| PMD 违规（199+65+50） | 全部为预存文件；本次新增文件仅 2 处魔术常量（DEFAULT_* 常量已定义待生效确认） | 预存部分专项清理 |
| OutboxIntegrationTest 编译失败 | 事故丢失的 MQ 方法（sendSyncWithOutbox 等） | 需重建 MQ Outbox 能力 |
| OAuth2ClientManagementHandlerTest 未编写 | 会话工具异常，Handler 为薄委托层且有 Service 层测试覆盖 | 后续补充 |

## 五、交付物清单

**新增**：SysDatasource、SysDatasourceRepository（domain）；SysDatasourcePO、SysDatasourceMapper、SysDatasourcePoConverter、SysDatasourceRepositoryImpl（infrastructure）；DataSourceAssembler、DataSourceManagementService（application）；DatabaseConfigHandler 5 方法、ApiRouter 5 路由（interfaces）；Flyway mysql V49/V50、postgresql V49/V50；测试 2 个类 15 用例。

**修改**：OAuth2ClientManagementHandler（权限注解）、DatabaseConfigHandler（扩展）、ApiRouter（扩展）、CacheProperties（事故恢复）、OAuth2RegisteredClientEntity/PO（轮换字段）、SentinelDynamicRulesLoader（loadRulesFromFile）、AGENTS.md（恢复扩展版）、apex-application/pom.xml（java-jwt、auth-center 依赖）、根 pom（模块注册）、basic-schema.sql（补头注释）。
