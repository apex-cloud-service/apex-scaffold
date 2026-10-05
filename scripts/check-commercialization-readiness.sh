#!/bin/bash
# ============================================================================
# Apex Scaffold 商业化就绪度检查脚本
# ============================================================================
# 用途: 检查项目是否达到商业化售卖标准
# 版本: v1.0.0
# 日期: 2026-09-09
# ============================================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 统计变量
PASSED=0
FAILED=0
WARNING=0

# 打印标题
print_header() {
    echo -e "\n${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}\n"
}

# 打印成功信息
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
    ((PASSED++))
}

# 打印失败信息
print_error() {
    echo -e "${RED}✗ $1${NC}"
    ((FAILED++))
}

# 打印警告信息
print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
    ((WARNING++))
}

# 打印信息
print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

# ============================================================================
# 检查1: 代码通用性
# ============================================================================
check_code_generality() {
    print_header "检查1: 代码通用性"
    
    # 检查Sample/Report实体是否有说明
    if grep -q "模块说明" apex-domain/src/main/java/com/apex/scaffold/domain/model/entity/SampleEntity.java; then
        print_success "Sample实体已添加通用说明"
    else
        print_error "Sample实体缺少通用说明"
    fi
    
    if grep -q "模块说明" apex-domain/src/main/java/com/apex/scaffold/domain/model/entity/ReportEntity.java; then
        print_success "Report实体已添加通用说明"
    else
        print_error "Report实体缺少通用说明"
    fi
    
    # 检查微信集成标注
    if grep -q "可选集成" apex-domain/src/main/java/com/apex/scaffold/domain/gateway/WeChatGateway.java; then
        print_success "微信集成已标注为可选模块"
    else
        print_error "微信集成未标注可选性"
    fi
}

# ============================================================================
# 检查2: 文档通用性
# ============================================================================
check_documentation() {
    print_header "检查2: 文档通用性"
    
    # 检查商业化文档是否更新
    if grep -q "v3.*2026-09-09" docs/COMMERCIAL_OPTIMIZATION_PROPOSAL.md; then
        print_success "COMMERCIAL_OPTIMIZATION_PROPOSAL.md已更新"
    else
        print_warning "COMMERCIAL_OPTIMIZATION_PROPOSAL.md可能未更新"
    fi
    
    if grep -q "项目已基本完成通用化" docs/COMMERCIAL_REFACTORING_PLAN.md; then
        print_success "COMMERCIAL_REFACTORING_PLAN.md已更新"
    else
        print_warning "COMMERCIAL_REFACTORING_PLAN.md可能未更新"
    fi
    
    # 检查新方案文档
    if [ -f ".trae/documents/商业化脚手架重构方案.md" ]; then
        print_success "商业化脚手架重构方案.md存在"
    else
        print_error "商业化脚手架重构方案.md不存在"
    fi
    
    if [ -f ".trae/documents/业务代码删除清单.md" ]; then
        print_success "业务代码删除清单.md存在"
    else
        print_error "业务代码删除清单.md不存在"
    fi
}

# ============================================================================
# 检查3: 数据库脚本
# ============================================================================
check_database_scripts() {
    print_header "检查3: 数据库迁移脚本"
    
    # 检查V9脚本注释
    if grep -q "通用用户属性" apex-interfaces/src/main/resources/db/migration/mysql/V9__add_birthday_onboarding_to_sys_user.sql; then
        print_success "V9迁移脚本注释已优化"
    else
        print_error "V9迁移脚本注释未优化"
    fi
    
    # 统计迁移脚本数量
    MIGRATION_COUNT=$(find apex-interfaces/src/main/resources/db/migration/mysql -name "V*.sql" | wc -l)
    print_info "Flyway迁移脚本总数: $MIGRATION_COUNT"
}

# ============================================================================
# 检查4: 模块结构
# ============================================================================
check_module_structure() {
    print_header "检查4: 模块结构"
    
    # 检查核心模块
    MODULES=("apex-common" "apex-domain" "apex-application" "apex-workflow" "apex-infrastructure" "apex-interfaces" "apex-example" "apex-generator")
    
    for module in "${MODULES[@]}"; do
        if [ -d "$module" ]; then
            print_success "模块 $module 存在"
        else
            print_error "模块 $module 不存在"
        fi
    done
}

# ============================================================================
# 检查5: 核心功能文件
# ============================================================================
check_core_files() {
    print_header "检查5: 核心功能文件"
    
    # 检查关键文件
    FILES=(
        "README.md"
        "pom.xml"
        "docker-compose.yml"
        "LICENSE"
    )
    
    for file in "${FILES[@]}"; do
        if [ -f "$file" ]; then
            print_success "文件 $file 存在"
        else
            print_error "文件 $file 不存在"
        fi
    done
}

# ============================================================================
# 检查6: License机制
# ============================================================================
check_license_mechanism() {
    print_header "检查6: License机制"
    
    # 检查License模块
    if [ -d "apex-license-generator" ] && [ -d "apex-license-validator" ]; then
        print_success "License模块存在"
    else
        print_error "License模块不完整"
    fi
    
    # 检查FeatureGate注解
    if find apex-common/src -name "FeatureGate.java" | grep -q .; then
        print_success "FeatureGate注解存在"
    else
        print_error "FeatureGate注解不存在"
    fi
}

# ============================================================================
# 检查7: 三档Profile配置
# ============================================================================
check_profiles() {
    print_header "检查7: 三档Profile配置"
    
    # 检查pom.xml中的profile
    if grep -q "<id>basic</id>" pom.xml && \
       grep -q "<id>standard</id>" pom.xml && \
       grep -q "<id>enterprise</id>" pom.xml; then
        print_success "三档Profile配置存在"
    else
        print_error "三档Profile配置不完整"
    fi
}

# ============================================================================
# 检查8: 测试覆盖
# ============================================================================
check_tests() {
    print_header "检查8: 测试覆盖"
    
    # 统计测试文件数量
    TEST_COUNT=$(find . -name "*Test.java" -not -path "./target/*" | wc -l)
    print_info "测试文件总数: $TEST_COUNT"
    
    if [ "$TEST_COUNT" -gt 200 ]; then
        print_success "测试覆盖充分 ($TEST_COUNT 个测试文件)"
    else
        print_warning "测试覆盖可能不足 ($TEST_COUNT 个测试文件)"
    fi
}

# ============================================================================
# 主函数
# ============================================================================
main() {
    echo -e "${BLUE}"
    echo "============================================"
    echo "  Apex Scaffold 商业化就绪度检查"
    echo "  版本: v1.0.0"
    echo "  日期: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "============================================"
    echo -e "${NC}\n"
    
    # 切换到项目根目录
    cd "$(dirname "$0")/.."
    print_info "项目根目录: $(pwd)"
    
    # 执行所有检查
    check_code_generality
    check_documentation
    check_database_scripts
    check_module_structure
    check_core_files
    check_license_mechanism
    check_profiles
    check_tests
    
    # 打印汇总报告
    print_header "检查汇总"
    echo -e "${GREEN}通过: $PASSED${NC}"
    echo -e "${YELLOW}警告: $WARNING${NC}"
    echo -e "${RED}失败: $FAILED${NC}"
    echo ""
    
    # 计算总分
    TOTAL=$((PASSED + FAILED + WARNING))
    SCORE=$(awk "BEGIN {printf \"%.1f\", ($PASSED * 100.0) / $TOTAL}")
    
    echo -e "总体评分: ${BLUE}$SCORE/100${NC}"
    
    # 评级
    if (( $(echo "$SCORE >= 90" | bc -l) )); then
        echo -e "评级: ${GREEN}优秀 - 已达到商业化售卖标准${NC}"
        EXIT_CODE=0
    elif (( $(echo "$SCORE >= 80" | bc -l) )); then
        echo -e "评级: ${YELLOW}良好 - 接近商业化标准，需完成警告项${NC}"
        EXIT_CODE=0
    elif (( $(echo "$SCORE >= 70" | bc -l) )); then
        echo -e "评级: ${YELLOW}及格 - 需要进一步优化${NC}"
        EXIT_CODE=1
    else
        echo -e "评级: ${RED}不合格 - 需要大量优化${NC}"
        EXIT_CODE=1
    fi
    
    echo -e "\n${BLUE}============================================${NC}\n"
    
    exit $EXIT_CODE
}

# 执行主函数
main
