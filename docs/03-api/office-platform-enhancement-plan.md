# 办公平台增强模块开发规划

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

> **历史规划文档**：本文档为 2026-09-19 阶段的早期规划，仅供参考。实际实现已采用 `domain/office/` 端口-适配器架构（消息/审批/日历/文档四大子域），与本文档描述的 `OffXxxEntity` 分层方案存在差异。数据库方面，MySQL 端办公平台表最终落在 `V57__add_office_platform_tables.sql`（而非本文档原定的 V42）；PostgreSQL 端对应为 `V57__mysql_alignment_placeholder.sql`。最新实现请以代码与 `office-platform-integration-guide.md` 为准。

**模块名称**: 办公平台集成增强  
**优先级**: 低  
**预估工作量**: 5-7 人天  
**开发策略**: 快速原型开发(MVP)

---

## 📋 功能概述

增强现有办公平台集成功能,完善消息推送、组织架构同步、审批流程、日历管理等功能。

---

## 🎯 四大子模块

> 以下 API 路径和数据表是早期规划内容，不是当前已注册接口。当前接口以 `docs/01-guides/office-platform-integration-guide.md`、Router 实现和 `/api/v1/office/...` 路径为准。

### 1. 消息推送模块 (Messages)

**API 路径**: `/api/office/messages`

**核心功能**:
- 消息模板管理(CRUD)
- 消息发送(单人/批量)
- 消息查询(收件箱/已发送)
- 消息已读/未读标记
- 消息删除(逻辑删除)

**数据库表**:
- `off_message_template` - 消息模板表
- `off_message` - 消息表
- `off_message_recipient` - 消息接收人表

**API 端点**(8个):
- POST `/api/office/messages/templates` - 创建消息模板
- GET `/api/office/messages/templates` - 查询模板列表
- POST `/api/office/messages/send` - 发送消息
- GET `/api/office/messages/inbox` - 收件箱
- GET `/api/office/messages/sent` - 已发送
- POST `/api/office/messages/{id}/read` - 标记已读
- DELETE `/api/office/messages/{id}` - 删除消息
- GET `/api/office/messages/{id}` - 消息详情

### 2. 组织架构同步模块 (Organization)

**API 路径**: `/api/office/organization`

**核心功能**:
- 组织架构全量同步
- 组织架构增量同步
- 部门树查询
- 员工列表查询
- 同步历史记录

**数据库表**:
- `off_department` - 部门表
- `off_employee` - 员工表
- `off_sync_history` - 同步历史表

**API 端点**(6个):
- POST `/api/office/organization/sync/full` - 全量同步
- POST `/api/office/organization/sync/incremental` - 增量同步
- GET `/api/office/organization/departments/tree` - 部门树
- GET `/api/office/organization/employees` - 员工列表
- GET `/api/office/organization/sync/history` - 同步历史
- GET `/api/office/organization/departments/{id}` - 部门详情

### 3. 审批流程模块 (Approval)

**API 路径**: `/api/office/approval`

**核心功能**:
- 审批流程模板管理
- 审批申请创建
- 审批任务处理(同意/拒绝)
- 审批状态查询
- 审批历史记录

**数据库表**:
- `off_approval_template` - 审批模板表
- `off_approval_instance` - 审批实例表
- `off_approval_task` - 审批任务表
- `off_approval_history` - 审批历史表

**API 端点**(10个):
- POST `/api/office/approval/templates` - 创建审批模板
- GET `/api/office/approval/templates` - 查询模板列表
- POST `/api/office/approval/instances` - 创建审批申请
- GET `/api/office/approval/instances` - 查询审批列表
- GET `/api/office/approval/tasks/pending` - 待办任务
- POST `/api/office/approval/tasks/{id}/approve` - 同意
- POST `/api/office/approval/tasks/{id}/reject` - 拒绝
- GET `/api/office/approval/instances/{id}` - 审批详情
- GET `/api/office/approval/instances/{id}/history` - 审批历史
- DELETE `/api/office/approval/instances/{id}` - 撤销申请

### 4. 日历管理模块 (Calendar)

**API 路径**: `/api/office/calendar`

**核心功能**:
- 日历事件创建
- 日历事件查询(按日/周/月)
- 日历事件提醒
- 日历事件参与者管理
- 事件重复规则(每日/每周/每月)

**数据库表**:
- `off_calendar_event` - 日历事件表
- `off_calendar_participant` - 事件参与者表
- `off_calendar_reminder` - 事件提醒表

**API 端点**(8个):
- POST `/api/office/calendar/events` - 创建事件
- PUT `/api/office/calendar/events/{id}` - 更新事件
- DELETE `/api/office/calendar/events/{id}` - 删除事件
- GET `/api/office/calendar/events/{id}` - 事件详情
- GET `/api/office/calendar/events/day` - 按日查询
- GET `/api/office/calendar/events/week` - 按周查询
- GET `/api/office/calendar/events/month` - 按月查询
- POST `/api/office/calendar/events/{id}/remind` - 发送提醒

---

## 📊 模块统计

| 模块 | 表数量 | API 数量 | 实体类 | 优先级 |
|------|--------|---------|--------|--------|
| 消息推送 | 3 | 8 | 3 | 高 |
| 组织同步 | 3 | 6 | 3 | 高 |
| 审批流程 | 4 | 10 | 4 | 中 |
| 日历管理 | 3 | 8 | 3 | 中 |
| **总计** | **13** | **32** | **13** | - |

---

## 🗂️ 文件结构

按照现有 DDD 分层架构:

```
apex-domain/
└── src/main/java/com/apex/scaffold/domain/
    ├── model/entity/
    │   ├── OffMessageEntity.java
    │   ├── OffMessageTemplateEntity.java
    │   ├── OffDepartmentEntity.java
    │   ├── OffEmployeeEntity.java
    │   ├── OffApprovalTemplateEntity.java
    │   ├── OffApprovalInstanceEntity.java
    │   ├── OffApprovalTaskEntity.java
    │   ├── OffCalendarEventEntity.java
    │   └── ...
    └── repository/
        ├── OffMessageRepository.java
        ├── OffDepartmentRepository.java
        ├── OffApprovalInstanceRepository.java
        └── ...

apex-infrastructure/
└── src/main/java/com/apex/scaffold/infrastructure/
    ├── persistence/
    │   ├── po/
    │   │   ├── OffMessagePO.java
    │   │   └── ...
    │   ├── mapper/
    │   │   ├── OffMessageMapper.java
    │   │   └── ...
    │   ├── converter/
    │   │   ├── OffMessagePoConverter.java
    │   │   └── ...
    │   └── repository/
    │       ├── OffMessageRepositoryImpl.java
    │       └── ...

apex-components-parent/apex-system/
└── src/main/java/com/apex/scaffold/application/
    ├── service/
    │   ├── OffMessageService.java
    │   ├── OffDepartmentService.java
    │   ├── OffApprovalService.java
    │   └── OffCalendarService.java
    ├── dto/
    │   ├── message/
    │   ├── organization/
    │   ├── approval/
    │   └── calendar/
    └── assembler/
        ├── OffMessageAssembler.java
        └── ...

apex-components-parent/apex-system/
└── src/main/java/com/apex/scaffold/interfaces/
    ├── handler/
    │   └── office/
    │       ├── OffMessageHandler.java
    │       ├── OffOrganizationHandler.java
    │       ├── OffApprovalHandler.java
    │       └── OffCalendarHandler.java
    └── router/
        └── OfficeRouter.java
```

---

## 🔄 开发流程

遵循现有的开发模式:

1. **Domain 层**: 定义实体和 Repository 接口
2. **Infrastructure 层**: 实现 Repository,创建 PO、Mapper、Converter
3. **Application 层**: 实现 Service,创建 DTO、Assembler
4. **Interfaces 层**: 实现 Handler,注册 Router

---

## 📝 数据库设计原则

1. **表命名**: `off_` 前缀(office 缩写)
2. **多租户**: 所有表包含 `tenant_id` 字段
3. **审计字段**: `created_at`, `updated_at`, `created_by`, `updated_by`
4. **逻辑删除**: `deleted` 字段(0=正常,1=删除)
5. **乐观锁**: `version` 字段

---

## 🚀 MVP 开发策略

由于时间限制,采用 MVP(最小可行产品)策略:

### 第一优先级(必须完成)
- ✅ 消息推送核心功能(模板管理 + 消息发送 + 收件箱)
- ✅ 组织同步核心功能(全量同步 + 部门树查询)

### 第二优先级(简化实现)
- ⚠️ 审批流程基础功能(简化审批流程,只支持单级审批)
- ⚠️ 日历管理基础功能(简化重复规则,只支持单次事件)

### 暂不实现(可扩展)
- ❌ 消息推送的实时通知(WebSocket)
- ❌ 审批流程的多级审批和会签
- ❌ 日历事件的复杂重复规则(如每月第2个周五)
- ❌ 组织同步的增量同步算法优化

---

## ⏱️ 时间分配

| 任务 | 预估时间 | 优先级 |
|------|----------|--------|
| 数据库设计(13张表) | 2小时 | 高 |
| Flyway 迁移脚本(V57) | 1小时 | 高 |
| 消息推送模块 | 4小时 | 高 |
| 组织同步模块 | 3小时 | 高 |
| 审批流程模块(简化) | 4小时 | 中 |
| 日历管理模块(简化) | 3小时 | 中 |
| 测试代码 | 2小时 | 中 |
| 技术文档 | 1小时 | 低 |
| **总计** | **20小时** | - |

**实际可用时间**: 约 3-4 小时(考虑 token 限制和效率)

**调整策略**: 聚焦**消息推送**和**组织同步**两个模块,其他模块只完成核心框架

---

## ✅ 验收标准

1. **功能完整性**:
   - 消息推送: 模板管理 + 消息发送 + 收件箱 ✅
   - 组织同步: 全量同步 + 部门树查询 ✅
   - 审批流程: 基础框架 ⚠️
   - 日历管理: 基础框架 ⚠️

2. **代码质量**:
   - DDD 分层架构正确 ✅
   - 响应式编程模式 ✅
   - 多租户隔离 ✅
   - MapStruct 对象转换 ✅

3. **数据库设计**:
   - Flyway 迁移脚本(V57) ✅
   - 13 张表设计合理 ✅
   - 索引优化 ✅

4. **文档完整性**:
   - 实现总览文档 ✅
   - API 文档(OpenAPI) ✅

---

**下一步行动**: 开始数据库设计,创建 Flyway V42 迁移脚本