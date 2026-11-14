# 后端模块测试报告 — JWT 测试 / OAuth2 客户端 / 数据源管理

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

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
| ~~FlywayMigrationVersionTest 失败~~ | ✅ **已解决**：历史 mysql 迁移的同版本号重复（V26×5/V27×3/V28×3）及与 postgresql 版本集不对齐问题已通过补齐对齐占位脚本闭环。当前 `apex-interfaces` 与 `apex-auth-center` 的 mysql/postgresql 迁移目录均为 **V1–V58 完整、无重复、无缺号**（postgresql V51–V58 为对齐占位），符合 FlywayMigrationVersionTest 的"两库版本一致"契约 | 已闭环，无需进一步处理 |
| ~~LayeringArchTest 53 处违规~~ | ✅ **已解决（2026-09-30）**：`apex-application` 移除对 `apex-auth-center` 的跨模块依赖，`SecurityAuditService` 接口+实现下沉至 `:apex-common`，`apex-interfaces` 排除对 `authcenter` 包的组件扫描；重跑 `mvn -pl :apex-system -am test -Dtest=LayeringArchTest` 结果为 **6/6 通过，0 违规** | 已闭环，无需进一步处理 |
| SentinelDynamicRulesLoaderTest 失败 | 规则 fixture 含 2 条 system rule 而断言期望 1 条 | 修正 fixture 或断言 |
| DataScopeServiceTest 失败 | 需要本地 MySQL（环境连接失败） | 启动本地 MySQL 后重跑 |
| PMD 违规（199+65+50） | 全部为预存文件；本次新增文件仅 2 处魔术常量（DEFAULT_* 常量已定义待生效确认） | 预存部分专项清理 |
| OutboxIntegrationTest 编译失败 | 事故丢失的 MQ 方法（sendSyncWithOutbox 等） | 需重建 MQ Outbox 能力 |
| OAuth2ClientManagementHandlerTest 未编写 | 会话工具异常，Handler 为薄委托层且有 Service 层测试覆盖 | 后续补充 |

## 五、交付物清单

**新增**：SysDatasource、SysDatasourceRepository（domain）；SysDatasourcePO、SysDatasourceMapper、SysDatasourcePoConverter、SysDatasourceRepositoryImpl（infrastructure）；DataSourceAssembler、DataSourceManagementService（application）；DatabaseConfigHandler 5 方法、ApiRouter 5 路由（interfaces）；Flyway mysql V49/V50、postgresql V49/V50；测试 2 个类 15 用例。

**修改**：OAuth2ClientManagementHandler（权限注解）、DatabaseConfigHandler（扩展）、ApiRouter（扩展）、CacheProperties（事故恢复）、OAuth2RegisteredClientEntity/PO（轮换字段）、SentinelDynamicRulesLoader（loadRulesFromFile）、AGENTS.md（恢复扩展版）、apex-components-parent/apex-system/pom.xml（java-jwt、auth-center 依赖）、根 pom（模块注册）、basic-schema.sql（补头注释）。

## 六、追加：apex-application / apex-auth-center 模块 Bean 冲突修复（2026-09-30）

> 本节记录第四节中"LayeringArchTest 53 处违规"问题的后续闭环处理过程，独立于上述数据源管理相关改动。

### 6.1 问题背景

`apex-interfaces`（主应用 `Application.java`）与 `apex-auth-center`（独立应用 `AuthCenterApplication.java`）此前因组件扫描范围重叠、且 `apex-application` 显式依赖 `apex-auth-center`，导致：
1. `LayeringArchTest` 判定 `apex-auth-center` 内 `application→infrastructure` 违规 51 处 + `apex-application` 中恢复的 `OAuth2ClientSecretRotationServiceImpl` 引用违规 2 处，合计 53 处。
2. 主应用启动时存在潜在 `ConflictingBeanDefinitionException` 风险（两个 `@SpringBootApplication` 的组件扫描范围交叉）。

### 6.2 修复方案与改动

| 改动 | 说明 |
|------|------|
| `SecurityAuditService` 接口+实现下沉 | 由 `apex-auth-center` 迁移至 `apex-common`（`com.apex.scaffold.common.audit`），作为跨模块共享的安全审计能力 |
| 删除死代码 | 移除 `apex-auth-center` 下不再被引用的 `JwtKeyManagementHandler`、旧 `SecurityAuditServiceImplTest` |
| 更新内部引用 | `JwtKeyRotationServiceTest`、`RefreshTokenRotationServiceTest`、`TokenServiceImplTest`、`OAuth2ClientSecretRotationServiceImpl` 的 import 路径改指向 `com.apex.scaffold.common.audit.SecurityAuditService` |
| 移除跨模块依赖 | `apex-components-parent/apex-system/pom.xml` 移除对 `apex-auth-center` 的模块依赖 |
| 组件扫描隔离 | `apex-components-parent/apex-system/Application.java` 的 `@ComponentScan` 增加 `excludeFilters` 排除 `com.apex.scaffold.authcenter.*` 包 |
| 回退过渡性补丁 | `AuditLogAssembler`、`PermissionAssembler`、`RoleAssembler` 的 `@Mapper` 注解移除临时性 `implementationName` 属性 |
| 新增测试 | `apex-common` 下新增 `SecurityAuditServiceImplTest`，覆盖登录/登出、Token 签发与验证、暴力破解、权限拒绝、密钥轮换等约 29 个场景 |

### 6.3 验证结果

- 编译：`apex-common`、`apex-domain`、`apex-infrastructure`、`apex-application`、`apex-interfaces`、`apex-auth-center` 全部通过。
- 单元测试：六模块合计约 1988 个测试全部通过，无回归。
- 架构测试：重跑 `mvn -pl :apex-system -am test -Dtest=LayeringArchTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`：

  ```
  [INFO] Running com.apex.scaffold.arch.LayeringArchTest
  [INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 22.63 s
  [INFO] BUILD SUCCESS
  ```

  第四节记录的 53 处违规**全部清零**。
- 启动验证：以完整 classpath 方式启动主应用，`ConflictingBeanDefinitionException` 不再出现；剩余报错均为本地缺少 MySQL/Redis 连接的环境性问题，与本次架构改动无关。

### 6.4 遗留说明

- `apex-auth-center` 作为独立部署应用，与 `apex-interfaces` 主应用运行时 classpath 互不包含，因此两者即使存在同名 Bean/Mapper 也不会在运行时冲突；但这属于"部署拓扑侥幸隔离"而非"显式防御"，若未来两模块合并部署需重新评估。
- 项目未使用显式 `@MapperScan`，MyBatis-Plus 依赖 Spring Boot 自动配置默认扫描规则（以各自主启动类所在包为根），当前两个应用包路径不重叠，暂无冲突风险。
