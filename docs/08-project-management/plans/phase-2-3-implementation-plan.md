# 办公平台集成项目 - 阶段二和阶段三实施计划

## 📋 概览

本文档详细说明阶段二（业务能力扩展）和阶段三（前端实现）的完整实施计划。

---

## 🎯 阶段二：业务能力扩展

### 2.1 组织通讯录同步（进行中）

#### 已完成
- ✅ 领域层：OfficeDepartment、OfficeMember、OfficeOrganizationService
- ✅ 应用层：OfficeOrganizationSyncService
- ✅ 基础设施层：OfficeOrganizationServiceImpl、FeishuOrganizationClient、DingTalkOrganizationClient
- ✅ Repository 扩展：组织映射和同步任务相关方法

#### 待完成
1. **基础设施层**
   - WeComOrganizationClient（企业微信组织客户端）
   - Repository 实现：组织映射和同步任务的 CRUD
   - PO、Mapper、Converter

2. **接口层**
   - OfficeOrganizationSyncHandler（接口处理器）
   - Router 注册
   - Request/Response DTO
   - Assembler

3. **数据库**
   - 无需新表（已有 office_organization_mapping 和 office_sync_task）
   - 可能需要添加索引优化

### 2.2 消息发送能力

#### 功能需求
- 文本消息发送（单聊、群聊）
- Markdown 消息发送
- 卡片消息发送（交互式卡片）
- 批量消息发送
- 消息发送状态追踪

#### 实现文件清单

**领域层（domain/office/message）**
1. `OfficeMessage` - 消息聚合根
2. `MessageType` - 消息类型枚举（TEXT、MARKDOWN、CARD）
3. `MessageTarget` - 消息目标（用户、群组）
4. `MessageContent` - 消息内容接口
5. `TextMessageContent` - 文本消息内容
6. `MarkdownMessageContent` - Markdown 消息内容
7. `CardMessageContent` - 卡片消息内容
8. `OfficeMessageService` - 消息服务领域端口

**应用层（application/office/message）**
1. `OfficeMessageApplicationService` - 消息应用服务
2. `SendMessageCommand` - 发送消息命令
3. `BatchSendMessageCommand` - 批量发送命令
4. `MessageSendResult` - 发送结果

**基础设施层（infrastructure/office/message）**
1. `OfficePlatformMessageClient` - 消息客户端 SPI
2. `FeishuMessageClient` - 飞书消息客户端
3. `DingTalkMessageClient` - 钉钉消息客户端
4. `WeComMessageClient` - 企业微信消息客户端
5. `OfficeMessageServiceImpl` - 消息服务实现
6. `OfficeMessagePO` - 消息持久化对象
7. `OfficeMessageMapper` - MyBatis Mapper
8. `OfficeMessageConverter` - PO 转换器
9. `OfficeMessageRepositoryImpl` - Repository 实现

**接口层（interfaces/office/message）**
1. `OfficeMessageHandler` - 消息处理器
2. `OfficeMessageRouter` - 消息路由
3. `SendMessageRequest` - 发送消息请求
4. `SendMessageResponse` - 发送消息响应
5. `BatchSendMessageRequest` - 批量发送请求
6. `MessageStatusResponse` - 消息状态响应
7. `OfficeMessageAssembler` - 消息装配器

**数据库**
- `office_message`（消息记录表）
- `office_message_recipient`（消息接收人表）

### 2.3 审批流程对接

#### 功能需求
- 查询审批单列表
- 提交审批
- 审批操作（同意、拒绝）
- 审批状态同步
- 审批流程模板管理

#### 实现文件清单

**领域层（domain/office/approval）**
1. `OfficeApproval` - 审批聚合根
2. `ApprovalStatus` - 审批状态枚举
3. `ApprovalNode` - 审批节点
4. `ApprovalTemplate` - 审批模板
5. `OfficeApprovalService` - 审批服务领域端口

**应用层（application/office/approval）**
1. `OfficeApprovalApplicationService` - 审批应用服务
2. `SubmitApprovalCommand` - 提交审批命令
3. `ProcessApprovalCommand` - 处理审批命令

**基础设施层（infrastructure/office/approval）**
1. `OfficePlatformApprovalClient` - 审批客户端 SPI
2. `FeishuApprovalClient` - 飞书审批客户端
3. `DingTalkApprovalClient` - 钉钉审批客户端
4. `WeComApprovalClient` - 企业微信审批客户端
5. `OfficeApprovalServiceImpl` - 审批服务实现
6. `OfficeApprovalPO` - 审批持久化对象
7. `OfficeApprovalMapper` - MyBatis Mapper

**接口层（interfaces/office/approval）**
1. `OfficeApprovalHandler` - 审批处理器
2. `OfficeApprovalRouter` - 审批路由
3. `SubmitApprovalRequest` - 提交审批请求
4. `ProcessApprovalRequest` - 处理审批请求
5. `ApprovalDetailResponse` - 审批详情响应

**数据库**
- `office_approval`（审批记录表）
- `office_approval_node`（审批节点表）

### 2.4 日历会议管理

#### 功能需求
- 日程 CRUD
- 会议邀请
- 参会人管理
- 会议提醒
- 会议状态同步

#### 实现文件清单

**领域层（domain/office/calendar）**
1. `OfficeCalendarEvent` - 日程聚合根
2. `EventType` - 事件类型枚举
3. `EventAttendee` - 参会人
4. `EventReminder` - 提醒设置
5. `OfficeCalendarService` - 日历服务领域端口

**应用层（application/office/calendar）**
1. `OfficeCalendarApplicationService` - 日历应用服务
2. `CreateEventCommand` - 创建日程命令
3. `UpdateEventCommand` - 更新日程命令
4. `InviteAttendeesCommand` - 邀请参会人命令

**基础设施层（infrastructure/office/calendar）**
1. `OfficePlatformCalendarClient` - 日历客户端 SPI
2. `FeishuCalendarClient` - 飞书日历客户端
3. `DingTalkCalendarClient` - 钉钉日历客户端
4. `WeComCalendarClient` - 企业微信日历客户端
5. `OfficeCalendarServiceImpl` - 日历服务实现
6. `OfficeCalendarEventPO` - 日程持久化对象
7. `OfficeCalendarEventMapper` - MyBatis Mapper

**接口层（interfaces/office/calendar）**
1. `OfficeCalendarHandler` - 日历处理器
2. `OfficeCalendarRouter` - 日历路由
3. `CreateEventRequest` - 创建日程请求
4. `UpdateEventRequest` - 更新日程请求
5. `EventDetailResponse` - 日程详情响应

**数据库**
- `office_calendar_event`（日程表）
- `office_event_attendee`（参会人表）

### 2.5 文件文档操作

#### 功能需求
- 文件上传下载
- 文档创建编辑
- 协同编辑（获取编辑链接）
- 文件权限管理
- 文件版本控制

#### 实现文件清单

**领域层（domain/office/document）**
1. `OfficeDocument` - 文档聚合根
2. `DocumentType` - 文档类型枚举
3. `DocumentPermission` - 文档权限
4. `DocumentVersion` - 文档版本
5. `OfficeDocumentService` - 文档服务领域端口

**应用层（application/office/document）**
1. `OfficeDocumentApplicationService` - 文档应用服务
2. `CreateDocumentCommand` - 创建文档命令
3. `UpdateDocumentCommand` - 更新文档命令
4. `ShareDocumentCommand` - 分享文档命令

**基础设施层（infrastructure/office/document）**
1. `OfficePlatformDocumentClient` - 文档客户端 SPI
2. `FeishuDocumentClient` - 飞书文档客户端
3. `DingTalkDocumentClient` - 钉钉文档客户端
4. `WeComDocumentClient` - 企业微信文档客户端
5. `OfficeDocumentServiceImpl` - 文档服务实现
6. `OfficeDocumentPO` - 文档持久化对象
7. `OfficeDocumentMapper` - MyBatis Mapper

**接口层（interfaces/office/document）**
1. `OfficeDocumentHandler` - 文档处理器
2. `OfficeDocumentRouter` - 文档路由
3. `CreateDocumentRequest` - 创建文档请求
4. `UpdateDocumentRequest` - 更新文档请求
5. `DocumentDetailResponse` - 文档详情响应
6. `ShareDocumentRequest` - 分享文档请求

**数据库**
- `office_document`（文档表）
- `office_document_permission`（文档权限表）
- `office_document_version`（文档版本表）

---

## 🎨 阶段三：前端实现

### 3.1 平台应用配置页

#### 功能需求
- 应用列表展示（支持筛选、排序）
- 创建应用配置
- 编辑应用配置
- 测试连通性
- 启用/禁用应用
- 删除应用

#### 文件清单
```
apex-admin-web/src/views/office/
├── platform-app/
│   ├── index.vue              # 应用列表页
│   ├── components/
│   │   ├── AppList.vue       # 应用列表组件
│   │   ├── AppForm.vue       # 应用表单组件
│   │   ├── AppDetail.vue     # 应用详情组件
│   │   └── TestConnection.vue # 测试连通性组件
│   └── types.ts              # 类型定义

apex-admin-web/src/api/office/
└── platform-app.ts           # API 接口
```

#### 页面功能
1. **列表页面**
   - 表格展示：平台、应用ID、应用名称、状态、创建时间
   - 操作按钮：编辑、测试、启用/禁用、删除
   - 搜索筛选：按平台、状态筛选
   - 新建按钮

2. **表单页面**
   - 平台选择（飞书、钉钉、企业微信）
   - 应用ID输入
   - 应用名称输入
   - 重定向URI输入
   - 密钥引用输入（环境变量名）
   - 启用状态开关

3. **测试连通性**
   - 测试按钮
   - 测试结果显示
   - 错误信息提示

### 3.2 绑定审批管理页

#### 功能需求
- 待审批列表
- 审批/拒绝操作
- 绑定记录查询
- 解绑操作
- 状态筛选

#### 文件清单
```
apex-admin-web/src/views/office/
├── binding/
│   ├── index.vue              # 绑定管理页
│   ├── components/
│   │   ├── BindingList.vue   # 绑定列表组件
│   │   ├── ReviewModal.vue   # 审批弹窗组件
│   │   └── BindingDetail.vue # 绑定详情组件
│   └── types.ts              # 类型定义

apex-admin-web/src/api/office/
└── binding.ts                # API 接口
```

#### 页面功能
1. **列表页面**
   - 表格展示：用户、平台、外部用户ID、状态、申请时间
   - 状态筛选：待审批、已通过、已拒绝
   - 批量操作：批量审批、批量拒绝

2. **审批操作**
   - 审批弹窗
   - 拒绝原因输入
   - 确认提示

3. **解绑操作**
   - 解绑确认
   - 解绑原因（可选）

### 3.3 办公操作控制台

#### 功能需求
- 消息发送（文本、Markdown、卡片）
- 组织同步控制
- 审批流程处理
- 文件管理
- 日历会议管理

#### 文件清单
```
apex-admin-web/src/views/office/
├── console/
│   ├── index.vue                    # 控制台主页
│   ├── message/
│   │   ├── index.vue               # 消息发送页
│   │   ├── components/
│   │   │   ├── MessageEditor.vue  # 消息编辑器
│   │   │   ├── RecipientSelect.vue # 接收人选择
│   │   │   └── MessageHistory.vue  # 消息历史
│   ├── organization/
│   │   ├── index.vue               # 组织同步页
│   │   ├── components/
│   │   │   ├── SyncControl.vue    # 同步控制组件
│   │   │   ├── SyncHistory.vue    # 同步历史
│   │   │   └── MappingList.vue    # 映射列表
│   ├── approval/
│   │   ├── index.vue               # 审批处理页
│   │   ├── components/
│   │   │   ├── ApprovalList.vue   # 审批列表
│   │   │   └── ApprovalDetail.vue # 审批详情
│   ├── document/
│   │   ├── index.vue               # 文档管理页
│   │   ├── components/
│   │   │   ├── DocumentList.vue   # 文档列表
│   │   │   └── DocumentEditor.vue # 文档编辑器
│   └── calendar/
│       ├── index.vue                # 日历管理页
│       └── components/
│           ├── CalendarView.vue    # 日历视图
│           └── EventEditor.vue     # 事件编辑器

apex-admin-web/src/api/office/
├── message.ts                       # 消息 API
├── organization.ts                  # 组织 API
├── approval.ts                      # 审批 API
├── document.ts                      # 文档 API
└── calendar.ts                      # 日历 API
```

#### 页面布局
- 左侧导航菜单：消息、组织、审批、文档、日历
- 右侧内容区域：对应功能模块
- 顶部面包屑导航

---

## 📊 数据库设计

### 新增表结构

#### 1. office_message（消息记录表）
```sql
CREATE TABLE office_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id VARCHAR(64) NOT NULL,
  platform VARCHAR(20) NOT NULL,
  message_type VARCHAR(20) NOT NULL,
  content TEXT,
  sender_id VARCHAR(128),
  message_id VARCHAR(128),
  send_status VARCHAR(20),
  error_message TEXT,
  created_at DATETIME,
  updated_at DATETIME,
  version INT DEFAULT 0,
  deleted TINYINT DEFAULT 0,
  INDEX idx_tenant_platform (tenant_id, platform),
  INDEX idx_sender (sender_id),
  INDEX idx_created (created_at)
);
```

#### 2. office_message_recipient（消息接收人表）
```sql
CREATE TABLE office_message_recipient (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  message_id BIGINT NOT NULL,
  recipient_type VARCHAR(20) NOT NULL,
  recipient_id VARCHAR(128) NOT NULL,
  read_status TINYINT DEFAULT 0,
  read_at DATETIME,
  created_at DATETIME,
  INDEX idx_message (message_id),
  INDEX idx_recipient (recipient_id),
  FOREIGN KEY (message_id) REFERENCES office_message(id)
);
```

#### 3. office_approval（审批记录表）
```sql
CREATE TABLE office_approval (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id VARCHAR(64) NOT NULL,
  platform VARCHAR(20) NOT NULL,
  approval_code VARCHAR(128) UNIQUE,
  external_approval_id VARCHAR(128),
  title VARCHAR(255),
  status VARCHAR(20),
  submitter_id VARCHAR(128),
  submitted_at DATETIME,
  completed_at DATETIME,
  created_at DATETIME,
  updated_at DATETIME,
  version INT DEFAULT 0,
  deleted TINYINT DEFAULT 0,
  INDEX idx_tenant_platform (tenant_id, platform),
  INDEX idx_status (status),
  INDEX idx_submitter (submitter_id)
);
```

#### 4. office_approval_node（审批节点表）
```sql
CREATE TABLE office_approval_node (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  approval_id BIGINT NOT NULL,
  node_order INT,
  approver_id VARCHAR(128),
  approver_name VARCHAR(128),
  status VARCHAR(20),
  comment TEXT,
  processed_at DATETIME,
  created_at DATETIME,
  INDEX idx_approval (approval_id),
  INDEX idx_approver (approver_id),
  FOREIGN KEY (approval_id) REFERENCES office_approval(id)
);
```

#### 5. office_calendar_event（日程表）
```sql
CREATE TABLE office_calendar_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id VARCHAR(64) NOT NULL,
  platform VARCHAR(20) NOT NULL,
  external_event_id VARCHAR(128),
  title VARCHAR(255),
  description TEXT,
  start_time DATETIME,
  end_time DATETIME,
  location VARCHAR(255),
  organizer_id VARCHAR(128),
  status VARCHAR(20),
  created_at DATETIME,
  updated_at DATETIME,
  version INT DEFAULT 0,
  deleted TINYINT DEFAULT 0,
  INDEX idx_tenant_platform (tenant_id, platform),
  INDEX idx_organizer (organizer_id),
  INDEX idx_start_time (start_time)
);
```

#### 6. office_event_attendee（参会人表）
```sql
CREATE TABLE office_event_attendee (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id BIGINT NOT NULL,
  attendee_id VARCHAR(128),
  attendee_name VARCHAR(128),
  status VARCHAR(20),
  response_time DATETIME,
  created_at DATETIME,
  INDEX idx_event (event_id),
  INDEX idx_attendee (attendee_id),
  FOREIGN KEY (event_id) REFERENCES office_calendar_event(id)
);
```

#### 7. office_document（文档表）
```sql
CREATE TABLE office_document (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id VARCHAR(64) NOT NULL,
  platform VARCHAR(20) NOT NULL,
  external_doc_id VARCHAR(128),
  title VARCHAR(255),
  doc_type VARCHAR(20),
  url TEXT,
  owner_id VARCHAR(128),
  status VARCHAR(20),
  created_at DATETIME,
  updated_at DATETIME,
  version INT DEFAULT 0,
  deleted TINYINT DEFAULT 0,
  INDEX idx_tenant_platform (tenant_id, platform),
  INDEX idx_owner (owner_id),
  INDEX idx_doc_type (doc_type)
);
```

#### 8. office_document_permission（文档权限表）
```sql
CREATE TABLE office_document_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  document_id BIGINT NOT NULL,
  user_id VARCHAR(128),
  permission_type VARCHAR(20),
  created_at DATETIME,
  INDEX idx_document (document_id),
  INDEX idx_user (user_id),
  FOREIGN KEY (document_id) REFERENCES office_document(id)
);
```

---

## 🧪 测试策略

### 单元测试
- 每个 Service 类至少 1 个测试类
- 覆盖正常流程、异常场景、边界条件
- Mock 外部依赖

### 集成测试
- Repository 实现测试（H2 内存数据库）
- Handler 契约测试
- 平台客户端集成测试（可选，需要真实凭据）

### 测试覆盖目标
- 代码覆盖率：≥ 80%
- 分支覆盖率：≥ 70%
- 核心业务逻辑：100%

---

## 📝 文档交付

### 开发文档
1. **业务能力扩展指南**
   - 每个功能模块的使用说明
   - API 接口文档
   - 配置说明

2. **前端开发指南**
   - 页面结构说明
   - 组件使用指南
   - API 对接说明

3. **数据库变更说明**
   - 新增表结构
   - 索引优化建议
   - 数据迁移脚本

### 运维文档
1. **部署指南**
   - 环境变量配置
   - 数据库迁移步骤
   - 服务启动验证

2. **监控告警**
   - 关键指标
   - 告警规则
   - 故障排查

---

## ⏱️ 实施时间估算

### 阶段二：业务能力扩展（约 40-50 小时）
- 组织同步（剩余）：2-3 小时
- 消息发送：8-10 小时
- 审批对接：8-10 小时
- 日历管理：8-10 小时
- 文件文档：8-10 小时
- 测试编写：6-8 小时

### 阶段三：前端实现（约 30-40 小时）
- 平台应用配置页：8-10 小时
- 绑定审批管理页：6-8 小时
- 办公操作控制台：16-22 小时

### 文档和测试（约 10-15 小时）
- 技术文档编写：4-5 小时
- 测试报告生成：2-3 小时
- 验收和修复：4-7 小时

**总计：80-105 小时（10-13 个工作日）**

---

## 🚀 实施顺序建议

### 第一批（核心功能）
1. 完成组织同步（包括企业微信客户端）
2. 实现消息发送能力
3. 前端：平台应用配置页和绑定审批管理页

### 第二批（扩展功能）
1. 实现审批流程对接
2. 实现日历会议管理
3. 前端：办公操作控制台（消息和组织模块）

### 第三批（高级功能）
1. 实现文件文档操作
2. 前端：办公操作控制台（审批、文档、日历模块）
3. 完善测试和文档

---

## ✅ 验收标准

### 功能完成度
- [ ] 所有后端接口实现完成
- [ ] 所有前端页面开发完成
- [ ] 数据库迁移脚本完成
- [ ] 单元测试通过率 100%
- [ ] 集成测试通过率 100%

### 代码质量
- [ ] Spotless 格式检查通过
- [ ] PMD 代码检查通过
- [ ] JavaDoc 覆盖率 100%
- [ ] SpringDoc 覆盖率 100%

### 文档交付
- [ ] 开发文档完整
- [ ] 运维文档完整
- [ ] API 文档完整
- [ ] 测试报告完整

---

**文档版本**: 1.0  
**创建时间**: 2026-09-14  
**负责团队**: Apex Team
