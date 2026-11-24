# BE-060 全模块 Handler SpringDoc / Swagger 注解补齐

> 任务卡：`docs/tasks/project-tasks.md` BE-060｜基线 `71e39a4f`｜执行日期 2026-11-22
> 依据：`AGENTS.md` §0.5-8、`docs/specs/project-governance.md` §6 红线 **B8**

## 1. 为什么做

红线 B8 要求「Handler 用 `@Tag`/`@Operation`，DTO 字段用 `@Schema`（说明、示例、必填、约束），保证 Swagger 可见完整请求体/响应体」。但该红线长期**没有真实守护**：

- CI 的 `scripts/ci/check-springdoc-coverage.sh` 宣称 100% 门槛，底层 `check-springdoc-annotations-v2.sh` 的扫描路径却硬编码只扫 `apex-system` 一个模块，其余 9 个模块零覆盖。结果是 system 全绿、其他模块长期漏检。
- 该脚本的 DTO 目录清单同样硬编码 7 个路径，`apex-integration`、`apex-knowledge`、`apex-knowledge-service`、`apex-example`、`apex-common` 全部不在范围内。
- 更关键：脚本只统计 `@Operation`**数量**，不校验写操作是否声明 `requestBody`。函数式端点下 SpringDoc 无法从 Handler 签名推断请求体，缺 `requestBody` 时在线文档只能看到"有请求体但无字段"。

## 2. 实测基线（改动前）

| 项 | 缺口 |
| --- | --- |
| Router `@RouterOperations` | `OpenFileRouter`、`OpenWorkflowRouter`、`OpenMessageRouter` **零注解**（业务路由共 3 个完全不可见）；`IntegrationConnectorRouter`、`IotDeviceRouter` 零注解，且类注释谎称"本模块 POM 无 springdoc 依赖"（POM 48–53 行实际已有 `optional` 依赖） |
| Handler `@Operation` | `knowledge-service` 4 个 Handler **全部无 `@Tag`**、20 方法仅 3 个 `@Operation`；`InternalOpenAppRegistryHandler` 8 方法 0 注解；`OpenFileHandler`/`OpenWorkflowHandler`/`OpenMessageHandler` 零注解；auth `verifyMfa`、`consent` 缺 `@Operation` |
| DTO `@Schema` | `apex-knowledge` 13/13 全缺；`apex-integration` 19 个 DTO 全缺；共享 `Result<T>`/`PageResponse` 无类级 `@Schema` |
| 写操作 `requestBody` | 60 个真实读取请求体的写端点中仅 51 个声明（85%），9 个缺失；若按全部写路由粗算则覆盖率仅 44% |

## 3. 改动清单

### 3.1 共享基础

- `apex-common` 新增 `swagger-annotations-jakarta`（2.2.27，与 springdoc 2.8.1 内置 swagger-api 对齐，仅注解无运行时框架，compile 作用域以让下游读取注解）。
- 根 `pom.xml` 新增 `swagger-annotations.version` 属性与该构件 dependencyManagement 条目。
- `Result<T>` 补类级 + 4 字段级 `@Schema`；`PageResponse<T>` 补类级 + 6 字段级 `@Schema`（含 `list`/`records` 别名的语义说明）。

### 3.2 Handler / Router（按模块）

| 模块 | 改动 |
| --- | --- |
| knowledge-service | 4 个 Handler 全部补 `@Tag`；20 个方法补完整 `@Operation`（含 `operationId`/`summary`/`description`/`@Parameter`/`requestBody`/`@ApiResponses`）；`ask` 按 SSE 语义用 `text/event-stream`；4 个 Router 的 `@RouterOperation` 补齐路由绑定 |
| file-service | `OpenFileHandler` 补 `@Tag` + 2 方法完整注解；`OpenFileRouter` 补 2 条 `@RouterOperation`；`FileShareHandler` 3 个方法补 `requestBody` |
| workflow-service | `OpenWorkflowHandler` 补 `@Tag` + 4 方法完整注解 + `StartRequest` `@Schema`；`OpenWorkflowRouter` 补 4 条路由绑定 |
| office-service | `OpenMessageHandler` 补 `@Tag` + `send` 完整注解 + `SendRequest` `@Schema`；`OpenMessageRouter` 补路由绑定 |
| auth-service | `InternalOpenAppRegistryHandler` 补 `@Tag` + 8 方法完整注解（注明"仅接受已验签 system 调用"）；`InternalAuthHandler`/`OauthTokenHandler`/`UnifiedAuthorizationHandler`/`OAuthEndpointHandler` 补 `@Tag`；`verifyMfa`、`consent` 补 `@Operation`（OAuth 端点注明 form-urlencoded 与原生协议响应） |
| integration | 删除两个 Router 中"无 springdoc 依赖"的**过时注释**，改写为真实说明；`IntegrationConnectorRouter` 补 11 条、`IotDeviceRouter` 补 16 条 `@RouterOperation`；19 个 DTO 全部补类级+字段级 `@Schema`；`WebhookInboundHandler.accept` 补 `requestBody` |
| system | 7 个 Handler 补 `@Tag`（`InternalOpenApp`/`InternalTenant`/`Capability`/`CapabilityUsage`/`InternalOverview`/`PlatformOverview`/`TenantPackage`）；`AuthHandler` 保持集中式 Customizer 策略不动 |
| masking-service | `MaskingHandler` 补 `@Tag` |
| example | 2 个 Handler 共 13 个方法补 `operationId` + `@Parameter`/`requestBody`/`@ApiResponses`（作为模板须完整）；`CreateUserRequest`/`UserResponse`/`UserExportDTO` 补 `@Schema` |

### 3.3 门禁扩展（防回归）

`scripts/dev/check/check-springdoc-annotations-v2.sh` 重构：

1. 新增 `MODULE_ROOTS` 清单，Router/Handler 扫描从硬编码单模块改为遍历全部 10 个模块。
2. 新增 `EXEMPT_ROUTERS` / `EXEMPT_HANDLERS` **显式豁免清单**（`MethodNotAllowedRouter`、`BaseHandler`、各异常边界、MyBatis 审计填充器、MQ 死信处理器等），不再隐式跳过。
3. DTO 扫描改为按模块自动发现任意层级 `*/dto` 目录，杜绝路径硬编码随重构失效。
4. **新增第 4 项检查：写操作 `@RequestBody` 声明**。按 Router 的 `beanClass`/`beanMethod`/`method=RequestMethod.X` 三元组反查 Handler 方法，仅对 POST/PUT/PATCH **且确实读取请求体**（`bodyToMono`/`handleRequestBody`/`bodyToFlux`/`multipartData`）的方法计数，避免给纯路径参数端点误判。识别 `OpenApiDocumentationCustomizer` 集中式声明，避免对 system 模块误报。

## 4. 验证证据

```
bash scripts/dev/check/check-springdoc-annotations-v2.sh
  Router @RouterOperations: 100%   (77/77)
  Handler @Operation:      100%   (516/516)
  DTO @Schema:             100%   (212/212)
  写操作 @RequestBody:      100%   (60/60)

bash scripts/ci/check-springdoc-coverage.sh   → exit 0
```

既有守护测试（全部通过，未做任何放宽）：

- `:apex-system` `RouterOpenApiCoverageTest` — 12/12
- `:apex-auth-service` `AuthCenterOpenApiIntegrationTest` — 8/8（73 个 operation 逐一比对）
- `:apex-office-service` `OfficeOpenApiContractTest` — 2/2
- `:apex-file-service` `FileHandlerIntegrationTest` — 55/55

模块回归：

- `:apex-knowledge-service` 72/72、`:apex-workflow-service` 86/86
- `:apex-aggregation-service` 26/26、`:apex-masking-service` 通过
- `:apex-system` / `:apex-common` BUILD SUCCESS
- `:apex-office-service` / `:apex-file-service` / `:apex-auth-service` / `:apex-integration` BUILD SUCCESS

**全量门禁**：

```
mvnd -T4 -B clean verify
  BUILD SUCCESS
  反应堆 45/45 项目 SUCCESS（零 FAILURE/SKIPPED）
  Tests 6740 / Failures 0 / Errors 0 / Skipped 33
  Total time 16:28 min
```

治理脚本：

- `bash scripts/governance/verify-docs.sh --strict` — 仅 1 项既有告警（ZCode 工具私有目录下的 harness 会话缓存，非业务文档；计划正文已归档至 [2026-11-22-handler-annotation-completion-plan.md](../plans/2026-11-22-handler-annotation-completion-plan.md)）
- `bash scripts/governance/audit-backend-rules.sh --strict` — 无阻断项
- `bash scripts/ci/check-doc-links.sh` — 11 条断链全部位于 `docs/plans/archived-ai-sessions/gateway-resilience-enhancement-plan.md`（历史只读归档，指向已退役 `apex-interfaces`/`apex-infrastructure`，最后修改于 `19731479`，非本次改动）；本次新增/修改的 8 个文档文件零断链
- `git diff --check` — 0 空白错误
- `spotless:check` — 通过（初次 verify 在 `apex-common` 的 `Result.java`/`PageResponse.java` 报排版违规，已 `spotless:apply` 修正后复跑）

## 5. 影响面

**HTTP 契约零变化**：URL、HTTP 方法、请求/响应字段名、序列化方式、状态码全部未动。

- 纯注解改动：`@Tag`/`@Operation`/`@Parameter`/`requestBody`/`@ApiResponses`/`@Schema` 均为文档元数据。
- 一处 POM 改动：`apex-common` 新增 swagger 注解构件（仅注解，不含框架、不引入自动装配）。根 POM 新增版本属性与 dependencyManagement 条目。
- 一处注释修正：integration 两个 Router 的过时说明。
- JavaDoc 除新增注解自带的说明外未改写措辞。

## 6. 遗留与边界

1. **system 模块采用集中式策略**：`OpenApiDocumentationCustomizer.JSON_REQUEST_TYPES` 为 33 个 operationId 集中声明请求体，Handler 上不重复写 `requestBody`。扫描脚本已识别该模式，不计缺失。这是与其余模块不同的合法架构选择，未强行统一。
2. **`@IdempotentApi` 在 office 无切面**：`OfficeAdminHandler` 5 处、`OfficeMessageHandler` 3 处标注了 `@IdempotentApi`，但 office 模块没有对应切面、也不依赖 `apex-system`，注解实际不生效。本轮**只记录不修改**（补切面属运行行为变更，需独立规格与回归），已登记为独立待办。
3. **auth/file/masking/gateway 的 `@RequirePermission` 无切面**：这些模块走 `IdentityRelayAuthenticationFilter` 链路，未注册权限切面。本轮未新增该注解，避免制造"存在但不生效"的注解。
4. `aggregation-service` 无 `@OperationLog` 切面，未加审计注解。

## 7. 复现命令

```bash
source scripts/build/init-build-env.sh
bash scripts/dev/check/check-springdoc-annotations-v2.sh        # 四档覆盖率
bash scripts/ci/check-springdoc-coverage.sh                     # CI 门禁（100/100/100 阈值）
mvnd -B -pl :apex-system test -Dtest=RouterOpenApiCoverageTest
mvnd -B -pl :apex-auth-service test -Dtest=AuthCenterOpenApiIntegrationTest
mvnd -B -pl :apex-office-service test -Dtest=OfficeOpenApiContractTest
mvnd -B -pl :apex-file-service test -Dtest=FileHandlerIntegrationTest
```

## 8. 回滚

按模块 `git revert` 即可；纯注解改动，无数据迁移、无配置开关、无运行期状态，回滚后在线文档回到改动前水平，服务行为不变。
---

## 2026-11-24 独立 R5 复核（REJECT）与门禁修复

独立 R5 对 `5f14b5a5` / `334c0f04` 的结论为 **REJECT**，一处必改（其余为 nit）：

- **必改 MF-1（已修复）**：新增的第 4 项“写操作 `@RequestBody`”检查只统计与打印，未纳入通过判定（原 `:658` 的条件只比较 router/handler/dto 三项）。复核者在隔离副本删除 `KnowledgeChatHandler.ask` 的唯一 `@RequestBody` 后，输出 98% 但脚本仍 `exit 0`，即该维度可退化至 0% 而 CI 保持绿色。
- **必改 MF-2（已修复）**：`:546-551` 的 `write_routes` 计算后未被使用，且相邻注释自述排序“并不准确”，属会误导读者的死代码。
- **复核确认无问题**：`swagger-annotations-jakarta` 2.2.27 为 compile 作用域的纯注解构件（构件内仅 `io/swagger/v3/oas/annotations/**`，POM 无依赖，不引入 springdoc 运行期框架），版本与 springdoc 2.8.1 的 `swagger-api 2.2.27` 对齐；67 个文件零新增/删除 `.java`，无 `public Mono/Flux` 签名增减、无路径/方法/状态码改动；新增的 `Result<>` 嵌套子类只出现在 `@Schema(implementation=…)`，运行期从不实例化或返回；`334c0f04` 未触碰任何测试文件、断言行零变化。

本轮实际修复：

1. 通过条件补入 `write_body_missing == 0`，使写操作请求体缺失真正阻断（收紧门禁）。
2. 删除未使用的 `write_routes` 及其失准注释。
3. 修正两处 macOS bash 3.2 多字节变量名崩溃点（`$module_root`、`$router_name` 后紧跟中文全角括号会被并入变量名）；原实现一旦出现缺注解路由就以 `unbound variable` 崩溃，丢失诊断信息。
4. 新增 `router_has_meta_router_operations`：识别“自定义组合注解上声明 `@RouterOperations`”的形式。BE-065 已将密码找回三个端点收敛为 `@PasswordRecoveryOperations` 元注解，SpringDoc 运行期可识别，而原脚本只匹配字面 `@RouterOperations(`，会把已合规的 `PasswordRecoveryRouter` 误判为缺失。

修复后实测：`Router 77/77`、`Handler 516/516`、`DTO 212/212`、写操作 `60/60` 四档均 100%，脚本 `exit 0`。

复核未覆盖（原结论声明）：未运行 `mvnd clean verify`，未核验实际渲染的 Swagger UI / OpenAPI JSON 内容。

## 2026-11-24 复核遗留（未关闭）

- DTO 自动发现范围收窄：`MODULE_ROOTS` 未覆盖 `apex-auth`（23）、`apex-workflow`（6）、`apex-file`（20）、`apex-office`（1 + off 8）共 58 个 DTO；这些当前全部 100% 有 `@Schema`，故不产生红灯，但未来回归不会被该脚本发现（原基线曾显式覆盖）。
- 6 个 auth 路由（`AuthenticationRouter` / `MfaRouter` / `RoleRouter` / `PermissionRouter` / `AuthConfigRouter` / `AuditLogRouter`）使用 `handler::method` 形式且无 `beanMethod` 绑定，第 4 项检查会整体跳过；其请求体由路由侧 `requestBody = @RequestBody(...)` 声明，覆盖真实存在但未被该脚本验证。
- `dto_missing < 5` 的容忍阈值沿用原实现，文档口径的“100%”实际允许 4 个 DTO 缺口。

任务卡保持 **待复核**：上述遗留与任务专属集成提交尚未收敛，不以本轮门禁修复替代关闭。
