# 网关模块测试报告 — apex-gateway（Phase 1.2）

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

> 报告日期：2026-10-08 ｜ 执行环境：macOS（Java 17.0.12-oracle + Maven）
> 涉及阶段：微服务改造执行计划 Phase 1.2（`docs/08-project-management/plans/microservice-migration-plan.md` §5.3 网关过滤器链）
> 执行命令：`mvnd -pl :apex-gateway -am verify`
>
> 追加记录（2026-10-12）：本文档在 Phase 1.2 基线之上，补充网关熔断 / 限流 / 负载均衡 / 访问日志
> 增强（迁移方案 §5.3、§15）的测试结论，详见 **第八节「熔断 · 限流 · 负载均衡 · 访问日志增强补充」**。

## 一、测试概览

| 项目 | 结果 |
|------|------|
| 模块编译（`mvnd -pl :apex-gateway -am verify`） | ✅ BUILD SUCCESS |
| 单元+架构测试（surefire） | ✅ 64/64 通过（0 失败 / 0 错误 / 0 跳过；Phase 1.2 基线） |
| 单元+架构测试（surefire，增强补充后） | ✅ 94/94 通过（0 失败 / 0 错误 / 0 跳过） |
| Spotless 格式检查（spotless:check） | ✅ 通过（exit=0） |
| PMD 静态检查（p3c-pmd-check） | ✅ 通过（0 违规，本轮已修复 4 处遗留） |
| ArchUnit 架构测试 | ✅ 4/4（Phase 1.2 基线）→ **7/7**（增强补充后） |
| JaCoCo 覆盖率门禁（apex-services-bom） | ✅ LINE 85.9%（≥60%）｜ BRANCH 64.4%（≥50%） |
| 上下文加载（`@SpringBootTest` dev） | ✅ 2/2 通过 |

## 二、覆盖率指标（JaCoCo）

| 指标 | 覆盖率 | 门禁阈值 | 结果 |
|------|-------|---------|------|
| 行覆盖率 LINE | 311/362 = **85.9%** | ≥ 60% | ✅ |
| 分支覆盖率 BRANCH | 112/174 = **64.4%** | ≥ 50% | ✅ |
| 指令覆盖率 INSTRUCTION | 1467/1758 = **83.4%** | —（参考） | ✅ |

## 三、测试范围与结果

### 3.1 过滤器链（迁移方案 §5.3）

| 过滤器 | 顺序 | 用例数 | 状态 | 覆盖要点 |
|--------|------|-------|------|---------|
| CorsGlobalConfig（CorsWebFilter） | -100 | 上下文加载内校验 | ✅ | 启动校验：`allow-all-origins=false` 且 `allowed-origins` 空时抛 IllegalStateException |
| TraceWebFilter | -1 | 6 | ✅ | 生成/透传 `X-Trace-Id`、常量、顺序 |
| JwtSignatureFilter | 0 | 9 | ✅ | JWKS 验签、过期检查、公开白名单跳过、`/internal/v1` 强制拦截、伪造 Authorization 头、常量/顺序 |
| UserContextRelayFilter | 20 | 6 | ✅ | `sub/accountId/tenantId/clientType` 注入 `X-User-Id`/`X-Tenant-Id`、伪造同名头剥离、`userId==null` 严格 401、顺序 |
| RequestRateLimitFilter | 30 | 7 | ✅ | IP 级+路径级限流计数、Key 维度（IP/路径）、触发 429、规则缺失兜底、顺序 |
| GrayRouteFilter | 40 | 8 | ✅ | `tenantId` 白名单、用户 ID 尾号百分比、Header 强制三级切流、开关、顺序 |
| RequestLoggingFilter | 50 | 4 | ✅ | method/uri/status/耗时摘要、原样透传、敏感头不抛异常、异常透传 |

### 3.2 安全验签服务

| 测试类 | 用例数 | 状态 | 覆盖要点 |
|--------|-------|------|---------|
| GatewayJwtVerifierServiceTest | 9 | ✅ | HMAC 兼容 kid 验签（4）、RSA/RS256 验签（1）、密钥缓存（1）、非法输入（3：null/空/未知 kid） |

### 3.3 统一响应工具

| 测试类 | 用例数 | 状态 | 覆盖要点 |
|--------|-------|------|---------|
| GatewayWebUtilTest | 9 | ✅ | `writeError` 三态 401/403/429 统一 Result JSON、traceId 透传、UTF-8 中文、`clientIp` 四级回退（X-Forwarded-For→X-Real-IP→socket→unknown）、工具类禁止实例化 |

### 3.4 应用与架构

| 测试类 | 用例数 | 状态 | 覆盖要点 |
|--------|-------|------|---------|
| GatewayApplicationTest | 2 | ✅ | 上下文加载（dev profile）、过滤器链顺序与方案 §5.3 一致 |
| GatewayArchTest | 4 | ✅ | 包结构合规、service/filter/config/support 分层依赖合规 |

**合计：10 个测试类共 64 个用例，全部通过。**

## 四、事故披露与恢复记录

本轮在推进网关全门禁过程中，先后定位并修复 3 类阻塞性问题，均在本地闭环：

| # | 现象 | 根因 | 修复 |
|---|------|------|------|
| 1 | 上下文加载抛 `ConfigurationPropertiesBindException` / `ConverterNotFoundException` | `application.yml` 中 `jwks.ks` 写成标量字符串占位符 `ks: ${JWKS_KEYS:[]}`，无法绑定到 `List<JwksProfile>` | 改为 YAML 列表 `ks: []`，并以注释说明必须用列表/JSON 数组、不可用标量占位符（与 `apex-interfaces/application.yml` 既有注释规则一致） |
| 2 | 启动抛 `IllegalStateException: cors.allow-all-origins=false 但 cors.allowed-origins 未配置` | `CorsGlobalConfig` 启动校验：允许任意源关闭且未声明白名单即拒绝启动；`application-dev.yml` 未配置 CORS | `application-dev.yml` 新增 `cors.allowed-origins`（放行前端本机端口 3000/5173/5174/5175，与主应用 dev 配置一致） |
| 3 | PMD `p3c-pmd-check` 报 4 处违规 | `EqualsAvoidNullRule`×2（`path.equals(...)` 应常量优先）、`UndefineMagicConstantRule`×2（字符串字面量 `"Bearer "`/`"HS"`） | `JwtSignatureFilter.isInternalPath` 改常量前置比较；`resolveBearerToken` 改用既有 `SystemConstants.TOKEN_PREFIX`；`GatewayJwtVerifierService` 新增 `HMAC_ALG_PREFIX = "HS"` 常量 |

## 五、预存问题（非本轮引入，建议专项处理）

| 问题 | 归因 | 建议 |
|------|------|------|
| `JwksProfile`/`JwksProperties` 分支覆盖率偏低（分支 21/39、6/10 合计） | 纯数据载体 + 校验方法，验签路径已覆盖主分支 | 属数据类，收益有限，可在后续契约测试中补充 |
| `GatewayExceptionHandler` 分支 0/8、`RateLimitConfig` 分支 0/8 未覆盖 | 动作为 WebFlux 全局异常处理与 KeyResolver 装配，依赖真实限流规则与网关请求触发 | Phase 2 网关接入 auth/路由后补集成触达场景 |

## 六、交付物清单

**新增测试类（本次 Phase 1.2 补齐）**：
- `filter/GrayRouteFilterTest.java`、`filter/JwtSignatureFilterTest.java`、`filter/RequestLoggingFilterTest.java`、`filter/RequestRateLimitFilterTest.java`、`filter/TraceWebFilterTest.java`、`filter/UserContextRelayFilterTest.java`（过滤器链单测）
- `security/GatewayJwtVerifierServiceTest.java`（JWKS HMAC/RSA 验签）
- `support/GatewayWebUtilTest.java`（统一错误出口 + 客户端 IP 提取）
- `GatewayApplicationTest.java`（上下文加载 + 过滤器链顺序契约）
- `GatewayArchTest.java`（架构分层合规）

**本轮修复**：
- `src/main/resources/application.yml`：`jwks.ks` 标量占位符 → YAML 列表 `[]`（修复上下文加载失败）
- `src/main/resources/application-dev.yml`：新增 `cors.allowed-origins`（修复 CORS 启动校验失败）
- `filter/JwtSignatureFilter.java`：常量优先比较 + 复用 `SystemConstants.TOKEN_PREFIX`（PMD）
- `security/GatewayJwtVerifierService.java`：新增 `HMAC_ALG_PREFIX` 常量（PMD）

## 七、测试执行命令

```bash
# 全门禁（依赖 + 单测 + 架构 + 覆盖率 + 格式 + 静态检查）
mvnd -pl :apex-gateway -am verify
# 覆盖率报告
#   位置：apex-gateway/target/site/jacoco/index.html
```

## 八、熔断 · 限流 · 负载均衡 · 访问日志增强补充（2026-10-12）

> 对应迁移方案 §5.3（过滤器链）、§15（灰度切流），以及内部方案
> `.trae/documents/gateway-resilience-enhancement-plan.md`（Task 1–5）。

### 8.1 新增/扩展测试类

| 测试类 | 用例数 | 覆盖要点 |
|--------|-------|---------|
| `fallback/FallbackHandlerTest` | 4 | 熔断 OPEN 兜底：`503` + 统一 `Result`（含 traceId）、路径 `fallbackUri`、不泄露下游异常堆栈 |
| `lb/GrayTagServiceInstanceListSupplierTest` | 7 | 按 `X-Gray-Tag` 匹配 `metadata[gray-tag]` 实例；无匹配回退稳定/全量；缺标签透传 |
| `log/AccessLogServiceTest` | 6 | 敏感头脱敏为 `***`、白名单外头不落库、异步吞仓储异常并计数、开关/采样拦截 |
| `ratelimit/TokenBucketRateLimitFilterTest` | 7 | 令牌桶精确限流、超限 `429`（`ResultCode.RATE_LIMIT`）、Redis 异常降级放行 + WARN + 指标 |
| `CircuitBreakerRouteIntegrationTest`（failsafe IT） | 2 | 路由绑定 `CircuitBreaker` filter、OPEN 触发兜底（不由 surefire 执行） |
| `filter/GrayRouteFilterTest`（扩展） | 8 | 三级灰度判定（Header 强制 / 租户白名单 / 用户百分比）+ **灰度命中指标**校验 |
| `GatewayArchTest`（扩展） | 7 | 包边界与依赖方向（见 8.2） |

### 8.2 ArchUnit 架构约束（新增 3 条）

在原 4 条（业务层隔离 / 无 Servlet / filter 不依赖 config / security 不依赖 filter）基础上，新增：

| 规则 | 约束 |
|------|------|
| `fallback_does_not_depend_on_business_layers` | `gateway.fallback` 不得依赖 interfaces/application/domain/infrastructure |
| `load_balancer_does_not_depend_on_business_layers` | `gateway.lb` 不得依赖业务分层 |
| `access_log_does_not_depend_on_business_layers` | `gateway.log` 不得依赖业务分层 |

### 8.3 可观测性指标（`apex.gateway.*`）

统一经 `/actuator/prometheus` 暴露，公共 tag 为 `application=${spring.application.name}`：

| 指标名 | 类型 | 标签 | 来源 |
|--------|------|------|------|
| `apex.gateway.circuitbreaker.transitions` | Counter | `name`、`state` | `ResilienceConfig` 状态迁移事件 |
| `apex.gateway.circuitbreaker.rejected` | Counter | `name` | 熔断 OPEN 拒绝放行 |
| `apex.gateway.ratelimit.rejected` | Counter | `scope` | 令牌桶限流拒绝 |
| `apex.gateway.ratelimit.failopen` | Counter | — | Redis 异常降级放行 |
| `apex.gateway.gray.hit` | Counter | `source`（header/tenant/percentage） | 灰度命中 |
| `apex.gateway.accesslog.persist.failed` | Counter | — | 访问日志落库失败（已吞异常） |

### 8.4 预存低覆盖分支的最新状态

| 问题 | 本次状态 |
|------|---------|
| `GatewayExceptionHandler` 分支 0/8 | 本轮经兜底/统一响应路径触达部分分支（详见覆盖率报告） |
| `RateLimitConfig` 分支 0/8 | 由 `TokenBucketRateLimitFilterTest` / 限流装配用例补充触达 |

### 8.5 增强补充执行命令

```bash
# 网关模块单测 + 架构（含新增约束）
mvnd -pl :apex-gateway -am test
# 本次结果：Tests run: 94, Failures: 0, Errors: 0, Skipped: 0（BUILD SUCCESS）
```