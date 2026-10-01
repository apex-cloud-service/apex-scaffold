# 监控大盘前端更新指南

## 概述

本文档说明如何更新前端监控大盘以展示后端新增的监控指标。

## 后端已完成的增强

### 1. 网络监控指标增强

**文件**: `MonitorSystemResponse.NetworkUsage`

新增字段：
- `rxPackets` / `txPackets`: 接收/发送包数
- `rxErrors` / `txErrors`: 接收/发送错误数
- `rxDropped` / `txDropped`: 接收/发送丢包数
- `errorRate`: 网络错误率（0~1）
- `bandwidthUtilization`: 带宽利用率（0~1）
- `activeConnections`: 活跃连接数

### 2. 性能监控指标增强（TPS/QPS）

**文件**: `MonitorGovernanceResponse.GatewayStats`

新增字段：
- `currentQps`: 当前 QPS（请求/秒）
- `currentTps`: 当前 TPS（事务/秒，仅 2xx 响应）
- `peakQps`: 峰值 QPS
- `successRate`: 成功率（0~1）
- `p50ResponseTime`: P50 响应时间（秒）
- `p95ResponseTime`: P95 响应时间（秒）
- `p99ResponseTime`: P99 响应时间（秒）

### 3. 链路追踪指标增强

**文件**: `MonitorMiddlewareResponse.TracingStats`

新增字段：
- `reportedSpans`: 累计上报成功数
- `failedSpans`: 累计上报失败数
- `avgReportLatency`: 平均上报延迟（毫秒）
- `lastReportTime`: 最近一次上报时间戳（毫秒）

## 前端更新步骤

### 1. 更新网关流量展示

**文件**: `/Users/work/apex-admin-web/src/views/monitor/dashboard/index.vue`

**位置**: 第 192-213 行（网关 / API 流量卡片）

**修改前**:
```vue
<div class="gateway-summary">
  <div class="g-item"><span>累计请求</span><b>{{ governance?.gateway?.totalRequests ?? 0 }}</b></div>
  <div class="g-item"><span>4xx</span><b class="c-warn">{{ governance?.gateway?.error4xx ?? 0 }}</b></div>
  <div class="g-item"><span>5xx</span><b class="c-danger">{{ governance?.gateway?.error5xx ?? 0 }}</b></div>
  <div class="g-item"><span>平均耗时</span><b>{{ formatMs(governance?.gateway?.avgResponseTime ?? 0) }}</b></div>
</div>
```

**修改后**:
```vue
<div class="gateway-summary">
  <div class="g-item"><span>当前 QPS</span><b>{{ (governance?.gateway?.currentQps ?? 0).toFixed(2) }}</b></div>
  <div class="g-item"><span>当前 TPS</span><b>{{ (governance?.gateway?.currentTps ?? 0).toFixed(2) }}</b></div>
  <div class="g-item"><span>峰值 QPS</span><b>{{ (governance?.gateway?.peakQps ?? 0).toFixed(2) }}</b></div>
  <div class="g-item"><span>成功率</span><b>{{ ((governance?.gateway?.successRate ?? 1) * 100).toFixed(2) }}%</b></div>
  <div class="g-item"><span>P50</span><b>{{ formatMs(governance?.gateway?.p50ResponseTime ?? 0) }}</b></div>
  <div class="g-item"><span>P95</span><b class="c-warn">{{ formatMs(governance?.gateway?.p95ResponseTime ?? 0) }}</b></div>
  <div class="g-item"><span>P99</span><b class="c-danger">{{ formatMs(governance?.gateway?.p99ResponseTime ?? 0) }}</b></div>
  <div class="g-item"><span>累计请求</span><b>{{ governance?.gateway?.totalRequests ?? 0 }}</b></div>
</div>
```

### 2. 增强网络监控图表

**文件**: `/Users/work/apex-admin-web/src/views/monitor/dashboard/index.vue`

**位置**: 网络图表渲染函数

**建议**: 在网络收发图表下方添加网络错误率和带宽利用率的展示：

```vue
<!-- 网络错误率 -->
<el-col :xs="24" :lg="8">
  <el-card shadow="never" class="panel">
    <template #header>网络质量</template>
    <div ref="netQualityChartRef" class="chart" />
    <div class="panel-meta">
      <span v-for="net in system?.network" :key="net.name">
        {{ net.name }}: 错误率 {{ (net.errorRate * 100).toFixed(3) }}% · 
        丢包 {{ net.rxDropped + net.txDropped }}
      </span>
    </div>
  </el-card>
</el-col>
```

**图表渲染代码**:
```typescript
function renderNetQualityChart() {
  if (!netQualityChartRef.value || !system.value?.network) return
  const chart = initChart(netQualityChartRef.value)
  const networks = system.value.network.filter(n => n.available)
  
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['错误率', '带宽利用率'] },
    xAxis: { type: 'category', data: networks.map(n => n.name) },
    yAxis: { type: 'value', axisLabel: { formatter: '{value}%' } },
    series: [
      {
        name: '错误率',
        type: 'bar',
        data: networks.map(n => (n.errorRate * 100).toFixed(3))
      },
      {
        name: '带宽利用率',
        type: 'bar',
        data: networks.map(n => (n.bandwidthUtilization * 100).toFixed(2))
      }
    ]
  })
}
```

### 3. 增强链路追踪展示

**文件**: `/Users/work/apex-admin-web/src/views/monitor/dashboard/index.vue`

**位置**: Zipkin 中间件卡片（约第 150 行）

**修改**: 在 Zipkin 卡片中添加更多统计信息：

```vue
<div class="middleware-meta">
  <div>采样率: {{ ((middleware?.tracing?.samplingRate ?? 0) * 100).toFixed(1) }}%</div>
  <div>上报成功: {{ middleware?.tracing?.reportedSpans ?? 0 }}</div>
  <div>上报失败: {{ middleware?.tracing?.failedSpans ?? 0 }}</div>
  <div>平均延迟: {{ middleware?.tracing?.avgReportLatency?.toFixed(2) ?? 0 }} ms</div>
  <div v-if="middleware?.tracing?.lastReportTime">
    最近上报: {{ formatTime(middleware.tracing.lastReportTime) }}
  </div>
</div>
```

### 4. 更新类型定义

**文件**: `/Users/work/apex-admin-web/src/types/monitor.ts`

确保类型定义与后端 DTO 保持一致：

```typescript
export interface GatewayStats {
  totalRequests: number
  error4xx: number
  error5xx: number
  avgResponseTime: number
  maxResponseTime: number
  currentQps: number          // 新增
  currentTps: number          // 新增
  peakQps: number            // 新增
  successRate: number        // 新增
  p50ResponseTime: number    // 新增
  p95ResponseTime: number    // 新增
  p99ResponseTime: number    // 新增
  topPaths: PathTraffic[]
}

export interface NetworkUsage {
  name: string
  up: boolean
  speedMbps: number
  rxBytes: number
  txBytes: number
  rxBytesPerSec: number
  txBytesPerSec: number
  rxPackets: number          // 新增
  txPackets: number          // 新增
  rxErrors: number           // 新增
  txErrors: number           // 新增
  rxDropped: number          // 新增
  txDropped: number          // 新增
  errorRate: number          // 新增
  bandwidthUtilization: number // 新增
  activeConnections: number  // 新增
  available: boolean
}

export interface TracingStats {
  available: boolean
  type: string
  endpoint: string | null
  samplingRate: number
  spansPending: number
  spansDropped: number
  reportedSpans: number      // 新增
  failedSpans: number        // 新增
  avgReportLatency: number   // 新增
  lastReportTime: number     // 新增
}
```

## 样式调整建议

### 网关流量卡片样式

由于新增了更多指标，建议调整 `.gateway-summary` 的布局：

```css
.gateway-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.g-item {
  text-align: center;
  padding: 8px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.g-item span {
  display: block;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.g-item b {
  font-size: 18px;
  font-weight: 600;
}

.g-item b.c-warn {
  color: var(--el-color-warning);
}

.g-item b.c-danger {
  color: var(--el-color-danger);
}
```

## 验证清单

更新完成后，请验证以下内容：

- [ ] 网关流量卡片显示 QPS/TPS/成功率/P50/P95/P99
- [ ] 网络监控显示错误率和带宽利用率
- [ ] Zipkin 卡片显示上报统计和延迟信息
- [ ] 所有新增字段的类型定义正确
- [ ] 数据格式化正确（百分比、毫秒、秒等）
- [ ] 响应式布局在不同屏幕尺寸下正常显示
- [ ] 自动刷新功能正常工作
- [ ] 无 TypeScript 类型错误
- [ ] 无控制台错误

## 相关文件

- 后端 DTO: 
  - `/Users/work/apex-scaffold/apex-interfaces/src/main/java/com/apex/scaffold/interfaces/dto/monitor/MonitorSystemResponse.java`
  - `/Users/work/apex-scaffold/apex-interfaces/src/main/java/com/apex/scaffold/interfaces/dto/monitor/MonitorGovernanceResponse.java`
  - `/Users/work/apex-scaffold/apex-interfaces/src/main/java/com/apex/scaffold/interfaces/dto/monitor/MonitorMiddlewareResponse.java`
- 后端采集器: `/Users/work/apex-scaffold/apex-interfaces/src/main/java/com/apex/scaffold/interfaces/monitor/dashboard/MonitorMetricsCollector.java`
- 前端类型: `/Users/work/apex-admin-web/src/types/monitor.ts`
- 前端页面: `/Users/work/apex-admin-web/src/views/monitor/dashboard/index.vue`
- 前端 API: `/Users/work/apex-admin-web/src/api/monitor.ts`
