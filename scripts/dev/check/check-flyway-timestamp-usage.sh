#!/bin/bash

###############################################################################
# check-flyway-timestamp-usage.sh - 检查 Flyway 迁移脚本中禁用的 TIMESTAMP 类型用法
# 用法：./scripts/dev/check/check-flyway-timestamp-usage.sh  无参数；发现问题输出清单并以非零码退出
# 作者：Apex Team    创建：2026-10-01
###############################################################################

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../../lib/common.sh"

setup_error_trap
PROJECT_ROOT=$(get_project_root) || exit 1
cd "$PROJECT_ROOT"

print_header "检查 Flyway 脚本中的 TIMESTAMP 类型使用..."

MYSQL_MIGRATION_DIR="apex-components-parent/apex-system/src/main/resources/db/migration/mysql"
POSTGRESQL_MIGRATION_DIR="apex-components-parent/apex-system/src/main/resources/db/migration/postgresql"

require_directory "$MYSQL_MIGRATION_DIR" "MySQL 迁移脚本目录" || exit 1
require_directory "$POSTGRESQL_MIGRATION_DIR" "PostgreSQL 迁移脚本目录" || exit 1

ISSUES_FOUND=0

print_section "检查 MySQL 脚本中的 TIMESTAMP 类型使用"

MYSQL_FILES=$(safe_find "$MYSQL_MIGRATION_DIR" "*.sql")

TIMESTAMP_DEFINITIONS=""
while IFS= read -r file; do
    [[ -z "$file" ]] && continue
    
    MATCHES=$(grep -n "TIMESTAMP" "$file" | grep -v "CURRENT_TIMESTAMP" | grep -v "DEFAULT CURRENT_TIMESTAMP" | grep -v "ON UPDATE CURRENT_TIMESTAMP" | grep -v "^[[:space:]]*--" || true)
    
    if [[ -n "$MATCHES" ]]; then
        TIMESTAMP_DEFINITIONS="${TIMESTAMP_DEFINITIONS}${file}:\n${MATCHES}\n\n"
        ISSUES_FOUND=$((ISSUES_FOUND + 1))
    fi
done <<< "$MYSQL_FILES"

if safe_gt "$ISSUES_FOUND" 0; then
    log_failure "发现 ${ISSUES_FOUND} 个 MySQL 脚本使用了 TIMESTAMP 类型"
    echo ""
    echo "问题详情："
    echo "----------------------------------------"
    echo -e "$TIMESTAMP_DEFINITIONS"
    echo "----------------------------------------"
    echo ""
    log_error "MySQL 数据库必须使用 DATETIME 类型，禁止使用 TIMESTAMP 类型"
    log_error "TIMESTAMP 类型范围限制为 1970-2038，存在 Y2038 问题"
    echo ""
    echo "建议修复："
    echo "  1. 将所有 TIMESTAMP 类型改为 DATETIME"
    echo "  2. 保持 DEFAULT CURRENT_TIMESTAMP 和 ON UPDATE CURRENT_TIMESTAMP 不变"
    echo "  3. 示例："
    echo "     错误: create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
    echo "     正确: create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP"
    exit 1
else
    log_success "所有 MySQL 脚本时间字段类型检查通过"
fi

print_section "PostgreSQL 脚本时间字段使用统计"

POSTGRESQL_FILES=$(safe_find "$POSTGRESQL_MIGRATION_DIR" "*.sql")
TIMESTAMP_COUNT=0

while IFS= read -r file; do
    [[ -z "$file" ]] && continue
    
    COUNT=$(safe_count "TIMESTAMP" "$file")
    TIMESTAMP_COUNT=$((TIMESTAMP_COUNT + COUNT))
done <<< "$POSTGRESQL_FILES"

log_info "PostgreSQL 脚本使用 TIMESTAMP 类型: ${TIMESTAMP_COUNT} 处"
log_success "PostgreSQL TIMESTAMP 类型为 64 位，无 2038 年问题"

echo ""
print_header "检查完成"
log_success "所有 Flyway 脚本时间字段类型符合规范"
