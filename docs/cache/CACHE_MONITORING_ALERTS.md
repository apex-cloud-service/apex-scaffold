# 缓存监控告警配置指南

## Prometheus 监控指标

### 核心指标列表

#### 1. 缓存命中率相关

```promql
# 缓存命中率（L1 + L2）
sum(rate(cache_hits_total[5m])) / sum(rate(cache_requests_total[5m]))

# L1 缓存命中率
sum(rate(l1_cache_hits_total[5m])) / sum(rate(l1_cache_requests_total[5m]))

# L2 缓存命中率
sum(rate(l2_cache_hits_total[5m])) / sum(rate(l2_cache_requests_total[5m]))
```

#### 2. 缓存穿透监控

```promql
# 缓存穿透次数（布隆过滤器拦截）
rate(apex_cache_penetration_blocked_total[5m])

# 空值标记命中次数
rate(apex_cache_empty_marker_hits_total[5m])

# DB回源次数
rate(apex_cache_db_fallback_total[5m])
```

#### 3. 缓存击穿监控

```promql
# 分布式锁获取失败次数
rate(apex_cache_lock_failed_total[5m])

# 分布式锁等待时间（P95）
histogram_quantile(0.95, rate(apex_cache_lock_wait_duration_seconds_bucket[5m]))

# Single-Flight 合并请求数
rate(apex_cache_inflight_merge_total[5m])
```

#### 4. 缓存雪崩监控

```promql
# TTL jitter 应用次数
rate(apex_cache_ttl_jitter_applied_total[5m])

# 缓存预热成功数
rate(apex_cache_prewarm_success_total[5m])

# 缓存预热失败数
rate(apex_cache_prewarm_failed_total[5m])
```

#### 5. 读降级监控

```promql
# 读降级次数（DB故障时返回过期缓存）
rate(apex_cache_read_degradation_total[5m])

# 读降级占比
rate(apex_cache_read_degradation_total[5m]) / rate(apex_cache_requests_total[5m])
```

## Grafana Dashboard 配置

### Dashboard JSON 模板

```json
{
  "dashboard": {
    "title": "Apex Scaffold 缓存监控",
    "panels": [
      {
        "title": "缓存命中率",
        "targets": [
          {
            "expr": "sum(rate(cache_hits_total[5m])) / sum(rate(cache_requests_total[5m]))"
          }
        ],
        "type": "graph"
      },
      {
        "title": "缓存穿透次数/分钟",
        "targets": [
          {
            "expr": "rate(apex_cache_penetration_blocked_total[1m]) * 60"
          }
        ],
        "type": "graph"
      },
      {
        "title": "缓存击穿次数/分钟",
        "targets": [
          {
            "expr": "rate(apex_cache_lock_failed_total[1m]) * 60"
          }
        ],
        "type": "graph"
      },
      {
        "title": "读降级次数/分钟",
        "targets": [
          {
            "expr": "rate(apex_cache_read_degradation_total[1m]) * 60"
          }
        ],
        "type": "graph"
      }
    ]
  }
}
```

## 告警规则配置

### Prometheus AlertManager 规则

```yaml
groups:
  - name: cache_alerts
    interval: 30s
    rules:
      # 缓存命中率告警
      - alert: CacheHitRateLow
        expr: |
          sum(rate(cache_hits_total[5m])) / sum(rate(cache_requests_total[5m])) < 0.8
        for: 5m
        labels:
          severity: warning
          team: platform
        annotations:
          summary: "缓存命中率过低"
          description: "缓存命中率低于80%，当前值：{{ $value | humanizePercentage }}"
          
      # 缓存穿透告警
      - alert: CachePenetrationHigh
        expr: |
          rate(apex_cache_penetration_blocked_total[5m]) > 100
        for: 5m
        labels:
          severity: warning
          team: platform
        annotations:
          summary: "缓存穿透频繁"
          description: "缓存穿透次数过高，每秒 {{ $value }} 次，可能存在恶意攻击"
          
      # 缓存击穿告警
      - alert: CacheBreakdownDetected
        expr: |
          rate(apex_cache_lock_failed_total[5m]) > 10
        for: 2m
        labels:
          severity: critical
          team: platform
        annotations:
          summary: "检测到缓存击穿"
          description: "分布式锁获取失败次数过高，每秒 {{ $value }} 次"
          
      # 读降级告警
      - alert: ReadDegradationActive
        expr: |
          rate(apex_cache_read_degradation_total[5m]) > 0
        for: 1m
        labels:
          severity: critical
          team: platform
        annotations:
          summary: "数据库故障，读降级启用"
          description: "检测到读降级，DB可能存在故障，每秒 {{ $value }} 次降级"
          
      # 缓存预热失败告警
      - alert: CachePrewarmFailed
        expr: |
          rate(apex_cache_prewarm_failed_total[5m]) > 0
        for: 5m
        labels:
          severity: warning
          team: platform
        annotations:
          summary: "缓存预热失败"
          description: "缓存预热出现失败，每秒 {{ $value }} 次"
          
      # Redis 连接异常告警
      - alert: RedisConnectionFailed
        expr: |
          up{job="redis"} == 0
        for: 1m
        labels:
          severity: critical
          team: platform
        annotations:
          summary: "Redis 连接失败"
          description: "Redis 实例 {{ $labels.instance }} 不可用"
          
      # 布隆过滤器容量告警
      - alert: BloomFilterCapacityHigh
        expr: |
          apex_cache_bloom_filter_count / apex_cache_bloom_filter_capacity > 0.8
        for: 10m
        labels:
          severity: warning
          team: platform
        annotations:
          summary: "布隆过滤器容量告警"
          description: "布隆过滤器使用率超过80%，可能需要扩容"
```

## 日志监控配置

### ELK/Loki 查询示例

```promql
# 查询缓存穿透日志
{app="apex-scaffold"} |= "缓存穿透" | json | line_format "{{.timestamp}} {{.message}}"

# 查询缓存击穿日志
{app="apex-scaffold"} |= "获取分布式锁失败" | json

# 查询读降级日志
{app="apex-scaffold"} |= "读降级" | json | severity="WARN"

# 查询缓存预热日志
{app="apex-scaffold"} |= "缓存预热" | json
```

## 应用性能监控（APM）

### SkyWalking 监控配置

```yaml
# 关键慢查询监控
slow_cache_queries:
  - metric_name: cache_query_duration
    threshold_ms: 100
    alert: true
    
# 缓存操作链路追踪
cache_operations:
  - operation: "cache.get"
    trace: true
  - operation: "cache.set"
    trace: true
  - operation: "cache.delete"
    trace: true
```

## 告警通知配置

### 钉钉/企业微信/Slack 集成

```yaml
# AlertManager 配置
receivers:
  - name: 'cache-team'
    webhook_configs:
      - url: 'https://oapi.dingtalk.com/robot/send?access_token=xxx'
        send_resolved: true
        
route:
  group_by: ['alertname', 'cluster']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 1h
  receiver: 'cache-team'
  routes:
    - match:
        severity: critical
      receiver: 'cache-team'
      continue: true
```

### 告警消息模板

```markdown
**【缓存告警】{{ .GroupLabels.alertname }}**

- **级别**：{{ .CommonLabels.severity }}
- **集群**：{{ .CommonLabels.cluster }}
- **描述**：{{ .CommonAnnotations.description }}
- **时间**：{{ .StartsAt.Format "2006-01-02 15:04:05" }}
- **详情**：[查看监控](http://grafana.example.com/d/cache-dashboard)
```

## 监控指标暴露

### application.yml 配置

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
      environment: ${spring.profiles.active}
```

## 关键指标阈值建议

| 指标 | 正常范围 | 告警阈值 | 严重阈值 |
|------|---------|---------|---------|
| 缓存命中率 | > 90% | < 80% | < 60% |
| 缓存穿透（次/秒） | < 10 | > 50 | > 100 |
| 缓存击穿（次/秒） | < 1 | > 5 | > 10 |
| 读降级（次/秒） | 0 | > 0 | > 10 |
| 锁等待时间（P95） | < 100ms | > 500ms | > 1000ms |
| 布隆过滤器容量 | < 70% | > 80% | > 90% |

## 监控最佳实践

1. **建立基线**
   - 收集至少一周的正常业务数据
   - 分析高峰期和低谷期的指标特征
   - 设置动态阈值而非固定值

2. **分级告警**
   - Info: 信息通知，无需立即处理
   - Warning: 警告级别，需要关注
   - Critical: 严重告警，需要立即处理

3. **告警降噪**
   - 设置合理的告警时间窗口（如 5分钟）
   - 避免短时波动触发告警
   - 使用告警聚合减少重复通知

4. **定期review**
   - 每周查看缓存监控报告
   - 每月优化告警规则
   - 每季度评估监控覆盖率

## 故障排查 Runbook

详见 [缓存使用指南 - 故障排查章节](CACHE_USAGE_GUIDE.md#故障排查)
