# 四端联调清单（PC 管理端 / App / 小程序 / 公众号 H5）

> 多端统一账户改造（批次 1–6）完成后的联调验收清单。后端改造点见
> `docs/02-architecture/multi-client-integration-plan.md`；环境变量与配置见
> `docs/05-operations/configuration.md` 的「多端统一账户与微信渠道」章节。
>
> 通用前置：主应用与认证中心使用相同 `JWT_SECRET`；各微信渠道凭据通过
> 环境变量注入（`WECHAT_APP_ID` / `WECHAT_MINIAPP_APP_ID` / `WECHAT_MP_APP_ID` 及对应 SECRET），
> 缺失渠道的登录方式返回 `wx.channel_not_configured`(1023)。

## 0. 数据库与存量迁移（一次性）

- [ ] 主应用库：Flyway 自动执行 `V59__multi_client_account.sql`（`sys_user.register_channel`、`sys_user_social`、`sys_login_log.client_type/platform`，MySQL/PG 双份已备）
- [ ] 认证中心库：Flyway 自动执行 `V55__auth_user_account_binding.sql`（`auth_user.account_id`）
- [ ] 认证中心库：按 `scripts/data/backfill_auth_user_account_id.sql` 人工回填存量 admin 账户映射（幂等；执行步骤三核对查询，确认未匹配清单为空或已处理、`ref_count>1` 冲突为 0 行）
- [ ] 未映射账户预期行为：认证中心登录成功，但访问主应用业务接口返回 401（令牌无 `accountId`）

## 1. PC 管理端（认证中心链路）

- [ ] `POST /auth/v1/login` 登录成功，响应含 `accountId`（已映射账户）
- [ ] 令牌访问主应用业务接口（如 `GET /api/v1/user/info`）通过 `JwtAuthenticationFilter` 认证中心分支，RBAC 权限正常装配（`@RequirePermission` 接口行为与本地账号一致）
- [ ] 禁用账户 / 租户不匹配令牌 → 401
- [ ] `POST /auth/v1/refresh` 续期后新令牌仍携带 `accountId`
- [ ] MFA/TOTP、设备信任、暴力破解防护按认证中心既有行为生效
- [ ] 请求头携带 `X-Client-Type: PC_ADMIN`（登录日志 `client_type` 落库为 PC_ADMIN）
- [ ] SSE 通知（`NotificationSseHandler`）正常收发

## 2. App（既有链路回归 + 多会话）

- [ ] `WX_CODE` / `WX_PHONE` / `WX_SMS` / `CL_PHONE` / `CL_SMS` 全方式登录回归通过（行为与改造前一致）
- [ ] 令牌 claims 含 `sid`/`clientType`；`X-Client-Type: APP_ANDROID`（或 IOS）上报后登录日志 `client_type` 正确
- [ ] **多会话**：同一账号在 Android 与 iOS 同时登录，两侧令牌均持续可用（互不顶号）
- [ ] 登出默认仅当前会话：A 端登出后 B 端令牌仍有效
- [ ] `POST /api/v1/auth/logout?scope=all` 全端登出后所有端 401
- [ ] `X-New-Access-Token` 滑动续期：新令牌写回同会话槽，另一端不受影响
- [ ] 新用户注册后 `sys_user.register_channel` 正确（APP_ANDROID/APP_IOS）

## 3. 微信小程序

- [ ] 前置：`wechat.miniapp` 凭据已配置；小程序后台配置服务器域名白名单（request 合法域名）
- [ ] `wx.login()` 取 code → `POST /api/v1/auth/login`（`loginType=WX_MINI`，`X-Client-Type: MINI_PROGRAM`）
- [ ] 已绑定用户直接登录；同主体（unionid 命中）自动归一到已有账户并补 `WECHAT_MINI` 绑定行
- [ ] 新用户无 phoneCode → `nextAction=PHONE_BIND_REQUIRED` + 票据；短信验证绑定后登录，`sys_user.open_id` 保持 `PHONE_` 占位、社交身份落 `sys_user_social`
- [ ] 新用户带 phoneCode → 手机号命中已注册账户直接登录；未注册 → `nextAction=REGISTER_REQUIRED`，注册后 `register_channel=MINI_PROGRAM`
- [ ] 手机号授权：`getuserphonenumber` 置换正常（走小程序全局 access_token）
- [ ] 订阅消息发送正常（基建既有能力）
- [ ] 渠道未配置演练：清空 `WECHAT_MINIAPP_APP_ID` → 登录返回 1023，其他登录方式不受影响

## 4. 微信公众号 H5

- [ ] 前置：`wechat.mp` 凭据已配置；公众平台配置网页授权域名
- [ ] `GET /api/v1/auth/wechat/mp/authorize?redirectUri=...` → 302 跳转微信授权页（无需登录态）
- [ ] 授权回跳携带 code+state → `POST /api/v1/auth/login`（`loginType=WX_OAUTH`，`state` 必传）
- [ ] **state 防 CSRF**：重放已消费的 state → 400；伪造 state → 400；state 5 分钟过期
- [ ] 已绑定 / unionid 归一 / 新用户票据引导三条路径同小程序矩阵
- [ ] `X-Client-Type: WECHAT_MP` 上报；模板消息发送正常
- [ ] 降级路径：短信验证码登录（`WX_SMS`）在公众号内可用

## 5. 横切验证（全端）

- [ ] 登录日志：`sys_login_log` 各端记录 `client_type`/`platform` 正确
- [ ] 账户来源：`sys_user.register_channel` 与 `sys_user_social`（channel+open_id 唯一）符合预期
- [ ] 限流：短信发送按手机号指纹限流生效；PASSWORD 登录按 username 维度防爆破（5 次失败锁定）
- [ ] Result<T> 契约：四端 `code/msg/data/traceId` 解包一致；错误消息 i18n 四语言
- [ ] CORS：预检请求携带 `X-Client-Type`/`X-Tenant-Id`/`Idempotency-Key`/`X-Device-Id` 通过
- [ ] 令牌互认隔离：C 端令牌（`type=access`）与管理端令牌（`tokenType=ACCESS`+`accountId`）各自链路独立，互不串用
