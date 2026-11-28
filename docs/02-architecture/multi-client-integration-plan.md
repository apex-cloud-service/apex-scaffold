# 多端对接调整方案（PC / 小程序 / 公众号 / App · 统一账户体系）

> **历史快照**：本文为 2026-10-06 时点的方案与实施基线（批次 1–6 已实施完成）；此后仓库经历第二阶段模块物理嵌套等演进，模块路径、计数与端口以当前代码为准。
>
> 状态：批次 1–6 已全部实施完成（2026-10-06 评审通过；四端联调验收见 docs/05-operations/multi-client-acceptance-checklist.md）
> 范围：本仓库后端改造 + 四端前端对接契约（管理后台为独立前端仓库）
> 图表索引：第三节 = 系统 / 对接 / 集成 / 部署四张架构视图 + 统一账户 ER 图；4.2 客户端标识判定流程；4.3 多会话令牌时序；4.4 账户归一流程 + 小程序 / 公众号登录时序；4.5 PC 管理端信任链时序；第六节 = 前端对接总流程 + App 登录时序 + 无感续期流程

## 一、结论

当前项目**部分支持**多端对接。App 链路成熟（微信开放平台 + 创蓝一键登录 + 短信 + 密码 + 图形验证码），PC 可密码登录；但存在以下结构性缺口，需分 6 批改造：

| # | 缺口 | 证据 |
|---|------|------|
| 1 | 小程序登录缺失（无 jscode2session） | `application-prod.yml` 微信配置注释明确"非小程序 jscode2session 接口" |
| 2 | 公众号网页授权未接入登录 | `WeChatOAuthService` 无任何调用方 |
| 3 | `sys_user` 单列 `open_id` + 唯一索引，同一用户多端 openid 无法共存 | 主库 `V1__init_schema.sql`（sys_user） |
| 4 | 令牌每用户单槽（`token:access:{subject}`），多端同时在线互顶 | `TokenServiceImpl` |
| 5 | 令牌无终端标识、账户无来源字段 | JWT claims 仅 subject+deviceId；sys_user 无 register_channel |
| 6 | CORS 请求头白名单缺 `X-Tenant-Id` / `Idempotency-Key` / `X-Client-Type` | `CorsConfig` |

**已具备**：统一 `Result<T>` 契约 + traceId、i18n 四语言、六种登录方式（WX_CODE/WX_PHONE/WX_SMS/CL_PHONE/CL_SMS/PASSWORD）、双令牌 + 设备指纹校验 + 滑动续期（`X-New-Access-Token`）、令牌黑名单、IP 限流 + Sentinel、防重放、租户上下文、认证中心 OAuth2 Token Endpoint（authorization_code/password/client_credentials/refresh_token）与按租户动态客户端注册（`auth_config` 表 OAUTH2_CLIENT 类型）、模板消息/订阅消息/企业微信基建。

## 二、已确认决策

1. 四端共用一套后端、**同一账户体系**，且**可区分账户来源**（注册渠道 + 每端绑定身份 + 登录终端三个维度）。
2. 会话策略：**全局多会话无限制**（各端各自保留会话，互不顶号）。
3. PC 管理端**走认证中心独立登录**（MFA/TOTP、设备信任、暴力破解防护、JWT 密钥轮换）。
4. 微信主体归属不确定 → 归一算法采用**手机号为主、unionid 辅助、绑定表兜底**的兼容设计。
5. 前端不在本仓库，本方案交付后端改造 + 四端前端对接契约。
6. **分批推进**：每批完成 → mvnd 验证（以退出码为准）→ 按主题分组提交 → 停下等确认 → 下一批。

## 三、目标架构

本节以四张架构视图描述目标形态：**系统架构**（3.1 整体分层）、**对接架构**（3.2 四端与端点契约）、**集成架构**（3.3 微信生态与内部组件）、**部署架构**（3.4 运行拓扑），并给出**统一账户数据模型 ER 图**（3.5）。登录链路时序图与流程图见 4.3 / 4.4 / 4.5 与第六节。

### 3.1 系统架构图

```mermaid
flowchart TB
    subgraph CLIENTS["客户端层 · 四端共用一套后端"]
        direction LR
        PC["PC 管理端<br/>独立前端仓库"]
        APP["App<br/>Android / iOS"]
        MINI["微信小程序"]
        MPH5["公众号 H5"]
    end

    subgraph EDGE["接入层"]
        NG["Nginx / 负载均衡<br/>TLS 终止 · 路由分发"]
    end

    subgraph SERVICES["应用层 · 两套独立 Spring Boot 服务"]
        MAIN["主应用 apex-system :8080<br/>WebFlux 响应式 · 8 种登录策略<br/>多会话令牌 · RBAC / 数据权限"]
        AUTH["认证中心 apex-auth :8081<br/>PC 管理端登录 · MFA / TOTP<br/>设备信任 · 暴力破解防护"]
    end

    subgraph DATA["数据层"]
        MAINDB[("主库 MySQL / PostgreSQL<br/>sys_user · sys_user_social · 业务数据")]
        AUTHDB[("认证中心库 apex_auth<br/>auth_user · auth_config")]
        REDIS[("Redis + Redisson<br/>令牌槽 · 会话索引 · 黑名单 · 分布式锁")]
    end

    APP -->|"X-Client-Type: APP_ANDROID / APP_IOS"| NG
    MINI -->|"X-Client-Type: MINI_PROGRAM"| NG
    MPH5 -->|"X-Client-Type: WECHAT_MP"| NG
    PC -->|"业务请求：Bearer 认证中心 JWT<br/>X-Client-Type: PC_ADMIN"| NG
    NG -->|"C 端与管理端业务流量统一入口"| MAIN

    PC -->|"独立登录：密码 + 验证码 + MFA/TOTP"| AUTH
    AUTH -.->|"签发 JWT（共享 JWT_SECRET · HS256）<br/>claims: tokenType=ACCESS + accountId"| PC

    MAIN --> MAINDB
    MAIN --> REDIS
    AUTH --> AUTHDB
```

### 3.2 对接架构图（四端 → 接入契约）

```mermaid
flowchart LR
    subgraph CLIENTS["四端"]
        PC["PC 管理端"]
        APP["App"]
        MINI["小程序"]
        MPH5["公众号 H5"]
    end

    subgraph MAINAPI["主应用 /api/v1/auth"]
        LOGIN["POST /login<br/>loginType 共 8 种：WX_CODE · WX_PHONE · WX_SMS ·<br/>CL_PHONE · CL_SMS · PASSWORD · WX_MINI · WX_OAUTH"]
        REFRESH["POST /refresh<br/>同 sid 令牌轮换"]
        LOGOUT["POST /logout<br/>默认本会话 · scope=all 全端"]
        MPAUTH["GET /wechat/mp/authorize<br/>302 跳微信授权 · state 一次性"]
    end

    subgraph AUTHAPI["认证中心 /auth/v1"]
        CLOGIN["POST /login<br/>密码 + 验证码 + MFA / TOTP"]
        CREFRESH["OAuth2 Token Endpoint<br/>refresh_token 续期"]
    end

    APP -->|"WX_CODE / WX_PHONE / CL_* / PASSWORD"| LOGIN
    APP --> REFRESH
    APP --> LOGOUT
    MINI -->|"WX_MINI：code + phoneCode 可选"| LOGIN
    MINI --> REFRESH
    MPH5 -->|"第一步：网页授权"| MPAUTH
    MPH5 -->|"第二步：WX_OAUTH（code + state）或 CL_SMS"| LOGIN
    MPH5 --> REFRESH
    PC -->|"登录"| CLOGIN
    PC -->|"续期"| CREFRESH
    PC -.->|"业务请求（Bearer 认证中心 JWT）走主应用接口"| MAINAPI
```

### 3.3 集成架构图（微信生态 × 内部组件）

```mermaid
flowchart TB
    subgraph WX["微信生态 · 三渠道独立配置与密钥（环境变量注入）"]
        WXOPEN["开放平台<br/>wechat.app"]
        WXMINI["小程序平台<br/>wechat.miniapp"]
        WXMP["公众平台<br/>wechat.mp"]
    end

    subgraph INFRA["主应用 · infrastructure 适配层"]
        G1["WeChatGatewayImpl<br/>开放平台 oauth / API"]
        G2["WeChatMiniProgramService<br/>jscode2session · getuserphonenumber"]
        G3["WeChatMpService<br/>authorize URL · state 签发与一次性校验"]
    end

    subgraph APPLAYER["主应用 · application 编排层"]
        S1["WeChatCodeLoginStrategy<br/>WX_CODE / WX_PHONE"]
        S2["WeChatMiniProgramLoginStrategy<br/>WX_MINI"]
        S3["WeChatMpAuthLoginStrategy<br/>WX_OAUTH · state 必填"]
        S4["手机号 / 验证码 / 密码策略族<br/>CL_PHONE · CL_SMS · WX_SMS · PASSWORD"]
        NORMAL["账户归一四步编排"]
        SOCIAL["UserSocialService<br/>sys_user_social 绑定维护（幂等）"]
        TOKEN["TokenServiceImpl<br/>多会话令牌（sid 多槽）"]
    end

    subgraph AUTHC["认证中心 apex-auth"]
        ALOGIN["/auth/v1/login<br/>buildClaims：tokenType=ACCESS<br/>+ accountId + username + clientType"]
    end

    S1 --> G1 --> WXOPEN
    S2 --> G2 --> WXMINI
    S3 --> G3 --> WXMP
    S1 --> NORMAL
    S2 --> NORMAL
    S3 --> NORMAL
    S4 --> NORMAL
    NORMAL --> SOCIAL
    NORMAL --> TOKEN
    ALOGIN -.->|"主应用 Filter 按 claims 结构路由<br/>accountId 映射统一账户"| TOKEN
```

### 3.4 部署架构图

```mermaid
flowchart TB
    USER["用户终端<br/>浏览器 · App · 微信客户端"]

    subgraph IDC["部署环境（可容器化或主机部署）"]
        NG["Nginx / Ingress :443<br/>TLS 终止 · 反向代理"]
        subgraph APPCLUSTER["应用集群（无状态，水平扩容）"]
            MAINPOD["apex-system 实例 x N<br/>:8080"]
            AUTHPOD["apex-auth 实例 x N<br/>:8081"]
        end
        subgraph STORE["存储层"]
            MAINDB[("主库<br/>MySQL 或 PostgreSQL")]
            AUTHDB[("认证中心库 apex_auth<br/>与主库同实例或独立实例")]
            REDIS[("Redis 主从 / 哨兵<br/>令牌槽 · 会话索引 · 黑名单")]
        end
        ENV["配置注入（环境变量，严禁入库）<br/>JWT_SECRET（两应用共享）<br/>微信三渠道 AppSecret · 短信 / OSS 等其余密钥"]
    end

    USER -->|"HTTPS"| NG
    NG -->|"/api C 端 + 管理端业务"| MAINPOD
    NG -->|"/auth 管理端认证"| AUTHPOD
    MAINPOD --> MAINDB
    MAINPOD --> REDIS
    AUTHPOD --> AUTHDB
    MAINPOD -.->|"启动读取"| ENV
    AUTHPOD -.->|"启动读取"| ENV
```

### 3.5 账户模型（统一账户 + 区分来源）

- `sys_user`：唯一账户主数据，新增 `register_channel`（注册来源）。
- 新增 `sys_user_social`：多端微信身份绑定表（user_id + channel + open_id + union_id），同一用户在 App/小程序/公众号的三个不同 openid 各占一行。
- `auth_user`（认证中心，仅凭证/MFA/风控）：新增 `account_id` 关联 `sys_user.id`，登录签发的 JWT 携带 account_id；权限仍走主应用 RBAC（`@RequirePermission`/`@DataScope` 链路不变），认证中心不复制权限。
- 来源三维度：`register_channel`（注册来源）+ `sys_user_social.channel`（绑定渠道）+ 登录日志/操作日志 `client_type`（请求终端）。

**统一账户数据模型 ER 图**（两库仅经 `account_id` 弱关联，权限能力不复制；渠道身份只入绑定表，不写 `sys_user.open_id`）：

```mermaid
erDiagram
    sys_user ||--o{ sys_user_social : binds
    sys_user ||--o{ sys_login_log : logs
    auth_user |o--o| sys_user : maps

    sys_user {
        bigint id PK
        varchar tenant_id
        varchar phone
        varchar username
        varchar open_id "存量列 · 仅开放平台 App 主身份"
        varchar register_channel "注册来源 · 默认 UNKNOWN"
    }
    sys_user_social {
        bigint id PK
        bigint user_id FK
        varchar channel "WECHAT_APP / WECHAT_MINI / WECHAT_MP"
        varchar open_id "uk_channel_openid 联合唯一"
        varchar union_id
        varchar status
        datetime bound_at
    }
    auth_user {
        bigint id PK
        bigint account_id FK "映射 sys_user.id · 跨库人工回填"
        varchar username
        varchar password
    }
    sys_login_log {
        bigint id PK
        bigint user_id
        varchar login_type
        varchar client_type "PC_ADMIN / MINI_PROGRAM 等"
        varchar platform
        datetime login_time
    }
```

## 四、详细设计

### 4.1 数据模型（Flyway；主库 V59 起，认证中心 V55 起，MySQL/PostgreSQL 双份、幂等）

**主库 V59__multi_client_account.sql**：

- `sys_user` 增加 `register_channel VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN'` + 索引。
- 新建 `sys_user_social`：id、tenant_id、user_id、channel（WECHAT_APP/WECHAT_MINI/WECHAT_MP）、open_id、union_id、status、bound_at、create_time、update_time、version、deleted；唯一键 `uk_channel_openid(channel, open_id)`、索引 `idx_user_id`、`idx_union_id`。
- 存量 `sys_user.open_id` 迁移为 channel=WECHAT_APP 绑定行（幂等 INSERT SELECT）。
- `sys_login_log` 增加 `client_type`、`platform`。

**认证中心 V55__auth_user_account_binding.sql**：`auth_user` 增加 `account_id BIGINT NULL` + 索引；存量数据按 username/phone 匹配 `sys_user` 补齐（主库迁移，输出未匹配清单人工处理）。

### 4.2 客户端标识

- 新增枚举 `ClientType`（apex-common）：PC_ADMIN、PC_WEB、WECHAT_MP、MINI_PROGRAM、APP_ANDROID、APP_IOS、H5、UNKNOWN。
- 请求头 `X-Client-Type`（优先）+ `LoginRequest.clientType`（兜底），缺省 UNKNOWN 完全兼容存量客户端。
- JWT claims 增加 `sid`（会话 UUID）、`clientType`；`TokenStoreEntry` 增加 clientType、platform、loginType、deviceId、sid。

**客户端标识判定流程图**（来源解析优先级：请求头 > 请求体 > UNKNOWN）：

```mermaid
flowchart TD
    REQ(["任意请求进入主应用"]) --> H{"请求头携带 X-Client-Type ?"}
    H -->|"是"| V{"ClientType.fromCode 可识别 ?"}
    H -->|"否"| B{"LoginRequest.clientType 有值 ?<br/>（仅登录接口）"}
    V -->|"是"| OK["clientType = 请求头取值"]
    V -->|"否"| UNK["clientType = UNKNOWN<br/>（存量客户端完全兼容）"]
    B -->|"是"| OK2["clientType = 请求体取值"]
    B -->|"否"| UNK
    OK --> CLAIMS["写入 JWT claims 与 TokenStoreEntry<br/>登录日志记录 client_type / platform"]
    OK2 --> CLAIMS
    UNK --> CLAIMS
```

### 4.3 多会话令牌模型（改 TokenServiceImpl，主应用）

- Redis 键：`token:access:{tenant}:{subject}:{sid}` 与 `token:refresh:{tenant}:{subject}:{sid}`；会话索引 `token:sessions:{tenant}:{subject}`（Set）。
- 登录=生成新 sid 新槽，不覆盖旧会话；刷新轮换与并发锁按 sid 隔离（沿用 Redisson 锁模式）；黑名单按 jti 不变。
- 登出默认仅本会话（`?scope=all` 全端登出，预留）；滑动续期 `X-New-Access-Token` 机制不变。
- 兼容：存量无 sid 的 token 视为 `sid=legacy` 单槽，自然过期淘汰。
- 在线会话列表查询对接现有 `OnlineUserHandler`。

**多会话令牌时序图**（登录各占新槽 → 按 sid 轮换刷新 → 登出按 scope 生效）：

```mermaid
sequenceDiagram
    autonumber
    participant CA as 客户端 A（App）
    participant CB as 客户端 B（小程序）
    participant F as 主应用 AuthHandler
    participant T as TokenServiceImpl
    participant R as Redis

    CA->>F: POST /api/v1/auth/login（X-Client-Type: APP_ANDROID）
    F->>T: generateAccessToken(subject, deviceId, sidA, clientType)
    T->>R: SET token:access:{tenant}:{subject}:{sidA}
    T->>R: SET token:refresh:{tenant}:{subject}:{sidA}
    T->>R: SADD token:sessions:{tenant}:{subject} 含 sidA
    F-->>CA: accessToken + refreshToken（claims 含 sid=sidA）

    CB->>F: POST /api/v1/auth/login（X-Client-Type: MINI_PROGRAM）
    F->>T: generateAccessToken(subject, deviceId, sidB, clientType)
    T->>R: 写入 sidB 独立槽 + 登记会话索引
    F-->>CB: accessToken + refreshToken（claims 含 sid=sidB）
    Note over CA,CB: 全局多会话：A / B 同时在线，互不顶号

    CA->>F: POST /api/v1/auth/refresh（refreshToken, deviceId 一致）
    F->>T: 按 sidA 取 Redisson 锁后轮换
    T->>R: DEL sidA 旧槽 → 写入新 access / refresh（同 sidA）
    F-->>CA: 新令牌对（滑动续期 X-New-Access-Token 机制不变）

    CB->>F: POST /api/v1/auth/logout（默认 scope=current）
    F->>T: deleteSessionToken(subject, sidB)
    T->>R: DEL sidB 双槽 + SREM 会话索引 + 按 jti 拉黑
    F-->>CB: 登出成功（仅小程序会话失效，App 会话保留）
    Note over F,R: scope=all：遍历会话索引删除全部槽位（全端登出，预留）
```

### 4.4 登录方式扩展与账户归一算法

统一归一四步（LoginStrategy 公共编排）：

1. 按 `(channel, openid)` 查 `sys_user_social` → 命中则登录该账户；
2. 未命中按 `union_id` 查绑定 → 命中则登录并为当前 channel 新增绑定行（同主体自动归一）；
3. 未命中且有手机号（WX_MINI phoneCode / 短信验证码）按手机号查 `sys_user` → 命中则登录 + 绑定；
4. 均未命中 → 返回 `nextAction=REGISTER` 一次性票据（复用现有 AuthTicket/AuthNextAction 机制），注册成功写 `register_channel`。

- **WX_MINI（小程序）**：新增 `MiniProgramService`（infrastructure/wechat）实现 `/sns/jscode2session`；`getuserphonenumber` 改用小程序 appid 的全局 access_token；`LoginType=WX_MINI`（code 必填、phoneCode 可选）。
- **WX_OAUTH（公众号 H5）**：复用 `WeChatOAuthService`（snsapi_userinfo 网页授权），新增 `GET /api/v1/auth/wechat/mp/authorize`（302 跳转）+ 登录策略 `WX_OAUTH`。
- **微信配置拆分**：`wechat.app`（开放平台，保持兼容）/ `wechat.miniapp`（新增）/ `wechat.mp`（新增）；各自独立全局 access_token 缓存键；未配置的渠道对应 LoginType 返回明确错误码（新增 ResultCode + i18n 四语言）。

**账户归一四步流程图**（`isChannelBoundIdentity` 渠道身份走绑定表，绝不写 `sys_user.open_id`）：

```mermaid
flowchart TD
    START(["登录请求携带渠道身份<br/>loginType = WX_MINI / WX_OAUTH / WX_CODE 等"]) --> ISCH{"是否渠道绑定身份<br/>isChannelBoundIdentity ?"}
    ISCH -->|"否（手机号 / 密码等）"| DIRECT["按既有策略直接登录<br/>按需记录登录日志 client_type"]
    ISCH -->|"是"| S1{"步骤 1<br/>按 channel + open_id<br/>查 sys_user_social 命中？"}
    S1 -->|"命中"| HIT["登录该账户<br/>（同渠道重复登录幂等）"]
    S1 -->|"未命中"| S2{"步骤 2<br/>按 union_id 查绑定命中？"}
    S2 -->|"命中"| BIND2["登录既有账户<br/>为当前 channel 补绑定行<br/>（同微信主体自动归一）"]
    S2 -->|"未命中"| S3{"步骤 3<br/>有手机号且按手机号<br/>查 sys_user 命中？"}
    S3 -->|"命中"| BIND3["登录既有账户<br/>写入当前渠道绑定行"]
    S3 -->|"未命中"| S4["步骤 4<br/>签发 nextAction = REGISTER<br/>一次性票据"]
    S4 --> REG["用户完成注册<br/>写 register_channel + 绑定行"]
    HIT --> TOKEN["签发多会话令牌<br/>claims 含 sid + clientType"]
    BIND2 --> TOKEN
    BIND3 --> TOKEN
    DIRECT --> TOKEN
    REG --> TOKEN
```

**小程序登录时序图（WX_MINI）**：

```mermaid
sequenceDiagram
    autonumber
    participant U as 用户
    participant MP as 小程序前端
    participant API as 主应用 /api/v1/auth
    participant WXS as WeChatMiniProgramService
    participant N as 归一编排 + UserSocialService
    participant T as TokenServiceImpl

    U->>MP: 打开小程序
    MP->>MP: wx.login() 取 code（可选 getPhoneNumber 取 phoneCode）
    MP->>API: POST /login（loginType=WX_MINI, code, phoneCode?, X-Client-Type=MINI_PROGRAM）
    API->>WXS: miniProgramCodeToSession(code)
    WXS->>WXS: 校验渠道已配置（未配置返回 1023 明确错误码）
    WXS-->>API: openid + session_key
    opt phoneCode 存在
        API->>WXS: miniProgramPhoneNumber(phoneCode)
        WXS-->>API: 手机号
    end
    API->>N: 归一四步（绑定表 → unionid → 手机号 → REGISTER）
    N->>N: 命中即登录；未命中返回 nextAction=REGISTER 票据
    N->>N: upsertBinding 幂等写 sys_user_social
    N->>T: 签发 access/refresh（新 sid，clientType=MINI_PROGRAM）
    T-->>API: 令牌对 + 会话索引登记
    API-->>MP: Result（accessToken / refreshToken 或 nextAction）
    MP-->>U: 登录成功或引导注册
```

**公众号 H5 登录时序图（WX_OAUTH · state 防 CSRF）**：

```mermaid
sequenceDiagram
    autonumber
    participant U as 用户
    participant H5 as 公众号 H5 前端
    participant API as 主应用
    participant MPS as WeChatMpService
    participant R as Redis
    participant WX as 微信公众平台

    U->>H5: 访问 H5 页面（未登录）
    H5->>API: GET /api/v1/auth/wechat/mp/authorize（公开路径）
    API->>MPS: mpIssueAuthState()
    MPS->>R: SET 一次性 state（TTL 5 分钟）
    API-->>H5: 302 跳转微信授权页（snsapi_userinfo + state）
    H5-->>U: 浏览器跳转微信授权
    U->>WX: 确认授权
    WX-->>H5: 重定向 redirect_uri?code=...&state=...
    H5->>API: POST /login（loginType=WX_OAUTH, code, state, X-Client-Type=WECHAT_MP）
    API->>MPS: mpValidateAuthState(state)
    MPS->>R: GET + DEL（一次性，重放即失败）
    MPS-->>API: state 合法
    API->>MPS: mpAuthCodeToSession(code)
    MPS->>WX: oauth2/access_token 换取 openid / unionid
    MPS-->>API: MpAuthSession（openid, unionid）
    API->>API: 归一四步 + upsertBinding
    API-->>H5: Result（令牌对或 nextAction）
```

### 4.5 PC 管理端信任链（认证中心）

> **实施偏差（2026-10-07 确认）**：勘察发现认证中心 `/auth/v1/login` 链路实际与主应用共用同一 `JWT_SECRET`（HS256），其 RS256/JWKS 属于独立的 OAuth2 端点体系，与 PC 管理端登录无关。因此本节从「双密钥体系 + JWKS 验签」调整为「**claims 结构区分**」方案，安全语义等价：
>
> - 认证中心令牌 claims：`tokenType=ACCESS` + `accountId`（主应用 `sys_user.id`）+ `username` + `tenantId` + `jti`；主应用令牌：`type=access` + `sid`。两类令牌以 claims 结构天然互斥，不会误识别。
> - 主应用 `JwtAuthenticationFilter` 入口按 claims 结构路由：命中认证中心结构时按 `accountId` 加载统一账户，复用既有 RBAC 链路（统一授权服务 / 角色缓存 / 角色仓储），不滑动续期、不查主应用注销黑名单（认证中心令牌以库内 jti 撤销状态为准）。
> - 部署要求两侧使用相同 `JWT_SECRET`；存量 `auth_user ↔ sys_user` 映射跨库人工回填（`scripts/data/backfill_auth_user_account_id.sql`）。

- 认证中心登录响应与 JWT claims 增加 `accountId`、`username`、`clientType`（`PC_ADMIN`）；会话身份由既有 jti/tokenFamily 承担（令牌全量落库、按 jti 撤销，无需另加 sid）。
- 权限、数据权限、审计、租户上下文全部沿用主应用现有链路（`materializeUserContext` 与 C 端共用）。
- 认证中心保持既有能力：暴力破解防护、IP 访问控制、TOTP/MFA、设备信任、密钥轮换。

**PC 管理端信任链时序图**（认证中心独立登录 + 主应用 claims 路由鉴权）：

```mermaid
sequenceDiagram
    autonumber
    participant ADM as PC 管理端前端
    participant AC as 认证中心 /auth/v1
    participant ADB as 认证中心库
    participant API as 主应用 /api
    participant FLT as JwtAuthenticationFilter
    participant RBAC as 统一授权 materializeUserContext

    ADM->>AC: POST /auth/v1/login（username + password + 验证码, clientType=PC_ADMIN）
    AC->>ADB: 暴力破解防护 · 凭证校验 · MFA/TOTP · 设备信任
    ADB-->>AC: 认证通过（令牌全量落库）
    AC-->>ADM: accessToken + refreshToken
    Note over ADM,AC: claims: tokenType=ACCESS · accountId · username · tenantId · jti<br/>续期走认证中心 OAuth2 refresh_token；撤销以库内 jti 为准

    ADM->>API: 业务请求（Bearer 认证中心 JWT · X-Client-Type: PC_ADMIN · X-Tenant-Id）
    API->>FLT: 过滤器链解析 token
    FLT->>FLT: 校验 claims 结构 → 命中认证中心结构（tokenType=ACCESS + accountId）
    FLT->>RBAC: 按 accountId 加载统一账户（findById → enabled / 租户校验）
    RBAC-->>FLT: 用户上下文 + 角色/权限
    Note over FLT: 不滑动续期 · 不查主应用注销黑名单
    API-->>ADM: 业务响应（权限走主应用既有 RBAC / 数据权限链路）
```

### 4.6 横切配套

- CORS allowedHeaders 增加：`X-Client-Type`、`X-Tenant-Id`、`Idempotency-Key`、`X-Device-Id`。
- `SystemConstants.PUBLIC_PATHS` 最小化增量：`/api/v1/auth/wechat/mp/authorize`。
- 限流：登录端点保留 IP 限流 + Sentinel；新增按 phone 维度参数限流（短信端点同样处理）。
- 审计：登录日志写 `client_type`/`platform`；`@OperationLog` 机制不变。
- 消息触达：SSE 仅 PC/H5；小程序订阅消息、公众号模板消息（基建已有）。
- 文件：复用 MinIO/TOS 预签名与代理路径；小程序 uploadFile 兼容性列入批次 6 实测项。

## 五、分批实施计划（每批验证 + 提交后停下等确认）

| 批次 | 内容 | 验证 | 状态 |
|------|------|------|------|
| 1 | 账户来源与多端身份基础：V59/V55 迁移（双库）、`sys_user_social` 域模型/仓储、登录链路写绑定行 + register_channel、登录日志字段 | 受影响模块 test + 迁移完整性校验 | ✅ 完成 |
| 2 | 客户端标识与多会话令牌：ClientType、sid、TokenServiceImpl 多槽、登出 scope、CORS 头 | 受影响模块 test + spotless + pmd | ✅ 完成 |
| 3 | 微信多应用配置 + 小程序登录：wechat.miniapp、WeChatMiniProgramService、WX_MINI 策略、归一四步 | 单测（mock 微信 API）+ 全量回归 | ✅ 完成 |
| 4 | 公众号网页授权登录：wechat.mp、authorize 端点、WX_OAUTH 策略、OAuth state 防 CSRF（JS-SDK 签名按需后续） | 单测 + RouterOpenApi 覆盖测试 | ✅ 完成 |
| 5 | PC 管理端接入认证中心：auth_user.account_id、统一账户 claims、主应用令牌分支（claims 区分，见 4.5 偏差说明）、存量映射回填脚本 | 双侧 test + spotless + pmd | ✅ 完成 |
| 6 | 配套、文档与验收：PASSWORD 登录 username 维度防爆破、docs 更新、四端联调清单 | 全量测试 + 迁移完整性校验 | ✅ 完成 |

**实施过程中顺带修复的存量质量债**：apex-infrastructure 51 处、apex-auth 18 处接口 Javadoc 缺失（PMD 存量违规清零）；apex-system 排除 MapStruct 生成代码的 PMD 检查（与 infrastructure 模块对齐）。

每批按 Conventional Commits 分主题提交，不触碰无关 WIP。

## 六、四端前端对接契约（供独立前端仓库实施）

| 端 | 登录方式 | 关键请求头 | 令牌续期 | 推送/其他 |
|----|---------|-----------|---------|----------|
| PC 管理端 | 认证中心 `/auth/v1/login`（密码+验证码+MFA） | `X-Client-Type: PC_ADMIN`、`X-Tenant-Id` | 认证中心 refresh | SSE、菜单按权限 |
| App（现有链路 + 新增头） | WX_CODE/WX_PHONE/CL_PHONE/CL_SMS | `X-Client-Type: APP_ANDROID/APP_IOS`、`X-Device-Id` | `/api/v1/auth/refresh`（deviceId 一致性） | 厂商推送（后续） |
| 小程序 | `wx.login()` → WX_MINI（code，可选 phoneCode）；按 `data.nextAction` 分支处理 | `X-Client-Type: MINI_PROGRAM` | 同 App | 订阅消息；request 域名白名单 |
| 公众号 H5 | 网页授权 → WX_OAUTH（code）或短信验证码 | `X-Client-Type: WECHAT_MP` | 同 App | 模板消息；JS 安全域名 |

通用契约：`Result<T>` 解包（code/msg/data/traceId）、traceId 透传（`X-Trace-Id`）、`X-New-Access-Token` 无感续期、错误码 i18n、注册接口 `Idempotency-Key` 幂等头。

**前端对接总流程图**（各端前端接入后端的统一步骤，泳道按端拆分）：

```mermaid
flowchart TD
    PREP["通用准备（四端一致）"] --> P1["约定 BaseURL 与租户头 X-Tenant-Id"]
    P1 --> P2["封装 Result 解包 / traceId 透传<br/>X-Trace-Id 请求与响应联动"]
    P2 --> P3["实现 X-Client-Type 常量注入<br/>PC_ADMIN / APP_ANDROID / APP_IOS /<br/>MINI_PROGRAM / WECHAT_MP"]
    P3 --> P4["实现 X-New-Access-Token 无感续期拦截器"]

    P4 --> BR{"按端选择登录链路"}
    BR -->|"PC 管理端"| PC1["对接认证中心 /auth/v1/login<br/>密码 + 验证码 + MFA/TOTP"]
    PC1 --> PC2["业务请求携带认证中心 Bearer JWT<br/>续期走认证中心 refresh_token"]
    BR -->|"App"| APP1["沿用既有六种登录方式<br/>新增 X-Client-Type + X-Device-Id 头"]
    APP1 --> APP2["/api/v1/auth/refresh 续期<br/>deviceId 保持一致性"]
    BR -->|"小程序"| MP1["wx.login() 取 code<br/>可选手机号授权取 phoneCode"]
    MP1 --> MP2["POST /login loginType=WX_MINI<br/>按 data.nextAction 分支处理"]
    BR -->|"公众号 H5"| WX1["检测未登录 → 跳<br/>GET /wechat/mp/authorize"]
    WX1 --> WX2["微信回调回跳后<br/>POST /login loginType=WX_OAUTH<br/>code + state"]
    PC2 --> DONE["联调验收<br/>docs/05-operations/multi-client-acceptance-checklist.md"]
    APP2 --> DONE
    MP2 --> DONE
    WX2 --> DONE
```

**App 登录时序图（既有链路 + 新增终端标识）**：

```mermaid
sequenceDiagram
    autonumber
    participant U as 用户
    participant A as App 前端
    participant API as 主应用 /api/v1/auth
    participant WX as 微信开放平台 / 创蓝
    participant N as 登录策略 + 归一编排
    participant T as TokenServiceImpl

    U->>A: 打开 App
    alt 微信登录
        A->>WX: 拉起微信 SDK 授权
        WX-->>A: code
        A->>API: POST /login（loginType=WX_CODE, code, X-Client-Type=APP_ANDROID, X-Device-Id）
    else 一键登录 / 短信
        A->>WX: 取手机号 token / 发送短信验证码
        A->>API: POST /login（loginType=CL_PHONE / CL_SMS, X-Client-Type=APP_IOS）
    else 密码登录
        A->>API: POST /login（loginType=PASSWORD + 图形验证码）
    end
    API->>N: 策略执行 + 归一（命中绑定 / unionid / 手机号 / REGISTER）
    N->>N: upsertBinding（渠道身份才写绑定表）
    N->>T: 签发令牌（新 sid，clientType / platform / loginType / deviceId 入槽）
    T-->>API: accessToken + refreshToken
    API-->>A: Result（令牌对或 nextAction=REGISTER）
    Note over A,API: 登录日志记录 client_type / platform（账户来源终端维度）
```

**无感续期流程图（X-New-Access-Token）**：

```mermaid
flowchart TD
    REQ(["业务请求携带 accessToken"]) --> AUTH{"网关鉴权"}
    AUTH -->|"有效"| PASS["正常放行"]
    AUTH -->|"过期"| REF["响应携带 X-New-Access-Token<br/>（refreshToken 有效时已自动轮换）"]
    REF --> INTER{"响应拦截器发现新令牌头 ?"}
    INTER -->|"是"| SWAP["替换本地 accessToken<br/>重放原请求"]
    INTER -->|"否"| FAIL401["401 → 走 refreshToken 显式续期<br/>仍失败则引导重新登录"]
    SWAP --> PASS2["对用户透明完成"]
    FAIL401 --> RELOGIN["重新登录（各端各自链路）"]
```

**四端会话并存示意（全局多会话）**：

```mermaid
flowchart LR
    U["同一账户 sys_user"] --> S1["会话 1 · PC 管理端<br/>认证中心域 · jti 落库"]
    U --> S2["会话 2 · App<br/>sid 槽 A · clientType=APP_ANDROID"]
    U --> S3["会话 3 · 小程序<br/>sid 槽 B · clientType=MINI_PROGRAM"]
    U --> S4["会话 4 · 公众号 H5<br/>sid 槽 C · clientType=WECHAT_MP"]
    NOTE["四个会话互不顶号同时在线；<br/>主应用域登出 scope=all 清空槽 A/B/C，<br/>认证中心域（槽 1）独立管理，跨域联动为后续增强"]
```

## 七、兼容与回滚

- 存量客户端不传 clientType/sid → UNKNOWN + legacy 槽，行为不变；未配置 miniapp/mp 时对应登录方式返回明确错误码，不影响现有渠道。
- 迁移脚本幂等、版本号只增不改；每批独立提交可单独回滚。
- `keycloak.enabled=false` 时 SocialLoginHandler 不装配（现状保持）；认证中心不可用时主应用 PASSWORD 登录保留作为后备。

## 八、风险与未决项

1. 微信主体归属需运营确认——影响 unionid 自动归一是否生效，手机号归一兜底已覆盖两种情况。
2. `auth_user` ↔ `sys_user` 存量映射依赖 username/phone 匹配率，迁移脚本输出未匹配清单人工处理。
3. 两应用会话域独立，"跨域全端登出"本方案只做单域实现，跨域联动列为后续增强。
4. 小程序/公众号的服务器域名、JS 安全域名、AppSecret 环境变量注入需运维配合（严禁入库）。
5. 全局多会话无限制存在账号共享滥用风险（已按决策执行）；会话索引设计预留"按端限量"扩展点。
