# Apex Scaffold 增强监控面板说明

## 📊 面板概述

本增强监控面板（`apex-dashboard-enhanced.json`）是对基础监控面板的补充，专注于 **WebFlux 网关**和 **JVM 深度监控**，提供更专业和详细的监控信息。

## 🎯 监控分组

### 1️⃣ WebFlux 网关核心指标

#### 1.1 EventLoop 线程监控
- **指标**：`reactor_netty_eventloop_threads`
- **用途**：监控 Netty EventLoop 线程数，正常应为 CPU 核心数的 2 倍
- **阈值**：
  - 🟢 正常：< 16 线程
  - 🟡 警告：16-32 线程
  - 🔴 异常：> 32 线程

#### 1.2 Reactor 待处理任务
- **指标**：`reactor_netty_eventloop_pending_tasks`
- **用途**：监控 EventLoop 队列积压情况
- **阈值**：
  - 🟢 正常：< 1000 任务
  - 🟡 警告：1000-5000 任务
  - 🔴 异常：> 5000 任务（说明处理不过来）

#### 1.3 WebFlux 活跃连接数
- **指标**：`reactor_netty_connection_provider_active_connections`
- **用途**：监控当前活跃的 HTTP 连接数
- **阈值**：
  - 🟢 正常：< 500 连接
  - 🟡 警告：500-1000 连接
  - 🔴 异常：> 1000 连接

#### 1.4 HTTP 服务器连接池详情
- **指标**：
  - `reactor_netty_connection_provider_pending_connections`（等待连接数）
  - `reactor_netty_connection_provider_idle_connections`（空闲连接数）
  - `reactor_netty_connection_provider_total_connections`（总连接数）
- **用途**：全面监控连接池状态，定位连接耗尽问题
- **分析**：
  - 如果等待连接数持续增长 → 连接池配置过小
  - 如果空闲连接数始终为 0 → 连接池满负载运行
  - 总连接数 = 活跃连接 + 空闲连接

#### 1.5 Netty ByteBuf 内存使用
- **指标**：
  - `reactor_netty_bytebuf_allocator_used_direct_memory`（Direct 内存）
  - `reactor_netty_bytebuf_allocator_used_heap_memory`（Heap 内存）
- **用途**：监控 Netty 内存分配器的内存使用
- **说明**：
  - Direct Memory：用于 I/O 操作的堆外内存
  - Heap Memory：用于临时数据的堆内内存
  - 如果 Direct Memory 持续增长 → 可能存在内存泄漏

---

### 2️⃣ JVM 深度监控

#### 2.1 JVM Heap 内存详情（按区域）
- **指标**：
  - `jvm_memory_used_bytes{area="heap"}`（已使用）
  - `jvm_memory_max_bytes{area="heap"}`（最大值）
- **用途**：按内存区域（Eden, Survivor, Old Gen）监控堆内存使用
- **分析**：
  - Eden 区快速增长 → 对象创建频繁
  - Old Gen 持续增长 → 可能存在内存泄漏
  - Used 接近 Max → 触发 Full GC

#### 2.2 JVM NonHeap 内存详情
- **指标**：
  - `jvm_memory_used_bytes{area="nonheap"}`（已使用）
  - `jvm_memory_committed_bytes{area="nonheap"}`（已提交）
- **用途**：监控 Metaspace、Code Cache 等非堆内存
- **分析**：
  - Metaspace 持续增长 → 类加载过多或泄漏
  - Code Cache 满 → JIT 编译受限

#### 2.3 GC 暂停时间分位数
- **指标**：`jvm_gc_pause_seconds{quantile="0.5|0.95|0.99"}`
- **用途**：监控 GC 暂停时间的分布
- **阈值**：
  - 🟢 P99 < 100ms：优秀
  - 🟡 P99 100-500ms：可接受
  - 🔴 P99 > 500ms：影响用户体验
- **说明**：
  - P50：50% 的 GC 暂停时间
  - P95：95% 的 GC 暂停时间（常用 SLA 指标）
  - P99：99% 的 GC 暂停时间（发现长尾问题）

#### 2.4 GC 频率（按类型）
- **指标**：`rate(jvm_gc_pause_seconds_count[5m]) * 60`
- **用途**：监控各类型 GC 的触发频率（次/分钟）
- **分析**：
  - Young GC 频繁 → Eden 区配置过小
  - Full GC 频繁 → 堆内存不足或存在泄漏
  - Mixed GC（G1）频繁 → Old Gen 增长过快

#### 2.5 JVM 线程状态
- **指标**：`jvm_threads_states_threads`
- **用途**：监控线程状态分布（RUNNABLE, BLOCKED, WAITING 等）
- **分析**：
  - BLOCKED 线程多 → 锁竞争严重
  - WAITING 线程多 → 线程池配置过大或任务阻塞
  - 总线程数持续增长 → 线程泄漏

#### 2.6 JVM 类加载
- **指标**：
  - `jvm_classes_loaded_classes`（已加载类数）
  - `rate(jvm_classes_unloaded_classes_total[5m])`（卸载速率）
- **用途**：监控类加载行为
- **分析**：
  - 已加载类数持续增长 → 动态类生成或类加载泄漏
  - 卸载速率低 → Metaspace 可能持续增长

#### 2.7 JVM 缓冲池
- **指标**：
  - `jvm_buffer_memory_used_bytes`（已使用）
  - `jvm_buffer_total_capacity_bytes`（总容量）
- **用途**：监控 Direct Buffer 和 Mapped Buffer
- **说明**：
  - Direct Buffer：NIO 直接内存缓冲区
  - Mapped Buffer：内存映射文件缓冲区
  - Used 接近 Capacity → 可能触发 System.gc()

---

## 🚀 使用方式

### 方式一：自动导入（推荐）

Grafana 已配置自动从 `/var/lib/grafana/dashboards` 目录加载面板，只需重启 Grafana：

```bash
# 在仓库根目录操作 Podman Compose 监控栈
podman-compose -f config/monitoring/podman-compose.yml restart grafana

# 等待 10 秒后访问
open http://localhost:3000/d/apex-enhanced
```

### 方式二：手动导入

1. 登录 Grafana（http://localhost:3000，默认 admin/admin）
2. 点击左侧 **+** → **Import**
3. 上传 `apex-dashboard-enhanced.json` 文件
4. 选择数据源 **Prometheus**
5. 点击 **Import**

---

## 📈 监控最佳实践

### 1. 性能基线建立

在正常负载下运行 1 小时，记录以下基线值：

- **WebFlux**：活跃连接数、EventLoop 待处理任务
- **JVM**：Heap 使用率、GC 频率、GC 暂停时间 P95

### 2. 告警阈值配置

建议在 `alert-rules.yml` 中添加以下告警规则：

```yaml
groups:
  - name: apex-enhanced
    rules:
      # WebFlux EventLoop 积压告警
      - alert: ReactorEventLoopBacklog
        expr: reactor_netty_eventloop_pending_tasks > 5000
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "EventLoop 任务积压严重"
          description: "{{ $labels.instance }} 待处理任务 {{ $value }}，可能影响响应时间"

      # JVM GC 暂停时间告警
      - alert: JvmGcPauseTooLong
        expr: jvm_gc_pause_seconds{quantile="0.99"} > 0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "GC 暂停时间过长"
          description: "{{ $labels.instance }} GC P99 暂停 {{ $value }}s，影响用户体验"

      # JVM Heap 使用率告警
      - alert: JvmHeapUsageHigh
        expr: sum(jvm_memory_used_bytes{area="heap"}) / sum(jvm_memory_max_bytes{area="heap"}) > 0.85
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "JVM 堆内存使用率过高"
          description: "{{ $labels.instance }} Heap 使用率 {{ $value | humanizePercentage }}，可能触发 Full GC"
```

### 3. 问题诊断指南

#### 场景一：响应时间突然升高

1. 查看 **WebFlux 活跃连接数** → 是否连接数暴增
2. 查看 **Reactor 待处理任务** → 是否 EventLoop 积压
3. 查看 **GC 暂停时间分位数** → 是否 GC 频繁导致

#### 场景二：内存持续增长

1. 查看 **JVM Heap 内存详情** → 哪个区域增长（Eden/Old）
2. 查看 **GC 频率** → Full GC 是否能回收内存
3. 查看 **Netty ByteBuf 内存** → 是否 Direct Memory 泄漏

#### 场景三：CPU 使用率高

1. 查看 **JVM 线程状态** → RUNNABLE 线程数是否异常多
2. 查看 **GC 频率** → 是否 GC 导致 CPU 高
3. 查看 **EventLoop 线程** → 是否 EventLoop 线程过多

---

## 🔗 相关文档

- [基础监控面板配置指南](../../../docs/_archive/operations/monitoring-dashboard-configuration.md)
- [Prometheus 配置说明](../prometheus/prometheus.yml)
- [告警规则配置](../prometheus/alert-rules.yml)
- [监控架构图](../../../docs/diagrams/24_prometheus_grafana_monitoring.svg)

---

## 📝 更新日志

### v1.0.0 (2026-09-07)

**新增监控面板：**
- ✅ WebFlux 网关核心指标（6 个面板）
  - EventLoop 线程监控
  - Reactor 待处理任务
  - WebFlux 活跃连接数
  - HTTP 服务器连接池详情
  - Netty ByteBuf 内存使用
- ✅ JVM 深度监控（7 个面板）
  - JVM Heap 内存详情（按区域）
  - JVM NonHeap 内存详情
  - GC 暂停时间分位数
  - GC 频率（按类型）
  - JVM 线程状态
  - JVM 类加载
  - JVM 缓冲池

**设计原则：**
- 所有面板基于实际可用的 Micrometer + Prometheus 指标
- 按专业监控标准设置阈值和颜色告警
- 提供详细的指标说明和问题诊断指南
- 支持与基础监控面板并行使用

---

**维护者**：Apex Team  
**最后更新**：2026-09-07
