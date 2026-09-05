# 工作流引擎前端调整指南

## 概述

本文档分析工作流引擎后端功能增强后，前端需要进行的相应调整，包括新增 API 接口、页面功能和 UI 组件。

---

## 一、后端新增功能汇总

### 1.1 WorkflowService 新增方法

| 方法 | 功能 | HTTP 接口（需前端调用） |
|------|------|------------------------|
| `rollbackToNode()` | 流程回退到指定节点 | `POST /api/workflow/tasks/{taskId}/rollback` |
| `transferTask()` | 转办任务给其他人 | `POST /api/workflow/tasks/{taskId}/transfer` |
| `delegateTask()` | 委派任务给其他人 | `POST /api/workflow/tasks/{taskId}/delegate` |
| `suspendInstance()` | 挂起流程实例 | `POST /api/workflow/instances/{instanceId}/suspend` |
| `resumeInstance()` | 恢复流程实例 | `POST /api/workflow/instances/{instanceId}/resume` |

### 1.2 数据模型变更

#### ProcessInstance 新增字段
- `suspended` (Boolean) - 挂起标志

#### Task 新增字段
- `nodeCode` (String) - 节点编码
- `createdTime` (DateTime) - 任务创建时间
- `timeoutAt` (DateTime) - 超时时间
- `timeoutStrategy` (String) - 超时策略（ESCALATE/AUTO_COMPLETE/NOTIFY）
- `transferredFrom` (Long) - 转办来源用户 ID

---

## 二、前端需要调整的内容

### ✅ 必须调整（核心功能）

#### 2.1 API 接口层调整

**文件**：`src/api/workflow.js` 或 `src/services/workflow.service.ts`

**新增接口方法**：

```typescript
// 流程回退
export function rollbackTask(taskId: number, data: {
  targetNodeCode: string;
  reason: string;
}): Promise<void> {
  return request.post(`/api/workflow/tasks/${taskId}/rollback`, data);
}

// 任务转办
export function transferTask(taskId: number, data: {
  newAssigneeId: number;
  reason: string;
}): Promise<void> {
  return request.post(`/api/workflow/tasks/${taskId}/transfer`, data);
}

// 任务委派
export function delegateTask(taskId: number, data: {
  delegateToId: number;
  reason: string;
}): Promise<void> {
  return request.post(`/api/workflow/tasks/${taskId}/delegate`, data);
}

// 挂起流程实例
export function suspendInstance(instanceId: number, data: {
  reason: string;
}): Promise<void> {
  return request.post(`/api/workflow/instances/${instanceId}/suspend`, data);
}

// 恢复流程实例
export function resumeInstance(instanceId: number, data: {
  reason: string;
}): Promise<void> {
  return request.post(`/api/workflow/instances/${instanceId}/resume`, data);
}
```

#### 2.2 TypeScript 类型定义更新

**文件**：`src/types/workflow.ts`

```typescript
// ProcessInstance 接口更新
export interface ProcessInstance {
  id: number;
  workflowDefinitionId: number;
  businessKey: string;
  status: 'RUNNING' | 'SUSPENDED' | 'COMPLETED' | 'REJECTED' | 'TERMINATED';
  initiatorId: number;
  contextJson: string;
  startTime: string;
  endTime?: string;
  createTime: string;
  updateTime: string;
  suspended: boolean;  // 新增字段
}

// Task 接口更新
export interface Task {
  id: number;
  processInstanceId: number;
  nodeKey: string;
  nodeCode: string;        // 新增字段
  nodeName: string;
  assigneeId: number;
  status: 'PENDING' | 'COMPLETED' | 'REJECTED' | 'ROLLED_BACK' | 'DELEGATED' | 'CANCELLED';
  dueDate?: string;
  completedTime?: string;
  createTime: string;
  createdTime: string;     // 新增字段
  updateTime: string;
  timeoutAt?: string;      // 新增字段
  timeoutStrategy?: 'ESCALATE' | 'AUTO_COMPLETE' | 'NOTIFY';  // 新增字段
  transferredFrom?: number; // 新增字段
}
```

#### 2.3 待办任务页面调整

**文件**：`src/views/workflow/TaskList.vue` 或 `TaskList.tsx`

**需要新增的 UI 组件**：

1. **任务操作按钮组**
   ```vue
   <template>
     <div class="task-actions">
       <!-- 原有按钮：审批通过、驳回 -->
       <el-button type="primary" @click="handleApprove">通过</el-button>
       <el-button type="danger" @click="handleReject">驳回</el-button>
       
       <!-- 新增按钮 -->
       <el-dropdown @command="handleMoreAction">
         <el-button>
           更多操作<el-icon><arrow-down /></el-icon>
         </el-button>
         <template #dropdown>
           <el-dropdown-menu>
             <el-dropdown-item command="rollback">回退</el-dropdown-item>
             <el-dropdown-item command="transfer">转办</el-dropdown-item>
             <el-dropdown-item command="delegate">委派</el-dropdown-item>
           </el-dropdown-menu>
         </template>
       </el-dropdown>
     </div>
   </template>
   ```

2. **流程回退对话框**
   ```vue
   <el-dialog v-model="rollbackDialogVisible" title="流程回退" width="500px">
     <el-form :model="rollbackForm" label-width="100px">
       <el-form-item label="回退节点" required>
         <el-select v-model="rollbackForm.targetNodeCode" placeholder="请选择">
           <el-option
             v-for="node in previousNodes"
             :key="node.code"
             :label="node.name"
             :value="node.code"
           />
         </el-select>
       </el-form-item>
       <el-form-item label="回退原因" required>
         <el-input
           v-model="rollbackForm.reason"
           type="textarea"
           :rows="3"
           placeholder="请输入回退原因"
         />
       </el-form-item>
     </el-form>
     <template #footer>
       <el-button @click="rollbackDialogVisible = false">取消</el-button>
       <el-button type="primary" @click="confirmRollback">确定</el-button>
     </template>
   </el-dialog>
   ```

3. **任务转办对话框**
   ```vue
   <el-dialog v-model="transferDialogVisible" title="任务转办" width="500px">
     <el-form :model="transferForm" label-width="100px">
       <el-form-item label="转办给" required>
         <el-select
           v-model="transferForm.newAssigneeId"
           filterable
           remote
           :remote-method="searchUsers"
           placeholder="请选择用户"
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
         />
       </el-form-item>
     </el-form>
     <template #footer>
       <el-button @click="transferDialogVisible = false">取消</el-button>
       <el-button type="primary" @click="confirmTransfer">确定</el-button>
     </template>
   </el-dialog>
   ```

4. **任务委派对话框**（类似转办对话框，字段相同）

5. **任务列表字段显示**
   ```vue
   <el-table :data="taskList">
     <!-- 原有列 -->
     <el-table-column prop="nodeName" label="节点名称" />
     <el-table-column prop="status" label="状态" />
     
     <!-- 新增列 -->
     <el-table-column label="超时时间">
       <template #default="{ row }">
         <span v-if="row.timeoutAt">
           {{ formatDateTime(row.timeoutAt) }}
           <el-tag v-if="isTimeout(row.timeoutAt)" type="danger" size="small">
             已超时
           </el-tag>
         </span>
         <span v-else>-</span>
       </template>
     </el-table-column>
     
     <el-table-column label="转办信息">
       <template #default="{ row }">
         <span v-if="row.transferredFrom">
           <el-tag type="info" size="small">转办</el-tag>
           来自 {{ getUserName(row.transferredFrom) }}
         </span>
         <span v-else>-</span>
       </template>
     </el-table-column>
   </el-table>
   ```

#### 2.4 流程实例详情页面调整

**文件**：`src/views/workflow/InstanceDetail.vue`

**需要新增的 UI 组件**：

1. **流程实例操作按钮**
   ```vue
   <div class="instance-actions">
     <!-- 原有按钮：查看流程图、审批记录等 -->
     
     <!-- 新增按钮 -->
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
   ```

2. **流程状态标签更新**
   ```vue
   <el-tag
     :type="getStatusType(instance.status)"
     size="large"
   >
     {{ getStatusText(instance.status) }}
     <span v-if="instance.suspended">(已挂起)</span>
   </el-tag>
   ```

3. **挂起/恢复确认对话框**
   ```vue
   <el-dialog v-model="suspendDialogVisible" title="挂起流程" width="500px">
     <el-form :model="suspendForm" label-width="100px">
       <el-form-item label="挂起原因" required>
         <el-input
           v-model="suspendForm.reason"
           type="textarea"
           :rows="3"
           placeholder="请输入挂起原因"
         />
       </el-form-item>
     </el-form>
     <template #footer>
       <el-button @click="suspendDialogVisible = false">取消</el-button>
       <el-button type="primary" @click="confirmSuspend">确定</el-button>
     </template>
   </el-dialog>
   ```

#### 2.5 流程图展示组件调整

**文件**：`src/components/workflow/ProcessDiagram.vue`

**调整内容**：

1. **节点状态显示增强**
   - 显示回退记录（节点上标注回退次数）
   - 显示转办/委派标识
   - 显示超时任务高亮

2. **流程轨迹展示**
   ```vue
   <div class="process-trace">
     <el-timeline>
       <el-timeline-item
         v-for="record in traceRecords"
         :key="record.id"
         :timestamp="record.createTime"
       >
         <div class="trace-content">
           <span class="node-name">{{ record.nodeName }}</span>
           <span class="operator">{{ record.operatorName }}</span>
           <el-tag :type="getActionType(record.action)" size="small">
             {{ getActionText(record.action) }}
           </el-tag>
           <p v-if="record.comment" class="comment">{{ record.comment }}</p>
         </div>
       </el-timeline-item>
     </el-timeline>
   </div>
   ```

   其中 `action` 枚举值新增：
   - `ROLLED_BACK` - 已回退
   - `TRANSFERRED` - 已转办
   - `DELEGATED` - 已委派
   - `SUSPENDED` - 已挂起
   - `RESUMED` - 已恢复

---

### 🔹 可选调整（增强功能）

#### 2.6 超时任务提醒

**文件**：新建 `src/components/workflow/TimeoutAlert.vue`

**功能**：
- 显示即将超时的任务列表（距离超时 < 2 小时）
- 显示已超时的任务列表
- 支持一键转办或自动完成

```vue
<template>
  <el-alert
    v-if="timeoutTasks.length > 0"
    type="warning"
    title="任务超时提醒"
    :closable="false"
  >
    <div class="timeout-tasks">
      <p>您有 {{ timeoutTasks.length }} 个任务即将超时：</p>
      <ul>
        <li v-for="task in timeoutTasks" :key="task.id">
          <span>{{ task.nodeName }}</span>
          <el-tag type="danger" size="small">
            {{ getTimeoutStatus(task.timeoutAt) }}
          </el-tag>
          <el-button type="text" size="small" @click="gotoTask(task.id)">
            立即处理
          </el-button>
        </li>
      </ul>
    </div>
  </el-alert>
</template>
```

#### 2.7 工作流统计看板

**文件**：新建 `src/views/workflow/Dashboard.vue`

**功能**：
- 统计挂起的流程实例数量
- 统计超时任务数量
- 统计转办/委派任务数量
- 显示平均审批时长

```vue
<template>
  <div class="workflow-dashboard">
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card>
          <div class="stat-item">
            <div class="stat-value">{{ stats.suspendedCount }}</div>
            <div class="stat-label">挂起流程</div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card>
          <div class="stat-item">
            <div class="stat-value danger">{{ stats.timeoutCount }}</div>
            <div class="stat-label">超时任务</div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card>
          <div class="stat-item">
            <div class="stat-value">{{ stats.transferredCount }}</div>
            <div class="stat-label">转办任务</div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card>
          <div class="stat-item">
            <div class="stat-value">{{ stats.avgDuration }}</div>
            <div class="stat-label">平均处理时长</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>
```

#### 2.8 任务搜索与筛选增强

**文件**：`src/views/workflow/TaskList.vue`

**新增筛选条件**：

```vue
<el-form inline>
  <!-- 原有筛选条件 -->
  <el-form-item label="状态">
    <el-select v-model="queryForm.status">
      <el-option label="待办" value="PENDING" />
      <el-option label="已完成" value="COMPLETED" />
      <el-option label="已驳回" value="REJECTED" />
      <!-- 新增状态 -->
      <el-option label="已回退" value="ROLLED_BACK" />
      <el-option label="已委派" value="DELEGATED" />
    </el-select>
  </el-form-item>
  
  <!-- 新增筛选条件 -->
  <el-form-item label="超时状态">
    <el-select v-model="queryForm.timeoutStatus">
      <el-option label="全部" value="" />
      <el-option label="即将超时" value="WARNING" />
      <el-option label="已超时" value="TIMEOUT" />
    </el-select>
  </el-form-item>
  
  <el-form-item label="转办状态">
    <el-select v-model="queryForm.transferStatus">
      <el-option label="全部" value="" />
      <el-option label="转办任务" value="TRANSFERRED" />
      <el-option label="委派任务" value="DELEGATED" />
    </el-select>
  </el-form-item>
</el-form>
```

---

## 三、后端 Controller 需要补充

### 3.1 WorkflowController 新增接口

**文件路径**：`apex-interfaces/src/main/java/com/apex/scaffold/interfaces/controller/WorkflowController.java`

**需要新增的 REST 接口**：

```java
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    /**
     * 流程回退
     */
    @PostMapping("/tasks/{taskId}/rollback")
    public Mono<Result<Void>> rollbackTask(
            @PathVariable Long taskId,
            @RequestBody @Valid RollbackTaskRequest request) {
        return workflowService.rollbackToNode(
                taskId,
                request.getTargetNodeCode(),
                SecurityUtils.getCurrentUserId(),
                request.getReason()
        ).thenReturn(Result.success());
    }

    /**
     * 任务转办
     */
    @PostMapping("/tasks/{taskId}/transfer")
    public Mono<Result<Void>> transferTask(
            @PathVariable Long taskId,
            @RequestBody @Valid TransferTaskRequest request) {
        return workflowService.transferTask(
                taskId,
                request.getNewAssigneeId(),
                SecurityUtils.getCurrentUserId(),
                request.getReason()
        ).thenReturn(Result.success());
    }

    /**
     * 任务委派
     */
    @PostMapping("/tasks/{taskId}/delegate")
    public Mono<Result<Void>> delegateTask(
            @PathVariable Long taskId,
            @RequestBody @Valid DelegateTaskRequest request) {
        return workflowService.delegateTask(
                taskId,
                request.getDelegateToId(),
                SecurityUtils.getCurrentUserId(),
                request.getReason()
        ).thenReturn(Result.success());
    }

    /**
     * 挂起流程实例
     */
    @PostMapping("/instances/{instanceId}/suspend")
    public Mono<Result<Void>> suspendInstance(
            @PathVariable Long instanceId,
            @RequestBody @Valid SuspendInstanceRequest request) {
        return workflowService.suspendInstance(
                instanceId,
                SecurityUtils.getCurrentUserId(),
                request.getReason()
        ).thenReturn(Result.success());
    }

    /**
     * 恢复流程实例
     */
    @PostMapping("/instances/{instanceId}/resume")
    public Mono<Result<Void>> resumeInstance(
            @PathVariable Long instanceId,
            @RequestBody @Valid ResumeInstanceRequest request) {
        return workflowService.resumeInstance(
                instanceId,
                SecurityUtils.getCurrentUserId(),
                request.getReason()
        ).thenReturn(Result.success());
    }

    /**
     * 查询前置节点列表（用于回退）
     */
    @GetMapping("/tasks/{taskId}/previous-nodes")
    public Mono<Result<List<WorkflowNodeVO>>> getPreviousNodes(
            @PathVariable Long taskId) {
        // TODO: 实现前置节点查询逻辑
        return Mono.just(Result.success(Collections.emptyList()));
    }
}
```

### 3.2 请求 DTO 类

**文件路径**：`apex-interfaces/src/main/java/com/apex/scaffold/interfaces/dto/workflow/`

```java
// RollbackTaskRequest.java
@Data
public class RollbackTaskRequest {
    @NotBlank(message = "目标节点编码不能为空")
    private String targetNodeCode;
    
    @NotBlank(message = "回退原因不能为空")
    @Size(max = 500, message = "回退原因不能超过500字")
    private String reason;
}

// TransferTaskRequest.java
@Data
public class TransferTaskRequest {
    @NotNull(message = "新审批人ID不能为空")
    private Long newAssigneeId;
    
    @NotBlank(message = "转办原因不能为空")
    @Size(max = 500, message = "转办原因不能超过500字")
    private String reason;
}

// DelegateTaskRequest.java
@Data
public class DelegateTaskRequest {
    @NotNull(message = "受托人ID不能为空")
    private Long delegateToId;
    
    @NotBlank(message = "委派说明不能为空")
    @Size(max = 500, message = "委派说明不能超过500字")
    private String reason;
}

// SuspendInstanceRequest.java
@Data
public class SuspendInstanceRequest {
    @NotBlank(message = "挂起原因不能为空")
    @Size(max = 500, message = "挂起原因不能超过500字")
    private String reason;
}

// ResumeInstanceRequest.java
@Data
public class ResumeInstanceRequest {
    @NotBlank(message = "恢复原因不能为空")
    @Size(max = 500, message = "恢复原因不能超过500字")
    private String reason;
}
```

---

## 四、实施优先级

### P0（必须实现）- 核心功能

| 项目 | 工作量 | 说明 |
|------|--------|------|
| API 接口层 | 0.5天 | 新增 5 个接口方法 |
| TypeScript 类型定义 | 0.5天 | 更新 ProcessInstance 和 Task 接口 |
| 待办任务页面 - 回退功能 | 1天 | 回退按钮 + 对话框 + 前置节点选择 |
| 待办任务页面 - 转办功能 | 1天 | 转办按钮 + 对话框 + 用户选择 |
| 待办任务页面 - 委派功能 | 0.5天 | 委派按钮 + 对话框（复用转办） |
| 流程实例详情 - 挂起/恢复 | 0.5天 | 挂起/恢复按钮 + 确认对话框 |
| 后端 Controller 接口 | 1天 | 新增 5 个 REST 接口 + DTO 类 |

**合计**：约 **5 个工作日**

### P1（建议实现）- 增强功能

| 项目 | 工作量 | 说明 |
|------|--------|------|
| 超时任务提醒组件 | 1天 | 全局提醒 + 跳转处理 |
| 任务列表字段显示 | 0.5天 | 超时时间、转办信息列 |
| 流程图展示增强 | 1天 | 回退标识、转办标识、超时高亮 |
| 搜索筛选增强 | 0.5天 | 新增超时状态、转办状态筛选 |

**合计**：约 **3 个工作日**

### P2（可选实现）- 锦上添花

| 项目 | 工作量 | 说明 |
|------|--------|------|
| 工作流统计看板 | 2天 | 数据统计 + 图表展示 |
| 流程轨迹时间轴 | 1天 | 完整的审批记录展示 |
| 批量操作功能 | 1天 | 批量转办、批量委派 |

**合计**：约 **4 个工作日**

---

## 五、实施步骤建议

### 阶段一：后端接口开发（1 天）

1. 创建请求 DTO 类
2. 在 WorkflowController 中新增 5 个接口
3. 编写接口单元测试
4. 更新 Swagger 文档

### 阶段二：前端基础功能开发（4 天）

1. **Day 1**：API 接口层 + TypeScript 类型定义
2. **Day 2**：待办任务页面 - 回退功能
3. **Day 3**：待办任务页面 - 转办/委派功能
4. **Day 4**：流程实例详情 - 挂起/恢复功能

### 阶段三：增强功能开发（3 天）

1. **Day 1**：超时任务提醒 + 任务列表字段显示
2. **Day 2**：流程图展示增强
3. **Day 3**：搜索筛选增强 + 测试修复

### 阶段四：可选功能开发（4 天）

根据项目需求和时间安排，可选择实施统计看板、流程轨迹等功能。

---

## 六、注意事项

### 6.1 权限控制

- **回退功能**：只有当前节点的审批人可以回退
- **转办/委派**：只有任务的当前办理人可以转办/委派
- **挂起/恢复**：只有流程发起人或管理员可以挂起/恢复

前端需要根据当前用户角色和权限动态显示/隐藏操作按钮。

### 6.2 状态同步

- 流程挂起后，所有待办任务应显示"流程已挂起"状态，禁用审批操作
- 转办后，任务从原审批人的待办列表消失，出现在新审批人的列表中
- 委派后，原任务保持待办状态，新增委派任务给受托人

### 6.3 错误处理

建议统一错误处理：

```typescript
async function handleRollback() {
  try {
    await rollbackTask(taskId, rollbackForm);
    ElMessage.success('回退成功');
    refreshTaskList();
  } catch (error) {
    if (error.code === 'TASK_NOT_FOUND') {
      ElMessage.error('任务不存在');
    } else if (error.code === 'PERMISSION_DENIED') {
      ElMessage.error('无权限操作');
    } else {
      ElMessage.error('回退失败：' + error.message);
    }
  }
}
```

### 6.4 用户体验优化

1. **操作确认**：所有不可逆操作（回退、转办、挂起）都需要二次确认
2. **加载状态**：接口调用时显示 loading 状态
3. **成功反馈**：操作成功后显示 Toast 提示并刷新列表
4. **表单校验**：原因/说明字段必填，限制最大长度

---

## 七、测试用例

### 7.1 回退功能测试

- ✅ 选择前置节点并输入原因，回退成功
- ✅ 未选择节点，提示"请选择回退节点"
- ✅ 原因为空，提示"请输入回退原因"
- ✅ 回退非待办任务，提示"只能回退待办任务"
- ✅ 回退他人任务，提示"无权限操作"

### 7.2 转办功能测试

- ✅ 选择新审批人并输入原因，转办成功
- ✅ 未选择新审批人，提示"请选择新审批人"
- ✅ 转办给自己，提示"不能转办给自己"
- ✅ 转办已完成任务，提示"只能转办待办任务"

### 7.3 挂起/恢复测试

- ✅ 挂起运行中的流程，挂起成功
- ✅ 挂起后，所有待办任务无法审批
- ✅ 恢复挂起的流程，恢复成功
- ✅ 恢复后，待办任务可以继续审批
- ✅ 非流程发起人挂起流程，提示"无权限操作"

---

## 八、总结

### 调整影响范围

| 层面 | 影响程度 | 说明 |
|------|----------|------|
| API 接口层 | 中等 | 新增 5 个接口方法，工作量适中 |
| 页面组件 | 较大 | 待办任务页面、流程详情页面需要较大改动 |
| 数据模型 | 小 | 只需更新 TypeScript 接口定义 |
| 业务逻辑 | 中等 | 需要处理新的业务状态和流转逻辑 |

### 工作量评估

- **后端接口开发**：1 天
- **前端核心功能**：4 天
- **前端增强功能**：3 天
- **前端可选功能**：4 天

**总计**：8-12 个工作日（取决于实施范围）

### 建议

1. **优先实施 P0 核心功能**（5 天），确保基础功能可用
2. **根据用户反馈**，决定是否实施 P1 增强功能
3. **P2 可选功能**可以作为后续迭代的内容

---

**文档版本**：1.0  
**创建时间**：2026-09-04  
**作者**：Kiro (AI Assistant)
