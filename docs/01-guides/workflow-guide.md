> **专题架构图**：[工作流引擎生命周期](../diagrams/60_workflow_engine_lifecycle.svg)

# 轻量工作流引擎使用指南

> **Enterprise License 特性** - 仅 Enterprise 版本可用
>
> **微服务化**：自 1.3.1 起工作流能力已独立为微服务 `apex-workflow-service`（端口 `8093`，独立库 `apex_workflow`）。
> 对外接口契约保持不变，统一经网关（端口 `8080`）路由：`/admin-api/v1/workflows/**`、`/api/v1/workflows/**`、`/api/workflow/**`。
> 独立服务构建：`mvnd -pl :apex-workflow-service -am package`。

## 1. 概述

Apex Scaffold 内置轻量级工作流引擎，支持基于 DAG（有向无环图）的流程定义与状态机驱动的任务流转。适用于审批流、订单处理、数据审核等业务场景。

工作流能力由独立服务 `apex-workflow-service` 提供，服务名 = Nacos 注册名 = 网关路由 `lb://apex-workflow-service` 的目标名，使用独立数据库 `apex_workflow` 与独立 Flyway 版本链（自 V1 起，见 §5）。

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

> 以下示例统一经网关（默认 `http://localhost:8080`）访问；工作流服务直连端口为 `8093`（仅内网/调试用）。

### 2.1 发起流程实例

```bash
curl -X POST http://localhost:8080/api/v1/workflows/leave_approval/instances \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '{
    "businessKey": "LEAVE-2026-001",
    "formData": "{\"days\": 3, \"reason\": \"家庭事务\"}"
  }'
```

### 2.2 查询待办任务

```bash
curl -X GET "http://localhost:8080/api/v1/workflows/tasks/pending?pageNum=1&pageSize=10" \
  -H "Authorization: Bearer ${TOKEN}"
```

### 2.3 办理完成任务

```bash
curl -X POST http://localhost:8080/api/v1/workflows/tasks/1/complete \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '{
    "result": "APPROVED",
    "comment": "同意请假"
  }'
```

### 2.4 管理端查询实例

```bash
curl -X GET "http://localhost:8080/admin-api/v1/workflows/instances?pageNum=1&pageSize=10&status=RUNNING" \
  -H "Authorization: Bearer ${TOKEN}"
```

## 3. 领域模型

### 3.1 核心实体

```
WorkflowDefinition（流程定义）→ 表 wf_workflow_definition
  ├── id: Long
  ├── workflowCode: String        // 流程编码（同一编码可多版本）
  ├── workflowName: String
  ├── nodeDefinition: String (JSON)  // 节点定义 DAG
  └── version: Integer            // 流程版本号（非乐观锁）

ProcessInstance（流程实例）→ 表 wf_process_instance
  ├── id: Long
  ├── workflowDefinitionId: Long
  ├── businessKey: String
  ├── currentNodeKey: String
  ├── status: InstanceStatus      // RUNNING/SUSPENDED/COMPLETED/REJECTED/TERMINATED
  ├── initiatorId: Long
  ├── formData: String (JSON)     // 表单数据（流程变量）
  ├── suspended: Boolean
  └── startTime / endTime

WorkflowTask（任务）→ 表 wf_task
  ├── id: Long
  ├── processInstanceId: Long
  ├── nodeCode: String
  ├── taskName: String
  ├── assigneeId: Long
  ├── status: TaskStatus          // PENDING/COMPLETED/REJECTED/ROLLED_BACK/DELEGATED/TRANSFERRED/CANCELLED
  ├── dueTime: LocalDateTime
  ├── completeTime: LocalDateTime
  └── timeoutAt / timeoutStrategy / timeoutHandled

ApprovalRecord（审批记录）→ 表 wf_approval_record
  ├── id: Long
  ├── processInstanceId: Long
  ├── taskId: Long
  ├── nodeCode: String
  ├── approverId: Long
  ├── action: String              // APPROVE/REJECT/TRANSFER/DELEGATE/ROLLBACK
  └── opinion: String
```

### 3.2 状态与动作枚举

**InstanceStatus**（流程实例状态）
- `RUNNING` - 运行中
- `SUSPENDED` - 已挂起（被暂停，不能继续流转）
- `COMPLETED` - 已完成
- `REJECTED` - 已驳回（流程被审批人驳回而结束）
- `TERMINATED` - 已终止

**TaskStatus**（任务状态）
- `PENDING` - 待处理
- `COMPLETED` - 已完成
- `REJECTED` - 已驳回
- `ROLLED_BACK` - 已回退
- `DELEGATED` - 已委派
- `TRANSFERRED` - 已转办
- `CANCELLED` - 已取消

**ApprovalAction**（审批动作）
- `APPROVE` - 同意
- `REJECT` - 驳回
- `TRANSFER` - 转办
- `DELEGATE` - 委派
- `ROLLBACK` - 回退

**TimeoutStrategy**（超时策略）
- `ESCALATE` - 升级（任务超时后升级给上级或管理员）
- `AUTO_COMPLETE` - 自动完成（任务超时后自动通过）
- `NOTIFY` - 通知（任务超时后仅发送提醒）

## 4. API 参考

> 下列路径为工作流服务对外契约（经网关透传），完整定义见 `WorkflowRouter`（`apex-workflow-service`）。`Authorization` 为除公开路径外必填。

### 4.1 管理端查询（`/admin-api/v1/workflows`）

| Method | Path | 说明 |
|--------|------|------|
| GET | `/admin-api/v1/workflows/instances` | 分页查询流程实例列表（支持 `status` 等筛选） |
| GET | `/admin-api/v1/workflows/instances/{instanceId}` | 查询流程实例详情 |
| GET | `/admin-api/v1/workflows/statistics` | 查询工作流统计概览 |
| GET | `/admin-api/v1/workflows/statistics/trend` | 查询工作流趋势数据（`days`，默认 7） |

### 4.2 业务流程（`/api/v1/workflows`）

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/v1/workflows/{workflowCode}/instances` | 发起流程实例 |
| GET | `/api/v1/workflows/tasks/pending` | 查询当前用户待办任务列表 |
| POST | `/api/v1/workflows/tasks/{taskId}/complete` | 办理完成任务 |

### 4.3 任务/实例操作（`/api/workflow`）

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/workflow/tasks/{taskId}/rollback` | 回退任务到指定节点 |
| POST | `/api/workflow/tasks/{taskId}/transfer` | 转办任务 |
| POST | `/api/workflow/tasks/{taskId}/delegate` | 委派任务（协办） |
| POST | `/api/workflow/instances/{instanceId}/suspend` | 挂起流程实例 |
| POST | `/api/workflow/instances/{instanceId}/resume` | 恢复流程实例 |

## 5. 数据库结构

工作流服务使用独立数据库 `apex_workflow`，由独立 Flyway 链（`db/migration/{mysql,postgresql}/V1__init_workflow_schema.sql`）维护，与旧单体版本链解耦（自 V1 重启，见 `microservice-migration-plan.md` §9.3）。共 4 张表（列名与 PO 类字段严格对应）：

```sql
-- 工作流定义表
CREATE TABLE wf_workflow_definition (
    id BIGINT NOT NULL,                       -- 主键ID（雪花算法）
    tenant_id VARCHAR(64) NOT NULL,           -- 租户ID（多租户行级隔离）
    workflow_code VARCHAR(100) NOT NULL,      -- 流程编码（同一编码可多版本）
    workflow_name VARCHAR(200) NOT NULL,      -- 流程名称
    description VARCHAR(500),                 -- 流程描述
    node_definition TEXT,                     -- 节点定义JSON（DAG）
    version INT NOT NULL DEFAULT 1,           -- 流程版本号
    enabled INT NOT NULL DEFAULT 1,           -- 启用标志（1-启用 0-停用）
    remark VARCHAR(500),                      -- 备注
    create_time DATETIME NOT NULL,            -- 创建时间
    update_time DATETIME NOT NULL,            -- 更新时间
    deleted TINYINT NOT NULL DEFAULT 0,       -- 逻辑删除标识
    PRIMARY KEY (id),
    UNIQUE KEY uk_wf_def_code_version (workflow_code, version, deleted)
);

-- 流程实例表
CREATE TABLE wf_process_instance (
    id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    workflow_definition_id BIGINT NOT NULL,   -- 工作流定义ID
    business_key VARCHAR(200) NOT NULL,       -- 业务主键（外部关联键）
    current_node_key VARCHAR(100),            -- 当前节点KEY
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING', -- RUNNING/SUSPENDED/COMPLETED/REJECTED/TERMINATED
    initiator_id BIGINT NOT NULL,             -- 发起人ID
    form_data TEXT,                           -- 表单数据JSON（流程变量）
    start_time DATETIME NOT NULL,
    end_time DATETIME,
    remark VARCHAR(500),                      -- 备注（复用为流程标题）
    suspended TINYINT NOT NULL DEFAULT 0,     -- 是否挂起（1-是 0-否）
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wf_instance_business_key (business_key, deleted)
);

-- 流程任务表
CREATE TABLE wf_task (
    id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    process_instance_id BIGINT NOT NULL,      -- 流程实例ID
    node_code VARCHAR(100) NOT NULL,          -- 节点编码
    task_name VARCHAR(200) NOT NULL,          -- 任务名称（节点展示名）
    assignee_id BIGINT NOT NULL,              -- 待办人用户ID
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    due_time DATETIME,                        -- 处理截止时间
    complete_time DATETIME,                   -- 完成时间
    remark VARCHAR(500),
    timeout_at DATETIME,                      -- 超时时间点
    timeout_strategy VARCHAR(50),             -- ESCALATE/AUTO_COMPLETE/NOTIFY
    timeout_handled TINYINT NOT NULL DEFAULT 0,
    transferred_from BIGINT,                  -- 转办来源用户ID
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- 审批记录表
CREATE TABLE wf_approval_record (
    id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    process_instance_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,                  -- 关联任务ID
    node_code VARCHAR(100),
    approver_id BIGINT,                       -- 操作人用户ID
    action VARCHAR(20),                       -- APPROVE/REJECT/TRANSFER/DELEGATE/ROLLBACK
    opinion VARCHAR(1000),                    -- 处理意见
    remark VARCHAR(500),
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
```

> 注：`wf_process_instance` / `wf_task` / `wf_approval_record` 不含乐观锁 `version` 列（工作流服务未加载 `apex-infrastructure` 乐观锁切面）；`wf_workflow_definition.version` 为流程版本号，非乐观锁。

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
- **明确责任**：每个任务应有明确的负责人（`assignee_id`）
- **超时控制**：为关键任务设置 `due_time`，防止流程阻塞
- **表单传递**：通过 `formData` 字段携带流程上下文，避免重复查询

### 7.2 性能优化

- **批量查询**：使用 `GET /api/v1/workflows/tasks/pending` 一次获取当前用户待办任务
- **实例查询**：使用 `GET /admin-api/v1/workflows/statistics`、`/statistics/trend` 获取统计与趋势数据
- **索引优化**：为 `business_key`、`assignee_id`、`status` 字段创建索引
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
A: 检查 `wf_workflow_definition.node_definition`（节点 DAG JSON）中流转条件表达式与优先级配置是否正确。

**Q: 流程实例一直卡在 RUNNING 状态？**
A: 检查是否有任务状态为 `PENDING` 且 `assignee_id` 无效，建议为关键任务设置 `timeout_strategy` 超时自动处理。

**Q: 表单数据 JSON 解析失败？**
A: 确保 `wf_process_instance.form_data`（PO 字段 `formData`）存储的是合法 JSON 字符串，避免特殊字符未转义。

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
