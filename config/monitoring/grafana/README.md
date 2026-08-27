# Grafana 监控大盘使用指南

## 功能说明

提供预配置的 Apex Scaffold 监控大盘（`apex-dashboard.json`），覆盖服务可用性、
接口质量（QPS / 延迟分位数 / 错误率）、JVM 健康、数据库连接池与 Sentinel 治理指标。

> **指标口径说明**：大盘基于项目自定义的 `apex_api_*` 指标（由 `MetricsWebFilter` 上报，
> 见 `apex-interfaces/.../filter/MetricsWebFilter.java`），而非 Spring MVC 的
> `http_server_requests_*`。WebFlux 下请以本大盘为准。

## 快速开始

### 方式一：docker-compose 一键启动（推荐）

```bash
cd config/monitoring
docker compose up -d
```

容器启动时通过 [auto-provisioning](./provisioning/) 自动完成：

1. 注册 **Prometheus** 数据源（`http://prometheus:9090`）
2. 导入 **Apex Scaffold 监控大盘**（`apex-dashboard.json`）

启动后访问：

| 组件 | 地址 | 默认账号 |
|------|------|----------|
| Grafana | http://localhost:3000 | admin / admin（首次登录提示修改） |
| Prometheus | http://localhost:9090 | - |
| Zipkin | http://localhost:9411 | - |

### 方式二：手动导入大盘

1. 登录 Grafana → **Dashboards** → **New** → **Import**
2. 上传 `config/monitoring/grafana/apex-dashboard.json`
3. 数据源选择 **Prometheus**（需预先添加，URL 指向实际 Prometheus 地址）
4. 点击 **Import**

## 大盘面板说明

### 顶部状态行（Stat）

| 面板 | PromQL 要点 | 阈值 |
|------|-------------|------|
| 服务状态 | `up{job="apex-scaffold"}` | 1=UP / 0=DOWN |
| 请求吞吐 | `sum(rate(apex_api_request_count_total[5m]))` | - |
| P95 响应时间 | `histogram_quantile(0.95, sum(rate(apex_api_response_time_seconds_bucket[5m])) by (le))` | >2s 红色 |
| 5xx 错误率 | 5xx 计数 / 总计数 | >5% 黄色，>10% 红色 |
| JVM 堆使用率 | `jvm_memory_used_bytes{area="heap"}` / max | >85% 红色 |
| DB 连接池水位 | `hikaricp_connections_active` / max | >80% 红色 |

### 图表行（Timeseries）

| 面板 | 说明 |
|------|------|
| 请求量（按状态码） | `sum by (status) (rate(apex_api_request_count_total[5m]))` |
| 响应时间分位数 | P50 / P95 / P99 三线对比 |
| JVM 堆内存 | 按内存池（id）拆分，定位泄漏来源 |
| 数据库连接池 | 活跃 / 空闲 / 最大连接三线 |
| JVM GC 频率 | `rate(jvm_gc_pause_seconds_count[5m])` |
| Sentinel 治理 | 拦截 QPS / 放行 QPS 对比，评估限流熔断效果 |

## 告警规则

告警规则已随包提供：`config/monitoring/prometheus/alert-rules.yml`，覆盖：

| 告警 | 阈值 | 级别 |
|------|------|------|
| ApexServiceDown | 抓取失败 1 分钟 | critical |
| ApexHighErrorRate | 5xx 错误率 > 5%（5 分钟） | warning |
| ApexHighLatency | P95 > 2s（5 分钟） | warning |
| ApexTrafficDrop | 流量 < 日均 20%（10 分钟） | warning |
| ApexJvmHeapHigh | 堆使用率 > 85%（10 分钟） | warning |
| ApexFullGcFrequent | Full GC > 5 次/分钟（5 分钟） | warning |
| ApexDbConnectionPoolHigh | 连接池水位 > 80%（5 分钟） | warning |
| ApexDbConnectionTimeout | 10 分钟连接超时 > 5 次 | critical |
| ApexRedisDown | 5 分钟 Redis 连接失败 > 0 | critical |
| ApexSentinelBlockHigh | 10 分钟拦截 > 100 次 | warning |

启用告警的两种方式：

- **本地演示**：`docker compose up -d` 后，Prometheus 自动加载 `alert-rules.yml`，可在
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

## 注意事项

1. **数据保留**：docker-compose 预设 Prometheus 保留 15 天，按需调整
   `--storage.tsdb.retention.time`
2. **性能影响**：面板控制在 20 个以内，避免拖慢 Grafana
3. **权限管理**：生产环境务必修改 Grafana 默认口令并配置 SSO / 权限
4. **管理端口安全**：应用监控端点位于独立端口 8081，生产建议仅内网暴露
5. **告警通道**：alert-rules.yml 仅定义规则，通知渠道需配置 Alertmanager

## 参考资料

- [Grafana 官方文档](https://grafana.com/docs/)
- [Prometheus 查询语言](https://prometheus.io/docs/prometheus/latest/querying/basics/)
- [Micrometer 指标文档](https://micrometer.io/docs/concepts)
