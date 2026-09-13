# 工作流可视化设计器使用指南

## 📋 概述

工作流可视化设计器是 Apex Scaffold v1.4.0 新增的 Enterprise 特性,提供所见即所得的流程定义能力,无需编写 JSON 配置即可快速创建复杂的审批流程。

**核心特性**:
- 🎨 可视化拖拽:拖拽节点创建流程,直观易用
- 🔗 连线操作:点击连接节点,自动校验有效性
- ⚙️ 属性配置:动态配置节点审批人、超时策略
- ✅ 流程验证:实时校验 DAG 有效性、节点完整性
- 💾 一键保存:自动转换为后端 JSON 格式

---

## 🚀 快速开始

### 前置条件

1. **License 要求**: Enterprise 版本(包含 `workflow:*` 特性)
2. **权限要求**: `workflow:definition:edit`
3. **技术依赖**: Vue 3 + Vue Flow + Element Plus

### 安装依赖

```bash
cd /Users/work/apex-admin-web
npm install @vue-flow/core @vue-flow/controls @vue-flow/minimap
```

### 访问入口

**前端页面路径**: `/workflow/designer`

---

## 🎨 节点类型

### 1. 开始节点 (START)

**外观**: 绿色圆形  
**用途**: 流程入口,每个流程必须有且仅有一个  
**配置项**: 无

**示例**:
```json
{
  "nodeId": "start_1",
  "nodeType": "START",
  "nodeName": "开始",
  "position": { "x": 100, "y": 100 }
}
```

### 2. 审批节点 (USER_TASK)

**外观**: 蓝色矩形  
**用途**: 人工审批环节,支持多种审批人配置  
**配置项**:

| 配置项 | 类型 | 说明 | 可选值 |
|--------|------|------|--------|
| `nodeName` | string | 节点名称 | 如"部门主管审批" |
| `approverType` | string | 审批人类型 | USER/ROLE/DEPT |
| `approverValue` | string | 审批人值 | 用户ID/角色CODE/部门ID |
| `approvalStrategy` | string | 审批策略 | ANY(或签)/ALL(会签) |
| `timeoutHours` | number | 超时时长(小时) | 如 24 |
| `timeoutAction` | string | 超时动作 | AUTO_PASS/AUTO_REJECT/NOTIFY |

**审批人类型说明**:
- `USER`: 指定用户,`approverValue` 为用户 ID(逗号分隔)
- `ROLE`: 指定角色,`approverValue` 为角色 CODE
- `DEPT`: 指定部门,`approverValue` 为部门 ID

**示例**:
```json
{
  "nodeId": "approval_1",
  "nodeType": "USER_TASK",
  "nodeName": "部门主管审批",
  "approverType": "ROLE",
  "approverValue": "dept_manager",
  "approvalStrategy": "ANY",
  "timeoutHours": 24,
  "timeoutAction": "NOTIFY"
}
```

### 3. 条件节点 (GATEWAY)

**外观**: 黄色菱形  
**用途**: 流程分支判断,根据条件选择不同路径  
**配置项**:

| 配置项 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| `nodeName` | string | 节点名称 | "金额判断" |
| `condition` | string | 条件表达式(SpEL) | `#amount > 10000` |

**条件表达式示例**:
```javascript
// 金额判断
#amount > 10000

// 请假天数判断
#days <= 3

// 复合条件
#amount > 10000 && #type == 'URGENT'
```

**示例**:
```json
{
  "nodeId": "gateway_1",
  "nodeType": "GATEWAY",
  "nodeName": "金额判断",
  "condition": "#amount > 10000"
}
```

### 4. 结束节点 (END)

**外观**: 红色圆形  
**用途**: 流程出口,每个流程必须有且仅有一个  
**配置项**: 无

**示例**:
```json
{
  "nodeId": "end_1",
  "nodeType": "END",
  "nodeName": "结束",
  "position": { "x": 500, "y": 100 }
}
```

---

## 🔗 连线规则

### 基本规则

1. **开始节点**: 只能作为起点,不能作为终点
2. **结束节点**: 只能作为终点,不能作为起点
3. **审批节点**: 既可作为起点,也可作为终点
4. **条件节点**: 必须有多个出口,每个出口配置不同条件

### 连线示例

```json
{
  "edgeId": "edge_1",
  "sourceNodeId": "start_1",
  "targetNodeId": "approval_1"
}
```

### 条件分支连线

```json
{
  "edgeId": "edge_2",
  "sourceNodeId": "gateway_1",
  "targetNodeId": "approval_2",
  "condition": "#amount > 10000"
}
```

---

## ✅ 流程验证规则

### 1. DAG 有效性检查

**规则**: 流程图必须是有向无环图(Directed Acyclic Graph)

**错误示例**:
```
A → B → C → A  ❌ 存在环路
```

**正确示例**:
```
A → B → C → D  ✅ 无环路
```

### 2. 节点完整性检查

**必须满足**:
- 有且仅有一个开始节点
- 有且仅有一个结束节点
- 所有节点必须有名称
- 审批节点必须配置审批人

### 3. 连线完整性检查

**必须满足**:
- 所有节点(除开始和结束)都有前驱和后继
- 条件节点至少有 2 个出口
- 不存在孤立节点

### 验证错误提示

```typescript
// 示例：验证结果
interface ValidationResult {
  valid: boolean
  errors: string[]
}

// 验证失败示例
{
  "valid": false,
  "errors": [
    "流程图存在环路",
    "审批节点'部门主管审批'未配置审批人",
    "条件节点'金额判断'只有1个出口，至少需要2个"
  ]
}
```

---

## 💾 保存与加载

### 保存流程

**前端数据格式** (Vue Flow):
```typescript
interface FlowData {
  nodes: FlowNode[]
  edges: FlowEdge[]
}
```

**后端存储格式** (JSON):
```json
{
  "code": "leave_approval",
  "name": "请假审批流程",
  "description": "员工请假审批流程",
  "nodes": [...],
  "edges": [...]
}
```

**转换过程**:
1. 前端调用 `converter.toBackendFormat()` 转换
2. 调用 API: `POST /admin-api/v1/workflows/definitions`
3. 后端保存到 `wf_workflow_definition` 表的 `node_definition` 字段

### 加载流程

**加载过程**:
1. 调用 API: `GET /admin-api/v1/workflows/definitions/{id}/detail`
2. 前端调用 `converter.toFrontendFormat()` 转换
3. Vue Flow 渲染流程图

---

## 🛠️ 开发指南

### 核心组件结构

```
views/workflow/designer/
├── index.vue                 # 设计器主页面
├── components/
│   ├── NodePanel.vue         # 左侧节点面板
│   ├── Canvas.vue            # 中间画布(Vue Flow)
│   ├── PropertyPanel.vue     # 右侧属性面板
│   └── nodes/               # 自定义节点组件
│       ├── StartNode.vue
│       ├── ApprovalNode.vue
│       ├── ConditionNode.vue
│       └── EndNode.vue
└── utils/
    ├── validator.ts          # 流程验证逻辑
    └── converter.ts          # 数据格式转换
```

### 自定义节点示例

#### StartNode.vue

```vue
<template>
  <div class="start-node">
    <div class="node-icon">▶</div>
    <div class="node-label">{{ data.label }}</div>
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'

interface Props {
  data: {
    label: string
  }
}

defineProps<Props>()
</script>

<style scoped>
.start-node {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: #67C23A;
  color: white;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 2px solid #409EFF;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.node-icon {
  font-size: 24px;
}

.node-label {
  font-size: 12px;
  margin-top: 4px;
}
</style>
```

#### ApprovalNode.vue

```vue
<template>
  <div class="approval-node" :class="{ selected: selected }">
    <Handle type="target" :position="Position.Left" />
    
    <div class="node-header">
      <el-icon><UserFilled /></el-icon>
      <span>{{ data.label }}</span>
    </div>
    
    <div class="node-body">
      <div v-if="data.approverType" class="node-info">
        <span class="label">审批人:</span>
        <span class="value">{{ getApproverLabel(data.approverType) }}</span>
      </div>
      <div v-if="data.approvalStrategy" class="node-info">
        <span class="label">策略:</span>
        <span class="value">{{ data.approvalStrategy === 'ANY' ? '或签' : '会签' }}</span>
      </div>
    </div>
    
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'
import { UserFilled } from '@element-plus/icons-vue'

interface Props {
  data: {
    label: string
    approverType?: string
    approvalStrategy?: string
  }
  selected?: boolean
}

defineProps<Props>()

const getApproverLabel = (type: string) => {
  const map: Record<string, string> = {
    USER: '指定用户',
    ROLE: '角色',
    DEPT: '部门'
  }
  return map[type] || type
}
</script>

<style scoped>
.approval-node {
  min-width: 200px;
  background: white;
  border: 2px solid #409EFF;
  border-radius: 8px;
  padding: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: all 0.3s;
}

.approval-node.selected {
  border-color: #67C23A;
  box-shadow: 0 4px 12px rgba(103, 194, 58, 0.3);
}

.node-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 8px;
}

.node-body {
  font-size: 12px;
  color: #606266;
}

.node-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 4px;
}

.node-info .label {
  color: #909399;
}

.node-info .value {
  font-weight: 500;
}
</style>
```

### 数据转换工具

#### converter.ts

```typescript
import type { WorkflowDefinition, WorkflowNode, WorkflowEdge } from '@/types/workflow'
import type { Node, Edge } from '@vue-flow/core'

/**
 * Vue Flow 格式 → 后端格式
 */
export function toBackendFormat(nodes: Node[], edges: Edge[]): WorkflowDefinition {
  return {
    code: '', // 由用户输入
    name: '', // 由用户输入
    version: 1,
    enabled: true,
    nodes: nodes.map(node => ({
      nodeId: node.id,
      nodeType: node.type?.toUpperCase() as any,
      nodeName: node.data.label,
      approverType: node.data.approverType,
      approverValue: node.data.approverValue,
      approvalStrategy: node.data.approvalStrategy,
      timeoutHours: node.data.timeoutHours,
      timeoutAction: node.data.timeoutAction,
      position: node.position
    })),
    edges: edges.map(edge => ({
      edgeId: edge.id,
      sourceNodeId: edge.source,
      targetNodeId: edge.target,
      condition: edge.data?.condition
    }))
  }
}

/**
 * 后端格式 → Vue Flow 格式
 */
export function toFrontendFormat(definition: WorkflowDefinition): { nodes: Node[]; edges: Edge[] } {
  return {
    nodes: definition.nodes.map(node => ({
      id: node.nodeId,
      type: node.nodeType.toLowerCase(),
      position: node.position || { x: 0, y: 0 },
      data: {
        label: node.nodeName,
        approverType: node.approverType,
        approverValue: node.approverValue,
        approvalStrategy: node.approvalStrategy,
        timeoutHours: node.timeoutHours,
        timeoutAction: node.timeoutAction
      }
    })),
    edges: definition.edges.map(edge => ({
      id: edge.edgeId,
      source: edge.sourceNodeId,
      target: edge.targetNodeId,
      data: {
        condition: edge.condition
      }
    }))
  }
}
```

### 流程验证工具

#### validator.ts

```typescript
import type { Node, Edge } from '@vue-flow/core'

export interface ValidationResult {
  valid: boolean
  errors: string[]
}

/**
 * 验证流程有效性
 */
export function validateWorkflow(nodes: Node[], edges: Edge[]): ValidationResult {
  const errors: string[] = []

  // 1. 检查开始节点
  const startNodes = nodes.filter(n => n.type === 'start')
  if (startNodes.length === 0) {
    errors.push('流程必须有一个开始节点')
  } else if (startNodes.length > 1) {
    errors.push('流程只能有一个开始节点')
  }

  // 2. 检查结束节点
  const endNodes = nodes.filter(n => n.type === 'end')
  if (endNodes.length === 0) {
    errors.push('流程必须有一个结束节点')
  } else if (endNodes.length > 1) {
    errors.push('流程只能有一个结束节点')
  }

  // 3. 检查节点名称
  nodes.forEach(node => {
    if (!node.data.label) {
      errors.push(`节点 ${node.id} 缺少名称`)
    }
  })

  // 4. 检查审批节点配置
  nodes.filter(n => n.type === 'approval').forEach(node => {
    if (!node.data.approverType) {
      errors.push(`审批节点 "${node.data.label}" 未配置审批人类型`)
    }
    if (!node.data.approverValue) {
      errors.push(`审批节点 "${node.data.label}" 未配置审批人`)
    }
  })

  // 5. 检查是否有环路 (DFS)
  if (hasCycle(nodes, edges)) {
    errors.push('流程图存在环路，请检查连线')
  }

  // 6. 检查孤立节点
  const connectedNodes = new Set<string>()
  edges.forEach(edge => {
    connectedNodes.add(edge.source)
    connectedNodes.add(edge.target)
  })
  
  nodes.forEach(node => {
    if (node.type !== 'start' && node.type !== 'end' && !connectedNodes.has(node.id)) {
      errors.push(`节点 "${node.data.label}" 未连接到流程中`)
    }
  })

  return {
    valid: errors.length === 0,
    errors
  }
}

/**
 * 检测是否有环路 (DFS)
 */
function hasCycle(nodes: Node[], edges: Edge[]): boolean {
  const graph = new Map<string, string[]>()
  
  // 构建邻接表
  nodes.forEach(node => graph.set(node.id, []))
  edges.forEach(edge => {
    const targets = graph.get(edge.source) || []
    targets.push(edge.target)
    graph.set(edge.source, targets)
  })

  const visited = new Set<string>()
  const recStack = new Set<string>()

  const dfs = (nodeId: string): boolean => {
    visited.add(nodeId)
    recStack.add(nodeId)

    const neighbors = graph.get(nodeId) || []
    for (const neighbor of neighbors) {
      if (!visited.has(neighbor)) {
        if (dfs(neighbor)) return true
      } else if (recStack.has(neighbor)) {
        return true // 发现环路
      }
    }

    recStack.delete(nodeId)
    return false
  }

  // 从所有未访问节点开始 DFS
  for (const node of nodes) {
    if (!visited.has(node.id)) {
      if (dfs(node.id)) return true
    }
  }

  return false
}
```

---

## 📚 常见流程示例

### 1. 简单请假审批

```
开始 → 部门主管审批 → 结束
```

**配置**:
- 部门主管审批: 审批人类型=ROLE, 审批人值=dept_manager

### 2. 分级请假审批

```
开始 → 天数判断(条件节点)
         ├─ <=3天 → 部门主管审批 → 结束
         └─ >3天 → 部门主管审批 → 人事审批 → 结束
```

**配置**:
- 条件节点: `#days <= 3`

### 3. 会签审批

```
开始 → 财务审批(会签) → 人事审批(会签) → 总经理审批 → 结束
```

**配置**:
- 财务审批: 审批策略=ALL(会签)
- 人事审批: 审批策略=ALL(会签)

---

## 🐛 常见问题

### Q1: 保存时提示"流程图存在环路"

**原因**: 流程中存在循环引用  
**解决**: 检查连线,确保没有 A→B→C→A 的情况

### Q2: 审批节点无法连接到其他节点

**原因**: 可能未正确连接 Handle  
**解决**: 确保节点组件正确使用 `<Handle>` 组件

### Q3: 加载的流程图位置混乱

**原因**: 未保存节点位置信息  
**解决**: 确保保存时包含 `position` 字段

---

## 📚 相关文档

- [工作流引擎使用指南](./workflow-guide.md)
- [工作流监控使用指南](./workflow-monitor-guide.md)
- [Vue Flow 官方文档](https://vueflow.dev/)

---

**文档版本**: v1.0  
**最后更新**: 2026-09-02  
**适用版本**: Apex Scaffold v1.2.0+
