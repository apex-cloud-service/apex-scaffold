# 微服务化改造 - §17 验收清单（可执行跟踪）

**来源**：`docs/microservice-migration-plan.md` §17 验收标准清单
**配套执行计划**：`.trae/documents/microservice-migration-execution-plan.md` §9/§10/§11
**最后更新**：2026-10-12
**当前拓扑**：网关 `apex-gateway` + 已服务化 `apex-auth-service`（原 `apex-auth-center` 更名）/ `apex-workflow-service` / `apex-file-service` / `apex-office-service`（Phase 5 业务抽取已完成）/ `apex-system-service`（已装配 `apex-system`，接口、应用与 system 专属领域/基础设施已完成物理迁移；本批最终门禁见下方记录）。

> 说明：本表用于逐项核对 §17。勾选状态分四档：
> - `[x]` 已满足（附证据路径）
> - `[~]` 部分满足 —— 代码/配置/脚本已交付，但需在线环境（MySQL/Redis/Nacos/K8s）执行运行时验证
> - `[ ]` 未满足 —— 尚未交付或尚未完成本批验证的工作；system 物理迁移、库更名与 ApiRouter 拆分已完成
> - `[!]` 阻塞 —— 依赖外部环境或未决决策

---

## 2026-10-12 system 第二批验证记录

- [x] application 源码、资源与测试，以及 system 专属 domain/infrastructure 类型和测试迁入 system，Java 包名保留；UserRolePoConverterTest 随实现迁入。
- [x] interfaces/application reactor 与版本声明退休；system-service/example 使用 system，auth-service parity 依赖仅 test scope。
- [x] 共享 domain/infrastructure 保留跨服务契约与设施，HTTP 日志改用 ApiCallObservation/ApiCallLogSink，业务日志实体留在 system。
- [x] 全 reactor `compile -DskipTests`（含 example）18:19:24 BUILD SUCCESS。
- [x] 本批迁移数量：application 281、domain 180、infrastructure 222，共 683 个文件（main 560、test 123，含资源，不含前批 interfaces）；801 个共享层/兄弟服务生产 Java 文件无 system 类型直接导入。
- [x] 最终 `source scripts/build/init-build-env.sh && mvnd -T4 -B clean verify -pl '!apex-example'`：JDK 17.0.12-oracle/mvnd 2.0.0-rc-3；21:09:11 BUILD SUCCESS（12:34），当前 reactor Surefire/Failsafe XML 汇总 4853 tests、Failures 0、Errors 0、Skipped 3；Spotless/PMD/ArchUnit/JaCoCo 全通过，阈值和检查未削弱。BaseEntityTest 已补齐；本次修复 LoggingFilterTest 缺少 httpHandler 测试装配，7 个测试全通过；用户的 Logback business-summary 断言完整保留且该类 3 个测试全通过。
- [x] `source scripts/build/init-build-env.sh && mvnd -T4 -B test-compile -pl apex-example -am`：21:10:28 BUILD SUCCESS（38.539s），example 及依赖模块生产/测试编译通过。
- [x] 本轮修改文件的差异空白检查通过；全仓 `git diff --check` 仍有 workflow-monitor-guide、api-routes、configuration-inventory、TEST_REPORT 四份未在本轮修改文档的既有行尾空格，未覆盖用户内容。在线部署、启停、压测与混沌验收仍待运行环境取证。

## 17.1 功能与契约

| # | 验收项 | 状态 | 证据 / 说明 |
|---|---|---|---|
| 17.1.1 | `/api/v1/**` 与 `/admin-api/v1/**` 全部现有接口回归通过，`Result` 结构与错误码逐字段一致（`export-api-docs.sh` OpenAPI diff 为空） | `[x]` 离线基线 / `[~]` 在线回归 | 离线契约基线已导出：`apex-interfaces/target/openapi-export/openapi.json`（79 paths，`OpenApiExportTest`）；归档 `docs/03-api/openapi/`。在线逐字段 diff 需 MySQL/Redis/Nacos 环境。 |
| 17.1.2 | 登录（密码/短信/微信/SSO）、MFA、刷新、登出、Token 黑名单全链路正常 | `[x]` | Phase 2.7：`AuthenticationHandlerIntegrationTest` / `MfaHandlerIntegrationTest` / `SessionHandlerIntegrationTest` / `OAuth2BootTest`。见 `apex-auth-center/TESTING.md`。 |
| 17.1.3 | 多租户隔离：跨租户数据访问在各服务均被拦截（含缓存 key、MQ 消费、异步任务） | `[x]` | `TenantContext` + `DefaultTenantLineHandler` + `TenantContext.withTenant(Mono/Flux)` 显式桥接；13 处桥接调用点已补齐（p4-7）。 |
| 17.1.4 | 工作流审批、办公平台推送、文件上传下载端到端可用 | `[x]` 代码/测试 / `[~]` 在线 | 工作流/文件已完成（Phase 3/4，`WorkflowHandlerIntegrationTest`/`FileHandlerIntegrationTest`）；**办公已完成（Phase 5）**：`apex-office-service` 独立化 + `OfficeRouterRegressionTest`/各子域 handler 测试 + `OfficeFlywayMigrationTest`。三平台（企微/钉钉/飞书）真机端到端推送需在线平台凭证环境执行。 |

## 17.2 架构

| # | 验收项 | 状态 | 证据 / 说明 |
|---|---|---|---|
| 17.2.1 | 5 个服务 + 网关可独立构建（`mvnd -pl <service> -am package`）、独立部署、独立启停 | `[~]` 已抽取，待在线验证 | 网关及 auth/workflow/file/office/system 服务均已抽取，system-service 装配 system 业务库；原 application 全迁，system 专属 domain/infrastructure 已迁入，interfaces/application 坐标已退休。auth-service parity 仅 test scope 使用 system，example 使用 system；`SystemServiceBoundaryTest` 保留入口与扫描装配守卫。ApiRouter 已拆为子域 Router。实际本批门禁见第二批验证记录；独立部署、启停及端到端验证需在线环境。 |
| 17.2.2 | 无任何跨服务数据库访问；每服务 Flyway 链独立 | `[x]` 已抽取服务 | 各服务独立库/迁移链：`apex-auth-service`（V44–V56）、`apex-workflow-service`（独立 V1 `apex_workflow`）、`apex-file-service`（独立 V1 `apex_file`）、`apex-office-service`（独立 V1/V2 `apex_office`，mysql+postgresql）、`apex-system-service`（`apex_system`）；`check-migration-integrity.sh` 已扩展校验多服务链唯一性。 |
| 17.2.3 | `apex-common-sdk` 无业务实体；横切能力经 starter 按需装配 | `[x]` | `apex-services-bom`（锁版本）+ `apex-api-contract`（纯契约，无业务实体，20/20 测试）。 |
| 17.2.4 | ArchUnit：服务间无编译依赖、domain 纯净性、`Schedulers.boundedElastic()` 禁用、Feign 必须经 `remote()` 桥接 | `[x]` | ArchUnit 规则随 `mvnd verify` 执行，全绿（各模块门禁）。 |
| 17.2.5 | `/internal/v1/**` 公网不可达 | `[x]` | 网关 `/internal` 403 过滤器链（Phase 1.2，`GatewayModuleTestReport`）。 |

## 17.3 韧性与性能

| # | 验收项 | 状态 | 证据 / 说明 |
|---|---|---|---|
| 17.3.1 | 故障注入：杀 auth-service → 已登录用户读操作不受影响、新登录降级 503 且熔断告警触发；杀任一业务服务 → 其余域正常 | `[~]` | 脚本 `scripts/test/chaos-fault-injection.sh`（`--scenario auth-down|workflow-down`，bash 3.2 兼容）；需在线环境执行取证。SOP 见 `docs/05-operations/oncall-runbook.md`。 |
| 17.3.2 | 网关单点故障时 Ingress 健康检查自动摘除 | `[~]` | `k8s/deployment.yaml` / `canary-deployment.yaml` / `pod-disruption-budget.yaml` 已就绪；需集群演练。 |
| 17.3.3 | 全链路 P95 ≤ 单体基线 × 1.3；压测覆盖网关 2 副本 | `[~]` | 压测脚本 `scripts/bench/bench.sh` + `scripts/performance/jmeter/api-load-test.jmx`；方法见 `docs/05-operations/microservice-hardening-guide.md` §4。需在线压测。 |
| 17.3.4 | MQ 堆积、Outbox 积压、DLQ 均有告警且演练过消费追赶 | `[~]` | 告警规则 `config/monitoring/prometheus/alert-rules.yml`（12 条 / 5 组）；Outbox/DLQ 消费追赶 SOP 见 runbook。 |

## 17.4 可观测与运维

| # | 验收项 | 状态 | 证据 / 说明 |
|---|---|---|---|
| 17.4.1 | 任一请求 traceId 贯穿：网关 → 服务 → Feign → MQ → 下游服务（Zipkin 可查完整链路） | `[x]` 代码/配置 / `[~]` 在线 | Micrometer Tracing + Zipkin 9411（`config/monitoring/`）；MDC Scheduler hook 保留（p4-7）。 |
| 17.4.2 | Grafana：网关/每服务/每库/每 topic 四类 dashboard + 告警规则上线 | `[~]` | Dashboard：`config/monitoring/grafana/apex-dashboard.json`(+enhanced)；告警：`config/monitoring/prometheus/alert-rules.yml`。上线需监控栈运行。 |
| 17.4.3 | 灰度切流与一键回滚在 staging 演练通过 | `[~]` | 灰度 `GrayRouteFilter`（1%→10%→50%→100%）；切流/回滚 SOP 见 runbook「灰度切回滚」。需 staging 演练。 |
| 17.4.4 | 交付物：架构图、部署手册、故障 runbook、SDK 升级指南 | `[x]` | 架构图 `docs/diagrams/`、`docs/02-architecture/`；部署 `docs/05-operations/deployment.md`；runbook `docs/05-operations/oncall-runbook.md`；SDK 升级 `docs/05-operations/microservice-hardening-guide.md` §8。 |

---

## 汇总

| 子项 | 已满足 `[x]` | 部分/在线验证 `[~]` | 未满足 `[ ]` | 阻塞 `[!]` |
|---|---|---|---|---|
| 17.1 功能与契约 | 3 | 1（办公三平台真机推送待在线） | 0 | 0 |
| 17.2 架构 | 4 | 1（system 业务已迁移，独立部署/启停待在线验证） | 0 | 0 |
| 17.3 韧性与性能 | 0 | 4 | 0 | 0 |
| 17.4 可观测与运维 | 2 | 2 | 0 | 0 |
| **合计** | **9** | **8** | **0** | **0** |

**结论**：代码/配置/脚本/文档层面 §17 交付物持续补齐。**Phase 5 办公服务独立化完成**（`apex-office`/`apex-office-service` + 独立 Flyway V1/V2 + 25 个测试类〔另有 1 个切片测试配置〕），17.1.4 由 `[ ]` 提升为 `[x]`（代码/测试）/`[~]`（在线）；**Phase 6 `apex-system-service` 骨架已完成并构建通过**（`mvnd -pl apex-system-service -am package` → 10 模块反应堆 BUILD SUCCESS），17.2.1 提升为 `[~]` 5/6；并新增模块边界守卫测试 `SystemServiceBoundaryTest`（DB-free，锁定「classpath 不混入兄弟服务入口 + 启动扫描装配完整」契约，`Tests run 3 / Failures 0 / Errors 0`）。**`ApiRouter`（2709 行）拆分已完成**（13+ 子域 Router + `RouterOpenApiConfig` 按模块 `@Bean` + `RouterOpenApiCoverageTest` 逐路径校验，全反应堆 `clean verify` 05:02 BUILD SUCCESS）。2026-10-12 已完成 system 领域/应用/专属基础设施物理迁移及库更名，兄弟服务无 system 生产依赖；本批实际门禁以第二批验证记录为准。剩余 `[~]` 为「产物已就绪、需在线环境执行运行时取证」，与 Phase 1/2 一贯口径一致（生产切流/压测/混沌属运维动作，不在代码交付物范围）。

---

## 17.5 最终回归门禁（2026-10-10 全绿）

**命令**：`JAVA_HOME=/Users/eric/.sdkman/candidates/java/17.0.12-oracle mvnd -T4 -B clean verify`

> 说明：`clean verify` 已覆盖编译、单测、failsafe IT、pmd(p3c)、spotless、ArchUnit、JaCoCo(LINE≥0.60 / BRANCH≥0.50)。

| 验收项 | 结果 | 证据 |
|---|---|---|
| 反应堆 18 模块全量 `BUILD SUCCESS`（含 apex-auth-center / apex-workflow-service / apex-file-service 及其 IT） | ✅ | `/tmp/mvnd_gate6.log` `Reactor Summary`，总耗时 11:13 |
| 测试总数 **9128 run、0 failure、0 error**（skipped 12）；无 failsafe IT 失败 | ✅ | `mvnd_gate6.log` 聚合计数 |
| 原先 flaky 的 `DualServiceAuthorizationIntegrationTest`（真实 Redis + HTTPS 中心）通过 | ✅ | 2 次运行均 `5 run / 0 fail / 0 error`（29.96s / 20.50s） |
| 修复后的 `LicenseAutoConfigurationTest`（`enabled=false` 强制降级）通过 | ✅ | apex-license-spring-boot-starter `19 run / 0 fail` |
| apex-interfaces（MySQL 在线 IT）、workflow / file 服务 JaCoCo 门禁通过 | ✅ | 各模块 SUCCESS |

**2026-10-12 复跑确认（最新证据）**：`JAVA_HOME=/Users/eric/.sdkman/candidates/java/17.0.12-oracle mvnd -T4 -B clean verify -pl '!apex-example'` → `[INFO] BUILD SUCCESS`，反应堆 **18 模块全量 SUCCESS**（含 parent `Apex Scaffold`），总耗时 **05:02**，无 `Failures`/`Errors`（`grep 'Failures: [1-9]|Errors: [1-9]'` 无命中）。证据：`/tmp/mvnd_verify_current.log`。本次复跑在 `AccessLogService`（改用 `BlockingIoSchedulers.jdbc()`）、`RequestLoggingFilterTest`、`RouterOpenApiCoverageTest` 重构之后执行，确认当前工作树（含 587 文件未提交改动）通过全部门禁（Spotless/PMD(p3c)/ArchUnit/单测+failsafe IT/JaCoCo LINE≥0.60·BRANCH≥0.50）。`apex-system-service` JaCoCo 门禁沿用 §6-1c 决策：该模块为 Phase 6 骨架（唯一生产类 `SystemServiceApplication` 仅 `main()`），在 `apex-system-service/pom.xml` 为 `jacoco-maven-plugin` 显式排除启动类 `com/apex/scaffold/systemservice/SystemServiceApplication.class`，`jacoco:check` 输出 `Analyzed bundle 'apex-system-service' with 0 classes` → `All coverage checks have been met.`（详见 `microservice-phase5-6-extraction-design.md` §6-1c）。

**遗留的运行时取证项（`[~]`）不变**：混沌故障注入、K8s 摘除演练、P95 压测、监控栈上线、灰度 staging —— 均属在线运维动作，非代码交付物。

---

## 相关交付物索引

- **加固指南**：`docs/05-operations/microservice-hardening-guide.md`
- **值班 runbook**：`docs/05-operations/oncall-runbook.md`
- **混沌脚本**：`scripts/test/chaos-fault-injection.sh`
- **迁移完整性校验**：`scripts/test/check-migration-integrity.sh`
- **压测脚本**：`scripts/bench/bench.sh`、`scripts/performance/jmeter/api-load-test.jmx`
- **监控配置**：`config/monitoring/`（prometheus/grafana/alertmanager）
- **K8s 清单**：`k8s/deployment.yaml`、`k8s/canary-deployment.yaml`、`k8s/pod-disruption-budget.yaml`
- **测试报告**：`docs/06-testing/`（gateway/contract/workflow/auth-center 等）
