# 监控大盘前端完整实现方案

## 📋 概述

本文档提供监控大盘前端优化的完整实现方案，包括：
1. TypeScript 类型定义更新
2. 网关流量展示组件优化（QPS/TPS/P50/P95/P99）
3. 网络监控展示增强（错误率/带宽利用率）
4. 链路追踪展示优化（上报统计/延迟）
5. **新增功能**：根据 traceId 查询链路追踪结果

---

## 1️⃣ 更新 TypeScript 类型定义

### 文件：`/Users/work/apex-admin-web/src/types/monitor.ts`

```typescript
// 网关统计
export interface GatewayStats {
  totalRequests: number
  error4xx: number
  error5xx: number
  avgResponseTime: number
  maxResponseTime: number
  // 新增字段
  currentQps: number          // 当前 QPS（请求/秒）
  currentTps: number          // 当前 TPS（事务/秒）
  peakQps: number            // 峰值 QPS
  successRate: number        // 成功率 0~1
  p50ResponseTime: number    // P50 响应时间（秒）
  p95ResponseTime: number    // P95 响应时间（秒）
  p99ResponseTime: number    // P99 响应时间（秒）
  topPaths: PathTraffic[]
}

// 网络使用
export interface NetworkUsage {
  name: string
  up: boolean
  speedMbps: number
  rxBytes: number
  txBytes: number
  rxBytesPerSec: number
  txBytesPerSec: number
  // 新增字段
  rxPackets: number          // 接收包数
  txPackets: number          // 发送包数
  rxErrors: number           // 接收错误数
  txErrors: number           // 发送错误数
  rxDropped: number          // 接收丢包数
  txDropped: number          // 发送丢包数
  errorRate: number          // 网络错误率 0~1
  bandwidthUtilization: number // 带宽利用率 0~1
  activeConnections: number  // 活跃连接数
  available: boolean
}

// 链路追踪统计
export interface TracingStats {
  available: boolean
  type: string
  endpoint: string | null
  samplingRate: number
  spansPending: number
  spansDropped: number
  // 新增字段
  reportedSpans: number      // 累计上报成功数
  failedSpans: number        // 累计上报失败数
  avgReportLatency: number   // 平均上报延迟（毫秒）
  lastReportTime: number     // 最近一次上报时间戳（毫秒）
}

// 新增：链路追踪查询相关类型
export interface TraceSpan {
  traceId: string
  spanId: string
  parentSpanId?: string
  serviceName: string
  operationName: string
  startTime: number
  duration: number
  tags: Record<string, string>
  logs: TraceLog[]
}

export interface TraceLog {
  timestamp: number
  fields: Record<string, string>
}

export interface TraceQueryResult {
  traceId: string
  spans: TraceSpan[]
  duration: number
  serviceCoun: number
  spanCount: number
}
```

---

## 2️⃣ 更新监控大盘组件

### 文件：`/Users/work/apex-admin-web/src/views/monitor/dashboard/index.vue`

#### 2.1 更新网关流量展示（第 192-213 行）

**替换原有的 gateway-summary 部分：**

```vue
<!-- 网关流量 -->
<el-col :xs="24" :lg="12">
  <el-card shadow="never" class="panel">
    <template #header>
      <div class="panel-header">
        <span>网关 / API 流量</span>
        <el-tag :type="gatewayHealthType" size="small">
          {{ gatewayHealthText }}
        </el-tag>
      </div>
    </template>
    
    <!-- 性能指标卡片 -->
    <div class="gateway-metrics">
      <div class="metric-card">
        <div class="metric-label">当前 QPS</div>
        <div class="metric-value">{{ (governance?.gateway?.currentQps ?? 0).toFixed(2) }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">当前 TPS</div>
        <div class="metric-value">{{ (governance?.gateway?.currentTps ?? 0).toFixed(2) }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">峰值 QPS</div>
        <div class="metric-value highlight">{{ (governance?.gateway?.peakQps ?? 0).toFixed(2) }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">成功率</div>
        <div class="metric-value" :class="successRateClass">
          {{ ((governance?.gateway?.successRate ?? 1) * 100).toFixed(2) }}%
        </div>
      </div>
    </div>

    <!-- 响应时间分位数 -->
    <div class="response-time-section">
      <div class="section-title">响应时间分位数</div>
      <div class="response-metrics">
        <div class="response-item">
          <span class="response-label">P50</span>
          <span class="response-value">{{ formatMs(governance?.gateway?.p50ResponseTime ?? 0) }}</span>
        </div>
        <div class="response-item">
          <span class="response-label">P95</span>
          <span class="response-value warn">{{ formatMs(governance?.gateway?.p95ResponseTime ?? 0) }}</span>
        </div>
        <div class="response-item">
          <span class="response-label">P99</span>
          <span class="response-value danger">{{ formatMs(governance?.gateway?.p99ResponseTime ?? 0) }}</span>
        </div>
        <div class="response-item">
          <span class="response-label">累计请求</span>
          <span class="response-value">{{ governance?.gateway?.totalRequests ?? 0 }}</span>
        </div>
      </div>
    </div>

    <!-- TOP 路径表格 -->
    <el-table :data="topPaths" size="small" border max-height="240" class="top-paths-table">
      <el-table-column prop="path" label="路径" min-width="180" show-overflow-tooltip />
      <el-table-column prop="method" label="方法" width="80" />
      <el-table-column prop="count" label="请求数" width="90" align="right" />
      <el-table-column prop="errorRate" label="错误率" width="90" align="right">
        <template #default="{ row }">
          <span :class="{ 'error-rate-high': row.errorRate > 0.05 }">
            {{ (row.errorRate * 100).toFixed(2) }}%
          </span>
        </template>
      </el-table-column>
      <el-table-column label="平均耗时" width="100" align="right">
        <template #default="{ row }">{{ formatMs(row.avgTime) }}</template>
      </el-table-column>
    </el-table>
  </el-card>
</el-col>
```

#### 2.2 新增网络质量监控（插入到网络收发图表后面）

```vue
<!-- 网络质量 -->
<el-col :xs="24" :lg="8">
  <el-card shadow="never" class="panel">
    <template #header>网络质量监控</template>
    <div ref="netQualityChartRef" class="chart" />
    <div class="panel-meta network-quality-meta">
      <div v-for="net in activeNetworks" :key="net.name" class="network-item">
        <div class="network-name">{{ net.name }}</div>
        <div class="network-stats">
          <span>错误率: <b :class="getErrorRateClass(net.errorRate)">{{ (net.errorRate * 100).toFixed(3) }}%</b></span>
          <span>丢包: <b>{{ net.rxDropped + net.txDropped }}</b></span>
          <span>带宽: <b>{{ (net.bandwidthUtilization * 100).toFixed(1) }}%</b></span>
        </div>
      </div>
    </div>
  </el-card>
</el-col>
```

#### 2.3 优化 Zipkin 中间件卡片（约第 145-165 行）

**更新 Zipkin 卡片内容：**

```vue
<!-- Zipkin -->
<el-col :xs="24" :lg="6">
  <el-card shadow="never" class="panel">
    <template #header>
      <div class="middleware-header">
        <span>Zipkin</span>
        <el-tag v-if="!tracingAvailable" type="info" size="small">未接入</el-tag>
        <el-tag v-else type="success" size="small">运行中</el-tag>
      </div>
    </template>
    <div class="middleware-body">
      <div v-if="tracingAvailable" class="tracing-stats">
        <div class="stat-item">
          <span class="stat-label">采样率</span>
          <span class="stat-value">{{ ((middleware?.tracing?.samplingRate ?? 0) * 100).toFixed(1) }}%</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">上报成功</span>
          <span class="stat-value success">{{ formatNumber(middleware?.tracing?.reportedSpans ?? 0) }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">上报失败</span>
          <span class="stat-value" :class="{ danger: (middleware?.tracing?.failedSpans ?? 0) > 0 }">
            {{ formatNumber(middleware?.tracing?.failedSpans ?? 0) }}
          </span>
        </div>
        <div class="stat-item">
          <span class="stat-label">平均延迟</span>
          <span class="stat-value">{{ (middleware?.tracing?.avgReportLatency ?? 0).toFixed(2) }} ms</span>
        </div>
        <div v-if="middleware?.tracing?.lastReportTime" class="stat-item">
          <span class="stat-label">最近上报</span>
          <span class="stat-value">{{ formatTime(middleware.tracing.lastReportTime) }}</span>
        </div>
      </div>
      <div v-else class="middleware-placeholder">
        <el-empty description="链路追踪未接入" :image-size="80" />
      </div>
      
      <!-- 新增：TraceId 查询入口 -->
      <div v-if="tracingAvailable" class="trace-query-section">
        <el-divider />
        <el-button type="primary" size="small" @click="openTraceQuery" :icon="Search">
          TraceId 查询
        </el-button>
      </div>
    </div>
  </el-card>
</el-col>
```

#### 2.4 更新 Script 部分

**在 computed 部分添加新的计算属性：**

```typescript
// 网关健康状态
const gatewayHealthType = computed(() => {
  const successRate = governance.value?.gateway?.successRate ?? 1
  if (successRate >= 0.99) return 'success'
  if (successRate >= 0.95) return 'warning'
  return 'danger'
})

const gatewayHealthText = computed(() => {
  const successRate = governance.value?.gateway?.successRate ?? 1
  if (successRate >= 0.99) return '健康'
  if (successRate >= 0.95) return '警告'
  return '异常'
})

const successRateClass = computed(() => {
  const rate = governance.value?.gateway?.successRate ?? 1
  if (rate >= 0.99) return 'success'
  if (rate >= 0.95) return 'warn'
  return 'danger'
})

// 活跃网络设备
const activeNetworks = computed(() => {
  return system.value?.network?.filter(n => n.available && n.up) || []
})

// 链路追踪是否可用
const tracingAvailable = computed(() => middleware.value?.tracing?.available ?? false)

// 网络错误率样式类
function getErrorRateClass(errorRate: number): string {
  if (errorRate > 0.01) return 'danger'
  if (errorRate > 0.001) return 'warn'
  return 'success'
}

// 格式化数字（千分位）
function formatNumber(num: number): string {
  return num.toLocaleString('zh-CN')
}

// 格式化时间戳
function formatTime(timestamp: number): string {
  return new Date(timestamp).toLocaleTimeString('zh-CN')
}

// 打开 TraceId 查询对话框
function openTraceQuery() {
  traceQueryDialogVisible.value = true
}
```

**在图表渲染部分添加网络质量图表：**

```typescript
/** 渲染网络质量图表 */
function renderNetQualityChart() {
  if (!netQualityChartRef.value || !activeNetworks.value.length) return
  const chart = initChart(netQualityChartRef.value)
  
  const names = activeNetworks.value.map(n => n.name)
  const errorRates = activeNetworks.value.map(n => (n.errorRate * 100).toFixed(3))
  const bandwidthUtils = activeNetworks.value.map(n => (n.bandwidthUtilization * 100).toFixed(2))
  
  chart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    legend: {
      data: ['错误率 (%)', '带宽利用率 (%)'],
      textStyle: { color: axisColor() }
    },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: { color: axisColor() }
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        formatter: '{value}%',
        color: axisColor()
      }
    },
    series: [
      {
        name: '错误率 (%)',
        type: 'bar',
        data: errorRates,
        itemStyle: { color: '#f56c6c' }
      },
      {
        name: '带宽利用率 (%)',
        type: 'bar',
        data: bandwidthUtils,
        itemStyle: { color: '#409eff' }
      }
    ]
  })
}

// 在 watch 中添加图表更新逻辑
watch([system, governance], () => {
  nextTick(() => {
    renderMemChart()
    renderCpuChart()
    renderThreadChart()
    renderDiskChart()
    renderNetChart()
    renderNetQualityChart()  // 新增
    renderPoolChart()
    renderBizChart()
  })
})
```

---

## 3️⃣ 新增 TraceId 查询功能

### 3.1 创建链路追踪查询组件

**文件：`/Users/work/apex-admin-web/src/views/monitor/trace/TraceQuery.vue`**

```vue
<template>
  <div class="trace-query">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span>链路追踪查询</span>
          <el-button type="primary" size="small" @click="goToDashboard" :icon="Back">
            返回监控大盘
          </el-button>
        </div>
      </template>

      <!-- 查询表单 -->
      <el-form :model="queryForm" :inline="true" class="query-form">
        <el-form-item label="Trace ID">
          <el-input
            v-model="queryForm.traceId"
            placeholder="请输入 Trace ID"
            clearable
            style="width: 400px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery" :loading="loading" :icon="Search">
            查询
          </el-button>
          <el-button @click="handleReset" :icon="RefreshLeft">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 查询结果 -->
      <div v-if="traceResult" class="trace-result">
        <!-- 概览信息 -->
        <div class="trace-overview">
          <el-descriptions title="链路概览" :column="4" border>
            <el-descriptions-item label="Trace ID">
              <el-tag>{{ traceResult.traceId }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="总耗时">
              {{ formatDuration(traceResult.duration) }}
            </el-descriptions-item>
            <el-descriptions-item label="服务数">
              {{ traceResult.serviceCount }}
            </el-descriptions-item>
            <el-descriptions-item label="Span 数">
              {{ traceResult.spanCount }}
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- Span 时间线可视化 -->
        <div class="trace-timeline">
          <div class="timeline-header">
            <h3>Span 时间线</h3>
          </div>
          <div ref="timelineChartRef" class="timeline-chart"></div>
        </div>

        <!-- Span 详情表格 -->
        <div class="trace-spans">
          <h3>Span 详情</h3>
          <el-table :data="traceResult.spans" border stripe>
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="span-detail">
                  <el-descriptions title="Span 详情" :column="2" border>
                    <el-descriptions-item label="Span ID">{{ row.spanId }}</el-descriptions-item>
                    <el-descriptions-item label="Parent Span ID">
                      {{ row.parentSpanId || '-' }}
                    </el-descriptions-item>
                    <el-descriptions-item label="开始时间" :span="2">
                      {{ formatTimestamp(row.startTime) }}
                    </el-descriptions-item>
                  </el-descriptions>
                  
                  <el-divider content-position="left">Tags</el-divider>
                  <el-descriptions :column="2" border>
                    <el-descriptions-item
                      v-for="(value, key) in row.tags"
                      :key="key"
                      :label="key"
                    >
                      {{ value }}
                    </el-descriptions-item>
                  </el-descriptions>

                  <el-divider v-if="row.logs.length" content-position="left">Logs</el-divider>
                  <el-timeline v-if="row.logs.length">
                    <el-timeline-item
                      v-for="(log, index) in row.logs"
                      :key="index"
                      :timestamp="formatTimestamp(log.timestamp)"
                    >
                      <el-descriptions :column="1" border size="small">
                        <el-descriptions-item
                          v-for="(value, key) in log.fields"
                          :key="key"
                          :label="key"
                        >
                          {{ value }}
                        </el-descriptions-item>
                      </el-descriptions>
                    </el-timeline-item>
                  </el-timeline>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="serviceName" label="服务名" width="150" />
            <el-table-column prop="operationName" label="操作名" min-width="200" show-overflow-tooltip />
            <el-table-column label="耗时" width="120" align="right">
              <template #default="{ row }">
                {{ formatDuration(row.duration) }}
              </template>
            </el-table-column>
            <el-table-column label="开始时间" width="180">
              <template #default="{ row }">
                {{ formatTimestamp(row.startTime) }}
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <!-- 空状态 -->
      <el-empty v-else description="请输入 Trace ID 进行查询" :image-size="200" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Search, RefreshLeft, Back } from '@element-plus/icons-vue'
import { showError, showSuccess } from '@/utils/request'
import { getTraceById } from '@/api/monitor'
import type { TraceQueryResult } from '@/types'
import * as echarts from 'echarts'

const router = useRouter()
const loading = ref(false)
const queryForm = ref({ traceId: '' })
const traceResult = ref<TraceQueryResult | null>(null)
const timelineChartRef = ref<HTMLElement>()

/** 查询链路追踪 */
async function handleQuery() {
  if (!queryForm.value.traceId.trim()) {
    showError('请输入 Trace ID')
    return
  }

  loading.value = true
  try {
    const result = await getTraceById(queryForm.value.traceId)
    traceResult.value = result
    showSuccess('查询成功')
    
    nextTick(() => {
      renderTimelineChart()
    })
  } catch (error) {
    showError('查询失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}

/** 重置查询 */
function handleReset() {
  queryForm.value.traceId = ''
  traceResult.value = null
}

/** 返回监控大盘 */
function goToDashboard() {
  router.push('/monitor/dashboard')
}

/** 渲染时间线图表 */
function renderTimelineChart() {
  if (!timelineChartRef.value || !traceResult.value) return
  
  const chart = echarts.init(timelineChartRef.value)
  const spans = traceResult.value.spans
  
  // 按开始时间排序
  const sortedSpans = [...spans].sort((a, b) => a.startTime - b.startTime)
  const minTime = sortedSpans[0].startTime
  
  // 构建 Gantt 图数据
  const data = sortedSpans.map((span, index) => ({
    name: `${span.serviceName}: ${span.operationName}`,
    value: [
      index,
      span.startTime - minTime,
      span.startTime - minTime + span.duration,
      span.duration
    ],
    itemStyle: {
      color: getSpanColor(span.serviceName)
    }
  }))
  
  chart.setOption({
    tooltip: {
      formatter: (params: any) => {
        const span = sortedSpans[params.value[0]]
        return `
          <div style="padding: 8px;">
            <div><b>${span.serviceName}</b></div>
            <div>${span.operationName}</div>
            <div>耗时: ${formatDuration(params.value[3])}</div>
          </div>
        `
      }
    },
    grid: {
      left: '15%',
      right: '5%',
      top: '5%',
      bottom: '5%'
    },
    xAxis: {
      type: 'value',
      name: '时间 (ms)',
      axisLabel: {
        formatter: '{value} ms'
      }
    },
    yAxis: {
      type: 'category',
      data: sortedSpans.map((_, i) => i),
      axisLabel: {
        formatter: (value: number) => {
          const span = sortedSpans[value]
          return `${span.serviceName}\n${span.operationName}`.substring(0, 30)
        }
      }
    },
    series: [{
      type: 'custom',
      renderItem: (params: any, api: any) => {
        const categoryIndex = api.value(0)
        const start = api.coord([api.value(1), categoryIndex])
        const end = api.coord([api.value(2), categoryIndex])
        const height = api.size([0, 1])[1] * 0.6
        
        return {
          type: 'rect',
          shape: {
            x: start[0],
            y: start[1] - height / 2,
            width: end[0] - start[0],
            height: height
          },
          style: api.style()
        }
      },
      encode: {
        x: [1, 2],
        y: 0
      },
      data: data
    }]
  })
}

/** 根据服务名生成颜色 */
function getSpanColor(serviceName: string): string {
  const colors = [
    '#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de',
    '#3ba272', '#fc8452', '#9a60b4', '#ea7ccc'
  ]
  let hash = 0
  for (let i = 0; i < serviceName.length; i++) {
    hash = serviceName.charCodeAt(i) + ((hash << 5) - hash)
  }
  return colors[Math.abs(hash) % colors.length]
}

/** 格式化耗时 */
function formatDuration(ms: number): string {
  if (ms < 1) return `${(ms * 1000).toFixed(2)} μs`
  if (ms < 1000) return `${ms.toFixed(2)} ms`
  return `${(ms / 1000).toFixed(2)} s`
}

/** 格式化时间戳 */
function formatTimestamp(timestamp: number): string {
  return new Date(timestamp / 1000).toLocaleString('zh-CN')
}
</script>

<style scoped lang="scss">
.trace-query {
  padding: 20px;

  .header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .query-form {
    margin-bottom: 20px;
  }

  .trace-result {
    margin-top: 20px;

    .trace-overview {
      margin-bottom: 24px;
    }

    .trace-timeline {
      margin-bottom: 24px;

      .timeline-header {
        margin-bottom: 12px;
      }

      .timeline-chart {
        height: 400px;
      }
    }

    .trace-spans {
      h3 {
        margin-bottom: 12px;
      }
    }

    .span-detail {
      padding: 16px;
      background: var(--el-fill-color-light);
    }
  }
}
</style>
```

### 3.2 添加路由配置

**文件：`/Users/work/apex-admin-web/src/router/index.ts`**

在监控模块下添加链路追踪查询路由：

```typescript
{
  path: '/monitor',
  component: Layout,
  children: [
    {
      path: 'dashboard',
      name: 'MonitorDashboard',
      component: () => import('@/views/monitor/dashboard/index.vue'),
      meta: { title: '监控大盘', icon: 'Monitor' }
    },
    // 新增
    {
      path: 'trace',
      name: 'TraceQuery',
      component: () => import('@/views/monitor/trace/TraceQuery.vue'),
      meta: { title: '链路追踪查询', icon: 'Search' }
    }
  ]
}
```

### 3.3 添加 API 接口

**文件：`/Users/work/apex-admin-web/src/api/monitor.ts`**

添加链路追踪查询接口：

```typescript
import request from '@/utils/request'
import type { TraceQueryResult } from '@/types'

/**
 * 根据 Trace ID 查询链路追踪
 */
export function getTraceById(traceId: string): Promise<TraceQueryResult> {
  return request({
    url: `/admin-api/v1/monitor/trace/${traceId}`,
    method: 'get'
  })
}
```

---

## 4️⃣ 添加样式

### 文件：`/Users/work/apex-admin-web/src/views/monitor/dashboard/index.vue`

在 `<style>` 部分添加新样式：

```scss
/* 网关流量样式 */
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.gateway-metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 16px;
  margin-bottom: 20px;

  .metric-card {
    padding: 16px;
    background: var(--el-fill-color-light);
    border-radius: 8px;
    text-align: center;
    transition: all 0.3s;

    &:hover {
      background: var(--el-fill-color);
      transform: translateY(-2px);
    }

    .metric-label {
      font-size: 12px;
      color: var(--el-text-color-secondary);
      margin-bottom: 8px;
    }

    .metric-value {
      font-size: 24px;
      font-weight: 600;
      color: var(--el-text-color-primary);

      &.highlight {
        color: var(--el-color-primary);
      }

      &.success {
        color: var(--el-color-success);
      }

      &.warn {
        color: var(--el-color-warning);
      }

      &.danger {
        color: var(--el-color-danger);
      }
    }
  }
}

.response-time-section {
  margin-bottom: 20px;

  .section-title {
    font-size: 14px;
    font-weight: 500;
    margin-bottom: 12px;
    color: var(--el-text-color-regular);
  }

  .response-metrics {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(100px, 1fr));
    gap: 12px;
    padding: 12px;
    background: var(--el-fill-color-light);
    border-radius: 6px;

    .response-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4px;

      .response-label {
        font-size: 12px;
        color: var(--el-text-color-secondary);
      }

      .response-value {
        font-size: 16px;
        font-weight: 600;

        &.warn {
          color: var(--el-color-warning);
        }

        &.danger {
          color: var(--el-color-danger);
        }
      }
    }
  }
}

.top-paths-table {
  margin-top: 16px;

  .error-rate-high {
    color: var(--el-color-danger);
    font-weight: 600;
  }
}

/* 网络质量样式 */
.network-quality-meta {
  margin-top: 12px;

  .network-item {
    padding: 8px;
    margin-bottom: 8px;
    background: var(--el-fill-color-light);
    border-radius: 4px;

    .network-name {
      font-weight: 500;
      margin-bottom: 4px;
      color: var(--el-text-color-primary);
    }

    .network-stats {
      display: flex;
      gap: 16px;
      font-size: 12px;
      color: var(--el-text-color-secondary);

      b {
        font-weight: 600;
        margin-left: 4px;

        &.success {
          color: var(--el-color-success);
        }

        &.warn {
          color: var(--el-color-warning);
        }

        &.danger {
          color: var(--el-color-danger);
        }
      }
    }
  }
}

/* 链路追踪样式 */
.middleware-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tracing-stats {
  .stat-item {
    display: flex;
    justify-content: space-between;
    padding: 8px 0;
    border-bottom: 1px solid var(--el-border-color-lighter);

    &:last-child {
      border-bottom: none;
    }

    .stat-label {
      color: var(--el-text-color-secondary);
      font-size: 14px;
    }

    .stat-value {
      font-weight: 600;
      font-size: 14px;

      &.success {
        color: var(--el-color-success);
      }

      &.danger {
        color: var(--el-color-danger);
      }
    }
  }
}

.trace-query-section {
  margin-top: 12px;
  text-align: center;
}
```

---

## 5️⃣ 后端 API 接口实现（参考）

虽然这是前端文档，但为了完整性，这里提供后端需要实现的 TraceId 查询接口规范：

### 接口路径
```
GET /admin-api/v1/monitor/trace/{traceId}
```

### 请求参数
- `traceId`: 链路追踪 ID（路径参数）

### 响应格式
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "traceId": "abc123...",
    "spans": [
      {
        "traceId": "abc123...",
        "spanId": "span001",
        "parentSpanId": null,
        "serviceName": "apex-gateway",
        "operationName": "/api/users",
        "startTime": 1694678400000000,
        "duration": 150000,
        "tags": {
          "http.method": "GET",
          "http.status_code": "200"
        },
        "logs": [
          {
            "timestamp": 1694678400050000,
            "fields": {
              "event": "request_received"
            }
          }
        ]
      }
    ],
    "duration": 200000,
    "serviceCount": 3,
    "spanCount": 10
  },
  "traceId": "req-trace-xxx"
}
```

---

## 6️⃣ 验证清单

完成以上实现后，请按照以下清单进行验证：

### 6.1 类型定义验证
- [ ] TypeScript 编译无错误
- [ ] 所有新增字段类型正确

### 6.2 监控大盘验证
- [ ] 网关流量卡片显示 QPS/TPS/成功率/P50/P95/P99
- [ ] 性能指标数值正确，格式化合理
- [ ] 响应时间分位数颜色高亮正确（P95 橙色，P99 红色）
- [ ] TOP 路径表格正常展示
- [ ] 网络质量监控图表正常渲染
- [ ] 网络错误率、丢包数、带宽利用率显示正确
- [ ] Zipkin 卡片显示上报统计、延迟、最近上报时间
- [ ] "TraceId 查询"按钮可点击

### 6.3 TraceId 查询验证
- [ ] 点击"TraceId 查询"按钮跳转到查询页面
- [ ] 输入 TraceId 可正常查询
- [ ] 链路概览信息正确显示
- [ ] Span 时间线图表正常渲染
- [ ] Span 详情表格可展开查看详情
- [ ] Tags 和 Logs 正确显示
- [ ] 返回监控大盘按钮正常工作

### 6.4 响应式验证
- [ ] 不同屏幕尺寸下布局正常
- [ ] 移动端显示友好

### 6.5 性能验证
- [ ] 监控大盘自动刷新（10秒）无性能问题
- [ ] 图表渲染流畅
- [ ] 无内存泄漏

---

## 7️⃣ 常见问题

### Q1: 后端接口返回数据格式不匹配怎么办？
A: 请参考本文档第 5 节的接口规范，确保后端返回的数据结构与 TypeScript 类型定义一致。

### Q2: 图表渲染失败怎么办？
A: 检查 ECharts 是否正确初始化，确保 DOM 元素存在后再调用渲染函数。

### Q3: TraceId 查询结果为空？
A: 确认后端 Zipkin 集成正确，traceId 格式正确，后端接口正常返回数据。

### Q4: 样式不生效？
A: 确认 SCSS 预处理器正确配置，检查 CSS 类名是否拼写正确。

---

## 8️⃣ 总结

本文档提供了监控大盘前端优化的完整实现方案，包括：

✅ TypeScript 类型定义更新
✅ 网关流量展示组件优化（QPS/TPS/响应时间分位数）
✅ 网络监控展示增强（错误率/带宽利用率）
✅ 链路追踪展示优化（上报统计/延迟/最近上报时间）
✅ **新增** TraceId 查询功能（查询界面、时间线可视化、Span 详情）

所有代码均已提供完整实现，可直接复制到相应文件中。按照验证清单逐项测试，确保所有功能正常工作。

如有问题，请参考常见问题部分或联系后端团队确认接口实现。
