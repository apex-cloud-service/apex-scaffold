# 工作流监控与统计功能使用指南

> 微服务化说明：本节功能由独立服务 **`apex-workflow-service`** 提供（默认端口 `8093`，数据库 `apex_workflow`），
> 通过网关（默认端口 `8080`）以 `/admin-api/v1/workflows/**` 路由对外暴露；
> 独立构建命令：`mvnd -pl apex-workflow-service -am package`。

## 📋 概述

工作流监控与统计功能为 Apex Scaffold v1.4.0 新增的 Enterprise 特性,提供完整的流程实例监控、待办任务管理和统计分析能力。

**核心功能**:
- 📊 统计看板:今日发起、运行中流程、待办任务、平均完成时长
- 📈 趋势分析:流程发起/完成趋势图(7天/30天)
- 📋 实例列表:分页查询、状态筛选、详情查看
- ✅ 待办任务:个人待办任务列表、审批操作
- 🔍 实例详情:流程图可视化、任务列表、审批记录

---

## 🚀 快速开始

### 前置条件

1. **License 要求**: Enterprise 版本(包含 `workflow:*` 特性)
2. **权限要求**: 
   - `workflow:statistics:view` - 查看统计数据
   - `workflow:instance:view` - 查看流程实例
   - `workflow:task:view` - 查看待办任务
   - `workflow:task:complete` - 完成任务

### 访问入口

**后端 API 基础路径**: `/admin-api/v1/workflows/`

**前端页面路径**(待实现):
- 监控主页: `/workflow/monitor`
- 我的待办: `/workflow/tasks`
- 实例详情: `/workflow/instances/:id`

---

## 📊 统计看板功能

### 1. 统计概览

**接口**: `GET /admin-api/v1/workflows/statistics`

**响应示例**:
```json
{
  "code": 0,
  "data": {
    "todayStarted": 15,
    "runningTotal": 8,
    "pendingTaskTotal": 23,
    "avgCompletionSeconds": 7200,
    "statusDistribution": {
      "RUNNING": 8,
      "APPROVED": 45,
      "REJECTED": 3,
      "CANCELLED": 2
    }
  }
}
```

**字段说明**:
- `todayStarted`: 今日发起的流程数量
- `runningTotal`: 当前运行中的流程总数
- `pendingTaskTotal`: 待办任务总数
- `avgCompletionSeconds`: 平均完成时长(秒)
- `statusDistribution`: 流程状态分布

### 2. 趋势分析

**接口**: `GET /admin-api/v1/workflows/statistics/trend?days=7`

**查询参数**:
- `days`: 天数,默认 7 天(可选 7/14/30)

**响应示例**:
```json
{
  "code": 0,
  "data": {
    "dates": ["2026-08-27", "2026-08-28", "2026-08-29", ...],
    "startedCounts": [5, 8, 12, 10, 15, 18, 20],
    "completedCounts": [4, 7, 10, 9, 14, 16, 18]
  }
}
```

**使用场景**:
- 流程发起趋势分析
- 完成效率评估
- 周期性规律识别

---

## 📋 流程实例管理

### 1. 实例列表查询

**接口**: `GET /admin-api/v1/workflows/instances`

**查询参数**:
- `pageNum`: 页码(必填)
- `pageSize`: 每页大小(必填)
- `status`: 状态过滤(可选)
  - `DRAFT` - 草稿
  - `RUNNING` - 运行中
  - `APPROVED` - 已通过
  - `REJECTED` - 已拒绝
  - `CANCELLED` - 已取消
  - `TERMINATED` - 已终止
- `initiatorId`: 发起人 ID(可选)

**请求示例**:
```bash
curl -X GET "http://localhost:8080/admin-api/v1/workflows/instances?pageNum=1&pageSize=10&status=RUNNING" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

**响应示例**:
```json
{
  "code": 0,
  "data": {
    "records": [
      {
        "id": 1,
        "definitionId": 100,
        "processCode": "leave_approval",
        "title": "张三的请假申请",
        "status": "RUNNING",
        "initiatorId": 10,
        "initiatorName": "张三",
        "businessKey": "LEAVE-2026-001",
        "currentNodeId": "node_2",
        "startTime": "2026-09-02T10:00:00",
        "createTime": "2026-09-02T10:00:00"
      }
    ],
    "total": 1,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

### 2. 实例详情查询

**接口**: `GET /admin-api/v1/workflows/instances/{id}/detail`

**响应内容**:
- `instance`: 流程实例基本信息
- `definition`: 流程定义(用于渲染流程图)
- `tasks`: 任务列表
- `currentNodeId`: 当前所在节点

**使用场景**:
- 查看流程进度
- 追踪审批记录
- 问题排查

---

## ✅ 待办任务管理

### 1. 查询我的待办

**接口**: `GET /admin-api/v1/workflows/tasks/my`

**查询参数**:
- `pageNum`: 页码
- `pageSize`: 每页大小

**响应示例**:
```json
{
  "code": 0,
  "data": {
    "records": [
      {
        "id": 1,
        "instanceId": 1,
        "taskName": "部门主管审批",
        "taskType": "USER_TASK",
        "status": "PENDING",
        "assigneeId": 20,
        "assigneeName": "李四",
        "nodeId": "node_2",
        "startTime": "2026-09-02T11:00:00",
        "createTime": "2026-09-02T11:00:00"
      }
    ],
    "total": 5
  }
}
```

### 2. 审批任务

**审批通过接口**: `POST /admin-api/v1/workflows/tasks/approve`

**审批拒绝接口**: `POST /admin-api/v1/workflows/tasks/reject`

**请求体**:
```json
{
  "taskId": 1,
  "result": "APPROVED",
  "comment": "同意该请假申请"
}
```

**使用场景**:
- 处理待办任务
- 添加审批意见
- 流程流转

---

## 📈 前端集成示例

### Vue 3 组件示例

#### 1. 统计看板组件

```vue
<template>
  <div class="workflow-statistics">
    <!-- 指标卡片 -->
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card>
          <div class="stat-card">
            <div class="stat-label">今日发起</div>
            <div class="stat-value">{{ statistics.todayStarted }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-card">
            <div class="stat-label">运行中</div>
            <div class="stat-value">{{ statistics.runningTotal }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-card">
            <div class="stat-label">待办任务</div>
            <div class="stat-value">{{ statistics.pendingTaskTotal }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-card">
            <div class="stat-label">平均完成时长</div>
            <div class="stat-value">{{ formatDuration(statistics.avgCompletionSeconds) }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势图 -->
    <el-card style="margin-top: 20px">
      <div ref="chartRef" style="width: 100%; height: 400px"></div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
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

const chartRef = ref<HTMLDivElement>()

const loadData = async () => {
  statistics.value = await getWorkflowStatistics()
  trend.value = await getWorkflowTrend(7)
  renderChart()
}

const renderChart = () => {
  if (!chartRef.value) return
  
  const chart = echarts.init(chartRef.value)
  chart.setOption({
    title: { text: '流程趋势(最近7天)' },
    tooltip: { trigger: 'axis' },
    legend: { data: ['发起数量', '完成数量'] },
    xAxis: { type: 'category', data: trend.value.dates },
    yAxis: { type: 'value' },
    series: [
      {
        name: '发起数量',
        type: 'line',
        data: trend.value.startedCounts,
        smooth: true
      },
      {
        name: '完成数量',
        type: 'line',
        data: trend.value.completedCounts,
        smooth: true
      }
    ]
  })
}

const formatDuration = (seconds: number) => {
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  return `${hours}h ${minutes}m`
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.stat-card {
  text-align: center;
  padding: 20px 0;
}
.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 10px;
}
.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}
</style>
```

#### 2. 实例列表组件

```vue
<template>
  <div class="workflow-instances">
    <!-- 筛选器 -->
    <el-form :inline="true" :model="queryParams">
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="请选择" clearable>
          <el-option label="运行中" value="RUNNING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已拒绝" value="REJECTED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadList">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 表格 -->
    <el-table :data="list" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="title" label="流程标题" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">
            {{ getStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="initiatorName" label="发起人" width="120" />
      <el-table-column prop="startTime" label="开始时间" width="180" />
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button link type="primary" @click="viewDetail(row.id)">
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
      layout="total, sizes, prev, pager, next, jumper"
      @size-change="loadList"
      @current-change="loadList"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
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

const loadList = async () => {
  const res = await listWorkflowInstances(queryParams.value)
  list.value = res.records
  total.value = res.total
}

const resetQuery = () => {
  queryParams.value = {
    pageNum: 1,
    pageSize: 10,
    status: undefined
  }
  loadList()
}

const viewDetail = (id: number) => {
  router.push(`/workflow/instances/${id}`)
}

const getStatusType = (status: string) => {
  const map: Record<string, any> = {
    RUNNING: '',
    APPROVED: 'success',
    REJECTED: 'danger',
    CANCELLED: 'info'
  }
  return map[status] || ''
}

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    RUNNING: '运行中',
    APPROVED: '已通过',
    REJECTED: '已拒绝',
    CANCELLED: '已取消'
  }
  return map[status] || status
}

onMounted(() => {
  loadList()
})
</script>
```

---

## 🔧 配置说明

### 权限配置

在菜单管理中配置以下权限:

```sql
-- 工作流统计权限
INSERT INTO sys_menu (menu_name, parent_id, menu_type, path, perms, icon, sort, visible, status)
VALUES ('工作流统计', 0, 1, '/workflow/statistics', 'workflow:statistics:view', 'chart-line', 10, 1, 1);

-- 流程实例权限
INSERT INTO sys_menu (menu_name, parent_id, menu_type, path, perms, icon, sort, visible, status)
VALUES ('流程实例', 0, 1, '/workflow/instances', 'workflow:instance:view', 'list', 11, 1, 1);

-- 待办任务权限
INSERT INTO sys_menu (menu_name, parent_id, menu_type, path, perms, icon, sort, visible, status)
VALUES ('我的待办', 0, 1, '/workflow/tasks', 'workflow:task:view', 'check-square', 12, 1, 1);
```

### License 特性门控

工作流监控功能需要 Enterprise License,在 `application-enterprise.yml` 中已默认配置:

```yaml
apex:
  license:
    required-features:
      - workflow:*
```

---

## 📊 性能优化建议

### 1. 数据库索引

```sql
-- 流程实例查询索引
CREATE INDEX idx_process_instance_status ON wf_process_instance(status);
CREATE INDEX idx_process_instance_create_time ON wf_process_instance(create_time);
CREATE INDEX idx_process_instance_initiator ON wf_process_instance(initiator_id);

-- 任务查询索引
CREATE INDEX idx_task_assignee_status ON wf_task(assignee_id, status);
CREATE INDEX idx_task_instance ON wf_task(process_instance_id);
```

### 2. 缓存策略

- 统计数据缓存 60 秒(实时性要求不高)
- 趋势数据缓存 5 分钟
- 实例详情不缓存(实时性要求高)

### 3. 分页限制

- 默认每页 10 条
- 最大每页 100 条
- 超过 100 条建议使用导出功能

---

## 🐛 故障排查

### 问题 1: 统计数据为 0

**原因**: 数据库中无流程实例数据

**解决**:
1. 检查 `wf_process_instance` 表是否有数据
2. 确认时间范围是否正确
3. 查看应用日志中的 SQL 执行情况

### 问题 2: 权限不足

**症状**: 接口返回 403 Forbidden

**解决**:
1. 检查用户是否有对应权限
2. 确认 License 包含 `workflow:*` 特性
3. 查看 `sys_role_menu` 表权限配置

### 问题 3: 趋势图数据异常

**原因**: 日期范围计算错误

**解决**:
1. 检查服务器时区设置
2. 确认数据库时间字段类型
3. 验证前端日期格式化逻辑

---

## 📚 相关文档

- [工作流引擎使用指南](./workflow-guide.md)
- [工作流设计器使用指南](./workflow-designer-guide.md)
- [API 接口文档](http://localhost:8080/doc.html)

---

**文档版本**: v1.1
**最后更新**: 2026-10-09
**适用版本**: Apex Scaffold v1.4.0+（微服务化：`apex-workflow-service`）
