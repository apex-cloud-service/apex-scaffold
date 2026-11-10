# 微服务加固手册（Microservice Hardening Guide）

> **性质**：Phase 7 加固（`docs/08-project-management/plans/microservice-migration-plan.md` 第 7 章 / 执行计划 §7）配套文档。
> **范围**：容量规划、混沌演练、全链路压测、告警调优与 runbook、值班手册、`apex-common` SDK 升级指南。
> **依据**：执行计划 §7.1–7.5 与 §9 验收「可观测与运维」项；基础设施已随仓库提供
> （`config/monitoring/`、`config/nacos/`、`k8s/`、`podman-compose.yml`、`podman-compose.infra.yml`）。
> **约束**：所有命令遵循仓库规范，构建一律使用 `mvnd`（禁用 `mvn`）。

---

## 1. 目标架构与加固范围

目标为 **API 网关 + 6 个业务微服务 + 共享 SDK/契约模块**：

| 角色 | 模块 | 端口 | 副本基线 |
|---|---|---|---|
| API 网关 | `apex-gateway` | 8080 | 2 |
| 认证服务 | `apex-auth-service` | 8081 | 2 |
| 系统服务 | `apex-system-service` | 8082（管理 8083） | 2 |
| 工作流服务 | `apex-workflow-service` | 8093 | 1 |
| 办公服务 | `apex-office-service` | 8084 | 1 |
| 文件服务 | `apex-file-service` | 8085 | 1 |
| 集成服务 | `apex-integration-service` | 8094（管理 8097） | 1 |

> 现状：六个业务服务与网关均已独立运行（各自启动类、独立数据库与 Flyway 版本链），本节的加固口径对全部服务统一适用。任务代理（8095）与脱敏服务（8096）为轻量侧车，未注册 Nacos，走固定受信地址。

加固内容对应执行计划 §7：

- **7.1 混沌测试**：杀 auth 看降级、杀任一业务服务看其余域正常 + 故障注入。
- **7.2 容量规划**：每服务 JVM 1G 起步、网关/auth 2 副本、HPA。
- **7.3 告警调优 + runbook/值班手册/SDK 升级指南**。
- **7.4 Grafana 四类 dashboard + 告警规则上线、Zipkin traceId 贯通验证**。
- **7.5 K8s 清单 + podman-compose**（gateway + 六个业务服务 + mysql/redis/rocketmq/nacos/minio）。

---

## 2. 容量规划（§7.2）

每业务服务 JVM **1G 起步**（`-Xms512m -Xmx1g`），网关/认证 **2 副本**起步；生产按压测基线扩缩。

### 2.1 JVM 参数模板

各服务 `JAVA_OPTS` 统一采用（与 `k8s/deployment.yaml` / `Containerfile` 口径一致）：

```bash
JAVA_OPTS="-Xms512m -Xmx1g -Xss512k -XX:MaxDirectMemorySize=256m \
  -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m \
  -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/heapdump.hprof \
  -XX:+ExitOnOutOfMemoryError \
  -Xlog:gc*:file=/app/logs/gc.log:time,uptime,level,tags:filecount=5,filesize=20m \
  -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom"
```

### 2.2 副本与 HPA 基线

| 服务 | 最小副本 | 最大副本 | HPA 指标 |
|---|---|---|---|
| `apex-gateway` | 2 | 10 | CPU 70% / 内存 80% |
| `apex-auth-service` | 2 | 6 | CPU 70% / 内存 80% |
| 业务服务（workflow/office/file/system） | 1–2 | 4–10 | CPU 70% / 内存 80% |

> 单体时代 `k8s/deployment.yaml` 的 HPA（min 2 / max 10、CPU 70%）作为参考模板；
> 微服务化后每个服务应拥有独立的 Deployment + Service + HPA（`k8s/` 目录按服务拆分）。

---

## 3. 混沌演练（§7.1）

> 混沌目标不是「制造故障」而是「验证降级路径与隔离性」。以下演练在**演练环境**执行，不得在生产直接执行。
> 配合 `config/monitoring/` 大盘与告警观察指标，演练结束归档到 `docs/06-testing/` 测试报告段。

### 3.1 场景 A：杀 auth 看降级

```bash
# 1) 定位 auth 进程并 kill -9 模拟硬宕机
pkill -9 -f apex-auth-service || true

# 2) 观察（PromQL，可在 Grafana Explore 执行）：
#    up{application="apex-scaffold",module="auth-service"} == 0  → 实例已摘除
#    ubi_summary / 网关 5xx 曲线                        → 依赖方是否有兜底
#    probe_success{job=~"blackbox.*auth.*"} == 0          → Blackbox 探测失败

# 3) 预期结果（§9 韧性项）：
#    - 网关对 /auth/** 返回 401/503 且不拖垮其它路由；
#    - 其余业务服务（workflow/office/file）独立可服务，不受 auth 宕机影响（服务间无同步依赖）；
#    - 本地权限缓存/降级生效，读接口不因 auth 异常雪崩。
#    断言：其余域 P95 增加 ≤ 单体基线 ×1.3。

# 4) 恢复：重启 auth，观察重新注册与流量恢复；销毁故障注入。
```

### 3.2 场景 B：杀任一业务服务看其余域正常

```bash
# 以 workflow 为例
pkill -9 -f apex-workflow-service || true
curl -sf http://127.0.0.1:8093/actuator/health || echo "期望：不可达"
# 验证其它域（file/office/auth/system/integration）健康、网关其余路由正常
for p in 8080 8081 8083 8084 8085 8097; do curl -sf "http://127.0.0.1:${p}/actuator/health" >/dev/null && echo "OK ${p}" || echo "FAIL ${p}"; done
```

### 3.3 故障注入清单

| 注入点 | 手段 | 预期保护 |
|---|---|---|
| 依赖服务不可达 | 停服务 / 拒绝连接 | Resilience4j 熔断、超时降级 |
| 数据库连接耗尽 | 注入慢查询 / 缩池 | 连接池水位告警、限流 |
| Redis 不可达 | 停止 Redis 节点 | 人工巡检 `redisson_healthy`、本地缓存降级（无内置告警） |
| RocketMQ 堆积 | 停消费者 / 灌量 | DLQ / 消费追赶告警、Outbox 兜底 |
| 单副本被杀 | `kill -9` | K8s ReplicaSet 自动拉起、`pod-disruption-budget.yaml` 保障 |

> 标准混沌脚本：`scripts/test/chaos-fault-injection.sh`（见 §6），可一键执行场景 A/B 并输出断言结果。

---

## 4. 全链路压测（§7.1 / §9 韧性与性能）

压测目标：**P95 ≤ 单体基线 ×1.3**；网关单点自动摘除；吞噬峰值不雪崩。

### 4.1 工具链

| 场景 | 工具 | 脚本 |
|---|---|---|
| 接口级 wrk | [wrk](https://github.com/wg/wrk) | `scripts/bench/bench.sh` |
| 全链路 JMeter | JMeter | `scripts/performance/jmeter/api-load-test.jmx` |
| 压测驱动 | — | `scripts/performance/run-load-test.sh` |
| 数据基准基线 | 2026-10-05 全量与分模块报告 | `docs/06-testing/` |

### 4.2 执行步骤

```bash
# 1) 单体基线（改造前记录）→ 微服务终态各自记录，统一口径：
./scripts/bench/bench.sh http://127.0.0.1:8080 60s 8 400
BENCH_SCENARIO=login BENCH_LOGIN_ENABLED=true \
  BENCH_USERNAME=<account> BENCH_PASSWORD=<pwd> ./scripts/bench/bench.sh http://127.0.0.1:8080 60s 8 400

# 2) 全链路（gateway 入口 → 六个业务服务）：
./scripts/performance/run-load-test.sh http://127.0.0.1:8080

# 3) JMeter 完整场景（登录/工作流发起/审批/文件上传/办公消息）：
jmeter -n -t scripts/performance/jmeter/api-load-test.jmx \
  -Jhost=127.0.0.1 -Jport=8080 -Jthreads=100 -Jduration=600
```

### 4.3 通过标准

> 记录改造后终态 P95、P99、吞吐、错误率，与第 4.2 章「单体基线」对比归档。

- 网关引入新增延迟（线程池/过滤器链）对 P95 增幅 ≤ **15ms**。
- 全链路 P95 ≤ 单体基线 × **1.3**。
- 错误率 < 0.1%；5xx 不扩散（熔断生效时对应指标短暂上升后回落）。

---

## 5. 告警调优与 Runbook（§7.3）

### 5.1 告警规则来源

规则文件 `config/monitoring/prometheus/alert-rules.yml`（现有组）：

- `jvm_deep_monitoring`：Metaspace / CodeCache / `jvm_gc_pause_seconds_max` 最大暂停。
- `sentinel_governance`：`SentinelBlockQpsHigh` / `SentinelResourceRtHigh`
  （`sentinel_metrics{type="blockQps"}`、`sentinel_metrics{type="rt"}`）。
- `gateway_performance`：慢请求(>3s)、连接池水位（`reactor_netty_connection_provider_*`）。
- `circuit_breaker`：熔断器 Open、失败率警戒线。
- `database_monitoring`：连接池水位、慢 SQL（`apex_sql_slow_count_total` /
  `apex_sql_slow_max_ms`）。

> 当前规则集为 5 组 13 条，**不包含 Redis 与独立服务可用性规则**；`probe_success`
> 由 Blackbox 采集但未用于内置告警，Redis 与可用性需人工巡检或外部探针。
> 下列按服务分组仍属**规划**，未在当前 `alert-rules.yml` 中实现：
> `auth_availability`（登录失败率）、`outbox_backlog`（Outbox 堆积）、
> `mq_consumer_lag`（消费追赶）、`per_service_availability`（每服务 `probe_success`）。
> 阈值调整遵循「先 info→warning→critical」灰度，禁止直接改动生产阈值。

### 5.2 告警升级路径

| 严重级 | 通道 | 升级时限 |
|---|---|---|
| critical | 计划接即时电话/短信（值班） | 15 分钟内响应 |
| warning | 计划接钉钉/企业微信/邮件 | 1 小时内响应 |
| info | 计划入库、工作时段复盘 | — |

Alertmanager 已由 Compose 启用并加载 `config/monitoring/prometheus/alertmanager.yml`，
但 4 个 receiver 当前均为占位 webhook，**真实通知触达尚未验证**；生产必须替换为可达渠道
并完成端到端触达测试。接入说明见 `config/monitoring/alertmanager/README.md`。

### 5.3 Runbook（高优先级告警处置手册）

| 告警 | 可能根因与处置 |
|---|---|
| `CircuitBreakerOpen` | 依赖服务故障→查下游健康/连接池；确认后人工或在 Nacos 调整熔断参数；验证恢复后关闭 |
| `SlowRequestsExcessive` | 慢 SQL→查 `apex_sql_slow_count_total` / `apex_sql_slow_max_ms` 与 DBA 慢日志；线程池排队→查 `reactor_netty_connection_provider_*` |
| `DBConnectionPoolUsageHigh` | 连接泄漏/缩池→临时调 pool-size，Processor 查 `hikaricp_connections_idle` |
| Redis 不可达（人工检查，无内置告警） | `redisson_healthy==0`→查 Redis 节点；读多写少服务应走本地缓存降级 |
| `SentinelBlockQpsHigh` / `SentinelResourceRtHigh` | 流量突增或规则过严→Nacos 调整 `{service}-flow-rules.json`（热更新）；当前 exporter 仅覆盖 system-service |
| `GCPauseTimeCritical` | 堆/堆外过小→按 §2.1 扩容 JVM，必要时水平扩容 |
| `MetaspaceUsageCritical` | 类加载过多→检查反射/动态代理，扩容 Metaspace |

### 5.4 值班手册（On-Call）

1. **第一响应（0–15min）**：确认告警→定位到服务与指标图→判断影响面（网关/DB/Redis/MQ）。
2. **止血（15–30min）**：按 §5.3 处置；需要降级时优先 K8s 缩副本 / 调整灰度比例回滚。
3. **根因（30min+）**：抓 `heapdump.hprof`/`gc.log`/Zipkin traceId 现场；进入标准故障排查流程。
4. **归档**：故障复盘按 `docs/08-project-management/` 规范归档，更新 Runbook 与告警阈值。

> 完整交付物：`docs/05-operations/oncall-runbook.md`（§7 值班手册细化版）。

---

## 6. 加固脚本（随仓库提供）

| 脚本 | 作用 | 依赖 |
|---|---|---|
| `scripts/bench/bench.sh` | wrk 压测（health/login） | wrk |
| `scripts/performance/run-load-test.sh` | 全链路压测驱动 | jmeter |
| `scripts/test/chaos-fault-injection.sh` | 混沌场景 A/B 一键演练 + 断言 | bash、curl |
| `scripts/ops/monitoring/start-monitoring.sh` | 拉起监控栈 | podman-compose |
| `scripts/ops/monitoring/grafana/import-dashboard.sh` | 导入 Grafana 大盘 | jq、curl |

```bash
# 混沌演练（场景 A/B，-y 确认，输出为 0/1 供 CI 断言）
./scripts/test/chaos-fault-injection.sh --scenario auth-down --yes
./scripts/test/chaos-fault-injection.sh --scenario workflow-down --yes

# 语法校验（本手册要求所有脚本先通过 bash -n）
bash -n scripts/test/chaos-fault-injection.sh
```

---

## 7. Grafana 四类大盘与 traceId 贯通（§7.4）

### 7.1 四类 dashboard

| 类型 | 现有配套 | 说明 |
|---|---|---|
| 网关 | `apex-dashboard.json` / `apex-dashboard-enhanced.json` | 请求量、慢请求、连接池、过滤器链耗时 |
| 每服务 | 同上 + 各服务实例标签分组 | JVM、Sentinel、DB 池、自定义 `apex_api_*` |
| 每库 | DB 面板（`hikaricp_*`）/（prod `druid_*`） | 连接池水位、慢查询 |
| 每 topic | （MQ 消费追加大盘，需按 RocketMQ exporter 补） | 消费滞后、DLQ |

> 部署：`podman-compose -f config/monitoring/podman-compose.yml up -d`；
> 置备：`scripts/ops/monitoring/grafana/import-dashboard.sh`（Grafana 11 要求按组件子目录存放）
> + `config/monitoring/grafana/provisioning/datasources/`（Prometheus/Zipkin 自动透配）。

### 7.2 Zipkin traceId 贯通验证

```bash
# 1) 发起一次跨服务请求（如 gateway → auth → workflow）
curl -s -H "X-Trace-Id: $(uuidgen)" http://127.0.0.1:8080/api/v1/workflows/... 

# 2) 在 Zipkin（默认 http://localhost:9411）按 X-Trace-Id 检索，断言：
#    - 同一 traceId 覆盖网关 + 认证 + 工作流 ≥3 个 span；
#    - 每 span 的 serviceName、parentId 链完整；
#    - 应用侧 X-Trace-Id 头 / MDC（`traceId`）/ Zipkin traceId 三处一致（见 config/monitoring README）。
```

---

## 8. SDK 升级指南（`apex-common`）

`apex-common` 独立发版为 SDK（`docs/05-operations/configuration.md`、执行计划 §1.1.3 SDK 准入规则）。

### 8.1 基线约定

- `apex-common` **允许**：`Result` / 异常体系 / `TenantContext` / 注解 / 工具类、`SystemConstants`。
- `apex-common` **禁止**：Redis/MQ/Storage 实现、业务实体、任何 `javax/jakarta` 业务依赖。

### 8.2 升级流程（消费方 `apex-*` 服务）

```bash
# 1) BOM 锁版本（apex-services-bom dependencyManagement 更新 apex-common 版本）
# 2) 消费方 pom 不需显式写版本（继承 BOM）；显式覆盖仅限灰度验证
# 3) 全量门禁（每服务）：
mvnd -pl <service> -am verify
# 4) 兼容性检查：Feign 跨服务契约签名一致性（CI 校验）、ArchUnit（服务间无编译依赖）
```

### 8.3 破坏性变更规范

- 任何 breaking change 必须在 `CHANGELOG.md` 标注并给出迁移指引。
- SDK 升级遵循「内容准入规则」收口：新增内容先评审是否属冒泡到 SDK，遵守 `apex-common/README.md`。
- 跨服务契约变更必须**同步更新** `apex-api-contract` 并提供方/消费方一起验证（CI 签名校验）。

---

## 9. 加固验收对照

| 执行计划 §9 验收项 | 本节落点 | 状态 |
|---|---|---|
| 故障注入符合预期 | §3 混沌演练 | 见演练归档 |
| 网关单点自动摘除 | §3.1 / §2.2（HPA、多副本） | 已具备清单 |
| P95 ≤ 单体基线 ×1.3 | §4 压测 | 见压测归档 |
| MQ/Outbox/DLQ 告警与消费追赶 | §5.1 告警规则 | 规则在 alert-rules.yml |
| traceId 全链路贯通（Zipkin） | §7.2 | 见验证记录 |
| 四类 Grafana dashboard + 告警上线 | §7.1 / §5 | 已随 config/monitoring 提供 |
| 灰度切流与一键回滚演练通过 | 执行计划 Phase 2.4 灰度 | 见测试报告 |
| 交付 runbook/SDK 升级指南 | §5.3 / §8 | 本文档 |