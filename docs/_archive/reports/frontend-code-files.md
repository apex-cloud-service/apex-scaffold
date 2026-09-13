# 前端完整代码文件

本文档包含所有需要在 `/Users/work/apex-admin-web` 项目中创建的文件完整代码。

---

## 📋 文件清单

1. `src/types/workflow.ts` - 工作流类型定义
2. `src/api/workflow.ts` - 工作流 API 接口
3. `src/views/workflow/monitor/Statistics.vue` - 统计看板组件
4. `src/views/workflow/monitor/InstanceList.vue` - 实例列表组件
5. `src/views/workflow/monitor/InstanceDetail.vue` - 实例详情组件
6. `src/views/workflow/monitor/index.vue` - 监控主页面
7. 依赖安装命令

---

## 文件 1: src/types/workflow.ts

```typescript
/**
 * 工作流相关类型定义
 */

// ========== 工作流定义 ==========

export interface WorkflowNode {
  nodeId: string
  nodeType: 'START' | 'USER_TASK' | 'GATEWAY' | 'END'
  nodeName: string
  approverType?: 'USER' | 'ROLE' | 'DEPT'
  approverValue?: string
  approvalStrategy?: 'ANY' | 'ALL'
  timeoutHours?: number
  timeoutAction?: string
  position?: { x: number; y: number }
}

export interface WorkflowEdge {
  edgeId: string
  sourceNodeId: string
  targetNodeId: string
  condition?: string
}

export interface WorkflowDefinition {
  id?: number
  code: string
  name: string
  description?: string
  version: number
  enabled: boolean
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
  createTime?: string
  updateTime?: string
}

// ========== 工作流实例 ==========

export interface WorkflowInstance {
  id: number
  definitionId: number
  processCode: string
  title: string
  status: 'DRAFT' | 'RUNNING' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'TERMINATED'
  initiatorId: number
  initiatorName: string
  businessKey: string
  currentNodeId?: string
  startTime?: string
  endTime?: string
  createTime: string
}

export interface WorkflowInstanceDetail {
  instance: WorkflowInstance
  definition: WorkflowDefinition
  tasks: WorkflowTask[]
  currentNodeId?: string
}

// ========== 工作流任务 ==========

export interface WorkflowTask {
  id: number
  instanceId: number
  taskName: string
  taskType: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  assigneeId: number
  assigneeName: string
  comment?: string
  nodeId: string
  startTime: string
  completedTime?: string
  createTime: string
}

// ========== 审批记录 ==========

export interface ApprovalRecord {
  id: number
  taskId: number
  approverId: number
  approverName: string
  result: 'APPROVED' | 'REJECTED'
  comment?: string
  createTime: string
}

// ========== 统计数据 ==========

export interface WorkflowStatistics {
  todayStarted: number
  runningTotal: number
  pendingTaskTotal: number
  avgCompletionSeconds: number
  statusDistribution: Record<string, number>
}

export interface WorkflowTrend {
  dates: string[]
  startedCounts: number[]
  completedCounts: number[]
}

// ========== 请求参数 ==========

export interface InstanceQueryParams {
  pageNum: number
  pageSize: number
  status?: string
  initiatorId?: number
}

export interface WorkflowDefinitionPayload {
  code: string
  name: string
  description?: string
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
}

export interface CompleteTaskPayload {
  taskId: number
  result: 'APPROVED' | 'REJECTED'
  comment?: string
}
```

---

## 文件 2: src/api/workflow.ts

```typescript
import request from '@/utils/request'
import type {
  WorkflowDefinition,
  WorkflowInstance,
  WorkflowInstanceDetail,
  WorkflowTask,
  WorkflowStatistics,
  WorkflowTrend,
  InstanceQueryParams,
  WorkflowDefinitionPayload,
  CompleteTaskPayload
} from '@/types/workflow'

// ===== 流程定义管理 =====

/**
 * 获取流程定义详情
 */
export function getWorkflowDefinition(id: number) {
  return request.get<WorkflowDefinition>(`/admin-api/v1/workflows/definitions/${id}/detail`)
}

/**
 * 创建流程定义
 */
export function createWorkflowDefinition(data: WorkflowDefinitionPayload) {
  return request.post('/admin-api/v1/workflows/definitions', data)
}

/**
 * 更新流程定义
 */
export function updateWorkflowDefinition(id: number, data: WorkflowDefinitionPayload) {
  return request.put(`/admin-api/v1/workflows/definitions/${id}`, data)
}

/**
 * 获取流程定义列表
 */
export function listWorkflowDefinitions(params?: { pageNum?: number; pageSize?: number }) {
  return request.get<{ records: WorkflowDefinition[]; total: number }>('/admin-api/v1/workflows/definitions', { params })
}

// ===== 流程实例管理 =====

/**
 * 分页查询流程实例
 */
export function listWorkflowInstances(params: InstanceQueryParams) {
  return request.get<{ records: WorkflowInstance[]; total: number }>('/admin-api/v1/workflows/instances', { params })
}

/**
 * 获取流程实例详情
 */
export function getInstanceDetail(id: number) {
  return request.get<WorkflowInstanceDetail>(`/admin-api/v1/workflows/instances/${id}/detail`)
}

/**
 * 启动流程实例
 */
export function startWorkflowInstance(data: { definitionId: number; businessKey: string; variables?: Record<string, any> }) {
  return request.post('/admin-api/v1/workflows/instances/start', data)
}

// ===== 统计分析 =====

/**
 * 获取工作流统计概览
 */
export function getWorkflowStatistics() {
  return request.get<WorkflowStatistics>('/admin-api/v1/workflows/statistics')
}

/**
 * 获取工作流趋势数据
 */
export function getWorkflowTrend(days: number = 7) {
  return request.get<WorkflowTrend>('/admin-api/v1/workflows/statistics/trend', {
    params: { days }
  })
}

// ===== 待办任务 =====

/**
 * 获取我的待办任务
 */
export function getMyPendingTasks(params: { pageNum: number; pageSize: number }) {
  return request.get<{ records: WorkflowTask[]; total: number }>('/admin-api/v1/workflows/tasks/my', { params })
}

/**
 * 完成任务（审批通过）
 */
export function approveTask(data: CompleteTaskPayload) {
  return request.post('/admin-api/v1/workflows/tasks/approve', data)
}

/**
 * 拒绝任务
 */
export function rejectTask(data: CompleteTaskPayload) {
  return request.post('/admin-api/v1/workflows/tasks/reject', data)
}

/**
 * 获取任务详情
 */
export function getTaskDetail(taskId: number) {
  return request.get<WorkflowTask>(`/admin-api/v1/workflows/tasks/${taskId}`)
}
```

---

## 文件 3: src/views/workflow/monitor/Statistics.vue

```vue
<template>
  <div class="workflow-statistics">
    <!-- 指标卡片 -->
    <el-row :gutter="20" style="margin-bottom: 20px">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-icon" style="background: #ecf5ff; color: #409eff">
              <el-icon :size="32"><TrendCharts /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-label">今日发起</div>
              <div class="stat-value">{{ statistics.todayStarted }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-icon" style="background: #f0f9ff; color: #67c23a">
              <el-icon :size="32"><Loading /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-label">运行中</div>
              <div class="stat-value">{{ statistics.runningTotal }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-icon" style="background: #fef0f0; color: #f56c6c">
              <el-icon :size="32"><Bell /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-label">待办任务</div>
              <div class="stat-value">{{ statistics.pendingTaskTotal }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-icon" style="background: #fdf6ec; color: #e6a23c">
              <el-icon :size="32"><Timer /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-label">平均完成时长</div>
              <div class="stat-value">{{ formatDuration(statistics.avgCompletionSeconds) }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="20">
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>流程状态分布</span>
          </template>
          <div ref="pieChartRef" style="width: 100%; height: 300px"></div>
        </el-card>
      </el-col>
      
      <el-col :span="12">
        <el-card>
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span>流程趋势</span>
              <el-radio-group v-model="trendDays" size="small" @change="loadTrend">
                <el-radio-button :value="7">近7天</el-radio-button>
                <el-radio-button :value="30">近30天</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="lineChartRef" style="width: 100%; height: 300px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { TrendCharts, Loading, Bell, Timer } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getWorkflowStatistics, getWorkflowTrend } from '@/api/workflow'
import type { WorkflowStatistics, WorkflowTrend } from '@/types/workflow'

const statistics = ref<WorkflowStatistics>({
  todayStarted: 0,
  runningTotal: 0,
  pendingTaskTotal: 0,
  avgCompletionSeconds: 0,
  statusDistribution: {}
})

const trend = ref<WorkflowTrend>({
  dates: [],
  startedCounts: [],
  completedCounts: []
})

const trendDays = ref(7)
const pieChartRef = ref<HTMLDivElement>()
const lineChartRef = ref<HTMLDivElement>()
let pieChart: echarts.ECharts | null = null
let lineChart: echarts.ECharts | null = null

const loadStatistics = async () => {
  try {
    statistics.value = await getWorkflowStatistics()
    renderPieChart()
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}

const loadTrend = async () => {
  try {
    trend.value = await getWorkflowTrend(trendDays.value)
    renderLineChart()
  } catch (error) {
    console.error('加载趋势数据失败:', error)
  }
}

const renderPieChart = () => {
  if (!pieChartRef.value) return
  
  if (!pieChart) {
    pieChart = echarts.init(pieChartRef.value)
  }
  
  const statusMap: Record<string, string> = {
    RUNNING: '运行中',
    APPROVED: '已通过',
    REJECTED: '已拒绝',
    CANCELLED: '已取消'
  }
  
  const data = Object.entries(statistics.value.statusDistribution).map(([key, value]) => ({
    name: statusMap[key] || key,
    value
  }))
  
  pieChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b}: {c} ({d}%)'
    },
    legend: {
      bottom: '0%',
      left: 'center'
    },
    series: [
      {
        name: '流程状态',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: false,
          position: 'center'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 20,
            fontWeight: 'bold'
          }
        },
        labelLine: {
          show: false
        },
        data
      }
    ]
  })
}

const renderLineChart = () => {
  if (!lineChartRef.value) return
  
  if (!lineChart) {
    lineChart = echarts.init(lineChartRef.value)
  }
  
  lineChart.setOption({
    tooltip: {
      trigger: 'axis'
    },
    legend: {
      data: ['发起数量', '完成数量']
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trend.value.dates
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '发起数量',
        type: 'line',
        smooth: true,
        data: trend.value.startedCounts,
        itemStyle: { color: '#409eff' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
            { offset: 1, color: 'rgba(64, 158, 255, 0.1)' }
          ])
        }
      },
      {
        name: '完成数量',
        type: 'line',
        smooth: true,
        data: trend.value.completedCounts,
        itemStyle: { color: '#67c23a' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(103, 194, 58, 0.3)' },
            { offset: 1, color: 'rgba(103, 194, 58, 0.1)' }
          ])
        }
      }
    ]
  })
}

const formatDuration = (seconds: number): string => {
  if (seconds === 0) return '0h 0m'
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  return `${hours}h ${minutes}m`
}

onMounted(() => {
  loadStatistics()
  loadTrend()
  
  window.addEventListener('resize', () => {
    pieChart?.resize()
    lineChart?.resize()
  })
})

onUnmounted(() => {
  pieChart?.dispose()
  lineChart?.dispose()
})
</script>

<style scoped>
.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 0;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-content {
  flex: 1;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
  line-height: 1;
}
</style>
```

---

## 文件 4: src/views/workflow/monitor/InstanceList.vue

```vue
<template>
  <div class="workflow-instance-list">
    <!-- 查询表单 -->
    <el-form :inline="true" :model="queryParams" style="margin-bottom: 20px">
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="请选择" clearable style="width: 150px">
          <el-option label="运行中" value="RUNNING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已拒绝" value="REJECTED" />
          <el-option label="已取消" value="CANCELLED" />
        </el-select>
      </el-form-item>
      
      <el-form-item>
        <el-button type="primary" @click="handleQuery">
          <el-icon><Search /></el-icon>
          查询
        </el-button>
        <el-button @click="handleReset">
          <el-icon><Refresh /></el-icon>
          重置
        </el-button>
      </el-form-item>
    </el-form>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="title" label="流程标题" min-width="200" show-overflow-tooltip />
      <el-table-column prop="processCode" label="流程编码" width="150" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">
            {{ getStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="initiatorName" label="发起人" width="120" />
      <el-table-column prop="startTime" label="开始时间" width="180" />
      <el-table-column prop="endTime" label="结束时间" width="180">
        <template #default="{ row }">
          {{ row.endTime || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleViewDetail(row.id)">
            查看详情
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <el-pagination
      v-model:current-page="queryParams.pageNum"
      v-model:page-size="queryParams.pageSize"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      style="margin-top: 20px; justify-content: flex-end"
      @size-change="loadList"
      @current-change="loadList"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Refresh } from '@element-plus/icons-vue'
import { listWorkflowInstances } from '@/api/workflow'
import type { WorkflowInstance, InstanceQueryParams } from '@/types/workflow'

const router = useRouter()

const queryParams = ref<InstanceQueryParams>({
  pageNum: 1,
  pageSize: 10,
  status: undefined
})

const list = ref<WorkflowInstance[]>([])
const total = ref(0)
const loading = ref(false)

const loadList = async () => {
  loading.value = true
  try {
    const res = await listWorkflowInstances(queryParams.value)
    list.value = res.records
    total.value = res.total
  } catch (error) {
    console.error('加载流程实例列表失败:', error)
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.value.pageNum = 1
  loadList()
}

const handleReset = () => {
  queryParams.value = {
    pageNum: 1,
    pageSize: 10,
    status: undefined
  }
  loadList()
}

const handleViewDetail = (id: number) => {
  router.push(`/workflow/instances/${id}`)
}

const getStatusType = (status: string) => {
  const map: Record<string, any> = {
    DRAFT: 'info',
    RUNNING: '',
    APPROVED: 'success',
    REJECTED: 'danger',
    CANCELLED: 'info',
    TERMINATED: 'warning'
  }
  return map[status] || ''
}

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    RUNNING: '运行中',
    APPROVED: '已通过',
    REJECTED: '已拒绝',
    CANCELLED: '已取消',
    TERMINATED: '已终止'
  }
  return map[status] || status
}

onMounted(() => {
  loadList()
})
</script>
```

---

## 文件 5: src/views/workflow/monitor/InstanceDetail.vue

```vue
<template>
  <div v-loading="loading" class="workflow-instance-detail">
    <el-page-header @back="handleBack">
      <template #content>
        <span style="font-size: 18px; font-weight: bold">流程实例详情</span>
      </template>
    </el-page-header>

    <div v-if="detail" style="margin-top: 20px">
      <!-- 基本信息 -->
      <el-card style="margin-bottom: 20px">
        <template #header>
          <span>基本信息</span>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="流程ID">{{ detail.instance.id }}</el-descriptions-item>
          <el-descriptions-item label="流程标题">{{ detail.instance.title }}</el-descriptions-item>
          <el-descriptions-item label="流程编码">{{ detail.instance.processCode }}</el-descriptions-item>
          <el-descriptions-item label="业务键">{{ detail.instance.businessKey }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(detail.instance.status)">
              {{ getStatusLabel(detail.instance.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="发起人">{{ detail.instance.initiatorName }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ detail.instance.startTime }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ detail.instance.endTime || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 任务列表 -->
      <el-card>
        <template #header>
          <span>任务列表</span>
        </template>
        <el-table :data="detail.tasks" border>
          <el-table-column prop="id" label="任务ID" width="80" />
          <el-table-column prop="taskName" label="任务名称" min-width="150" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getTaskStatusType(row.status)">
                {{ getTaskStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="assigneeName" label="办理人" width="120" />
          <el-table-column prop="comment" label="审批意见" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.comment || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="startTime" label="开始时间" width="180" />
          <el-table-column prop="completedTime" label="完成时间" width="180">
            <template #default="{ row }">
              {{ row.completedTime || '-' }}
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getInstanceDetail } from '@/api/workflow'
import type { WorkflowInstanceDetail } from '@/types/workflow'

const route = useRoute()
const router = useRouter()

const detail = ref<WorkflowInstanceDetail | null>(null)
const loading = ref(false)

const loadDetail = async () => {
  const id = Number(route.params.id)
  if (!id) return
  
  loading.value = true
  try {
    detail.value = await getInstanceDetail(id)
  } catch (error) {
    console.error('加载流程实例详情失败:', error)
  } finally {
    loading.value = false
  }
}

const handleBack = () => {
  router.back()
}

const getStatusType = (status: string) => {
  const map: Record<string, any> = {
    DRAFT: 'info',
    RUNNING: '',
    APPROVED: 'success',
    REJECTED: 'danger',
    CANCELLED: 'info',
    TERMINATED: 'warning'
  }
  return map[status] || ''
}

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    RUNNING: '运行中',
    APPROVED: '已通过',
    REJECTED: '已拒绝',
    CANCELLED: '已取消',
    TERMINATED: '已终止'
  }
  return map[status] || status
}

const getTaskStatusType = (status: string) => {
  const map: Record<string, any> = {
    PENDING: '',
    APPROVED: 'success',
    REJECTED: 'danger'
  }
  return map[status] || ''
}

const getTaskStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    PENDING: '待办',
    APPROVED: '已通过',
    REJECTED: '已拒绝'
  }
  return map[status] || status
}

onMounted(() => {
  loadDetail()
})
</script>

<style scoped>
.workflow-instance-detail {
  padding: 20px;
}
</style>
```

---

## 文件 6: src/views/workflow/monitor/index.vue

```vue
<template>
  <div class="workflow-monitor">
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="统计看板" name="statistics">
        <Statistics />
      </el-tab-pane>
      
      <el-tab-pane label="流程实例" name="instances">
        <InstanceList />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import Statistics from './Statistics.vue'
import InstanceList from './InstanceList.vue'

const activeTab = ref('statistics')
</script>

<style scoped>
.workflow-monitor {
  padding: 20px;
}
</style>
```

---

## 📦 依赖安装

由于权限限制，请手动在前端项目中执行以下命令：

```bash
cd /Users/work/apex-admin-web
npm install @vue-flow/core @vue-flow/controls @vue-flow/minimap --save
```

**注意**: 如果遇到权限问题，可以使用 `sudo` 或者调整文件夹权限后再执行。

---

## 📂 创建目录结构

请手动创建以下目录结构：

```bash
mkdir -p /Users/work/apex-admin-web/src/views/workflow/monitor
```

---

## ✅ 验证步骤

### 1. 创建文件

按照上述内容依次创建所有文件。

### 2. 启动后端服务

```bash
cd /Users/work/apex-scaffold
mvnd spring-boot:run -pl apex-interfaces
```

### 3. 启动前端服务

```bash
cd /Users/work/apex-admin-web
npm run dev
```

### 4. 访问页面

在浏览器中访问：`http://localhost:5173/workflow/monitor`（具体端口以 Vite 启动输出为准）

---

## 🎯 功能清单

- ✅ 工作流类型定义（`workflow.ts` types）
- ✅ 工作流 API 接口封装（`workflow.ts` api）
- ✅ 统计看板组件（显示今日发起、运行中、待办任务、平均时长）
- ✅ 状态分布饼图（ECharts）
- ✅ 趋势折线图（近7天/30天）
- ✅ 流程实例列表（分页、筛选、查看详情）
- ✅ 流程实例详情（基本信息、任务列表）
- ✅ 监控主页面（Tab 切换）

---

## 📝 后续工作

如需实现工作流可视化设计器，还需要：

1. 安装 Vue Flow: `npm install @vue-flow/core @vue-flow/controls @vue-flow/minimap`
2. 创建设计器组件（`src/views/workflow/designer/`）
3. 实现自定义节点组件（StartNode、ApprovalNode、ConditionNode、EndNode）
4. 实现流程验证和数据转换工具

详细设计器实现代码可参考 `/Users/work/apex-scaffold/docs/WORKFLOW_DESIGNER_GUIDE.md` 文档。

---

**文档生成时间**: 2026-09-02 21:07  
**版本**: v1.0
