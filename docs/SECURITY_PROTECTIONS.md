# 幂等 / 重试 / 攻击防护全景评估

> 适用范围：apex-scaffold（Java 17 · Spring Boot 3.5 · WebFlux 纯响应式）\
> 评估日期：2026-08-28 · 对应版本：v1.1.3+\
> 目的：回答「项目是否已实现幂等、重试机制，是否已拦截恶意攻击，相关防护是否齐备」，\
> 并记录本轮（2026-08-28）补齐的缺口与落地方式。\
> **配套架构图**: [安全防护体系](diagrams/12_security_protection_system.svg)

***

## 1. 结论速览

| 维度               | 现状                                                                     | 结论    |
| ---------------- | ---------------------------------------------------------------------- | ----- |
| 消息消费幂等           | 已实现（Redisson 带 token 所有权的 Redis `SETNX`/`compareAndSet` 状态机 + 业务处理 ID） | ✅ 到位  |
| 账号注销幂等           | 已实现（Idempotency-Key + DB 唯一索引 + 原子 UPDATE）                             | ✅ 到位  |
| HTTP 写接口幂等       | **本轮新增** `@IdempotentApi` + 响应式切面（Redis SETNX）                         | 🆕 补齐 |
| 防重放（Anti-Replay） | 切面已存在但**此前从未命中函数式端点**；**本轮修复**                                         | 🆕 补齐 |
| 重试机制             | 远程调用 Reactor Retry、微信/创蓝网关重试、Outbox 指数退避、MQ 重投、乐观锁重试                   | ✅ 到位  |
| 恶意攻击拦截           | 扫描拦截、IP 黑名单、XSS/SQLi/路径穿越、安全头、IP 限流、登录爆破防护                             | ✅ 到位  |
| 登录保护配置诚实性        | `enabled: false` 此前被静默忽略；**本轮接线 + WARN 告警**                            | 🆕 补齐 |
| 验证码升级强制          | **本轮新增**：连续失败 N 次后强制图形验证码（默认 3 次）                                      | 🆕 补齐 |

遗留缺口（不影响结论，见 §7）：死信队列无业务处理器、百度内容审核适配器未接入上传链路、
`lock-duration` 配置键未消费、生产环境登录保护仍处于关闭状态（有意的运维决策）。

***

## 2. 幂等机制

### 2.1 已实现（评估前即存在）

**MQ 消息消费幂等** — `MqConsumerService.processMessage(messageId, message, handler)`

- 通过 Redisson `RBucket.setIfAbsent` 实现 `mq:idempotent:{messageId}` 键，默认 TTL 30 分钟；

- 处理失败/异常时删除键，允许重投后再次处理；关闭前有 `shuttingDown` 闸门；

- `UserMessageListener`（`@RocketMQMessageListener`，`maxReconsumeTimes=3`）经标签→处理器注册表统一走该服务。

**账号注销幂等** — `AccountDeletionService` + `AccountDeletionPortAdapter`

- 幂等键取 `Idempotency-Key` 请求头（缺省用 UUID）；

- DB 唯一索引 `uk_account_deletion_request` + `deactivateForAccountDeletion` 原子 UPDATE 保证并发下只生效一次；

- 异步清理由 `AccountDeletionTaskScheduler` 轮询认领（`claim` + staleBefore + WORKER\_ID 并发保护）。

### 2.2 本轮新增：HTTP 写接口幂等 `@IdempotentApi`

**缺口**：通用 HTTP 写接口（注册、下单、支付回调等）此前无幂等保护；网络超时后客户端重试会重复执行副作用。

**组件**：

- 注解：`apex-common/.../annotation/IdempotentApi.java`

  - `header()` 默认 `Idempotency-Key`；`expireTime()` 默认 600 秒；`required()` 默认 true（严格模式）

- 切面：`apex-interfaces/.../aspect/IdempotentApiAspect.java`

  - `@Order(HIGHEST_PRECEDENCE + 10)`，拦截 `@annotation(idempotentApi)`

  - 兼容函数式端点（`ServerRequest`）与注解式端点（`ServerWebExchange`）两种参数形态：
    统一经 `AspectRequestUtil.resolveRequestMeta(args)` 归一化为 `RequestMeta`
    （路径/方法/请求头/远端地址），再以 `resolveClientIp(meta)` 提取客户端 IP；
    函数式端点直接消费 `ServerRequest` 自身的 `path()/headers()/remoteAddress()`，
    不依赖 `ServerRequest.exchange()` 转换（该调用在部分 WebFlux 构建与测试场景返回 null）

  - 键合成：`apex:idem:{sha256Hex(路径 | 幂等键头值 | 客户端IP)}`

  - 语义：SETNX 并发只放行一个；业务异常释放键允许重试；成功保留至 TTL 过期

  - Redis 不可用：记录 ERROR 并 fail-open 放行，DB 唯一约束作最终兜底

**错误码 / 文案**：

| 错误码  | 说明          | i18n key                      |
| ---- | ----------- | ----------------------------- |
| 5002 | 重复提交，请勿重复操作 | `web.repeat_submit`           |
| 5003 | 缺少幂等键请求头    | `web.idempotency_key_missing` |
| 4742 | 请输入图形验证码后重试 | `auth.captcha_required`       |

**接线示例**：`AuthHandler.register` 标注 `@IdempotentApi(required = false, header = "Idempotency-Key")`
——渐进接入：旧客户端不带头照常放行，带头即获得幂等保护。新写接口建议 `required = true` 严格模式。

**监控**：Micrometer 计数器 `apex.security.repeat.submit.rejected`（Prometheus 指标名
`apex.security.repeat.submit.rejected`，标签 `type="repeat_submit"`）。

### 2.3 防重放（Anti-Replay）修复

**缺口**：`AntiReplayAspect` 原 `findExchange` 只识别 `ServerWebExchange`；项目全库为
WebFlux 纯函数式端点（Handler 参数是 `ServerRequest`），导致标注 `@AntiReplay` 的方法**从未被拦截**。

**修复（2026-08-28）**：`resolveHeaders(Object[] args)` 依次匹配 `ServerWebExchange` → `ServerRequest`
（`asHttpHeaders()` 转换），函数式端点防重放校验真实生效。

**统一化（2026-09-02）**：请求解析收敛至 `AspectRequestUtil` —— 新增 `RequestMeta` record
与 `resolveRequestMeta(Object[])`（查找优先级 `ServerWebExchange` → `ServerHttpRequest` →
`ServerRequest`），`AntiReplayAspect` 经 `AspectRequestUtil.resolveHttpHeaders(args)`
获取请求头；函数式端点直接读取 `ServerRequest` 自身的 `path()/method()/headers()/remoteAddress()`，
**不再调用** **`ServerRequest.exchange()`**（该调用在部分 WebFlux 构建与测试场景返回 null），
保证注解式与函数式两种端点形态在切面中行为一致。

**防重放键**：`replay:{nonce}`，TTL 默认 360 秒（`apex.security.anti-replay.nonce-ttl-seconds`）。

### 2.4 遗留死代码（建议清理或接线）

- `@Idempotent`（MQ 切面注解）与 `IdempotentService`（`idempotent:` 前缀）：零调用点；
  职责已被 `MqConsumerService` 与 `@IdempotentApi` 覆盖，建议后续删除（保留 `audit_log` 式无害迁移同理）。

***

## 3. 重试机制

| 场景          | 实现                                                                        | 参数                          |
| ----------- | ------------------------------------------------------------------------- | --------------------------- |
| 通用远程调用      | `RemoteCallPolicy`：`Retry.backoff`，仅 5xx/IO 异常                            | `maxBackoff=10s`，按服务配置次数/延迟 |
| 微信网关        | `WeChatWebClientConfig`：Reactor `Retry.backoff` + `onRetryExhaustedThrow` | 按需配置                        |
| 创蓝网关        | `ChuanglanWebClientConfig`：同上                                             | 按需配置                        |
| 领域事件 Outbox | `DomainEventOutboxStore`：指数退避 `min(300s, 1L << min(attempts,8))`，超限转 DEAD | 300s 上限                     |
| MQ 消费重投     | RocketMQ `maxReconsumeTimes = 3` + 幂等键释放允许重投                              | MqConstant                  |
| 乐观锁重试       | `OptimisticLockRetryAspect`（同步退避重试）                                       | 底层乐观锁已接线                    |

**说明（诚实标注）**：`application.yml` / `application-prod.yml` 中的 `resilience4j.retry.instances`
为声明式配置，当前**无代码消费**（无 `@Retryable` / `RetryRegistry` 注入）；实际重试全部由
Reactor `Retry` 编程式完成，参数含义与声明式配置存在差异。建议：要么删除冗余 YAML，
要么按文档化的 `docs/DEPLOYMENT.md` 理解二者互补关系，避免运维误改后「没生效」。

***

## 4. 恶意攻击拦截（分层防御）

请求按 `@Order` 依次穿过拦截链（`HIGHEST_PRECEDENCE` 越小越先执行）：

| 顺序   | 组件                               | 职责                                                          |
| ---- | -------------------------------- | ----------------------------------------------------------- |
| +0   | `TraceFilter`                    | TraceId 透传                                                  |
| +1   | `BraveWebTracingFilter`          | 链路追踪                                                        |
| +2   | `GlobalResponseTimeoutWebFilter` | 全局响应超时兜底                                                    |
| +5   | `ScanTrafficGuardWebFilter`      | 25 类扫描路径 + CONNECT 方法阻断（403），指标 `apex.gateway.scan.blocked` |
| +20  | `ApiGatewayFilter`               | 网关路由/鉴权入口                                                   |
| +10  | `IdempotentApiAspect`            | HTTP 写幂等（AOP 层，方法级）                                         |
| 0    | `AntiReplayAspect`               | 防重放（AOP 层，方法级）                                              |
| -180 | `InputValidationFilter`          | XSS / SQLi / 路径穿越 / 超长参数 / Content-Type 白名单 / 10MB 文件上限     |
| -200 | `GlobalGuardWebFilter`           | Redis IP 黑名单 `blacklist:ip:{ip}` + UA 检查                    |

**登录爆破防护**（`AuthService.login`，`apex.security.login-protection.*`）：

| 配置                       | 默认   | 作用                                                                |
| ------------------------ | ---- | ----------------------------------------------------------------- |
| `enabled`                | true | **总开关（本轮真实接线）**；关闭时跳过防护并 WARN 告警一次                                |
| `ip-max-attempts`        | 20   | 单 IP 窗口内失败上限，超限返回 `LOGIN_LOCKED`(1008)                            |
| `username-max-attempts`  | 5    | 单用户名失败上限（更严格，防定向爆破）                                               |
| `captcha-after-failures` | 3    | **验证码升级阈值（本轮新增）**：连续失败 ≥3 次后登录必须携带验证码，否则 `CAPTCHA_REQUIRED`(4742) |
| `window-size`            | 900  | 失败计数窗口（秒）                                                         |

**业务侧防护**：`AntiReplay`（防重放）、`@IdempotentApi`（防重复提交）、Sentinel IP 限流
（登录接口）、短信发送限流（防轰炸）、图形验证码服务（`CaptchaService`）、安全响应头
（CSP / X-Frame-Options DENY / HSTS / Referrer-Policy / Permissions-Policy 等）。

***

## 5. 本轮改动清单

| 文件                                                           | 改动                                                                                                                                                                   |
| ------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `apex-common/.../annotation/IdempotentApi.java`              | 新增注解                                                                                                                                                                 |
| `apex-common/.../result/ResultCode.java`                     | 新增 4742 / 5002 / 5003                                                                                                                                                |
| `apex-interfaces/.../aspect/IdempotentApiAspect.java`        | 新增响应式幂等切面；请求上下文经 `AspectRequestUtil.RequestMeta` 统一解析                                                                                                                |
| `apex-interfaces/.../aspect/AntiReplayAspect.java`           | 修复函数式端点识别；请求头解析收敛至 `AspectRequestUtil.resolveHttpHeaders`                                                                                                            |
| `apex-interfaces/.../aspect/AspectRequestUtil.java`          | 统一请求解析工具：`RequestMeta` record + `resolveRequestMeta`（函数式端点不依赖 `exchange()`）                                                                                          |
| `apex-interfaces/.../monitor/SecurityMetricsRecorder.java`   | 新增 `recordRepeatSubmitRejected`                                                                                                                                      |
| `apex-interfaces/.../config/MetricsConfig.java`              | 新增 `apex.security.repeat.submit.rejected` 计数器                                                                                                                        |
| `apex-interfaces/.../handler/AuthHandler.java`               | `register` 接线 `@IdempotentApi(required=false)`                                                                                                                       |
| `apex-application/.../config/LoginProtectionProperties.java` | 新增 `enabled` / `captchaAfterFailures`                                                                                                                                |
| `apex-application/.../service/AuthService.java`              | 接线 `enabled` 开关 + 验证码升级强制                                                                                                                                            |
| `application.yml` / `application-prod.yml`                   | 新增 `captcha-after-failures: 3`；更新开关注释                                                                                                                                |
| i18n `messages_{zh_CN,zh_TW,en_US,th_TH}.properties`         | 新增 3 组文案                                                                                                                                                             |
| 测试                                                           | `IdempotentApiAspectTest`（新增）、`AntiReplayAspectTest`（补函数式端点用例）、`SecurityMetricsRecorderTest`（补计数器）、`AuthServiceTest`（补开关/升级用例）；函数式/注解式端点请求解析用例覆盖 `AspectRequestUtil` |

***

## 6. 运维指引

**需要关注的指标**（Prometheus）：

- `apex.security.repeat.submit.rejected` — 幂等拒绝；突增说明客户端重试行为异常或键冲突

- `apex.security.anti.replay.rejected` — 防重放拒绝；突增可能为攻击或客户端时钟偏差

- `apex.security.login.failed` / `apex.security.ip.blocked` / `apex.security.rate.limit.rejected` — 爆破/封禁/限流

- `apex.gateway.scan.blocked` — 扫描流量拦截

**幂等接入清单**：新写接口（POST/PUT/DELETE）一律标注 `@IdempotentApi`；对存量接口采用
`required=false` 渐进接入，客户端逐步补齐 `Idempotency-Key` 后收紧为 `required=true`。

***

## 7. 遗留缺口与建议

| 缺口                                      | 影响               | 建议                                     |
| --------------------------------------- | ---------------- | -------------------------------------- |
| `DeadLetterQueueProcessor` 零处理器注册       | 死信消息无人消费，问题不可见   | 按业务注册 DLQ handler 并告警                  |
| 百度内容审核适配器未接入上传链路                        | 图片/文本审核未真正生效     | 在 `FileHandler` 上传管线中接线                |
| prod `login-protection.enabled: false`  | 生产无爆破防护（有意的暂缓决策） | 完成 yml 注释中 3 项验证后开启                    |
| `lock-duration` 配置键未被属性类读取              | 配置漂移，运维误以为生效     | 删除该键或在 `LoginProtectionProperties` 中消费 |
| resilience4j YAML 与编程式重试并存              | 双份配置易误导          | 二选一并更新文档                               |
| `@Idempotent` / `IdempotentService` 死代码 | 维护负担             | 后续版本删除                                 |

