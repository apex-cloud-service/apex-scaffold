#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/common.sh"

PROJECT_ROOT=$(get_project_root) || exit 1
cd "$PROJECT_ROOT"

print_header "批量修复Converter接口JavaDoc和@author注解"

CONVERTER_DIR="apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/converter"

declare -a FILES_TO_FIX=(
    "OffMessagePoConverter.java"
    "OffMessageRecipientPoConverter.java"
    "OffMessageTemplatePoConverter.java"
    "OffSyncHistoryPoConverter.java"
    "OfficeApprovalProcessPoConverter.java"
    "OfficeCalendarEventPoConverter.java"
    "SysJwtConfigPoConverter.java"
    "SysSecurityPolicyPoConverter.java"
    "SysSessionConfigPoConverter.java"
    "SysSsoAuditLogPoConverter.java"
    "TaskPoConverter.java"
    "WorkflowDefinitionPoConverter.java"
    "ApprovalRecordPoConverter.java"
    "ProcessInstancePoConverter.java"
    "ChartPoConverter.java"
    "DocumentPoConverter.java"
    "RolePoConverter.java"
)

for file in "${FILES_TO_FIX[@]}"; do
    filepath="$CONVERTER_DIR/$file"
    
    if [ ! -f "$filepath" ]; then
        log_warn "文件不存在: $filepath"
        continue
    fi
    
    log_info "处理: $file"
    
    if grep -q '@author' "$filepath"; then
        log_debug "已有@author注解，跳过: $file"
        continue
    fi
    
    python3 - "$filepath" <<'PYTHON_SCRIPT'
import sys
import re

filepath = sys.argv[1]

with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

content = re.sub(
    r'(/\*\*\n \* [^\n]+\n)( \* @since)',
    r'\1 *\n \* @author Apex Team\n\2',
    content
)

content = re.sub(
    r'^(\s+)(\w+\s+to\w+\([^)]+\);)\s*$',
    lambda m: f'''{m.group(1)}/**
{m.group(1)} * 对象转换方法
{m.group(1)} *
{m.group(1)} * @param source 源对象
{m.group(1)} * @return 转换后的对象
{m.group(1)} */
{m.group(1)}{m.group(2)}''',
    content,
    flags=re.MULTILINE
)

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
    
print(f"✅ 已修复: {filepath}")
PYTHON_SCRIPT

done

log_success "批量修复完成"
