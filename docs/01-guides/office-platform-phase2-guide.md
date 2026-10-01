# 办公平台集成 - 阶段二功能指南

## 概述

本文档介绍阶段二新增的办公平台业务能力扩展功能，覆盖消息、审批、日历、文档四大子域。所有能力均通过端口-适配器架构对接飞书、钉钉、企业微信三大平台。

## 功能模块

### 1. 消息发送能力

对应领域：`domain/office/message`，端口：`MessagePort`，应用服务：`OfficeMessageService` / `OfficeMessageApplicationService`。

- 文本消息发送（`TextMessageContent`）
- Markdown 消息发送（`MarkdownMessageContent`）
- 卡片消息发送（`CardMessageContent`）
- 批量消息发送
- 消息状态查询（`MessageStatus`）

### 2. 审批流程对接

对应领域：`domain/office/approval`，端口：`ApprovalPort`，应用服务：`OfficeApprovalService` / `ApprovalProcessApplicationService`。

- 发起审批（`InitiateApprovalCommand`）
- 查询审批单列表（按租户/平台/状态分页）
- 审批详情查询
- 审批操作（通过/拒绝/转审/撤销/催办）
- 审批状态同步
- 待审批列表查询

### 3. 日历会议管理

对应领域：`domain/office/calendar`，端口：`CalendarPort`，应用服务：`OfficeCalendarService`。

- 日程 CRUD（`CalendarEvent`）
- 会议邀请
- 参会人管理
- 即将开始日程查询
- 会议室可用性查询

### 4. 文件文档管理

对应领域：`domain/office/document`，端口：`DocumentPort`，应用服务：`OfficeDocumentService`。

- 文档创建（doc/sheet/slide）
- 文档信息查询与标题更新
- 文档移动、复制、删除
- 文件上传与下载（多格式导出）
- 文档权限管理（查看/编辑/完全控制）
- 文档分享（链接/用户，支持过期时间）
- 文件夹文档列表与文档搜索

> 说明：当前实现聚焦于文档元数据与生命周期管理，不包含实时协同编辑能力。

### 5. 组织架构同步

对应领域：`domain/office`（`OfficeOrganizationService`、`OfficeDepartment`、`OfficeMember`），应用服务：`OfficeOrganizationSyncService`。

- 部门与成员信息同步
- 组织映射关系维护（`OfficeOrganizationMapping`）

## 使用说明

详细使用说明请参考各功能模块的具体文档与 `office-platform-integration-guide.md`。
