# 前端代码更新指南

## 概述

本文档提供前端项目 `apex-admin-web` 的完整更新代码，用于支持工作流引擎的高级功能（回退、转办、委派、挂起/恢复）。

**项目路径**：`/Users/work/apex-admin-web`

---

## 一、类型定义更新

### 文件：`src/types/workflow.ts`

**完整替换为以下内容**：

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
  status: 'DRAFT' | 'RUNNING' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'TERMINATED' | 'SUSPENDED'
  initiatorId: number
  initiatorName: string
  businessKey: string
  currentNodeId?: string
  startTime?: string
  endTime?: string
  createTime: string
  suspended?: boolean  // 新增字段
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
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'ROLLED_BACK' | 'DELEGATED' | 'CANCELLED'  // 新增状态
  assigneeId: number
  assigneeName: string
  comment?: string
  nodeId: string
  nodeCode?: string  // 新增字段
  startTime: string
  completedTime?: string
  createTime: string
  createdTime?: string  // 新增字段
  timeoutAt?: string  // 新增字段
  timeoutStrategy?: 'ESCALATE' | 'AUTO_COMPLETE' | 'NOTIFY'  // 新增字段
  transferredFrom?: number  // 新增字段
  transferredFromName?: string  // 新增字段
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
  suspendedCount?: number  // 新增字段
  timeoutCount?: number  // 新增字段
  transferredCount?: number  // 新增字段
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

// ========== 新增请求参数类型 ==========

export interface RollbackTaskPayload {
  targetNodeCode: string
  reason: string
}

export interface TransferTaskPayload {
  newAssigneeId: number
  reason: string
}

export interface DelegateTaskPayload {
  delegateToId: number
  reason: string
}

export interface SuspendInstancePayload {
  reason: string
}

export interface ResumeInstancePayload {
  reason: string
}
```

---

## 二、API 接口更新

### 文件：`src/api/workflow.ts`

**在文件末尾添加以下代码**：

```typescript
// ===== 高级任务操作 =====

/**
 * 流程回退
 */
export function rollbackTask(taskId: number, data: RollbackTaskPayload) {
  return post(`/api/workflow/tasks/${taskId}/rollback`, data)
}

/**
 * 任务转办
 */
export function transferTask(taskId: number, data: TransferTaskPayload) {
  return post(`/api/workflow/tasks/${taskId}/transfer`, data)
}

/**
 * 任务委派
 */
export function delegateTask(taskId: number, data: DelegateTaskPayload) {
  return post(`/api/workflow/tasks/${taskId}/delegate`, data)
}

/**
 * 挂起流程实例
 */
export function suspendInstance(instanceId: number, data: SuspendInstancePayload) {
  return post(`/api/workflow/instances/${instanceId}/suspend`, data)
}

/**
 * 恢复流程实例
 */
export function resumeInstance(instanceId: number, data: ResumeInstancePayload) {
  return post(`/api/workflow/instances/${instanceId}/resume`, data)
}
```

**并在文件顶部的 import 语句中添加新类型**：

```typescript
import type {
  WorkflowDefinition,
  WorkflowInstance,
  WorkflowInstanceDetail,
  WorkflowTask,
  WorkflowStatistics,
  WorkflowTrend,
  InstanceQueryParams,
  WorkflowDefinitionPayload,
  CompleteTaskPayload,
  RollbackTaskPayload,      // 新增
  TransferTaskPayload,       // 新增
  DelegateTaskPayload,       // 新增
  SuspendInstancePayload,    // 新增
  ResumeInstancePayload      // 新增
} from '@/types/workflow'
```

---

## 三、流程实例详情页面更新

### 文件：`src/views/workflow/monitor/InstanceDetail.vue`

**在操作按钮区域添加挂起/恢复按钮**：

```vue
<template>
  <div class="instance-detail">
    <!-- 原有内容 -->
    
    <!-- 在操作按钮区域添加 -->
    <div class="action-buttons">
      <!-- 原有按钮 -->
      
      <!-- 新增挂起/恢复按钮 -->
      <el-button
        v-if="instance.status === 'RUNNING' && !instance.suspended"
        type="warning"
        @click="handleSuspend"
      >
        挂起流程
      </el-button>
      
      <el-button
        v-if="instance.suspended"
        type="success"
        @click="handleResume"
      >
        恢复流程
      </el-button>
    </div>
    
    <!-- 流程状态标签 -->
    <el-tag :type="getStatusType(instance.status)" size="large">
      {{ getStatusText(instance.status) }}
      <span v-if="instance.suspended">(已挂起)</span>
    </el-tag>
    
    <!-- 挂起对话框 -->
    <el-dialog v-model="suspendDialogVisible" title="挂起流程" width="500px">
      <el-form :model="suspendForm" label-width="100px">
        <el-form-item label="挂起原因" required>
          <el-input
            v-model="suspendForm.reason"
            type="textarea"
            :rows="3"
            placeholder="请输入挂起原因"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="suspendDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmSuspend" :loading="loading">确定</el-button>
      </template>
    </el-dialog>
    
    <!-- 恢复对话框 -->
    <el-dialog v-model="resumeDialogVisible" title="恢复流程" width="500px">
      <el-form :model="resumeForm" label-width="100px">
        <el-form-item label="恢复原因" required>
          <el-input
            v-model="resumeForm.reason"
            type="textarea"
            :rows="3"
            placeholder="请输入恢复原因"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resumeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmResume" :loading="loading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { suspendInstance, resumeInstance } from '@/api/workflow'
import type { WorkflowInstance } from '@/types/workflow'

// ... 原有代码 ...

const suspendDialogVisible = ref(false)
const resumeDialogVisible = ref(false)
const loading = ref(false)

const suspendForm = ref({
  reason: ''
})

const resumeForm = ref({
  reason: ''
})

// 挂起流程
const handleSuspend = () => {
  suspendForm.value.reason = ''
  suspendDialogVisible.value = true
}

const confirmSuspend = async () => {
  if (!suspendForm.value.reason) {
    ElMessage.warning('请输入挂起原因')
    return
  }
  
  loading.value = true
  try {
    await suspendInstance(instance.value.id, suspendForm.value)
    ElMessage.success('流程已挂起')
    suspendDialogVisible.value = false
    // 刷新数据
    loadInstanceDetail()
  } catch (error: any) {
    ElMessage.error(error.message || '挂起失败')
  } finally {
    loading.value = false
  }
}

// 恢复流程
const handleResume = () => {
  resumeForm.value.reason = ''
  resumeDialogVisible.value = true
}

const confirmResume = async () {
  if (!resumeForm.value.reason) {
    ElMessage.warning('请输入恢复原因')
    return
  }
  
  loading.value = true
  try {
    await resumeInstance(instance.value.id, resumeForm.value)
    ElMessage.success('流程已恢复')
    resumeDialogVisible.value = false
    // 刷新数据
    loadInstanceDetail()
  } catch (error: any) {
    ElMessage.error(error.message || '恢复失败')
  } finally {
    loading.value = false
  }
}

// 状态文本映射
const getStatusText = (status: string) => {
  const statusMap: Record<string, string> = {
    DRAFT: '草稿',
    RUNNING: '运行中',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    CANCELLED: '已取消',
    TERMINATED: '已终止',
    SUSPENDED: '已挂起'
  }
  return statusMap[status] || status
}

// 状态颜色映射
const getStatusType = (status: string) => {
  const typeMap: Record<string, any> = {
    DRAFT: 'info',
    RUNNING: 'primary',
    APPROVED: 'success',
    REJECTED: 'danger',
    CANCELLED: 'info',
    TERMINATED: 'warning',
    SUSPENDED: 'warning'
  }
  return typeMap[status] || 'info'
}
</script>
```

---

## 四、创建任务操作组件

### 新建文件：`src/views/workflow/components/TaskActions.vue`

```vue
<template>
  <div class="task-actions">
    <el-button type="primary" @click="$emit('approve')">通过</el-button>
    <el-button type="danger" @click="$emit('reject')">驳回</el-button>
    
    <el-dropdown @command="handleMoreAction" trigger="click">
      <el-button>
        更多操作<el-icon class="el-icon--right"><arrow-down /></el-icon>
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="rollback">回退</el-dropdown-item>
          <el-dropdown-item command="transfer">转办</el-dropdown-item>
          <el-dropdown-item command="delegate">委派</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
    
    <!-- 回退对话框 -->
    <el-dialog v-model="rollbackVisible" title="流程回退" width="500px">
      <el-form :model="rollbackForm" label-width="100px">
        <el-form-item label="回退节点" required>
          <el-select v-model="rollbackForm.targetNodeCode" placeholder="请选择回退节点">
            <el-option
              v-for="node in previousNodes"
              :key="node.nodeCode"
              :label="node.nodeName"
              :value="node.nodeCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="回退原因" required>
          <el-input
            v-model="rollbackForm.reason"
            type="textarea"
            :rows="3"
            placeholder="请输入回退原因"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rollbackVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmRollback" :loading="loading">确定</el-button>
      </template>
    </el-dialog>
    
    <!-- 转办对话框 -->
    <el-dialog v-model="transferVisible" title="任务转办" width="500px">
      <el-form :model="transferForm" label-width="100px">
        <el-form-item label="转办给" required>
          <el-select
            v-model="transferForm.newAssigneeId"
            filterable
            remote
            :remote-method="searchUsers"
            placeholder="请选择用户"
            :loading="userSearchLoading"
          >
            <el-option
              v-for="user in userList"
              :key="user.id"
              :label="user.name"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="转办原因" required>
          <el-input
            v-model="transferForm.reason"
            type="textarea"
            :rows="3"
            placeholder="请输入转办原因"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmTransfer" :loading="loading">确定</el-button>
      </template>
    </el-dialog>
    
    <!-- 委派对话框 -->
    <el-dialog v-model="delegateVisible" title="任务委派" width="500px">
      <el-form :model="delegateForm" label-width="100px">
        <el-form-item label="委派给" required>
          <el-select
            v-model="delegateForm.delegateToId"
            filterable
            remote
            :remote-method="searchUsers"
            placeholder="请选择用户"
            :loading="userSearchLoading"
          >
            <el-option
              v-for="user in userList"
              :key="user.id"
              :label="user.name"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="委派说明" required>
          <el-input
            v-model="delegateForm.reason"
            type="textarea"
            :rows="3"
            placeholder="请输入委派说明"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="delegateVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmDelegate" :loading="loading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { rollbackTask, transferTask, delegateTask } from '@/api/workflow'
import { listUsers } from '@/api/system'

interface Props {
  taskId: number
  previousNodes?: Array<{ nodeCode: string; nodeName: string }>
}

const props = defineProps<Props>()
const emit = defineEmits(['approve', 'reject', 'success'])

const loading = ref(false)
const userSearchLoading = ref(false)
const rollbackVisible = ref(false)
const transferVisible = ref(false)
const delegateVisible = ref(false)

const rollbackForm = ref({
  targetNodeCode: '',
  reason: ''
})

const transferForm = ref({
  newAssigneeId: undefined as number | undefined,
  reason: ''
})

const delegateForm = ref({
  delegateToId: undefined as number | undefined,
  reason: ''
})

const userList = ref<Array<{ id: number; name: string }>>([])
const previousNodes = ref(props.previousNodes || [])

// 处理更多操作
const handleMoreAction = (command: string) => {
  switch (command) {
    case 'rollback':
      handleRollback()
      break
    case 'transfer':
      handleTransfer()
      break
    case 'delegate':
      handleDelegate()
      break
  }
}

// 回退
const handleRollback = () => {
  rollbackForm.value = { targetNodeCode: '', reason: '' }
  rollbackVisible.value = true
}

const confirmRollback = async () => {
  if (!rollbackForm.value.targetNodeCode) {
    ElMessage.warning('请选择回退节点')
    return
  }
  if (!rollbackForm.value.reason) {
    ElMessage.warning('请输入回退原因')
    return
  }
  
  loading.value = true
  try {
    await rollbackTask(props.taskId, rollbackForm.value)
    ElMessage.success('回退成功')
    rollbackVisible.value = false
    emit('success')
  } catch (error: any) {
    ElMessage.error(error.message || '回退失败')
  } finally {
    loading.value = false
  }
}

// 转办
const handleTransfer = () => {
  transferForm.value = { newAssigneeId: undefined, reason: '' }
  userList.value = []
  transferVisible.value = true
}

const confirmTransfer = async () => {
  if (!transferForm.value.newAssigneeId) {
    ElMessage.warning('请选择转办人')
    return
  }
  if (!transferForm.value.reason) {
    ElMessage.warning('请输入转办原因')
    return
  }
  
  loading.value = true
  try {
    await transferTask(props.taskId, {
      newAssigneeId: transferForm.value.newAssigneeId,
      reason: transferForm.value.reason
    })
    ElMessage.success('转办成功')
    transferVisible.value = false
    emit('success')
  } catch (error: any) {
    ElMessage.error(error.message || '转办失败')
  } finally {
    loading.value = false
  }
}

// 委派
const handleDelegate = () => {
  delegateForm.value = { delegateToId: undefined, reason: '' }
  userList.value = []
  delegateVisible.value = true
}

const confirmDelegate = async () => {
  if (!delegateForm.value.delegateToId) {
    ElMessage.warning('请选择委派人')
    return
  }
  if (!delegateForm.value.reason) {
    ElMessage.warning('请输入委派说明')
    return
  }
  
  loading.value = true
  try {
    await delegateTask(props.taskId, {
      delegateToId: delegateForm.value.delegateToId,
      reason: delegateForm.value.reason
    })
    ElMessage.success('委派成功')
    delegateVisible.value = false
    emit('success')
  } catch (error: any) {
    ElMessage.error(error.message || '委派失败')
  } finally {
    loading.value = false
  }
}

// 搜索用户
const searchUsers = async (query: string) => {
  if (!query) {
    userList.value = []
    return
  }
  
  userSearchLoading.value = true
  try {
    const { data } = await listUsers({ username: query, pageNum: 1, pageSize: 20 })
    userList.value = data.records.map((user: any) => ({
      id: user.id,
      name: user.username
    }))
  } catch (error) {
    console.error('搜索用户失败', error)
  } finally {
    userSearchLoading.value = false
  }
}
</script>

<style scoped>
.task-actions {
  display: flex;
  gap: 10px;
}
</style>
```

---

## 五、使用说明

### 5.1 应用更新

1. **进入前端项目目录**：
   ```bash
   cd /Users/work/apex-admin-web
   ```

2. **更新类型定义**：
   - 打开 `src/types/workflow.ts`
   - 完整替换为本文档「第一部分」的代码

3. **更新 API 接口**：
   - 打开 `src/api/workflow.ts`
   - 在 import 语句中添加新类型
   - 在文件末尾添加「第二部分」的 API 方法

4. **更新流程实例详情页面**：
   - 打开 `src/views/workflow/monitor/InstanceDetail.vue`
   - 按照「第三部分」添加挂起/恢复功能

5. **创建任务操作组件**：
   - 创建新文件 `src/views/workflow/components/TaskActions.vue`
   - 复制「第四部分」的完整代码

6. **在待办任务页面中使用组件**：
   ```vue
   <template>
     <task-actions
       :task-id="currentTask.id"
       :previous-nodes="previousNodes"
       @approve="handleApprove"
       @reject="handleReject"
       @success="refreshTaskList"
     />
   </template>
   
   <script setup>
   import TaskActions from './components/TaskActions.vue'
   </script>
   ```

### 5.2 验证

1. **启动前端项目**：
   ```bash
   npm run dev
   ```

2. **测试功能**：
   - 打开待办任务列表
   - 验证「更多操作」下拉菜单显示
   - 测试回退、转办、委派功能
   - 打开流程实例详情
   - 验证挂起/恢复按钮显示
   - 测试挂起/恢复功能

---

## 六、注意事项

1. **API 地址**：
   - 新接口使用 `/api/workflow` 前缀（WebFlux Router 方式）
   - 与原有接口的 `/admin-api/v1/workflows` 前缀不同

2. **用户搜索接口**：
   - 组件中使用了 `listUsers` 接口，确保该接口存在于 `@/api/system`

3. **权限控制**：
   - 建议在组件中添加权限判断，根据用户角色显示/隐藏操作按钮

4. **错误处理**：
   - 所有 API 调用都有统一的错误处理
   - 确保后端返回标准的错误格式

---

**文档版本**：1.0  
**创建时间**：2026-09-04  
**作者**：Kiro (AI Assistant)
