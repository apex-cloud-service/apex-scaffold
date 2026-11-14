# Grafana 监控大盘使用指南

## 功能说明

提供预配置的 Apex Scaffold 监控大盘（`apex-dashboard.json`），覆盖服务可用性、
接口质量（QPS / 延迟分位数 / 错误率）、JVM 健康、数据库连接池、慢 SQL、MQ/Outbox、
Sentinel 治理与安全防护指标。

> **指标口径说明**：接口指标来自项目自定义的 `apex_api_*`（由
> `apex-system/src/main/java/com/apex/scaffold/interfaces/filter/MetricsWebFilter.java`
> 上报），不是 Spring MVC 的 `http_server_requests_*`。WebFlux 部署以本大盘为准。

## 快速开始

### 方式一：Podman Compose 一键启动（推荐）

```bash
cd config/monitoring
podman-compose -f podman-compose.yml up -d
```

容器启动时通过 [auto-provisioning](./provisioning/) 自动完成：

1. 注册 **Prometheus** 数据源（`http://prometheus:9090`，**uid 固定为 `prometheus`**）
2. 导入 **Apex Scaffold 监控大盘**（`apex-dashboard.json`）与增强大盘
   （`apex-dashboard-enhanced.json`）

> **自动置备目录结构（勿改）**：Grafana 11 要求置备 YAML 按组件子目录存放，
> 即 `provisioning/dashboards/dashboards.yml` 与 `provisioning/datasources/datasources.yml`。
> 若直接放在 `provisioning/` 根目录，Grafana 会静默忽略，表现为没有面板。
>
> **数据源 uid 约定**：`datasources.yml` 将 Prometheus 的 uid 固定为 `prometheus`，
> 大盘 JSON 中全部面板以 `"uid": "prometheus"` 直引（文件置备不会解析
> `__inputs` 模板变量，因此大盘 JSON 中不使用 `${DS_PROMETHEUS}`）。

启动后访问：

| 组件 | 地址 | 默认账号 |
|------|------|----------|
| Grafana | http://localhost:3200 | admin / admin（首次登录提示修改） |
| Prometheus | http://localhost:9090 | - |
| Alertmanager | http://localhost:9093 | - |
| Zipkin | http://localhost:9411 | - |
| Blackbox Exporter | http://localhost:9115 | - |

### 方式二：手动导入大盘

1. 登录 Grafana → **Dashboards** → **New** → **Import**
2. 上传 `config/monitoring/grafana/apex-dashboard.json`
3. 数据源选择 **Prometheus**（需预先添加，URL 指向实际 Prometheus 地址）
4. 点击 **Import**

## 大盘面板说明

主大盘 `apex-dashboard.json`（UID `apex-scaffold-dashboard`）共 **96 项面板元素**：
**12 个 row 分组、84 个数据面板**。主要分组如下：

| 分组 | 面板示例 | 主要指标 |
|------|----------|----------|
| 顶部状态行 | 服务状态 / 请求吞吐 / P95 / 5xx 错误率 / JVM 堆 / DB 池水位 | `up`、`apex_api_*`、`jvm_*`、`hikaricp_*`/`druid_*` |
| HTTP 接口指标 | 请求量按状态码 / 响应时间分位数 | `apex_api_request_count_total`、`apex_api_response_time_seconds_bucket` |
| JVM / 数据库 / 治理 | JVM 堆内存 / 连接池 / GC 频率 / 连接池等待超时 / Sentinel | `jvm_*`、`hikaricp_*`、`druid_*`、`sentinel_metrics` |
| 系统资源（磁盘/网络） | 磁盘使用率 / 网卡状态 | `apex_disk_*`、`apex_network_*` |
| 中间件（Redis/缓存） | Redis 健康 / 节点数 / 缓存命中率 / 命中未命中 | `redisson_healthy`、`redisson_nodes_total`、`apex_cache_gets_total` |
| 治理与安全 | 限流拒绝 / 熔断触发 / 活跃会话 / 安全拦截 | `apex_security_*` |
| 核心业务 | 登录 / 注册 / 上传 / 外部 API QPS / 成功率 / 耗时 | `apex_business_*_total`、`apex_api_call_*` |
| Zipkin 链路追踪 | Zipkin 在线 / Span 接收 / Span 丢弃 | `zipkin_collector_*`（`zipkin:9411/prometheus`） |
| JVM 详细（CPU/线程/类加载） | CPU / 线程 / 类加载 / 非堆内存 / 缓冲区 | `process_cpu_usage`、`jvm_threads_*`、`jvm_classes_*`、`jvm_memory_*` |
| 数据库（慢 SQL） | 慢 SQL 次数 / 最长耗时 / SQL 平均耗时 | `apex_sql_slow_count_total`、`apex_sql_slow_max_ms`、`apex_sql_execution_seconds_*` |
| 消息中间件（RocketMQ/Outbox） | MQ 发送 QPS / 失败率 / 耗时 / Outbox 积压 | `apex_mq_publish_*`、`apex_outbox_records`、`apex_outbox_metrics_up` |
| 异步响应 / 幂等重试 | 响应超时 / SSE / 幂等 / 乐观锁重试 | `apex_response_timeout_*`、`apex_sse_*`、`apex_idempotent_*`、`apex_retry_optimistic_*` |
| 异常监控 | 异常总量 / 告警次数 / 按级别和类型趋势 | `apex_exception_total`、`apex_exception_alert_count`、`apex_exception_count_total` |

增强大盘 `apex-dashboard-enhanced.json`（UID `apex-enhanced`）共 **14 项面板元素**：
**2 个 row 分组、12 个数据面板**，聚焦 WebFlux/Reactor Netty 与 JVM 深度指标，详见
[README-ENHANCED.md](README-ENHANCED.md)。

### 顶部状态行（Stat）

| 面板 | PromQL 要点 | 阈值 |
|------|-------------|------|
| 服务状态 | `up{application="apex-scaffold"}` | 1=UP / 0=DOWN |
| 请求吞吐 | `sum(rate(apex_api_request_count_total[5m]))` | - |
| P95 响应时间 | `histogram_quantile(0.95, sum(rate(apex_api_response_time_seconds_bucket[5m])) by (le)) * 1000` | >2s 红色 |
| 5xx 错误率 | 5xx 计数 / 总计数 | >5% 黄色，>10% 红色 |
| JVM 堆使用率 | `sum(jvm_memory_used_bytes{area="heap"}) / sum(jvm_memory_max_bytes{area="heap"}) * 100` | >85% 红色 |
| DB 连接池水位 | `(sum(hikaricp_connections_active) or sum(druid_connections_active)) / (sum(hikaricp_connections_max) or sum(druid_connections_max)) * 100` | >80% 红色 |

## 告警规则

告警规则位于 `config/monitoring/prometheus/alert-rules.yml`，当前共 **5 个 group、13 条规则**：

| 告警 | 阈值与持续时间 | 级别 |
|------|----------------|------|
| `MetaspaceUsageHigh` | Metaspace 使用率 > 85%，5m | warning |
| `MetaspaceUsageCritical` | Metaspace 使用率 > 90%，2m | critical |
| `CodeCacheUsageHigh` | CodeCache 使用率 > 80%，10m | warning |
| `GCPauseTimeHigh` | `jvm_gc_pause_seconds_max` > 0.5s，5m | warning |
| `GCPauseTimeCritical` | `jvm_gc_pause_seconds_max` > 1.0s，2m | critical |
| `SentinelBlockQpsHigh` | `sentinel_metrics{type="blockQps"}` > 10，5m | warning |
| `SentinelResourceRtHigh` | `sentinel_metrics{type="rt"}` > 1000ms，5m | warning |
| `SlowRequestsExcessive` | 响应时间 > 3s 的请求占比 > 10%，10m | warning |
| `ConnectionPoolUsageHigh` | Reactor Netty 连接池使用率 > 80%，5m | warning |
| `CircuitBreakerOpen` | 熔断器 state=open，1m | critical |
| `CircuitBreakerFailureRateHigh` | 熔断器失败率 > 50%，5m | warning |
| `DBConnectionPoolUsageHigh` | HikariCP 连接池使用率 > 80%，5m | warning |
| `DBSlowQueriesExcessive` | `rate(apex_sql_slow_count_total[5m])` > 0.1/s，5m | warning |

> **指标口径说明**：
> - 数据库连接池：本地/开发通常使用 HikariCP（`hikaricp_*`）；大盘对 Druid 提供
>   `or` 兼容查询，但当前告警规则仅覆盖 HikariCP。Redis 与 Blackbox 健康探针目前
>   有采集、无内置告警规则，需人工检查或按实际渠道补充规则。
> - 慢 SQL：由 MyBatis 拦截器上报 `apex_sql_slow_count_total` 与
>   `apex_sql_slow_max_ms`，阈值由 `apex.sql.slow.threshold-ms` 配置。
> - 健康探针：`probe_success` 由 Blackbox Exporter 探测 `/actuator/health` 提供；
>   Compose 已内置 Blackbox 与 Prometheus 抓取任务。
> - Alertmanager 已由 Compose 启动并加载
>   `config/monitoring/prometheus/alertmanager.yml`。当前 4 个 receiver 均为占位
>   webhook（`http://localhost:8080/webhook/alert*`）；容器内 `localhost` 指向
>   Alertmanager 自身，实际通知触达**尚未验证**，生产必须替换为可达渠道并做触达测试。

启用告警的两种方式：

- **本地演示**：在仓库根目录执行
  `podman-compose -f config/monitoring/podman-compose.yml up -d` 后，Prometheus
  自动加载 `alert-rules.yml`，可在 Prometheus **Alerts** 页查看规则状态，在
  Alertmanager（`http://localhost:9093`）查看路由结果。
- **生产**：将 `alert-rules.yml` 与经脱敏的 Alertmanager 配置挂载到生产实例，
  并将 receiver 替换为钉钉/企业微信/邮件等真实通知渠道。

## 多实例监控

Prometheus 按服务拆分为独立 job：`apex-gateway`、`apex-auth-service`、
`apex-system-service`、`apex-workflow-service`、`apex-office-service`、
`apex-file-service`、`apex-integration-service`、`apex-aggregation-service`。所有应用指标统一带
`application="apex-scaffold"`，并以 `module="gateway|auth-service|system-service|..."` 区分服务。

部署多实例时：

1. 在 `prometheus.yml` 对应服务的 `static_configs.targets` 下追加实例地址
2. 在大盘添加模板变量 `instance`（`label_values(up{application="apex-scaffold"}, instance)`）
3. 在 PromQL 中按需追加 `instance="$instance"` 或 `module="$module"` 过滤

> 当前 Compose 的 `host.containers.internal` 抓取方式面向宿主机应用；容器化应用
> 部署时需将 target 改为容器服务名。Sentinel exporter 当前指向
> `host.containers.internal:9092`，标签为 `module="system-service"`。

## 自定义大盘

- 编辑 `config/monitoring/grafana/apex-dashboard.json` 后等待置备同步，或重启 Grafana 容器
- 常用 PromQL 参考：
  - P99 响应时间：`histogram_quantile(0.99, sum(rate(apex_api_response_time_seconds_bucket[5m])) by (le))`
  - Top 10 慢接口：`topk(10, apex_api_response_time_seconds_sum / apex_api_response_time_seconds_count)`
  - 活跃线程数：`jvm_threads_live_threads`
  - Redis 节点健康：`redisson_healthy`（0 = 节点不可达）
  - 慢 SQL 速率：`sum(rate(apex_sql_slow_count_total[5m]))`

## 注意事项

1. **数据保留**：Podman Compose 预设 Prometheus 保留 15 天，按需调整
   `--storage.tsdb.retention.time`
2. **面板规模**：主大盘 96 项元素（84 个数据面板），按 12 个 row 折叠；增强大盘
   14 项元素（12 个数据面板）。性能开销需结合实际采集量和浏览器性能评估。
3. **权限管理**：生产环境务必修改 Grafana 默认口令并配置 SSO / 权限
4. **管理端口安全**：各服务监控端点分别位于 gateway `8080`、auth `8081`、
   system `8083`、workflow `8093`、office `8084`、file `8085`、integration `8097`、
   aggregation `8098`；
   生产应仅在内网或受控网络暴露。
5. **告警通道**：规则已定义且 Alertmanager 已编排，但占位 webhook 的真实触达未验证。

## 参考资料

- [Grafana 官方文档](https://grafana.com/docs/)
- [Prometheus 查询语言](https://prometheus.io/docs/prometheus/latest/querying/basics/)
- [Micrometer 指标文档](https://micrometer.io/docs/concepts)
