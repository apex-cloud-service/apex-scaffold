# 工作流可视化设计器与监控实现方案

## 📋 项目概述

**目标**：基于 v1.3.0 商业化增强完成报告中的"下一步建议"，实现工作流引擎的可视化设计器和流程监控统计功能，完成前后端联调和配套文档。

**执行范围**：
- ✅ 工作流可视化设计器（前端）- 基于 Vue Flow
- ✅ 工作流监控和统计功能（前后端）
- ✅ 前后端联调验证
- ✅ 配套文档、脚本、配置文件和测试代码

**技术栈**：
- 前端：Vue 3 + TypeScript + Vite + Element Plus + Vue Flow
- 后端：Java 17 + Spring Boot 3 + WebFlux + MyBatis-Plus
- 架构：DDD 五层架构（已实现）

---

## 🎯 实现目标

### 1. 工作流可视化设计器（前端）

#### 1.1 核心功能
- **流程图绘制**：拖拽节点（开始、审批、条件、结束）创建流程
- **节点配置面板**：
  - 审批节点：配置审批人类型（指定用户/角色/上级/发起人）、审批策略（或签/会签）、超时设置
  - 条件节点：配置条件表达式（支持 SpEL）
  - 连线配置：条件分支设置
- **流程验证**：
  - DAG 有效性检查（无环）
  - 节点完整性检查（必须有开始和结束节点）
  - 连线完整性检查（所有节点都有前驱和后继）
- **流程保存**：将设计的流程转换为后端 WorkflowDefinitionAggregate JSON 格式保存
- **流程加载**：从后端加载已保存的流程定义并渲染

#### 1.2 页面结构
```
/workflow/designer
  ├── 顶部工具栏：保存、验证、预览、导出
  ├── 左侧节点面板：可拖拽的节点库（START/APPROVAL/CONDITION/END）
  ├── 中间画布：Vue Flow 画布区域
  └── 右侧属性面板：选中节点/连线的配置表单
```

#### 1.3 数据模型映射

**前端 Vue Flow 数据结构**：
```typescript
interface FlowNode {
  id: string
  type: 'start' | 'approval' | 'condition' | 'end'
  position: { x: number; y: number }
  data: {
    label: string
    // 审批节点特有
    approverType?: 'SPECIFIED_USER' | 'ROLE' | 'SUPERIOR' | 'INITIATOR'
    approverValue?: string
    approvalStrategy?: 'OR' | 'AND'
    timeoutHours?: number
    timeoutAction?: string
    // 条件节点特有
    condition?: string
  }
}

interface FlowEdge {
  id: string
  source: string
  target: string
  label?: string
  data?: {
    condition?: string  // 条件分支表达式
  }
}
```

**后端领域模型**：
```java
// WorkflowDefinitionAggregate
{
  "code": "leave_approval",
  "name": "请假审批流程",
  "nodes": [
    {
      "nodeId": "node_1",
      "nodeType": "START",
      "nodeName": "开始"
    },
    {
      "nodeId": "node_2",
      "nodeType": "APPROVAL",
      "nodeName": "部门主管审批",
      "approverType": "SUPERIOR",
      "approvalStrategy": "OR"
    }
  ],
  "edges": [
    {
      "edgeId": "edge_1",
      "sourceNodeId": "node_1",
      "targetNodeId": "node_2"
    }
  ]
}
```

**转换逻辑**：
- 前端保存时：`FlowNode[] + FlowEdge[]` → `WorkflowDefinitionAggregate` JSON
- 前端加载时：`WorkflowDefinitionAggregate` JSON → `FlowNode[] + FlowEdge[]`

---

### 2. 工作流监控和统计（前后端）

#### 2.1 监控页面功能

**页面路径**：`/workflow/monitor`

**核心功能**：
- **流程实例列表**：
  - 表格展示：流程名称、业务键、状态、发起人、开始时间、结束时间、耗时
  - 筛选：按状态（运行中/已完成/已拒绝）、时间范围筛选
  - 分页查询
- **流程实例详情**：
  - 流程图可视化：高亮当前节点
  - 任务列表：任务名称、状态、办理人、开始时间、完成时间
  - 审批记录：审批人、审批意见、审批时间、审批结果
- **统计看板**：
  - 今日发起流程数
  - 运行中流程数
  - 待办任务数
  - 平均完成时长
  - 流程状态分布（饼图）
  - 流程趋势（折线图：每日发起/完成数量）

#### 2.2 后端 API 增强

需要新增的 API 接口：

```java
// WorkflowHandler 新增接口
GET /admin-api/v1/workflows/instances?status=&pageNum=&pageSize=  // 分页查询流程实例
GET /admin-api/v1/workflows/instances/{id}/detail               // 查询流程实例详情（含任务列表）
GET /admin-api/v1/workflows/instances/{id}/records              // 查询审批记录
GET /admin-api/v1/workflows/statistics/overview                 // 统计概览数据
GET /admin-api/v1/workflows/statistics/trend?days=7             // 流程趋势数据
```

**数据模型**：

```java
// WorkflowInstanceDTO（应用层 DTO）
{
  "id": 1,
  "definitionCode": "leave_approval",
  "definitionName": "请假审批流程",
  "businessKey": "LEAVE-2026-001",
  "status": "RUNNING",
  "initiatorId": 10,
  "initiatorName": "张三",
  "startTime": "2026-09-02T10:00:00",
  "endTime": null,
  "duration": 3600  // 秒
}

// WorkflowInstanceDetailDTO
{
  "instance": { /* WorkflowInstanceDTO */ },
  "definition": { /* 流程定义，用于渲染流程图 */ },
  "tasks": [ /* 任务列表 */ ],
  "currentNodeId": "node_2"  // 当前所在节点
}

// WorkflowStatisticsDTO
{
  "todayStarted": 15,
  "runningTotal": 8,
  "pendingTaskTotal": 23,
  "avgCompletionSeconds": 7200,
  "statusDistribution": {
    "RUNNING": 8,
    "COMPLETED": 45,
    "REJECTED": 3
  }
}

// WorkflowTrendDTO
{
  "dates": ["2026-08-27", "2026-08-28", ...],
  "startedCounts": [5, 8, 12, ...],
  "completedCounts": [4, 7, 10, ...]
}
```

---

## 📐 系统架构设计

### 3.1 前端目录结构

```
apex-admin-web/
├── src/
│   ├── api/
│   │   └── workflow.ts              # 工作流 API 接口
│   ├── views/
│   │   └── workflow/
│   │       ├── designer/            # 流程设计器
│   │       │   ├── index.vue        # 设计器主页面
│   │       │   ├── NodePanel.vue    # 左侧节点面板
│   │       │   ├── Canvas.vue       # 中间画布（Vue Flow）
│   │       │   ├── PropertyPanel.vue # 右侧属性面板
│   │       │   ├── nodes/           # 自定义节点组件
│   │       │   │   ├── StartNode.vue
│   │       │   │   ├── ApprovalNode.vue
│   │       │   │   ├── ConditionNode.vue
│   │       │   │   └── EndNode.vue
│   │       │   └── utils/
│   │       │       ├── validator.ts  # 流程验证逻辑
│   │       │       └── converter.ts  # 前后端数据转换
│   │       ├── monitor/             # 流程监控
│   │       │   ├── index.vue        # 监控主页面
│   │       │   ├── InstanceList.vue # 流程实例列表
│   │       │   ├── InstanceDetail.vue # 流程实例详情
│   │       │   └── Statistics.vue   # 统计看板
│   │       └── task/                # 待办任务（已有，增强）
│   │           └── index.vue        # 我的待办任务
│   ├── types/
│   │   └── workflow.ts              # 工作流类型定义
│   └── router/
│       └── dynamic.ts               # 新增工作流路由配置
└── package.json                     # 新增 @vue-flow/core 依赖
```

### 3.2 后端目录结构（增强）

```
apex-scaffold/
├── apex-domain/
│   └── src/main/java/.../domain/
│       └── workflow/
│           ├── WorkflowStatistics.java       # 统计值对象（新增）
│           └── WorkflowTrend.java            # 趋势值对象（新增）
├── apex-infrastructure/
│   └── src/main/java/.../infrastructure/
│       └── persistence/
│           ├── mapper/
│           │   ├── ProcessInstanceMapper.java  # 新增统计查询方法
│           │   └── TaskMapper.java            # 新增统计查询方法
│           └── repository/
│               └── WorkflowStatisticsRepositoryImpl.java  # 新增统计仓储
├── apex-application/
│   └── src/main/java/.../application/
│       ├── dto/
│       │   ├── WorkflowInstanceDTO.java       # 新增
│       │   ├── WorkflowInstanceDetailDTO.java # 新增
│       │   ├── WorkflowStatisticsDTO.java     # 新增
│       │   └── WorkflowTrendDTO.java          # 新增
│       └── service/
│           ├── WorkflowQueryService.java      # 新增（查询服务）
│           └── WorkflowStatisticsService.java # 新增（统计服务）
├── apex-interfaces/
│   └── src/main/java/.../interfaces/
│       └── handler/
│           └── WorkflowHandler.java           # 增强（新增 6 个接口）
└── docs/
    ├── WORKFLOW_DESIGNER_GUIDE.md             # 设计器使用指南（新增）
    └── WORKFLOW_MONITOR_GUIDE.md              # 监控使用指南（新增）
```

---

## 🔧 实现步骤

### 阶段 1：后端 API 增强（2-3 小时）

#### 步骤 1.1：创建 DTO 类
- [ ] WorkflowInstanceDTO
- [ ] WorkflowInstanceDetailDTO
- [ ] WorkflowStatisticsDTO
- [ ] WorkflowTrendDTO
- [ ] WorkflowTaskDTO（增强现有 Task）
- [ ] ApprovalRecordDTO

#### 步骤 1.2：实现统计查询服务
- [ ] WorkflowStatisticsService
  - `getOverview()`: 统计概览
  - `getTrend(int days)`: 流程趋势
- [ ] WorkflowQueryService
  - `listInstances(String status, int pageNum, int pageSize)`: 分页查询流程实例
  - `getInstanceDetail(Long instanceId)`: 查询流程实例详情
  - `listApprovalRecords(Long instanceId)`: 查询审批记录

#### 步骤 1.3：增强 WorkflowHandler
- [ ] `GET /workflows/instances` - 分页查询流程实例
- [ ] `GET /workflows/instances/{id}/detail` - 查询流程实例详情
- [ ] `GET /workflows/instances/{id}/records` - 查询审批记录
- [ ] `GET /workflows/statistics/overview` - 统计概览
- [ ] `GET /workflows/statistics/trend` - 流程趋势
- [ ] `PUT /workflows/definitions/{code}` - 更新流程定义（设计器保存）

#### 步骤 1.4：编写单元测试
- [ ] WorkflowStatisticsServiceTest
- [ ] WorkflowQueryServiceTest
- [ ] WorkflowHandlerTest（新接口）

---

### 阶段 2：前端工作流 API 封装（0.5 小时）

#### 步骤 2.1：创建 workflow.ts API 文件
```typescript
// src/api/workflow.ts

// ===== 流程定义管理 =====
export function getWorkflowDefinition(code: string): Promise<WorkflowDefinition>
export function saveWorkflowDefinition(payload: WorkflowDefinitionPayload): Promise<void>
export function updateWorkflowDefinition(code: string, payload: WorkflowDefinitionPayload): Promise<void>

// ===== 流程实例管理 =====
export function listWorkflowInstances(params: InstanceQueryParams): Promise<PageResponse<WorkflowInstance>>
export function getInstanceDetail(id: number): Promise<WorkflowInstanceDetail>
export function getApprovalRecords(instanceId: number): Promise<ApprovalRecord[]>

// ===== 统计分析 =====
export function getWorkflowStatistics(): Promise<WorkflowStatistics>
export function getWorkflowTrend(days: number): Promise<WorkflowTrend>

// ===== 待办任务 =====
export function getMyPendingTasks(): Promise<WorkflowTask[]>
export function completeTask(taskId: number, comment?: string): Promise<void>
export function rejectTask(taskId: number, comment: string): Promise<void>
```

#### 步骤 2.2：创建类型定义文件
```typescript
// src/types/workflow.ts
export interface WorkflowDefinition { /* ... */ }
export interface WorkflowNode { /* ... */ }
export interface WorkflowEdge { /* ... */ }
export interface WorkflowInstance { /* ... */ }
export interface WorkflowTask { /* ... */ }
export interface ApprovalRecord { /* ... */ }
export interface WorkflowStatistics { /* ... */ }
export interface WorkflowTrend { /* ... */ }
```

---

### 阶段 3：工作流可视化设计器实现（4-5 小时）

#### 步骤 3.1：安装依赖
```bash
cd /Users/work/apex-admin-web
npm install @vue-flow/core @vue-flow/controls @vue-flow/minimap
```

#### 步骤 3.2：实现自定义节点组件
- [ ] StartNode.vue（圆形绿色节点）
- [ ] ApprovalNode.vue（矩形蓝色节点，显示审批人类型）
- [ ] ConditionNode.vue（菱形黄色节点，显示条件表达式）
- [ ] EndNode.vue（圆形红色节点）

#### 步骤 3.3：实现核心组件
- [ ] NodePanel.vue（可拖拽节点库）
- [ ] Canvas.vue（Vue Flow 画布，支持拖拽、连线、缩放、平移）
- [ ] PropertyPanel.vue（属性配置表单，动态渲染不同节点类型的配置项）

#### 步骤 3.4：实现设计器主页面
- [ ] index.vue
  - 顶部工具栏：保存、验证、预览、导出按钮
  - 三栏布局：节点面板 + 画布 + 属性面板
  - 流程验证逻辑调用
  - 保存时调用 converter.ts 转换为后端格式
  - 加载时从后端获取并转换为 Vue Flow 格式

#### 步骤 3.5：实现工具函数
- [ ] validator.ts
  - `validateDAG()`: DAG 有效性检查
  - `validateCompleteness()`: 节点完整性检查
  - `validateConnections()`: 连线完整性检查
- [ ] converter.ts
  - `toBackendFormat()`: 前端 → 后端格式转换
  - `toFrontendFormat()`: 后端 → 前端格式转换

---

### 阶段 4：工作流监控实现（3-4 小时）

#### 步骤 4.1：实现监控页面组件
- [ ] Statistics.vue（统计看板）
  - 4 个指标卡片：今日发起/运行中/待办任务/平均耗时
  - 饼图：流程状态分布（使用 ECharts）
  - 折线图：流程趋势（使用 ECharts）
- [ ] InstanceList.vue（流程实例列表）
  - 表格展示：流程名称、业务键、状态、发起人、时间、耗时
  - 筛选器：状态筛选、时间范围选择
  - 分页组件
  - 点击行跳转到详情页
- [ ] InstanceDetail.vue（流程实例详情）
  - 流程图可视化（复用 Vue Flow，只读模式）
  - 高亮当前节点
  - 任务列表表格
  - 审批记录时间轴

#### 步骤 4.2：实现监控主页面
- [ ] index.vue
  - Tab 切换：统计看板 / 流程实例列表
  - 数据加载与刷新逻辑

---

### 阶段 5：前后端联调（1-2 小时）

#### 步骤 5.1：启动后端服务
```bash
cd /Users/work/apex-scaffold
mvnd clean spring-boot:run -pl apex-interfaces
```

#### 步骤 5.2：启动前端服务
```bash
cd /Users/work/apex-admin-web
npm run dev
```

#### 步骤 5.3：联调测试
- [ ] 设计器保存流程定义
- [ ] 设计器加载已保存的流程定义
- [ ] 流程验证功能测试
- [ ] 监控页面数据加载测试
- [ ] 流程实例详情页测试
- [ ] 统计看板图表渲染测试

---

### 阶段 6：测试与文档（1-2 小时）

#### 步骤 6.1：编写前端测试
- [ ] 流程验证逻辑单元测试
- [ ] 数据转换逻辑单元测试

#### 步骤 6.2：编写文档
- [ ] WORKFLOW_DESIGNER_GUIDE.md
  - 设计器使用说明
  - 节点类型介绍
  - 配置说明
  - 最佳实践
- [ ] WORKFLOW_MONITOR_GUIDE.md
  - 监控功能说明
  - 统计指标解释
  - 故障排查

#### 步骤 6.3：更新项目文档
- [ ] 更新 README.md（新增工作流模块）
- [ ] 更新 CHANGELOG.md（v1.4.0 新特性）
- [ ] 更新 COMMERCIAL_IMPROVEMENT_TASKS.md（标记已完成）

---

## 📊 验收标准

### 功能验收
- [ ] 设计器能正常创建包含 4 种节点类型的流程
- [ ] 设计器能验证流程的有效性
- [ ] 设计器能保存和加载流程定义
- [ ] 监控页面能展示流程实例列表
- [ ] 监控页面能展示流程实例详情（含流程图、任务列表、审批记录）
- [ ] 统计看板能展示实时统计数据和图表
- [ ] 所有 API 接口正常响应

### 质量验收
- [ ] 后端新增代码通过单元测试
- [ ] 前端页面在 Chrome/Firefox/Safari 浏览器正常运行
- [ ] 流程图渲染性能良好（50 个节点以内流畅）
- [ ] 代码符合项目现有规范（Java P3C / TypeScript ESLint）
- [ ] 所有文档完整且准确

### 兼容性验收
- [ ] 不影响现有功能（全量回归测试通过）
- [ ] 与 v1.3.0 工作流引擎后端完全兼容
- [ ] Enterprise License 特性门控正常工作

---

## 🔒 安全与权限

### 权限定义
所有工作流相关功能需要 `workflow:*` 权限，具体细分：
- `workflow:definition:view` - 查看流程定义
- `workflow:definition:edit` - 编辑流程定义
- `workflow:instance:view` - 查看流程实例
- `workflow:task:view` - 查看待办任务
- `workflow:task:complete` - 完成任务
- `workflow:statistics:view` - 查看统计数据

### License 门控
- 前端菜单路由根据用户权限动态生成
- 后端 Handler 通过 `@RequirePermission` 注解强制鉴权
- 工作流模块仅 Enterprise 版本可用（通过 License 特性门控）

---

## 🎨 UI 设计规范

### 设计器样式
- 节点：圆角 8px，阴影 0 2px 8px rgba(0,0,0,0.1)
- 连线：灰色实线，选中时蓝色高亮
- 画布背景：浅灰点阵 (#f8f9fa)
- 节点颜色：
  - START: #67C23A（绿色）
  - APPROVAL: #409EFF（蓝色）
  - CONDITION: #E6A23C（橙色）
  - END: #F56C6C（红色）

### 监控页面样式
- 统计卡片：白色背景，圆角 4px，阴影 hover 效果
- 表格：斑马纹，悬停高亮
- 图表：Element Plus 主题色 + ECharts 默认调色盘
- 状态标签：
  - RUNNING: 蓝色
  - COMPLETED: 绿色
  - REJECTED: 红色
  - PENDING: 橙色

---

## 📝 注意事项

### 开发注意事项
1. **数据转换**：前后端数据模型不一致，务必通过 converter.ts 转换
2. **流程验证**：保存前必须验证流程有效性，避免无效流程入库
3. **性能优化**：流程图渲染时使用虚拟滚动，避免大流程卡顿
4. **错误处理**：所有 API 调用需要统一错误处理和提示

### 测试注意事项
1. **边界测试**：测试空流程、单节点流程、100 节点大流程
2. **异常测试**：测试保存失败、加载失败、验证失败场景
3. **兼容测试**：测试 v1.3.0 工作流数据能否正常加载

### 文档注意事项
1. **截图**：文档中包含关键页面截图
2. **示例**：提供完整的流程定义示例（请假审批、报销审批）
3. **故障排查**：列出常见问题和解决方案

---

## 🚀 后续扩展方向

### v1.5.0 可选增强
1. 流程模板市场（预置常见业务流程）
2. 流程版本管理（灰度发布、回滚）
3. 流程模拟运行（预执行，不落库）
4. 流程性能分析（瓶颈节点识别）
5. 移动端待办任务 H5 页面

### 商业化价值
- **可视化设计器**：降低业务人员配置门槛，提升 Enterprise 版吸引力
- **监控统计**：提供运营数据分析能力，支撑流程优化决策
- **完整闭环**：设计 → 执行 → 监控形成完整产品，可独立定价

---

## ✅ 总结

本方案基于 v1.3.0 商业化增强完成报告的"中期建议"，实现工作流引擎的可视化设计器和监控统计功能。通过 Vue Flow 快速构建流程设计器，通过 ECharts 实现统计看板，与后端 DDD 五层架构深度集成，形成完整的工作流管理闭环。

**预计开发时间**：12-15 小时（约 2 个工作日）  
**商业价值提升**：Enterprise 版本竞争力显著增强，可独立售卖工作流模块  
**技术债务**：无，完全基于现有架构扩展，不引入新的技术栈风险
