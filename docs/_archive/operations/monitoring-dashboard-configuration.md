# Apex Scaffold 监控大盘配置指南

[查看监控配置链路图](../../diagrams/48_monitoring_configuration.svg)

> 本文档详细说明 Prometheus + Grafana 监控体系的配置、使用和维护

## 📋 目录

- [1. 监控体系概览](#1-监控体系概览)

- [2. Prometheus 配置](#2-prometheus-配置)

- [3. Grafana 配置](#3-grafana-配置)

- [4. 监控面板说明](#4-监控面板说明)

- [5. 告警规则配置](#5-告警规则配置)

- [6. 运维操作](#6-运维操作)

- [7. 故障排查](#7-故障排查)

- [8. 最佳实践](#8-最佳实践)

***

<a id="1-监控体系概览"></a>

## 1. 监控体系概览

### 1.1 架构图

参考架构图：

- [24\_grafana\_dashboard\_architecture.svg](../../diagrams/48_monitoring_configuration.svg) - Grafana 监控面板分组架构

- [25\_prometheus\_monitoring\_architecture.svg](../../diagrams/24_prometheus_grafana_monitoring.svg) - Prometheus 监控体系架构

### 1.2 组件清单

| 组件                | 版本    | 端口   | 说明             |
| ----------------- | ----- | ---- | -------------- |
| Prometheus        | 2.45+ | 9090 | 时间序列数据库 + 告警引擎 |
| Grafana           | 10.0+ | 3000 | 数据可视化平台        |
| Alertmanager      | 0.26+ | 9093 | 告警通知管理         |
| Blackbox Exporter | 0.24+ | 9115 | 黑盒探测           |
| Node Exporter     | 1.6+  | 9100 | 主机指标采集 (可选)    |

### 1.3 监控指标统计

- **抓取目标**: 6个服务端点

- **指标类型**: 100+ 种

- **监控面板**: 66个面板，11个分组

- **告警规则**: 10组，18条规则

- **数据保留**: 15天

- **采集周期**: 15秒

***

<a id="2-prometheus-配置"></a>

## 2. Prometheus 配置

### 2.1 核心配置文件

**位置**: `/etc/prometheus/prometheus.yml`

```yaml
# 全局配置
global:
  scrape_interval: 15s          # 抓取间隔
  scrape_timeout: 10s           # 抓取超时
  evaluation_interval: 15s      # 告警规则评估间隔
  external_labels:
    cluster: 'apex-prod'        # 集群标识
    environment: 'production'

# 告警管理器配置
alerting:
  alertmanagers:
    - static_configs:
        - targets:
            - alertmanager:9093

# 告警规则文件
rule_files:
  - '/etc/prometheus/rules/*.yml'

# 抓取配置
scrape_configs:
  # 1. Apex Scaffold 应用 (主要)
  - job_name: 'apex-scaffold'
    scrape_interval: 15s
    scrape_timeout: 10s
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets:
          - 'apex-scaffold:8081'
        labels:
          service: 'apex-scaffold'
          tier: 'application'

  # 2. Sentinel 治理平台
  - job_name: 'sentinel'
    scrape_interval: 15s
    metrics_path: '/metrics'
    static_configs:
      - targets:
          - 'sentinel:8720'
        labels:
          service: 'sentinel'
          tier: 'governance'

  # 3. Blackbox Exporter 健康探针
  - job_name: 'blackbox'
    scrape_interval: 30s
    metrics_path: '/probe'
    params:
      module: [http_2xx]
    static_configs:
      - targets:
          - http://apex-scaffold:8081/actuator/health
          - http://grafana:3000/api/health
        labels:
          tier: 'healthcheck'
    relabel_configs:
      - source_labels: [__address__]
        target_label: __param_target
      - source_labels: [__param_target]
        target_label: instance
      - target_label: __address__
        replacement: blackbox-exporter:9115

  # 4. Grafana 自监控
  - job_name: 'grafana'
    scrape_interval: 30s
    metrics_path: '/metrics'
    static_configs:
      - targets:
          - 'grafana:3000'
        labels:
          service: 'grafana'
          tier: 'monitoring'

  # 5. Zipkin 链路追踪
  - job_name: 'zipkin'
    scrape_interval: 15s
    metrics_path: '/prometheus'
    static_configs:
      - targets:
          - 'zipkin:9411'
        labels:
          service: 'zipkin'
          tier: 'tracing'

  # 6. Node Exporter 主机监控 (可选)
  - job_name: 'node-exporter'
    scrape_interval: 30s
    static_configs:
      - targets:
          - 'node-exporter:9100'
        labels:
          service: 'node-exporter'
          tier: 'infrastructure'
```

### 2.2 存储配置

**启动参数**:

```bash
--storage.tsdb.path=/data/prometheus          # 数据存储路径
--storage.tsdb.retention.time=15d             # 数据保留15天
--storage.tsdb.retention.size=50GB            # 最大存储50GB (可选)
--web.enable-lifecycle                        # 启用热重载 API
--web.enable-admin-api                        # 启用管理 API
```

**容量规划**:

```
每个时间序列: ~1-2 bytes/sample
10k 指标 × 15s 间隔 = 667 samples/秒
15天数据: ~86M samples (~172MB 未压缩)
实际压缩后: ~17MB (10:1 压缩比)
```

### 2.3 告警规则配置

**位置**: `/etc/prometheus/rules/alert_rules.yml`

```yaml
groups:
  # ==================== 应用层告警 ====================
  - name: application_alerts
    interval: 30s
    rules:
      # 高错误率告警
      - alert: HighErrorRate
        expr: |
          (
            sum(rate(apex_api_request_count_total{status=~"5.."}[5m])) by (service)
            /
            sum(rate(apex_api_request_count_total[5m])) by (service)
          ) > 0.05
        for: 2m
        labels:
          severity: critical
          category: application
        annotations:
          summary: "应用 {{ $labels.service }} 错误率过高"
          description: "错误率: {{ $value | humanizePercentage }}"
          dashboard: "http://grafana:3000/d/apex-app-overview"

      # 高延迟告警
      - alert: HighAPILatency
        expr: |
          histogram_quantile(0.95, 
            sum(rate(apex_api_response_time_seconds_bucket[5m])) by (le, uri)
          ) > 3
        for: 5m
        labels:
          severity: warning
          category: performance
        annotations:
          summary: "API {{ $labels.uri }} 延迟过高"
          description: "P95延迟: {{ $value }}s"

      # 服务宕机告警
      - alert: ServiceDown
        expr: up{job="apex-scaffold"} == 0
        for: 1m
        labels:
          severity: critical
          category: availability
        annotations:
          summary: "服务 {{ $labels.instance }} 已宕机"
          description: "无法抓取指标，请检查服务状态"

      # 限流触发告警
      - alert: RateLimitTriggered
        expr: |
          rate(apex_sentinel_rate_limit_total[5m]) > 100
        for: 3m
        labels:
          severity: warning
          category: traffic
        annotations:
          summary: "限流触发频繁"
          description: "每秒触发 {{ $value }} 次限流"

  # ==================== JVM 告警 ====================
  - name: jvm_alerts
    interval: 30s
    rules:
      # 堆内存告警
      - alert: HighJVMHeapUsage
        expr: |
          (jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) > 0.85
        for: 5m
        labels:
          severity: warning
          category: jvm
        annotations:
          summary: "JVM 堆内存使用率过高"
          description: "当前使用率: {{ $value | humanizePercentage }}"

      # GC 频繁告警
      - alert: HighGCRate
        expr: |
          rate(jvm_gc_pause_seconds_count[5m]) > 10
        for: 5m
        labels:
          severity: warning
          category: jvm
        annotations:
          summary: "GC 频率过高"
          description: "GC次数/秒: {{ $value }}"

      # 线程死锁告警
      - alert: ThreadDeadlock
        expr: |
          jvm_threads_deadlocked > 0
        for: 1m
        labels:
          severity: critical
          category: jvm
        annotations:
          summary: "检测到线程死锁"
          description: "死锁线程数: {{ $value }}"

  # ==================== 数据库告警 ====================
  - name: database_alerts
    interval: 30s
    rules:
      # 数据库连接池告警
      - alert: DatabaseConnectionPoolExhausted
        expr: |
          hikaricp_connections_active / hikaricp_connections_max > 0.9
        for: 3m
        labels:
          severity: critical
          category: database
        annotations:
          summary: "数据库连接池即将耗尽"
          description: "使用率: {{ $value | humanizePercentage }}"

      # 慢查询告警
      - alert: HighSlowQueries
        expr: |
          rate(mysql_slow_queries[5m]) > 10
        for: 5m
        labels:
          severity: warning
          category: database
        annotations:
          summary: "慢查询数量过多"
          description: "慢查询/秒: {{ $value }}"

  # ==================== Redis 告警 ====================
  - name: redis_alerts
    interval: 30s
    rules:
      # Redis 内存告警
      - alert: HighRedisMemoryUsage
        expr: |
          (redis_memory_used_bytes / redis_memory_max_bytes) > 0.85
        for: 5m
        labels:
          severity: warning
          category: redis
        annotations:
          summary: "Redis 内存使用率过高"
          description: "使用率: {{ $value | humanizePercentage }}"

      # Redis 命中率低告警
      - alert: LowRedisCacheHitRate
        expr: |
          (
            rate(redis_keyspace_hits_total[5m])
            /
            (rate(redis_keyspace_hits_total[5m]) + rate(redis_keyspace_misses_total[5m]))
          ) < 0.8
        for: 10m
        labels:
          severity: warning
          category: redis
        annotations:
          summary: "Redis 缓存命中率过低"
          description: "命中率: {{ $value | humanizePercentage }}"

  # ==================== 系统资源告警 ====================
  - name: system_alerts
    interval: 30s
    rules:
      # CPU 使用率告警
      - alert: HighCPUUsage
        expr: |
          (1 - avg(rate(node_cpu_seconds_total{mode="idle"}[5m]))) > 0.85
        for: 5m
        labels:
          severity: warning
          category: system
        annotations:
          summary: "CPU 使用率过高"
          description: "使用率: {{ $value | humanizePercentage }}"

      # 内存使用率告警
      - alert: HighMemoryUsage
        expr: |
          (1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)) > 0.90
        for: 5m
        labels:
          severity: warning
          category: system
        annotations:
          summary: "内存使用率过高"
          description: "使用率: {{ $value | humanizePercentage }}"

      # 磁盘空间告警
      - alert: HighDiskUsage
        expr: |
          (node_filesystem_avail_bytes{fstype!~"tmpfs|fuse.lxcfs"} 
           / node_filesystem_size_bytes) < 0.15
        for: 5m
        labels:
          severity: critical
          category: system
        annotations:
          summary: "磁盘空间不足"
          description: "剩余空间: {{ $value | humanizePercentage }}"
```

### 2.4 Recording Rules 配置

**位置**: `/etc/prometheus/rules/recording_rules.yml`

```yaml
groups:
  - name: recording_rules
    interval: 30s
    rules:
      # 预计算 API QPS (每个接口)
      - record: apex:api:qps:rate5m
        expr: |
          sum(rate(apex_api_request_count_total[5m])) by (uri, method)

      # 预计算 API P95 延迟
      - record: apex:api:latency:p95
        expr: |
          histogram_quantile(0.95, 
            sum(rate(apex_api_response_time_seconds_bucket[5m])) by (le, uri)
          )

      # 预计算错误率
      - record: apex:api:error_rate:rate5m
        expr: |
          sum(rate(apex_api_request_count_total{status=~"5.."}[5m])) by (service)
          /
          sum(rate(apex_api_request_count_total[5m])) by (service)

      # 预计算 JVM 堆内存使用率
      - record: apex:jvm:heap:usage_ratio
        expr: |
          jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}

      # 预计算数据库连接池使用率
      - record: apex:db:connection_pool:usage_ratio
        expr: |
          hikaricp_connections_active / hikaricp_connections_max

      # 预计算 Redis 命中率
      - record: apex:redis:hit_rate:rate5m
        expr: |
          rate(redis_keyspace_hits_total[5m])
          /
          (rate(redis_keyspace_hits_total[5m]) + rate(redis_keyspace_misses_total[5m]))
```

### 2.6 Alertmanager 配置

**位置**: `/etc/alertmanager/alertmanager.yml`

```yaml
# 全局配置
global:
  resolve_timeout: 5m
  smtp_smarthost: 'smtp.gmail.com:587'
  smtp_from: 'alerts@example.com'
  smtp_auth_username: 'alerts@example.com'
  smtp_auth_password: '<password>'
  smtp_require_tls: true

# 模板文件
templates:
  - '/etc/alertmanager/templates/*.tmpl'

# 路由配置
route:
  # 默认分组标签
  group_by: ['alertname', 'cluster', 'service']
  
  # 分组等待时间 (等待更多告警一起发送)
  group_wait: 10s
  
  # 分组间隔时间 (同一组告警的发送间隔)
  group_interval: 10s
  
  # 重复告警间隔 (未恢复时的重复通知间隔)
  repeat_interval: 12h
  
  # 默认接收器
  receiver: 'default'
  
  # 子路由
  routes:
    # 严重告警 - 立即通知所有渠道
    - match:
        severity: critical
      receiver: 'critical-alerts'
      group_wait: 0s
      group_interval: 5m
      repeat_interval: 4h
      continue: true  # 继续匹配后续路由
    
    # 应用告警
    - match:
        category: application
      receiver: 'app-team'
      group_by: ['alertname', 'service']
    
    # 数据库告警
    - match:
        category: database
      receiver: 'dba-team'
    
    # 系统资源告警
    - match:
        category: system
      receiver: 'ops-team'
    
    # JVM 告警
    - match:
        category: jvm
      receiver: 'app-team'

# 抑制规则 (告警抑制逻辑)
inhibit_rules:
  # 服务宕机时抑制所有该服务的其他告警
  - source_match:
      alertname: 'ServiceDown'
    target_match_re:
      service: '.*'
    equal: ['instance']
  
  # 高错误率时抑制单个接口的错误告警
  - source_match:
      alertname: 'HighErrorRate'
    target_match:
      alertname: 'APIError'
    equal: ['service']
  
  # CPU 告警时抑制内存告警 (避免雪崩)
  - source_match:
      alertname: 'HighCPUUsage'
      severity: critical
    target_match:
      alertname: 'HighMemoryUsage'
    equal: ['instance']

# 接收器配置
receivers:
  # 默认接收器
  - name: 'default'
    webhook_configs:
      - url: 'http://localhost:5001/webhook'
        send_resolved: true

  # 严重告警 - 多渠道通知
  - name: 'critical-alerts'
    email_configs:
      - to: 'ops-team@example.com,dev-team@example.com'
        headers:
          Subject: '[CRITICAL] {{ .GroupLabels.alertname }}'
        html: '{{ template "email.html" . }}'
        send_resolved: true
    
    webhook_configs:
      # 钉钉通知
      - url: 'https://oapi.dingtalk.com/robot/send?access_token=YOUR_TOKEN'
        send_resolved: true
        http_config:
          headers:
            Content-Type: application/json
    
    # Slack 通知
    slack_configs:
      - api_url: 'https://hooks.slack.com/services/YOUR/WEBHOOK/URL'
        channel: '#alerts-critical'
        title: '🚨 {{ .GroupLabels.alertname }}'
        text: '{{ range .Alerts }}{{ .Annotations.description }}{{ end }}'
        send_resolved: true
    
    # PagerDuty 通知 (值班电话)
    pagerduty_configs:
      - service_key: '<pagerduty-service-key>'
        severity: 'critical'
        description: '{{ .GroupLabels.alertname }}'
        details:
          firing: '{{ .Alerts.Firing | len }}'
          resolved: '{{ .Alerts.Resolved | len }}'

  # 应用团队
  - name: 'app-team'
    email_configs:
      - to: 'app-team@example.com'
        headers:
          Subject: '[APP] {{ .GroupLabels.alertname }}'
        send_resolved: true
    
    webhook_configs:
      - url: 'https://oapi.dingtalk.com/robot/send?access_token=APP_TEAM_TOKEN'
        send_resolved: true

  # DBA 团队
  - name: 'dba-team'
    email_configs:
      - to: 'dba-team@example.com'
        headers:
          Subject: '[DATABASE] {{ .GroupLabels.alertname }}'
        send_resolved: true

  # 运维团队
  - name: 'ops-team'
    email_configs:
      - to: 'ops-team@example.com'
        headers:
          Subject: '[OPS] {{ .GroupLabels.alertname }}'
        send_resolved: true
```

**钉钉 Webhook 消息模板** (`/etc/alertmanager/templates/dingtalk.tmpl`):

```json
{{ define "dingtalk.default.message" }}
{
  "msgtype": "markdown",
  "markdown": {
    "title": "{{ .GroupLabels.alertname }}",
    "text": "## 🚨 Prometheus 告警\n\n**告警名称:** {{ .GroupLabels.alertname }}\n\n**严重程度:** {{ .CommonLabels.severity }}\n\n**告警数量:** {{ len .Alerts }}\n\n{{ range .Alerts }}\n---\n\n**状态:** {{ .Status }}\n\n**实例:** {{ .Labels.instance }}\n\n**描述:** {{ .Annotations.description }}\n\n**触发时间:** {{ .StartsAt.Format \"2006-01-02 15:04:05\" }}\n\n{{ if .EndsAt }}**恢复时间:** {{ .EndsAt.Format \"2006-01-02 15:04:05\" }}{{ end }}\n\n{{ end }}\n\n[查看详情]({{ .ExternalURL }})"
  }
}
{{ end }}
```

**Email HTML 模板** (`/etc/alertmanager/templates/email.html.tmpl`):

```html
{{ define "email.html" }}
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <style>
    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
    .container { max-width: 800px; margin: 0 auto; padding: 20px; }
    .header { background: #d32f2f; color: white; padding: 20px; border-radius: 5px; }
    .alert { border: 1px solid #ddd; margin: 15px 0; padding: 15px; border-radius: 5px; }
    .alert.critical { border-left: 5px solid #d32f2f; background: #ffebee; }
    .alert.warning { border-left: 5px solid #f57c00; background: #fff3e0; }
    .label { display: inline-block; padding: 3px 8px; background: #e0e0e0; border-radius: 3px; margin: 2px; font-size: 12px; }
    .footer { margin-top: 30px; padding-top: 20px; border-top: 1px solid #ddd; font-size: 12px; color: #666; }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <h2>🚨 Prometheus 告警通知</h2>
      <p>告警名称: {{ .GroupLabels.alertname }}</p>
    </div>
    
    <h3>告警详情</h3>
    {{ range .Alerts }}
    <div class="alert {{ .Labels.severity }}">
      <h4>{{ .Labels.alertname }}</h4>
      <p><strong>状态:</strong> {{ .Status }}</p>
      <p><strong>严重程度:</strong> <span class="label">{{ .Labels.severity }}</span></p>
      <p><strong>实例:</strong> {{ .Labels.instance }}</p>
      <p><strong>服务:</strong> {{ .Labels.service }}</p>
      <p><strong>描述:</strong> {{ .Annotations.description }}</p>
      <p><strong>触发时间:</strong> {{ .StartsAt.Format "2006-01-02 15:04:05" }}</p>
      {{ if .EndsAt }}
      <p><strong>恢复时间:</strong> {{ .EndsAt.Format "2006-01-02 15:04:05" }}</p>
      {{ end }}
    </div>
    {{ end }}
    
    <div class="footer">
      <p>此邮件由 Alertmanager 自动发送</p>
      <p>查看详情: <a href="{{ .ExternalURL }}">{{ .ExternalURL }}</a></p>
    </div>
  </div>
</body>
</html>
{{ end }}
```

**静默规则示例** (通过 API 或 UI 配置):

```bash
# 维护窗口 - 静默所有告警
amtool silence add \
  --comment="Scheduled maintenance" \
  --duration=2h \
  --author="ops@example.com" \
  alertname=~".+"

# 静默特定实例的告警
amtool silence add \
  --comment="Server upgrade" \
  --duration=1h \
  --author="ops@example.com" \
  instance="apex-scaffold:8081"

# 静默特定告警
amtool silence add \
  --comment="Known issue, working on fix" \
  --duration=24h \
  --author="dev@example.com" \
  alertname="HighAPILatency"
```

***

**规范**: `apex_<subsystem>_<name>_<unit>`

**示例**:

```
# Counter 计数器 (累积递增)
apex_api_request_count_total              # API 请求总数
apex_business_login_total                 # 登录成功总数
apex_exception_total                      # 异常总数

# Gauge 仪表盘 (可增可减)
jvm_memory_used_bytes                     # JVM 内存使用
hikaricp_connections_active               # 活跃连接数
apex_disk_used_percent                    # 磁盘使用率

# Histogram 直方图 (分布统计)
apex_api_response_time_seconds_bucket     # 响应时间分桶
apex_api_response_time_seconds_sum        # 响应时间总和
apex_api_response_time_seconds_count      # 请求总数

# Summary 摘要 (分位数)
apex_cache_latency_seconds{quantile="0.5"}   # P50 延迟
apex_cache_latency_seconds{quantile="0.95"}  # P95 延迟
```

***

<a id="3-grafana-配置"></a>

## 3. Grafana 配置

### 3.1 数据源配置

**步骤**:

1. 登录 Grafana: `http://localhost:3000` (admin/admin)
2. Configuration → Data Sources → Add data source
3. 选择 Prometheus
4. 配置连接:

```yaml
Name: Prometheus
URL: http://prometheus:9090
Access: Server (默认)
Scrape interval: 15s
HTTP Method: GET
```

1. 点击 "Save & Test" 验证连接

### 3.2 Dashboard 导入

**方法 1: 从文件导入**

```bash
# 1. 准备 Dashboard JSON 文件
# 2. Dashboards → Import → Upload JSON file
# 3. 选择数据源: Prometheus
# 4. 点击 Import
```

**方法 2: 从 Grafana.com 导入**

```
Dashboard ID: 
- 12633 (Spring Boot 2.7+ Statistics)
- 4701  (JVM (Micrometer))
- 11074 (Node Exporter Full)
```

**方法 3: 使用完整 JSON 模板**

完整的应用概览 Dashboard JSON 示例 (`apex-app-overview.json`):

```json
{
  "dashboard": {
    "id": null,
    "uid": "apex-app-overview",
    "title": "Apex Scaffold - 应用概览",
    "tags": ["apex", "application", "overview"],
    "timezone": "browser",
    "schemaVersion": 38,
    "refresh": "30s",
    "time": {
      "from": "now-1h",
      "to": "now"
    },
    "templating": {
      "list": [
        {
          "name": "instance",
          "type": "query",
          "datasource": "Prometheus",
          "query": "label_values(up{job=\"apex-scaffold\"}, instance)",
          "refresh": 1,
          "multi": false,
          "includeAll": false
        }
      ]
    },
    "panels": [
      {
        "id": 1,
        "title": "服务状态",
        "type": "stat",
        "gridPos": {"h": 4, "w": 4, "x": 0, "y": 0},
        "targets": [
          {
            "expr": "up{job=\"apex-scaffold\", instance=\"$instance\"}",
            "refId": "A"
          }
        ],
        "options": {
          "reduceOptions": {
            "values": false,
            "calcs": ["lastNotNull"]
          },
          "colorMode": "background",
          "graphMode": "none"
        },
        "fieldConfig": {
          "defaults": {
            "thresholds": {
              "mode": "absolute",
              "steps": [
                {"value": 0, "color": "red"},
                {"value": 1, "color": "green"}
              ]
            },
            "mappings": [
              {"type": "value", "value": "1", "text": "UP"},
              {"type": "value", "value": "0", "text": "DOWN"}
            ]
          }
        }
      },
      {
        "id": 2,
        "title": "请求吞吐量 (QPS)",
        "type": "graph",
        "gridPos": {"h": 8, "w": 12, "x": 0, "y": 4},
        "targets": [
          {
            "expr": "sum(rate(apex_api_request_count_total{instance=\"$instance\"}[5m]))",
            "legendFormat": "总QPS",
            "refId": "A"
          },
          {
            "expr": "sum(rate(apex_api_request_count_total{instance=\"$instance\", status=~\"2..\"}[5m]))",
            "legendFormat": "2xx",
            "refId": "B"
          },
          {
            "expr": "sum(rate(apex_api_request_count_total{instance=\"$instance\", status=~\"4..\"}[5m]))",
            "legendFormat": "4xx",
            "refId": "C"
          },
          {
            "expr": "sum(rate(apex_api_request_count_total{instance=\"$instance\", status=~\"5..\"}[5m]))",
            "legendFormat": "5xx",
            "refId": "D"
          }
        ],
        "yaxes": [
          {"format": "reqps", "label": "请求/秒"}
        ]
      },
      {
        "id": 3,
        "title": "API P95 响应时间",
        "type": "graph",
        "gridPos": {"h": 8, "w": 12, "x": 12, "y": 4},
        "targets": [
          {
            "expr": "histogram_quantile(0.95, sum(rate(apex_api_response_time_seconds_bucket{instance=\"$instance\"}[5m])) by (le, uri))",
            "legendFormat": "{{uri}}",
            "refId": "A"
          }
        ],
        "yaxes": [
          {"format": "s", "label": "延迟"}
        ],
        "alert": {
          "conditions": [
            {
              "evaluator": {"params": [3], "type": "gt"},
              "operator": {"type": "and"},
              "query": {"params": ["A", "5m", "now"]},
              "reducer": {"params": [], "type": "avg"},
              "type": "query"
            }
          ],
          "executionErrorState": "alerting",
          "frequency": "1m",
          "handler": 1,
          "name": "API响应时间告警",
          "noDataState": "no_data",
          "notifications": []
        }
      },
      {
        "id": 4,
        "title": "错误率",
        "type": "graph",
        "gridPos": {"h": 8, "w": 12, "x": 0, "y": 12},
        "targets": [
          {
            "expr": "sum(rate(apex_api_request_count_total{instance=\"$instance\", status=~\"5..\"}[5m])) / sum(rate(apex_api_request_count_total{instance=\"$instance\"}[5m]))",
            "legendFormat": "错误率",
            "refId": "A"
          }
        ],
        "yaxes": [
          {"format": "percentunit", "max": 1, "min": 0}
        ]
      },
      {
        "id": 5,
        "title": "JVM 堆内存使用率",
        "type": "graph",
        "gridPos": {"h": 8, "w": 12, "x": 12, "y": 12},
        "targets": [
          {
            "expr": "jvm_memory_used_bytes{instance=\"$instance\", area=\"heap\"} / jvm_memory_max_bytes{instance=\"$instance\", area=\"heap\"}",
            "legendFormat": "堆内存使用率",
            "refId": "A"
          }
        ],
        "yaxes": [
          {"format": "percentunit", "max": 1, "min": 0}
        ]
      },
      {
        "id": 6,
        "title": "数据库连接池使用率",
        "type": "gauge",
        "gridPos": {"h": 8, "w": 6, "x": 0, "y": 20},
        "targets": [
          {
            "expr": "hikaricp_connections_active{instance=\"$instance\"} / hikaricp_connections_max{instance=\"$instance\"}",
            "refId": "A"
          }
        ],
        "options": {
          "showThresholdLabels": false,
          "showThresholdMarkers": true
        },
        "fieldConfig": {
          "defaults": {
            "unit": "percentunit",
            "max": 1,
            "min": 0,
            "thresholds": {
              "mode": "absolute",
              "steps": [
                {"value": 0, "color": "green"},
                {"value": 0.7, "color": "yellow"},
                {"value": 0.9, "color": "red"}
              ]
            }
          }
        }
      }
    ]
  }
}
```

### 3.3 变量模板配置

Grafana 支持动态变量，用于构建可复用的 Dashboard。

**常用变量**:

```json
{
  "templating": {
    "list": [
      {
        "name": "datasource",
        "type": "datasource",
        "query": "prometheus",
        "current": {
          "text": "Prometheus",
          "value": "Prometheus"
        }
      },
      {
        "name": "instance",
        "type": "query",
        "datasource": "${datasource}",
        "query": "label_values(up{job=\"apex-scaffold\"}, instance)",
        "refresh": 1,
        "multi": true,
        "includeAll": true,
        "allValue": ".*"
      },
      {
        "name": "uri",
        "type": "query",
        "datasource": "${datasource}",
        "query": "label_values(apex_api_request_count_total{instance=~\"$instance\"}, uri)",
        "refresh": 2,
        "multi": true,
        "includeAll": true
      },
      {
        "name": "interval",
        "type": "interval",
        "query": "1m,5m,10m,30m,1h",
        "current": {
          "text": "5m",
          "value": "5m"
        }
      }
    ]
  }
}
```

**在查询中使用变量**:

```promql
# 使用实例变量
up{job="apex-scaffold", instance=~"$instance"}

# 使用 URI 变量
sum(rate(apex_api_request_count_total{instance=~"$instance", uri=~"$uri"}[5m])) by (uri)

# 使用时间间隔变量
rate(apex_api_request_count_total[$interval])
```

### 3.4 告警通知配置

**配置 Alertmanager 为通知渠道**:

1. Alerting → Notification channels → Add channel
2. 配置通知渠道:

```yaml
# 钉钉 Webhook
Name: DingTalk-Operations
Type: webhook
URL: https://oapi.dingtalk.com/robot/send?access_token=YOUR_TOKEN
HTTP Method: POST
Username: (留空)
Password: (留空)

# 消息模板
{
  "msgtype": "markdown",
  "markdown": {
    "title": "Prometheus Alert",
    "text": "## {{ .GroupLabels.alertname }}\n\n**Severity:** {{ .CommonLabels.severity }}\n\n**Description:** {{ .CommonAnnotations.description }}\n\n**Instance:** {{ .CommonLabels.instance }}\n\n**Time:** {{ .StartsAt.Format \"2006-01-02 15:04:05\" }}"
  }
}
```

**Email 通知**:

```yaml
Name: Email-Ops-Team
Type: email
Addresses: ops@example.com, devops@example.com
Single email: true (发送单封邮件)
```

**Slack 通知**:

```yaml
Name: Slack-Alerts
Type: slack
URL: https://hooks.slack.com/services/YOUR/WEBHOOK/URL
Username: Grafana Alert
Recipient: #alerts
Icon emoji: :warning:
Mention: @channel (严重告警时)
```

### 3.5 监控面板分组

参考: [24\_grafana\_dashboard\_architecture.svg](../../diagrams/48_monitoring_configuration.svg)

**11 个分组，66 个面板**:

| 分组           | 面板数 | 说明                            |
| ------------ | --- | ----------------------------- |
| 1️⃣ 核心指标看板   | 6   | 服务状态、QPS、P95延迟、错误率、JVM堆、DB连接池 |
| 2️⃣ HTTP接口监控 | 4   | 请求量、响应时间分位数、外部API             |
| 3️⃣ JVM深度监控  | 10  | 堆内存、GC、线程、类加载                 |
| 4️⃣ 数据库监控    | 2   | 连接池 (HikariCP/Druid)          |
| 5️⃣ 系统资源     | 4   | 磁盘、网卡                         |
| 6️⃣ 中间件监控    | 5   | Redis 健康、节点、缓存命中率             |
| 7️⃣ 治理与安全    | 12  | Sentinel、熔断、会话、安全拦截           |
| 8️⃣ 核心业务     | 5   | 登录、注册、上传、信用变更                 |
| 9️⃣ 链路追踪     | 4   | Zipkin 在线、Span 接收             |
| 🔟 网关与接口性能   | 5   | QPS Top 10、慢接口、错误接口           |
| 1️⃣1️⃣ 熔断器状态 | 6   | 熔断器状态、失败率、慢调用                 |

***

<a id="4-监控面板说明"></a>

## 4. 监控面板说明

### 4.1 核心指标看板 (6个)

#### 面板 1: 服务状态

**指标**: `up{job="apex-scaffold"}`

**PromQL**:

```promql
up{job="apex-scaffold"}
```

**可视化**: Stat面板，绿色=1 (UP)，红色=0 (DOWN)

#### 面板 2: 请求吞吐 (QPS)

**指标**: `apex_api_request_count_total`

**PromQL**:

```promql
# 总 QPS
sum(rate(apex_api_request_count_total[5m]))

# 按状态码分组
sum(rate(apex_api_request_count_total[5m])) by (status)
```

**可视化**: Time Series 时间序列图

#### 面板 3: P95 响应时间

**指标**: `apex_api_response_time_seconds_bucket`

**PromQL**:

```promql
histogram_quantile(
  0.95,
  sum(rate(apex_api_response_time_seconds_bucket[5m])) by (le, uri)
)
```

**可视化**: Time Series，按 URI 分组

#### 面板 4: 5xx 错误率

**指标**: `apex_api_request_count_total{status=~"5.."}`

**PromQL**:

```promql
# 5xx 错误率百分比
sum(rate(apex_api_request_count_total{status=~"5.."}[5m]))
/
sum(rate(apex_api_request_count_total[5m]))
* 100
```

**可视化**: Gauge 仪表盘，阈值 >5% 红色告警

#### 面板 5: JVM 堆使用率

**指标**: `jvm_memory_used_bytes{area="heap"}`

**PromQL**:

```promql
# 堆使用率百分比
jvm_memory_used_bytes{area="heap"}
/
jvm_memory_max_bytes{area="heap"}
* 100
```

**可视化**: Gauge，阈值 >85% 橙色，>90% 红色

#### 面板 6: DB 连接池水位

**指标**: `hikaricp_connections_active`

**PromQL**:

```promql
# HikariCP 活跃连接数
hikaricp_connections_active{pool="HikariPool"}

# 连接池使用率
hikaricp_connections_active{pool="HikariPool"}
/
hikaricp_connections_max{pool="HikariPool"}
* 100
```

**可视化**: Time Series + Gauge

### 4.2 JVM 深度监控 (10个)

#### 面板 7: JVM 堆内存趋势

**PromQL**:

```promql
# 已使用堆内存
jvm_memory_used_bytes{area="heap"}

# 已提交堆内存
jvm_memory_committed_bytes{area="heap"}

# 最大堆内存
jvm_memory_max_bytes{area="heap"}
```

**可视化**: Time Series，三条曲线对比

#### 面板 8: GC 频率

**PromQL**:

```promql
# Minor GC 频率 (次/分钟)
rate(jvm_gc_pause_seconds_count{action="end of minor GC"}[5m]) * 60

# Major/Full GC 频率
rate(jvm_gc_pause_seconds_count{action=~".*major.*"}[5m]) * 60
```

**可视化**: Time Series，告警阈值 Full GC > 5次/min

#### 面板 9: GC 耗时

**PromQL**:

```promql
# GC 平均耗时 (毫秒)
rate(jvm_gc_pause_seconds_sum[5m])
/
rate(jvm_gc_pause_seconds_count[5m])
* 1000
```

**可视化**: Time Series，告警阈值 > 1000ms

#### 面板 10: 线程数

**PromQL**:

```promql
# 活跃线程数
jvm_threads_live_threads

# 守护线程数
jvm_threads_daemon_threads

# 峰值线程数
jvm_threads_peak_threads
```

**可视化**: Time Series，告警阈值 > 2000

### 4.3 告警面板示例

#### 面板: 限流拒绝 (5分钟)

**PromQL**:

```promql
# Sentinel 限流拒绝总数
increase(sentinel_block_qps_total{resource=~".*"}[5m])
```

**可视化**: Stat，显示最近5分钟拒绝数

***

<a id="5-告警规则配置"></a>

## 5. 告警规则配置

### 5.1 告警规则文件

**位置**: `/etc/prometheus/rules/apex-alerts.yml`

```yaml
groups:
  # 1. 服务可用性告警
  - name: service_availability
    interval: 30s
    rules:
      - alert: ApexServiceDown
        expr: up{job="apex-scaffold"} == 0
        for: 1m
        labels:
          severity: critical
          tier: application
        annotations:
          summary: "Apex Scaffold 服务不可用"
          description: "服务 {{ $labels.instance }} 已停止响应超过 1 分钟"
          runbook_url: "https://wiki.example.com/runbook/apex-service-down"

      - alert: ApexHealthCheckFailed
        expr: probe_success{job="blackbox"} == 0
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "健康检查失败"
          description: "{{ $labels.instance }} 健康检查失败超过 2 分钟"

  # 2. 接口质量告警
  - name: api_quality
    interval: 1m
    rules:
      - alert: High5xxErrorRate
        expr: |
          sum(rate(apex_api_request_count_total{status=~"5.."}[5m]))
          /
          sum(rate(apex_api_request_count_total[5m]))
          > 0.05
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "5xx 错误率过高"
          description: "最近 5 分钟 5xx 错误率: {{ $value | humanizePercentage }}"

      - alert: HighP95Latency
        expr: |
          histogram_quantile(
            0.95,
            sum(rate(apex_api_response_time_seconds_bucket[5m])) by (le)
          ) > 2
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "P95 响应时间过高"
          description: "P95 响应时间: {{ $value | humanizeDuration }}"

      - alert: TrafficDrop
        expr: |
          sum(rate(apex_api_request_count_total[5m]))
          <
          sum(rate(apex_api_request_count_total[5m] offset 10m)) * 0.2
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "流量骤降"
          description: "QPS 相比 10 分钟前下降超过 80%"

      - alert: ExceptionSpike
        expr: rate(apex_exception_total[5m]) > 50
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "异常突增"
          description: "异常速率: {{ $value | humanize }}/秒"

  # 3. JVM 健康告警
  - name: jvm_health
    interval: 1m
    rules:
      - alert: HighHeapUsage
        expr: |
          jvm_memory_used_bytes{area="heap"}
          /
          jvm_memory_max_bytes{area="heap"}
          > 0.85
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "JVM 堆内存使用率过高"
          description: "堆内存使用率: {{ $value | humanizePercentage }}"

      - alert: FrequentFullGC
        expr: rate(jvm_gc_pause_seconds_count{action=~".*major.*"}[5m]) * 60 > 5
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Full GC 过于频繁"
          description: "Full GC 频率: {{ $value | humanize }}/分钟"

      - alert: LongGCPause
        expr: |
          rate(jvm_gc_pause_seconds_sum[5m])
          /
          rate(jvm_gc_pause_seconds_count[5m])
          > 1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "GC 停顿时间过长"
          description: "平均 GC 停顿: {{ $value | humanizeDuration }}"

      - alert: HighThreadCount
        expr: jvm_threads_live_threads > 2000
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "线程数过高"
          description: "活跃线程数: {{ $value }}"

  # 4. 数据库与中间件
  - name: database_middleware
    interval: 1m
    rules:
      - alert: ConnectionPoolHigh
        expr: |
          hikaricp_connections_active{pool="HikariPool"}
          /
          hikaricp_connections_max{pool="HikariPool"}
          > 0.8
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "连接池水位过高"
          description: "连接池使用率: {{ $value | humanizePercentage }}"

      - alert: RedisDown
        expr: redisson_healthy == 0
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Redis 不可达"
          description: "Redis 连接失败超过 2 分钟"

  # 5. Sentinel 治理
  - name: sentinel_governance
    interval: 1m
    rules:
      - alert: HighSentinelBlock
        expr: increase(sentinel_block_qps_total[10m]) > 100
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Sentinel 拦截过多"
          description: "最近 10 分钟拦截: {{ $value }} 次"
```

### 5.2 Recording Rules (预聚合)

**位置**: `/etc/prometheus/rules/apex-recording.yml`

```yaml
groups:
  - name: apex_recording_rules
    interval: 15s
    rules:
      # QPS 5分钟速率
      - record: job:apex_api_qps:rate5m
        expr: sum(rate(apex_api_request_count_total[5m]))

      # P95 响应时间
      - record: job:apex_api_p95_latency:histogram_quantile
        expr: |
          histogram_quantile(
            0.95,
            sum(rate(apex_api_response_time_seconds_bucket[5m])) by (le)
          )

      # 5xx 错误率
      - record: job:apex_api_5xx_rate:rate5m
        expr: |
          sum(rate(apex_api_request_count_total{status=~"5.."}[5m]))
          /
          sum(rate(apex_api_request_count_total[5m]))

      # JVM 堆使用率
      - record: job:jvm_heap_usage:ratio
        expr: |
          jvm_memory_used_bytes{area="heap"}
          /
          jvm_memory_max_bytes{area="heap"}
```

***

<a id="6-运维操作"></a>

## 6. 运维操作

### 6.1 配置热重载

```bash
# 方法 1: HTTP API (推荐)
curl -X POST http://prometheus:9090/-/reload

# 方法 2: 发送 SIGHUP 信号
kill -HUP $(pidof prometheus)
```

### 6.2 配置文件校验

```bash
# 校验 prometheus.yml
promtool check config /etc/prometheus/prometheus.yml

# 校验告警规则
promtool check rules /etc/prometheus/rules/*.yml
```

### 6.3 查询指标

```bash
# 查询当前所有指标
curl http://prometheus:9090/api/v1/label/__name__/values | jq

# 查询指定指标
curl -G http://prometheus:9090/api/v1/query \
  --data-urlencode 'query=up{job="apex-scaffold"}' | jq

# 范围查询
curl -G http://prometheus:9090/api/v1/query_range \
  --data-urlencode 'query=rate(apex_api_request_count_total[5m])' \
  --data-urlencode 'start=2024-01-01T00:00:00Z' \
  --data-urlencode 'end=2024-01-01T01:00:00Z' \
  --data-urlencode 'step=15s' | jq
```

### 6.4 告警管理

```bash
# 查看所有告警
curl http://prometheus:9090/api/v1/alerts | jq

# 使用 amtool 查看 Alertmanager 告警
amtool --alertmanager.url=http://alertmanager:9093 alert query

# 静默告警
amtool --alertmanager.url=http://alertmanager:9093 silence add \
  alertname=HighHeapUsage \
  --duration=2h \
  --comment="Planned maintenance"
```

### 6.5 数据备份

```bash
# 创建快照 (需要启用 --web.enable-admin-api)
curl -X POST http://prometheus:9090/api/v1/admin/tsdb/snapshot

# 快照位置: /data/prometheus/snapshots/<snapshot-id>

# 备份快照
tar -czf prometheus-snapshot-$(date +%Y%m%d).tar.gz \
  /data/prometheus/snapshots/<snapshot-id>
```

***

<a id="7-故障排查"></a>

## 7. 故障排查

### 7.1 Prometheus 故障

#### 问题 1: 抓取目标失败

**症状**: Targets 页面显示 DOWN

**排查步骤**:

```bash
# 1. 检查目标服务是否运行
curl http://apex-scaffold:8081/actuator/prometheus

# 2. 检查网络连通性
telnet apex-scaffold 8081

# 3. 查看 Prometheus 日志
docker logs prometheus

# 4. 检查 scrape_configs 配置
promtool check config /etc/prometheus/prometheus.yml
```

**常见原因**:

- 服务未启动或崩溃

- 网络不通 (防火墙/网络策略)

- 配置错误 (地址、端口、路径)

- 抓取超时 (调整 scrape\_timeout)

#### 问题 2: 磁盘空间不足

**症状**: TSDB 写入失败

**解决方案**:

```bash
# 1. 清理旧数据
rm -rf /data/prometheus/wal/*

# 2. 缩短保留时间
--storage.tsdb.retention.time=7d

# 3. 限制存储大小
--storage.tsdb.retention.size=30GB
```

#### 问题 3: 查询慢/超时

**排查步骤**:

```bash
# 1. 查看 TSDB 统计
curl http://prometheus:9090/api/v1/status/tsdb | jq

# 2. 检查 cardinality (基数过高)
curl http://prometheus:9090/api/v1/label/__name__/values | jq | wc -l

# 3. 使用 Recording Rules 预聚合复杂查询
```

### 7.2 Grafana 故障

#### 问题 1: 数据源连接失败

**排查步骤**:

```bash
# 1. 测试 Prometheus API
curl http://prometheus:9090/api/v1/query?query=up

# 2. 检查 Grafana 配置
# Configuration → Data Sources → Prometheus → Test

# 3. 查看 Grafana 日志
docker logs grafana
```

#### 问题 2: Dashboard 无数据

**排查步骤**:

1. 检查时间范围是否正确
2. 在 Prometheus UI 测试 PromQL
3. 检查 Panel Query 是否有语法错误
4. 确认数据源选择正确

### 7.3 Alertmanager 故障

#### 问题 1: 告警未发送

**排查步骤**:

```bash
# 1. 检查告警规则是否生效
curl http://prometheus:9090/api/v1/alerts | jq

# 2. 查看 Alertmanager 状态
curl http://alertmanager:9093/api/v2/status | jq

# 3. 检查 receiver 配置
cat /etc/alertmanager/alertmanager.yml

# 4. 查看 Alertmanager 日志
docker logs alertmanager
```

***

<a id="8-最佳实践"></a>

## 8. 最佳实践

### 8.1 指标设计

✅ **推荐做法**:

1. 使用标准命名规范: `<namespace>_<subsystem>_<name>_<unit>`
2. Label 不要过多 (< 10个)，避免高基数
3. 优先使用 Counter 和 Histogram，避免 Summary
4. 业务指标与技术指标分离

❌ **避免做法**:

1. Label 值使用用户 ID、订单号等高基数值
2. 指标名包含 Label 值 (如 `http_status_200_total`)
3. 创建过多不使用的指标

### 8.2 告警设计

✅ **推荐做法**:

1. 设置合理的 `for` 时长避免抖动 (通常 5-10分钟)
2. 使用 `severity` 分级: critical / warning / info
3. 告警消息包含明确的修复建议和 Runbook 链接
4. 配置告警抑制规则避免告警风暴
5. 定期 review 和优化告警规则

❌ **避免做法**:

1. 没有 `for` 时长，导致频繁误报
2. 所有告警都是 critical
3. 告警消息不明确，无法快速定位
4. 告警规则过多，产生告警疲劳

### 8.3 Dashboard 设计

✅ **推荐做法**:

1. 首屏展示最关键的 6-8 个指标
2. 使用变量 (Variables) 实现筛选和切换
3. 面板按逻辑分组 (Rows)
4. 合理使用颜色阈值和告警状态
5. 添加面板说明和链接

❌ **避免做法**:

1. 单个 Dashboard 包含过多面板 (> 50个)
2. 复杂查询未使用 Recording Rules
3. 时间范围固定死

### 8.4 性能优化

**Prometheus 优化**:

```yaml
# 1. 降低抓取频率 (非关键服务)
scrape_interval: 30s

# 2. 使用 metric_relabel_configs 过滤不需要的指标
metric_relabel_configs:
  - source_labels: [__name__]
    regex: 'go_.*|process_.*'
    action: drop

# 3. 启用 Recording Rules 预聚合
rule_files:
  - '/etc/prometheus/rules/recording.yml'

# 4. 使用远程存储 (Thanos/Cortex)
remote_write:
  - url: http://thanos-receive:19291/api/v1/receive
```

**Grafana 优化**:

1. 使用 `$__interval` 变量动态调整步长
2. 限制查询时间范围 (< 7天)
3. 使用缓存和 CDN
4. 定期清理无用 Dashboard

### 8.5 安全加固

**Prometheus**:

```yaml
# 1. 启用基础认证
basic_auth:
  username: admin
  password: <password>

# 2. 使用 TLS
tls_config:
  cert_file: /etc/prometheus/cert.pem
  key_file: /etc/prometheus/key.pem

# 3. 限制 API 访问
--web.external-url=https://prometheus.example.com
--web.route-prefix=/
```

**Grafana**:

```ini
# grafana.ini
[security]
admin_user = admin
admin_password = <strong-password>
secret_key = <random-secret>

[auth]
disable_login_form = false
oauth_auto_login = false

[auth.anonymous]
enabled = false
```

***

## 9. 相关文档

- [系统架构设计](../../architecture/system-architecture.md)

- 运维部署指南（目标不存在，无已核实替代文件；原路径：`./deployment-guide.md`）

- 监控告警SOP（目标不存在，无已核实替代文件；原路径：`./monitoring-sop.md`）

***

## 10. 附录

### 10.1 PromQL 实用查询示例 (30+)

#### 📊 应用层指标

```promql
# 1. 实时 QPS (总请求数)
sum(rate(apex_api_request_count_total[5m]))

# 2. 按接口统计 QPS (Top 10)
topk(10, sum(rate(apex_api_request_count_total[5m])) by (uri))

# 3. 按状态码统计 QPS
sum(rate(apex_api_request_count_total[5m])) by (status)

# 4. 错误率 (5xx / 总请求)
sum(rate(apex_api_request_count_total{status=~"5.."}[5m])) 
/ 
sum(rate(apex_api_request_count_total[5m]))

# 5. API P50/P95/P99 响应时间
histogram_quantile(0.50, sum(rate(apex_api_response_time_seconds_bucket[5m])) by (le, uri))
histogram_quantile(0.95, sum(rate(apex_api_response_time_seconds_bucket[5m])) by (le, uri))
histogram_quantile(0.99, sum(rate(apex_api_response_time_seconds_bucket[5m])) by (le, uri))

# 6. 平均响应时间 (AVG)
sum(rate(apex_api_response_time_seconds_sum[5m])) by (uri)
/
sum(rate(apex_api_response_time_seconds_count[5m])) by (uri)

# 7. 慢接口 Top 10 (P95 > 1秒)
topk(10, 
  histogram_quantile(0.95, 
    sum(rate(apex_api_response_time_seconds_bucket[5m])) by (le, uri)
  ) > 1
)

# 8. 5分钟内总请求数
increase(apex_api_request_count_total[5m])

# 9. 请求量增长率
(rate(apex_api_request_count_total[5m]) - rate(apex_api_request_count_total[5m] offset 1h))
/
rate(apex_api_request_count_total[5m] offset 1h)
* 100

# 10. 活跃用户数 (需自定义指标)
count(count by (user_id)(apex_user_active))
```

#### 🔧 JVM 监控指标

```promql
# 11. 堆内存使用率
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100

# 12. 非堆内存使用率
jvm_memory_used_bytes{area="nonheap"} / jvm_memory_max_bytes{area="nonheap"} * 100

# 13. GC 频率 (次/秒)
rate(jvm_gc_pause_seconds_count[5m])

# 14. GC 总耗时占比
rate(jvm_gc_pause_seconds_sum[5m]) / rate(process_uptime_seconds[5m]) * 100

# 15. Young GC 平均耗时 (ms)
rate(jvm_gc_pause_seconds_sum{action="end of minor GC"}[5m])
/
rate(jvm_gc_pause_seconds_count{action="end of minor GC"}[5m])
* 1000

# 16. Old GC 平均耗时 (ms)
rate(jvm_gc_pause_seconds_sum{action="end of major GC"}[5m])
/
rate(jvm_gc_pause_seconds_count{action="end of major GC"}[5m])
* 1000

# 17. 线程总数
jvm_threads_live_threads

# 18. 死锁线程数
jvm_threads_deadlocked_threads

# 19. 类加载数量
jvm_classes_loaded_classes

# 20. CPU 使用率
system_cpu_usage * 100
```

#### 💾 数据库与缓存指标

```promql
# 21. HikariCP 连接池使用率
hikaricp_connections_active / hikaricp_connections_max * 100

# 22. 连接池等待线程数
hikaricp_connections_pending

# 23. 平均连接获取时间 (ms)
rate(hikaricp_connections_acquire_seconds_sum[5m])
/
rate(hikaricp_connections_acquire_seconds_count[5m])
* 1000

# 24. 连接超时数
increase(hikaricp_connections_timeout_total[5m])

# 25. Redis 缓存命中率
rate(redis_keyspace_hits_total[5m])
/
(rate(redis_keyspace_hits_total[5m]) + rate(redis_keyspace_misses_total[5m]))
* 100

# 26. Redis 内存使用率
redis_memory_used_bytes / redis_memory_max_bytes * 100

# 27. Redis Key 数量
redis_db_keys{db="db0"}

# 28. Redis 慢查询数
increase(redis_slowlog_length[5m])
```

#### 🖥️ 系统资源指标

```promql
# 29. CPU 使用率 (多核平均)
100 - (avg by (instance)(rate(node_cpu_seconds_total{mode="idle"}[5m])) * 100)

# 30. 内存使用率
(1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)) * 100

# 31. 磁盘使用率
(1 - (node_filesystem_avail_bytes{fstype!~"tmpfs|fuse.lxcfs"} 
      / node_filesystem_size_bytes)) * 100

# 32. 网络接收速率 (MB/s)
rate(node_network_receive_bytes_total[5m]) / 1024 / 1024

# 33. 网络发送速率 (MB/s)
rate(node_network_transmit_bytes_total[5m]) / 1024 / 1024

# 34. 磁盘 I/O 使用率
rate(node_disk_io_time_seconds_total[5m]) * 100

# 35. 文件描述符使用率
process_open_fds / process_max_fds * 100
```

#### 🔔 告警相关查询

```promql
# 36. 服务可用性 (UP=1, DOWN=0)
up{job="apex-scaffold"}

# 37. 服务宕机时长 (秒)
time() - max(timestamp(up{job="apex-scaffold"} == 1))

# 38. 触发中的告警数量
count(ALERTS{alertstate="firing"})

# 39. 按严重程度统计告警
count by (severity)(ALERTS{alertstate="firing"})

# 40. 最近5分钟触发过的告警
count(ALERTS{alertstate="firing"} or ALERTS{alertstate="firing" offset 5m})
```

#### 📈 聚合与数学运算

```promql
# 聚合函数
sum()           # 求和
avg()           # 平均值
min()           # 最小值
max()           # 最大值
count()         # 计数
stddev()        # 标准差
topk(N, )       # 前N个最大值
bottomk(N, )    # 前N个最小值

# 时间函数
rate()          # 每秒平均增长率 (适用于Counter)
irate()         # 瞬时增长率 (适用于快速变化的指标)
increase()      # 时间范围内总增长量
delta()         # 时间范围内变化量 (适用于Gauge)
predict_linear() # 线性预测

# 标签操作
sum by (label)              # 按标签分组求和
sum without (label)         # 排除标签分组求和
count_values("label", )     # 统计不同值的数量
label_replace()             # 替换标签值
label_join()                # 合并多个标签
```

### 10.2 PromQL 速查表

| 操作符      | 说明    | 示例                  |
| -------- | ----- | ------------------- |
| `+`      | 加法    | `a + b`             |
| `-`      | 减法    | `a - b`             |
| `*`      | 乘法    | `a * 100`           |
| `/`      | 除法    | `a / b`             |
| `%`      | 取模    | `a % 60`            |
| `^`      | 幂运算   | `2 ^ 10`            |
| `==`     | 等于    | `up == 1`           |
| `!=`     | 不等于   | `up != 0`           |
| `>`      | 大于    | `value > 100`       |
| `<`      | 小于    | `value < 10`        |
| `>=`     | 大于等于  | `rate >= 0.8`       |
| `<=`     | 小于等于  | `rate <= 0.2`       |
| `and`    | 逻辑与   | `a > 0 and b > 0`   |
| `or`     | 逻辑或   | `a or b`            |
| `unless` | 排除    | `a unless b`        |
| `=~`     | 正则匹配  | `{status=~"5.."}`   |
| `!~`     | 正则不匹配 | `{path!~"/health"}` |

### 10.2 常用 Grafana 变量

```
# 实例列表
label_values(up{job="apex-scaffold"}, instance)

# 时间范围
$__from (起始时间戳 ms)
$__to   (结束时间戳 ms)
$__interval (步长,自动计算)

# 动态步长
rate(apex_api_request_count_total[$__rate_interval])
```

***

**文档版本**: v1.0\
**最后更新**: 2024-01-15\
**维护人**: DevOps Team
