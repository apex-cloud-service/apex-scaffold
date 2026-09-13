# Grafana监控平台现状与配置总结

**项目**: Apex Scaffold  
**创建日期**: 2026-09-07  
**监控栈**: Prometheus + Grafana + Zipkin + Loki

---

## 一、监控端点配置现状

### 1.1 Management端点配置

**配置文件**: `apex-interfaces/src/main/resources/application.yml`

**Management端口**: 8081 (与业务端口8080物理隔离)

**已暴露端点**:
```yaml
management:
  server:
    port: 8081
    address: 127.0.0.1  # 仅回环地址,外部不可达
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,circuitbreakers
```

**端点说明**:
- ✅ `health` - 健康检查(存活/就绪探针)
- ✅ `info` - 应用信息
- ✅ `metrics` - 指标数据
- ✅ `prometheus` - Prometheus专用端点
- ✅ `circuitbreakers` - 熔断器状态

**结论**: 所有必需的监控端点已暴露完整。

### 1.2 各环境配置差异

| 环境 | 暴露端点 | 说明 |
|------|----------|------|
| **basic** | health,info | 基础版仅健康检查 |
| **standard** | health,info,metrics,prometheus | 标准版含监控指标 |
| **enterprise** | health,info,metrics,prometheus,circuitbreakers | 企业版含熔断器监控 |
| **prod** | health,info,metrics,prometheus,circuitbreakers | 生产环境完整监控 |

---

## 二、监控指标可用性验证

### 2.1 JVM指标

**测试命令**:
```bash
curl -s http://localhost:8081/actuator/prometheus | grep "^jvm_" | head -20
```

**预期指标**:
- ✅ `jvm_memory_used_bytes` - 内存使用
- ✅ `jvm_memory_max_bytes` - 最大内存
- ✅ `jvm_gc_pause_seconds` - GC暂停时间
- ✅ `jvm_threads_live` - 活跃线程数
- ✅ `jvm_threads_states` - 线程状态分布
- ✅ `jvm_classes_loaded_classes` - 已加载类数
- ✅ `jvm_buffer_memory_used_bytes` - 缓冲区使用

**JVM深度监控指标**:
- ✅ `jvm_memory_used_bytes{area="nonheap",id="Metaspace"}` - Metaspace
- ✅ `jvm_memory_used_bytes{area="nonheap",id="CodeCache"}` - CodeCache
- ✅ `jvm_gc_pause_seconds{quantile="0.5|0.95|0.99"}` - GC暂停分位数
- ✅ `process_resident_memory_bytes` - RSS物理内存
- ✅ `process_virtual_memory_bytes` - 虚拟内存

### 2.2 Netty/WebFlux指标

**测试命令**:
```bash
curl -s http://localhost:8081/actuator/prometheus | grep "reactor_netty"
```

**预期指标**:
- ✅ `reactor_netty_connection_provider_active_connections` - 活跃连接
- ✅ `reactor_netty_connection_provider_pending_connections` - 待分配连接
- ✅ `reactor_netty_connection_provider_max_connections` - 最大连接数
- ✅ `reactor_netty_eventloop_pending_tasks` - EventLoop待处理任务
- ✅ `reactor_netty_bytebuf_allocator_used_direct_memory` - Direct内存使用

### 2.3 Resilience4j熔断器指标

**测试命令**:
```bash
curl -s http://localhost:8081/actuator/prometheus | grep "resilience4j"
```

**预期指标**:
- ✅ `resilience4j_circuitbreaker_state` - 熔断器状态
- ✅ `resilience4j_circuitbreaker_failure_rate` - 失败率
- ✅ `resilience4j_circuitbreaker_successful_calls` - 成功调用
- ✅ `resilience4j_circuitbreaker_failed_calls` - 失败调用
- ✅ `resilience4j_circuitbreaker_not_permitted_calls` - 被拒绝调用

**配置验证**:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: circuitbreakers  # ✅ 已暴露
```

### 2.4 Sentinel指标

**说明**: Sentinel指标通过独立端口8720暴露

**测试命令**:
```bash
curl -s http://localhost:8720/metrics
```

**Prometheus抓取配置**: `config/monitoring/prometheus/prometheus.yml`
```yaml
- job_name: 'apex-sentinel'
  static_configs:
    - targets: ['localhost:8720']
```

---

## 三、Grafana Dashboard现状

### 3.1 现有Dashboard

**位置**: `config/monitoring/grafana/`

**基础Dashboard**: `apex-dashboard.json` (42个面板)
**增强Dashboard**: `apex-dashboard-enhanced.json` (新增24个面板)

**总计**: 66个监控面板

### 3.2 面板分组

| 分组 | 面板数 | 状态 |
|------|--------|------|
| 核心指标 | 6 | ✅ 已有 |
| HTTP接口 | 4 | ✅ 已有 |
| JVM基础 | 2 | ✅ 已有 |
| JVM深度 | 8 | 🆕 待新增 |
| 数据库 | 2 | ✅ 已有 |
| 系统资源 | 4 | ✅ 已有 |
| 中间件 | 5 | ✅ 已有 |
| Sentinel治理 | 6 | 🆕 待新增 |
| 治理与安全 | 6 | ✅ 已有 |
| 核心业务 | 5 | ✅ 已有 |
| 链路追踪 | 4 | ✅ 已有 |
| 网关性能 | 5 | 🆕 待新增 |
| 熔断器监控 | 5 | 🆕 待新增 |
| 外部API | 2 | ✅ 已有 |

---

## 四、新增监控面板PromQL查询

### 4.1 JVM深度监控 (8个面板)

#### 面板1: 类加载统计

```promql
# 当前已加载类数量
jvm_classes_loaded_classes{application="apex-scaffold"}

# 累计加载类数量
jvm_classes_loaded{application="apex-scaffold"}

# 累计卸载类数量
jvm_classes_unloaded{application="apex-scaffold"}
```

#### 面板2: 类加载速率

```promql
# 类加载速率 (classes/s)
rate(jvm_classes_loaded[5m])

# 类卸载速率 (classes/s)
rate(jvm_classes_unloaded[5m])
```

#### 面板3: 进程内存使用

```promql
# RSS (Resident Set Size) - 实际物理内存
process_resident_memory_bytes{application="apex-scaffold"}

# VSZ (Virtual Memory Size) - 虚拟内存
process_virtual_memory_bytes{application="apex-scaffold"}
```

#### 面板4: Metaspace使用率

```promql
# Metaspace已使用
jvm_memory_used_bytes{area="nonheap",id="Metaspace"}

# Metaspace最大值
jvm_memory_max_bytes{area="nonheap",id="Metaspace"}

# 使用率 (%)
100 * jvm_memory_used_bytes{area="nonheap",id="Metaspace"} 
  / jvm_memory_max_bytes{area="nonheap",id="Metaspace"}
```

**告警阈值**:
- Warning: > 80%
- Critical: > 90%

#### 面板5: CodeCache使用率

```promql
# CodeCache已使用 (JIT编译后的native代码)
jvm_memory_used_bytes{area="nonheap",id="CodeCache"}

# CodeCache最大值
jvm_memory_max_bytes{area="nonheap",id="CodeCache"}

# 使用率 (%)
100 * jvm_memory_used_bytes{area="nonheap",id="CodeCache"} 
  / jvm_memory_max_bytes{area="nonheap",id="CodeCache"}
```

**告警阈值**:
- Warning: > 70%
- Critical: > 85%

#### 面板6: JVM线程状态分布

```promql
# 总线程数
jvm_threads_live{application="apex-scaffold"}

# 守护线程数
jvm_threads_daemon{application="apex-scaffold"}

# 峰值线程数
jvm_threads_peak{application="apex-scaffold"}

# Runnable状态线程
jvm_threads_states{state="runnable",application="apex-scaffold"}

# Blocked状态线程
jvm_threads_states{state="blocked",application="apex-scaffold"}

# Waiting状态线程
jvm_threads_states{state="waiting",application="apex-scaffold"}

# Timed-Waiting状态线程
jvm_threads_states{state="timed-waiting",application="apex-scaffold"}
```

**可视化**: 堆叠面积图(Stacked Area Chart)

#### 面板7: GC暂停时间分位数

```promql
# GC暂停时间 P50
jvm_gc_pause_seconds{application="apex-scaffold",quantile="0.5"}

# GC暂停时间 P95
jvm_gc_pause_seconds{application="apex-scaffold",quantile="0.95"}

# GC暂停时间 P99
jvm_gc_pause_seconds{application="apex-scaffold",quantile="0.99"}

# GC暂停时间 P999
jvm_gc_pause_seconds{application="apex-scaffold",quantile="0.999"}
```

**告警阈值**:
- P99 > 100ms: Warning
- P99 > 500ms: Critical

#### 面板8: GC暂停时间热力图

```promql
# GC暂停时间直方图
increase(jvm_gc_pause_seconds_bucket[5m])
```

**可视化**: Heatmap

---

### 4.2 网关与接口性能 (5个面板)

#### 面板15: 慢请求Top10

```promql
# 接口平均响应时间 Top10 (>3s)
topk(10, 
  avg_over_time(
    http_server_requests_seconds_sum{application="apex-scaffold"}[5m]
  ) 
  / 
  avg_over_time(
    http_server_requests_seconds_count{application="apex-scaffold"}[5m]
  )
) > 3
```

#### 面板16: 慢请求趋势

```promql
# 慢请求数量 (>3s)
sum(rate(
  http_server_requests_seconds_bucket{le="3.0",application="apex-scaffold"}[5m]
))
```

#### 面板19: WebFlux活跃连接数

```promql
# Netty活跃连接数
reactor_netty_connection_provider_active_connections{application="apex-scaffold"}

# Netty待分配连接数
reactor_netty_connection_provider_pending_connections{application="apex-scaffold"}

# 连接池最大连接数
reactor_netty_connection_provider_max_connections{application="apex-scaffold"}

# 连接池使用率 (%)
100 * reactor_netty_connection_provider_active_connections 
  / reactor_netty_connection_provider_max_connections
```

---

### 4.3 熔断器状态监控 (5个面板)

#### 面板20: 熔断器状态分布

```promql
# Closed状态熔断器数量
count(resilience4j_circuitbreaker_state{state="closed"})

# Open状态熔断器数量
count(resilience4j_circuitbreaker_state{state="open"})

# HalfOpen状态熔断器数量
count(resilience4j_circuitbreaker_state{state="half_open"})

# 按熔断器名称分组的状态
resilience4j_circuitbreaker_state{application="apex-scaffold"}
```

#### 面板21: 实时失败率

```promql
# 熔断器失败率 (%)
100 * resilience4j_circuitbreaker_failure_rate{application="apex-scaffold"}

# 按熔断器分组
100 * resilience4j_circuitbreaker_failure_rate
```

**阈值可视化**:
- 绿色: < 10%
- 黄色: 10-30%
- 橙色: 30-50%
- 红色: > 50%

---

## 五、监控配置文件

### 5.1 Prometheus配置

**文件**: `config/monitoring/prometheus/prometheus.yml`

**抓取目标**:
```yaml
scrape_configs:
  - job_name: 'apex-scaffold'
    static_configs:
      - targets: ['localhost:8081']  # 应用监控端点
  
  - job_name: 'apex-sentinel'
    static_configs:
      - targets: ['localhost:8720']  # Sentinel指标
```

### 5.2 Grafana数据源

**文件**: `config/monitoring/grafana/provisioning/datasources/datasources.yml`

**配置内容**:
```yaml
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
```

### 5.3 Dashboard自动加载

**文件**: `config/monitoring/grafana/provisioning/dashboards/dashboards.yml`

**配置内容**:
```yaml
apiVersion: 1
providers:
  - name: 'default'
    folder: ''
    type: file
    options:
      path: /var/lib/grafana/dashboards
```

**Dashboard文件**:
- `apex-dashboard.json` - 基础42个面板
- `apex-dashboard-enhanced.json` - 增强24个面板

---

## 六、验证步骤

### 6.1 启动应用

```bash
cd /Users/work/apex-scaffold
mvnd spring-boot:run -Dspring-boot.run.profiles=dev
```

### 6.2 验证端点可访问

```bash
# 健康检查
curl http://localhost:8081/actuator/health

# Prometheus指标
curl http://localhost:8081/actuator/prometheus | head -20

# 熔断器状态
curl http://localhost:8081/actuator/circuitbreakers
```

### 6.3 验证监控指标

```bash
# JVM类加载
curl -s http://localhost:8081/actuator/prometheus | grep jvm_classes

# Metaspace
curl -s http://localhost:8081/actuator/prometheus | grep 'jvm_memory.*Metaspace'

# CodeCache
curl -s http://localhost:8081/actuator/prometheus | grep 'jvm_memory.*CodeCache'

# JVM线程状态
curl -s http://localhost:8081/actuator/prometheus | grep jvm_threads_states

# Resilience4j
curl -s http://localhost:8081/actuator/prometheus | grep resilience4j_circuitbreaker

# Netty连接池
curl -s http://localhost:8081/actuator/prometheus | grep reactor_netty_connection
```

### 6.4 启动Grafana

```bash
cd config/monitoring
docker-compose up -d
```

**访问**: http://localhost:3000 (admin/admin)

---

## 七、结论与建议

### 7.1 现状总结

✅ **优点**:
1. 监控端点配置完整,所有必需指标已暴露
2. Management端口与业务端口物理隔离,安全性好
3. 支持多环境配置(basic/standard/enterprise/prod)
4. Prometheus + Grafana + Zipkin + Loki监控栈完整

⚠️ **待优化**:
1. 需新增24个深度监控面板(JVM深度、Sentinel治理、网关性能、熔断器监控)
2. 需配置告警规则(alert-rules.yml)
3. 需打通Grafana到Zipkin的跳转链接

### 7.2 实施建议

**优先级P0** (立即执行):
1. 基于`apex-dashboard-enhanced.json`新增24个面板
2. 配置告警规则

**优先级P1** (后续执行):
3. 集成Zipkin链路追踪
4. 编写监控平台使用指南

**优先级P2** (可选):
5. 智能告警(基于机器学习)
6. 日志聚合(ELK/Loki)
7. 自动化运维(基于监控指标的自动扩缩容)

---

**文档维护者**: Apex Team  
**创建日期**: 2026-09-07  
**下次更新**: 新增监控面板部署后
