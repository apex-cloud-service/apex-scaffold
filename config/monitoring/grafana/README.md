# Grafana Dashboard 使用指南

## 功能说明

提供预配置的Grafana监控面板,包含QPS、响应时间、错误率、JVM内存、数据库连接池等关键指标。

## 快速开始

### 1. 导入Dashboard

#### 方式一: 通过UI导入

1. 登录Grafana (默认 http://localhost:3000)
2. 点击左侧菜单 **Dashboards** → **Import**
3. 上传 `config/monitoring/grafana/dashboard.json` 文件
4. 选择Prometheus数据源
5. 点击 **Import**

#### 方式二: 通过API导入

```bash
curl -X POST http://localhost:3000/api/dashboards/import \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -d @config/monitoring/grafana/dashboard.json
```

### 2. 配置数据源

确保Grafana已配置Prometheus数据源:

- **Name**: Prometheus
- **URL**: http://localhost:9090 (根据实际部署调整)
- **Access**: Server (default)

### 3. 查看Dashboard

导入后访问: http://localhost:3000/d/apex-scaffold-app

## 监控指标说明

### HTTP请求指标

| 面板 | 指标 | 说明 |
|------|------|------|
| QPS | `rate(http_server_requests_seconds_count[5m])` | 每秒请求数,按方法和URI分组 |
| 平均响应时间 | `rate(http_server_requests_seconds_sum[5m]) / count` | 5分钟平均响应时间 |
| 错误率 | `rate(status=~"5.."[5m]) / total * 100` | 5xx错误占比百分比 |

### JVM指标

| 面板 | 指标 | 说明 |
|------|------|------|
| JVM内存使用 | `jvm_memory_used_bytes{area="heap"}` | 堆内存使用情况 |
| 活跃线程数 | `jvm_threads_live_threads` | 当前活跃线程数 |
| GC次数 | `rate(jvm_gc_pause_seconds_count[5m])` | 每分钟GC次数 |

### 中间件指标

| 面板 | 指标 | 说明 |
|------|------|------|
| 数据库连接池 | `hikaricp_connections_active/idle` | 活跃/空闲连接数 |
| Redis操作 | `rate(redis_commands_total[5m])` | Redis命令执行频率 |

## 告警规则建议

### 1. 高错误率告警

```yaml
groups:
  - name: apex-alerts
    rules:
      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) / rate(http_server_requests_seconds_count[5m]) > 0.05
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "错误率超过5%"
```

### 2. 高响应时间告警

```yaml
      - alert: HighResponseTime
        expr: rate(http_server_requests_seconds_sum[5m]) / rate(http_server_requests_seconds_count[5m]) > 1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "平均响应时间超过1秒"
```

### 3. JVM内存告警

```yaml
      - alert: HighMemoryUsage
        expr: jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.85
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "JVM堆内存使用率超过85%"
```

## 自定义Dashboard

### 添加新面板

1. 点击Dashboard右上角 **Edit**
2. 点击 **Add panel**
3. 选择图表类型(Graph/Stat/Gauge等)
4. 输入PromQL查询语句
5. 配置显示选项
6. 保存Dashboard

### 常用PromQL示例

```promql
# P99响应时间
histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))

# Top 10慢接口
topk(10, rate(http_server_requests_seconds_sum[5m]) / rate(http_server_requests_seconds_count[5m]))

# 数据库慢查询
rate(mysql_slow_queries_total[5m])

# Redis命中率
rate(redis_keyspace_hits_total[5m]) / (rate(redis_keyspace_hits_total[5m]) + rate(redis_keyspace_misses_total[5m]))
```

## 注意事项

1. **数据保留**: Prometheus默认保留15天数据,根据需要调整`--storage.tsdb.retention.time`
2. **采样间隔**: Grafana默认15秒采样,可根据需要调整
3. **性能影响**: 过多面板会影响Grafana性能,建议控制在20个面板以内
4. **权限管理**: 生产环境应配置Grafana用户认证和权限控制
5. **备份导出**: 定期导出Dashboard JSON备份

## 进阶配置

### 多实例监控

修改Dashboard中的变量配置,支持多实例切换:

```json
{
  "name": "instance",
  "type": "query",
  "query": "label_values(up, instance)"
}
```

### 模板化查询

在PromQL中使用`$instance`变量:

```promql
rate(http_server_requests_seconds_count{instance="$instance"}[5m])
```

## 参考资料

- [Grafana官方文档](https://grafana.com/docs/)
- [Prometheus查询语言](https://prometheus.io/docs/prometheus/latest/querying/basics/)
- [Micrometer指标文档](https://micrometer.io/docs/concepts)
