# Grafana 监控大盘使用指南

## 功能说明

提供预配置的 Apex Scaffold 监控大盘（`apex-dashboard.json`），覆盖服务可用性、
接口质量（QPS / 延迟分位数 / 错误率）、JVM 健康、数据库连接池与 Sentinel 治理指标。

> **指标口径说明**：大盘基于项目自定义的 `apex_api_*` 指标（由 `MetricsWebFilter` 上报，
> 见 `apex-interfaces/.../filter/MetricsWebFilter.java`），而非 Spring MVC 的
> `http_server_requests_*`。WebFlux 下请以本大盘为准。

## 快速开始

### 方式一：Podman Compose 一键启动（推荐）

```bash
cd config/monitoring
podman-compose -f podman-compose.yml up -d
```

容器启动时通过 [auto-provisioning](./provisioning/) 自动完成：

1. 注册 **Prometheus** 数据源（`http://prometheus:9090`，**uid 固定为 `prometheus`**）
2. 导入 **Apex Scaffold 监控大盘**（`apex-dashboard.json`）

> **自动置备目录结构（勿改）**：Grafana 11 要求置备 YAML 按组件子目录存放，
> 即 `provisioning/dashboards/dashboards.yml` 与 `provisioning/datasources/datasources.yml`。
> 若直接放在 `provisioning/` 根目录，Grafana 会静默忽略（日志报
> `can't read dashboard provisioning files from directory ... no such file or directory`），
> 表现为访问 3000 端口没有任何面板。
>
> **数据源 uid 约定**：`datasources.yml` 将 Prometheus 的 uid 固定为 `prometheus`，
> 大盘 JSON 中全部面板以 `"uid": "prometheus"` 直引（文件置备不会解析
> `__inputs` 模板变量，因此大盘 JSON 中不再使用 `${DS_PROMETHEUS}`）。

启动后访问：

| 组件 | 地址 | 默认账号 |
|------|------|----------|
| Grafana | http://localhost:3000 | admin / admin（首次登录提示修改） |
| Prometheus | http://localhost:9090 | - |
| Zipkin | http://localhost:9411 | - |
| Blackbox Exporter | http://localhost:9115 | - |

### 方式二：手动导入大盘

1. 登录 Grafana → **Dashboards** → **New** → **Import**
2. 上传 `config/monitoring/grafana/apex-dashboard.json`
3. 数据源选择 **Prometheus**（需预先添加，URL 指向实际 Prometheus 地址）
4. 点击 **Import**

## 大盘面板说明

大盘共 **11 个分组 91 个面板**（3 行既有 + 8 行增强分组）：

| 分组 | 面板 | 指标来源 |
|------|------|----------|
| 顶部状态行 | 服务状态 / 请求吞吐 / P95 / 5xx 错误率 / JVM 堆 / DB 池水位 | `up`、`apex_api_*`、`jvm_*`、`hikaricp_*`/`druid_*` |
| HTTP 接口指标 | 请求量按状态码 / 响应时间分位数 | `apex_api_request_count_total`、`apex_api_response_time_seconds_bucket` |
| JVM / 数据库 / 治理 | JVM 堆内存 / 连接池 / GC 频率 / 连接池等待超时 / Sentinel | `jvm_*`、`hikaricp_*`、`druid_*`、`sentinel_*` |
| 系统资源（磁盘/网络） | 磁盘使用率 / 网卡启用数 / 磁盘使用率按挂载点 / 网卡状态 | `apex_disk_*`、`apex_network_*` |
| 中间件（Redis/缓存） | Redis 健康 / 节点数 / 缓存命中率 / 命中未命中 / Redis 可用性 | `redisson_healthy`、`redisson_nodes_total`、`apex_cache_gets_total`、`apex_security_redis_metrics_up` |
| 治理与安全 | 限流拒绝 / 熔断触发 / 活跃会话 / 安全拦截 / 限流·熔断 1h 累计 / 趋势 / 防护明细 | `apex_security_rate_limit_rejected_total`、`apex_security_circuit_breaker_opened_total`、`apex_security_active_sessions`、`apex_security_*` 拦截类 |
| 核心业务 | 登录成功 / 注册 / 上传 / 信用变更 / 外部API QPS / 外部API成功率 / 业务量趋势 / 调用结果 / 按服务QPS / 耗时 | `apex_business_*_total`、`apex_api_call_result_total`、`apex_api_call_seconds_*` |
| Zipkin 链路追踪 | Zipkin 在线 / Span 接收 / Span 丢弃 / 收集器负载 | `zipkin_collector_*`（Prometheus 已新增 `zipkin` 抓取任务，`zipkin:9411/prometheus`） |
| JVM 详细（CPU/线程/类加载） | 进程/系统CPU / 存活·守护线程 / 已加载类 / 运行时长 / CPU趋势 / 线程数趋势 / 线程状态分布 / 非堆内存 / 类加载卸载 / 内存缓冲区 | `process_cpu_usage`、`system_cpu_usage`、`jvm_threads_*`、`jvm_classes_*`、`jvm_memory_used_bytes{area="nonheap"}`、`jvm_buffer_memory_used_bytes`、`process_uptime_seconds` |
| 数据库（慢SQL） | 慢SQL次数 / 慢SQL最长耗时 / SQL执行耗时均值 / 慢SQL按类型 | `apex.sql.slow.count_total`、`apex.sql.slow.max.ms`、`apex.sql.execution_seconds_*`（由 `SlowSqlMetricsInterceptor` MyBatis 插件上报，`apex.sql.slow.threshold-ms` 默认 1000ms） |
| 消息中间件（RocketMQ/Outbox） | MQ发送QPS / 发送失败率 / 发送耗时 / Outbox待处理·死信·统计可用 / QPS按模式 / 成功失败 / Outbox各状态 / 耗时按模式 | `apex_mq_publish_count_total{mode,result}`、`apex_mq_publish_latency_seconds_*`（`MqPublishMetricsAspect` 上报）、`apex_outbox_records{status}`、`apex_outbox_metrics_up` |
| 异步响应 / 幂等重试 | 响应超时次数 / SSE连接数 / SSE推送事件 / 幂等通过·拒绝 / 乐观锁重试 / 各自趋势 | `apex_response_timeout_count_total`（超时过滤器）、`apex_sse_connections`、`apex_sse_events_count_total`、`apex_idempotent_count_total{result}`、`apex_retry_optimistic_count_total{result}` |

> **外部指标依赖**：应用侧指标（`apex_*`/`redisson_*`/`apex.sql.*` 等）均由应用自身
> （8081 `/actuator/prometheus`）上报，应用未启动时对应面板显示 No data，启动后自动填充；
> `zipkin_collector_*` 为 Zipkin 自指标，链路数据上报后 rate 即出值。

### 顶部状态行（Stat）

| 面板 | PromQL 要点 | 阈值 |
|------|-------------|------|
| 服务状态 | `up{job="apex-scaffold"}` | 1=UP / 0=DOWN |
| 请求吞吐 | `sum(rate(apex_api_request_count_total[5m]))` | - |
| P95 响应时间 | `histogram_quantile(0.95, sum(rate(apex_api_response_time_seconds_bucket[5m])) by (le))` | >2s 红色 |
| 5xx 错误率 | 5xx 计数 / 总计数 | >5% 黄色，>10% 红色 |
| JVM 堆使用率 | `jvm_memory_used_bytes{area="heap"}` / max | >85% 红色 |
| DB 连接池水位 | `(sum(hikaricp_connections_active) or sum(druid_connections_active)) / (sum(hikaricp_connections_max) or sum(druid_connections_max)) * 100` | >80% 红色 |

### 图表行（Timeseries）

| 面板 | 说明 |
|------|------|
| 请求量（按状态码） | `sum by (status) (rate(apex_api_request_count_total[5m]))` |
| 响应时间分位数 | P50 / P95 / P99 三线对比 |
| JVM 堆内存 | 按内存池（id）拆分，定位泄漏来源 |
| 数据库连接池 | 活跃 / 空闲 / 最大连接三线（`hikaricp_*` 与 `druid_*` 双口径，任一环境生效） |
| JVM GC 频率 | `rate(jvm_gc_pause_seconds_count[5m])` |
| Sentinel 治理 | 拦截 QPS / 放行 QPS 对比，评估限流熔断效果 |

## 告警规则

告警规则已随包提供：`config/monitoring/prometheus/alert-rules.yml`，共 **12 条**，覆盖：

| 告警 | 阈值 | 级别 |
|------|------|------|
| ApexServiceDown | 抓取失败 1 分钟 | critical |
| ApexHealthCheckDown | `/actuator/health` 非 UP 2 分钟（Blackbox 探测） | critical |
| ApexHighErrorRate | 5xx 错误率 > 5%（5 分钟） | warning |
| ApexHighLatency | P95 > 2s（5 分钟） | warning |
| ApexTrafficDrop | 流量 < 日均 20%（10 分钟） | warning |
| ApexJvmHeapHigh | 堆使用率 > 85%（10 分钟） | warning |
| ApexFullGcFrequent | Full GC > 5 次/分钟（5 分钟） | warning |
| ApexThreadCountHigh | 存活线程 > 2000（10 分钟） | warning |
| ApexDbConnectionPoolHigh | 连接池水位 > 80%（5 分钟，Hikari/Druid 双口径） | warning |
| ApexDbConnectionTimeout | 10 分钟连接超时 > 5 次（Hikari/Druid 双口径） | critical |
| ApexRedisDown | `redisson_healthy == 0`（2 分钟） | critical |
| ApexSentinelBlockHigh | 10 分钟拦截 > 100 次 | warning |

> **指标口径说明**：
> - 数据库连接池：dev/本地用 HikariCP（`hikaricp_*`），prod 用 Druid（`druid_*`，由
>   `DruidDataSourceMetricsBinder` 上报）。大盘与告警均以 `or` 合并，任一环境生效。
> - Redis：`redisson_healthy`（1 = 全部节点 PING 通，0 = 不可达）由应用侧
>   `RedissonMetricsBinder` 上报（带 500ms 超时探针）。
> - 健康探针：`probe_success` 由 Blackbox Exporter 探测 `/actuator/health` 提供，
>   Podman Compose 栈已内置（`blackbox-exporter` 服务）。

启用告警的两种方式：

- **本地演示**：在仓库根目录执行 `podman-compose -f config/monitoring/podman-compose.yml up -d` 后，Prometheus 自动加载 `alert-rules.yml`，可在
  Prometheus **Alerts** 页查看规则状态（Alertmanager 需另行配置接收端）。
- **生产**：将 `alert-rules.yml` 挂载到生产 Prometheus，并配置 Alertmanager 对接
  钉钉/企业微信/邮件等通知渠道。

## 多实例监控

单机大盘按 `up{job="apex-scaffold"}` 过滤。部署多实例时：

1. 在 `prometheus.yml` 的 `apex-scaffold` job 下追加实例地址
2. 在大盘添加模板变量 `instance`（`label_values(up, instance)`）
3. PromQL 中追加 `instance="$instance"` 过滤即可按实例切换

## 自定义大盘

- 编辑 `config/monitoring/grafana/apex-dashboard.json` 后重启 Grafana 容器
  （provisioning 每 30s 自动同步，无需重启亦可生效）
- 常用 PromQL 参考：
  - P99 响应时间：`histogram_quantile(0.99, sum(rate(apex_api_response_time_seconds_bucket[5m])) by (le))`
  - Top 10 慢接口：`topk(10, apex_api_response_time_seconds_sum / apex_api_response_time_seconds_count)`
  - 活跃线程数：`jvm_threads_live_threads`
  - Redis 节点健康：`redisson_healthy`（0 = 节点不可达）
  - Druid 连接池水位：`sum(druid_connections_active) / sum(druid_connections_max) * 100`

## 注意事项

1. **数据保留**：Podman Compose 预设 Prometheus 保留 15 天，按需调整
   `--storage.tsdb.retention.time`
2. **面板规模**：大盘 46 面板按 7 个分组折叠展示，性能开销可控；如需精简可折叠分组行
3. **权限管理**：生产环境务必修改 Grafana 默认口令并配置 SSO / 权限
4. **管理端口安全**：应用监控端点位于独立端口 8081，生产建议仅内网暴露
5. **告警通道**：alert-rules.yml 仅定义规则，通知渠道需配置 Alertmanager

## 参考资料

- [Grafana 官方文档](https://grafana.com/docs/)
- [Prometheus 查询语言](https://prometheus.io/docs/prometheus/latest/querying/basics/)
- [Micrometer 指标文档](https://micrometer.io/docs/concepts)
