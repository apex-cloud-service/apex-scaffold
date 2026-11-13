# 查询聚合与统一待办测试报告（2026-11-13）

> **范围**：2026-11-12 引入的四项能力——只读查询聚合服务（`apex-aggregation-service`，8098）、
> 统一待办门面（`GET /api/v2/todo`）、office 审批撤销幂等编排（V9/V10）、system 概览聚合
> 舱壁（缓存 + 请求合并）。配套战役：全仓文档/配置/脚本/图表/注释对齐（见批次清单）。
> **执行环境**：macOS（arm64 交叉 x64 容器镜像不适用；本报告全部为宿主直跑）、
> JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`、`-T4`。

## 一、测试补充清单（批次 D）

| 类型 | 测试类 | 模块 | 用例数 | 结果 |
| --- | --- | --- | --- | --- |
| 单元 | `InternalOverviewHandlerTest` | apex-system | 4 | 全绿 |
| 单元 | `PlatformOverviewHandlerTest` | apex-system | 3 | 全绿 |
| 单元 | `InternalOverviewRouterTest` | apex-system | 2 | 全绿 |
| 单元 | `PlatformOverviewRouterTest` | apex-system | 1 | 全绿 |
| 单元 | `UnifiedTodoHandlerTest` | apex-aggregation-service | 4 | 全绿 |
| 单元 | `IdentityRelayAuthenticationFilterTest`（伪造头/ACCESS/刷新令牌/RS256/非法密钥） | apex-aggregation-service | 5 | 全绿 |
| 单元 | `AggregationSecurityConfigTest` | apex-aggregation-service | 1 | 全绿 |
| 集成 | `UnifiedTodoAggregationServiceIntegrationTest`（MockWebServer 真实出站 HTTP + 签名头校验） | apex-aggregation-service | 3 | 全绿 |
| 集成 | `ApprovalCancelOperationRepositoryIntegrationTest`（H2 basic 全上下文 + 真实 Mapper/事务） | apex-office-service | 5 | 全绿 |
| 压力 | `OverviewFacadeServiceStressTest`（并发合并/舱壁饱和/降级不缓存） | apex-system | 3 | 全绿 |
| 压力 | `UnifiedTodoAggregationServiceStressTest`（不缓存不合并/舱壁约束/许可回收） | apex-aggregation-service | 3 | 全绿 |

新增合计：34 个用例（单元 20、集成 8、压力 6）。既有回归见第二节。

**覆盖率**（`apex-aggregation-service`，JaCoCo BUNDLE）：行 83.4%（门槛 60%）、分支 58.8%（门槛 50%）。
新增认证链测试补齐了此前为 0 的 `IdentityRelayAuthenticationFilter` 与 `AggregationSecurityConfig`
覆盖，使该模块从 44.1%/26.3%（不达标）回到门槛以上。

## 二、测试执行结果

### 2.1 单元/集成门禁（mvnd clean verify）

命令：`mvnd -T4 -B clean verify`（2026-11-13，JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`）

- 反应堆 **37 个模块全部 SUCCESS**，`BUILD SUCCESS`，退出码 0。
- Surefire 汇总 **5825 / Failures 0 / Errors 0**；Failsafe 汇总 **492 / Failures 0 / Errors 0 / Skipped 33**
  （跳过项为既有 `@EnabledIfSystemProperty`/`@EnabledIfEnvironmentVariable` 环境依赖用例，需真实
  MySQL/Redis/HTTPS/MQTT/TC 等外部条件，与 2026-11-05 基线口径一致）。
- 门禁组成：Surefire + Failsafe（`*IntegrationTest`/`*E2ETest`/`FlywayMigrationStartupTest`）+
  Spotless + PMD/p3c + JaCoCo BUNDLE（行 60% / 分支 50%）。
- 与 2026-11-05 基线（surefire 6181 / failsafe 388）相比：surefire 净减 356（删除 system 旧待办栈
  测试与相关类，新增 20 个用例抵消部分）、failsafe 净增 104（新增 8 个 IT 用例与 office/system 侧
  既有 IT 的执行范围变化）。
- 构建过程中曾出现的 4 次失败均为可定位问题并已修复：能力目录文档契约（撤销越界新增能力行）、
  Spotless 格式（4 处 import 排序/换行）、聚合服务 JaCoCo 覆盖率不足（补认证链测试后 83.4%/58.8%）、
  聚合 IT 出站请求顺序假设不稳定（改为按路径匹配并连跑 3 次验证稳定）。

### 2.2 压力测试（mvnd -Pstress-tests）

`mvnd -T4 -B -pl apex-system,apex-aggregation-service -am test -Pstress-tests`（2026-11-13）：

| 测试类 | 用例数 | 结果 |
| --- | ---: | --- |
| `OverviewFacadeServiceStressTest`（并发合并/舱壁饱和/降级不缓存） | 3 | 全绿 |
| `UnifiedTodoAggregationServiceStressTest`（不缓存不合并/舱壁约束/许可回收） | 3 | 全绿 |
| 既有 `LocalRateLimiterStressTest`（apex-common） | 7 | 全绿 |
| 既有 `AdaptiveRateLimiterStressTest`（apex-infrastructure-cache） | 6 | 全绿 |

关键断言语义：64 并发同租户请求合并后下游恰好 3 次拉取（合并生效）；舱壁饱和时下游实际并发不超过
许可数；降级结果不入租户缓存，下游恢复后下一次请求立即回到全源 UP；个人维度待办 48 并发产生
96 次下游拉取（无合并，符合设计口径）。


### 2.3 性能测试（本地最小栈真实压测）

执行时间 2026-11-13 08:00–08:15（宿主直跑，无容器交叉损耗）。栈：`apex-workflow-service`(8093, H2 basic)、
`apex-office-service`(8084, H2 basic + Redis)、`apex-aggregation-service`(8098)；三服务共用 HMAC JWT 密钥与
内部签名密钥，聚合服务经真实 HTTP + 内部签名调用两源。压测工具 wrk 4.2.0 / ApacheBench 2.3。
复现脚本：`scripts/performance/results/20261113/`（`gen_hmac_jwt.py`、`start-minimal-stack.sh`）。

| 场景 | 并发 | 时长 | QPS | P50 | P90 | P99 | 错误率 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `GET /api/v2/todo` 双源全 UP | 64 | 30s | **671.65** | 94.88ms | 262.60ms | 481.38ms | 0% |
| `GET /api/v2/todo` 双源全 UP（中并发） | 16 | 20s | **263.28** | 40.28ms | 213.91ms | 475.64ms | 0% |
| `GET /api/v2/todo` 单源降级（office 停机） | 32 | 20s | **2390.47** | 9.95ms | — | 170.80ms | 0%（连接错误 0） |
| `GET /actuator/health` 基线（无下游调用） | 20 | 10s | — | 13.71ms | — | — | 0% |

**验收判定**（对照 `integration-test-guide.md`：QPS > 100、RT < 100ms、错误率 < 1%）：

- QPS：671.65（64 并发）> 100 ✅；263.28（16 并发）> 100 ✅。
- RT：中并发 P50 40.28ms < 100ms ✅；高并发 P50 94.88ms < 100ms ✅（P90/P99 分别为 262ms/481ms，
  受两源真实 HTTP 往返与 64 并发排队影响，属预期长尾）。
- 错误率：0%（ab 报告的 `Failed requests` 全部为 `Length` 计数——响应体每次含不同 traceId/时间戳，
  非状态码失败；`Connect/Receive/Exceptions` 均为 0，另以 300 次串行抽样验证 300/300 为 HTTP 200）。
- 降级行为：office 停机期间 workflow 源保持 `UP`、office 源 `DEGRADED`、整体 `degraded=true`，
  且降级路径更快（2390 QPS / P50 9.95ms，跳过不可达源等待），验证「单源失败不拖垮整体」。


## 三、测试发现并修复的缺陷

| # | 缺陷 | 级别 | 修复 |
| --- | --- | --- | --- |
| 1 | `apex-system` 残留旧统一待办栈 6 文件：网关 `/api/v2/todo` 已固定路由至聚合服务，system 侧不可达（死代码），文档却声称"已迁移删除" | 设计漂移 | 删除 6 文件，完成迁移闭环 |
| 2 | 聚合服务缺整套认证链：引入 spring-security 但无 `SecurityWebFilterChain`（Boot 默认链锁死全部端点）、无 JWT 验签过滤器、`request.principal()` 永远为空 → `/api/v2/todo` 实际不可用 | P0 功能阻断 | 新增 `AggregationSecurityConfig` + `IdentityRelayAuthenticationFilter` + `AuthenticatedUser`（对齐 file/office 模式） |
| 3 | `InternalAuthProperties` 默认 `callers` 白名单缺 `apex-aggregation-service` → 出站签名被 workflow/office fail-closed 拒绝 | P0 功能阻断 | 默认白名单登记两内部待办路径 |
| 4 | office V9 MySQL 迁移主键未声明自增，而 PO 为 `IdType.AUTO`（插入不含 id）→ MySQL 生产插入必失败（PG 侧 BIGSERIAL 正常） | P0 数据库缺陷 | V10 配对迁移补 `AUTO_INCREMENT`（PG 侧为注释配对迁移），basic-schema 同步 |
| 5 | office 缺 MyBatis-Plus 拦截器（无 `OptimisticLockerInnerInterceptor`/`PaginationInnerInterceptor`）→ 全部带 `@Version` PO 的 `updateById` 抛 `BindingException`（含撤销补偿事务、通知模板/会议室/日历/文档更新），`selectPage` 丢失 count | P0 持久化缺陷 | 新增 `OfficeMybatisPlusConfig`（乐观锁 + 分页，office 显式租户条件口径故不注册租户行级插件）；补 `mybatis-plus-jsqlparser` 依赖 |
| 6 | office basic-schema 漏 V9 表 `off_approval_cancel_operation`，迁移一致性测试白名单同步缺失（表结构守卫盲区） | P2 守卫缺口 | basic-schema 补表 + `OfficeFlywayMigrationTest` 白名单/PO 映射/索引断言补齐 |
| 7 | `XXL_ADMIN_PORT` 默认 8098 与聚合服务业务端口撞号（.env.example + compose） | P2 部署冲突 | XXL 默认改 8099 |
| 8 | 聚合服务 handler 死 `@OperationLog` 注解（模块无切面）+ 硬编码中文错误消息违反 i18n 口径 | P3 | 移除注解，消息改用 `ResultCode` 默认值 |
| 9 | `ApprovalProcessMapper.selectPendingByApprover` 使用 MySQL 专有 `JSON_CONTAINS`/`JSON_QUOTE` → H2 basic 与 PostgreSQL 上待审批查询（含 office 内部待办端点）直接抛 `BadSqlGrammarException` | P0 跨方言缺陷 | Mapper 改为方言中性的状态+租户过滤，审批人归属在仓储层解析 JSON 数组过滤（与同文件 `selectAllByTenant` 既有规避口径一致）；经压测链路实测两源全 UP |

## 四、性能测试方法与验收线

- 验收线沿用 `docs/06-testing/integration-test-guide.md`：QPS > 100、RT < 100ms、错误率 < 1%。
- 压测目标：`GET /api/v2/todo`（聚合服务直连 8098，HMAC JWT 认证；workflow/office 以 basic 档
  H2 内存库承载内部待办端点，全链路真实 HTTP + 内部签名）。
- 工具：wrk 4.2.0 / ApacheBench 2.3。

## 五、环境与复现说明

- **压测栈**：宿主直跑三个 jar（`basic` Profile），H2 内存库替代 MySQL/PostgreSQL；Redis 以
  `podman run -d -p 6379:6379 redis:7-alpine` 提供（office 的 rate-limit/会话组件依赖）。
  未启动网关、认证中心与 system：聚合服务直连 8098，JWT 由 HMAC 密钥本地签发（与 workflow/office 的
  `jwt-secret` 一致），内部签名密钥三服务共用。
- **租户口径**：office 审批域租户为数字 ID（`ApprovalProcessRepositoryImpl` 以 `Long.valueOf` 解析
  `TenantContext`），故压测 JWT 的 `tenantId` 取 `1`；字符串租户（如 `default`）在 office 待办源会
  降级——这是既有租户口径约束，本报告如实标注，未在本次改造中变更。
- **未覆盖**：网关层（`lb://` 负载均衡、网关限流/CircuitBreaker）未纳入本轮压测，因其需要
  Nacos 注册与完整拓扑；网关侧熔断与 fallback 语义由既有集成测试与配置校验覆盖。
- **verify 口径**：全量 `mvnd -T4 -B clean verify` 在压测之后单独执行，避免压测占用 CPU 影响
  计时敏感用例。


---

**结论**：全量 `mvnd -T4 -B clean verify` **BUILD SUCCESS**（37 模块，Failures 0 / Errors 0）；
压力测试 6 个新用例与既有 13 个限流压力用例全绿；本地真实压测 `GET /api/v2/todo` 达到
**671.65 QPS / P50 94.88ms / 错误率 0%**（中并发 263.28 QPS / P50 40.28ms），满足
QPS>100、RT<100ms、错误率<1% 的验收线；单源降级路径实测 2390 QPS / P50 9.95ms 且整体
不失败。本轮共发现并修复 9 项缺陷，其中 4 项为 P0（聚合服务认证链缺失、内部认证白名单缺登记、
office V9 主键非自增、office 缺 MyBatis-Plus 插件导致 `@Version` 更新全失败），另 1 项 P0
跨方言缺陷（`JSON_CONTAINS`）在压测链路中暴露并修复。

**未覆盖边界**（如实标注，不记为通过）：网关层 `lb://` 负载均衡与限流/熔断未纳入本轮压测；
MySQL/PostgreSQL 真实库执行 V9/V10 迁移未在本机演练（H2 basic 与静态迁移一致性测试已覆盖表/列/
索引集合）；remote-it（远程 MySQL+Redis）与混沌演练未执行；K8s 清单未改造（维持单体模板）。

**辅助汇总**：`bash scripts/test/generate-test-report.sh` 亦已执行（`target/reports/summary/test-report.md`，
5839 通过 / 0 失败 / 0 错误 / 15 跳过）。该脚本按既有实现只汇总部分模块的 surefire XML，模块口径窄于
本报告的完整 37 模块构建统计（surefire 5825 + failsafe 492），两者不可互相替代；以本报告与构建日志为准。

**报告生成**：apex-scaffold 全仓对齐战役（2026-11-13），数据由实际构建/测试产物采集，非估算。
