# P0 实际 API 与表资产评估（代表性静态核查）

- 核查日期：2026-10-16。
- 范围：system、auth、workflow、file、office 的实际入口、代表性认证/租户调用边界及迁移定义。
- 证据基线：本次工作区可读源码；工作区已有其他改动，不能视为某个已提交版本的不可变快照。本文行号为核查时定位，后续修改可能使其漂移。
- 交付性质：**不是完整 API/表清单，不是当前数据库 schema，不构成 P0 准出，也不是安全审计通过证明。**

## 1. 依据、方法与限制

[AGENTS.md](../../../../AGENTS.md) 的“模块与物理边界”“Flyway 资源归属”“验证门禁”是本次归属与证据口径依据。当前实现不能用历史模块名替代；system 业务实现实际位于 `apex-system`，启动装配位于 `apex-system-service`。

[主实施方案](../../../02-architecture/jdk21-unified-auth-multitenancy-implementation-plan.md) L408–423 将 P0 定义为基线冻结，包含全部迁移、表/API/Java 声明/配置清单、依赖树、外部环境及决策前置条件。**本文只覆盖其中 API/表的代表性实际资产分析**，未完成其他 P0 项，也未完成全量冻结。

本次以文件发现和文本检索定位，再阅读可执行 Router 注册、Handler 方法体、相关安全配置/服务和 SQL；不将 `@RouterOperation`、注释、文档表格或检索命中数当作实际完整资产。尚未用 AST 解析组合路由、条件 Bean、动态 DDL，也未启动服务或连接数据库。

### 现有资料应如何使用

| 资料 | 已核查内容及使用边界 |
| --- | --- |
| [API_ROUTES.md](../../../API_ROUTES.md) L3–26 | 仍有“主应用/认证中心”双运行边界表述；可作历史契约线索，不能代替当前五域运行归属。 |
| [03-api/api-routes.md](../../../03-api/api-routes.md) L7、L41–79 | 文件身份及认证/授权接口线索；“需登录”等描述不能代替实际角色、操作权限及对象归属检查。 |
| [03-api/README.md](../../../03-api/README.md) L1–75 | 提供归档与文档入口；历史数量及本地 UI 示例不作为五域当前运行证明。 |
| [OpenAPI MANIFEST](../../../03-api/openapi/MANIFEST.md) L1–12 | 记录归档日期 2026-10-01、版本 `1.2.0-7fa3ebc` 和 Git `7fa3ebc`。`openapi-latest` 是归档文件名，不能证明当前工作区的运行路由。 |

## 2. 实际入口与归属

### 2.1 网关配置入口（不是运行路由导出）

以 [gateway application.yml](../../../../apex-gateway/src/main/resources/application.yml) 的配置值为证据，而非其顶部仍保留的透明代理阶段注释：

| 归属 | 配置中的 Path 与目标 | 定位 |
| --- | --- | --- |
| auth | `/auth/v1/**`、`/oauth2/**`、`/.well-known/**`、`/admin-api/v1/auth/**`、`/admin-api/v1/sso/**` → `ROUTE_AUTH_URI`，缺省 `lb://apex-auth-service` | L109–117 |
| workflow | `/api/v1/workflows/**`、`/api/workflow/**`、`/admin-api/v1/workflows/**` → `ROUTE_WORKFLOW_URI` | L121–129 |
| office 消息 | `/api/v1/messages` 及其子路径 → `ROUTE_OFFICE_MESSAGE_URI`；WS `/api/v1/messages/ws` 加 Upgrade 条件 → `ROUTE_OFFICE_WS_URI` | L136–149 |
| office 其他 | `/api/v1/office/**`、消息子路径、`/admin-api/v1/office/**`、`/admin-api/v1/org/**`、精确 `/admin-api/v1/message-templates` → `ROUTE_OFFICE_URI` | L150–158 |
| file | `/api/v1/file/**`、`/api/v1/files/**`、`/api/v1/documents/**`、`/admin-api/v1/documents/**` → `ROUTE_FILE_URI` | L161–169 |
| system 兜底 | `/api/v1/**`、`/admin-api/v1/**`、`/health`、`/actuator/**` → `ROUTE_SYSTEM_URI` | L193–201 |

这些配置没有展示 StripPrefix/RewritePath，因此本文按下游实际注册路径核对代表接口，不自行添加或删除前缀。Profile、环境变量、外部配置、路由顺序和实际可达性仍需运行导出确认。特别是 office 模板 Router 有 `/{templateCode}`、`/{templateId}` 子路径，而上述 office 网关模板项仅列精确根路径；子路径是否落到 system 兜底须专项验证，不能把根路径已配置等同子路径已切流。

### 2.2 后续全量枚举应从这些真实位置开始

| 域 | Router 实现入口 | 代表 Handler 入口 |
| --- | --- | --- |
| system | [interfaces/router](../../../../apex-system/src/main/java/com/apex/scaffold/interfaces/router/)，并检查 [interfaces/config](../../../../apex-system/src/main/java/com/apex/scaffold/interfaces/config/) 内协议路由配置 | [AuthorizationHandler](../../../../apex-system/src/main/java/com/apex/scaffold/interfaces/handler/AuthorizationHandler.java) |
| auth | [authservice/interfaces/router](../../../../apex-auth-service/src/main/java/com/apex/scaffold/authservice/interfaces/router/) | [AuthenticationHandler](../../../../apex-auth-service/src/main/java/com/apex/scaffold/authservice/interfaces/handler/AuthenticationHandler.java)、[InternalAuthHandler](../../../../apex-auth-service/src/main/java/com/apex/scaffold/authservice/interfaces/handler/InternalAuthHandler.java) |
| workflow | [workflowservice/interfaces/router](../../../../apex-workflow-service/src/main/java/com/apex/scaffold/workflowservice/interfaces/router/) | [WorkflowHandler](../../../../apex-workflow-service/src/main/java/com/apex/scaffold/workflowservice/interfaces/handler/WorkflowHandler.java) |
| file | [fileservice/interfaces/router](../../../../apex-file-service/src/main/java/com/apex/scaffold/fileservice/interfaces/router/) | [FileHandler](../../../../apex-file-service/src/main/java/com/apex/scaffold/fileservice/interfaces/handler/FileHandler.java) |
| office | [interfaces/router](../../../../apex-office-service/src/main/java/com/apex/scaffold/officeservice/interfaces/router/) 及 [interfaces/off/router](../../../../apex-office-service/src/main/java/com/apex/scaffold/officeservice/interfaces/off/router/) | [MessageHandler](../../../../apex-office-service/src/main/java/com/apex/scaffold/officeservice/interfaces/off/handler/MessageHandler.java) |

此表是入口地图，不声称目录内已逐方法完成核验；协议端点、WebSocket、内部端点及非 Router 类名的配置也不能遗漏。

## 3. 已确认的代表性 API 与具体安全边界

### 3.1 system：旧认证前缀仍有实际实现，权限不只看 Handler 注解

[PublicApiRouter](../../../../apex-system/src/main/java/com/apex/scaffold/interfaces/router/PublicApiRouter.java) L565–596 组合 `/api/v1` 与 `/auth`，L623–646 实际注册：

- `POST /api/v1/auth/login`、`POST /api/v1/auth/refresh`、`POST /api/v1/auth/logout`。
- `GET /api/v1/auth/roles/my`、`GET /api/v1/auth/permissions/my`。
- `POST /api/v1/auth/roles`、`POST /api/v1/auth/roles/update`、`DELETE /api/v1/auth/roles/{id}`。
- `POST /api/v1/auth/users/{userId}/roles/{roleId}` 及同路径 DELETE。

这些路径与 auth-service 的 `/auth/v1/*` 是不同入口，不能因名字相近合并计数或假定已统一签发。

[AuthorizationHandler](../../../../apex-system/src/main/java/com/apex/scaffold/interfaces/handler/AuthorizationHandler.java) L248–258 的 `createRole` 读取请求 DTO 后直接调用 roleService；L286–295 的更新类似；L326–344 删除先解析 id 再调用服务。上述方法体未提供逐对象 tenant 检查，`@OperationLog` 也不是授权证明。但不能据此判为“无授权”：

- [SecurityConfig](../../../../apex-system/src/main/java/com/apex/scaffold/interfaces/config/SecurityConfig.java) L136–159 先匹配公开/已认证路径，再按 `rbacEnabled` 分支限制管理路径；开启时 adminPaths 要求配置管理员角色或 `SUPER_ADMIN`，关闭时退为 authenticated。
- [SecurityProperties](../../../../apex-system/src/main/java/com/apex/scaffold/interfaces/config/SecurityProperties.java) L45–61 的默认 adminPaths 包含角色、权限及用户角色分配路径。

**边界结论：**已确认存在配置级角色控制，实际有效匹配还受前置路径列表、配置覆盖和 RBAC 开关影响；不能以 Handler 没有权限注解认定匿名可操作，也不能由路径角色限制推导目标角色/用户跨租户归属已受保护。后续需逐项连到 RoleService、仓储及实际安全链测试。

### 3.2 auth：用户主体一致性与内部调用者认证必须分开

[AuthenticationRouter](../../../../apex-auth-service/src/main/java/com/apex/scaffold/authservice/interfaces/router/AuthenticationRouter.java) L192–205 注册：

| 接口 | Handler 已确认行为 |
| --- | --- |
| `POST /auth/v1/login` | [AuthenticationHandler](../../../../apex-auth-service/src/main/java/com/apex/scaffold/authservice/interfaces/handler/AuthenticationHandler.java) L130–145 解析 LoginRequest 并委托认证服务；该 Handler 不单独证明账号、成员、client 的联合准入已成立。 |
| `POST /auth/v1/logout/{userId}` | 同文件 L198–211 要求已认证且 details 为 String；主体 name 必须等于路径 userId，不等返回403，无身份返回401；随后用主体 id 与 details tenant 调用 logout，成功返回200。不能按注释写成204或宣称全部 access token 已即时失效。 |
| `POST /auth/v1/refresh` | 注册已确认；刷新重放、撤销范围、租户/会话绑定尚需沿完整服务链验证，不继承注释的安全承诺。 |

[AuthCenterConfig](../../../../apex-auth-service/src/main/java/com/apex/scaffold/authservice/config/AuthCenterConfig.java) L193–226 的 Bearer 链调用 token 校验，要求 ACCESS、正 userId、非空 tenant，并将 tenant 放入认证 details；L173–177 对登录/OAuth 部分入口使用服务配置 `auth-center.login-tenant`（缺省 default）。这只是配置/过滤链事实，不等于已验证多租户成员模型。

内部入口由 [InternalAuthRouter](../../../../apex-auth-service/src/main/java/com/apex/scaffold/authservice/interfaces/router/InternalAuthRouter.java) L177–187 注册：

- `GET /internal/v1/users/{accountId}/permissions`： [InternalAuthHandler](../../../../apex-auth-service/src/main/java/com/apex/scaffold/authservice/interfaces/handler/InternalAuthHandler.java) L44–69 校验 accountId 为正整数，tenantId 来自必填查询参数，然后调用权限服务。
- `POST /internal/v1/tokens/validate`：同文件 L82–88 校验请求 DTO 后委托 token 验证；**验证被提交 token 不等于验证调用者身份**。

[AuthCenterConfig](../../../../apex-auth-service/src/main/java/com/apex/scaffold/authservice/config/AuthCenterConfig.java) L147–159 将 `/internal/v1/**` 列为 permitAll；L163–171 对内部请求从租户头读取 tenant，缺失时回退配置。所读 Handler 没有调用者凭据检查。**这是需要阻断式核验的内部信任边界：源码片段不要求调用者先取得 Bearer 认证，且存在请求可携带的租户输入。** 网关公开路由表没有这些内部路径不能替代服务直连保护；部署网络、mTLS/代理约束、是否另有生效的过滤器尚未验证，本文不声称已完成网络可利用性复现。

### 3.3 workflow：已有用户传递，不等于服务独立验签或对象隔离已闭环

[WorkflowRouter](../../../../apex-workflow-service/src/main/java/com/apex/scaffold/workflowservice/interfaces/router/WorkflowRouter.java) 的实际注册包括：

- L185–188：`GET /admin-api/v1/workflows/instances`、`GET /admin-api/v1/workflows/instances/{instanceId}` 等查询。
- L305–313：`POST /api/v1/workflows/{workflowCode}/instances`、`GET /api/v1/workflows/tasks/pending`、`POST /api/v1/workflows/tasks/{taskId}/complete`。
- L507–520：`POST /api/workflow/tasks/{taskId}/transfer`、delegate、rollback，以及实例 suspend/resume。不能统一改写为 `/api/v1/workflows`。

[WorkflowHandler](../../../../apex-workflow-service/src/main/java/com/apex/scaffold/workflowservice/interfaces/handler/WorkflowHandler.java) L432–440 的 transfer 从路径取 taskId、验证 DTO，将 `getCurrentUserId()` 的用户和新办理人传给服务；L640–647 从已认证 `AuthenticatedUser` 取 userId。实例详情 L250–253 则按 instanceId 委托查询。**这些方法不能独立证明 task/instance、新办理人与当前 tenant 的关联检查成立。**

[IdentityRelayAuthenticationFilter](../../../../apex-workflow-service/src/main/java/com/apex/scaffold/workflowservice/interfaces/security/IdentityRelayAuthenticationFilter.java) L52–81 直接读取 `X-User-Id`、`X-Tenant-Id`，构建已认证主体；本过滤器未验签。与 [WorkflowSecurityConfig](../../../../apex-workflow-service/src/main/java/com/apex/scaffold/workflowservice/config/WorkflowSecurityConfig.java) L52–62 的 authenticated 要求组合后，仍依赖身份头来源可信。不能把“存在 Spring Security”写成“服务独立验证 JWT”。服务直连/代理伪造身份头、tenant 缺失和跨租户对象负例需运行验证。

### 3.4 file：代表性对象存储入口已有显式归属链

[FileRouter](../../../../apex-file-service/src/main/java/com/apex/scaffold/fileservice/interfaces/router/FileRouter.java) L294–300 注册 `/api/v1/file` 下 upload、content、preview、url、delete、orphan retry、exists。

- [FileHandler](../../../../apex-file-service/src/main/java/com/apex/scaffold/fileservice/interfaces/handler/FileHandler.java) L501–507 的 `GET /url` 委托 `fileService.getUrl`；L665–671 的 `GET /content`、L731–739 的 preview 委托 download。bucket/key 是对象定位输入，不是身份来源。
- [FileService](../../../../apex-file/src/main/java/com/apex/scaffold/file/application/service/FileService.java) L274–300 的 URL/下载/删除先走 authorized；L323–333 先查存储位置对应记录、调用 identity.authorize，再检查 STORED 状态，之后才触达记录指定的存储提供商。
- [FileAccessContext](../../../../apex-file/src/main/java/com/apex/scaffold/file/application/service/FileAccessContext.java) L19–43 拒绝缺失 tenant/正 userId；授权比较记录 tenant、user，并拒绝空 storageType。
- [文件身份过滤器](../../../../apex-file-service/src/main/java/com/apex/scaffold/fileservice/interfaces/security/IdentityRelayAuthenticationFilter.java) L74–100、L109–136 从 Bearer 验签结果读取 ACCESS/userId/tenant，注入 FileAccessContext，而非仅信任外部身份头；无有效结果时不注入身份，最终是否返回401需结合实际安全链运行确认。

**边界结论：**以上对象访问调用链有明确 tenant+owner+存储归属/状态防线；不能推广成所有文件、文档、图表、分片、后台清理接口都已逐项核验。历史 NULL 归属不会被此链自动接受，应与迁移回填策略一起验证。

### 3.5 office：消息对象关系已检查，插件覆盖不等于全域覆盖

[MessageRouter](../../../../apex-office-service/src/main/java/com/apex/scaffold/officeservice/interfaces/off/router/MessageRouter.java) L258–265 注册 `POST /api/v1/messages`、GET inbox/sent/消息详情、PUT `/{messageId}/read`、PUT read-all、GET unread/count；L437–441 另注册 `/admin-api/v1/message-templates` 根及子路径。

[MessageHandler](../../../../apex-office-service/src/main/java/com/apex/scaffold/officeservice/interfaces/off/handler/MessageHandler.java) L195–205 将当前用户传给消息详情；L222–231 将当前用户传给标记已读；L279–286 仅从已认证 `AuthenticatedUser` 取 id，不使用客户端 recipientId 代替当前用户。

[OffMessageService](../../../../apex-office/src/main/java/com/apex/scaffold/office/off/application/service/OffMessageService.java) L131–140 对详情要求消息 tenant 等于 Reactor tenant，且用户是发件人或同租户接收人。不存在或不可见返回空，Handler 转404。已读接口的具体更新行数/接收人条件还需仓储及运行负例确认，不能由详情方法外推。

[MessageTenantConfig](../../../../apex-office-service/src/main/java/com/apex/scaffold/officeservice/config/MessageTenantConfig.java) L25–50 的 MyBatis 租户处理器缺租户即失败，且仅对 `off_message`、`off_message_recipient` 生效；**不证明模板、部门、员工及其他 office 表也被该插件隔离**。[office 身份过滤器](../../../../apex-office-service/src/main/java/com/apex/scaffold/officeservice/interfaces/security/IdentityRelayAuthenticationFilter.java) L88–104 对 HTTP 调用 verify，对 WS 校验 Origin/GET/Upgrade 并消费票据；票据跨实例、身份注入、重连补偿需独立运行验证。

## 4. 五域真实表定义例证与 tenant/索引缺口

### 4.1 物理迁移归属与口径

| 域 | 当前生产迁移资源根（两种方言分别核查） |
| --- | --- |
| system | [apex-system/db/migration](../../../../apex-system/src/main/resources/db/migration/) |
| auth | [apex-auth-service/db/migration](../../../../apex-auth-service/src/main/resources/db/migration/) |
| workflow | [apex-workflow-service/db/migration](../../../../apex-workflow-service/src/main/resources/db/migration/) |
| file | [apex-file-service/db/migration](../../../../apex-file-service/src/main/resources/db/migration/) |
| office | [apex-office-service/db/migration](../../../../apex-office-service/src/main/resources/db/migration/) |

每域独立版本链；system 历史保留其他域旧表不代表当前仍归 system 所有，也不能和独立库建表重复计为当前物理表。本文仅选取真实 CREATE/ALTER 的例证，不列全量表数，不将这些目录等同已运行库。

### 4.2 system：初建定义必须叠加后续 ALTER，平台表不能机械要求 tenant_id

- [MySQL V1](../../../../apex-system/src/main/resources/db/migration/mysql/V1__init_schema.sql) L608–640 真实定义 `sys_user`，初始无 tenant_id；`uk_open_id(open_id)` 是全局唯一键，phone 为普通索引。
- [MySQL V14](../../../../apex-system/src/main/resources/db/migration/mysql/V14__add_tenant_id_to_core_tables.sql) L21–28 给 `sys_user`、`sys_user_role` 增加可空 `tenant_id VARCHAR(64)` 与单列 tenant 普通索引。因此不能只看 V1 说源码未给 sys_user 加 tenant；也不能把普通 tenant 索引当成成员唯一约束。
- [MySQL V15](../../../../apex-system/src/main/resources/db/migration/mysql/V15__complete_tenant_isolation_schema.sql) L1–130 有条件动态 SQL 补列/索引；这说明纯 CREATE TABLE 正则清单会漏 ALTER、存储过程及 PREPARE/EXECUTE。本文未折叠整个版本链，不声称列出的旧唯一键就是最终库唯一键。
- [MySQL V17](../../../../apex-system/src/main/resources/db/migration/mysql/V17__create_sys_tenant.sql) L12–30 与 [PostgreSQL V17](../../../../apex-system/src/main/resources/db/migration/postgresql/V17__create_sys_tenant.sql) L12–30 真实定义 `sys_tenant`：全局 tenant_code 唯一、无 tenant_id；它是平台租户元数据，不应机械判为“缺租户列漏洞”。平台管理授权及实际忽略表配置仍需单独核验，不能仅依赖 SQL 注释。

**待闭合缺口：**可空历史归属、全局账号与租户成员的模型边界、关联表 tenant 一致性及最终索引/约束；不能通过统一补列替代这些判断。

### 4.3 auth：租户业务键已有约束，account 映射仍需对账

- [MySQL V44](../../../../apex-auth-service/src/main/resources/db/migration/mysql/V44__create_auth_center_core_tables.sql) L13–73 的 `auth_user` 有 NOT NULL tenant，`(tenant_id,username)`/`(tenant_id,phone)` 普通索引，以及 `(tenant_id,username,deleted)` 唯一键；L79–130 的 `auth_token` 有 NOT NULL tenant、`(tenant_id,user_id)` 索引及 jti 唯一约束。[PostgreSQL V44](../../../../apex-auth-service/src/main/resources/db/migration/postgresql/V44__create_auth_center_core_tables.sql) L6–44 可对照这些代表结构。
- [MySQL V54](../../../../apex-auth-service/src/main/resources/db/migration/mysql/V54__scope_oauth2_authorization_by_tenant.sql) L1–10 为 `oauth2_authorization` 加 NOT NULL tenant，默认 `default`，及 `(tenant_id,client_id)` 索引；默认值不是历史记录真实归属证明。
- [MySQL V55](../../../../apex-auth-service/src/main/resources/db/migration/mysql/V55__auth_user_account_binding.sql) L1–32、[PostgreSQL V55](../../../../apex-auth-service/src/main/resources/db/migration/postgresql/V55__auth_user_account_binding.sql) L9–11 增加可空 account_id 与普通 account_id 索引。**这项迁移本身不提供 account 映射唯一约束，也不证明存量回填完成。** 映射是否允许一对多须先确认模型，不能未经决策直接补唯一键。

### 4.4 workflow：tenant 非空仍可能存在跨租户业务唯一键冲突

[MySQL V1](../../../../apex-workflow-service/src/main/resources/db/migration/mysql/V1__init_workflow_schema.sql) 的真实定义：

| 表 | tenant 与索引例证 | 评估 |
| --- | --- | --- |
| `wf_workflow_definition` L34–52 | tenant NOT NULL；单列 tenant 索引；唯一 `(workflow_code,version,deleted)` 不含 tenant | 若业务编码应租户内唯一，此定义会阻止不同租户使用同编码/版本；需要业务语义确认及新迁移，不等于已证明读泄漏。 |
| `wf_process_instance` L57–81 | tenant NOT NULL；`(tenant_id,initiator_id)` 索引；唯一 `(business_key,deleted)` 不含 tenant | 同样需确认 business_key 是否全局键；tenant 普通索引不能修复唯一键作用域。 |
| `wf_task` L86–112 | tenant NOT NULL；`(tenant_id,assignee_id,status)` 索引；另有 timeout 索引 | 已有典型租户待办索引，但后台超时扫描是否跨租户受控及是否需另一复合索引，要结合 SQL/执行计划验证。 |

[PostgreSQL V1](../../../../apex-workflow-service/src/main/resources/db/migration/postgresql/V1__init_workflow_schema.sql) L34–118 对照确认上述定义/实例唯一键同样不含 tenant，task 有租户办理人状态索引。这是具体脚本语义核对，不代表两套全部迁移均已验证。

### 4.5 file：后续归属列保留 NULL，禁止按 V1 误报最终源码

- [MySQL V1](../../../../apex-file-service/src/main/resources/db/migration/mysql/V1__init_file_schema.sql) L38–63、L126–144 定义 `file_record`、`file_chunk`，初始无 tenant；L68–121 的 `file_document`、`file_chart` 有可空 tenant 及单 tenant 索引。
- [MySQL V4](../../../../apex-file-service/src/main/resources/db/migration/mysql/V4__add_file_storage_ownership.sql) L1–7 和 [PostgreSQL V4](../../../../apex-file-service/src/main/resources/db/migration/postgresql/V4__add_file_storage_ownership.sql) L1–7 给 record/chunk 增加可空 tenant、storage_type 及 `(tenant_id,user_id)` 索引，明确历史未知归属保持 NULL。不能写成“当前源码 file_record 无 tenant”。
- [MySQL V5](../../../../apex-file-service/src/main/resources/db/migration/mysql/V5__persist_chunk_session.sql) L1–3 追加 total_chunks、chunk_size，不是 tenant 非空回填迁移。

**缺口：**历史 NULL 行归属证据、文档/图表路径隔离及分片会话一致性仍需逐项验证；V4 ownership 是普通索引，不提供会话幂等或租户业务唯一性保证。是否需要其他索引应以真实查询条件、数据量和 EXPLAIN 决定。

### 4.6 office：表定义较完整，消息插件只覆盖局部

[MySQL V2](../../../../apex-office-service/src/main/resources/db/migration/mysql/V2__create_off_msg_org.sql) 的例证：

- `off_message_template` L37–53：tenant NOT NULL，唯一 `(template_code,tenant_id,deleted)`，索引 `(tenant_id,enabled,deleted)`。
- `off_message` L58–81：tenant NOT NULL，唯一 `(message_code,tenant_id,deleted)`，索引 `(tenant_id,status,deleted)`。
- `off_message_recipient` L86–103：tenant NOT NULL；唯一 `(message_id,recipient_id,deleted)`，普通 `(recipient_id,read_status,deleted)` 与 `(tenant_id,deleted)` 分开。
- `off_department` L108–132：tenant NOT NULL，部门编码唯一键和 parent 索引均含 tenant。

[PostgreSQL V2](../../../../apex-office-service/src/main/resources/db/migration/postgresql/V2__create_off_msg_org.sql) L38–108 对照确认消息模板、消息及接收人的这些列/索引结构。接收人唯一键不含 tenant 未必错误：若 message_id 是全局标识且关联 tenant 一致性受保证，不能机械判缺陷；需要实际关系写入与数据核验。租户+收件人复合查询是否需要新索引须 EXPLAIN；**索引优化与越权隔离是两个问题**。更直接的覆盖缺口是上述租户插件仅覆盖消息两表，其他表需要各自 SQL/插件/服务证据。

## 5. P0 剩余核验与退出限制

| 待办 | 必须取得的证据 | 当前状态 |
| --- | --- | --- |
| API 全量冻结 | AST 解析 Router builder/helper/nest/path 组合、条件 Bean、协议配置与 WS；保留 method/path/条件/Handler/模块/来源位置，处理重复及冲突 | 未执行；本文不提供全量数量 |
| 运行入口对照 | 实际生效的 profile/配置、网关路由导出、五服务运行路由及 OpenAPI，与源码逐项差异核对；包含模板子路径及内部直连 | 未执行 |
| 安全覆盖矩阵 | 逐接口分别记录认证、操作权限、资源 tenant/owner、成员状态、client 准入；追踪 Handler→应用→仓储/远程及缓存/事件 | 本文仅完成代表链静态核查 |
| 高优先级负例 | auth 内部匿名/伪造租户输入；workflow 直连伪造身份头；同角色跨 tenant 读写/转办；file 历史 NULL/跨 owner；office 跨 tenant 消息及非消息表 | 未执行；源码边界不能代替攻击复现或修复验收 |
| 表资产全量与归属 | 分域折叠 CREATE/ALTER/RENAME/DROP/条件动态 SQL，关联实体、Mapper、R2DBC SQL、后台任务；区分业务、平台元数据和基础设施表 | 未完成；不能从 CREATE 正则数量准出 |
| 双方言迁移演练 | 从空库及真实历史版本升级，记录 Flyway history/checksum、失败/跳过/修复情况；验证所有后续 ALTER 及约束语义 | 未执行 |
| 当前 schema 与数据 | 真实库 metadata、索引/约束/默认值、NULL/孤儿/重复映射、跨表 tenant 一致性和回填对账；记录库版本、环境与采集时间 | 未连接数据库 |
| 索引与约束决策 | 业务键全局/租户内语义批准，真实查询与 EXPLAIN、存量冲突及新迁移/回退方案 | 未执行，本文不修改历史迁移 |

迁移文件只是历史意图与源码输入。真实库可能受版本未执行、条件分支、Flyway baseline/repair、手工 DDL、旧版本升级路径及数据回填影响；不能以迁移目录存在就宣布当前 schema 一致。基础设施/平台级表也不能一律按“无 tenant_id”计缺陷。

## 6. 本次检查与结论

- 已静态阅读方案 P0、仓库规则、现有 API 资料，以及五域代表性可执行 Router、Handler、安全配置/服务和真实 CREATE/ALTER；对指定代表结构阅读了 PostgreSQL 对照，**没有宣称读完全部迁移**。
- 已确认优先继续核验的边界：auth 内部接口调用者认证与租户输入、workflow 对身份头的信任、system 配置角色与对象隔离分离、office 模板网关子路径与局部插件覆盖、file 未知历史归属，以及 workflow 业务唯一键作用域。
- 本次仅新增此报告；不改业务、迁移或其他文档，不提交。未启动服务、未运行 AST 枚举、数据库迁移或 Java 构建/测试，不声明 Java 21 兼容或任何运行门禁通过。
- **结论：本报告可作为后续全量资产冻结的证据入口与缺口清单；P0 保持未准出。**
