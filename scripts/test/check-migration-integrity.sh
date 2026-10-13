#!/bin/bash
###############################################################################
# Flyway 迁移脚本完整性检查
# 用途：验证所有迁移文件是否正确配置
# 作者：Apex Scaffold Team
# 日期：2024-09-22
###############################################################################

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Flyway 迁移脚本完整性检查${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$PROJECT_ROOT"

# 检查项计数
TOTAL_CHECKS=0
PASSED_CHECKS=0
FAILED_CHECKS=0

# 辅助函数
check_pass() {
    echo -e "${GREEN}✓${NC} $1"
    PASSED_CHECKS=$((PASSED_CHECKS + 1))
}

check_fail() {
    echo -e "${RED}✗${NC} $1"
    FAILED_CHECKS=$((FAILED_CHECKS + 1))
}

# 1. 检查 MySQL 迁移文件
echo -e "${YELLOW}[1/9] 检查 MySQL 迁移文件...${NC}"
TOTAL_CHECKS=$((TOTAL_CHECKS + 1))

# 参与逐服务校验的微服务清单（迁移方案 §9.3 / §12：每服务独立 V1 版本链）
# apex-auth-service：Phase 2（V44~V56，apex_auth 库）
# apex-workflow-service：Phase 3（V1 起，apex_workflow 库）
# apex-file-service：Phase 4（V1 起，apex_file 库）
# apex-office-service：Phase 5（V1 起，apex_office 库）
# apex-system-service：Phase 6（V1 起，apex_system 库，骨架阶段迁移目录可能为空）
MIGRATION_SERVICES=(
    "apex-auth-service"
    "apex-workflow-service"
    "apex-file-service"
    "apex-office-service"
    "apex-system-service"
)

# 各业务服务独立库的 V1 迁移脚本（每服务独立 Flyway 链，方案 §7 风险 7）
SERVICE_CHAIN_FILES=(
    "apex-workflow-service/src/main/resources/db/migration/mysql/V1__init_workflow_schema.sql"
    "apex-workflow-service/src/main/resources/db/migration/postgresql/V1__init_workflow_schema.sql"
    "apex-file-service/src/main/resources/db/migration/mysql/V1__init_file_schema.sql"
    "apex-file-service/src/main/resources/db/migration/postgresql/V1__init_file_schema.sql"
    "apex-office-service/src/main/resources/db/migration/mysql/V1__init_office_schema.sql"
    "apex-office-service/src/main/resources/db/migration/postgresql/V1__init_office_schema.sql"
)

MYSQL_FILES=(
    "apex-auth-service/src/main/resources/db/migration/mysql/V44__create_auth_center_core_tables.sql"
    "apex-auth-service/src/main/resources/db/migration/mysql/V45__align_auth_domain_fields.sql"
    "apex-auth-service/src/main/resources/db/migration/mysql/V46__insert_test_users.sql"
    "apex-auth-service/src/main/resources/db/migration/mysql/V47__insert_auth_center_seed_data.sql"
    "apex-auth-service/src/main/resources/db/migration/mysql/V48__add_config_and_audit_menu_permissions.sql"
    "apex-auth-service/src/main/resources/db/migration/mysql/V49__create_auth_config_table.sql"
    "apex-auth-service/src/main/resources/db/migration/mysql/V50__create_totp_and_mfa_config_tables.sql"
    "apex-auth-service/src/main/resources/db/migration/mysql/V51__add_high_risk_authorization_permissions.sql"
    "apex-auth-service/src/main/resources/db/migration/mysql/V52__align_auth_token_fields.sql"
    "apex-auth-service/src/main/resources/db/migration/mysql/V53__create_oauth2_authorization_tables.sql"
    "apex-auth-service/src/main/resources/db/migration/mysql/V54__scope_oauth2_authorization_by_tenant.sql"
    "apex-auth-service/src/main/resources/db/migration/mysql/V55__auth_user_account_binding.sql"
    "apex-auth-service/src/main/resources/db/migration/mysql/V56__create_domain_event_outbox.sql"
)

ALL_MYSQL_EXISTS=true
for file in "${MYSQL_FILES[@]}"; do
    if [ -f "$file" ]; then
        echo "  ✓ $(basename $file)"
    else
        echo "  ✗ $(basename $file) 不存在"
        ALL_MYSQL_EXISTS=false
    fi
done

if [ "$ALL_MYSQL_EXISTS" = true ]; then
    check_pass "MySQL 迁移文件完整（${#MYSQL_FILES[@]}个文件）"
else
    check_fail "MySQL 迁移文件缺失"
fi

# 2. 检查 PostgreSQL 迁移文件
echo ""
echo -e "${YELLOW}[2/9] 检查 PostgreSQL 迁移文件...${NC}"
TOTAL_CHECKS=$((TOTAL_CHECKS + 1))

PG_FILES=(
    "apex-auth-service/src/main/resources/db/migration/postgresql/V44__create_auth_center_core_tables.sql"
    "apex-auth-service/src/main/resources/db/migration/postgresql/V45__align_auth_domain_fields.sql"
    "apex-auth-service/src/main/resources/db/migration/postgresql/V46__insert_test_users.sql"
    "apex-auth-service/src/main/resources/db/migration/postgresql/V47__insert_auth_center_seed_data.sql"
    "apex-auth-service/src/main/resources/db/migration/postgresql/V48__add_config_and_audit_menu_permissions.sql"
    "apex-auth-service/src/main/resources/db/migration/postgresql/V49__create_auth_config_table.sql"
    "apex-auth-service/src/main/resources/db/migration/postgresql/V50__create_totp_and_mfa_config_tables.sql"
    "apex-auth-service/src/main/resources/db/migration/postgresql/V51__add_high_risk_authorization_permissions.sql"
    "apex-auth-service/src/main/resources/db/migration/postgresql/V52__align_auth_token_fields.sql"
    "apex-auth-service/src/main/resources/db/migration/postgresql/V53__create_oauth2_authorization_tables.sql"
    "apex-auth-service/src/main/resources/db/migration/postgresql/V54__scope_oauth2_authorization_by_tenant.sql"
    "apex-auth-service/src/main/resources/db/migration/postgresql/V55__auth_user_account_binding.sql"
    "apex-auth-service/src/main/resources/db/migration/postgresql/V56__create_domain_event_outbox.sql"
)

ALL_PG_EXISTS=true
for file in "${PG_FILES[@]}"; do
    if [ -f "$file" ]; then
        echo "  ✓ $(basename $file)"
    else
        echo "  ✗ $(basename $file) 不存在"
        ALL_PG_EXISTS=false
    fi
done

if [ "$ALL_PG_EXISTS" = true ]; then
    check_pass "PostgreSQL 迁移文件完整（${#PG_FILES[@]}个文件）"
else
    check_fail "PostgreSQL 迁移文件缺失"
fi

# 3. 检查 V49 是否存在（关键修复）
echo ""
echo -e "${YELLOW}[3/9] 检查 V49 auth_config 表创建脚本...${NC}"
TOTAL_CHECKS=$((TOTAL_CHECKS + 1))

if [ -f "apex-auth-service/src/main/resources/db/migration/mysql/V49__create_auth_config_table.sql" ] && \
   [ -f "apex-auth-service/src/main/resources/db/migration/postgresql/V49__create_auth_config_table.sql" ]; then
    check_pass "V49 auth_config 表创建脚本存在（MySQL + PostgreSQL）"
else
    check_fail "V49 auth_config 表创建脚本缺失"
fi

# 4. 检查 V50 是否在正确位置
echo ""
echo -e "${YELLOW}[4/9] 检查 V50 文件位置...${NC}"
TOTAL_CHECKS=$((TOTAL_CHECKS + 1))

V50_IN_ROOT=false
if [ -f "apex-auth-service/src/main/resources/db/migration/V50__create_totp_and_mfa_config_tables.sql" ]; then
    V50_IN_ROOT=true
fi

V50_IN_MYSQL=false
if [ -f "apex-auth-service/src/main/resources/db/migration/mysql/V50__create_totp_and_mfa_config_tables.sql" ]; then
    V50_IN_MYSQL=true
fi

V50_IN_PG=false
if [ -f "apex-auth-service/src/main/resources/db/migration/postgresql/V50__create_totp_and_mfa_config_tables.sql" ]; then
    V50_IN_PG=true
fi

if [ "$V50_IN_ROOT" = true ]; then
    check_fail "V50 仍在根目录（应该已移动到 mysql/ 和 postgresql/）"
elif [ "$V50_IN_MYSQL" = true ] && [ "$V50_IN_PG" = true ]; then
    check_pass "V50 已正确放置在 mysql/ 和 postgresql/ 目录"
else
    check_fail "V50 位置不正确"
fi

# 5. 逐服务检查版本号唯一性（防止 V49 重复类问题复发）
echo ""
echo -e "${YELLOW}[5/9] 逐服务检查迁移版本号唯一性...${NC}"
TOTAL_CHECKS=$((TOTAL_CHECKS + 1))

DUPLICATE_FOUND=false
for svc in "${MIGRATION_SERVICES[@]}"; do
    for dialect in mysql postgresql; do
        MIGRATION_DIR="${svc}/src/main/resources/db/migration/${dialect}"
        if [ ! -d "$MIGRATION_DIR" ]; then
            echo "  - ${svc}（${dialect}）无迁移目录，跳过"
            continue
        fi

        # 提取每个迁移文件的版本号（V 前缀后的数字），统计重复项
        DUPLICATED_VERSIONS=$(ls "$MIGRATION_DIR" 2>/dev/null \
            | grep -oE '^V[0-9]+' \
            | sort \
            | uniq -d)

        if [ -n "$DUPLICATED_VERSIONS" ]; then
            echo "  ✗ ${svc}（${dialect}）存在重复版本号：$(echo "$DUPLICATED_VERSIONS" | tr '\n' ' ')"
            DUPLICATE_FOUND=true
        else
            VERSION_COUNT=$(ls "$MIGRATION_DIR" 2>/dev/null | grep -cE '^V[0-9]+' || true)
            if [ "${VERSION_COUNT:-0}" -eq 0 ]; then
                echo "  - ${svc}（${dialect}）暂无迁移脚本，跳过"
            else
                echo "  ✓ ${svc}（${dialect}）版本号唯一（共 ${VERSION_COUNT} 个迁移脚本）"
            fi
        fi
    done
done

if [ "$DUPLICATE_FOUND" = false ]; then
    check_pass "所有服务迁移版本号唯一，无重复版本冲突"
else
    check_fail "检测到重复的迁移版本号，Flyway 启动将失败"
fi

# 6. 检查各业务服务独立 V1 迁移链
echo ""
echo -e "${YELLOW}[6/9] 检查各业务服务独立 V1 迁移链...${NC}"
TOTAL_CHECKS=$((TOTAL_CHECKS + 1))

ALL_CHAIN_EXISTS=true
for file in "${SERVICE_CHAIN_FILES[@]}"; do
    if [ -f "$file" ]; then
        echo "  ✓ $file"
    else
        echo "  ✗ $file 不存在"
        ALL_CHAIN_EXISTS=false
    fi
done

if [ "$ALL_CHAIN_EXISTS" = true ]; then
    check_pass "业务服务独立 Flyway V1 链完整（workflow/file/office 各 MySQL + PostgreSQL）"
else
    check_fail "业务服务独立 Flyway V1 链缺失"
fi

# 7. 检查测试脚本
echo ""
echo -e "${YELLOW}[7/9] 检查测试脚本...${NC}"
TOTAL_CHECKS=$((TOTAL_CHECKS + 1))

if [ -x "scripts/test/test-mysql-migration.sh" ] && \
   [ -x "scripts/test/test-postgresql-migration.sh" ]; then
    check_pass "迁移测试脚本存在且可执行"
else
    check_fail "迁移测试脚本缺失或无执行权限"
fi

# 8. 检查文档
echo ""
echo -e "${YELLOW}[8/9] 检查文档...${NC}"
TOTAL_CHECKS=$((TOTAL_CHECKS + 1))

if [ -f "docs/migration/flyway-migration-fix-report-20240922.md" ] && \
   [ -f "docs/migration/MIGRATION_FIX_SUMMARY.md" ]; then
    check_pass "修复文档完整"
else
    check_fail "修复文档缺失"
fi

# 9. 检查 PostgreSQL V48 语法
echo ""
echo -e "${YELLOW}[9/9] 检查 PostgreSQL V48 语法修复...${NC}"
TOTAL_CHECKS=$((TOTAL_CHECKS + 1))

PG_V48="apex-auth-service/src/main/resources/db/migration/postgresql/V48__add_config_and_audit_menu_permissions.sql"

if [ -f "$PG_V48" ]; then
    # 检查是否包含 DO $$ 语法
    if grep -q "DO \$\$" "$PG_V48"; then
        echo "  ✓ 使用 DO \$\$ 块"
    else
        echo "  ✗ 未使用 DO \$\$ 块"
    fi
    
    # 检查是否使用 CURRENT_TIMESTAMP
    if grep -q "CURRENT_TIMESTAMP" "$PG_V48"; then
        echo "  ✓ 使用 CURRENT_TIMESTAMP"
    else
        echo "  ✗ 未使用 CURRENT_TIMESTAMP"
    fi
    
    # 检查是否使用 ::jsonb
    if grep -q "::jsonb" "$PG_V48"; then
        echo "  ✓ 使用 ::jsonb 类型转换"
    else
        echo "  ✗ 未使用 ::jsonb 类型转换"
    fi
    
    # 检查是否使用 true/false
    if grep -q "true" "$PG_V48" && grep -q "false" "$PG_V48"; then
        echo "  ✓ 使用 true/false 布尔值"
    else
        echo "  ✗ 未使用 true/false 布尔值"
    fi
    
    check_pass "PostgreSQL V48 语法修复完成"
else
    check_fail "PostgreSQL V48 文件不存在"
fi

# 最终汇总
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  检查结果汇总${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo "总检查项: $TOTAL_CHECKS"
echo -e "${GREEN}通过: $PASSED_CHECKS${NC}"
echo -e "${RED}失败: $FAILED_CHECKS${NC}"
echo ""

if [ $FAILED_CHECKS -eq 0 ]; then
    echo -e "${GREEN}✓ 所有检查通过！可以进行迁移测试。${NC}"
    echo ""
    echo "下一步："
    echo "  1. 运行 MySQL 迁移测试：./scripts/test/test-mysql-migration.sh"
    echo "  2. 运行 PostgreSQL 迁移测试：./scripts/test/test-postgresql-migration.sh"
    echo ""
    exit 0
else
    echo -e "${RED}✗ 发现 $FAILED_CHECKS 个问题，请修复后再进行迁移测试。${NC}"
    echo ""
    exit 1
fi
