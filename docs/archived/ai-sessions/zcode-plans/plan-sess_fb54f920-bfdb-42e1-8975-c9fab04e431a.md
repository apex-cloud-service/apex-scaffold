# 多端对接调整方案（PC / 小程序 / 公众号 / App · 统一账户体系）

## 一、结论

当前项目**部分支持**多端对接。App 链路成熟（微信开放平台 + 创蓝一键登录 + 短信 + 密码 + 图形验证码），PC 可密码登录；但存在 6 个结构性缺口，需分 6 批改造（每批验证后停下等你确认再继续）：

| # | 缺口 | 证据 |
|---|------|------|
| 1 | 小程序登录缺失（无 jscode2session） | application-prod.yml:633 注释明确"非小程序 jscode2session 接口" |
| 2 | 公众号网页授权未接入登录 | WeChatOAuthService.java 无任何调用方 |
| 3 | 微信身份单列 open_id + 唯一索引，同用户多端 openid 无法共存 | V1__init_schema.sql:610-633（sys_user） |
| 4 | 令牌每用户单槽，多端同时在线互顶 | TokenServiceImpl.java:119-127（token:access:{subject}） |
| 5 | 令牌无终端标识、账户无来源字段 | TokenServiceImpl 仅 claims subject+deviceId；sys_user 无 register_channel |
| 6 | CORS 请求头白名单缺 X-Tenant-Id / Idempotency-Key / X-Client-Type | CorsConfig.java:117-119 |

**已具备**：统一 Result<T> 契约 + traceId、i18n 四语言、六种登录方式（WX_CODE/WX_PHONE/WX_SMS/CL_PHONE/CL_SMS/PASSWORD）、双令牌+设备指纹+滑动续期（X-New-Access-Token）、黑名单、IP 限流+Sentinel、防重放、租户上下文、认证中心 OAuth2 Token Endpoint（authorization_code/password/client_credentials/refresh_token）与按租户动态客户端注册（auth_config 表 OAUTH2_CLIENT 类型）、模板消息/订阅消息/企业微信基建。

## 二、目标与已确认决策

1. 四端共用一套后端、**同一账户体系**，且**可区分账户来源**（注册渠道 + 每端绑定身份 + 登录终端）。
2. 会话策略：**全局多会话无限制**（各端各自保留会话，互不顶号）。
3. PC 管理端**走认证中心独立登录**（获得 MFA/TOTP、设备信任、暴力破解防护、JWT 密钥轮换）。
4. 微信主体不确定 → 归一算法采用**手机号为主、unionid 辅助、绑定表兜底**的兼容设计（不依赖主体假设）。
5. 前端不在本仓库（管理后台为独立项目），本方案交付后端改造 + 四端前端对接契约。
6. **分批推进**：每批完成 → mvnd 验证（以退出码为准）→ 按主题分组提交 → 停下等确认 → 下一批。
7. 批准后第一批先落方案文档 `docs/02-architecture/multi-client-integration-plan.md`，再实施。

## 三、目标架构

```
C 端（App / 小程序 / 公众号H5 / PC官网）
    → 主应用 POST /api/v1/auth/login（六种方式 + 新增 WX_MINI / WX_OAUTH）
    → HS256 JWT（主应用密钥）→ 统一账户 sys_user

PC 管理端（独立前端仓库）
    → 认证中心 POST /auth/v1/login（密码 + 验证码 + MFA/TOTP + 设备信任）
    → RS256 JWT（auth-center JWKS，claims 含 account_id）
    → 主应用 JwtAuthenticationFilter 增加 JWKS 验签分支
    → claims.account_id 映射 sys_user → 同一账户体系
```

**账户模型（统一账户 + 区分来源）**：
- `sys_user`：唯一账户主数据，新增 `register_channel`（注册来源：APP_ANDROID/APP_IOS/MINI_PROGRAM/WECHAT_MP/PC_WEB/PC_ADMIN/SYSTEM）。
- 新增 `sys_user_social`：多端微信身份绑定表（user_id + channel + open_id + union_id），同一用户在 App/小程序/公众号的三个不同 openid 各占一行。
- `auth_user`（认证中心，仅凭证/MFA/风控）：新增 `account_id` 关联 `sys_user.id`，登录签发的 JWT 携带 account_id；权限仍走主应用 RBAC（@RequirePermission/@DataScope 链路不变），认证中心不复制权限。
- 来源三维度：`register_channel`（注册来源）+ `sys_user_social.channel`（绑定渠道）+ 登录日志/操作日志 `client_type`（请求终端）。

## 四、详细设计

### 4.1 数据模型（Flyway，主库从 V59 起、认证中心从 V53 起，MySQL/PostgreSQL 双份、幂等）

**主库 V59__multi_client_account.sql**：
- `sys_user` 增加 `register_channel VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN'` + 索引（DATETIME 规范沿用现有约定，禁 TIMESTAMP/硬编码未来日期）。
- 新建 `sys_user_social`：id、tenant_id、user_id、channel（WECHAT_APP/WECHAT_MINI/WECHAT_MP）、open_id、union_id、status、bound_at、create_time、update_time、deleted；唯一键 `uk_channel_openid(channel, open_id)`、索引 `idx_user_id`、`idx_union_id`。存量 sys_user.open_id 迁移为 channel=WECHAT_APP 绑定行（幂等 INSERT SELECT）。
- `sys_login_log` 增加 `client_type`、`platform`（以实际表结构为准，先读 V1 定义再写脚本）。

**认证中心 V53__auth_user_account_binding.sql**：`auth_user` 增加 `account_id BIGINT NULL` + 索引；存量数据迁移脚本按 username/phone 匹配 sys_user 补齐，匹配不到的创建对应 sys_user（register_channel=PC_ADMIN）。

### 4.2 客户端标识

- 新增枚举 `ClientType`（apex-common）：PC_ADMIN、PC_WEB、WECHAT_MP、MINI_PROGRAM、APP_ANDROID、APP_IOS、H5、UNKNOWN。
- 请求头 `X-Client-Type`（优先）+ `LoginRequest.clientType`（兜底），缺省 UNKNOWN 完全兼容存量客户端。
- JWT claims 增加 `sid`（会话 UUID）、`clientType`；`TokenStoreEntry` 增加 clientType、platform、loginType、deviceId、sid。

### 4.3 多会话令牌模型（改 TokenServiceImpl，主应用）

- Redis 键：`token:access:{tenant}:{subject}:{sid}` 与 `token:refresh:{tenant}:{subject}:{sid}`；会话索引 `token:sessions:{tenant}:{subject}`（Set）。
- 登录=生成新 sid 新槽，不覆盖旧会话（全局多会话无限制）；刷新轮换与并发锁按 sid 隔离（沿用 Redisson 锁模式）；黑名单按 jti 不变。
- 登出默认仅本会话（`?scope=all` 全端登出，预留）；滑动续期 `X-New-Access-Token` 机制不变。
- 兼容：存量无 sid 的 token 视为 `sid=legacy` 单槽，自然过期淘汰。
- 在线会话列表查询对接现有 `OnlineUserHandler`。

### 4.4 登录方式扩展与账户归一算法

统一归一四步（LoginStrategy 公共编排）：
1. 按 `(channel, openid)` 查 `sys_user_social` → 命中则登录该账户；
2. 未命中按 `union_id` 查绑定 → 命中则登录并为当前 channel 新增绑定行（同主体自动归一）；
3. 未命中且有手机号（WX_MINI phoneCode / 短信验证码）按手机号查 `sys_user` → 命中则登录+绑定；
4. 均未命中 → 返回 `nextAction=REGISTER` 一次性票据（复用现有 AuthTicket/AuthNextAction 机制），注册成功写 `register_channel`。

- **WX_MINI（小程序）**：新增 `MiniProgramService`（infrastructure/wechat）实现 `/sns/jscode2session`；`getuserphonenumber` 改用小程序 appid 的全局 access_token；`LoginType=WX_MINI`（code 必填、phoneCode 可选）。新增 LoginStrategy 实现类，沿用策略模式。
- **WX_OAUTH（公众号 H5）**：复用 `WeChatOAuthService`（snsapi_userinfo 网页授权），新增 `GET /api/v1/auth/wechat/mp/authorize`（302 跳转）+ 登录策略 `WX_OAUTH`（code → openid/unionid）。
- **微信配置拆分**：`wechat.app`（开放平台，保持兼容）/ `wechat.miniapp`（新增 app-id/app-secret）/ `wechat.mp`（新增 app-id/app-secret）；各自独立全局 access_token 缓存键；未配置的渠道对应 LoginType 返回明确错误码（新增 ResultCode + i18n 四语言），不影响其他渠道。

### 4.5 PC 管理端信任链（认证中心）

- 认证中心登录响应与 JWT claims 增加 `account_id`、`clientType=PC_ADMIN`、`sid`（SessionPO 已天然支持多会话）。
- 主应用 `JwtAuthenticationFilter` 增加第二验签分支：token header kid/issuer 匹配认证中心 → RS256 验签（JWKS：配置注入公钥为主、HTTP 拉取 `/oauth2/jwks` 缓存为辅）→ `account_id` 装配 `AuthenticatedUser`（与 C 端同一身份模型）。
- 权限、数据权限、审计、租户上下文全部沿用主应用现有链路。
- 认证中心侧保持：暴力破解防护、IP 访问控制、TOTP/MFA、设备信任、密钥轮换（均已存在，无需重写）。

### 4.6 横切配套

- CORS allowedHeaders 增加：`X-Client-Type`、`X-Tenant-Id`、`Idempotency-Key`、`X-Device-Id`（CorsConfig.java）。
- `SystemConstants.PUBLIC_PATHS` 最小化增量：`/api/v1/auth/wechat/mp/authorize`（登录前跳转）；WX_MINI/WX_OAUTH 登录走现有 `/api/v1/auth/login` 无需新增。
- 限流：登录端点保留 IP 限流 + Sentinel；新增按 phone 维度参数限流规则，防止单手机号轰炸（短信端点同样处理）。
- 审计：登录日志写 `client_type`/`platform`；`@OperationLog` 机制不变。
- 消息触达：SSE（NotificationSseHandler）仅 PC/H5 可用；小程序用订阅消息、公众号用模板消息（基建已有，方案仅做接入文档说明）。
- 文件：复用 MinIO/TOS 预签名与代理路径；小程序 uploadFile 与 `MultipartRequestParser` 兼容性列入批次 6 实测项。

## 五、分批实施计划（每批验证 + 提交后停下等确认）

**批次 1：账户来源与多端身份基础**
- 迁移脚本 V59/V53（MySQL+PG）、`sys_user_social` 的 PO/Mapper/Converter/Repository（infrastructure）、domain 契约、现有六种登录策略接入"写绑定行 + register_channel"、登录日志字段。
- 验证：`mvnd -pl apex-domain,apex-infrastructure,apex-application,apex-interfaces -am test` + 迁移脚本校验（scripts/test/check-migration-integrity.sh）。

**批次 2：客户端标识与多会话令牌**
- ClientType 枚举、LoginRequest/头解析、TokenServiceImpl 多槽改造、JwtAuthenticationFilter 适配、登出 scope、CORS 头、ResultCode/i18n。
- 验证：受影响模块 test（重点：多会话并发、刷新轮换按 sid、legacy 兼容）+ spotless:check + pmd:check。

**批次 3：微信多应用配置 + 小程序登录**
- wechat.miniapp 配置、MiniProgramService（jscode2session）、WX_MINI 策略、归一四步编排、绑定/注册流程适配、PUBLIC_PATHS 增量、i18n。
- 验证：单测（mock 微信 API）+ 集成测试 + ArchUnit。

**批次 4：公众号网页授权登录**
- mp 配置、authorize 跳转端点、WX_OAUTH 策略、JS-SDK 签名端点（可选，按需）。
- 验证：同批次 3。

**批次 5：PC 管理端接入认证中心**
- auth_user.account_id、claims 扩展、主应用 JWKS 验签分支、存量 admin 账户映射迁移、在线会话管理对接。
- 验证：认证中心与主应用双侧 test + 手工联调说明（两应用独立启动）。

**批次 6：配套、文档与验收**
- 限流参数规则、文档更新（docs/02-architecture/ 架构图 + docs/05-operations/configuration.md 配置 + API_ROUTES.md）、四端联调清单、全量 `mvnd clean test`、迁移双库脚本实测。

每批按 Conventional Commits 分主题提交（如 `feat(auth): multi-session token model`、`feat(wechat): mini-program login`），不触碰无关 WIP。

## 六、四端前端对接契约（输出到方案文档，供独立前端仓库实施）

| 端 | 登录方式 | 关键请求头 | 令牌续期 | 推送/其他 |
|----|---------|-----------|---------|----------|
| PC 管理端 | 认证中心 /auth/v1/login（密码+验证码+MFA） | X-Client-Type: PC_ADMIN、X-Tenant-Id | 认证中心 refresh | SSE、菜单按权限 |
| App（现有链路不变+新增头） | WX_CODE/WX_PHONE/CL_PHONE/CL_SMS | X-Client-Type: APP_ANDROID/IOS、X-Device-Id | /api/v1/auth/refresh（deviceId 一致性） | 厂商推送（后续） |
| 小程序 | wx.login→WX_MINI（code，可选 phoneCode）；nextAction 分支处理 | X-Client-Type: MINI_PROGRAM | 同上 | 订阅消息；request 域名白名单 |
| 公众号 H5 | 网页授权→WX_OAUTH（code）或短信验证码 | X-Client-Type: WECHAT_MP | 同上 | 模板消息；JS 安全域名 |

通用契约：Result<T> 解包、traceId 透传（X-Trace-Id）、X-New-Access-Token 无感续期、错误码 i18n、`Idempotency-Key` 用于注册幂等。

## 七、兼容与回滚

- 存量客户端不传 clientType/sid → UNKNOWN + legacy 槽，行为不变；未配置 miniapp/mp 时对应登录方式返回明确错误码，不影响现有渠道。
- 迁移脚本幂等、版本号只增不改；每批独立提交可单独回滚。
- `keycloak.enabled=false` 时 SocialLoginHandler 不装配（现状保持）；认证中心不可用时 PC 管理端降级方案：主应用 PASSWORD 登录保留作为后备（不删除）。

## 八、风险与未决项

1. 微信主体归属需运营确认——影响"unionid 自动归一"是否生效，手机号归一兜底已覆盖两种情况。
2. auth_user ↔ sys_user 存量映射依赖 username/phone 匹配率，迁移脚本输出未匹配清单人工处理。
3. 两应用会话域独立，"跨域全端登出"本方案只做单域实现，跨域联动列为后续增强。
4. 小程序/公众号的服务器域名、JS 安全域名、AppSecret 环境变量注入需运维配合（严禁入库）。
5. 全局多会话无限制存在账号共享滥用风险，已按你的决策执行；会话索引设计预留后续加"按端限量"策略的扩展点。

## 验收标准

- 每批：mvnd 编译 + 受影响模块测试退出码为 0、`mvnd spotless:check`、`mvnd pmd:check` 通过；涉迁移批次执行 check-migration-integrity.sh 与双库迁移脚本。
- 最终：全量 `mvnd clean test`（含 ArchUnit 架构测试）通过；四端登录矩阵集成测试覆盖（PC 密码、App 微信、小程序 code、公众号授权码 + 归一四步 + 多会话互不顶号）。
- 交付物：docs/02-architecture/multi-client-integration-plan.md（含本方案与前端对接契约细化）。