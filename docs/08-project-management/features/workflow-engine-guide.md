# 工作流引擎使用指南

## 📋 概述

Apex Scaffold 工作流引擎是一个轻量级、高性能的业务流程管理系统，基于 DDD 领域驱动设计和 WebFlux 响应式编程模型构建。本指南详细介绍工作流引擎的功能、配置和最佳实践。

**核心特性**：
- ✅ 轻量级流程引擎，无需额外中间件
- ✅ 支持会签、或签、条件分支等复杂审批策略
- ✅ 支持流程回退、转办、委派、挂起等高级功能
- ✅ 内置超时处理机制，自动执行超时动作
- ✅ 完整的审批历史记录和审计追踪
- ✅ 响应式 API，支持高并发场景

**License 要求**：Enterprise 版

---

## 🎯 核心概念

### 1. 流程定义（Workflow Definition）

流程定义是业务流程的模板，包含流程节点（审批、条件、开始、结束）和节点间的流转边。

**关键属性**：
- `workflowCode`: 流程编码（唯一标识）
- `workflowName`: 流程名称
- `dagJson`: DAG（有向无环图）流程图 JSON
- `version`: 版本号
- `enabled`: 是否启用

**DAG JSON 结构**：
```json
{
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
      "approvalStrategy": "OR",
      "timeoutHours": 24,
      "timeoutAction": "REMIND"
    },
    {
      "nodeId": "node_3",
      "nodeType": "END",
      "nodeName": "结束"
    }
  ],
  "edges": [
    {
      "edgeId": "edge_1",
      "sourceNodeId": "node_1",
      "targetNodeId": "node_2"
    },
    {
      "edgeId": "edge_2",
      "sourceNodeId": "node_2",
      "targetNodeId": "node_3"
    }
  ]
}
```

### 2. 流程实例（Process Instance）

流程实例是流程定义的具体执行实例，代表一次完整的业务流程执行。

**关键属性**：
- `workflowDefinitionId`: 流程定义ID
- `businessKey`: 业务主键（如订单号、请假单号）
- `status`: 流程状态（RUNNING/COMPLETED/REJECTED/SUSPENDED）
- `initiatorId`: 发起人ID
- `contextJson`: 流程上下文数据
- `suspended`: 是否挂起
- `suspendReason`: 挂起原因

### 3. 任务（Task）

任务是流程实例中的待办事项，分配给具体的审批人处理。

**关键属性**：
- `processInstanceId`: 流程实例ID
- `taskName`: 任务名称
- `taskType`: 任务类型（APPROVAL/CONDITION）
- `status`: 任务状态（PENDING/COMPLETED/REJECTED/CANCELLED）
- `assigneeId`: 办理人ID
- `timeoutAt`: 超时时间
- `timeoutAction`: 超时动作（AUTO_APPROVE/AUTO_REJECT/REMIND）

### 4. 审批记录（Approval Record）

审批记录保存每次审批的详细信息，用于审计和追踪。

**关键属性**：
- `processInstanceId`: 流程实例ID
- `taskId`: 任务ID
- `approverId`: 审批人ID
- `result`: 审批结果（APPROVED/REJECTED）
- `comment`: 审批意见
- `approveTime`: 审批时间

---

## ⚙️ 配置说明

### 1. 节点类型（Node Type）

| 节点类型 | 说明 | 配置项 |
|---------|------|-------|
| START | 开始节点 | 无 |
| END | 结束节点 | 无 |
| APPROVAL | 审批节点 | approverType, approvalStrategy, timeoutHours, timeoutAction |
| CONDITION | 条件节点 | conditionExpression |

### 2. 审批人类型（Approver Type）

| 类型 | 说明 | 配置方式 |
|-----|------|---------|
| SPECIFIED_USER | 指定用户 | 配置具体用户ID列表 |
| ROLE | 角色 | 配置角色编码，系统自动查找该角色下的所有用户 |
| SUPERIOR | 上级 | 根据发起人的组织架构自动查找直属上级 |
| INITIATOR | 发起人 | 将任务分配给流程发起人（用于补充材料等场景） |

**配置示例**：
```json
{
  "nodeType": "APPROVAL",
  "approverType": "SPECIFIED_USER",
  "approverValue": "10001,10002,10003"
}
```

### 3. 审批策略（Approval Strategy）

| 策略 | 说明 | 适用场景 |
|-----|------|---------|
| OR（或签） | 任一审批人通过即可 | 部门主管审批、多人会审 |
| AND（会签） | 所有审批人都通过才可 | 重大决策、多部门协同 |

**OR 签（或签）**：
- 场景：部门有多位主管，任意一位审批通过即可
- 行为：第一个审批人通过后，流程自动流转到下一节点，其他人的待办任务自动取消

**AND 签（会签）**：
- 场景：采购申请需要财务部、采购部、总经理都审批通过
- 行为：所有审批人都通过后，流程才流转到下一节点

### 4. 超时处理（Timeout Handling）

| 超时动作 | 说明 | 适用场景 |
|---------|------|---------|
| AUTO_APPROVE | 自动通过 | 日常审批，防止流程阻塞 |
| AUTO_REJECT | 自动驳回 | 时效性要求高的业务 |
| REMIND | 发送提醒 | 重要审批，仅提醒不自动处理 |

**配置示例**：
```json
{
  "nodeType": "APPROVAL",
  "timeoutHours": 24,
  "timeoutAction": "REMIND"
}
```

**超时处理机制**：
- 定时任务每分钟扫描一次超时任务
- 超时后根据配置自动执行相应动作
- 记录超时处理日志，便于审计

---

## 💡 使用示例

### 示例 1：请假审批流程

**业务需求**：
1. 员工发起请假申请
2. 直属上级审批（24小时内，超时自动提醒）
3. 请假天数 > 3 天需要部门经理审批
4. 审批通过后流程结束

**流程定义 JSON**：
```json
{
  "workflowCode": "leave_approval",
  "workflowName": "请假审批流程",
  "dagJson": {
    "nodes": [
      {
        "nodeId": "start",
        "nodeType": "START",
        "nodeName": "开始"
      },
      {
        "nodeId": "direct_manager",
        "nodeType": "APPROVAL",
        "nodeName": "直属上级审批",
        "approverType": "SUPERIOR",
        "approvalStrategy": "OR",
        "timeoutHours": 24,
        "timeoutAction": "REMIND"
      },
      {
        "nodeId": "condition_days",
        "nodeType": "CONDITION",
        "nodeName": "判断请假天数",
        "conditionExpression": "#days > 3"
      },
      {
        "nodeId": "dept_manager",
        "nodeType": "APPROVAL",
        "nodeName": "部门经理审批",
        "approverType": "ROLE",
        "approverValue": "DEPT_MANAGER",
        "approvalStrategy": "OR",
        "timeoutHours": 48,
        "timeoutAction": "REMIND"
      },
      {
        "nodeId": "end",
        "nodeType": "END",
        "nodeName": "结束"
      }
    ],
    "edges": [
      {
        "edgeId": "e1",
        "sourceNodeId": "start",
        "targetNodeId": "direct_manager"
      },
      {
        "edgeId": "e2",
        "sourceNodeId": "direct_manager",
        "targetNodeId": "condition_days"
      },
      {
        "edgeId": "e3",
        "sourceNodeId": "condition_days",
        "targetNodeId": "dept_manager",
        "conditionExpression": "#days > 3"
      },
      {
        "edgeId": "e4",
        "sourceNodeId": "condition_days",
        "targetNodeId": "end",
        "conditionExpression": "#days <= 3"
      },
      {
        "edgeId": "e5",
        "sourceNodeId": "dept_manager",
        "targetNodeId": "end"
      }
    ]
  }
}
```

**Java 代码**：
```java
// 1. 发起请假申请
String contextJson = "{\"days\": 5, \"reason\": \"家庭原因\", \"startDate\": \"2026-09-10\"}";
ProcessInstance instance = workflowService.startProcessInstance(
    "leave_approval",
    "LEAVE-2026-001",
    10001L,  // 发起人ID
    contextJson
).block();

// 2. 直属上级审批
Long taskId = workflowService.getPendingTasks(10002L).block().get(0).getId();
workflowService.completeTask(taskId, 10002L, "同意请假").block();

// 3. 部门经理审批
Long managerTaskId = workflowService.getPendingTasks(10003L).block().get(0).getId();
workflowService.completeTask(managerTaskId, 10003L, "批准").block();
```

### 示例 2：采购审批流程（会签）

**业务需求**：
1. 员工发起采购申请
2. 财务部、采购部、总经理必须全部审批通过（会签）
3. 任何一方驳回则流程结束

**流程定义 JSON**：
```json
{
  "workflowCode": "purchase_approval",
  "workflowName": "采购审批流程",
  "dagJson": {
    "nodes": [
      {
        "nodeId": "start",
        "nodeType": "START"
      },
      {
        "nodeId": "multi_approval",
        "nodeType": "APPROVAL",
        "nodeName": "财务、采购、总经理会签",
        "approverType": "ROLE",
        "approverValue": "FINANCE,PURCHASE,GM",
        "approvalStrategy": "AND",
        "timeoutHours": 72,
        "timeoutAction": "REMIND"
      },
      {
        "nodeId": "end",
        "nodeType": "END"
      }
    ],
    "edges": [
      {"sourceNodeId": "start", "targetNodeId": "multi_approval"},
      {"sourceNodeId": "multi_approval", "targetNodeId": "end"}
    ]
  }
}
```

---

## 🔧 高级功能

### 1. 流程回退

**使用场景**：审批人发现材料不全，驳回给发起人补充材料

**API 调用**：
```java
workflowService.rollbackToNode(
    instanceId,
    "start_node",  // 回退到开始节点
    "材料不全，请补充"
).block();
```

### 2. 任务转办

**使用场景**：审批人临时出差，将任务转办给其他人

**API 调用**：
```java
workflowService.transferTask(
    taskId,
    20001L,  // 目标用户ID
    "本人出差，转办给张三处理"
).block();
```

### 3. 任务委派

**使用场景**：审批人不确定，委派给专家评审后再决策

**委派与转办的区别**：
- **转办**：任务完全转移给其他人，原审批人不再参与
- **委派**：被委派人完成后，任务回到委派人手中继续审批

**API 调用**：
```java
workflowService.delegateTask(
    taskId,
    30001L,  // 被委派人ID
    "需要技术专家评审"
).block();
```

### 4. 流程挂起

**使用场景**：等待外部系统响应，暂停流程

**API 调用**：
```java
// 挂起流程
workflowService.suspendInstance(
    instanceId,
    "等待供应商报价"
).block();

// 恢复流程
workflowService.resumeInstance(instanceId).block();
```

---

## 📊 监控指标

### 1. Prometheus 指标

| 指标名称 | 类型 | 说明 |
|---------|------|------|
| `apex.workflow.process.active` | Gauge | 运行中流程数 |
| `apex.workflow.process.completed.total` | Counter | 累计完成流程数 |
| `apex.workflow.task.pending` | Gauge | 待办任务总数 |
| `apex.workflow.task.overdue` | Gauge | 超时任务数 |
| `apex.workflow.process.duration.seconds` | Histogram | 流程执行时长分布 |

### 2. Grafana 查询示例

```promql
# 流程完成率
rate(apex_workflow_process_completed_total[5m]) / rate(apex_workflow_process_started_total[5m])

# 平均流程执行时长（P95）
histogram_quantile(0.95, rate(apex_workflow_process_duration_seconds_bucket[5m]))

# 超时任务告警
apex_workflow_task_overdue > 10
```

---

## ⚠️ 最佳实践

### 1. 流程设计

✅ **推荐做法**：
- 流程节点不超过 10 个（保持简洁）
- 条件表达式使用 SpEL，避免复杂逻辑
- 超时时间根据业务实际情况合理配置
- 使用会签时考虑人员变动场景

❌ **避免做法**：
- 流程图出现环路（系统会拒绝）
- 流程上下文存储大对象（建议 < 10KB）
- 所有节点都设置超时自动通过（失去审批意义）

### 2. 性能优化

**数据库索引**：
- 已在 V21 迁移脚本中创建关键索引
- 定期检查慢查询日志

**缓存策略**：
- 流程定义缓存（Caffeine 60 秒）
- 用户待办任务计数缓存（Redis 30 秒）

**批量处理**：
- 使用 `Flux.buffer()` 批量处理任务
- 避免循环调用 API

### 3. 安全考虑

- 验证审批人权限（防止越权审批）
- 记录完整的审批日志（审计追踪）
- 敏感数据加密存储（如薪资审批）
- 防止 CSRF 攻击（使用 Anti-Replay 机制）

---

## 🐛 常见问题

### Q1: 流程启动失败，提示"流程定义不存在"

**原因**：`workflowCode` 不正确或流程定义未启用

**解决**：
```java
// 检查流程定义是否存在
WorkflowDefinition def = workflowService.getWorkflowDefinitionByCode("leave_approval").block();
if (def == null) {
    throw new BusinessException("流程定义不存在");
}
```

### Q2: 待办任务查询很慢

**原因**：缺少索引或数据量过大

**解决**：
1. 确认已执行 V21 迁移脚本（创建索引）
2. 定期归档历史数据
3. 使用分页查询

### Q3: 超时任务未自动处理

**原因**：超时处理定时任务未启动

**检查**：
```bash
# 查看定时任务日志
grep "WorkflowTimeoutHandler" logs/apex-scaffold.log
```

---

## 🔗 参考资料

### 项目文档
- [工作流实现方案](../../_archive/reports/workflow-implementation.md)
- [工作流进度报告](../../_archive/reports/workflow-progress.md)
- [后端开发规范](../../04-development/development-standards.md)

### 相关代码
- 服务类：`WorkflowService.java`, `WorkflowQueryService.java`, `WorkflowStatisticsService.java`
- 领域模型：`WorkflowDefinitionAggregate.java`, `ProcessInstanceEntity.java`, `TaskEntity.java`
- 数据库迁移：`V20__create_workflow_engine_tables.sql`, `V21__enhance_workflow_tables.sql`

---

**文档版本**: v1.0.0  
**创建日期**: 2026-09-04  
**维护团队**: Apex Team  
**最后更新**: 2026-09-04
