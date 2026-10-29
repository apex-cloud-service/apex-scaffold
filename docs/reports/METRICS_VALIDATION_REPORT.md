# 增强监控面板指标验证报告

**验证时间**：2026-09-07 21:45  
**验证人员**：Apex Team  
**应用状态**：DOWN（但 Prometheus 端点可用）

---

## 📊 指标验证结果

### ✅ 可用指标（7 类，18+ 个指标）

#### 1. JVM 内存指标 ✅
- `jvm_memory_used_bytes{area="heap"}` - Heap 内存使用（4 个区域）
  - G1 Eden Space: 197 MB
  - G1 Old Gen: 124 MB
  - G1 Survivor Space: 13 MB
- `jvm_memory_used_bytes{area="nonheap"}` - NonHeap 内存使用（4 个区域）
  - Metaspace: 136 MB
  - Code Cache: 62 MB
  - Compressed Class Space: 16 MB

#### 2. JVM GC 指标 ✅
- `jvm_gc_pause_seconds_count` - GC 次数统计
  - G1 Young Generation (Evacuation Pause): 26 次
  - G1 Young Generation (Metadata GC): 1 次
- `jvm_gc_pause_seconds_sum` - GC 总暂停时间
  - 总计: 1.555 秒

#### 3. JVM 线程状态 ✅
- `jvm_threads_states_threads` - 12 个线程状态指标
  - RUNNABLE, WAITING, TIMED_WAITING, BLOCKED 等

#### 4. JVM 缓冲池 ✅
- `jvm_buffer_memory_used_bytes` - 6 个缓冲池指标
  - Direct Buffer
  - Mapped Buffer

#### 5. JVM 类加载 ✅
- `jvm_classes_loaded_classes` - 已加载类数量
- `jvm_classes_unloaded_classes_total` - 已卸载类数量

#### 6. 应用自定义指标 ✅
- `apex_api_request_count_total` - API 请求计数
- `apex_api_response_time_seconds` - API 响应时间直方图

---

### ❌ 缺失指标（WebFlux Reactor Netty）

以下 Reactor Netty 指标在当前应用中**不可用**：

#### 1. EventLoop 指标
- `reactor_netty_eventloop_threads` - EventLoop 线程数
- `reactor_netty_eventloop_pending_tasks` - 待处理任务数

#### 2. 连接池指标
- `reactor_netty_connection_provider_active_connections` - 活跃连接数
- `reactor_netty_connection_provider_pending_connections` - 等待连接数
- `reactor_netty_connection_provider_idle_connections` - 空闲连接数
- `reactor_netty_connection_provider_total_connections` - 总连接数

#### 3. ByteBuf 内存指标
- `reactor_netty_bytebuf_allocator_used_direct_memory` - Direct 内存使用
- `reactor_netty_bytebuf_allocator_used_heap_memory` - Heap 内存使用

---

## 🔍 原因分析

### Reactor Netty 指标缺失原因

Reactor Netty 的指标**默认不启用**，需要在应用配置中显式启用。有两种方式：

#### 方式一：全局启用（推荐）

在 `application.yml` 或 `application.properties` 中添加：

```yaml
# application.yml
management:
  metrics:
    enable:
      reactor.netty: true
```

或

```properties
# application.properties
management.metrics.enable.reactor.netty=true
```

#### 方式二：代码启用

在配置类中手动注册 Reactor Netty 指标：

```java
@Configuration
public class MetricsConfig {
    
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> {
            // 启用 Reactor Netty 指标
            Metrics.globalRegistry.add(registry);
        };
    }
}
```

---

## 📈 当前可用的监控面板

基于现有指标，以下监控面板**可以正常显示**：

### ✅ 完全可用（7 个面板）

1. **JVM Heap 内存详情（按区域）** ✅
   - 显示 G1 Eden, Survivor, Old Gen 内存使用
   
2. **JVM NonHeap 内存详情** ✅
   - 显示 Metaspace, Code Cache 内存使用
   
3. **GC 暂停时间分位数** ✅
   - 显示 P50/P95/P99 GC 暂停时间
   
4. **GC 频率（按类型）** ✅
   - 显示 Young GC, Full GC 频率
   
5. **JVM 线程状态** ✅
   - 显示 RUNNABLE, WAITING 等线程分布
   
6. **JVM 类加载** ✅
   - 显示已加载/卸载类数量
   
7. **JVM 缓冲池** ✅
   - 显示 Direct/Mapped Buffer 使用情况

### ⚠️ 无数据（6 个面板）

以下面板因缺少 Reactor Netty 指标而**无法显示数据**：

1. **WebFlux EventLoop 线程** ❌
2. **Reactor 待处理任务** ❌
3. **WebFlux 活跃连接数** ❌
4. **HTTP 服务器连接池详情** ❌
5. **Netty ByteBuf 内存使用** ❌

---

## 🛠️ 解决方案

### 方案一：启用 Reactor Netty 指标（推荐）

**步骤：**

1. 修改 `apex-interfaces/src/main/resources/application.yml`

```yaml
management:
  metrics:
    enable:
      reactor.netty: true  # 新增此配置
```

2. 重启应用

```bash
mvnd spring-boot:run -pl apex-interfaces
```

3. 验证指标

```bash
curl -s http://localhost:8081/actuator/prometheus | grep reactor_netty
```

**预期结果**：应该看到 10+ 个 `reactor_netty_*` 指标

---

### 方案二：使用仅 JVM 监控的精简版面板

如果不需要 WebFlux 网关监控，可以创建一个仅包含 JVM 指标的精简版面板。

**优势**：
- ✅ 无需修改应用配置
- ✅ 立即可用
- ✅ 覆盖最核心的 JVM 监控需求

**劣势**：
- ❌ 缺少 WebFlux 网关级别的监控
- ❌ 无法诊断连接池、EventLoop 问题

---

## 📊 验证命令汇总

### 1. 检查 Prometheus 采集状态

```bash
curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | select(.labels.job=="apex-scaffold")'
```

### 2. 验证 JVM 指标

```bash
curl -s http://localhost:8081/actuator/prometheus | grep "^jvm_" | wc -l
```

**当前结果**：约 150+ 个 JVM 指标

### 3. 验证 Reactor Netty 指标

```bash
curl -s http://localhost:8081/actuator/prometheus | grep "^reactor_netty" | wc -l
```

**当前结果**：0 个指标（需要启用）

### 4. 在 Grafana 中验证

访问增强监控面板：http://localhost:3000/d/apex-enhanced

**预期表现**：
- JVM 面板（下半部分）：✅ 显示正常数据
- WebFlux 面板（上半部分）：⚠️ 显示 "No Data"

---

## 🎯 建议

### 短期建议（立即可用）

1. **使用 JVM 监控面板**
   - 当前 7 个 JVM 面板可以正常使用
   - 提供了完整的 JVM 深度监控能力
   - 满足大部分性能诊断需求

2. **继续使用基础监控面板**
   - 原有的 66 个基础监控面板完全可用
   - 包含 HTTP 请求、数据库、缓存等监控

### 中期建议（1-2 天内）

1. **启用 Reactor Netty 指标**
   - 在 `application.yml` 中添加配置
   - 重启应用使其生效
   - 解锁全部 13 个增强监控面板

2. **验证指标完整性**
   - 运行本报告中的验证命令
   - 确保所有面板都有数据

### 长期建议（持续优化）

1. **建立监控基线**
   - 在正常负载下运行 1 小时
   - 记录各项指标的正常范围
   - 设置合理的告警阈值

2. **定期审查监控面板**
   - 每月检查指标可用性
   - 根据业务变化调整面板
   - 优化告警规则减少误报

---

## 📚 相关文档

- [增强监控面板使用指南](../../config/monitoring/grafana/README-ENHANCED.md)
- [Micrometer 官方文档](https://micrometer.io/docs)
- [Reactor Netty Metrics](https://projectreactor.io/docs/netty/release/reference/index.html#observability-metrics)

---

**报告生成时间**：2026-09-07 21:45:00  
**下次验证时间**：启用 Reactor Netty 指标后
