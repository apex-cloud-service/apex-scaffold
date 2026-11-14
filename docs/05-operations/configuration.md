# 配置项详解

[查看配置层级图](../diagrams/46_configuration_hierarchy.svg)

本文档详细说明了 Apex Scaffold 的所有可配置项,帮助你根据实际需求定制系统行为。

## 📁 配置文件位置

当前配置入口为各运行模块的 `src/main/resources/application*.yml`；system 还加载 `apex-system/src/main/resources/` 中的配置。原 apex-interfaces 已退出 reactor。

服务增强配置详见 [XXL-JOB 接入](xxl-job-integration.md)、[数据脱敏](data-masking.md)、[Seata 接入](seata-integration.md)。任务代理默认 8095、脱敏默认 8096；中心通过 Compose `xxl-job` / `seata` profile 单独选择，业务执行器和 AT 仍须独立配置。脱敏使用 `masking` profile，启动前填写 JWT issuer、JWKS、audience 和允许租户。共享基础设施已拆分为 persistence、cache、messaging、remote、integration 五个能力 JAR，配置前缀和 Java 包名保持兼容；office 站内信的安全与路由变量见[站内信说明](in-app-messaging.md)。

环境特定配置:
- `application-dev.yml` - 开发环境
- `application-it.yml` - 集成测试环境（仅 `apex-system/src/test/resources`，不参与运行态装配）
- `application-prod.yml` - 生产环境

通过 `spring.profiles.active` 激活对应环境:
```bash
mvnd spring-boot:run -Dspring-boot.run.profiles=prod
```

## 🔐 安全认证配置

### office 注解权限与审计

office-service 在订阅时执行本地 `@RequirePermission` 检查，权限码直接来自 Handler 注解，逗号分隔权限沿用 AND/OR 语义。角色名称本身不提供绕过；管理员也必须在授权快照中拥有声明的权限。

`office.security.authorization.shared-user-namespace`（`OFFICE_AUTHORIZATION_SHARED_USER_NAMESPACE`）默认 `false`。只有部署者确认已验签 office JWT 的 `userId`、`tenantId` 与 auth 用户及租户命名空间相同才可开启；`accountId` 不能代替 `userId`，异构身份部署需先实现可信显式映射。`base-url`（`OFFICE_AUTHORIZATION_BASE_URL`）必须是 HTTPS auth 中心地址，`timeout`（`OFFICE_AUTHORIZATION_TIMEOUT`）默认 `3s`，范围为大于零至 `30s`。

通过安全配置中心或 `SPRING_APPLICATION_JSON` 注入 `office.security.authorization.reader-tokens` 映射，键为精确租户 ID，值为该租户的专用 reader 访问 JWT。reader 必须满足 auth 的 `auth-center.authorization-reader-user-id` 及有效账号校验；端点只能读取 reader 自身租户，无法用请求头切换租户。需由部署方为每个租户提供有效 reader，并负责凭据续期；当前 auth reader 的全局用户 ID 配置可能限制多租户部署，无法配置的租户保持拒绝。调用 `GET /auth/v1/authorization/users/{userId}` 后还会校验返回业务码 `0` 与用户、租户一致，缺配置、过期、远端错误或超时均拒绝。`.env` 不会自动注入 Java 进程或全部 Compose 变量。

本地 `AuditLogService` 输出可检索的 `OFFICE_AUDIT` 应用日志，不保证可靠持久化。`@OperationLog` 仅采集注解和 HTTP 元数据，正文、参数、身份头、凭据、异常消息均不采集；`saveRequest=false` / `saveResponse=false` 进一步关闭对应 HTTP 元数据。`HTTP_RESPONSE_READY` 只表示 Handler 已生成响应，不表示响应已发送或业务成功（包括 Handler 吞错、`Result<Boolean>` 为 `false` 的情况）。

### 凭据加密密钥

数据源密码和认证中心 TOTP 密钥、备份码及已消费备份码使用 `apex.security.data-encryption-key`，由 `APEX_DATA_ENCRYPTION_KEY` 注入，值为 Base64 编码的 32 字节随机密钥，可用 `openssl rand -base64 32` 生成。必须跨实例和重启保持一致；缺失、非法 Base64 或长度错误会拒绝初始化。原先由临时随机密钥加密的数据需要使用原密钥解密后重新加密，无法通过新密钥恢复。

TOTP 仓储只读取加密格式，不会在解密失败时回退为明文。升级前需备份 `auth_totp`，使用部署的稳定数据密钥将历史明文 `secret`、`backup_codes`、`used_backup_codes` 转换为当前 AES-GCM 格式，或安排用户重新绑定 MFA；可用 `bash scripts/utils/local-deployment.sh prepare` 离线生成候选快照、备份和审查报告；工具不连接数据库，不自动执行迁移。更换密钥前也必须先完成旧密钥到新密钥的数据迁移。

公共配置加密使用 `security.config.encryption.key`（`CONFIG_ENCRYPTION_KEY`），要求 UTF-8 编码后恰好 32 字节且不为全空白。认证中心独立配置加密使用 `apex.security.config-encryption-key`（`APEX_CONFIG_ENCRYPTION_KEY`），要求 Base64 解码后恰好 32 字节。两者格式不同，不能直接共用同一个字符串；均不再提供默认或临时密钥。

Token 存储加密使用 `security.encryption.key`（环境变量 `SECURITY_ENCRYPTION_KEY`）；认证中心也支持 `security.token.encryption.key`（`TOKEN_ENCRYPTION_KEY`），前者优先。密钥必须为 Base64 编码的 32 字节，跨实例和重启保持一致；缺失或长度错误会拒绝启动，不再生成临时密钥。旧临时密钥加密的 Token 需要保留原密钥迁移，否则必须重新登录。

认证中心（`apex-auth-service`）除上述密钥外还要求 OAuth2 签发密钥对：`auth-center.oauth2.private-key` / `public-key`，通过 `AUTH_CENTER_OAUTH2_PRIVATE_KEY` / `AUTH_CENTER_OAUTH2_PUBLIC_KEY` 注入，值为 Base64 编码的 PKCS#8 私钥与 X.509 公钥（不含 PEM 头尾）。**非 basic 档缺失时认证服务直接拒绝启动**（`OAuth2 RSA keys must be configured outside basic profile`）。`AUTH_CENTER_OAUTH2_KEY_ID` 对应 JWT header 的 `kid`，需与 `/.well-known/jwks.json` 输出一致。

认证服务的数据源必须使用 `spring.datasource.dynamic.datasource.master`（dynamic-datasource 由 `apex-infrastructure-persistence` 传递引入）。只配置普通 `spring.datasource.url` 会在启动时报 `dynamic-datasource can not find primary datasource`；basic 档位通过排除该自动配置改用 H2。认证服务的容器健康检查目标为 `GET /actuator/health/readiness`，安全链必须放行 `/actuator/health/**`，否则镜像始终为 unhealthy。

Compose 强制注入 `APEX_DATA_ENCRYPTION_KEY`、`SECURITY_ENCRYPTION_KEY`、`CONFIG_ENCRYPTION_KEY`、`APEX_CONFIG_ENCRYPTION_KEY` 和 `JWT_SECRET`，缺失或为空时拒绝渲染。一次性 `security-key-check` 服务使用 Java 21 JRE 镜像和预构建的离线工具 JAR 校验格式，成功后才允许启动应用；四类加密密钥必须独立生成并安全保存，不应每次启动重新生成。三个 Base64 密钥可分别用 `openssl rand -base64 32` 生成；公共配置密钥可用 `openssl rand -hex 16` 生成（结果是 32 字节 ASCII）。`.env.example` 仅保留空值。环境变量仍可被具有 Podman 管理权限的用户查看。

安全检查命令（捕获渲染结果，不把展开后的密钥打印到终端）：

```bash
source scripts/build/init-build-env.sh
mvnd -B -pl apex-tools-deployment -am verify
bash scripts/utils/local-deployment.sh compose --env-file .env
```

离线 TOTP 迁移准备与回滚：

```bash
source scripts/build/init-build-env.sh
mvnd -B -pl apex-tools-deployment -am verify
bash scripts/utils/local-deployment.sh prepare --input /secure/auth_totp.json --output /secure/totp-review
bash scripts/utils/local-deployment.sh rollback --bundle /secure/totp-review --output /secure/auth_totp-restored.json
```

运行前通过安全环境注入 `APEX_DATA_ENCRYPTION_KEY`；如需轮换，另注入 `TOTP_OLD_DATA_ENCRYPTION_KEY`，不将值写进命令行。输入为完整 `auth_totp` 行的 JSON 数组，必须包含 `id`、`tenant_id`、`version`、`secret`、`backup_codes`、`used_backup_codes`，包括逻辑删除行。工具只转换后三个敏感字段，保留其他字段。历史明文只接受当前生成器的 32 字符大写 Base32 secret 及逗号分隔的 8 位数字备份码；允许空备份码和 NULL 备份码。当前密文为裸 `Base64(12 字节 IV + AES-256-GCM 密文 + 16 字节标签)`，不是 `ENC(...)`；必须认证解密成功且明文格式正确才能跳过。旧密钥密文在提供原密钥时重加密，未知格式、损坏密文、身份重复或错误密钥会阻止生成迁移包。

输出目录权限为 0700、文件为 0600，包含原始字节备份 `backup.json`、候选 `candidate.json` 与不含字段值的 `review.json`。备份可能含明文，仍须放在受控加密磁盘，不能入库。重复处理候选数据不产生二次加密；拒绝覆盖已有输出。离线回滚同时核对备份和候选 SHA-256 后恢复原始快照，摘要用于检测意外改动，不代替可信签名。输出或磁盘写入失败可能留下不完整目录，应检查后选择新的输出目录。

实际数据库应用须另行审查、停写、完整备份并在隔离副本演练；按 `tenant_id`、`id`、`version` 及旧字段值做事务内比较更新，只替换三个字段，任何冲突整批回滚。恢复写入前验证 MFA。数据库回滚同样需要停写并检查迁移后是否发生业务修改；本工具的 rollback 只恢复离线快照，不能声称已回滚数据库。丢失原随机密钥的数据不能恢复，需重新绑定 MFA；本工具不会连接或修改现有数据库。


部署配置生成仅输出密码环境变量占位符；环境变量模板中的 `DB_PASSWORD` 留空，由部署者在安全配置中填写。

### JWT配置

```yaml
jwt:
  # JWT签名密钥(生产环境必须修改为强随机字符串,至少32字符)
  secret: ${JWT_SECRET:change-me-in-production}

  # Access Token过期时间(毫秒),默认2小时
  access-token-expire: 7200000

  # Refresh Token过期时间(毫秒),默认7天
  refresh-token-expire: 604800000

  # JWT密钥最小长度
  min-secret-length: 32
```

**安全建议**:
- ⚠️ 生产环境务必通过环境变量设置 `JWT_SECRET`
- 使用强随机字符串生成器: `openssl rand -base64 48`
- 定期轮换密钥

### workflow-service 独立 JWT 验签

workflow-service 从 `Authorization: Bearer` 独立验签，身份只读取已签名的 `userId`、`tenantId`、`clientType`，不信任 `X-User-Id` / `X-Tenant-Id` / `X-Client-Type`。必须包含有效 `exp`、`tokenType=ACCESS`、正数用户 ID 和非空白字符串租户。当前 auth 签发 RS256，设置 `WORKFLOW_JWT_PUBLIC_KEY_BASE64`（`workflow.security.jwt-public-key-base64`）为对应活跃 RSA 公钥的 X509 DER Base64，不能填写 PEM 或 JWKS JSON，也不能用共享 `JWT_SECRET` 验证 RS256。多公钥模式（统一认证收口 A1）可设置 `WORKFLOW_JWT_PUBLIC_KEYS_BASE64`（`workflow.security.jwt-public-keys-base64`，格式 `kid=Base64X509,kid=Base64X509`）按 JWT header 的 `kid` 精确选择公钥，多公钥优先于单公钥。

历史 HMAC 签发方须同时显式设置 `WORKFLOW_JWT_HMAC_ENABLED=true` 和至少 32 字节的 `WORKFLOW_JWT_SECRET`，对应 `workflow.security.jwt-hmac-enabled` / `jwt-secret`；默认关闭。配置 RSA 后只接受 RS256，不因签名失败而降级到 HMAC；非法 RSA 配置拒绝启动，缺失验签密钥时不建立认证。

`.env.example` 提供以上变量，`podman-compose.yml` 在 workflow 容器中显式注入；直接启动 Java 进程时仍须导出环境变量或通过安全配置注入。服务只做本地签名与声明校验，不检查会话撤销、账号停用或令牌黑名单；`/internal/v1/**` 仍在现有安全链中 `permitAll`，内部接口鉴权需另行治理。

### 共享 JWKS 拉取与网关撤销流（统一认证收口 A2/A3）

认证中心登录密钥（RS256，Redis 存储）以 30 天周期自动轮换并保留 7 天宽限；`GET /.well-known/jwks.json` 聚合输出轮换密钥（活跃 + 宽限期）与 OAuth2 过渡静态密钥，是登录令牌与 OAuth2 令牌唯一的公钥分发点。网关与各服务通过共享拉取客户端（apex-common `JwksAutoConfiguration`）自动获取轮换后的公钥，无需人工同步：

| 配置（`apex.jwks.*` / 网关为 `apex.gateway.jwks.*`） | 环境变量 | 说明 |
| --- | --- | --- |
| `apex.jwks.remote-url` | `JWKS_REMOTE_URL` | JWKS 完整地址；空 = 拉取关闭，各服务使用静态公钥兜底 |
| `apex.gateway.jwks.remote-url` | 同上 | 网关远程拉取地址；同 kid 覆盖静态 `ks` 条目，`hmac-legacy` 不受影响 |
| `apex.jwks.refresh-interval-seconds` / `apex.gateway.jwks.remote-refresh-interval-seconds` | `JWKS_REFRESH_INTERVAL_SECONDS` | 拉取间隔（默认 300，下限 30）；密钥轮换后最迟一个周期内生效 |
| `apex.system.jwt-center.public-keys` | — | 主应用认证中心公钥静态清单（`kid → X509 DER Base64`），仅作拉取不可用时的兜底 |
| `auth-center.oauth2.legacy-key-in-jwks` | — | 认证中心聚合 JWKS 是否并入配置注入静态密钥（过渡期默认 true，结束后关闭并移除静态密钥配置） |
| `apex.storage-quota.usage-cache-seconds` | — | file 侧存储已用字节聚合缓存（默认 60s，软上限口径，窗口内连续上传可能短暂低估） |
| `apex.gateway.revocation.enabled` / `url` | `REVOCATION_FEED_ENABLED` / `REVOCATION_FEED_URL` | 网关令牌撤销流（A3）：周期拉取认证中心撤销增量（`GET /internal/v1/tokens/revocations?since=`），本地缓存 jti 撤销集；验签通过但命中撤销集的令牌收敛为 401，登出/踢人在拉取间隔内于网关生效 |
| `apex.gateway.revocation.pull-interval-seconds` / `lookback-seconds` | `REVOCATION_PULL_INTERVAL_SECONDS` / `REVOCATION_LOOKBACK_SECONDS` | 拉取间隔（默认 15s，下限 5）与启动回看窗口（默认 7200s，覆盖访问令牌全生命周期） |

行为约定：拉取失败保留上一次快照并告警，不影响在用公钥；启动时立即拉取一次；主应用自有令牌仍为 HS256（`jwt.secret`），`apex.system.jwt-center.legacy-hmac-enabled` 在主应用签发链路迁入统一密钥体系前不得关闭。轮换期间旧公钥在宽限期内持续可用，存量令牌不失效。

### 多端统一账户与微信渠道（多端对接改造新增）

主应用一套后端同时服务 App / 微信小程序 / 微信公众号 H5 / PC 端，账户统一收敛到 `sys_user`，账户来源可区分（`register_channel` + `sys_user_social.channel` + 登录日志 `client_type`）。

**登录方式矩阵**（统一入口 `POST /api/v1/auth/login`，按 `loginType` 分发）：

| loginType | 端 | 说明 |
| --- | --- | --- |
| `WX_CODE` / `WX_PHONE` / `WX_SMS` / `CL_PHONE` / `CL_SMS` | App | 既有链路不变 |
| `PASSWORD` | PC Web / PC 官网 | 账号密码登录 |
| `WX_MINI` | 微信小程序 | `wx.login()` 的 code（必填）+ `phoneCode`（可选） |
| `WX_OAUTH` | 微信公众号 H5 | 网页授权 code（必填）+ `state`（必传，由 authorize 端点签发） |

**微信渠道配置**（三渠道互相独立，凭据缺失时对应登录方式返回 `wx.channel_not_configured`(1023)，不影响其他渠道）：

```yaml
wechat:
  app:                      # 微信开放平台移动应用（App 端微信登录）
    app-id: ${WECHAT_APP_ID:}
    app-secret: ${WECHAT_APP_SECRET:}
  miniapp:                  # 微信小程序（WX_MINI 登录渠道）
    app-id: ${WECHAT_MINIAPP_APP_ID:}
    app-secret: ${WECHAT_MINIAPP_APP_SECRET:}
  mp:                       # 微信公众号（WX_OAUTH 网页授权登录渠道）
    app-id: ${WECHAT_MP_APP_ID:}
    app-secret: ${WECHAT_MP_APP_SECRET:}
```

**多端请求头**（CORS 已放行）：

- `X-Client-Type`：终端标识（`PC_ADMIN`/`PC_WEB`/`WECHAT_MP`/`MINI_PROGRAM`/`APP_ANDROID`/`APP_IOS`/`H5`），写入 JWT claims 与登录日志 `sys_login_log.client_type`；请求体 `clientType` 字段兜底，缺省 `UNKNOWN` 完全兼容存量客户端
- `X-Tenant-Id`：租户标识
- `Idempotency-Key`：注册幂等键
- `X-Device-Id`：设备指纹

**多会话令牌**：每次登录生成独立会话（JWT claims `sid`），Redis 键 `token:(access|refresh):{subject}:{sid}` + 会话索引 `token:sessions:{subject}`；多端同时在线互不顶号。登出默认仅当前会话，`POST /api/v1/auth/logout?scope=all` 全端登出。存量无 `sid` 令牌走 legacy 单会话槽，行为不变，自然过期淘汰。无感续期沿用 `X-New-Access-Token` 响应头。

**PC 管理端（认证中心登录）**：PC 管理后台走认证中心 `POST /auth/v1/login`（密码 + MFA/TOTP + 设备信任），签发令牌 claims 携带 `accountId`（主应用 `sys_user.id`）、`username`、`clientType=PC_ADMIN`；主应用 `JwtAuthenticationFilter` 识别该 claims 结构后按 `accountId` 装配统一账户并复用主应用 RBAC。要求：

- 两应用使用相同 `JWT_SECRET` 环境变量
- 认证中心库执行 V55 迁移（`auth_user.account_id`）
- 存量账户映射按 `scripts/data/backfill_auth_user_account_id.sql` 人工回填（两库独立，username 精确匹配，幂等）；未映射账户登录后访问主应用业务接口将返回 401
- `wechat.mp` 网页授权需在公众平台配置网页授权域名；小程序需配置服务器域名白名单

<a id="认证中心统一授权"></a>

### 认证中心统一授权

主应用使用 `apex.features.auth-center-permission` 切换权限、JWT 角色和数据范围的数据源，默认 `false`。开启后，缺失可信映射、中心故障、超时或身份不一致均拒绝授权，不回退本地角色或权限。

```yaml
apex:
  features:
    auth-center-permission: ${APEX_AUTH_CENTER_PERMISSION_ENABLED:false}
  auth-center-permission:
    base-url: ${APEX_AUTH_CENTER_BASE_URL:}
    access-token: ${APEX_AUTH_CENTER_ACCESS_TOKEN:}
    cache-seconds: ${APEX_AUTH_CENTER_CACHE_SECONDS:0}
    mappings:
      - main-tenant-id: main
        main-user-id: 7
        center-tenant-id: center
        center-user-id: 99
```

映射由服务端配置维护，必须唯一，禁止把两个系统的用户 ID 或租户 ID 默认视为相同。租户取自主应用数据库用户；中心查询租户取自 Bearer Token，请求租户头不能覆盖它。`base-url` 必须使用 HTTPS，不允许用户信息、查询参数或片段。

认证中心独立配置 `auth-center.authorization-reader-user-id`，通过环境变量 `AUTH_CENTER_AUTHORIZATION_READER_USER_ID` 指定专用读取账号。主应用的 Token 必须是该账号的有效 ACCESS Token，所属租户必须与映射的中心租户一致。不同中心租户应使用各自的部署配置和读取凭据。每次中心查询均检查读取账号与目标账号的状态、锁定和软删除。读取账号不需要管理员角色。凭据由部署侧安全注入并负责在过期前轮换；当前适配器不自动登录或刷新 Token。

缓存默认关闭。启用时 `cache-seconds` 允许 0–30 秒，撤权和账号停用最多存在该 TTL 的延迟。缓存键包含主租户与用户，快照独立检查有效期，过期缓存不参与故障降级。当前没有跨实例主动撤权失效机制；修改映射或读取凭据后，应清理授权缓存或等待 TTL 到期再接入流量。

中心支持 `SELF`、`ALL`、`DEPT`、`DEPT_AND_CHILD`：SELF 使用主应用用户 ID，DEPT 和 DEPT_AND_CHILD 使用主应用已认证且启用的部门信息。中心角色、部门 ID 不复制到主库；`CUSTOM`、空值和未知范围默认拒绝。主应用仍负责原有 JWT 身份认证；本配置统一的是授权数据源。

授权读取请求为 `GET {base-url}/auth/v1/authorization/users/{centerUserId}`，使用配置的 Bearer ACCESS Token。成功响应是 `Result`，`data` 包含中心租户/用户身份、权限编码和角色数据范围；客户端校验成功码与映射身份后转换为主应用身份。客户端超时为 3 秒，使用名为 `auth-center-permission` 的 Resilience4j 默认熔断器；当前没有为该熔断器提供独立配置项。

中心默认监听 8081，HTTPS 入口须由实际部署提供；示例本地 HTTP 地址不能直接用于主应用远程授权配置。参见[认证接口路由契约](../../apex-auth-service/src/main/java/com/apex/scaffold/authservice/interfaces/router/AuthenticationRouter.java)、[认证接口集成测试源码](../../apex-auth-service/src/test/java/com/apex/scaffold/authservice/interfaces/handler/AuthenticationHandlerIntegrationTest.java)和[业务时序](../architecture/business-flows.md)。

### RBAC权限配置

```yaml
security:
  # 是否启用RBAC权限控制
  rbac-enabled: true
  
  # 超级管理员角色标识
  admin-role: ADMIN
  
  # 公开路径(无需认证即可访问)
  public-paths:
    - /api/v1/health
    - /api/v1/info
    - /swagger-ui.html
    - /doc.html
    - /v3/api-docs/**
```

## 💾 数据源配置

### MySQL配置

```yaml
spring:
  datasource:
    dynamic:
      # 默认数据源（dynamic-datasource，应用实际结构见 application-dev.yml / application-prod.yml）
      primary: master
      datasource:
        master:
          # 数据库URL（默认库 apex_system；dev 默认 root/apex-root，prod 必须环境变量注入）
          url: jdbc:mysql://127.0.0.1:3306/apex_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
          # 用户名
          username: root
          # 密码(生产环境建议使用环境变量 DB_MASTER_PASSWORD)
          password: ${DB_MASTER_PASSWORD:apex-root}
          # 驱动类名
          driver-class-name: com.mysql.cj.jdbc.Driver
          # Druid连接池配置
          druid:
            # 初始连接数
            druid:
              # 初始连接数
              initial-size: 5
              # 最小空闲连接数
              min-idle: 5
              # 最大活跃连接数
              max-active: 20
              # 获取连接超时时间(毫秒)
              max-wait: 60000
              # 空闲连接检测间隔(毫秒)（须小于 keep-alive-between-time-millis）
              time-between-eviction-runs-millis: 30000
              # 连接保活间隔(毫秒)
              keep-alive-between-time-millis: 60000
              # 连接最小生存时间(毫秒)
              min-evictable-idle-time-millis: 300000
              # 是否开启PSCache
              pool-prepared-statements: true
              # PSCache大小
              max-pool-prepared-statement-per-connection-size: 20
              # 监控统计拦截的filters
              filters: stat,wall,slf4j
              # 合并多个DruidDataSource的监控数据
              use-global-data-source-stat: true
        # 从库（与 master 参数完全一致，仅环境变量前缀 DB_SLAVE_*；prod 必须注入真实只读副本地址）
        slave:
          url: jdbc:mysql://127.0.0.1:3306/apex_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
          username: root
          # 密码(生产环境建议使用环境变量 DB_SLAVE_PASSWORD)
          password: ${DB_SLAVE_PASSWORD:apex-root}
          driver-class-name: com.mysql.cj.jdbc.Driver
          druid:
            # 初始连接数
            initial-size: 5
            # 最小空闲连接数
            min-idle: 5
            # 最大活跃连接数
            max-active: 20
            # 获取连接超时时间(毫秒)
            max-wait: 60000
            # 空闲连接检测间隔(毫秒)（须小于 keep-alive-between-time-millis）
            time-between-eviction-runs-millis: 30000
            # 连接保活间隔(毫秒)
            keep-alive-between-time-millis: 60000
            # 连接最小生存时间(毫秒)
            min-evictable-idle-time-millis: 300000
            # 是否开启PSCache
            pool-prepared-statements: true
            # PSCache大小
            max-pool-prepared-statement-per-connection-size: 20
            # 监控统计拦截的filters
            filters: stat,wall,slf4j
            # 合并多个DruidDataSource的监控数据
            use-global-data-source-stat: true
```

### MyBatis-Plus配置

```yaml
mybatis-plus:
  # Mapper XML文件位置
  mapper-locations: classpath*:mapper/**/*.xml
  
  # 实体类扫描包
  type-aliases-package: com.apex.**.model.entity
  
  # 全局配置
  global-config:
    db-config:
      # 主键类型(AUTO自增/INPUT手动输入/ASSIGN_ID雪花算法)
      id-type: ASSIGN_ID
      
      # 逻辑删除字段名
      logic-delete-field: deleted
      
      # 逻辑删除值(已删除)
      logic-delete-value: 1
      
      # 逻辑未删除值
      logic-not-delete-value: 0
  
  # 配置
  configuration:
    # 驼峰命名转换
    map-underscore-to-camel-case: true
    
    # 缓存
    cache-enabled: false
    
    # 日志实现
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
```

## 🗄️ 缓存配置

<a id="redis配置"></a>

### Redis配置

```yaml
spring:
  data:
    redis:
      # Redis主机
      host: localhost
      
      # Redis端口
      port: 6379
      
      # Redis密码(如果有)
      password: ${REDIS_PASSWORD:}
      
      # 数据库索引
      database: 0
      
      # 连接超时时间(毫秒)
      timeout: 3000
      
      # Lettuce连接池配置
      lettuce:
        pool:
          # 最大活跃连接数
          max-active: 8
          
          # 最大空闲连接数
          max-idle: 8
          
          # 最小空闲连接数
          min-idle: 0
          
          # 获取连接最大等待时间(毫秒)
          max-wait: -1
```

### Caffeine本地缓存配置

```yaml
cache:
  caffeine:
    # 最大缓存条目数
    max-size: 1000
    
    # 写入后过期时间(秒)
    expire-after-write: 300
    
    # 访问后过期时间(秒,0表示禁用)
    expire-after-access: 0
    
    # 是否统计命中率
    record-stats: true
```

### 多级缓存策略

```yaml
cache:
  multi-level:
    # 是否启用多级缓存
    enabled: true
    
    # L1缓存(Caffeine)TTL(秒)
    l1-ttl: 60
    
    # L2缓存(Redis)TTL(秒)
    l2-ttl: 300
    
    # 缓存更新策略(NONE/WRITE_THROUGH/WRITE_BEHIND)
    update-strategy: WRITE_THROUGH
```

## ⚡ 限流熔断配置

### 全局响应超时

```yaml
apex:
  api:
    # 单个请求（含过滤器链与业务处理）的最大处理时长
    # 超时返回 504 + REQUEST_TIMEOUT(2006) 统一错误响应，防止慢请求占用连接引发雪崩
    # SSE 长连接（Accept: text/event-stream）自动豁免；设为 0s 关闭
    response-timeout: ${APEX_RESPONSE_TIMEOUT:30s}
```

<a id="sentinel配置"></a>

### Sentinel配置

**控制台通信（spring.cloud.sentinel）**：

```yaml
spring:
  cloud:
    sentinel:
      transport:
        # 控制台地址（空 = 不连接控制台，仅本地规则生效；生产经 SENTINEL_DASHBOARD_URL 注入）
        dashboard: ${SENTINEL_DASHBOARD_URL:}
        # 客户端传输端口，与宿主/容器内其他占用对齐
        port: ${SENTINEL_TRANSPORT_PORT:8719}
      # 启动即初始化客户端，避免首请求懒加载延迟
      eager: ${SENTINEL_EAGER:true}
```

**规则 Nacos 动态下发（apex.sentinel.nacos，生产热更新）**：

```yaml
apex:
  sentinel:
    nacos:
      # 启用后限流/熔断规则从 Nacos 拉取（约 30s 热生效），静态规则作为启动兜底；
      # 默认关闭（无 Nacos 零影响）；生产接入 Nacos 后置 true
      enabled: ${SENTINEL_NACOS_ENABLED:false}
      server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:18848}
      namespace: ${NACOS_NAMESPACE:}
      group-id: ${SENTINEL_NACOS_GROUP:SENTINEL_GROUP}
      flow-data-id: ${SENTINEL_NACOS_FLOW_DATA_ID:apex-sentinel-flow-rules}
      degrade-data-id: ${SENTINEL_NACOS_DEGRADE_DATA_ID:apex-sentinel-degrade-rules}
```

> ⚠️ 启用动态下发时，Nacos 的 DataId 需维护**完整规则集**（推送为整体替换语义）。
> JSON 模板与字段说明见 `config/sentinel/README.md`；当前内置静态基线为 43 条限流规则和 30 条降级规则，定义于 `SentinelConfig`，两个 Nacos DataId 必须分别发布完整集合。动态规则推送采用整体替换语义，不会与静态规则自动合并。

### Resilience4j 与远程调用配置

当前存在两条独立的断路器链路：

- `apex.circuit-breaker` 由 `CircuitBreakerService` 读取，供业务通过 `CircuitBreakerPort.withCircuitBreaker(name, mono)` 显式接入编程式断路器。
- `resilience4j.circuitbreaker.instances` 由 `RemoteApiClient` 使用的 `CircuitBreakerRegistry` 管理。实例名必须与 `remote.api.services` 的服务名一致，远程调用才会绑定对应断路器。

`RemoteApiClient` 的连接超时、读取超时和重试参数实际来自 `RemoteApiProperties`，即 `remote.api` 配置。重试由 Reactor Retry 执行：4xx 不重试，5xx 和未分类调用异常按配置进行退避重试。

```yaml
remote:
  api:
    default-connect-timeout: 5000
    default-read-timeout: 10000
    default-retry-count: 2
    default-retry-delay: 1000
    services:
      example-service:
        base-url: https://api.example.com
        connect-timeout: 3000
        read-timeout: 8000
        retry-count: 2
        retry-delay: 1000

resilience4j:
  circuitbreaker:
    instances:
      example-service:
        sliding-window-size: 10
        failure-rate-threshold: 50
        slow-call-rate-threshold: 50
        slow-call-duration-threshold: 3s
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 10

apex:
  circuit-breaker:
    default-config:
      failure-rate-threshold: 50
      wait-duration-in-open-state: 60s
      sliding-window-size: 100
      minimum-number-of-calls: 10
      permitted-calls-in-half-open: 5
```

`resilience4j.retry`、`resilience4j.timelimiter` 和 `resilience4j.ratelimiter` 即使存在于配置中，也不会自动作用于当前 `RemoteApiClient` 调用链；当前远程重试和超时分别由 Reactor Retry 与 `remote.api` 参数控制。

### 概览聚合门面并发隔离（system-service）

`GET /internal/v1/overview` 的跨域聚合门面（`OverviewFacadeService`）对每个数据源维护独立并发舱壁（JDK `Semaphore` 实现，饱和即降级、不排队等待，等价于 Resilience4j `SemaphoreBulkhead` 的降级语义且不引入 basic 档裁剪依赖）：

```yaml
apex:
  overview:
    timeout-millis: 3000            # 单源调用超时
    cache-ttl-seconds: 30           # 全源 UP 结果的租户缓存 TTL
    default-bulkhead-max-concurrency: 8   # 未显式配置的源的舱壁并发上限
    bulkhead:
      workflow: 4                   # 按源覆盖并发上限（键与 apex.overview.sources 一致）
```

- 舱壁饱和时该源立即降级（`DEGRADED`，错误摘要 `bulkhead saturated`），不影响其他源；许可随上游调用终止（完成/错误/超时）释放，并发合并下调用方取消不会中断共享刷新（释放在上游终止时发生，超时为上界）。
- 降级结果不进入租户缓存；仅全源 UP 的结果按租户缓存共享，同（租户、调用方凭证）的并发请求合并为一次下游刷新。

### 统一待办聚合（apex-aggregation-service）

`GET /api/v2/todo` 的跨域待办门面（`UnifiedTodoAggregationService`）并行聚合两个来源：workflow 当前用户任务（内部 HTTP）、office 待审批与未读站内信（内部 HTTP）。该门面于 2026-11-12 由 system 应用层迁至独立只读聚合运行模块 `apex-aggregation-service`（默认端口 8098，无数据库连接、无 Flyway 迁移、不引入事务组件），原 system 进程内站内通知源已删除。与概览门面不同，**个人维度数据不做任何缓存，也不做 in-flight 合并**，每次请求实时聚合：

```yaml
apex:
  todo:
    timeout-millis: 3000              # 单源调用超时
    page-size: 10                     # workflow 任务分页大小（上限 50）
    approval-limit: 10                # office 待审批条目上限（上限 50）
    message-limit: 10                 # office 未读站内信条目上限（上限 50）
    default-bulkhead-max-concurrency: 8   # 未显式配置的源的舱壁并发上限
    bulkhead:
      workflow: 4                     # 按源覆盖并发上限
      office: 4
    sources:
      workflow: http://127.0.0.1:8093   # workflow 内部待办端点地址
      office: http://127.0.0.1:8084     # office 内部待办端点地址
```

- 两源并发执行；单源超时、不可达、信封非 0 或解析失败时该源降级为 `DEGRADED` 并携带错误摘要，`degraded=true`，不影响其他源，也不向上抛错。
- 舱壁沿用 JDK `Semaphore`（饱和即降级、不排队，许可随订阅终止释放），语义与概览门面一致，不引入 basic 档裁剪依赖。
- `sources.*` 默认指向本机端口，部署时须与 workflow/office 实际地址一致；关闭 Nacos 的直连部署需显式覆盖。对外路由由网关 `/api/v2/**` 转发至 `lb://apex-aggregation-service`（`ROUTE_AGGREGATION_URI`，服务默认业务端口 8098）；关闭 Nacos 的直连部署须显式注入该地址并指向实际聚合实例。
- 调用方 `apex-aggregation-service` 已登记在 `InternalAuthProperties` 默认 `callers` 白名单（`/internal/v1/workflow/todo/**` 与 `/internal/v1/office/todo`）；租户取自本服务验签 JWT 并经 `InternalAuthExchangeFilter` 转换为 `X-Internal-Tenant` 参与下游签名。未登记调用方的出站签名会被下游 fail-closed 拒绝。
- 本服务自验访问 JWT（`aggregation.security.jwt-public-keys-base64/jwt-public-key-base64/jwt-secret`，env 前缀 `AGGREGATION_JWT_*`），身份头不作为认证依据；`/api/v2/todo` 要求已认证，未配置验签密钥且未启用 JWKS 拉取时拒绝认证。

### 接口级限流

```yaml
rate-limit:
  # 是否启用限流
  enabled: true
  
  # 默认QPS限制
  default-qps: 100
  
  # 用户级别限流
  user:
    # 每用户每秒请求数
    qps-per-user: 10
    
    # 限流维度(USER/IP/GLOBAL)
    dimension: USER
  
  # 热点参数限流
  hot-spot:
    # 是否启用
    enabled: true
    
    # 统计窗口时长(秒)
    window-length: 60
    
    # 最大访问次数
    max-count: 1000
```

### IP 限流与客户端 IP 解析

`apex.security.ip-rate-limit.trusted-proxy-cidrs` 声明可信反向代理网段（CIDR，逗号分隔），由 `APEX_TRUSTED_PROXY_CIDRS` 注入，默认留空。

- 仅当 TCP 直连地址命中可信网段时，才采信 `X-Forwarded-For` / `X-Real-IP`；并按代理链从右向左跳过可信代理，取首个非可信地址作为客户端 IP。
- 直连地址不可信时一律使用直连地址，客户端自带的转发头被忽略，避免伪造 IP 绕过 IP 限流、登录保护或污染审计日志。
- 留空表示不信任任何代理。经网关或负载均衡转发时必须填入其网段，否则限流与审计只能看到代理地址。
- 该配置同时作用于 system（`IpRateLimitProperties`，限流、幂等键与操作日志）与 auth（`AuthTrustedProxyProperties`，OAuth 端点、密码找回与资源权限切面）。

## 📨 消息队列配置

### RocketMQ配置

> ⚠️ rocketmq-spring-boot 2.3.3 依据 `rocketmq.name-server` 属性**是否存在**装配生产者组件：
> 不注入 `ROCKETMQ_NAMESERVER` 时全部跳过（应用可无 MQ 启动），启用时注入即可。

```yaml
rocketmq:
  # NameServer地址（启用消息能力时注入；本地 infra 栈为 127.0.0.1:9876）
  name-server: 127.0.0.1:9876
  
  # 生产者配置
  producer:
    # 生产者组名
    group: apex-producer-group
    
    # 发送消息超时时间(毫秒)
    send-message-timeout: ${ROCKETMQ_SEND_TIMEOUT:30000}
    
    # 消息最大长度(字节)
    max-message-size: 4194304
    
    # 同步发送失败重试次数
    retry-times-when-send-failed: 2
    
    # 异步发送失败重试次数
    retry-times-when-send-async-failed: 2

  # 消费者配置（项目仅启用开关，其余以 rocketmq-spring 默认值运行）
  consumer:
    # 是否启用消息消费
    enabled: ${ROCKETMQ_CONSUMER_ENABLED:true}

mq:
  consumer:
    # 消费幂等记录保留时间（分钟）
    idempotent-ttl-minutes: ${MQ_IDEMPOTENT_TTL_MINUTES:30}

apex:
  mq:
    # 是否通过 RocketMQ 广播领域事件
    domain-event-enabled: ${APEX_MQ_DOMAIN_EVENT_ENABLED:false}
    domain-event-topic: ${APEX_MQ_DOMAIN_EVENT_TOPIC:apex-domain-event}
  events:
    outbox:
      batch-size: ${OUTBOX_BATCH_SIZE:50}
      max-attempts: ${OUTBOX_MAX_ATTEMPTS:8}
      lock-timeout-seconds: ${OUTBOX_LOCK_TIMEOUT_SECONDS:120}
      parallelism: ${OUTBOX_PARALLELISM:4}
      retention-enabled: ${OUTBOX_RETENTION_ENABLED:true}
      retention-delivered-hours: ${OUTBOX_RETENTION_DELIVERED_HOURS:168}
      retention-dead-days: ${OUTBOX_RETENTION_DEAD_DAYS:30}
      retention-batch-size: ${OUTBOX_RETENTION_BATCH_SIZE:5000}
```

生产消息使用稳定的业务 message ID，同时写入消息 header 和 RocketMQ `KEYS`。消费者以 Redis token 竞争处理权，状态从 `PROCESSING:<token>` 变为 `SUCCESS`；业务处理或最终确认失败时释放当前 token 并抛出异常，使 RocketMQ 触发重消费。

### 自动重试配置

`@AutoRetry` 适用于幂等或可安全重复执行的业务方法，支持同步返回值、`Mono` 和 `Flux`：

```java
@AutoRetry(maxAttempts = 3, retryDelayMs = 200, maxDelayMs = 5000,
        backoffMultiplier = 2.0, retryOn = {TransientDataAccessException.class})
public Mono<Result> refreshCache(String key) {
    return cacheClient.refresh(key);
}
```

`maxAttempts` 包含首次调用；仅 `retryOn` 指定的异常会重试，其他异常立即传播。响应式方法在订阅时执行退避，不应在注解方法内部自行 `subscribe()`。

### Outbox 配置与生命周期

领域事件先写入 Outbox，再由 worker 领取并投递。记录按 `PENDING -> PROCESSING -> DELIVERED` 流转；超时的 `PROCESSING` 会被回收，失败达到 `max-attempts` 后进入 `DEAD`。只有 RocketMQ callback 成功后才标记 `DELIVERED`。

## 📁 文件存储配置

### 多存储后端支持

五种类型为 `local`、`minio`、`volcengine`、`aliyun-oss`、`qiniu`，默认 Local；云开关默认关闭，不进行故障降级。完整字段以 [file 主配置](../../apex-file-service/src/main/resources/application.yml)及[环境模板](../../.env.example)为准，接入、凭据、迁移与限制见[对象存储指南](object-storage-integration.md)。

```yaml
storage:
  default-type: local
  bucket-name: apex
  presigned-url-expire-seconds: 300
  local:
    enabled: true
    base-path: /data/uploads
  minio:
    enabled: false
    endpoint: ${MINIO_ENDPOINT:}
    access-key: ${MINIO_ACCESS_KEY:}
    secret-key: ${MINIO_SECRET_KEY:}
  volcengine:
    enabled: false
    endpoint: ${STORAGE_VOLCENGINE_ENDPOINT:}
    region: ${STORAGE_VOLCENGINE_REGION:}
    access-key: ${STORAGE_VOLCENGINE_ACCESS_KEY:}
    secret-key: ${STORAGE_VOLCENGINE_SECRET_KEY:}
  aliyun-oss:
    enabled: false
    endpoint: ${STORAGE_ALIYUN_OSS_ENDPOINT:}
    access-key: ${STORAGE_ALIYUN_OSS_ACCESS_KEY:}
    secret-key: ${STORAGE_ALIYUN_OSS_SECRET_KEY:}
  qiniu:
    enabled: false
    region: ${STORAGE_QINIU_REGION:}
    bucket: ${STORAGE_QINIU_BUCKET:}
    download-domain: ${STORAGE_QINIU_DOWNLOAD_DOMAIN:}
    access-key: ${STORAGE_QINIU_ACCESS_KEY:}
    secret-key: ${STORAGE_QINIU_SECRET_KEY:}
```

云桶预创建；七牛区域与 HTTPS 下载域名必须对应配置桶。Local 不提供无认证预签名 URL。旧 `storage.s3`、`storage.local.url-prefix`、`STORAGE_LOCAL_PUBLIC_BASE_URL`、`MINIO_SECURE` 不属于当前 starter 配置。auth 当前签发 RS256，file 必须配置对应 X509 DER Base64 公钥；`JWT_SECRET` 不能替代 RSA 公钥。静态公钥仅作共享 JWKS 拉取（见上文「共享 JWKS 拉取」节）不可用时的兜底，推荐配置 `JWKS_REMOTE_URL` 自动跟进认证中心轮换。

## 🧭 Nacos 配置中心（微服务化）

微服务化后，配置分为**本地文件**与 **Nacos 远端**两层：`bootstrap.yml` 在应用
启动早期建立与 Nacos 的连接，再拉取远端配置与本地 `application.yml` 合并（迁移方案
§12.1）。本节的模板文件位于 [`config/nacos/`](../../config/nacos/README.md)。

### 加载顺序与优先级

```text
bootstrap.yml
  -> Nacos 远端配置（apex-common.yaml -> apex-datasource-{env}.yaml -> apex-{service}.yaml -> Sentinel 规则）
  -> application.yml
  -> application-{profile}.yml
```

本地占位符可由环境变量供值；最终优先级还须核对实际profile、bootstrap加载及Nacos远端属性源，不能仅按模板推断。上述加载链为部署约定；共享模板是否发布、挂载及映射到Spring属性须按实际部署核验，见[配置链核验记录](../06-testing/jdk21-unified-auth/20261016-p0/report.md)。

无Nacos部署时，分别设置 `NACOS_CONFIG_ENABLED=false`、`NACOS_DISCOVERY_ENABLED=false`、`NACOS_REGISTER_ENABLED=false`；三个开关独立，关闭配置中心不会自动关闭发现和注册。网关 `lb://` 路由同时改为明确HTTP地址，office消息HTTP与WebSocket地址须保持同一实例。仍需验证实际属性源和启动结果。

### bootstrap.yml 关键项

```yaml
spring:
  application:
    # 服务名 = Nacos 注册名 = 网关路由 lb:// 的目标名（apex-xxx-service）
    name: apex-gateway
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
        namespace: ${NACOS_NAMESPACE:public}
        group: DEFAULT_GROUP
        file-extension: yml
        refresh-enabled: true
        enabled: ${NACOS_CONFIG_ENABLED:true}
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
        namespace: ${NACOS_NAMESPACE:dev}
        group: DEFAULT_GROUP
        enabled: ${NACOS_DISCOVERY_ENABLED:true}
        register-enabled: ${NACOS_REGISTER_ENABLED:true}
```

网关 `application-prod.yml` 的三个开关现均使用上述环境占位符，默认true；`GatewayNacosConfigContractTest`加载真实prod/application/bootstrap YAML并隔离宿主属性源，覆盖八组合及默认，共9项。该契约不启动Spring或Nacos客户端，不证明远端覆盖后的有效值。五业务service的bootstrap默认false，但system-service application及system业务库bootstrap/application另有默认true；须按实际加载资源核验，不将“业务服务默认false”外推到所有配置层。Compose三项默认true、namespace默认public，部分prod文件namespace默认prod，也须明确实际部署取值。

### auth JDBC与R2DBC的TLS核验

[主Compose](../../podman-compose.yml)中auth的 `SPRING_DATASOURCE_URL` 含 `useSSL=false`，按Spring环境绑定覆盖[auth prod配置](../../apex-auth-service/src/main/resources/application-prod.yml) JDBC URL的 `useSSL=true&requireSSL=true`。prod R2DBC URL则由 `DB_HOST`、`DB_PORT`、`DB_NAME` 拼接，设 `sslMode=REQUIRED`；主Compose没有 `SPRING_R2DBC_*` 注入。[JwtKeyRepositoryImpl](../../apex-auth/src/main/java/com/apex/scaffold/auth/infrastructure/repository/JwtKeyRepositoryImpl.java)通过R2DBC `DatabaseClient`读写 `jwt_keys`，该配置存在实际消费链。

这是已确认的静态配置差异，尚未证实生产故障；本轮没有调整TLS策略。部署核验需记录实际运行profile、Compose override及远端/环境/本地配置源、最终DataSource和ConnectionFactory目标与TLS参数，再分别验证JDBC/R2DBC握手及业务访问；配置摘要应脱敏，不输出密码、令牌或密钥。未执行该运行核验前，不能据静态差异声称生产连接失败或双连接配置已一致。

### Nacos Data ID 清单

> 约定：`{service}` = `spring.application.name`，服务名一律 `apex-xxx-service` 形式；
> 配置分组 `DEFAULT_GROUP`（Sentinel 规则为 `SENTINEL_GROUP`）；`{env}` = dev/prod。

| Data ID | 类型 | 引用服务 | 模板文件 |
|---------|------|----------|----------|
| `apex-common.yaml` | 共享 | 全部微服务 | [`config/nacos/apex-common.yaml`](../../config/nacos/apex-common.yaml) |
| `apex-datasource-dev.yaml` | 共享 | 有库服务（dev） | [`config/nacos/apex-datasource-dev.yaml`](../../config/nacos/apex-datasource-dev.yaml) |
| `apex-datasource-prod.yaml` | 共享 | 有库服务（prod） | [`config/nacos/apex-datasource-prod.yaml`](../../config/nacos/apex-datasource-prod.yaml) |
| `apex-gateway.yaml` | 服务私有 | `apex-gateway` | [`config/nacos/apex-gateway.yaml`](../../config/nacos/apex-gateway.yaml) |
| `apex-auth-service.yaml` | 服务私有 | `apex-auth-service` | Phase 2 引入 |
| `apex-system-service.yaml` | 服务私有 | `apex-system-service` | Phase 6 引入 |
| `apex-workflow-service.yaml` | 服务私有 | `apex-workflow-service` | Phase 3 引入 |
| `apex-office-service.yaml` | 服务私有 | `apex-office-service` | Phase 5 引入 |
| `apex-file-service.yaml` | 服务私有 | `apex-file-service` | Phase 4 引入 |
| `{service}-flow-rules` | Sentinel | 各微服务 | [`config/nacos/sentinel/`](../../config/nacos/sentinel/README.md) |
| `{service}-degrade-rules` | Sentinel | 各微服务 | [`config/nacos/sentinel/`](../../config/nacos/sentinel/README.md) |

> 💡 Sentinel 规则推送为**整体替换**语义，发布时必须提交完整规则集；
> 相关配置项见[Sentinel配置](#sentinel配置)的 `apex.sentinel.nacos`。

### 敏感配置约定

Nacos 远端配置**禁止**出现明文密钥。JWT 私钥、数据库口令、Redis 口令等一律经
环境变量 / KMS 注入；相关变量名见[环境变量清单](configuration-inventory.md)。

## 🧩 服务增强配置

任务代理、数据脱敏与 Seata 事务的完整约束、前置条件与运维事项见 [XXL-JOB 接入](xxl-job-integration.md)、[数据脱敏](data-masking.md)、[Seata 接入](seata-integration.md)。此处仅列出 Seata 多模式关键配置项，其余接入细节以集成文档为准。

```yaml
apex:
  seata:
    enabled: ${APEX_SEATA_ENABLED:false}          # 默认关闭；关闭时不初始化 TM/RM、不代理数据源
    mode: ${APEX_SEATA_MODE:AT}                   # AT（默认）/ TCC / SAGA / XA，大小写不敏感，非法值启动失败
    application-id: apex-system-service           # 每个服务不同
    transaction-service-group: ${APEX_SEATA_TX_GROUP:apex_tx_group}   # 须与 TC vgroupMapping 一致
    timeout-millis: 30000
    inbound-path-prefix: /internal/seata/
    propagation-secret: ${APEX_SEATA_PROPAGATION_SECRET}
    allowed-origins: []                            # 空列表不向任何目标传播 XID
    saga:                                          # 仅 SAGA 模式读取，其他模式忽略
      table-prefix: seata_                         # 须与 SAGA 建表脚本表名一致
      state-machine-resources: []                  # classpath 状态机 JSON；空则仅装配引擎
```

| 配置项 | 默认 | 适用模式 | 说明 |
| --- | --- | --- | --- |
| `apex.seata.enabled` | `false` | 全部 | 关闭时无 TM/RM 初始化、无数据源代理、无传播与 Outbox 切面 |
| `apex.seata.mode` | `AT` | 全部 | 事务模式选择；决定数据源代理、必需表结构与事务边界写法 |
| `apex.seata.transaction-service-group` | 空 | 全部 | 事务组，须与 TC `vgroupMapping` 一致 |
| `apex.seata.saga.table-prefix` | `seata_` | 仅 SAGA | SAGA 存储三表前缀 |
| `apex.seata.saga.state-machine-resources` | 空数组 | 仅 SAGA | classpath 状态机 JSON 资源模式；留空只装配引擎 |

`mode` 决定的前置条件：**AT** 需业务库 `undo_log`、单 JDBC 数据源；**TCC** 不包装数据源，业务类实现 `@LocalTCC` 三段动作；**SAGA** 需先执行 starter 发布在 `sql/saga/{mysql,postgresql}/seata_state_machine.sql` 的存储三表；**XA** 需数据库支持 XA 且单 JDBC 数据源，无需 `undo_log`。详见 [Seata 接入](seata-integration.md) 的「事务模式选择」章节。

## 📚 知识库服务配置（apex-knowledge-service，可选运行模块）

知识库为可选运行模块（不进标准发布包，Compose `knowledge` profile），三个能力各自独立开关、按能力级降级；生产密钥一律环境变量注入。完整端点见 [API 路由清单](../03-api/API_ROUTES.md)。

| 配置键 | 环境变量 | 默认 | 说明 |
| --- | --- | --- | --- |
| `server.port` | `KNOWLEDGE_PORT` | 8100 | 业务端口（宿主 8099 被 XXL 控制台占用） |
| `spring.datasource.*` | `KNOWLEDGE_DB_URL` 等 | MySQL `apex_knowledge` | 独立库；Flyway 双目录迁移 |
| `security.config.encryption.key` | `APEX_CONFIG_ENCRYPTION_KEY` | 空（启动失败） | 恰好 32 字节，与认证中心口径一致 |
| `apex.tenant.enabled` | `APEX_TENANT_ENABLED` | true | 多租户行级隔离；kb_* 全表租户过滤 |
| `apex.knowledge.security.jwt-public-keys-base64` | `KNOWLEDGE_JWT_PUBLIC_KEYS_BASE64` | 空 | 独立验签多公钥（`kid=Base64X509` 逗号分隔）；生产推荐配 `apex.jwks.remote-url` |
| `apex.knowledge.security.authorization.*` | `KNOWLEDGE_AUTH_BASE_URL` 等 | 未确认即拒绝 | 授权中心 reader 凭据按租户注入；未配置时受 `@RequirePermission` 保护的端点 fail-closed 拒绝 |
| `apex.knowledge.meilisearch.enabled` | `KNOWLEDGE_MEILISEARCH_ENABLED` | false | 知识分块索引引擎总开关（独立于 system 的 `meilisearch.*`）；关闭时检索空结果、摄取只入库 |
| `apex.knowledge.meilisearch.url/api-key/index-prefix` | `KNOWLEDGE_MEILISEARCH_*` | localhost:7700 / 空 / `apex_` | 索引名 `indexPrefix + kb_chunks`；hybrid 检索走 REST 直调 |
| `apex.knowledge.embedding.enabled` | `KNOWLEDGE_EMBEDDING_ENABLED` | false | 文本向量化；关闭时仅词法索引与检索 |
| `apex.knowledge.embedding.base-url/api-key/model/dimensions` | `KNOWLEDGE_EMBEDDING_*` | 空 / 空 / 空 / 1024 | OpenAI 兼容协议（Dashscope/DeepSeek/Ollama/vLLM）；维度与模型输出不符启动后首调即失败 |
| `apex.knowledge.chat.enabled` | `KNOWLEDGE_CHAT_ENABLED` | false | RAG 问答；关闭时问答端点显式报错，检索不受影响 |
| `apex.knowledge.chat.base-url/api-key/model` | `KNOWLEDGE_CHAT_*` | 空 | OpenAI 兼容协议，SSE 流式 |
| `apex.knowledge.chat.empty-context-strategy` | `KNOWLEDGE_EMPTY_CONTEXT_STRATEGY` | fallback | 检索为空时：fallback=模板兜底回复不调模型；answer=仍调用模型 |
| `apex.knowledge.ingest.chunk-size/chunk-overlap` | `KNOWLEDGE_CHUNK_SIZE/OVERLAP` | 800 / 120 | 分块滑窗参数（overlap 必须小于 chunk-size） |
| `apex.knowledge.ingest.max-upload-size-mb` | `KNOWLEDGE_MAX_UPLOAD_MB` | 50 | 单文件上传上限 |
| `apex.knowledge.storage.bucket` | `KNOWLEDGE_STORAGE_BUCKET` | knowledge | 文档对象桶（复用五后端存储配置 `storage.*`） |
| `apex.knowledge.search.semantic-ratio/top-k` | `KNOWLEDGE_SEMANTIC_RATIO` / `KNOWLEDGE_TOP_K` | 0.6 / 5 | hybrid 语义占比与问答引用条数 |
| `apex.knowledge.reconcile-cron` | `KNOWLEDGE_RECONCILE_CRON` | `0 30 2 * * ?` | 摄取校准 Job（每日 02:30，扫滞留/失败文档） |

网关路由：`knowledge-domain`（`ROUTE_KNOWLEDGE_URI`，默认 `http://127.0.0.1:8100`，可选服务固定地址口径）。菜单与权限种子在 system 域 V91 迁移（权限码 `kb:*`）。

## 📊 监控告警配置

### Prometheus配置

各服务暴露清单不同，以各自 `application.yml` 为准：

| 服务 | management.endpoints.web.exposure.include |
| --- | --- |
| apex-gateway | `health,info,prometheus,gateway` |
| apex-system-service | `health,info,metrics,prometheus,circuitbreakers` |
| apex-auth-service | `health,info,prometheus,circuitbreakers` |
| apex-integration-service | `health,info,metrics,prometheus` |

```yaml
management:
  metrics:
    tags:
      # 应用标签
      application: ${spring.application.name}
```

### Zipkin链路追踪配置

```yaml
management:
  tracing:
    # 采样率（0.0-1.0，默认 0.1，经 TRACE_SAMPLING_RATE 覆盖）
    sampling:
      probability: ${TRACE_SAMPLING_RATE:0.1}
  zipkin:
    tracing:
      # Zipkin 上报端点（Boot 3 起为 management.zipkin.tracing.endpoint，spring.zipkin/spring.sleuth 已废弃）
      endpoint: ${ZIPKIN_BASE_URL:http://localhost:9411}/api/v2/spans
```

### Grafana告警配置

```yaml
monitoring:
  grafana:
    # 是否启用告警
    alert-enabled: false
    
    # Webhook地址
    webhook-url: https://hooks.slack.com/services/YOUR/WEBHOOK/URL
    
    # 告警阈值
    thresholds:
      # CPU使用率(%)
      cpu-usage: 80
      
      # 内存使用率(%)
      memory-usage: 85
      
      # 响应时间P99(毫秒)
      response-time-p99: 2000
      
      # 错误率(%)
      error-rate: 5
```

## 🌍 国际化配置

```yaml
spring:
  messages:
    # 资源文件基础名
    basename: i18n/messages
    
    # 编码格式
    encoding: UTF-8
    
    # 缓存秒数(-1表示永久缓存)
    cache-duration: -1
    
    #  fallback语言
    fallback-to-system-locale: true
```

资源文件位置: `src/main/resources/i18n/`
- `messages.properties` - 默认语言
- `messages_zh_CN.properties` - 简体中文
- `messages_en_US.properties` - 英文

## 📝 API文档配置

```yaml
springdoc:
  # API文档标题
  api-docs:
    title: Apex Scaffold API
    description: 企业级后端脚手架API文档
    version: 1.0.0
    
  # Swagger UI配置
  swagger-ui:
    # 是否启用
    enabled: true
    
    # 路径
    path: /swagger-ui.html
    
    # 默认展开
    operations-sorter: method
    
    # 标签排序
    tags-sorter: alpha
  
  # Knife4j配置
  knife4j:
    # 是否启用
    enable: true
    
    # 基本认证
    basic:
      enable: false
      username: admin
      password: 123456
```

## 🔄 功能开关

功能开关命名空间为 `apex.features.*`（`FeatureProperties`），当前共 6 个键：

```yaml
apex:
  features:
    # JWT认证（默认 true）
    jwt-auth: true

    # RBAC权限（默认 false；启用后管理端按角色授权）
    rbac: false

    # 多级缓存（默认 false）
    cache: false

    # 消息队列（默认 false）
    message-queue: false

    # 监控指标（默认 false）
    monitoring: false

    # 链路追踪（默认 false）
    tracing: false
```

## 🎯 环境特定配置示例

### 开发环境 (application-dev.yml)

```yaml
spring:
  profiles: dev

logging:
  level:
    root: DEBUG
    com.apex: DEBUG

# 关闭不必要的功能提升启动速度
apex:
  features:
    tracing: false
    monitoring: false
```

### 生产环境 (application-prod.yml)

```yaml
spring:
  profiles: prod

logging:
  level:
    root: WARN
    com.apex: INFO

  file:
    name: /var/log/apex/application.log

# 按需启用功能（其余键见上文，默认值均为 false）
apex:
  features:
    tracing: true
    monitoring: true

# 安全加固
jwt:
  secret: ${JWT_SECRET}  # 必须从环境变量读取

spring:
  datasource:
    password: ${DB_PASSWORD}  # 必须从环境变量读取
```

### 日志与审计容量边界

主应用日志配置位于各运行模块的 `src/main/resources/logback-spring.xml`，认证服务配置位于 `apex-auth-service/src/main/resources/logback-spring.xml`。控制台与文件输出使用有界 `AsyncAppender`：每个队列最多 1024 条，剩余容量低于 200 时可丢弃低级别事件，`neverBlock=true` 避免日志输出阻塞请求线程。队列满时 WARN/ERROR 也可能丢失；关闭时最多等待 2000ms 刷新，因此运行日志不能作为可靠审计存储。

### 内部服务调用签名（/internal/v1/**）

集群内契约接口统一要求 HMAC 签名（`apex.internal-auth.*`）：请求需携带 `X-Internal-Service`、
`X-Internal-Timestamp`、`X-Internal-Nonce`、`X-Internal-Signature`（可选 `X-Internal-Tenant`），
签名串为 `service:timestamp:nonce:method:path:tenant`，时间窗默认 60 秒且 nonce 防重放。
共享密钥经 `APEX_INTERNAL_AUTH_SECRET` 注入，全部服务必须一致；启用但未配置密钥时内部
接口一律 503 拒绝。默认豁免 EMQX HTTP 认证/ACL 钩子（设备凭据自行约束），其余豁免须在
`apex.internal-auth.exempt-paths` 显式声明并说明外部调用方。仅本地开发可置
`APEX_INTERNAL_AUTH_ENABLED=false` 关闭。

工作流事件的可靠投递表为 `sys_domain_event_outbox`（V6 成对迁移），与业务写在同事务落箱；`apex.events.outbox.enabled=false`（basic 档默认）时不落箱，仅保留本地通道语义。

主应用 `LoggingFilter` 每个请求只记录完成或异常事件，不输出查询参数、请求体及异常消息；路径最多 256 字符，标识和 IP 最多 64 字符，User-Agent 最多 80 字符，Referer 删除查询与片段后最多 120 字符。`performance` profile 关闭此请求日志。认证配置及审计查询日志使用 DEBUG，查询标识在输出前限长并移除控制字符。

操作审计最多准入 1024 个待完成任务，通过 event 专用调度器写入，并设置 3 秒响应式超时。饱和时立即丢弃，不排队等待；失败和丢弃分别累计在 `OperationLogService.getFailedCount()` 与 `getDroppedCount()`。超时取消订阅并释放名额；服务关闭时取消在途订阅并停止接收新任务，关闭后拒绝的任务计入丢弃数量。底层同步 JDBC 的执行时间仍需通过驱动、数据库与连接池超时控制。

认证中心统一审计按每次订阅读取 Reactor Context 中的 traceId，最多保留 64 字符；没有上下文标识时使用 MDC 或生成标识。统一审计最多准入 256 个任务，高危告警最多准入 64 个任务，均设置 3 秒超时并在完成、取消或失败时释放名额。Redis 审计 Stream 使用 `MAXLEN 100000`，告警 Stream 使用 `MAXLEN 10000`；超出保留窗口的旧事件会被裁剪。失败与丢弃由 `UnifiedAuditService` 对应计数方法提供，当前没有新增监控端点。

审计参数与结果使用安全摘要：全局最多 8192 字符，递归最多 6 层，每个容器最多 32 项；认证审计详情最多 2048 字符。敏感字段及自定义排除字段脱敏，无字段名的字符串省略，循环引用省略，非有限浮点数替换为 null；预算内无法生成合法 JSON 时返回 null。此策略会损失详情，需结合审计保留与可靠性要求评估部署容量。

防重放运行日志不输出 nonce 或请求时间戳原值；拒绝事件按当前切面实例的累计次数，在第 1、2、4、8… 次输出 WARN，日志包含固定原因和累计次数。采样只使用一个原子计数器，不按外部字段维护键集合；每次拒绝仍记录安全指标。该策略不是按时间窗口限速，长期运行时日志会逐渐稀疏，应以安全指标观察持续拒绝。幂等缺键及重复拒绝使用独立原子计数器按相同累计次数采样，逐次安全指标不变，日志不输出路径或请求键；Redis 降级错误仅记录异常类型，不输出异常消息与堆栈，现有 fail-open 行为保持不变。Refresh Token 轮换成功和重放分支运行日志使用 DEBUG，不输出 jti 或 Family 标识；解析失败仅记录异常类型，安全审计仍保留原有事件记录。

IP 限流过滤器的速率超限、已封禁请求及封禁触发分别使用固定原子计数器，按第 1、2、4、8… 次输出 WARN；日志只记录固定原因和累计次数，不输出请求路径或客户端 IP。429 响应与既有速率拒绝、封禁触发指标保留。权限切面不重复记录目标方法抛出的异常，Mono/Flux 保留原异常向上层传播；统一异常处理器的日志策略仍需独立审查。

认证中心文件输出当前使用文本格式，主应用按 profile 配置输出格式；采集规则应与对应应用配置匹配，不能统一假定全部日志为 JSON。

<a id="-阻塞-io-调度器与虚拟线程"></a>

## 🧵 阻塞 I/O 调度器与虚拟线程

`apex.blocking-io` 配置 `BlockingIoSchedulers` 的六个专用阻塞池（`jdbc`、`cache`、`storage`、`event`、`remote`、`general`）。每个池可独立选择平台线程池或虚拟线程执行器，**默认全部关闭虚拟线程**，保持与既有行为一致。

```yaml
apex:
  blocking-io:
    jdbc:
      threads: 32            # 平台线程模式为线程数；虚拟线程模式为默认并发上限
      queue-capacity: 128
      fair: false
      virtual-threads: false # 开启后该池改用虚拟线程执行器
      max-concurrency: 32    # 可选，省略或 0 时沿用 threads；负值启动校验直接失败
```

### 开关语义

| 配置项 | 平台线程模式 | 虚拟线程模式 |
| --- | --- | --- |
| `threads` | 最大工作线程数 | 默认最大并发（`max-concurrency` 未设时生效） |
| `queue-capacity` | 有界队列容量 | 可排队任务上限 |
| `fair` | 公平模式 | 公平模式（信号量） |
| `max-concurrency` | 忽略 | 真正的并发闸门，须与下游稀缺资源对齐 |
| `task-ttl-ms` | 任务 TTL | 仅校验，不生效 |

- 虚拟线程不可池化，`max-concurrency` 才是需要保护的稀缺资源上限。**`jdbc` 池应与数据库连接池最大连接数一致**，`remote` 池应与下游配额一致，不能按 CPU 核数放大。
- 排队超过 `max-concurrency + queue-capacity` 时提交方收到 `RejectedExecutionException`，与平台线程池队列打满的行为一致，不静默堆积。
- 建议按池灰度：`jdbc`、`storage`、`remote`、`general` 是"等待远大于计算"的池，收益最明显；`cache` 池任务典型小于 5ms，一般无需开启。
- 关闭开关后，`BlockingIoSchedulers` 会回收该池残留的虚拟线程执行器，指标不会继续读到旧计数。

### 定时调度

虚拟线程执行器实现了 `ScheduledExecutorService`，因此 `Mono.delay(duration, BlockingIoSchedulers.cache())`、`retryWhen` 和 `Schedulers#schedulePeriodically` 在虚拟线程模式下仍可用：延迟由每个池一条守护线程 `<pool>-timer` 计时，到期后仍交给虚拟线程执行，不占用计时线程。周期任务每次触发都重新走并发闸门，闸门打满时该次触发记录 WARN 并跳过。

### 观测与钉住

`snapshotAll()` 在虚拟线程模式下直接读执行器真实计数，Micrometer Gauge 的并发、排队、完成、拒绝数值不退化。JDK 21 中虚拟线程在 `synchronized` 块内阻塞会钉住（pin）载体线程；怀疑存在钉住时可在启动参数加 `-Djdk.tracePinnedThreads=full` 观测。本项目的阻塞边界已统一收口到 `BlockingIoSchedulers`，切换前仍需确认目标池调用的驱动/客户端不使用 `synchronized` 包裹阻塞 I/O。

## 🩺 体检快照增强（system-service）

`apex.operations.health` 控制运维体检快照的定时留存、保留期清理、签名防篡改与异步导出，仅 `system-service` 使用。四组开关互相独立，任一组设为 `enabled: false` 即关闭对应能力。

```yaml
apex:
  operations:
    health:
      schedule:
        enabled: true                    # 定时采集并留存历史快照
        tenant-id: default               # 快照归属租户（定时任务无请求上下文）
        cron: 0 0 * * * ?                # 默认每小时整点
      retention:
        enabled: true                    # 物理删除过期快照与导出结果
        days: 90                         # 保留天数，< 1 回落到 90
        batch-size: 500                  # 单批删除条数
        max-batches: 20                  # 单次清理最多批次数
        cron: 0 30 3 * * ?               # 默认每天 03:30
      signature:
        enabled: true                    # 关闭后完整性一律为 UNVERIFIABLE
        secret: ""                       # 留空复用 apex.security.data-encryption-key
      async-export:
        enabled: true
        poll-delay-ms: 5000              # 轮询间隔，< 1000 回落到 5000
        tenant-id: default               # 导出任务归属租户
        max-rows: 200000                 # 单任务最大生成行数
        result-ttl-hours: 24             # 结果保留小时数
        max-attempts: 3                  # 单任务最大尝试次数
```

### 运行约束

- **平台租户**：定时任务没有认证上下文，`schedule.tenant-id` 与 `async-export.tenant-id` 必须填写实际业务租户，否则该轮调度直接跳过并记录 WARN。多租户部署时这两个定时能力只服务配置的单一租户。
- **签名密钥**：`signature.secret` 留空时复用 `apex.security.data-encryption-key`。该密钥须跨实例、跨重启稳定保存，轮换会让历史快照的完整性判定变为 `INVALID`；快照实体本身不受影响。
- **异步导出**：同步导出上限固定 5000 行；超过时改用 `POST /api/v2/operations/health-snapshots/export-tasks` 提交后台任务并轮询状态，下载端点返回 CSV 附件。提交阶段即校验规模，超出 `max-rows` 直接拒绝。
- **保留期清理**：使用物理删除回收容量，删除范围仍受租户拦截器限制。导出结果按 `completed_at + result-ttl-hours` 判定过期。

## 📈 性能压测脚本

脚本位置为 `scripts/bench/bench.sh`，依赖 `wrk`。基础用法如下：

```bash
./scripts/bench/bench.sh [base_url] [duration] [threads] [connections]
```

脚本支持以下环境变量：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `BENCH_SCENARIO` | `health` | 执行场景：`health`、`login` 或 `all` |
| `BENCH_LOGIN_ENABLED` | `false` | 是否允许执行登录场景，支持 `true`/`false`、`1`/`0`、`yes`/`no` |
| `BENCH_USERNAME` | 空 | 登录场景使用的真实用户名 |
| `BENCH_PASSWORD` | 空 | 登录场景使用的真实密码 |

示例：

```bash
BENCH_SCENARIO=health ./scripts/bench/bench.sh http://127.0.0.1:8080 30s 4 200
BENCH_SCENARIO=login BENCH_LOGIN_ENABLED=true BENCH_USERNAME=real-user BENCH_PASSWORD='real-password' ./scripts/bench/bench.sh
BENCH_SCENARIO=all BENCH_LOGIN_ENABLED=true BENCH_USERNAME=real-user BENCH_PASSWORD='real-password' ./scripts/bench/bench.sh
```

安全要求：默认只执行健康检查，不会使用占位密码执行登录压测。登录场景必须显式设置 `BENCH_LOGIN_ENABLED=true`，并通过环境变量提供真实账号和密码；不要把密码写入脚本、文档、命令历史或提交到版本库。完整脚本说明和失败排查见 `docs/sales/PERFORMANCE_REPORT.md`。

### 虚拟线程压测脚本

`scripts/bench/bench-vt.sh` 用于验证 [阻塞 I/O 调度器与虚拟线程](#-阻塞-io-调度器与虚拟线程)的开关效果：同一接口分别以平台线程与虚拟线程模式压测，输出吞吐/延迟对比，并采集池指标与 JFR `jdk.VirtualThreadStart` 事件证明开关确实生效。脚本只通过 JVM 参数开关，不修改任何配置文件。

```bash
# 对比模式（默认）：依次启动两个进程，先平台线程后虚拟线程
./scripts/bench/bench-vt.sh

# 只检查已在运行的服务，不启停进程
BENCH_MODE=report BENCH_PID=<pid> ./scripts/bench/bench-vt.sh

# 只跑其中一轮
BENCH_MODE=baseline ./scripts/bench/bench-vt.sh
BENCH_MODE=virtual  ./scripts/bench/bench-vt.sh
```

脚本对每个池注入的参数如下，两轮使用相同并发上限，只有线程模型不同：

| 模式 | 注入参数 |
| --- | --- |
| `baseline` | `-Dapex.blocking-io.<pool>.threads=<并发上限>` |
| `virtual` | `-Dapex.blocking-io.<pool>.virtual-threads=true`、`-Dapex.blocking-io.<pool>.max-concurrency=<并发上限>` |

必须使用 `apex.blocking-io.<pool>.*` 前缀：`BlockingIoSchedulers` 会被 Spring 绑定后的 `ApexBlockingIoProperties` 覆盖一次，仅传 `-Dapex.<pool>.virtual-threads=true` 会被 `application.yml` 的默认 `false` 覆盖。

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `BENCH_MODE` | `compare` | `report`、`baseline`、`virtual` 或 `compare` |
| `BENCH_BASE_URL` | `http://127.0.0.1:8082` | system-service 业务地址 |
| `BENCH_PATH` | `/api/v1/health` | 压测路径 |
| `BENCH_ADMIN_URL` | `http://127.0.0.1:8083` | 管理端口，用于读取 `/actuator/prometheus` |
| `BENCH_DURATION` | `30s` | wrk 时长，最小 10 秒，支持 `30s`/`1m`/纯秒数 |
| `BENCH_THREADS` | `4` | wrk 线程数 |
| `BENCH_CONNECTIONS` | `200` | wrk 并发连接数 |
| `BENCH_POOLS` | `event,cache` | 参与注入与采样的池，逗号分隔 |
| `BENCH_CONCURRENCY` | `16` | 每池并发上限；`BENCH_CONCURRENCY_<POOL>`（大写）可单独覆盖 |
| `BENCH_SAMPLE_GUARD` | `true` | 关闭 IP 限流与 Sentinel 系统规则，避免 429/503 污染结果 |
| `BENCH_TRACE_PINNED` | `false` | 为 `true` 时加 `-Djdk.tracePinnedThreads=full` 观察载体线程钉住 |
| `BENCH_JAVA_OPTS` | 空 | 追加的 JVM 参数 |
| `BENCH_JAR` | 自动挑选 | 默认取 `apex-system-service/target/apex-system-service-*.jar` |
| `BENCH_PID` | 空 | `report` 模式下采集既有进程的池指标与 JFR 虚拟线程启动事件 |
| `BENCH_OUTPUT_DIR` | `target/bench-vt/<时间戳>` | 产物目录 |

前置条件与限制：

- 需先打包可执行 JAR：`mvnd -T4 -B -pl apex-system-service -am package`；启动时 MySQL 与 Redis 必须可用，业务端口与管理端口不能被占用。
- `/api/v1/health` 实际只打到 `cache` 与 `event` 池（Redisson 查询、IP 限流与操作日志写入），`jdbc`、`storage`、`remote` 需换成有相应流量的业务接口才有意义；报告会把"采样窗口内完成数为 0"的池标注为不适用于该结论。
- 判定虚拟线程生效看两点：该池在压测窗口内出现了 `jdk.VirtualThreadStart` 事件（线程名 `apex.<pool>-vt-*`，平台线程模式为 0），且 `apex_blocking_io_max_threads` 等于配置的 `max-concurrency`。`jcmd Thread.dump_to_file -format=text` 的文本格式不含虚拟线程栈（仅有总数汇总），不能用来数虚拟线程，因此证据取自 JFR。
- 产物目录包含 `results.tsv`、`summary.md`、`wrk-<mode>.txt`、`prom-<mode>-{pre,mid}.txt`、`vt-start-<mode>.jfr`、`threads-<mode>.txt`（平台线程 Dump）、`jvm-args-<mode>.txt` 与 `service-<mode>.log`。

本地以 `dev` 配置启动 system-service 做压测时，脚本以默认参数起进程会遇到以下阻断点；把对应开关放进 `BENCH_JAVA_OPTS` 即可（`KEYCLOAK_ENABLED` 由环境变量传入）：

| 阻断现象 | 需要的开关 |
| --- | --- |
| `Failed to determine a suitable R2DBC Connection URL` | `-Dspring.autoconfigure.exclude=org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration,org.springframework.boot.autoconfigure.data.r2dbc.R2dbcDataAutoConfiguration` |
| `必须配置配置加密密钥` | `-Dsecurity.config.encryption.key=<32 字节原文>` |
| `必须配置数据加密密钥` | `-Dapex.security.data-encryption-key=<Base64 32 字节>` |
| `必须配置Token加密密钥` | `-Dsecurity.encryption.key=<Base64 32 字节>` |
| `MailService required a bean of type 'JavaMailSender'` | `-Dspring.mail.host=localhost` |
| dev 默认关闭 Flyway，空库缺表 | `-Dspring.flyway.enabled=true` |
| `keycloak.enabled` 默认 `true` | 环境变量 `KEYCLOAK_ENABLED=false` |

依赖可用容器准备（与 `podman-compose.infra.yml` 的默认值一致）：

```bash
docker run -d --name apex-bench-mysql -e MYSQL_ROOT_PASSWORD=apex-root -e MYSQL_DATABASE=apex_system -p 3306:3306 mysql:9.0
docker run -d --name apex-bench-redis -p 6379:6379 redis:7-alpine
```

完整示例（16 并发上限、30 秒、200 连接，双轮对比）：

```bash
mvnd -T4 -B -pl apex-system-service -am package
KEYCLOAK_ENABLED=false BENCH_POOLS="event,cache" BENCH_CONCURRENCY=16 \
BENCH_JAVA_OPTS="-Dspring.flyway.enabled=true -Dspring.datasource.dynamic.p6spy=false \
  -Dspring.autoconfigure.exclude=org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration,org.springframework.boot.autoconfigure.data.r2dbc.R2dbcDataAutoConfiguration \
  -Dapex.security.data-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA= \
  -Dsecurity.config.encryption.key=0123456789abcdef0123456789abcdef \
  -Dsecurity.encryption.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA= \
  -Dspring.mail.host=localhost" \
./scripts/bench/bench-vt.sh
```

上述密钥只用于本地压测进程，不要在共享或生产环境复用。

## ❓ 常见问题

### Q1: 如何覆盖默认配置?

优先级从高到低:
1. 命令行参数: `--server.port=8081`
2. 环境变量: `SERVER_PORT=8081`
3. `application-{profile}.yml`
4. `application.yml`

### Q2: 配置不生效怎么办?

1. 检查 `spring.profiles.active` 是否正确
2. 查看启动日志确认加载的配置文件
3. 使用 Actuator `/actuator/env` 端点查看当前生效配置

### Q3: 如何加密敏感配置?

项目不依赖 Jasypt，使用自研 `ConfigEncryptionService`（`apex-common/src/main/java/com/apex/scaffold/common/security/ConfigEncryptionService.java`）做配置字段的 AES-256-GCM 加解密，密钥通过 `security.config.encryption.key` 注入：

```yaml
security:
  config:
    encryption:
      # 由环境变量或密钥管理服务注入，禁止写入版本库
      key: ${APEX_CONFIG_ENCRYPTION_KEY}
```

密文格式为裸 `Base64(12 字节 IV + AES-256-GCM 密文 + 16 字节标签)`，不是 Jasypt 的 `ENC(...)` 包装。已用于持久数据的密钥必须稳定保存，轮换需配合迁移（参见 `apex-tools-deployment` 的 TOTP 敏感字段迁移能力）。

数据库口令、JWT 密钥、外部凭据等仍应优先通过部署环境变量注入（见 [配置文档 §密钥与环境变量](../05-operations/configuration.md) 与 `.env.example`），配置加密仅用于确需落库的字段。

---

**提示**: 生产环境部署前,请务必审查所有配置项,特别是安全相关的配置!

## 2026-10-19 auth双客户端绑定核验补充

本节补充前述auth TLS静态记录，保留其历史口径。[主Compose](../../podman-compose.yml)的公共环境锚点已注入 `DB_HOST=mysql`、`DB_PORT=3306`、`DB_USERNAME`、`DB_PASSWORD`，auth合并后另设 `DB_NAME=apex_auth`。没有显式 `SPRING_R2DBC_*` 不代表R2DBC目标错误：真实YAML合并与Spring Binder的离线契约已验证双客户端均指向 `mysql:3306/apex_auth`，用户名及凭据一致。JDBC URL仍由Compose覆盖为 `useSSL=false`，R2DBC仍从prod配置绑定 `sslMode=REQUIRED`；这一TLS静态差异保留，没有修改配置或证实生产故障。

[AuthDataSourceConfigContractTest](../../apex-auth-service/src/test/java/com/apex/scaffold/authservice/AuthDataSourceConfigContractTest.java)使用SnakeYAML解析真实Compose合并锚点，移除宿主系统属性与环境源，再通过独立 `SystemEnvironmentPropertySource`、真实prod/application/bootstrap和Spring Binder验证7项；现存Surefire 7项加驱动3项共10项通过，无失败/错误/跳过。JDK固定17.0.12-oracle，mvnd2.0.0-rc-3及相关Spotless通过按本轮交付记录；本次文档收口只读复核Surefire和源码，未重跑Java或连接数据库。

| 输入情况 | 已验证的属性绑定结果 | 解释边界 |
| --- | --- | --- |
| 当前Compose合并公共DB变量 | JDBC/R2DBC目标、用户及凭据一致，TLS策略不同 | 不证明TLS握手、认证或业务访问成功 |
| 直接prod提供DB定位及凭据变量 | 两客户端读取共同DB目标；JDBC启用并要求SSL，R2DBC REQUIRED | 不包含远端属性源覆盖 |
| 缺少DB定位变量、保留凭据 | 默认localhost:3306/apex_auth，绑定不失败 | 不证明默认目标可用或应用启动 |
| 缺少凭据 | Binder保留 `${DB_USERNAME}` / `${DB_PASSWORD}`；strict required属性解析抛IllegalArgumentException | 不能由Binder成功推断已满足认证，也不能据此断言完整启动路径的失败时点 |
| 仅保留SPRING_DATASOURCE变量、移除公共DB变量 | JDBC仍为mysql，R2DBC回到localhost且凭据未解析 | 这是隔离负向输入，不是当前Compose事实 |
| 显式SPRING_R2DBC_URL/USERNAME/PASSWORD | 覆盖prod的DB变量派生值 | 部署应另核对覆盖后的双客户端目标和TLS |

仍需核验实际profile、Compose override、Nacos远端配置及其优先级、最终DataSource/ConnectionFactory与真实双客户端握手；Flyway历史/checksum未由绑定测试验证。[本轮报告](../06-testing/jdk21-unified-auth/20261016-p0/report.md)另记录PG17.11的72场景行为证据，旧 `verified_union()` 独立复算仍500/586、剩86；PG剩16项行为验证不改变旧三分支计分。未修改配置、原迁移或旧证据，P0仍未准出。

## 2026-10-19 auth真实TLS握手补充

继上述离线绑定核验后，[AuthMySqlTlsContractIntegrationTest](../../apex-auth-service/src/test/java/com/apex/scaffold/authservice/AuthMySqlTlsContractIntegrationTest.java)已使用真实Compose/prod属性及隔离MySQL8.0.46执行双驱动连接；仅替换隔离目标。Connector/J8.4.0、r2dbc-mysql1.4.2下，最终7项配置契约及3项TLS场景通过，失败、错误和跳过均0，auth-service Spotless通过。详见[保存证据](../06-testing/jdk21-unified-auth/20261016-p0/auth-mysql-tls-contract-20261019-2336-evidence.json)及[阶段报告](../06-testing/jdk21-unified-auth/20261016-p0/report.md)。

| 隔离服务端条件 | 当前Compose JDBC useSSL=false | prod JDBC useSSL=true/requireSSL=true | prod R2DBC sslMode=REQUIRED |
| --- | --- | --- | --- |
| TLS可用 | 明文连接成功 | 加密连接成功 | 加密连接成功 |
| TLS关闭 | 明文连接成功 | 因SSL要求失败 | 因SSL要求失败 |
| require_secure_transport=ON | 被insecure transport拒绝 | 加密连接成功 | 加密连接成功 |

成功连接均在同一会话确认隔离数据库名和Ssl_cipher/Ssl_version状态：明文两值为空，加密两值非空。未保存具体协商字符串。服务端要求安全传输时，当前Compose JDBC明文覆盖与该要求不兼容；本次保留现有配置，未据隔离测试断言生产故障。部署需核对实际服务端策略及最终覆盖后的双客户端TLS参数。

当前要求SSL的参数证明传输加密，不证明CA信任链或主机名身份校验，未验证VERIFY_IDENTITY。真实profile、Compose override、Nacos远端加载及优先级、完整应用启动与业务访问仍待核验；Flyway历史/checksum也不在握手测试范围。测试容器及网络已清理，镜像标签恢复。未修改生产配置，未执行全仓clean verify，P0仍未准出。

## 2026-10-19 Compose多文件覆盖契约补充

真实主Compose与临时override在podman-compose1.6.0的config渲染中已验证：仅覆盖auth DB_HOST/DB_NAME不会重写原显式SPRING_DATASOURCE_URL，JDBC仍指向mysql:3306/apex_auth，而R2DBC可由DB变量派生其他目标。需要同时核对最终JDBC/R2DBC URL与公共DB变量；显式覆盖双URL的合成测试可使目标对齐，但测试URL不含TLS策略，不应复制为生产模板。合并后公共凭据、安全变量、Redis/Nacos锚点保留，system DB目标不受auth覆盖影响。被测四个DB变量的同名非空宿主值优先于env文件。

[现行 Java 等价测试](../../apex-tools-deployment/src/test/java/com/apex/scaffold/tools/deployment/ConfigPreflightTest.java)保留该4子场景覆盖。2026-10-20 Java/Shell 迁移后，独立离线工具的三组测试共27项通过，失败、错误和跳过均为0。仅使用临时合成输入及config渲染，未读取真实.env、启动服务或连接数据库。现有compose_check预检CLI仍只处理单主文件，不能据本次测试推断它已覆盖实际部署的多文件输入。

旧Nacos链证据的gateway prod字面true描述已过时，当前为环境占位符；app.sh环境文件加载当前在62–66行。collector采集的shared_nacos/DB_HOST_PROD仍静态未知，远端Data ID、namespace/group/revision、实际加载及优先级没有运行证据。详见[本轮证据](../06-testing/jdk21-unified-auth/20261016-p0/compose-override-coverage-audit-20261019-evidence.json)。

## 2026-10-20 Spring Binder覆盖契约补充

[auth配置契约测试](../../apex-auth-service/src/test/java/com/apex/scaffold/authservice/AuthDataSourceConfigContractTest.java)新增两项覆盖场景，总计9项通过，无失败、错误或跳过，Spotless检查通过。测试解析真实Compose合并后的环境变量，并使用真实application、prod及bootstrap配置进行隔离的Spring Binder绑定：仅覆盖DB_HOST/DB_NAME时，显式JDBC URL仍指向mysql:3306/apex_auth并保留useSSL=false，R2DBC则派生至覆盖目标并保留sslMode=REQUIRED；显式覆盖双URL时，两客户端目标对齐，合成凭据及JDBC的useSSL=true、requireSSL=true和R2DBC的sslMode=REQUIRED均保留。

本轮仅验证离线配置绑定，未连接数据库、执行TLS握手、访问Nacos或启动应用；远端配置实际加载及优先级仍未知。生产部署仍须核对最终双URL、凭据与TLS策略。完整命令、失败尝试和结果见[绑定证据](../06-testing/jdk21-unified-auth/20261016-p0/auth-compose-binder-override-20261020-evidence.json)。
