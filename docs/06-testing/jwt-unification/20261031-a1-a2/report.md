# 批次 A1+A2 测试报告：统一认证收口（双算法验签统一 + JWKS 闭环）

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

- 批次：方案 [unified-auth-tenant-platform-remediation-plan.md](../../../08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md) 批次 A 的 A1+A2
- 执行日期：2026-10-31
- 环境：macOS（darwin 24.6.0 x64）、JDK `21.0.13.0+9-tem`（SDKMAN 固定）、mvnd `2.0.0-rc-3`（均经 `scripts/build/init-build-env.sh` 校验）
- 状态：**实施完成，门禁结果见 §4**；未经用户验收不进入批次 A3

## 1. 已交付变更

### A1 双算法验签统一

| 模块 | 变更 |
| --- | --- |
| apex-common | 新增 `MultiKeyJwtVerifier`（按 header `kid`/`alg` 在 RS256 公钥集与 HMAC 过渡密钥间选钥，算法白名单防混淆）；新增 `JwksFetcher`（共享 JWKS 拉取客户端：启动即拉 + 周期刷新 + 失败保留快照）与 `JwksFetchProperties`（`apex.jwks.*`，对齐 Nacos 预留约定）；`JwksAutoConfiguration` 注册进自动装配清单 |
| apex-system | 新增 `TokenVerifier` 契约、`JwtVerifierSupplier`（静态公钥兜底 ∪ JWKS 拉取快照 ∪ HS256 过渡）、`CenterJwtKeyProperties`（`apex.system.jwt-center.*`）、`JwtVerifierConfiguration`；`JwtAuthenticationFilter` 改注入验签器（L244 原静态 HS256 调用移除），认证中心 RS256 令牌分支由"不可达"变为可达；该分支本就不做滑动续期（既有注释口径保持） |
| apex-workflow-service / apex-office-service | `IdentityRelayAuthenticationFilter` 对齐 file-service 多 kid 模型：新增 `jwt-public-keys-base64` 多键配置（`kid=Base64,...`），单键"忽略 kid"语义保留，JWKS 拉取快照同 kid 覆盖静态条目 |
| apex-file-service | 既有多 kid 模型上增量接入 JWKS 拉取合并（其余语义不动） |
| apex-gateway | 无代码语义变更（验签服务本已支持 RSA+kid 双算法；构造器补 `@Autowired` 消除双构造歧义） |

### A2 JWKS 闭环

| 模块 | 变更 |
| --- | --- |
| apex-auth | `OAuth2Service.jwt()` 签发切换到 `JwtKeyMaterialService.sign()`（Redis 轮换 RSA，`kid` 头自动写入；`iss`/`aud`/`jti` 经 claims 携带），`tokens()` 链路响应式化；introspect/revoke 不变（DB 哈希查表） |
| apex-auth-service | `OAuth2MetadataService.jwks()` 改为聚合输出：轮换密钥（活跃+宽限，`availablePublicKeys()` 投影）+ 配置注入静态密钥（`auth-center.oauth2.legacy-key-in-jwks` 过渡开关，默认 true）；`OauthMetadataRouter` jwks handler 改响应式 |
| apex-gateway | `JwksProperties` 新增 `remote-url`/`remote-refresh-interval-seconds`；`GatewayJwtVerifierService` 周期拉取认证中心聚合 JWKS 并合并进密钥缓存（同 kid 覆盖静态条目，`hmac-legacy` 不受影响，失败保留快照），JWKS 解析复用 apex-common 静态方法 |

### 配置与文档

- `application.yml`：system（`apex.system.jwt-center.*` + `apex.jwks.*`）、workflow/office（多公钥占位 + `apex.jwks.*`）、file（`apex.jwks.*` + 注释更新）、gateway（`apex.gateway.jwks.remote-url`）；`config/nacos/apex-gateway.yaml` 同步
- `.env.example`：新增 `JWKS_REMOTE_URL`、`JWKS_REFRESH_INTERVAL_SECONDS`、`WORKFLOW_JWT_PUBLIC_KEYS_BASE64`、`OFFICE_JWT_PUBLIC_KEYS_BASE64`
- `docs/05-operations/configuration.md`：新增"共享 JWKS 拉取"节 + workflow/file 段更新
- 方案文档升版 v3.1：记录执行授权与 D01–D14 推荐值采纳口径

## 2. 新增/变更测试

| 测试 | 数量 | 覆盖 |
| --- | --- | --- |
| apex-common `MultiKeyJwtVerifierTest`（新增） | 5 | RS256 按 kid 选钥（活跃+宽限）、未知/缺失 kid 拒绝、HMAC 开关双态、算法混淆（RS512 冒充/非 JWT）拒绝、过期与签名错误传播 |
| apex-common `JwksFetcherTest`（新增） | 4 | JWK 解析（非 RSA/不完整条目跳过）、非法响应、禁用态、成功/空结果/失败/恢复四轮快照语义 |
| apex-system `JwtVerifierSupplierTest`（新增） | 4 | 静态公钥解码与非法配置快速失败、双令牌家族（RS256+HS256）解析、拉取快照同 kid 覆盖（轮换新钥即时生效）、禁用拉取回退静态钥 |
| apex-system `JwtAuthenticationFilterTest`（迁移+新增） | 21 | 既有用例由 `MockedStatic<JwtUtil>` 迁移为注入式 `TokenVerifier` 替身；新增真实 RSA-2048 RS256 认证中心令牌经真实验签器走通主应用认证分支（含统一授权装配断言） |
| apex-workflow-service 过滤器测试（新增用例） | 16 | 多 kid 按 header 选钥、未知 kid 拒绝、拉取快照同 kid 覆盖 |
| apex-office-service 过滤器测试（新增用例） | 5 | 同上（office 版） |
| apex-gateway `GatewayJwtVerifierServiceTest$RemoteJwksTests`（新增） | 3 | 远程公钥合并与验签、拉取失败保留静态密钥、同 kid 覆盖（轮换即时生效） |
| apex-auth-service `OAuth2BootTest`（迁移+新增） | 12 | 本地验签由 `verificationKey()` 改为"从聚合 JWKS 端点取钥按 kid 验签"；新增 `issuedTokenVerifiesAgainstAggregatedJwks`（签发令牌 kid ∈ 聚合 JWKS 且可用该钥验签 + iss/token_use 断言） |

## 3. 实际执行记录

全部命令在 `source scripts/build/init-build-env.sh` 通过（JDK 21.0.13 + mvnd 2.0.0-rc-3 校验）后执行：

1. `mvnd -T4 -B -pl apex-common,apex-system,apex-gateway,apex-auth-service,apex-workflow-service,apex-office-service,apex-file-service -am test` —— 初次运行暴露 3 个实施缺陷（网关双构造器歧义、office 兼容构造器 NPE、测试轮次设计错误），逐一修复后全绿（见 §4）。
2. `mvnd -T4 -B spotless:check` → 违规后对受影响 7 模块 `spotless:apply` → 复查通过。
3. `git diff --check` —— 空白错误 0。
4. `bash scripts/ci/check-doc-links.sh` —— 15 个断链全部来自 `.workbuddy/knowledge/README.md`（并行会话本地工具目录，非本批引入）；本批新增/修改文档链接 0 断裂。
5. `mvnd -T4 -B clean verify` —— 结果见 §4。

## 4. 结果

- 受影响 7 模块 `test`：**BUILD SUCCESS**（apex-common 561、system、gateway 116、auth-service（OAuth2BootTest 12/12）、workflow-service、office-service、file-service 全部 0 Failures / 0 Errors；过程缺陷修复记录见 §5）。
- spotless:check：**通过**（apply 引入的 file-service 测试格式化为 HEAD 既有违规的顺带修正，纯格式变更）。
- `git diff --check`：**干净**。
- check-doc-links.sh：本批 **0 断裂**（全仓 15 断链均为并行会话 `.workbuddy` 目录，不入本批口径）。
- `clean verify`：见下方补记。

### clean verify 结果补记

执行两轮全量 `mvnd -T4 -B clean verify`：

**第一轮**暴露两处与 A1+A2 代码无关的门禁问题（已处理）：
- `apex-auth` spotless-check 违规——实施时 `spotless:apply` 遗漏本模块，补 apply 后通过；
- `apex-workflow-service` `WorkflowDualDatabaseIntegrationTest` PostgreSQL 分支 1 error（Testcontainers 容器启动期 SSL 连接错误，410s）——定向重跑（`-Dit.test=WorkflowDualDatabaseIntegrationTest -Djacoco.skip=true -T1`）**8/8 通过（106.6s）**，确认为 `-T4` 并行负载下的容器启动竞态，非代码问题。

**第二轮（最终门禁）**：反应堆 32 模块中 31 个 SUCCESS，含全部受影响 7 模块（apex-common / apex-system 10:52 / apex-gateway 2:28 / apex-auth-service 7:29 / apex-workflow-service 8:25 / apex-office-service / apex-file-service）与 workflow 双库迁移 IT；唯一失败为 `apex-example` `ExampleDataSourceTimeoutTest`（模拟无响应数据库的计时敏感网络测试，断言 12 秒内握手失败）——该模块不引用任何本批改动类，`-T1` 单模块重跑 **通过（12.75s）**，按仓库"计时敏感测试负载超时应重跑、不放宽 SLA"口径归类为并行负载瞬时失败。`Failures: 0 / Errors: 0` 口径在全部受影响模块与重跑验证上成立；全反应堆一次性全绿因上述环境类瞬时项未在同一轮达成，如实记录。

## 5. 过程缺陷与修复记录

| 缺陷 | 根因 | 修复 |
| --- | --- | --- |
| 网关应用上下文启动失败（GatewayApplicationTest 3 errors：`No default constructor found`） | `GatewayJwtVerifierService` 增加第二构造器后未标注 `@Autowired`，Spring 无法选择 | 生产构造器补 `@Autowired` |
| office 过滤器 7 个测试 NPE | 4 参兼容构造器向 6 参构造器传 `null` provider 后直接解引用 | `jwksFetcher == null ? null : getIfAvailable()` 空值保护 |
| `JwksFetcherTest.refreshSemantics` 断言失败 | 测试轮次设计错误（第三轮命中 error 分支） | 修正为 成功→空→失败→恢复 四轮 |
| `mvnd -pl <module> test`（无 `-am`）编译失败 | 本地仓库 apex-common 构件未 install，缺新类 | 统一带 `-am` 执行 |

## 6. 未验证项与边界

- **运行时联调未执行**：本报告全部为构建期验证（单元 + Boot 集成测试）；未启动真实网关/五服务/认证中心做端到端 RS256 令牌贯通、密钥轮换（30 天周期）与 JWKS 拉取的真实时序验证，也未部署 EMQX/broker 等外部设施。建议验收前以 Compose 环境冒烟：认证中心登录 → 网关验签 → system/file/workflow 消费。
- **存量环境**：项目从未部署（大方案 2026-10-20 口径），无双轨切换的存量数据风险；若存在已初始化环境，RS256/HS256 双读窗口与 `legacy-hmac-enabled` 开关的回退路径未做演练。
- **并行会话干扰**：执行期间工作区存在并行会话改动（`.gitignore`、`README.md`、weekly-reports 归档、`.workbuddy/`），本批未触碰；`clean verify` 在混合工作区上执行，理论上并行改动可能影响无关模块结果，已通过模块级定位区分。
- **前端**：A1/A2 无前端功能变更（方案 §12.2 口径），仅回归项，本批未触碰前端仓库。
- **遗留开关**：`auth-center.oauth2.legacy-key-in-jwks` 与主应用 HS256 过渡通道为过渡期设计，收口完成后按方案 §5/A2 弃用周期移除。

## 7. 变更清单

25 个既有文件修改 + 11 个新增文件（Java 主源 7、Java 测试 4），另含方案/配置/文档更新；未提交 Git，待用户验收后按 Conventional Commits 分组提交（建议：`feat(auth): 统一令牌双算法验签与 JWKS 闭环（批次 A1+A2）`）。
