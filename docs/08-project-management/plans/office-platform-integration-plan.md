# 企业办公平台集成实施计划

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Apex Scaffold 与 apex-admin-web 基础上，集成飞书、钉钉、企业微信，提供统一登录与绑定、组织通讯录、消息与机器人、审批、日历会议、文件文档等日常办公能力，并交付配置、脚本、文档、测试代码和测试报告。

**Architecture:** 在 infrastructure 层建立统一办公平台 SPI 与三套平台适配器，在 application 层编排平台身份绑定、组织同步、消息、审批、日历和文件用例，在 interfaces 层以函数式 WebFlux Handler/Router 暴露统一 REST API 与回调端点。各租户独立配置平台应用，平台身份只用于校验和调用，最终由 Apex 统一签发现有 JWT/Refresh Token；平台 token、回调 state、幂等键和租户上下文进入受控的 Redis/数据库存储。

**Tech Stack:** Java 17、Spring Boot 3.5.15、Spring WebFlux、WebClient、MyBatis-Plus、Flyway、Redis/Redisson、MapStruct、SpringDoc、JUnit 5、Mockito、WebTestClient、MockWebServer/WireMock；Vue 3、TypeScript、Vue Router、Pinia、Element Plus、现有 request/auth 工具链。

---

## 一、当前状态分析

- 后端模块已经按 `interfaces → application → domain ← infrastructure` 分层，HTTP 使用函数式 Handler + Router，统一响应使用 `Result<T>`。
- `apex-infrastructure` 已存在微信相关适配代码，可复用 `ConfigurationProperties`、专用 WebClient、Reactor `Mono` 和平台异常模式；当前没有飞书、钉钉以及完整企业微信办公闭环。
- `SocialLoginHandler` 目前用硬编码 provider switch，并且受 `keycloak.enabled` 条件控制；回调读取 query 中的 accessToken/refreshToken，需改为服务端授权码、state 与签名校验后再换取 Apex JWT。
- `apex-admin-web` 已有登录页、`/oauth2/callback` 路由、OAuth state 检查、token 存储、用户身份加载和统一 request 封装，适合扩展为平台选择与办公平台管理入口。
- 配置按 `application.yml` 与 profile 文件组织，敏感值通过环境变量；项目要求使用 `mvnd`、Spotless、PMD、ArchUnit 和受影响模块测试。

## 二、已确定的产品决策

- 平台范围：飞书、钉钉、企业微信。
- 能力范围：统一登录/账号绑定、组织通讯录、消息与机器人、审批、日历会议、文件/文档。
- 认证：平台 OAuth/免登完成身份确认后，由 Apex 签发现有 access token 与 refresh token。
- 配置：每个 Apex 租户独立维护各平台应用配置，同时允许平台能力开关、权限范围和回调地址独立配置。
- 身份：管理员发起或批准绑定；一个 Apex 用户可按平台绑定账号，禁止未经授权自动创建或跨租户绑定。
- 测试：Mock 自动化覆盖为主；提供真实平台联调脚本，凭据仅来自环境变量，不写入仓库。
- 前端：实现登录与回调、平台配置、账号与组织绑定、办公操作台。

## 三、数据模型与基础契约

### Task 1: 建立办公平台领域模型与数据库迁移

**Files:**
- Create: `apex-domain/src/main/java/com/apex/scaffold/domain/office/OfficePlatform.java`
- Create: `apex-domain/src/main/java/com/apex/scaffold/domain/office/OfficePlatformApp.java`
- Create: `apex-domain/src/main/java/com/apex/scaffold/domain/office/OfficeIdentityBinding.java`
- Create: `apex-domain/src/main/java/com/apex/scaffold/domain/office/OfficeOrganizationMapping.java`
- Create: `apex-domain/src/main/java/com/apex/scaffold/domain/office/OfficeSyncTask.java`
- Create: `apex-domain/src/main/java/com/apex/scaffold/domain/office/OfficeRepository.java`
- Create: `apex-infrastructure/src/main/resources/mapper/office/Office*.xml`
- Create: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/po/Office*.java`
- Create: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/mapper/Office*Mapper.java`
- Create: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/repository/OfficeRepositoryImpl.java`
- Create: `apex-interfaces/src/main/resources/db/migration/mysql/V29__create_office_platform_tables.sql`
- Create: `apex-interfaces/src/main/resources/db/migration/postgresql/V29__create_office_platform_tables.sql`
- Test: `apex-domain/src/test/java/com/apex/scaffold/domain/office/*Test.java`
- Test: `apex-infrastructure/src/test/java/com/apex/scaffold/infrastructure/office/*Test.java`

- [ ] Define platform enum values `FEISHU`, `DINGTALK`, `WECOM`; model tenant-scoped app credentials, encrypted secret references, enabled capabilities, redirect/signing settings, identity binding status, external organization/department/user IDs, sync cursor and failure state.
- [ ] Add tenant-aware unique constraints: `(tenant_id, platform, external_user_id)`, `(tenant_id, platform, external_org_id)`, and one active binding per Apex user/platform/account according to existing tenant conventions.
- [ ] Add created/updated/operator/audit fields, logical deletion and optimistic locking fields consistent with existing tables.
- [ ] Implement PO/entity conversion with MapStruct and repository methods for tenant-filtered CRUD, binding lookup and sync cursor persistence.
- [ ] Write migration validation tests for both database dialect resources and architecture tests for domain dependency direction.

### Task 2: Define统一平台 SPI、配置与安全组件

**Files:**
- Create: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/spi/OfficePlatformClient.java`
- Create: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/spi/OfficePlatformCapabilities.java`
- Create: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/spi/OfficeOAuthClient.java`
- Create: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/security/OfficeSignatureVerifier.java`
- Create: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/security/OfficeStateService.java`
- Create: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/config/OfficePlatformProperties.java`
- Modify: `apex-interfaces/src/main/resources/application.yml`
- Modify: `apex-interfaces/src/main/resources/application-basic.yml`
- Modify: `apex-interfaces/src/main/resources/application-dev.yml`
- Modify: `apex-interfaces/src/main/resources/application-standard.yml`
- Modify: `apex-interfaces/src/main/resources/application-enterprise.yml`
- Modify: `apex-interfaces/src/main/resources/application-performance.yml`
- Modify: `apex-interfaces/src/main/resources/application-prod.yml`
- Test: `apex-infrastructure/src/test/java/com/apex/scaffold/infrastructure/office/security/OfficeStateServiceTest.java`
- Test: `apex-infrastructure/src/test/java/com/apex/scaffold/infrastructure/office/config/OfficePlatformPropertiesTest.java`

- [ ] Define capability-neutral contracts for OAuth start/exchange/refresh, identity lookup, organization listing, message send, approval query, calendar query, file metadata/download/upload and event acknowledgement.
- [ ] Implement Redis-backed one-time state with tenant, platform, redirect URI, nonce, expiry and consumed marker; reject missing, expired, reused or mismatched state.
- [ ] Define environment-variable-backed tenant app configuration with no secret logging; support disabled platform and per-capability scopes.
- [ ] Add timeout, retry and error mapping policies to the existing WebClient/blocking-I/O conventions.

## 四、三平台适配器

### Task 3: Implement 飞书 adapter

**Files:**
- Create: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/feishu/FeishuOAuthClient.java`
- Create: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/feishu/FeishuOfficeClient.java`
- Create: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/feishu/FeishuSignatureVerifier.java`
- Create: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/feishu/FeishuWebClientConfig.java`
- Create: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/feishu/FeishuApiException.java`
- Test: `apex-infrastructure/src/test/java/com/apex/scaffold/infrastructure/office/feishu/*Test.java`

- [ ] Cover tenant app OAuth login, tenant/user token exchange, user identity, departments/members, bot/message APIs, approval instances, calendar events/meetings and drive file metadata/content operations through the common SPI.
- [ ] Verify Feishu callback signature/timestamp/nonce and event challenge handling; reject stale timestamps and invalid signatures.
- [ ] Use fixture-driven MockWebServer tests for success, rate limit, expired token, permission denial, malformed payload and upstream timeout.

### Task 4: Implement 钉钉 adapter

**Files:**
- Create: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/dingtalk/DingTalkOAuthClient.java`
- Create: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/dingtalk/DingTalkOfficeClient.java`
- Create: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/dingtalk/DingTalkSignatureVerifier.java`
- Create: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/dingtalk/DingTalkWebClientConfig.java`
- Create: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/dingtalk/DingTalkApiException.java`
- Test: `apex-infrastructure/src/test/java/com/apex/scaffold/infrastructure/office/dingtalk/*Test.java`

- [ ] Cover app OAuth/免登, user and department directory, work notification/robot, approval, calendar and file APIs through the common SPI.
- [ ] Implement DingTalk callback signature verification and event challenge/acknowledgement with replay protection.
- [ ] Add MockWebServer fixtures for token refresh, permission scope errors, throttling, network failure and provider error responses.

### Task 5: Complete 企业微信 adapter

**Files:**
- Modify: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/wechat/config/WeChatAppProperties.java`
- Modify: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/wechat/service/WeWorkService.java`
- Create: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/wecom/WeComOAuthClient.java`
- Create: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/wecom/WeComOfficeClient.java`
- Create: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/wecom/WeComSignatureVerifier.java`
- Create: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/wecom/WeComWebClientConfig.java`
- Test: `apex-infrastructure/src/test/java/com/apex/scaffold/infrastructure/office/wecom/*Test.java`

- [ ] Separate existing consumer WeChat OAuth code from enterprise WeCom app operations while preserving compatible behavior.
- [ ] Cover enterprise app login, access token, departments/members, application messages, approval, calendar and media/file APIs.
- [ ] Implement token callback signature/decryption, event acknowledgement, replay protection and idempotency.

## 五、应用层用例与身份闭环

### Task 6: Implement application services and MapStruct DTO assembly

**Files:**
- Create: `apex-application/src/main/java/com/apex/scaffold/application/service/office/OfficeAuthApplicationService.java`
- Create: `apex-application/src/main/java/com/apex/scaffold/application/service/office/OfficeBindingApplicationService.java`
- Create: `apex-application/src/main/java/com/apex/scaffold/application/service/office/OfficeOrganizationApplicationService.java`
- Create: `apex-application/src/main/java/com/apex/scaffold/application/service/office/OfficeMessageApplicationService.java`
- Create: `apex-application/src/main/java/com/apex/scaffold/application/service/office/OfficeApprovalApplicationService.java`
- Create: `apex-application/src/main/java/com/apex/scaffold/application/service/office/OfficeCalendarApplicationService.java`
- Create: `apex-application/src/main/java/com/apex/scaffold/application/service/office/OfficeFileApplicationService.java`
- Create: `apex-application/src/main/java/com/apex/scaffold/application/dto/office/*.java`
- Create: `apex-application/src/main/java/com/apex/scaffold/application/assembler/office/*.java`
- Test: `apex-application/src/test/java/com/apex/scaffold/application/service/office/*Test.java`

- [ ] Implement login start/callback: validate state, exchange platform code, resolve external identity, require approved tenant-scoped binding, then invoke existing Apex token issuance service.
- [ ] Implement administrator-only binding create/approve/reject/unbind with duplicate, conflict, inactive tenant and cross-tenant rejection.
- [ ] Implement full sync with pagination, department/member mapping, deletion/update reconciliation, dry-run preview, retry state and tenant context.
- [ ] Implement message, approval, calendar and file use cases with platform capability checks and unified DTOs.
- [ ] Use `TransactionalOperator` for reactive transaction boundaries and existing scheduler for blocking repository operations.

## 六、接口层与 OpenAPI

### Task 7: Add routers, handlers, validation and permissions

**Files:**
- Create: `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/handler/OfficeAuthHandler.java`
- Create: `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/handler/OfficeConfigHandler.java`
- Create: `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/handler/OfficeBindingHandler.java`
- Create: `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/handler/OfficeOrganizationHandler.java`
- Create: `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/handler/OfficeOperationHandler.java`
- Create: `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/handler/OfficeCallbackHandler.java`
- Create: `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/router/OfficeRouter.java`
- Create: `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/request/office/*.java`
- Create: `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/response/office/*.java`
- Modify: existing router aggregation file discovered during implementation
- Modify: Flyway menu/permission seed files for office administration
- Test: `apex-interfaces/src/test/java/com/apex/scaffold/interfaces/handler/office/*Test.java`
- Test: `apex-interfaces/src/test/java/com/apex/scaffold/interfaces/router/OfficeRouterTest.java`
- Test: `apex-interfaces/src/test/java/com/apex/scaffold/interfaces/test/OfficeOpenApiContractTest.java`

- [ ] Add public endpoints for platform login start/callback and callback verification; never accept access/refresh tokens from query parameters.
- [ ] Add authenticated tenant-admin endpoints for platform app config, connectivity test, binding approval, organization sync and capability status.
- [ ] Add authenticated user endpoints for binding status and approved bind/unbind flow.
- [ ] Add operation endpoints for message send, approval list/detail/action, calendar list/create/update/cancel, file list/upload/download.
- [ ] Add separate platform callback endpoints with signature verification, body size limits, idempotency and fast acknowledgement.
- [ ] Annotate routers with `@RouterOperation(s)`, handlers with Chinese JavaDoc and `@Operation`, DTOs with `@Schema` and parameters with `@Parameter`; use existing validation and Result conversion.

## 七、前端功能

### Task 8: Add frontend APIs, stores and OAuth flow

**Files:**
- Modify: `/Users/work/apex-admin-web/src/api/auth.ts`
- Modify: `/Users/work/apex-admin-web/src/api/oauth2.ts`
- Create: `/Users/work/apex-admin-web/src/api/office.ts`
- Modify: `/Users/work/apex-admin-web/src/views/login/index.vue`
- Modify: `/Users/work/apex-admin-web/src/views/oauth2/callback.vue`
- Create: `/Users/work/apex-admin-web/src/stores/office.ts`
- Modify: `/Users/work/apex-admin-web/src/router/index.ts`
- Test: `/Users/work/apex-admin-web/src/api/__tests__/office.test.ts`
- Test: `/Users/work/apex-admin-web/src/views/oauth2/__tests__/callback.test.ts`

- [ ] Add platform discovery/configured-status API and login URL generation; preserve current token and route guard behavior.
- [ ] Handle provider, code, state, error and error_description uniformly in the callback page; call server exchange endpoint and then load identity.
- [ ] Add typed APIs for platform config, connectivity check, binding approval, organization sync, messages, approvals, calendar and files.
- [ ] Add office store for selected platform, capability status, sync progress and binding state.

### Task 9: Build frontend administration and office console pages

**Files:**
- Create: `/Users/work/apex-admin-web/src/views/office/config/index.vue`
- Create: `/Users/work/apex-admin-web/src/views/office/bindings/index.vue`
- Create: `/Users/work/apex-admin-web/src/views/office/organization/index.vue`
- Create: `/Users/work/apex-admin-web/src/views/office/console/index.vue`
- Create: `/Users/work/apex-admin-web/src/views/office/components/PlatformSelector.vue`
- Create: `/Users/work/apex-admin-web/src/views/office/components/BindingTable.vue`
- Create: `/Users/work/apex-admin-web/src/views/office/components/SyncStatus.vue`
- Modify: `/Users/work/apex-admin-web/src/router/index.ts`
- Modify: frontend view registry/menu integration files discovered from existing router conventions
- Test: `/Users/work/apex-admin-web/src/views/office/__tests__/*.test.ts`

- [ ] Add platform cards and forms for tenant-admin app credentials, enabled scopes, callback URLs, webhook verification and connectivity testing; secrets use masked inputs and are never rendered back in plaintext.
- [ ] Add binding approval/rejection/unbind views and organization tree/member sync preview with conflict/error states.
- [ ] Add office console tabs for messages, approvals, calendar and files using typed API responses and existing Result/request patterns.
- [ ] Register routes and ensure backend menu component names match the frontend view registry exactly.

## 八、配置、脚本、文档与报告

### Task 10: Add environment templates and operational scripts

**Files:**
- Modify: `apex-interfaces/src/main/resources/application.yml`
- Modify: all existing profile YAML files
- Create: `config/office-platforms.env.example`
- Create: `scripts/office/validate-office-config.sh`
- Create: `scripts/office/register-webhook.sh`
- Create: `scripts/office/smoke-test.sh`
- Create: `scripts/office/generate-test-report.sh`

- [ ] Document every platform/tenant configuration key, endpoint, timeout, scope, webhook secret, encryption key reference and feature flag with environment-variable defaults that are empty/disabled.
- [ ] Make validation fail with actionable messages for missing required values, invalid callback URLs, weak secrets, duplicate platform tenant keys and incompatible capability flags.
- [ ] Make smoke script target a configurable base URL and test tenant, use only environment-provided credentials, redact response secrets and support dry-run.
- [ ] Generate JUnit XML aggregation, coverage summary, static-check output and a Markdown/HTML test report under `target/reports/office/` without committing generated artifacts.

### Task 11: Produce complete implementation documentation

**Files:**
- Create: `docs/01-guides/office-platform-integration-guide.md`
- Create: `docs/01-guides/office-platform-configuration.md`
- Create: `docs/01-guides/office-platform-security.md`
- Create: `docs/03-api/office-platform-api.md`
- Create: `docs/06-testing/office-platform-test-guide.md`
- Create: `docs/diagrams/office-platform-architecture.mmd`
- Modify: `docs/README.md` or documentation index discovered in repository

- [ ] Describe tenant configuration, platform console setup, callback/webhook registration, permission scopes, local Mock testing, production deployment and troubleshooting.
- [ ] Specify identity binding rules, tenant isolation, token handling, state/replay protection, signature verification, idempotency and audit requirements.
- [ ] Document all REST request/response examples, error codes, capability matrix and frontend routes.
- [ ] Include Mermaid architecture/data-flow/sequence diagrams and validate links/diagram conventions with existing scripts.

## 九、测试与验收

### Task 12: Execute verification and publish test report

**Files:**
- Test updates across the files above
- Output: `target/reports/office/office-platform-test-report.html`
- Output: `target/reports/office/office-platform-test-report.md`

- [ ] Run focused domain, infrastructure adapter, application, interface and frontend tests using repository-standard commands.
- [ ] Run contract tests for all provider callbacks, OAuth state, signature verification, tenant isolation, binding conflicts, pagination, retries, token expiry and idempotency.
- [ ] Run `mvnd spotless:check`, `mvnd pmd:check`, `mvnd -pl apex-interfaces -am test`, relevant frontend lint/typecheck/test commands discovered from `package.json`, and documentation link/consistency checks.
- [ ] Run `git diff --check` and inspect generated report contents; report exact commands, pass/fail result, coverage and any limitation caused by absent real platform credentials.
- [ ] Keep real platform smoke tests opt-in and clearly mark them skipped when credentials or public callback infrastructure are unavailable.

## 十、假设与约束

- 企业微信是微信侧的目标平台；现有公众号/微信 App OAuth 能力保留，但不作为本次办公平台统一能力的替代。
- 平台 SDK 只有在确认根 POM/模块已有依赖或官方兼容性后才引入；优先使用 WebClient 调用官方 HTTP API，避免不必要的 SDK 锁定。
- 不修改已执行 Flyway 脚本；新版本从当前实际最高版本顺序递增，计划中的 `V29` 需实施前通过目录核验并调整为真实下一个版本。
- 所有敏感配置只来自环境变量、配置中心或未跟踪本地文件；不在日志、响应、测试 fixture 或文档中放真实密钥。
- 不使用注解式 Controller；所有新增 HTTP 接口遵循现有 Handler/Router 模式。

## 十一、实施顺序

1. 先完成 Task 1-2 的数据模型、SPI、安全状态与配置。
2. 并行完成 Task 3-5 的三个平台适配器及 Mock 测试。
3. 完成 Task 6-7 的应用用例和后端接口，再接入 Task 8-9 前端。
4. 完成 Task 10-11 的运维交付物与文档。
5. 执行 Task 12 的全量验证，修复失败项后生成最终测试报告。

该计划覆盖统一认证、租户应用配置、组织同步、消息、审批、日历、文件、前端管理、运维脚本、文档、Mock 自动化测试和测试报告；真实平台联调作为凭据与公网回调条件满足后的可选验收步骤。