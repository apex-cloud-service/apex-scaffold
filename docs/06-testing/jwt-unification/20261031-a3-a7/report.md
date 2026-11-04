# 批次 A3–A7 测试报告：撤销传播、会话/MFA 接线、OAuth2 收口与统一授权默认化

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

- 批次：方案 [unified-auth-tenant-platform-remediation-plan.md](../../../08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md) 批次 A 的 A3/A4/A5/A7（A6 依赖 B1，随 B 批次实施）
- 执行日期：2026-10-31
- 环境：macOS（darwin 24.6.0 x64）、JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`（`scripts/build/init-build-env.sh` 校验通过）
- 状态：**实施完成，门禁结果见 §4**；下一批 B1（租户编码统一）待用户验收后启动

## 1. 已交付变更

### A3 撤销传播

| 端 | 变更 |
| --- | --- |
| 现状核实（评审结论修正） | 发布侧 outbox 链路（`AuthCenterEventConfig` @Primary `DomainEventPublisherImpl` + `DomainEventOutboxDispatcher`）与 system 登出黑名单写点（`AuthService` L824 `add(token, TTL)`）**本已完整**；v1 评审"空实现/未实现"定性修正为"basic 档刻意降级 + 消费端缺失" |
| apex-auth | `TokenRepository` 新增平台级受控入口 `findRevocationsSince(since, limit)`（撤销流专用，javadoc 声明受控口径）；`TokenRevocation` 领域记录；`InternalAuthService.revocationsSince` |
| apex-auth-service | 新增内部端点 `GET /internal/v1/tokens/revocations?since=&limit=`（增量、游标幂等、单批上限收敛 [1,1000]），`InternalAuthHandler` + `@RouterOperation` |
| apex-gateway | 新增 `RevocationProperties`（`apex.gateway.revocation.*`）、`RevocationCacheService`（启动回看 7200s 补拉 + 周期拉取 + 失败保留游标/缓存 + 无 Caffeine 依赖的过期 Map）、`TokenRevocationFilter`（order 11，验签后命中撤销集即 401）；网关/Nacos/`.env.example` 配置 |

### A4 会话与 MFA 接线

| 变更 | 说明 |
| --- | --- |
| `TokenPair.accessTokenId` | `TokenServiceImpl.issueToken` 保存令牌后回填访问令牌持久化 ID |
| `AuthenticationApplicationService.login` | 签发后经 `SessionService.createSession` 登记会话；`logout` 追加 `terminateUserSessions`（SessionRouter 端点从空壳变为可用） |
| MFA 二步登录 | `TotpService` 启用判定接入登录链：启用 MFA 的用户登录返回 `mfaRequired=true` + 短 TTL（5 分钟）RS256 挑战凭据（`purpose=mfa_challenge`），不签发正式令牌；新增 `POST /auth/v1/mfa/verify`（`MfaVerifyRequest` 增量扩展 `challengeToken` 字段）以挑战凭据+验证码交换正式令牌；未启用 MFA 用户零影响 |
| 可信租户桥接 | 登录端点无租户上下文（permitAll），MFA 判定与会话/令牌链以已验证的 `(username, tenantId)` 匹配结果 `contextWrite(TenantContext.withTenant(...))` 桥接（仓库既有范式） |

### A5 OAuth2 收口

| 变更 | 说明 |
| --- | --- |
| consent 移植 | V60 迁移（MySQL/PostgreSQL 成对 + H2 `basic-schema.sql`）：`auth_oauth2_consent`（(tenant_id, principal, client_id) 唯一）；`OAuth2ConsentRepository`（hasConsent/save 幂等/revoke）；`OAuth2Service.authorize` 发码前同意门控（无同意返回 `consent_required`）；新增 `POST /oauth2/consent`（approve/deny） |
| system 端点弃用 | `OAuth2RouterConfig` 两个路由 Bean 挂 `apex.features.system-oauth2-endpoints`（默认 true，两个发布周期后关闭）；`/oauth2/jwks` 空壳端点（硬编码无密钥材料）**直接移除**，`OpenApiDocumentationCustomizer` 的 jwks 分支与对应测试同步删除 |
| 客户端迁移工具 | **未实施**（列为后续项）：存量客户端按迁移口径需 reset-secret 流程；当前无存量部署，风险可控 |

### A7 统一授权默认化

- `apex-system/application.yml`：`apex.features.auth-center-permission` 默认翻转为 `true`（环境变量可覆盖）；代码级回退默认保持 `false` 以保留单元测试语义，生产生效路径经 yml。

## 2. 新增/变更测试

| 测试 | 数量 | 覆盖 |
| --- | --- | --- |
| auth-service `TokenRevocationFeedTest`（新增） | 2 | 撤销增量按游标返回 + 游标推进 + 空批幂等；缺失 since 拒绝 |
| gateway `RevocationCacheServiceTest`（新增） | 3 | Result 封装解析、游标单调、空批/非法响应容错、禁用态 |
| gateway `TokenRevocationFilterTest`（新增） | 3 | 撤销命中 401 且不进链、未撤销放行、未启用/无 Claims 放行 |
| apex-auth `AuthenticationMfaFlowTest`（新增） | 5 | MFA 启用登录返挑战不签发、未启用照常签发并登记会话、挑战+验证码换正式令牌、验证码错误拒绝、登出终止会话（真实 RSA-2048） |
| apex-auth `AuthenticationApplicationServiceTest`（同步） | 11 | @InjectMocks 补 3 依赖 + lenient 桩，既有语义保持 |
| apex-auth `PersistenceContractTest`（扩展） | 13 | 契约豁免平台级 `findRevocationsSince` + 新增平台语义单测（无租户上下文可执行） |
| auth-service `OAuth2BootTest`（同步） | 12 | 授权码用例改端到端 consent 流（authorize→consent_required→approve→302 发码→交换→重放拒绝）；JWKS 验签用例保持 |
| apex-system `OpenApiDocumentationCustomizerTest`（同步） | — | jwks 断言移除，discovery 原生 JSON 保留 |

## 3. 实际执行记录与过程缺陷

四轮迭代修复（全部为实施缺陷，逐项记录）：

1. **consent INSERT 参数错位**（10 占位符 8 参数）→ 补 version/deleted 值。
2. **登录链引入 MFA 判定后 permitAll 场景缺租户上下文**（`JdbcTenantScope` fail-closed → 登录 500）→ 可信租户 `contextWrite` 桥接（正确修法，非降级）。
3. **H2 basic-schema 与 MySQL 漂移**：`auth_session.ip_address` vs PO `client_ip`（会话登记新链路暴露）→ 对齐 MySQL 命名；`OAuth2BootTest` 缺 `auth_totp` 建表 → 按 `AuthCenterBootTest` 范式补 CREATE TABLE IF NOT EXISTS。
4. **旧测试未同步新构造器/MFA 桩** → `AuthenticationApplicationServiceTest` 补 Mock；`PersistenceContractTest` 平台方法显式豁免 + 专属语义测试。

另有重复踩坑记录：`mvnd -pl <module>` 无 `-am` 使用 `~/.m2` 陈旧构件导致 `NoSuchMethodError`/旧行为假象（本批内出现 2 次，已再次确认必须带 `-am`）。

## 4. 结果

- 受影响 4 模块（apex-auth / apex-auth-service / apex-system / apex-gateway）及全部上游 `test`：**BUILD SUCCESS**（apex-auth 1298+ 用例含新增 26 项全绿；OAuth2BootTest 12/12；AuthenticationMfaFlowTest 5/5；TokenRevocationFeedTest 2/2；gateway 撤销测试 6/6）。
- `spotless:apply`（5 模块）→ 全仓 `spotless:check`：**通过**。
- `git diff --check`：**干净**。
- `check-doc-links.sh`：本批 **0 断裂**（全仓非 workbuddy 断链 0）。
- 全量 `clean verify` 本批未重跑：A1+A2 轮已完成全量验证，本批增量集中于四个模块且模块级 `test` 全绿；全量 verify 纳入下一批门禁。

## 5. 未验证项与边界

- **运行时联调未执行**：撤销流端到端时延（登出→网关 401 ≤ 拉取间隔）需 Compose 环境冒烟验证；本批验证止于模块测试与 Boot 集成测试。
- **MFA/consent 前端未就绪**（方案 §12）：MFA 二步登录与 consent 页为前端批次 A4/A5 项；后端已可用但前端联调未做，`apex.auth.mfa-required` 未强制（MFA 仅对已绑定用户生效，不阻塞存量登录）。
- **PermissionChangedEvent 未新增**：权限变更传播当前依赖统一授权 ≤30s 快照缓存（A7 默认开启后为默认路径），事件化传播列为后续优化；方案 A3 的网关撤销感知已闭环。
- **客户端迁移工具未实施**（方案 A5 项）：无存量部署，列为后续。
- **并行会话**：执行期间 apex-auth 出现并行会话瞬态编译中间态（`AdminRoleAspect`/`ResourcePermissionService`，自行恢复），本批未触碰其文件。
