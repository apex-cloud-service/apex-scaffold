# Grafana 缓存监控配置指南

本文档提供 Apex Scaffold 缓存监控在 Grafana 中的完整配置步骤。

## 前置要求

- Grafana 7.0+
- Prometheus 已配置并采集应用指标
- Spring Boot Actuator 已启用 Prometheus 端点

## 快速开始

### 1. 导入 Dashboard

#### 方法一：通过 UI 导入

1. 登录 Grafana
2. 点击左侧菜单 **+** → **Import**
3. 上传文件：选择 `docs/cache/grafana-cache-dashboard.json`
4. 选择 Prometheus 数据源
5. 点击 **Import**

#### 方法二：通过 API 导入

```bash
curl -X POST http://localhost:3000/api/dashboards/db \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -d @docs/cache/grafana-cache-dashboard.json
```

#### 方法三：通过 Provisioning 自动导入

创建配置文件 `/etc/grafana/provisioning/dashboards/apex-cache.yaml`：

```yaml
apiVersion: 1

providers:
  - name: 'Apex Cache Monitoring'
    orgId: 1
    folder: 'Apex Scaffold'
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    allowUiUpdates: true
    options:
      path: /var/lib/grafana/dashboards/apex-cache
```

复制 Dashboard 文件：

```bash
sudo cp docs/cache/grafana-cache-dashboard.json \
  /var/lib/grafana/dashboards/apex-cache/
```

重启 Grafana：

```bash
sudo systemctl restart grafana-server
```

## Dashboard 面板说明

### 1. 缓存命中率 (Panel ID: 1)

**指标：**
- 总命中率：所有缓存请求的命中率
- L1 命中率：Caffeine 本地缓存命中率
- L2 命中率：Redis 分布式缓存命中率

**告警规则：**
- 触发条件：命中率 < 80%
- 持续时间：5 分钟
- 级别：Warning

**优化建议：**
- 命中率 < 60%：检查 TTL 配置，考虑启用缓存预热
- 命中率 60-80%：正常但有优化空间
- 命中率 > 80%：良好

### 2. 缓存请求 QPS (Panel ID: 2)

**指标：**
- 总请求 QPS
- 命中 QPS
- 未命中 QPS

**使用场景：**
- 监控缓存负载
- 分析流量模式
- 容量规划

### 3. 缓存穿透防护 (Panel ID: 3)

**指标：**
- 布隆过滤器拦截次数/分钟
- 空值标记命中次数/分钟
- DB 回源次数/分钟

**告警规则：**
- 触发条件：布隆过滤器拦截 > 100 次/分钟
- 持续时间：5 分钟
- 级别：Warning

**问题排查：**
- 拦截次数突增：可能存在恶意攻击或爬虫
- DB 回源频繁：检查布隆过滤器容量

### 4. 缓存击穿防护 (Panel ID: 4)

**指标：**
- 分布式锁失败次数/分钟
- 分布式锁成功次数/分钟
- Single-Flight 请求合并次数/分钟

**告警规则：**
- 触发条件：锁失败 > 10 次/分钟
- 持续时间：2 分钟
- 级别：Critical

**问题排查：**
- 锁失败频繁：检查 Redis 连接状态
- 锁等待时间长：调整 `lock-wait-time-millis`

### 5. 读降级监控 (Panel ID: 5)

**指标：**
- 读降级次数/分钟
- 读降级占比

**告警规则：**
- 触发条件：读降级次数 > 0
- 持续时间：1 分钟
- 级别：Critical

**问题排查：**
- 读降级启用：数据库可能存在故障
- 立即检查数据库连接和性能

### 6. 分布式锁等待时间 (Panel ID: 6)

**指标：**
- P95 等待时间
- P99 等待时间

**告警规则：**
- 触发条件：P95 > 500ms
- 持续时间：5 分钟
- 级别：Warning

**优化建议：**
- < 100ms：优秀
- 100-500ms：正常
- > 500ms：需要优化数据库查询或调整锁配置

### 7. 缓存预热状态 (Panel ID: 7)

**指标：**
- 预热成功次数/分钟
- 预热失败次数/分钟

**告警规则：**
- 触发条件：预热失败 > 0
- 持续时间：5 分钟
- 级别：Warning

### 8. 布隆过滤器容量 (Panel ID: 8)

**指标：**
- 使用率（百分比）

**阈值说明：**
- 绿色：0-70%（正常）
- 黄色：70-80%（关注）
- 橙色：80-90%（警告）
- 红色：> 90%（严重，需扩容）

### 9. TTL Jitter 应用统计 (Panel ID: 9)

**指标：**
- 最近 1 小时应用次数

**说明：**
- 显示 TTL jitter 功能的使用频率
- 帮助验证缓存雪崩防护是否生效

### 10. Redis 连接池状态 (Panel ID: 10)

**指标：**
- 活跃连接数
- 空闲连接数
- 总连接数

**监控要点：**
- 活跃连接接近总连接数：需要扩大连接池
- 空闲连接过多：可以减小连接池

## Prometheus 配置

### Spring Boot Actuator 配置

确保 `application.yml` 中已启用 Prometheus：

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
      application: apex-scaffold
      environment: ${spring.profiles.active}
```

### Prometheus 抓取配置

添加到 `prometheus.yml`：

```yaml
scrape_configs:
  - job_name: 'apex-scaffold'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
    static_configs:
      - targets: ['localhost:8080']
        labels:
          application: 'apex-scaffold'
          environment: 'production'
```

### 验证指标采集

```bash
curl http://localhost:8080/actuator/prometheus | grep apex_cache
```

## 告警配置

### Grafana 告警通知渠道

#### 1. 配置钉钉通知

进入 **Alerting** → **Contact points** → **New contact point**：

```yaml
Name: dingtalk-cache-alerts
Type: DingDing
URL: https://oapi.dingtalk.com/robot/send?access_token=YOUR_TOKEN
```

测试消息：

```bash
curl 'https://oapi.dingtalk.com/robot/send?access_token=YOUR_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{
    "msgtype": "text",
    "text": {
      "content": "Grafana 告警测试"
    }
  }'
```

#### 2. 配置企业微信通知

```yaml
Name: wecom-cache-alerts
Type: WeChat
Corp ID: YOUR_CORP_ID
Secret: YOUR_SECRET
Agent ID: YOUR_AGENT_ID
```

#### 3. 配置 Slack 通知

```yaml
Name: slack-cache-alerts
Type: Slack
Webhook URL: https://hooks.slack.com/services/YOUR/WEBHOOK/URL
```

### 告警规则示例

Dashboard 中已包含以下告警规则：

1. **缓存命中率过低** (Warning)
   - 命中率 < 80% 持续 5 分钟

2. **缓存穿透频繁** (Warning)
   - 布隆过滤器拦截 > 100 次/分钟持续 5 分钟

3. **缓存击穿检测** (Critical)
   - 分布式锁失败 > 10 次/分钟持续 2 分钟

4. **读降级启用** (Critical)
   - 读降级次数 > 0 持续 1 分钟

5. **锁等待时间过长** (Warning)
   - P95 等待时间 > 500ms 持续 5 分钟

6. **缓存预热失败** (Warning)
   - 预热失败次数 > 0 持续 5 分钟

## 自定义配置

### 修改刷新间隔

编辑 Dashboard 设置：

1. 点击右上角 **Dashboard settings** (⚙️)
2. 在 **General** 标签中找到 **Auto refresh**
3. 修改为所需间隔（如 `5s`, `30s`, `1m`）

### 添加变量过滤

添加环境变量过滤器：

1. 进入 **Dashboard settings** → **Variables**
2. 点击 **Add variable**
3. 配置：
   ```
   Name: environment
   Type: Query
   Data source: Prometheus
   Query: label_values(cache_requests_total, environment)
   ```
4. 在面板查询中使用：
   ```promql
   cache_hits_total{environment="$environment"}
   ```

### 修改告警阈值

编辑面板告警：

1. 点击面板标题 → **Edit**
2. 切换到 **Alert** 标签
3. 修改 **Conditions** 中的阈值
4. 保存更改

## 故障排查

### 问题 1：Dashboard 无数据

**可能原因：**
- Prometheus 未采集到指标
- 数据源配置错误
- 应用未启动或 Actuator 未启用

**解决方法：**

```bash
curl http://localhost:9090/api/v1/targets

curl http://localhost:8080/actuator/prometheus | grep cache

curl http://localhost:9090/api/v1/query?query=cache_requests_total
```

### 问题 2：告警不触发

**检查清单：**
- [ ] 告警规则是否正确配置
- [ ] 通知渠道是否已测试
- [ ] 告警状态是否为 `OK`
- [ ] 是否有数据满足告警条件

**查看告警状态：**

进入 **Alerting** → **Alert rules**，查看规则状态。

### 问题 3：指标名称不匹配

**原因：**
- Micrometer 指标命名规则
- 应用自定义指标前缀

**解决方法：**

查看实际指标名称：

```bash
curl http://localhost:8080/actuator/prometheus | grep cache | cut -d'{' -f1 | sort -u
```

修改 Dashboard 中的查询表达式。

## 最佳实践

### 1. 分层监控策略

```
实时监控（Dashboard）→ 短期告警（Grafana Alert）→ 长期分析（Prometheus）
```

### 2. 告警降噪

- 设置合理的告警阈值和持续时间
- 使用告警分组和静默规则
- 定期 review 告警规则

### 3. 性能优化

- 使用 Recording Rules 预计算复杂查询
- 调整数据保留时间
- 限制时间范围选择

### 4. 团队协作

- 共享 Dashboard URL
- 使用文件夹组织 Dashboard
- 定期导出备份配置

## 扩展功能

### 1. 集成 Loki 日志

添加日志面板查询缓存相关日志：

```logql
{app="apex-scaffold"} |= "缓存" | json | line_format "{{.timestamp}} {{.level}} {{.message}}"
```

### 2. 添加 SLA 面板

计算缓存 SLA：

```promql
sum(rate(cache_hits_total[24h])) / sum(rate(cache_requests_total[24h])) * 100
```

### 3. 成本分析

监控 Redis 使用成本：

```promql
redis_memory_used_bytes * COST_PER_GB
```

## 相关文档

- [缓存优化方案](CACHE_OPTIMIZATION_SOLUTION.md)
- [缓存使用指南](CACHE_USAGE_GUIDE.md)
- [监控告警配置](CACHE_MONITORING_ALERTS.md)
- [性能调优指南](CACHE_PERFORMANCE_TUNING.md)

## 技术支持

如遇问题，请参考：
- Grafana 官方文档：https://grafana.com/docs/
- Prometheus 查询语法：https://prometheus.io/docs/prometheus/latest/querying/basics/
- 项目 Issue 跟踪
