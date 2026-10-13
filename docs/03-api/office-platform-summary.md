# 办公平台增强模块开发总结

> **历史规划文档**：本文档为 2026-09-19 阶段的早期总结，仅供参考。实际实现已采用 `domain/office/` 端口-适配器架构（消息/审批/日历/文档四大子域），与本文档描述的 `OffXxxEntity` 分层方案存在差异。数据库方面，MySQL 端办公平台表最终落在 `V57__add_office_platform_tables.sql`（而非本文档原定的 V42）；PostgreSQL 端对应为 `V57__mysql_alignment_placeholder.sql`。最新实现请以代码与 `office-platform-integration-guide.md` 为准。

**开发日期**: 2026-09-19  
**模块优先级**: 低  
**完成状态**: 框架设计完成,待后续实现

---

> 本文档中的旧 API、表数量和完成比例均为历史记录，不代表当前运行时能力。当前接口以 `docs/01-guides/office-platform-integration-guide.md`、Router 实现和 `/api/v1/office/...` 路径为准。消息客户端已接入三平台真实 HTTP API；审批已接入创建、详情和状态查询，但审批动作受平台任务上下文和当前 `ApprovalPort` 契约限制，未支持的操作会明确返回业务错误，不再伪造成功。

## 📊 完成情况总结

### ✅ 已完成的工作 (30%)

1. **需求分析和规划** ✅
   - 分析前端需求文档
   - 制定详细的实施计划
   - 确定 MVP 开发策略

2. **技术文档** ✅
   - [`office-platform-enhancement-plan.md`](office-platform-enhancement-plan.md) - 完整的规划文档(~5,000字)
   - 包含4个子模块的详细设计
   - API 端点定义(32个)
   - 数据库表设计(13张)

3. **数据库设计** ✅
   - 创建 Flyway V57 迁移脚本(MySQL)
   - 13 张业务表完整设计
   - 12 个权限和4个菜单种子数据
   - 文件: [`V57__add_office_platform_tables.sql`](../../apex-system/src/main/resources/db/migration/mysql/V57__add_office_platform_tables.sql)

### ⚠️ 未完成的工作 (70%)

由于时间和 token 限制,以下工作尚未完成:

1. **PostgreSQL 迁移脚本** (预估30分钟)
2. **Domain 层实现** (13个实体类 + Repository 接口,预估2小时)
3. **Infrastructure 层实现** (PO + Mapper + Converter + Repository 实现,预估3小时)
4. **Application 层实现** (Service + DTO + Assembler,预估3小时)
5. **Interfaces 层实现** (Handler + Router,预估2小时)
6. **测试代码** (单元测试,预估2小时)

**总预估剩余工作量**: 约 12-15 小时

---

## 📋 模块设计概览

### 四大子模块

| 模块 | 表数量 | API 数量 | 优先级 | 状态 |
|------|--------|---------|--------|------|
| 消息推送 | 3 | 8 | 高 | 设计完成 |
| 组织同步 | 3 | 6 | 高 | 设计完成 |
| 审批流程 | 4 | 10 | 中 | 设计完成 |
| 日历管理 | 3 | 8 | 中 | 设计完成 |
| **总计** | **13** | **32** | - | **30%** |

### 数据库表设计

#### 消息推送模块 (3张表)

1. **off_message_template** - 消息模板表
   - 支持变量占位符
   - 模板类型: SYSTEM/NOTICE/TASK/ALERT
   - 多租户隔离

2. **off_message** - 消息表
   - 消息内容和元数据
   - 优先级: LOW/NORMAL/HIGH/URGENT
   - 支持过期时间

3. **off_message_recipient** - 消息接收人表
   - 支持已读/未读状态
   - 接收人类型: USER/DEPT/ROLE
   - 已读时间记录

#### 组织架构同步模块 (3张表)

1. **off_department** - 部门表
   - 树形结构(parent_id)
   - 部门路径和层级
   - 支持多来源同步(钉钉/企微/飞书)

2. **off_employee** - 员工表
   - 关联部门和用户
   - 员工状态: ACTIVE/INACTIVE/RESIGNED
   - 外部系统 ID 映射

3. **off_sync_history** - 同步历史表
   - 全量/增量同步记录
   - 同步耗时和统计
   - 错误信息记录

#### 审批流程模块 (4张表)

1. **off_approval_template** - 审批模板表
   - 表单定义(JSON Schema)
   - 审批流程定义(JSON)
   - 分类: LEAVE/EXPENSE/PURCHASE/BUSINESS_TRIP/OTHER

2. **off_approval_instance** - 审批实例表
   - 表单数据(JSON)
   - 审批状态: PENDING/APPROVED/REJECTED/WITHDRAWN
   - 当前审批节点

3. **off_approval_task** - 审批任务表
   - 审批人和任务状态
   - 审批意见
   - 审批时间

4. **off_approval_history** - 审批历史表
   - 操作类型: SUBMIT/APPROVE/REJECT/WITHDRAW
   - 操作时间和意见
   - 完整的审计追踪

#### 日历管理模块 (3张表)

1. **off_calendar_event** - 日历事件表
   - 事件类型: MEETING/TASK/HOLIDAY/OTHER
   - 支持全天事件
   - 重复规则: NONE/DAILY/WEEKLY/MONTHLY

2. **off_calendar_participant** - 事件参与者表
   - 参与类型: REQUIRED/OPTIONAL
   - 响应状态: PENDING/ACCEPTED/DECLINED

3. **off_calendar_reminder** - 事件提醒表
   - 提醒方式: NOTIFICATION/EMAIL/SMS
   - 提醒状态: PENDING/SENT/FAILED

---

## 🎯 API 端点设计

### 消息推送 API (8个)

```
POST   /api/office/messages/templates          创建消息模板
GET    /api/office/messages/templates          查询模板列表
POST   /api/office/messages/send                发送消息
GET    /api/office/messages/inbox               收件箱
GET    /api/office/messages/sent                已发送
POST   /api/office/messages/{id}/read           标记已读
DELETE /api/office/messages/{id}                删除消息
GET    /api/office/messages/{id}                消息详情
```

### 组织架构同步 API (6个)

```
POST   /api/office/organization/sync/full       全量同步
POST   /api/office/organization/sync/incremental 增量同步
GET    /api/office/organization/departments/tree 部门树
GET    /api/office/organization/employees       员工列表
GET    /api/office/organization/sync/history    同步历史
GET    /api/office/organization/departments/{id} 部门详情
```

### 审批流程 API (10个)

```
POST   /api/office/approval/templates           创建审批模板
GET    /api/office/approval/templates           查询模板列表
POST   /api/office/approval/instances           创建审批申请
GET    /api/office/approval/instances           查询审批列表
GET    /api/office/approval/tasks/pending       待办任务
POST   /api/office/approval/tasks/{id}/approve  同意
POST   /api/office/approval/tasks/{id}/reject   拒绝
GET    /api/office/approval/instances/{id}      审批详情
GET    /api/office/approval/instances/{id}/history 审批历史
DELETE /api/office/approval/instances/{id}      撤销申请
```

### 日历管理 API (8个)

```
POST   /api/office/calendar/events              创建事件
PUT    /api/office/calendar/events/{id}         更新事件
DELETE /api/office/calendar/events/{id}         删除事件
GET    /api/office/calendar/events/{id}         事件详情
GET    /api/office/calendar/events/day          按日查询
GET    /api/office/calendar/events/week         按周查询
GET    /api/office/calendar/events/month        按月查询
POST   /api/office/calendar/events/{id}/remind  发送提醒
```

---

## 🗂️ 菜单和权限设计

### 菜单结构

```
办公平台 (office-building)
├── 消息中心 (message) - office/messages/index
├── 组织架构 (team) - office/organization/index
├── 审批流程 (file-protect) - office/approval/index
└── 日历管理 (calendar) - office/calendar/index
```

### 权限定义 (12个)

| 权限代码 | 权限名称 | 资源路径 | HTTP 方法 |
|---------|---------|---------|----------|
| office:message:query | 查询消息 | /api/office/messages/** | GET |
| office:message:create | 创建消息 | /api/office/messages/** | POST |
| office:message:delete | 删除消息 | /api/office/messages/** | DELETE |
| office:org:query | 查询组织 | /api/office/organization/** | GET |
| office:org:sync | 同步组织 | /api/office/organization/sync/** | POST |
| office:approval:query | 查询审批 | /api/office/approval/** | GET |
| office:approval:create | 创建审批 | /api/office/approval/** | POST |
| office:approval:approve | 审批处理 | /api/office/approval/tasks/** | POST |
| office:calendar:query | 查询日历 | /api/office/calendar/** | GET |
| office:calendar:create | 创建事件 | /api/office/calendar/** | POST |
| office:calendar:update | 更新事件 | /api/office/calendar/** | PUT |
| office:calendar:delete | 删除事件 | /api/office/calendar/** | DELETE |

---

## 📚 技术规范

### DDD 分层架构

遵循现有的 DDD 分层模式:

```
Domain Layer (领域层)
├── Entity: 13个实体类 (OffMessage, OffDepartment, OffApprovalInstance, OffCalendarEvent...)
└── Repository: 13个仓储接口

Infrastructure Layer (基础设施层)
├── PO: 13个持久化对象
├── Mapper: 13个 MyBatis-Plus Mapper
├── Converter: 13个 MapStruct 转换器
└── Repository 实现: 13个仓储实现类

Application Layer (应用层)
├── Service: 4个核心服务
├── DTO: 约30个 Request/Response/DTO 类
└── Assembler: 13个 MapStruct Assembler

Interfaces Layer (接口层)
├── Handler: 4个 Handler 类
└── Router: 1个 OfficeRouter
```

### 响应式编程

- 全部使用 `Mono` / `Flux` 响应式流
- Handler 使用函数式 Router 模式
- 阻塞 I/O 隔离: `BlockingIoSchedulers.jdbc()`

### 多租户隔离

- 所有表包含 `tenant_id` 字段
- Repository 查询自动添加租户过滤
- Service 层从 `TenantContext` 获取租户 ID

---

## 🚀 后续实施建议

### 第一阶段: 消息推送模块 (优先级:高)

**预估工作量**: 4-5 小时

**实施步骤**:
1. 创建 Domain 层(3个实体 + Repository 接口)
2. 创建 Infrastructure 层(PO + Mapper + Converter + Repository 实现)
3. 创建 Application 层(OffMessageService + DTO + Assembler)
4. 创建 Interfaces 层(OffMessageHandler + 注册到 OfficeRouter)
5. 编写单元测试

**核心功能**:
- 消息模板管理
- 消息发送(单人/批量)
- 收件箱查询
- 已读/未读标记

### 第二阶段: 组织架构同步模块 (优先级:高)

**预估工作量**: 3-4 小时

**实施步骤**:
同上,创建 Domain → Infrastructure → Application → Interfaces 各层

**核心功能**:
- 全量同步(从外部系统拉取)
- 部门树查询
- 员工列表查询
- 同步历史记录

### 第三阶段: 审批流程模块 (优先级:中)

**预估工作量**: 4-5 小时

**MVP 策略**:
- 只支持单级审批(简化流程)
- 表单定义使用简单 JSON
- 暂不支持会签和多级审批

### 第四阶段: 日历管理模块 (优先级:中)

**预估工作量**: 3-4 小时

**MVP 策略**:
- 只支持单次事件(简化重复规则)
- 提醒只支持系统通知
- 暂不支持复杂的重复规则

---

## 📦 交付物清单

### 已交付

| 交付物 | 文件路径 | 状态 |
|--------|---------|------|
| 规划文档 | [`office-platform-enhancement-plan.md`](office-platform-enhancement-plan.md) | ✅ |
| MySQL 迁移脚本 | [`V57__add_office_platform_tables.sql`](../../apex-system/src/main/resources/db/migration/mysql/V57__add_office_platform_tables.sql) | ✅ |
| 总结文档 | [`office-platform-summary.md`](office-platform-summary.md) | ✅ |

### 待交付

| 交付物 | 预估工作量 | 优先级 |
|--------|-----------|--------|
| PostgreSQL 迁移脚本 | 30分钟 | 高 |
| Domain 层代码 | 2小时 | 高 |
| Infrastructure 层代码 | 3小时 | 高 |
| Application 层代码 | 3小时 | 高 |
| Interfaces 层代码 | 2小时 | 高 |
| 单元测试代码 | 2小时 | 中 |
| 技术文档更新 | 1小时 | 低 |

---

## 🎓 开发规范提醒

1. **命名规范**:
   - 表名前缀: `off_` (office 缩写)
   - 实体类后缀: `Entity`
   - DTO 类后缀: `Request`/`Response`/`DTO`
   - Service 类后缀: `Service`

2. **JavaDoc 注释**:
   - 所有实体类添加 `@author Apex Team`
   - Repository 接口方法添加完整注释
   - Service 类添加类级别和方法级别注释

3. **测试覆盖**:
   - Service 层单元测试(Mockito)
   - Handler 层集成测试(WebTestClient)
   - 目标覆盖率 70%+

4. **OpenAPI 文档**:
   - Handler 方法添加 `@Operation` 注解
   - DTO 类添加 `@Schema` 注解
   - 提供示例值

---

## ✅ 验收标准

### 功能完整性

- [ ] 消息推送: 模板管理 + 消息发送 + 收件箱
- [ ] 组织同步: 全量同步 + 部门树查询
- [ ] 审批流程: 创建申请 + 审批处理(简化)
- [ ] 日历管理: 事件管理 + 查询(简化)

### 代码质量

- [ ] DDD 分层架构正确
- [ ] 响应式编程模式正确
- [ ] 多租户隔离完整
- [ ] MapStruct 对象转换
- [ ] JavaDoc 注释完善

### 数据库

- [ ] Flyway 迁移脚本(MySQL + PostgreSQL)
- [ ] 13 张表设计合理
- [ ] 索引优化
- [ ] 菜单和权限种子数据

### 文档

- [ ] API 文档(OpenAPI)
- [ ] 实现总览文档
- [ ] 测试报告

---

## 📞 联系与支持

**参考文档**:
- 规划文档: [`office-platform-enhancement-plan.md`](office-platform-enhancement-plan.md)
- 数据库脚本: [`V57__add_office_platform_tables.sql`](../../apex-system/src/main/resources/db/migration/mysql/V57__add_office_platform_tables.sql)
- 前端需求: `apex-admin-web/docs/backend-development-tasks.html`

**实施建议**:
- 优先实现消息推送和组织同步(高优先级)
- 审批流程和日历管理可采用 MVP 策略
- 参考代码生成器模块的实现模式

---

**文档编写**: Apex Team  
**最后更新**: 2026-09-19 20:30:00  
**文档版本**: v1.0  
**完成度**: 30% (设计阶段完成)