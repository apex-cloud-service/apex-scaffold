# 轻量工作流引擎使用指南

> **Enterprise License 特性** - 仅 Enterprise 版本可用

## 1. 概述

Apex Scaffold 内置轻量级工作流引擎，支持基于 DAG（有向无环图）的流程定义与状态机驱动的任务流转。适用于审批流、订单处理、数据审核等业务场景。

### 1.1 核心特性

- **DAG 流程定义**：支持串行、并行、条件分支的任务编排
- **状态机驱动**：任务状态自动流转（PENDING → IN_PROGRESS → COMPLETED/REJECTED）
- **多种任务类型**：人工审批、自动任务、并行网关、排他网关
- **条件路由**：支持表达式、脚本、规则引擎三种条件判断方式
- **流程追踪**：完整的任务历史和状态变更记录
- **License 门控**：通过 `@ConditionalOnLicenseFeature("workflow")` 自动启用

### 1.2 适用场景

| 场景 | 说明 |
|------|------|
| **审批流** | 请假申请、报销审批、合同审批等多级审批场景 |
| **订单处理** | 订单创建 → 支付 → 发货 → 签收的全流程管理 |
| **数据审核** | 内容发布、用户认证、资料审核等需要人工介入的流程 |
| **自动化任务** | 定时任务编排、数据同步流程、批处理任务 |

## 2. 快速开始

### 2.1 创建流程定义

```bash
curl -X POST http://localhost:8080/admin-api/v1/workflows/definitions \
  -H "Content-Type: application/json" \
  -d '{
    "name": "请假审批流程",
    "description": "员工请假申请审批",
    "config": "{\"timeout\": 86400}"
  }'
```

### 2.2 启动流程实例

```bash
curl -X POST http://localhost:8080/admin-api/v1/workflows/instances \
  -H "Content-Type: application/json" \
  -d '{
    "definitionId": 1,
    "businessKey": "LEAVE-2026-001",
    "variables": "{\"days\": 3, \"reason\": \"家庭事务\"}"
  }'
```

### 2.3 完成任务

```bash
curl -X POST http://localhost:8080/admin-api/v1/workflows/tasks/1/complete \
  -H "Content-Type: application/json" \
  -d '{
    "result": "APPROVED",
    "comment": "同意请假"
  }'
```

## 3. 领域模型

### 3.1 核心实体

```
WorkflowDefinition（流程定义）
  ├── id: Long
  ├── name: String
  ├── description: String
  ├── config: String (JSON)
  └── version: Integer

WorkflowInstance（流程实例）
  ├── id: Long
  ├── definitionId: Long
  ├── businessKey: String
  ├── status: WorkflowStatus
  ├── variables: String (JSON)
  └── startTime / endTime

WorkflowTask（任务）
  ├── id: Long
  ├── instanceId: Long
  ├── name: String
  ├── type: TaskType
  ├── status: TaskStatus
  ├── assignee: String
  └── dueDate: LocalDateTime

TaskTransition（任务流转）
  ├── id: Long
  ├── taskId: Long
  ├── fromStatus: TaskStatus
  ├── toStatus: TaskStatus
  ├── conditionType: TransitionConditionType
  ├── conditionExpression: String
  └── priority: Integer
```

### 3.2 状态枚举

**WorkflowStatus**（流程状态）
- `DRAFT` - 草稿
- `ACTIVE` - 运行中
- `COMPLETED` - 已完成
- `SUSPENDED` - 已暂停
- `TERMINATED` - 已终止

**TaskStatus**（任务状态）
- `PENDING` - 待处理
- `IN_PROGRESS` - 处理中
- `COMPLETED` - 已完成
- `REJECTED` - 已拒绝
- `CANCELLED` - 已取消

**TaskType**（任务类型）
- `USER_TASK` - 人工任务
- `SERVICE_TASK` - 自动任务
- `PARALLEL_GATEWAY` - 并行网关
- `EXCLUSIVE_GATEWAY` - 排他网关

**TransitionConditionType**（条件类型）
- `NONE` - 无条件（默认流转）
- `EXPRESSION` - SpEL 表达式
- `SCRIPT` - 脚本（Groovy/JavaScript）
- `RULE_ENGINE` - 规则引擎

**ApprovalResult**（审批结果）
- `APPROVED` - 通过
- `REJECTED` - 拒绝
- `RETURNED` - 退回

## 4. API 参考

### 4.1 流程定义管理

| Method | Path | 说明 |
|--------|------|------|
| POST | `/admin-api/v1/workflows/definitions` | 创建流程定义 |
| GET | `/admin-api/v1/workflows/definitions` | 查询流程定义列表 |
| GET | `/admin-api/v1/workflows/definitions/{id}` | 查询流程定义详情 |

### 4.2 流程实例管理

| Method | Path | 说明 |
|--------|------|------|
| POST | `/admin-api/v1/workflows/instances` | 启动流程实例 |
| GET | `/admin-api/v1/workflows/instances/{id}` | 查询流程实例详情 |
| GET | `/admin-api/v1/workflows/instances/{id}/tasks` | 查询实例的所有任务 |

### 4.3 任务管理

| Method | Path | 说明 |
|--------|------|------|
| POST | `/admin-api/v1/workflows/tasks/{id}/complete` | 完成任务 |
| GET | `/admin-api/v1/workflows/tasks/{id}` | 查询任务详情 |
| GET | `/admin-api/v1/workflows/tasks/{id}/transitions` | 查询任务流转记录 |

## 5. 数据库结构

```sql
-- 流程定义表
CREATE TABLE wf_definition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    config TEXT,
    version INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 流程实例表
CREATE TABLE wf_instance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    definition_id BIGINT NOT NULL,
    business_key VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    variables TEXT,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (definition_id) REFERENCES wf_definition(id)
);

-- 任务表
CREATE TABLE wf_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    instance_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    assignee VARCHAR(100),
    due_date TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (instance_id) REFERENCES wf_instance(id)
);

-- 任务流转表
CREATE TABLE wf_task_transition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    condition_type VARCHAR(50),
    condition_expression TEXT,
    priority INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (task_id) REFERENCES wf_task(id)
);
```

## 6. 配置说明

工作流引擎通过 License 特性门控自动启用，无需额外配置。确保 License 包含 `workflow` 特性：

```yaml
# application-enterprise.yml（自动生效）
apex:
  features:
    workflow: true  # Enterprise License 自动启用
```

## 7. 最佳实践

### 7.1 流程设计原则

- **最小化节点**：避免过度细化任务，保持流程简洁
- **明确责任**：每个任务应有明确的负责人（assignee）
- **超时控制**：为关键任务设置 dueDate，防止流程阻塞
- **变量传递**：使用 variables 字段存储流程上下文，避免重复查询

### 7.2 性能优化

- **批量查询**：使用 `GET /instances/{id}/tasks` 一次获取所有任务
- **索引优化**：为 `business_key`、`assignee`、`status` 字段创建索引
- **异步执行**：SERVICE_TASK 类型任务建议使用异步处理
- **状态缓存**：高频查询的流程状态可缓存到 Redis

### 7.3 监控指标

```yaml
# 建议监控的指标
- workflow_instances_active_total  # 运行中的流程实例数
- workflow_tasks_pending_total     # 待处理任务数
- workflow_tasks_overdue_total     # 超期任务数
- workflow_instances_completed_duration_seconds  # 流程完成耗时
```

## 8. 故障排查

### 8.1 常见问题

**Q: 任务状态未自动流转？**
A: 检查 `wf_task_transition` 表是否配置了正确的条件表达式和优先级。

**Q: 流程实例一直卡在 ACTIVE 状态？**
A: 检查是否有任务状态为 PENDING 且无 assignee，建议设置超时自动处理。

**Q: 变量 JSON 解析失败？**
A: 确保 variables 字段存储的是合法 JSON 字符串，避免特殊字符未转义。

### 8.2 日志查看

```bash
# 查看工作流相关日志
tail -f logs/apex-scaffold.log | grep "WorkflowService\|WorkflowHandler"
```

## 9. 未来扩展方向

- **可视化流程设计器**：拖拽式流程建模工具
- **外部系统集成**：支持调用第三方 API（如钉钉、企业微信）
- **分布式事务**：跨服务的流程编排与补偿
- **流程模板市场**：预置常见业务流程模板

---

**License**: Enterprise Only  
**Version**: 1.3.0  
**Support**: [Apex Scaffold 官方文档](https://apex-scaffold.com)
