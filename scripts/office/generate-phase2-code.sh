#!/bin/bash

# 办公平台集成项目 - 阶段二和阶段三代码生成脚本
# 此脚本用于批量生成领域层、应用层、基础设施层和接口层的代码框架

set -e

PROJECT_ROOT="/Users/work/apex-scaffold"
DOMAIN_PATH="$PROJECT_ROOT/apex-domain/src/main/java/com/apex/scaffold/domain/office"
APPLICATION_PATH="$PROJECT_ROOT/apex-application/src/main/java/com/apex/scaffold/application/office"
INFRASTRUCTURE_PATH="$PROJECT_ROOT/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office"
INTERFACES_PATH="$PROJECT_ROOT/apex-system/src/main/java/com/apex/scaffold/interfaces"

echo "=========================================="
echo "办公平台集成 - 阶段二代码生成工具"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

print_step() {
    echo -e "${GREEN}[步骤]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[警告]${NC} $1"
}

print_error() {
    echo -e "${RED}[错误]${NC} $1"
}

# 创建目录结构
create_directories() {
    print_step "创建目录结构..."
    
    # 消息模块
    mkdir -p "$DOMAIN_PATH/message"
    mkdir -p "$APPLICATION_PATH/message"
    mkdir -p "$INFRASTRUCTURE_PATH/message/client"
    mkdir -p "$INFRASTRUCTURE_PATH/message/persistence"
    mkdir -p "$INTERFACES_PATH/handler/office/message"
    mkdir -p "$INTERFACES_PATH/router/office"
    mkdir -p "$INTERFACES_PATH/dto/office/message"
    
    # 审批模块
    mkdir -p "$DOMAIN_PATH/approval"
    mkdir -p "$APPLICATION_PATH/approval"
    mkdir -p "$INFRASTRUCTURE_PATH/approval/client"
    mkdir -p "$INFRASTRUCTURE_PATH/approval/persistence"
    mkdir -p "$INTERFACES_PATH/handler/office/approval"
    mkdir -p "$INTERFACES_PATH/dto/office/approval"
    
    # 日历模块
    mkdir -p "$DOMAIN_PATH/calendar"
    mkdir -p "$APPLICATION_PATH/calendar"
    mkdir -p "$INFRASTRUCTURE_PATH/calendar/client"
    mkdir -p "$INFRASTRUCTURE_PATH/calendar/persistence"
    mkdir -p "$INTERFACES_PATH/handler/office/calendar"
    mkdir -p "$INTERFACES_PATH/dto/office/calendar"
    
    # 文档模块
    mkdir -p "$DOMAIN_PATH/document"
    mkdir -p "$APPLICATION_PATH/document"
    mkdir -p "$INFRASTRUCTURE_PATH/document/client"
    mkdir -p "$INFRASTRUCTURE_PATH/document/persistence"
    mkdir -p "$INTERFACES_PATH/handler/office/document"
    mkdir -p "$INTERFACES_PATH/dto/office/document"
    
    echo "✓ 目录结构创建完成"
}

# 生成消息模块代码
generate_message_module() {
    print_step "生成消息模块代码..."
    
    # 这里只是创建占位文件，实际代码需要手动实现
    touch "$DOMAIN_PATH/message/OfficeMessage.java"
    touch "$DOMAIN_PATH/message/MessageType.java"
    touch "$DOMAIN_PATH/message/MessageTarget.java"
    touch "$DOMAIN_PATH/message/OfficeMessageService.java"
    
    touch "$APPLICATION_PATH/message/OfficeMessageApplicationService.java"
    touch "$APPLICATION_PATH/message/SendMessageCommand.java"
    
    echo "  - 领域层：4 个文件"
    echo "  - 应用层：2 个文件"
    echo "✓ 消息模块框架生成完成"
}

# 生成审批模块代码
generate_approval_module() {
    print_step "生成审批模块代码..."
    
    touch "$DOMAIN_PATH/approval/OfficeApproval.java"
    touch "$DOMAIN_PATH/approval/ApprovalStatus.java"
    touch "$DOMAIN_PATH/approval/OfficeApprovalService.java"
    
    touch "$APPLICATION_PATH/approval/OfficeApprovalApplicationService.java"
    
    echo "  - 领域层：3 个文件"
    echo "  - 应用层：1 个文件"
    echo "✓ 审批模块框架生成完成"
}

# 生成日历模块代码
generate_calendar_module() {
    print_step "生成日历模块代码..."
    
    touch "$DOMAIN_PATH/calendar/OfficeCalendarEvent.java"
    touch "$DOMAIN_PATH/calendar/EventType.java"
    touch "$DOMAIN_PATH/calendar/OfficeCalendarService.java"
    
    touch "$APPLICATION_PATH/calendar/OfficeCalendarApplicationService.java"
    
    echo "  - 领域层：3 个文件"
    echo "  - 应用层：1 个文件"
    echo "✓ 日历模块框架生成完成"
}

# 生成文档模块代码
generate_document_module() {
    print_step "生成文档模块代码..."
    
    touch "$DOMAIN_PATH/document/OfficeDocument.java"
    touch "$DOMAIN_PATH/document/DocumentType.java"
    touch "$DOMAIN_PATH/document/OfficeDocumentService.java"
    
    touch "$APPLICATION_PATH/document/OfficeDocumentApplicationService.java"
    
    echo "  - 领域层：3 个文件"
    echo "  - 应用层：1 个文件"
    echo "✓ 文档模块框架生成完成"
}

# 生成数据库迁移脚本
generate_migration_scripts() {
    print_step "生成数据库迁移脚本..."
    
    MIGRATION_PATH="$PROJECT_ROOT/apex-system/src/main/resources/db/migration"
    
    # 查找最新的版本号
    LATEST_VERSION=$(ls -1 "$MIGRATION_PATH/mysql" | grep -oP 'V\K[0-9]+' | sort -n | tail -1)
    NEXT_VERSION=$((LATEST_VERSION + 1))
    
    echo "  - 最新版本：V$LATEST_VERSION"
    echo "  - 下一版本：V$NEXT_VERSION"
    
    # 创建迁移脚本占位符
    touch "$MIGRATION_PATH/mysql/V${NEXT_VERSION}__add_office_message_tables.sql"
    touch "$MIGRATION_PATH/postgresql/V${NEXT_VERSION}__add_office_message_tables.sql"
    
    NEXT_VERSION=$((NEXT_VERSION + 1))
    touch "$MIGRATION_PATH/mysql/V${NEXT_VERSION}__add_office_approval_tables.sql"
    touch "$MIGRATION_PATH/postgresql/V${NEXT_VERSION}__add_office_approval_tables.sql"
    
    NEXT_VERSION=$((NEXT_VERSION + 1))
    touch "$MIGRATION_PATH/mysql/V${NEXT_VERSION}__add_office_calendar_tables.sql"
    touch "$MIGRATION_PATH/postgresql/V${NEXT_VERSION}__add_office_calendar_tables.sql"
    
    NEXT_VERSION=$((NEXT_VERSION + 1))
    touch "$MIGRATION_PATH/mysql/V${NEXT_VERSION}__add_office_document_tables.sql"
    touch "$MIGRATION_PATH/postgresql/V${NEXT_VERSION}__add_office_document_tables.sql"
    
    echo "✓ 迁移脚本占位符创建完成（需要手动填充SQL内容）"
}

# 生成测试代码框架
generate_test_framework() {
    print_step "生成测试代码框架..."
    
    TEST_PATH="$PROJECT_ROOT/apex-application/src/test/java/com/apex/scaffold/application/office"
    
    mkdir -p "$TEST_PATH/message"
    mkdir -p "$TEST_PATH/approval"
    mkdir -p "$TEST_PATH/calendar"
    mkdir -p "$TEST_PATH/document"
    
    touch "$TEST_PATH/message/OfficeMessageApplicationServiceTest.java"
    touch "$TEST_PATH/approval/OfficeApprovalApplicationServiceTest.java"
    touch "$TEST_PATH/calendar/OfficeCalendarApplicationServiceTest.java"
    touch "$TEST_PATH/document/OfficeDocumentApplicationServiceTest.java"
    
    echo "✓ 测试框架创建完成"
}

# 生成文档
generate_documentation() {
    print_step "生成文档..."
    
    DOC_PATH="$PROJECT_ROOT/docs/01-guides"
    
    cat > "$DOC_PATH/office-platform-phase2-guide.md" << 'EOF'
# 办公平台集成 - 阶段二功能指南

## 概述

本文档介绍阶段二新增的业务能力扩展功能。

## 功能模块

### 1. 消息发送能力
- 文本消息发送
- Markdown 消息发送
- 卡片消息发送
- 批量消息发送

### 2. 审批流程对接
- 查询审批单
- 提交审批
- 审批操作
- 状态同步

### 3. 日历会议管理
- 日程 CRUD
- 会议邀请
- 参会管理

### 4. 文件文档操作
- 文件上传下载
- 协同编辑
- 权限管理

## 使用说明

详细使用说明请参考各功能模块的具体文档。

EOF
    
    echo "✓ 文档生成完成"
}

# 生成进度报告
generate_progress_report() {
    print_step "生成进度报告..."
    
    REPORT_PATH="$PROJECT_ROOT/.trae/documents"
    
    cat > "$REPORT_PATH/phase-2-progress-report.md" << EOF
# 阶段二实施进度报告

生成时间：$(date '+%Y-%m-%d %H:%M:%S')

## 已完成

### 组织同步功能 ✅
- 领域层：OfficeDepartment、OfficeMember、OfficeOrganizationService
- 应用层：OfficeOrganizationSyncService
- 基础设施层：3个平台的组织客户端实现
- Repository 扩展：组织映射和同步任务方法

### 代码框架生成 ✅
- 消息模块：目录和占位文件
- 审批模块：目录和占位文件
- 日历模块：目录和占位文件
- 文档模块：目录和占位文件

## 进行中

### 消息发送能力 🔄
- 需要实现完整的业务逻辑
- 需要实现平台客户端
- 需要实现接口层

### 审批流程对接 📋
- 待实现

### 日历会议管理 📋
- 待实现

### 文件文档操作 📋
- 待实现

## 下一步

1. 完成消息模块的完整实现
2. 实现接口层 Handler 和 Router
3. 编写数据库迁移脚本
4. 编写测试代码
5. 生成 API 文档

## 预计完成时间

- 阶段二后端：5-7 个工作日
- 阶段三前端：4-5 个工作日
- 测试和文档：2-3 个工作日

**总计：11-15 个工作日**

EOF
    
    echo "✓ 进度报告生成完成"
}

# 主流程
main() {
    echo ""
    print_step "开始生成代码框架..."
    echo ""
    
    create_directories
    echo ""
    
    generate_message_module
    echo ""
    
    generate_approval_module
    echo ""
    
    generate_calendar_module
    echo ""
    
    generate_document_module
    echo ""
    
    generate_migration_scripts
    echo ""
    
    generate_test_framework
    echo ""
    
    generate_documentation
    echo ""
    
    generate_progress_report
    echo ""
    
    echo "=========================================="
    echo -e "${GREEN}代码框架生成完成！${NC}"
    echo "=========================================="
    echo ""
    echo "生成的文件位置："
    echo "  - 领域层：$DOMAIN_PATH"
    echo "  - 应用层：$APPLICATION_PATH"
    echo "  - 基础设施层：$INFRASTRUCTURE_PATH"
    echo "  - 接口层：$INTERFACES_PATH"
    echo ""
    echo "下一步："
    echo "  1. 查看进度报告：.trae/documents/phase-2-progress-report.md"
    echo "  2. 填充业务逻辑代码"
    echo "  3. 编写数据库迁移脚本"
    echo "  4. 实现测试代码"
    echo "  5. 运行测试验证"
    echo ""
    print_warning "注意：此脚本仅生成代码框架，具体业务逻辑需要手动实现"
    echo ""
}

# 执行主流程
main
