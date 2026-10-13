# 配置项详解

[查看配置层级图](../diagrams/46_configuration_hierarchy.svg)

本文档详细说明了 Apex Scaffold 的所有可配置项,帮助你根据实际需求定制系统行为。

## 📁 配置文件位置

主配置文件: `apex-interfaces/src/main/resources/application.yml`

环境特定配置:
- `application-dev.yml` - 开发环境
- `application-test.yml` - 测试环境
- `application-prod.yml` - 生产环境

通过 `spring.profiles.active` 激活对应环境:
```bash
mvnd spring-boot:run -Dspring-boot.run.profiles=prod
```

## 🔐 安全认证配置

### 凭据加密密钥

数据源密码和认证中心 TOTP 密钥、备份码及已消费备份码使用 `apex.security.data-encryption-key`，由 `APEX_DATA_ENCRYPTION_KEY` 注入，值为 Base64 编码的 32 字节随机密钥，可用 `openssl rand -base64 32` 生成。必须跨实例和重启保持一致；缺失、非法 Base64 或长度错误会拒绝初始化。原先由临时随机密钥加密的数据需要使用原密钥解密后重新加密，无法通过新密钥恢复。

TOTP 仓储只读取加密格式，不会在解密失败时回退为明文。升级前需备份 `auth_totp`，使用部署的稳定数据密钥将历史明文 `secret`、`backup_codes`、`used_backup_codes` 转换为当前 AES-GCM 格式，或安排用户重新绑定 MFA；可用 `scripts/utils/local_deployment.py prepare` 离线生成候选快照、备份和审查报告；工具不连接数据库，不自动执行迁移。更换密钥前也必须先完成旧密钥到新密钥的数据迁移。

公共配置加密使用 `security.config.encryption.key`（`CONFIG_ENCRYPTION_KEY`），要求 UTF-8 编码后恰好 32 字节且不为全空白。认证中心独立配置加密使用 `apex.security.config-encryption-key`（`APEX_CONFIG_ENCRYPTION_KEY`），要求 Base64 解码后恰好 32 字节。两者格式不同，不能直接共用同一个字符串；均不再提供默认或临时密钥。

Token 存储加密使用 `security.encryption.key`（环境变量 `SECURITY_ENCRYPTION_KEY`）；认证中心也支持 `security.token.encryption.key`（`TOKEN_ENCRYPTION_KEY`），前者优先。密钥必须为 Base64 编码的 32 字节，跨实例和重启保持一致；缺失或长度错误会拒绝启动，不再生成临时密钥。旧临时密钥加密的 Token 需要保留原密钥迁移，否则必须重新登录。

Compose 强制注入 `APEX_DATA_ENCRYPTION_KEY`、`SECURITY_ENCRYPTION_KEY`、`CONFIG_ENCRYPTION_KEY`、`APEX_CONFIG_ENCRYPTION_KEY` 和 `JWT_SECRET`，缺失或为空时拒绝渲染。一次性 `security-key-check` 服务使用 Python 3.13 镜像校验格式，成功后才允许启动应用；四类加密密钥必须独立生成并安全保存，不应每次启动重新生成。三个 Base64 密钥可分别用 `openssl rand -base64 32` 生成；公共配置密钥可用 `openssl rand -hex 16` 生成（结果是 32 字节 ASCII）。`.env.example` 仅保留空值。环境变量仍可被具有 Podman 管理权限的用户查看。

安全检查命令（捕获渲染结果，不把展开后的密钥打印到终端）：

```bash
python3 scripts/utils/local_deployment.py compose --env-file .env
```

离线 TOTP 迁移准备与回滚：

```bash
python3 -m venv /tmp/apex-totp-tools
/tmp/apex-totp-tools/bin/pip install -r scripts/utils/local-deployment-requirements.txt
/tmp/apex-totp-tools/bin/python scripts/utils/local_deployment.py prepare --input /secure/auth_totp.json --output /secure/totp-review
/tmp/apex-totp-tools/bin/python scripts/utils/local_deployment.py rollback --bundle /secure/totp-review --output /secure/auth_totp-restored.json
```

运行前通过安全环境注入 `APEX_DATA_ENCRYPTION_KEY`；如需轮换，另注入 `TOTP_OLD_DATA_ENCRYPTION_KEY`，不将值写进命令行。输入为完整 `auth_totp` 行的 JSON 数组，必须包含 `id`、`tenant_id`、`version`、`secret`、`backup_codes`、`used_backup_codes`，包括逻辑删除行。工具只转换后三个敏感字段，保留其他字段。历史明文只接受当前生成器的 32 字符大写 Base32 secret 及逗号分隔的 8 位数字备份码；允许空备份码和 NULL 备份码。当前密文为裸 `Base64(12 字节 IV + AES-256-GCM 密文 + 16 字节标签)`，不是 `ENC(...)`；必须认证解密成功且明文格式正确才能跳过。旧密钥密文在提供原密钥时重加密，未知格式、损坏密文、身份重复或错误密钥会阻止生成迁移包。

输出目录权限为 0700、文件为 0600，包含原始字节备份 `backup.json`、候选 `candidate.json` 与不含字段值的 `review.json`。备份可能含明文，仍须放在受控加密磁盘，不能入库。重复处理候选数据不产生二次加密；拒绝覆盖已有输出。离线回滚同时核对备份和候选 SHA-256 后恢复原始快照，摘要用于检测意外改动，不代替可信签名。输出或磁盘写入失败可能留下不完整目录，应检查后选择新的输出目录。

实际数据库应用须另行审查、停写、完整备份并在隔离副本演练；按 `tenant_id`、`id`、`version` 及旧字段值做事务内比较更新，只替换三个字段，任何冲突整批回滚。恢复写入前验证 MFA。数据库回滚同样需要停写并检查迁移后是否发生业务修改；本工具的 rollback 只恢复离线快照，不能声称已回滚数据库。丢失原随机密钥的数据不能恢复，需重新绑定 MFA；本工具不会连接或修改现有数据库。


部署配置生成仅输出密码环境变量占位符；环境变量模板中的 `DB_PASSWORD` 留空，由部署者在安全配置中填写。

### JWT配置

```yaml
jwt:
  # JWT签名密钥(生产环境必须修改为强随机字符串,至少32字符)
  secret: ${JWT_SECRET:change-this-in-production-min-32-chars}

  # Access Token过期时间(毫秒),默认2小时
  access-token-expire: 7200000

  # Refresh Token过期时间(毫秒),默认7天
  refresh-token-expire: 604800000

  # JWT密钥最小长度
  min-secret-length: 32
  
  # Token请求头名称
  header: Authorization
  
  # Token前缀
  prefix: "Bearer "
```

**安全建议**:
- ⚠️ 生产环境务必通过环境变量设置 `JWT_SECRET`
- 使用强随机字符串生成器: `openssl rand -base64 48`
- 定期轮换密钥

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
          # 数据库URL（默认库 apex_data；dev 默认 root/apex-root，prod 必须环境变量注入）
          url: jdbc:mysql://127.0.0.1:3306/apex_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
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
          url: jdbc:mysql://127.0.0.1:3306/apex_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
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

### Sentinel配置

**控制台通信（spring.cloud.sentinel）**：

```yaml
spring:
  cloud:
    sentinel:
      # 是否启用 Sentinel 客户端
      enabled: true
      # 控制台地址（空 = 不连接控制台，仅本地规则生效）
      transport:
        dashboard: localhost:8858
        port: 8719
      # 启动即初始化客户端，避免首请求懒加载延迟
      eager: true
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
    default:
      failure-rate-threshold: 50
      wait-duration-in-open-state: 60s
      sliding-window-size: 100
      minimum-number-of-calls: 10
      permitted-calls-in-half-open: 5
```

`resilience4j.retry`、`resilience4j.timelimiter` 和 `resilience4j.ratelimiter` 即使存在于配置中，也不会自动作用于当前 `RemoteApiClient` 调用链；当前远程重试和超时分别由 Reactor Retry 与 `remote.api` 参数控制。

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
  
  # 消费者配置
  consumer:
    # 消费者组名
    group: apex-consumer-group
    
    # 每次拉取消息数量
    pull-batch-size: 32
    
    # 消费线程数
    consume-thread-min: 10
    consume-thread-max: 20

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

```yaml
storage:
  # 当前使用的存储类型(LOCAL/MINIO/S3/VOLCENGINE_TOS)
  default-type: LOCAL
  
  # 本地存储配置
  local:
    # 存储根路径
    base-path: /data/uploads
    
    # 访问URL前缀
    url-prefix: /uploads
  
  # MinIO配置
  minio:
    # 服务端点
    endpoint: http://localhost:9000
    
    # Access Key
    access-key: ${MINIO_ACCESS_KEY:minioadmin}
    
    # Secret Key
    secret-key: ${MINIO_SECRET_KEY:minioadmin}
    
    # Bucket名称
    bucket: apex-files
    
    # 是否启用HTTPS
    secure: false
  
  # AWS S3配置
  s3:
    # 区域
    region: cn-north-1
    
    # Access Key
    access-key: ${AWS_ACCESS_KEY:}
    
    # Secret Key
    secret-key: ${AWS_SECRET_KEY:}
    
    # Bucket名称
    bucket: your-bucket-name
    
    # CDN域名(可选)
    cdn-domain: https://cdn.example.com
```

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

优先级：**环境变量 > Nacos 远端配置 > 本地文件默认值**。无 Nacos 部署时通过
`NACOS_CONFIG_ENABLED=false` 关闭配置中心，应用回退到本地 `application.yml`，行为与单体一致。

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

> ⚠️ 网关（`apex-gateway`）作为流量入口，`NACOS_CONFIG_ENABLED`/`NACOS_DISCOVERY_ENABLED`/
> `NACOS_REGISTER_ENABLED` 默认 **true**；业务服务默认 **false**，避免本地未部署 Nacos 时启动失败。

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

## 📊 监控告警配置

### Prometheus配置

```yaml
management:
  endpoints:
    web:
      exposure:
        # 暴露的端点
        include: health,info,prometheus,metrics
    
  metrics:
    export:
      prometheus:
        # 是否启用
        enabled: true
    
    tags:
      # 应用标签
      application: ${spring.application.name}
```

### Zipkin链路追踪配置

```yaml
spring:
  zipkin:
    # Zipkin服务器地址
    base-url: http://localhost:9411
    
    # 采样率(0.0-1.0)
    sampler:
      probability: 1.0
  
  sleuth:
    # 采样率
    sampler:
      probability: 1.0
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

通过以下配置可以按需启用/禁用功能模块:

```yaml
features:
  # JWT认证
  jwt-auth: true
  
  # RBAC权限
  rbac: true
  
  # 多级缓存
  multi-level-cache: true
  
  # 限流熔断
  rate-limit: true
  
  # 消息队列
  message-queue: false  # 不需要时关闭以简化部署
  
  # 链路追踪
  tracing: false  # 开发环境可关闭提升性能
  
  # 操作日志
  operation-log: true
  
  # 监控指标
  monitoring: true
  
  # API文档
  api-docs: true
  
  # 国际化
  i18n: true
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

# 启用所有功能
features:
  tracing: true
  monitoring: true
  alert-enabled: true

# 安全加固
jwt:
  secret: ${JWT_SECRET}  # 必须从环境变量读取

spring:
  datasource:
    password: ${DB_PASSWORD}  # 必须从环境变量读取
```

### 日志与审计容量边界

主应用配置位于 `apex-interfaces/src/main/resources/logback-spring.xml`，认证中心配置位于 `apex-auth-center/src/main/resources/logback-spring.xml`。控制台与文件输出使用有界 `AsyncAppender`：每个队列最多 1024 条，剩余容量低于 200 时可丢弃低级别事件，`neverBlock=true` 避免日志输出阻塞请求线程。队列满时 WARN/ERROR 也可能丢失；关闭时最多等待 2000ms 刷新，因此运行日志不能作为可靠审计存储。

主应用 `LoggingFilter` 每个请求只记录完成或异常事件，不输出查询参数、请求体及异常消息；路径最多 256 字符，标识和 IP 最多 64 字符，User-Agent 最多 80 字符，Referer 删除查询与片段后最多 120 字符。`performance` profile 关闭此请求日志。认证配置及审计查询日志使用 DEBUG，查询标识在输出前限长并移除控制字符。

操作审计最多准入 1024 个待完成任务，通过 event 专用调度器写入，并设置 3 秒响应式超时。饱和时立即丢弃，不排队等待；失败和丢弃分别累计在 `OperationLogService.getFailedCount()` 与 `getDroppedCount()`。超时取消订阅并释放名额；服务关闭时取消在途订阅并停止接收新任务，关闭后拒绝的任务计入丢弃数量。底层同步 JDBC 的执行时间仍需通过驱动、数据库与连接池超时控制。

认证中心统一审计按每次订阅读取 Reactor Context 中的 traceId，最多保留 64 字符；没有上下文标识时使用 MDC 或生成标识。统一审计最多准入 256 个任务，高危告警最多准入 64 个任务，均设置 3 秒超时并在完成、取消或失败时释放名额。Redis 审计 Stream 使用 `MAXLEN 100000`，告警 Stream 使用 `MAXLEN 10000`；超出保留窗口的旧事件会被裁剪。失败与丢弃由 `UnifiedAuditService` 对应计数方法提供，当前没有新增监控端点。

审计参数与结果使用安全摘要：全局最多 8192 字符，递归最多 6 层，每个容器最多 32 项；认证审计详情最多 2048 字符。敏感字段及自定义排除字段脱敏，无字段名的字符串省略，循环引用省略，非有限浮点数替换为 null；预算内无法生成合法 JSON 时返回 null。此策略会损失详情，需结合审计保留与可靠性要求评估部署容量。

防重放运行日志不输出 nonce 或请求时间戳原值；拒绝事件按当前切面实例的累计次数，在第 1、2、4、8… 次输出 WARN，日志包含固定原因和累计次数。采样只使用一个原子计数器，不按外部字段维护键集合；每次拒绝仍记录安全指标。该策略不是按时间窗口限速，长期运行时日志会逐渐稀疏，应以安全指标观察持续拒绝。幂等缺键及重复拒绝使用独立原子计数器按相同累计次数采样，逐次安全指标不变，日志不输出路径或请求键；Redis 降级错误仅记录异常类型，不输出异常消息与堆栈，现有 fail-open 行为保持不变。Refresh Token 轮换成功和重放分支运行日志使用 DEBUG，不输出 jti 或 Family 标识；解析失败仅记录异常类型，安全审计仍保留原有事件记录。

IP 限流过滤器的速率超限、已封禁请求及封禁触发分别使用固定原子计数器，按第 1、2、4、8… 次输出 WARN；日志只记录固定原因和累计次数，不输出请求路径或客户端 IP。429 响应与既有速率拒绝、封禁触发指标保留。权限切面不重复记录目标方法抛出的异常，Mono/Flux 保留原异常向上层传播；统一异常处理器的日志策略仍需独立审查。

认证中心文件输出当前使用文本格式，主应用按 profile 配置输出格式；采集规则应与对应应用配置匹配，不能统一假定全部日志为 JSON。

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

推荐使用 Jasypt:

```yaml
# 添加依赖
# <dependency>
#   <groupId>com.github.ulisesbocchio</groupId>
#   <artifactId>jasypt-spring-boot-starter</artifactId>
# </dependency>

# 配置加密
jasypt:
  encryptor:
    password: ${JASYPT_ENCRYPTOR_PASSWORD}

# 使用加密值
spring:
  datasource:
    password: ENC(encrypted_value_here)
```

---

**提示**: 生产环境部署前,请务必审查所有配置项,特别是安全相关的配置!
