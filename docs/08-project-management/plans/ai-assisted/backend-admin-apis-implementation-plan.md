# 后端管理端 API 实施计划

> 依据:`/Users/work/apex-admin-web/docs/backend-development-tasks.html`
> 范围:代码生成器 + 认证授权配置 + SSO + 办公平台增强
> 路径冲突策略:**新增文档路径,复用现有 Service**(用户确认)
> 测试/文档策略:**完整测试 + Markdown 报告**(用户确认)

---

## 1. Context

前端 `apex-admin-web` 已完成 4 个模块的 UI 和 API 接口定义,后端 `apex-scaffold` 需提供对应业务实现。经代码扫描,部分功能(OAuth2 客户端管理、在线用户、办公应用管理)已存在但路径与文档约定不同;部分功能(JWT 配置 CRUD、权限码同步、会话配置、安全策略、SSO、代码生成器)完全缺失。

本计划聚焦**补齐文档要求的所有端点**,对已存在的服务通过**别名路由**复用,避免重复实现;对真正缺失的功能按 DDD 分层新建;并配套 Flyway 迁移、菜单/权限种子、集成测试和 Markdown 测试报告。

---

## 2. 现状盘点与复用映射

### 2.1 已存在 — 仅新增别名路由

| 文档要求路径 | 现有路径 | 现有 Handler/Service | 处理方式 |
|---|---|---|---|
| `GET/POST/PUT/DELETE /admin-api/v1/auth/config/oauth2/clients` | `/admin-api/v1/oauth2/clients` | `OAuth2ClientManagementHandler` + `OAuth2ClientManagementService` | 新增 `AuthConfigRouter` 别名路由 |
| `GET/DELETE /admin-api/v1/auth/sessions` | `GET /admin-api/v1/online-users` | `OnlineUserHandler` + `OnlineUserService` | 别名路由;补 `POST /sessions/kickout` 委托 `AdminUserManagementHandler` 现有逻辑 |
| `GET /admin-api/v1/auth/login-attempts` | `GET /admin-api/v1/login-logs` | `LoginLogHandler` + `LoginLogService` | 别名路由 |
| 权限码 CRUD `/admin-api/v1/auth/permissions` | `AuthorizationHandler` 已有 createPermission/updatePermission/deletePermission | `PermissionService` | 别名路由 |

### 2.2 完全缺失 — 新建 DDD 全栈

| 模块 | 缺失表 | 缺失端点 |
|---|---|---|
| **代码生成器** | `gen_datasource`, `gen_config`, `gen_template`, `gen_history` | `/admin-api/v1/generator/datasources*`, `/generator/configs*` |
| **JWT 配置 CRUD** | `sys_jwt_config` | `GET/PUT /admin-api/v1/auth/config/jwt` (现有 `JwtConfig.java` 是 `@ConfigurationProperties` 启动配置,非 CRUD 实体) |
| **会话配置** | `sys_session_config` | `GET/PUT /admin-api/v1/auth/config/session` |
| **安全策略** | `sys_security_policy` | `GET/PUT /admin-api/v1/auth/config/security` |
| **权限码同步** | — | `POST /admin-api/v1/auth/permissions/sync` (扫描 Controller 的 `@RequirePermission`) |
| **SSO** | `sys_sso_provider`, `sys_sso_session`, `sys_sso_audit_log` | `/admin-api/v1/sso/providers*`, `/sso/sessions*`, `/sso/audit-logs`, `/sso/statistics` |
| **办公平台增强** | — | `/admin-api/v1/office/messages`, `/office/organization/sync`, `/office/approval/*`, `/office/calendar/*` |

---

## 3. 实施步骤(按优先级)

### 阶段 0:Flyway 迁移(共 1 个版本号,MySQL + PostgreSQL)

新建 `V41__add_generator_auth_sso_office_tables.sql` (MySQL) 和同名 PostgreSQL 版本。**幂等**实现,使用 `CREATE TABLE IF NOT EXISTS` / `INSERT ... ON DUPLICATE KEY UPDATE` / `DO $$` 块。

涵盖 11 张表:
- 代码生成器: `gen_datasource`, `gen_config`, `gen_template`, `gen_history`
- 认证授权: `sys_jwt_config`, `sys_session_config`, `sys_security_policy`(注:`sys_login_attempt` 已被 `LoginLogHandler` 现有逻辑覆盖,不重复建表)
- SSO: `sys_sso_provider`, `sys_sso_session`, `sys_sso_audit_log`

菜单种子:`/admin-api/v1/auth/config/*`、`/admin-api/v1/sso/*`、`/admin-api/v1/generator/*`、`/admin-api/v1/office/messages` 等的菜单 + 权限码,按 `V23__add_database_config_menu.sql` 格式追加(注意 `component` 与前端 `VIEW_REGISTRY` 对齐)。

### 阶段 1:代码生成器(高优)

**DDD 分层(参考 `DictHandler` + `DictService` + `DictTypeRepositoryImpl` 模式):**

1. **domain**: `apex-domain/src/main/java/com/apex/scaffold/domain/model/generator/`
   - `GenDatasourceEntity`, `GenConfigEntity`, `GenTemplateEntity`, `GenHistoryEntity`
   - `GenDatasourceRepository`, `GenConfigRepository`, `GenTemplateRepository`, `GenHistoryRepository`(接口)
   - 值对象: `DbType`(枚举: MYSQL/POSTGRESQL/ORACLE/SQLSERVER), `ColumnType`(列信息)

2. **infrastructure**: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/`
   - `po/GenDatasourcePO.java`, `po/GenConfigPO.java`, `po/GenTemplatePO.java`, `po/GenHistoryPO.java`(继承现有 `BasePO`)
   - `mapper/GenDatasourceMapper.java` 等 4 个 MyBatis-Plus Mapper
   - `converter/GenDatasourcePoConverter.java` 等 4 个 MapStruct Converter(`@Mapper(componentModel="spring", unmappedTargetPolicy=ReportingPolicy.IGNORE)`)
   - `repository/GenDatasourceRepositoryImpl.java` 等 4 个实现,通过 `BlockingIoSchedulers.jdbc()` 隔离 JDBC

3. **application**: `apex-application/src/main/java/com/apex/scaffold/application/`
   - `service/GenDatasourceService.java`, `service/GenConfigService.java`, `service/CodeGenerationService.java`(模板渲染 + 文件生成)
   - `dto/admin/GenDatasourceRequest.java`, `dto/admin/GenDatasourceDTO.java`, `dto/admin/GenConfigRequest.java`, `dto/admin/GenConfigDTO.java`, `dto/admin/GeneratedFileDTO.java`, `dto/admin/TableInfoDTO.java`, `dto/admin/ColumnInfoDTO.java`
   - `assembler/GenDatasourceAssembler.java`, `assembler/GenConfigAssembler.java`(MapStruct)
   - `support/DataSourcePoolManager.java`(HikariCP 动态池管理)
   - `support/MetadataReader.java`(JDBC `DatabaseMetaData` 读取表/列)
   - `support/TypeMapper.java`(DB 类型 → Java 类型映射)
   - `support/CodeTemplateEngine.java`(FreeMarker 集成,沿用 `apex-generator/CodeGenerator.java` 既有逻辑)

4. **interfaces**: `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/`
   - `handler/GenDatasourceHandler.java`, `handler/GenConfigHandler.java`(继承 `BaseHandler`)
   - `router/GeneratorAdminRouter.java`(参考 `OfficeAdminRouter.java` 模式,使用 `SpringdocRouteBuilder` + `@RouterOperations`)
   - 路径: `/admin-api/v1/generator/datasources*`, `/generator/configs*`

**安全要点:**
- 密码字段使用项目现有 `EncryptUtil`(AES)加密存储,密钥来自环境变量
- 测试连接超时 5 秒,防止资源耗尽
- 生成代码前校验输出路径不超出工作目录白名单
- 权限码: `system:generator:list/add/edit/remove/test/generate`

### 阶段 2:认证授权配置(高优)

**JWT 配置 CRUD**:
- domain: `SysJwtConfigEntity`, `SysJwtConfigRepository`(接口)
- infrastructure: `SysJwtConfigPO`, `SysJwtConfigMapper`, `SysJwtConfigPoConverter`, `SysJwtConfigRepositoryImpl`
- application: `JwtConfigManageService`(注意与现有启动用的 `JwtConfig.java` 区分,前者是 CRUD,后者是 `@ConfigurationProperties`),`JwtConfigRequest`, `JwtConfigDTO`
- interfaces: `AuthConfigRouter` 中注册 `GET/PUT /admin-api/v1/auth/config/jwt`
- 保存后通过 `ApplicationEventPublisher` 发布 `JwtConfigUpdatedEvent`,由现有 `JwtConfig` 重新加载(不直接破坏启动配置)

**会话配置 / 安全策略**:同上 DDD 全栈,2 个表 → 2 套 CRUD。

**权限码同步**:
- application: `PermissionSyncService`(`@Component`),通过 `ApplicationContextAware` 扫描所有 `@RequirePermission` 注解,与 `sys_permission` 表 diff,返回新增/废弃清单
- interfaces: 在 `AuthConfigRouter` 注册 `POST /admin-api/v1/auth/permissions/sync`,委托给 `PermissionSyncService`

**别名路由(复用现有 Service)**:
- 新建 `apex-interfaces/.../router/AuthConfigRouter.java`,在同一个 `RouterFunction<ServerResponse>` Bean 中:
  - `GET/POST/PUT/DELETE /admin-api/v1/auth/config/oauth2/clients` → 调用 `OAuth2ClientManagementHandler` 现有方法
  - `GET /admin-api/v1/auth/sessions` → `OnlineUserHandler::list`
  - `POST /admin-api/v1/auth/sessions/kickout` → 委托 `AdminUserManagementHandler` 现有会话撤销逻辑(若无批量端点,新增一个轻量方法)
  - `GET /admin-api/v1/auth/login-attempts` → `LoginLogHandler` 现有方法
  - `GET/POST/PUT/DELETE /admin-api/v1/auth/permissions` → `AuthorizationHandler` 现有方法

### 阶段 3:SSO 单点登录(中优)

DDD 全栈,3 张表 → 3 套 CRUD + 1 个统计端点 + 1 个测试连接端点 + 1 个元数据端点。

**关键设计:**
- `sys_sso_provider.config` 字段为 JSON,使用现有 `JsonUtils` 序列化
- 协议适配器:`SsoProtocolAdapter` 接口 + 4 个实现(`SamlAdapter`, `CasAdapter`, `LdapAdapter`, `OidcAdapter`),通过 `SsoAdapterFactory` 按 `type` 路由
- 测试连接端点调用对应 Adapter 的 `testConnection()`,3 秒超时
- 元数据端点返回 Adapter 自描述(如 SAML metadata XML、OIDC discovery doc)
- 审计日志在所有 SSO 操作切面自动记录(`@Aspect` 切 `SsoService` 公开方法)
- 统计端点返回:按 provider 分组会话数、近 7 天登录成功/失败趋势

**路由:`SsoAdminRouter.java`**,路径全部为 `/admin-api/v1/sso/*`。

### 阶段 4:办公平台增强(低优)

经核查,`OfficeHandler` 现仅有平台状态/授权 URL/OAuth 回调;`OfficeAdminHandler` 现仅有 apps/bindings CRUD。需新增:

- `OfficeMessageHandler`(消息推送,复用现有 `OfficeMessageService` 若有,否则新建 + 消息模板管理)
- `OfficeOrganizationHandler`(组织架构增量/全量同步)
- `OfficeApprovalHandler`(审批流查询)
- `OfficeCalendarHandler`(日历事件管理)
- 路由加入 `OfficeAdminRouter.java` 现有 Bean 扩展

### 阶段 5:测试与报告

**测试范围:**
- 单元测试(`apex-application/src/test`):每个新 Service 1 个测试类,Mockito + `StepVerifier`,覆盖成功/参数校验/异常/边界
- 集成测试(`apex-interfaces/src/test`):每个新 Handler 1 个集成测试类,继承 `BaseIntegrationTest`,WebTestClient 调用,覆盖:
  - 成功路径 200
  - 未授权 401 / 权限不足 403
  - 参数校验失败 400
  - 资源不存在 404
  - 唯一约束冲突 409
- 仓储测试(`apex-infrastructure/src/test`):每个新 Repository 1 个测试类,验证分页/软删除/租户隔离

**测试命令:**
```bash
mvnd -pl apex-common,apex-domain,apex-application,apex-infrastructure,apex-interfaces -am test -P basic
```

**报告生成:**
1. 运行 `scripts/test/generate-test-report.sh` 自动从 `target/surefire-reports/` 抓取统计
2. 追加手写章节:新增接口清单、Flyway 迁移验证、菜单种子验证、安全检查清单
3. 输出 `docs/test-reports/backend-admin-apis-test-report-YYYYMMDD.md`(参考 `docs/test-reports/test-summary-20260916.md` 格式)

### 阶段 6:文档

- `docs/03-api/admin-apis-implementation-report.md`:本批次实现总览、接口清单、数据库表、安全策略、复用映射
- 由 SpringDoc 自动生成的 OpenAPI 文档验证(启动应用 + 访问 `/swagger-ui.html`)

---

## 4. 关键参考文件

| 用途 | 路径 |
|---|---|
| Handler 模板 | `apex-interfaces/.../handler/DictHandler.java` |
| Router 模板 | `apex-interfaces/.../router/OfficeAdminRouter.java` |
| Service 模板 | `apex-application/.../service/DictService.java` |
| RepositoryImpl 模板 | `apex-infrastructure/.../persistence/repository/DictTypeRepositoryImpl.java` |
| DTO 模板 | `apex-application/.../dto/admin/DictTypeRequest.java` |
| 测试基类 | `apex-interfaces/src/test/.../test/BaseIntegrationTest.java` |
| 测试报告模板 | `docs/test-reports/test-summary-20260916.md` |
| 菜单种子模板 | `db/migration/mysql/V23__add_database_config_menu.sql` |
| PG 菜单模板 | `db/migration/postgresql/V36__add_office_message_menu_and_permissions.sql` |
| 现有 JwtConfig | `apex-interfaces/.../config/JwtConfig.java`(启动配置,勿破坏) |
| 现有 OAuth2 | `apex-interfaces/.../config/OAuth2RouterConfig.java` + `OAuth2ClientManagementHandler.java` |
| 现有在线用户 | `apex-interfaces/.../handler/OnlineUserHandler.java` |
| 现有登录日志 | `apex-interfaces/.../handler/LoginLogHandler.java` |
| 现有权限管理 | `apex-interfaces/.../handler/AuthorizationHandler.java` + `PermissionService.java` |

---

## 5. 验证步骤(端到端)

1. **编译验证:** `mvnd -pl apex-interfaces -am compile -DskipTests`
2. **格式化:** `mvnd spotless:check`
3. **代码质量:** `mvnd pmd:check`
4. **Flyway 迁移:** 启动应用 → 验证 V41 执行成功 → `SELECT * FROM gen_datasource LIMIT 0` 等验证表存在 → `SELECT * FROM sys_menu WHERE path LIKE '/admin-api/v1/generator%'` 验证菜单种子
5. **单测:** `mvnd -pl apex-application -am test`
6. **集成测试:** `mvnd -pl apex-interfaces -am test -P basic`
7. **OpenAPI:** 启动应用 → `curl http://localhost:8080/v3/api-docs | jq '.paths | keys[] | select(startwith("/admin-api/v1/generator") or startwith("/admin-api/v1/sso") or startwith("/admin-api/v1/auth/config"))'`
8. **报告生成:** `bash scripts/test/generate-test-report.sh` → 校验 `docs/test-reports/backend-admin-apis-test-report-YYYYMMDD.md` 包含所有新测试通过统计

---

## 6. 约束与限制

- 严格遵守 `AGENTS.md`:WebFlux 函数式 Router+Handler、MapStruct、Lombok、`@RequiredArgsConstructor`、`mvnd`、中文 JavaDoc、租户隔离、阻塞 I/O 隔离、Flyway 幂等
- 不破坏现有 `JwtConfig.java` 启动配置;新 CRUD 仅通过事件解耦
- 不重复实现 OAuth2/在线用户/权限码 CRUD,仅加别名路由
- 不修改已执行的 Flyway 脚本(V1-V40),仅新增 V41
- 不引入 `@Controller`/`@RestController`
- 不在请求处理链调用 `.block()`
- 不创建文档文件,除非用户要求(本计划已显式要求,故例外)
