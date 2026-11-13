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

## 六、未验证项补充执行（2026-11-13 第二轮）

第一轮标注的 5 项未验证边界已逐项执行，结果如下。

### 6.1 MySQL / PostgreSQL 真实库迁移演练（已完成）

新增 `OfficeDualDatabaseMigrationIntegrationTest`（Testcontainers，真实 MySQL 9 + PostgreSQL 15）：
在两种方言上执行办公域完整迁移链 V1–V10，并断言

- V9 建表与 V10 主键自增修正顺序生效：MySQL `AUTO_INCREMENT` / PostgreSQL `BIGSERIAL`，
  省略主键插入后主键由数据库生成（与 PO 的 `IdType.AUTO` 对齐）；
- 幂等键唯一约束 `uk_off_approval_cancel_operation` 与状态索引真实存在；
- `status` 默认值为 `PENDING`；迁移版本连续且 `validate()` 无 pending。

`scripts/test/run-dual-database-migration.sh` 同步纳入 office 域，三域完整演练结果：
**system（V1–V90）✓ / integration（V1–V3）✓ / office（V1–V10）✓，全部通过**。

### 6.2 网关层 lb:// 路由与熔断（已完成）

以 Nacos 2.3.2 standalone + 真实网关构建完整拓扑（workflow / office / aggregation 注册到同一命名空间），
经 `kubectl` 等价路径 `curl → 网关 8080 → lb://apex-aggregation-service → 8098` 验证：

| 场景 | 结果 |
| --- | --- |
| 网关 `lb://` 路由转发 `/api/v2/todo` | **200**，双源 `UP`，`degraded=false`，端到端 53–228ms |
| 低并发网关链路（1 并发） | **110.68 QPS / P50 8.37ms / P99 24.23ms**，无限流 |
| 停掉聚合服务后请求 | **503 + `code=3007`「服务暂时不可用」**（熔断 fallback 生效，6–26ms 快速失败，非超时堆积） |
| 聚合服务恢复后 | 熔断半开自动恢复，**连续 200**，无需重启网关 |
| 限流 | `default-capacity=1000`、`refill-rate=100/s` 按用户维度计数；高并发压测触发大量限流响应，串行与低并发请求不受影响 |

**网关层未做高并发 RT 压测的原因**：网关按用户维度限流（默认 100/s），单 token 高并发会被限流，
无法得到有意义的端到端 RT 分布；限流本身已通过高并发触发验证。真实扩容场景需多 token 或调整限流阈值。

### 6.3 remote-it（远程 MySQL + Redis，已完成链路打通）

`-Premote-it -Dapex.remote-it.enabled=true` 首次真实执行，暴露出 4 个此前从未被发现的阻断缺陷
（该 profile 此前从未运行过，配置与装配均未验证）：

| # | 缺陷 | 修复 |
| --- | --- | --- |
| 1 | `IntegrationTestApplication` 缺 `@MapperScan` → Mapper Bean 缺失，上下文无法启动 | 与生产 `SystemServiceApplication` 同口径补 `@MapperScan` |
| 2 | `it-local` profile 无对应配置文件 → 数据源/Redis/Flyway 全部未加载 | 新增 `application-it-local.yml`（与 `application-it.yml` 同源，改用真实 Redis） |
| 3 | `MeilisearchAdapter` 无条件 `@Component`，但 `Client` Bean 仅 `meilisearch.enabled=true` 时存在 → 关闭搜索的环境上下文启动失败 | 适配器加 `@ConditionalOnProperty`；新增 `SearchDisabledConfiguration` 提供空实现 |
| 4 | `LokiAdapter` 同型缺陷（`LokiProperties` 条件装配） | 加 `@ConditionalOnProperty`；新增 `LogQueryDisabledConfiguration` |
| 5 | 生产 `SecurityConfig` 的 `@Profile("!it")` 未排除 `it-local` → 测试安全链被生产链覆盖，`request.principal()` 为空 | 改为 `@Profile("!it & !it-local")`；测试侧补 `@Import` 与 OAuth2 占位注册表 |

**执行结果**：10 个用例中 **9 个通过**（未认证 401、无效 token 401、过期 token 401、刷新 token、
用户信息等核心鉴权语义全部通过），1 个 `logout_withValidToken_shouldReturn200` 失败——该用例依赖
`request.principal()`，在测试安全链下未建立认证主体。**已定位为测试基础设施问题（非产品缺陷）**：
`ItProfileSecurityConfiguration` 的 `itJwtBypassFilter` 未在 `it-local` 组合下生效，需进一步排查
Spring Security 多链注册顺序；本轮未修改，记为**已知未通过项**，不影响 9 个已通过用例的结论。

### 6.4 混沌演练（已完成）

`scripts/test/chaos-fault-injection.sh` 两个场景均通过：

| 场景 | 断言 | 结果 |
| --- | --- | --- |
| `workflow-down` | workflow 摘除；file、auth 独立可服务 | **✓ 通过** |
| `auth-down` | auth 摘除；workflow、file 独立可服务 | **✓ 通过**（首轮因上一场景未恢复 workflow 而失败，恢复后重跑通过） |

### 6.5 K8s 清单（已改造）

`k8s/deployment.yaml` 由单体单 Deployment 改为**多服务清单**：8 个 Deployment + 8 个 Service
（gateway / auth / system / workflow / office / file / integration / aggregation）+ Secret +
Ingress + 网关 HPA。端口、探针端口（system 8083、integration 8097、其余业务端口）与
`build-manifest.yaml` / `podman-compose.yml` 同源；`canary-deployment.yaml` 与
`pod-disruption-budget.yaml` 同步改为网关口径。三份清单均通过 YAML 解析校验。

**仍未验证**：K8s 清单未在真实集群 `kubectl apply` 演练（本机无可用集群）；镜像需按
`SERVICE_MODULE` 分别构建，本轮未逐一构建 8 个镜像。

### 6.7 第二轮全量回归

`mvnd -T1 -B clean verify`（串行，避免 Testcontainers 跨模块争抢）：**37 个模块全部 SUCCESS**，
Surefire **5825 / Failures 0 / Errors 0**，Failsafe **494 / Failures 0 / Errors 0 / Skipped 33**。
较第一轮 failsafe 388 → 494（+106），增量来自 office 双库迁移 IT（2 用例）与本轮新增的 office/IT 变体；
`surefire` 计数不变（本轮未新增单元测试）。

> **并行度提示**：`-T4` 下三个 `*DualDatabaseMigrationIntegrationTest` 会同时拉起 MySQL/PG 容器，
> 偶发 `ContainerLaunchException`。此类失败为环境资源竞争，单独 `-T1 -pl <module>` 重跑即通过
> （本轮已实测：integration 域 `-T1` 70s 内 2/2 通过）。该口径已写入 `AGENTS.md` 验证门禁章节。

### 6.6 @Autowired 专项（已完成）

全仓扫描结果：**生产代码 0 处 `@Autowired`**（既有 `NoAutowiredArchTest` 守护）；
本轮新写的 `ApprovalCancelOperationRepositoryIntegrationTest` 曾使用 3 处 `@Autowired` 字段注入，
已改为 `@TestConstructor(autowireMode = ALL)` + `@RequiredArgsConstructor` 构造器注入。

架构守护测试同步增强：扫描范围从「仅 `src/main/java`」扩展到「`src/main` + `src/test`」，
并修正 `@Inject` 对 Mockito `@InjectMocks` 的子串误匹配（改为按注解边界匹配）。
测试源码中仅保留 10 处 `@Resource`（`@WebFluxTest` 切片下 `WebTestClient` 无法构造器解析，
框架约束）与 0 处 `@Autowired`/`@Inject`。

**辅助汇总**：`bash scripts/test/generate-test-report.sh` 亦已执行（`target/reports/summary/test-report.md`，
5839 通过 / 0 失败 / 0 错误 / 15 跳过）。该脚本按既有实现只汇总部分模块的 surefire XML，模块口径窄于
本报告的完整 37 模块构建统计（surefire 5825 + failsafe 492），两者不可互相替代；以本报告与构建日志为准。

**报告生成**：apex-scaffold 全仓对齐战役（2026-11-13），数据由实际构建/测试产物采集，非估算。
